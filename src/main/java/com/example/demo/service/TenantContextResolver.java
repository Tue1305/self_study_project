package com.example.demo.service;

import com.example.demo.entity.TenantConfig;
import com.example.demo.repository.TenantConfigRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
public class TenantContextResolver {

    public static final String DEFAULT_TENANT_ID = "default";

    private final TenantConfigRepository tenantConfigRepository;

    public TenantContextResolver(TenantConfigRepository tenantConfigRepository) {
        this.tenantConfigRepository = tenantConfigRepository;
    }

    /**
     * Resolves the active tenant ID from X-Tenant-ID header, query parameter (?tenant= / ?domain=),
     * or Host / X-Forwarded-Host header, falling back safely to the default tenant.
     */
    public String resolveTenantId(HttpServletRequest request) {
        if (request == null) {
            return DEFAULT_TENANT_ID;
        }

        String headerTenant = request.getHeader("X-Tenant-ID");
        if (headerTenant != null && !headerTenant.isBlank()) {
            return normalizeKey(headerTenant);
        }

        String paramTenant = request.getParameter("tenant");
        if (paramTenant != null && !paramTenant.isBlank()) {
            return normalizeKey(paramTenant);
        }

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
