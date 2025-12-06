import { test, expect } from '@playwright/test';

test.describe('Navigation', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('should display the dashboard on initial load', async ({ page }) => {
    await expect(page).toHaveTitle(/Cube Creator X/);
    await expect(page.locator('app-dashboard')).toBeVisible();
  });

  test('should navigate to pipelines page', async ({ page }) => {
    await page.getByRole('link', { name: /pipelines/i }).click();
    await expect(page).toHaveURL(/.*pipelines/);
    await expect(page.locator('app-pipeline-list')).toBeVisible();
  });

  test('should navigate to cube creator page', async ({ page }) => {
    await page.getByRole('link', { name: /cubes/i }).click();
    await expect(page).toHaveURL(/.*cubes/);
    await expect(page.locator('app-cube-wizard')).toBeVisible();
  });

  test('should navigate to data sources page', async ({ page }) => {
    await page.getByRole('link', { name: /data/i }).click();
    await expect(page).toHaveURL(/.*data/);
  });

  test('should navigate to jobs page', async ({ page }) => {
    await page.getByRole('link', { name: /jobs/i }).click();
    await expect(page).toHaveURL(/.*jobs/);
    await expect(page.locator('app-job-list')).toBeVisible();
  });

  test('should navigate to settings page', async ({ page }) => {
    await page.getByRole('link', { name: /settings/i }).click();
    await expect(page).toHaveURL(/.*settings/);
    await expect(page.locator('app-settings')).toBeVisible();
  });

  test('should navigate to SHACL studio page', async ({ page }) => {
    await page.getByRole('link', { name: /shacl/i }).click();
    await expect(page).toHaveURL(/.*shacl/);
  });

  test('should navigate to triplestore browser page', async ({ page }) => {
    await page.getByRole('link', { name: /triplestore/i }).click();
    await expect(page).toHaveURL(/.*triplestore/);
  });
});
