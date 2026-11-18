package com.example.demo.controller;

import com.example.demo.repository.AppointmentRepository;
import com.example.demo.repository.AppointmentTransactionRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.GiftCardRepository;
import com.example.demo.repository.TenantConfigRepository;
import com.example.demo.service.MarketingAutomationService;
import com.example.demo.service.TenantContextResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MarketingControllerTest {

    @Mock
    private TenantConfigRepository tenantConfigRepository;

    private StubMarketingAutomationService marketingService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    static class StubMarketingAutomationService extends MarketingAutomationService {
        Map<String, Object> summaryResult;
        List<Map<String, Object>> birthdaysResult;
        Map<String, Object> dispatchBirthdayResult;
        List<Map<String, Object>> lapsedResult;
        Map<String, Object> dispatchWinBackResult;

        public StubMarketingAutomationService() {
            super(
                    mock(CustomerRepository.class),
                    mock(AppointmentRepository.class),
                    mock(AppointmentTransactionRepository.class),
                    mock(GiftCardRepository.class),
                    null,
                    null
            );
        }

        @Override
        public Map<String, Object> getMarketingSummary(String tenantId) {
            return summaryResult;
        }

        @Override
        public List<Map<String, Object>> getBirthdayCustomers(String tenantId, Integer month) {
            return birthdaysResult;
        }

        @Override
        public Map<String, Object> dispatchBirthdayPerks(String tenantId, String perkType, Integer points, BigDecimal voucherAmount, Long customerId) {
            return dispatchBirthdayResult;
        }

        @Override
        public List<Map<String, Object>> getLapsedCustomers(String tenantId, int daysInactive) {
            return lapsedResult;
        }

        @Override
        public Map<String, Object> dispatchWinBackCampaign(String tenantId, int daysInactive, List<Long> customerIds) {
            return dispatchWinBackResult;
        }
    }

    @BeforeEach
    void setUp() {
        marketingService = new StubMarketingAutomationService();
        TenantContextResolver tenantContextResolver = new TenantContextResolver(tenantConfigRepository);
        MarketingController controller = new MarketingController(marketingService, tenantContextResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testGetMarketingSummary() throws Exception {
        marketingService.summaryResult = Map.of(
                "totalCustomers", 25,
                "birthdaysThisMonth", 3,
                "lapsedCustomersCount", 4
        );

        mockMvc.perform(get("/api/admin/marketing/summary")
                        .header("X-Tenant-ID", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCustomers").value(25))
                .andExpect(jsonPath("$.birthdaysThisMonth").value(3))
                .andExpect(jsonPath("$.lapsedCustomersCount").value(4));
    }

    @Test
    void testGetBirthdays() throws Exception {
        marketingService.birthdaysResult = List.of(
                Map.of("id", 1L, "name", "Sophia", "dayOfMonth", 18, "awardedThisYear", false)
        );

        mockMvc.perform(get("/api/admin/marketing/birthdays")
                        .header("X-Tenant-ID", "default")
                        .param("month", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Sophia"))
                .andExpect(jsonPath("$[0].dayOfMonth").value(18));
    }

    @Test
    void testDispatchBirthdayPerks() throws Exception {
        marketingService.dispatchBirthdayResult = Map.of("awardedCount", 2, "message", "Awarded!");

        mockMvc.perform(post("/api/admin/marketing/birthdays/dispatch")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("perkType", "POINTS", "pointsAward", 100))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.awardedCount").value(2))
                .andExpect(jsonPath("$.message").value("Awarded!"));
    }

    @Test
    void testGetLapsed() throws Exception {
        marketingService.lapsedResult = List.of(
                Map.of("id", 2L, "name", "Chloe", "daysInactive", 35)
        );

        mockMvc.perform(get("/api/admin/marketing/lapsed")
                        .header("X-Tenant-ID", "default")
                        .param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Chloe"))
                .andExpect(jsonPath("$[0].daysInactive").value(35));
    }

    @Test
    void testDispatchWinBack() throws Exception {
        marketingService.dispatchWinBackResult = Map.of("dispatchedCount", 3, "message", "Dispatched!");

        mockMvc.perform(post("/api/admin/marketing/win-back/dispatch")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("daysThreshold", 30))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispatchedCount").value(3));
    }
}
