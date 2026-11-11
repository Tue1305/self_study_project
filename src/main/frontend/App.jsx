import React from 'react';
import { TenantProvider, useTenant } from './context/TenantContext';
import TenantHeader from './components/TenantHeader';
import TenantFallbackScreen from './components/TenantFallbackScreen';
import DynamicBookingWizard from './components/DynamicBookingWizard';

function TenantAppShell() {
  const { status, tenant } = useTenant();

  if (status === 'loading') {
    return (
      <div className="d-flex flex-column justify-content-center align-items-center min-vh-100 bg-light">
        <div className="spinner-border mb-3" style={{ color: '#e83e8c' }} role="status" />
        <p className="text-muted fw-semibold">Loading salon experience...</p>
      </div>
    );
  }

  if (status === 'not_found' || status === 'error') {
    return <TenantFallbackScreen />;
  }

  return (
    <div className="bg-light d-flex flex-column min-vh-100">
      <TenantHeader />
      <DynamicBookingWizard />
      <footer className="bg-white border-top py-4 mt-auto text-center">
        <div className="d-inline-flex align-items-center gap-2 px-3 py-1 bg-light border rounded-pill mb-2">
          <span className="small fw-semibold text-dark">
            Store Hours: Monday – Saturday: 10:00 AM – 7:00 PM | Sunday: Closed
          </span>
        </div>
        <div>
          <small className="text-muted">&copy; {tenant.salonName}</small>
        </div>
      </footer>
    </div>
  );
}

export default function App() {
  return (
    <TenantProvider strictSubdomainCheck={true}>
      <TenantAppShell />
    </TenantProvider>
  );
}
