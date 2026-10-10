import React, { useCallback, useEffect, useRef, useState } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { getTracking } from '../services/trackingApi.js';

// SVG icons to avoid bundler image asset 404s
const createCustomerIcon = () => {
  return L.divIcon({
    className: 'klickit-customer-pin',
    html: `
      <div style="transform: translate(-14px, -30px); width: 28px; height: 32px;">
        <svg width="28" height="32" viewBox="0 0 28 32" fill="none" xmlns="http://www.w3.org/2000/svg">
          <filter id="c-pin-shadow" x="0" y="0" width="28" height="32" filterUnits="userSpaceOnUse">
            <feDropShadow dx="0" dy="2" stdDeviation="2" flood-color="#000" flood-opacity="0.3"/>
          </filter>
          <path d="M14 2C7.373 2 2 7.373 2 14c0 9 12 16 12 16s12-7 12-16c0-6.627-5.373-12-12-12z" fill="#16a34a" filter="url(#c-pin-shadow)"/>
          <circle cx="14" cy="13" r="5" fill="#ffffff"/>
        </svg>
      </div>
    `,
    iconSize: [28, 32],
    iconAnchor: [14, 32]
  });
};

const createRiderIcon = () => {
  return L.divIcon({
    className: 'klickit-rider-pin',
    html: `
      <div style="transform: translate(-16px, -16px); width: 32px; height: 32px; background: #2563eb; border-radius: 50%; display: flex; align-items: center; justify-content: center; box-shadow: 0 3px 8px rgba(0,0,0,0.35); border: 2px solid #ffffff;">
        <span style="font-size: 16px; line-height: 1;">🛵</span>
      </div>
    `,
    iconSize: [32, 32],
    iconAnchor: [16, 16]
  });
};

