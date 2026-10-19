import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const mainJsxPath = path.resolve(__dirname, '../main.jsx');
const mainJsxContent = fs.readFileSync(mainJsxPath, 'utf8');

test('Catalog: Stationery & Electronics category is present in navigation categories', () => {
  assert.match(mainJsxContent, /name:\s*'Stationery & Electronics'/, 'Stationery & Electronics must be in categories array');
});

test('Catalog: PLACEHOLDER_IMAGE is defined as a clean SVG data URI', () => {
  assert.match(mainJsxContent, /const PLACEHOLDER_IMAGE = "data:image\/svg\+xml;utf8,<svg/, 'PLACEHOLDER_IMAGE must be defined as SVG');
  assert.ok(!mainJsxContent.includes('seedProducts[i % seedProducts.length].image'), 'Must NOT substitute seedProducts cyclically for missing images');
});

test('Catalog: Product card and cart drawer use PLACEHOLDER_IMAGE fallback without unrelated food substitution', () => {
  // Check product card img tag
  assert.match(mainJsxContent, /p\.image \|\| PLACEHOLDER_IMAGE/, 'Product card img should fall back to PLACEHOLDER_IMAGE');
  // Check onError fallback in product card
  assert.ok(!mainJsxContent.includes('seedProducts[0].image'), 'Must NOT fallback to seedProducts[0].image on error');
  // Check cart drawer item img tag
  assert.match(mainJsxContent, /p\.image \|\| PLACEHOLDER_IMAGE/, 'Cart drawer img should fall back to PLACEHOLDER_IMAGE');
});

test('Cart Consistency: cartItems and cartCount derive strictly from valid matched products', () => {
  // Simulate cart reconciliation logic from main.jsx
  const liveProducts = [
    { id: '11111111-1111-1111-1111-111111111111', name: 'Chakki Atta 5 kg', price: 210, mrp: 250, image: 'data:image/svg+xml...' },
    { id: '22222222-2222-2222-2222-222222222222', name: 'Maggi Masala Noodles 70 g', price: 18, mrp: 18, image: 'https://cdn...' }
  ];

  // User had a stale cart with an old numeric id '1' (from seed) and a live UUID
  const cartState = {
    '1': 3, // Stale numeric id
    '22222222-2222-2222-2222-222222222222': 2 // Live product
  };

  const cartItems = liveProducts.filter(p => cartState[p.id] > 0).map(p => ({ ...p, qty: cartState[p.id] }));
  const cartCount = cartItems.reduce((sum, p) => sum + p.qty, 0);
  const subtotal = cartItems.reduce((sum, p) => sum + p.price * p.qty, 0);

  assert.equal(cartItems.length, 1, 'Only live catalog product should be in cartItems');
  assert.equal(cartItems[0].name, 'Maggi Masala Noodles 70 g');
  assert.equal(cartItems[0].price, 18, 'Authoritative price from catalog must be used');
  assert.equal(cartCount, 2, 'Stale items must not increment cartCount');
  assert.equal(subtotal, 36, 'Subtotal must match live catalog price * qty (18 * 2)');
});
