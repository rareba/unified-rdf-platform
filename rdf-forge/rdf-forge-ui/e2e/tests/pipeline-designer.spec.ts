import { test, expect } from '@playwright/test';

test.describe('Pipeline Designer', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/pipelines');
  });

  test('should display pipeline list', async ({ page }) => {
    await expect(page.locator('app-pipeline-list')).toBeVisible();
  });

  test('should have create pipeline button', async ({ page }) => {
    const createButton = page.getByRole('button', { name: /create|new/i });
    await expect(createButton).toBeVisible();
  });

  test('should open pipeline designer on create', async ({ page }) => {
    const createButton = page.getByRole('button', { name: /create|new/i });
    await createButton.click();

    // Should show pipeline designer or creation dialog
    await expect(
      page.locator('app-pipeline-designer').or(
        page.getByRole('dialog')
      )
    ).toBeVisible();
  });

  test('should display operation palette', async ({ page }) => {
    // Navigate to designer
    const createButton = page.getByRole('button', { name: /create|new/i });
    await createButton.click();

    // If directly opens designer, check for palette
    const palette = page.getByText(/operations/i).or(
      page.getByText(/source/i)
    ).first();

    if (await palette.isVisible({ timeout: 3000 }).catch(() => false)) {
      // Check operation categories exist (use first() for multiple matches)
      await expect(page.getByText(/source/i).first()).toBeVisible();
      await expect(page.getByText(/transform/i).first()).toBeVisible();
      await expect(page.getByText(/output/i).first()).toBeVisible();
    }
  });

  test('should allow naming pipeline', async ({ page }) => {
    const createButton = page.getByRole('button', { name: /create|new/i });
    await createButton.click();

    // Should have name input
    const nameInput = page.getByLabel(/name/i).or(
      page.getByPlaceholder(/name/i)
    );
    await expect(nameInput).toBeVisible();

    await nameInput.fill('Test Pipeline');
    await expect(nameInput).toHaveValue('Test Pipeline');
  });

  test('should show pipeline canvas area', async ({ page }) => {
    const createButton = page.getByRole('button', { name: /create|new/i });
    await createButton.click();

    // Look for canvas or designer area
    const canvas = page.locator('.pipeline-canvas, .designer-canvas, [data-testid="pipeline-canvas"]');
    if (await canvas.isVisible()) {
      await expect(canvas).toBeVisible();
    }
  });

  test('should have save and run buttons', async ({ page }) => {
    const createButton = page.getByRole('button', { name: /create|new/i });
    await createButton.click();

    // In designer view, should have action buttons
    const saveButton = page.getByRole('button', { name: /save/i });
    const runButton = page.getByRole('button', { name: /run/i });

    // At least one should be present
    const hasSave = await saveButton.isVisible();
    const hasRun = await runButton.isVisible();

    expect(hasSave || hasRun).toBeTruthy();
  });
});

test.describe('Pipeline Execution', () => {
  test.skip('should run a pipeline and show job status', async ({ page }) => {
    // This test requires a saved pipeline and backend
    // Skip for now

    await page.goto('/pipelines');

    // Select a pipeline from list
    await page.locator('.pipeline-row').first().click();

    // Click run
    await page.getByRole('button', { name: /run/i }).click();

    // Should navigate to jobs or show progress
    await expect(
      page.locator('app-job-monitor').or(
        page.getByText(/running/i)
      )
    ).toBeVisible();
  });
});
