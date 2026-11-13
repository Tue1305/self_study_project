package com.example.demo.controller;

import com.example.demo.entity.Appointment;
import com.example.demo.entity.AppointmentActivityLog;
import com.example.demo.entity.Customer;
import com.example.demo.entity.ServiceEntity;
import com.example.demo.repository.AppointmentActivityLogRepository;
import com.example.demo.repository.AppointmentRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.TenantConfigRepository;
import com.example.demo.service.AppointmentAuditService;
import com.example.demo.service.TenantContextResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CustomerAuditHistoryTest {

    @Mock
    private AppointmentActivityLogRepository activityLogRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private TenantConfigRepository tenantConfigRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TenantContextResolver resolver = new TenantContextResolver(tenantConfigRepository);
        AppointmentAuditService auditService = new AppointmentAuditService(
                activityLogRepository, appointmentRepository, customerRepository
        );
        AdminCustomerController controller = new AdminCustomerController(auditService, resolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getCustomerHistoryReturnsReliabilityAndLogs() throws Exception {
        Customer customer = new Customer("Alice Smith", "alice@example.com", "555-1234", "default");
        customer.setId(10L);

        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));

        ServiceEntity svc = new ServiceEntity("default", "Gel Manicure", "Gel nails", new BigDecimal("45.00"), 45);
        Appointment apt1 = new Appointment();
        apt1.setId(101L);
        apt1.setTenantId("default");
        apt1.setCustomerEmail("alice@example.com");
        apt1.setCustomerName("Alice Smith");
        apt1.setAppointmentTime(LocalDateTime.now().minusDays(3));
        apt1.setStatus(Appointment.AppointmentStatus.COMPLETED);
        apt1.setService(svc);

        Appointment apt2 = new Appointment();
        apt2.setId(102L);
        apt2.setTenantId("default");
        apt2.setCustomerEmail("alice@example.com");
        apt2.setCustomerName("Alice Smith");
        apt2.setAppointmentTime(LocalDateTime.now().minusDays(1));
        apt2.setStatus(Appointment.AppointmentStatus.NO_SHOW);
        apt2.setService(svc);

        when(appointmentRepository.findAllByTenantId("default")).thenReturn(List.of(apt1, apt2));

        AppointmentActivityLog log1 = new AppointmentActivityLog(
                "default", 10L, 102L, "Alice Smith", "alice@example.com", "555-1234",
                "NO_SHOW", "ADMIN: Jenny", "Customer did not arrive"
        );
        log1.setId(501L);

        when(activityLogRepository.findByTenantIdAndCustomerEmailIgnoreCaseOrderByCreatedAtDesc("default", "alice@example.com"))
                .thenReturn(List.of(log1));
        when(activityLogRepository.findByTenantIdAndCustomerIdOrderByCreatedAtDesc("default", 10L))
                .thenReturn(List.of(log1));

        mockMvc.perform(get("/api/admin/customers/10/history")
                        .header("X-Tenant-ID", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.id").value(10))
                .andExpect(jsonPath("$.customer.email").value("alice@example.com"))
                .andExpect(jsonPath("$.reliability.totalBooked").value(2))
                .andExpect(jsonPath("$.reliability.totalCompleted").value(1))
                .andExpect(jsonPath("$.reliability.totalNoShows").value(1))
                .andExpect(jsonPath("$.reliability.hasHighNoShowRisk").value(false))
                .andExpect(jsonPath("$.appointments[0].id").value(102))
                .andExpect(jsonPath("$.activityLogs[0].actionType").value("NO_SHOW"))
                .andExpect(jsonPath("$.activityLogs[0].performedBy").value("ADMIN: Jenny"))
                .andExpect(jsonPath("$.activityLogs[0].notes").value("Customer did not arrive"));
    }

    @Test
    void getCustomerHistoryReturns404WhenNotFound() throws Exception {
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/customers/999/history")
                        .header("X-Tenant-ID", "default"))
                .andExpect(status().isNotFound());
    }
}
