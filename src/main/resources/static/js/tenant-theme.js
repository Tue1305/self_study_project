/**
 * Shared Tenant Theme & Clean URL Manager
 * Provides synchronous pre-loading, localStorage caching, clean URL helpers,
 * and automatic session/subdomain/slug tenant resolution without query parameters.
 */
const TenantTheme = (function() {

    function resolveTenantId() {
        try {
            // 1. Check path slug for public booking routes: /book/{tenantSlug}, /booking/{tenantSlug}, /gift-cards/{tenantSlug}, /giftcards/{tenantSlug}
            const path = window.location.pathname || '';
            const slugMatch = path.match(/^\/(?:book|booking|gift-cards|giftcards)\/([a-zA-Z0-9_-]+)/i);
            if (slugMatch && slugMatch[1]) {
                const candidate = slugMatch[1].trim().toLowerCase();
                // Exclude system keywords if any
                if (candidate !== 'manage' && candidate !== 'confirmation') {
                    return candidate;
                }
            }

            // 2. Check JWT token in storage (authenticated admin / staff session)
            const token = localStorage.getItem('accessToken') || localStorage.getItem('token');
            if (token && token.includes('.')) {
                try {
                    const payload = JSON.parse(atob(token.split('.')[1]));
                    if (payload && payload.tenant_id) {
                        return payload.tenant_id.trim().toLowerCase();
                    }
                } catch (e) {}
            }

            // 3. Check Subdomain from hostname (e.g. luxe.salon.com or luxe.localhost)
            const host = window.location.hostname;
            if (host && host !== 'localhost' && !/^\d+\.\d+\.\d+\.\d+$/.test(host)) {
                const parts = host.split('.');
                if ((parts.length >= 3 || (parts.length === 2 && parts[1] === 'localhost')) && parts[0] !== 'www') {
                    return parts[0].trim().toLowerCase();
                }
            }

            // 4. Check localStorage / sessionStorage
            const stored = localStorage.getItem('active_tenant') || localStorage.getItem('tenant') || sessionStorage.getItem('active_tenant');
            if (stored) return stored.trim().toLowerCase();

            // 5. Query parameter fallback for backwards compatibility (?tenant=)
            const urlParams = new URLSearchParams(window.location.search);
            const tenant = urlParams.get('tenant') || urlParams.get('tenantId');
            if (tenant) return tenant.trim().toLowerCase();
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

    function getTenantDisplayName(tenantKey, salonName) {
        const key = (tenantKey || '').trim().toLowerCase();
        if (key === 'luxe') return 'Luxe';
        if (key === 'default' || !key) return 'Jenny';
        if (salonName && !salonName.toLowerCase().includes('nail')) {
            return salonName;
        }
        if (salonName) {
            const firstWord = salonName.split(/[\s'’]/)[0];
            if (firstWord && firstWord.length > 1) return firstWord;
        }
        return key.charAt(0).toUpperCase() + key.slice(1);
    }

    function syncAdminElements(tenantId, theme) {
        const tenant = (tenantId || resolveTenantId()).trim().toLowerCase();
        const brandEl = document.getElementById('portalBrandTitle');
        if (brandEl) {
            brandEl.textContent = 'Admin Portal';
        }

        const pill = document.getElementById('tenantPill');
        if (pill) {
            let salon = theme ? theme.salonName : null;
            let primary = theme ? theme.primaryColor : null;
            if (!primary) {
                try {
                    const cached = localStorage.getItem('tenant_theme_' + tenant);
                    if (cached) {
                        const parsed = JSON.parse(cached);
                        salon = salon || parsed.salonName;
                        primary = parsed.primaryColor;
                    }
                } catch (e) {}
            }
            if (!primary) {
                primary = (tenant === 'luxe') ? '#00b4d8' : '#e91e63';
            }
            const displayName = getTenantDisplayName(tenant, salon);
            pill.textContent = displayName;
            pill.style.backgroundColor = primary;
            pill.classList.remove('bg-secondary');
            pill.style.fontSize = '0.75rem';
            pill.style.letterSpacing = '0.05em';
            pill.style.fontWeight = '700';
        }
    }

    function applyThemeToDom(theme, tenantId) {
        if (!theme) return;
        const tenant = (tenantId || resolveTenantId()).trim().toLowerCase();
        const primary = theme.primaryColor || (tenant === 'luxe' ? '#00b4d8' : '#e91e63');
        const secondary = theme.secondaryColor || (tenant === 'luxe' ? '#03045e' : '#343a40');
        const hover = theme.primaryPinkHover || darkenHex(primary, 12);
        const soft = theme.softPink || hexToRgba(primary, 0.1);
        const font = theme.fontFamily || 'system-ui, -apple-system, "Segoe UI", Roboto, sans-serif';

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

        syncAdminElements(tenant, theme);
        syncDynamicStoreHours();

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

    // Helper to generate auth headers including tenant
    function getAuthHeaders(customHeaders = {}) {
        const tenant = resolveTenantId();
        const token = localStorage.getItem('accessToken') || localStorage.getItem('token');
        return {
            'X-Tenant-ID': tenant,
            ...(token ? { 'Authorization': 'Bearer ' + token } : {}),
            ...customHeaders
        };
    }

    // Clean URL mapping helper
    function cleanPath(url) {
        if (!url) return '/';
        return url
            .replace(/admin-dashboard\.html/g, '/admin/dashboard')
            .replace(/admin-calendar\.html/g, '/admin/calendar')
            .replace(/admin-customers\.html/g, '/admin/customers')
            .replace(/nail-techs\.html/g, '/admin/nail-techs')
            .replace(/booking\.html/g, '/book')
            .replace(/services\.html/g, '/services')
            .replace(/login\.html/g, '/login')
            .replace(/signup\.html/g, '/signup')
            .replace(/forgot-password\.html/g, '/forgot-password')
            .replace(/profile\.html/g, '/profile')
            .replace(/dashboard\.html/g, '/dashboard')
            .replace(/index\.html/g, '/');
    }

    // Background asynchronous sync with backend config
    async function syncFromApi(tenantId) {
        const tenant = (tenantId || resolveTenantId()).trim().toLowerCase();
        try {
            const headers = getAuthHeaders();
            const res = await fetch(`/api/public/tenant-config?tenant=${encodeURIComponent(tenant)}`, { headers });
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

    function getTodayStoreHoursInfo(nowDate) {
        const now = nowDate instanceof Date ? nowDate : new Date();
        const dayNames = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
        const dayIndex = now.getDay();
        const dayName = dayNames[dayIndex];
        const currentMins = now.getHours() * 60 + now.getMinutes();

        // Store schedule: Monday–Saturday 10:00 AM – 7:00 PM, Sunday Closed
        if (dayIndex === 0) {
            return {
                dayName,
                isOpenDay: false,
                isOpenNow: false,
                hoursText: 'Closed',
                statusText: 'Opens Monday at 10:00 AM',
                label: `${dayName} Store Hours: Closed • Opens Monday at 10:00 AM`,
                weeklyTooltip: 'Weekly Hours: Monday – Saturday: 10:00 AM – 7:00 PM | Sunday: Closed'
            };
        }

        const openMins = 10 * 60;  // 10:00 AM
        const closeMins = 19 * 60; // 7:00 PM
        const isOpenNow = currentMins >= openMins && currentMins < closeMins;
        let statusText = 'Closed Now';
        if (isOpenNow) {
            statusText = 'Open Now';
        } else if (currentMins < openMins) {
            statusText = 'Opens at 10:00 AM';
        }

        return {
            dayName,
            isOpenDay: true,
            isOpenNow,
            hoursText: '10:00 AM – 7:00 PM',
            statusText,
            label: `${dayName} Store Hours: 10:00 AM – 7:00 PM • ${statusText}`,
            weeklyTooltip: 'Weekly Hours: Monday – Saturday: 10:00 AM – 7:00 PM | Sunday: Closed'
        };
    }

    function syncDynamicStoreHours() {
        if (typeof document === 'undefined') return;
        const info = getTodayStoreHoursInfo(new Date());

        const targets = new Set();
        document.querySelectorAll('[data-dynamic-store-hours="true"], #footerHours').forEach(el => targets.add(el));
        document.querySelectorAll('footer span, .rounded-pill span').forEach(span => {
            const text = (span.textContent || '').trim();
            if (text.startsWith('Store Hours:') || text.includes('Store Hours:')) {
                targets.add(span);
            }
        });

        targets.forEach(el => {
            el.setAttribute('data-dynamic-store-hours', 'true');
            const badgeColor = info.isOpenNow ? '#198754' : '#6c757d';
            el.innerHTML = `${info.dayName} Store Hours: <strong>${info.hoursText}</strong> <span style="color:${badgeColor};font-weight:700;">• ${info.statusText}</span>`;
            const pill = el.closest('.rounded-pill') || el.parentElement;
            if (pill && !pill.getAttribute('title')) {
                pill.setAttribute('title', info.weeklyTooltip);
            }
        });
    }

    return {
        resolveTenantId,
        darkenHex,
        hexToRgba,
        applyThemeToDom,
        syncPreload,
        syncFromApi,
        getAuthHeaders,
        cleanPath,
        getTenantDisplayName,
        syncAdminElements,
        getTodayStoreHoursInfo,
        syncDynamicStoreHours
    };
})();

// Immediately trigger synchronous pre-load and navigation sync if executing in browser
if (typeof window !== 'undefined' && typeof document !== 'undefined') {
    TenantTheme.syncPreload();
    const initDomSync = () => {
        TenantTheme.syncAdminElements();
        TenantTheme.syncDynamicStoreHours();
    };
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initDomSync);
    } else {
        initDomSync();
    }
    // Refresh live Open/Closed status and weekday automatically every 60 seconds
    setInterval(() => TenantTheme.syncDynamicStoreHours(), 60000);
}
