// API base URL resolution matching main.jsx
const API = import.meta.env?.VITE_API_BASE_URL || '';

/**
 * Fetch live tracking details for an order
 * @param {string} orderId 
 * @param {string} token 
 * @returns {Promise<Object>} TrackingResponse
 */
export async function getTracking(orderId, token) {
  if (!orderId) throw new Error('Order ID is required');

  const headers = { 'Content-Type': 'application/json' };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const res = await fetch(`${API}/api/tracking/${orderId}`, {
    method: 'GET',
    headers
  });

  const body = await res.json().catch(() => ({}));

  if (!res.ok || !body.success) {
    throw new Error(body.message || `Failed to fetch tracking information (HTTP ${res.status})`);
  }

  return body.data;
}

/**
 * Send delivery partner GPS location update
 * @param {string} orderId 
 * @param {string} token 
 * @param {Object} coords { latitude, longitude, accuracy }
 * @returns {Promise<Object>} TrackingResponse
 */
export async function sendDeliveryLocation(orderId, token, coords) {
  if (!orderId) throw new Error('Order ID is required');
  if (!coords || typeof coords.latitude !== 'number' || typeof coords.longitude !== 'number') {
    throw new Error('Valid GPS latitude and longitude are required');
  }

  const res = await fetch(`${API}/api/tracking/${orderId}/location`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify({
      latitude: coords.latitude,
      longitude: coords.longitude,
      accuracy: typeof coords.accuracy === 'number' && Number.isFinite(coords.accuracy) ? coords.accuracy : null
    })
  });

  const body = await res.json().catch(() => ({}));

  if (!res.ok || !body.success) {
    throw new Error(body.message || `Failed to update delivery location (HTTP ${res.status})`);
  }

  return body.data;
}
