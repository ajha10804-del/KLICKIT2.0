import React, { useState, useEffect, useRef } from 'react';
import { buildAuthProfile, getRoleFromToken } from '../utils/authProfile.js';

const AUTH_TIMEOUT_MS = 30000;
const TIMEOUT_FALLBACK_MESSAGE = 'The server is taking too long or is unavailable. Please try again in a minute.';

async function parseResponseBody(res) {
  try {
    const contentType = res.headers.get('content-type') || '';
    if (contentType.includes('application/json')) {
      return await res.json().catch(() => ({}));
    }
    const text = await res.text().catch(() => '');
    if (!text) return {};
    try {
      return JSON.parse(text);
    } catch {
      return {};
    }
  } catch {
    return {};
  }
}

export default function AuthFlow({
  apiBaseUrl = '',
  onAuthSuccess,
  notify = () => {},
  onClose = null,
  allowRegister = true,
  initialTab = 'login',
  barrierTitle = null
}) {
  // Navigation tabs: 'login' | 'register'
  const [activeTab, setActiveTab] = useState(allowRegister ? initialTab : 'login');

  // Sub-modes for Sign In tab: 'password' | 'otp-request' | 'otp-verify'
  const [loginMode, setLoginMode] = useState('password');

  // Sub-steps for Register tab: 'form' | 'verify'
  const [registerStep, setRegisterStep] = useState('form');

  // Form states
  const [loginEmail, setLoginEmail] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [showLoginPassword, setShowLoginPassword] = useState(false);

  // Login OTP state
  const [loginOtpEmail, setLoginOtpEmail] = useState('');
  const [loginOtpCode, setLoginOtpCode] = useState('');
  const [loginOtpCooldown, setLoginOtpCooldown] = useState(0);

  // Register form state
  const [regForm, setRegForm] = useState({
    name: '',
    email: '',
    phone: '',
    password: ''
  });
  const [showRegPassword, setShowRegPassword] = useState(false);
  const [regOtpCode, setRegOtpCode] = useState('');
  const [regOtpCooldown, setRegOtpCooldown] = useState(0);

  // Common UI states
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const API = (apiBaseUrl || '').replace(/\/$/, '');

  // Lifecycle & submission locks
  const isMountedRef = useRef(true);
  const isSubmittingRef = useRef(false);
  const activeAbortRef = useRef(null);

  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
      if (activeAbortRef.current) {
        activeAbortRef.current.abort();
        activeAbortRef.current = null;
      }
    };
  }, []);

  // Active cooldown timers countdown
  useEffect(() => {
    let timer;
    if (loginOtpCooldown > 0 || regOtpCooldown > 0) {
      timer = setInterval(() => {
        setLoginOtpCooldown(prev => Math.max(0, prev - 1));
        setRegOtpCooldown(prev => Math.max(0, prev - 1));
      }, 1000);
    }
    return () => {
      if (timer) clearInterval(timer);
    };
  }, [loginOtpCooldown, regOtpCooldown]);

  // Handle successful authentication and token extraction
  function completeAuth(data, fallbackEmail, fallbackName = '', fallbackPhone = '') {
    const { profile, token } = buildAuthProfile(data, fallbackEmail, fallbackName, fallbackPhone);
    if (onAuthSuccess) {
      onAuthSuccess(profile, token);
    }
  }

  // --- SIGN IN: Password ---
  async function handlePasswordLogin(e) {
    if (e) e.preventDefault();
    if (isSubmittingRef.current) return;

    const cleanEmail = loginEmail.trim().toLowerCase();
    if (!cleanEmail || !cleanEmail.includes('@')) {
      setError('Please enter a valid email address.');
      return;
    }
    if (!loginPassword) {
      setError('Please enter your password.');
      return;
    }

    isSubmittingRef.current = true;
    setLoading(true);
    setError('');

    const controller = new AbortController();
    activeAbortRef.current = controller;
    let timedOut = false;
    const timeoutId = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, AUTH_TIMEOUT_MS);

    try {
      const res = await fetch(`${API}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: cleanEmail, password: loginPassword }),
        signal: controller.signal
      });
      const data = await parseResponseBody(res);

      if (!res.ok) {
        if (res.status === 401) {
          throw new Error('Invalid email or password. Please verify your credentials.');
        }
        if (res.status === 403) {
          throw new Error('Access denied. You do not have permission to access this portal.');
        }
        throw new Error(data.message || data.error || (res.status >= 500 ? TIMEOUT_FALLBACK_MESSAGE : 'Sign in failed. Please check your credentials.'));
      }

      completeAuth(data, cleanEmail);
    } catch (err) {
      if (!isMountedRef.current) return;
      if (timedOut) {
        setError(TIMEOUT_FALLBACK_MESSAGE);
        return;
      }
      if (err.name === 'AbortError') {
        return;
      }
      if (err.message === 'Failed to fetch') {
        setError(TIMEOUT_FALLBACK_MESSAGE);
        return;
      }
      setError(err.message);
    } finally {
      clearTimeout(timeoutId);
      if (activeAbortRef.current === controller) {
        activeAbortRef.current = null;
      }
      isSubmittingRef.current = false;
      if (isMountedRef.current) {
        setLoading(false);
      }
    }
  }

  // --- SIGN IN: Request Login Code ---
  async function handleRequestLoginOtp(e) {
    if (e) e.preventDefault();
    if (isSubmittingRef.current) return;

    const cleanEmail = loginOtpEmail.trim().toLowerCase();
    if (!cleanEmail || !cleanEmail.includes('@')) {
      setError('Please enter a valid email address.');
      return;
    }

    isSubmittingRef.current = true;
    setLoading(true);
    setError('');

    const controller = new AbortController();
    activeAbortRef.current = controller;
    let timedOut = false;
    const timeoutId = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, AUTH_TIMEOUT_MS);

    try {
      const res = await fetch(`${API}/api/auth/otp/request`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: cleanEmail }),
        signal: controller.signal
      });
      const data = await parseResponseBody(res);

      if (!res.ok) {
        throw new Error(data.message || data.error || (res.status >= 500 ? TIMEOUT_FALLBACK_MESSAGE : 'Failed to send sign-in code. Please try again.'));
      }

      if (isMountedRef.current) {
        setLoginMode('otp-verify');
        setLoginOtpCooldown(60);
        setLoginOtpCode('');
      }
    } catch (err) {
      if (!isMountedRef.current) return;
      if (timedOut) {
        setError(TIMEOUT_FALLBACK_MESSAGE);
        return;
      }
      if (err.name === 'AbortError') {
        return;
      }
      if (err.message === 'Failed to fetch') {
        setError(TIMEOUT_FALLBACK_MESSAGE);
        return;
      }
      setError(err.message);
    } finally {
      clearTimeout(timeoutId);
      if (activeAbortRef.current === controller) {
        activeAbortRef.current = null;
      }
      isSubmittingRef.current = false;
      if (isMountedRef.current) {
        setLoading(false);
      }
    }
  }

  // --- SIGN IN: Verify Login Code ---
  async function handleVerifyLoginOtp(e) {
    if (e) e.preventDefault();
    if (isSubmittingRef.current) return;

    const cleanEmail = loginOtpEmail.trim().toLowerCase();
    const cleanCode = loginOtpCode.trim();

    if (cleanCode.length !== 6 || !/^\d{6}$/.test(cleanCode)) {
      setError('Please enter a valid 6-digit verification code.');
      return;
    }

    isSubmittingRef.current = true;
    setLoading(true);
    setError('');

    const controller = new AbortController();
    activeAbortRef.current = controller;
    let timedOut = false;
    const timeoutId = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, AUTH_TIMEOUT_MS);

    try {
      const res = await fetch(`${API}/api/auth/otp/verify`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: cleanEmail, code: cleanCode }),
        signal: controller.signal
      });
      const data = await parseResponseBody(res);

      if (!res.ok) {
        if (res.status === 401) {
          throw new Error('Invalid or expired code');
        }
        throw new Error(data.message || data.error || 'Invalid or expired code');
      }

      completeAuth(data, cleanEmail);
    } catch (err) {
      if (!isMountedRef.current) return;
      if (timedOut) {
        setError(TIMEOUT_FALLBACK_MESSAGE);
        return;
      }
      if (err.name === 'AbortError') {
        return;
      }
      if (err.message === 'Failed to fetch') {
        setError(TIMEOUT_FALLBACK_MESSAGE);
        return;
      }
      setError(err.message);
    } finally {
      clearTimeout(timeoutId);
      if (activeAbortRef.current === controller) {
        activeAbortRef.current = null;
      }
      isSubmittingRef.current = false;
      if (isMountedRef.current) {
        setLoading(false);
      }
    }
  }

  // --- REGISTER: Step 1 Request Verification Code ---
  async function handleRequestRegOtp(e) {
    if (e) e.preventDefault();
    if (isSubmittingRef.current) return;

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
    if (!cleanPhone || !/^[+0-9\-\s()]{7,20}$/.test(cleanPhone)) {
      setError('Please enter a valid phone number (7-20 digits).');
      return;
    }
    if (!cleanPass || cleanPass.length < 6) {
      setError('Password must be at least 6 characters.');
      return;
    }

    isSubmittingRef.current = true;
    setLoading(true);
    setError('');

    const controller = new AbortController();
    activeAbortRef.current = controller;
    let timedOut = false;
    const timeoutId = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, AUTH_TIMEOUT_MS);

    try {
      const res = await fetch(`${API}/api/auth/register/request-otp`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: cleanName,
          email: cleanEmail,
          phone: cleanPhone,
          password: cleanPass
        }),
        signal: controller.signal
      });
      const data = await parseResponseBody(res);

      if (!res.ok) {
        throw new Error(data.message || data.error || (res.status >= 500 ? TIMEOUT_FALLBACK_MESSAGE : 'Failed to send verification code. Please check your details.'));
      }

      if (isMountedRef.current) {
        setRegisterStep('verify');
        setRegOtpCooldown(60);
        setRegOtpCode('');
        notify('Verification code sent to ' + cleanEmail);
      }
    } catch (err) {
      if (!isMountedRef.current) return;
      if (timedOut) {
        setError(TIMEOUT_FALLBACK_MESSAGE);
        return;
      }
      if (err.name === 'AbortError') {
        return;
      }
      if (err.message === 'Failed to fetch') {
        setError(TIMEOUT_FALLBACK_MESSAGE);
        return;
      }
      setError(err.message);
    } finally {
      clearTimeout(timeoutId);
      if (activeAbortRef.current === controller) {
        activeAbortRef.current = null;
      }
      isSubmittingRef.current = false;
      if (isMountedRef.current) {
        setLoading(false);
      }
    }
  }

  // --- REGISTER: Step 2 Verify Code & Finalize Account Creation ---
  async function handleVerifyRegOtp(e) {
    if (e) e.preventDefault();
    if (isSubmittingRef.current) return;

    const cleanName = regForm.name.trim();
    const cleanEmail = regForm.email.trim().toLowerCase();
    const cleanPhone = regForm.phone.trim();
    const cleanPass = regForm.password;
    const cleanCode = regOtpCode.trim();

    if (cleanCode.length !== 6 || !/^\d{6}$/.test(cleanCode)) {
      setError('Please enter the 6-digit verification code.');
      return;
    }

    isSubmittingRef.current = true;
    setLoading(true);
    setError('');

    const controller = new AbortController();
    activeAbortRef.current = controller;
    let timedOut = false;
    const timeoutId = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, AUTH_TIMEOUT_MS);

    try {
      const res = await fetch(`${API}/api/auth/register/verify-otp`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: cleanName,
          email: cleanEmail,
          phone: cleanPhone,
          password: cleanPass,
          code: cleanCode
        }),
        signal: controller.signal
      });
      const data = await parseResponseBody(res);

      if (!res.ok) {
        if (res.status === 401) {
          throw new Error('Invalid or expired verification code');
        }
        throw new Error(data.message || data.error || 'Invalid or expired verification code');
      }

      if (isMountedRef.current) {
        notify('Account verified successfully!');
      }
      completeAuth(data, cleanEmail, cleanName, cleanPhone);
    } catch (err) {
      if (!isMountedRef.current) return;
      if (timedOut) {
        setError(TIMEOUT_FALLBACK_MESSAGE);
        return;
      }
      if (err.name === 'AbortError') {
        return;
      }
      if (err.message === 'Failed to fetch') {
        setError(TIMEOUT_FALLBACK_MESSAGE);
        return;
      }
      setError(err.message);
    } finally {
      clearTimeout(timeoutId);
      if (activeAbortRef.current === controller) {
        activeAbortRef.current = null;
      }
      isSubmittingRef.current = false;
      if (isMountedRef.current) {
        setLoading(false);
      }
    }
  }

  // Helper to switch to Register with prefilled email
  function switchToRegisterWithEmail(emailToPrefill) {
    const clean = (emailToPrefill || '').trim().toLowerCase();
    if (clean) {
      setRegForm(prev => ({ ...prev, email: clean }));
    }
    setActiveTab('register');
    setRegisterStep('form');
    setError('');
  }

  // Helper to switch to Sign In with prefilled email
  function switchToLoginWithEmail(emailToPrefill) {
    const clean = (emailToPrefill || '').trim().toLowerCase();
    if (clean) {
      setLoginEmail(clean);
      setLoginOtpEmail(clean);
    }
    setActiveTab('login');
    setLoginMode('password');
    setError('');
  }

  // Render the core modal card contents
  const modalContent = (
    <div className="auth-modal">
      {onClose && (
        <button
          type="button"
          className="close-button modal-close"
          onClick={onClose}
          aria-label="Close sign in"
        >
          ×
        </button>
      )}

      <div className="auth-brand">k<span>!</span></div>
      <span className="section-kicker">
        {barrierTitle ? barrierTitle.toUpperCase() : 'YOUR EVERYDAY, MADE EASIER'}
      </span>

      {/* Tabs: Sign in vs Register (Register hidden for staff barrier sign-ins) */}
      {allowRegister && (
        <div className="auth-mode-tabs">
          <button
            type="button"
            className={`auth-mode-tab ${activeTab === 'login' ? 'active' : ''}`}
            onClick={() => {
              setActiveTab('login');
              setError('');
            }}
          >
            Sign in
          </button>
          <button
            type="button"
            className={`auth-mode-tab ${activeTab === 'register' ? 'active' : ''}`}
            onClick={() => {
              setActiveTab('register');
              setError('');
            }}
          >
            Register
          </button>
        </div>
      )}

      {/* ================================================================= */}
      {/* SIGN IN TAB CONTENT                                               */}
      {/* ================================================================= */}
      {activeTab === 'login' && (
        <>
          {loginMode === 'password' && (
            <>
              <h2>Welcome back!</h2>
              <p>Sign in to pick up right where you left off.</p>

              {error && <div className="otp-error-banner" style={{ marginBottom: '12px' }}>{error}</div>}

              <form onSubmit={handlePasswordLogin}>
                <input
                  required
                  type="email"
                  placeholder="Email address"
                  value={loginEmail}
                  onChange={e => {
                    setLoginEmail(e.target.value);
                    if (error) setError('');
                  }}
                  disabled={loading}
                  autoFocus
                />

                <div style={{ position: 'relative' }}>
                  <input
                    required
                    type={showLoginPassword ? 'text' : 'password'}
                    placeholder="Password"
                    value={loginPassword}
                    onChange={e => {
                      setLoginPassword(e.target.value);
                      if (error) setError('');
                    }}
                    disabled={loading}
                    style={{ width: '100%', paddingRight: '40px' }}
                  />
                  <button
                    type="button"
                    onClick={() => setShowLoginPassword(p => !p)}
                    aria-label={showLoginPassword ? 'Hide password' : 'Show password'}
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
                    {showLoginPassword ? '🔒' : '👁️'}
                  </button>
                </div>

                <button
                  type="submit"
                  className="checkout-button"
                  disabled={loading}
                  style={{ justifyContent: 'center' }}
                >
                  {loading ? 'One moment…' : 'Sign in ↗'}
                </button>
              </form>

              <div className="auth-switch" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {allowRegister && (
                  <div>
                    New here?{' '}
                    <button
                      type="button"
                      onClick={() => switchToRegisterWithEmail(loginEmail)}
                      disabled={loading}
                    >
                      Create an account
                    </button>
                  </div>
                )}
                <div>
                  <button
                    type="button"
                    onClick={() => {
                      setLoginOtpEmail(loginEmail);
                      setLoginMode('otp-request');
                      setError('');
                    }}
                    disabled={loading}
                    style={{ color: '#0c831f', fontWeight: 600 }}
                  >
                    Email me a sign-in code instead
                  </button>
                </div>
              </div>
            </>
          )}

          {loginMode === 'otp-request' && (
            <>
              <h2>Sign in with code</h2>
              <p>We'll send a 6-digit verification code to your email.</p>

              {error && <div className="otp-error-banner" style={{ marginBottom: '12px' }}>{error}</div>}

              <form onSubmit={handleRequestLoginOtp}>
                <input
                  required
                  type="email"
                  placeholder="Enter your email address"
                  value={loginOtpEmail}
                  onChange={e => {
                    setLoginOtpEmail(e.target.value);
                    if (error) setError('');
                  }}
                  disabled={loading}
                  autoFocus
                />
                <button
                  type="submit"
                  className="checkout-button"
                  disabled={loading}
                  style={{ justifyContent: 'center' }}
                >
                  {loading ? 'Sending code…' : 'Send sign-in code ↗'}
                </button>
              </form>

              <div className="auth-switch" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <div>
                  Prefer password?{' '}
                  <button
                    type="button"
                    onClick={() => {
                      setLoginMode('password');
                      setError('');
                    }}
                    disabled={loading}
                  >
                    Sign in with password
                  </button>
                </div>
                {allowRegister && (
                  <div>
                    New here?{' '}
                    <button
                      type="button"
                      onClick={() => switchToRegisterWithEmail(loginOtpEmail)}
                      disabled={loading}
                    >
                      Create an account
                    </button>
                  </div>
                )}
              </div>
            </>
          )}

          {loginMode === 'otp-verify' && (
            <>
              <h2>Sign in with code</h2>

              {error && <div className="otp-error-banner" style={{ marginBottom: '12px' }}>{error}</div>}

              <div className="otp-info-banner" style={{ marginBottom: '12px' }}>
                If this email is registered, a code is on its way to <b>{loginOtpEmail}</b>.{' '}
                <button
                  type="button"
                  className="otp-action-link"
                  onClick={() => {
                    setLoginMode('otp-request');
                    setLoginOtpCode('');
                    setError('');
                  }}
                  disabled={loading}
                >
                  Change
                </button>
              </div>

              <form onSubmit={handleVerifyLoginOtp}>
                <input
                  required
                  type="text"
                  inputMode="numeric"
                  pattern="[0-9]*"
                  maxLength={6}
                  placeholder="······"
                  className="otp-input-field"
                  value={loginOtpCode}
                  onChange={e => {
                    setLoginOtpCode(e.target.value.replace(/\D/g, '').slice(0, 6));
                    if (error) setError('');
                  }}
                  disabled={loading}
                  autoFocus
                  autoComplete="one-time-code"
                />

                <button
                  type="submit"
                  className="checkout-button"
                  disabled={loading || loginOtpCode.length !== 6}
                  style={{ justifyContent: 'center' }}
                >
                  {loading ? 'Verifying code…' : 'Sign in with code ↗'}
                </button>

                <div className="otp-actions-row">
                  <button
                    type="button"
                    className="otp-action-link"
                    onClick={() => handleRequestLoginOtp()}
                    disabled={loading || loginOtpCooldown > 0}
                  >
                    {loginOtpCooldown > 0 ? `Resend code in ${loginOtpCooldown}s` : 'Resend code'}
                  </button>
                  <button
                    type="button"
                    className="otp-action-link"
                    onClick={() => {
                      setLoginMode('password');
                      setError('');
                    }}
                    disabled={loading}
                  >
                    Use password instead
                  </button>
                </div>
              </form>

              {allowRegister && (
                <div className="auth-switch">
                  New here?{' '}
                  <button
                    type="button"
                    onClick={() => switchToRegisterWithEmail(loginOtpEmail)}
                    disabled={loading}
                  >
                    Create an account
                  </button>
                </div>
              )}
            </>
          )}
        </>
      )}

      {/* ================================================================= */}
      {/* REGISTER TAB CONTENT                                              */}
      {/* ================================================================= */}
      {allowRegister && activeTab === 'register' && (
        <>
          {registerStep === 'form' && (
            <>
              <h2>Come on in!</h2>
              <p>Create an account for a little more convenience.</p>

              {error && <div className="otp-error-banner" style={{ marginBottom: '12px' }}>{error}</div>}

              <form onSubmit={handleRequestRegOtp}>
                <input
                  required
                  type="text"
                  placeholder="Your name"
                  value={regForm.name}
                  onChange={e => {
                    setRegForm({ ...regForm, name: e.target.value });
                    if (error) setError('');
                  }}
                  disabled={loading}
                  autoFocus
                />
                <input
                  required
                  type="email"
                  placeholder="Email address"
                  value={regForm.email}
                  onChange={e => {
                    setRegForm({ ...regForm, email: e.target.value });
                    if (error) setError('');
                  }}
                  disabled={loading}
                />
                <input
                  required
                  type="tel"
                  placeholder="Phone number (required)"
                  value={regForm.phone}
                  onChange={e => {
                    setRegForm({ ...regForm, phone: e.target.value });
                    if (error) setError('');
                  }}
                  disabled={loading}
                />

                <div style={{ position: 'relative' }}>
                  <input
                    required
                    minLength="6"
                    type={showRegPassword ? 'text' : 'password'}
                    placeholder="Password (at least 6 characters)"
                    value={regForm.password}
                    onChange={e => {
                      setRegForm({ ...regForm, password: e.target.value });
                      if (error) setError('');
                    }}
                    disabled={loading}
                    style={{ width: '100%', paddingRight: '40px' }}
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

                <button
                  type="submit"
                  className="checkout-button"
                  disabled={loading}
                  style={{ justifyContent: 'center' }}
                >
                  {loading ? 'Sending code…' : 'Send verification code ↗'}
                </button>
              </form>

              <div className="auth-switch">
                Already have an account?{' '}
                <button
                  type="button"
                  onClick={() => switchToLoginWithEmail(regForm.email)}
                  disabled={loading}
                >
                  Sign in
                </button>
              </div>
            </>
          )}

          {registerStep === 'verify' && (
            <>
              <h2>Verify your email</h2>
              <p>Enter the 6-digit verification code to complete your registration.</p>

              {error && <div className="otp-error-banner" style={{ marginBottom: '12px' }}>{error}</div>}

              <div className="otp-info-banner" style={{ marginBottom: '12px' }}>
                Verification code sent to <b>{regForm.email}</b>.{' '}
                <button
                  type="button"
                  className="otp-action-link"
                  onClick={() => {
                    setRegisterStep('form');
                    setRegOtpCode('');
                    setError('');
                  }}
                  disabled={loading}
                >
                  Change email
                </button>
              </div>

              <form onSubmit={handleVerifyRegOtp}>
                <input
                  required
                  type="text"
                  inputMode="numeric"
                  pattern="[0-9]*"
                  maxLength={6}
                  placeholder="······"
                  className="otp-input-field"
                  value={regOtpCode}
                  onChange={e => {
                    setRegOtpCode(e.target.value.replace(/\D/g, '').slice(0, 6));
                    if (error) setError('');
                  }}
                  disabled={loading}
                  autoFocus
                  autoComplete="one-time-code"
                />

                <button
                  type="submit"
                  className="checkout-button"
                  disabled={loading || regOtpCode.length !== 6}
                  style={{ justifyContent: 'center' }}
                >
                  {loading ? 'Creating account…' : 'Verify and create account ↗'}
                </button>

                <div className="otp-actions-row">
                  <button
                    type="button"
                    className="otp-action-link"
                    onClick={() => handleRequestRegOtp()}
                    disabled={loading || regOtpCooldown > 0}
                  >
                    {regOtpCooldown > 0 ? `Resend code in ${regOtpCooldown}s` : 'Resend code'}
                  </button>
                  <button
                    type="button"
                    className="otp-action-link"
                    onClick={() => {
                      setRegisterStep('form');
                      setRegOtpCode('');
                      setError('');
                    }}
                    disabled={loading}
                  >
                    Change details
                  </button>
                </div>
              </form>

              <div className="auth-switch">
                Already have an account?{' '}
                <button
                  type="button"
                  onClick={() => switchToLoginWithEmail(regForm.email)}
                  disabled={loading}
                >
                  Sign in
                </button>
              </div>
            </>
          )}
        </>
      )}

      <small className="auth-legal">
        By continuing, you agree to our Terms of Service and Privacy Policy.
      </small>
    </div>
  );

  // If used as a modal overlay (e.g. from checkout or barriers with onClose)
  if (onClose) {
    return (
      <div className="modal-wrap">
        <button
          type="button"
          className="modal-backdrop"
          onClick={onClose}
          aria-label="Close sign in"
        />
        {modalContent}
      </div>
    );
  }

  // Bare card (e.g. rendered inside AuthScreen)
  return modalContent;
}
