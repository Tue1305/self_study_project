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
import com.example.demo.service.TenantContext;
import com.example.demo.service.TenantContextResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AppointmentStaffReassignmentTest {

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
        TenantContext.clear();
        TenantContextResolver resolver = new TenantContextResolver(tenantConfigRepository);
        AppointmentAuditService auditService = new AppointmentAuditService(
                activityLogRepository, appointmentRepository, customerRepository
        );
        NotificationService notificationService = new NotificationService(tenantConfigRepository, null);
        AppointmentController controller = new AppointmentController(
                appointmentRepository, customerRepository, serviceRepository, userRepository, resolver,
                auditService, notificationService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Admin can reassign an appointment to any staff member via /assign-tech")
    void testAssignTechnicianSuccess() throws Exception {
        ServiceEntity svc = new ServiceEntity("default", "Classic Manicure", "Standard", new BigDecimal("25.00"), 45);
        svc.setId(1L);

        UserEntity techTony = new UserEntity("tony", "pass", "ROLE_NAIL_TECH");
        techTony.setTenantId("default");
        techTony.setId(20L);
        techTony.setName("Tony Nguyen");

        Appointment appt = new Appointment();
        appt.setId(100L);
        appt.setTenantId("default");
        appt.setCustomerName("Sophia Miller");
        appt.setCustomerEmail("sophia@example.com");
        appt.setAppointmentTime(LocalDateTime.of(2026, 11, 20, 14, 0));
        appt.setService(svc);
        appt.setStatus(Appointment.AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findByTenantIdAndId("default", 100L)).thenReturn(Optional.of(appt));
        when(userRepository.findByTenantIdAndId("default", 20L)).thenReturn(Optional.of(techTony));
        when(appointmentRepository.findByTenantIdAndAppointmentTimeBetween(eq("default"), any(), any()))
                .thenReturn(List.of(appt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(patch("/api/appointments/100/assign-tech")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"techId\": 20, \"reason\": \"Reassigned to Tony via calendar drag & drop\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Appointment reassigned successfully to Tony Nguyen"))
                .andExpect(jsonPath("$.appointment.nailTech.id").value(20));
    }

    @Test
    @DisplayName("Admin receives 409 Conflict when reassigning to a technician who is already booked")
    void testAssignTechnicianConflict() throws Exception {
        ServiceEntity svc = new ServiceEntity("default", "Classic Manicure", "Standard", new BigDecimal("25.00"), 45);
        svc.setId(1L);

        UserEntity techTony = new UserEntity("tony", "pass", "ROLE_NAIL_TECH");
        techTony.setTenantId("default");
        techTony.setId(20L);
        techTony.setName("Tony Nguyen");

        LocalDateTime timeSlot = LocalDateTime.of(2026, 11, 20, 14, 0);

        Appointment appt1 = new Appointment();
        appt1.setId(100L);
        appt1.setTenantId("default");
        appt1.setCustomerName("Sophia Miller");
        appt1.setAppointmentTime(timeSlot);
        appt1.setService(svc);
        appt1.setStatus(Appointment.AppointmentStatus.CONFIRMED);

        // Tony already has an overlapping appointment at 14:15
        Appointment existingTonyAppt = new Appointment();
        existingTonyAppt.setId(101L);
        existingTonyAppt.setTenantId("default");
        existingTonyAppt.setCustomerName("David Clark");
        existingTonyAppt.setAppointmentTime(LocalDateTime.of(2026, 11, 20, 14, 15));
        existingTonyAppt.setService(svc);
        existingTonyAppt.setNailTech(techTony);
        existingTonyAppt.setStatus(Appointment.AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findByTenantIdAndId("default", 100L)).thenReturn(Optional.of(appt1));
        when(userRepository.findByTenantIdAndId("default", 20L)).thenReturn(Optional.of(techTony));
        when(appointmentRepository.findByTenantIdAndAppointmentTimeBetween(eq("default"), any(), any()))
                .thenReturn(List.of(appt1, existingTonyAppt));

        mockMvc.perform(patch("/api/appointments/100/assign-tech")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"techId\": 20}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Tony Nguyen is already booked at this time slot."));
    }

    @Test
    @DisplayName("Admin can unassign a technician to 'Anyone Available' queue")
    void testUnassignTechnicianToOpenQueue() throws Exception {
        ServiceEntity svc = new ServiceEntity("default", "Classic Manicure", "Standard", new BigDecimal("25.00"), 45);
        svc.setId(1L);

        UserEntity techTony = new UserEntity("tony", "pass", "ROLE_NAIL_TECH");
        techTony.setTenantId("default");
        techTony.setId(20L);
        techTony.setName("Tony Nguyen");

        Appointment appt = new Appointment();
        appt.setId(100L);
        appt.setTenantId("default");
        appt.setCustomerName("Sophia Miller");
        appt.setAppointmentTime(LocalDateTime.of(2026, 11, 20, 14, 0));
        appt.setService(svc);
        appt.setNailTech(techTony);
        appt.setStatus(Appointment.AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findByTenantIdAndId("default", 100L)).thenReturn(Optional.of(appt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(patch("/api/appointments/100/assign-tech")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"techId\": null, \"reason\": \"Moved to unassigned queue\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Appointment reassigned successfully to Anyone Available"))
                .andExpect(jsonPath("$.appointment.nailTech").doesNotExist());
    }
}
