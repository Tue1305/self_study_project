package com.example.demo.controller;

import com.example.demo.service.AppointmentAuditService;
import com.example.demo.service.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/customers")
@CrossOrigin(origins = "*")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
public class AdminCustomerController {

    private final AppointmentAuditService auditService;
    private final TenantContextResolver tenantContextResolver;

    public AdminCustomerController(
            AppointmentAuditService auditService,
            TenantContextResolver tenantContextResolver
    ) {
        this.auditService = auditService;
        this.tenantContextResolver = tenantContextResolver;
    }

    /**
     * Tenant-scoped customer appointment history, reliability summary, and audit log.
     * Accessible via GET /api/admin/customers/{customerId}/history
     */
    @GetMapping("/{customerId}/history")
    public ResponseEntity<?> getCustomerHistory(
            @PathVariable Long customerId,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        Map<String, Object> history = auditService.getCustomerHistory(tenantId, customerId);
        if (history == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Customer not found."));
        }
        return ResponseEntity.ok(history);
    }
}
