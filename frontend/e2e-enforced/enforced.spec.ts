import { test, expect } from '@playwright/test'

/**
 * #209 (#155 §5, closes the last item of #156) — the UI end to end against a real backend with
 * authentication enforced, nothing stubbed.
 *
 * Each role signs in once through development sign-in, lands on the page it was heading for,
 * sees its role in the user menu, and is offered only the controls its role allows. The CI job
 * that runs this starts the backend with SECURITY_ENFORCE_AUTH=true and dev sign-in enabled.
 */

const roles = ['ADMIN', 'MEMBER', 'VIEWER'] as const

for (const role of roles) {
  test(`${role} signs in through development sign-in and lands where they were going`, async ({ page }) => {
    await page.goto('/flows')
    await expect(page).toHaveURL(/\/login\?/)

    await page.locator('[data-test="dev-email"]').fill(`${role.toLowerCase()}-${Date.now()}@localhost`)
    await page.locator('[data-test="dev-role"]').selectOption(role)
    await page.locator('[data-test="dev-login"] button[type="submit"]').click()

    await expect(page).toHaveURL(/\/flows$/)
    await expect(page.getByRole('heading', { name: 'Flows', exact: true })).toBeVisible()
    await expect(page.locator('[data-test="user-role"]')).toHaveText(role)

    // Creating a flow is an ADMIN capability (#155 FR-4.2); MEMBER and VIEWER are not offered it.
    const newFlow = page.getByRole('button', { name: /New Flow/ })
    if (role === 'ADMIN') {
      await expect(newFlow).toBeVisible()
    } else {
      await expect(newFlow).toHaveCount(0)
    }
  })
}

test('an API call without a credential is refused with a Bearer challenge', async ({ request }) => {
  const response = await request.get('/api/v1/flows')

  expect(response.status()).toBe(401)
  expect(response.headers()['www-authenticate']).toContain('Bearer')
})

test('signing out ends the session', async ({ page }) => {
  await page.goto('/login')
  await page.locator('[data-test="dev-email"]').fill(`signout-${Date.now()}@localhost`)
  await page.locator('[data-test="dev-login"] button[type="submit"]').click()
  await expect(page.locator('[data-test="user-menu"]')).toBeVisible()

  await page.locator('[data-test="user-menu-button"]').click()
  // Wait for the server to end the session before navigating, or the navigation aborts it.
  await Promise.all([
    page.waitForResponse(r => r.url().includes('/api/v1/auth/logout')),
    page.locator('[data-test="sign-out"]').click(),
  ])
  await expect(page).toHaveURL(/\/login/)

  await page.goto('/flows')
  await expect(page).toHaveURL(/\/login\?/)
})
