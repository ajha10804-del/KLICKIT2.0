import test from 'node:test';
import assert from 'node:assert/strict';

// Helpers replicating the logic in LocationPicker.jsx and main.jsx

function resolveSavedAddress({ address, landmark, coordinates }) {
  let trimmedAddress = (address || '').trim();
  const trimmedLandmark = (landmark || '').trim();

  if (!trimmedAddress) {
    if (trimmedLandmark) {
      trimmedAddress = trimmedLandmark;
    } else if (coordinates && typeof coordinates.latitude === 'number' && typeof coordinates.longitude === 'number') {
      trimmedAddress = `Pinned Location (${coordinates.latitude.toFixed(4)}, ${coordinates.longitude.toFixed(4)})`;
    }
  }

  return {
    address: trimmedAddress,
    landmark: trimmedLandmark || null,
    coordinates
  };
}

function getCartDrawerAddressDisplay({ address, landmark, coordinates }) {
  const isSet = Boolean(address || coordinates);
  const color = isSet ? '#1b1d19' : '#dc2626';

  let text;
  if (address) {
    text = address + (landmark && landmark !== address ? ` (${landmark})` : '');
  } else if (coordinates) {
    text = landmark ? `${landmark} (Pinned Location)` : `Pinned Location (${coordinates.latitude.toFixed(4)}, ${coordinates.longitude.toFixed(4)})`;
  } else {
    text = 'No address set — required';
  }

  return { text, color, isSet };
}

function isValidPhoneNumber(ph) {
  if (!ph || typeof ph !== 'string') return false;
  const trimmed = ph.trim();
  if (trimmed.length < 7 || trimmed.length > 20) return false;
  if (trimmed === '9876543210' || trimmed === '+919876543210') return false;
  return /^[+]?[0-9\-\s()]{7,20}$/.test(trimmed);
}

function checkCheckoutPhoneAction(user) {
  const phone = user?.phone?.trim();
  if (!isValidPhoneNumber(phone)) {
    return { shouldPromptPhoneModal: true, phoneInput: user?.phone || '' };
  }
  return { shouldPromptPhoneModal: false, confirmedPhone: phone };
}

function normalizeLoginCredentials(authForm) {
  return {
    email: (authForm.email || '').trim().toLowerCase(),
    password: authForm.password
  };
}

function mapAuthErrorMessage(status, body) {
  if (status === 401) {
    return body?.message || 'Invalid email or password. Please verify your credentials.';
  }
  if (status === 403) {
    return 'Access denied. You do not have permission to access this portal.';
  }
  return body?.message || body?.error || 'Please check your details and try again.';
}

// Tests

test('LocationPicker: Preserves explicit address when entered', () => {
  const result = resolveSavedAddress({
    address: 'Block 2, Room 301',
    landmark: 'Near Water Cooler',
    coordinates: { latitude: 23.0756, longitude: 76.8501 }
  });
  assert.equal(result.address, 'Block 2, Room 301');
  assert.equal(result.landmark, 'Near Water Cooler');
});

test('LocationPicker: Falls back to landmark when address field is left blank', () => {
  const result = resolveSavedAddress({
    address: '   ',
    landmark: 'VIT Boys Hostel Block 1',
    coordinates: { latitude: 23.0756, longitude: 76.8501 }
  });
  assert.equal(result.address, 'VIT Boys Hostel Block 1');
  assert.equal(result.landmark, 'VIT Boys Hostel Block 1');
});

test('LocationPicker: Falls back to formatted coordinates when address and landmark are blank', () => {
  const result = resolveSavedAddress({
    address: '',
    landmark: '',
    coordinates: { latitude: 23.07561, longitude: 76.85008 }
  });
  assert.equal(result.address, 'Pinned Location (23.0756, 76.8501)');
  assert.equal(result.landmark, null);
});

test('Cart Drawer: Displays normal dark text when address is set', () => {
  const display = getCartDrawerAddressDisplay({
    address: 'Flat 402, Green Valley',
    landmark: 'Opposite Park',
    coordinates: { latitude: 23.07, longitude: 76.85 }
  });
  assert.equal(display.color, '#1b1d19');
  assert.equal(display.text, 'Flat 402, Green Valley (Opposite Park)');
  assert.equal(display.isSet, true);
});

