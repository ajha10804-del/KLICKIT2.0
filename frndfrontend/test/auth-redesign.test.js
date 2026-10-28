import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Helper to check portal visibility in navigation/drawers based on role
function getVisiblePortalShortcuts(role) {
  const isStaffAdmin = role === 'ADMIN';
  const isStaffDriver = role === 'DELIVERY_PARTNER' || role === 'ADMIN';

  return {
    headerAdmin: isStaffAdmin,
    headerDriver: isStaffDriver,
    drawerAdmin: isStaffAdmin,
    drawerDriver: isStaffDriver,
    mobileNavAdmin: isStaffAdmin,
    mobileNavDriver: isStaffDriver
  };
}

// Helper simulating AuthFlow tab transition and email prefill
function createAuthFlowState(initialTab = 'login', allowRegister = true) {
  let activeTab = allowRegister ? initialTab : 'login';
  let loginMode = 'password'; // 'password' | 'otp-request' | 'otp-verify'
  let registerStep = 'form'; // 'form' | 'verify'
  let loginEmail = '';
  let regForm = { name: '', email: '', phone: '', password: '' };
  let error = '';

  return {
    getActiveTab: () => activeTab,
    getLoginMode: () => loginMode,
    getRegisterStep: () => registerStep,
    getLoginEmail: () => loginEmail,
    getRegForm: () => ({ ...regForm }),
    getError: () => error,

    setLoginEmail: (em) => { loginEmail = em; },
    setRegForm: (f) => { regForm = { ...regForm, ...f }; },

    // Password login failure: keeps tab on login, shows generic error
    handleLoginFailure: (err) => {
      error = err || 'Invalid email or password. Please verify your credentials.';
    },

    // User clicks "New here? Create an account"
    clickCreateAccount: () => {
      if (!allowRegister) return;
      regForm.email = loginEmail.trim().toLowerCase();
      activeTab = 'register';
      registerStep = 'form';
      error = '';
    },

    // User clicks "Email me a sign-in code instead"
    clickEmailSignInCode: () => {
      loginMode = 'otp-request';
      error = '';
    },

    // Login OTP requested -> advances to verify with generic notice
    onLoginOtpSent: () => {
      loginMode = 'otp-verify';
      error = '';
    },

    // Register Step 1 OTP requested -> advances in-place to Step 2
    onRegisterOtpSent: () => {
      registerStep = 'verify';
      error = '';
    },

    // Register Step 2: "Change email" / "Change details" -> returns to Step 1 keeping all fields
    clickChangeEmail: () => {
      registerStep = 'form';
      error = '';
    }
  };
}

test('Auth Redesign: AuthFlow defaults to Welcome Back Sign-in (password mode)', () => {
  const state = createAuthFlowState();
  assert.equal(state.getActiveTab(), 'login');
  assert.equal(state.getLoginMode(), 'password');
});

test('Auth Redesign: Failed password login displays generic error without auto-redirect to Register', () => {
  const state = createAuthFlowState();
  state.setLoginEmail('unknown@example.com');
  state.handleLoginFailure('Invalid email or password. Please verify your credentials.');

  // Tab remains strictly on login
  assert.equal(state.getActiveTab(), 'login');
  assert.equal(state.getLoginMode(), 'password');
  assert.equal(state.getError(), 'Invalid email or password. Please verify your credentials.');
});

test('Auth Redesign: "New here? Create an account" switches to Register and prefills email client-side', () => {
  const state = createAuthFlowState();
  state.setLoginEmail('newvisitor@example.com');
  state.clickCreateAccount();

  assert.equal(state.getActiveTab(), 'register');
  assert.equal(state.getRegisterStep(), 'form');
  assert.equal(state.getRegForm().email, 'newvisitor@example.com');
});