export default function CustomerTrackingMap({ orderId, token, pollMs = 4000 }) {
  const mapContainerRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const customerMarkerRef = useRef(null);
  const riderMarkerRef = useRef(null);
  const routeLineRef = useRef(null);
  const isMountedRef = useRef(true);

  // Authoritative token fallback if not passed directly through props
  const authToken = token || (typeof localStorage !== 'undefined' ? localStorage.getItem('klickit_token') : '') || '';

  const [trackingData, setTrackingData] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  const fetchTracking = useCallback(async () => {
    if (!orderId) return;
    try {
      const data = await getTracking(orderId, authToken);
      if (isMountedRef.current) {
        setTrackingData(data);
        setError(null);
      }
    } catch (err) {
      if (isMountedRef.current) {
        setError(err.message || 'Unable to update live tracking');
      }
    } finally {
      if (isMountedRef.current) {
        setLoading(false);
      }
    }
  }, [orderId, authToken]);

  // Polling lifecycle: stops when delivered, cancelled, or rejected
  useEffect(() => {
    isMountedRef.current = true;
    fetchTracking();

    const intervalId = setInterval(() => {
      // Cease polling if terminal state is reached
      if (trackingData && ['DELIVERED', 'CANCELLED', 'REJECTED'].includes(trackingData.status)) {
        clearInterval(intervalId);
        return;
      }
      fetchTracking();
    }, pollMs);

    return () => {
      isMountedRef.current = false;
      clearInterval(intervalId);
    };
  }, [fetchTracking, pollMs, trackingData?.status]);

  // Initialize Leaflet Map
  useEffect(() => {
    const container = mapContainerRef.current;
    if (!container || mapInstanceRef.current) return;

    const defaultLat = 23.075611;
    const defaultLng = 76.850082;

    const map = L.map(container, {
      center: [defaultLat, defaultLng],
      zoom: 15,
      zoomControl: true,
      attributionControl: false
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19
    }).addTo(map);

    mapInstanceRef.current = map;

    const safeInvalidate = () => {
      if (mapInstanceRef.current) {
        try {
          mapInstanceRef.current.invalidateSize();
        } catch (_) {
          // ignore
        }
      }
    };

    // Staggered invalidation across initial mount, modal transitions, and route animations
    const t1 = setTimeout(safeInvalidate, 60);
    const t2 = setTimeout(safeInvalidate, 200);
    const t3 = setTimeout(safeInvalidate, 400);

    // ResizeObserver ensures dimensions are refreshed whenever the container transitions from 0 to visible
    let observer = null;
    if (typeof ResizeObserver !== 'undefined') {
      observer = new ResizeObserver((entries) => {
        for (const entry of entries) {
          if (entry.contentRect && entry.contentRect.width > 0 && entry.contentRect.height > 0) {
            safeInvalidate();
          }
        }
      });
      observer.observe(container);
    }

    return () => {
      clearTimeout(t1);
      clearTimeout(t2);
      clearTimeout(t3);
      if (observer) {
        observer.disconnect();
      }
      if (mapInstanceRef.current) {
        mapInstanceRef.current.remove();
        mapInstanceRef.current = null;
      }
    };
  }, []);

  // Update Markers and Polyline when trackingData changes
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map || !trackingData) return;

    // Recalculate container size before adjusting view bounds
    try {
      map.invalidateSize();
    } catch (_) {
      // ignore
    }

    const hasCustomerCoords = typeof trackingData.customerLatitude === 'number' &&
      typeof trackingData.customerLongitude === 'number' &&
      Number.isFinite(trackingData.customerLatitude) &&
      Number.isFinite(trackingData.customerLongitude);

    const hasRiderCoords = typeof trackingData.deliveryLatitude === 'number' &&
      typeof trackingData.deliveryLongitude === 'number' &&
      Number.isFinite(trackingData.deliveryLatitude) &&
      Number.isFinite(trackingData.deliveryLongitude);

    const pointsToFit = [];

    // Customer Delivery Marker
    if (hasCustomerCoords) {
      const cPoint = [trackingData.customerLatitude, trackingData.customerLongitude];
      pointsToFit.push(cPoint);

      if (!customerMarkerRef.current) {
        customerMarkerRef.current = L.marker(cPoint, { icon: createCustomerIcon() })
          .addTo(map)
          .bindPopup('<b>Your Delivery Point</b>');
      } else {
        customerMarkerRef.current.setLatLng(cPoint);
      }
    }

    // Rider Marker
    if (hasRiderCoords) {
      const rPoint = [trackingData.deliveryLatitude, trackingData.deliveryLongitude];
      pointsToFit.push(rPoint);

      if (!riderMarkerRef.current) {
        riderMarkerRef.current = L.marker(rPoint, { icon: createRiderIcon() })
          .addTo(map)
          .bindPopup(`<b>${trackingData.deliveryPartnerName || 'Delivery Partner'}</b><br/>En route`);
      } else {
        riderMarkerRef.current.setLatLng(rPoint);
      }
    } else if (riderMarkerRef.current) {
      riderMarkerRef.current.remove();
      riderMarkerRef.current = null;
    }

    // Route connection line
    if (hasCustomerCoords && hasRiderCoords) {
      const linePoints = [
        [trackingData.deliveryLatitude, trackingData.deliveryLongitude],
        [trackingData.customerLatitude, trackingData.customerLongitude]
      ];

      if (!routeLineRef.current) {
        routeLineRef.current = L.polyline(linePoints, {
          color: '#2563eb',
          weight: 4,
          dashArray: '8, 8',
          opacity: 0.85
        }).addTo(map);
      } else {
        routeLineRef.current.setLatLngs(linePoints);
      }
    } else if (routeLineRef.current) {
      routeLineRef.current.remove();
      routeLineRef.current = null;
    }

    // Adjust camera view
    if (pointsToFit.length > 1) {
      map.fitBounds(L.latLngBounds(pointsToFit), { padding: [40, 40], maxZoom: 16 });
    } else if (pointsToFit.length === 1) {
      map.setView(pointsToFit[0], 15);
    }
  }, [trackingData]);

  const hasRider = typeof trackingData?.deliveryLatitude === 'number';

  return (
    <div className="live-tracking-container" style={{ margin: '16px 0', borderRadius: '12px', overflow: 'hidden', border: '1px solid var(--line, #e2e8f0)', background: '#fff' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 16px', background: 'var(--surface-sunken, #f8fafc)', borderBottom: '1px solid var(--line, #e2e8f0)' }}>
        <div>
          <span style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--muted, #64748b)', fontWeight: 600 }}>LIVE DELIVERY TRACKING</span>
          <div style={{ fontSize: '14px', fontWeight: 700, color: 'var(--ink, #0f172a)' }}>
            {trackingData?.status === 'OUT_FOR_DELIVERY' ? 'Rider is on the way' : 'Preparing your delivery'}
          </div>
        </div>
        <span style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: '6px',
          padding: '4px 10px',
          borderRadius: '20px',
          fontSize: '12px',
          fontWeight: 600,
          background: hasRider ? '#ecfdf5' : '#f1f5f9',
          color: hasRider ? '#15803d' : '#64748b'
        }}>
          {hasRider && <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#16a34a', display: 'inline-block' }}></span>}
          {hasRider ? 'Live GPS' : 'Awaiting GPS'}
        </span>
      </div>

      <div
        ref={mapContainerRef}
        style={{ width: '100%', height: '220px', background: '#e2e8f0' }}
        role="region"
        aria-label="Customer Delivery Tracking Map"
      />

      <div style={{ padding: '12px 16px', fontSize: '12px', color: 'var(--muted, #64748b)', display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: '8px' }}>
        <div>
          <span style={{ display: 'block', fontSize: '11px', color: '#94a3b8' }}>Delivery Partner</span>
          <strong style={{ color: 'var(--ink, #0f172a)' }}>{trackingData?.deliveryPartnerName || 'Assigning soon...'}</strong>
        </div>
        <div>
          <span style={{ display: 'block', fontSize: '11px', color: '#94a3b8' }}>Rider Status</span>
          <strong style={{ color: 'var(--ink, #0f172a)' }}>
            {hasRider ? 'Broadcasting live position' : 'Driver not yet active on GPS'}
          </strong>
        </div>
        {trackingData?.locationUpdatedAt && (
          <div>
            <span style={{ display: 'block', fontSize: '11px', color: '#94a3b8' }}>Last Updated</span>
            <strong style={{ color: 'var(--ink, #0f172a)' }}>{new Date(trackingData.locationUpdatedAt).toLocaleTimeString()}</strong>
          </div>
        )}
      </div>

      {trackingData?.meetAtGate && (
        <div style={{ margin: '0 16px 12px', padding: '8px 12px', background: '#fef3c7', borderRadius: '8px', fontSize: '12px', color: '#92400e', display: 'flex', alignItems: 'center', gap: '6px' }}>
          <span>🏛️</span>
          <span><b>Campus Security:</b> Please meet the rider at the Main Gate for handover.</span>
        </div>
      )}

      {error && (
        <div style={{ margin: '0 16px 12px', padding: '8px 12px', background: '#fef2f2', borderRadius: '8px', fontSize: '12px', color: '#b91c1c' }}>
          {error}
        </div>
      )}
    </div>
  );
}
