package com.example.demo.controller;

import com.example.demo.entity.Appointment;
import com.example.demo.entity.Customer;
import com.example.demo.entity.ServiceEntity;
import com.example.demo.entity.UserEntity;
import com.example.demo.repository.AppointmentActivityLogRepository;
import com.example.demo.repository.AppointmentRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.ServiceRepository;
import com.example.demo.repository.TenantConfigRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AppointmentAuditService;
import com.example.demo.service.NotificationService;
import com.example.demo.service.TenantContextResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AppointmentBookingFlowTest {

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private ServiceRepository serviceRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TenantConfigRepository tenantConfigRepository;
    @Mock
    private AppointmentActivityLogRepository activityLogRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TenantContextResolver resolver = new TenantContextResolver(tenantConfigRepository);
        AppointmentAuditService auditService = new AppointmentAuditService(activityLogRepository, appointmentRepository, customerRepository);
        NotificationService notificationService = new NotificationService(tenantConfigRepository, null);

        AppointmentController controller = new AppointmentController(
                appointmentRepository,
                customerRepository,
                serviceRepository,
                userRepository,
                resolver,
                auditService,
                notificationService
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testSingleGuestSingleServiceBookingSuccess() throws Exception {
        ServiceEntity svc = new ServiceEntity("default", "Classic Manicure", "Mani", new BigDecimal("35.00"), 45);
        svc.setId(10L);

        when(serviceRepository.findByTenantIdAndId("default", 10L)).thenReturn(Optional.of(svc));
        when(customerRepository.findByEmail("jane@example.com")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            if (a.getId() == null) a.setId(101L);
            return a;
        });

        String payload = """
                {
                  "appointmentTime": "2026-11-20T11:00:00",
                  "customerName": "Jane Doe",
                  "customerEmail": "jane@example.com",
                  "customerPhone": "(555) 123-4567",
                  "serviceIds": [10],
                  "guests": [
                    {
                      "guestNumber": 1,
                      "customerName": "Jane Doe",
                      "customerEmail": "jane@example.com",
                      "customerPhone": "(555) 123-4567",
                      "serviceIds": [10]
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.guests.length()").value(1))
                .andExpect(jsonPath("$.guests[0].customerName").value("Jane Doe"))
                .andExpect(jsonPath("$.grandTotal").value(35.00));
    }

    @Test
    void testSingleGuestMultiServiceBookingSuccess() throws Exception {
        ServiceEntity mani = new ServiceEntity("default", "Classic Manicure", "Mani", new BigDecimal("35.00"), 45);
        mani.setId(10L);
        ServiceEntity pedi = new ServiceEntity("default", "Classic Pedicure", "Pedi", new BigDecimal("45.00"), 50);
        pedi.setId(20L);

        when(serviceRepository.findByTenantIdAndId("default", 10L)).thenReturn(Optional.of(mani));
        when(serviceRepository.findByTenantIdAndId("default", 20L)).thenReturn(Optional.of(pedi));
        when(customerRepository.findByEmail("jane@example.com")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            if (a.getId() == null) a.setId(201L);
            return a;
        });

        String payload = """
                {
                  "appointmentTime": "2026-11-20T11:00:00",
                  "guests": [
                    {
                      "guestNumber": 1,
                      "customerName": "Jane Doe",
                      "customerEmail": "jane@example.com",
                      "customerPhone": "(555) 123-4567",
                      "serviceIds": [10, 20]
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guests.length()").value(1))
                .andExpect(jsonPath("$.grandTotal").value(80.00))
                .andExpect(jsonPath("$.totalDurationMinutes").value(95))
                .andExpect(jsonPath("$.appointments.length()").value(2));
    }

    @Test
    void testInvalidEmailRejection() throws Exception {
        String payload = """
                {
                  "appointmentTime": "2026-11-20T11:00:00",
                  "guests": [
                    {
                      "guestNumber": 1,
                      "customerName": "Jane Doe",
                      "customerEmail": "invalid-email-no-at-sign",
                      "customerPhone": "(555) 123-4567",
                      "serviceIds": [10]
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("valid email address")));
    }

    @Test
    void testAdminWalkInBookingManualOverride() throws Exception {
        ServiceEntity svc = new ServiceEntity("default", "Quick Polish", "Express", new BigDecimal("20.00"), 20);
        svc.setId(5L);

        when(serviceRepository.findByTenantIdAndId("default", 5L)).thenReturn(Optional.of(svc));
        when(customerRepository.findByEmail("walkin@salon.com")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            if (a.getId() == null) a.setId(301L);
            return a;
        });

        String payload = """
                {
                  "appointmentTime": "2026-11-20T14:00:00",
                  "customerName": "Walk-In Guest",
                  "customerEmail": "walkin@salon.com",
                  "customerPhone": "(555) 000-0000",
                  "serviceIds": [5],
                  "manualOverride": true
                }
                """;

        mockMvc.perform(post("/api/appointments")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(301L));
    }

    @Test
    void testSelfServiceByTokenRescheduleAndCancel() throws Exception {
        ServiceEntity svc = new ServiceEntity("default", "Gel Nails", "Nail", new BigDecimal("50.00"), 45);
        svc.setId(15L);

        Appointment appt = new Appointment();
        appt.setId(401L);
        appt.setTenantId("default");
        appt.setCustomerName("Token User");
        appt.setCustomerEmail("token@example.com");
        appt.setCustomerPhone("(555) 999-0000");
        appt.setCancellationToken("valid-test-token");
        appt.setService(svc);
        appt.setAppointmentTime(LocalDateTime.of(2026, 11, 20, 10, 0));
        appt.setStatus(Appointment.AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findByCancellationToken("valid-test-token")).thenReturn(Optional.of(appt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. GET by token
        mockMvc.perform(get("/api/appointments/by-token/valid-test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(401))
                .andExpect(jsonPath("$.customerName").value("Token User"))
                .andExpect(jsonPath("$.serviceName").value("Gel Nails"));

        // 2. Reschedule
        String rescheduleBody = """
                {
                  "date": "2026-11-25",
                  "startTime": "14:30:00"
                }
                """;

        mockMvc.perform(post("/api/appointments/by-token/valid-test-token/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newAppointmentTime").value(org.hamcrest.Matchers.startsWith("2026-11-25T14:30")));

        // 3. Cancel
        String cancelBody = """
                {
                  "reason": "Change of plans"
                }
                """;

        mockMvc.perform(post("/api/appointments/by-token/valid-test-token/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
