import React from 'react';
import { useTenant } from '../context/TenantContext';

export default function TenantFallbackScreen() {
  const { status, errorMessage, hostInfo, reloadTenant, switchToDefaultTenant } = useTenant();

  return (
    <div className="container my-5">
      <div className="row justify-content-center">
        <div className="col-md-8 col-lg-6">
          <div className="card border-0 shadow-sm rounded-4 p-4 p-md-5 text-center">
            <div className="mb-3">
              <span
                className="d-inline-flex align-items-center justify-content-center rounded-circle bg-danger-subtle text-danger"
                style={{ width: 64, height: 64, fontSize: '1.75rem' }}
              >
                !
              </span>
            </div>

            <h3 className="fw-bold text-dark mb-2">
              {status === 'not_found' ? 'Salon Not Found' : 'Unable to Load Salon'}
            </h3>

            <p className="text-muted mb-4">
              {errorMessage ||
                `We could not find an active salon configuration for "${hostInfo?.hostname}".`}
            </p>

            <div className="d-flex flex-wrap justify-content-center gap-3">
              <button
                type="button"
                className="btn btn-outline-secondary px-4 py-2 fw-semibold rounded-pill"
                onClick={reloadTenant}
              >
                Retry
              </button>
              <button
                type="button"
                className="btn px-4 py-2 fw-bold text-white rounded-pill"
                style={{ backgroundColor: '#e83e8c' }}
                onClick={switchToDefaultTenant}
              >
                Continue to Default Salon
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
