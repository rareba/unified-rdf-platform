import { test, expect } from '@playwright/test';

test.describe('Debug Keycloak Refresh Issue', () => {
  test('should login via Keycloak and observe post-login behavior', async ({ page }) => {
    let refreshCount = 0;
    const navigationEvents: string[] = [];
    const authRequests: string[] = [];

    // Track all navigations
    page.on('framenavigated', (frame) => {
      if (frame === page.mainFrame()) {
        refreshCount++;
        const url = frame.url();
        navigationEvents.push(`[${refreshCount}] ${url}`);
        console.log(`[NAV ${refreshCount}] ${url}`);
      }
    });

    // Track auth-related requests
    page.on('request', (request) => {
      const url = request.url();
      if (url.includes('keycloak') || url.includes('token') || url.includes('auth') || url.includes('session')) {
        authRequests.push(`${request.method()} ${url}`);
        console.log(`[AUTH REQ] ${request.method()} ${url}`);
      }
    });

    // Track console errors
    page.on('console', (msg) => {
      if (msg.type() === 'error' || msg.type() === 'warning') {
        console.log(`[CONSOLE ${msg.type().toUpperCase()}] ${msg.text()}`);
      }
    });

    page.on('pageerror', (error) => {
      console.log(`[PAGE ERROR] ${error.message}`);
    });

    console.log('=== Step 1: Navigate to app (expect redirect to Keycloak) ===');

    // Use the production environment URL which has auth enabled
    // First, let's check if we need to go through Keycloak
    await page.goto('http://localhost:4200', { waitUntil: 'networkidle', timeout: 30000 });

    const currentUrl = page.url();
    console.log(`Current URL: ${currentUrl}`);

    if (currentUrl.includes('keycloak') || currentUrl.includes('/auth/')) {
      console.log('=== Step 2: Login via Keycloak ===');

      // Wait for login form
      await page.waitForSelector('#username, input[name="username"]', { timeout: 10000 });

      // Fill credentials
      await page.fill('#username, input[name="username"]', 'admin');
      await page.fill('#password, input[name="password"]', 'admin');

      console.log('Submitting login form...');
      await page.click('#kc-login, input[type="submit"]');

      // Wait for redirect back to app
      await page.waitForURL(/localhost:4200/, { timeout: 15000 });

      console.log(`=== Step 3: Post-login URL: ${page.url()} ===`);
    } else {
      console.log('Auth seems to be disabled or already logged in');
      console.log('Checking localStorage for tokens...');

      const tokens = await page.evaluate(() => {
        const keys = Object.keys(localStorage).filter(k =>
          k.includes('token') || k.includes('auth') || k.includes('keycloak')
        );
        return keys.map(k => ({ key: k, hasValue: !!localStorage.getItem(k) }));
      });
      console.log('Token-related localStorage keys:', JSON.stringify(tokens));
    }

    console.log('=== Step 4: Observing for 30 seconds post-login ===');
    const startNavCount = refreshCount;

    // Observe for 30 seconds
    for (let i = 0; i < 6; i++) {
      await page.waitForTimeout(5000);
      console.log(`  ${(i + 1) * 5}s: URL=${page.url()}, navCount=${refreshCount}`);

      // Take periodic screenshots
      if (i === 0 || i === 2 || i === 5) {
        await page.screenshot({ path: `e2e/debug-keycloak-${i * 5}s.png` });
      }
    }

    const postLoginNavigations = refreshCount - startNavCount;
    console.log(`\n=== Summary ===`);
    console.log(`Total navigations: ${refreshCount}`);
    console.log(`Post-login navigations: ${postLoginNavigations}`);
    console.log(`Final URL: ${page.url()}`);

    console.log('\nNavigation history:');
    navigationEvents.forEach(e => console.log(`  ${e}`));

    console.log('\nAuth requests:');
    authRequests.forEach(r => console.log(`  ${r}`));

    // Check for refresh loop
    if (postLoginNavigations > 3) {
      console.log('\n!!! REFRESH LOOP DETECTED AFTER LOGIN !!!');
    }

    // The test should pass if there's no excessive refresh
    expect(postLoginNavigations).toBeLessThanOrEqual(3);
  });

  test('should check auth service implementation', async ({ page }) => {
    // Inject script to monitor Angular services
    await page.goto('http://localhost:4200');
    await page.waitForTimeout(2000);

    // Check what's in localStorage related to auth
    const storageData = await page.evaluate(() => {
      const data: Record<string, string | null> = {};
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        if (key) {
          data[key] = localStorage.getItem(key)?.substring(0, 100) + '...';
        }
      }
      return data;
    });

    console.log('LocalStorage contents:');
    Object.entries(storageData).forEach(([k, v]) => {
      console.log(`  ${k}: ${v}`);
    });

    // Check sessionStorage too
    const sessionData = await page.evaluate(() => {
      const data: Record<string, string | null> = {};
      for (let i = 0; i < sessionStorage.length; i++) {
        const key = sessionStorage.key(i);
        if (key) {
          data[key] = sessionStorage.getItem(key)?.substring(0, 100) + '...';
        }
      }
      return data;
    });

    console.log('\nSessionStorage contents:');
    Object.entries(sessionData).forEach(([k, v]) => {
      console.log(`  ${k}: ${v}`);
    });

    // Check cookies
    const cookies = await page.context().cookies();
    console.log('\nCookies:');
    cookies.forEach(c => {
      console.log(`  ${c.name}: ${c.value.substring(0, 50)}... (domain: ${c.domain})`);
    });
  });
});
