import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');
const backendLogPath = path.join(rootDir, 'logs', 'backend.log');

const API_BASE = 'http://localhost:8080/api';

async function fetchJson(endpoint, options = {}) {
  const url = endpoint.startsWith('http') ? endpoint : `${API_BASE}${endpoint}`;
  const res = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    }
  });
  let body = null;
  const text = await res.text();
  try {
    body = JSON.parse(text);
  } catch {
    body = text;
  }
  return { status: res.status, ok: res.ok, headers: res.headers, body };
}

function extractLatestOtpCode(email) {
  if (!fs.existsSync(backendLogPath)) {
    throw new Error(`Backend log not found at ${backendLogPath}`);
  }
  const content = fs.readFileSync(backendLogPath, 'utf-8');
  const lines = content.split('\n');
  const regex = new RegExp(`Login OTP code for ${email.toLowerCase()}: (\\d{6})`);
  for (let i = lines.length - 1; i >= 0; i--) {
    const match = lines[i].match(regex);
    if (match) {
      return match[1];
    }
  }
  throw new Error(`Could not find OTP code for ${email} in backend.log`);
}

async function runSmokeTests() {
  console.log('================================================================');
  console.log('TASK 6: OTP END-TO-END SMOKE TEST & SESSION VERIFICATION');
  console.log('Target API: ' + API_BASE);
  console.log('Timestamp: ' + new Date().toISOString());
  console.log('================================================================\n');

  const results = [];

  function record(id, name, type, expected, actualStatus, pass, details) {
    results.push({ id, name, type, expected, actualStatus, pass, details });
    const mark = pass ? '✓ PASS' : '✗ FAIL';
    console.log(`[${id}] ${mark} - ${name} (Status: ${actualStatus})`);
    if (details) console.log(`      Details: ${JSON.stringify(details).substring(0, 160)}...`);
  }

  // 1. Runtime Environment & Service Configuration
  console.log('--- 1. Runtime Environment & Actuator Probes ---');
  {
    const res = await fetchJson('/actuator/health');
    const pass = res.status === 200 && res.body?.status === 'UP';
    record('ENV-01', 'Actuator Health Probe is UP', 'Live API', 200, res.status, pass, res.body);
  }

  // 2. Core Happy Path Flow
  console.log('\n--- 2. Core Happy Path Flow (End-to-End) ---');
  const happyEmail = `smoke-happy-${Date.now()}@klickit.test`;
  let customerToken = null;
  let customerUser = null;

  // Step 1: Request OTP
  {
    const res = await fetchJson('/auth/otp/request', {
      method: 'POST',
      body: JSON.stringify({ email: happyEmail })
    });
    const pass = res.status === 200 && res.body?.success === true;
    record('HP-01', 'OTP Request returns 200 OK with generic message', 'Live API', 200, res.status, pass, res.body);
  }

  // Retrieve code from logs
  let otpCode = null;
  try {
    // Small sleep to ensure log flush
    await new Promise(r => setTimeout(r, 200));
    otpCode = extractLatestOtpCode(happyEmail);
    console.log(`      Extracted OTP code from dev log: ${otpCode}`);
    record('HP-02', 'OTP Code captured from backend dev log', 'Runtime Log', '6-digit', 200, /^\d{6}$/.test(otpCode), { otpCode });
  } catch (err) {
    record('HP-02', 'OTP Code captured from backend dev log', 'Runtime Log', '6-digit', 500, false, { error: err.message });
  }

  // Step 2: Verify OTP
  if (otpCode) {
    const res = await fetchJson('/auth/otp/verify', {
      method: 'POST',
      body: JSON.stringify({ email: happyEmail, code: otpCode })
    });
    const pass = res.status === 200 && res.body?.success === true && !!res.body?.data?.token && res.body?.data?.role === 'CUSTOMER';
    customerToken = res.body?.data?.token;
    customerUser = res.body?.data;
    record('HP-03', 'OTP Verification succeeds and returns JWT & CUSTOMER profile', 'Live API', 200, res.status, pass, {
      userId: customerUser?.userId,
      email: customerUser?.email,
      role: customerUser?.role,
      tokenPresent: !!customerToken
    });
  }

  // Step 3: Session Persistence verification
  {
    const simulatedStorage = {
      klickit_token: customerToken,
      klickit_user: JSON.stringify({
        userId: customerUser?.userId,
        name: customerUser?.name,
        email: customerUser?.email,
        phone: customerUser?.phone || '',
        role: customerUser?.role
      })
    };
    const parsedUser = JSON.parse(simulatedStorage.klickit_user);
    const pass = !!simulatedStorage.klickit_token && parsedUser.role === 'CUSTOMER' && parsedUser.email === happyEmail;
    record('HP-04', 'Client session structure matches klickit_token & klickit_user format', 'Client Simulation', 'Valid Storage', 200, pass, parsedUser);
  }

  // 3. Downstream Customer Session & Invariant Verification
  console.log('\n--- 3. Downstream Customer Session & Invariant Verification ---');
  {
    const res = await fetchJson('/orders', {
      headers: { Authorization: `Bearer ${customerToken}` }
    });
    const pass = res.status === 200 && res.body?.success === true && Array.isArray(res.body?.data);
    record('DS-01', 'Authenticated GET /api/orders succeeds with OTP JWT', 'Live API', 200, res.status, pass, {
      orderCount: res.body?.data?.length
    });
  }

  {
    // Cart operations with session
    const res = await fetchJson('/cart/smoke-session-01');
    const pass = res.status === 200 && res.body?.success === true;
    record('DS-02', 'Public cart endpoint /cart/{sessionId} accessible alongside active session', 'Live API', 200, res.status, pass, {
      cartSessionId: res.body?.data?.sessionId
    });
  }

  // 4. Negative, Boundary & Failure Mode Testing
  console.log('\n--- 4. Negative, Boundary & Failure Mode Testing ---');
  const negEmail = `smoke-neg-${Date.now()}@klickit.test`;

  // Request OTP for negative tests
  await fetchJson('/auth/otp/request', {
    method: 'POST',
    body: JSON.stringify({ email: negEmail })
  });
  await new Promise(r => setTimeout(r, 200));
  const validNegCode = extractLatestOtpCode(negEmail);

  // Invalid Code
  {
    const res = await fetchJson('/auth/otp/verify', {
      method: 'POST',
      body: JSON.stringify({ email: negEmail, code: '999999' })
    });
    const pass = res.status === 401 && res.body?.message?.includes('Invalid or expired');
    record('NEG-01', 'Invalid OTP code rejected with 401 Unauthorized', 'Live API', 401, res.status, pass, res.body);
  }

  // Max Attempts Lockout (attempts 2, 3, 4, 5)
  {
    let lastStatus = 0;
    let lastBody = null;
    for (let attempt = 2; attempt <= 5; attempt++) {
      const res = await fetchJson('/auth/otp/verify', {
        method: 'POST',
        body: JSON.stringify({ email: negEmail, code: '000000' })
      });
      lastStatus = res.status;
      lastBody = res.body;
    }
    // Now after 5 failed attempts, even submitting the real code must fail
    const finalRes = await fetchJson('/auth/otp/verify', {
      method: 'POST',
      body: JSON.stringify({ email: negEmail, code: validNegCode })
    });
    const pass = finalRes.status === 401;
    record('NEG-02', 'Max failed attempts (5) locks out code; subsequent valid code rejected', 'Live API', 401, finalRes.status, pass, finalRes.body);
  }

  // Expired / Consumed Code Replay
  {
    const res = await fetchJson('/auth/otp/verify', {
      method: 'POST',
      body: JSON.stringify({ email: happyEmail, code: otpCode })
    });
    const pass = res.status === 401;
    record('NEG-03', 'Replaying already consumed OTP code rejected with 401 Unauthorized', 'Live API', 401, res.status, pass, res.body);
  }

  // Resend Cooldown Throttling
  {
    const throttleEmail = `smoke-throttle-${Date.now()}@klickit.test`;
    const firstReq = await fetchJson('/auth/otp/request', {
      method: 'POST',
      body: JSON.stringify({ email: throttleEmail })
    });
    // Immediately send second request within 60s cooldown
    const secondReq = await fetchJson('/auth/otp/request', {
      method: 'POST',
      body: JSON.stringify({ email: throttleEmail })
    });
    const pass = firstReq.status === 200 && secondReq.status === 429 && secondReq.body?.message?.includes('wait');
    record('NEG-04', 'Immediate resend within 60s cooldown rejected with 429 Too Many Requests', 'Live API', 429, secondReq.status, pass, secondReq.body);
  }

  // Fallback to Password Login
  let adminToken = null;
  {
    const res = await fetchJson('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email: 'admin@klickit.test', password: 'CustomerPassword123!' })
    });
    const pass = res.status === 200 && res.body?.success === true && res.body?.data?.role === 'ADMIN';
    adminToken = res.body?.data?.token;
    record('FB-01', 'Traditional password login succeeds for admin demo credentials', 'Live API', 200, res.status, pass, {
      role: res.body?.data?.role,
      tokenPresent: !!adminToken
    });
  }

  // 5. Role-Based Access Control (RBAC) & Barrier Validation
  console.log('\n--- 5. Role-Based Access Control (RBAC) & Barrier Validation ---');
  {
    // Customer OTP user attempting Admin orders endpoint
    const res = await fetchJson('/admin/orders', {
      headers: { Authorization: `Bearer ${customerToken}` }
    });
    const pass = res.status === 403;
    record('RBAC-01', 'Customer OTP token blocked from /api/admin/orders with 403 Forbidden', 'Live API', 403, res.status, pass, res.body);
  }

  {
    // Customer OTP user attempting Delivery orders endpoint
    const res = await fetchJson('/delivery/orders', {
      headers: { Authorization: `Bearer ${customerToken}` }
    });
    const pass = res.status === 403;
    record('RBAC-02', 'Customer OTP token blocked from /api/delivery/orders with 403 Forbidden', 'Live API', 403, res.status, pass, res.body);
  }

  {
    // Admin user attempting Admin orders endpoint
    const res = await fetchJson('/admin/orders', {
      headers: { Authorization: `Bearer ${adminToken}` }
    });
    const pass = res.status === 200 && res.body?.success === true;
    record('RBAC-03', 'Admin token successfully accesses /api/admin/orders with 200 OK', 'Live API', 200, res.status, pass, {
      totalOrders: res.body?.data?.totalElements ?? res.body?.data?.length
    });
  }

  // 6. Security Leak & Information Exposure Audit
  console.log('\n--- 6. Security Leak & Information Exposure Audit ---');
  {
    // Check request and verify response bodies
    const reqRes = await fetchJson('/auth/otp/request', {
      method: 'POST',
      body: JSON.stringify({ email: `smoke-audit-${Date.now()}@klickit.test` })
    });
    const reqStr = JSON.stringify(reqRes.body);
    const hasRawOtpInReq = /\b\d{6}\b/.test(reqStr);
    const hasStackTrace = reqStr.includes('Exception') || reqStr.includes('at com.klickit');
    const passReq = !hasRawOtpInReq && !hasStackTrace;
    record('SEC-01', 'OTP request response contains no raw OTP code or stack trace', 'Security Audit', 'Zero Leaks', reqRes.status, passReq, { bodySnippet: reqStr });

    // Verify response inspection
    const verRes = await fetchJson('/auth/otp/verify', {
      method: 'POST',
      body: JSON.stringify({ email: 'fake@example.com', code: '123456' })
    });
    const verStr = JSON.stringify(verRes.body);
    const passVer = !verStr.includes('Exception') && !verStr.includes('at com.klickit');
    record('SEC-02', 'OTP verify failure response contains no sensitive server internals', 'Security Audit', 'Zero Leaks', verRes.status, passVer, { bodySnippet: verStr });
  }

  console.log('\n================================================================');
  console.log('SMOKE TEST EXECUTION SUMMARY:');
  const passedCount = results.filter(r => r.pass).length;
  const failedCount = results.filter(r => !r.pass).length;
  console.log(`Total Scenarios: ${results.length}`);
  console.log(`Passed:          ${passedCount}`);
  console.log(`Failed:          ${failedCount}`);
  console.log('================================================================\n');

  if (failedCount > 0) {
    console.error('FAILURES DETECTED:');
    results.filter(r => !r.pass).forEach(f => console.error(` - [${f.id}] ${f.name}`));
    process.exit(1);
  } else {
    console.log('ALL SMOKE TEST SCENARIOS PASSED WITH ZERO REGRESSIONS.');
    process.exit(0);
  }
}

runSmokeTests().catch(err => {
  console.error('Smoke test script fatal error:', err);
  process.exit(1);
});
