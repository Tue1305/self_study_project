package com.example.demo.filter;

import com.example.demo.entity.TenantConfig;
import com.example.demo.repository.TenantConfigRepository;
import com.example.demo.service.TenantContextResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filter that intercepts HTML page responses and injects/updates the active tenant's
 * CSS variables directly inside a server-side <style id="tenant-theme-vars">:root { ... }</style> block
 * in the <head> before the first byte is sent to the client.
 *
 * This provides zero-delay, first-visit FOUC protection even on cold caches.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class TenantThemeFilter extends OncePerRequestFilter {

    private static final Pattern THEME_VARS_PATTERN = Pattern.compile(
            "<style id=[\"']tenant-theme-vars[\"'][^>]*>.*?</style>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern HEAD_OPEN_PATTERN = Pattern.compile(
            "<head[^>]*>",
            Pattern.CASE_INSENSITIVE
    );

    private final TenantContextResolver tenantContextResolver;
    private final TenantConfigRepository tenantConfigRepository;

    @Autowired
    public TenantThemeFilter(
            TenantContextResolver tenantContextResolver,
            TenantConfigRepository tenantConfigRepository
    ) {
        this.tenantContextResolver = tenantContextResolver;
        this.tenantConfigRepository = tenantConfigRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method)) {
            return true;
        }
        String path = request.getRequestURI();
        if (path.startsWith("/api/") || path.startsWith("/actuator/")) {
            return true;
        }
        // Only process HTML pages and root
        return !(path.endsWith(".html") || path.equals("/") || path.isEmpty());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(request, wrappedResponse);
        } finally {
            int status = wrappedResponse.getStatus();
            byte[] body = wrappedResponse.getContentAsByteArray();

            if (status == HttpServletResponse.SC_OK && body.length > 0) {
                String contentType = wrappedResponse.getContentType();
                if (contentType == null || contentType.contains("text/html")) {
                    String originalHtml = new String(body, StandardCharsets.UTF_8);
                    String injectedHtml = injectTenantThemeVars(originalHtml, request);

                    byte[] modifiedBytes = injectedHtml.getBytes(StandardCharsets.UTF_8);
                    response.setContentType(contentType != null ? contentType : "text/html;charset=UTF-8");
                    response.setContentLength(modifiedBytes.length);
                    response.getOutputStream().write(modifiedBytes);
                    response.getOutputStream().flush();
                    return;
                }
            }

            wrappedResponse.copyBodyToResponse();
        }
    }

    private String injectTenantThemeVars(String html, HttpServletRequest request) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        Optional<TenantConfig> configOpt = tenantConfigRepository.findByTenantKeyIgnoreCase(tenantId);
        if (configOpt.isEmpty()) {
            configOpt = tenantConfigRepository.findFirstByDefaultTenantTrue();
        }

        String primaryColor = "luxe".equalsIgnoreCase(tenantId) ? "#00b4d8" : "#e91e63";
        String secondaryColor = "luxe".equalsIgnoreCase(tenantId) ? "#03045e" : "#343a40";
        String fontFamily = "system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif";

        if (configOpt.isPresent()) {
            TenantConfig cfg = configOpt.get();
            if (cfg.getPrimaryColor() != null && !cfg.getPrimaryColor().isBlank()) {
                primaryColor = cfg.getPrimaryColor().trim();
            }
            if (cfg.getSecondaryColor() != null && !cfg.getSecondaryColor().isBlank()) {
                secondaryColor = cfg.getSecondaryColor().trim();
            }
            if (cfg.getFontFamily() != null && !cfg.getFontFamily().isBlank()) {
                fontFamily = cfg.getFontFamily().trim();
            }
        }

        String hoverColor = darkenHex(primaryColor, 12);
        String softColor = hexToRgba(primaryColor, 0.1);

        String themeStyle = String.format(
                "<style id=\"tenant-theme-vars\">\n" +
                "    :root {\n" +
                "        --primary-color: %s;\n" +
                "        --secondary-color: %s;\n" +
                "        --bs-primary-pink: %s;\n" +
                "        --bs-primary-pink-hover: %s;\n" +
                "        --bs-soft-pink: %s;\n" +
                "        --bs-secondary-accent: %s;\n" +
                "        --bs-secondary-color: %s;\n" +
                "        --tenant-font-family: %s;\n" +
                "        --font-family: %s;\n" +
                "    }\n" +
                "    :root, html, body {\n" +
                "        transition: none !important;\n" +
                "    }\n" +
                "</style>",
                primaryColor, secondaryColor, primaryColor, hoverColor, softColor, secondaryColor, secondaryColor, fontFamily, fontFamily
        );

        Matcher matcher = THEME_VARS_PATTERN.matcher(html);
        if (matcher.find()) {
            return matcher.replaceFirst(Matcher.quoteReplacement(themeStyle));
        }

        Matcher headMatcher = HEAD_OPEN_PATTERN.matcher(html);
        if (headMatcher.find()) {
            return headMatcher.replaceFirst("$0\n    " + Matcher.quoteReplacement(themeStyle));
        }

        return html;
    }

    public static String darkenHex(String hex, int percent) {
        if (hex == null || !hex.startsWith("#")) {
            return hex;
        }
        try {
            String clean = hex.substring(1);
            if (clean.length() == 3) {
                clean = "" + clean.charAt(0) + clean.charAt(0)
                           + clean.charAt(1) + clean.charAt(1)
                           + clean.charAt(2) + clean.charAt(2);
            }
            int num = Integer.parseInt(clean, 16);
            int r = Math.max(0, (num >> 16) - (int) Math.round(255 * (percent / 100.0)));
            int g = Math.max(0, ((num >> 8) & 0xFF) - (int) Math.round(255 * (percent / 100.0)));
            int b = Math.max(0, (num & 0xFF) - (int) Math.round(255 * (percent / 100.0)));
            return String.format("#%02x%02x%02x", r, g, b);
        } catch (Exception e) {
            return hex;
        }
    }

    public static String hexToRgba(String hex, double alpha) {
        if (hex == null || !hex.startsWith("#")) {
            return "rgba(233, 30, 99, " + alpha + ")";
        }
        try {
            String clean = hex.substring(1);
            if (clean.length() == 3) {
                clean = "" + clean.charAt(0) + clean.charAt(0)
                           + clean.charAt(1) + clean.charAt(1)
                           + clean.charAt(2) + clean.charAt(2);
            }
            int num = Integer.parseInt(clean, 16);
            int r = (num >> 16) & 0xFF;
            int g = (num >> 8) & 0xFF;
            int b = num & 0xFF;
            return String.format(java.util.Locale.ROOT, "rgba(%d, %d, %d, %.2f)", r, g, b, alpha);
        } catch (Exception e) {
            return "rgba(233, 30, 99, " + alpha + ")";
        }
    }
}
