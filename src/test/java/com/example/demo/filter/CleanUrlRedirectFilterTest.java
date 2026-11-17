package com.example.demo.filter;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class CleanUrlRedirectFilterTest {

    private CleanUrlRedirectFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CleanUrlRedirectFilter();
    }

    @Test
    @DisplayName("Direct GET /booking.html issues 301 Moved Permanently to /book")
    void testDirectBookingHtmlRedirect() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/booking.html");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_MOVED_PERMANENTLY, response.getStatus());
        assertEquals("/book", response.getHeader(HttpHeaders.LOCATION));
    }

    @Test
    @DisplayName("Direct GET /booking.html with action=manage redirects to /manage-booking")
    void testDirectBookingHtmlWithManageToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/booking.html");
        request.setQueryString("token=cancel-xyz&action=manage");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_MOVED_PERMANENTLY, response.getStatus());
        assertEquals("/manage-booking?token=cancel-xyz&action=manage", response.getHeader(HttpHeaders.LOCATION));
    }

    @Test
    @DisplayName("Direct GET /admin-calendar.html issues 301 to /admin/calendar")
    void testAdminCalendarHtmlRedirect() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin-calendar.html");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_MOVED_PERMANENTLY, response.getStatus());
        assertEquals("/admin/calendar", response.getHeader(HttpHeaders.LOCATION));
    }

    @Test
    @DisplayName("Direct GET /index.html issues 301 to /")
    void testIndexHtmlRedirect() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/index.html");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_MOVED_PERMANENTLY, response.getStatus());
        assertEquals("/", response.getHeader(HttpHeaders.LOCATION));
    }

    @Test
    @DisplayName("Internal DispatcherType.FORWARD passes through without 301 redirect")
    void testInternalForwardPassesThrough() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/booking.html");
        request.setDispatcherType(DispatcherType.FORWARD);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        // Filter should not filter, chain should be invoked, status is default 200
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertNull(response.getHeader(HttpHeaders.LOCATION));
    }

    @Test
    @DisplayName("Request with forward attribute passes through without 301 redirect")
    void testForwardAttributePassesThrough() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/booking.html");
        request.setAttribute("jakarta.servlet.forward.request_uri", "/book");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertNull(response.getHeader(HttpHeaders.LOCATION));
    }
}
