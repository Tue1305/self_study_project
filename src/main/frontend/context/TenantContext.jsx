import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { resolveHostTenantInfo } from '../utils/tenantHostResolver';
import { useTenantTheme } from '../hooks/useTenantTheme';

const ALLOWED_STEPS = ['SERVICE', 'STAFF', 'STATION', 'DETAILS'];

export const DEFAULT_TENANT_PROFILE = {
  tenantKey: 'default',
  customDomain: null,
  salonName: "Jenny's Nail Studio",
  layoutVariant: 'classic',
  theme: {
    primaryColor: '#e83e8c',
    secondaryColor: '#343a40',
    fontFamily: "'Poppins', sans-serif",
    logoUrl: null,
    bannerImageUrl: null,
  },
  wizard: {
    stepOrder: ['SERVICE', 'STAFF', 'DETAILS'],
    enableStationSelection: false,
    availableStations: ['Main Floor Chair 1', 'Main Floor Chair 2', 'VIP Window Suite'],
    enabledCategories: [],
    showAppointmentNotes: true,
    showStaffQualificationTags: false,
  },
};

const TenantContext = createContext(null);

/**
 * Normalizes backend payload (supporting both camelCase and snake_case keys)
 * and enforces core wizard state-machine invariants and staff privacy rules.
 */
function normalizeTenantProfile(raw) {
  if (!raw) return DEFAULT_TENANT_PROFILE;

  const rawTheme = raw.theme || {};
  const rawWizard = raw.wizard || {};

  const enableStationSelection = Boolean(
    rawWizard.enableStationSelection ?? rawWizard.enable_station_selection ?? false
  );
  const showAppointmentNotes = Boolean(
    rawWizard.showAppointmentNotes ?? rawWizard.show_appointment_notes ?? true
  );

  const rawSteps = rawWizard.stepOrder || rawWizard.step_order || ['SERVICE', 'STAFF', 'DETAILS'];
  const filteredSteps = rawSteps
    .map((s) => String(s).trim().toUpperCase())
    .filter((step, idx, arr) => {
      if (!ALLOWED_STEPS.includes(step)) return false;
      if (step === 'STATION' && !enableStationSelection) return false;
      return arr.indexOf(step) === idx;
    });

  const hasCoreSteps = ['SERVICE', 'STAFF', 'DETAILS'].every((req) => filteredSteps.includes(req));
  const stepOrder = hasCoreSteps ? filteredSteps : ['SERVICE', 'STAFF', 'DETAILS'];

  return {
    tenantKey: raw.tenantKey || raw.tenant_key || 'default',
    customDomain: raw.customDomain || raw.custom_domain || null,
    salonName: raw.salonName || raw.salon_name || DEFAULT_TENANT_PROFILE.salonName,
    layoutVariant: raw.layoutVariant || raw.layout_variant || 'classic',
    theme: {
      primaryColor: rawTheme.primaryColor || rawTheme.primary_color || '#e83e8c',
      secondaryColor: rawTheme.secondaryColor || rawTheme.secondary_color || '#343a40',
      fontFamily: rawTheme.fontFamily || rawTheme.font_family || "'Poppins', sans-serif",
      logoUrl: rawTheme.logoUrl || rawTheme.logo_url || null,
      bannerImageUrl: rawTheme.bannerImageUrl || rawTheme.banner_image_url || null,
    },
    wizard: {
      stepOrder,
      enableStationSelection,
      availableStations: rawWizard.availableStations || rawWizard.available_stations || [],
      enabledCategories: rawWizard.enabledCategories || rawWizard.enabled_categories || [],
      showAppointmentNotes,
      // Security & privacy invariant: never expose staff qualification badges to customers
      showStaffQualificationTags: false,
    },
  };
}

export function TenantProvider({ children, strictSubdomainCheck = true }) {
  const [tenant, setTenant] = useState(null);
  const [status, setStatus] = useState('loading'); // 'loading' | 'ready' | 'not_found' | 'error'
  const [errorMessage, setErrorMessage] = useState(null);
  const [hostInfo, setHostInfo] = useState(() => resolveHostTenantInfo());

  // Automatically inject CSS variables (--primary-color, --secondary-color, --font-family)
  useTenantTheme(tenant);

  const fetchTenantProfile = useCallback(
    async (overrideTenantKey = null, allowFallback = !strictSubdomainCheck) => {
      setStatus('loading');
      setErrorMessage(null);

      const resolved = resolveHostTenantInfo();
      setHostInfo(resolved);

      const targetTenantKey = overrideTenantKey ?? resolved.effectiveTenantKey;
      const params = new URLSearchParams();
      if (targetTenantKey) {
        params.set('tenant', targetTenantKey);
      }
      if (resolved.hostname) {
        params.set('domain', resolved.hostname);
      }
      if (!allowFallback && resolved.isExplicitTenantRequest && overrideTenantKey !== 'default') {
        params.set('strict', 'true');
      }

      const headers = { Accept: 'application/json' };
      if (targetTenantKey) {
        headers['X-Tenant-ID'] = targetTenantKey;
      }

      try {
        const response = await fetch(`/api/public/tenant-config?${params.toString()}`, { headers });

        if (response.status === 404) {
          setStatus('not_found');
          setErrorMessage(
            `We couldn't find a salon matching "${targetTenantKey || resolved.hostname}".`
          );
          return;
        }

        if (!response.ok) {
          throw new Error(`Failed to load salon configuration (HTTP ${response.status})`);
        }

        const data = await response.json();
        const normalized = normalizeTenantProfile(data);

        // Detect mismatch if a specific subdomain was requested but backend fell back to "default"
        if (
          !allowFallback &&
          targetTenantKey &&
          targetTenantKey !== 'default' &&
          normalized.tenantKey.toLowerCase() !== targetTenantKey.toLowerCase()
        ) {
          setStatus('not_found');
          setErrorMessage(`Subdomain "${targetTenantKey}" is not registered to an active salon.`);
          return;
        }

        setTenant(normalized);
        setStatus('ready');
      } catch (err) {
        console.error('Tenant initialization error:', err);
        setErrorMessage(err.message || 'Unable to reach salon configuration service.');
        setStatus('error');
      }
    },
    [strictSubdomainCheck]
  );

  useEffect(() => {
    fetchTenantProfile();
  }, [fetchTenantProfile]);

  const switchToDefaultTenant = useCallback(() => {
    if (window.history?.replaceState) {
      const url = new URL(window.location.href);
      url.searchParams.set('tenant', 'default');
      window.history.replaceState({}, '', url.toString());
    }
    fetchTenantProfile('default', true);
  }, [fetchTenantProfile]);

  const contextValue = useMemo(
    () => ({
      tenant: tenant || DEFAULT_TENANT_PROFILE,
      status,
      errorMessage,
      hostInfo,
      reloadTenant: () => fetchTenantProfile(),
      switchToDefaultTenant,
    }),
    [tenant, status, errorMessage, hostInfo, fetchTenantProfile, switchToDefaultTenant]
  );

  return <TenantContext.Provider value={contextValue}>{children}</TenantContext.Provider>;
}

export function useTenant() {
  const ctx = useContext(TenantContext);
  if (!ctx) {
    throw new Error('useTenant() must be used inside a <TenantProvider>');
  }
  return ctx;
}
