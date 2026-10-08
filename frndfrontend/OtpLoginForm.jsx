import React, { useEffect, useRef, useState } from 'react';

/**
 * Email OTP sign-in used by the storefront, /admin and /delivery modals.
 * Step 1: enter email -> POST /api/auth/otp/request
 * Step 2: enter the 6-digit code -> POST /api/auth/otp/verify
 * On success the backend returns the account's role; the parent decides where to go.
 */
export default function OtpLoginForm({ api, emailPlaceholder = 'Email address', submitLabel = 'Sign in', onAuthenticated }) {
  const [step, setStep] = useState('email'); // 'email' | 'code'
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [cooldown, setCooldown] = useState(0);
  const codeRef = useRef(null);

  useEffect(() => {
    if (cooldown <= 0) return undefined;
    const t = setTimeout(() => setCooldown(c => c - 1), 1000);
    return () => clearTimeout(t);
  }, [cooldown]);

  useEffect(() => {
    if (step === 'code') codeRef.current?.focus();
  }, [step]);

  async function post(path, payload) {
    const res = await fetch(`${api}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    const body = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(body.message || 'Something went wrong. Please try again.');
    return body;
  }

  function friendly(err) {
    return err.message === 'Failed to fetch'
      ? 'Could not connect to the server. Check that your backend is running.'
      : err.message;
  }

  async function sendCode(e) {
    if (e) e.preventDefault();
    if (loading) return;
    setLoading(true);
    setError('');
    try {
      await post('/api/auth/otp/request', { email: email.trim() });
      setStep('code');
      setCode('');
      setCooldown(60);
    } catch (err) {
      setError(friendly(err));
    } finally {
      setLoading(false);
    }
  }

  async function verifyCode(e) {
    e.preventDefault();
    if (loading) return;
    setLoading(true);
    setError('');
    try {
      const body = await post('/api/auth/otp/verify', { email: email.trim(), code });
      const data = body.data;
      if (!data || !data.token) throw new Error('Sign-in failed. Please try again.');
      onAuthenticated(data);
    } catch (err) {
      setError(friendly(err));
      setLoading(false);
    }
  }

  if (step === 'email') {
    return (
      <form onSubmit={sendCode}>
        <input required type="email" autoComplete="email" placeholder={emailPlaceholder}
          value={email} onChange={e => setEmail(e.target.value)} />
        {error && <div className="otp-error" role="alert">{error}</div>}
        <button className="checkout-button" disabled={loading}>{loading ? 'Sending code…' : 'Send code ↗'}</button>
      </form>
    );
  }

  return (
    <form onSubmit={verifyCode}>
      <div className="otp-sent">We sent a 6-digit code to <b>{email.trim()}</b></div>
      <input ref={codeRef} className="otp-input" required inputMode="numeric" autoComplete="one-time-code"
        pattern="\d{6}" maxLength={6} placeholder="······" value={code}
        onChange={e => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))} />
      {error && <div className="otp-error" role="alert">{error}</div>}
      <button className="checkout-button" disabled={loading || code.length !== 6}>{loading ? 'Verifying…' : `${submitLabel} ↗`}</button>
      <div className="otp-actions">
        <button type="button" disabled={cooldown > 0 || loading} onClick={() => sendCode()}>
          {cooldown > 0 ? `Resend code in ${cooldown}s` : 'Resend code'}
        </button>
        <button type="button" onClick={() => { setStep('email'); setCode(''); setError(''); }}>Change email</button>
      </div>
    </form>
  );
}
