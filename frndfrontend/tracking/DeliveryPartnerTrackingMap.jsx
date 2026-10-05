import React, { useCallback, useEffect, useRef, useState } from 'react';
import { getTracking, sendDeliveryLocation } from './trackingApi.js';
import { createMap, customerIcon, fitTrackingBounds, riderIcon } from './mapUtils.js';
import './tracking.css';

const L = window.L;

const MIN_SEND_INTERVAL_MS = 4000;

export default function DeliveryPartnerTrackingMap({ orderId, token }) {
  const mapNodeRef = useRef(null);
  const mapRef = useRef(null);
  const customerMarkerRef = useRef(null);
  const riderMarkerRef = useRef(null);
  const lineRef = useRef(null);
  const watchIdRef = useRef(null);
  const lastSentRef = useRef(0);

  const [data, setData] = useState(null);
  const [tracking, setTracking] = useState(false);
  const [error, setError] = useState('');
  const [lastSent, setLastSent] = useState(null);
  const [riderPosition, setRiderPosition] = useState(null);

  const refresh = useCallback(async () => {
    if (!orderId || !token) return;
    try {
      setData(await getTracking(orderId, token));
      setError('');
    } catch (e) {
      setError(e.message || 'Could not load order tracking');
    }
  }, [orderId, token]);

  useEffect(() => { refresh(); }, [refresh]);

  useEffect(() => {
    if (!mapNodeRef.current || mapRef.current) return;
    mapRef.current = createMap(mapNodeRef.current);
    return () => {
      if (watchIdRef.current !== null) navigator.geolocation.clearWatch(watchIdRef.current);
      mapRef.current?.remove();
      mapRef.current = null;
    };
  }, []);

  const draw = useCallback((riderOverride = null) => {
    const map = mapRef.current;
    if (!map || !data) return;

    const customer = Number.isFinite(data.customerLatitude) && Number.isFinite(data.customerLongitude)
      ? { latitude: data.customerLatitude, longitude: data.customerLongitude }
      : null;
    const rider = riderOverride || (
      Number.isFinite(data.deliveryLatitude) && Number.isFinite(data.deliveryLongitude)
        ? { latitude: data.deliveryLatitude, longitude: data.deliveryLongitude }
        : null
    );

    if (customer) {
      const p = [customer.latitude, customer.longitude];
      if (!customerMarkerRef.current) customerMarkerRef.current = L.marker(p, { icon: customerIcon }).addTo(map).bindPopup('Customer');
      else customerMarkerRef.current.setLatLng(p);
    }

    if (rider) {
      const p = [rider.latitude, rider.longitude];
      if (!riderMarkerRef.current) riderMarkerRef.current = L.marker(p, { icon: riderIcon }).addTo(map).bindPopup('You');
      else riderMarkerRef.current.setLatLng(p);
    }

    if (customer && rider) {
      const points = [[rider.latitude, rider.longitude], [customer.latitude, customer.longitude]];
      if (!lineRef.current) lineRef.current = L.polyline(points, { weight: 4, dashArray: '8 8' }).addTo(map);
      else lineRef.current.setLatLngs(points);
    }

    fitTrackingBounds(map, customer, rider);
  }, [data]);

  useEffect(() => { draw(); }, [draw]);

  async function pushPosition(position) {
    const now = Date.now();
    if (now - lastSentRef.current < MIN_SEND_INTERVAL_MS) return;
    lastSentRef.current = now;

    const livePoint = {
      latitude: position.coords.latitude,
      longitude: position.coords.longitude
    };
    setRiderPosition(livePoint);
    draw(livePoint);

    try {
      const next = await sendDeliveryLocation(orderId, token, position.coords);
      setData(next);
      setLastSent(new Date());
      setError('');
    } catch (e) {
      setError(e.message || 'Could not send your GPS location');
    }
  }

  function startGps() {
    if (!navigator.geolocation) {
      setError('Geolocation is not supported on this device.');
      return;
    }
    if (watchIdRef.current !== null) return;

    watchIdRef.current = navigator.geolocation.watchPosition(
      pushPosition,
      err => setError(err.message || 'Location permission was denied'),
      { enableHighAccuracy: true, maximumAge: 2000, timeout: 12000 }
    );
    setTracking(true);
  }

  function stopGps() {
    if (watchIdRef.current !== null) {
      navigator.geolocation.clearWatch(watchIdRef.current);
      watchIdRef.current = null;
    }
    setTracking(false);
  }

  const customerReady = Number.isFinite(data?.customerLatitude) && Number.isFinite(data?.customerLongitude);
  const canTrack = data?.status === 'OUT_FOR_DELIVERY';

  function getCurrentPosition() {
    return new Promise((resolve, reject) => {
      if (!navigator.geolocation) {
        reject(new Error('Geolocation is not supported on this device.'));
        return;
      }

      navigator.geolocation.getCurrentPosition(
        position => resolve({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude
        }),
        error => reject(new Error(error.message || 'Could not get your current location.')),
        { enableHighAccuracy: true, maximumAge: 5000, timeout: 12000 }
      );
    });
  }

  async function openNavigation() {
    if (!customerReady) {
      setError('Customer has not set an exact delivery pin yet.');
      return;
    }

    try {
      // Prefer the rider's live GPS from this page. If GPS sharing has not
      // started yet, request a fresh one-time location before opening Maps.
      const origin = riderPosition || await getCurrentPosition();
      setRiderPosition(origin);
      draw(origin);

      const originValue = `${origin.latitude},${origin.longitude}`;
      const destinationValue = `${data.customerLatitude},${data.customerLongitude}`;
      const url = `https://www.google.com/maps/dir/?api=1&origin=${encodeURIComponent(originValue)}&destination=${encodeURIComponent(destinationValue)}&travelmode=driving`;

      window.open(url, '_blank', 'noopener,noreferrer');
      setError('');
    } catch (e) {
      setError(e.message || 'Could not open navigation with your current GPS.');
    }
  }

  return (
    <section className="klickit-tracking-card">
      <div className="klickit-tracking-head">
        <div>
          <span>DELIVERY PARTNER</span>
          <h2>Customer + live GPS</h2>
        </div>
        <strong className={`klickit-status ${tracking ? 'live' : ''}`}>{tracking ? '● SHARING' : 'GPS OFF'}</strong>
      </div>

      <div ref={mapNodeRef} className="klickit-map" />
      {error && <p className="klickit-error">{error}</p>}

      {!customerReady && <p className="klickit-note">Customer has not set an exact map pin yet. You can still see their text address from the order.</p>}
      {!canTrack && <p className="klickit-note">Location sharing is accepted only after this order is marked OUT_FOR_DELIVERY.</p>}

      <div className="klickit-actions-row">
        {!tracking ? (
          <button onClick={startGps} disabled={!canTrack}>Start live GPS</button>
        ) : (
          <button className="danger" onClick={stopGps}>Stop sharing</button>
        )}
        <button type="button" onClick={openNavigation} disabled={!customerReady}>Open navigation ↗</button>
      </div>

      <div className="klickit-tracking-meta">
        <div><small>Order</small><b>{orderId ? String(orderId).slice(0, 8) : '—'}</b></div>
        <div><small>Status</small><b>{data?.status || '—'}</b></div>
        <div><small>Customer address</small><b>{data?.customerAddress || '—'}</b></div>
        <div><small>Last sent</small><b>{lastSent ? lastSent.toLocaleTimeString() : '—'}</b></div>
      </div>
    </section>
  );
}

