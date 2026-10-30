/**
 * Authentication profile and token resolution utility.
 * Authoritatively builds the client user profile from authentication responses.
 */

/**
 * Helper to safely extract role claim from JWT token.
 * Compatible with both browser (atob) and Node.js (Buffer) runtime environments.
 *
 * @param {string} token
 * @returns {string|null} role claim (e.g., 'ADMIN', 'DELIVERY_PARTNER', 'CUSTOMER') or null
 */
export function getRoleFromToken(token) {
  if (!token || typeof token !== 'string') return null;
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const jsonStr = typeof atob === 'function'
      ? atob(base64)
      : (typeof Buffer !== 'undefined' ? Buffer.from(base64, 'base64').toString('utf-8') : null);
    if (!jsonStr) return null;
    const payload = JSON.parse(jsonStr);
    return payload.role || null;
  } catch {
    return null;
  }
}

/**
 * Extracts and constructs an authoritative user profile and token from an auth response envelope.
 * Preserves all six profile fields: userId, id, name, email, phone, role.
 * Throws an Error if token is missing.
 * Derives role authoritatively from JWT claim over response body.
 *
 * @param {Object} data Server response body
 * @param {string} [fallbackEmail] Fallback email if absent in payload
 * @param {string} [fallbackName] Fallback name if absent in payload
 * @param {string} [fallbackPhone] Fallback phone if absent in payload
 * @returns {{ profile: { userId: string|null, id: string|null, name: string, email: string, phone: string, role: string }, token: string }}
 */
export function buildAuthProfile(data, fallbackEmail = '', fallbackName = '', fallbackPhone = '') {
  if (!data || typeof data !== 'object') {
    throw new Error('Authentication response is missing or malformed.');
  }

  const token = data.data?.token || data.token || data.accessToken;
  if (!token || typeof token !== 'string') {
    throw new Error('Authentication token missing from server response.');
  }

  const tokenRole = getRoleFromToken(token);
  const resUser = data.data?.user || data.user || data.data || {};
  const authoritativeRole = tokenRole || resUser.role || 'CUSTOMER';

  const resolvedUserId = resUser.userId || resUser.id || null;
  const resolvedPhone = resUser.phone || fallbackPhone || '';
  const resolvedEmail = resUser.email || fallbackEmail || '';
  const resolvedName = resUser.name || fallbackName || (resolvedEmail ? resolvedEmail.split('@')[0] : 'Customer');

  const profile = {
    userId: resolvedUserId,
    id: resolvedUserId,
    name: resolvedName,
    email: resolvedEmail,
    phone: resolvedPhone,
    role: authoritativeRole
  };

  return { profile, token };
}
