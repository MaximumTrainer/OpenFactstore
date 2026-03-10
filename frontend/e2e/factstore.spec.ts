import { test, expect } from '@playwright/test'

test('Dashboard loads', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByText('Dashboard')).toBeVisible()
})

test('Flows page loads', async ({ page }) => {
  await page.goto('/flows')
  await expect(page.getByText('Flows')).toBeVisible()
})

test('Assert page has form', async ({ page }) => {
  await page.goto('/assert')
  await expect(page.getByText('Assert Compliance')).toBeVisible()
  await expect(page.getByPlaceholder('sha256:abc123...')).toBeVisible()
})

test('Evidence Vault page loads', async ({ page }) => {
  await page.goto('/evidence')
  await expect(page.getByText('Evidence Vault')).toBeVisible()
})
