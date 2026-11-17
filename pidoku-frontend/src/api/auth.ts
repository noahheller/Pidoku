import type { User } from 'firebase/auth';
import { firebaseConfig } from './firebaseConfig';

/**
 * Anonymous Firebase sign-in: every visitor gets an account (a uid) with no sign-up, kept in this
 * browser by the Firebase SDK, so they are the same player on every visit. The API identifies the
 * player by the account's ID token.
 */

let signIn: Promise<User> | null = null;

async function signedInUser(config: NonNullable<typeof firebaseConfig>): Promise<User> {
  // Loaded on first use, so the Firebase SDK isn't in the bundle the start screen waits for.
  const [{ initializeApp }, { getAuth, signInAnonymously }] = await Promise.all([import('firebase/app'), import('firebase/auth')]);
  const auth = getAuth(initializeApp(config));
  // Wait for the SDK to restore a saved account before deciding to create one.
  await auth.authStateReady();
  return auth.currentUser ?? (await signInAnonymously(auth)).user;
}

/**
 * The player's ID token for an `Authorization: Bearer` header, signing them in on first use.
 * Null when sign-in isn't possible (offline, blocked, or not configured): callers then go
 * without, and the game works as a signed-out one.
 */
export async function idToken(): Promise<string | null> {
  if (!firebaseConfig) return null;
  // One shared sign-in, so calls made together don't each create an account.
  signIn ??= signedInUser(firebaseConfig);
  try {
    // The SDK returns its cached token and refreshes it when it's about to expire.
    return await (await signIn).getIdToken();
  } catch {
    signIn = null; // Try again on the next call.
    return null;
  }
}
