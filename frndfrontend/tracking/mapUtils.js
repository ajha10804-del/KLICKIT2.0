
const L = window.L;

if (!L) {
  throw new Error(
    'Leaflet failed to load. Check the Leaflet CDN tags in index.html.'
  );
}

export const customerIcon = L.divIcon({
  className: 'klickit-map-marker-shell',
  html: `
    <div class="klickit-map-marker customer">
      <span>📍</span>
    </div>
  `,
  iconSize: [38, 38],
  iconAnchor: [19, 34],
  popupAnchor: [0, -32]
});

export const riderIcon = L.divIcon({
  className: 'klickit-map-marker-shell',
  html: `
    <div class="klickit-map-marker rider">
      <span>🚴</span>
    </div>
  `,
  iconSize: [38, 38],
  iconAnchor: [19, 34],
  popupAnchor: [0, -32]
});

export function createMap(node) {
  const map = L.map(node, {
    zoomControl: true,
    attributionControl: true
  }).setView([20.5937, 78.9629], 5);

  L.tileLayer(
    'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
    {
      maxZoom: 19,
      attribution: '&copy; OpenStreetMap contributors'
    }
  ).addTo(map);

  window.setTimeout(() => {
    map.invalidateSize();
  }, 0);

  return map;
}

export function fitTrackingBounds(
  map,
  customer,
  rider
) {
  if (!map) return;

  const points = [];

  if (customer) {
    points.push([
      customer.latitude,
      customer.longitude
    ]);
  }

  if (rider) {
    points.push([
      rider.latitude,
      rider.longitude
    ]);
  }

  if (points.length === 2) {
    map.fitBounds(points, {
      padding: [40, 40],
      maxZoom: 16
    });
  } else if (points.length === 1) {
    map.setView(points[0], 16);
  }
}
