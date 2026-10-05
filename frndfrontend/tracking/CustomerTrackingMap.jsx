
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { getTracking, setCustomerDeliveryPin } from './trackingApi.js';
import { createMap, customerIcon, fitTrackingBounds, riderIcon } from './mapUtils.js';
import './tracking.css';

const L = window.L;

export default function CustomerTrackingMap({ orderId, token, pollMs = 4000, compact = false }) {
  const mapNodeRef = useRef(null);
  const mapRef = useRef(null);
  const customerMarkerRef = useRef(null);
  const riderMarkerRef = useRef(null);
  const routeLineRef = useRef(null);

  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [pinSaving, setPinSaving] = useState(false);

  const refresh = useCallback(async () => {
    if (!orderId || !token) return;
    try {
      const next = await getTracking(orderId, token);
      setData(next);
      setError('');
    } catch (e) {
      setError(e.message || 'Could not load live tracking');
    } finally {
      setLoading(false);
    }
  }, [orderId, token]);

  useEffect(() => {
    refresh();
    const id = window.setInterval(refresh, pollMs);
    return () => window.clearInterval(id);
  }, [refresh, pollMs]);

  useEffect(() => {
    if (!mapNodeRef.current || mapRef.current) return;
    mapRef.current = createMap(mapNodeRef.current);
    return () => {
      mapRef.current?.remove();
      mapRef.current = null;
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !data) return;

    const customer = Number.isFinite(data.customerLatitude) && Number.isFinite(data.customerLongitude)
      ? { latitude: data.customerLatitude, longitude: data.customerLongitude }
      : null;
    const rider = Number.isFinite(data.deliveryLatitude) && Number.isFinite(data.deliveryLongitude)
      ? { latitude: data.deliveryLatitude, longitude: data.deliveryLongitude }
      : null;

    if (customer) {
      const latLng = [customer.latitude, customer.longitude];
      if (!customerMarkerRef.current) {
        customerMarkerRef.current = L.marker(latLng, { icon: customerIcon }).addTo(map).bindPopup('Your delivery point');
      } else {
        customerMarkerRef.current.setLatLng(latLng);
      }
    }

    if (rider) {
      const latLng = [rider.latitude, rider.longitude];
      if (!riderMarkerRef.current) {
        riderMarkerRef.current = L.marker(latLng, { icon: riderIcon }).addTo(map).bindPopup('Delivery partner');
      } else {
        riderMarkerRef.current.setLatLng(latLng);
      }
    }

    if (customer && rider) {
      const points = [[rider.latitude, rider.longitude], [customer.latitude, customer.longitude]];
      if (!routeLineRef.current) {
        routeLineRef.current = L.polyline(points, { weight: 4, dashArray: '8 8' }).addTo(map);
      } else {
        routeLineRef.current.setLatLngs(points);
      }
    }

    fitTrackingBounds(map, customer, rider);
  }, [data]);

  function useMyLocation() {
    if (!navigator.geolocation) {
      setError('Geolocation is not supported on this device.');
      return;
    }

    setPinSaving(true);
    navigator.geolocation.getCurrentPosition(async position => {
      try {
        const next = await setCustomerDeliveryPin(
          orderId,
          token,
          position.coords.latitude,
          position.coords.longitude
        );
        setData(next);
        setError('');
      } catch (e) {
        setError(e.message || 'Could not save delivery pin');
      } finally {
        setPinSaving(false);
      }
    }, err => {
      setPinSaving(false);
      setError(err.message || 'Location permission was denied');
    }, { enableHighAccuracy: true, timeout: 12000, maximumAge: 10000 });
  }

  const hasCustomerPin = Number.isFinite(data?.customerLatitude) && Number.isFinite(data?.customerLongitude);
  const hasRider = Number.isFinite(data?.deliveryLatitude) && Number.isFinite(data?.deliveryLongitude);

  return (
    <section className={`klickit-tracking-card ${compact ? 'compact' : ''}`}>
      <div className="klickit-tracking-head">
        <div>
          <span>LIVE ORDER MAP</span>
          <h2>Track your delivery</h2>
        </div>
        <strong className={`klickit-status ${data?.trackingActive ? 'live' : ''}`}>
          {data?.trackingActive ? '● LIVE' : data?.status || 'WAITING'}
        </strong>
      </div>

      <div ref={mapNodeRef} className="klickit-map" />

      {loading && <p className="klickit-note">Loading tracking data…</p>}
      {error && <p className="klickit-error">{error}</p>}

      {!hasCustomerPin && !loading && (
        <div className="klickit-callout">
          <b>Your exact delivery pin is not set yet.</b>
          <p>Use your phone GPS so the delivery partner can find you accurately.</p>
          <button onClick={useMyLocation} disabled={pinSaving}>
            {pinSaving ? 'Saving location…' : '📍 Use my current location'}
          </button>
        </div>
      )}

      {hasCustomerPin && (
        <button className="klickit-secondary" onClick={useMyLocation} disabled={pinSaving}>
          {pinSaving ? 'Updating…' : 'Update my delivery pin'}
        </button>
      )}

      <div className="klickit-tracking-meta">
        <div><small>Order status</small><b>{data?.status || '—'}</b></div>
        <div><small>Rider</small><b>{data?.deliveryPartnerName || 'Not assigned yet'}</b></div>
        <div><small>Rider location</small><b>{hasRider ? 'Available' : 'Waiting for rider GPS'}</b></div>
        <div><small>Last GPS update</small><b>{data?.locationUpdatedAt ? new Date(data.locationUpdatedAt).toLocaleTimeString() : '—'}</b></div>
      </div>
    </section>
  );
}
