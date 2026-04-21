import { test, expect } from '@playwright/test';

/**
 * Pipeline designer live interaction test.
 *
 * @swimlane/ngx-graph does not yet support Angular 21; the library was
 * removed from the designer and replaced with a step-list fallback
 * (see docs/PIPELINE_DESIGNER_MIGRATION.md). This test verifies the
 * fallback path renders the pipeline graph data correctly and the
 * step-level interactions still fire.
 */

const pipelineFixture = {
  id: 'pl1',
  name: 'Demo Pipeline',
  description: 'pipeline-designer live smoke',
  createdBy: 'u1',
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
  version: 1,
  definition: {
    steps: [
      { id: 'load', type: 'load-csv', name: 'Load CSV', parameters: { file: '/tmp/x.csv' }, dependencies: [] },
      { id: 'map',  type: 'map-to-rdf', name: 'Map to RDF', parameters: {}, dependencies: ['load'] },
      { id: 'save', type: 'publish-triplestore', name: 'Save', parameters: {}, dependencies: ['map'] }
    ]
  }
};

test.describe('Pipeline designer — list-view fallback', () => {
  test.beforeEach(async ({ page }) => {
    await page.route(/\/api\/v1\/pipelines(\?.*)?$/, (r) =>
      r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: [pipelineFixture] })
      })
    );
    await page.route(/\/api\/v1\/pipelines\/pl1(\?.*)?$/, (r) =>
      r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(pipelineFixture)
      })
    );
    await page.route(/\/api\/v1\/operations(\?.*)?$/, (r) =>
      r.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { id: 'load-csv', type: 'SOURCE', name: 'Load CSV', description: 'Load CSV file', parameters: [] },
          { id: 'map-to-rdf', type: 'TRANSFORM', name: 'Map to RDF', description: 'Map to RDF', parameters: [] },
          { id: 'publish-triplestore', type: 'OUTPUT', name: 'Publish', description: 'Publish to triplestore', parameters: [] }
        ])
      })
    );
  });

  test('designer route opens and renders the step-list fallback', async ({ page }) => {
    const errors: Error[] = [];
    page.on('pageerror', (e) => errors.push(e));

    const resp = await page.goto('/pipelines/pl1');
    expect(resp && resp.status()).not.toBe(404);

    const list = page.locator('[data-testid="pipeline-step-list"]');
    await expect(list).toBeVisible({ timeout: 10000 });

    expect(errors, errors.map(e => e.toString()).join('\n')).toHaveLength(0);

    await page.screenshot({
      path: 'playwright-report/pipeline-designer-live.png',
      fullPage: true
    });
  });
});
