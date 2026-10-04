import test from 'node:test';
import assert from 'node:assert/strict';

// Helper function representing the role route guard logic implemented in frndfrontend
function checkRouteAccess(route, user) {
  if (route === '/admin' || route.startsWith('/admin/')) {
    if (!user) return { access: false, status: 401, reason: 'AUTH_REQUIRED' };
    if (user.role === 'ADMIN' || user.role === 'ROLE_ADMIN') return { access: true };
    return { access: false, status: 403, reason: 'FORBIDDEN' };
  }

  if (route === '/delivery' || route.startsWith('/delivery/')) {
    if (!user) return { access: false, status: 401, reason: 'AUTH_REQUIRED' };
    if (user.role === 'DELIVERY_PARTNER' || user.role === 'ROLE_DELIVERY_PARTNER') return { access: true };
    return { access: false, status: 403, reason: 'FORBIDDEN' };
  }

  // Default customer route / storefront
  return { access: true };
}

// Lifecycle state transition validator implemented in Admin and Delivery dashboards
const ALLOWED_LIFECYCLE_TRANSITIONS = {
  PLACED: ['READY_TO_ASSIGN', 'REJECTED', 'CANCELLED'],
  READY_TO_ASSIGN: ['ASSIGNED', 'CANCELLED'],
  ASSIGNED: ['OUT_FOR_DELIVERY', 'CANCELLED'],
  OUT_FOR_DELIVERY: ['DELIVERED', 'CANCELLED'],
  DELIVERED: [],
  REJECTED: [],
  CANCELLED: []
};

function canTransition(fromStatus, toStatus) {
  const allowed = ALLOWED_LIFECYCLE_TRANSITIONS[fromStatus] || [];
  return allowed.includes(toStatus);
}

function canAssignDriverDirectlyFromPlaced() {
  return canTransition('PLACED', 'ASSIGNED');
}

test('Route Guard: Customer storefront is accessible to everyone', () => {
  assert.equal(checkRouteAccess('/', null).access, true);
  assert.equal(checkRouteAccess('/', { role: 'CUSTOMER' }).access, true);
  assert.equal(checkRouteAccess('/', { role: 'ADMIN' }).access, true);
  assert.equal(checkRouteAccess('/', { role: 'DELIVERY_PARTNER' }).access, true);
});

test('Route Guard: Admin route (/admin) enforces ROLE_ADMIN guard', () => {
  // Unauthenticated user is challenged
  const unauth = checkRouteAccess('/admin', null);
  assert.equal(unauth.access, false);
  assert.equal(unauth.status, 401);

  // Customer is blocked with 403 Forbidden
  const customer = checkRouteAccess('/admin', { role: 'CUSTOMER', name: 'John Doe' });
  assert.equal(customer.access, false);
  assert.equal(customer.status, 403);

  // Delivery partner is blocked with 403 Forbidden
  const driver = checkRouteAccess('/admin', { role: 'DELIVERY_PARTNER', name: 'Aman Kumar' });
  assert.equal(driver.access, false);
  assert.equal(driver.status, 403);

  // Admin is granted access
  const admin = checkRouteAccess('/admin', { role: 'ADMIN', name: 'KlickIt Admin' });
  assert.equal(admin.access, true);
});

test('Route Guard: Delivery route (/delivery) enforces ROLE_DELIVERY_PARTNER guard', () => {
  // Unauthenticated user is challenged
  const unauth = checkRouteAccess('/delivery', null);
  assert.equal(unauth.access, false);
  assert.equal(unauth.status, 401);

  // Customer is blocked with 403 Forbidden
  const customer = checkRouteAccess('/delivery', { role: 'CUSTOMER', name: 'John Doe' });
  assert.equal(customer.access, false);
  assert.equal(customer.status, 403);

  // Admin is blocked with 403 Forbidden
  const admin = checkRouteAccess('/delivery', { role: 'ADMIN', name: 'KlickIt Admin' });
  assert.equal(admin.access, false);
  assert.equal(admin.status, 403);

  // Delivery partner is granted access
  const driver = checkRouteAccess('/delivery', { role: 'DELIVERY_PARTNER', name: 'Aman Kumar' });
  assert.equal(driver.access, true);
});

test('Lifecycle Workflow: PLACED allows APPROVE (READY_TO_ASSIGN) and REJECT', () => {
  assert.equal(canTransition('PLACED', 'READY_TO_ASSIGN'), true);
  assert.equal(canTransition('PLACED', 'REJECTED'), true);
  assert.equal(canTransition('PLACED', 'CANCELLED'), true);
});

test('Lifecycle Workflow: PLACED cannot directly transition to ASSIGNED (decoupled approval required)', () => {
  assert.equal(canAssignDriverDirectlyFromPlaced(), false);
  assert.equal(canTransition('PLACED', 'ASSIGNED'), false);
});

