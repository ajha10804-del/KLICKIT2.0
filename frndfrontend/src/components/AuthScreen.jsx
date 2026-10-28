import React from 'react';
import AuthFlow from './AuthFlow.jsx';

/**
 * Full-page authentication screen for unauthenticated visitors.
 * Thin wrapper around the shared AuthFlow component, centered on the page background.
 */
export default function AuthScreen({
  apiBaseUrl = '',
  onAuthSuccess,
  notify = () => {}
}) {
  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'radial-gradient(circle at top, #f2f7f4 0%, #edf2ee 100%)',
      padding: '24px 16px',
      fontFamily: "'DM Sans', sans-serif"
    }}>
      <AuthFlow
        apiBaseUrl={apiBaseUrl}
        onAuthSuccess={onAuthSuccess}
        notify={notify}
        allowRegister={true}
        initialTab="login"
      />
    </div>
  );
}
