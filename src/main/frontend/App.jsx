import React, { useRef, useState } from 'react';
import { TenantProvider, useTenant } from './context/TenantContext';
import TenantHeader from './components/TenantHeader';
import TenantFallbackScreen from './components/TenantFallbackScreen';
import DynamicBookingWizard from './components/DynamicBookingWizard';
import TenantLoginModal from './components/TenantLoginModal';

function TenantAppShell() {
  const { status, tenant } = useTenant();
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);
  const resetWizardRef = useRef(null);

  if (status === 'loading') {
    return (
      <div className="d-flex flex-column justify-content-center align-items-center min-vh-100 bg-light">
        <div className="spinner-border mb-3" style={{ color: 'var(--primary-color, #00b4d8)' }} role="status" />
        <p className="text-muted fw-semibold">Loading salon experience...</p>
      </div>
    );
  }

  if (status === 'not_found' || status === 'error') {
    return <TenantFallbackScreen />;
  }

  return (
    <div className={`bg-light d-flex flex-column min-vh-100 layout-${tenant.layoutVariant}`}>
      <TenantHeader
        onOpenLogin={() => setIsLoginModalOpen(true)}
        onResetWizard={() => {
          if (resetWizardRef.current) resetWizardRef.current();
        }}
      />

      <DynamicBookingWizard
        onRegisterResetHandler={(handler) => {
          resetWizardRef.current = handler;
        }}
      />

      <TenantLoginModal
        isOpen={isLoginModalOpen}
        onClose={() => setIsLoginModalOpen(false)}
      />

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
    <TenantProvider strictSubdomainCheck={false}>
      <TenantAppShell />
    </TenantProvider>
  );
}
