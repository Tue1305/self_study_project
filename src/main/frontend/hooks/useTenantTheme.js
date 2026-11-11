import { useEffect } from 'react';

function darkenHex(hex, percent = 12) {
  if (!hex) return '#0077b6';
  const clean = hex.replace('#', '');
  if (!/^[0-9a-fA-F]{6}$/.test(clean)) return hex;
  const num = parseInt(clean, 16);
  const amt = Math.round(2.55 * percent);
  const r = Math.max(0, (num >> 16) - amt);
  const g = Math.max(0, ((num >> 8) & 0x00ff) - amt);
  const b = Math.max(0, (num & 0x0000ff) - amt);
  return `#${(0x1000000 + r * 0x10000 + g * 0x100 + b).toString(16).slice(1)}`;
}

function hexToRgba(hex, alpha = 0.1) {
  if (!hex) return 'rgba(0, 180, 216, 0.1)';
  const clean = hex.replace('#', '');
  if (!/^[0-9a-fA-F]{6}$/.test(clean)) return 'rgba(0, 180, 216, 0.1)';
  const num = parseInt(clean, 16);
  return `rgba(${(num >> 16) & 255}, ${(num >> 8) & 255}, ${num & 255}, ${alpha})`;
}

/**
 * Dynamically injects tenant theme tokens into CSS :root variables
 * (--primary-color, --secondary-color, --font-family, plus Bootstrap aliases)
 * and updates the document title.
 */
export function useTenantTheme(tenant) {
  useEffect(() => {
    if (!tenant) return;

    const root = document.documentElement;
    const primaryColor = tenant.theme?.primaryColor || '#e83e8c';
    const secondaryColor = tenant.theme?.secondaryColor || '#343a40';
    const fontFamily = tenant.theme?.fontFamily || "'Poppins', sans-serif";
    const hoverColor = darkenHex(primaryColor, 12);
    const softTint = hexToRgba(primaryColor, 0.1);

    // Standard multi-tenant CSS custom properties
    root.style.setProperty('--primary-color', primaryColor);
    root.style.setProperty('--primary-color-hover', hoverColor);
    root.style.setProperty('--primary-color-soft', softTint);
    root.style.setProperty('--secondary-color', secondaryColor);
    root.style.setProperty('--font-family', fontFamily);

    // Compatibility aliases for existing salon stylesheets
    root.style.setProperty('--bs-primary-pink', primaryColor);
    root.style.setProperty('--bs-primary-pink-hover', hoverColor);
    root.style.setProperty('--bs-secondary-accent', secondaryColor);
    root.style.setProperty('--bs-soft-pink', softTint);
    root.style.setProperty('--tenant-font-family', fontFamily);

    document.body.style.fontFamily = 'var(--font-family)';
    if (tenant.salonName) {
      document.title = `Book an Appointment — ${tenant.salonName}`;
    }
  }, [tenant]);
}
