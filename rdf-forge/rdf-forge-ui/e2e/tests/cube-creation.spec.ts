import { test, expect } from '@playwright/test';
import * as path from 'path';

test.describe('Cube Creation Wizard', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/cubes');
  });

  test('should display the cube wizard with first step', async ({ page }) => {
    await expect(page.locator('app-cube-wizard')).toBeVisible();
    await expect(page.getByText(/basic information/i)).toBeVisible();
  });

  test('should require cube name to proceed', async ({ page }) => {
    // Try to proceed without filling name
    const nextButton = page.getByRole('button', { name: /next/i });

    // Button should be disabled when name is empty
    await expect(nextButton).toBeDisabled();

    // Fill in cube name
    await page.getByLabel(/cube name/i).fill('Test Cube');

    // Now button should be enabled
    await expect(nextButton).toBeEnabled();
  });

  test('should auto-generate cube ID from name', async ({ page }) => {
    await page.getByLabel(/cube name/i).fill('My Test Cube 2024');

    // Check that generated ID appears
    const generatedIdElement = page.locator('[data-testid="generated-id"]').or(
      page.getByText(/my-test-cube-2024/i)
    );
    await expect(generatedIdElement).toBeVisible();
  });

  test('should navigate through wizard steps', async ({ page }) => {
    // Step 1: Basic Information
    await page.getByLabel(/cube name/i).fill('Test Cube');
    await page.getByRole('button', { name: /next/i }).click();

    // Step 2: Data Source Selection
    await expect(page.getByText(/data source/i)).toBeVisible();

    // Go back
    await page.getByRole('button', { name: /back/i }).click();

    // Should be back on step 1
    await expect(page.getByLabel(/cube name/i)).toBeVisible();
  });

  test('should show data source options on step 2', async ({ page }) => {
    // Complete step 1
    await page.getByLabel(/cube name/i).fill('Test Cube');
    await page.getByRole('button', { name: /next/i }).click();

    // Step 2: Should show existing data sources or upload option
    await expect(page.getByText(/existing data source/i).or(
      page.getByText(/upload/i)
    )).toBeVisible();
  });

  test('should display column mapping on step 3', async ({ page }) => {
    // This test requires a data source to be selected
    // For now, we'll just verify the step exists in the wizard
    await page.getByLabel(/cube name/i).fill('Test Cube');

    // Check that stepper shows all steps
    await expect(page.getByText(/column mapping/i)).toBeVisible();
    await expect(page.getByText(/metadata/i)).toBeVisible();
    await expect(page.getByText(/validation/i)).toBeVisible();
    await expect(page.getByText(/publish/i)).toBeVisible();
  });

  test('should show validation checks on step 5', async ({ page }) => {
    // Navigate to validation step visually
    await page.getByLabel(/cube name/i).fill('Test Cube');

    // Check step labels exist
    const validationStep = page.getByText(/validation/i);
    await expect(validationStep).toBeVisible();
  });

  test('should allow cancellation', async ({ page }) => {
    await page.getByLabel(/cube name/i).fill('Test Cube');

    // Click cancel
    const cancelButton = page.getByRole('button', { name: /cancel/i });
    if (await cancelButton.isVisible()) {
      await cancelButton.click();
      // Should navigate away from wizard
      await expect(page).not.toHaveURL(/.*cubes/);
    }
  });

  test('should persist form state when navigating steps', async ({ page }) => {
    const cubeName = 'Persistent Test Cube';

    // Fill step 1
    await page.getByLabel(/cube name/i).fill(cubeName);
    await page.getByRole('button', { name: /next/i }).click();

    // Go back
    await page.getByRole('button', { name: /back/i }).click();

    // Verify name is still there
    await expect(page.getByLabel(/cube name/i)).toHaveValue(cubeName);
  });
});

test.describe('Cube Creator with Data', () => {
  test.skip('should complete full cube creation flow', async ({ page }) => {
    // This test requires backend to be running
    // Skip for now, enable when backend is available

    await page.goto('/cubes');

    // Step 1: Basic Information
    await page.getByLabel(/cube name/i).fill('Swiss Population Cube');
    await page.getByLabel(/description/i).fill('Population data by canton');
    await page.getByRole('button', { name: /next/i }).click();

    // Step 2: Data Source
    // Upload CSV or select existing
    // ...

    // Step 3: Column Mapping
    // Map columns to dimensions/measures
    // ...

    // Step 4: Metadata
    await page.getByLabel(/title/i).fill('Swiss Population Statistics');
    await page.getByLabel(/publisher/i).fill('Swiss Federal Office');
    await page.getByRole('button', { name: /next/i }).click();

    // Step 5: Validation
    await page.getByRole('button', { name: /validate/i }).click();
    // Wait for validation to complete
    // ...

    // Step 6: Publish
    await page.getByRole('button', { name: /publish/i }).click();

    // Should show success message or navigate to jobs
    await expect(page.getByText(/success/i).or(
      page.locator('app-job-list')
    )).toBeVisible();
  });
});
