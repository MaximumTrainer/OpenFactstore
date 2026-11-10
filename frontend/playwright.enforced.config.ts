import { defineConfig, devices } from '@playwright/test'

/**
 * The enforced end-to-end suite (#209): a real backend with authentication enforced, reached
 * through the dev server's /api proxy. Unlike ./e2e, nothing is stubbed. The CI job
 * "Frontend E2E (authentication enforced)" starts the backend and the dev server first.
 */
export default defineConfig({
  testDir: './e2e-enforced',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure'
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ]
})
