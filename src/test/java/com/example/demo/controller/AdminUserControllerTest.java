package com.example.demo.controller;

import com.example.demo.entity.UserEntity;
import com.example.demo.repository.TenantConfigRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.TenantContextResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantConfigRepository tenantConfigRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TenantContextResolver resolver = new TenantContextResolver(tenantConfigRepository);
        AdminUserController controller = new AdminUserController(userRepository, resolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("Admin Users: Returns only users belonging to active tenant")
    void testGetAdminUsersScopedToTenant() throws Exception {
        UserEntity luxeAdmin = new UserEntity("luxe_admin", "pwd", "ROLE_ADMIN");
        luxeAdmin.setId(10L);
        luxeAdmin.setTenantId("luxe");

        UserEntity luxeTech = new UserEntity("luxe_tech", "pwd", "ROLE_NAIL_TECH");
        luxeTech.setId(11L);
        luxeTech.setTenantId("luxe");

        when(userRepository.findAllByTenantId("luxe")).thenReturn(List.of(luxeAdmin, luxeTech));

        mockMvc.perform(get("/api/admin/users")
                        .header("X-Tenant-ID", "luxe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].username").value("luxe_admin"))
                .andExpect(jsonPath("$[1].username").value("luxe_tech"));
    }
}
