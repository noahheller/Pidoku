import type { Auth, AuthError, User } from 'firebase/auth';
import { hasAccount, markHasAccount } from '../game/storage';
import { firebaseConfig } from './firebaseConfig';

/**
 * Firebase sign-in. Every visitor gets an anonymous account (a uid) with no sign-up, kept in this
 * browser by the Firebase SDK, so they are the same player on every visit. The API identifies the
 * player by the account's ID token.
 *
 * An anonymous account can be upgraded to Google (`signInWithGoogle`). Linking keeps the same uid,
 * so the player, their attempts and streak carry over, and the account then works on any device.
 */

let authInstance: Promise<Auth> | null = null;
let anonymousSignIn: Promise<User> | null = null;

/** The Firebase Auth instance, once it has restored any saved account. */
function getAuthInstance(config: NonNullable<typeof firebaseConfig>): Promise<Auth> {
  authInstance ??= (async () => {
    // Loaded on first use, so the Firebase SDK isn't in the bundle the start screen waits for.
    const [{ initializeApp }, { getAuth }] = await Promise.all([import('firebase/app'), import('firebase/auth')]);
    const auth = getAuth(initializeApp(config));
    // Wait for the SDK to restore a saved account before deciding to create one.
    await auth.authStateReady();
    return auth;
  })();
  authInstance.catch(() => (authInstance = null)); // Try again on the next call.
  return authInstance;
}

async function currentUser(auth: Auth): Promise<User> {
  if (auth.currentUser) {
    markHasAccount();
    return auth.currentUser;
  }
  // One shared sign-in, so calls made together don't each create an account.
  anonymousSignIn ??= import('firebase/auth').then(({ signInAnonymously }) => signInAnonymously(auth)).then((c) => c.user);
  try {
    const user = await anonymousSignIn;
    markHasAccount();
    return user;
  } finally {
    anonymousSignIn = null;
  }
}

/**
 * The ID token of the account this browser already has, or null if it has none. Never signs
 * anyone in, and doesn't even load the Firebase SDK for a browser that has never signed in, so a
 * first-time visitor's home screen stays light.
 */
export async function existingIdToken(): Promise<string | null> {
  if (!firebaseConfig || !hasAccount()) return null;
  try {
    const user = (await getAuthInstance(firebaseConfig)).currentUser;
    return user ? await user.getIdToken() : null;
  } catch {
    return null;
  }
}

/**
 * The player's ID token for an `Authorization: Bearer` header, signing them in on first use.
 * Null when sign-in isn't possible (offline, blocked, or not configured): callers then go
 * without, and the game works as a signed-out one.
 */
export async function idToken(): Promise<string | null> {
  if (!firebaseConfig) return null;
  try {
    // The SDK returns its cached token and refreshes it when it's about to expire.
    return await (await currentUser(await getAuthInstance(firebaseConfig))).getIdToken();
  } catch {
    return null;
  }
}

/** How the player is signed in. */
export interface Account {
  /** True until the account is linked to Google: then it only lives in this browser. */
  isAnonymous: boolean;
  /** The Google account's address, for "Signed in as …". */
  email: string | null;
}

/** The player's account, signing them in anonymously if needed. Null when sign-in isn't possible. */
export async function currentAccount(): Promise<Account | null> {
  if (!firebaseConfig) return null;
  try {
    const user = await currentUser(await getAuthInstance(firebaseConfig));
    return { isAnonymous: user.isAnonymous, email: user.email };
  } catch {
    return null;
  }
}

/**
 * What signing in with Google did:
 * - `linked`: the anonymous account is now a Google account, with the same player.
 * - `switched`: that Google account was already a player (signed in on another device), so this
 *   browser now uses it. The anonymous account's games stay with the anonymous account.
 * - `cancelled`: the player closed the Google window.
 */
export type GoogleSignIn = 'linked' | 'switched' | 'cancelled';

/** A failed sign-in, with a message fit to show the player. */
export class SignInError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'SignInError';
  }
}

/**
 * Upgrades the player's account to Google in a popup. Call it straight from a click, so the popup
 * isn't blocked.
 *
 * @param beforeSwitch runs before switching to a Google account that's already a player, while
 *        the anonymous account is still signed in (e.g. to send its unsent solves)
 * @throws SignInError if it didn't work
 */
