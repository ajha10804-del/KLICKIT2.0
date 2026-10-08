import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';

import './styles.css';

import AdminDashboard from './admin and deliverydashboard/AdminDashboard.jsx';
import DeliveryDashboard from './admin and deliverydashboard/DeliveryDashboard.jsx';
import OtpLoginForm from './OtpLoginForm.jsx';

const API = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');

const categories = [
  { name: 'All', icon: '✦', tint: '#fff4ce' },
  { name: 'Fruits & Veg', icon: '🥑', tint: '#e5f4df' },
  { name: 'Dairy & Eggs', icon: '🥛', tint: '#e5f1ff' },
  { name: 'Munchies', icon: '🍿', tint: '#fff0df' },
  { name: 'Cold Drinks', icon: '🥤', tint: '#fce4e8' },
  { name: 'Bakery', icon: '🍞', tint: '#f9ead7' },
  { name: 'Staples', icon: '🌾', tint: '#f5eddb' },
  { name: 'Personal Care', icon: '🧴', tint: '#eee7ff' },
  { name: 'Home Care', icon: '🧹', tint: '#e5f5f3' },
  { name: 'Instant Food', icon: '🍜', tint: '#ffe9dd' }
];

const seedProducts = [
  {
    id: 1,
    name: 'Fresh Bananas',
    description: 'Robusta • 4–6 pcs',
    price: 42,
    mrp: 55,
    category: 'Fruits & Veg',
    image:
      'https://images.unsplash.com/photo-1571771894821-ce9b6c11b08e?auto=format&fit=crop&w=480&q=85',
    time: '8 mins'
  },
  {
    id: 2,
    name: 'Farm Fresh Eggs',
    description: 'Pack of 6 • Protein rich',
    price: 58,
    mrp: 68,
    category: 'Dairy & Eggs',
    image:
      'https://images.unsplash.com/photo-1506976785307-8732e854ad03?auto=format&fit=crop&w=480&q=85',
    time: '8 mins'
  },
  {
    id: 3,
    name: 'Amul Taaza Milk',
    description: 'Toned milk • 500 ml',
    price: 29,
    mrp: 32,
    category: 'Dairy & Eggs',
    image:
      'https://images.unsplash.com/photo-1563636619-e9143da7973b?auto=format&fit=crop&w=480&q=85',
    time: '8 mins'
  },
  {
    id: 4,
    name: 'Classic Potato Chips',
    description: 'Classic salted • pack',
    price: 20,
    mrp: 20,
    category: 'Munchies',
    image:
      'https://images.unsplash.com/photo-1566478989037-eec170784d0b?auto=format&fit=crop&w=480&q=85',
    time: '9 mins'
  },
  {
    id: 5,
    name: 'Red Apples',
    description: 'Crisp & juicy • 4 pcs',
    price: 119,
    mrp: 149,
    category: 'Fruits & Veg',
    image:
      'https://images.unsplash.com/photo-1560806887-1e4cd0b6cbd6?auto=format&fit=crop&w=480&q=85',
    time: '8 mins'
  },
  {
    id: 6,
    name: 'Orange Juice',
    description: 'Real fruit • 1 L',
    price: 110,
    mrp: 125,
    category: 'Cold Drinks',
    image:
      'https://images.unsplash.com/photo-1600271886742-f049cd451bba?auto=format&fit=crop&w=480&q=85',
    time: '10 mins'
  },
  {
    id: 7,
    name: 'Sourdough Bread',
    description: 'Freshly baked • 400 g',
    price: 65,
    mrp: 75,
    category: 'Bakery',
    image:
      'https://images.unsplash.com/photo-1585478259715-876acc5be8eb?auto=format&fit=crop&w=480&q=85',
    time: '12 mins'
  },
  {
    id: 8,
    name: 'Basmati Rice',
    description: 'Premium long grain • 1 kg',
    price: 99,
    mrp: 125,
    category: 'Staples',
    image:
      'https://images.unsplash.com/photo-1586201375761-83865001e31c?auto=format&fit=crop&w=480&q=85',
    time: '10 mins'
  },
  {
    id: 9,
    name: 'Instant Noodles',
    description: 'Masala noodles • pack of 4',
    price: 56,
    mrp: 60,
    category: 'Instant Food',
    image:
      'https://images.unsplash.com/photo-1569718212165-3a8278d5f624?auto=format&fit=crop&w=480&q=85',
    time: '9 mins'
  },
  {
    id: 10,
    name: 'Dishwash Liquid',
    description: 'Lemon fresh • 500 ml',
    price: 99,
    mrp: 120,
    category: 'Home Care',
    image:
      'https://images.unsplash.com/photo-1585421514738-01798e348b17?auto=format&fit=crop&w=480&q=85',
    time: '10 mins'
  },
  {
    id: 11,
    name: 'Gentle Face Wash',
    description: 'Daily care • 100 g',
    price: 149,
    mrp: 175,
    category: 'Personal Care',
    image:
      'https://images.unsplash.com/photo-1556229010-6c3f2c9ca5f8?auto=format&fit=crop&w=480&q=85',
    time: '10 mins'
  },
  {
    id: 12,
    name: 'Green Capsicum',
    description: 'Fresh • 250 g',
    price: 24,
    mrp: 30,
    category: 'Fruits & Veg',
    image:
      'https://images.unsplash.com/photo-1563565375-f3fdfdbefa83?auto=format&fit=crop&w=480&q=85',
    time: '8 mins'
  }
];

const money = (n) => `₹${Number(n || 0).toFixed(0)}`;

const UUID_REGEX =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function isTokenExpired(token) {
  if (!token) return true;

  try {
    const parts = token.split('.');

    if (parts.length !== 3) return true;

    const payload = JSON.parse(
      atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'))
    );

    if (!payload.exp) return false;

    return Date.now() >= payload.exp * 1000;
  } catch {
    return true;
  }
}

function isValidDeliveryAddress(addr) {
  if (!addr || typeof addr !== 'string') return false;

  const trimmed = addr.trim();

  if (trimmed.length < 10) return false;

  if (/^indore(,\s*madhya\s*pradesh)?$/i.test(trimmed)) {
    return false;
  }

  return true;
}

function isValidPhoneNumber(ph) {
  if (!ph || typeof ph !== 'string') return false;

  const trimmed = ph.trim();

  if (trimmed.length < 7 || trimmed.length > 20) return false;

  if (!/^[+0-9\-()\s]{7,20}$/.test(trimmed)) return false;

  if (trimmed === '9876543210' || trimmed === '+919876543210') {
    return false;
  }

  return true;
}

/* -------------------------------------------------------------------------- */
/* ACCESS BARRIERS                                                            */
/* -------------------------------------------------------------------------- */

function AdminAccessBarrier({ user, onSignIn, onBackToStore }) {
  const isWrongRole = user && user.role !== 'ADMIN';

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#f8faf9',
        padding: '20px',
        fontFamily: 'Inter, sans-serif'
      }}
    >
      <div
        style={{
          maxWidth: '440px',
          width: '100%',
          background: '#fff',
          borderRadius: '16px',
          padding: '32px',
          boxShadow: '0 10px 25px -5px rgba(0,0,0,0.06)',
          textAlign: 'center',
          border: '1px solid #e5e7eb'
        }}
      >
        <div
          style={{
            width: '56px',
            height: '56px',
            borderRadius: '50%',
            background: '#fee2e2',
            color: '#dc2626',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '24px',
            margin: '0 auto 16px'
          }}
        >
          🛡️
        </div>

        <h2
          style={{
            fontSize: '20px',
            fontWeight: '700',
            color: '#111827',
            marginBottom: '8px'
          }}
        >
          {isWrongRole
            ? 'Access Denied (403 Forbidden)'
            : 'Admin Authentication Required'}
        </h2>

        <p
          style={{
            fontSize: '13px',
            color: '#6b7280',
            lineHeight: '1.5',
            marginBottom: '24px'
          }}
        >
          {isWrongRole
            ? `You are currently signed in as "${
                user.name || user.email
              }" with role "${user.role || 'CUSTOMER'}". You do not have permission to access the Administrator Portal.`
            : 'You must be signed in with an authorized Administrator account to view and manage store operations.'}
        </p>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <button
            onClick={onSignIn}
            style={{
              width: '100%',
              padding: '12px',
              background: '#0c831f',
              color: '#fff',
              border: 'none',
              borderRadius: '8px',
              fontWeight: '600',
              fontSize: '13px',
              cursor: 'pointer'
            }}
          >
            {isWrongRole
              ? 'Switch to Admin Account'
              : 'Sign In as Administrator'}
          </button>

          <button
            onClick={onBackToStore}
            style={{
              width: '100%',
              padding: '12px',
              background: '#f3f4f6',
              color: '#374151',
              border: '1px solid #e5e7eb',
              borderRadius: '8px',
              fontWeight: '600',
              fontSize: '13px',
              cursor: 'pointer'
            }}
          >
            Return to Customer Storefront
          </button>
        </div>
      </div>
    </div>
  );
}

