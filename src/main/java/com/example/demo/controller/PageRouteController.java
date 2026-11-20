package com.example.demo.controller;

import com.example.demo.service.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Spring MVC controller that serves clean, extensionless URLs by forwarding internally
 * to static HTML templates without displaying .html in the browser address bar.
 */
@Controller
public class PageRouteController {

    // Clean Customer Booking Routes
    @GetMapping({"/book", "/booking"})
    public String bookingPage() {
        return "forward:/booking.html";
    }

    @GetMapping({"/book/{tenantSlug}", "/booking/{tenantSlug}"})
    public String bookingPageWithSlug(@PathVariable String tenantSlug, HttpServletRequest request) {
        if (tenantSlug != null && !tenantSlug.isBlank()) {
            String normalized = tenantSlug.trim().toLowerCase();
            request.setAttribute("tenant_id", normalized);
            TenantContext.setCurrentTenant(normalized);
        }
        return "forward:/booking.html";
    }

    @GetMapping({"/manage-booking", "/manage-reservation"})
    public String manageBookingPage() {
        return "forward:/booking.html";
    }

    // Clean Admin Routes
    @GetMapping({"/admin/dashboard", "/admin"})
    public String adminDashboard() {
        return "forward:/admin-dashboard.html";
    }

    @GetMapping("/admin/calendar")
    public String adminCalendar() {
        return "forward:/admin-calendar.html";
    }

    @GetMapping("/admin/customers")
    public String adminCustomers() {
        return "forward:/admin-customers.html";
    }

    @GetMapping("/admin/nail-techs")
    public String adminNailTechs() {
        return "forward:/nail-techs.html";
    }

    // Clean Technician / Staff Self-Service Portal Routes
    @GetMapping({"/staff", "/staff/portal", "/tech-portal"})
    public String staffPortalPage() {
        return "forward:/staff-portal.html";
    }

    // Clean General Routes
    @GetMapping("/services")
    public String servicesPage() {
        return "forward:/services.html";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "forward:/login.html";
    }

    @GetMapping("/signup")
    public String signupPage() {
        return "forward:/signup.html";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "forward:/forgot-password.html";
    }

    @GetMapping("/profile")
    public String profilePage() {
        return "forward:/profile.html";
    }

    @GetMapping("/dashboard")
    public String userDashboard() {
        return "forward:/dashboard.html";
    }

    // Clean Public Gift Cards Store & Balance Checker Routes
    @GetMapping({"/gift-cards", "/giftcard", "/giftcards"})
    public String giftCardsPage() {
        return "forward:/gift-cards.html";
    }

    @GetMapping({"/gift-cards/{tenantSlug}", "/giftcards/{tenantSlug}"})
    public String giftCardsPageWithSlug(@PathVariable String tenantSlug, HttpServletRequest request) {
        if (tenantSlug != null && !tenantSlug.isBlank()) {
            String normalized = tenantSlug.trim().toLowerCase();
            request.setAttribute("tenant_id", normalized);
            TenantContext.setCurrentTenant(normalized);
        }
        return "forward:/gift-cards.html";
    }
}
