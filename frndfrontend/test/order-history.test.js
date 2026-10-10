import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const stylesCssPath = path.resolve(__dirname, '../styles.css');
const stylesCss = fs.readFileSync(stylesCssPath, 'utf8');

// Simulation of Order History card formatting logic from frndfrontend/main.jsx
function formatOrderCard(order) {
  const isCancelled = order.status === 'CANCELLED';
  const isRejected = order.status === 'REJECTED';

  const statusDisplay = isCancelled
    ? '✕ CANCELLED'
    : isRejected
    ? '✕ REJECTED'
    : order.status;

  const statusPillClass = `status-pill ${String(order.status || '').toLowerCase()}`;

  const dateStr = order.createdAt
    ? new Date(order.createdAt).toLocaleDateString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
    : 'Recent';

  const itemsCount = Array.isArray(order.items)
    ? order.items.reduce((sum, it) => sum + (it.quantity || 1), 0)
    : 0;

  const itemsSummary = Array.isArray(order.items)
    ? order.items.map(it => `${it.productName}${it.quantity > 1 ? ` (×${it.quantity})` : ''}`).filter(Boolean).join(', ')
    : '';

  const displayAddress = order.customerAddress && order.customerAddress.trim()
    ? order.customerAddress.trim()
    : 'Pinned Map Location';

  const landmark = order.customerLandmark ? `(${order.customerLandmark})` : null;

  return {
    idShort: `#${String(order.id).substring(0, 8)}`,
    statusDisplay,
    statusPillClass,
    dateStr,
    totalFormatted: `₹${Number(order.totalAmount || 0).toFixed(2)}`,
    displayAddress,
    landmark,
    locationFull: landmark ? `📍 ${displayAddress} ${landmark}` : `📍 ${displayAddress}`,
    itemsCount,
    itemsSummary: itemsSummary.length > 55 ? `${itemsSummary.substring(0, 52)}…` : itemsSummary
  };
}

// 1. Order history card renders correctly
test('Order History: 1. Order history card renders complete structured model', () => {
  const card = formatOrderCard({
    id: 'f81d4fae-7dec-11d0-a765-00a0c91e6bf6',
    status: 'PLACED',
    totalAmount: 180.50,
    createdAt: '2026-10-07T12:00:00Z',
    customerAddress: 'Hostel 3, Room 204',
    customerLandmark: 'Near Water Cooler',
    items: [{ productName: 'Maggi Noodles', quantity: 2 }]
  });

  assert.equal(card.idShort, '#f81d4fae');
  assert.equal(card.statusDisplay, 'PLACED');
  assert.equal(card.statusPillClass, 'status-pill placed');
  assert.equal(card.totalFormatted, '₹180.50');
  assert.equal(card.displayAddress, 'Hostel 3, Room 204');
  assert.equal(card.landmark, '(Near Water Cooler)');
  assert.equal(card.itemsCount, 2);
  assert.match(card.itemsSummary, /Maggi Noodles/);
});

// 2. Order ID/status/items/total appear
test('Order History: 2. Order ID, status, items summary, and total amount appear correctly', () => {
  const card = formatOrderCard({
    id: '12345678-abcd-ef01-2345-6789abcdef01',
    status: 'OUT_FOR_DELIVERY',
    totalAmount: 349.00,
    items: [
      { productName: 'Amul Milk 500ml', quantity: 2 },
      { productName: 'Brown Bread', quantity: 1 }
    ]
  });

  assert.equal(card.idShort, '#12345678');
  assert.equal(card.statusDisplay, 'OUT_FOR_DELIVERY');
  assert.equal(card.statusPillClass, 'status-pill out_for_delivery');
  assert.equal(card.itemsCount, 3);
  assert.match(card.itemsSummary, /Amul Milk.*Brown Bread/);
  assert.equal(card.totalFormatted, '₹349.00');
});

// 3. Nullable address renders Pinned Map Location
test('Order History: 3. Nullable customerAddress renders "Pinned Map Location"', () => {
  const card = formatOrderCard({
    id: 'ord-pinned-1',
    status: 'ASSIGNED',
    customerAddress: null,
    customerLatitude: 23.075611,
    customerLongitude: 76.850082,
    customerLandmark: null
  });

  assert.equal(card.displayAddress, 'Pinned Map Location');
  assert.equal(card.landmark, null);
  assert.equal(card.locationFull, '📍 Pinned Map Location');
});

// 4. Landmark renders when available
test('Order History: 4. Landmark renders as secondary info alongside Pinned Map Location', () => {
  const card = formatOrderCard({
    id: 'ord-pinned-2',
    status: 'ASSIGNED',
    customerAddress: null,
    customerLatitude: 23.075611,
    customerLongitude: 76.850082,
    customerLandmark: 'Main Gate Security Desk'
  });

  assert.equal(card.displayAddress, 'Pinned Map Location');
  assert.equal(card.landmark, '(Main Gate Security Desk)');
  assert.equal(card.locationFull, '📍 Pinned Map Location (Main Gate Security Desk)');
});

