import { test, expect } from '@playwright/test';

test.describe('Debug Refresh Issue', () => {
  test('should capture refresh behavior and console errors', async ({ page }) => {
    const navigationEvents: string[] = [];
    const consoleMessages: { type: string; text: string }[] = [];
    const networkRequests: { url: string; status?: number }[] = [];
    let refreshCount = 0;

    // Capture console messages
    page.on('console', (msg) => {
      consoleMessages.push({ type: msg.type(), text: msg.text() });
      if (msg.type() === 'error') {
        console.log(`[CONSOLE ERROR] ${msg.text()}`);
      }
    });

    // Capture page errors
    page.on('pageerror', (error) => {
      console.log(`[PAGE ERROR] ${error.message}`);
    });

    // Track navigation/refresh events
    page.on('framenavigated', (frame) => {
      if (frame === page.mainFrame()) {
        refreshCount++;
        navigationEvents.push(`Navigation ${refreshCount}: ${frame.url()}`);
        console.log(`[NAVIGATION ${refreshCount}] ${frame.url()}`);
      }
    });

    // Track network requests for auth-related calls
    page.on('request', (request) => {
      const url = request.url();
      if (url.includes('keycloak') || url.includes('auth') || url.includes('token') || url.includes('openid')) {
        console.log(`[AUTH REQUEST] ${request.method()} ${url}`);
        networkRequests.push({ url });
      }
    });

    page.on('response', (response) => {
      const url = response.url();
      if (url.includes('keycloak') || url.includes('auth') || url.includes('token')) {
        console.log(`[AUTH RESPONSE] ${response.status()} ${url}`);
      }
      // Check for redirect responses
      if (response.status() >= 300 && response.status() < 400) {
        console.log(`[REDIRECT] ${response.status()} ${url} -> ${response.headers()['location'] || 'unknown'}`);
      }
    });

    console.log('=== Starting UI Debug Test ===');
    console.log('Navigating to http://localhost:4200...');

    // Navigate to the app
    await page.goto('http://localhost:4200', { waitUntil: 'networkidle', timeout: 30000 });

    console.log(`Initial page URL: ${page.url()}`);
    console.log(`Initial page title: ${await page.title()}`);

    // Wait and observe for refresh behavior
    console.log('Waiting 10 seconds to observe refresh behavior...');
    await page.waitForTimeout(10000);

    console.log('\n=== Summary ===');
    console.log(`Total navigations detected: ${refreshCount}`);
    console.log(`Final URL: ${page.url()}`);

    // Check for common issues
    const authErrors = consoleMessages.filter(m =>
      m.text.toLowerCase().includes('auth') ||
      m.text.toLowerCase().includes('token') ||
      m.text.toLowerCase().includes('keycloak')
    );

    if (authErrors.length > 0) {
      console.log('\n=== Auth-related console messages ===');
      authErrors.forEach(m => console.log(`[${m.type}] ${m.text}`));
    }

    const allErrors = consoleMessages.filter(m => m.type === 'error');
    if (allErrors.length > 0) {
      console.log('\n=== All console errors ===');
      allErrors.forEach(m => console.log(m.text));
    }

    // Check page content
    const bodyText = await page.locator('body').textContent();
    console.log(`\nPage has content: ${(bodyText?.length || 0) > 100 ? 'Yes' : 'No/Minimal'}`);

    // Take a screenshot
    await page.screenshot({ path: 'e2e/debug-refresh-screenshot.png', fullPage: true });
    console.log('Screenshot saved to e2e/debug-refresh-screenshot.png');

    // If refresh count > 2, we have a problem
    if (refreshCount > 2) {
      console.log('\n!!! REFRESH LOOP DETECTED !!!');
      console.log('Navigation events:');
      navigationEvents.forEach(e => console.log(`  ${e}`));
    }

    expect(refreshCount).toBeLessThanOrEqual(2);
  });

  test('should check auth configuration', async ({ page }) => {
    console.log('=== Checking Auth Configuration ===');

    // Try to access the app and see what happens
    const response = await page.goto('http://localhost:4200', { waitUntil: 'commit' });
    console.log(`Initial response status: ${response?.status()}`);
    console.log(`Initial response URL: ${response?.url()}`);

    // Check if we're redirected to Keycloak
    const currentUrl = page.url();
    console.log(`Current URL after navigation: ${currentUrl}`);

    if (currentUrl.includes('keycloak') || currentUrl.includes('auth')) {
      console.log('Redirected to Keycloak login page');

      // Check if the login form is visible
      const loginForm = page.locator('#kc-form-login, form[action*="authenticate"]');
      if (await loginForm.isVisible({ timeout: 5000 }).catch(() => false)) {
        console.log('Login form is visible - auth redirect working correctly');

        // Try to login
        console.log('Attempting login with admin/admin...');
        await page.fill('#username', 'admin');
        await page.fill('#password', 'admin');
        await page.click('#kc-login');

        await page.waitForTimeout(3000);
        console.log(`URL after login attempt: ${page.url()}`);
      }
    } else {
      console.log('No redirect to Keycloak - checking if auth is disabled or already logged in');

      // Check if app content is visible
      const appRoot = page.locator('app-root');
      const isAppVisible = await appRoot.isVisible({ timeout: 5000 }).catch(() => false);
      console.log(`App root visible: ${isAppVisible}`);
    }

    await page.screenshot({ path: 'e2e/debug-auth-screenshot.png', fullPage: true });
  });
});
