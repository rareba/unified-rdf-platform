import { test, expect } from '@playwright/test';

test.describe('Debug Connection', () => {
  test('should connect to the app', async ({ page, baseURL }) => {
    console.log('Test starting...');
    console.log('Base URL:', baseURL);

    try {
      // Use the baseURL from config (port 4200 for dev server, 3000 for Docker)
      const response = await page.goto(baseURL || '/', {
        waitUntil: 'domcontentloaded',
        timeout: 30000
      });

      console.log('Navigation response status:', response?.status());
      console.log('Navigation response URL:', response?.url());

      await page.screenshot({ path: 'debug-127-screenshot.png' });

      const title = await page.title();
      console.log('Page title:', title);

      // Check if it's actually the app (not error page)
      const hasRdfForge = await page.locator('text=RDF').count();
      console.log('Found RDF text:', hasRdfForge > 0);

      // Look for Angular app root
      const appRoot = await page.locator('app-root').count();
      console.log('Found app-root:', appRoot > 0);

      expect(appRoot).toBeGreaterThan(0);

    } catch (error) {
      console.error('Navigation failed:', error);
      await page.screenshot({ path: 'debug-error-screenshot.png' });
      throw error;
    }
  });

  test('should connect using relative path with 127.0.0.1 base', async ({ page }) => {
    console.log('Testing relative path navigation...');

    try {
      const response = await page.goto('/', {
        waitUntil: 'networkidle',
        timeout: 30000
      });

      console.log('Response status:', response?.status());
      console.log('Response URL:', response?.url());

      await page.screenshot({ path: 'debug-relative-screenshot.png' });

      // Check for dashboard or any app content
      const dashboardVisible = await page.locator('.dashboard, app-dashboard, mat-toolbar').first().isVisible().catch(() => false);
      console.log('Dashboard/toolbar visible:', dashboardVisible);

    } catch (error) {
      console.error('Relative navigation failed:', error);
      throw error;
    }
  });
});
