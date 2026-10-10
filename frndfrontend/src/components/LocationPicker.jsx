import React, { useEffect, useRef, useState } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

// VIT Bhopal Main Gate baseline coordinates
const DEFAULT_CENTER = {
  lat: 23.075611,
  lng: 76.850082
};

// Custom SVG Pin Icon for high-DPI crisp rendering without bundler asset path issues
const createCustomPinIcon = () => {
  return L.divIcon({
    className: 'klickit-custom-map-pin',
    html: `
      <div style="transform: translate(-16px, -36px); width: 32px; height: 36px; cursor: grab;">
        <svg width="32" height="36" viewBox="0 0 32 36" fill="none" xmlns="http://www.w3.org/2000/svg">
          <filter id="pin-shadow" x="0" y="0" width="32" height="36" filterUnits="userSpaceOnUse">
            <feDropShadow dx="0" dy="3" stdDeviation="2" flood-color="#000" flood-opacity="0.35"/>
          </filter>
          <path d="M16 2C8.268 2 2 8.268 2 16c0 10.2 14 18 14 18s14-7.8 14-18c0-7.732-6.268-14-14-14z" fill="#dc2626" filter="url(#pin-shadow)"/>
          <circle cx="16" cy="15" r="5.5" fill="#ffffff"/>
        </svg>
      </div>
    `,
    iconSize: [32, 36],
    iconAnchor: [16, 36]
  });
};