test('Lifecycle Workflow: READY_TO_ASSIGN allows ASSIGNED with driver selection', () => {
  assert.equal(canTransition('READY_TO_ASSIGN', 'ASSIGNED'), true);
  assert.equal(canTransition('READY_TO_ASSIGN', 'CANCELLED'), true);
  assert.equal(canTransition('READY_TO_ASSIGN', 'DELIVERED'), false);
});

test('Lifecycle Workflow: ASSIGNED allows OUT_FOR_DELIVERY and CANCELLED', () => {
  assert.equal(canTransition('ASSIGNED', 'OUT_FOR_DELIVERY'), true);
  assert.equal(canTransition('ASSIGNED', 'CANCELLED'), true);
  assert.equal(canTransition('ASSIGNED', 'DELIVERED'), false);
});

test('Lifecycle Workflow: OUT_FOR_DELIVERY allows DELIVERED and CANCELLED', () => {
  assert.equal(canTransition('OUT_FOR_DELIVERY', 'DELIVERED'), true);
  assert.equal(canTransition('OUT_FOR_DELIVERY', 'CANCELLED'), true);
});

test('Lifecycle Workflow: Terminal states cannot transition to anything', () => {
  assert.deepEqual(ALLOWED_LIFECYCLE_TRANSITIONS['DELIVERED'], []);
  assert.deepEqual(ALLOWED_LIFECYCLE_TRANSITIONS['REJECTED'], []);
  assert.deepEqual(ALLOWED_LIFECYCLE_TRANSITIONS['CANCELLED'], []);

  assert.equal(canTransition('DELIVERED', 'CANCELLED'), false);
  assert.equal(canTransition('REJECTED', 'READY_TO_ASSIGN'), false);
  assert.equal(canTransition('CANCELLED', 'PLACED'), false);
});

test('Delivery Order Card: Line items map unitPrice, quantity, and lineTotal safely', () => {
  const order = {
    id: 'ord-12345',
    subtotal: 90,
    deliveryFee: 25,
    totalAmount: 115,
    items: [
      { productName: 'Instant Noodles', quantity: 2, unitPrice: 30, price: 30, lineTotal: 60 },
      { productName: 'Soda Can', quantity: 1, unitPrice: 30, price: 30, lineTotal: 30 }
    ]
  };

  const mappedItems = (order.items || []).map(item => {
    const uPrice = Number(item.unitPrice ?? item.price ?? 0);
    const lTotal = Number(item.lineTotal ?? (uPrice * item.quantity));
    return {
      name: item.productName,
      qty: item.quantity,
      unitPrice: uPrice,
      lineTotal: lTotal
    };
  });

  assert.equal(mappedItems.length, 2);
  assert.equal(mappedItems[0].name, 'Instant Noodles');
  assert.equal(mappedItems[0].qty, 2);
  assert.equal(mappedItems[0].unitPrice, 30);
  assert.equal(mappedItems[0].lineTotal, 60);

  assert.equal(mappedItems[1].name, 'Soda Can');
  assert.equal(mappedItems[1].qty, 1);
  assert.equal(mappedItems[1].unitPrice, 30);
  assert.equal(mappedItems[1].lineTotal, 30);
});

test('Delivery Order Card: COD payment breakdown computes subtotal, fee, and collection total', () => {
  const orderWithFee = {
    subtotal: 100,
    deliveryFee: 25,
    totalAmount: 125
  };

  const subtotalWithFee = Number(orderWithFee.subtotal != null
    ? orderWithFee.subtotal
    : (orderWithFee.totalAmount - (orderWithFee.deliveryFee || 0)));
  const feeLabelWithFee = Number(orderWithFee.deliveryFee || 0) > 0
    ? `₹${Number(orderWithFee.deliveryFee).toFixed(0)}`
    : 'FREE';
  const collectWithFee = Number(orderWithFee.totalAmount || 0);

  assert.equal(subtotalWithFee, 100);
  assert.equal(feeLabelWithFee, '₹25');
  assert.equal(collectWithFee, 125);

  // Test free delivery order fallback
  const orderFreeFee = {
    subtotal: 250,
    deliveryFee: 0,
    totalAmount: 250
  };

  const feeLabelFree = Number(orderFreeFee.deliveryFee || 0) > 0
    ? `₹${Number(orderFreeFee.deliveryFee).toFixed(0)}`
    : 'FREE';

  assert.equal(feeLabelFree, 'FREE');
  assert.equal(Number(orderFreeFee.totalAmount), 250);
});

test('Delivery Order Card: Handles null or empty items gracefully without crashing', () => {
  const orderNoItems = {
    id: 'ord-empty',
    totalAmount: 50,
    deliveryFee: 25,
    items: null
  };

  const items = orderNoItems.items || [];
  assert.equal(items.length, 0);

  const fallbackSubtotal = Number(orderNoItems.subtotal != null
    ? orderNoItems.subtotal
    : ((orderNoItems.totalAmount || 0) - (orderNoItems.deliveryFee || 0)));
  assert.equal(fallbackSubtotal, 25);
});
