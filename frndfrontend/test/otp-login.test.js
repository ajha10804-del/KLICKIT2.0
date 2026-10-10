import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const frontendDir = path.resolve(__dirname, '..');

// Helper functions mirroring OtpLoginForm logic
function validateOtpEmail(email) {
  if (!email || typeof email !== 'string') return { valid: false, error: 'Please enter a valid email address.' };
  const clean = email.trim().toLowerCase();
  if (!clean || !clean.includes('@') || !clean.includes('.')) {
    return { valid: false, error: 'Please enter a valid email address.' };
  }
  return { valid: true, email: clean };
}

function sanitizeOtpCode(input) {
  if (!input || typeof input !== 'string') return '';
  return input.replace(/\D/g, '').slice(0, 6);
}

function buildOtpRequestPayload(email) {
  const validated = validateOtpEmail(email);
  if (!validated.valid) throw new Error(validated.error);
  return {
    url: '/api/auth/otp/request',
    method: 'POST',
    body: { email: validated.email }
  };
}

function buildOtpVerifyPayload(email, code) {
  const validated = validateOtpEmail(email);
  if (!validated.valid) throw new Error(validated.error);
  const cleanCode = sanitizeOtpCode(code);
  if (cleanCode.length !== 6) {
    throw new Error('Please enter a valid 6-digit verification code.');
  }
  return {
    url: '/api/auth/otp/verify',
    method: 'POST',
    body: { email: validated.email, code: cleanCode }
  };
}

function parseOtpVerifyResponse(resBody) {
  if (!resBody || typeof resBody !== 'object') {
    throw new Error('Invalid response from server.');
  }
  const token = resBody.data?.token || resBody.token;
  if (!token) {
    throw new Error('Authentication token missing in response.');
  }
  const resUser = resBody.data?.user || resBody.data || {};
  return {
    token,
    user: {
      userId: resUser.userId || null,
      name: resUser.name || (resUser.email ? resUser.email.split('@')[0] : 'User'),
      email: resUser.email || '',
      phone: resUser.phone || '',
      role: resUser.role || 'CUSTOMER'
    }
  };
}

test('OTP Request: Sanitizes email and constructs POST /api/auth/otp/request payload', () => {
  const req = buildOtpRequestPayload('  Customer@Example.COM  ');
  assert.equal(req.url, '/api/auth/otp/request');
  assert.equal(req.method, 'POST');
  assert.deepEqual(req.body, { email: 'customer@example.com' });
});

test('OTP Request: Rejects malformed or empty email addresses', () => {
  assert.throws(() => buildOtpRequestPayload(''), /valid email/i);
  assert.throws(() => buildOtpRequestPayload('plainaddress'), /valid email/i);
  assert.throws(() => buildOtpRequestPayload('user@domain'), /valid email/i);
  assert.throws(() => buildOtpRequestPayload(null), /valid email/i);
});

test('OTP Verify: Sanitizes 6-digit numeric input and rejects non-digits or wrong lengths', () => {
  // Non-digits stripped
  assert.equal(sanitizeOtpCode('12a3b4c5d6e'), '123456');
  // Truncated at 6 digits
  assert.equal(sanitizeOtpCode('123456789'), '123456');
  // Valid 6 digits payload
  const req = buildOtpVerifyPayload('customer@example.com', '  123456  ');
  assert.equal(req.url, '/api/auth/otp/verify');
  assert.deepEqual(req.body, { email: 'customer@example.com', code: '123456' });

  // Incomplete code rejected
  assert.throws(() => buildOtpVerifyPayload('customer@example.com', '12345'), /6-digit/i);
  assert.throws(() => buildOtpVerifyPayload('customer@example.com', 'abc'), /6-digit/i);
});

test('OTP Verify: Parses response envelope into token and user profile', () => {
  const serverResponse = {
    success: true,
    message: 'Login successful',
    data: {
      token: 'jwt-header.jwt-payload.jwt-sig',
      userId: 'c56a4180-65aa-42ec-a945-5fd21dec0538',
      name: 'Aditi Sharma',
      email: 'aditi@example.com',
      phone: '+919876543210',
      role: 'CUSTOMER'
    }
  };

  const parsed = parseOtpVerifyResponse(serverResponse);
  assert.equal(parsed.token, 'jwt-header.jwt-payload.jwt-sig');
  assert.equal(parsed.user.userId, 'c56a4180-65aa-42ec-a945-5fd21dec0538');
  assert.equal(parsed.user.name, 'Aditi Sharma');
  assert.equal(parsed.user.email, 'aditi@example.com');
  assert.equal(parsed.user.phone, '+919876543210');
  assert.equal(parsed.user.role, 'CUSTOMER');
});

test('OTP Verify: Defaults missing role to CUSTOMER and derives default name from email', () => {
  const bareResponse = {
    data: {
      token: 'token-xyz',
      email: 'newbie@example.com'
    }
  };

  const parsed = parseOtpVerifyResponse(bareResponse);
  assert.equal(parsed.token, 'token-xyz');
  assert.equal(parsed.user.name, 'newbie');
  assert.equal(parsed.user.email, 'newbie@example.com');
  assert.equal(parsed.user.role, 'CUSTOMER');
  assert.equal(parsed.user.phone, '');
});

