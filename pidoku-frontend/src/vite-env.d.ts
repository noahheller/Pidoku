/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** "1" in the Playwright build: sign in with e2e test tokens instead of Firebase (see api/auth.ts). */
  readonly VITE_E2E_AUTH?: string;
}
