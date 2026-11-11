package com.example.demo.controller;

import com.example.demo.entity.ServiceEntity;
import com.example.demo.repository.ServiceRepository;
import com.example.demo.service.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/services")
@CrossOrigin(origins = "*")
public class ServiceController {

    private final ServiceRepository serviceRepository;
    private final TenantContextResolver tenantContextResolver;

    public ServiceController(ServiceRepository serviceRepository, TenantContextResolver tenantContextResolver) {
        this.serviceRepository = serviceRepository;
        this.tenantContextResolver = tenantContextResolver;
    }

    @GetMapping
    public List<ServiceEntity> getAllServices(HttpServletRequest request) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        return serviceRepository.findAllByTenantId(tenantId);
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    @PostMapping
    public ResponseEntity<?> createService(@RequestBody ServiceEntity service, HttpServletRequest request) {
        if (service.getName() == null || service.getName().isBlank() || service.getPrice() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Service name and price are required."));
        }
        if (service.getDurationMinutes() == null) {
            service.setDurationMinutes(60);
        }
        String tenantId = tenantContextResolver.resolveTenantId(request);
        service.setTenantId(tenantId);
        return ResponseEntity.ok(serviceRepository.save(service));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateService(
            @PathVariable Long id,
            @RequestBody ServiceEntity updated,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        Optional<ServiceEntity> optional = serviceRepository.findByTenantIdAndId(tenantId, id);
        if (optional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Service not found."));
        }

        ServiceEntity existing = optional.get();
        if (updated.getName() != null && !updated.getName().isBlank()) {
            existing.setName(updated.getName().trim());
        }
        existing.setDescription(updated.getDescription());
        if (updated.getPrice() != null) {
            existing.setPrice(updated.getPrice());
        }
        if (updated.getDurationMinutes() != null) {
            existing.setDurationMinutes(updated.getDurationMinutes());
        }
        return ResponseEntity.ok(serviceRepository.save(existing));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteService(@PathVariable Long id, HttpServletRequest request) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        Optional<ServiceEntity> existing = serviceRepository.findByTenantIdAndId(tenantId, id);
        if (existing.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Service not found."));
        }
        serviceRepository.delete(existing.get());
        return ResponseEntity.ok(Map.of("message", "Service deleted successfully."));
    }
}
