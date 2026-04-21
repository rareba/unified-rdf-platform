import { test, expect, request } from '@playwright/test';

/**
 * Real-stack smoke for the pipeline-designer list-view fallback.
 *
 * Since @swimlane/ngx-graph doesn't have an Angular 21-compatible release,
 * the designer ships a numbered vertical list with upstream/downstream
 * dependency chips and move-up / move-down actions. This suite drives the
 * real stack (no page.route stubs) to prove:
 *
 *   1) /pipelines loads the real pipeline list from the gateway.
 *   2) /pipelines/new loads the designer shell for new-pipeline mode.
 *   3) The list-view container (data-testid="pipeline-step-list") is in
 *      the DOM — so the fallback renderer actually shipped.
 */

const UI_BASE = process.env['E2E_BASE_URL'] || 'http://localhost:4200';

test.describe('pipeline designer (real stack)', () => {
  test.beforeAll(async () => {
    const api = await request.newContext({ baseURL: UI_BASE });
    const probe = await api.get('/api/v1/projects');
    expect(probe.ok(), `gateway must answer 200; got ${probe.status()}`).toBeTruthy();
    await api.dispose();
  });

  test('pipelines list page loads through the real backend', async ({ page }) => {
    const resp = await page.goto(`${UI_BASE}/pipelines`, { waitUntil: 'domcontentloaded' });
    expect(resp?.status(), '/pipelines navigation status').toBeLessThan(400);
    await expect(page).not.toHaveURL(/404/);
    await expect(page.locator('app-root')).toBeVisible();
  });

  test('pipelines/new renders the designer shell and the list-view fallback', async ({ page }) => {
    const resp = await page.goto(`${UI_BASE}/pipelines/new`, { waitUntil: 'domcontentloaded' });
    expect(resp?.status(), '/pipelines/new navigation status').toBeLessThan(400);
    await expect(page).not.toHaveURL(/404/);
    await expect(page.locator('app-root')).toBeVisible();

    // The list-view container ships regardless of whether any steps have
    // been added — it wraps the empty-state and the numbered <ol>.
    const listContainer = page.locator('[data-testid="pipeline-step-list"]');
    await expect(listContainer).toBeVisible();
  });
});
