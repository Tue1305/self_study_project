package com.example.demo.controller;

import com.example.demo.entity.ServiceEntity;
import com.example.demo.repository.ServiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/services")
@CrossOrigin(origins = "*")
public class ServiceController {

    @Autowired
    private ServiceRepository serviceRepository;

    @GetMapping
    public List<ServiceEntity> getAllServices() {
        return serviceRepository.findAll();
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    @PostMapping
    public ResponseEntity<?> createService(@RequestBody ServiceEntity service) {
        if (service.getName() == null || service.getName().isBlank() || service.getPrice() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Service name and price are required."));
        }
        if (service.getDurationMinutes() == null) {
            service.setDurationMinutes(60);
        }
        return ResponseEntity.ok(serviceRepository.save(service));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateService(@PathVariable Long id, @RequestBody ServiceEntity updated) {
        return serviceRepository.findById(id)
                .map(existing -> {
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
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Service not found.")));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteService(@PathVariable Long id) {
        if (!serviceRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Service not found."));
        }
        serviceRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Service deleted successfully."));
    }
}
