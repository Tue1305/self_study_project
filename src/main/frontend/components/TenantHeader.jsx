import React, { useState } from 'react';
import { useTenant } from '../context/TenantContext';

export default function TenantHeader({ onOpenLogin, onResetWizard }) {
  const { tenant, startBlankBooking } = useTenant();
  const [logoFailed, setLogoFailed] = useState(false);
  const [bannerFailed, setBannerFailed] = useState(false);

  const { salonName, theme } = tenant;

  const handleGlobalBookNowClick = (e) => {
    e.preventDefault();
    startBlankBooking();
    if (onResetWizard) onResetWizard();
  };

  return (
    <>
      <header className="navbar navbar-expand-lg bg-white shadow-sm sticky-top px-3 px-lg-4">
        <div className="container-fluid d-flex justify-content-between align-items-center">
          <a
            href="/index.html"
            className="navbar-brand fw-bold fs-4 mb-0 d-inline-flex align-items-center gap-2"
            style={{ color: 'var(--primary-color)' }}
          >
            {theme?.logoUrl && !logoFailed && (
              <img
                src={theme.logoUrl}
                alt={`${salonName} Logo`}
                style={{ maxHeight: 40, maxWidth: 140, objectFit: 'contain' }}
                onError={() => setLogoFailed(true)}
              />
            )}
            <span>{salonName}</span>
          </a>

          <nav className="d-flex align-items-center gap-3">
            <a href="/index.html" className="nav-link fw-semibold text-dark">
              Home
            </a>
            <a href="/dashboard.html" className="nav-link fw-semibold text-dark">
              Showcase
            </a>
            <a href="/services.html" className="nav-link fw-semibold text-dark">
              Services
            </a>
            {/* Global Book Now button - initializes completely blank */}
            <button
              type="button"
              onClick={handleGlobalBookNowClick}
              className="btn rounded-pill px-4 py-2 fw-bold text-white shadow-sm"
              style={{ backgroundColor: 'var(--primary-color)' }}
            >
              Book Now
            </button>
            {onOpenLogin && (
              <button
                type="button"
                onClick={onOpenLogin}
                className="btn btn-outline-secondary btn-sm rounded-pill px-3 py-1"
                title="Direct Tenant Login"
              >
                Sign In
              </button>
            )}
          </nav>
        </div>
      </header>

      {theme?.bannerImageUrl && !bannerFailed && (
        <div className="container mt-4">
          <div className="overflow-hidden rounded-4 shadow-sm" style={{ maxHeight: 200 }}>
            <img
              src={theme.bannerImageUrl}
              alt={`${salonName} Banner`}
              style={{ width: '100%', height: 200, objectFit: 'cover', display: 'block' }}
              onError={() => setBannerFailed(true)}
            />
          </div>
        </div>
      )}
    </>
  );
}
