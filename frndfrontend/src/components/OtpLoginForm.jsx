import React, { useState, useEffect, useRef } from 'react';
import { buildAuthProfile } from '../utils/authProfile.js';

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

export default function OtpLoginForm({
  onSuccess,
  onSwitchToPassword,
  onSwitchToRegister,
  apiBaseUrl = ''
}) {
  const [step, setStep] = useState('request'); // 'request' | 'verify'
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [cooldown, setCooldown] = useState(0);

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

  useEffect(() => {
    let timer;
    if (cooldown > 0) {
      timer = setInterval(() => {
        setCooldown(prev => Math.max(0, prev - 1));
      }, 1000);
    }
    return () => {
      if (timer) clearInterval(timer);
    };
  }, [cooldown]);

  const apiPrefix = (apiBaseUrl || '').replace(/\/$/, '');

  async function handleRequestCode(e) {
    if (e) e.preventDefault();
    if (isSubmittingRef.current) return;

    const cleanEmail = email.trim().toLowerCase();
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
      const res = await fetch(`${apiPrefix}/api/auth/otp/request`, {
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
        setStep('verify');
        setCooldown(60);
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

  async function handleVerifyCode(e) {
    if (e) e.preventDefault();
    if (isSubmittingRef.current) return;

    const cleanEmail = email.trim().toLowerCase();
    const cleanCode = code.trim();

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
      const res = await fetch(`${apiPrefix}/api/auth/otp/verify`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: cleanEmail, code: cleanCode }),
        signal: controller.signal
      });
      const data = await parseResponseBody(res);

      if (!res.ok) {
        throw new Error(data.message || data.error || (res.status >= 500 ? TIMEOUT_FALLBACK_MESSAGE : 'Invalid or expired code.'));
      }

      const { profile, token } = buildAuthProfile(data, cleanEmail);

      if (onSuccess) {
        onSuccess(token, profile);
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

  function handleCodeChange(e) {
    const numeric = e.target.value.replace(/\D/g, '').slice(0, 6);
    setCode(numeric);
    if (error) setError('');
  }

  function handleChangeEmail() {
    setStep('request');
    setCode('');
    setError('');
  }

  return (
    <div className="otp-form-wrap">
      {error && <div className="otp-error-banner">{error}</div>}

      {step === 'request' ? (
        <form onSubmit={handleRequestCode} style={{ display: 'flex', flexDirection: 'column', gap: '11px' }}>
          <input
            required
            type="email"
            placeholder="Enter your email address"
            value={email}
            onChange={e => {
              setEmail(e.target.value);
              if (error) setError('');
            }}
            disabled={loading}
            autoFocus
          />
          <button
            type="submit"
            className="checkout-button"
            disabled={loading}
          >
            {loading ? 'Sending code…' : 'Send sign-in code ↗'}
          </button>
        </form>
      ) : (
        <form onSubmit={handleVerifyCode} style={{ display: 'flex', flexDirection: 'column', gap: '11px' }}>
          <div className="otp-info-banner">
            Code sent to <b>{email}</b>.{' '}
            <button
              type="button"
              className="otp-action-link"
              onClick={handleChangeEmail}
              disabled={loading}
            >
              Change
            </button>
          </div>

          <input
            required
            type="text"
            inputMode="numeric"
            pattern="[0-9]*"
            maxLength={6}
            placeholder="······"
            className="otp-input-field"
            value={code}
            onChange={handleCodeChange}
            disabled={loading}
            autoFocus
          />

          <button
            type="submit"
            className="checkout-button"
            disabled={loading || code.length !== 6}
          >
            {loading ? 'Verifying code…' : 'Sign in ↗'}
          </button>

          <div className="otp-actions-row">
            <button
              type="button"
              className="otp-action-link"
              onClick={() => handleRequestCode()}
              disabled={loading || cooldown > 0}
            >
              {cooldown > 0 ? `Resend code in ${cooldown}s` : 'Resend code'}
            </button>
            <button
              type="button"
              className="otp-action-link"
              onClick={onSwitchToPassword}
              disabled={loading}
            >
              Use password
            </button>
          </div>
        </form>
      )}

      <div className="auth-switch">
        {step === 'request' && (
          <div style={{ marginBottom: '8px' }}>
            Prefer password?{' '}
            <button type="button" onClick={onSwitchToPassword} disabled={loading}>
              Sign in with password
            </button>
          </div>
        )}
        <div>
          New around here?{' '}
          <button type="button" onClick={onSwitchToRegister} disabled={loading}>
            Create account
          </button>
        </div>
      </div>
    </div>
  );
}
