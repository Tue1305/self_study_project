package com.example.demo.controller;

import com.example.demo.entity.GiftCard;
import com.example.demo.repository.GiftCardRepository;
import com.example.demo.repository.TenantConfigRepository;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GiftCardControllerTest {

    @Mock
    private GiftCardRepository giftCardRepository;

    @Mock
    private TenantConfigRepository tenantConfigRepository;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        TenantContextResolver tenantContextResolver = new TenantContextResolver(tenantConfigRepository);
        GiftCardController controller = new GiftCardController(giftCardRepository, tenantContextResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testIssueGiftCardSuccess() throws Exception {
        when(giftCardRepository.existsByTenantIdAndCodeIgnoreCase(eq("default"), any())).thenReturn(false);
        when(giftCardRepository.save(any(GiftCard.class))).thenAnswer(inv -> {
            GiftCard gc = inv.getArgument(0);
            gc.setId(10L);
            return gc;
        });

        Map<String, Object> body = Map.of(
                "initialBalance", "50.00",
                "purchaserName", "Alice",
                "recipientName", "Bob",
                "notes", "Happy Birthday"
        );

        mockMvc.perform(post("/api/gift-cards")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Gift card issued successfully."))
                .andExpect(jsonPath("$.giftCard.initialBalance").value(50.00))
                .andExpect(jsonPath("$.giftCard.currentBalance").value(50.00))
                .andExpect(jsonPath("$.giftCard.purchaserName").value("Alice"))
                .andExpect(jsonPath("$.giftCard.recipientName").value("Bob"));

        verify(giftCardRepository).save(any(GiftCard.class));
    }

    @Test
    void testIssueGiftCardWithCustomCode() throws Exception {
        when(giftCardRepository.existsByTenantIdAndCodeIgnoreCase("default", "GC-SPECIAL-100")).thenReturn(false);
        when(giftCardRepository.save(any(GiftCard.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = Map.of(
                "amount", "100.00",
                "code", "GC-SPECIAL-100",
                "recipientName", "VIP Customer"
        );

        mockMvc.perform(post("/api/gift-cards")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.giftCard.code").value("GC-SPECIAL-100"))
                .andExpect(jsonPath("$.giftCard.currentBalance").value(100.00));
    }

    @Test
    void testIssueGiftCardDuplicateCodeReturnsConflict() throws Exception {
        when(giftCardRepository.existsByTenantIdAndCodeIgnoreCase("default", "GC-DUPLICATE")).thenReturn(true);

        Map<String, Object> body = Map.of(
                "initialBalance", "25.00",
                "code", "GC-DUPLICATE"
        );

        mockMvc.perform(post("/api/gift-cards")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A gift card with code 'GC-DUPLICATE' already exists in this salon."));
    }

    @Test
    void testVerifyGiftCardValid() throws Exception {
        GiftCard gc = new GiftCard("default", "GC-VALID-50", new BigDecimal("50.00"));
        gc.setStatus("ACTIVE");

        when(giftCardRepository.findByTenantIdAndCodeIgnoreCase("default", "GC-VALID-50")).thenReturn(Optional.of(gc));

        mockMvc.perform(get("/api/gift-cards/verify")
                        .header("X-Tenant-ID", "default")
                        .param("code", "GC-VALID-50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.code").value("GC-VALID-50"))
                .andExpect(jsonPath("$.currentBalance").value(50.00))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void testVerifyGiftCardNotFound() throws Exception {
        when(giftCardRepository.findByTenantIdAndCodeIgnoreCase("default", "NON-EXISTENT")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/gift-cards/verify")
                        .header("X-Tenant-ID", "default")
                        .param("code", "NON-EXISTENT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    void testVerifyGiftCardExpiredTransitionsToStatusExpired() throws Exception {
        GiftCard gc = new GiftCard("default", "GC-OLD", new BigDecimal("30.00"));
        gc.setStatus("ACTIVE");
        gc.setExpiresAt(LocalDateTime.now().minusDays(1));

        when(giftCardRepository.findByTenantIdAndCodeIgnoreCase("default", "GC-OLD")).thenReturn(Optional.of(gc));
        when(giftCardRepository.save(any(GiftCard.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(get("/api/gift-cards/verify")
                        .header("X-Tenant-ID", "default")
                        .param("code", "GC-OLD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.status").value("EXPIRED"));

        verify(giftCardRepository).save(gc);
    }

    @Test
    void testListGiftCards() throws Exception {
        GiftCard gc1 = new GiftCard("default", "GC-1", new BigDecimal("50.00"));
        GiftCard gc2 = new GiftCard("default", "GC-2", new BigDecimal("75.00"));
        when(giftCardRepository.findAllByTenantIdOrderByCreatedAtDesc("default")).thenReturn(List.of(gc1, gc2));

        mockMvc.perform(get("/api/gift-cards")
                        .header("X-Tenant-ID", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("GC-1"))
                .andExpect(jsonPath("$[1].code").value("GC-2"));
    }

    @Test
    void testUpdateStatus() throws Exception {
        GiftCard gc = new GiftCard("default", "GC-1", new BigDecimal("50.00"));
        gc.setId(5L);
        gc.setStatus("ACTIVE");

        when(giftCardRepository.findByTenantIdAndId("default", 5L)).thenReturn(Optional.of(gc));
        when(giftCardRepository.save(any(GiftCard.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(patch("/api/gift-cards/5/status")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DEPLETED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Gift card status updated."))
                .andExpect(jsonPath("$.giftCard.status").value("DEPLETED"));
    }
}
