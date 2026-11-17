package com.example.demo.filter;

import com.example.demo.entity.TenantConfig;
import com.example.demo.repository.TenantConfigRepository;
import com.example.demo.service.TenantContext;
import com.example.demo.service.TenantContextResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class TenantThemeFilterTest {

    private TenantConfigRepository tenantConfigRepository;
    private TenantContextResolver tenantContextResolver;
    private TenantThemeFilter filter;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        tenantConfigRepository = Mockito.mock(TenantConfigRepository.class);
        tenantContextResolver = new TenantContextResolver(tenantConfigRepository);
        filter = new TenantThemeFilter(tenantContextResolver, tenantConfigRepository);

        TenantConfig luxeConfig = new TenantConfig();
        luxeConfig.setTenantKey("luxe");
        luxeConfig.setSalonName("Luxe Nail & Spa Sanctuary");
        luxeConfig.setPrimaryColor("#00b4d8");
        luxeConfig.setSecondaryColor("#03045e");
        luxeConfig.setFontFamily("'Montserrat', sans-serif");
        when(tenantConfigRepository.findByTenantKeyIgnoreCase("luxe")).thenReturn(Optional.of(luxeConfig));

        TenantConfig defaultConfig = new TenantConfig();
        defaultConfig.setTenantKey("default");
        defaultConfig.setSalonName("Jenny's Nail Studio");
        defaultConfig.setPrimaryColor("#e91e63");
        defaultConfig.setSecondaryColor("#343a40");
        defaultConfig.setDefaultTenant(true);
        when(tenantConfigRepository.findByTenantKeyIgnoreCase("default")).thenReturn(Optional.of(defaultConfig));
        when(tenantConfigRepository.findFirstByDefaultTenantTrue()).thenReturn(Optional.of(defaultConfig));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Injects active tenant's theme variables directly into <style id='tenant-theme-vars'> for Luxe")
    void testInjectsLuxeThemeVariables() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/booking.html");
        request.setParameter("tenant", "luxe");

        MockHttpServletResponse response = new MockHttpServletResponse();

        String rawHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Booking</title>
                    <style id="tenant-theme-vars">
                        :root { --primary-color: #e91e63; }
                    </style>
                </head>
                <body><h1>Hello</h1></body>
                </html>
                """;

        FilterChain chain = (req, res) -> {
            res.setContentType("text/html;charset=UTF-8");
            res.getOutputStream().write(rawHtml.getBytes(StandardCharsets.UTF_8));
        };

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        String modifiedHtml = response.getContentAsString();

        assertTrue(modifiedHtml.contains("<style id=\"tenant-theme-vars\">"));
        assertTrue(modifiedHtml.contains("--primary-color: #00b4d8;"));
        assertTrue(modifiedHtml.contains("--bs-primary-pink: #00b4d8;"));
        assertTrue(modifiedHtml.contains("--secondary-color: #03045e;"));
        assertTrue(modifiedHtml.contains("transition: none !important;"));
        assertFalse(modifiedHtml.contains("--primary-color: #e91e63;"));
    }

    @Test
    @DisplayName("Injects default salon theme variables when no tenant is requested")
    void testInjectsDefaultThemeVariables() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/index.html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String rawHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Home</title>
                    <style id="tenant-theme-vars">
                        :root { --primary-color: #000; }
                    </style>
                </head>
                <body><h1>Home</h1></body>
                </html>
                """;

        FilterChain chain = (req, res) -> {
            res.setContentType("text/html");
            res.getOutputStream().write(rawHtml.getBytes(StandardCharsets.UTF_8));
        };

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        String modifiedHtml = response.getContentAsString();

        assertTrue(modifiedHtml.contains("<style id=\"tenant-theme-vars\">"));
        assertTrue(modifiedHtml.contains("--primary-color: #e91e63;"));
        assertTrue(modifiedHtml.contains("--bs-primary-pink: #e91e63;"));
    }

    @Test
    @DisplayName("Skips non-HTML requests like /api/appointments")
    void testSkipsApiEndpoints() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/appointments");
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);

        assertTrue(chain.getRequest() != null);
    }
}