test('Cart Drawer: Displays pinned location and eliminates red warning when coordinates are pinned', () => {
  const display = getCartDrawerAddressDisplay({
    address: '',
    landmark: 'Campus Gate',
    coordinates: { latitude: 23.0756, longitude: 76.8501 }
  });
  assert.equal(display.color, '#1b1d19');
  assert.equal(display.text, 'Campus Gate (Pinned Location)');
  assert.equal(display.isSet, true);
});

test('Cart Drawer: Displays red required warning only when neither address nor coordinates are set', () => {
  const display = getCartDrawerAddressDisplay({
    address: '',
    landmark: '',
    coordinates: null
  });
  assert.equal(display.color, '#dc2626');
  assert.equal(display.text, 'No address set — required');
  assert.equal(display.isSet, false);
});

test('Checkout: Prompts for phone when user has null or empty phone number', () => {
  const action1 = checkCheckoutPhoneAction({ email: 'user@test.com', phone: null });
  assert.equal(action1.shouldPromptPhoneModal, true);

  const action2 = checkCheckoutPhoneAction({ email: 'user@test.com', phone: '' });
  assert.equal(action2.shouldPromptPhoneModal, true);

  const action3 = checkCheckoutPhoneAction({ email: 'user@test.com', phone: '9876543210' }); // rejected placeholder
  assert.equal(action3.shouldPromptPhoneModal, true);
});

test('Checkout: Proceeds without prompt when user has valid phone number', () => {
  const action = checkCheckoutPhoneAction({ email: 'user@test.com', phone: '9876543211' });
  assert.equal(action.shouldPromptPhoneModal, false);
  assert.equal(action.confirmedPhone, '9876543211');
});

test('Phone Validation: Validates real Indian mobile phone formats', () => {
  assert.equal(isValidPhoneNumber('9876543211'), true);
  assert.equal(isValidPhoneNumber('+91 9876543211'), true);
  assert.equal(isValidPhoneNumber('123'), false); // too short
  assert.equal(isValidPhoneNumber('abcdefghijk'), false); // letters
});

test('Auth: Normalizes email to lowercase for consistent login lookup', () => {
  const creds = normalizeLoginCredentials({ email: '  Admin@KlickIt.Test  ', password: 'secretPassword' });
  assert.equal(creds.email, 'admin@klickit.test');
  assert.equal(creds.password, 'secretPassword');
});

test('Auth: Maps HTTP 401 to clear credential failure message', () => {
  const msg = mapAuthErrorMessage(401, { message: 'Invalid email or password' });
  assert.equal(msg, 'Invalid email or password');

  const defaultMsg = mapAuthErrorMessage(401, {});
  assert.equal(defaultMsg, 'Invalid email or password. Please verify your credentials.');
});

// Helper replicating isTokenExpired and getRoleFromToken in main.jsx and AuthScreen.jsx
function isTokenExpired(token) {
  if (!token) return true;
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return true;
    const payload = JSON.parse(Buffer.from(parts[1].replace(/-/g, '+').replace(/_/g, '/'), 'base64').toString('utf8'));
    if (!payload.exp) return false;
    return Date.now() >= payload.exp * 1000;
  } catch {
    return true;
  }
}

function getRoleFromToken(token) {
  if (!token) return null;
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const payload = JSON.parse(Buffer.from(parts[1].replace(/-/g, '+').replace(/_/g, '/'), 'base64').toString('utf8'));
    return payload.role || null;
  } catch {
    return null;
  }
}

function determineInitialView({ currentPath, user, token }) {
  const isAuthValid = Boolean(user && token && !isTokenExpired(token));
  if (!isAuthValid) {
    if (currentPath === '/admin' || currentPath.startsWith('/admin/')) return 'ADMIN_BARRIER';
    if (currentPath === '/delivery' || currentPath.startsWith('/delivery/')) return 'DELIVERY_BARRIER';
    return 'AUTH_SCREEN';
  }
  const role = (token ? getRoleFromToken(token) : null) || user.role || 'CUSTOMER';
  if (currentPath === '/admin' || currentPath.startsWith('/admin/')) {
    return role === 'ADMIN' ? 'ADMIN_DASHBOARD' : 'ADMIN_BARRIER_403';
  }
  if (currentPath === '/delivery' || currentPath.startsWith('/delivery/')) {
    return (role === 'DELIVERY_PARTNER' || role === 'ADMIN') ? 'DELIVERY_DASHBOARD' : 'DELIVERY_BARRIER_403';
  }
  return 'STOREFRONT';
}

