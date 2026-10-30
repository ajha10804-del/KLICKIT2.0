import test from 'node:test';
import assert from 'node:assert/strict';
import { buildAuthProfile, getRoleFromToken } from '../src/utils/authProfile.js';

// Helper to generate a dummy unsigned JWT with a given payload
function createDummyJwt(payload) {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const body = Buffer.from(JSON.stringify(payload)).toString('base64url');
  return `${header}.${body}.dummySignature`;
}

test('Auth Profile: getRoleFromToken extracts role claim from valid JWT', () => {
  const adminToken = createDummyJwt({ sub: 'admin@klickit.test', role: 'ADMIN' });
  const driverToken = createDummyJwt({ sub: 'driver@klickit.test', role: 'DELIVERY_PARTNER' });
  const customerToken = createDummyJwt({ sub: 'customer@klickit.test', role: 'CUSTOMER' });

  assert.equal(getRoleFromToken(adminToken), 'ADMIN');
  assert.equal(getRoleFromToken(driverToken), 'DELIVERY_PARTNER');
  assert.equal(getRoleFromToken(customerToken), 'CUSTOMER');
});

test('Auth Profile: getRoleFromToken returns null for missing or malformed tokens', () => {
  assert.equal(getRoleFromToken(null), null);
  assert.equal(getRoleFromToken(''), null);
  assert.equal(getRoleFromToken('not-a-jwt'), null);
  assert.equal(getRoleFromToken('header.malformed-json.sig'), null);
});

test('Auth Profile: buildAuthProfile builds complete 6-field profile from full response envelope', () => {
  const token = createDummyJwt({ sub: 'alice@example.com', role: 'CUSTOMER' });
  const responseData = {
    success: true,
    data: {
      token,
      userId: 'uuid-alice-1234',
      name: 'Alice Smith',
      email: 'alice@example.com',
      phone: '+919876543210',
      role: 'CUSTOMER'
    }
  };

  const { profile, token: resolvedToken } = buildAuthProfile(responseData);

  assert.equal(resolvedToken, token);
  assert.equal(profile.userId, 'uuid-alice-1234');
  assert.equal(profile.id, 'uuid-alice-1234');
  assert.equal(profile.name, 'Alice Smith');
  assert.equal(profile.email, 'alice@example.com');
  assert.equal(profile.phone, '+919876543210');
  assert.equal(profile.role, 'CUSTOMER');
});

test('Auth Profile: buildAuthProfile derives name from email and phone from fallback when missing', () => {
  const token = createDummyJwt({ sub: 'bob@klickit.test', role: 'CUSTOMER' });
  const responseData = {
    data: {
      token,
      user: {
        id: 'uuid-bob-5678',
        email: 'bob@klickit.test'
      }
    }
  };

  const { profile } = buildAuthProfile(responseData, 'bob@klickit.test', '', '+919800000000');

  assert.equal(profile.userId, 'uuid-bob-5678');
  assert.equal(profile.id, 'uuid-bob-5678');
  assert.equal(profile.name, 'bob');
  assert.equal(profile.email, 'bob@klickit.test');
  assert.equal(profile.phone, '+919800000000');
  assert.equal(profile.role, 'CUSTOMER');
});

test('Auth Profile: buildAuthProfile prioritizes authoritative JWT role claim over body role', () => {
  // Attacker or inconsistent body claims CUSTOMER, but authoritative token claim is ADMIN
  const token = createDummyJwt({ sub: 'admin@klickit.test', role: 'ADMIN' });
  const responseData = {
    token,
    user: {
      userId: 'uuid-admin-001',
      name: 'Admin User',
      email: 'admin@klickit.test',
      role: 'CUSTOMER'
    }
  };

  const { profile } = buildAuthProfile(responseData);
  assert.equal(profile.role, 'ADMIN', 'Must preserve authoritative JWT role claim');
});

test('Auth Profile: buildAuthProfile throws descriptive error when token is missing', () => {
  assert.throws(() => {
    buildAuthProfile({ data: { user: { name: 'No Token' } } });
  }, /Authentication token missing/);

  assert.throws(() => {
    buildAuthProfile(null);
  }, /Authentication response is missing or malformed/);
});
