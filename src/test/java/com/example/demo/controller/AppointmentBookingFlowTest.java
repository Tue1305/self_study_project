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

    @Test
    void testAvailableSlotsDurationAndTurnoverBufferBlocking() throws Exception {
        // Tony (id=7) is booked at 3:00 PM (15:00) for a 60-minute Pedicure Deluxe
        UserEntity tony = new UserEntity();
        tony.setId(7L);
        tony.setName("Tony");
        tony.setTenantId("default");

        ServiceEntity pediDeluxe = new ServiceEntity("default", "Pedicure Deluxe", "Deluxe", new BigDecimal("60.00"), 60);
        pediDeluxe.setId(30L);

        Appointment existingAppt = new Appointment();
        existingAppt.setId(500L);
        existingAppt.setTenantId("default");
        existingAppt.setNailTech(tony);
        existingAppt.setService(pediDeluxe);
        existingAppt.setAppointmentTime(LocalDateTime.of(2026, 11, 20, 15, 0));
        existingAppt.setStatus(Appointment.AppointmentStatus.CONFIRMED);

        when(userRepository.findByTenantIdAndId("default", 7L)).thenReturn(Optional.of(tony));
        when(appointmentRepository.findByTenantIdAndAppointmentTimeBetween(
                eq("default"), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(List.of(existingAppt));

        // Request 60-min service with 15-min turnover buffer for Tony on 2026-11-20
        // Busy window: 15:00 - 16:00 + 15m buffer = 16:15
        // - 14:00 (runs 14:00-15:00) -> available=true
        // - 14:30 (runs 14:30-15:30) -> available=false (overlaps 15:00)
        // - 15:00 -> available=false
        // - 15:30 -> available=false
        // - 16:00 -> available=false (within 15m turnover buffer ending at 16:15)
        // - 16:15 -> available=true (next open slot!)
        mockMvc.perform(get("/api/appointments/available-slots")
                        .header("X-Tenant-ID", "default")
                        .param("date", "2026-11-20")
                        .param("techId", "7")
                        .param("durationMinutes", "60")
                        .param("bufferMinutes", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isClosed").value(false))
                .andExpect(jsonPath("$.availableSlots[?(@.value == '14:00:00')].available").value(true))
                .andExpect(jsonPath("$.availableSlots[?(@.value == '14:30:00')].available").value(false))
                .andExpect(jsonPath("$.availableSlots[?(@.value == '15:00:00')].available").value(false))
                .andExpect(jsonPath("$.availableSlots[?(@.value == '15:30:00')].available").value(false))
                .andExpect(jsonPath("$.availableSlots[?(@.value == '16:00:00')].available").value(false))
                .andExpect(jsonPath("$.availableSlots[?(@.value == '16:15:00')].available").value(true))
                .andExpect(jsonPath("$.availableSlots[?(@.value == '16:15:00')].timeRangeLabel").value("4:15 PM – 5:15 PM"));
    }

    @Test
    void testSequentialMultiServiceTechnicianAvailability() throws Exception {
        // Tech 1 (id=10, Manicure 30m) is free all afternoon,
        // Tech 2 (id=20, Waxing 30m) has an appointment from 14:00 to 14:30 (+15m buffer -> 14:45).
        // If customer books Service 1 (30m with Tech 10) + Service 2 (30m with Tech 20) starting at 13:30:
        // - Service 1 runs [13:30, 14:00] with Tech 10 (Free)
        // - Service 2 runs [14:00, 14:30] with Tech 20 (Conflicts with Tech 20's 14:00 appointment!)
        // So 13:30 must be unavailable, whereas 14:15 (Service 1 [14:15, 14:45] Tech 10, Service 2 [14:45, 15:15] Tech 20) is available!
        UserEntity tech20 = new UserEntity();
        tech20.setId(20L);
        tech20.setName("Sarah");
        tech20.setTenantId("default");

        ServiceEntity waxSvc = new ServiceEntity("default", "Brow Wax", "Wax", new BigDecimal("25.00"), 30);
        waxSvc.setId(2L);

        Appointment tech20Appt = new Appointment();
        tech20Appt.setId(600L);
        tech20Appt.setTenantId("default");
        tech20Appt.setNailTech(tech20);
        tech20Appt.setService(waxSvc);
        tech20Appt.setAppointmentTime(LocalDateTime.of(2026, 11, 20, 14, 0));
        tech20Appt.setStatus(Appointment.AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findByTenantIdAndAppointmentTimeBetween(
                eq("default"), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(List.of(tech20Appt));

        mockMvc.perform(get("/api/appointments/available-slots")
                        .header("X-Tenant-ID", "default")
                        .param("date", "2026-11-20")
                        .param("durationMinutes", "60")
                        .param("bufferMinutes", "15")
                        .param("serviceDurations", "30,30")
                        .param("serviceTechIds", "10,20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableSlots[?(@.value == '13:30:00')].available").value(false))
                .andExpect(jsonPath("$.availableSlots[?(@.value == '14:15:00')].available").value(true));
    }

    @Test
    void testMultiStaffAvailabilityBreakdownAndEarliestWindowAlignment() throws Exception {
        // Service 1: Pedicure (60m) assigned to Staff A (techId=10, Tony)
        // Service 2: Manicure (30m) assigned to Staff B (techId=20, Chloe)
        // Tony is booked 15:00 to 16:00 (+15m turnover buffer -> 16:15)
        // Chloe is completely free at 15:00 and afternoon
        UserEntity tony = new UserEntity();
        tony.setId(10L);
        tony.setName("Tony");
        tony.setTenantId("default");

        UserEntity chloe = new UserEntity();
        chloe.setId(20L);
        chloe.setName("Chloe");
        chloe.setTenantId("default");

        ServiceEntity pediSvc = new ServiceEntity("default", "Pedicure Deluxe", "Pedi", new BigDecimal("55.00"), 60);
        pediSvc.setId(101L);

        ServiceEntity maniSvc = new ServiceEntity("default", "Express Manicure", "Mani", new BigDecimal("30.00"), 30);
        maniSvc.setId(102L);

        Appointment tonyAppt = new Appointment();
        tonyAppt.setId(701L);
        tonyAppt.setTenantId("default");
        tonyAppt.setNailTech(tony);
        tonyAppt.setService(pediSvc);
        tonyAppt.setAppointmentTime(LocalDateTime.of(2026, 11, 20, 15, 0));
        tonyAppt.setStatus(Appointment.AppointmentStatus.CONFIRMED);

        when(userRepository.findByTenantIdAndId("default", 10L)).thenReturn(Optional.of(tony));
        when(userRepository.findByTenantIdAndId("default", 20L)).thenReturn(Optional.of(chloe));
        when(userRepository.findAllByTenantIdAndRole("default", "ROLE_NAIL_TECH")).thenReturn(List.of(tony, chloe));
        when(serviceRepository.findByTenantIdAndId("default", 101L)).thenReturn(Optional.of(pediSvc));
        when(serviceRepository.findByTenantIdAndId("default", 102L)).thenReturn(Optional.of(maniSvc));
        when(appointmentRepository.findByTenantIdAndAppointmentTimeBetween(
                eq("default"), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(List.of(tonyAppt));

        mockMvc.perform(get("/api/appointments/available-slots")
                        .header("X-Tenant-ID", "default")
                        .param("date", "2026-11-20")
                        .param("durationMinutes", "90")
                        .param("bufferMinutes", "15")
                        .param("serviceIds", "101,102")
                        .param("serviceDurations", "60,30")
                        .param("serviceTechIds", "10,20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isClosed").value(false))
                // 3:00 PM candidate slot is NOT available because Tony is booked 3:00 - 4:15
                .andExpect(jsonPath("$.availableSlots[?(@.value == '15:00:00')].available").value(false))
                .andExpect(jsonPath("$.availableSlots[?(@.value == '15:00:00')].staffBookedDesc").value("Tony booked 3:00 PM – 4:15 PM"))
                // Segment 0 (Tony, Pedicure) is booked with next available at 4:15 PM
                .andExpect(jsonPath("$.availableSlots[?(@.value == '15:00:00')].staffBreakdown[0].techName").value("Tony"))
                .andExpect(jsonPath("$.availableSlots[?(@.value == '15:00:00')].staffBreakdown[0].available").value(false))
                .andExpect(jsonPath("$.availableSlots[?(@.value == '15:00:00')].staffBreakdown[0].busyWindow").value("3:00 PM – 4:15 PM"))
                .andExpect(jsonPath("$.availableSlots[?(@.value == '15:00:00')].staffBreakdown[0].nextAvailable").value("4:15 PM"))
                // Segment 1 (Chloe, Manicure) is available
                .andExpect(jsonPath("$.availableSlots[?(@.value == '15:00:00')].staffBreakdown[1].techName").value("Chloe"))
                .andExpect(jsonPath("$.availableSlots[?(@.value == '15:00:00')].staffBreakdown[1].available").value(true))
                // 4:15 PM slot is the earliest open slot where BOTH are free
                .andExpect(jsonPath("$.availableSlots[?(@.value == '16:15:00')].available").value(true))
                .andExpect(jsonPath("$.availableSlots[?(@.value == '16:15:00')].timeRangeLabel").value("4:15 PM – 5:45 PM"))
                .andExpect(jsonPath("$.availableSlots[?(@.value == '16:15:00')].staffBreakdown[0].available").value(true))
                .andExpect(jsonPath("$.availableSlots[?(@.value == '16:15:00')].staffBreakdown[1].available").value(true))
                .andExpect(jsonPath("$.staffConflicts[0].staffBookedDesc").value("Tony booked 3:00 PM – 4:15 PM"))
                // Per-service availability breakdown allowing selection of each staff/service timeslot
                .andExpect(jsonPath("$.servicesAvailability[0].serviceName").value("Pedicure Deluxe"))
                .andExpect(jsonPath("$.servicesAvailability[0].techName").value("Tony"))
                .andExpect(jsonPath("$.servicesAvailability[0].slots[?(@.value == '15:00:00')].available").value(false))
                .andExpect(jsonPath("$.servicesAvailability[0].slots[?(@.value == '15:00:00')].statusMessage").value("Tony is booked at this time. The next available is 4:15 PM, or choose Anyone Available at 3:00 PM"))
                .andExpect(jsonPath("$.servicesAvailability[0].slots[?(@.value == '15:00:00')].anyoneAvailableAtSlot").value(true))
                .andExpect(jsonPath("$.servicesAvailability[1].serviceName").value("Express Manicure"))
                .andExpect(jsonPath("$.servicesAvailability[1].techName").value("Chloe"))
                .andExpect(jsonPath("$.servicesAvailability[1].slots[?(@.value == '15:00:00')].available").value(true));
    }

    @Test
    void testMultiServiceBookingWithPerServiceTimeSlots() throws Exception {
        UserEntity tony = new UserEntity();
        tony.setId(10L);
        tony.setName("Tony");
        tony.setTenantId("default");

        UserEntity chloe = new UserEntity();
        chloe.setId(20L);
        chloe.setName("Chloe");
        chloe.setTenantId("default");

        ServiceEntity mani = new ServiceEntity("default", "Manicure", "Mani", new BigDecimal("35.00"), 30);
        mani.setId(101L);
        ServiceEntity pedi = new ServiceEntity("default", "Pedicure", "Pedi", new BigDecimal("45.00"), 45);
        pedi.setId(102L);

        when(userRepository.findByTenantIdAndId("default", 10L)).thenReturn(Optional.of(tony));
        when(userRepository.findByTenantIdAndId("default", 20L)).thenReturn(Optional.of(chloe));
        when(serviceRepository.findByTenantIdAndId("default", 101L)).thenReturn(Optional.of(mani));
        when(serviceRepository.findByTenantIdAndId("default", 102L)).thenReturn(Optional.of(pedi));
        when(customerRepository.findByEmail("jane@example.com")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        java.util.List<Appointment> savedList = new java.util.ArrayList<>();
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            if (a.getId() == null) a.setId((long) (300 + savedList.size()));
            savedList.add(a);
            return a;
        });

        // Customer books Manicure with Tony at 15:00 and Pedicure with Chloe at 15:30
        String payload = """
                {
                  "appointmentTime": "2026-11-20T15:00:00",
                  "customerName": "Jane Doe",
                  "customerEmail": "jane@example.com",
                  "customerPhone": "(555) 123-4567",
                  "serviceIds": [101, 102],
                  "serviceTimeSlots": {
                    "101": "15:00:00",
                    "102": "15:30:00"
                  },
                  "guests": [
                    {
                      "guestNumber": 1,
                      "customerName": "Jane Doe",
                      "customerEmail": "jane@example.com",
                      "customerPhone": "(555) 123-4567",
                      "serviceIds": [101, 102],
                      "serviceStaffAssignments": {
                        "101": 10,
                        "102": 20
                      },
                      "serviceTimeSlots": {
                        "101": "15:00:00",
                        "102": "15:30:00"
                      }
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointments.length()").value(2));

        // Verify that Service 1 (Manicure) was saved with Tony at 15:00
        org.junit.jupiter.api.Assertions.assertEquals(2, savedList.size());
        Appointment appt1 = savedList.get(0);
        org.junit.jupiter.api.Assertions.assertEquals(101L, appt1.getService().getId());
        org.junit.jupiter.api.Assertions.assertEquals(10L, appt1.getNailTech().getId());
        org.junit.jupiter.api.Assertions.assertEquals(LocalDateTime.of(2026, 11, 20, 15, 0), appt1.getAppointmentTime());

        // Verify that Service 2 (Pedicure) was saved with Chloe at 15:30
        Appointment appt2 = savedList.get(1);
        org.junit.jupiter.api.Assertions.assertEquals(102L, appt2.getService().getId());
        org.junit.jupiter.api.Assertions.assertEquals(20L, appt2.getNailTech().getId());
        org.junit.jupiter.api.Assertions.assertEquals(LocalDateTime.of(2026, 11, 20, 15, 30), appt2.getAppointmentTime());
    }
}
