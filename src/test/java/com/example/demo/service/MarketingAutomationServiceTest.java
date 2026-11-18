package com.example.demo.service;

import com.example.demo.entity.Appointment;
import com.example.demo.entity.AppointmentTransaction;
import com.example.demo.entity.Customer;
import com.example.demo.entity.GiftCard;
import com.example.demo.repository.AppointmentRepository;
import com.example.demo.repository.AppointmentTransactionRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.GiftCardRepository;
import com.example.demo.repository.TenantConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketingAutomationServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AppointmentTransactionRepository transactionRepository;
    @Mock
    private GiftCardRepository giftCardRepository;

    private TestNotificationService notificationService;
    private MarketingAutomationService marketingService;

    static class TestNotificationService extends NotificationService {
        int birthdayPerkCalls = 0;
        int winBackCalls = 0;
        Customer lastCustomer;
        int lastPointsAwarded;
        BigDecimal lastVoucherAmount;

        public TestNotificationService() {
            super(mock(TenantConfigRepository.class), null);
        }

        @Override
        public NotificationResult sendBirthdayPerkNotification(Customer customer, int pointsAwarded, String voucherCode, BigDecimal voucherAmount, String tenantId) {
            this.birthdayPerkCalls++;
            this.lastCustomer = customer;
            this.lastPointsAwarded = pointsAwarded;
            this.lastVoucherAmount = voucherAmount;
            return new NotificationResult(true, true, "TEST-B", "OK");
        }

        @Override
        public NotificationResult sendWinBackReminderNotification(Customer customer, LocalDate lastVisitDate, String bookingUrl, String tenantId) {
            this.winBackCalls++;
            this.lastCustomer = customer;
            return new NotificationResult(true, true, "TEST-W", "OK");
        }
    }

    @BeforeEach
    void setUp() {
        notificationService = new TestNotificationService();
        marketingService = new MarketingAutomationService(
                customerRepository,
                appointmentRepository,
                transactionRepository,
                giftCardRepository,
                null,
                notificationService
        );
    }

    @Test
    void testGetMarketingSummary() {
        int currentMonth = LocalDate.now().getMonthValue();
        int currentYear = LocalDate.now().getYear();

        Customer c1 = new Customer("Alice", "alice@example.com", "555-0101", "default");
        c1.setBirthDate(LocalDate.of(1995, currentMonth, 15));

        Customer c2 = new Customer("Bob", "bob@example.com", "555-0102", "default");
        c2.setBirthDate(LocalDate.of(1992, currentMonth, 20));
        c2.setLastBirthdayPerkYear(currentYear);

        when(customerRepository.findAllByTenantId("default")).thenReturn(List.of(c1, c2));
        when(transactionRepository.findAllByTenantIdOrderByCreatedAtDesc("default")).thenReturn(List.of());
        when(appointmentRepository.findAllByTenantId("default")).thenReturn(List.of());

        Map<String, Object> summary = marketingService.getMarketingSummary("default");

        assertEquals(2, summary.get("totalCustomers"));
        assertEquals(2L, summary.get("birthdaysThisMonth"));
        assertEquals(1L, summary.get("birthdaysAwardedThisYear"));
    }

    @Test
    void testDispatchBirthdayPerksPoints() {
        int currentMonth = LocalDate.now().getMonthValue();
        int currentYear = LocalDate.now().getYear();

        Customer c = new Customer("Clara", "clara@example.com", "555-0103", "default");
        c.setId(10L);
        c.setBirthDate(LocalDate.of(1998, currentMonth, 10));
        c.setLoyaltyPoints(50);

        when(customerRepository.findAllByTenantId("default")).thenReturn(List.of(c));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = marketingService.dispatchBirthdayPerks("default", "POINTS", 100, null, null);

        assertEquals(1, result.get("awardedCount"));
        assertEquals(150, c.getLoyaltyPoints());
        assertEquals(currentYear, c.getLastBirthdayPerkYear());

        verify(customerRepository).save(c);
        assertEquals(1, notificationService.birthdayPerkCalls);
        assertEquals(100, notificationService.lastPointsAwarded);
        assertEquals("Clara", notificationService.lastCustomer.getName());
    }

    @Test
    void testDispatchBirthdayPerksGiftVoucher() {
        int currentMonth = LocalDate.now().getMonthValue();
        int currentYear = LocalDate.now().getYear();

        Customer c = new Customer("Diana", "diana@example.com", "555-0104", "default");
        c.setId(11L);
        c.setBirthDate(LocalDate.of(1996, currentMonth, 12));

        when(customerRepository.findAllByTenantId("default")).thenReturn(List.of(c));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(giftCardRepository.save(any(GiftCard.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = marketingService.dispatchBirthdayPerks("default", "VOUCHER", null, new BigDecimal("10.00"), null);

        assertEquals(1, result.get("awardedCount"));
        assertEquals(currentYear, c.getLastBirthdayPerkYear());

        verify(giftCardRepository).save(any(GiftCard.class));
        assertEquals(1, notificationService.birthdayPerkCalls);
        assertEquals(new BigDecimal("10.00"), notificationService.lastVoucherAmount);
    }

    @Test
    void testBirthdayPerksSkipsAlreadyAwardedThisYear() {
        int currentMonth = LocalDate.now().getMonthValue();
        int currentYear = LocalDate.now().getYear();

        Customer c = new Customer("Emma", "emma@example.com", "555-0105", "default");
        c.setId(12L);
        c.setBirthDate(LocalDate.of(1994, currentMonth, 14));
        c.setLastBirthdayPerkYear(currentYear); // Already celebrated

        when(customerRepository.findAllByTenantId("default")).thenReturn(List.of(c));

        Map<String, Object> result = marketingService.dispatchBirthdayPerks("default", "POINTS", 100, null, null);

        assertEquals(0, result.get("awardedCount"));
        verify(customerRepository, never()).save(c);
        assertEquals(0, notificationService.birthdayPerkCalls);
    }

    @Test
    void testGetLapsedCustomersIdentifiesInactiveClients() {
        Customer c = new Customer("Frank", "frank@example.com", "555-0106", "default");
        c.setId(20L);

        // Last visited 40 days ago
        AppointmentTransaction oldTx = new AppointmentTransaction();
        oldTx.setTenantId("default");
        oldTx.setCustomerId(20L);
        oldTx.setCustomerName("Frank");
        oldTx.setCreatedAt(LocalDateTime.now().minusDays(40));

        when(customerRepository.findAllByTenantId("default")).thenReturn(List.of(c));
        when(transactionRepository.findAllByTenantIdOrderByCreatedAtDesc("default")).thenReturn(List.of(oldTx));
        when(appointmentRepository.findAllByTenantId("default")).thenReturn(List.of());

        List<Map<String, Object>> lapsed = marketingService.getLapsedCustomers("default", 30);

        assertEquals(1, lapsed.size());
        assertEquals("Frank", lapsed.get(0).get("name"));
        assertTrue((Long) lapsed.get(0).get("daysInactive") >= 40);
    }

    @Test
    void testLapsedCustomersExcludesClientsWithUpcomingAppointments() {
        Customer c = new Customer("Grace", "grace@example.com", "555-0107", "default");
        c.setId(21L);

        // Last visited 45 days ago
        AppointmentTransaction oldTx = new AppointmentTransaction();
        oldTx.setTenantId("default");
        oldTx.setCustomerId(21L);
        oldTx.setCustomerName("Grace");
        oldTx.setCreatedAt(LocalDateTime.now().minusDays(45));

        // BUT has an upcoming appointment tomorrow!
        Appointment upcoming = new Appointment();
        upcoming.setTenantId("default");
        upcoming.setCustomerEmail("grace@example.com");
        upcoming.setAppointmentTime(LocalDateTime.now().plusDays(1));
        upcoming.setStatus(Appointment.AppointmentStatus.CONFIRMED);

        when(customerRepository.findAllByTenantId("default")).thenReturn(List.of(c));
        when(transactionRepository.findAllByTenantIdOrderByCreatedAtDesc("default")).thenReturn(List.of(oldTx));
        when(appointmentRepository.findAllByTenantId("default")).thenReturn(List.of(upcoming));

        List<Map<String, Object>> lapsed = marketingService.getLapsedCustomers("default", 30);

        // Should be empty because client has an active upcoming booking!
        assertTrue(lapsed.isEmpty());
    }

    @Test
    void testDispatchWinBackCampaignDispatchesNotificationAndUpdatesTimestamp() {
        Customer c = new Customer("Hannah", "hannah@example.com", "555-0108", "default");
        c.setId(30L);

        AppointmentTransaction oldTx = new AppointmentTransaction();
        oldTx.setTenantId("default");
        oldTx.setCustomerId(30L);
        oldTx.setCustomerName("Hannah");
        oldTx.setCreatedAt(LocalDateTime.now().minusDays(35));

        when(customerRepository.findAllByTenantId("default")).thenReturn(List.of(c));
        when(transactionRepository.findAllByTenantIdOrderByCreatedAtDesc("default")).thenReturn(List.of(oldTx));
        when(appointmentRepository.findAllByTenantId("default")).thenReturn(List.of());
        when(customerRepository.findById(30L)).thenReturn(Optional.of(c));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = marketingService.dispatchWinBackCampaign("default", 30, null);

        assertEquals(1, result.get("dispatchedCount"));
        assertNotNull(c.getLastRetentionReminderSentAt());
        verify(customerRepository).save(c);
        assertEquals(1, notificationService.winBackCalls);
        assertEquals("Hannah", notificationService.lastCustomer.getName());
    }
}