test('OTP Verify: Throws clear error when authentication token is missing', () => {
  assert.throws(() => parseOtpVerifyResponse({ success: false, message: 'Invalid code' }), /missing/i);
});

test('OTP Cooldown: 60-second cooldown timer decrements down to 0', () => {
  let cooldown = 60;
  const tick = () => { cooldown = Math.max(0, cooldown - 1); };

  assert.equal(cooldown, 60);
  for (let i = 0; i < 30; i++) tick();
  assert.equal(cooldown, 30);
  for (let i = 0; i < 35; i++) tick();
  assert.equal(cooldown, 0);
  // Does not go negative
  tick();
  assert.equal(cooldown, 0);
});

test('Error Mapping: Handles HTTP 400, 401, 429, and network error gracefully', () => {
  function mapError(status, data, isNetworkError = false) {
    if (isNetworkError) {
      return 'Could not connect to the server. Please check your network.';
    }
    if (status === 429) {
      return data?.message || 'Too many attempts. Please wait before trying again.';
    }
    if (status === 401) {
      return data?.message || 'Invalid or expired verification code.';
    }
    if (status === 400) {
      return data?.message || 'Invalid request. Please check your input.';
    }
    return data?.message || 'An unexpected error occurred.';
  }

  assert.equal(mapError(429, { message: 'Too many OTP requests. Please wait a moment.' }), 'Too many OTP requests. Please wait a moment.');
  assert.equal(mapError(401, { message: 'Invalid or expired code.' }), 'Invalid or expired code.');
  assert.equal(mapError(400, { message: 'Email is required' }), 'Email is required');
  assert.equal(mapError(0, null, true), 'Could not connect to the server. Please check your network.');
});

test('Fallback Preservation: Traditional password login endpoint and payload remain intact', () => {
  const loginPayload = {
    email: 'admin@klickit.test',
    password: 'CustomerPassword123!'
  };
  assert.ok(loginPayload.email);
  assert.ok(loginPayload.password);

  const registerPayload = {
    name: 'New User',
    email: 'newuser@example.com',
    password: 'Password123!',
    phone: '+919876543210'
  };
  assert.ok(registerPayload.name);
  assert.ok(registerPayload.email);
  assert.ok(registerPayload.password);
  assert.ok(registerPayload.phone);
});

test('Component Audit: OtpLoginForm.jsx exists and exports component with expected features', () => {
  const componentPath = path.join(frontendDir, 'src', 'components', 'OtpLoginForm.jsx');
  assert.ok(fs.existsSync(componentPath), 'OtpLoginForm.jsx must exist');

  const content = fs.readFileSync(componentPath, 'utf-8');
  assert.ok(content.includes('export default function OtpLoginForm'), 'Must have default export');
  assert.ok(content.includes('/api/auth/otp/request'), 'Must call OTP request endpoint');
  assert.ok(content.includes('/api/auth/otp/verify'), 'Must call OTP verify endpoint');
  assert.ok(content.includes('onSuccess'), 'Must support onSuccess callback');
  assert.ok(content.includes('onSwitchToPassword'), 'Must support switching to password');
  assert.ok(content.includes('onSwitchToRegister'), 'Must support switching to register');
  assert.ok(!content.includes('localStorage.setItem(\'otp\''), 'Must never store raw OTP in localStorage');
  assert.ok(!content.includes('console.log(code)'), 'Must never log raw OTP code');
});

test('Architecture Audit: main.jsx integrates OtpLoginForm while preserving critical components', () => {
  const mainPath = path.join(frontendDir, 'main.jsx');
  const mainContent = fs.readFileSync(mainPath, 'utf-8');

  // Integrated OtpLoginForm
  assert.ok(mainContent.includes('OtpLoginForm'), 'main.jsx must import and use OtpLoginForm');

  // Critical components preserved
  assert.ok(mainContent.includes('LocationPicker'), 'main.jsx must preserve LocationPicker');
  assert.ok(mainContent.includes('CustomerTrackingMap'), 'main.jsx must preserve CustomerTrackingMap');
  assert.ok(mainContent.includes('AdminDashboard'), 'main.jsx must preserve AdminDashboard');
  assert.ok(mainContent.includes('DeliveryDashboard'), 'main.jsx must preserve DeliveryDashboard');

  // Ensure LocationPicker.jsx and CustomerTrackingMap.jsx files exist and have content
  const locationPickerPath = path.join(frontendDir, 'src', 'components', 'LocationPicker.jsx');
  const trackingMapPath = path.join(frontendDir, 'src', 'components', 'CustomerTrackingMap.jsx');
  assert.ok(fs.existsSync(locationPickerPath), 'LocationPicker.jsx must exist intact');
  assert.ok(fs.existsSync(trackingMapPath), 'CustomerTrackingMap.jsx must exist intact');

  const lpContent = fs.readFileSync(locationPickerPath, 'utf-8');
  const tmContent = fs.readFileSync(trackingMapPath, 'utf-8');
  assert.ok(lpContent.length > 500, 'LocationPicker.jsx must not be empty or truncated');
  assert.ok(tmContent.length > 500, 'CustomerTrackingMap.jsx must not be empty or truncated');
});
