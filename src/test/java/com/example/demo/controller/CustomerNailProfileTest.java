package com.example.demo.controller;

import com.example.demo.entity.Customer;
import com.example.demo.entity.CustomerNailNote;
import com.example.demo.repository.AppointmentActivityLogRepository;
import com.example.demo.repository.AppointmentRepository;
import com.example.demo.repository.CustomerNailNoteRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.TenantConfigRepository;
import com.example.demo.service.AppointmentAuditService;
import com.example.demo.service.TenantContextResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CustomerNailProfileTest {

    @Mock
    private AppointmentActivityLogRepository activityLogRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private TenantConfigRepository tenantConfigRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private CustomerNailNoteRepository nailNoteRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TenantContextResolver resolver = new TenantContextResolver(tenantConfigRepository);
        AppointmentAuditService auditService = new AppointmentAuditService(
                activityLogRepository, appointmentRepository, customerRepository, nailNoteRepository
        );
        AdminCustomerController controller = new AdminCustomerController(
                auditService, resolver, customerRepository, nailNoteRepository
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testAddCustomerNailNoteSuccess() throws Exception {
        Customer customer = new Customer("Jessica Alba", "jessica@example.com", "555-4321", "default");
        customer.setId(5L);
        when(customerRepository.findById(5L)).thenReturn(Optional.of(customer));

        CustomerNailNote saved = new CustomerNailNote(
                "default", 5L, "Chloe Vance", LocalDate.now(),
                "Almond", "Medium", "Gel-X",
                "OPI Bubble Bath #T80", "Client requested soft apex, very happy with length.", "https://example.com/nails1.jpg"
        );
        saved.setId(101L);

        when(nailNoteRepository.save(any(CustomerNailNote.class))).thenReturn(saved);

        mockMvc.perform(post("/api/admin/customers/5/nail-notes")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "technicianName": "Chloe Vance",
                                    "nailShape": "Almond",
                                    "nailLength": "Medium",
                                    "nailSystem": "Gel-X",
                                    "colorDetails": "OPI Bubble Bath #T80",
                                    "notes": "Client requested soft apex, very happy with length.",
                                    "photoUrl": "https://example.com/nails1.jpg"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.nailShape").value("Almond"))
                .andExpect(jsonPath("$.nailSystem").value("Gel-X"))
                .andExpect(jsonPath("$.colorDetails").value("OPI Bubble Bath #T80"))
                .andExpect(jsonPath("$.technicianName").value("Chloe Vance"));
    }

    @Test
    void testGetCustomerNailNotes() throws Exception {
        CustomerNailNote note = new CustomerNailNote(
                "default", 5L, "Tony Nguyen", LocalDate.now().minusWeeks(2),
                "Coffin", "Long", "Bio Gel",
                "DND #452 Cherry Mocha", "Chrome glaze topcoat applied", "https://example.com/nails2.jpg"
        );
        note.setId(102L);

        when(nailNoteRepository.findAllByTenantIdAndCustomerIdOrderByServiceDateDescCreatedAtDesc("default", 5L))
                .thenReturn(List.of(note));

        mockMvc.perform(get("/api/admin/customers/5/nail-notes")
                        .header("X-Tenant-ID", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nailShape").value("Coffin"))
                .andExpect(jsonPath("$[0].colorDetails").value("DND #452 Cherry Mocha"));
    }

    @Test
    void testUpdateNailProfilePreferences() throws Exception {
        Customer customer = new Customer("Jessica Alba", "jessica@example.com", "555-4321", "default");
        customer.setId(5L);
        when(customerRepository.findById(5L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        mockMvc.perform(put("/api/admin/customers/5/nail-profile")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "nailShape": "Stiletto",
                                    "nailLength": "Long",
                                    "nailSystem": "Acrylic",
                                    "favoriteColors": "French Ombre, Hailey Bieber Glazed Donut",
                                    "cuticleCareNotes": "Sensitive cuticles, use glass buffer only"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nailShape").value("Stiletto"))
                .andExpect(jsonPath("$.nailLength").value("Long"))
                .andExpect(jsonPath("$.nailSystem").value("Acrylic"))
                .andExpect(jsonPath("$.favoriteColors").value("French Ombre, Hailey Bieber Glazed Donut"))
                .andExpect(jsonPath("$.cuticleCareNotes").value("Sensitive cuticles, use glass buffer only"));
    }

    @Test
    void testDeleteCustomerNailNote() throws Exception {
        CustomerNailNote note = new CustomerNailNote();
        note.setId(102L);
        note.setTenantId("default");
        note.setCustomerId(5L);

        when(nailNoteRepository.findByIdAndTenantId(102L, "default")).thenReturn(Optional.of(note));

        mockMvc.perform(delete("/api/admin/customers/5/nail-notes/102")
                        .header("X-Tenant-ID", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Nail note deleted successfully."));

        verify(nailNoteRepository).delete(note);
    }
}
