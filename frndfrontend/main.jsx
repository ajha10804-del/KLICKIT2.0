import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

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

function App() {
  const [products, setProducts] = useState(seedProducts);
  const [catalogError, setCatalogError] = useState(false);
  const [activeCategory, setActiveCategory] = useState('All');
  const [search, setSearch] = useState('');
  const [cart, setCart] = useState({});
  const [cartOpen, setCartOpen] = useState(false);
  const [authOpen, setAuthOpen] = useState(false);
  const [authMode, setAuthMode] = useState('login');
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
    const saved = localStorage.getItem('klickit_address') || '';
    return isValidDeliveryAddress(saved) ? saved : '';
  });
  const [addressOpen, setAddressOpen] = useState(false);
  const [authForm, setAuthForm] = useState({ name: '', email: '', password: '', phone: '' });
  const [loading, setLoading] = useState(false);
  const [editingPhone, setEditingPhone] = useState(false);
  const [phoneInput, setPhoneInput] = useState('');

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
  const notify = message => { setToast(message); window.setTimeout(() => setToast(''), 2600); };
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
    setAuthMode('login');
    setAuthOpen(true);
    notify(msg);
  }

  async function fetchOrderHistory() {
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

  function handleSignOut() {
    localStorage.removeItem('klickit_token');
    localStorage.removeItem('klickit_user');
    setUser(null);
    setAccountOpen(false);
    setEditingPhone(false);
    notify('Signed out successfully.');
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
    setActiveOrder(order);
    setTrackingError(null);
    setTrackingOpen(true);
    if (order?.id) {
      window.history.replaceState(null, '', `#order-${order.id}`);
    }
  }

  function closeTracking() {
    setTrackingOpen(false);
    if (window.location.hash.startsWith('#order-')) {
      window.history.replaceState(null, '', window.location.pathname);
    }
  }

  // Handle direct navigation or refresh with #order-:id
  useEffect(() => {
    const hash = window.location.hash;
    if (hash.startsWith('#order-')) {
      const orderId = hash.replace('#order-', '').trim();
      if (orderId) {
        setTrackingOpen(true);
        fetchOrder(orderId);
      }
    }
  }, []);

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
        phone: resUser?.phone || authForm.phone || ''
      };
      localStorage.setItem('klickit_user', JSON.stringify(profile)); setUser(profile); setAuthOpen(false); notify(`Welcome${profile.name ? `, ${profile.name}` : ''}!`);
    } catch (err) { notify(err.message === 'Failed to fetch' ? 'Could not connect to the server. Check that your backend is running.' : err.message); }
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

    // 2. Validate real delivery address
    if (!isValidDeliveryAddress(address)) {
      setAddressOpen(true);
      notify('Please enter a valid delivery address (at least 10 characters).');
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

      // 7. Call backend checkout with real delivery info and Authorization header
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
          customerAddress: address.trim()
        })
      });

      if (checkoutRes.status === 401) {
        handleAuthFailure('Session expired. Please sign in again.');
        return;
      }

      const checkoutBody = await checkoutRes.json().catch(() => ({}));
      if (!checkoutRes.ok || !checkoutBody.success) {
        throw new Error(checkoutBody.message || 'Could not place your order. Please try again.');
      }

      // 8. On success: clear local cart, close cart drawer, open Order Confirmation & Tracking
      const order = checkoutBody.data;
      setCart({});
      setCartOpen(false);
      openTracking(order);
      notify('Order placed successfully!');
    } catch (err) {
      // On failure: local cart remains untouched so customer can retry
      notify(err.message === 'Failed to fetch' ? 'Could not connect to the server. Check your backend URL.' : err.message);
    }
    finally { setLoading(false); }
  }

  const lifecycleStages = ['PLACED', 'ASSIGNED', 'OUT_FOR_DELIVERY', 'DELIVERED'];
  const stageLabels = {
    PLACED: 'Placed',
    ASSIGNED: 'Assigned',
    OUT_FOR_DELIVERY: 'Out for Delivery',
    DELIVERED: 'Delivered'
  };

  const currentStatus = activeOrder?.status;
  const isCancelled = currentStatus === 'CANCELLED';
  const currentStageIndex = lifecycleStages.indexOf(currentStatus);

  return <div className="app-shell">
    <div className="announcement"><span>✦</span> Your everyday essentials, delivered in minutes <span className="announcement-right">Fresh finds. Happy prices. <b>♡</b></span></div>
    <header className="header">
      <a className="brand" href="#top" aria-label="KlickIt home"><span className="brand-mark">k<span>!</span></span><span className="brand-word">klick<span>it</span><i>.</i></span></a>
      <button className="delivery-location" onClick={() => setAddressOpen(!addressOpen)}><span className="location-pin">⌖</span><span className="location-copy"><b>Delivery in 8–15 minutes</b><small>{address || 'Add delivery address'}</small></span><span className="chevron">⌄</span></button>
      {addressOpen && <div className="address-popover"><b>Where should we deliver?</b><p>Set your delivery address (min 10 characters)</p><input value={address} onChange={e => setAddress(e.target.value)} placeholder="Enter full street address or flat no."/><button onClick={() => { if (!isValidDeliveryAddress(address)) { notify('Address must be at least 10 characters.'); return; } localStorage.setItem('klickit_address', address.trim()); setAddressOpen(false); notify('Delivery location updated'); }}>Save location</button></div>}
      <label className="searchbar"><span className="search-icon">⌕</span><input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search for atta, dal, chips, and more..."/><kbd>⌘ K</kbd>{search && <button onClick={() => setSearch('')} aria-label="Clear search">×</button>}</label>
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
      <footer className="footer"><div className="footer-top"><div className="footer-brand"><a className="brand" href="#top"><span className="brand-mark">k<span>!</span></span><span className="brand-word">klick<span>it</span><i>.</i></span></a><p>Everyday things. Extraordinary convenience.</p></div><div className="footer-col"><b>Discover</b><a href="#shop">All products</a><a href="#shop" onClick={() => setActiveCategory('Fruits & Veg')}>Fresh produce</a><a href="#shop" onClick={() => setActiveCategory('Munchies')}>Snacks & munchies</a></div><div className="footer-col"><b>Need a hand?</b><a href="mailto:hello@klickit.example">Contact us</a><a href="#top">FAQs</a><a href="#top">Delivery information</a></div><div className="footer-note"><span>MADE FOR YOUR EVERYDAY ✳</span><p>More living, less running around.</p><div className="social-dots"><i>ig</i><i>in</i><i>♡</i></div></div></div><div className="footer-bottom"><span>© 2026 KlickIt. All little joys reserved.</span><span>Made with a little <b>♥</b> for everyday life.</span></div></footer>
    </main>
    {cartOpen && <><button className="overlay" onClick={() => setCartOpen(false)} aria-label="Close cart"></button><aside className="cart-drawer"><div className="drawer-header"><div><span className="section-kicker">YOUR LITTLE HAUL</span><h2>My cart <span>({cartCount})</span></h2></div><button className="close-button" onClick={() => setCartOpen(false)}>×</button></div>{cartCount ? <><div className="delivery-progress"><span>✦</span><div><b>{subtotal >= 199 ? 'You unlocked free delivery!' : `Add ${money(199-subtotal)} more for free delivery`}</b><div className="progress-track"><i style={{ width: `${Math.min(100, subtotal/199*100)}%` }}></i></div></div></div><div className="drawer-items">{cartItems.map(p => <div className="drawer-item" key={p.id}><img src={p.image} alt=""/><div className="drawer-item-info"><b>{p.name}</b><small>{p.description}</small><strong>{money(p.price)}</strong></div><div className="qty-control"><button onClick={() => changeQty(p.id,-1)}>−</button><b>{p.qty}</b><button onClick={() => changeQty(p.id,1)}>+</button></div></div>)}</div><div className="drawer-summary"><div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderBottom: '1px dashed #e2e4dc', marginBottom: '10px' }}><div style={{ fontSize: '11px', textAlign: 'left', maxWidth: '70%' }}><span style={{ color: '#888a7e', display: 'block', fontSize: '9px', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Delivery Address</span><b style={{ color: address ? '#1b1d19' : '#dc2626', wordBreak: 'break-word', display: 'block', marginTop: '2px' }}>{address || 'No address set — required'}</b></div><button type="button" onClick={() => setAddressOpen(true)} style={{ background: '#f5f6f2', border: '1px solid #d2d5c8', borderRadius: '6px', padding: '4px 10px', fontSize: '10px', cursor: 'pointer', fontWeight: 600, color: '#245b3b' }}>{address ? 'Change' : '+ Add'}</button></div><div><span>Item total</span><b>{money(subtotal+discount)}</b></div>{discount > 0 && <div className="saving-line"><span>Product savings</span><b>−{money(discount)}</b></div>}<div><span>Delivery fee</span><b>{delivery ? money(delivery) : <span className="free-label">FREE</span>}</b></div><div className="grand-total"><span>To pay</span><b>{money(subtotal+delivery)}</b></div><button className="checkout-button" onClick={checkout} disabled={loading}>{loading ? 'Working on it…' : <>Proceed to checkout <span>{money(subtotal+delivery)} ↗</span></>}</button><small className="secure-note">♡ Secure checkout · Cash on delivery</small></div></> : <div className="empty-cart"><span>🧺</span><h3>Your basket's taking a nap</h3><p>Let's fill it with a few everyday favourites.</p><button onClick={() => setCartOpen(false)}>Start shopping ↗</button></div>}</aside></>}
    {authOpen && <div className="modal-wrap"><button className="modal-backdrop" onClick={() => setAuthOpen(false)} aria-label="Close sign in"></button><div className="auth-modal"><button className="close-button modal-close" onClick={() => setAuthOpen(false)}>×</button><div className="auth-brand">k<span>!</span></div><span className="section-kicker">YOUR EVERYDAY, MADE EASIER</span><h2>{authMode === 'login' ? 'Welcome back!' : 'Come on in!'}</h2><p>{authMode === 'login' ? 'Sign in to pick up right where you left off.' : 'Create an account for a little more convenience.'}</p><form onSubmit={submitAuth}>{authMode === 'register' && <input required placeholder="Your name" value={authForm.name} onChange={e => setAuthForm({...authForm, name:e.target.value})}/>}<input required type="email" placeholder="Email address" value={authForm.email} onChange={e => setAuthForm({...authForm, email:e.target.value})}/>{authMode === 'register' && <input required placeholder="Phone number (required)" value={authForm.phone} onChange={e => setAuthForm({...authForm, phone:e.target.value})}/>}<input required minLength="6" type="password" placeholder="Password (at least 6 characters)" value={authForm.password} onChange={e => setAuthForm({...authForm, password:e.target.value})}/><button className="checkout-button" disabled={loading}>{loading ? 'One moment…' : authMode === 'login' ? 'Sign in ↗' : 'Create my account ↗'}</button></form><div className="auth-switch">{authMode === 'login' ? "New around here?" : 'Already have an account?'} <button onClick={() => setAuthMode(authMode === 'login' ? 'register' : 'login')}>{authMode === 'login' ? 'Create account' : 'Sign in'}</button></div><small className="auth-legal">By continuing, you agree to our Terms of Service and Privacy Policy.</small></div></div>}

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
              {isCancelled ? '✕ CANCELLED' : currentStatus}
            </span>
          </div>

          {/* Stepper or Cancelled Banner */}
          {isCancelled ? (
            <div className="cancelled-banner">
              <span style={{ fontSize: '20px' }}>✕</span>
              <div>
                <b>Order Cancelled</b>
                <p>This order has been cancelled and will not progress to delivery.</p>
              </div>
            </div>
          ) : (
            <div className="status-stepper">
              <span className="section-kicker" style={{ fontSize: '8px' }}>DELIVERY PROGRESSION</span>
              <div className="stepper-steps">
                {lifecycleStages.map((stage, idx) => {
                  const isCompleted = currentStageIndex > idx;
                  const isActive = currentStageIndex === idx;
                  return (
                    <div key={stage} className={`stepper-step ${isCompleted ? 'completed' : ''} ${isActive ? 'active' : ''}`}>
                      <div className="step-dot">{isCompleted ? '✓' : idx + 1}</div>
                      <span>{stageLabels[stage]}</span>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Key order details */}
          <div className="tracking-info-grid">
            <div className="info-card">
              <small>Total Amount</small>
              <b style={{ fontSize: '15px', color: '#245b3b' }}>{money(activeOrder.totalAmount)}</b>
            </div>
            <div className="info-card">
              <small>Expected Deadline</small>
              <b>
                {activeOrder.deadline
                  ? new Date(activeOrder.deadline).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                  : '15 mins SLA'}
              </b>
            </div>
            <div className="info-card" style={{ gridColumn: 'span 2' }}>
              <small>Delivery Address</small>
              <p>{activeOrder.customerAddress || 'Address on file'}</p>
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
      <div className="account-modal">
        <div className="tracking-header">
          <div>
            <span className="section-kicker">CUSTOMER ACCOUNT</span>
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
            <span>Customer Name</span>
            <b>{user?.name || 'Not set'}</b>
          </div>
          <div className="profile-row">
            <span>Email Address</span>
            <b>{user?.email || 'Not set'}</b>
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
          <button className="btn-signout" onClick={handleSignOut}>Sign Out</button>
        </div>}

        {accountTab === 'history' && <div>
          {historyLoading && <div style={{ textAlign: 'center', padding: '24px 0', color: '#77796f', fontSize: '12px' }}>Loading your orders...</div>}

          {historyError && <div style={{ background: '#fef2f2', border: '1px solid #fecaca', borderRadius: '10px', padding: '12px', color: '#991b1b', fontSize: '11px', margin: '12px 0' }}>
            <b>Error: </b>{historyError}
            <div style={{ marginTop: '8px' }}><button className="btn-refresh" onClick={fetchOrderHistory} style={{ height: '30px', fontSize: '10px' }}>Retry</button></div>
          </div>}

          {!historyLoading && !historyError && orderHistory.length === 0 && (
            <div className="empty-search" style={{ padding: '30px 10px', minHeight: 'auto' }}>
              <span>🧺</span>
              <h3>No orders yet</h3>
              <p>When you place an order, it will appear here so you can track its progress.</p>
              <button onClick={() => { setAccountOpen(false); document.getElementById('shop')?.scrollIntoView({ behavior: 'smooth' }); }}>Start shopping ↗</button>
            </div>
          )}

          {!historyLoading && !historyError && orderHistory.length > 0 && (
            <div className="order-history-list">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                <span className="section-kicker" style={{ fontSize: '8px' }}>PAST ORDERS ({orderHistory.length})</span>
                <button className="btn-refresh" onClick={fetchOrderHistory} style={{ height: '28px', fontSize: '9px', padding: '0 8px', flex: 'none' }}><span>↻</span> Refresh</button>
              </div>
              {orderHistory.map(order => {
                const orderStatus = order?.status;
                const isCancelledOrder = orderStatus === 'CANCELLED';
                const dateStr = order?.createdAt ? new Date(order.createdAt).toLocaleDateString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '';
                const itemsCount = Array.isArray(order?.items) ? order.items.reduce((sum, it) => sum + (it.quantity || 1), 0) : 0;
                const itemsSummary = Array.isArray(order?.items) && order.items.length > 0
                  ? order.items.map(it => `${it.productName}${it.quantity > 1 ? ` (×${it.quantity})` : ''}`).join(', ')
                  : '';

                return (
                  <button
                    key={order.id}
                    className="order-card"
                    onClick={() => {
                      setAccountOpen(false);
                      openTracking(order);
                      fetchOrder(order.id); // authoritatively refresh current state
                    }}
                  >
                    <div className="order-card-header">
                      <b>Order #{String(order.id).substring(0, 8)}</b>
                      <span className={`status-pill ${String(orderStatus || '').toLowerCase()}`}>
                        {isCancelledOrder ? '✕ CANCELLED' : orderStatus}
                      </span>
                    </div>
                    <div className="order-card-meta">
                      <span>{dateStr || 'Recent'}</span>
                      <b style={{ color: '#245b3b', fontSize: '13px' }}>{money(order.totalAmount)}</b>
                    </div>
                    {itemsSummary && (
                      <div className="order-card-items">
                        <span>{itemsCount} item{itemsCount !== 1 ? 's' : ''}: </span>
                        <span>{itemsSummary.length > 60 ? `${itemsSummary.substring(0, 58)}…` : itemsSummary}</span>
                      </div>
                    )}
                    <div className="order-card-cta">Track Order Details <span>↗</span></div>
                  </button>
                );
              })}
            </div>
          )}
        </div>}
      </div>
    </div>}

    {toast && <div className="toast"><span>✦</span>{toast}</div>}
    <nav className="mobile-nav"><button onClick={() => {setActiveCategory('All');window.scrollTo({top:0,behavior:'smooth'});}}><span>⌂</span>Home</button><button onClick={() => document.getElementById('shop')?.scrollIntoView({behavior:'smooth'})}><span>⌕</span>Explore</button><button onClick={() => setCartOpen(true)}><span>🛍</span>Cart {cartCount ? `(${cartCount})` : ''}</button><button onClick={() => openAccount('profile')}><span>♙</span>Account</button></nav>
  </div>;
}

createRoot(document.getElementById('root')).render(<React.StrictMode><App /></React.StrictMode>);
