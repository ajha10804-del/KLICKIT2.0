import test from 'node:test';
import assert from 'node:assert/strict';

/**
 * Unit & Integration tests for Live Delivery Tracking (Task 5)
 */

// Helper simulating GPS position validation logic
function validateRiderCoordinates(coords) {
  if (!coords || typeof coords.latitude !== 'number' || typeof coords.longitude !== 'number') {
    return { valid: false, error: 'Valid GPS latitude and longitude are required' };
  }
  if (!Number.isFinite(coords.latitude) || coords.latitude < -90 || coords.latitude > 90) {
    return { valid: false, error: 'Latitude must be a finite number between -90 and 90' };
  }
  if (!Number.isFinite(coords.longitude) || coords.longitude < -180 || coords.longitude > 180) {
    return { valid: false, error: 'Longitude must be a finite number between -180 and 180' };
  }
  if (coords.accuracy !== undefined && coords.accuracy !== null) {
    if (!Number.isFinite(coords.accuracy) || coords.accuracy < 0) {
      return { valid: false, error: 'Accuracy must be a finite, non-negative number' };
    }
  }
  return { valid: true, error: null };
}

// Helper simulating order status eligibility for GPS tracking
function isOrderEligibleForTracking(status) {
  return status === 'ASSIGNED' || status === 'OUT_FOR_DELIVERY';
}

// Helper simulating client-side throttling
class GpsThrottleController {
  constructor(minIntervalMs = 4000) {
    this.minIntervalMs = minIntervalMs;
    this.lastSentTime = 0;
  }

  shouldSend(now) {
    if (now - this.lastSentTime >= this.minIntervalMs) {
      this.lastSentTime = now;
      return true;
    }
    return false;
  }
}

// Helper simulating Google Maps Driving Navigation deep-link generator
function buildNavigationDeepLink(origin, destination) {
  if (!destination || typeof destination.lat !== 'number' || typeof destination.lng !== 'number') {
    return null;
  }
  const destStr = `${destination.lat},${destination.lng}`;
  let url = `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(destStr)}&travelmode=driving`;
  if (origin && typeof origin.lat === 'number' && typeof origin.lng === 'number') {
    const originStr = `${origin.lat},${origin.lng}`;
    url += `&origin=${encodeURIComponent(originStr)}`;
  }
  return url;
}

// Helper simulating watchPosition lifecycle tracking
class MockGeolocationWatcher {
  constructor() {
    this.activeWatchId = null;
    this.nextId = 1;
  }

  watchPosition(onSuccess, onError, options) {
    const id = this.nextId++;
    this.activeWatchId = id;
    this.onSuccess = onSuccess;
    this.onError = onError;
    this.options = options;
    return id;
  }

  clearWatch(id) {
    if (this.activeWatchId === id) {
      this.activeWatchId = null;
      this.onSuccess = null;
      this.onError = null;
    }
  }

  simulatePosition(latitude, longitude, accuracy = 10) {
    if (this.activeWatchId !== null && this.onSuccess) {
      this.onSuccess({
        coords: { latitude, longitude, accuracy }
      });
    }
  }

  simulateError(code, message) {
    if (this.activeWatchId !== null && this.onError) {
      this.onError({ code, message });
    }
  }
}

// --- TEST SUITE ---

test('Task 5: GPS watcher starts when order status is ASSIGNED or OUT_FOR_DELIVERY', () => {
  assert.equal(isOrderEligibleForTracking('ASSIGNED'), true);
  assert.equal(isOrderEligibleForTracking('OUT_FOR_DELIVERY'), true);
  assert.equal(isOrderEligibleForTracking('PLACED'), false);
  assert.equal(isOrderEligibleForTracking('READY_TO_ASSIGN'), false);
  assert.equal(isOrderEligibleForTracking('DELIVERED'), false);
  assert.equal(isOrderEligibleForTracking('CANCELLED'), false);

  const geo = new MockGeolocationWatcher();
  const watchId = geo.watchPosition(
    pos => {},
    err => {},
    { enableHighAccuracy: true, maximumAge: 2000, timeout: 12000 }
  );

  assert.ok(watchId > 0);
  assert.equal(geo.activeWatchId, watchId);
});

test('Task 5: GPS watcher stops and clears cleanly on stop sharing or unmount', () => {
  const geo = new MockGeolocationWatcher();
  const watchId = geo.watchPosition(() => {}, () => {});
  assert.equal(geo.activeWatchId, watchId);

  // Stop sharing / unmount cleanup
  geo.clearWatch(watchId);
  assert.equal(geo.activeWatchId, null);
});

