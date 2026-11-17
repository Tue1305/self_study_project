package com.example.demo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, CustomUserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    // Removed ServletException from throws clause to eliminate warning
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/auth/")
                || path.startsWith("/static/")
                || path.startsWith("/nail_collection/")
                || path.equals("/")
                || path.equals("/error")
                || path.equals("/favicon.ico")
                || path.endsWith(".css")
                || path.endsWith(".js");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            final String token = authHeader.substring(7);
            final String username = jwtUtil.extractUsername(token);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

                if (jwtUtil.isTokenValid(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    String tenantId = jwtUtil.extractTenantId(token);
                    if (tenantId != null && !tenantId.isBlank()) {
                        String cleanTenant = tenantId.trim().toLowerCase();
                        com.example.demo.service.TenantContext.setCurrentTenant(cleanTenant);
                        request.setAttribute("tenant_id", cleanTenant);
                    }
                }
            }
        } catch (Exception e) {
            // If the token is invalid/expired, do not crash; clear context and proceed
            logger.error("JWT Authentication failed: " + e.getMessage(), e);
            SecurityContextHolder.clearContext();
            com.example.demo.service.TenantContext.clear();
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            com.example.demo.service.TenantContext.clear();
        }
    }
}
