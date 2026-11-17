package com.example.demo.service;

import com.example.demo.entity.Appointment;
import com.example.demo.entity.Customer;
import com.example.demo.entity.ServiceEntity;
import com.example.demo.repository.AppointmentRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.entity.AppointmentActivityLog;
import com.example.demo.repository.AppointmentActivityLogRepository;
import com.example.demo.repository.TenantConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppointmentReminderServiceTest {

    private AppointmentRepository appointmentRepository;
    private TenantConfigRepository tenantConfigRepository;
    private NotificationService notificationService;
    private CustomerRepository customerRepository;
    private AppointmentActivityLogRepository activityLogRepository;
    private AppointmentAuditService auditService;
    private AppointmentReminderService reminderService;

    @BeforeEach
    void setUp() {
        appointmentRepository = Mockito.mock(AppointmentRepository.class);
        tenantConfigRepository = Mockito.mock(TenantConfigRepository.class);
        notificationService = new NotificationService(tenantConfigRepository, null);
        customerRepository = Mockito.mock(CustomerRepository.class);
        activityLogRepository = Mockito.mock(AppointmentActivityLogRepository.class);
        auditService = new AppointmentAuditService(activityLogRepository, appointmentRepository, customerRepository);

        reminderService = new AppointmentReminderService(
                appointmentRepository,
                notificationService,
                customerRepository,
                auditService
        );
    }

    @Test
    @DisplayName("Dispatches 24-hour reminder, updates reminderSentAt timestamp, and logs activity")
    void testDispatchUpcomingReminders_Success() {
        LocalDateTime apptTime = LocalDateTime.now().plusHours(18);

        Appointment appt = new Appointment();
        appt.setId(501L);
        appt.setTenantId("default");
        appt.setCustomerName("Alice Walker");
        appt.setCustomerEmail("alice@example.com");
        appt.setCustomerPhone("(555) 234-5678");
        appt.setAppointmentTime(apptTime);
        appt.setStatus(Appointment.AppointmentStatus.CONFIRMED);
        appt.setReminderSentAt(null);

        ServiceEntity svc = new ServiceEntity("default", "Classic Manicure", "Mani", new BigDecimal("35.00"), 45);
        appt.setService(svc);

        Customer customer = new Customer();
        customer.setId(88L);
        customer.setName("Alice Walker");
        customer.setEmail("alice@example.com");

        when(appointmentRepository.findByTenantIdAndAppointmentTimeBetweenAndReminderSentAtIsNullAndStatusNot(
                eq("default"), any(), any(), eq(Appointment.AppointmentStatus.CANCELLED)
        )).thenReturn(List.of(appt));

        when(customerRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(customer));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = reminderService.dispatchUpcomingReminders("default");

        assertNotNull(result);
        assertEquals(1, result.get("totalCandidatesChecked"));
        assertEquals(1, result.get("remindersSent"));

        assertNotNull(appt.getReminderSentAt());
        verify(appointmentRepository, times(1)).save(appt);

        verify(activityLogRepository, times(1)).save(any(AppointmentActivityLog.class));
    }

    @Test
    @DisplayName("When no upcoming appointments qualify, dispatch returns zero sent")
    void testDispatchUpcomingReminders_NoEligible() {
        when(appointmentRepository.findByTenantIdAndAppointmentTimeBetweenAndReminderSentAtIsNullAndStatusNot(
                eq("luxe"), any(), any(), eq(Appointment.AppointmentStatus.CANCELLED)
        )).thenReturn(List.of());

        Map<String, Object> result = reminderService.dispatchUpcomingReminders("luxe");

        assertEquals(0, result.get("totalCandidatesChecked"));
        assertEquals(0, result.get("remindersSent"));
        verifyNoInteractions(activityLogRepository);
    }
}
