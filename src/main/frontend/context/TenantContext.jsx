import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { resolveHostTenantInfo } from '../utils/tenantHostResolver';
import { useTenantTheme } from '../hooks/useTenantTheme';

const ALLOWED_STEPS = ['SERVICE', 'STAFF', 'STATION', 'DETAILS'];

// Blue Sky theme specification for Luxe
export const BLUE_SKY_THEME = {
  primaryColor: '#00b4d8',
  secondaryColor: '#03045e',
  fontFamily: "'Playfair Display', Georgia, serif",
  logoUrl: null,
  bannerImageUrl: null,
};

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
 * Normalizes backend payload and ensures that Luxe accounts receive the Blue Sky theme
 * and blue-sky layout variant.
 */
function normalizeTenantProfile(raw) {
  if (!raw) return DEFAULT_TENANT_PROFILE;

  const rawTheme = raw.theme || {};
  const rawWizard = raw.wizard || {};
  const rawTenantKey = (raw.tenantKey || raw.tenant_key || 'default').toLowerCase();
  const isLuxe = rawTenantKey === 'luxe';

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

  // Apply Blue Sky theme (#00b4d8 primary, #03045e secondary) when tenant is Luxe
  const primaryColor = isLuxe
    ? (rawTheme.primaryColor || BLUE_SKY_THEME.primaryColor)
    : (rawTheme.primaryColor || rawTheme.primary_color || '#e83e8c');

  const secondaryColor = isLuxe
    ? (rawTheme.secondaryColor || BLUE_SKY_THEME.secondaryColor)
    : (rawTheme.secondaryColor || rawTheme.secondary_color || '#343a40');

  const fontFamily = isLuxe
    ? (rawTheme.fontFamily || BLUE_SKY_THEME.fontFamily)
    : (rawTheme.fontFamily || rawTheme.font_family || "'Poppins', sans-serif");

  const layoutVariant = isLuxe ? 'blue-sky' : (raw.layoutVariant || raw.layout_variant || 'classic');

  return {
    tenantKey: rawTenantKey,
    customDomain: raw.customDomain || raw.custom_domain || null,
    salonName: raw.salonName || raw.salon_name || (isLuxe ? 'Luxe Nail & Spa Sanctuary' : DEFAULT_TENANT_PROFILE.salonName),
    layoutVariant,
    theme: {
      primaryColor,
      secondaryColor,
      fontFamily,
      logoUrl: rawTheme.logoUrl || rawTheme.logo_url || null,
      bannerImageUrl: rawTheme.bannerImageUrl || rawTheme.banner_image_url || null,
    },
    wizard: {
      stepOrder,
      enableStationSelection,
      availableStations: rawWizard.availableStations || rawWizard.available_stations || [],
      enabledCategories: rawWizard.enabledCategories || rawWizard.enabled_categories || [],
      showAppointmentNotes,
      showStaffQualificationTags: false,
    },
  };
}