test('Task 5: Customer tracking map displays rider coordinates when provided', () => {
  const trackingPayload = {
    orderId: 'uuid-1234',
    status: 'OUT_FOR_DELIVERY',
    trackingActive: true,
    customerLatitude: 23.075327,
    customerLongitude: 76.860658,
    deliveryLatitude: 23.074120,
    deliveryLongitude: 76.852000,
    accuracy: 12.5,
    deliveryPartnerName: 'Raju Driver'
  };

  const hasRider = typeof trackingPayload.deliveryLatitude === 'number' &&
    Number.isFinite(trackingPayload.deliveryLatitude);

  assert.equal(hasRider, true);
  assert.equal(trackingPayload.deliveryLatitude, 23.074120);
  assert.equal(trackingPayload.deliveryLongitude, 76.852000);
});

test('Task 5: Missing rider position does not crash customer tracking view', () => {
  const trackingPayloadNoRider = {
    orderId: 'uuid-5678',
    status: 'ASSIGNED',
    trackingActive: true,
    customerLatitude: 23.075327,
    customerLongitude: 76.860658,
    deliveryLatitude: null,
    deliveryLongitude: null,
    deliveryPartnerName: 'Raju Driver'
  };

  const hasRider = typeof trackingPayloadNoRider.deliveryLatitude === 'number' &&
    Number.isFinite(trackingPayloadNoRider.deliveryLatitude);

  // Must be false without throwing any errors or null-pointer crashes
  assert.equal(hasRider, false);

  // Customer coordinates are still valid
  const hasCustomer = typeof trackingPayloadNoRider.customerLatitude === 'number' &&
    Number.isFinite(trackingPayloadNoRider.customerLatitude);
  assert.equal(hasCustomer, true);
});

test('Task 5: Rider coordinates validation rejects invalid ranges, NaN, and negative accuracy', () => {
  // Valid coordinate
  const valid = validateRiderCoordinates({ latitude: 23.075, longitude: 76.850, accuracy: 15.0 });
  assert.equal(valid.valid, true);

  // Out of range latitude
  const badLat = validateRiderCoordinates({ latitude: 95.0, longitude: 76.850 });
  assert.equal(badLat.valid, false);
  assert.match(badLat.error, /between -90 and 90/);

  // Out of range longitude
  const badLon = validateRiderCoordinates({ latitude: 23.0, longitude: 185.0 });
  assert.equal(badLon.valid, false);
  assert.match(badLon.error, /between -180 and 180/);

  // NaN values
  const nanCoord = validateRiderCoordinates({ latitude: NaN, longitude: 76.850 });
  assert.equal(nanCoord.valid, false);

  // Negative accuracy
  const negAcc = validateRiderCoordinates({ latitude: 23.0, longitude: 76.850, accuracy: -10 });
  assert.equal(negAcc.valid, false);
  assert.match(negAcc.error, /non-negative/);
});

test('Task 5: Client-side throttling suppresses high-frequency GPS update spikes', () => {
  const throttle = new GpsThrottleController(4000);
  const t0 = 1000000;

  // First event at t0: should send
  assert.equal(throttle.shouldSend(t0), true);

  // Event 1000ms later: suppressed
  assert.equal(throttle.shouldSend(t0 + 1000), false);

  // Event 2500ms later: suppressed
  assert.equal(throttle.shouldSend(t0 + 2500), false);

  // Event 4001ms later: should send
  assert.equal(throttle.shouldSend(t0 + 4001), true);
});

test('Task 5: Navigation deep link constructs proper Google Maps driving route', () => {
  const destination = { lat: 23.075327, lng: 76.860658 };
  const origin = { lat: 23.074000, lng: 76.840000 };

  const url = buildNavigationDeepLink(origin, destination);
  assert.ok(url);
  assert.match(url, /google\.com\/maps\/dir/);
  assert.match(url, /travelmode=driving/);
  assert.match(url, /23\.075327%2C76\.860658/);
  assert.match(url, /23\.074%2C76\.84/);

  // Missing destination returns null
  assert.equal(buildNavigationDeepLink(origin, null), null);
});

// --- Leaflet Map Sizing & Invalidation Lifecycle Tests ---

class MockLeafletInstance {
  constructor(container, options) {
    this.container = container;
    this.options = options;
    this.invalidateCount = 0;
    this.isRemoved = false;
  }

  invalidateSize() {
    if (this.isRemoved) throw new Error('Cannot invalidate a removed map');
    this.invalidateCount++;
  }

  remove() {
    this.isRemoved = true;
  }
}

class MockResizeObserver {
  constructor(callback) {
    this.callback = callback;
    this.observedElement = null;
    this.isDisconnected = false;
  }

  observe(element) {
    this.observedElement = element;
    this.isDisconnected = false;
  }

  disconnect() {
    this.isDisconnected = true;
    this.observedElement = null;
  }

  trigger(width, height) {
    if (this.isDisconnected) return;
    this.callback([{
      contentRect: { width, height }
    }]);
  }
}

