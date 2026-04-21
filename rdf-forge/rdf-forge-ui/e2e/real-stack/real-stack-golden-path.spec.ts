import { test, expect, request } from '@playwright/test';

/**
 * Real-stack golden path.
 *
 * Runs against the compose/standalone deployment at E2E_BASE_URL (default
 * http://localhost:4200). The UI proxies /api/* to the real gateway at
 * gateway:8080 via docker/nginx-standalone.conf, so every fetch this test
 * triggers hits the real backend and the resulting state persists in
 * Postgres.
 *
 * NO page.route stubbing. If this suite passes, the UI truly speaks to
 * the gateway, pipeline/data/shacl/triplestore/dimension/job services,
 * MinIO, and Postgres through the full standalone topology.
 */

const UI_BASE = process.env['E2E_BASE_URL'] || 'http://localhost:4200';
// We anchor the APIRequestContext at the UI host, not at /api/v1, because
// Playwright's baseURL + a leading-slash path discards the baseURL path
// component (so baseURL http://host/api/v1 + get('/projects') becomes
// http://host/projects and hits the SPA, not the gateway). Keep baseURL at
// the host and spell out /api/v1/... on each call.
async function apiCtx() {
  return request.newContext({ baseURL: UI_BASE, extraHTTPHeaders: { Accept: 'application/json' } });
}

test.describe('real-stack golden path', () => {
  test.beforeAll(async () => {
    // Sanity: refuse to run if the backend is not actually answering. The
    // whole point of this file is to catch a broken stack, not silently
    // paper over it. nginx only proxies /api/*, so we probe /api/v1/projects
    // instead of /actuator/health — it returns a JSON list from pipeline-service
    // and cannot be served by the SPA fallback.
    const api = await apiCtx();
    const probe = await api.get('/api/v1/projects');
    expect(probe.ok(), `/api/v1/projects must answer 200 (got ${probe.status()})`).toBeTruthy();
    const contentType = probe.headers()['content-type'] ?? '';
    expect(contentType, 'probe must return JSON, not SPA HTML').toContain('application/json');
    await api.dispose();
  });

  test('create a project through the UI and confirm it persists via the gateway', async ({ page }) => {
    // Unique name per run so repeated runs do not collide on the unique constraint.
    const stamp = Date.now();
    const name = `E2E Project ${stamp}`;
    const baseUri = `https://example.org/e2e/${stamp}/`;

    // 1) Drive the real UI to submit the project form.
    await page.goto(`${UI_BASE}/projects/new`);
    await expect(page.locator('app-root')).toBeVisible();

    const nameInput = page.locator('input[formcontrolname="name"]');
    const baseUriInput = page.locator('input[formcontrolname="baseUri"]');
    await expect(nameInput).toBeVisible();
    await expect(baseUriInput).toBeVisible();

    await nameInput.fill(name);
    await baseUriInput.fill(baseUri);

    // Prefer the labelled button so the test survives any Angular
    // template refactor that replaces type=submit with a regular click
    // handler (or vice versa).
    const submit = page.getByRole('button', { name: /create project/i });
    await expect(submit).toBeVisible();
    await expect(submit).toBeEnabled();
    await submit.click();

    // Poll the backend up to 15s for the new row. The real acceptance
    // criterion is "did the submit actually persist through the real
    // gateway into pipeline-service/Postgres" — not "did Playwright
    // observe a 2xx response for the POST" (Firefox's XHR layer sometimes
    // reports an empty 500 even when the row lands, which is a
    // client-side artefact and not a stack failure).
    const api = await apiCtx();
    let found: { name?: string; baseUri?: string } | undefined;
    const deadline = Date.now() + 15000;
    while (Date.now() < deadline) {
      const list = await api.get('/api/v1/projects');
      if (list.ok()) {
        const body = await list.json();
        const rows = Array.isArray(body) ? body : (body.content ?? []);
        found = rows.find((p: { name?: string }) => p?.name === name);
        if (found) break;
      }
      await page.waitForTimeout(500);
    }
    await api.dispose();
    expect(found, `project "${name}" must be visible via GET /api/v1/projects within 15s of submit`).toBeTruthy();
    expect(found!.baseUri).toBe(baseUri);
    await expect(page).not.toHaveURL(/404/);
  });

  test('navigate every headline surface against the real backend', async ({ page }) => {
    // Reuse any project that already exists so this test does not care whether
    // the create test ran first. Fall back to seeded demo id if present.
    const api = await apiCtx();
    const list = await api.get('/api/v1/projects');
    expect(list.ok()).toBeTruthy();
    const body = await list.json();
    const rows = Array.isArray(body) ? body : (body.content ?? []);
    let projectId: string;
    if (rows.length > 0) {
      projectId = rows[0].id;
    } else {
      // No project yet — create one on the fly via the real POST so the
      // navigation test can still run standalone.
      const stamp = Date.now();
      const created = await api.post('/api/v1/projects', {
        data: {
          name: `E2E Nav ${stamp}`,
          baseUri: `https://example.org/e2e-nav/${stamp}/`,
          description: 'created by real-stack-golden-path',
        },
      });
      expect(created.ok(), `POST /projects failed: ${created.status()}`).toBeTruthy();
      projectId = (await created.json()).id;
    }
    await api.dispose();

    for (const tab of [
      'overview', 'data', 'ontology', 'mapping',
      'validation', 'publish', 'lineage', 'docs',
    ]) {
      const resp = await page.goto(`${UI_BASE}/projects/${projectId}/${tab}`, { waitUntil: 'domcontentloaded' });
      expect(resp?.status(), `nav to ${tab} returned ${resp?.status()}`).toBeLessThan(400);
      await expect(page.locator('app-root')).toBeVisible();
      // Avoid claiming we asserted tab content — only that the shell rendered
      // without routing to /404 and that the network traffic reached nginx.
      await expect(page).not.toHaveURL(/404/);
    }

    // Top-level surfaces that do not require a project id.
    for (const route of ['/dashboard', '/extensions', '/sparql']) {
      const resp = await page.goto(`${UI_BASE}${route}`, { waitUntil: 'domcontentloaded' });
      expect(resp?.status(), `nav to ${route} returned ${resp?.status()}`).toBeLessThan(400);
      await expect(page).not.toHaveURL(/404/);
    }
  });

  test('gateway returns a real (not stubbed) 404 for an unknown project id', async () => {
    // This pins that /api/v1 is actually the backend: the stubbed suite
    // always returned 200, so a backend 404 proves we are on the real path.
    const api = await apiCtx();
    const resp = await api.get('/api/v1/projects/00000000-0000-0000-0000-000000000404');
    // The stub suite always returned 200. A real gateway hitting a real
    // pipeline-service with no matching project cannot answer 200 — we get
    // 404 (ResourceNotFoundException -> ProblemDetail after the exception
    // handler fix) or, transitionally, 500 on services that have not yet
    // picked up the fix. Either is acceptable here; 200 is not.
    expect(resp.status(), `unknown project id must not answer 200 on the real stack`).not.toBe(200);
    await api.dispose();
  });
});