export async function signInWithGoogle(beforeSwitch: () => Promise<void> = async () => {}): Promise<GoogleSignIn> {
  if (!firebaseConfig) throw new SignInError('Sign-in is turned off on this site.');
  const [auth, { GoogleAuthProvider, linkWithPopup, signInWithCredential, signInWithPopup }] = await Promise.all([
    getAuthInstance(firebaseConfig),
    import('firebase/auth'),
  ]);
  const provider = new GoogleAuthProvider();
  provider.setCustomParameters({ prompt: 'select_account' });
  try {
    const user = auth.currentUser;
    if (!user || !user.isAnonymous) {
      await signInWithPopup(auth, provider);
      return 'switched';
    }
    try {
      await linkWithPopup(user, provider);
      // The token carries the new sign-in provider; refresh it so the API sees the linked account.
      await user.getIdToken(true);
      return 'linked';
    } catch (e) {
      if (errorCode(e) !== 'auth/credential-already-in-use') throw e;
      const credential = GoogleAuthProvider.credentialFromError(e as AuthError);
      if (!credential) throw e;
      await beforeSwitch();
      await signInWithCredential(auth, credential);
      return 'switched';
    }
  } catch (e) {
    if (isCancelled(e)) return 'cancelled';
    throw googleError(e);
  }
}

function isCancelled(e: unknown): boolean {
  const code = errorCode(e);
  return code === 'auth/popup-closed-by-user' || code === 'auth/cancelled-popup-request' || code === 'auth/user-cancelled';
}

/** A failed Google popup as a message fit to show the player. */
function googleError(e: unknown): SignInError {
  const code = errorCode(e);
  if (code === 'auth/popup-blocked') return new SignInError('Your browser blocked the Google sign-in window. Allow pop-ups for this site and try again.');
  if (code === 'auth/network-request-failed') return new SignInError("Couldn't reach Google. Check your connection and try again.");
  if (code === 'auth/user-mismatch') return new SignInError("That's a different Google account. Pick the one you're signed in with.");
  if (code === 'auth/operation-not-allowed') return new SignInError("Google sign-in isn't turned on for Pidoku yet.");
  return new SignInError(`Google sign-in didn't work${code ? ` (${code})` : ''}. Try again in a moment.`);
}

/**
 * Deletes the player's account: first their data on the server (`deleteData`), then the Firebase
 * sign-in itself. Call it straight from a click: a Google account is asked to confirm in a popup
 * first (Firebase only deletes an account signed in recently), and nothing is deleted if they
 * close it.
 *
 * If Firebase still refuses to delete the sign-in, the browser is signed out instead: the data
 * is gone either way, and signing in again starts a new, empty player.
 *
 * @returns `cancelled` if the player closed the Google popup
 * @throws SignInError if the popup didn't work; whatever `deleteData` throws, with nothing deleted
 */
export async function deleteAccount(deleteData: () => Promise<void>): Promise<'deleted' | 'cancelled'> {
  if (!firebaseConfig) throw new SignInError('Sign-in is turned off on this site.');
  const [auth, { GoogleAuthProvider, deleteUser, reauthenticateWithPopup, signOut: firebaseSignOut }] = await Promise.all([
    getAuthInstance(firebaseConfig),
    import('firebase/auth'),
  ]);
  const user = auth.currentUser;
  if (user && !user.isAnonymous) {
    try {
      await reauthenticateWithPopup(user, new GoogleAuthProvider());
    } catch (e) {
      if (isCancelled(e)) return 'cancelled';
      throw googleError(e);
    }
  }
  await deleteData();
  try {
    if (user) await deleteUser(user);
  } catch {
    await firebaseSignOut(auth).catch(() => {});
  }
  return 'deleted';
}

/**
 * Signs out of Google. This browser then gets a new anonymous account, a fresh player; signing in
 * with Google again brings the old one back.
 */
export async function signOut(): Promise<void> {
  if (!firebaseConfig) return;
  const [auth, { signOut: firebaseSignOut }] = await Promise.all([getAuthInstance(firebaseConfig), import('firebase/auth')]);
  await firebaseSignOut(auth);
}

function errorCode(e: unknown): string | null {
  return typeof e === 'object' && e !== null && typeof (e as { code?: unknown }).code === 'string' ? (e as { code: string }).code : null;
}
