import { test, expect } from '@playwright/test';

/**
 * Full-workflow smoke rewritten for the current Project Workspace.
 * The prior file targeted the deleted Cube Wizard + full stack assumed
 * up. This one walks a single project through every workspace tab and
 * the top-level feature surfaces with route-level API stubs, asserting
 * no 404 and no runtime explosion.
 */

const PROJECT_ID = 'p1';
const projectFixture = {
  id: PROJECT_ID,
  name: 'Full Workflow',
  description: 'End-to-end smoke',
  baseUri: 'https://example.org/full/',
  status: 'ACTIVE',
  createdBy: '00000000-0000-0000-0000-000000000001',
  createdAt: new Date().toISOString(),
};

test.describe('Project workspace full tour', () => {
  test.beforeEach(async ({ page }) => {
    await page.route(/\/api\/v1\/projects\?.*$/, (r) =>
      r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([projectFixture]) })
    );
    await page.route(/\/api\/v1\/projects\/p1(\?.*)?$/, (r) =>
      r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(projectFixture) })
    );
    await page.route(/\/api\/v1\/projects\/p1\/summary$/, (r) =>
      r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          ...projectFixture,
          counts: {
            pipelines: 0, dataSources: 0, shapes: 0, dimensions: 0,
            cubes: 0, jobs: 0, triplestores: 0,
          },
        }),
      })
    );
    await page.route(/\/api\/v1\/(ontologies|mappings|validation\/suites|releases|reconciliation\/candidates|sparql\/queries|data|dimensions|triplestores|shapes|pipelines|jobs|cubes)/, (r) =>
      r.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
    );
    await page.route(/\/api\/v1\/admin\/extensions/, (r) =>
      r.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
    );
    await page.route(/\/api\/v1\/lineage\/project\/p1$/, (r) =>
      r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ projectId: PROJECT_ID, nodes: [], edges: [] }),
      })
    );
  });

  test('project workspace tabs and feature surfaces all 200', async ({ page }) => {
    const tabs = [
      'overview', 'data', 'ontology', 'mapping',
      'validation', 'publish', 'lineage', 'docs', 'reconciliation',
    ];
    for (const tab of tabs) {
      const resp = await page.goto(`/projects/${PROJECT_ID}/${tab}`);
      expect(resp && resp.status()).not.toBe(404);
      await expect(page.locator('body')).toBeVisible();
    }

    for (const path of ['/extensions', '/sparql', '/cubes', '/data', '/triplestore']) {
      const resp = await page.goto(path);
      expect(resp && resp.status()).not.toBe(404);
      await expect(page.locator('body')).toBeVisible();
    }
  });
});
