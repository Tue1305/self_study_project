package com.example.demo.controller;

import com.example.demo.entity.Appointment;
import com.example.demo.entity.ServiceEntity;
import com.example.demo.entity.UserEntity;
import com.example.demo.repository.AppointmentRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.ServiceRepository;
import com.example.demo.repository.TenantConfigRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.TenantContextResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MultiTenantIsolationTest {

    @Mock
    private ServiceRepository serviceRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TenantConfigRepository tenantConfigRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private MockMvc serviceMockMvc;
    private MockMvc nailTechMockMvc;
    private MockMvc appointmentMockMvc;

    @BeforeEach
    void setUp() {
        TenantContextResolver resolver = new TenantContextResolver(tenantConfigRepository);
        ServiceController serviceController = new ServiceController(serviceRepository, resolver);
        NailTechController nailTechController = new NailTechController(userRepository, serviceRepository, passwordEncoder, resolver);
        AppointmentController appointmentController = new AppointmentController(
                appointmentRepository, customerRepository, serviceRepository, userRepository, resolver
        );

        serviceMockMvc = MockMvcBuilders.standaloneSetup(serviceController).build();
        nailTechMockMvc = MockMvcBuilders.standaloneSetup(nailTechController).build();
        appointmentMockMvc = MockMvcBuilders.standaloneSetup(appointmentController).build();
    }

    @Test
    void servicesAreStrictlyIsolatedByTenantHeader() throws Exception {
        ServiceEntity defaultSvc = new ServiceEntity("default", "Express Manicure", "Jenny's classic", new BigDecimal("23.00"), 25);
        defaultSvc.setId(1L);
        ServiceEntity luxeSvc = new ServiceEntity("luxe", "Luxe Signature Gel Manicure", "Luxe organic", new BigDecimal("48.00"), 40);
        luxeSvc.setId(101L);

        when(serviceRepository.findAllByTenantId("default")).thenReturn(List.of(defaultSvc));
        when(serviceRepository.findAllByTenantId("luxe")).thenReturn(List.of(luxeSvc));

        serviceMockMvc.perform(get("/api/services").header("X-Tenant-ID", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Express Manicure"))
                .andExpect(jsonPath("$[0].tenantId").value("default"));

        serviceMockMvc.perform(get("/api/services").header("X-Tenant-ID", "luxe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Luxe Signature Gel Manicure"))
                .andExpect(jsonPath("$[0].tenantId").value("luxe"));
    }

    @Test
    void nailTechsAreStrictlyIsolatedByTenant() throws Exception {
        UserEntity tony = new UserEntity("tony@jennysnailstudio.com", "pw", "ROLE_NAIL_TECH");
        tony.setId(10L);
        tony.setName("Tony");
        tony.setTenantId("default");
        tony.setSpecialty("ALL");

        UserEntity sophia = new UserEntity("sophia@luxenailspa.com", "pw", "ROLE_NAIL_TECH");
        sophia.setId(20L);
        sophia.setName("Sophia Laurent");
        sophia.setTenantId("luxe");
        sophia.setSpecialty("ALL");

        when(userRepository.findAllByTenantIdAndRole("default", "ROLE_NAIL_TECH")).thenReturn(List.of(tony));
        when(userRepository.findAllByTenantIdAndRole("luxe", "ROLE_NAIL_TECH")).thenReturn(List.of(sophia));

        nailTechMockMvc.perform(get("/api/nail-techs").header("X-Tenant-ID", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Tony"))
                .andExpect(jsonPath("$[0].tenantId").value("default"));

        nailTechMockMvc.perform(get("/api/nail-techs").header("X-Tenant-ID", "luxe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Sophia Laurent"))
                .andExpect(jsonPath("$[0].tenantId").value("luxe"));
    }

    @Test
    void bookingConflictChecksOnlyWithinActiveTenantScope() throws Exception {
        ServiceEntity luxeSvc = new ServiceEntity("luxe", "Royal Lavender Spa Pedicure", "Spa", new BigDecimal("72.00"), 55);
        luxeSvc.setId(101L);

        UserEntity sophia = new UserEntity("sophia@luxenailspa.com", "pw", "ROLE_NAIL_TECH");
        sophia.setId(20L);
        sophia.setName("Sophia Laurent");
        sophia.setTenantId("luxe");

        LocalDateTime slot = LocalDateTime.of(2026, 11, 20, 14, 0, 0);

        when(serviceRepository.findByTenantIdAndId("luxe", 101L)).thenReturn(Optional.of(luxeSvc));
        when(userRepository.findByTenantIdAndId("luxe", 20L)).thenReturn(Optional.of(sophia));
        when(appointmentRepository.existsByTenantIdAndNailTechIdAndAppointmentTimeAndStatusNot(
                "luxe", 20L, slot, Appointment.AppointmentStatus.CANCELLED
        )).thenReturn(false).thenReturn(true);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        String body = """
                {
                  "customerName": "Clara Test",
                  "customerEmail": "clara@example.com",
                  "customerPhone": "555-0199",
                  "service": { "id": 101 },
                  "nailTech": { "id": 20 },
                  "appointmentTime": "2026-11-20T14:00:00"
                }
                """;

        // 1st booking in 'luxe' succeeds
        appointmentMockMvc.perform(post("/api/bookings")
                        .header("X-Tenant-ID", "luxe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("luxe"));

        // 2nd booking at same slot in 'luxe' returns 409 CONFLICT
        appointmentMockMvc.perform(post("/api/bookings")
                        .header("X-Tenant-ID", "luxe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void crossTenantServiceBookingIsRejected() throws Exception {
        when(serviceRepository.findByTenantIdAndId("luxe", 1L)).thenReturn(Optional.empty());

        String body = """
                {
                  "customerName": "Cross Tenant",
                  "customerEmail": "cross@example.com",
                  "customerPhone": "555-0100",
                  "service": { "id": 1 },
                  "appointmentTime": "2026-11-20T14:00:00"
                }
                """;

        appointmentMockMvc.perform(post("/api/bookings")
                        .header("X-Tenant-ID", "luxe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
