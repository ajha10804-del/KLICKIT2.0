import React, { useState, useEffect } from 'react';
import OtpLoginForm from './OtpLoginForm.jsx';

export default function AuthScreen({
  apiBaseUrl = '',
  onAuthSuccess,
  notify = () => {}
}) {
  const [mode, setMode] = useState('login'); // 'login' | 'register' | 'register-otp' | 'otp-login'
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // Password visibility
  const [showPassword, setShowPassword] = useState(false);
  const [showRegPassword, setShowRegPassword] = useState(false);

  // Form states
  const [loginForm, setLoginForm] = useState({ email: '', password: '' });
  const [regForm, setRegForm] = useState({ name: '', email: '', phone: '', password: '' });
  const [otpCode, setOtpCode] = useState('');
  const [resendCooldown, setResendCooldown] = useState(0);

  const API = (apiBaseUrl || '').replace(/\/$/, '');

  useEffect(() => {
    let timer;
    if (resendCooldown > 0) {
      timer = setInterval(() => {
        setResendCooldown(prev => Math.max(0, prev - 1));
      }, 1000);
    }
    return () => {
      if (timer) clearInterval(timer);
    };
  }, [resendCooldown]);

  // Decode role from token payload safely
  function getRoleFromToken(token) {
    if (!token) return null;
    try {
      const parts = token.split('.');
      if (parts.length !== 3) return null;
      const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')));
      return payload.role || null;
    } catch {
      return null;
    }
  }

  // Handle password sign-in
  async function handlePasswordLogin(e) {
    if (e) e.preventDefault();
    const cleanEmail = loginForm.email.trim().toLowerCase();
    if (!cleanEmail || !cleanEmail.includes('@')) {
      setError('Please enter a valid email address.');
      return;
    }
    if (!loginForm.password) {
      setError('Please enter your password.');
      return;
    }

    setLoading(true);
    setError('');

    try {
      const res = await fetch(`${API}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: cleanEmail, password: loginForm.password })
      });
      const data = await res.json().catch(() => ({}));

      if (!res.ok) {
        if (res.status === 401) {
          throw new Error('Invalid email or password. Please verify your credentials.');
        }
        throw new Error(data.message || data.error || 'Sign in failed. Please check your credentials.');
      }

      const token = data.data?.token || data.token;
      if (!token) throw new Error('Authentication token missing from server response.');

      const tokenRole = getRoleFromToken(token);
      const resUser = data.data?.user || data.data || {};
      const profile = {
        userId: resUser.userId || null,
        name: resUser.name || cleanEmail.split('@')[0],
        email: resUser.email || cleanEmail,
        phone: resUser.phone || '',
        role: tokenRole || resUser.role || 'CUSTOMER'
      };

      onAuthSuccess(profile, token);
    } catch (err) {
      setError(err.message === 'Failed to fetch'
        ? 'Could not connect to server. Check your network or backend.'
        : err.message);
    } finally {
      setLoading(false);
    }
  }

  // Handle step 1: Request OTP for new registration
  async function handleRequestRegOtp(e) {
    if (e) e.preventDefault();
    const cleanName = regForm.name.trim();
    const cleanEmail = regForm.email.trim().toLowerCase();
    const cleanPhone = regForm.phone.trim();
    const cleanPass = regForm.password;

    if (!cleanName) {
      setError('Please enter your full name.');
      return;
    }
    if (!cleanEmail || !cleanEmail.includes('@')) {
      setError('Please enter a valid email address.');
      return;
    }
    if (!cleanPhone || cleanPhone.length < 7 || cleanPhone.length > 20) {
      setError('Please enter a valid phone number (7-20 digits).');
      return;
    }
    if (!cleanPass || cleanPass.length < 6) {
      setError('Password must be at least 6 characters.');
      return;
    }

    setLoading(true);
    setError('');

    try {
      const res = await fetch(`${API}/api/auth/register/request-otp`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: cleanName,
          email: cleanEmail,
          phone: cleanPhone,
          password: cleanPass
        })
      });
      const data = await res.json().catch(() => ({}));

      if (!res.ok) {
        throw new Error(data.message || data.error || 'Failed to send verification code. Please try again.');
      }

      setMode('register-otp');
      setResendCooldown(60);
      setOtpCode('');
      notify('Verification code sent to ' + cleanEmail);
    } catch (err) {
      setError(err.message === 'Failed to fetch'
        ? 'Could not connect to server. Check your network or backend.'
        : err.message);
    } finally {
      setLoading(false);
    }
  }

  // Handle step 2: Verify OTP and finalize registration
  async function handleVerifyRegOtp(e) {
    if (e) e.preventDefault();
    const cleanCode = otpCode.trim();
    if (!cleanCode || cleanCode.length !== 6 || !/^\d{6}$/.test(cleanCode)) {
      setError('Please enter the 6-digit verification code.');
      return;
    }

    setLoading(true);
    setError('');

    try {
      const res = await fetch(`${API}/api/auth/register/verify-otp`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: regForm.name.trim(),
          email: regForm.email.trim().toLowerCase(),
          phone: regForm.phone.trim(),
          password: regForm.password,
          code: cleanCode
        })
      });
      const data = await res.json().catch(() => ({}));

      if (!res.ok) {
        throw new Error(data.message || data.error || 'Invalid or expired verification code.');
      }

      const token = data.data?.token || data.token;
      if (!token) throw new Error('Authentication token missing from server response.');

      const tokenRole = getRoleFromToken(token);
      const resUser = data.data?.user || data.data || {};
      const profile = {
        userId: resUser.userId || null,
        name: resUser.name || regForm.name.trim(),
        email: resUser.email || regForm.email.trim().toLowerCase(),
        phone: resUser.phone || regForm.phone.trim(),
        role: tokenRole || resUser.role || 'CUSTOMER'
      };

      notify('Account verified successfully!');
      onAuthSuccess(profile, token);
    } catch (err) {
      setError(err.message === 'Failed to fetch'
        ? 'Could not connect to server. Check your network or backend.'
        : err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'radial-gradient(circle at top, #f2f7f4 0%, #edf2ee 100%)',
      padding: '24px 16px',
      fontFamily: "'Inter', system-ui, sans-serif"
    }}>
      {/* Brand Header */}
      <div style={{ textAlign: 'center', marginBottom: '28px' }}>
        <div style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: '8px',
          background: '#ffffff',
          padding: '8px 16px',
          borderRadius: '999px',
          boxShadow: '0 2px 8px rgba(0,0,0,0.04)',
          marginBottom: '12px'
        }}>
          <span style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: '26px',
            height: '26px',
            background: '#245b3b',
            color: '#fff',
            borderRadius: '50%',
            fontWeight: 800,
            fontSize: '14px'
          }}>
            k!
          </span>
          <span style={{ fontSize: '18px', fontWeight: 800, color: '#1b1d19', letterSpacing: '-0.5px' }}>
            klick<span style={{ color: '#245b3b' }}>it</span><i>.</i>
          </span>
        </div>
        <p style={{ margin: 0, fontSize: '13px', color: '#64748b' }}>
          Your everyday essentials, delivered in minutes.
        </p>
      </div>

      {/* Main Authentication Card */}
      <div style={{
        maxWidth: '420px',
        width: '100%',
        background: '#ffffff',
        borderRadius: '20px',
        padding: '32px 28px',
        boxShadow: '0 12px 36px -4px rgba(0, 0, 0, 0.08), 0 4px 12px -2px rgba(0, 0, 0, 0.04)',
        border: '1px solid #e2e8f0',
        boxSizing: 'border-box'
      }}>

        {/* Global Error Alert */}
        {error && (
          <div style={{
            background: '#fef2f2',
            border: '1px solid #fecaca',
            color: '#991b1b',
            padding: '10px 14px',
            borderRadius: '10px',
            fontSize: '12px',
            lineHeight: 1.4,
            marginBottom: '18px',
            display: 'flex',
            alignItems: 'flex-start',
            gap: '8px'
          }}>
            <span style={{ fontSize: '14px' }}>⚠</span>
            <span>{error}</span>
          </div>
        )}

        {/* MODE 1: Email + Password Sign In */}
        {mode === 'login' && (
          <>
            <div style={{ marginBottom: '20px' }}>
              <span style={{ fontSize: '10px', textTransform: 'uppercase', letterSpacing: '0.1em', color: '#245b3b', fontWeight: 700 }}>
                SECURE SIGN IN
              </span>
              <h2 style={{ fontSize: '22px', fontWeight: 800, margin: '4px 0 6px', color: '#0f172a', letterSpacing: '-0.4px' }}>
                Welcome back!
              </h2>
              <p style={{ margin: 0, fontSize: '12px', color: '#64748b', lineHeight: 1.5 }}>
                Sign in with your email and password to access your cart and orders.
              </p>
            </div>

            <form onSubmit={handlePasswordLogin} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              <div>
                <label style={{ display: 'block', fontSize: '11px', fontWeight: 700, color: '#334155', marginBottom: '6px' }}>
                  Email Address
                </label>
                <input
                  required
                  type="email"
                  placeholder="name@example.com"
                  value={loginForm.email}
                  onChange={e => { setLoginForm({ ...loginForm, email: e.target.value }); setError(''); }}
                  style={{
                    width: '100%',
                    height: '42px',
                    border: '1px solid #cbd5e1',
                    borderRadius: '8px',
                    padding: '0 12px',
                    fontSize: '13px',
                    boxSizing: 'border-box',
                    outline: 'none'
                  }}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '11px', fontWeight: 700, color: '#334155', marginBottom: '6px' }}>
                  Password
                </label>
                <div style={{ position: 'relative' }}>
                  <input
                    required
                    type={showPassword ? 'text' : 'password'}
                    placeholder="Enter password"
                    value={loginForm.password}
                    onChange={e => { setLoginForm({ ...loginForm, password: e.target.value }); setError(''); }}
                    style={{
                      width: '100%',
                      height: '42px',
                      border: '1px solid #cbd5e1',
                      borderRadius: '8px',
                      padding: '0 40px 0 12px',
                      fontSize: '13px',
                      boxSizing: 'border-box',
                      outline: 'none'
                    }}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(p => !p)}
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                    style={{
                      position: 'absolute',
                      right: '8px',
                      top: '50%',
                      transform: 'translateY(-50%)',
                      background: 'transparent',
                      border: 'none',
                      cursor: 'pointer',
                      fontSize: '15px',
                      color: '#64748b',
                      padding: '4px'
                    }}
                  >
                    {showPassword ? '🔒' : '👁️'}
                  </button>
                </div>
              </div>

              <button
                type="submit"
                disabled={loading}
                style={{
                  height: '44px',
                  background: '#245b3b',
                  color: '#ffffff',
                  border: 'none',
                  borderRadius: '10px',
                  fontWeight: 700,
                  fontSize: '13px',
                  cursor: loading ? 'wait' : 'pointer',
                  boxShadow: '0 2px 6px rgba(36, 91, 59, 0.25)',
                  marginTop: '4px'
                }}
              >
                {loading ? 'Signing in…' : 'Sign In ↗'}
              </button>
            </form>

            <div style={{ marginTop: '20px', paddingTop: '16px', borderTop: '1px solid #f1f5f9', display: 'flex', flexDirection: 'column', gap: '10px', textAlign: 'center' }}>
              <button
                type="button"
                onClick={() => { setMode('otp-login'); setError(''); }}
                style={{
                  background: 'transparent',
                  border: 'none',
                  color: '#2563eb',
                  fontSize: '12px',
                  fontWeight: 600,
                  cursor: 'pointer',
                  padding: 0
                }}
              >
                🔑 Sign in with Email OTP instead
              </button>
              <div style={{ fontSize: '12px', color: '#64748b' }}>
                Don't have an account?{' '}
                <button
                  type="button"
                  onClick={() => { setMode('register'); setError(''); }}
                  style={{
                    background: 'transparent',
                    border: 'none',
                    color: '#245b3b',
                    fontWeight: 700,
                    cursor: 'pointer',
                    padding: 0
                  }}
                >
                  Register now
                </button>
              </div>
            </div>
          </>
        )}

        {/* MODE 2: Registration - Details Input */}
        {mode === 'register' && (
          <>
            <div style={{ marginBottom: '20px' }}>
              <span style={{ fontSize: '10px', textTransform: 'uppercase', letterSpacing: '0.1em', color: '#245b3b', fontWeight: 700 }}>
                CUSTOMER REGISTRATION
              </span>
              <h2 style={{ fontSize: '22px', fontWeight: 800, margin: '4px 0 6px', color: '#0f172a', letterSpacing: '-0.4px' }}>
                Create your account
              </h2>
              <p style={{ margin: 0, fontSize: '12px', color: '#64748b', lineHeight: 1.5 }}>
                Enter your details to receive an email verification code.
              </p>
            </div>

            <form onSubmit={handleRequestRegOtp} style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              <div>
                <label style={{ display: 'block', fontSize: '11px', fontWeight: 700, color: '#334155', marginBottom: '4px' }}>
                  Full Name
                </label>
                <input
                  required
                  type="text"
                  placeholder="e.g. John Doe"
                  value={regForm.name}
                  onChange={e => { setRegForm({ ...regForm, name: e.target.value }); setError(''); }}
                  style={{
                    width: '100%',
                    height: '40px',
                    border: '1px solid #cbd5e1',
                    borderRadius: '8px',
                    padding: '0 12px',
                    fontSize: '13px',
                    boxSizing: 'border-box'
                  }}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '11px', fontWeight: 700, color: '#334155', marginBottom: '4px' }}>
                  Email Address
                </label>
                <input
                  required
                  type="email"
                  placeholder="name@example.com"
                  value={regForm.email}
                  onChange={e => { setRegForm({ ...regForm, email: e.target.value }); setError(''); }}
                  style={{
                    width: '100%',
                    height: '40px',
                    border: '1px solid #cbd5e1',
                    borderRadius: '8px',
                    padding: '0 12px',
                    fontSize: '13px',
                    boxSizing: 'border-box'
                  }}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '11px', fontWeight: 700, color: '#334155', marginBottom: '4px' }}>
                  Phone Number
                </label>
                <input
                  required
                  type="tel"
                  placeholder="10-digit mobile number"
                  value={regForm.phone}
                  onChange={e => { setRegForm({ ...regForm, phone: e.target.value }); setError(''); }}
                  style={{
                    width: '100%',
                    height: '40px',
                    border: '1px solid #cbd5e1',
                    borderRadius: '8px',
                    padding: '0 12px',
                    fontSize: '13px',
                    boxSizing: 'border-box'
                  }}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '11px', fontWeight: 700, color: '#334155', marginBottom: '4px' }}>
                  Password <span style={{ color: '#64748b', fontWeight: 400 }}>(min. 6 characters)</span>
                </label>
                <div style={{ position: 'relative' }}>
                  <input
                    required
                    minLength="6"
                    type={showRegPassword ? 'text' : 'password'}
                    placeholder="Create a password"
                    value={regForm.password}
                    onChange={e => { setRegForm({ ...regForm, password: e.target.value }); setError(''); }}
                    style={{
                      width: '100%',
                      height: '40px',
                      border: '1px solid #cbd5e1',
                      borderRadius: '8px',
                      padding: '0 40px 0 12px',
                      fontSize: '13px',
                      boxSizing: 'border-box'
                    }}
                  />
                  <button
                    type="button"
                    onClick={() => setShowRegPassword(p => !p)}
                    aria-label={showRegPassword ? 'Hide password' : 'Show password'}
                    style={{
                      position: 'absolute',
                      right: '8px',
                      top: '50%',
                      transform: 'translateY(-50%)',
                      background: 'transparent',
                      border: 'none',
                      cursor: 'pointer',
                      fontSize: '15px',
                      color: '#64748b',
                      padding: '4px'
                    }}
                  >
                    {showRegPassword ? '🔒' : '👁️'}
                  </button>
                </div>
              </div>

              <button
                type="submit"
                disabled={loading}
                style={{
                  height: '44px',
                  background: '#245b3b',
                  color: '#ffffff',
                  border: 'none',
                  borderRadius: '10px',
                  fontWeight: 700,
                  fontSize: '13px',
                  cursor: loading ? 'wait' : 'pointer',
                  boxShadow: '0 2px 6px rgba(36, 91, 59, 0.25)',
                  marginTop: '6px'
                }}
              >
                {loading ? 'Sending code…' : 'Send Verification Code ↗'}
              </button>
            </form>

            <div style={{ marginTop: '18px', paddingTop: '14px', borderTop: '1px solid #f1f5f9', textAlign: 'center' }}>
              <span style={{ fontSize: '12px', color: '#64748b' }}>Already have an account? </span>
              <button
                type="button"
                onClick={() => { setMode('login'); setError(''); }}
                style={{
                  background: 'transparent',
                  border: 'none',
                  color: '#245b3b',
                  fontWeight: 700,
                  cursor: 'pointer',
                  padding: 0,
                  fontSize: '12px'
                }}
              >
                Sign In
              </button>
            </div>
          </>
        )}

        {/* MODE 3: Registration - OTP Verification */}
        {mode === 'register-otp' && (
          <>
            <div style={{ marginBottom: '20px' }}>
              <span style={{ fontSize: '10px', textTransform: 'uppercase', letterSpacing: '0.1em', color: '#245b3b', fontWeight: 700 }}>
                EMAIL VERIFICATION
              </span>
              <h2 style={{ fontSize: '22px', fontWeight: 800, margin: '4px 0 6px', color: '#0f172a', letterSpacing: '-0.4px' }}>
                Enter verification code
              </h2>
              <p style={{ margin: 0, fontSize: '12px', color: '#64748b', lineHeight: 1.5 }}>
                We sent a 6-digit code to <b>{regForm.email}</b>.
              </p>
            </div>

            <form onSubmit={handleVerifyRegOtp} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              <div>
                <label style={{ display: 'block', fontSize: '11px', fontWeight: 700, color: '#334155', marginBottom: '6px' }}>
                  6-Digit Code
                </label>
                <input
                  autoFocus
                  required
                  type="text"
                  maxLength="6"
                  pattern="\d{6}"
                  placeholder="000000"
                  value={otpCode}
                  onChange={e => { setOtpCode(e.target.value.replace(/\D/g, '')); setError(''); }}
                  style={{
                    width: '100%',
                    height: '48px',
                    textAlign: 'center',
                    fontSize: '22px',
                    fontWeight: 800,
                    letterSpacing: '8px',
                    border: '1px solid #cbd5e1',
                    borderRadius: '8px',
                    boxSizing: 'border-box'
                  }}
                />
              </div>

              <button
                type="submit"
                disabled={loading}
                style={{
                  height: '44px',
                  background: '#245b3b',
                  color: '#ffffff',
                  border: 'none',
                  borderRadius: '10px',
                  fontWeight: 700,
                  fontSize: '13px',
                  cursor: loading ? 'wait' : 'pointer',
                  boxShadow: '0 2px 6px rgba(36, 91, 59, 0.25)',
                  marginTop: '4px'
                }}
              >
                {loading ? 'Verifying…' : 'Complete Registration & Sign In ↗'}
              </button>
            </form>

            <div style={{ marginTop: '18px', paddingTop: '14px', borderTop: '1px solid #f1f5f9', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <button
                type="button"
                onClick={() => { setMode('register'); setError(''); }}
                style={{
                  background: 'transparent',
                  border: 'none',
                  color: '#64748b',
                  fontSize: '12px',
                  cursor: 'pointer',
                  padding: 0
                }}
              >
                ← Edit details
              </button>
              <button
                type="button"
                disabled={resendCooldown > 0 || loading}
                onClick={handleRequestRegOtp}
                style={{
                  background: 'transparent',
                  border: 'none',
                  color: resendCooldown > 0 ? '#94a3b8' : '#245b3b',
                  fontWeight: 700,
                  fontSize: '12px',
                  cursor: resendCooldown > 0 ? 'default' : 'pointer',
                  padding: 0
                }}
              >
                {resendCooldown > 0 ? `Resend code (${resendCooldown}s)` : 'Resend code'}
              </button>
            </div>
          </>
        )}

        {/* MODE 4: Passwordless Email OTP Login */}
        {mode === 'otp-login' && (
          <div>
            <OtpLoginForm
              apiBaseUrl={API}
              onSuccess={(profile, token) => {
                notify('Welcome back!');
                onAuthSuccess(profile, token);
              }}
              onSwitchToPassword={() => { setMode('login'); setError(''); }}
              onSwitchToRegister={() => { setMode('register'); setError(''); }}
            />
          </div>
        )}

      </div>
    </div>
  );
}
