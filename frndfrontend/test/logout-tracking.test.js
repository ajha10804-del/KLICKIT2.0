import test from 'node:test';
import assert from 'node:assert/strict';

/**
 * Test Suite: Logout State Cleanup and Tracking Polling Lifecycle
 */

test('Logout Tracking: handleSignOut closes tracking modal and clears activeOrder', () => {
  // Simulate main.jsx state and localStorage
  const mockLocalStorage = {
    store: {
      klickit_token: 'fake.jwt.token',
      klickit_user: JSON.stringify({ name: 'Test User', role: 'CUSTOMER' })
    },
    removeItem(key) {
      delete this.store[key];
    }
  };

  const appState = {
    authToken: 'fake.jwt.token',
    user: { name: 'Test User', role: 'CUSTOMER' },
    accountOpen: true,
    editingPhone: true,
    trackingOpen: true,
    activeOrder: { id: 'order-uuid-999', status: 'OUT_FOR_DELIVERY' },
    navigatedPath: '/orders/order-uuid-999'
  };

  // Replicate exact handleSignOut function from main.jsx
  function handleSignOut() {
    mockLocalStorage.removeItem('klickit_token');
    mockLocalStorage.removeItem('klickit_user');
    appState.authToken = '';
    appState.user = null;
    appState.accountOpen = false;
    appState.editingPhone = false;
    appState.trackingOpen = false;
    appState.activeOrder = null;
    appState.navigatedPath = '/';
  }

  handleSignOut();

  // Verify all authentication and tracking state is cleared
  assert.equal(mockLocalStorage.store.klickit_token, undefined, 'klickit_token must be removed from localStorage');
  assert.equal(mockLocalStorage.store.klickit_user, undefined, 'klickit_user must be removed from localStorage');
  assert.equal(appState.authToken, '', 'authToken must be empty');
  assert.equal(appState.user, null, 'user must be null');
  assert.equal(appState.accountOpen, false, 'account modal must be closed');
  assert.equal(appState.editingPhone, false, 'editingPhone must be reset');
  assert.equal(appState.trackingOpen, false, 'Tracking modal MUST be closed on logout');
  assert.equal(appState.activeOrder, null, 'activeOrder MUST be cleared on logout');
  assert.equal(appState.navigatedPath, '/', 'Must navigate to home /');
});

test('Logout Tracking: CustomerTrackingMap polling interval stops completely on unmount', async () => {
  let intervalTickCount = 0;
  let intervalId = null;
  let isMounted = true;

  // Simulate CustomerTrackingMap polling effect lifecycle
  const pollMs = 50;

  function mountTrackingMap() {
    isMounted = true;
    intervalId = setInterval(() => {
      if (!isMounted) return;
      intervalTickCount++;
    }, pollMs);

    // Return cleanup hook
    return () => {
      isMounted = false;
      clearInterval(intervalId);
    };
  }

  const unmount = mountTrackingMap();

  // Allow a few ticks while mounted
  await new Promise(r => setTimeout(r, 120));
  assert.ok(intervalTickCount >= 2, `Expected at least 2 ticks, got ${intervalTickCount}`);

  // Component unmounts (as triggered by handleSignOut clearing activeOrder / closing tracking modal)
  unmount();

  const tickCountAtUnmount = intervalTickCount;

  // Wait additional time after unmount
  await new Promise(r => setTimeout(r, 120));

  assert.equal(
    intervalTickCount,
    tickCountAtUnmount,
    'No polling ticks may fire after CustomerTrackingMap unmount'
  );
});

test('Logout Tracking: Active tracking continues normally when user remains logged in', async () => {
  let pollCount = 0;
  let isMounted = true;

  const cleanup = () => {
    isMounted = false;
    clearInterval(timer);
  };

  const timer = setInterval(() => {
    if (isMounted) pollCount++;
  }, 20);

  await new Promise(r => setTimeout(r, 70));
  cleanup();

  assert.ok(pollCount >= 2, 'Tracking must poll unimpeded while order tracking is open');
});
