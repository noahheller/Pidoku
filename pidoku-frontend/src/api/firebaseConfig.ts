import type { FirebaseOptions } from 'firebase/app';

/**
 * The Firebase web app's config, from `firebase apps:sdkconfig WEB --project pidoku`. These values
 * identify the project and aren't secret: they ship in every Firebase web page. What protects
 * player data is the API checking each ID token.
 *
 * Null turns sign-in off: the game still plays, but attempts aren't recorded on the server.
 */
export const firebaseConfig: FirebaseOptions | null = {
  apiKey: 'AIzaSyDDptF9XWOrk_j5xAYPiAMQ_r2AQwsZSEc',
  authDomain: 'pidoku.firebaseapp.com',
  projectId: 'pidoku',
  appId: '1:816584687259:web:6f4fe427a62ae6786ad03e',
};
