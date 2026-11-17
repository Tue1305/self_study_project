package com.example.demo.service;

/**
 * ThreadLocal storage holding the active tenant ID for the current request execution context.
 * Enables transparent tenant scoping across services and controllers without query parameters.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setCurrentTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            CURRENT_TENANT.remove();
        } else {
            CURRENT_TENANT.set(tenantId.trim().toLowerCase());
        }
    }

    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
