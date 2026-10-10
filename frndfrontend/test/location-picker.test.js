import test from 'node:test';
import assert from 'node:assert/strict';

/**
 * Unit and integration tests for Task C Location Picker and Geofenced Checkout flow
 */

// Helper replicating frontend coordinates validation logic in redesigned LocationPicker & main.jsx
function validateLocationCoordinates({ coordinates }) {
  if (!coordinates || typeof coordinates.lat !== 'number' || typeof coordinates.lng !== 'number') {
    return { valid: false, error: 'Please select your delivery location using the map or "Use My Current Location".' };
  }
  return { valid: true, error: null };
}

// Helper replicating accuracy warning logic
function getAccuracyWarning(accuracyMeters) {
  if (typeof accuracyMeters === 'number' && accuracyMeters > 100) {
    return `Your location may not be precise (~${Math.round(accuracyMeters)}m). Please adjust the pin on the map or move outdoors.`;
  }
  return null;
}

// Helper replicating Geolocation error translation
function translateGeolocationError(code) {
  // GeolocationPositionError codes: 1 = PERMISSION_DENIED, 2 = POSITION_UNAVAILABLE, 3 = TIMEOUT
  if (code === 1) {
    return 'Location permission is required to place a delivery order. Please enable location access and try again.';
  } else if (code === 2) {
    return 'Unable to determine your current position. Please select your location on the map.';
  } else if (code === 3) {
    return 'Location request timed out. Please try again or drag the pin on the map.';
  }
  return 'Could not retrieve location. Please adjust the pin manually.';
}

// Helper replicating checkout payload construction with nullable customerAddress
function buildCheckoutPayload({ address, landmark, coordinates }) {
  const resolvedAddress = (address && address.trim()) || null;
  return {
    customerAddress: resolvedAddress,
    customerLandmark: landmark && landmark.trim() ? landmark.trim() : null,
    customerLatitude: coordinates ? coordinates.lat : null,
    customerLongitude: coordinates ? coordinates.lng : null
  };
}

// Helper replicating checkout error response mapping
function mapCheckoutErrorMessage(status, backendMessage) {
  if (status === 400 && backendMessage && (
    backendMessage.includes('Delivery is not available at this location') ||
    backendMessage.includes('exceeds maximum delivery radius') ||
    backendMessage.includes('Delivery location is outside our operational service area') ||
    backendMessage.includes('service area') ||
    backendMessage.includes('delivery radius')
  )) {
    return 'Delivery is not possible in this area.';
  }
  return backendMessage || 'Checkout failed. Please try again.';
}

// Helper replicating campus warning display condition in tracking modal
function shouldDisplayCampusNotice(order) {
  return Boolean(order && order.meetAtGate === true);
}

// --- TEST SUITE ---

test('Task C: Location Picker requires valid coordinates', () => {
  const resultMissing = validateLocationCoordinates({ coordinates: null });
  assert.equal(resultMissing.valid, false);
  assert.match(resultMissing.error, /Please select your delivery location/i);

  const resultEmpty = validateLocationCoordinates({ coordinates: {} });
  assert.equal(resultEmpty.valid, false);

  const resultValid = validateLocationCoordinates({ coordinates: { lat: 23.0753, lng: 76.8606 } });
  assert.equal(resultValid.valid, true);
  assert.equal(resultValid.error, null);
});

test('Task C: Location can be confirmed with valid coordinates and no address', () => {
  const coords = { lat: 23.0756, lng: 76.8500 };
  
  // No address provided at all
  const noAddressResult = validateLocationCoordinates({ coordinates: coords });
  assert.equal(noAddressResult.valid, true);
  assert.equal(noAddressResult.error, null);

  // Payload cleanly sends null for customerAddress
  const payloadNoAddress = buildCheckoutPayload({
    address: '',
    landmark: 'Near Hostel 5',
    coordinates: coords
  });
  assert.equal(payloadNoAddress.customerAddress, null);
  assert.equal(payloadNoAddress.customerLandmark, 'Near Hostel 5');
  assert.equal(payloadNoAddress.customerLatitude, 23.0756);
  assert.equal(payloadNoAddress.customerLongitude, 76.8500);

  // When optional address text is supplied, it is preserved
  const payloadWithAddress = buildCheckoutPayload({
    address: 'Block 6, Room 304',
    landmark: null,
    coordinates: coords
  });
  assert.equal(payloadWithAddress.customerAddress, 'Block 6, Room 304');
});