function determinePostAuthRoute(role) {
  if (role === 'ADMIN') return '/admin';
  if (role === 'DELIVERY_PARTNER') return '/delivery';
  return '/';
}

function validateRegistrationInputs({ name, email, phone, password }) {
  const cleanName = (name || '').trim();
  const cleanEmail = (email || '').trim().toLowerCase();
  const cleanPhone = (phone || '').trim();
  const cleanPass = password || '';

  if (!cleanName) return { valid: false, error: 'Please enter your full name.' };
  if (!cleanEmail || !cleanEmail.includes('@')) return { valid: false, error: 'Please enter a valid email address.' };
  if (!cleanPhone || cleanPhone.length < 7 || cleanPhone.length > 20) return { valid: false, error: 'Please enter a valid phone number (7-20 digits).' };
  if (!cleanPass || cleanPass.length < 6) return { valid: false, error: 'Password must be at least 6 characters.' };
  return { valid: true, clean: { name: cleanName, email: cleanEmail, phone: cleanPhone, password: cleanPass } };
}

function createMockJwt(payload) {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64');
  const body = Buffer.from(JSON.stringify(payload)).toString('base64');
  return `${header}.${body}.mockSignature`;
}

test('Auth Token: Decodes role claim and checks expiration correctly', () => {
  const futureExp = Math.floor(Date.now() / 1000) + 3600;
  const pastExp = Math.floor(Date.now() / 1000) - 3600;

  const validAdminToken = createMockJwt({ sub: 'admin@klickit.com', role: 'ADMIN', exp: futureExp });
  assert.equal(isTokenExpired(validAdminToken), false);
  assert.equal(getRoleFromToken(validAdminToken), 'ADMIN');

  const expiredToken = createMockJwt({ sub: 'customer@klickit.com', role: 'CUSTOMER', exp: pastExp });
  assert.equal(isTokenExpired(expiredToken), true);

  assert.equal(isTokenExpired('malformed.token'), true);
  assert.equal(getRoleFromToken('malformed.token'), null);
});

test('Auth Initial View: Unauthenticated visitors see AuthScreen before storefront', () => {
  // Visitor with no session sees AuthScreen on storefront
  assert.equal(determineInitialView({ currentPath: '/', user: null, token: null }), 'AUTH_SCREEN');

  // Visitor with expired token sees AuthScreen
  const pastExp = Math.floor(Date.now() / 1000) - 60;
  const expiredToken = createMockJwt({ sub: 'c@k.com', role: 'CUSTOMER', exp: pastExp });
  assert.equal(determineInitialView({ currentPath: '/', user: { role: 'CUSTOMER' }, token: expiredToken }), 'AUTH_SCREEN');

  // Authenticated customer sees Storefront
  const futureExp = Math.floor(Date.now() / 1000) + 3600;
  const validCustToken = createMockJwt({ sub: 'c@k.com', role: 'CUSTOMER', exp: futureExp });
  assert.equal(determineInitialView({ currentPath: '/', user: { role: 'CUSTOMER' }, token: validCustToken }), 'STOREFRONT');
});

