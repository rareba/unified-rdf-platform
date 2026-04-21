import { test, expect } from '@playwright/test';

/**
 * Pipeline designer smoke. The view still uses ngx-graph under the
 * hood; this test asserts the shell loads and reaches the designer
 * route without a 404. Exercising the graph canvas interaction under
 * Playwright is environment-sensitive; the Karma unit tests carry the
 * finer-grained coverage.
 */

test.describe('Pipeline designer', () => {
  test.beforeEach(async ({ page }) => {
    await page.route(/\/api\/v1\/pipelines(\?.*)?$/, (r) =>
      r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            { id: 'pl1', name: 'Pipeline 1', description: '', createdBy: 'u1', createdAt: new Date().toISOString() },
          ],
        }),
      })
    );
    await page.route(/\/api\/v1\/pipelines\/pl1$/, (r) =>
      r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'pl1', name: 'Pipeline 1', description: '',
          createdBy: 'u1', createdAt: new Date().toISOString(),
          definition: { steps: [] },
        }),
      })
    );
    await page.route(/\/api\/v1\/operations(\?.*)?$/, (r) =>
      r.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
    );
  });

  test('list opens', async ({ page }) => {
    const r = await page.goto('/pipelines');
    expect(r && r.status()).not.toBe(404);
    await expect(page.locator('body')).toBeVisible();
  });

  test('single pipeline designer route opens without 404', async ({ page }) => {
    const r = await page.goto('/pipelines/pl1');
    expect(r && r.status()).not.toBe(404);
    await expect(page.locator('body')).toBeVisible();
  });
});
