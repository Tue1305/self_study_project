package com.example.demo.controller;

import com.example.demo.dto.LoginRequest;
import com.example.demo.entity.UserEntity;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.ServiceRepository;
import com.example.demo.repository.TenantConfigRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.CustomUserDetailsService;
import com.example.demo.security.JwtUtil;
import com.example.demo.service.CustomerService;
import com.example.demo.service.TenantContext;
import com.example.demo.service.TenantContextResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MultiTenantStaffIsolationTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ServiceRepository serviceRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private TenantConfigRepository tenantConfigRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;

    private CustomerService customerService;
    private JwtUtil jwtUtil;
    private TenantContextResolver tenantResolver;
    private MockMvc nailTechMockMvc;
    private MockMvc authMockMvc;
    private CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        jwtUtil = new JwtUtil();
        tenantResolver = new TenantContextResolver(tenantConfigRepository);
        customerService = new CustomerService(customerRepository);

        NailTechController nailTechController = new NailTechController(
                userRepository, serviceRepository, passwordEncoder, tenantResolver
        );
        AuthController authController = new AuthController(
                authenticationManager, jwtUtil, userRepository, passwordEncoder, customerService, tenantResolver
        );

        nailTechMockMvc = MockMvcBuilders.standaloneSetup(nailTechController).build();
        authMockMvc = MockMvcBuilders.standaloneSetup(authController).build();
        customUserDetailsService = new CustomUserDetailsService(userRepository, tenantResolver);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void canAddStaffWithSameEmailInLuxeWhenAlreadyExistsInJennyDefault() throws Exception {
        // Given: 'tony@salon.com' exists in 'default'
        // But DOES NOT exist in 'luxe'
        when(userRepository.existsByTenantIdAndUsername("luxe", "tony@salon.com")).thenReturn(false);
        when(userRepository.existsByTenantIdAndUsernameIgnoreCase("luxe", "tony@salon.com")).thenReturn(false);
        when(userRepository.existsByTenantIdAndEmail("luxe", "tony@salon.com")).thenReturn(false);
        when(userRepository.existsByTenantIdAndEmailIgnoreCase("luxe", "tony@salon.com")).thenReturn(false);

        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity u = inv.getArgument(0);
            u.setId(201L);
            return u;
        });

        String payload = """
                {
                  "name": "Tony Luxe",
                  "email": "tony@salon.com",
                  "specialty": "MANICURE, NAIL_ART"
                }
                """;

        // When: Luxe admin creates Tony under 'luxe' tenant
        nailTechMockMvc.perform(post("/api/nail-techs")
                        .header("X-Tenant-ID", "luxe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(201))
                .andExpect(jsonPath("$.name").value("Tony Luxe"))
                .andExpect(jsonPath("$.tenantId").value("luxe"))
                .andExpect(jsonPath("$.username").value("tony@salon.com"));

        // Verify save was called with tenantId = 'luxe'
        verify(userRepository).save(argThat(user ->
                "luxe".equals(user.getTenantId()) &&
                "tony@salon.com".equals(user.getUsername()) &&
                "ROLE_NAIL_TECH".equals(user.getRole())
        ));
    }

    @Test
    void cannotAddDuplicateStaffWithinSameTenant() throws Exception {
        // Given: 'tony@salon.com' already exists in 'default'
        when(userRepository.existsByTenantIdAndUsername("default", "tony@salon.com")).thenReturn(true);

        String payload = """
                {
                  "name": "Tony Duplicate",
                  "email": "tony@salon.com",
                  "specialty": "MANICURE"
                }
                """;

        // When: Trying to create Tony in 'default' again
        nailTechMockMvc.perform(post("/api/nail-techs")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email or username is already taken!"));

        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void updateStaffAllowsSameEmailAcrossTenantsButBlocksDuplicatesInSameTenant() throws Exception {
        UserEntity existingTech = new UserEntity();
        existingTech.setId(50L);
        existingTech.setTenantId("luxe");
        existingTech.setName("Luxe Tech");
        existingTech.setUsername("tech1@luxenailspa.com");
        existingTech.setEmail("tech1@luxenailspa.com");

        when(userRepository.findByTenantIdAndId("luxe", 50L)).thenReturn(Optional.of(existingTech));

        UserEntity anotherLuxeTech = new UserEntity();
        anotherLuxeTech.setId(51L);
        anotherLuxeTech.setTenantId("luxe");
        anotherLuxeTech.setUsername("conflict@luxenailspa.com");
        anotherLuxeTech.setEmail("conflict@luxenailspa.com");

        when(userRepository.findByTenantIdAndUsernameIgnoreCase("luxe", "conflict@luxenailspa.com"))
                .thenReturn(Optional.of(anotherLuxeTech));

        String conflictingPayload = """
                {
                  "name": "Updated Tech",
                  "email": "conflict@luxenailspa.com",
                  "specialty": "MANICURE"
                }
                """;

        // Attempting to change tech 50's email to conflict with tech 51 in the same 'luxe' tenant is rejected
        nailTechMockMvc.perform(put("/api/nail-techs/50")
                        .header("X-Tenant-ID", "luxe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflictingPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email or username is already taken!"));
    }

    @Test
    void customUserDetailsServiceResolvesUserScopedToActiveTenant() {
        UserEntity defaultTony = new UserEntity();
        defaultTony.setId(10L);
        defaultTony.setTenantId("default");
        defaultTony.setUsername("tony@salon.com");
        defaultTony.setPassword("hash1");
        defaultTony.setRole("ROLE_NAIL_TECH");

        UserEntity luxeTony = new UserEntity();
        luxeTony.setId(20L);
        luxeTony.setTenantId("luxe");
        luxeTony.setUsername("tony@salon.com");
        luxeTony.setPassword("hash2");
        luxeTony.setRole("ROLE_ADMIN");

        when(userRepository.findByTenantIdAndUsernameIgnoreCase("luxe", "tony@salon.com"))
                .thenReturn(Optional.of(luxeTony));
        when(userRepository.findByTenantIdAndUsernameIgnoreCase("default", "tony@salon.com"))
                .thenReturn(Optional.of(defaultTony));

        // When loading for tenant 'luxe'
        UserDetails userDetailsLuxe = customUserDetailsService.loadUserByUsernameAndTenant("tony@salon.com", "luxe");
        assertNotNull(userDetailsLuxe);
        assertEquals("tony@salon.com", userDetailsLuxe.getUsername());
        assertEquals("hash2", userDetailsLuxe.getPassword());
        assertTrue(userDetailsLuxe.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")));

        // When loading for tenant 'default'
        UserDetails userDetailsDefault = customUserDetailsService.loadUserByUsernameAndTenant("tony@salon.com", "default");
        assertNotNull(userDetailsDefault);
        assertEquals("tony@salon.com", userDetailsDefault.getUsername());
        assertEquals("hash1", userDetailsDefault.getPassword());
        assertTrue(userDetailsDefault.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_NAIL_TECH")));
    }

    @Test
    void authLoginResolvesTenantAndIssuesTenantScopedJwt() throws Exception {
        UserDetails principal = new org.springframework.security.core.userdetails.User(
                "tony@salon.com", "pw", List.of(new SimpleGrantedAuthority("ROLE_NAIL_TECH"))
        );
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, "pw", principal.getAuthorities());

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);

        UserEntity luxeTony = new UserEntity();
        luxeTony.setId(201L);
        luxeTony.setTenantId("luxe");
        luxeTony.setUsername("tony@salon.com");
        luxeTony.setRole("ROLE_NAIL_TECH");

        when(userRepository.findByTenantIdAndUsernameIgnoreCase("luxe", "tony@salon.com"))
                .thenReturn(Optional.of(luxeTony));

        String loginPayload = """
                {
                  "username": "tony@salon.com",
                  "password": "secretPassword",
                  "tenantId": "luxe"
                }
                """;

        var result = authMockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("luxe"))
                .andExpect(jsonPath("$.role").value("ROLE_NAIL_TECH"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertTrue(responseBody.contains("accessToken"));
        String token = result.getResponse().getHeader("Authorization"); // or parse from json
        // Verify tenant claim in generated token
        com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(responseBody);
        String jwtToken = node.get("accessToken").asText();
        assertEquals("luxe", jwtUtil.extractTenantId(jwtToken));
        assertEquals("tony@salon.com", jwtUtil.extractUsername(jwtToken));
    }
}
