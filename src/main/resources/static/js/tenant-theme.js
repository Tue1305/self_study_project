/**
 * Shared Tenant Theme Manager
 * Provides synchronous pre-loading, localStorage caching, and async API synchronization
 * to eliminate Flash of Unstyled Content (FOUC).
 */
const TenantTheme = (function() {
    function resolveTenantId() {
        try {
            const urlParams = new URLSearchParams(window.location.search);
            const tenant = urlParams.get('tenant') || urlParams.get('tenantId');
            if (tenant) return tenant.trim().toLowerCase();

            const host = window.location.hostname;
            if (host && host !== 'localhost' && !/^\d+\.\d+\.\d+\.\d+$/.test(host)) {
                const parts = host.split('.');
                if ((parts.length >= 3 || (parts.length === 2 && parts[1] === 'localhost')) && parts[0] !== 'www') {
                    return parts[0].trim().toLowerCase();
                }
            }

            const stored = localStorage.getItem('active_tenant') || localStorage.getItem('tenant') || sessionStorage.getItem('active_tenant');
            if (stored) return stored.trim().toLowerCase();
        } catch (e) {}
        return 'default';
    }

    function darkenHex(hex, pct) {
        if (!hex || hex[0] !== '#') return hex;
        let c = hex.substring(1);
        if (c.length === 3) c = c.split('').map(x => x + x).join('');
        const num = parseInt(c, 16);
        const r = Math.max(0, (num >> 16) - Math.round(255 * (pct / 100)));
        const g = Math.max(0, ((num >> 8) & 0x00FF) - Math.round(255 * (pct / 100)));
        const b = Math.max(0, (num & 0x0000FF) - Math.round(255 * (pct / 100)));
        return '#' + ((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1);
    }

    function hexToRgba(hex, alpha) {
        if (!hex || hex[0] !== '#') return `rgba(233,30,99,${alpha})`;
        let c = hex.substring(1);
        if (c.length === 3) c = c.split('').map(x => x + x).join('');
        const num = parseInt(c, 16);
        return `rgba(${(num >> 16) & 255}, ${(num >> 8) & 255}, ${num & 255}, ${alpha})`;
    }

    function applyThemeToDom(theme, tenantId) {
        if (!theme) return;
        const tenant = (tenantId || resolveTenantId()).trim().toLowerCase();
        const primary = theme.primaryColor || (tenant === 'luxe' ? '#00b4d8' : '#e91e63');
        const secondary = theme.secondaryColor || (tenant === 'luxe' ? '#03045e' : '#343a40');
        const hover = theme.primaryPinkHover || darkenHex(primary, 12);
        const soft = theme.softPink || hexToRgba(primary, 0.1);
        const font = theme.fontFamily || (tenant === 'luxe' ? 'system-ui, -apple-system, sans-serif' : 'system-ui, -apple-system, sans-serif');

        const rootStyle = document.documentElement.style;
        rootStyle.setProperty('--primary-color', primary);
        rootStyle.setProperty('--secondary-color', secondary);
        rootStyle.setProperty('--bs-primary-pink', primary);
        rootStyle.setProperty('--bs-primary-pink-hover', hover);
        rootStyle.setProperty('--bs-soft-pink', soft);
        rootStyle.setProperty('--bs-secondary-accent', secondary);
        rootStyle.setProperty('--bs-secondary-color', secondary);
        if (font) {
            rootStyle.setProperty('--font-family', font);
            rootStyle.setProperty('--tenant-font-family', font);
        }

        // Cache in localStorage for subsequent page navigation
        try {
            const cacheObj = {
                primaryColor: primary,
                secondaryColor: secondary,
                primaryPinkHover: hover,
                softPink: soft,
                fontFamily: font,
                salonName: theme.salonName || (tenant === 'luxe' ? 'Luxe Nail & Spa Sanctuary' : "Jenny's Nail Studio"),
                logoUrl: theme.logoUrl || null
            };
            localStorage.setItem('tenant_theme_' + tenant, JSON.stringify(cacheObj));
            localStorage.setItem('active_tenant', tenant);
            localStorage.setItem('tenant', tenant);
        } catch (e) {}
    }

    // Synchronous execution for pre-loader
    function syncPreload() {
        const tenant = resolveTenantId();
        try {
            const cachedRaw = localStorage.getItem('tenant_theme_' + tenant);
            if (cachedRaw) {
                const parsed = JSON.parse(cachedRaw);
                applyThemeToDom(parsed, tenant);
                return;
            }
        } catch (e) {}

        // Fast fallback for known tenants before API responds
        if (tenant === 'luxe') {
            applyThemeToDom({
                primaryColor: '#00b4d8',
                secondaryColor: '#03045e',
                salonName: 'Luxe Nail & Spa Sanctuary'
            }, tenant);
        } else {
            applyThemeToDom({
                primaryColor: '#e91e63',
                secondaryColor: '#343a40',
                salonName: "Jenny's Nail Studio"
            }, tenant);
        }
    }

    // Background asynchronous sync with backend config
    async function syncFromApi(tenantId) {
        const tenant = (tenantId || resolveTenantId()).trim().toLowerCase();
        try {
            const res = await fetch(`/api/public/tenant-config?tenant=${encodeURIComponent(tenant)}`);
            if (!res.ok) return null;
            const cfg = await res.json();
            const themeConfig = cfg.theme || {};
            const themeObj = {
                primaryColor: themeConfig.primaryColor || cfg.primaryColor,
                secondaryColor: themeConfig.secondaryColor || cfg.secondaryColor,
                fontFamily: themeConfig.fontFamily || cfg.fontFamily,
                logoUrl: themeConfig.logoUrl || cfg.logoUrl,
                salonName: cfg.salonName
            };
            applyThemeToDom(themeObj, tenant);
            return cfg;
        } catch (err) {
            console.warn('[TenantTheme] Failed to sync config from API:', err);
            return null;
        }
    }

    return {
        resolveTenantId,
        darkenHex,
        hexToRgba,
        applyThemeToDom,
        syncPreload,
        syncFromApi
    };
})();

// Immediately trigger synchronous pre-load if executing in browser
if (typeof window !== 'undefined' && typeof document !== 'undefined') {
    TenantTheme.syncPreload();
}