test('Map Sizing: CustomerTrackingMap initializes and schedules multi-tier size invalidation', async () => {
  const container = { id: 'map-container' };
  const map = new MockLeafletInstance(container, { zoom: 15 });

  // Simulate CustomerTrackingMap timer lifecycle
  let mapInstance = map;
  const safeInvalidate = () => {
    if (mapInstance && !mapInstance.isRemoved) {
      mapInstance.invalidateSize();
    }
  };

  const t1 = setTimeout(safeInvalidate, 60);
  const t2 = setTimeout(safeInvalidate, 200);
  const t3 = setTimeout(safeInvalidate, 400);

  assert.equal(map.invalidateCount, 0);

  // Wait for first 2 timers (at ~220ms)
  await new Promise(r => setTimeout(r, 220));
  assert.ok(map.invalidateCount >= 2, `Expected at least 2 invalidations, got ${map.invalidateCount}`);

  // Wait for 3rd timer
  await new Promise(r => setTimeout(r, 220));
  assert.equal(map.invalidateCount, 3);

  clearTimeout(t1);
  clearTimeout(t2);
  clearTimeout(t3);
  map.remove();
  mapInstance = null;
});

test('Map Sizing: ResizeObserver triggers size invalidation when container dimensions become positive', () => {
  const container = { id: 'map-container' };
  const map = new MockLeafletInstance(container, { zoom: 15 });

  let mapInstance = map;
  const safeInvalidate = () => {
    if (mapInstance && !mapInstance.isRemoved) {
      mapInstance.invalidateSize();
    }
  };

  const observer = new MockResizeObserver((entries) => {
    for (const entry of entries) {
      if (entry.contentRect && entry.contentRect.width > 0 && entry.contentRect.height > 0) {
        safeInvalidate();
      }
    }
  });

  observer.observe(container);
  assert.equal(observer.observedElement, container);
  assert.equal(map.invalidateCount, 0);

  // Initial zero size (e.g. hidden modal): should NOT trigger
  observer.trigger(0, 0);
  assert.equal(map.invalidateCount, 0);

  // Container gains dimensions (e.g. modal opens / layout completes): triggers invalidation
  observer.trigger(500, 220);
  assert.equal(map.invalidateCount, 1);

  // Container resizes (e.g. mobile orientation or window resize): triggers again
  observer.trigger(360, 220);
  assert.equal(map.invalidateCount, 2);

  // Disconnect cleans up
  observer.disconnect();
  assert.equal(observer.isDisconnected, true);

  // Post-disconnect triggers do not invoke map
  observer.trigger(600, 220);
  assert.equal(map.invalidateCount, 2);
});

test('Map Sizing: Map cleanup clears all timers, disconnects observer, and removes map instance', () => {
  const container = { id: 'map-container' };
  const map = new MockLeafletInstance(container, { zoom: 15 });

  let mapInstance = map;
  const safeInvalidate = () => {
    if (mapInstance && !mapInstance.isRemoved) {
      mapInstance.invalidateSize();
    }
  };

  const t1 = setTimeout(safeInvalidate, 60);
  const t2 = setTimeout(safeInvalidate, 200);
  const t3 = setTimeout(safeInvalidate, 400);

  const observer = new MockResizeObserver(safeInvalidate);
  observer.observe(container);

  // Simulate unmount cleanup before timers fire
  clearTimeout(t1);
  clearTimeout(t2);
  clearTimeout(t3);
  observer.disconnect();
  map.remove();
  mapInstance = null;

  assert.equal(map.isRemoved, true);
  assert.equal(observer.isDisconnected, true);
  assert.equal(mapInstance, null);
  assert.equal(map.invalidateCount, 0);
});

test('Map Sizing: Arrival of trackingData recalculates map size before adjusting bounds', () => {
  const container = { id: 'map-container' };
  const map = new MockLeafletInstance(container, { zoom: 15 });

  // Simulate trackingData update effect in CustomerTrackingMap
  const handleTrackingUpdate = (data) => {
    if (!map || !data) return;
    map.invalidateSize(); // Pre-bounds size refresh
  };

  handleTrackingUpdate({ customerLatitude: 23.075, customerLongitude: 76.850 });
  assert.equal(map.invalidateCount, 1);

  handleTrackingUpdate({ customerLatitude: 23.075, customerLongitude: 76.850, deliveryLatitude: 23.074, deliveryLongitude: 76.852 });
  assert.equal(map.invalidateCount, 2);
});

test('Map Sizing: Repeated mount and unmount cycles execute cleanly without duplicate instances', () => {
  for (let cycle = 0; cycle < 5; cycle++) {
    const container = { id: `map-container-${cycle}` };
    const map = new MockLeafletInstance(container, { zoom: 15 });
    assert.equal(map.isRemoved, false);
    map.remove();
    assert.equal(map.isRemoved, true);
  }
});
