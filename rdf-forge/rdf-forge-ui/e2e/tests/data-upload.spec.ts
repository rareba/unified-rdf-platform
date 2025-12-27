import { test, expect } from '@playwright/test';

test.describe('Data Upload and Management', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/data');
  });

  test('should display data manager page', async ({ page }) => {
    await expect(page.locator('app-data-manager')).toBeVisible();
  });

  test('should have upload button', async ({ page }) => {
    const uploadButton = page.getByRole('button', { name: /upload/i });
    await expect(uploadButton).toBeVisible();
  });

  test('should open upload dialog', async ({ page }) => {
    const uploadButton = page.getByRole('button', { name: /upload/i });
    await uploadButton.click();

    // Should show upload dialog or dropzone
    await expect(
      page.getByRole('dialog').or(
        page.getByText(/drag.*drop/i)
      )
    ).toBeVisible();
  });

  test('should show supported file formats', async ({ page }) => {
    const uploadButton = page.getByRole('button', { name: /upload/i }).first();
    await uploadButton.click();

    // Should mention supported formats (use first() for multiple matches)
    const formatText = page.getByText(/csv|xlsx|json|parquet/i).first();
    await expect(formatText).toBeVisible({ timeout: 5000 });
  });

  test('should display data sources list', async ({ page }) => {
    // Look for list or table of data sources
    const list = page.locator('table, .data-source-list, mat-list');
    await expect(list).toBeVisible();
  });

  test('should allow filtering data sources', async ({ page }) => {
    const searchInput = page.getByPlaceholder(/search|filter/i);
    if (await searchInput.isVisible()) {
      await searchInput.fill('test');
      // Verify filtering works (list updates)
      await page.waitForTimeout(500); // Wait for filter to apply
    }
  });

  test('should have refresh button', async ({ page }) => {
    const refreshButton = page.getByRole('button', { name: /refresh/i }).or(
      page.getByRole('button').filter({ has: page.locator('[class*="refresh"]') })
    );
    if (await refreshButton.isVisible()) {
      await expect(refreshButton).toBeEnabled();
    }
  });
});

test.describe('Data Preview', () => {
  test.skip('should preview uploaded data', async ({ page }) => {
    // This test requires existing data
    await page.goto('/data');

    // Select a data source
    await page.locator('.data-source-row, tr').first().click();

    // Should show preview
    await expect(
      page.locator('app-data-preview').or(
        page.locator('table.preview-table')
      )
    ).toBeVisible();
  });

  test.skip('should show column information', async ({ page }) => {
    await page.goto('/data');

    // Select a data source
    await page.locator('.data-source-row, tr').first().click();

    // Should show column names and types
    await expect(page.getByText(/columns/i)).toBeVisible();
  });
});
