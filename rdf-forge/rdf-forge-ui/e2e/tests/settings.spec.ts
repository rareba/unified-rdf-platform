import { test, expect } from '@playwright/test';

test.describe('Settings', () => {
  test.beforeEach(async ({ page }) => {
    // Clear localStorage before each test
    await page.goto('/');
    await page.evaluate(() => localStorage.clear());
    await page.goto('/settings');
  });

  test('should display settings page with tabs', async ({ page }) => {
    await expect(page.locator('app-settings')).toBeVisible();
    await expect(page.getByRole('tab', { name: /appearance/i })).toBeVisible();
    await expect(page.getByRole('tab', { name: /rdf configuration/i })).toBeVisible();
    await expect(page.getByRole('tab', { name: /pipeline defaults/i })).toBeVisible();
    await expect(page.getByRole('tab', { name: /users/i })).toBeVisible();
    await expect(page.getByRole('tab', { name: /system/i })).toBeVisible();
  });

  test('should change theme and persist it', async ({ page }) => {
    // Select dark theme
    await page.getByLabel(/theme/i).click();
    await page.getByRole('option', { name: /dark/i }).click();

    // Verify theme class is applied
    await expect(page.locator('html')).toHaveClass(/dark-theme/);

    // Reload and verify persistence
    await page.reload();
    await expect(page.locator('html')).toHaveClass(/dark-theme/);
  });

  test('should change language setting', async ({ page }) => {
    await page.getByLabel(/language/i).click();
    await page.getByRole('option', { name: /german/i }).click();

    // Verify it was saved (check localStorage)
    const settings = await page.evaluate(() => localStorage.getItem('rdf-forge-settings'));
    expect(settings).toContain('"language":"de"');
  });

  test('should add custom prefix', async ({ page }) => {
    // Navigate to RDF Configuration tab
    await page.getByRole('tab', { name: /rdf configuration/i }).click();

    // Click add prefix button
    await page.getByRole('button', { name: /add prefix/i }).click();

    // Fill in prefix form
    await page.getByLabel(/prefix/i).fill('ex');
    await page.getByLabel(/uri/i).fill('http://example.org/');

    // Submit
    await page.getByRole('button', { name: /add$/i }).click();

    // Verify prefix was added
    await expect(page.getByText('ex')).toBeVisible();
    await expect(page.getByText('http://example.org/')).toBeVisible();
  });

  test('should update pipeline timeout', async ({ page }) => {
    // Navigate to Pipeline Defaults tab
    await page.getByRole('tab', { name: /pipeline defaults/i }).click();

    // Update timeout
    const timeoutInput = page.getByLabel(/pipeline timeout/i);
    await timeoutInput.clear();
    await timeoutInput.fill('600');

    // Verify it was saved
    const settings = await page.evaluate(() => localStorage.getItem('rdf-forge-settings'));
    expect(settings).toContain('"pipelineTimeout":600');
  });

  test('should export settings', async ({ page }) => {
    // Navigate to System tab
    await page.getByRole('tab', { name: /system/i }).click();

    // Click export button
    await page.getByRole('button', { name: /export settings/i }).click();

    // Verify export dialog appears with JSON
    await expect(page.getByRole('dialog')).toBeVisible();
    const exportText = await page.locator('textarea').inputValue();
    expect(exportText).toContain('"settings"');
    expect(exportText).toContain('"version"');
  });

  test('should reset settings to defaults', async ({ page }) => {
    // First change a setting
    await page.getByLabel(/theme/i).click();
    await page.getByRole('option', { name: /dark/i }).click();

    // Navigate to System tab
    await page.getByRole('tab', { name: /system/i }).click();

    // Handle confirmation dialog
    page.on('dialog', dialog => dialog.accept());

    // Click reset button
    await page.getByRole('button', { name: /reset to defaults/i }).click();

    // Verify theme is back to light
    await expect(page.locator('html')).toHaveClass(/light-theme/);
  });

  test('should check service health', async ({ page }) => {
    // Navigate to System tab
    await page.getByRole('tab', { name: /system/i }).click();

    // Click check health button
    await page.getByRole('button', { name: /check health/i }).click();

    // Verify health status is displayed (may be UP or DOWN depending on backend)
    await expect(page.getByText(/gateway/i)).toBeVisible();
  });
});
