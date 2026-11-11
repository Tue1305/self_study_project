package com.example.demo.controller;

import com.example.demo.dto.TenantConfigDto;
import com.example.demo.entity.TenantConfig;
import com.example.demo.repository.TenantConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TenantConfigControllerTest {

    @Mock
    private TenantConfigRepository tenantConfigRepository;

    private TenantConfigController controller;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        controller = new TenantConfigController(tenantConfigRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void publicEndpointResolvesByHeaderAndEnforcesCustomerPrivacy() throws Exception {
        TenantConfig luxe = new TenantConfig();
        luxe.setTenantKey("luxe");
        luxe.setSalonName("Luxe Nail & Spa");
        luxe.setPrimaryColor("#6f42c1");
        luxe.setEnableStationSelection(true);
        luxe.setStepOrder("SERVICE,STAFF,STATION,DETAILS");
        luxe.setShowStaffQualificationTags(true); // Even if true in DB, public DTO must force false

        when(tenantConfigRepository.findByTenantKeyIgnoreCase("luxe")).thenReturn(Optional.of(luxe));

        mockMvc.perform(get("/api/public/tenant-config").header("X-Tenant-ID", "luxe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantKey").value("luxe"))
                .andExpect(jsonPath("$.salonName").value("Luxe Nail & Spa"))
                .andExpect(jsonPath("$.theme.primaryColor").value("#6f42c1"))
                .andExpect(jsonPath("$.wizard.enableStationSelection").value(true))
                .andExpect(jsonPath("$.wizard.stepOrder[2]").value("STATION"))
                .andExpect(jsonPath("$.wizard.showStaffQualificationTags").value(false));
    }

    @Test
    void publicEndpointOmitsStationStepWhenDisabledAndResolvesSubdomain() throws Exception {
        TenantConfig spa = new TenantConfig();
        spa.setTenantKey("zen");
        spa.setSalonName("Zen Nails");
        spa.setEnableStationSelection(false);
        spa.setStepOrder("SERVICE,STAFF,STATION,DETAILS");

        when(tenantConfigRepository.findByCustomDomainIgnoreCase("zen.example.com")).thenReturn(Optional.empty());
        when(tenantConfigRepository.findByTenantKeyIgnoreCase("zen")).thenReturn(Optional.of(spa));

        mockMvc.perform(get("/api/public/tenant-config").header("Host", "zen.example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantKey").value("zen"))
                .andExpect(jsonPath("$.wizard.enableStationSelection").value(false))
                .andExpect(jsonPath("$.wizard.stepOrder.length()").value(3))
                .andExpect(jsonPath("$.wizard.stepOrder[2]").value("DETAILS"));
    }

    @Test
    void adminUpdateRejectsMissingRequiredCoreStep() {
        TenantConfig existing = new TenantConfig();
        existing.setTenantKey("luxe");
        when(tenantConfigRepository.findByTenantKeyIgnoreCase("luxe")).thenReturn(Optional.of(existing));

        TenantConfigDto payload = new TenantConfigDto();
        TenantConfigDto.WizardLayoutConfig wizard = new TenantConfigDto.WizardLayoutConfig();
        wizard.setStepOrder(List.of("SERVICE", "DETAILS")); // Missing mandatory STAFF step
        payload.setWizard(wizard);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.upsertTenantConfig("luxe", payload));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void adminUpdateAcceptsValidStepsAndForceDisablesStaffTags() throws Exception {
        TenantConfig existing = new TenantConfig();
        existing.setTenantKey("luxe");
        when(tenantConfigRepository.findByTenantKeyIgnoreCase("luxe")).thenReturn(Optional.of(existing));
        when(tenantConfigRepository.save(any(TenantConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantConfigDto payload = new TenantConfigDto();
        payload.setSalonName("Luxe Updated");
        payload.setLayoutVariant("luxe");
        TenantConfigDto.ThemeConfig theme = new TenantConfigDto.ThemeConfig("#112233", "#445566", "'Inter', sans-serif", "/logo.png", null);
        TenantConfigDto.WizardLayoutConfig wizard = new TenantConfigDto.WizardLayoutConfig(
                List.of("STAFF", "SERVICE", "STATION", "DETAILS"),
                true,
                List.of("Suite A", "Suite B"),
                List.of("Manicures", "Pedicures"),
                true,
                true
        );
        payload.setTheme(theme);
        payload.setWizard(wizard);

        mockMvc.perform(put("/api/admin/tenant-config/luxe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salonName").value("Luxe Updated"))
                .andExpect(jsonPath("$.wizard.stepOrder[0]").value("STAFF"))
                .andExpect(jsonPath("$.wizard.stepOrder[2]").value("STATION"))
                .andExpect(jsonPath("$.wizard.showStaffQualificationTags").value(false));
    }

    @Test
    void publicEndpointStrictModeReturnsNotFoundForUnknownSubdomain() throws Exception {
        when(tenantConfigRepository.findByCustomDomainIgnoreCase("unknown.localhost")).thenReturn(Optional.empty());
        when(tenantConfigRepository.findByTenantKeyIgnoreCase("unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/public/tenant-config")
                        .param("domain", "unknown.localhost")
                        .param("strict", "true"))
                .andExpect(status().isNotFound());
    }
}
