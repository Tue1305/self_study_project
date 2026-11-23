import React, { useState } from 'react';
import { useTenant } from '../context/TenantContext';

export default function TenantLoginModal({ isOpen, onClose }) {
  const { onTenantLogin } = useTenant();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [errorMsg, setErrorMsg] = useState('');
  const [loading, setLoading] = useState(false);

  if (!isOpen) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMsg('');
    setLoading(true);

    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
      });

      if (response.ok) {
        const data = await response.json();
        const token = data.accessToken || data.token;
        localStorage.setItem('accessToken', token);
        if (data.role) {
          localStorage.setItem('userRole', data.role);
        }
        localStorage.setItem('username', username);

        // Notify TenantContext to handle tenant redirection and theme switching
        onTenantLogin({ username, role: data.role, token });
        if (onClose) onClose();
      } else {
        setErrorMsg('Invalid username or password.');
      }
    } catch (err) {
      console.error('Login error:', err);
      setErrorMsg('Unable to connect to authentication service.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      className="position-fixed top-0 start-0 w-100 h-100 d-flex align-items-center justify-content-center"
      style={{ backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 1100 }}
    >
      <div className="card border-0 shadow-lg rounded-4 p-4 p-sm-5" style={{ maxWidth: 440, width: '90%' }}>
        <div className="d-flex justify-content-between align-items-center mb-4">
          <h4 className="fw-bold mb-0 text-dark">Sign In</h4>
          <button type="button" className="btn-close" onClick={onClose} aria-label="Close" />
        </div>

        {errorMsg && <div className="alert alert-danger py-2 small">{errorMsg}</div>}

        <form onSubmit={handleSubmit}>
          <div className="mb-3">
            <label className="form-label small fw-semibold text-secondary">Username / Email</label>
            <input
              type="text"
              className="form-control"
              required
              placeholder="e.g. luxe or user@example.com"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />
          </div>

          <div className="mb-4">
            <label className="form-label small fw-semibold text-secondary">Password</label>
            <input
              type="password"
              className="form-control"
              required
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          <button
            type="submit"
            className="btn w-100 py-2 fw-bold text-white rounded-3 shadow-sm mb-3"
            style={{ backgroundColor: 'var(--primary-color)' }}
            disabled={loading}
          >
            {loading ? 'Signing in...' : 'Sign In'}
          </button>
        </form>
      </div>
    </div>
  );
}
