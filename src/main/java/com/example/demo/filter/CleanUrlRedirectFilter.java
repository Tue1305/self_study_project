package com.example.demo.filter;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Filter that issues HTTP 301 (Moved Permanently) redirects whenever direct requests
 * to legacy .html files are made, redirecting to clean, extensionless URLs.
 * Internal server forwards (DispatcherType.FORWARD) pass through untouched.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CleanUrlRedirectFilter extends OncePerRequestFilter {

    private static final Map<String, String> HTML_TO_CLEAN_MAP = Map.ofEntries(
            Map.entry("/admin-dashboard.html", "/admin/dashboard"),
            Map.entry("/admin-calendar.html", "/admin/calendar"),
            Map.entry("/admin-customers.html", "/admin/customers"),
            Map.entry("/nail-techs.html", "/admin/nail-techs"),
            Map.entry("/booking.html", "/book"),
            Map.entry("/services.html", "/services"),
            Map.entry("/login.html", "/login"),
            Map.entry("/signup.html", "/signup"),
            Map.entry("/forgot-password.html", "/forgot-password"),
            Map.entry("/profile.html", "/profile"),
            Map.entry("/dashboard.html", "/dashboard"),
            Map.entry("/index.html", "/")
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Never intercept internal forwards from PageRouteController
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return true;
        }
        if (request.getAttribute("jakarta.servlet.forward.request_uri") != null) {
            return true;
        }
        String path = request.getRequestURI();
        return !path.endsWith(".html");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String uri = request.getRequestURI();
        String cleanPath = HTML_TO_CLEAN_MAP.get(uri);

        if (cleanPath != null) {
            String query = request.getQueryString();
            // If booking.html was accessed with a cancellation/manage token, send to /manage-booking
            if ("/book".equals(cleanPath) && query != null && query.contains("action=manage")) {
                cleanPath = "/manage-booking";
            }
            String target = cleanPath + (query != null && !query.isBlank() ? "?" + query : "");

            response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
            response.setHeader(HttpHeaders.LOCATION, target);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
