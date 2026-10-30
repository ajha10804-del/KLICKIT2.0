import test from 'node:test';
import assert from 'node:assert/strict';

const AUTH_TIMEOUT_MS = 30000;
const TIMEOUT_FALLBACK_MESSAGE = 'The server is taking too long or is unavailable. Please try again in a minute.';

/**
 * Harness replicating the exact runtime lifecycle and lock behavior implemented in AuthFlow & OtpLoginForm
 */
class AuthFetchHarness {
  constructor(fetchImpl, options = {}) {
    this.fetchImpl = fetchImpl;
    this.timeoutMs = options.timeoutMs ?? AUTH_TIMEOUT_MS;
    this.isMounted = true;
    this.isSubmitting = false;
    this.activeAbort = null;
    this.loading = false;
    this.error = null;
    this.successPayload = null;
    this.fetchCallCount = 0;
  }

  unmount() {
    this.isMounted = false;
    if (this.activeAbort) {
      this.activeAbort.abort();
      this.activeAbort = null;
    }
  }

  async executeAuthRequest(endpoint, body) {
    // 1. Synchronous submission lock check
    if (this.isSubmitting) return false;
    this.isSubmitting = true;

    this.loading = true;
    this.error = null;

    const controller = new AbortController();
    this.activeAbort = controller;
    let timedOut = false;

    const timeoutId = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, this.timeoutMs);

    try {
      this.fetchCallCount++;
      const res = await this.fetchImpl(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
        signal: controller.signal
      });

      // Safe body parsing (handles non-JSON / HTML / 502 gracefully)
      let data = {};
      try {
        const contentType = res.headers?.get?.('content-type') || '';
        if (contentType.includes('application/json')) {
          data = typeof res.json === 'function' ? await res.json() : {};
        } else if (typeof res.text === 'function') {
          const text = await res.text();
          try { data = JSON.parse(text); } catch { data = {}; }
        }
      } catch {
        data = {};
      }

      if (!res.ok) {
        if (res.status === 401) {
          throw new Error(data.message || 'Invalid or expired code');
        }
        throw new Error(data.message || data.error || (res.status >= 500 ? TIMEOUT_FALLBACK_MESSAGE : 'Request failed'));
      }

      this.successPayload = data;
      return true;
    } catch (err) {
      if (!this.isMounted) return false;

      if (timedOut) {
        this.error = TIMEOUT_FALLBACK_MESSAGE;
        return false;
      }
      if (err.name === 'AbortError') {
        // Normal unmount cancellation suppresses error
        return false;
      }
      if (err.message === 'Failed to fetch') {
        this.error = TIMEOUT_FALLBACK_MESSAGE;
        return false;
      }
      this.error = err.message;
      return false;
    } finally {
      clearTimeout(timeoutId);
      if (this.activeAbort === controller) {
        this.activeAbort = null;
      }
      this.isSubmitting = false;
      if (this.isMounted) {
        this.loading = false;
      }
    }
  }
}

test('Auth Hardening: Synchronous useRef submission lock blocks duplicate in-flight requests', async () => {
  let resolveFirstRequest;
  const slowMockFetch = () => new Promise(resolve => {
    resolveFirstRequest = resolve;
  });

  const harness = new AuthFetchHarness(slowMockFetch);

  // First submission starts
  const p1 = harness.executeAuthRequest('/api/auth/login', { email: 'test@klickit.test' });
  assert.equal(harness.loading, true);
  assert.equal(harness.fetchCallCount, 1);

  // Immediate second submission while first is still unresolved
  const p2 = harness.executeAuthRequest('/api/auth/login', { email: 'test@klickit.test' });

  // Second submission was synchronously rejected by the lock
  assert.equal(await p2, false);
  assert.equal(harness.fetchCallCount, 1, 'Duplicate request must NOT invoke fetch a second time');

  // Complete first request
  resolveFirstRequest({
    ok: true,
    status: 200,
    headers: { get: () => 'application/json' },
    json: async () => ({ success: true, data: { token: 'tok' } })
  });

  assert.equal(await p1, true);
  assert.equal(harness.loading, false, 'Loading state must be restored');
  assert.equal(harness.isSubmitting, false, 'Lock must be released in finally');
});

test('Auth Hardening: 30-second timeout aborts stalled request and displays friendly fallback message', async () => {
  // Mock fetch that hangs forever until aborted by signal
  const hangingFetch = (url, opts) => new Promise((_, reject) => {
    opts.signal.addEventListener('abort', () => {
      const err = new Error('The operation was aborted');
      err.name = 'AbortError';
      reject(err);
    });
  });

  // Use a fast timeout for testing
  const harness = new AuthFetchHarness(hangingFetch, { timeoutMs: 50 });

  const result = await harness.executeAuthRequest('/api/auth/login', { email: 'slow@klickit.test' });

  assert.equal(result, false);
  assert.equal(harness.error, TIMEOUT_FALLBACK_MESSAGE);
  assert.equal(harness.loading, false, 'Loading must be cleared in finally');
  assert.equal(harness.isSubmitting, false, 'Submission lock must be released');
});

test('Auth Hardening: Component unmount suppresses error and state updates', async () => {
  let unmountSignalListener;
  const hangingFetch = (url, opts) => new Promise((_, reject) => {
    unmountSignalListener = () => {
      const err = new Error('The operation was aborted');
      err.name = 'AbortError';
      reject(err);
    };
    opts.signal.addEventListener('abort', unmountSignalListener);
  });

  const harness = new AuthFetchHarness(hangingFetch, { timeoutMs: 5000 });

  const p = harness.executeAuthRequest('/api/auth/login', { email: 'unmount@klickit.test' });

  // Simulate user closing modal while request is in flight
  harness.unmount();

  await p;

  assert.equal(harness.error, null, 'Unmount cancellation must NEVER set an error');
  assert.equal(harness.loading, true, 'No state updates allowed after unmount');
});

test('Auth Hardening: Non-JSON / 502 Bad Gateway response does not crash and restores loading state', async () => {
  const badGatewayFetch = async () => ({
    ok: false,
    status: 502,
    headers: { get: () => 'text/html' },
    text: async () => '<html><body>502 Bad Gateway</body></html>'
  });

  const harness = new AuthFetchHarness(badGatewayFetch);

  const result = await harness.executeAuthRequest('/api/auth/login', { email: 'test@klickit.test' });

  assert.equal(result, false);
  assert.equal(harness.error, TIMEOUT_FALLBACK_MESSAGE);
  assert.equal(harness.loading, false, 'Loading must be restored to false');
  assert.equal(harness.isSubmitting, false, 'Lock must be released');
});

test('Auth Hardening: Preserves specific 401 message "Invalid or expired code" upon OTP failure', async () => {
  const otpFailedFetch = async () => ({
    ok: false,
    status: 401,
    headers: { get: () => 'application/json' },
    json: async () => ({ success: false, message: 'Invalid or expired code' })
  });

  const harness = new AuthFetchHarness(otpFailedFetch);

  const result = await harness.executeAuthRequest('/api/auth/otp/verify', { email: 'test@klickit.test', code: '123456' });

  assert.equal(result, false);
  assert.equal(harness.error, 'Invalid or expired code');
  assert.equal(harness.loading, false);
  assert.equal(harness.isSubmitting, false);
});