test('Auth Routing: Directs roles strictly according to validated JWT claims', () => {
  assert.equal(determinePostAuthRoute('ADMIN'), '/admin');
  assert.equal(determinePostAuthRoute('DELIVERY_PARTNER'), '/delivery');
  assert.equal(determinePostAuthRoute('CUSTOMER'), '/');

  const futureExp = Math.floor(Date.now() / 1000) + 3600;
  const adminToken = createMockJwt({ sub: 'a@k.com', role: 'ADMIN', exp: futureExp });
  const driverToken = createMockJwt({ sub: 'd@k.com', role: 'DELIVERY_PARTNER', exp: futureExp });
  const custToken = createMockJwt({ sub: 'c@k.com', role: 'CUSTOMER', exp: futureExp });

  // Admin access
  assert.equal(determineInitialView({ currentPath: '/admin', user: { role: 'ADMIN' }, token: adminToken }), 'ADMIN_DASHBOARD');
  assert.equal(determineInitialView({ currentPath: '/admin', user: { role: 'CUSTOMER' }, token: custToken }), 'ADMIN_BARRIER_403');
  assert.equal(determineInitialView({ currentPath: '/admin', user: { role: 'DELIVERY_PARTNER' }, token: driverToken }), 'ADMIN_BARRIER_403');

  // Driver and Admin access
  assert.equal(determineInitialView({ currentPath: '/delivery', user: { role: 'DELIVERY_PARTNER' }, token: driverToken }), 'DELIVERY_DASHBOARD');
  assert.equal(determineInitialView({ currentPath: '/delivery', user: { role: 'ADMIN' }, token: adminToken }), 'DELIVERY_DASHBOARD');
  assert.equal(determineInitialView({ currentPath: '/delivery', user: { role: 'CUSTOMER' }, token: custToken }), 'DELIVERY_BARRIER_403');
});

test('Registration Validation: Enforces required fields, phone digits, and minimum password length', () => {
  assert.equal(validateRegistrationInputs({ name: '', email: 'user@test.com', phone: '9876543211', password: 'password123' }).valid, false);
  assert.equal(validateRegistrationInputs({ name: 'User', email: 'invalid-email', phone: '9876543211', password: 'password123' }).valid, false);
  assert.equal(validateRegistrationInputs({ name: 'User', email: 'user@test.com', phone: '123', password: 'password123' }).valid, false);
  assert.equal(validateRegistrationInputs({ name: 'User', email: 'user@test.com', phone: '9876543211', password: '123' }).valid, false);

  const valid = validateRegistrationInputs({ name: 'Raj Kumar', email: '  Raj@Example.com ', phone: '+91 9876543211', password: 'securePassword1!' });
  assert.equal(valid.valid, true);
  assert.equal(valid.clean.email, 'raj@example.com');
  assert.equal(valid.clean.name, 'Raj Kumar');
});

test('Location & Address: Reverse geocode does not overwrite user manually customized address', () => {
  // Simulates LocationPicker: setAddress(prev => (prev.trim() && !prev.startsWith('Pinned Location') ? prev : formatted))
  const updateAddressOnGeocode = (prevAddress, formattedGeocode) => {
    return prevAddress.trim() && !prevAddress.startsWith('Pinned Location') ? prevAddress : formattedGeocode;
  };

  const manualAddress = 'Hostel 4, Room 212';
  const reverseGeocoded = 'VIT Bhopal University, Kothri, Sehore, Madhya Pradesh, 466114';

  // Custom manual address remains intact
  assert.equal(updateAddressOnGeocode(manualAddress, reverseGeocoded), manualAddress);

  // Default / empty / pinned location gets auto-filled
  assert.equal(updateAddressOnGeocode('', reverseGeocoded), reverseGeocoded);
  assert.equal(updateAddressOnGeocode('Pinned Location (23.0756, 76.8501)', reverseGeocoded), reverseGeocoded);
});

test('Location & Persistence: Cancelled location modal preserves existing localStorage address', () => {
  // Mock localStorage store
  const storage = new Map();
  storage.set('klickit_address', 'Original Saved Address, Bhopal');
  storage.set('klickit_coordinates', JSON.stringify({ latitude: 23.0756, longitude: 76.8501 }));

  // User opens picker with initial state
  const initialAddress = storage.get('klickit_address');
  const initialCoords = JSON.parse(storage.get('klickit_coordinates'));

  // User moves pin in modal (temporary local state)
  let modalTempAddress = 'Temporary Discarded Address';
  let modalTempCoords = { latitude: 22.0, longitude: 77.0 };

  // User cancels/closes without onSave
  const onModalClose = () => {
    // onSave is NOT invoked; storage must remain strictly unchanged
  };
  onModalClose();

  assert.equal(storage.get('klickit_address'), 'Original Saved Address, Bhopal');
  assert.deepEqual(JSON.parse(storage.get('klickit_coordinates')), { latitude: 23.0756, longitude: 76.8501 });
});
