import { test, expect } from '@playwright/test';

/**
 * Top-level navigation smoke rewritten for the current Angular 21 shell.
 * The previous file targeted the removed `app-cube-wizard` route. This
 * suite exercises every primary route and asserts no 404 lands.
 */

test.describe('Primary navigation', () => {
  const ROUTES = [
    '/dashboard',
    '/projects',
    '/projects/new',
    '/pipelines',
    '/data',
    '/shacl',
    '/cubes',
    '/dimensions',
    '/triplestore',
    '/extensions',
    '/sparql',
    '/settings',
  ];

  test.beforeEach(async ({ page }) => {
    await page.route(/\/api\/v1\/.*/, (r) =>
      r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: '[]',
      })
    );
  });

  for (const route of ROUTES) {
    test(`${route} renders`, async ({ page }) => {
      const resp = await page.goto(route);
      expect(resp && resp.status()).not.toBe(404);
      await expect(page.locator('body')).toBeVisible();
    });
  }
});
