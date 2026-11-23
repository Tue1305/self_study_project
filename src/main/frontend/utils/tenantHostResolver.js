/**
 * Extracts tenant routing metadata from window.location.
 * Supports:
 *  - Local subdomains: salonA.localhost:3000 -> subdomain: "salona"
 *  - SaaS subdomains:uxe.jennysnailstudio.com -> subdomain: "luxe"
 *  - Custom domains: booking.luxesalon.com -> customDomain: "booking.luxesalon.com"
 *  - Query fallback: ?tenant=luxe
 */
export function resolveHostTenantInfo(locationObj = window.location, baseDomain = 'jennysnailstudio.com') {
  const rawHostname = (locationObj?.hostname || 'localhost').toLowerCase().trim();
  const searchParams = new URLSearchParams(locationObj?.search || '');
  const queryTenant = searchParams.get('tenant')?.trim().toLowerCase() || null;

  const isIpAddress = /^\d+\.\d+\.\d+\.\d+$/.test(rawHostname);
  const isBareLocalhost = rawHostname === 'localhost' || isIpAddress;

  let subdomain = null;
  let customDomain = null;

  if (!isBareLocalhost) {
    if (rawHostname.endsWith('.localhost')) {
      const candidate = rawHostname.slice(0, -'.localhost'.length);
      if (candidate && candidate !== 'www') {
        subdomain = candidate;
      }
    } else if (rawHostname.endsWith(`.${baseDomain}`)) {
      const candidate = rawHostname.slice(0, -(`.${baseDomain}`.length));
      if (candidate && candidate !== 'www') {
        subdomain = candidate;
      }
    } else if (rawHostname !== baseDomain && rawHostname !== `www.${baseDomain}`) {
      customDomain = rawHostname;
    }
  }

  return {
    hostname: rawHostname,
    subdomain,
    customDomain,
    queryTenant,
    effectiveTenantKey: queryTenant || subdomain || null,
    isExplicitTenantRequest: Boolean(queryTenant || subdomain || customDomain),
  };
}
