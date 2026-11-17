package com.example.demo.controller;

import com.example.demo.service.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PageRouteControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        PageRouteController controller = new PageRouteController();
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET /book forwards internally to /booking.html")
    void testBookingRoute() throws Exception {
        mockMvc.perform(get("/book"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/booking.html"));
    }

    @Test
    @DisplayName("GET /book/{tenantSlug} forwards to /booking.html and populates tenant_id")
    void testBookingRouteWithTenantSlug() throws Exception {
        mockMvc.perform(get("/book/luxe"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/booking.html"))
                .andExpect(request().attribute("tenant_id", "luxe"));
    }

    @Test
    @DisplayName("GET /manage-booking forwards to /booking.html")
    void testManageBookingRoute() throws Exception {
        mockMvc.perform(get("/manage-booking"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/booking.html"));
    }

    @Test
    @DisplayName("GET /admin/dashboard forwards to /admin-dashboard.html")
    void testAdminDashboardRoute() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/admin-dashboard.html"));
    }

    @Test
    @DisplayName("GET /admin/calendar forwards to /admin-calendar.html")
    void testAdminCalendarRoute() throws Exception {
        mockMvc.perform(get("/admin/calendar"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/admin-calendar.html"));
    }

    @Test
    @DisplayName("GET /admin/customers forwards to /admin-customers.html")
    void testAdminCustomersRoute() throws Exception {
        mockMvc.perform(get("/admin/customers"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/admin-customers.html"));
    }

    @Test
    @DisplayName("GET /admin/nail-techs forwards to /nail-techs.html")
    void testAdminNailTechsRoute() throws Exception {
        mockMvc.perform(get("/admin/nail-techs"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/nail-techs.html"));
    }

    @Test
    @DisplayName("GET /services forwards to /services.html")
    void testServicesRoute() throws Exception {
        mockMvc.perform(get("/services"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/services.html"));
    }

    @Test
    @DisplayName("GET /login forwards to /login.html")
    void testLoginRoute() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/login.html"));
    }
}