export default function LocationPicker({
  initialAddress = '',
  initialLandmark = '',
  initialCoordinates = null,
  onSave,
  onClose
}) {
  const [address, setAddress] = useState(initialAddress || '');
  const [landmark, setLandmark] = useState(initialLandmark || '');
  const [coordinates, setCoordinates] = useState(initialCoordinates || null);

  const [geoLoading, setGeoLoading] = useState(false);
  const [geoError, setGeoError] = useState(null);
  const [geoWarning, setGeoWarning] = useState(null);
  const [geoAccuracy, setGeoAccuracy] = useState(null);
  const [formError, setFormError] = useState(null);

  const mapContainerRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const markerRef = useRef(null);

  // Initialize Leaflet Map
  useEffect(() => {
    if (!mapContainerRef.current || mapInstanceRef.current) return;

    const startLat = initialCoordinates?.latitude || DEFAULT_CENTER.lat;
    const startLng = initialCoordinates?.longitude || DEFAULT_CENTER.lng;

    const map = L.map(mapContainerRef.current, {
      center: [startLat, startLng],
      zoom: 15,
      zoomControl: true,
      attributionControl: false
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19
    }).addTo(map);

    const pinIcon = createCustomPinIcon();
    const marker = L.marker([startLat, startLng], {
      draggable: true,
      icon: pinIcon
    }).addTo(map);

    // Initial default coordinates
    if (!initialCoordinates) {
      setCoordinates({ latitude: startLat, longitude: startLng });
    }

    // Marker drag updates coordinates
    marker.on('dragend', () => {
      const pos = marker.getLatLng();
      setCoordinates({ latitude: pos.lat, longitude: pos.lng });
      setFormError(null);
    });

    // Clicking map also places the marker
    map.on('click', (e) => {
      marker.setLatLng(e.latlng);
      setCoordinates({ latitude: e.latlng.lat, longitude: e.latlng.lng });
      setFormError(null);
    });

    mapInstanceRef.current = map;
    markerRef.current = marker;

    // Recalculate dimensions once modal is rendered
    const timer1 = setTimeout(() => {
      if (mapInstanceRef.current) {
        mapInstanceRef.current.invalidateSize();
      }
    }, 100);

    const timer2 = setTimeout(() => {
      if (mapInstanceRef.current) {
        mapInstanceRef.current.invalidateSize();
      }
    }, 350);

    return () => {
      clearTimeout(timer1);
      clearTimeout(timer2);
      map.remove();
      mapInstanceRef.current = null;
      markerRef.current = null;
    };
  }, []);

  // Browser Geolocation trigger
  const handleUseCurrentLocation = () => {
    if (!navigator.geolocation) {
      setGeoError('Geolocation is not supported by your browser.');
      return;
    }

    setGeoLoading(true);
    setGeoError(null);
    setGeoWarning(null);

    navigator.geolocation.getCurrentPosition(
      (position) => {
        setGeoLoading(false);
        const { latitude, longitude, accuracy } = position.coords;

        setCoordinates({ latitude, longitude });
        setGeoAccuracy(accuracy);
        setFormError(null);

        // Accuracy warning
        if (accuracy > 100) {
          setGeoWarning(
            `Your location may not be precise (~${Math.round(accuracy)}m). Please adjust the pin on the map or move outdoors.`
          );
        } else {
          setGeoWarning(null);
        }

        // Center map, move marker, and recalculate Leaflet tiles
        if (mapInstanceRef.current && markerRef.current) {
          mapInstanceRef.current.setView([latitude, longitude], 16);
          markerRef.current.setLatLng([latitude, longitude]);
          setTimeout(() => {
            if (mapInstanceRef.current) {
              mapInstanceRef.current.invalidateSize();
            }
          }, 150);
        }
      },
      (error) => {
        setGeoLoading(false);
        if (error.code === 1) {
          // PERMISSION_DENIED
          setGeoError(
            'Location permission was denied. You can manually place the pin on the map.'
          );
        } else if (error.code === 3) {
          // TIMEOUT
          setGeoError('Location request timed out. You can manually place the pin on the map.');
        } else {
          // POSITION_UNAVAILABLE
          setGeoError('Unable to determine location. You can manually place the pin on the map.');
        }
      },
      {
        enableHighAccuracy: true,
        timeout: 10000,
        maximumAge: 0
      }
    );
  };

  const handleSubmit = (e) => {
    e?.preventDefault();

    // Coordinates are authoritative
    if (!coordinates || typeof coordinates.latitude !== 'number' || typeof coordinates.longitude !== 'number') {
      setFormError('Please select your delivery location using the map or "Use My Current Location".');
      return;
    }

    const trimmedAddress = address.trim();
    const trimmedLandmark = landmark.trim();

    setFormError(null);
    onSave({
      address: trimmedAddress,
      landmark: trimmedLandmark || null,
      coordinates
    });
  };

  return (
    <div
      className="modal-wrap"
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 9999,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '16px',
        backgroundColor: 'rgba(0, 0, 0, 0.6)',
        backdropFilter: 'blur(3px)'
      }}
    >
      <button
        className="modal-backdrop"
        onClick={onClose}
        aria-label="Close location picker"
        style={{ position: 'absolute', inset: 0, background: 'transparent', border: 'none', cursor: 'pointer' }}
      />
      <div
        className="auth-modal"
        style={{
          position: 'relative',
          zIndex: 1,
          width: 'min(540px, 96vw)',
          maxHeight: '92vh',
          display: 'flex',
          flexDirection: 'column',
          overflowY: 'auto',
          padding: '24px',
          background: '#ffffff',
          borderRadius: '16px',
          boxShadow: '0 20px 50px rgba(0, 0, 0, 0.25)',
          border: '1px solid #e2e8f0'
        }}
      >
        <button
          className="close-button modal-close"
          onClick={onClose}
          aria-label="Close"
          style={{
            position: 'absolute',
            top: '16px',
            right: '16px',
            background: '#f1f5f9',
            border: 'none',
            borderRadius: '50%',
            width: '32px',
            height: '32px',
            fontSize: '20px',
            lineHeight: 1,
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#64748b'
          }}
        >
          ×
        </button>

        <span style={{ fontSize: '10px', textTransform: 'uppercase', letterSpacing: '0.1em', color: '#64748b', fontWeight: 700 }}>
          DELIVERY DESTINATION
        </span>
        <h2 style={{ fontSize: '20px', fontWeight: 800, margin: '4px 0 16px', color: '#0f172a', letterSpacing: '-0.4px' }}>
          Choose delivery location
        </h2>

        {/* Primary Action 1: Use Current Location button */}
        <div style={{ marginBottom: '14px' }}>
          <button
            type="button"
            onClick={handleUseCurrentLocation}
            disabled={geoLoading}
            style={{
              width: '100%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '8px',
              padding: '12px 18px',
              background: '#245b3b',
              color: '#ffffff',
              border: 'none',
              borderRadius: '10px',
              fontSize: '13px',
              fontWeight: 700,
              cursor: geoLoading ? 'wait' : 'pointer',
              boxShadow: '0 2px 6px rgba(36, 91, 59, 0.25)',
              transition: 'background 0.2s'
            }}
          >
            <span>📍</span>
            <span>{geoLoading ? 'Detecting your location…' : 'Use my current location'}</span>
          </button>
        </div>

        {/* Geolocation feedback / error message */}
        {geoError && (
          <div
            style={{
              background: '#fef2f2',
              border: '1px solid #fecaca',
              borderRadius: '8px',
              padding: '10px 14px',
              marginBottom: '12px',
              color: '#991b1b',
              fontSize: '12px',
              lineHeight: 1.5,
              display: 'flex',
              alignItems: 'flex-start',
              justifyContent: 'space-between',
              gap: '8px'
            }}
          >
            <div>
              <b>Location note:</b> {geoError}
            </div>
            {geoError.includes('timed out') || geoError.includes('Unable to determine') ? (
              <button
                type="button"
                onClick={handleUseCurrentLocation}
                style={{
                  background: '#fee2e2',
                  border: '1px solid #fca5a5',
                  borderRadius: '6px',
                  padding: '4px 8px',
                  fontSize: '11px',
                  color: '#7f1d1d',
                  fontWeight: 600,
                  cursor: 'pointer',
                  flexShrink: 0
                }}
              >
                Retry
              </button>
            ) : null}
          </div>
        )}

        {/* Accuracy Warning */}
        {geoWarning && (
          <div
            style={{
              background: '#fffbeb',
              border: '1px solid #fde68a',
              borderRadius: '8px',
              padding: '10px 14px',
              marginBottom: '12px',
              color: '#92400e',
              fontSize: '12px',
              lineHeight: 1.5
            }}
          >
            <b>Notice:</b> {geoWarning}
          </div>
        )}

        {/* Leaflet Map Frame */}
        <div style={{ position: 'relative', marginBottom: '8px' }}>
          <div
            ref={mapContainerRef}
            data-testid="leaflet-map-container"
            style={{
              width: '100%',
              height: '260px',
              borderRadius: '12px',
              border: '1px solid #cbd5e1',
              overflow: 'hidden',
              background: '#f1f5f9'
            }}
            role="region"
            aria-label="Delivery Location Map"
          />
        </div>

        {/* Location coordinates readout */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            background: '#f8fafc',
            border: '1px solid #e2e8f0',
            borderRadius: '8px',
            padding: '8px 12px',
            marginBottom: '14px'
          }}
        >
          <div style={{ fontSize: '11px', color: '#64748b' }}>
            <span style={{ fontWeight: 600, color: '#334155' }}>Location selected: </span>
            {coordinates ? (
              <span style={{ fontFamily: 'monospace', color: '#0f172a', fontWeight: 600 }}>
                {coordinates.latitude.toFixed(5)}, {coordinates.longitude.toFixed(5)}
              </span>
            ) : (
              <span style={{ color: '#dc2626' }}>No coordinates selected</span>
            )}
          </div>
          <span style={{ fontSize: '11px', fontWeight: 700, color: coordinates ? '#16a34a' : '#dc2626' }}>
            {coordinates ? '✓ Pinned' : '⚠ Required'}
          </span>
        </div>

        <small
          style={{
            display: 'block',
            color: '#64748b',
            fontSize: '11px',
            marginBottom: '14px',
            textAlign: 'center'
          }}
        >
          Drag the pin or tap anywhere on the map to place your delivery spot.
        </small>

        {/* Additional Optional Details Form */}
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          <div>
            <label
              style={{
                display: 'block',
                fontSize: '12px',
                fontWeight: 700,
                color: '#334155',
                marginBottom: '5px'
              }}
            >
              Additional delivery details <span style={{ color: '#64748b', fontWeight: 400 }}>(optional)</span>
            </label>
            <input
              type="text"
              value={landmark}
              onChange={(e) => {
                setLandmark(e.target.value);
                setFormError(null);
              }}
              placeholder="Call me near the gate / landmark / room details..."
              style={{
                width: '100%',
                height: '42px',
                border: '1px solid #cbd5e1',
                borderRadius: '8px',
                padding: '0 12px',
                fontSize: '12px',
                boxSizing: 'border-box'
              }}
            />
          </div>

          {/* Validation Error */}
          {formError && (
            <div
              style={{
                background: '#fef2f2',
                border: '1px solid #fecaca',
                borderRadius: '8px',
                padding: '8px 12px',
                color: '#b91c1c',
                fontSize: '12px',
                fontWeight: 600
              }}
            >
              {formError}
            </div>
          )}

          <button
            type="submit"
            className="checkout-button"
            style={{
              marginTop: '4px',
              justifyContent: 'center',
              fontWeight: 700,
              fontSize: '13px',
              background: '#245b3b',
              color: '#ffffff',
              minHeight: '44px',
              borderRadius: '10px'
            }}
          >
            Confirm Location
          </button>
        </form>
      </div>
    </div>
  );
}