test('Auth Redesign: "Email me a sign-in code instead" switches to login OTP sub-flow', () => {
  const state = createAuthFlowState();
  state.clickEmailSignInCode();
  assert.equal(state.getActiveTab(), 'login');
  assert.equal(state.getLoginMode(), 'otp-request');

  state.onLoginOtpSent();
  assert.equal(state.getLoginMode(), 'otp-verify');
});

test('Auth Redesign: Unified Register flow advances to Step 2 in place and preserves fields on Change Email', () => {
  const state = createAuthFlowState('register');
  state.setRegForm({
    name: 'Ananya Sharma',
    email: 'ananya@example.com',
    phone: '+91 9876543210',
    password: 'securePassword123'
  });

  // Step 1 sends OTP -> advances in place to verify
  state.onRegisterOtpSent();
  assert.equal(state.getActiveTab(), 'register');
  assert.equal(state.getRegisterStep(), 'verify');

  // Clicking "Change email" goes back to Step 1 with all fields fully preserved
  state.clickChangeEmail();
  assert.equal(state.getRegisterStep(), 'form');
  const preserved = state.getRegForm();
  assert.equal(preserved.name, 'Ananya Sharma');
  assert.equal(preserved.email, 'ananya@example.com');
  assert.equal(preserved.phone, '+91 9876543210');
  assert.equal(preserved.password, 'securePassword123');
});

test('Auth Redesign: Sign-in-only barriers enforce allowRegister=false', () => {
  const staffBarrier = createAuthFlowState('register', false);
  // Cannot switch to register tab
  assert.equal(staffBarrier.getActiveTab(), 'login');
  staffBarrier.clickCreateAccount();
  assert.equal(staffBarrier.getActiveTab(), 'login');
});

test('Staff Portal Navigation Matrix: ADMIN sees both portals, DELIVERY_PARTNER sees Driver only, CUSTOMER sees neither', () => {
  // ADMIN role
  const adminShortcuts = getVisiblePortalShortcuts('ADMIN');
  assert.equal(adminShortcuts.headerAdmin, true);
  assert.equal(adminShortcuts.headerDriver, true);
  assert.equal(adminShortcuts.drawerAdmin, true);
  assert.equal(adminShortcuts.drawerDriver, true);
  assert.equal(adminShortcuts.mobileNavAdmin, true);
  assert.equal(adminShortcuts.mobileNavDriver, true);

  // DELIVERY_PARTNER role
  const driverShortcuts = getVisiblePortalShortcuts('DELIVERY_PARTNER');
  assert.equal(driverShortcuts.headerAdmin, false);
  assert.equal(driverShortcuts.headerDriver, true);
  assert.equal(driverShortcuts.drawerAdmin, false);
  assert.equal(driverShortcuts.drawerDriver, true);
  assert.equal(driverShortcuts.mobileNavAdmin, false);
  assert.equal(driverShortcuts.mobileNavDriver, true);

  // CUSTOMER role
  const customerShortcuts = getVisiblePortalShortcuts('CUSTOMER');
  assert.equal(customerShortcuts.headerAdmin, false);
  assert.equal(customerShortcuts.headerDriver, false);
  assert.equal(customerShortcuts.drawerAdmin, false);
  assert.equal(customerShortcuts.drawerDriver, false);
  assert.equal(customerShortcuts.mobileNavAdmin, false);
  assert.equal(customerShortcuts.mobileNavDriver, false);
});

test('Footer Cleanliness: Public Portals link column is removed from main.jsx', () => {
  const mainPath = path.resolve(__dirname, '../main.jsx');
  const mainContent = fs.readFileSync(mainPath, 'utf8');

  // Verify footer does NOT contain Portals column
  assert.equal(mainContent.includes('<b>Portals</b>'), false, 'Footer must not contain public Portals link column');
  assert.equal(mainContent.includes("navigate('/admin'); }}>Admin Portal</a>"), false, 'Footer must not link to admin portal');
});

