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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
        when(customerRepository.findByTenantIdAndEmail("default", "jane@example.com")).thenReturn(Optional.empty());
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
        when(customerRepository.findByTenantIdAndEmail("default", "jane@example.com")).thenReturn(Optional.empty());
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
    void testBookingSucceedsWhenEmailIsOmitted() throws Exception {
        ServiceEntity svc = new ServiceEntity("default", "Classic Manicure", "Nails", new BigDecimal("35.00"), 45);
        svc.setId(10L);

        when(serviceRepository.findByTenantIdAndId("default", 10L)).thenReturn(Optional.of(svc));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId(777L);
            return a;
        });

        // Booking with customerEmail omitted
        String payload = """
                {
                  "appointmentTime": "2026-11-20T11:00:00",
                  "customerName": "Jane Doe",
                  "customerPhone": "(555) 123-4567",
                  "serviceIds": [10]
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(777));
    }

    @Test
    void testAdminWalkInBookingManualOverride() throws Exception {
        ServiceEntity svc = new ServiceEntity("default", "Quick Polish", "Express", new BigDecimal("20.00"), 20);
        svc.setId(5L);

        when(serviceRepository.findByTenantIdAndId("default", 5L)).thenReturn(Optional.of(svc));
        when(customerRepository.findByTenantIdAndEmail("default", "walkin@salon.com")).thenReturn(Optional.empty());
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
                        .param("clientNow", "2026-11-20T09:00:00")
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
                        .param("clientNow", "2026-11-20T09:00:00")
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
        lenient().when(userRepository.findByTenantIdAndId("default", 20L)).thenReturn(Optional.of(chloe));
        when(userRepository.findAllByTenantIdAndRole("default", "ROLE_NAIL_TECH")).thenReturn(List.of(tony, chloe));
        when(serviceRepository.findByTenantIdAndId("default", 101L)).thenReturn(Optional.of(pediSvc));
        when(serviceRepository.findByTenantIdAndId("default", 102L)).thenReturn(Optional.of(maniSvc));
        when(appointmentRepository.findByTenantIdAndAppointmentTimeBetween(
                eq("default"), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(List.of(tonyAppt));

        mockMvc.perform(get("/api/appointments/available-slots")
                        .header("X-Tenant-ID", "default")
                        .param("date", "2026-11-20")
                        .param("clientNow", "2026-11-20T09:00:00")
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
        lenient().when(userRepository.findByTenantIdAndId("default", 20L)).thenReturn(Optional.of(chloe));
        when(serviceRepository.findByTenantIdAndId("default", 101L)).thenReturn(Optional.of(mani));
        when(serviceRepository.findByTenantIdAndId("default", 102L)).thenReturn(Optional.of(pedi));
        when(customerRepository.findByTenantIdAndEmail("default", "jane@example.com")).thenReturn(Optional.empty());
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

    @Test
    void testAnyoneAvailableBookingBlockedWhenAllTechsBooked() throws Exception {
        UserEntity tony = new UserEntity();
        tony.setId(10L);
        tony.setName("Tony");
        tony.setTenantId("default");

        ServiceEntity mani = new ServiceEntity("default", "Manicure", "Mani", new BigDecimal("35.00"), 45);
        mani.setId(101L);

        // Tony is already booked at 15:00
        Appointment tonyExisting = new Appointment();
        tonyExisting.setId(800L);
        tonyExisting.setTenantId("default");
        tonyExisting.setNailTech(tony);
        tonyExisting.setService(mani);
        tonyExisting.setAppointmentTime(LocalDateTime.of(2026, 11, 20, 15, 0));
        tonyExisting.setStatus(Appointment.AppointmentStatus.CONFIRMED);

        when(userRepository.findAllByTenantIdAndRole("default", "ROLE_NAIL_TECH")).thenReturn(List.of(tony));
        when(serviceRepository.findByTenantIdAndId("default", 101L)).thenReturn(Optional.of(mani));
        when(appointmentRepository.findByTenantIdAndAppointmentTimeBetween(
                eq("default"), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(List.of(tonyExisting));

        // Customer attempts to book at 15:00 with "Anyone Available" (no nailTech in body)
        String payload = """
                {
                  "appointmentTime": "2026-11-20T15:00:00",
                  "customerName": "Alice Smith",
                  "customerEmail": "alice@example.com",
                  "customerPhone": "(555) 444-5555",
                  "serviceIds": [101]
                }
                """;

        mockMvc.perform(post("/api/appointments")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("All specialists are booked at the selected time slot. Please choose another time or specialist."));
    }

    @Test
    void testAnyoneAvailableAutoAssignsFreeTech() throws Exception {
        UserEntity chloe = new UserEntity();
        chloe.setId(20L);
        chloe.setName("Chloe");
        chloe.setTenantId("default");

        ServiceEntity mani = new ServiceEntity("default", "Manicure", "Mani", new BigDecimal("35.00"), 45);
        mani.setId(101L);

        when(userRepository.findAllByTenantIdAndRole("default", "ROLE_NAIL_TECH")).thenReturn(List.of(chloe));
        when(serviceRepository.findByTenantIdAndId("default", 101L)).thenReturn(Optional.of(mani));
        when(customerRepository.findByTenantIdAndEmail("default", "alice@example.com")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appointmentRepository.findByTenantIdAndAppointmentTimeBetween(
                eq("default"), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(Collections.emptyList());

        java.util.concurrent.atomic.AtomicReference<Appointment> savedRef = new java.util.concurrent.atomic.AtomicReference<>();
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId(901L);
            savedRef.set(a);
            return a;
        });

        String payload = """
                {
                  "appointmentTime": "2026-11-20T11:00:00",
                  "customerName": "Alice Smith",
                  "customerEmail": "alice@example.com",
                  "customerPhone": "(555) 444-5555",
                  "serviceIds": [101]
                }
                """;

        mockMvc.perform(post("/api/appointments")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(901L))
                .andExpect(jsonPath("$.nailTech.name").value("Chloe"));

        org.junit.jupiter.api.Assertions.assertNotNull(savedRef.get().getNailTech());
        org.junit.jupiter.api.Assertions.assertEquals(20L, savedRef.get().getNailTech().getId());
    }

    @Test
    void testUpdateStatusConfirmedLogsStaffAndService() throws Exception {
        ServiceEntity svc = new ServiceEntity("default", "Gel Manicure Deluxe", "Mani", new BigDecimal("45.00"), 45);
        svc.setId(101L);
        UserEntity tony = new UserEntity();
        tony.setId(7L);
        tony.setName("Tony");

        Appointment appt = new Appointment();
        appt.setId(501L);
        appt.setTenantId("default");
        appt.setCustomerName("Sarah Connor");
        appt.setCustomerEmail("sarah@example.com");
        appt.setCustomerPhone("(555) 777-9999");
        appt.setAppointmentTime(LocalDateTime.of(2026, 11, 20, 14, 0));
        appt.setService(svc);
        appt.setNailTech(tony);
        appt.setStatus(Appointment.AppointmentStatus.PENDING);

        when(appointmentRepository.findByTenantIdAndId("default", 501L)).thenReturn(Optional.of(appt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(patch("/api/appointments/501/status")
                        .header("X-Tenant-ID", "default")
                        .param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.appointment.notes").value(org.hamcrest.Matchers.containsString("Customer arrived for Gel Manicure Deluxe with Tony")));

        org.junit.jupiter.api.Assertions.assertEquals(Appointment.AppointmentStatus.CONFIRMED, appt.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(appt.getNotes().contains("Tony"));
    }

    @Test
    void testUpdateStatusCancelledWithReason() throws Exception {
        ServiceEntity svc = new ServiceEntity("default", "Pedicure", "Pedi", new BigDecimal("50.00"), 45);
        svc.setId(102L);

        Appointment appt = new Appointment();
        appt.setId(502L);
        appt.setTenantId("default");
        appt.setCustomerName("John Doe");
        appt.setCustomerEmail("john@example.com");
        appt.setCustomerPhone("(555) 123-4567");
        appt.setAppointmentTime(LocalDateTime.of(2026, 11, 20, 15, 0));
        appt.setService(svc);
        appt.setStatus(Appointment.AppointmentStatus.PENDING);

        when(appointmentRepository.findByTenantIdAndId("default", 502L)).thenReturn(Optional.of(appt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(patch("/api/appointments/502/status")
                        .header("X-Tenant-ID", "default")
                        .param("status", "CANCELLED")
                        .param("reason", "Client called sick with fever"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.appointment.notes").value(org.hamcrest.Matchers.containsString("Client called sick with fever")));

        org.junit.jupiter.api.Assertions.assertEquals(Appointment.AppointmentStatus.CANCELLED, appt.getStatus());
    }

    @Test
    void testUpdateStatusNoShow() throws Exception {
        ServiceEntity svc = new ServiceEntity("default", "Manicure", "Mani", new BigDecimal("35.00"), 30);
        svc.setId(103L);

        Appointment appt = new Appointment();
        appt.setId(503L);
        appt.setTenantId("default");
        appt.setCustomerName("Emily Blunt");
        appt.setCustomerEmail("emily@example.com");
        appt.setCustomerPhone("(555) 987-6543");
        appt.setAppointmentTime(LocalDateTime.of(2026, 11, 20, 16, 0));
        appt.setService(svc);
        appt.setStatus(Appointment.AppointmentStatus.PENDING);

        when(appointmentRepository.findByTenantIdAndId("default", 503L)).thenReturn(Optional.of(appt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(patch("/api/appointments/503/status")
                        .header("X-Tenant-ID", "default")
                        .param("status", "NO_SHOW")
                        .param("reason", "Did not arrive and no answer on phone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"))
                .andExpect(jsonPath("$.appointment.notes").value(org.hamcrest.Matchers.containsString("Did not arrive and no answer on phone")));

        org.junit.jupiter.api.Assertions.assertEquals(Appointment.AppointmentStatus.NO_SHOW, appt.getStatus());
    }

    @Test
    void testAutoMarkNoShowAppointmentsOverdue15Minutes() throws Exception {
        ServiceEntity svc = new ServiceEntity("default", "Quick Polish", "Mani", new BigDecimal("20.00"), 30);
        svc.setId(104L);

        Appointment overdueAppt = new Appointment();
        overdueAppt.setId(504L);
        overdueAppt.setTenantId("default");
        overdueAppt.setCustomerName("Late Guest");
        overdueAppt.setCustomerEmail("late@example.com");
        overdueAppt.setCustomerPhone("(555) 000-1111");
        // Overdue by 45 minutes
        overdueAppt.setAppointmentTime(LocalDateTime.now().minusMinutes(45));
        overdueAppt.setService(svc);
        overdueAppt.setStatus(Appointment.AppointmentStatus.PENDING);

        when(appointmentRepository.findAllByTenantId("default")).thenReturn(List.of(overdueAppt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/appointments/auto-no-show")
                        .header("X-Tenant-ID", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedCount").value(1));

        org.junit.jupiter.api.Assertions.assertEquals(Appointment.AppointmentStatus.NO_SHOW, overdueAppt.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(overdueAppt.getNotes().contains("Auto NO_SHOW: 15+ minutes past scheduled time"));
    }

    @Test
    void testAdminCanFreelyEditNotes() throws Exception {
        Appointment appt = new Appointment();
        appt.setId(505L);
        appt.setTenantId("default");
        appt.setCustomerName("Maria Santos");
        appt.setCustomerEmail("maria@example.com");
        appt.setCustomerPhone("(555) 222-3333");
        appt.setNotes("Old notes");

        when(appointmentRepository.findByTenantIdAndId("default", 505L)).thenReturn(Optional.of(appt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        String notesJson = "{\"notes\": \"Prefers short square nails with lavender tint.\"}";

        mockMvc.perform(patch("/api/appointments/505/notes")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(notesJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointment.notes").value("Prefers short square nails with lavender tint."));

        org.junit.jupiter.api.Assertions.assertEquals("Prefers short square nails with lavender tint.", appt.getNotes());
    }

    @Test
    void testSendSingleReminder() throws Exception {
        ServiceEntity svc = new ServiceEntity("default", "Manicure", "Mani", new BigDecimal("35.00"), 30);
        svc.setId(106L);

        Appointment appt = new Appointment();
        appt.setId(506L);
        appt.setTenantId("default");
        appt.setCustomerName("Kelly Green");
        appt.setCustomerEmail("kelly@example.com");
        appt.setCustomerPhone("(555) 333-4444");
        appt.setAppointmentTime(LocalDateTime.now().plusHours(3));
        appt.setService(svc);
        appt.setStatus(Appointment.AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findByTenantIdAndId("default", 506L)).thenReturn(Optional.of(appt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/appointments/506/remind")
                        .header("X-Tenant-ID", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        org.junit.jupiter.api.Assertions.assertNotNull(appt.getReminderSentAt());
    }

    @Test
    void testUpdateStatusCompletedWithStaffNotes() throws Exception {
        ServiceEntity svc = new ServiceEntity("default", "Gel Manicure Deluxe", "Mani", new BigDecimal("48.00"), 45);
        svc.setId(107L);
        UserEntity tony = new UserEntity();
        tony.setId(7L);
        tony.setName("Tony");

        Appointment appt = new Appointment();
        appt.setId(507L);
        appt.setTenantId("default");
        appt.setCustomerName("Jessica Alba");
        appt.setCustomerEmail("jessica@example.com");
        appt.setCustomerPhone("(555) 777-1111");
        appt.setAppointmentTime(LocalDateTime.of(2026, 11, 20, 14, 0));
        appt.setService(svc);
        appt.setNailTech(tony);
        appt.setStatus(Appointment.AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findByTenantIdAndId("default", 507L)).thenReturn(Optional.of(appt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        String staffNotes = "OPI Bubble Bath 2 coats, chrome top coat. Paid in cash with tip.";

        mockMvc.perform(patch("/api/appointments/507/status")
                        .header("X-Tenant-ID", "default")
                        .param("status", "COMPLETED")
                        .param("reason", staffNotes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.appointment.notes").value(org.hamcrest.Matchers.containsString("COMPLETED: Gel Manicure Deluxe with Tony")))
                .andExpect(jsonPath("$.appointment.notes").value(org.hamcrest.Matchers.containsString(staffNotes)));

        org.junit.jupiter.api.Assertions.assertEquals(Appointment.AppointmentStatus.COMPLETED, appt.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(appt.getNotes().contains("OPI Bubble Bath"));
    }

    @Test
    void testCompleteAppointmentWithColorFormulaAndSafetyFlagsUpdatesCustomerProfile() throws Exception {
        ServiceEntity svc = new ServiceEntity("default", "Signature Gel Manicure", "Mani", new BigDecimal("45.00"), 45);
        svc.setId(108L);

        Customer cust = new Customer("Rachel Green", "rachel@example.com", "(555) 222-3333", "default");
        cust.setId(88L);

        Appointment appt = new Appointment();
        appt.setId(508L);
        appt.setTenantId("default");
        appt.setCustomerName("Rachel Green");
        appt.setCustomerEmail("rachel@example.com");
        appt.setCustomerPhone("(555) 222-3333");
        appt.setAppointmentTime(LocalDateTime.of(2026, 11, 22, 11, 0));
        appt.setService(svc);
        appt.setStatus(Appointment.AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findByTenantIdAndId("default", 508L)).thenReturn(Optional.of(appt));
        when(customerRepository.findByTenantIdAndEmail("default", "rachel@example.com")).thenReturn(Optional.of(cust));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        String formula = "OPI Bubble Bath Gel #23 with Chrome Topcoat";
        String safetyFlags = "Diabetic (requires gentle cuticle care), Latex allergy";

        mockMvc.perform(patch("/api/appointments/508/status")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"notes\":\"Great client, rebooked\",\"colorFormula\":\"" + formula + "\",\"medicalFlags\":\"" + safetyFlags + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.appointment.colorFormula").value(formula))
                .andExpect(jsonPath("$.appointment.medicalFlags").value(safetyFlags));

        // Verify customer profile was updated with color formula & medical flags
        org.junit.jupiter.api.Assertions.assertEquals(formula, cust.getColorFormula());
        org.junit.jupiter.api.Assertions.assertEquals(safetyFlags, cust.getMedicalFlags());
        verify(customerRepository).save(cust);
    }

    @Test
    void testGetAllAppointmentsEnrichesCustomerMedicalFlagsAndColorFormula() throws Exception {
        Customer cust = new Customer("Monica Geller", "monica@example.com", "(555) 333-4444", "default");
        cust.setMedicalFlags("MMA acrylic sensitivity");
        cust.setColorFormula("DND #455 French Tip");

        Appointment appt = new Appointment();
        appt.setId(509L);
        appt.setTenantId("default");
        appt.setCustomerName("Monica Geller");
        appt.setCustomerEmail("monica@example.com");
        appt.setStatus(Appointment.AppointmentStatus.CONFIRMED);
        appt.setAppointmentTime(LocalDateTime.of(2026, 11, 23, 10, 0));

        when(appointmentRepository.findAllByTenantId("default")).thenReturn(new ArrayList<>(List.of(appt)));
        when(customerRepository.findByTenantIdAndEmail("default", "monica@example.com")).thenReturn(Optional.of(cust));

        mockMvc.perform(get("/api/appointments")
                        .header("X-Tenant-ID", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].medicalFlags").value("MMA acrylic sensitivity"))
                .andExpect(jsonPath("$[0].colorFormula").value("DND #455 French Tip"));
    }

    @Test
    void testCustomerProfileSafetyAndFormulaMultiTenantIsolation() throws Exception {
        Customer jennyCust = new Customer("Sarah Connor", "sarah@example.com", "(555) 999-0001", "default");
        jennyCust.setMedicalFlags("Latex allergy");
        jennyCust.setColorFormula("OPI Bubble Bath");

        Customer luxeCust = new Customer("Sarah Connor", "sarah@example.com", "(555) 999-0002", "luxe");
        luxeCust.setMedicalFlags("Diabetic (gentle cuticle care)");
        luxeCust.setColorFormula("Royal Gold Shimmer #7");

        when(customerRepository.findByTenantIdAndEmail("default", "sarah@example.com")).thenReturn(Optional.of(jennyCust));
        when(customerRepository.findByTenantIdAndEmail("luxe", "sarah@example.com")).thenReturn(Optional.of(luxeCust));

        // When queried under default tenant
        Optional<Customer> queriedDefault = customerRepository.findByTenantIdAndEmail("default", "sarah@example.com");
        org.junit.jupiter.api.Assertions.assertTrue(queriedDefault.isPresent());
        org.junit.jupiter.api.Assertions.assertEquals("Latex allergy", queriedDefault.get().getMedicalFlags());
        org.junit.jupiter.api.Assertions.assertEquals("OPI Bubble Bath", queriedDefault.get().getColorFormula());

        // When queried under luxe tenant
        Optional<Customer> queriedLuxe = customerRepository.findByTenantIdAndEmail("luxe", "sarah@example.com");
        org.junit.jupiter.api.Assertions.assertTrue(queriedLuxe.isPresent());
        org.junit.jupiter.api.Assertions.assertEquals("Diabetic (gentle cuticle care)", queriedLuxe.get().getMedicalFlags());
        org.junit.jupiter.api.Assertions.assertEquals("Royal Gold Shimmer #7", queriedLuxe.get().getColorFormula());
    }

    @Test
    void testThreeOrMoreServicesSequentialBookingSuccessForSameTech() throws Exception {
        UserEntity tony = new UserEntity();
        tony.setId(10L);
        tony.setName("Tony");
        tony.setTenantId("default");

        ServiceEntity svc1 = new ServiceEntity("default", "Manicure", "Mani", new BigDecimal("35.00"), 45);
        svc1.setId(201L);
        ServiceEntity svc2 = new ServiceEntity("default", "Pedicure", "Pedi", new BigDecimal("45.00"), 45);
        svc2.setId(202L);
        ServiceEntity svc3 = new ServiceEntity("default", "Nail Art", "Art", new BigDecimal("25.00"), 30);
        svc3.setId(203L);

        when(userRepository.findAllByTenantIdAndRole("default", "ROLE_NAIL_TECH")).thenReturn(List.of(tony));
        when(userRepository.findByTenantIdAndId("default", 10L)).thenReturn(Optional.of(tony));
        when(serviceRepository.findByTenantIdAndId("default", 201L)).thenReturn(Optional.of(svc1));
        when(serviceRepository.findByTenantIdAndId("default", 202L)).thenReturn(Optional.of(svc2));
        when(serviceRepository.findByTenantIdAndId("default", 203L)).thenReturn(Optional.of(svc3));
        when(appointmentRepository.findByTenantIdAndAppointmentTimeBetween(
                eq("default"), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(Collections.emptyList());

        List<Appointment> savedList = new ArrayList<>();
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId((long) (900 + savedList.size()));
            savedList.add(a);
            return a;
        });

        // Booking 3 services sequentially (10:00, 10:45, 11:30) with same technician Tony
        String payload = """
                {
                  "appointmentTime": "2026-11-20T10:00:00",
                  "nailTech": { "id": 10 },
                  "customerName": "Emma Watson",
                  "customerPhone": "(555) 123-9999",
                  "customerEmail": "emma@example.com",
                  "serviceIds": [201, 202, 203],
                  "serviceTimeSlots": {
                    "201": "10:00:00",
                    "202": "10:45:00",
                    "203": "11:30:00"
                  },
                  "guests": [
                    {
                      "guestNumber": 1,
                      "customerName": "Emma Watson",
                      "customerPhone": "(555) 123-9999",
                      "customerEmail": "emma@example.com",
                      "serviceIds": [201, 202, 203],
                      "serviceStaffAssignments": {
                        "201": 10,
                        "202": 10,
                        "203": 10
                      },
                      "serviceTimeSlots": {
                        "201": "10:00:00",
                        "202": "10:45:00",
                        "203": "11:30:00"
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
                .andExpect(jsonPath("$.appointments.length()").value(3));

        org.junit.jupiter.api.Assertions.assertEquals(3, savedList.size());
        org.junit.jupiter.api.Assertions.assertEquals(10L, savedList.get(0).getNailTech().getId());
        org.junit.jupiter.api.Assertions.assertEquals(10L, savedList.get(1).getNailTech().getId());
        org.junit.jupiter.api.Assertions.assertEquals(10L, savedList.get(2).getNailTech().getId());
        org.junit.jupiter.api.Assertions.assertEquals(LocalDateTime.of(2026, 11, 20, 10, 0), savedList.get(0).getAppointmentTime());
        org.junit.jupiter.api.Assertions.assertEquals(LocalDateTime.of(2026, 11, 20, 10, 45), savedList.get(1).getAppointmentTime());
        org.junit.jupiter.api.Assertions.assertEquals(LocalDateTime.of(2026, 11, 20, 11, 30), savedList.get(2).getAppointmentTime());
    }

    @Test
    void testAnyoneAvailableWorksWhenTonyIsBookedAtTimeSlot() throws Exception {
        UserEntity tony = new UserEntity();
        tony.setId(10L);
        tony.setName("Tony");
        tony.setTenantId("default");

        UserEntity chloe = new UserEntity();
        chloe.setId(20L);
        chloe.setName("Chloe");
        chloe.setTenantId("default");

        ServiceEntity svc = new ServiceEntity("default", "Manicure", "Mani", new BigDecimal("35.00"), 45);
        svc.setId(204L);

        // Tony is already booked at 14:00
        Appointment tonyExisting = new Appointment();
        tonyExisting.setId(850L);
        tonyExisting.setTenantId("default");
        tonyExisting.setNailTech(tony);
        tonyExisting.setService(svc);
        tonyExisting.setAppointmentTime(LocalDateTime.of(2026, 11, 20, 14, 0));
        tonyExisting.setStatus(Appointment.AppointmentStatus.CONFIRMED);

        when(userRepository.findAllByTenantIdAndRole("default", "ROLE_NAIL_TECH")).thenReturn(List.of(tony, chloe));
        when(serviceRepository.findByTenantIdAndId("default", 204L)).thenReturn(Optional.of(svc));
        when(appointmentRepository.findByTenantIdAndAppointmentTimeBetween(
                eq("default"), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(List.of(tonyExisting));

        List<Appointment> savedList = new ArrayList<>();
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId((long) (950 + savedList.size()));
            savedList.add(a);
            return a;
        });

        // Customer chooses Anyone Available at 14:00 (serviceStaffAssignments has "anyone" or null)
        String payload = """
                {
                  "appointmentTime": "2026-11-20T14:00:00",
                  "nailTech": null,
                  "customerName": "Diana Prince",
                  "customerPhone": "(555) 888-9999",
                  "customerEmail": "diana@example.com",
                  "serviceIds": [204],
                  "guests": [
                    {
                      "guestNumber": 1,
                      "customerName": "Diana Prince",
                      "customerPhone": "(555) 888-9999",
                      "customerEmail": "diana@example.com",
                      "serviceIds": [204],
                      "serviceStaffAssignments": {
                        "204": "anyone"
                      }
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(1, savedList.size());
        // Verify Chloe was auto-assigned since Tony was booked
        org.junit.jupiter.api.Assertions.assertEquals(20L, savedList.get(0).getNailTech().getId());
        org.junit.jupiter.api.Assertions.assertEquals("Chloe", savedList.get(0).getNailTech().getName());
    }

    @Test
    void testStaffBookedAt1015NotAvailableAt1000AndAnyoneAvailableSucceeds() throws Exception {
        // Tony is booked at 10:15 AM for a 45-min Manicure (10:15 - 11:00 + 15m buffer -> 11:15)
        UserEntity tony = new UserEntity();
        tony.setId(7L);
        tony.setName("Tony");
        tony.setTenantId("default");

        UserEntity chloe = new UserEntity();
        chloe.setId(20L);
        chloe.setName("Chloe");
        chloe.setTenantId("default");

        ServiceEntity gelMani = new ServiceEntity("default", "Gel Manicure", "Gel", new BigDecimal("45.00"), 40);
        gelMani.setId(101L);

        Appointment tony1015Appt = new Appointment();
        tony1015Appt.setId(601L);
        tony1015Appt.setTenantId("default");
        tony1015Appt.setNailTech(tony);
        tony1015Appt.setService(gelMani);
        tony1015Appt.setAppointmentTime(LocalDateTime.of(2026, 11, 20, 10, 15));
        tony1015Appt.setStatus(Appointment.AppointmentStatus.CONFIRMED);

        when(userRepository.findByTenantIdAndId("default", 7L)).thenReturn(Optional.of(tony));
        lenient().when(userRepository.findByTenantIdAndId("default", 20L)).thenReturn(Optional.of(chloe));
        when(userRepository.findAllByTenantIdAndRole("default", "ROLE_NAIL_TECH")).thenReturn(List.of(tony, chloe));
        when(serviceRepository.findByTenantIdAndId("default", 101L)).thenReturn(Optional.of(gelMani));
        when(appointmentRepository.findByTenantIdAndAppointmentTimeBetween(
                eq("default"), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(List.of(tony1015Appt));

        // 1. Check availability for Tony: 10:00 AM MUST be unavailable because Tony has an appointment in 15 mins at 10:15 AM
        mockMvc.perform(get("/api/appointments/available-slots")
                        .header("X-Tenant-ID", "default")
                        .param("date", "2026-11-20")
                        .param("clientNow", "2026-11-20T09:00:00")
                        .param("techId", "7")
                        .param("durationMinutes", "40")
                        .param("bufferMinutes", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isClosed").value(false))
                .andExpect(jsonPath("$.availableSlots[?(@.value == '10:00:00')].available").value(false))
                .andExpect(jsonPath("$.availableSlots[?(@.value == '10:15:00')].available").value(false))
                .andExpect(jsonPath("$.availableSlots[?(@.value == '11:15:00')].available").value(true));

        // 2. Booking at 10:00 AM with "Anyone Available" must auto-assign free specialist (Chloe) and succeed
        List<Appointment> savedList = new ArrayList<>();
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId((long) (960 + savedList.size()));
            savedList.add(a);
            return a;
        });

        String payload = """
                {
                  "appointmentTime": "2026-11-20T10:00:00",
                  "nailTech": null,
                  "customerName": "Sophia Taylor",
                  "customerPhone": "(555) 321-7654",
                  "customerEmail": "sophia@example.com",
                  "serviceIds": [101],
                  "guests": [
                    {
                      "guestNumber": 1,
                      "customerName": "Sophia Taylor",
                      "customerPhone": "(555) 321-7654",
                      "customerEmail": "sophia@example.com",
                      "serviceIds": [101],
                      "serviceStaffAssignments": {
                        "101": "anyone"
                      }
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(1, savedList.size());
        org.junit.jupiter.api.Assertions.assertEquals(20L, savedList.get(0).getNailTech().getId());
        org.junit.jupiter.api.Assertions.assertEquals("Chloe", savedList.get(0).getNailTech().getName());
    }

    @Test
    void testMultiServiceTimeLapseSequencingAndBooking() throws Exception {
        // Customer selects 2 services:
        // Service 1: Gel Manicure (40 min)
        // Service 2: Spa Pedicure (35 min)
        // If Service 1 is scheduled at 10:00 AM (10:00 - 10:40 AM), Service 2 starts at 10:40 AM
        UserEntity tony = new UserEntity();
        tony.setId(7L);
        tony.setName("Tony");
        tony.setTenantId("default");

        ServiceEntity s1 = new ServiceEntity("default", "Gel Manicure", "Mani", new BigDecimal("40.00"), 40);
        s1.setId(101L);
        ServiceEntity s2 = new ServiceEntity("default", "Spa Pedicure", "Pedi", new BigDecimal("45.00"), 35);
        s2.setId(102L);

        when(userRepository.findByTenantIdAndId("default", 7L)).thenReturn(Optional.of(tony));
        when(userRepository.findAllByTenantIdAndRole("default", "ROLE_NAIL_TECH")).thenReturn(List.of(tony));
        when(serviceRepository.findByTenantIdAndId("default", 101L)).thenReturn(Optional.of(s1));
        when(serviceRepository.findByTenantIdAndId("default", 102L)).thenReturn(Optional.of(s2));
        when(appointmentRepository.findByTenantIdAndAppointmentTimeBetween(
                eq("default"), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(Collections.emptyList());

        List<Appointment> savedList = new ArrayList<>();
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId((long) (970 + savedList.size()));
            savedList.add(a);
            return a;
        });

        String payload = """
                {
                  "appointmentTime": "2026-11-20T10:00:00",
                  "nailTech": { "id": 7 },
                  "customerName": "Jessica Miller",
                  "customerPhone": "(555) 444-5555",
                  "customerEmail": "jessica@example.com",
                  "serviceIds": [101, 102],
                  "serviceTimeSlots": {
                    "101": "10:00:00",
                    "102": "10:40:00"
                  },
                  "guests": [
                    {
                      "guestNumber": 1,
                      "customerName": "Jessica Miller",
                      "customerPhone": "(555) 444-5555",
                      "customerEmail": "jessica@example.com",
                      "serviceIds": [101, 102],
                      "serviceStaffAssignments": {
                        "101": 7,
                        "102": 7
                      },
                      "serviceTimeSlots": {
                        "101": "10:00:00",
                        "102": "10:40:00"
                      }
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(2, savedList.size());
        org.junit.jupiter.api.Assertions.assertEquals(LocalDateTime.of(2026, 11, 20, 10, 0), savedList.get(0).getAppointmentTime());
        org.junit.jupiter.api.Assertions.assertEquals(LocalDateTime.of(2026, 11, 20, 10, 40), savedList.get(1).getAppointmentTime());
    }
}
