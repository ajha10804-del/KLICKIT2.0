import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';
import AdminDashboard from './admin and deliverydashboard/AdminDashboard.jsx';
import DeliveryDashboard from './admin and deliverydashboard/DeliveryDashboard.jsx';
import LocationPicker from './src/components/LocationPicker.jsx';
import CustomerTrackingMap from './src/components/CustomerTrackingMap.jsx';
import OtpLoginForm from './src/components/OtpLoginForm.jsx';

const API = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');
const categories = [
  { name: 'All', icon: '✦', tint: '#fff4ce' }, { name: 'Fruits & Veg', icon: '🥑', tint: '#e5f4df' },
  { name: 'Dairy & Eggs', icon: '🥛', tint: '#e5f1ff' }, { name: 'Munchies', icon: '🍿', tint: '#fff0df' },
  { name: 'Cold Drinks', icon: '🥤', tint: '#fce4e8' }, { name: 'Bakery', icon: '🍞', tint: '#f9ead7' },
  { name: 'Staples', icon: '🌾', tint: '#f5eddb' }, { name: 'Personal Care', icon: '🧴', tint: '#eee7ff' },
  { name: 'Home Care', icon: '🧹', tint: '#e5f5f3' }, { name: 'Instant Food', icon: '🍜', tint: '#ffe9dd' }
];
const seedProducts = [
  { id: 1, name: 'Fresh Bananas', description: 'Robusta • 4–6 pcs', price: 42, mrp: 55, category: 'Fruits & Veg', image: 'https://images.unsplash.com/photo-1571771894821-ce9b6c11b08e?auto=format&fit=crop&w=480&q=85', time: '8 mins' },
  { id: 2, name: 'Farm Fresh Eggs', description: 'Pack of 6 • Protein rich', price: 58, mrp: 68, category: 'Dairy & Eggs', image: 'https://images.unsplash.com/photo-1506976785307-8732e854ad03?auto=format&fit=crop&w=480&q=85', time: '8 mins' },
  { id: 3, name: 'Amul Taaza Milk', description: 'Toned milk • 500 ml', price: 29, mrp: 32, category: 'Dairy & Eggs', image: 'https://images.unsplash.com/photo-1563636619-e9143da7973b?auto=format&fit=crop&w=480&q=85', time: '8 mins' },
  { id: 4, name: 'Classic Potato Chips', description: 'Classic salted •  pack', price: 20, mrp: 20, category: 'Munchies', image: 'https://images.unsplash.com/photo-1566478989037-eec170784d0b?auto=format&fit=crop&w=480&q=85', time: '9 mins' },
  { id: 5, name: 'Red Apples', description: 'Crisp & juicy • 4 pcs', price: 119, mrp: 149, category: 'Fruits & Veg', image: 'https://images.unsplash.com/photo-1560806887-1e4cd0b6cbd6?auto=format&fit=crop&w=480&q=85', time: '8 mins' },
  { id: 6, name: 'Orange Juice', description: 'Real fruit • 1 L', price: 110, mrp: 125, category: 'Cold Drinks', image: 'https://images.unsplash.com/photo-1600271886742-f049cd451bba?auto=format&fit=crop&w=480&q=85', time: '10 mins' },
  { id: 7, name: 'Sourdough Bread', description: 'Freshly baked • 400 g', price: 65, mrp: 75, category: 'Bakery', image: 'https://images.unsplash.com/photo-1585478259715-876acc5be8eb?auto=format&fit=crop&w=480&q=85', time: '12 mins' },
  { id: 8, name: 'Basmati Rice', description: 'Premium long grain • 1 kg', price: 99, mrp: 125, category: 'Staples', image: 'https://images.unsplash.com/photo-1586201375761-83865001e31c?auto=format&fit=crop&w=480&q=85', time: '10 mins' },
  { id: 9, name: 'Instant Noodles', description: 'Masala noodles • pack of 4', price: 56, mrp: 60, category: 'Instant Food', image: 'https://images.unsplash.com/photo-1569718212165-3a8278d5f624?auto=format&fit=crop&w=480&q=85', time: '9 mins' },
  { id: 10, name: 'Dishwash Liquid', description: 'Lemon fresh • 500 ml', price: 99, mrp: 120, category: 'Home Care', image: 'https://images.unsplash.com/photo-1585421514738-01798e348b17?auto=format&fit=crop&w=480&q=85', time: '10 mins' },
  { id: 11, name: 'Gentle Face Wash', description: 'Daily care • 100 g', price: 149, mrp: 175, category: 'Personal Care', image: 'https://images.unsplash.com/photo-1556229010-6c3f2c9ca5f8?auto=format&fit=crop&w=480&q=85', time: '10 mins' },
  { id: 12, name: 'Green Capsicum', description: 'Fresh • 250 g', price: 24, mrp: 30, category: 'Fruits & Veg', image: 'https://images.unsplash.com/photo-1563565375-f3fdfdbefa83?auto=format&fit=crop&w=480&q=85', time: '8 mins' }
];
const money = n => `₹${Number(n || 0).toFixed(0)}`;

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function isTokenExpired(token) {
  if (!token) return true;
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return true;
    const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')));
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
  if (/^indore(,\s*madhya\s*pradesh)?$/i.test(trimmed)) return false;
  return true;
}

function isValidPhoneNumber(ph) {
  if (!ph || typeof ph !== 'string') return false;
  const trimmed = ph.trim();
  if (trimmed.length < 7 || trimmed.length > 20) return false;
  if (!/^[+0-9\-\s()]{7,20}$/.test(trimmed)) return false;
  if (trimmed === '9876543210' || trimmed === '+919876543210') return false;
  return true;
}

// Role Barrier UI Components
function AdminAccessBarrier({ user, onSignIn, onBackToStore }) {
  const isWrongRole = user && user.role !== 'ADMIN';

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#f8faf9', padding: '20px', fontFamily: 'Inter, sans-serif' }}>
      <div style={{ maxWidth: '440px', width: '100%', background: '#fff', borderRadius: '16px', padding: '32px', boxShadow: '0 10px 25px -5px rgba(0,0,0,0.06)', textAlign: 'center', border: '1px solid #e5e7eb' }}>
        <div style={{ width: '56px', height: '56px', borderRadius: '50%', background: '#fee2e2', color: '#dc2626', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '24px', margin: '0 auto 16px' }}>
          🛡️
        </div>
        <h2 style={{ fontSize: '20px', fontWeight: '700', color: '#111827', marginBottom: '8px' }}>
          {isWrongRole ? 'Access Denied (403 Forbidden)' : 'Admin Authentication Required'}
        </h2>
        <p style={{ fontSize: '13px', color: '#6b7280', lineHeight: '1.5', marginBottom: '24px' }}>
          {isWrongRole
            ? `You are currently signed in as "${user.name || user.email}" with role "${user.role || 'CUSTOMER'}". You do not have permission to access the Administrator Portal.`
            : 'You must be signed in with an authorized Administrator account to view and manage store operations.'}
        </p>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <button
            onClick={onSignIn}
            style={{ width: '100%', padding: '12px', background: '#0c831f', color: '#fff', border: 'none', borderRadius: '8px', fontWeight: '600', fontSize: '13px', cursor: 'pointer' }}
          >
            {isWrongRole ? 'Switch to Admin Account' : 'Sign In as Administrator'}
          </button>
          <button
            onClick={onBackToStore}
            style={{ width: '100%', padding: '12px', background: '#f3f4f6', color: '#374151', border: '1px solid #e5e7eb', borderRadius: '8px', fontWeight: '600', fontSize: '13px', cursor: 'pointer' }}
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
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#f8faf9', padding: '20px', fontFamily: 'Inter, sans-serif' }}>
      <div style={{ maxWidth: '440px', width: '100%', background: '#fff', borderRadius: '16px', padding: '32px', boxShadow: '0 10px 25px -5px rgba(0,0,0,0.06)', textAlign: 'center', border: '1px solid #e5e7eb' }}>
        <div style={{ width: '56px', height: '56px', borderRadius: '50%', background: '#ede9fe', color: '#7c3aed', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '24px', margin: '0 auto 16px' }}>
          🚴
        </div>
        <h2 style={{ fontSize: '20px', fontWeight: '700', color: '#111827', marginBottom: '8px' }}>
          {isWrongRole ? 'Access Denied (403 Forbidden)' : 'Driver Authentication Required'}
        </h2>
        <p style={{ fontSize: '13px', color: '#6b7280', lineHeight: '1.5', marginBottom: '24px' }}>
          {isWrongRole
            ? `You are currently signed in as "${user.name || user.email}" with role "${user.role || 'CUSTOMER'}". You do not have permission to access the Delivery Partner Portal.`
            : 'You must be signed in with a registered Delivery Partner account to view assigned orders and update delivery status.'}
        </p>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <button
            onClick={onSignIn}
            style={{ width: '100%', padding: '12px', background: '#7c3aed', color: '#fff', border: 'none', borderRadius: '8px', fontWeight: '600', fontSize: '13px', cursor: 'pointer' }}
          >
            {isWrongRole ? 'Switch to Driver Account' : 'Sign In as Delivery Partner'}
          </button>
          <button
            onClick={onBackToStore}
            style={{ width: '100%', padding: '12px', background: '#f3f4f6', color: '#374151', border: '1px solid #e5e7eb', borderRadius: '8px', fontWeight: '600', fontSize: '13px', cursor: 'pointer' }}
          >
            Return to Customer Storefront
          </button>
        </div>
      </div>
    </div>
  );
}