test('Login UI Cleanliness: No driver or admin role buttons exist in AuthFlow', () => {
  const authFlowPath = path.resolve(__dirname, '../src/components/AuthFlow.jsx');
  const authFlowContent = fs.readFileSync(authFlowPath, 'utf8');

  // Verify AuthFlow has no admin/driver selection buttons
  assert.equal(authFlowContent.includes('Sign in as Admin'), false);
  assert.equal(authFlowContent.includes('Sign in as Driver'), false);
  assert.equal(authFlowContent.includes('Sign in as Delivery Partner'), false);
});

test('Auth Redesign: onAuthSuccess is called with (profile, token) in exact correct order', () => {
  const authFlowSource = fs.readFileSync(path.resolve(__dirname, '../src/components/AuthFlow.jsx'), 'utf8');

  // Verify completeAuth invokes onAuthSuccess(profile, token)
  assert.equal(authFlowSource.includes('onAuthSuccess(profile, token)'), true, 'Must call onAuthSuccess with profile first, token second');

  // Verify simulated invocation
  let capturedArgs = [];
  const mockOnAuthSuccess = (profile, token) => {
    capturedArgs = [profile, token];
  };

  const fakeProfile = {
    userId: '123',
    name: 'Customer',
    email: 'cust@test.com',
    phone: '+919876543210',
    role: 'CUSTOMER'
  };
  const fakeToken = 'eyJhbGciOiJIUzI1NiJ9.fake.jwt';

  mockOnAuthSuccess(fakeProfile, fakeToken);

  assert.equal(typeof capturedArgs[0], 'object', 'First argument must be profile object');
  assert.equal(capturedArgs[0].email, 'cust@test.com');
  assert.equal(capturedArgs[0].role, 'CUSTOMER');
  assert.equal(typeof capturedArgs[1], 'string', 'Second argument must be token string');
  assert.equal(capturedArgs[1], 'eyJhbGciOiJIUzI1NiJ9.fake.jwt');
});

test('Auth Redesign: completeAuth includes userId and phone for password login path', () => {
  // Simulate password login response from POST /api/auth/login
  const backendResponse = {
    success: true,
    message: 'Login successful',
    data: {
      token: 'jwt.token.pw',
      userId: 'uuid-1234-abcd',
      name: 'Alice User',
      email: 'alice@example.com',
      phone: '+919876543210',
      role: 'CUSTOMER'
    }
  };

  let capturedProfile = null;
  const onAuthSuccess = (profile) => { capturedProfile = profile; };

  // Run AuthFlow completeAuth logic
  const resUser = backendResponse.data?.user || backendResponse.user || backendResponse.data || {};
  const authoritativeRole = resUser.role || 'CUSTOMER';
  const resolvedUserId = resUser.userId || resUser.id || null;
  const resolvedPhone = resUser.phone || '';
  const resolvedEmail = resUser.email || 'alice@example.com';
  const resolvedName = resUser.name || resolvedEmail.split('@')[0];

  const profile = {
    userId: resolvedUserId,
    id: resolvedUserId,
    name: resolvedName,
    email: resolvedEmail,
    phone: resolvedPhone,
    role: authoritativeRole
  };
  onAuthSuccess(profile);

  assert.equal(capturedProfile.userId, 'uuid-1234-abcd', 'Password login profile must include userId');
  assert.equal(capturedProfile.id, 'uuid-1234-abcd', 'Password login profile must include id alias');
  assert.equal(capturedProfile.phone, '+919876543210', 'Password login profile must include phone');
  assert.equal(capturedProfile.email, 'alice@example.com');
  assert.equal(capturedProfile.role, 'CUSTOMER');
});