function DeliveryAccessBarrier({ user, onSignIn, onBackToStore }) {
  const isWrongRole = user && user.role !== 'DELIVERY_PARTNER';

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#f8faf9',
        padding: '20px',
        fontFamily: 'Inter, sans-serif'
      }}
    >
      <div
        style={{
          maxWidth: '440px',
          width: '100%',
          background: '#fff',
          borderRadius: '16px',
          padding: '32px',
          boxShadow: '0 10px 25px -5px rgba(0,0,0,0.06)',
          textAlign: 'center',
          border: '1px solid #e5e7eb'
        }}
      >
        <div
          style={{
            width: '56px',
            height: '56px',
            borderRadius: '50%',
            background: '#ede9fe',
            color: '#7c3aed',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '24px',
            margin: '0 auto 16px'
          }}
        >
          🚴
        </div>

        <h2
          style={{
            fontSize: '20px',
            fontWeight: '700',
            color: '#111827',
            marginBottom: '8px'
          }}
        >
          {isWrongRole
            ? 'Access Denied (403 Forbidden)'
            : 'Driver Authentication Required'}
        </h2>

        <p
          style={{
            fontSize: '13px',
            color: '#6b7280',
            lineHeight: '1.5',
            marginBottom: '24px'
          }}
        >
          {isWrongRole
            ? `You are currently signed in as "${
                user.name || user.email
              }" with role "${user.role || 'CUSTOMER'}". You do not have permission to access the Delivery Partner Portal.`
            : 'You must be signed in with a registered Delivery Partner account to view assigned orders and update delivery status.'}
        </p>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <button
            onClick={onSignIn}
            style={{
              width: '100%',
              padding: '12px',
              background: '#7c3aed',
              color: '#fff',
              border: 'none',
              borderRadius: '8px',
              fontWeight: '600',
              fontSize: '13px',
              cursor: 'pointer'
            }}
          >
            {isWrongRole
              ? 'Switch to Driver Account'
              : 'Sign In as Delivery Partner'}
          </button>

          <button
            onClick={onBackToStore}
            style={{
              width: '100%',
              padding: '12px',
              background: '#f3f4f6',
              color: '#374151',
              border: '1px solid #e5e7eb',
              borderRadius: '8px',
              fontWeight: '600',
              fontSize: '13px',
              cursor: 'pointer'
            }}
          >
            Return to Customer Storefront
          </button>
        </div>
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* APP                                                                         */
/* -------------------------------------------------------------------------- */

