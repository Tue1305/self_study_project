package com.example.demo.controller;

import com.example.demo.entity.ShowcaseItem;
import com.example.demo.repository.ShowcaseRepository;
import com.example.demo.repository.TenantConfigRepository;
import com.example.demo.service.TenantContextResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ShowcaseTenantIsolationTest {

    @Mock
    private ShowcaseRepository showcaseRepository;

    @Mock
    private TenantConfigRepository tenantConfigRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TenantContextResolver resolver = new TenantContextResolver(tenantConfigRepository);
        ShowcaseController controller = new ShowcaseController(showcaseRepository, resolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("Tenant Isolation: GET /api/showcase returns only active tenant items")
    void testGetAllShowcaseItemsScopedToTenant() throws Exception {
        ShowcaseItem luxeItem1 = new ShowcaseItem("luxe", "Luxe Gold", "Desc", "art", "Custom", "@Luxe", "1 hr", "img1.png");
        luxeItem1.setId(10L);
        ShowcaseItem luxeItem2 = new ShowcaseItem("luxe", "Luxe Chrome", "Desc", "gel", "Gel-X", "@Luxe", "1 hr", "img2.png");
        luxeItem2.setId(11L);

        when(showcaseRepository.findAllByTenantIdOrderByIdAsc("luxe")).thenReturn(List.of(luxeItem1, luxeItem2));

        mockMvc.perform(get("/api/showcase")
                        .header("X-Tenant-ID", "luxe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].tenantId").value("luxe"))
                .andExpect(jsonPath("$[0].title").value("Luxe Gold"))
                .andExpect(jsonPath("$[1].id").value(11))
                .andExpect(jsonPath("$[1].tenantId").value("luxe"))
                .andExpect(jsonPath("$[1].title").value("Luxe Chrome"));
    }

    @Test
    @DisplayName("Tenant Scope: POST /api/showcase sets active tenantId from context")
    void testCreateShowcaseItemSetsTenantId() throws Exception {
        when(showcaseRepository.save(any(ShowcaseItem.class))).thenAnswer(inv -> {
            ShowcaseItem item = inv.getArgument(0);
            item.setId(25L);
            return item;
        });

        String payload = """
                {
                  "title": "Luxe Velvet Stiletto",
                  "description": "Deep emerald cat-eye velvet shimmer",
                  "category": "art",
                  "imageUrl": "nail_collection/nail7.png"
                }
                """;

        mockMvc.perform(post("/api/showcase")
                        .header("X-Tenant-ID", "luxe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(25))
                .andExpect(jsonPath("$.tenantId").value("luxe"))
                .andExpect(jsonPath("$.title").value("Luxe Velvet Stiletto"));

        ArgumentCaptor<ShowcaseItem> captor = ArgumentCaptor.forClass(ShowcaseItem.class);
        verify(showcaseRepository).save(captor.capture());
        assertEquals("luxe", captor.getValue().getTenantId());
    }

    @Test
    @DisplayName("Cross-Tenant Protection: PUT /api/showcase/{id} rejects modifying another tenant's item")
    void testUpdateShowcaseItemCrossTenantRejected() throws Exception {
        // Item exists in Jenny's salon ("default"), but Luxe admin attempts to update it
        when(showcaseRepository.findByTenantIdAndId("luxe", 5L)).thenReturn(Optional.empty());

        String payload = """
                {
                  "title": "Hacked Title"
                }
                """;

        mockMvc.perform(put("/api/showcase/5")
                        .header("X-Tenant-ID", "luxe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Showcase item not found."));

        verify(showcaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cross-Tenant Protection: DELETE /api/showcase/{id} rejects deleting another tenant's item")
    void testDeleteShowcaseItemCrossTenantRejected() throws Exception {
        // Item exists in Jenny's salon ("default"), but Luxe admin attempts to delete it
        when(showcaseRepository.findByTenantIdAndId("luxe", 5L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/showcase/5")
                        .header("X-Tenant-ID", "luxe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Showcase item not found."));

        verify(showcaseRepository, never()).delete(any());
        verify(showcaseRepository, never()).deleteById(any());
    }
}
