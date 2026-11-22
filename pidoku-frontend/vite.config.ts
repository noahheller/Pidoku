import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
// In production Firebase Hosting rewrites /api/** to Cloud Run (see firebase.json), so the
// frontend always calls relative /api URLs. Locally, forward them to the API on :8080, or to
// another API with e.g. API_PROXY=https://pidoku.web.app npm run dev. `vite preview` does the
// same, for the Playwright tests (see e2e/README.md).
const proxy = Object.fromEntries(
  ['/api', '/actuator/health/liveness'].map((path) => [path, { target: process.env.API_PROXY ?? 'http://localhost:8080', changeOrigin: true }]),
)

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: { proxy },
  preview: { proxy },
})
