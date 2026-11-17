package com.example.demo.controller;

import com.example.demo.entity.Customer;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.TenantConfigRepository;
import com.example.demo.service.CustomerService;
import com.example.demo.service.TenantContextResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CustomerTenantIsolationTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private TenantConfigRepository tenantConfigRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TenantContextResolver resolver = new TenantContextResolver(tenantConfigRepository);
        CustomerService customerService = new CustomerService(customerRepository);

        CustomerController controller = new CustomerController();
        ReflectionTestUtils.setField(controller, "customerService", customerService);
        ReflectionTestUtils.setField(controller, "customerRepository", customerRepository);
        ReflectionTestUtils.setField(controller, "tenantContextResolver", resolver);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("Tenant Isolation: Can register same customer email under Luxe even if already registered in Jenny's")
    void testCreateCustomerAllowedAcrossDifferentTenants() throws Exception {
        // Customer exists under 'default' (Jenny's)
        Customer jennyCustomer = new Customer("Alice Jenny", "alice@example.com", "(555) 111-2222", "default");
        jennyCustomer.setId(1L);

        // Luxe tenant does not have alice@example.com yet
        when(customerRepository.findByTenantIdAndEmail("luxe", "alice@example.com")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(2L);
            return c;
        });

        String payload = """
                {
                    "name": "Alice Luxe",
                    "email": "alice@example.com",
                    "phone": "(555) 999-8888"
                }
                """;

        mockMvc.perform(post("/api/customers")
                        .header("X-Tenant-ID", "luxe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.tenantId").value("luxe"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    @DisplayName("Tenant Scope: Rejects duplicate email within the same tenant")
    void testCreateCustomerDuplicateRejectedWithinSameTenant() throws Exception {
        Customer existingInLuxe = new Customer("Alice Existing", "alice@example.com", "(555) 333-4444", "luxe");
        existingInLuxe.setId(10L);

        when(customerRepository.findByTenantIdAndEmail("luxe", "alice@example.com")).thenReturn(Optional.of(existingInLuxe));

        String payload = """
                {
                    "name": "Alice Duplicate",
                    "email": "alice@example.com",
                    "phone": "(555) 999-8888"
                }
                """;

        mockMvc.perform(post("/api/customers")
                        .header("X-Tenant-ID", "luxe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A customer with this email already exists in this salon."));
    }

    @Test
    @DisplayName("Tenant Filter: Returns only the active tenant's customers")
    void testGetAllCustomersScopedToTenant() throws Exception {
        Customer luxeCust1 = new Customer("Luxe Client 1", "client1@luxe.com", "555-01", "luxe");
        Customer luxeCust2 = new Customer("Luxe Client 2", "client2@luxe.com", "555-02", "luxe");

        when(customerRepository.findByTenantId("luxe")).thenReturn(List.of(luxeCust1, luxeCust2));

        mockMvc.perform(get("/api/customers")
                        .header("X-Tenant-ID", "luxe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Luxe Client 1"))
                .andExpect(jsonPath("$[1].name").value("Luxe Client 2"));
    }
}