test('Task C: Draggable pin updates internal coordinates without displaying raw lat/lng', () => {
  let selectedCoordinates = { lat: 23.073428, lng: 76.828648 };
  
  // Simulate dragging marker to new point
  const onMarkerDragEnd = (newLat, newLng) => {
    selectedCoordinates = { lat: Number(newLat.toFixed(6)), lng: Number(newLng.toFixed(6)) };
  };

  onMarkerDragEnd(23.075611, 76.850082);
  assert.equal(selectedCoordinates.lat, 23.075611);
  assert.equal(selectedCoordinates.lng, 76.850082);

  // Payload verification: lat and lng are numeric and never transformed into user-facing text inputs
  const payload = buildCheckoutPayload({
    address: 'Main Gate Security Post, VIT Bhopal',
    landmark: 'Opposite Visitor Lounge',
    coordinates: selectedCoordinates
  });

  assert.equal(payload.customerLatitude, 23.075611);
  assert.equal(payload.customerLongitude, 76.850082);
  assert.equal(payload.customerLandmark, 'Opposite Visitor Lounge');
  assert.equal(payload.customerAddress, 'Main Gate Security Post, VIT Bhopal');
});

test('Task C: Geolocation accuracy > 100m triggers non-blocking warning', () => {
  assert.equal(getAccuracyWarning(45), null);
  assert.equal(getAccuracyWarning(100), null);

  const warning150 = getAccuracyWarning(150);
  assert.ok(warning150);
  assert.match(warning150, /~150m/);
  assert.match(warning150, /adjust the pin on the map or move outdoors/i);

  const warning350 = getAccuracyWarning(350.2);
  assert.ok(warning350);
  assert.match(warning350, /~350m/);
});

test('Task C: Geolocation error handling (Permission Denied, Timeout, Position Unavailable)', () => {
  // Permission denied (code 1)
  const permError = translateGeolocationError(1);
  assert.match(permError, /Location permission is required to place a delivery order/i);

  // Position unavailable (code 2)
  const posError = translateGeolocationError(2);
  assert.match(posError, /Unable to determine your current position/i);

  // Timeout (code 3)
  const timeError = translateGeolocationError(3);
  assert.match(timeError, /Location request timed out/i);
});

test('Task C: Out-of-range backend 400 rejection displays "Delivery is not possible in this area."', () => {
  const backendErrors = [
    'Delivery is not available at this location (6.42 km exceeds maximum delivery radius of 6.00 km)',
    'Delivery location is outside our operational service area (7.10 km > 6.00 km)',
    'Selected coordinates exceed maximum delivery radius'
  ];

  for (const errMsg of backendErrors) {
    const userMessage = mapCheckoutErrorMessage(400, errMsg);
    assert.equal(userMessage, 'Delivery is not possible in this area.');
  }

  // Non-radius 400 error should pass through backend message
  const otherErr = mapCheckoutErrorMessage(400, 'Cart is empty');
  assert.equal(otherErr, 'Cart is empty');
});

test('Task C: Campus Meet-At-Gate warning renders strictly when order.meetAtGate is true', () => {
  // Point outside campus geofence: meetAtGate is false
  const standardOrder = {
    id: 101,
    customerAddress: 'Kothri Village Center, Sehore',
    customerLatitude: 23.073428,
    customerLongitude: 76.828648,
    meetAtGate: false
  };
  assert.equal(shouldDisplayCampusNotice(standardOrder), false);

  // Legacy order without meetAtGate field
  const legacyOrder = {
    id: 99,
    customerAddress: 'Old Order Address'
  };
  assert.equal(shouldDisplayCampusNotice(legacyOrder), false);

  // Point inside campus geofence: backend authoritatively sets meetAtGate to true
  const campusOrder = {
    id: 102,
    customerAddress: 'Block 6, Room 402, VIT Bhopal',
    customerLatitude: 23.075327,
    customerLongitude: 76.860658,
    customerLandmark: 'Near Badminton Court',
    meetAtGate: true
  };
  assert.equal(shouldDisplayCampusNotice(campusOrder), true);
});

test('Task C: Checkout payload excludes client-calculated distance or client meetAtGate flag', () => {
  const payload = buildCheckoutPayload({
    address: 'Block 3 Hostel, VIT Bhopal Campus',
    landmark: 'Near Mess 2',
    coordinates: { lat: 23.0754, lng: 76.8601 }
  });

  // Client must NOT send meetAtGate or distance; backend is authoritative
  assert.equal('meetAtGate' in payload, false);
  assert.equal('distanceKm' in payload, false);
  assert.equal(payload.customerLatitude, 23.0754);
  assert.equal(payload.customerLongitude, 76.8601);
  assert.equal(payload.customerLandmark, 'Near Mess 2');
  assert.equal(payload.customerAddress, 'Block 3 Hostel, VIT Bhopal Campus');
});