function App() {
  const [currentPath, setCurrentPath] = useState(() => window.location.pathname);
  const [products, setProducts] = useState(seedProducts);
  const [catalogError, setCatalogError] = useState(false);
  const [activeCategory, setActiveCategory] = useState('All');
  const [search, setSearch] = useState('');
  const [cart, setCart] = useState({});
  const [cartOpen, setCartOpen] = useState(false);
  const [authOpen, setAuthOpen] = useState(false);
  const [authMode, setAuthMode] = useState('otp');
  const [user, setUser] = useState(() => {
    try {
      const u = JSON.parse(localStorage.getItem('klickit_user') || 'null');
      if (u && !isValidPhoneNumber(u.phone)) {
        u.phone = '';
      }
      return u;
    } catch {
      return null;
    }
  });
  const [toast, setToast] = useState('');
  const [address, setAddress] = useState(() => {
    return localStorage.getItem('klickit_address') || '';
  });
  const [landmark, setLandmark] = useState(() => {
    return localStorage.getItem('klickit_landmark') || '';
  });
  const [coordinates, setCoordinates] = useState(() => {
    try {
      const saved = localStorage.getItem('klickit_coordinates');
      if (!saved) return null;
      const parsed = JSON.parse(saved);
      if (typeof parsed?.latitude === 'number' && typeof parsed?.longitude === 'number') {
        return parsed;
      }
      return null;
    } catch {
      return null;
    }
  });
  const [addressOpen, setAddressOpen] = useState(false);
  const [authForm, setAuthForm] = useState({ name: '', email: '', password: '', phone: '' });
  const [loading, setLoading] = useState(false);
  const [editingPhone, setEditingPhone] = useState(false);
  const [phoneInput, setPhoneInput] = useState('');

  const authToken = (typeof localStorage !== 'undefined' ? localStorage.getItem('klickit_token') : '') || '';

  // Client-side router navigation
  useEffect(() => {
    const handlePopState = () => setCurrentPath(window.location.pathname);
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  function navigate(path) {
    if (window.location.pathname !== path) {
      window.history.pushState(null, '', path);
      setCurrentPath(path);
      window.scrollTo(0, 0);
    }
  }

  useEffect(() => {
    fetch(`${API}/api/products`).then(r => r.ok ? r.json() : Promise.reject()).then(data => {
      const list = Array.isArray(data) ? data : (data?.content || data?.data || data?.products || []);
      if (list.length) {
        setProducts(list.map((p, i) => ({ ...p, id: p.id ?? p.productId, name: p.name ?? p.productName ?? 'Everyday essential', description: p.description ?? p.unit ?? 'Quality you can trust', price: Number(p.price ?? p.sellingPrice ?? 0), mrp: Number(p.mrp ?? p.originalPrice ?? p.price ?? 0), category: p.category ?? 'Staples', image: p.imageUrl ?? p.image ?? seedProducts[i % seedProducts.length].image, time: '10 mins' })));
        setCatalogError(false);
      }
    }).catch(() => {
      setCatalogError(true);
    });
  }, []);

  const filtered = useMemo(() => products.filter(p => (activeCategory === 'All' || p.category?.toLowerCase().includes(activeCategory.toLowerCase()) || (activeCategory === 'Fruits & Veg' && /fruit|vegetable|produce/i.test(p.category || ''))) && `${p.name} ${p.description} ${p.category}`.toLowerCase().includes(search.toLowerCase())), [products, activeCategory, search]);
  const cartItems = products.filter(p => cart[p.id] > 0).map(p => ({ ...p, qty: cart[p.id] }));
  const cartCount = Object.values(cart).reduce((a, b) => a + b, 0);
  const subtotal = cartItems.reduce((sum, p) => sum + p.price * p.qty, 0);
  const delivery = subtotal === 0 || subtotal >= 199 ? 0 : 25;
  const discount = cartItems.reduce((sum, p) => sum + Math.max(0, p.mrp - p.price) * p.qty, 0);
  const notify = message => { setToast(message); window.setTimeout(() => setToast(''), 2800); };
  const changeQty = (id, delta) => setCart(prev => { const next = { ...prev, [id]: Math.max(0, (prev[id] || 0) + delta) }; if (!next[id]) delete next[id]; return next; });

  const [activeOrder, setActiveOrder] = useState(null);
  const [trackingOpen, setTrackingOpen] = useState(false);
  const [trackingLoading, setTrackingLoading] = useState(false);
  const [trackingError, setTrackingError] = useState(null);

  const [accountOpen, setAccountOpen] = useState(false);
  const [accountTab, setAccountTab] = useState('profile'); // 'profile' | 'history'
  const [orderHistory, setOrderHistory] = useState([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState(null);

  function handleAuthFailure(msg = 'Session expired. Please sign in again.') {
    localStorage.removeItem('klickit_token');
    localStorage.removeItem('klickit_user');
    setUser(null);
    setAccountOpen(false);
    setCartOpen(false);
    setAuthMode('otp');
    setAuthOpen(true);
    notify(msg);
  }

  async function fetchOrderHistory() {
    if (historyLoading) return;
    setHistoryLoading(true);
    setHistoryError(null);
    try {
      const token = localStorage.getItem('klickit_token');
      if (!token || isTokenExpired(token)) {
        handleAuthFailure('Session expired. Please sign in again.');
        return;
      }
      const res = await fetch(`${API}/api/orders`, {
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`
        }
      });
      if (res.status === 401) {
        handleAuthFailure('Session expired. Please sign in again.');
        return;
      }
      const body = await res.json().catch(() => ({}));
      if (res.status === 403) {
        throw new Error(body.message || 'Access denied.');
      }
      if (!res.ok || !body.success || !Array.isArray(body.data)) {
        throw new Error(body.message || 'Could not load your order history.');
      }
      setOrderHistory(body.data);
    } catch (err) {
      setHistoryError(err.message === 'Failed to fetch' ? 'Network error: could not load orders.' : err.message);
    } finally {
      setHistoryLoading(false);
    }
  }

  function openAccount(tab = 'profile') {
    if (!user) {
      setAuthMode('otp');
      setAuthOpen(true);
      return;
    }
    setAccountTab(tab);
    setAccountOpen(true);
    if (tab === 'history') {
      fetchOrderHistory();
    }
  }

  function handleSignOut() {
    localStorage.removeItem('klickit_token');
    localStorage.removeItem('klickit_user');
    setUser(null);
    setAccountOpen(false);
    setEditingPhone(false);
    notify('Signed out successfully.');
    if (currentPath === '/admin' || currentPath === '/delivery') {
      navigate('/');
    }
  }

  async function fetchOrder(orderId) {
    if (!orderId) return;
    setTrackingLoading(true);
    setTrackingError(null);
    try {
      const token = localStorage.getItem('klickit_token');
      if (token && isTokenExpired(token)) {
        handleAuthFailure('Session expired. Please sign in again.');
        return;
      }
      const res = await fetch(`${API}/api/orders/${orderId}`, {
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {})
        }
      });
      if (res.status === 401) {
        handleAuthFailure('Session expired. Please sign in again.');
        return;
      }
      const body = await res.json().catch(() => ({}));
      if (res.status === 404) {
        throw new Error(body.message || 'Order not found.');
      }
      if (res.status === 403) {
        throw new Error(body.message || 'You are not authorized to view this order.');
      }
      if (!res.ok || !body.success || !body.data) {
        throw new Error(body.message || 'Could not load order details.');
      }
      setActiveOrder(body.data);
    } catch (err) {
      setTrackingError(err.message === 'Failed to fetch' ? 'Network error: could not load order.' : err.message);
    } finally {
      setTrackingLoading(false);
    }
  }

  function openTracking(order) {
    if (order?.id) {
      setActiveOrder(order);
      setTrackingError(null);
      navigate(`/orders/${order.id}`);
      fetchOrder(order.id);
    } else {
      setActiveOrder(order);
      setTrackingError(null);
      setTrackingOpen(true);
    }
  }

  function closeTracking() {
    setTrackingOpen(false);
    if (window.location.hash.startsWith('#order-')) {
      window.history.replaceState(null, '', window.location.pathname);
    }
    if (currentPath.startsWith('/orders/')) {
      navigate('/');
    }
  }

  // Handle direct navigation or refresh with /orders/:id or #order-:id
  useEffect(() => {
    const orderRouteMatch = currentPath.match(/^\/orders\/([0-9a-fA-F-]{36}|[a-zA-Z0-9_-]+)/);
    if (orderRouteMatch) {
      const orderId = orderRouteMatch[1];
      if (!activeOrder || activeOrder.id !== orderId) {
        fetchOrder(orderId);
      }
    } else {
      const hash = window.location.hash;
      if (hash.startsWith('#order-')) {
        const orderId = hash.replace('#order-', '').trim();
        if (orderId && (!activeOrder || activeOrder.id !== orderId)) {
          fetchOrder(orderId);
        }
      }
    }
  }, [currentPath]);

  async function submitAuth(e) {
    e.preventDefault(); setLoading(true);
    const endpoint = authMode === 'login' ? '/api/auth/login' : '/api/auth/register';
    const payload = authMode === 'login' ? { email: authForm.email, password: authForm.password } : { name: authForm.name, email: authForm.email, password: authForm.password, phone: authForm.phone };
    try {
      const res = await fetch(`${API}${endpoint}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
      const body = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(body.message || body.error || 'Please check your details and try again.');
      const token = body.token || body.accessToken || body.data?.token;
      if (token) localStorage.setItem('klickit_token', token);
      const resUser = body.user || body.data?.user || body.data;
      const profile = {
        name: resUser?.name || authForm.name || authForm.email.split('@')[0],
        email: resUser?.email || authForm.email,
        phone: resUser?.phone || authForm.phone || '',
        role: resUser?.role || 'CUSTOMER'
      };
      localStorage.setItem('klickit_user', JSON.stringify(profile));
      setUser(profile);
      setAuthOpen(false);
      notify(`Welcome${profile.name ? `, ${profile.name}` : ''}!`);

      // Automatic role-based routing redirection
      if (profile.role === 'ADMIN') {
        navigate('/admin');
      } else if (profile.role === 'DELIVERY_PARTNER') {
        navigate('/delivery');
      }
    } catch (err) {
      notify(err.message === 'Failed to fetch' ? 'Could not connect to the server. Check that your backend is running.' : err.message);
    }
    finally { setLoading(false); }
  }

  async function checkout() {
    if (!cartCount) return;

    // 1. Verify authentication and active session
    const token = localStorage.getItem('klickit_token');
    if (!user || !token || isTokenExpired(token)) {
      handleAuthFailure(user ? 'Session expired. Please sign in again.' : 'Please sign in to place your order.');
      return;
    }

    // 2. Validate real delivery coordinates (authoritative geographic source of truth)
    if (!coordinates || typeof coordinates.latitude !== 'number' || typeof coordinates.longitude !== 'number') {
      setAddressOpen(true);
      notify('Please set your delivery location on the map to proceed.');
      return;
    }

    // 3. Validate real customer phone number
    const customerPhone = user.phone?.trim();
    if (!isValidPhoneNumber(customerPhone)) {
      openAccount('profile');
      setEditingPhone(true);
      notify('Please provide a valid phone number before placing your order.');
      return;
    }

    // 4. Validate cart products belong to real catalog (UUID check)
    const invalidItems = cartItems.filter(p => !UUID_REGEX.test(String(p.id)));
    if (invalidItems.length > 0) {
      notify('Some items in your cart are not from the active catalog. Please refresh to load live products.');
      return;
    }

    setLoading(true);
    try {
      // 5. Generate fresh session ID for this checkout attempt
      const sessionId = 'cart_' + (window.crypto?.randomUUID ? crypto.randomUUID() : Math.random().toString(36).substring(2) + Date.now().toString(36));

      // 6. Synchronize local cart items to backend cart session
      for (const p of cartItems) {
        const addRes = await fetch(`${API}/api/cart/add`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ sessionId, productId: p.id, quantity: p.qty })
        });
        if (!addRes.ok) {
          const errBody = await addRes.json().catch(() => ({}));
          throw new Error(errBody.message || `Failed to add item (${p.name || p.id}) to server cart.`);
        }
      }

      // 7. Call backend checkout with real delivery info, coordinates, landmark, and Authorization header
      // customerAddress is optional now that coordinates are the authoritative geographic truth
      const resolvedAddress = address.trim() || null;
      const checkoutRes = await fetch(`${API}/api/orders/checkout`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`
        },
        body: JSON.stringify({
          sessionId,
          customerName: user.name || 'Customer',
          customerPhone: customerPhone,
          customerAddress: resolvedAddress,
          customerLatitude: coordinates.latitude,
          customerLongitude: coordinates.longitude,
          customerLandmark: landmark ? landmark.trim() : null
        })
      });

      if (checkoutRes.status === 401) {
        handleAuthFailure('Session expired. Please sign in again.');
        return;
      }

      const checkoutBody = await checkoutRes.json().catch(() => ({}));
      if (!checkoutRes.ok || !checkoutBody.success) {
        const errorMsg = checkoutBody.message || '';
        if (checkoutRes.status === 400 && (
            errorMsg.toLowerCase().includes('outside our service area') ||
            errorMsg.toLowerCase().includes('maximum radius') ||
            errorMsg.toLowerCase().includes('service area')
        )) {
          throw new Error('Delivery is not possible in this area.');
        }
        throw new Error(errorMsg || 'Checkout failed. Please try again.');
      }

      // 8. On success: clear local cart, close cart drawer, and open tracking modal with real order
      const placedOrder = checkoutBody.data;
      setCart({});
      setCartOpen(false);
      if (placedOrder?.meetAtGate) {
        notify('Order placed! Note: VIT campus delivery — please meet at Main Gate.');
      } else {
        notify('Order placed successfully! Tracking your delivery…');
      }
      openTracking(placedOrder);

    } catch (err) {
      notify(err.message === 'Failed to fetch' ? 'Could not connect to the server. Check your backend URL.' : err.message);
    }
    finally { setLoading(false); }
  }

  // Lifecycle stages matching real backend order lifecycle
  const lifecycleStages = ['PLACED', 'READY_TO_ASSIGN', 'ASSIGNED', 'OUT_FOR_DELIVERY', 'DELIVERED'];
  const stageLabels = {
    PLACED: 'Order Placed',
    READY_TO_ASSIGN: 'Approved (Preparing)',
    ASSIGNED: 'Driver Assigned',
    OUT_FOR_DELIVERY: 'Out for Delivery',
    DELIVERED: 'Delivered'
  };

  const currentStatus = activeOrder?.status;
  const isCancelled = currentStatus === 'CANCELLED';
  const isRejected = currentStatus === 'REJECTED';
  const currentStageIndex = lifecycleStages.indexOf(currentStatus);

  // ROUTE DISPATCH: Admin Dashboard
  if (currentPath === '/admin' || currentPath.startsWith('/admin/')) {
    if (user?.role === 'ADMIN') {
      return (
        <>
          <AdminDashboard
            user={user}
            onSignOut={handleSignOut}
            onNavigateStore={() => navigate('/')}
            notify={notify}
          />
          {toast && <div className="toast"><span>✦</span>{toast}</div>}
        </>
      );
    }
    return (
      <>
        <AdminAccessBarrier
          user={user}
          onSignIn={() => { setAuthMode('login'); setAuthOpen(true); }}
          onBackToStore={() => navigate('/')}
        />
        {authOpen && (
          <div className="modal-wrap">
            <button className="modal-backdrop" onClick={() => setAuthOpen(false)} aria-label="Close sign in"></button>
            <div className="auth-modal">
              <button className="close-button modal-close" onClick={() => setAuthOpen(false)}>×</button>
              <div className="auth-brand">k<span>!</span></div>
              <span className="section-kicker">ADMINISTRATOR SIGN IN</span>
              <h2>Sign in to Admin Panel</h2>
              <p>Enter administrator credentials to manage orders and drivers.</p>
              <form onSubmit={submitAuth}>
                <input required type="email" placeholder="Admin email address" value={authForm.email} onChange={e => setAuthForm({...authForm, email:e.target.value})}/>
                <input required minLength="6" type="password" placeholder="Password" value={authForm.password} onChange={e => setAuthForm({...authForm, password:e.target.value})}/>
                <button className="checkout-button" disabled={loading}>{loading ? 'Signing in…' : 'Sign in as Admin ↗'}</button>
              </form>
            </div>
          </div>
        )}
        {toast && <div className="toast"><span>✦</span>{toast}</div>}
      </>
    );
  }

  // ROUTE DISPATCH: Delivery Partner Dashboard
  if (currentPath === '/delivery' || currentPath.startsWith('/delivery/')) {
    if (user?.role === 'DELIVERY_PARTNER') {
      return (
        <>
          <DeliveryDashboard
            user={user}
            onSignOut={handleSignOut}
            onNavigateStore={() => navigate('/')}
            notify={notify}
          />
          {toast && <div className="toast"><span>✦</span>{toast}</div>}
        </>
      );
    }
    return (
      <>
        <DeliveryAccessBarrier
          user={user}
          onSignIn={() => { setAuthMode('login'); setAuthOpen(true); }}
          onBackToStore={() => navigate('/')}
        />
        {authOpen && (
          <div className="modal-wrap">
            <button className="modal-backdrop" onClick={() => setAuthOpen(false)} aria-label="Close sign in"></button>
            <div className="auth-modal">
              <button className="close-button modal-close" onClick={() => setAuthOpen(false)}>×</button>
              <div className="auth-brand">k<span>!</span></div>
              <span className="section-kicker">DELIVERY PARTNER SIGN IN</span>
              <h2>Sign in to Driver Portal</h2>
              <p>Enter your driver credentials to access your assigned orders.</p>
              <form onSubmit={submitAuth}>
                <input required type="email" placeholder="Driver email address" value={authForm.email} onChange={e => setAuthForm({...authForm, email:e.target.value})}/>
                <input required minLength="6" type="password" placeholder="Password" value={authForm.password} onChange={e => setAuthForm({...authForm, password:e.target.value})}/>
                <button className="checkout-button" disabled={loading}>{loading ? 'Signing in…' : 'Sign in as Driver ↗'}</button>
              </form>
            </div>
          </div>
        )}
        {toast && <div className="toast"><span>✦</span>{toast}</div>}
      </>
    );
  }

  // ROUTE DISPATCH: Order Details & Live Tracking (/orders/:orderId)
  const orderRouteMatch = currentPath.match(/^\/orders\/([0-9a-fA-F-]{36}|[a-zA-Z0-9_-]+)/);
  if (orderRouteMatch) {
    const orderId = orderRouteMatch[1];
    return (
      <div className="app-shell" style={{ minHeight: '100vh', background: '#f8faf9' }}>
        <header className="header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <a className="brand" href="#top" onClick={(e) => { e.preventDefault(); navigate('/'); }} aria-label="KlickIt home">
            <span className="brand-mark">k<span>!</span></span>
            <span className="brand-word">klick<span>it</span><i>.</i></span>
          </a>
          <button
            onClick={() => navigate('/')}
            style={{
              padding: '8px 16px',
              borderRadius: '8px',
              border: '1px solid #d2d5c8',
              background: '#fff',
              fontSize: '12px',
              fontWeight: 600,
              color: '#245b3b',
              cursor: 'pointer'
            }}
          >
            ← Return to Store
          </button>
        </header>

        <main style={{ maxWidth: '680px', margin: '24px auto', padding: '0 16px' }}>
          <div style={{ background: '#fff', borderRadius: '16px', padding: '24px', border: '1px solid #e2e8f0', boxShadow: '0 4px 20px rgba(0,0,0,0.04)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', borderBottom: '1px solid #f1f5f9', paddingBottom: '16px', marginBottom: '20px' }}>
              <div>
                <span className="section-kicker">ORDER TRACKING</span>
                <h1 style={{ fontSize: '20px', fontWeight: 800, margin: '4px 0 2px', color: '#0f172a' }}>
                  Order #{String(orderId).substring(0, 8)}
                </h1>
                <small style={{ color: '#888a7e', fontSize: '11px' }}>ID: {orderId}</small>
              </div>
              {currentStatus && (
                <span className={`status-pill ${String(currentStatus || '').toLowerCase()}`}>
                  {isCancelled ? '✕ CANCELLED' : isRejected ? '✕ REJECTED' : currentStatus}
                </span>
              )}
            </div>

            {trackingLoading && (
              <div style={{ textAlign: 'center', padding: '40px 0', color: '#64748b', fontSize: '13px' }}>
                Loading live order tracking...
              </div>
            )}

            {trackingError && (
              <div style={{ background: '#fef2f2', border: '1px solid #fecaca', borderRadius: '10px', padding: '16px', color: '#991b1b', fontSize: '12px', margin: '16px 0' }}>
                <b>Unable to load order: </b>{trackingError}
                <div style={{ marginTop: '12px' }}>
                  <button className="btn-refresh" onClick={() => fetchOrder(orderId)} style={{ height: '32px', fontSize: '11px' }}>
                    ↻ Retry
                  </button>
                </div>
              </div>
            )}

            {activeOrder && !trackingLoading && (
              <>
                {/* Stepper, Cancelled Banner, or Rejected Banner */}
                {isCancelled ? (
                  <div className="cancelled-banner terminal-banner" data-testid="terminal-banner-cancelled">
                    <span style={{ fontSize: '20px' }}>✕</span>
                    <div>
                      <b>Order Cancelled</b>
                      <p>{activeOrder.cancellationReason || 'This order has been cancelled and will not progress to delivery.'}</p>
                    </div>
                  </div>
                ) : isRejected ? (
                  <div className="cancelled-banner terminal-banner" data-testid="terminal-banner-rejected" style={{ background: '#fef2f2', borderColor: '#fca5a5' }}>
                    <span style={{ fontSize: '20px', color: '#dc2626' }}>✕</span>
                    <div>
                      <b style={{ color: '#991b1b' }}>Order Rejected</b>
                      <p style={{ color: '#b91c1c' }}>{activeOrder.rejectionReason || 'This order was rejected by the store administrator.'}</p>
                    </div>
                  </div>
                ) : (
                  <div className="status-stepper" data-testid="status-stepper">
                    <span className="section-kicker" style={{ fontSize: '8px' }}>DELIVERY PROGRESSION</span>
                    <div className="stepper-track stepper-steps">
                      {lifecycleStages.map((stage, idx) => {
                        const isCompleted = currentStageIndex > idx || (currentStageIndex === idx && stage === 'DELIVERED');
                        const isActive = currentStageIndex === idx && stage !== 'DELIVERED';
                        const isDone = currentStageIndex >= idx;
                        return (
                          <div
                            key={stage}
                            data-testid={`stepper-step-${stage.toLowerCase()}`}
                            className={`step-item ${isCompleted ? 'completed' : ''} ${isDone ? 'done' : ''} ${isActive ? 'active' : ''}`}
                          >
                            {idx < lifecycleStages.length - 1 && (
                              <div className={`step-line ${currentStageIndex > idx ? 'completed done' : ''}`} />
                            )}
                            <div className="step-circle">{isCompleted ? '✓' : idx + 1}</div>
                            <span className="step-label">{stageLabels[stage] || stage}</span>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}

                {/* Campus Meet-At-Gate Notice */}
                {activeOrder.meetAtGate && (
                  <div
                    data-testid="campus-delivery-warning"
                    style={{
                      background: 'rgba(234, 88, 12, 0.1)',
                      border: '1px solid rgba(234, 88, 12, 0.3)',
                      borderRadius: '12px',
                      padding: '14px 16px',
                      marginBottom: '16px',
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: '12px'
                    }}
                  >
                    <span style={{ fontSize: '20px', lineHeight: 1 }}>🏫</span>
                    <div>
                      <strong style={{ display: 'block', color: '#c2410c', fontSize: '13px', marginBottom: '2px' }}>
                        VIT campus delivery: Please come to the Main Gate to receive your order.
                      </strong>
                      <p style={{ margin: 0, fontSize: '12px', color: '#9a3412', lineHeight: '1.4' }}>
                        Orders addressed to campus locations are handed over directly at the Main Gate for security protocol.
                      </p>
                    </div>
                  </div>
                )}

                {/* Live Delivery Tracking Map */}
                <CustomerTrackingMap orderId={activeOrder.id} token={authToken} />

                {/* Order info details */}
                <div className="tracking-info-grid" style={{ marginTop: '20px' }}>
                  <div className="info-card">
                    <small>Total Amount (COD)</small>
                    <p><b>{money(activeOrder.totalAmount)}</b></p>
                  </div>
                  <div className="info-card">
                    <small>Delivery SLA</small>
                    <p><b>{activeOrder.deadline ? new Date(activeOrder.deadline).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '15 mins'}</b></p>
                  </div>
                  <div className="info-card" style={{ gridColumn: 'span 2' }}>
                    <small>{activeOrder.customerAddress ? 'Delivery Address' : 'Delivery Location'}</small>
                    <p>{activeOrder.customerAddress || (activeOrder.customerLatitude ? `Pinned Map Location (${activeOrder.customerLatitude.toFixed(4)}, ${activeOrder.customerLongitude.toFixed(4)})` : 'Pinned map location')}</p>
                    {activeOrder.customerLandmark && (
                      <p style={{ marginTop: '4px', fontSize: '12px', color: 'var(--muted, #64748b)' }}>
                        Delivery Instructions: {activeOrder.customerLandmark}
                      </p>
                    )}
                  </div>
                  {activeOrder.deliveryPartnerName && (
                    <div className="info-card" style={{ gridColumn: 'span 2' }}>
                      <small>Delivery Partner</small>
                      <p><b>{activeOrder.deliveryPartnerName}</b>{activeOrder.deliveryPartnerPhone ? ` (${activeOrder.deliveryPartnerPhone})` : ''}</p>
                    </div>
                  )}
                </div>

                {/* Ordered items */}
                {Array.isArray(activeOrder.items) && activeOrder.items.length > 0 && (
                  <div className="tracking-items" style={{ marginTop: '20px' }}>
                    <span className="section-kicker" style={{ fontSize: '8px', marginBottom: '8px', display: 'block' }}>ITEMS ORDERED ({activeOrder.items.length})</span>
                    {activeOrder.items.map((item, idx) => (
                      <div key={idx} className="tracking-item-row">
                        <div>
                          <span className="tracking-item-name">{item.productName}</span>
                          <span className="tracking-item-qty">× {item.quantity}</span>
                        </div>
                        <span className="tracking-item-total">{money(item.lineTotal || (item.price * item.quantity))}</span>
                      </div>
                    ))}
                  </div>
                )}

                {/* Action buttons */}
                <div className="tracking-actions" style={{ marginTop: '24px' }}>
                  <button className="btn-refresh" onClick={() => fetchOrder(activeOrder.id)} disabled={trackingLoading}>
                    <span>↻</span> Refresh Status
                  </button>
                  <button className="btn-close-tracking" onClick={() => navigate('/')}>
                    ← Return to Store
                  </button>
                </div>
              </>
            )}
          </div>
        </main>
        {toast && <div className="toast"><span>✦</span>{toast}</div>}
      </div>
    );
  }

  // DEFAULT ROUTE: Customer Storefront (Friend's visual design 100% preserved)
  return <div className="app-shell">
    <div className="announcement"><span>✦</span> Your everyday essentials, delivered in minutes <span className="announcement-right">Fresh finds. Happy prices. <b>♡</b></span></div>
    <header className="header">
      <a className="brand" href="#top" onClick={(e) => { e.preventDefault(); navigate('/'); }} aria-label="KlickIt home"><span className="brand-mark">k<span>!</span></span><span className="brand-word">klick<span>it</span><i>.</i></span></a>
      <button className="delivery-location" onClick={() => setAddressOpen(true)}>
        <span className="location-pin">⌖</span>
        <span className="location-copy">
          <b>Delivery in 8–15 minutes</b>
          <small>
            {coordinates
              ? (landmark ? `${landmark} (Pinned)` : address ? address : 'Location pinned on map')
              : 'Set delivery location'}
          </small>
        </span>
        <span className="chevron">⌄</span>
      </button>
      <label className="searchbar"><span className="search-icon">⌕</span><input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search for atta, dal, chips, and more..."/><kbd>⌘ K</kbd>{search && <button onClick={() => setSearch('')} aria-label="Clear search">×</button>}</label>

      {/* Role specific portals */}
      {user?.role === 'ADMIN' && (
        <button
          className="account-btn"
          style={{ background: '#eaf7ec', color: '#0c831f', fontWeight: 600, border: '1px solid #bbf7d0' }}
          onClick={() => navigate('/admin')}
          title="Open Administrator Portal"
        >
          <span>⚙</span><span>Admin Portal</span>
        </button>
      )}
      {user?.role === 'DELIVERY_PARTNER' && (
        <button
          className="account-btn"
          style={{ background: '#ede9fe', color: '#6d28d9', fontWeight: 600, border: '1px solid #ddd6fe' }}
          onClick={() => navigate('/delivery')}
          title="Open Delivery Partner Portal"
        >
          <span>🚴</span><span>Driver Portal</span>
        </button>
      )}

      {user && <button className="account-btn" onClick={() => openAccount('history')} title="View order history"><span className="account-icon">📜</span><span>My Orders</span></button>}
      <button className="account-btn" onClick={() => openAccount('profile')}><span className="account-icon">♙</span><span>{user?.name?.split(' ')[0] || 'Account'}</span></button>
      <button className={`cart-button ${cartCount ? 'has-items' : ''}`} onClick={() => setCartOpen(true)}><span className="cart-icon">🛍</span><span className="cart-label">My Cart</span>{cartCount > 0 && <span className="cart-count">{cartCount}</span>}</button>
    </header>
    <main id="top">
      <section className="hero-wrap"><div className="hero"><div className="hero-copy"><div className="eyebrow"><span className="sparkle">✳</span> THE NEIGHBOURHOOD STORE, REIMAGINED</div><h1>Good things.<br/><em>Great timing.</em></h1><p>From your morning chai to tonight's cravings — your daily needs are just a klick away.</p><button className="hero-cta" onClick={() => document.getElementById('shop')?.scrollIntoView({ behavior: 'smooth' })}>Shop everyday essentials <span>↗</span></button><div className="hero-trust"><span>✦</span> Fresh picks <i/> <span>◷</span> Quick delivery <i/> <span>♡</span> Happy prices</div></div><div className="hero-art"><div className="hero-circle"></div><div className="hero-sticker sticker-one">FRESH<br/><b>DAILY</b> ✳</div><div className="hero-sticker sticker-two">GOOD<br/>MOOD <span>☺</span></div><img className="hero-produce" src="https://images.unsplash.com/photo-1542838132-92c53300491e?auto=format&fit=crop&w=950&q=90" alt="Fresh colourful fruits and vegetables"/><div className="delivery-tag"><span className="delivery-tag-icon">⚡</span><span><b>At your door</b><small>Before you know it</small></span></div></div></div></section>
      <section className="benefits"><div className="benefit"><span className="benefit-icon mint">✿</span><span><b>Fresh, always</b><small>Handpicked goodness</small></span></div><div className="benefit"><span className="benefit-icon peach">↗</span><span><b>Fast to your door</b><small>Minutes, not hours</small></span></div><div className="benefit"><span className="benefit-icon lilac">♡</span><span><b>Prices you'll love</b><small>Little joys, every day</small></span></div><div className="benefit"><span className="benefit-icon yellow">✓</span><span><b>Quality checked</b><small>Good stuff guaranteed</small></span></div></section>
      <section className="categories-section"><div className="section-heading"><div><span className="section-kicker">A LITTLE BIT OF EVERYTHING</span><h2>What are we <em>picking up?</em></h2></div><span className="side-note">Your list, your way <span>↗</span></span></div><div className="category-grid">{categories.slice(1).map(c => <button key={c.name} className={`category-tile ${activeCategory === c.name ? 'selected' : ''}`} onClick={() => { setActiveCategory(activeCategory === c.name ? 'All' : c.name); document.getElementById('shop')?.scrollIntoView({ behavior: 'smooth', block: 'start' }); }}><span className="category-art" style={{ background: c.tint }}>{c.icon}</span><span>{c.name}</span><span className="category-arrow">↗</span></button>)}</div></section>
      <section className="shop-section" id="shop">
        {catalogError && (
          <div style={{ background: '#fef2f2', border: '1px solid #fecaca', borderRadius: '12px', padding: '12px 16px', color: '#991b1b', fontSize: '12px', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span>⚠️</span>
            <span><b>Live catalog offline:</b> Unable to connect to backend product service. Displaying preview catalog. Checkout requires live catalog products.</span>
          </div>
        )}
        <div className="section-heading shop-heading"><div><span className="section-kicker">THE GOOD STUFF, RIGHT HERE</span><h2>{search ? <>Results for <em>“{search}”</em></> : activeCategory === 'All' ? <>Popular <em>right now</em></> : <>{activeCategory} <em>for you</em></>}</h2></div><div className="shop-meta"><span className="live-dot"></span> Ready in minutes</div></div>
        {filtered.length ? <div className="product-grid">{filtered.map(p => <article className="product-card" key={p.id}><div className="product-image-wrap"><span className="product-time"><span>◷</span> {p.time || '10 mins'}</span>{p.mrp > p.price && <span className="discount-tag">{Math.round((p.mrp-p.price)/p.mrp*100)}% OFF</span>}<img src={p.image || seedProducts[0].image} alt={p.name} loading="lazy" onError={e => { e.currentTarget.src = seedProducts[0].image; }}/></div><div className="product-info"><div className="product-category">{p.category || 'DAILY ESSENTIALS'}</div><h3>{p.name}</h3><p>{p.description}</p><div className="product-bottom"><div className="price-stack"><b>{money(p.price)}</b>{p.mrp > p.price && <del>{money(p.mrp)}</del>}</div>{cart[p.id] ? <div className="qty-control"><button onClick={() => changeQty(p.id, -1)} aria-label={`Remove one ${p.name}`}>−</button><b>{cart[p.id]}</b><button onClick={() => changeQty(p.id, 1)} aria-label={`Add one ${p.name}`}>+</button></div> : <button className="add-button" onClick={() => { changeQty(p.id, 1); notify(`${p.name} added to cart`); }}>ADD <span>＋</span></button>}</div></div></article>)}</div> : <div className="empty-search"><span>🧺</span><h3>No matches just yet</h3><p>Try another search or browse all our everyday essentials.</p><button onClick={() => { setSearch(''); setActiveCategory('All'); }}>See all products</button></div>}
      </section>
      <section className="promo-banner"><div className="promo-decoration">✳</div><div><span className="section-kicker">A LITTLE SOMETHING EXTRA</span><h2>Your first basket<br/>looks <em>better on us.</em></h2><p>Good things start with a little treat. Save on your first order.</p></div><div className="promo-code"><span>USE CODE</span><b>KLICKFIRST</b><small>Terms & conditions apply</small></div><div className="promo-sun">☺</div></section>
      <footer className="footer"><div className="footer-top"><div className="footer-brand"><a className="brand" href="#top" onClick={(e) => { e.preventDefault(); navigate('/'); }}><span className="brand-mark">k<span>!</span></span><span className="brand-word">klick<span>it</span><i>.</i></span></a><p>Everyday things. Extraordinary convenience.</p></div><div className="footer-col"><b>Discover</b><a href="#shop">All products</a><a href="#shop" onClick={() => setActiveCategory('Fruits & Veg')}>Fresh produce</a><a href="#shop" onClick={() => setActiveCategory('Munchies')}>Snacks & munchies</a></div><div className="footer-col"><b>Portals</b><a href="#top" onClick={(e) => { e.preventDefault(); navigate('/admin'); }}>Admin Portal</a><a href="#top" onClick={(e) => { e.preventDefault(); navigate('/delivery'); }}>Delivery Portal</a></div><div className="footer-note"><span>MADE FOR YOUR EVERYDAY ✳</span><p>More living, less running around.</p><div className="social-dots"><i>ig</i><i>in</i><i>♡</i></div></div></div><div className="footer-bottom"><span>© 2026 KlickIt. All little joys reserved.</span><span>Made with a little <b>♥</b> for everyday life.</span></div></footer>
    </main>
    {addressOpen && (
      <LocationPicker
        initialAddress={address}
        initialLandmark={landmark}
        initialCoordinates={coordinates}
        onSave={({ address: newAddr, landmark: newLandmark, coordinates: newCoords }) => {
          setAddress(newAddr);
          localStorage.setItem('klickit_address', newAddr);
          setLandmark(newLandmark || '');
          if (newLandmark) {
            localStorage.setItem('klickit_landmark', newLandmark);
          } else {
            localStorage.removeItem('klickit_landmark');
          }
          setCoordinates(newCoords);
          localStorage.setItem('klickit_coordinates', JSON.stringify(newCoords));
          setAddressOpen(false);
          notify('Delivery location updated');
        }}
        onClose={() => setAddressOpen(false)}
      />
    )}
    {cartOpen && <><button className="overlay" onClick={() => setCartOpen(false)} aria-label="Close cart"></button><aside className="cart-drawer"><div className="drawer-header"><div><span className="section-kicker">YOUR LITTLE HAUL</span><h2>My cart <span>({cartCount})</span></h2></div><button className="close-button" onClick={() => setCartOpen(false)}>×</button></div>{cartCount ? <><div className="delivery-progress"><span>✦</span><div><b>{subtotal >= 199 ? 'You unlocked free delivery!' : `Add ${money(199-subtotal)} more for free delivery`}</b><div className="progress-track"><i style={{ width: `${Math.min(100, subtotal/199*100)}%` }}></i></div></div></div><div className="drawer-items">{cartItems.map(p => <div className="drawer-item" key={p.id}><img src={p.image} alt=""/><div className="drawer-item-info"><b>{p.name}</b><small>{p.description}</small><strong>{money(p.price)}</strong></div><div className="qty-control"><button onClick={() => changeQty(p.id,-1)}>−</button><b>{p.qty}</b><button onClick={() => changeQty(p.id,1)}>+</button></div></div>)}</div><div className="drawer-summary"><div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderBottom: '1px dashed #e2e4dc', marginBottom: '10px' }}><div style={{ fontSize: '11px', textAlign: 'left', maxWidth: '70%' }}><span style={{ color: '#888a7e', display: 'block', fontSize: '9px', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Delivery Address</span><b style={{ color: address ? '#1b1d19' : '#dc2626', wordBreak: 'break-word', display: 'block', marginTop: '2px' }}>{address ? (address + (landmark ? ` (${landmark})` : '')) : 'No address set — required'}</b>{coordinates ? <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '9px', color: '#15803d', fontWeight: 600, marginTop: '2px' }}>✓ Location pin set</span> : <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '9px', color: '#dc2626', fontWeight: 600, marginTop: '2px' }}>⚠ Map pin required</span>}</div><button type="button" onClick={() => setAddressOpen(true)} style={{ background: '#f5f6f2', border: '1px solid #d2d5c8', borderRadius: '6px', padding: '4px 10px', fontSize: '10px', cursor: 'pointer', fontWeight: 600, color: '#245b3b' }}>{address ? 'Change' : '+ Add'}</button></div><div><span>Item total</span><b>{money(subtotal+discount)}</b></div>{discount > 0 && <div className="saving-line"><span>Product savings</span><b>−{money(discount)}</b></div>}<div><span>Delivery fee</span><b>{delivery ? money(delivery) : <span className="free-label">FREE</span>}</b></div><div className="grand-total"><span>To pay</span><b>{money(subtotal+delivery)}</b></div><button className="checkout-button" onClick={checkout} disabled={loading}>{loading ? 'Working on it…' : <>Proceed to checkout <span>{money(subtotal+delivery)} ↗</span></>}</button><small className="secure-note">♡ Secure checkout · Cash on delivery</small></div></> : <div className="empty-cart"><span>🧺</span><h3>Your basket's taking a nap</h3><p>Let's fill it with a few everyday favourites.</p><button onClick={() => setCartOpen(false)}>Start shopping ↗</button></div>}</aside></>}
    {authOpen && (
      <div className="modal-wrap">
        <button className="modal-backdrop" onClick={() => setAuthOpen(false)} aria-label="Close sign in"></button>
        <div className="auth-modal">
          <button className="close-button modal-close" onClick={() => setAuthOpen(false)}>×</button>
          <div className="auth-brand">k<span>!</span></div>
          <span className="section-kicker">YOUR EVERYDAY, MADE EASIER</span>

          <div className="auth-mode-tabs">
            <button
              type="button"
              className={`auth-mode-tab ${authMode === 'otp' ? 'active' : ''}`}
              onClick={() => setAuthMode('otp')}
            >
              Email Code
            </button>
            <button
              type="button"
              className={`auth-mode-tab ${authMode === 'login' ? 'active' : ''}`}
              onClick={() => setAuthMode('login')}
            >
              Password
            </button>
            <button
              type="button"
              className={`auth-mode-tab ${authMode === 'register' ? 'active' : ''}`}
              onClick={() => setAuthMode('register')}
            >
              Register
            </button>
          </div>

          {authMode === 'otp' && (
            <>
              <h2>Sign in with Code</h2>
              <p>We'll send a 6-digit verification code to your email.</p>
              <OtpLoginForm
                onSuccess={(token, profile) => {
                  localStorage.setItem('klickit_token', token);
                  localStorage.setItem('klickit_user', JSON.stringify(profile));
                  setUser(profile);
                  setAuthOpen(false);
                  notify(`Welcome${profile.name ? `, ${profile.name}` : ''}!`);
                  if (profile.role === 'ADMIN') {
                    navigate('/admin');
                  } else if (profile.role === 'DELIVERY_PARTNER') {
                    navigate('/delivery');
                  }
                }}
                onSwitchToPassword={() => setAuthMode('login')}
                onSwitchToRegister={() => setAuthMode('register')}
                apiBaseUrl={API}
              />
            </>
          )}

          {authMode === 'login' && (
            <>
              <h2>Welcome back!</h2>
              <p>Sign in to pick up right where you left off.</p>
              <div style={{ display: 'flex', gap: '8px', marginBottom: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
                <span style={{ fontSize: '11px', color: '#6b7280' }}>Quick fill:</span>
                <button
                  type="button"
                  onClick={() => setAuthForm({ ...authForm, email: 'admin@klickit.test', password: 'CustomerPassword123!' })}
                  style={{ fontSize: '11px', padding: '4px 10px', background: '#eaf7ec', color: '#0c831f', border: '1px solid #bbf7d0', borderRadius: '6px', cursor: 'pointer', fontWeight: 600 }}
                >
                  ⚙ Admin Demo
                </button>
                <button
                  type="button"
                  onClick={() => setAuthForm({ ...authForm, email: 'driver1@klickit.test', password: 'CustomerPassword123!' })}
                  style={{ fontSize: '11px', padding: '4px 10px', background: '#ede9fe', color: '#6d28d9', border: '1px solid #ddd6fe', borderRadius: '6px', cursor: 'pointer', fontWeight: 600 }}
                >
                  🚴 Driver Demo
                </button>
              </div>
              <form onSubmit={submitAuth}>
                <input
                  required
                  type="email"
                  placeholder="Email address"
                  value={authForm.email}
                  onChange={e => setAuthForm({ ...authForm, email: e.target.value })}
                />
                <input
                  required
                  minLength="6"
                  type="password"
                  placeholder="Password (at least 6 characters)"
                  value={authForm.password}
                  onChange={e => setAuthForm({ ...authForm, password: e.target.value })}
                />
                <button className="checkout-button" disabled={loading}>
                  {loading ? 'One moment…' : 'Sign in ↗'}
                </button>
              </form>
              <div className="auth-switch">
                Prefer passwordless? <button type="button" onClick={() => setAuthMode('otp')}>Sign in with Email Code</button>
              </div>
            </>
          )}

          {authMode === 'register' && (
            <>
              <h2>Come on in!</h2>
              <p>Create an account for a little more convenience.</p>
              <form onSubmit={submitAuth}>
                <input
                  required
                  placeholder="Your name"
                  value={authForm.name}
                  onChange={e => setAuthForm({ ...authForm, name: e.target.value })}
                />
                <input
                  required
                  type="email"
                  placeholder="Email address"
                  value={authForm.email}
                  onChange={e => setAuthForm({ ...authForm, email: e.target.value })}
                />
                <input
                  required
                  placeholder="Phone number (required)"
                  value={authForm.phone}
                  onChange={e => setAuthForm({ ...authForm, phone: e.target.value })}
                />
                <input
                  required
                  minLength="6"
                  type="password"
                  placeholder="Password (at least 6 characters)"
                  value={authForm.password}
                  onChange={e => setAuthForm({ ...authForm, password: e.target.value })}
                />
                <button className="checkout-button" disabled={loading}>
                  {loading ? 'One moment…' : 'Create my account ↗'}
                </button>
              </form>
              <div className="auth-switch">
                Already have an account? <button type="button" onClick={() => setAuthMode('otp')}>Sign in with Code</button>
              </div>
            </>
          )}

          <small className="auth-legal">By continuing, you agree to our Terms of Service and Privacy Policy.</small>
        </div>
      </div>
    )}

    {trackingOpen && <div className="modal-wrap"><button className="modal-backdrop" onClick={closeTracking} aria-label="Close tracking"></button>
      <div className="tracking-modal">
        <div className="tracking-header">
          <div>
            <span className="section-kicker">CUSTOMER ORDER TRACKING</span>
            <h2>{activeOrder?.id ? `Order #${String(activeOrder.id).substring(0, 8)}` : 'Order Details'}</h2>
            {activeOrder?.id && <small style={{ color: '#888a7e', fontSize: '10px' }}>Full ID: {activeOrder.id}</small>}
          </div>
          <button className="close-button" onClick={closeTracking} aria-label="Close tracking">×</button>
        </div>

        {trackingLoading && <div style={{ textAlign: 'center', padding: '24px 0', color: '#77796f', fontSize: '12px' }}>Loading latest order status...</div>}

        {trackingError && <div style={{ background: '#fef2f2', border: '1px solid #fecaca', borderRadius: '10px', padding: '12px', color: '#991b1b', fontSize: '11px', margin: '12px 0' }}>
          <b>Error: </b>{trackingError}
          <div style={{ marginTop: '8px' }}><button className="btn-refresh" onClick={() => fetchOrder(activeOrder?.id)} style={{ height: '30px', fontSize: '10px' }}>Retry</button></div>
        </div>}

        {activeOrder && !trackingLoading && <>
          {/* Status badge */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', margin: '10px 0' }}>
            <span style={{ fontSize: '11px', color: '#77796f' }}>Current Status:</span>
            <span className={`status-pill ${String(currentStatus || '').toLowerCase()}`}>
              {isCancelled ? '✕ CANCELLED' : isRejected ? '✕ REJECTED' : currentStatus}
            </span>
          </div>

          {/* Stepper, Cancelled Banner, or Rejected Banner */}
          {isCancelled ? (
            <div className="cancelled-banner terminal-banner" data-testid="terminal-banner-cancelled">
              <span style={{ fontSize: '20px' }}>✕</span>
              <div>
                <b>Order Cancelled</b>
                <p>{activeOrder.cancellationReason || 'This order has been cancelled and will not progress to delivery.'}</p>
              </div>
            </div>
          ) : isRejected ? (
            <div className="cancelled-banner terminal-banner" data-testid="terminal-banner-rejected" style={{ background: '#fef2f2', borderColor: '#fca5a5' }}>
              <span style={{ fontSize: '20px', color: '#dc2626' }}>✕</span>
              <div>
                <b style={{ color: '#991b1b' }}>Order Rejected</b>
                <p style={{ color: '#b91c1c' }}>{activeOrder.rejectionReason || 'This order was rejected by the store administrator.'}</p>
              </div>
            </div>
          ) : (
            <div className="status-stepper" data-testid="status-stepper">
              <span className="section-kicker" style={{ fontSize: '8px' }}>DELIVERY PROGRESSION</span>
              <div className="stepper-track stepper-steps">
                {lifecycleStages.map((stage, idx) => {
                  const isCompleted = currentStageIndex > idx || (currentStageIndex === idx && stage === 'DELIVERED');
                  const isActive = currentStageIndex === idx && stage !== 'DELIVERED';
                  const isDone = currentStageIndex >= idx;
                  return (
                    <div
                      key={stage}
                      data-testid={`stepper-step-${stage.toLowerCase()}`}
                      className={`step-item ${isCompleted ? 'completed' : ''} ${isDone ? 'done' : ''} ${isActive ? 'active' : ''}`}
                    >
                      {idx < lifecycleStages.length - 1 && (
                        <div className={`step-line ${currentStageIndex > idx ? 'completed done' : ''}`} />
                      )}
                      <div className="step-circle">{isCompleted ? '✓' : idx + 1}</div>
                      <span className="step-label">{stageLabels[stage] || stage}</span>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Campus Meet-At-Gate Notice */}
          {activeOrder.meetAtGate && (
            <div
              data-testid="campus-delivery-warning"
              style={{
                background: 'rgba(234, 88, 12, 0.1)',
                border: '1px solid rgba(234, 88, 12, 0.3)',
                borderRadius: '12px',
                padding: '14px 16px',
                marginBottom: '16px',
                display: 'flex',
                alignItems: 'flex-start',
                gap: '12px'
              }}
            >
              <span style={{ fontSize: '20px', lineHeight: 1 }}>🏫</span>
              <div>
                <strong style={{ display: 'block', color: '#c2410c', fontSize: '13px', marginBottom: '2px' }}>
                  VIT campus delivery: Please come to the Main Gate to receive your order.
                </strong>
                <p style={{ margin: 0, fontSize: '12px', color: '#9a3412', lineHeight: '1.4' }}>
                  Orders addressed to campus locations are handed over directly at the Main Gate for security protocol.
                </p>
              </div>
            </div>
          )}

          {/* Live Delivery Tracking Map */}
          {activeOrder?.id && (
            <CustomerTrackingMap orderId={activeOrder.id} token={authToken} />
          )}

          {/* Order info details */}
          <div className="tracking-info-grid">
            <div className="info-card">
              <small>Total Amount (COD)</small>
              <p><b>{money(activeOrder.totalAmount)}</b></p>
            </div>
            <div className="info-card">
              <small>Delivery SLA</small>
              <p><b>{activeOrder.deadline ? new Date(activeOrder.deadline).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '15 mins'}</b></p>
            </div>
            <div className="info-card" style={{ gridColumn: 'span 2' }}>
              <small>{activeOrder.customerAddress ? 'Delivery Address' : 'Delivery Location'}</small>
              <p>{activeOrder.customerAddress || (activeOrder.customerLatitude ? `Pinned Map Location (${activeOrder.customerLatitude.toFixed(4)}, ${activeOrder.customerLongitude.toFixed(4)})` : 'Pinned map location')}</p>
              {activeOrder.customerLandmark && (
                <p style={{ marginTop: '4px', fontSize: '12px', color: 'var(--muted, #64748b)' }}>
                  Delivery Instructions: {activeOrder.customerLandmark}
                </p>
              )}
            </div>
            {activeOrder.deliveryPartnerName && (
              <div className="info-card" style={{ gridColumn: 'span 2' }}>
                <small>Delivery Partner</small>
                <p><b>{activeOrder.deliveryPartnerName}</b>{activeOrder.deliveryPartnerPhone ? ` (${activeOrder.deliveryPartnerPhone})` : ''}</p>
              </div>
            )}
          </div>

          {/* Ordered items */}
          {Array.isArray(activeOrder.items) && activeOrder.items.length > 0 && (
            <div className="tracking-items">
              <span className="section-kicker" style={{ fontSize: '8px', marginBottom: '8px', display: 'block' }}>ITEMS ORDERED ({activeOrder.items.length})</span>
              {activeOrder.items.map((item, idx) => (
                <div key={idx} className="tracking-item-row">
                  <div>
                    <span className="tracking-item-name">{item.productName}</span>
                    <span className="tracking-item-qty">× {item.quantity}</span>
                  </div>
                  <span className="tracking-item-total">{money(item.lineTotal || (item.price * item.quantity))}</span>
                </div>
              ))}
            </div>
          )}

          {/* Action buttons */}
          <div className="tracking-actions">
            <button className="btn-refresh" onClick={() => fetchOrder(activeOrder.id)} disabled={trackingLoading}>
              <span>↻</span> Refresh Status
            </button>
            <button className="btn-close-tracking" onClick={closeTracking}>Done</button>
          </div>
        </>}
      </div>
    </div>}

    {accountOpen && <div className="modal-wrap"><button className="modal-backdrop" onClick={() => setAccountOpen(false)} aria-label="Close account"></button>
      <div className="account-modal order-history-drawer">
        <div className="tracking-header">
          <div>
            <span className="section-kicker">ACCOUNT & PREFERENCES</span>
            <h2>{user?.name || 'My Account'}</h2>
            <small style={{ color: '#888a7e', fontSize: '10px' }}>{user?.email}</small>
          </div>
          <button className="close-button" onClick={() => setAccountOpen(false)} aria-label="Close account">×</button>
        </div>

        <div className="account-tabs">
          <button className={`account-tab-btn ${accountTab === 'profile' ? 'active' : ''}`} onClick={() => setAccountTab('profile')}>
            Profile Details
          </button>
          <button className={`account-tab-btn ${accountTab === 'history' ? 'active' : ''}`} onClick={() => { setAccountTab('history'); fetchOrderHistory(); }}>
            Order History
          </button>
        </div>

        {accountTab === 'profile' && <div className="profile-card">
          <div className="profile-row">
            <span>Name</span>
            <b>{user?.name || 'Not set'}</b>
          </div>
          <div className="profile-row">
            <span>Email Address</span>
            <b>{user?.email || 'Not set'}</b>
          </div>
          <div className="profile-row">
            <span>Account Role</span>
            <b style={{ color: user?.role === 'ADMIN' ? '#0c831f' : user?.role === 'DELIVERY_PARTNER' ? '#7c3aed' : '#2563eb' }}>
              {user?.role || 'CUSTOMER'}
            </b>
          </div>
          <div className="profile-row">
            <span>Phone Number</span>
            {editingPhone ? (
              <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
                <input
                  value={phoneInput}
                  onChange={e => setPhoneInput(e.target.value)}
                  placeholder="10-digit phone"
                  style={{ padding: '4px 8px', fontSize: '11px', borderRadius: '4px', border: '1px solid #ccc', width: '130px' }}
                />
                <button
                  type="button"
                  onClick={() => {
                    if (!isValidPhoneNumber(phoneInput)) {
                      notify('Please enter a valid phone number (7-20 digits)');
                      return;
                    }
                    const updatedUser = { ...user, phone: phoneInput.trim() };
                    setUser(updatedUser);
                    localStorage.setItem('klickit_user', JSON.stringify(updatedUser));
                    setEditingPhone(false);
                    notify('Phone number updated');
                  }}
                  style={{ background: '#245b3b', color: '#fff', border: 'none', borderRadius: '4px', padding: '4px 8px', fontSize: '10px', cursor: 'pointer' }}
                >
                  Save
                </button>
                <button
                  type="button"
                  onClick={() => setEditingPhone(false)}
                  style={{ background: 'none', border: 'none', color: '#777', cursor: 'pointer', fontSize: '10px' }}
                >
                  ✕
                </button>
              </div>
            ) : (
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <b>{user?.phone || 'Not set'}</b>
                <button
                  type="button"
                  onClick={() => { setPhoneInput(user?.phone || ''); setEditingPhone(true); }}
                  style={{ background: 'none', border: 'none', color: '#245b3b', cursor: 'pointer', fontSize: '10px', textDecoration: 'underline' }}
                >
                  {user?.phone ? 'Edit' : 'Add'}
                </button>
              </div>
            )}
          </div>
          <div className="profile-row">
            <span>Saved Area</span>
            <b>{address || 'Not set'}</b>
          </div>

          {/* Quick jump to authorized dashboard */}
          {user?.role === 'ADMIN' && (
            <button
              type="button"
              className="checkout-button"
              style={{ marginTop: '12px', background: '#0c831f' }}
              onClick={() => { setAccountOpen(false); navigate('/admin'); }}
            >
              Open Administrator Dashboard ⚙ ↗
            </button>
          )}
          {user?.role === 'DELIVERY_PARTNER' && (
            <button
              type="button"
              className="checkout-button"
              style={{ marginTop: '12px', background: '#7c3aed' }}
              onClick={() => { setAccountOpen(false); navigate('/delivery'); }}
            >
              Open Delivery Partner Dashboard 🚴 ↗
            </button>
          )}

          <button className="btn-signout" style={{ marginTop: '16px' }} onClick={handleSignOut}>Sign Out</button>
        </div>}

        {accountTab === 'history' && <div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px' }}>
            <span className="section-kicker" style={{ fontSize: '9px', fontWeight: 800, letterSpacing: '0.08em' }}>
              PAST ORDERS {orderHistory.length > 0 ? `(${orderHistory.length})` : ''}
            </span>
            <button
              className="btn-refresh"
              onClick={fetchOrderHistory}
              disabled={historyLoading}
              style={{ height: '28px', fontSize: '10px', padding: '0 10px', flex: 'none', cursor: historyLoading ? 'wait' : 'pointer' }}
              aria-label="Refresh order history"
            >
              <span>↻</span> {historyLoading ? 'Refreshing...' : 'Refresh'}
            </button>
          </div>

          {historyLoading && orderHistory.length === 0 && (
            <div style={{ textAlign: 'center', padding: '28px 0', color: '#77796f', fontSize: '12px' }}>
              Loading your orders...
            </div>
          )}

          {historyError && (
            <div style={{ background: '#fef2f2', border: '1px solid #fecaca', borderRadius: '10px', padding: '12px', color: '#991b1b', fontSize: '11px', margin: '12px 0' }}>
              <b>Error: </b>{historyError}
              <div style={{ marginTop: '8px' }}>
                <button className="btn-refresh" onClick={fetchOrderHistory} style={{ height: '30px', fontSize: '10px' }}>
                  Retry
                </button>
              </div>
            </div>
          )}

          {!historyLoading && !historyError && orderHistory.length === 0 && (
            <div className="empty-search" style={{ padding: '36px 16px', minHeight: 'auto', textAlign: 'center' }}>
              <span style={{ fontSize: '42px', marginBottom: '8px', display: 'block' }}>🧺</span>
              <h3 style={{ margin: '0 0 6px', fontSize: '16px' }}>No orders yet</h3>
              <p style={{ margin: '0 0 16px', fontSize: '12px', color: '#77796f', lineHeight: 1.5, maxWidth: '280px' }}>
                When you place an order, it will appear here so you can track its delivery status anytime.
              </p>
              <button
                style={{ background: '#245b3b', color: '#fff', border: 0, borderRadius: '8px', padding: '10px 18px', fontSize: '12px', fontWeight: 700 }}
                onClick={() => { setAccountOpen(false); document.getElementById('shop')?.scrollIntoView({ behavior: 'smooth' }); }}
              >
                Start Shopping ↗
              </button>
            </div>
          )}

          {!historyError && orderHistory.length > 0 && (
            <div className="order-history-list">
              {orderHistory.map(order => {
                const dateStr = order.createdAt ? new Date(order.createdAt).toLocaleDateString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '';
                const itemsCount = Array.isArray(order.items) ? order.items.reduce((sum, it) => sum + (it.quantity || 1), 0) : 0;
                const itemsSummary = Array.isArray(order.items)
                  ? order.items.map(it => `${it.productName}${it.quantity > 1 ? ` (×${it.quantity})` : ''}`).filter(Boolean).join(', ')
                  : '';
                const isCancelled = order.status === 'CANCELLED';
                const isRejected = order.status === 'REJECTED';

                const displayAddress = order.customerAddress && order.customerAddress.trim()
                  ? order.customerAddress.trim()
                  : 'Pinned Map Location';

                return (
                  <button
                    key={order.id}
                    className="order-card order-card-summary"
                    onClick={() => {
                      setAccountOpen(false);
                      openTracking(order);
                      fetchOrder(order.id);
                    }}
                  >
                    <div className="order-card-header">
                      <span className="order-card-id">#{String(order.id).substring(0, 8)}</span>
                      <span className={`status-pill ${String(order.status || '').toLowerCase()}`}>
                        {isCancelled ? '✕ CANCELLED' : isRejected ? '✕ REJECTED' : order.status}
                      </span>
                    </div>

                    <div className="order-card-meta">
                      <span className="order-card-date">{dateStr || 'Recent'}</span>
                      <b className="order-card-total">{money(order.totalAmount)}</b>
                    </div>

                    <div className="order-card-location">
                      <span className="order-loc-text">📍 {displayAddress}</span>
                      {order.customerLandmark && (
                        <span className="order-loc-landmark">({order.customerLandmark})</span>
                      )}
                    </div>

                    {itemsSummary && (
                      <div className="order-card-items order-card-items-preview">
                        <span className="order-items-count">{itemsCount} item{itemsCount !== 1 ? 's' : ''}: </span>
                        <span className="order-items-names">{itemsSummary.length > 55 ? `${itemsSummary.substring(0, 52)}…` : itemsSummary}</span>
                      </div>
                    )}

                    <div className="order-card-footer">
                      <span className="order-card-cta">Track Order Details <span>↗</span></span>
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>}
      </div>
    </div>}

    {toast && <div className="toast"><span>✦</span>{toast}</div>}
    <nav className="mobile-nav">
      <button onClick={() => { navigate('/'); setActiveCategory('All'); window.scrollTo({top:0,behavior:'smooth'}); }}><span>⌂</span>Home</button>
      <button onClick={() => { navigate('/'); document.getElementById('shop')?.scrollIntoView({behavior:'smooth'}); }}><span>⌕</span>Explore</button>
      <button onClick={() => setCartOpen(true)}><span>🛍</span>Cart {cartCount ? `(${cartCount})` : ''}</button>
      {user?.role === 'ADMIN' && <button onClick={() => navigate('/admin')}><span>⚙</span>Admin</button>}
      {user?.role === 'DELIVERY_PARTNER' && <button onClick={() => navigate('/delivery')}><span>🚴</span>Driver</button>}
      <button onClick={() => openAccount('profile')}><span>♙</span>Account</button>
    </nav>
  </div>;
}

createRoot(document.getElementById('root')).render(<React.StrictMode><App /></React.StrictMode>);
