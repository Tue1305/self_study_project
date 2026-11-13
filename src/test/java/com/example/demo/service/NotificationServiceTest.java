package com.example.demo.service;

import com.example.demo.entity.Appointment;
import com.example.demo.entity.ServiceEntity;
import com.example.demo.entity.TenantConfig;
import com.example.demo.entity.UserEntity;
import com.example.demo.repository.TenantConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private TenantConfigRepository tenantConfigRepository;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        tenantConfigRepository = Mockito.mock(TenantConfigRepository.class);
        notificationService = new NotificationService(tenantConfigRepository, null);

        TenantConfig luxeConfig = new TenantConfig();
        luxeConfig.setTenantKey("luxe");
        luxeConfig.setSalonName("Luxe Nail & Spa Sanctuary");
        luxeConfig.setPrimaryColor("#00b4d8");
        luxeConfig.setSecondaryColor("#03045e");
        luxeConfig.setCustomDomain("luxe.jennysnailstudio.com");

        when(tenantConfigRepository.findByTenantKeyIgnoreCase("luxe")).thenReturn(Optional.of(luxeConfig));
        when(tenantConfigRepository.findFirstByDefaultTenantTrue()).thenReturn(Optional.of(luxeConfig));
    }

    @Test
    @DisplayName("Single Booking: Dispatches branded notification with dev fallback and cancellation token link")
    void testSendBookingConfirmation_SingleAppointment() {
        ServiceEntity service = new ServiceEntity();
        service.setName("Luxe Signature Gel Manicure");
        service.setPrice(new BigDecimal("48.00"));
        service.setDurationMinutes(40);

        UserEntity tech = new UserEntity();
        tech.setName("Chloe Vance");

        Appointment appt = new Appointment();
        appt.setId(101L);
        appt.setTenantId("luxe");
        appt.setCustomerName("Sophia Miller");
        appt.setCustomerEmail("sophia.miller@example.com");
        appt.setCustomerPhone("(555) 321-7654");
        appt.setAppointmentTime(LocalDateTime.of(2026, 11, 20, 14, 0));
        appt.setService(service);
        appt.setNailTech(tech);
        appt.setCancellationToken("token-abc-123");

        NotificationService.NotificationResult result = notificationService.sendBookingConfirmation(appt);

        assertNotNull(result);
        assertTrue(result.success());
        assertTrue(result.simulated(), "Should safely fall back to simulated dispatch in dev environment without SMTP");
        assertNotNull(result.messageId());
        assertTrue(result.details().contains("sophia.miller@example.com"));

        // Verify self-service link generation
        String link = notificationService.buildSelfServiceLink(
                notificationService.resolveTenantConfig("luxe"),
                appt.getCancellationToken()
        );
        assertTrue(link.contains("token=token-abc-123"));
        assertTrue(link.contains("luxe.jennysnailstudio.com"));
        assertTrue(link.contains("action=manage"));
    }

    @Test
    @DisplayName("Multi-Guest Booking: Dispatches individual confirmations for each guest plus group receipt")
    void testSendMultiGuestBookingConfirmations() {
        ServiceEntity mani = new ServiceEntity();
        mani.setName("Luxe Gel Manicure");
        mani.setPrice(new BigDecimal("45.00"));
        mani.setDurationMinutes(40);

        ServiceEntity pedi = new ServiceEntity();
        pedi.setName("Spa Pedicure");
        pedi.setPrice(new BigDecimal("60.00"));
        pedi.setDurationMinutes(50);

        Appointment appt1 = new Appointment();
        appt1.setId(201L);
        appt1.setTenantId("luxe");
        appt1.setCustomerName("Guest One");
        appt1.setCustomerEmail("guest1@example.com");
        appt1.setCustomerPhone("(555) 111-2222");
        appt1.setAppointmentTime(LocalDateTime.of(2026, 11, 21, 11, 0));
        appt1.setService(mani);
        appt1.setCancellationToken("token-guest-1");

        Appointment appt2 = new Appointment();
        appt2.setId(202L);
        appt2.setTenantId("luxe");
        appt2.setCustomerName("Guest Two");
        appt2.setCustomerEmail("guest2@example.com");
        appt2.setCustomerPhone("(555) 333-4444");
        appt2.setAppointmentTime(LocalDateTime.of(2026, 11, 21, 11, 0));
        appt2.setService(pedi);
        appt2.setCancellationToken("token-guest-2");

        List<Appointment> savedAppointments = List.of(appt1, appt2);

        Map<String, Object> receipt1 = new HashMap<>();
        receipt1.put("customerName", "Guest One");
        receipt1.put("customerEmail", "guest1@example.com");
        receipt1.put("customerPhone", "(555) 111-2222");
        receipt1.put("services", List.of(mani));
        receipt1.put("subtotal", new BigDecimal("45.00"));

        Map<String, Object> receipt2 = new HashMap<>();
        receipt2.put("customerName", "Guest Two");
        receipt2.put("customerEmail", "guest2@example.com");
        receipt2.put("customerPhone", "(555) 333-4444");
        receipt2.put("services", List.of(pedi));
        receipt2.put("subtotal", new BigDecimal("60.00"));

        List<Map<String, Object>> receipts = List.of(receipt1, receipt2);

        List<NotificationService.NotificationResult> results = notificationService.sendMultiGuestBookingConfirmations(
                savedAppointments,
                receipts,
                "luxe",
                new BigDecimal("105.00")
        );

        // Expect 2 individual guest confirmations + 1 group receipt for primary guest = 3 notifications
        assertEquals(3, results.size());
        for (NotificationService.NotificationResult r : results) {
            assertTrue(r.success());
            assertTrue(r.simulated());
        }
    }

    @Test
    @DisplayName("Fault Tolerance: Null appointment returns safe error result without exception")
    void testResilience_NullAppointment() {
        NotificationService.NotificationResult result = notificationService.sendBookingConfirmation(null);
        assertNotNull(result);
        assertFalse(result.success());
        assertEquals("Appointment was null", result.details());
    }

    @Test
    @DisplayName("Tenant Branded Info: Resolves custom salon address and support phone")
    void testTenantBrandedInfo() {
        String luxeAddress = notificationService.resolveSalonAddress("luxe");
        String luxePhone = notificationService.resolveSupportPhone("luxe");
        assertTrue(luxeAddress.contains("Ocean Blvd"));
        assertEquals("(555) 987-6543", luxePhone);

        String defaultAddress = notificationService.resolveSalonAddress("default");
        String defaultPhone = notificationService.resolveSupportPhone("default");
        assertTrue(defaultAddress.contains("Beauty Lane"));
        assertEquals("(555) 123-4567", defaultPhone);
    }
}
