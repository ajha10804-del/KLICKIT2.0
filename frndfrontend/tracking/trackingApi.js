
const API = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');

async function request(path, token, options = {}) {
  const res = await fetch(`${API}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers || {})
    }
  });

  const body = await res.json().catch(() => ({}));

  if (!res.ok || body.success === false) {
    throw new Error(
      body.message ||
      body.error ||
      `Request failed (${res.status})`
    );
  }

  return body.data ?? body;
}

export function getTracking(orderId, token) {
  return request(`/api/orders/${orderId}`, token);
}

export function setCustomerDeliveryPin(
  orderId,
  token,
  latitude,
  longitude
) {
  return request(`/api/orders/${orderId}/delivery-pin`, token, {
    method: 'PATCH',
    body: JSON.stringify({
      latitude,
      longitude
    })
  });
}

export function sendDeliveryLocation(
  orderId,
  token,
  coords
) {
  return request(
    `/api/delivery/orders/${orderId}/location`,
    token,
    {
      method: 'PATCH',
      body: JSON.stringify({
        latitude: coords.latitude,
        longitude: coords.longitude,
        accuracyMeters: Number.isFinite(coords.accuracy)
          ? coords.accuracy
          : null
      })
    }
  );
}
