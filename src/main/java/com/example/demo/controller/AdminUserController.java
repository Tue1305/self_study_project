package com.example.demo.controller;

import com.example.demo.entity.UserEntity;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@CrossOrigin(origins = "*")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
public class AdminUserController {

    private final UserRepository userRepository;
    private final TenantContextResolver tenantContextResolver;

    public AdminUserController(UserRepository userRepository, TenantContextResolver tenantContextResolver) {
        this.userRepository = userRepository;
        this.tenantContextResolver = tenantContextResolver;
    }

    /**
     * Tenant-scoped admin user list.
     * Only authenticated admins can access this, and it strictly returns users within the admin's salon.
     */
    @GetMapping
    public ResponseEntity<List<UserEntity>> getAllUsersInTenant(HttpServletRequest request) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        List<UserEntity> users = userRepository.findAllByTenantId(tenantId);
        return ResponseEntity.ok(users);
    }
}
