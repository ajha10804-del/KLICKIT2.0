import React, { useCallback, useEffect, useRef, useState } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { getTracking, sendDeliveryLocation } from '../services/trackingApi.js';

const MIN_SEND_INTERVAL_MS = 4000;

const createCustomerIcon = () => {
  return L.divIcon({
    className: 'klickit-customer-pin',
    html: `
      <div style="transform: translate(-14px, -30px); width: 28px; height: 32px;">
        <svg width="28" height="32" viewBox="0 0 28 32" fill="none" xmlns="http://www.w3.org/2000/svg">
          <filter id="dp-c-shadow" x="0" y="0" width="28" height="32" filterUnits="userSpaceOnUse">
            <feDropShadow dx="0" dy="2" stdDeviation="2" flood-color="#000" flood-opacity="0.3"/>
          </filter>
          <path d="M14 2C7.373 2 2 7.373 2 14c0 9 12 16 12 16s12-7 12-16c0-6.627-5.373-12-12-12z" fill="#dc2626" filter="url(#dp-c-shadow)"/>
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

export default function DeliveryPartnerTrackingMap({
  orderId,
  token,
  customerLatitude,
  customerLongitude
}) {
  const mapContainerRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const customerMarkerRef = useRef(null);
  const riderMarkerRef = useRef(null);
  const routeLineRef = useRef(null);
  const watchIdRef = useRef(null);
  const lastSendTimeRef = useRef(0);
  const isMountedRef = useRef(true);

  // Authoritative token fallback if not passed directly through props
  const authToken = token || (typeof localStorage !== 'undefined' ? localStorage.getItem('klickit_token') : '') || '';

  const [trackingData, setTrackingData] = useState(null);
  const [sharingGps, setSharingGps] = useState(false);
  const [error, setError] = useState(null);
  const [lastSentTime, setLastSentTime] = useState(null);
  const [currentRiderPos, setCurrentRiderPos] = useState(null);

  const fetchOrderTracking = useCallback(async () => {
    if (!orderId || !authToken) return;
    try {
      const data = await getTracking(orderId, authToken);
      if (isMountedRef.current) {
        setTrackingData(data);
        setError(null);
      }
    } catch (err) {
      if (isMountedRef.current) {
        setError(err.message || 'Could not load order tracking');
      }
    }
  }, [orderId, authToken]);

  useEffect(() => {
    isMountedRef.current = true;
    fetchOrderTracking();

    return () => {
      isMountedRef.current = false;
      if (watchIdRef.current !== null) {
        navigator.geolocation.clearWatch(watchIdRef.current);
        watchIdRef.current = null;
      }
    };
  }, [fetchOrderTracking]);

  // Leaflet map initialization
  useEffect(() => {
    if (!mapContainerRef.current || mapInstanceRef.current) return;

    const initialLat = customerLatitude || 23.075611;
    const initialLng = customerLongitude || 76.850082;

    const map = L.map(mapContainerRef.current, {
      center: [initialLat, initialLng],
      zoom: 15,
      zoomControl: true,
      attributionControl: false
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19
    }).addTo(map);

    mapInstanceRef.current = map;

    const timer = setTimeout(() => {
      if (mapInstanceRef.current) {
        mapInstanceRef.current.invalidateSize();
      }
    }, 200);

    return () => {
      clearTimeout(timer);
      if (mapInstanceRef.current) {
        mapInstanceRef.current.remove();
        mapInstanceRef.current = null;
      }
    };
  }, [customerLatitude, customerLongitude]);

  // Authoritative customer coordinates (from tracking API or order props)
  const effectiveCustomerLat = trackingData?.customerLatitude ?? customerLatitude;
  const effectiveCustomerLng = trackingData?.customerLongitude ?? customerLongitude;

  // Sync map markers
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map) return;

    const hasCustomerCoords = typeof effectiveCustomerLat === 'number' &&
      typeof effectiveCustomerLng === 'number' &&
      Number.isFinite(effectiveCustomerLat) &&
      Number.isFinite(effectiveCustomerLng);

    const riderLat = currentRiderPos?.latitude || trackingData?.deliveryLatitude;
    const riderLng = currentRiderPos?.longitude || trackingData?.deliveryLongitude;
    const hasRiderCoords = typeof riderLat === 'number' && Number.isFinite(riderLat);

    const points = [];

    if (hasCustomerCoords) {
      const cPos = [effectiveCustomerLat, effectiveCustomerLng];
      points.push(cPos);
      if (!customerMarkerRef.current) {
        customerMarkerRef.current = L.marker(cPos, { icon: createCustomerIcon() })
          .addTo(map)
          .bindPopup('<b>Customer Destination</b>');
      } else {
        customerMarkerRef.current.setLatLng(cPos);
      }
    }

    if (hasRiderCoords) {
      const rPos = [riderLat, riderLng];
      points.push(rPos);
      if (!riderMarkerRef.current) {
        riderMarkerRef.current = L.marker(rPos, { icon: createRiderIcon() })
          .addTo(map)
          .bindPopup('<b>Your Live Position</b>');
      } else {
        riderMarkerRef.current.setLatLng(rPos);
      }
    }

    if (hasCustomerCoords && hasRiderCoords) {
      const line = [
        [riderLat, riderLng],
        [effectiveCustomerLat, effectiveCustomerLng]
      ];
      if (!routeLineRef.current) {
        routeLineRef.current = L.polyline(line, {
          color: '#2563eb',
          weight: 4,
          dashArray: '8, 8'
        }).addTo(map);
      } else {
        routeLineRef.current.setLatLngs(line);
      }
    }

    if (points.length > 1) {
      map.fitBounds(L.latLngBounds(points), { padding: [40, 40], maxZoom: 16 });
    } else if (points.length === 1) {
      map.setView(points[0], 15);
    }
  }, [trackingData, currentRiderPos, effectiveCustomerLat, effectiveCustomerLng]);

  // Handle position update from watchPosition
  const handlePositionUpdate = async (position) => {
    const now = Date.now();
    if (now - lastSendTimeRef.current < MIN_SEND_INTERVAL_MS) {
      return; // Client-side throttle
    }
    lastSendTimeRef.current = now;

    const coords = {
      latitude: position.coords.latitude,
      longitude: position.coords.longitude,
      accuracy: position.coords.accuracy
    };

    if (isMountedRef.current) {
      setCurrentRiderPos(coords);
    }

    try {
      const updated = await sendDeliveryLocation(orderId, authToken, coords);
      if (isMountedRef.current) {
        setTrackingData(updated);
        setLastSentTime(new Date());
        setError(null);
      }
    } catch (err) {
      if (isMountedRef.current) {
        setError(err.message || 'Failed to sync live GPS with server');
      }
    }
  };

  const startGpsSharing = () => {
    if (!navigator.geolocation) {
      setError('Geolocation is not supported by your browser.');
      return;
    }
    if (watchIdRef.current !== null) return;

    setError(null);
    watchIdRef.current = navigator.geolocation.watchPosition(
      handlePositionUpdate,
      (err) => {
        let msg = 'Could not access GPS.';
        if (err.code === 1) msg = 'Location access permission was denied.';
        else if (err.code === 2) msg = 'GPS signal unavailable. Please ensure location is enabled.';
        else if (err.code === 3) msg = 'GPS request timed out.';
        if (isMountedRef.current) {
          setError(msg);
          setSharingGps(false);
        }
      },
      { enableHighAccuracy: true, maximumAge: 2000, timeout: 12000 }
    );
    setSharingGps(true);
  };

  const stopGpsSharing = () => {
    if (watchIdRef.current !== null) {
      navigator.geolocation.clearWatch(watchIdRef.current);
      watchIdRef.current = null;
    }
    setSharingGps(false);
  };

  // Google Maps Driving Navigation deep link
  const openExternalNavigation = () => {
    if (!effectiveCustomerLat || !effectiveCustomerLng) {
      setError('Customer delivery coordinates are not set.');
      return;
    }

    const dest = `${effectiveCustomerLat},${effectiveCustomerLng}`;
    let url = `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(dest)}&travelmode=driving`;

    if (currentRiderPos) {
      const origin = `${currentRiderPos.latitude},${currentRiderPos.longitude}`;
      url += `&origin=${encodeURIComponent(origin)}`;
    }

    window.open(url, '_blank', 'noopener,noreferrer');
  };

  const isAssignableOrOut = ['ASSIGNED', 'OUT_FOR_DELIVERY'].includes(trackingData?.status || 'ASSIGNED');

  return (
    <div style={{ marginTop: '12px', border: '1px solid var(--line, #e2e8f0)', borderRadius: '10px', background: '#fff', overflow: 'hidden' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 14px', background: '#f8fafc', borderBottom: '1px solid var(--line, #e2e8f0)' }}>
        <div>
          <span style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.04em', color: '#64748b', fontWeight: 600 }}>DELIVERY NAVIGATION</span>
          <div style={{ fontSize: '13px', fontWeight: 700, color: '#0f172a' }}>Live GPS Broadcast</div>
        </div>
        <span style={{
          padding: '3px 8px',
          borderRadius: '12px',
          fontSize: '11px',
          fontWeight: 700,
          background: sharingGps ? '#dcfce7' : '#f1f5f9',
          color: sharingGps ? '#15803d' : '#64748b'
        }}>
          {sharingGps ? '● BROADCASTING' : 'GPS OFF'}
        </span>
      </div>

      <div
        ref={mapContainerRef}
        style={{ width: '100%', height: '180px', background: '#f1f5f9' }}
        role="region"
        aria-label="Delivery Partner Live Map"
      />

      <div style={{ padding: '10px 14px', display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
        {!sharingGps ? (
          <button
            type="button"
            onClick={startGpsSharing}
            disabled={!isAssignableOrOut}
            style={{
              padding: '8px 14px',
              borderRadius: '8px',
              fontSize: '12px',
              fontWeight: 600,
              background: '#2563eb',
              color: '#fff',
              border: 'none',
              cursor: isAssignableOrOut ? 'pointer' : 'not-allowed',
              opacity: isAssignableOrOut ? 1 : 0.6
            }}
          >
            📍 Start live GPS
          </button>
        ) : (
          <button
            type="button"
            onClick={stopGpsSharing}
            style={{
              padding: '8px 14px',
              borderRadius: '8px',
              fontSize: '12px',
              fontWeight: 600,
              background: '#dc2626',
              color: '#fff',
              border: 'none',
              cursor: 'pointer'
            }}
          >
            ⏹ Stop sharing
          </button>
        )}

        <button
          type="button"
          onClick={openExternalNavigation}
          style={{
            padding: '8px 14px',
            borderRadius: '8px',
            fontSize: '12px',
            fontWeight: 600,
            background: '#f8fafc',
            color: '#0f172a',
            border: '1px solid #cbd5e1',
            cursor: 'pointer'
          }}
        >
          Open Maps ↗
        </button>

        {lastSentTime && (
          <span style={{ alignSelf: 'center', fontSize: '11px', color: '#64748b', marginLeft: 'auto' }}>
            Last sent: {lastSentTime.toLocaleTimeString()}
          </span>
        )}
      </div>

      {error && (
        <div style={{ margin: '0 14px 10px', padding: '6px 10px', background: '#fef2f2', borderRadius: '6px', fontSize: '11px', color: '#b91c1c' }}>
          {error}
        </div>
      )}
    </div>
  );
}
