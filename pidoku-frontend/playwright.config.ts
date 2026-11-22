import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests (see e2e/README.md): the real API on a Testcontainers Postgres
 * (`:pidoku-api:bootTestRun`, port 8181) and the frontend's e2e build served by `vite preview`
 * (port 4181), which forwards /api to it. Test tokens stand in for Firebase.
 */
const API_PORT = 8181;
const WEB_PORT = 4181;
const CI = !!process.env.CI;
// A browser already on the machine (e.g. PLAYWRIGHT_CHROMIUM=/usr/bin/chromium) instead of
// Playwright's own download.
const executablePath = process.env.PLAYWRIGHT_CHROMIUM || undefined;

export default defineConfig({
  testDir: 'e2e',
  testMatch: '*.e2e.ts',
  // The tests share one API and database; each player is new, but dailies are shared.
  fullyParallel: true,
  workers: CI ? 2 : undefined,
  forbidOnly: CI,
  retries: CI ? 1 : 0,
  timeout: 60_000,
  reporter: CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: `http://localhost:${WEB_PORT}`,
    // Dailies are by date; the API's dates and the browser's agree in UTC.
    timezoneId: 'UTC',
    locale: 'en-GB',
    trace: 'retain-on-failure',
    launchOptions: { executablePath },
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], launchOptions: { executablePath } } },
    { name: 'phone', use: { ...devices['Pixel 7'], launchOptions: { executablePath } } },
  ],
  webServer: [
    {
      // Boots slowly the first time (Gradle, Postgres image, then today's dailies are generated).
      command: '../gradlew -p .. :pidoku-api:bootTestRun',
      // Answers once today's dailies are generated (see E2eConfiguration).
      url: `http://localhost:${API_PORT}/actuator/health`,
      env: { E2E_API_PORT: String(API_PORT), TZ: 'UTC' },
      timeout: 600_000,
      reuseExistingServer: !CI,
      stdout: 'ignore',
      stderr: 'pipe',
    },
    {
      command: `npm run build:e2e && npx vite preview --outDir dist-e2e --port ${WEB_PORT} --strictPort`,
      url: `http://localhost:${WEB_PORT}`,
      env: { API_PROXY: `http://localhost:${API_PORT}` },
      timeout: 180_000,
      reuseExistingServer: !CI,
    },
  ],
});