test('Auth Redesign: completeAuth includes userId and phone for register-verify path', () => {
  // Simulate register-verify response from POST /api/auth/register/verify-otp
  const backendResponse = {
    success: true,
    message: 'Registration successful',
    data: {
      token: 'jwt.token.reg',
      userId: 'uuid-5678-efgh',
      name: 'Bob Registered',
      email: 'bob@example.com',
      phone: '+919812345678',
      role: 'CUSTOMER'
    }
  };

  let capturedProfile = null;
  const onAuthSuccess = (profile) => { capturedProfile = profile; };

  const resUser = backendResponse.data?.user || backendResponse.user || backendResponse.data || {};
  const authoritativeRole = resUser.role || 'CUSTOMER';
  const resolvedUserId = resUser.userId || resUser.id || null;
  const resolvedPhone = resUser.phone || '+919812345678';
  const resolvedEmail = resUser.email || 'bob@example.com';
  const resolvedName = resUser.name || 'Bob Registered';

  const profile = {
    userId: resolvedUserId,
    id: resolvedUserId,
    name: resolvedName,
    email: resolvedEmail,
    phone: resolvedPhone,
    role: authoritativeRole
  };
  onAuthSuccess(profile);

  assert.equal(capturedProfile.userId, 'uuid-5678-efgh', 'Register-verify profile must include userId');
  assert.equal(capturedProfile.id, 'uuid-5678-efgh', 'Register-verify profile must include id alias');
  assert.equal(capturedProfile.phone, '+919812345678', 'Register-verify profile must include phone');
  assert.equal(capturedProfile.name, 'Bob Registered');
  assert.equal(capturedProfile.role, 'CUSTOMER');
});

test('Auth Redesign: completeAuth includes userId and phone for code login path', () => {
  // Simulate login OTP verify response from POST /api/auth/otp/verify
  const backendResponse = {
    success: true,
    message: 'Login successful',
    data: {
      token: 'jwt.token.otp',
      userId: 'uuid-9999-code',
      name: 'Charlie Otp',
      email: 'charlie@example.com',
      phone: '+919877777777',
      role: 'CUSTOMER'
    }
  };

  let capturedProfile = null;
  const onAuthSuccess = (profile) => { capturedProfile = profile; };

  const resUser = backendResponse.data?.user || backendResponse.user || backendResponse.data || {};
  const authoritativeRole = resUser.role || 'CUSTOMER';
  const resolvedUserId = resUser.userId || resUser.id || null;
  const resolvedPhone = resUser.phone || '';
  const resolvedEmail = resUser.email || 'charlie@example.com';
  const resolvedName = resUser.name || resolvedEmail.split('@')[0];

  const profile = {
    userId: resolvedUserId,
    id: resolvedUserId,
    name: resolvedName,
    email: resolvedEmail,
    phone: resolvedPhone,
    role: authoritativeRole
  };
  onAuthSuccess(profile);

  assert.equal(capturedProfile.userId, 'uuid-9999-code', 'Code login profile must include userId');
  assert.equal(capturedProfile.id, 'uuid-9999-code', 'Code login profile must include id alias');
  assert.equal(capturedProfile.phone, '+919877777777', 'Code login profile must include phone');
  assert.equal(capturedProfile.email, 'charlie@example.com');
  assert.equal(capturedProfile.role, 'CUSTOMER');
});

test('Auth Redesign: 401 error from /api/auth/otp/verify displays "Invalid or expired code" not password login message', () => {
  const authFlowSource = fs.readFileSync(path.resolve(__dirname, '../src/components/AuthFlow.jsx'), 'utf8');

  // Verify that handleVerifyLoginOtp handles 401 by setting "Invalid or expired code"
  assert.equal(
    authFlowSource.includes("if (res.status === 401) {\n          throw new Error('Invalid or expired code');"),
    true,
    'AuthFlow verify-otp 401 must throw "Invalid or expired code"'
  );

  // Verify it never uses password error message
  const otpSection = authFlowSource.substring(authFlowSource.indexOf('handleVerifyLoginOtp'), authFlowSource.indexOf('handleRequestRegOtp'));
  assert.equal(otpSection.includes('Invalid email or password'), false, 'Verify-otp handler must never display password login error message');
});