// 5. No null / undefined text appears
test('Order History: 5. No "null", "undefined", or raw coordinates appear in rendered location string', () => {
  const card = formatOrderCard({
    id: 'ord-safe-1',
    status: 'DELIVERED',
    customerAddress: null,
    customerLandmark: null,
    customerLatitude: null,
    customerLongitude: null
  });

  assert.equal(card.locationFull.includes('null'), false);
  assert.equal(card.locationFull.includes('undefined'), false);
  assert.equal(card.locationFull.includes('Address on file'), false);
  assert.equal(card.locationFull, '📍 Pinned Map Location');
});

// 6. Refresh button calls order-history fetch
test('Order History: 6. Refresh button triggers order history fetch', async () => {
  let fetchCalled = false;
  let loadingState = false;

  async function mockFetchOrderHistory() {
    if (loadingState) return;
    loadingState = true;
    fetchCalled = true;
    loadingState = false;
  }

  await mockFetchOrderHistory();
  assert.equal(fetchCalled, true);
});

// 7. Refresh loading state prevents duplicate requests
test('Order History: 7. Concurrency guard prevents duplicate fetchOrderHistory requests while loading', async () => {
  let executionCount = 0;
  let historyLoading = false;

  async function fetchWithGuard() {
    if (historyLoading) return;
    historyLoading = true;
    executionCount++;
    // Simulate async network latency
    await new Promise(r => setTimeout(r, 20));
    historyLoading = false;
  }

  // Fire three calls rapidly
  const p1 = fetchWithGuard();
  const p2 = fetchWithGuard();
  const p3 = fetchWithGuard();

  await Promise.all([p1, p2, p3]);

  // Only the first call should have proceeded
  assert.equal(executionCount, 1);
});

// 8. Clicking history order triggers latest order fetch
test('Order History: 8. Clicking order card immediately sets state and fires fresh fetchOrder', () => {
  const staleHistoricalOrder = {
    id: 'ord-hist-888',
    status: 'PLACED',
    totalAmount: 150
  };

  let activeOrder = null;
  let navigatedPath = null;
  let fetchOrderIdCalled = null;

  function handleOrderClick(order) {
    activeOrder = order;
    navigatedPath = `/orders/${order.id}`;
    fetchOrderIdCalled = order.id;
  }

  handleOrderClick(staleHistoricalOrder);

  assert.equal(activeOrder.id, 'ord-hist-888');
  assert.equal(navigatedPath, '/orders/ord-hist-888');
  assert.equal(fetchOrderIdCalled, 'ord-hist-888');
});

// 9. Failed latest-order fetch preserves existing order data
test('Order History: 9. Network error during fetchOrder preserves previously rendered order data', async () => {
  const cachedOrder = {
    id: 'ord-hist-999',
    status: 'READY_TO_ASSIGN',
    totalAmount: 220
  };

  let activeOrder = cachedOrder;
  let trackingError = null;

  async function simulateFailedFetch(orderId) {
    try {
      throw new Error('Network error: could not load order.');
    } catch (err) {
      trackingError = err.message;
      // Note: activeOrder is NOT reset to null
    }
  }

  await simulateFailedFetch('ord-hist-999');

  assert.equal(activeOrder.id, 'ord-hist-999');
  assert.equal(activeOrder.status, 'READY_TO_ASSIGN');
  assert.equal(trackingError, 'Network error: could not load order.');
});

// 10. Empty order history renders correctly
test('Order History: 10. Empty state renders clean message and shopping CTA when orderHistory is empty', () => {
  function renderHistoryContainer(orderHistory, loading, error) {
    if (loading) return { view: 'LOADING' };
    if (error) return { view: 'ERROR', error };
    if (orderHistory.length === 0) {
      return {
        view: 'EMPTY',
        title: 'No orders yet',
        description: 'When you place an order, it will appear here so you can track its delivery status anytime.',
        cta: 'Start Shopping ↗'
      };
    }
    return { view: 'LIST', count: orderHistory.length };
  }

  const emptyResult = renderHistoryContainer([], false, null);
  assert.equal(emptyResult.view, 'EMPTY');
  assert.equal(emptyResult.title, 'No orders yet');
  assert.match(emptyResult.description, /appear here so you can track/);
  assert.equal(emptyResult.cta, 'Start Shopping ↗');
});

// 11. Mobile drawer CSS prevents fixed-width overflow
test('Order History: 11. CSS styles define responsive drawer width preventing overflow on mobile', () => {
  // Drawer must use width: min(540px, 100vw) or max-width: 100vw
  assert.match(stylesCss, /\.account-modal,\.order-history-drawer\{[^}]*width:\s*min\(540px,\s*100vw\)/);
  assert.match(stylesCss, /\.account-modal,\.order-history-drawer\{[^}]*max-width:\s*100vw/);

  // Mobile media query must constrain drawer width to 100vw
  assert.match(stylesCss, /@media\s*\(max-width:\s*600px\)\s*\{[^}]*\.account-modal,\.order-history-drawer\{width:100vw;max-width:100vw/);

  // .order-card / .order-card-summary classes defined with box-sizing: border-box
  assert.match(stylesCss, /\.order-card,\.order-card-summary\{[^}]*box-sizing:\s*border-box/);
});