function App() {
  const [currentPath, setCurrentPath] = useState(
    () => window.location.pathname
  );
const [products, setProducts] = useState([]);
const [catalogError, setCatalogError] = useState(false);
const [catalogLoading, setCatalogLoading] = useState(true);
  const [activeCategory, setActiveCategory] = useState('All');
  const [search, setSearch] = useState('');

  const [cart, setCart] = useState(() => {
  try {
    const saved =
      localStorage.getItem('klickit_cart');

    return saved
      ? JSON.parse(saved)
      : {};
  } catch {
    return {};
  }
});
useEffect(() => {
  localStorage.setItem(
    'klickit_cart',
    JSON.stringify(cart)
  );
}, [cart]);
  const [cartOpen, setCartOpen] = useState(false);

  const [authOpen, setAuthOpen] = useState(false);
  const [authMode, setAuthMode] = useState('login');

  const [user, setUser] = useState(() => {
    try {
      const saved = JSON.parse(
        localStorage.getItem('klickit_user') || 'null'
      );

      if (saved && !isValidPhoneNumber(saved.phone)) {
        saved.phone = '';
      }

      return saved;
    } catch {
      return null;
    }
  });

  const [toast, setToast] = useState('');

  const [address, setAddress] = useState(() => {
    const saved = localStorage.getItem('klickit_address') || '';

    return isValidDeliveryAddress(saved) ? saved : '';
  });

  const [addressOpen, setAddressOpen] = useState(false);

  const [loading, setLoading] = useState(false);

  const [editingPhone, setEditingPhone] = useState(false);
  const [locationLoading, setLocationLoading] =
  useState(false);

  const [locationError, setLocationError] =
  useState('');
  const [phoneInput, setPhoneInput] = useState('');

  const [activeOrder, setActiveOrder] = useState(null);
  const [trackingOpen, setTrackingOpen] = useState(false);
  const [trackingLoading, setTrackingLoading] = useState(false);
  const [trackingError, setTrackingError] = useState(null);

  const [accountOpen, setAccountOpen] = useState(false);
  const [accountTab, setAccountTab] = useState('profile');

  const [orderHistory, setOrderHistory] = useState([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState(null);

  /* ------------------------------------------------------------------------ */
  /* ROUTING                                                                   */
  /* ------------------------------------------------------------------------ */

  async function useCurrentLocation() {
  setLocationError('');

  if (!navigator.geolocation) {
    setLocationError(
      'Location is not supported by this browser. Please enter your address manually.'
    );
    return;
  }

  setLocationLoading(true);

  navigator.geolocation.getCurrentPosition(
    async (position) => {
      try {
        const {
          latitude,
          longitude
        } = position.coords;

        const response = await fetch(
          `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${latitude}&lon=${longitude}`,
          {
            headers: {
              Accept:
                'application/json'
            }
          }
        );

        if (!response.ok) {
          throw new Error(
            'Reverse geocoding failed'
          );
        }

        const data =
          await response.json();

        const detectedAddress =
          data.display_name;

        if (!detectedAddress) {
          throw new Error(
            'Address not found'
          );
        }

        setAddress(
          detectedAddress
        );

        localStorage.setItem(
          'klickit_address',
          detectedAddress
        );

        setLocationError('');
        setAddressOpen(false);

        notify(
          'Current delivery location detected'
        );
      } catch {
        setLocationError(
          'We could not convert your location into an address. Please enter your address manually.'
        );
      } finally {
        setLocationLoading(false);
      }
    },

    (error) => {
      setLocationLoading(false);

      if (
        error.code ===
        error.PERMISSION_DENIED
      ) {
        setLocationError(
          'Location permission was denied. Please enter your address manually.'
        );
      } else if (
        error.code ===
        error.POSITION_UNAVAILABLE
      ) {
        setLocationError(
          'Your location is currently unavailable. Please enter your address manually.'
        );
      } else if (
        error.code ===
        error.TIMEOUT
      ) {
        setLocationError(
          'Location detection timed out. Please try again or enter your address manually.'
        );
      } else {
        setLocationError(
          'Unable to detect your location. Please enter your address manually.'
        );
      }
    },

    {
      enableHighAccuracy: true,
      timeout: 10000,
      maximumAge: 60000
    }
  );
}
  useEffect(() => {
    const handlePopState = () => {
      setCurrentPath(window.location.pathname);
    };

    window.addEventListener('popstate', handlePopState);

    return () => {
      window.removeEventListener('popstate', handlePopState);
    };
  }, []);

  function navigate(path) {
    if (window.location.pathname === path) return;

    window.history.pushState(null, '', path);
    setCurrentPath(path);
    window.scrollTo(0, 0);
  }

  /* ------------------------------------------------------------------------ */
  /* LOAD PRODUCTS                                                             */
  /* ------------------------------------------------------------------------ */
useEffect(() => {
  let cancelled = false;

  async function loadProducts() {
    setCatalogLoading(true);
    setCatalogError(false);

    try {
      const response = await fetch(`${API}/api/products`);

      if (!response.ok) {
        throw new Error('Could not load products');
      }

      const body = await response.json();

      const list = Array.isArray(body)
        ? body
        : Array.isArray(body?.data)
          ? body.data
          : Array.isArray(body?.content)
            ? body.content
            : Array.isArray(body?.products)
              ? body.products
              : [];

      const mappedProducts = list
        .filter((product) => product?.id || product?.productId)
        .map((product, index) => ({
          ...product,

          // IMPORTANT:
          // Keep the real PostgreSQL UUID.
          id: product.id ?? product.productId,

          name:
            product.name ??
            product.productName ??
            'Everyday essential',

          description:
            product.description ??
            product.unit ??
            'Quality you can trust',

          price: Number(
            product.price ??
              product.sellingPrice ??
              0
          ),

          mrp: Number(
            product.mrp ??
              product.originalPrice ??
              product.price ??
              0
          ),

          category:
            product.category ??
            'Staples',

          image:
            product.imageUrl ??
            product.image ??
            seedProducts[
              index % seedProducts.length
            ].image,

          time: '10 mins'
        }));

      if (cancelled) return;

      if (!mappedProducts.length) {
        throw new Error('No active products found');
      }

      setProducts(mappedProducts);
      setCatalogError(false);
    } catch (error) {
      if (cancelled) return;

      setProducts([]);
      setCatalogError(true);
    } finally {
      if (!cancelled) {
        setCatalogLoading(false);
      }
    }
  }

  loadProducts();

  return () => {
    cancelled = true;
  };
}, []);

  /* ------------------------------------------------------------------------ */
  /* PRODUCT FILTERING                                                         */
  /* ------------------------------------------------------------------------ */

  const filtered = useMemo(() => {
    return products.filter((product) => {
      const categoryMatch =
        activeCategory === 'All' ||
        product.category
          ?.toLowerCase()
          .includes(activeCategory.toLowerCase()) ||
        (activeCategory === 'Fruits & Veg' &&
          /fruit|vegetable|produce/i.test(
            product.category || ''
          ));

      const searchMatch =
        `${product.name} ${product.description} ${product.category}`
          .toLowerCase()
          .includes(search.toLowerCase());

      return categoryMatch && searchMatch;
    });
  }, [products, activeCategory, search]);

  /* ------------------------------------------------------------------------ */
  /* CART                                                                      */
  /* ------------------------------------------------------------------------ */

  const cartItems = products
    .filter((product) => cart[product.id] > 0)
    .map((product) => ({
      ...product,
      qty: cart[product.id]
    }));

  const cartCount = Object.values(cart).reduce(
    (total, quantity) => total + quantity,
    0
  );

  const subtotal = cartItems.reduce(
    (sum, product) =>
      sum + product.price * product.qty,
    0
  );

  const delivery =
    subtotal === 0 || subtotal >= 199
      ? 0
      : 25;

  const discount = cartItems.reduce(
    (sum, product) =>
      sum +
      Math.max(
        0,
        product.mrp - product.price
      ) *
        product.qty,
    0
  );

  const notify = (message) => {
    setToast(message);

    window.setTimeout(() => {
      setToast('');
    }, 2800);
  };

  const changeQty = (id, delta) => {
  if (!id) return;

  setCart((previous) => {
    const currentQuantity =
      Number(previous[id]) || 0;

    const nextQuantity =
      Math.max(
        0,
        currentQuantity + delta
      );

    const next = {
      ...previous
    };

    if (nextQuantity === 0) {
      delete next[id];
    } else {
      next[id] = nextQuantity;
    }

    return next;
  });
};
function completeLogin(data) {
  const profile = {
    userId: data.userId,
    name: data.name,
    email: data.email,
    phone: data.phone,
    role: data.role
  };

  localStorage.setItem('klickit_token', data.token);
  localStorage.setItem('klickit_user', JSON.stringify(profile));

  setUser(profile);
  setAuthOpen(false);
}
function logout() {
  localStorage.removeItem('klickit_token');
  localStorage.removeItem('klickit_user');

  setUser(null);
  setAuthOpen(false);
  setOtp('');
  setOtpSent(false);

  notify('Logged out successfully');
}
  /* ------------------------------------------------------------------------ */
  /* AUTH SESSION                                                              */
  /* ------------------------------------------------------------------------ */

  function handleAuthFailure(
    message = 'Session expired. Please sign in again.'
  ) {
    localStorage.removeItem('klickit_token');
    localStorage.removeItem('klickit_user');

    setUser(null);
    setAccountOpen(false);
    setCartOpen(false);

    setAuthMode('login');
    setAuthOpen(true);

    notify(message);
  }

  function completeLogin(data) {
    if (!data?.token) {
      notify('Login failed: server did not return a token.');
      return;
    }

    localStorage.setItem(
      'klickit_token',
      data.token
    );

    const profile = {
      id: data.userId,
      name:
        data.name ||
        (data.email || '').split('@')[0],
      email: data.email,
      phone: isValidPhoneNumber(data.phone)
        ? data.phone
        : '',
      role: data.role || 'CUSTOMER'
    };

    localStorage.setItem(
      'klickit_user',
      JSON.stringify(profile)
    );

    setUser(profile);
    setAuthOpen(false);

    notify(
      `Welcome${
        profile.name
          ? `, ${profile.name}`
          : ''
      }!`
    );

    if (profile.role === 'ADMIN') {
      navigate('/admin');
    } else if (
      profile.role === 'DELIVERY_PARTNER'
    ) {
      navigate('/delivery');
    } else if (currentPath !== '/') {
      navigate('/');
    }
  }

  function handleSignOut() {
    localStorage.removeItem('klickit_token');
    localStorage.removeItem('klickit_user');

    setUser(null);
    setAccountOpen(false);
    setEditingPhone(false);

    notify('Signed out successfully.');

    if (
      currentPath === '/admin' ||
      currentPath.startsWith('/admin/') ||
      currentPath === '/delivery' ||
      currentPath.startsWith('/delivery/')
    ) {
      navigate('/');
    }
  }

  /* ------------------------------------------------------------------------ */
  /* ORDER HISTORY                                                             */
  /* ------------------------------------------------------------------------ */

  async function fetchOrderHistory() {
    setHistoryLoading(true);
    setHistoryError(null);

    try {
      const token =
        localStorage.getItem('klickit_token');

      if (!token || isTokenExpired(token)) {
        handleAuthFailure(
          'Session expired. Please sign in again.'
        );
        return;
      }

      const response = await fetch(
        `${API}/api/orders`,
        {
          headers: {
            'Content-Type':
              'application/json',
            Authorization: `Bearer ${token}`
          }
        }
      );

      if (response.status === 401) {
        handleAuthFailure(
          'Session expired. Please sign in again.'
        );
        return;
      }

      const body =
        await response.json().catch(
          () => ({})
        );

      if (response.status === 403) {
        throw new Error(
          body.message || 'Access denied.'
        );
      }

      if (
        !response.ok ||
        !body.success ||
        !Array.isArray(body.data)
      ) {
        throw new Error(
          body.message ||
            'Could not load your order history.'
        );
      }

      setOrderHistory(body.data);
    } catch (error) {
      setHistoryError(
        error.message === 'Failed to fetch'
          ? 'Network error: could not load orders.'
          : error.message
      );
    } finally {
      setHistoryLoading(false);
    }
  }

  function openAccount(tab = 'profile') {
    if (!user) {
      setAuthMode('login');
      setAuthOpen(true);
      return;
    }

    setAccountTab(tab);
    setAccountOpen(true);

    if (tab === 'history') {
      fetchOrderHistory();
    }
  }

  /* ------------------------------------------------------------------------ */
  /* ORDER TRACKING                                                            */
  /* ------------------------------------------------------------------------ */

  async function fetchOrder(orderId) {
    if (!orderId) return;

    setTrackingLoading(true);
    setTrackingError(null);

    try {
      const token =
        localStorage.getItem('klickit_token');

      if (token && isTokenExpired(token)) {
        handleAuthFailure(
          'Session expired. Please sign in again.'
        );
        return;
      }

      const response = await fetch(
        `${API}/api/orders/${orderId}`,
        {
          headers: {
            'Content-Type':
              'application/json',
            ...(token
              ? {
                  Authorization: `Bearer ${token}`
                }
              : {})
          }
        }
      );

      if (response.status === 401) {
        handleAuthFailure(
          'Session expired. Please sign in again.'
        );
        return;
      }

      const body =
        await response.json().catch(
          () => ({})
        );

      if (response.status === 404) {
        throw new Error(
          body.message || 'Order not found.'
        );
      }

      if (response.status === 403) {
        throw new Error(
          body.message ||
            'You are not authorized to view this order.'
        );
      }

      if (
        !response.ok ||
        !body.success ||
        !body.data
      ) {
        throw new Error(
          body.message ||
            'Could not load order details.'
        );
      }

      setActiveOrder(body.data);
    } catch (error) {
      setTrackingError(
        error.message === 'Failed to fetch'
          ? 'Network error: could not load order.'
          : error.message
      );
    } finally {
      setTrackingLoading(false);
    }
  }

  function openTracking(order) {
    setActiveOrder(order);
    setTrackingError(null);
    setTrackingOpen(true);

    if (order?.id) {
      window.history.replaceState(
        null,
        '',
        `#order-${order.id}`
      );
    }
  }

  function closeTracking() {
    setTrackingOpen(false);

    if (
      window.location.hash.startsWith(
        '#order-'
      )
    ) {
      window.history.replaceState(
        null,
        '',
        window.location.pathname
      );
    }
  }

  /* ------------------------------------------------------------------------ */
  /* OPEN TRACKING FROM URL                                                    */
  /* ------------------------------------------------------------------------ */

  useEffect(() => {
    const hash = window.location.hash;

    if (hash.startsWith('#order-')) {
      const orderId = hash
        .replace('#order-', '')
        .trim();

      if (orderId) {
        setTrackingOpen(true);
        fetchOrder(orderId);
      }
    }
  }, []);

  /* ------------------------------------------------------------------------ */
  /* TRACKING POLLING                                                          */
  /* ------------------------------------------------------------------------ */

  useEffect(() => {
    if (
      !trackingOpen ||
      !activeOrder?.id
    ) {
      return undefined;
    }

    const refreshTrackingOrder =
      async () => {
        try {
          const token =
            localStorage.getItem(
              'klickit_token'
            );

          if (
            !token ||
            isTokenExpired(token)
          ) {
            return;
          }

          const response = await fetch(
            `${API}/api/orders/${activeOrder.id}`,
            {
              headers: {
                'Content-Type':
                  'application/json',
                Authorization: `Bearer ${token}`
              }
            }
          );

          if (!response.ok) return;

          const body =
            await response.json().catch(
              () => ({})
            );

          if (
            body.success &&
            body.data
          ) {
            setActiveOrder(body.data);
          }
        } catch {
          // Keep previous tracking state.
        }
      };

    const timer = window.setInterval(
      refreshTrackingOrder,
      4000
    );

    return () => {
      window.clearInterval(timer);
    };
  }, [
    trackingOpen,
    activeOrder?.id
  ]);

  /* ------------------------------------------------------------------------ */
  /* CHECKOUT                                                                  */
  /* ------------------------------------------------------------------------ */

  async function checkout() {
    if (!cartCount) return;

    const token =
      localStorage.getItem('klickit_token');

    if (
      !user ||
      !token ||
      isTokenExpired(token)
    ) {
      handleAuthFailure(
        user
          ? 'Session expired. Please sign in again.'
          : 'Please sign in to place your order.'
      );
      return;
    }

    if (!isValidDeliveryAddress(address)) {
      setAddressOpen(true);

      notify(
        'Please enter a valid delivery address (at least 10 characters).'
      );

      return;
    }

    const customerPhone =
      user.phone?.trim();

    if (!isValidPhoneNumber(customerPhone)) {
      openAccount('profile');
      setEditingPhone(true);

      notify(
        'Please provide a valid phone number before placing your order.'
      );

      return;
    }

    const invalidItems = cartItems.filter(
  (product) => {
    const id = String(
      product.id || ''
    ).trim();

    return !UUID_REGEX.test(id);
  }
);

if (invalidItems.length > 0) {
  notify(
    'One or more cart items are invalid. Please refresh the catalog and add the item again.'
  );

  return;
}

    if (invalidItems.length > 0) {
      notify(
        'Some items in your cart are not from the active catalog. Please refresh to load live products.'
      );

      return;
    }

    setLoading(true);

    try {
      const sessionStorageKey =
  'klickit_cart_session_id';

let sessionId =
  localStorage.getItem(
    sessionStorageKey
  );

if (!sessionId) {
  sessionId =
    'cart_' +
    (
      window.crypto?.randomUUID
        ? window.crypto.randomUUID()
        : Math.random()
            .toString(36)
            .substring(2) +
          Date.now().toString(36)
    );

  localStorage.setItem(
    sessionStorageKey,
    sessionId
  );
}
      for (const product of cartItems) {
        const addResponse =
          await fetch(
            `${API}/api/cart/add`,
            {
              method: 'POST',
              headers: {
                'Content-Type':
                  'application/json'
              },
              body: JSON.stringify({
                sessionId,
                productId: product.id,
                quantity: product.qty
              })
            }
          );

        if (!addResponse.ok) {
          const errorBody =
            await addResponse
              .json()
              .catch(() => ({}));

          throw new Error(
            errorBody.message ||
              `Failed to add item (${product.name}) to server cart.`
          );
        }
      }

      const checkoutResponse =
        await fetch(
          `${API}/api/orders/checkout`,
          {
            method: 'POST',
            headers: {
              'Content-Type':
                'application/json',
              Authorization: `Bearer ${token}`
            },
            body: JSON.stringify({
              sessionId,
              customerName:
                user.name || 'Customer',
              customerPhone,
              customerAddress:
                address.trim()
            })
          }
        );

      if (
        checkoutResponse.status === 401
      ) {
        handleAuthFailure(
          'Session expired. Please sign in again.'
        );
        return;
      }

      const checkoutBody =
        await checkoutResponse
          .json()
          .catch(() => ({}));

      if (
        !checkoutResponse.ok ||
        !checkoutBody.success
      ) {
        throw new Error(
          checkoutBody.message ||
            'Checkout failed. Please try again.'
        );
      }

      const placedOrder =
        checkoutBody.data;

      setCart({});
      setCartOpen(false);

      notify(
        'Order placed successfully! Tracking your delivery…'
      );

      openTracking(placedOrder);
    } catch (error) {
      notify(
        error.message === 'Failed to fetch'
          ? 'Could not connect to the server. Check your backend URL.'
          : error.message
      );
    } finally {
      setLoading(false);
    }
  }

  /* ------------------------------------------------------------------------ */
  /* ORDER STATUS                                                              */
  /* ------------------------------------------------------------------------ */

  const lifecycleStages = [
    'PLACED',
    'READY_TO_ASSIGN',
    'ASSIGNED',
    'OUT_FOR_DELIVERY',
    'DELIVERED'
  ];

  const stageLabels = {
    PLACED: 'Order Placed',
    READY_TO_ASSIGN:
      'Approved (Preparing)',
    ASSIGNED: 'Driver Assigned',
    OUT_FOR_DELIVERY:
      'Out for Delivery',
    DELIVERED: 'Delivered'
  };

  const currentStatus =
    activeOrder?.status;

  const isCancelled =
    currentStatus === 'CANCELLED';

  const isRejected =
    currentStatus === 'REJECTED';

  const currentStageIndex =
    lifecycleStages.indexOf(
      currentStatus
    );

  /* ------------------------------------------------------------------------ */
  /* ADMIN ROUTE                                                               */
  /* ------------------------------------------------------------------------ */

  if (
    currentPath === '/admin' ||
    currentPath.startsWith('/admin/')
  ) {
    if (user?.role === 'ADMIN') {
      return (
        <>
          <AdminDashboard
            user={user}
            onSignOut={handleSignOut}
            onNavigateStore={() =>
              navigate('/')
            }
            notify={notify}
          />

          {toast && (
            <div className="toast">
              <span>✦</span>
              {toast}
            </div>
          )}
        </>
      );
    }

    return (
      <>
        <AdminAccessBarrier
          user={user}
          onSignIn={() => {
            setAuthMode('login');
            setAuthOpen(true);
          }}
          onBackToStore={() =>
            navigate('/')
          }
        />

        {authOpen && (
          <div className="modal-wrap">
            <button
              className="modal-backdrop"
              onClick={() =>
                setAuthOpen(false)
              }
              aria-label="Close sign in"
            />

            <div className="auth-modal">
              <button
                className="close-button modal-close"
                onClick={() =>
                  setAuthOpen(false)
                }
              >
                ×
              </button>

              <div className="auth-brand">
                k<span>!</span>
              </div>

              <span className="section-kicker">
                ADMINISTRATOR SIGN IN
              </span>

              <h2>
                Sign in to Admin Panel
              </h2>

              <p>
                We'll email you a one-time
                code. Only accounts with the
                administrator role can open
                this portal.
              </p>

              <OtpLoginForm
                api={API}
                emailPlaceholder="Admin email address"
                submitLabel="Verify & sign in"
                onAuthenticated={
                  completeLogin
                }
              />
            </div>
          </div>
        )}

        {toast && (
          <div className="toast">
            <span>✦</span>
            {toast}
          </div>
        )}
      </>
    );
  }

  /* ------------------------------------------------------------------------ */
  /* DELIVERY ROUTE                                                            */
  /* ------------------------------------------------------------------------ */

  if (
    currentPath === '/delivery' ||
    currentPath.startsWith('/delivery/')
  ) {
    if (
      user?.role ===
      'DELIVERY_PARTNER'
    ) {
      return (
        <>
          <DeliveryDashboard
            user={user}
            onSignOut={handleSignOut}
            onNavigateStore={() =>
              navigate('/')
            }
            notify={notify}
          />

          {toast && (
            <div className="toast">
              <span>✦</span>
              {toast}
            </div>
          )}
        </>
      );
    }

    return (
      <>
        <DeliveryAccessBarrier
          user={user}
          onSignIn={() => {
            setAuthMode('login');
            setAuthOpen(true);
          }}
          onBackToStore={() =>
            navigate('/')
          }
        />

        {authOpen && (
          <div className="modal-wrap">
            <button
              className="modal-backdrop"
              onClick={() =>
                setAuthOpen(false)
              }
              aria-label="Close sign in"
            />

            <div className="auth-modal">
              <button
                className="close-button modal-close"
                onClick={() =>
                  setAuthOpen(false)
                }
              >
                ×
              </button>

              <div className="auth-brand">
                k<span>!</span>
              </div>

              <span className="section-kicker">
                DELIVERY PARTNER SIGN IN
              </span>

              <h2>
                Sign in to Driver Portal
              </h2>

              <p>
                We'll email you a one-time
                code. Only delivery partner
                accounts can open this portal.
              </p>

              <OtpLoginForm
                api={API}
                emailPlaceholder="Driver email address"
                submitLabel="Verify & sign in"
                onAuthenticated={
                  completeLogin
                }
              />
            </div>
          </div>
        )}

        {toast && (
          <div className="toast">
            <span>✦</span>
            {toast}
          </div>
        )}
      </>
    );
  }

  /* ------------------------------------------------------------------------ */
  /* CUSTOMER STOREFRONT                                                       */
  /* ------------------------------------------------------------------------ */

  return (
    <div className="app-shell">
      <div className="announcement">
        <span>✦</span>
        Your everyday essentials,
        delivered in minutes
        <span className="announcement-right">
          Fresh finds. Happy prices.{' '}
          <b>♡</b>
        </span>
      </div>

      <header className="header">
        <a
          className="brand"
          href="#top"
          onClick={(event) => {
            event.preventDefault();
            navigate('/');
          }}
          aria-label="KlickIt home"
        >
          <span className="brand-mark">
            k<span>!</span>
          </span>

          <span className="brand-word">
            klick<span>it</span>
            <i>.</i>
          </span>
        </a>

        <button
          className="delivery-location"
          onClick={() =>
            setAddressOpen(!addressOpen)
          }
        >
          <span className="location-pin">
            ⌖
          </span>

          <span className="location-copy">
            <b>
              Delivery in 8–15 minutes
            </b>

            <small>
              {address ||
                'Add delivery address'}
            </small>
          </span>

          <span className="chevron">
            ⌄
          </span>
        </button>

        {addressOpen && (
  <div className="address-popover">
    <b>
      Where should we deliver?
    </b>

    <p>
      Use your current location or
      enter your address manually.
    </p>

    <button
      type="button"
      onClick={useCurrentLocation}
      disabled={locationLoading}
      style={{
        width: '100%',
        marginBottom: '10px'
      }}
    >
      {locationLoading
        ? 'Detecting location...'
        : '📍 Use my current location'}
    </button>

    {locationError && (
      <div
        style={{
          color: '#b42318',
          fontSize: '11px',
          marginBottom: '10px'
        }}
      >
        {locationError}
      </div>
    )}

    <input
      value={address}
      onChange={(event) =>
        setAddress(
          event.target.value
        )
      }
      placeholder="Enter full street address or flat no."
    />

    <button
      type="button"
      onClick={() => {
        const trimmed =
          address.trim();

        if (
          !isValidDeliveryAddress(
            trimmed
          )
        ) {
          notify(
            'Address must be at least 10 characters.'
          );
          return;
        }

        localStorage.setItem(
          'klickit_address',
          trimmed
        );

        setAddress(
          trimmed
        );

        setAddressOpen(false);

        notify(
          'Delivery location updated'
        );
      }}
    >
      Save location
    </button>
  </div>
)}

        <label className="searchbar">
          <span className="search-icon">
            ⌕
          </span>

          <input
            value={search}
            onChange={(event) =>
              setSearch(
                event.target.value
              )
            }
            placeholder="Search for atta, dal, chips, and more..."
          />

          <kbd>⌘ K</kbd>

          {search && (
            <button
              onClick={() =>
                setSearch('')
              }
              aria-label="Clear search"
            >
              ×
            </button>
          )}
        </label>

        {user?.role === 'ADMIN' && (
          <button
            className="account-btn"
            style={{
              background: '#eaf7ec',
              color: '#0c831f',
              fontWeight: 600,
              border:
                '1px solid #bbf7d0'
            }}
            onClick={() =>
              navigate('/admin')
            }
            title="Open Administrator Portal"
          >
            <span>⚙</span>
            <span>Admin Portal</span>
          </button>
        )}

        {user?.role ===
          'DELIVERY_PARTNER' && (
          <button
            className="account-btn"
            style={{
              background: '#ede9fe',
              color: '#6d28d9',
              fontWeight: 600,
              border:
                '1px solid #ddd6fe'
            }}
            onClick={() =>
              navigate('/delivery')
            }
            title="Open Delivery Partner Portal"
          >
            <span>🚴</span>
            <span>Driver Portal</span>
          </button>
        )}

        {user && (
          <button
            className="account-btn"
            onClick={() =>
              openAccount('history')
            }
            title="View order history"
          >
            <span className="account-icon">
              📜
            </span>
            <span>My Orders</span>
          </button>
        )}

        <button
          className="account-btn"
          onClick={() =>
            openAccount('profile')
          }
        >
          <span className="account-icon">
            ♙
          </span>

          <span>
            {user?.name?.split(' ')[0] ||
              'Account'}
          </span>
        </button>

        <button
          className={`cart-button ${
            cartCount
              ? 'has-items'
              : ''
          }`}
          onClick={() =>
            setCartOpen(true)
          }
        >
          <span className="cart-icon">
            🛍
          </span>

          <span className="cart-label">
            My Cart
          </span>

          {cartCount > 0 && (
            <span className="cart-count">
              {cartCount}
            </span>
          )}
        </button>
      </header>

      <main id="top">
        <section className="hero-wrap">
          <div className="hero">
            <div className="hero-copy">
              <div className="eyebrow">
                <span className="sparkle">
                  ✳
                </span>
                THE NEIGHBOURHOOD STORE,
                REIMAGINED
              </div>

              <h1>
                Good things.
                <br />
                <em>Great timing.</em>
              </h1>

              <p>
                From your morning chai to
                tonight's cravings — your
                daily needs are just a klick
                away.
              </p>

              <button
                className="hero-cta"
                onClick={() =>
                  document
                    .getElementById(
                      'shop'
                    )
                    ?.scrollIntoView({
                      behavior:
                        'smooth'
                    })
                }
              >
                Shop everyday essentials
                <span>↗</span>
              </button>

              <div className="hero-trust">
                <span>✦</span> Fresh picks
                <i />
                <span>◷</span> Quick delivery
                <i />
                <span>♡</span> Happy prices
              </div>
            </div>

            <div className="hero-art">
              <div className="hero-circle" />

              <div className="hero-sticker sticker-one">
                FRESH
                <br />
                <b>DAILY</b> ✳
              </div>

              <div className="hero-sticker sticker-two">
                GOOD
                <br />
                MOOD <span>☺</span>
              </div>

              <img
                className="hero-produce"
                src="https://images.unsplash.com/photo-1542838132-92c53300491e?auto=format&fit=crop&w=950&q=90"
                alt="Fresh colourful fruits and vegetables"
              />

              <div className="delivery-tag">
                <span className="delivery-tag-icon">
                  ⚡
                </span>

                <span>
                  <b>At your door</b>
                  <small>
                    Before you know it
                  </small>
                </span>
              </div>
            </div>
          </div>
        </section>

        <section className="benefits">
          <div className="benefit">
            <span className="benefit-icon mint">
              ✿
            </span>
            <span>
              <b>Fresh, always</b>
              <small>
                Handpicked goodness
              </small>
            </span>
          </div>

          <div className="benefit">
            <span className="benefit-icon peach">
              ↗
            </span>
            <span>
              <b>Fast to your door</b>
              <small>
                Minutes, not hours
              </small>
            </span>
          </div>

          <div className="benefit">
            <span className="benefit-icon lilac">
              ♡
            </span>
            <span>
              <b>Prices you'll love</b>
              <small>
                Little joys, every day
              </small>
            </span>
          </div>

          <div className="benefit">
            <span className="benefit-icon yellow">
              ✓
            </span>
            <span>
              <b>Quality checked</b>
              <small>
                Good stuff guaranteed
              </small>
            </span>
          </div>
        </section>

        <section className="categories-section">
          <div className="section-heading">
            <div>
              <span className="section-kicker">
                A LITTLE BIT OF EVERYTHING
              </span>

              <h2>
                What are we{' '}
                <em>picking up?</em>
              </h2>
            </div>

            <span className="side-note">
              Your list, your way{' '}
              <span>↗</span>
            </span>
          </div>

          <div className="category-grid">
            {categories
              .slice(1)
              .map((category) => (
                <button
                  key={category.name}
                  className={`category-tile ${
                    activeCategory ===
                    category.name
                      ? 'selected'
                      : ''
                  }`}
                  onClick={() => {
                    setActiveCategory(
                      activeCategory ===
                        category.name
                        ? 'All'
                        : category.name
                    );

                    document
                      .getElementById(
                        'shop'
                      )
                      ?.scrollIntoView({
                        behavior:
                          'smooth',
                        block: 'start'
                      });
                  }}
                >
                  <span
                    className="category-art"
                    style={{
                      background:
                        category.tint
                    }}
                  >
                    {category.icon}
                  </span>

                  <span>
                    {category.name}
                  </span>

                  <span className="category-arrow">
                    ↗
                  </span>
                </button>
              ))}
          </div>
        </section>

        <section
          className="shop-section"
          id="shop"
        >
          {catalogError && (
            <div
              style={{
                background: '#fef2f2',
                border:
                  '1px solid #fecaca',
                borderRadius: '12px',
                padding:
                  '12px 16px',
                color: '#991b1b',
                fontSize: '12px',
                marginBottom: '16px',
                display: 'flex',
                alignItems:
                  'center',
                gap: '8px'
              }}
            >
              <span>⚠️</span>

              <span>
                <b>
                  Live catalog offline:
                </b>{' '}
                Unable to connect to
                backend product service.
                Displaying preview
                catalog. Checkout
                requires live catalog
                products.
              </span>
            </div>
          )}

          <div className="section-heading shop-heading">
            <div>
              <span className="section-kicker">
                THE GOOD STUFF, RIGHT HERE
              </span>

              <h2>
                {search ? (
                  <>
                    Results for{' '}
                    <em>
                      “{search}”
                    </em>
                  </>
                ) : activeCategory ===
                  'All' ? (
                  <>
                    Popular{' '}
                    <em>right now</em>
                  </>
                ) : (
                  <>
                    {activeCategory}{' '}
                    <em>for you</em>
                  </>
                )}
              </h2>
            </div>

            <div className="shop-meta">
              <span className="live-dot" />
              Ready in minutes
            </div>
          </div>

          {filtered.length ? (
            <div className="product-grid">
              {filtered.map((product) => (
                <article
                  className="product-card"
                  key={product.id}
                >
                  <div className="product-image-wrap">
                    <span className="product-time">
                      <span>◷</span>{' '}
                      {product.time ||
                        '10 mins'}
                    </span>

                    {product.mrp >
                      product.price && (
                      <span className="discount-tag">
                        {Math.round(
                          ((product.mrp -
                            product.price) /
                            product.mrp) *
                            100
                        )}
                        % OFF
                      </span>
                    )}

                    <img
                      src={
                        product.image ||
                        seedProducts[0].image
                      }
                      alt={product.name}
                      loading="lazy"
                      onError={(event) => {
                        event.currentTarget.src =
                          seedProducts[0].image;
                      }}
                    />
                  </div>

                  <div className="product-info">
                    <div className="product-category">
                      {product.category ||
                        'DAILY ESSENTIALS'}
                    </div>

                    <h3>
                      {product.name}
                    </h3>

                    <p>
                      {product.description}
                    </p>

                    <div className="product-bottom">
                      <div className="price-stack">
                        <b>
                          {money(
                            product.price
                          )}
                        </b>

                        {product.mrp >
                          product.price && (
                          <del>
                            {money(
                              product.mrp
                            )}
                          </del>
                        )}
                      </div>

                      {cart[product.id] ? (
                        <div className="qty-control">
                          <button
                            onClick={() =>
                              changeQty(
                                product.id,
                                -1
                              )
                            }
                            aria-label={`Remove one ${product.name}`}
                          >
                            −
                          </button>

                          <b>
                            {cart[
                              product.id
                            ]}
                          </b>

                          <button
                            onClick={() =>
                              changeQty(
                                product.id,
                                1
                              )
                            }
                            aria-label={`Add one ${product.name}`}
                          >
                            +
                          </button>
                        </div>
                      ) : (
                        <button
                          className="add-button"
                          onClick={() => {
                            changeQty(
                              product.id,
                              1
                            );

                            notify(
                              `${product.name} added to cart`
                            );
                          }}
                        >
                          ADD{' '}
                          <span>＋</span>
                        </button>
                      )}
                    </div>
                  </div>
                </article>
              ))}
            </div>
          ) : (
            <div className="empty-search">
              <span>🧺</span>

              <h3>
                No matches just yet
              </h3>

              <p>
                Try another search or
                browse all our everyday
                essentials.
              </p>

              <button
                onClick={() => {
                  setSearch('');
                  setActiveCategory('All');
                }}
              >
                See all products
              </button>
            </div>
          )}
        </section>

        <section className="promo-banner">
          <div className="promo-decoration">
            ✳
          </div>

          <div>
            <span className="section-kicker">
              A LITTLE SOMETHING EXTRA
            </span>

            <h2>
              Your first basket
              <br />
              looks{' '}
              <em>better on us.</em>
            </h2>

            <p>
              Good things start with a
              little treat. Save on your
              first order.
            </p>
          </div>

          <div className="promo-code">
            <span>USE CODE</span>
            <b>KLICKFIRST</b>
            <small>
              Terms & conditions apply
            </small>
          </div>

          <div className="promo-sun">
            ☺
          </div>
        </section>

        <footer className="footer">
          <div className="footer-top">
            <div className="footer-brand">
              <a
                className="brand"
                href="#top"
                onClick={(event) => {
                  event.preventDefault();
                  navigate('/');
                }}
              >
                <span className="brand-mark">
                  k<span>!</span>
                </span>

                <span className="brand-word">
                  klick<span>it</span>
                  <i>.</i>
                </span>
              </a>

              <p>
                Everyday things.
                Extraordinary
                convenience.
              </p>
            </div>

            <div className="footer-col">
              <b>Discover</b>

              <a href="#shop">
                All products
              </a>

              <a
                href="#shop"
                onClick={() =>
                  setActiveCategory(
                    'Fruits & Veg'
                  )
                }
              >
                Fresh produce
              </a>

              <a
                href="#shop"
                onClick={() =>
                  setActiveCategory(
                    'Munchies'
                  )
                }
              >
                Snacks & munchies
              </a>
            </div>

            <div className="footer-col">
              <b>Portals</b>

              <a
                href="#top"
                onClick={(event) => {
                  event.preventDefault();
                  navigate('/admin');
                }}
              >
                Admin Portal
              </a>

              <a
                href="#top"
                onClick={(event) => {
                  event.preventDefault();
                  navigate('/delivery');
                }}
              >
                Delivery Portal
              </a>
            </div>

            <div className="footer-note">
              <span>
                MADE FOR YOUR EVERYDAY ✳
              </span>

              <p>
                More living, less running
                around.
              </p>

              <div className="social-dots">
                <i>ig</i>
                <i>in</i>
                <i>♡</i>
              </div>
            </div>
          </div>

          <div className="footer-bottom">
            <span>
              © 2026 KlickIt. All little
              joys reserved.
            </span>

            <span>
              Made with a little{' '}
              <b>♥</b> for everyday
              life.
            </span>
          </div>
        </footer>
      </main>

      {/* CART */}

      {cartOpen && (
        <>
          <button
            className="overlay"
            onClick={() =>
              setCartOpen(false)
            }
            aria-label="Close cart"
          />

          <aside className="cart-drawer">
            <div className="drawer-header">
              <div>
                <span className="section-kicker">
                  YOUR LITTLE HAUL
                </span>

                <h2>
                  My cart{' '}
                  <span>
                    ({cartCount})
                  </span>
                </h2>
              </div>

              <button
                className="close-button"
                onClick={() =>
                  setCartOpen(false)
                }
              >
                ×
              </button>
            </div>

            {cartCount ? (
              <>
                <div className="delivery-progress">
                  <span>✦</span>

                  <div>
                    <b>
                      {subtotal >=
                      199
                        ? 'You unlocked free delivery!'
                        : `Add ${money(
                            199 -
                              subtotal
                          )} more for free delivery`}
                    </b>

                    <div className="progress-track">
                      <i
                        style={{
                          width: `${Math.min(
                            100,
                            (subtotal /
                              199) *
                              100
                          )}%`
                        }}
                      />
                    </div>
                  </div>
                </div>

                <div className="drawer-items">
                  {cartItems.map(
                    (product) => (
                      <div
                        className="drawer-item"
                        key={product.id}
                      >
                        <img
                          src={
                            product.image
                          }
                          alt=""
                        />

                        <div className="drawer-item-info">
                          <b>
                            {product.name}
                          </b>

                          <small>
                            {
                              product.description
                            }
                          </small>

                          <strong>
                            {money(
                              product.price
                            )}
                          </strong>
                        </div>

                        <div className="qty-control">
                          <button
                            onClick={() =>
                              changeQty(
                                product.id,
                                -1
                              )
                            }
                          >
                            −
                          </button>

                          <b>
                            {product.qty}
                          </b>

                          <button
                            onClick={() =>
                              changeQty(
                                product.id,
                                1
                              )
                            }
                          >
                            +
                          </button>
                        </div>
                      </div>
                    )
                  )}
                </div>

                <div className="drawer-summary">
                  <div
                    style={{
                      display: 'flex',
                      justifyContent:
                        'space-between',
                      alignItems:
                        'center',
                      padding:
                        '8px 0',
                      borderBottom:
                        '1px dashed #e2e4dc',
                      marginBottom:
                        '10px'
                    }}
                  >
                    <div
                      style={{
                        fontSize:
                          '11px',
                        textAlign:
                          'left',
                        maxWidth:
                          '70%'
                      }}
                    >
                      <span
                        style={{
                          color:
                            '#888a7e',
                          display:
                            'block',
                          fontSize:
                            '9px',
                          textTransform:
                            'uppercase',
                          letterSpacing:
                            '0.5px'
                        }}
                      >
                        Delivery Address
                      </span>

                      <b
                        style={{
                          color:
                            address
                              ? '#1b1d19'
                              : '#dc2626',
                          wordBreak:
                            'break-word',
                          display:
                            'block',
                          marginTop:
                            '2px'
                        }}
                      >
                        {address ||
                          'No address set — required'}
                      </b>
                    </div>

                    <button
                      type="button"
                      onClick={() =>
                        setAddressOpen(
                          true
                        )
                      }
                      style={{
                        background:
                          '#f5f6f2',
                        border:
                          '1px solid #d2d5c8',
                        borderRadius:
                          '6px',
                        padding:
                          '4px 10px',
                        fontSize:
                          '10px',
                        cursor:
                          'pointer',
                        fontWeight:
                          600,
                        color:
                          '#245b3b'
                      }}
                    >
                      {address
                        ? 'Change'
                        : '+ Add'}
                    </button>
                  </div>

                  <div>
                    <span>
                      Item total
                    </span>

                    <b>
                      {money(
                        subtotal +
                          discount
                      )}
                    </b>
                  </div>

                  {discount > 0 && (
                    <div className="saving-line">
                      <span>
                        Product savings
                      </span>

                      <b>
                        −
                        {money(
                          discount
                        )}
                      </b>
                    </div>
                  )}

                  <div>
                    <span>
                      Delivery fee
                    </span>

                    <b>
                      {delivery ? (
                        money(delivery)
                      ) : (
                        <span className="free-label">
                          FREE
                        </span>
                      )}
                    </b>
                  </div>

                  <div className="grand-total">
                    <span>
                      To pay
                    </span>

                    <b>
                      {money(
                        subtotal +
                          delivery
                      )}
                    </b>
                  </div>

                  <button
                    className="checkout-button"
                    onClick={checkout}
                    disabled={loading}
                  >
                    {loading
                      ? 'Working on it…'
                      : (
                        <>
                          Proceed to
                          checkout{' '}
                          <span>
                            {money(
                              subtotal +
                                delivery
                            )}{' '}
                            ↗
                          </span>
                        </>
                      )}
                  </button>

                  <small className="secure-note">
                    ♡ Secure checkout ·
                    Cash on delivery
                  </small>
                </div>
              </>
            ) : (
              <div className="empty-cart">
                <span>🧺</span>

                <h3>
                  Your basket's taking
                  a nap
                </h3>

                <p>
                  Let's fill it with a
                  few everyday
                  favourites.
                </p>

                <button
                  onClick={() =>
                    setCartOpen(false)
                  }
                >
                  Start shopping ↗
                </button>
              </div>
            )}
          </aside>
        </>
      )}

      {/* LOGIN MODAL */}

      {authOpen && (
        <div className="modal-wrap">
          <button
            className="modal-backdrop"
            onClick={() =>
              setAuthOpen(false)
            }
            aria-label="Close sign in"
          />

          <div className="auth-modal">
            <button
              className="close-button modal-close"
              onClick={() =>
                setAuthOpen(false)
              }
            >
              ×
            </button>

            <div className="auth-brand">
              k<span>!</span>
            </div>

            <span className="section-kicker">
              YOUR EVERYDAY, MADE EASIER
            </span>

            <h2>
              Sign in or sign up
            </h2>

            <p>
              Enter your email and we'll
              send you a one-time code.
              No password needed.
            </p>

            <OtpLoginForm
              api={API}
              emailPlaceholder="Email address"
              submitLabel="Verify & continue"
              onAuthenticated={
                completeLogin
              }
            />

            <small className="auth-legal">
              By continuing, you agree to
              our Terms of Service and
              Privacy Policy.
            </small>
          </div>
        </div>
      )}

      {/* ORDER TRACKING */}

      {trackingOpen && (
        <div className="modal-wrap">
          <button
            className="modal-backdrop"
            onClick={closeTracking}
            aria-label="Close tracking"
          />

          <div className="tracking-modal">
            <div className="tracking-header">
              <div>
                <span className="section-kicker">
                  CUSTOMER ORDER TRACKING
                </span>

                <h2>
                  {activeOrder?.id
                    ? `Order #${String(
                        activeOrder.id
                      ).substring(0, 8)}`
                    : 'Order Details'}
                </h2>

                {activeOrder?.id && (
                  <small
                    style={{
                      color:
                        '#888a7e',
                      fontSize:
                        '10px'
                    }}
                  >
                    Full ID:{' '}
                    {
                      activeOrder.id
                    }
                  </small>
                )}
              </div>

              <button
                className="close-button"
                onClick={
                  closeTracking
                }
                aria-label="Close tracking"
              >
                ×
              </button>
            </div>

            {trackingLoading && (
              <div
                style={{
                  textAlign:
                    'center',
                  padding:
                    '24px 0',
                  color:
                    '#77796f',
                  fontSize:
                    '12px'
                }}
              >
                Loading latest order
                status...
              </div>
            )}

            {trackingError && (
              <div
                style={{
                  background:
                    '#fef2f2',
                  border:
                    '1px solid #fecaca',
                  borderRadius:
                    '10px',
                  padding:
                    '12px',
                  color:
                    '#991b1b',
                  fontSize:
                    '11px',
                  margin:
                    '12px 0'
                }}
              >
                <b>Error: </b>
                {trackingError}

                <div
                  style={{
                    marginTop:
                      '8px'
                  }}
                >
                  <button
                    className="btn-refresh"
                    onClick={() =>
                      fetchOrder(
                        activeOrder?.id
                      )
                    }
                    style={{
                      height:
                        '30px',
                      fontSize:
                        '10px'
                    }}
                  >
                    Retry
                  </button>
                </div>
              </div>
            )}

            {activeOrder &&
              !trackingLoading && (
                <>
                  <div
                    style={{
                      display:
                        'flex',
                      alignItems:
                        'center',
                      justifyContent:
                        'space-between',
                      margin:
                        '10px 0'
                    }}
                  >
                    <span
                      style={{
                        fontSize:
                          '11px',
                        color:
                          '#77796f'
                      }}
                    >
                      Current Status:
                    </span>

                    <span
                      className={`status-pill ${String(
                        currentStatus || ''
                      ).toLowerCase()}`}
                    >
                      {isCancelled
                        ? '✕ CANCELLED'
                        : isRejected
                        ? '✕ REJECTED'
                        : currentStatus}
                    </span>
                  </div>

                  {isCancelled ? (
                    <div className="cancelled-banner">
                      <span
                        style={{
                          fontSize:
                            '20px'
                        }}
                      >
                        ✕
                      </span>

                      <div>
                        <b>
                          Order
                          Cancelled
                        </b>

                        <p>
                          This order has
                          been cancelled
                          and will not
                          progress to
                          delivery.
                        </p>
                      </div>
                    </div>
                  ) : isRejected ? (
                    <div
                      className="cancelled-banner"
                      style={{
                        background:
                          '#fef2f2',
                        borderColor:
                          '#fca5a5'
                      }}
                    >
                      <span
                        style={{
                          fontSize:
                            '20px',
                          color:
                            '#dc2626'
                        }}
                      >
                        ✕
                      </span>

                      <div>
                        <b
                          style={{
                            color:
                              '#991b1b'
                          }}
                        >
                          Order Rejected
                        </b>

                        <p
                          style={{
                            color:
                              '#b91c1c'
                          }}
                        >
                          This order was
                          rejected by
                          the store
                          administrator.
                        </p>
                      </div>
                    </div>
                  ) : (
                    <div className="status-stepper">
                      <span
                        className="section-kicker"
                        style={{
                          fontSize:
                            '8px'
                        }}
                      >
                        DELIVERY
                        PROGRESSION
                      </span>

                      <div className="stepper-steps">
                        {lifecycleStages.map(
                          (
                            stage,
                            index
                          ) => {
                            const done =
                              currentStageIndex >=
                              index;

                            const active =
                              currentStageIndex ===
                              index;

                            return (
                              <div
                                key={
                                  stage
                                }
                                className={`step-item ${
                                  done
                                    ? 'done'
                                    : ''
                                } ${
                                  active
                                    ? 'active'
                                    : ''
                                }`}
                              >
                                <div className="step-circle">
                                  {done
                                    ? '✓'
                                    : index +
                                      1}
                                </div>

                                <span className="step-label">
                                  {stageLabels[
                                    stage
                                  ] ||
                                    stage}
                                </span>
                              </div>
                            );
                          }
                        )}
                      </div>
                    </div>
                  )}

                  <div className="tracking-info-grid">
                    <div className="info-card">
                      <small>
                        Total Amount
                        (COD)
                      </small>

                      <p>
                        <b>
                          {money(
                            activeOrder.totalAmount
                          )}
                        </b>
                      </p>
                    </div>

                    <div className="info-card">
                      <small>
                        Delivery SLA
                      </small>

                      <p>
                        <b>
                          {activeOrder.deadline
                            ? new Date(
                                activeOrder.deadline
                              ).toLocaleTimeString(
                                [],
                                {
                                  hour: '2-digit',
                                  minute:
                                    '2-digit'
                                }
                              )
                            : '15 mins'}
                        </b>
                      </p>
                    </div>

                    <div
                      className="info-card"
                      style={{
                        gridColumn:
                          'span 2'
                      }}
                    >
                      <small>
                        Delivery Address
                      </small>

                      <p>
                        {activeOrder.customerAddress ||
                          'Address on file'}
                      </p>
                    </div>

                    {activeOrder.deliveryPartnerName && (
                      <div
                        className="info-card"
                        style={{
                          gridColumn:
                            'span 2'
                        }}
                      >
                        <small>
                          Delivery
                          Partner
                        </small>

                        <p>
                          <b>
                            {
                              activeOrder.deliveryPartnerName
                            }
                          </b>

                          {activeOrder.deliveryPartnerPhone
                            ? ` (${activeOrder.deliveryPartnerPhone})`
                            : ''}
                        </p>
                      </div>
                    )}
                  </div>

                  {Array.isArray(
                    activeOrder.items
                  ) &&
                    activeOrder.items
                      .length > 0 && (
                      <div className="tracking-items">
                        <span
                          className="section-kicker"
                          style={{
                            fontSize:
                              '8px',
                            marginBottom:
                              '8px',
                            display:
                              'block'
                          }}
                        >
                          ITEMS ORDERED (
                          {
                            activeOrder
                              .items
                              .length
                          }
                          )
                        </span>

                        {activeOrder.items.map(
                          (
                            item,
                            index
                          ) => (
                            <div
                              key={
                                index
                              }
                              className="tracking-item-row"
                            >
                              <div>
                                <span className="tracking-item-name">
                                  {
                                    item.productName
                                  }
                                </span>

                                <span className="tracking-item-qty">
                                  ×{' '}
                                  {
                                    item.quantity
                                  }
                                </span>
                              </div>

                              <span className="tracking-item-total">
                                {money(
                                  item.lineTotal ??
                                    item.price *
                                      item.quantity
                                )}
                              </span>
                            </div>
                          )
                        )}
                      </div>
                    )}

                  <div className="tracking-actions">
                    <button
                      className="btn-refresh"
                      onClick={() =>
                        fetchOrder(
                          activeOrder.id
                        )
                      }
                      disabled={
                        trackingLoading
                      }
                    >
                      <span>↻</span>{' '}
                      Refresh Status
                    </button>

                    <button
                      className="btn-close-tracking"
                      onClick={
                        closeTracking
                      }
                    >
                      Done
                    </button>
                  </div>
                </>
              )}
          </div>
        </div>
      )}

      {/* ACCOUNT */}

      {accountOpen && (
        <div className="modal-wrap">
          <button
            className="modal-backdrop"
            onClick={() =>
              setAccountOpen(false)
            }
            aria-label="Close account"
          />

          <div className="account-modal">
            <div className="tracking-header">
              <div>
                <span className="section-kicker">
                  ACCOUNT & PREFERENCES
                </span>

                <h2>
                  {user?.name ||
                    'My Account'}
                </h2>

                <small
                  style={{
                    color:
                      '#888a7e',
                    fontSize:
                      '10px'
                  }}
                >
                  {user?.email}
                </small>
              </div>

              <button
                className="close-button"
                onClick={() =>
                  setAccountOpen(false)
                }
                aria-label="Close account"
              >
                ×
              </button>
            </div>

            <div className="account-tabs">
              <button
                className={`account-tab-btn ${
                  accountTab ===
                  'profile'
                    ? 'active'
                    : ''
                }`}
                onClick={() =>
                  setAccountTab(
                    'profile'
                  )
                }
              >
                Profile Details
              </button>

              <button
                className={`account-tab-btn ${
                  accountTab ===
                  'history'
                    ? 'active'
                    : ''
                }`}
                onClick={() => {
                  setAccountTab(
                    'history'
                  );
                  fetchOrderHistory();
                }}
              >
                Order History
              </button>
            </div>

            {accountTab ===
              'profile' && (
              <div className="profile-card">
                <div className="profile-row">
                  <span>Name</span>
                  <b>
                    {user?.name ||
                      'Not set'}
                  </b>
                </div>

                <div className="profile-row">
                  <span>
                    Email Address
                  </span>

                  <b>
                    {user?.email ||
                      'Not set'}
                  </b>
                </div>

                <div className="profile-row">
                  <span>
                    Account Role
                  </span>

                  <b
                    style={{
                      color:
                        user?.role ===
                        'ADMIN'
                          ? '#0c831f'
                          : user?.role ===
                            'DELIVERY_PARTNER'
                          ? '#7c3aed'
                          : '#2563eb'
                    }}
                  >
                    {user?.role ||
                      'CUSTOMER'}
                  </b>
                </div>

                <div className="profile-row">
                  <span>
                    Phone Number
                  </span>

                  {editingPhone ? (
                    <div
                      style={{
                        display:
                          'flex',
                        gap: '6px',
                        alignItems:
                          'center'
                      }}
                    >
                      <input
                        value={
                          phoneInput
                        }
                        onChange={(
                          event
                        ) =>
                          setPhoneInput(
                            event
                              .target
                              .value
                          )
                        }
                        placeholder="10-digit phone"
                        style={{
                          padding:
                            '4px 8px',
                          fontSize:
                            '11px',
                          borderRadius:
                            '4px',
                          border:
                            '1px solid #ccc',
                          width:
                            '130px'
                        }}
                      />

                      <button
                        type="button"
                        onClick={() => {
                          if (
                            !isValidPhoneNumber(
                              phoneInput
                            )
                          ) {
                            notify(
                              'Please enter a valid phone number (7-20 digits)'
                            );
                            return;
                          }

                          const updatedUser =
                            {
                              ...user,
                              phone:
                                phoneInput.trim()
                            };

                          setUser(
                            updatedUser
                          );

                          localStorage.setItem(
                            'klickit_user',
                            JSON.stringify(
                              updatedUser
                            )
                          );

                          setEditingPhone(
                            false
                          );

                          notify(
                            'Phone number updated'
                          );
                        }}
                        style={{
                          background:
                            '#245b3b',
                          color:
                            '#fff',
                          border:
                            'none',
                          borderRadius:
                            '4px',
                          padding:
                            '4px 8px',
                          fontSize:
                            '10px',
                          cursor:
                            'pointer'
                        }}
                      >
                        Save
                      </button>

                      <button
                        type="button"
                        onClick={() =>
                          setEditingPhone(
                            false
                          )
                        }
                        style={{
                          background:
                            'none',
                          border:
                            'none',
                          color:
                            '#777',
                          cursor:
                            'pointer',
                          fontSize:
                            '10px'
                        }}
                      >
                        ✕
                      </button>
                    </div>
                  ) : (
                    <div
                      style={{
                        display:
                          'flex',
                        alignItems:
                          'center',
                        gap: '6px'
                      }}
                    >
                      <b>
                        {user?.phone ||
                          'Not set'}
                      </b>

                      <button
                        type="button"
                        onClick={() => {
                          setPhoneInput(
                            user?.phone ||
                              ''
                          );
                          setEditingPhone(
                            true
                          );
                        }}
                        style={{
                          background:
                            'none',
                          border:
                            'none',
                          color:
                            '#245b3b',
                          cursor:
                            'pointer',
                          fontSize:
                            '10px',
                          textDecoration:
                            'underline'
                        }}
                      >
                        {user?.phone
                          ? 'Edit'
                          : 'Add'}
                      </button>
                    </div>
                  )}
                </div>

                <div className="profile-row">
                  <span>
                    Saved Area
                  </span>

                  <b>
                    {address ||
                      'Not set'}
                  </b>
                </div>

                {user?.role ===
                  'ADMIN' && (
                  <button
                    type="button"
                    className="checkout-button"
                    style={{
                      marginTop:
                        '12px',
                      background:
                        '#0c831f'
                    }}
                    onClick={() => {
                      setAccountOpen(
                        false
                      );
                      navigate(
                        '/admin'
                      );
                    }}
                  >
                    Open Administrator
                    Dashboard ⚙ ↗
                  </button>
                )}

                {user?.role ===
                  'DELIVERY_PARTNER' && (
                  <button
                    type="button"
                    className="checkout-button"
                    style={{
                      marginTop:
                        '12px',
                      background:
                        '#7c3aed'
                    }}
                    onClick={() => {
                      setAccountOpen(
                        false
                      );
                      navigate(
                        '/delivery'
                      );
                    }}
                  >
                    Open Delivery
                    Partner Dashboard
                    🚴 ↗
                  </button>
                )}

                <button
                  className="btn-signout"
                  style={{
                    marginTop:
                      '16px'
                  }}
                  onClick={
                    handleSignOut
                  }
                >
                  Sign Out
                </button>
              </div>
            )}

            {accountTab ===
              'history' && (
              <div>
                {historyLoading && (
                  <div
                    style={{
                      textAlign:
                        'center',
                      padding:
                        '24px 0',
                      color:
                        '#77796f',
                      fontSize:
                        '12px'
                    }}
                  >
                    Loading your
                    orders...
                  </div>
                )}

                {historyError && (
                  <div
                    style={{
                      background:
                        '#fef2f2',
                      border:
                        '1px solid #fecaca',
                      borderRadius:
                        '10px',
                      padding:
                        '12px',
                      color:
                        '#991b1b',
                      fontSize:
                        '11px',
                      margin:
                        '12px 0'
                    }}
                  >
                    <b>Error: </b>
                    {historyError}

                    <div
                      style={{
                        marginTop:
                          '8px'
                      }}
                    >
                      <button
                        className="btn-refresh"
                        onClick={
                          fetchOrderHistory
                        }
                        style={{
                          height:
                            '30px',
                          fontSize:
                            '10px'
                        }}
                      >
                        Retry
                      </button>
                    </div>
                  </div>
                )}

                {!historyLoading &&
                  !historyError &&
                  orderHistory.length ===
                    0 && (
                    <div
                      className="empty-search"
                      style={{
                        padding:
                          '30px 10px',
                        minHeight:
                          'auto'
                      }}
                    >
                      <span>
                        🧺
                      </span>

                      <h3>
                        No orders yet
                      </h3>

                      <p>
                        When you place
                        an order, it
                        will appear
                        here so you
                        can track its
                        progress.
                      </p>

                      <button
                        onClick={() => {
                          setAccountOpen(
                            false
                          );

                          document
                            .getElementById(
                              'shop'
                            )
                            ?.scrollIntoView(
                              {
                                behavior:
                                  'smooth'
                              }
                            );
                        }}
                      >
                        Start shopping ↗
                      </button>
                    </div>
                  )}

                {!historyLoading &&
                  !historyError &&
                  orderHistory.length >
                    0 && (
                    <div className="order-history-list">
                      {orderHistory.map(
                        (order) => {
                          const dateStr =
                            order.createdAt
                              ? new Date(
                                  order.createdAt
                                ).toLocaleDateString(
                                  [],
                                  {
                                    month:
                                      'short',
                                    day:
                                      'numeric',
                                    hour:
                                      '2-digit',
                                    minute:
                                      '2-digit'
                                  }
                                )
                              : '';

                          const itemsCount =
                            Array.isArray(
                              order.items
                            )
                              ? order.items.reduce(
                                  (
                                    total,
                                    item
                                  ) =>
                                    total +
                                    (item.quantity ||
                                      1),
                                  0
                                )
                              : 0;

                          const itemsSummary =
                            Array.isArray(
                              order.items
                            )
                              ? order.items
                                  .map(
                                    (
                                      item
                                    ) =>
                                      item.productName
                                  )
                                  .filter(
                                    Boolean
                                  )
                                  .join(
                                    ', '
                                  )
                              : '';

                          return (
                            <button
                              key={
                                order.id
                              }
                              className="order-card-summary"
                              onClick={() => {
                                setAccountOpen(
                                  false
                                );

                                openTracking(
                                  order
                                );
                              }}
                            >
                              <div className="order-card-header">
                                <span className="order-card-id">
                                  #
                                  {String(
                                    order.id
                                  ).substring(
                                    0,
                                    8
                                  )}
                                </span>

                                <span
                                  className={`status-pill ${String(
                                    order.status ||
                                      ''
                                  ).toLowerCase()}`}
                                >
                                  {
                                    order.status
                                  }
                                </span>
                              </div>

                              <div className="order-card-meta">
                                <span>
                                  {dateStr ||
                                    'Recent'}
                                </span>

                                <b
                                  style={{
                                    color:
                                      '#245b3b',
                                    fontSize:
                                      '13px'
                                  }}
                                >
                                  {money(
                                    order.totalAmount
                                  )}
                                </b>
                              </div>

                              {itemsSummary && (
                                <div className="order-card-items">
                                  <span>
                                    {
                                      itemsCount
                                    }{' '}
                                    item
                                    {itemsCount !==
                                    1
                                      ? 's'
                                      : ''}
                                    :{' '}
                                  </span>

                                  <span>
                                    {itemsSummary.length >
                                    60
                                      ? `${itemsSummary.substring(
                                          0,
                                          58
                                        )}…`
                                      : itemsSummary}
                                  </span>
                                </div>
                              )}

                              <div className="order-card-cta">
                                Track Order
                                Details{' '}
                                <span>
                                  ↗
                                </span>
                              </div>
                            </button>
                          );
                        }
                      )}
                    </div>
                  )}
              </div>
            )}
          </div>
        </div>
      )}

      {toast && (
        <div className="toast">
          <span>✦</span>
          {toast}
        </div>
      )}

      <nav className="mobile-nav">
        <button
          onClick={() => {
            navigate('/');
            setActiveCategory('All');

            window.scrollTo({
              top: 0,
              behavior: 'smooth'
            });
          }}
        >
          <span>⌂</span>
          Home
        </button>

        <button
          onClick={() => {
            navigate('/');

            document
              .getElementById('shop')
              ?.scrollIntoView({
                behavior: 'smooth'
              });
          }}
        >
          <span>⌕</span>
          Explore
        </button>

        <button
          onClick={() =>
            setCartOpen(true)
          }
        >
          <span>🛍</span>
          Cart{' '}
          {cartCount
            ? `(${cartCount})`
            : ''}
        </button>

        {user?.role ===
          'ADMIN' && (
          <button
            onClick={() =>
              navigate('/admin')
            }
          >
            <span>⚙</span>
            Admin
          </button>
        )}

        {user?.role ===
          'DELIVERY_PARTNER' && (
          <button
            onClick={() =>
              navigate('/delivery')
            }
          >
            <span>🚴</span>
            Driver
          </button>
        )}

        <button
          onClick={() =>
            openAccount('profile')
          }
        >
          <span>♙</span>
          Account
        </button>
      </nav>
    </div>
  );
}

createRoot(
  document.getElementById('root')
).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);