package com.example.demo.service;

import com.example.demo.entity.TenantConfig;
import com.example.demo.repository.TenantConfigRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TenantContextResolver {

    public static final String DEFAULT_TENANT_ID = "default";

    private static final Pattern BOOKING_SLUG_PATTERN =
            Pattern.compile("^/(?:book|booking)/([a-zA-Z0-9_-]+)(?:/.*)?$", Pattern.CASE_INSENSITIVE);

    private final TenantConfigRepository tenantConfigRepository;

    public TenantContextResolver(TenantConfigRepository tenantConfigRepository) {
        this.tenantConfigRepository = tenantConfigRepository;
    }

    /**
     * Resolves the active tenant ID from:
     * 1. TenantContext ThreadLocal (JWT session / Security context)
     * 2. Request attributes (set by PageRouteController for /book/{slug})
     * 3. Clean path slug in URL (/book/{slug})
     * 4. X-Tenant-ID header
     * 5. Cookies (tenant_id or active_tenant)
     * 6. Query parameters (?tenant= / ?tenantId=)
     * 7. Host header / custom domain / subdomain
     * 8. Fallback to default tenant
     */
    public String resolveTenantId(HttpServletRequest request) {
        // 1. ThreadLocal TenantContext (authenticated admin session)
        String contextTenant = TenantContext.getCurrentTenant();
        if (contextTenant != null && !contextTenant.isBlank()) {
            return normalizeKey(contextTenant);
        }

        if (request == null) {
            return DEFAULT_TENANT_ID;
        }

        // 2. Request attribute (set during internal forwarding)
        Object attrTenant = request.getAttribute("tenant_id");
        if (attrTenant instanceof String s && !s.isBlank()) {
            return normalizeKey(s);
        }

        // 3. Clean path slug: /book/{tenantSlug}
        String uri = request.getRequestURI();
        if (uri != null) {
            Matcher m = BOOKING_SLUG_PATTERN.matcher(uri);
            if (m.find()) {
                String slug = m.group(1);
                if (slug != null && !slug.isBlank()) {
                    return normalizeKey(slug);
                }
            }
        }

        // 4. Explicit X-Tenant-ID header
        String headerTenant = request.getHeader("X-Tenant-ID");
        if (headerTenant != null && !headerTenant.isBlank()) {
            return normalizeKey(headerTenant);
        }

        // 5. Session cookies
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("tenant_id".equalsIgnoreCase(cookie.getName()) || "active_tenant".equalsIgnoreCase(cookie.getName())) {
                    if (cookie.getValue() != null && !cookie.getValue().isBlank()) {
                        return normalizeKey(cookie.getValue());
                    }
                }
            }
        }

        // 6. Query parameter fallback
        String paramTenant = request.getParameter("tenant");
        if (paramTenant == null || paramTenant.isBlank()) {
            paramTenant = request.getParameter("tenantId");
        }
        if (paramTenant != null && !paramTenant.isBlank()) {
            return normalizeKey(paramTenant);
        }

        // 7. Host header (subdomain or custom domain)
        String domainParam = request.getParameter("domain");
        String host = (domainParam != null && !domainParam.isBlank())
                ? cleanHost(domainParam)
                : extractHost(request);

        if (host != null && !host.isBlank()) {
            Optional<TenantConfig> byDomain = tenantConfigRepository.findByCustomDomainIgnoreCase(host);
            if (byDomain.isPresent()) {
                return normalizeKey(byDomain.get().getTenantKey());
            }
            String subdomain = extractSubdomain(host);
            if (subdomain != null) {
                Optional<TenantConfig> bySubdomain = tenantConfigRepository.findByTenantKeyIgnoreCase(subdomain);
                if (bySubdomain.isPresent()) {
                    return normalizeKey(bySubdomain.get().getTenantKey());
                }
            }
        }

        return tenantConfigRepository.findFirstByDefaultTenantTrue()
                .map(cfg -> normalizeKey(cfg.getTenantKey()))
                .orElse(DEFAULT_TENANT_ID);
    }

    public String extractHost(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        String host = (forwardedHost != null && !forwardedHost.isBlank())
                ? forwardedHost.split(",")[0].trim()
                : request.getHeader("Host");
        return cleanHost(host);
    }

    public String cleanHost(String host) {
        if (host == null) {
            return null;
        }
        String cleaned = host.trim();
        int colonIdx = cleaned.indexOf(':');
        if (colonIdx > -1) {
            cleaned = cleaned.substring(0, colonIdx);
        }
        return cleaned.trim().toLowerCase(Locale.ROOT);
    }

    public String extractSubdomain(String host) {
        if (host == null || host.equals("localhost") || host.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+$")) {
            return null;
        }
        String[] parts = host.split("\\.");
        if (parts.length >= 3 || (parts.length == 2 && "localhost".equals(parts[1]))) {
            String candidate = parts[0];
            if (!"www".equals(candidate) && !candidate.isBlank()) {
                return normalizeKey(candidate);
            }
        }
        return null;
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return DEFAULT_TENANT_ID;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-_]", "");
        return normalized.isEmpty() ? DEFAULT_TENANT_ID : normalized;
    }
}
