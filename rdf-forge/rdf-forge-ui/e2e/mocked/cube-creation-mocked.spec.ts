import { test, expect } from '@playwright/test';

/**
 * Cube Creator smoke rewritten for the current 4-tab redesign
 * (CubeList + CubeProject shell). The prior file targeted the deleted
 * `app-cube-wizard` component.
 */

const cubeFixture = {
  id: 'c1',
  projectId: 'p1',
  name: 'Demo Cube',
  status: 'draft',
  createdBy: '00000000-0000-0000-0000-000000000001',
  createdAt: new Date().toISOString(),
};

test.describe('Cube Creator (post-redesign)', () => {
  test.beforeEach(async ({ page }) => {
    await page.route(/\/api\/v1\/cubes(\?.*)?$/, (r) =>
      r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: [cubeFixture] }),
      })
    );
    await page.route(/\/api\/v1\/cubes\/c1(\?.*)?$/, (r) =>
      r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(cubeFixture),
      })
    );
    await page.route(/\/api\/v1\/data(\?.*)?$/, (r) =>
      r.fulfill({ status: 200, contentType: 'application/json', body: '{"content": []}' })
    );
    await page.route(/\/api\/v1\/dimensions(\?.*)?$/, (r) =>
      r.fulfill({ status: 200, contentType: 'application/json', body: '{"content": []}' })
    );
  });

  test('cubes list renders without 404', async ({ page }) => {
    const resp = await page.goto('/cubes');
    expect(resp && resp.status()).not.toBe(404);
    await expect(page.locator('body')).toBeVisible();
  });

  test('cube-project 4-tab shell opens each tab without 404', async ({ page }) => {
    for (const tab of ['csv-mapping', 'transform', 'cube-designer', 'publish']) {
      const resp = await page.goto(`/cubes/c1/${tab}`);
      expect(resp && resp.status()).not.toBe(404);
      await expect(page.locator('body')).toBeVisible();
    }
  });
});
