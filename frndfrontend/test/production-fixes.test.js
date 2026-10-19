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