export function TenantProvider({ children, strictSubdomainCheck = false }) {
  const [tenant, setTenant] = useState(null);
  const [status, setStatus] = useState('loading'); // 'loading' | 'ready' | 'not_found' | 'error'
  const [errorMessage, setErrorMessage] = useState(null);
  const [hostInfo, setHostInfo] = useState(() => resolveHostTenantInfo());
  const [bookingEntryMode, setBookingEntryMode] = useState('blank'); // 'blank' | 'preselected'
  const [preselectedServiceId, setPreselectedServiceId] = useState(null);

  // Automatically inject CSS root variables (--primary-color, --secondary-color, --font-family)
  useTenantTheme(tenant);

  // Parse incoming URL query params on mount for preselection or blank entry
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const svcId = params.get('serviceId') || params.get('service_id') || params.get('service');
    if (svcId) {
      setPreselectedServiceId(svcId.trim());
      setBookingEntryMode('preselected');
    } else {
      setPreselectedServiceId(null);
      setBookingEntryMode('blank');
    }
  }, []);

  const fetchTenantProfile = useCallback(
    async (overrideTenantKey = null, allowFallback = !strictSubdomainCheck) => {
      setStatus('loading');
      setErrorMessage(null);

      const resolved = resolveHostTenantInfo();
      setHostInfo(resolved);

      const targetTenantKey = overrideTenantKey ?? resolved.effectiveTenantKey;
      const params = new URLSearchParams();
      if (targetTenantKey) params.set('tenant', targetTenantKey);
      if (resolved.hostname) params.set('domain', resolved.hostname);
      if (!allowFallback && resolved.isExplicitTenantRequest && overrideTenantKey !== 'default') {
        params.set('strict', 'true');
      }

      const headers = { Accept: 'application/json' };
      if (targetTenantKey) headers['X-Tenant-ID'] = targetTenantKey;

      try {
        const response = await fetch(`/api/public/tenant-config?${params.toString()}`, { headers });

        if (response.status === 404) {
          setStatus('not_found');
          setErrorMessage(`We couldn't find a salon matching "${targetTenantKey || resolved.hostname}".`);
          return;
        }
        if (!response.ok) {
          throw new Error(`Failed to load salon configuration (HTTP ${response.status})`);
        }

        const data = await response.json();
        const normalized = normalizeTenantProfile(data);

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
        setErrorMessage(err.message || 'Unable to reach salon configuration service.');
        setStatus('error');
      }
    },
    [strictSubdomainCheck]
  );

  useEffect(() => {
    fetchTenantProfile();
  }, [fetchTenantProfile]);

  /**
   * Called when a user authenticates.
   * If logging into a "Luxe" account, automatically redirect to the tenant subdomain/host
   * or apply the Luxe "Blue Sky" (#00b4d8 / #03045e) theme immediately.
   */
  const onTenantLogin = useCallback(
    ({ username, role, token }) => {
      const lowerUser = (username || '').toLowerCase();
      const isLuxeUser = lowerUser.includes('luxe');

      if (isLuxeUser) {
        const currentHost = window.location.hostname;
        const currentPort = window.location.port ? `:${window.location.port}` : '';
        const currentProtocol = window.location.protocol;

        // If on localhost without subdomain, redirect to luxe.localhost:port
        if (currentHost === 'localhost' || currentHost === '127.0.0.1') {
          const redirectUrl = `${currentProtocol}//luxe.localhost${currentPort}${window.location.pathname}?tenant=luxe`;
          window.location.href = redirectUrl;
          return;
        }

        // In SPA / same-domain mode, dynamically transition context to Luxe Blue Sky theme
        const url = new URL(window.location.href);
        url.searchParams.set('tenant', 'luxe');
        window.history.replaceState({}, '', url.toString());

        fetchTenantProfile('luxe', true);
      }
    },
    [fetchTenantProfile]
  );

  /**
   * Starts a brand new blank booking session.
   * Clears any URL serviceId params and resets pre-selection state.
   */
  const startBlankBooking = useCallback(() => {
    if (window.history?.replaceState) {
      const url = new URL(window.location.href);
      url.searchParams.delete('serviceId');
      url.searchParams.delete('service_id');
      url.searchParams.delete('service');
      window.history.replaceState({}, '', url.toString());
    }
    setPreselectedServiceId(null);
    setBookingEntryMode('blank');
  }, []);

  const switchToDefaultTenant = useCallback(() => {
    fetchTenantProfile('default', true);
  }, [fetchTenantProfile]);

  const contextValue = useMemo(
    () => ({
      tenant: tenant || DEFAULT_TENANT_PROFILE,
      status,
      errorMessage,
      hostInfo,
      bookingEntryMode,
      preselectedServiceId,
      reloadTenant: () => fetchTenantProfile(),
      onTenantLogin,
      startBlankBooking,
      switchToDefaultTenant,
    }),
    [
      tenant,
      status,
      errorMessage,
      hostInfo,
      bookingEntryMode,
      preselectedServiceId,
      fetchTenantProfile,
      onTenantLogin,
      startBlankBooking,
      switchToDefaultTenant,
    ]
  );

  return <TenantContext.Provider value={contextValue}>{children}</TenantContext.Provider>;
}

export function useTenant() {
  const ctx = useContext(TenantContext);
  if (!ctx) throw new Error('useTenant() must be used inside a <TenantProvider>');
  return ctx;
}
