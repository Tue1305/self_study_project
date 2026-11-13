package com.example.demo.controller;

import com.example.demo.entity.ServiceEntity;
import com.example.demo.entity.UserEntity;
import com.example.demo.repository.ServiceRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/nail-techs")
@CrossOrigin(origins = "*")
public class NailTechController {

    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantContextResolver tenantContextResolver;

    public NailTechController(
            UserRepository userRepository,
            ServiceRepository serviceRepository,
            PasswordEncoder passwordEncoder,
            TenantContextResolver tenantContextResolver
    ) {
        this.userRepository = userRepository;
        this.serviceRepository = serviceRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantContextResolver = tenantContextResolver;
    }

    // Supports optional ?serviceId={id} or ?serviceIds={id1,id2} filtering scoped strictly to the active tenant
    @GetMapping
    public List<UserEntity> getAllNailTechs(
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) List<Long> serviceIds,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        List<UserEntity> tenantTechs = userRepository.findAllByTenantIdAndRole(tenantId, "ROLE_NAIL_TECH");

        List<Long> targetIds = new java.util.ArrayList<>();
        if (serviceIds != null && !serviceIds.isEmpty()) {
            targetIds.addAll(serviceIds);
        }
        if (serviceId != null && !targetIds.contains(serviceId)) {
            targetIds.add(serviceId);
        }

        if (targetIds.isEmpty()) {
            return tenantTechs;
        }

        Set<String> targetCategories = targetIds.stream()
                .map(id -> serviceRepository.findByTenantIdAndId(tenantId, id).orElse(null))
                .filter(s -> s != null && s.getName() != null)
                .map(s -> categorizeService(s.getName()))
                .collect(Collectors.toSet());

        if (targetCategories.isEmpty()) {
            return tenantTechs;
        }

        // For 1 service, filter strictly for that service's category.
        // For 2+ services, return the union (anyMatch) of technicians qualified for ANY of the requested services
        // so per-service staff pickers can filter each service block independently without computing an intersection.
        List<UserEntity> qualifiedTechs = tenantTechs.stream()
                .filter(tech -> targetCategories.stream().anyMatch(cat -> isTechQualifiedForCategory(tech, cat)))
                .toList();

        return qualifiedTechs.isEmpty() ? tenantTechs : qualifiedTechs;
    }

    public static String categorizeService(String serviceName) {
        String lower = serviceName.toLowerCase().trim();

        if (lower.contains("pedicure") || lower.contains("combo")) {
            return "PEDICURE";
        }
        if (lower.contains("kid") || lower.contains("kids")) {
            return "KID_SERVICES";
        }
        if (lower.contains("massage") || lower.contains("hot stone")) {
            return "BODY_MASSAGE";
        }
        if (lower.contains("wax") || lower.contains("waxing")) {
            return "WAXING";
        }
        if (lower.contains("facial") || lower.contains("babor") || lower.contains("collagen")) {
            return "FACIAL";
        }
        if (lower.contains("bio sculpture") || lower.contains("gel powder") || lower.contains("dipping powder")
                || lower.contains("nail art") || lower.contains("french tip") || lower.contains("ombre")
                || lower.contains("chrome") || lower.contains("take off") || lower.contains("extension")
                || lower.contains("refill") || lower.contains("cuticle") || lower.contains("paraffin")
                || lower.contains("nail cut") || lower.contains("nail repair") || lower.contains("shellac")) {
            return "NAIL_ART";
        }
        if (lower.contains("manicure")) {
            return "MANICURE";
        }
        if (lower.contains("tint") || lower.contains("tinting")) {
            return "FACIAL";
        }
        if (lower.contains("rejuvenation") || lower.contains("executive") || lower.contains("package")) {
            return "ALL";
        }

        return "MANICURE";
    }

    public static boolean isTechQualifiedForCategory(UserEntity tech, String category) {
        if ("PEDICURE".equals(category) || "KID_SERVICES".equals(category)) {
            return true;
        }

        String spec = tech.getSpecialty();
        if (spec == null || spec.isBlank()) {
            return false;
        }

        Set<String> capabilityTags = Arrays.stream(spec.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());

        if (capabilityTags.contains("ALL") || capabilityTags.contains("FULL EVERYTHING") || capabilityTags.contains("GENERAL")) {
            return true;
        }

        return capabilityTags.contains(category);
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    @PostMapping
    public ResponseEntity<?> createNailTech(@RequestBody UserEntity tech, HttpServletRequest request) {
        String identifier = tech.getEmail() != null ? tech.getEmail() : tech.getUsername();

        if (identifier == null || userRepository.existsByUsername(identifier)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email or username is already taken!"));
        }

        if (tech.getUsername() == null && tech.getEmail() != null) {
            tech.setUsername(tech.getEmail());
        }
        if (tech.getPassword() == null || tech.getPassword().isEmpty()) {
            tech.setPassword(passwordEncoder.encode("defaultPassword123"));
        }

        String tenantId = tenantContextResolver.resolveTenantId(request);
        tech.setTenantId(tenantId);
        tech.setRole("ROLE_NAIL_TECH");

        UserEntity savedTech = userRepository.save(tech);
        return ResponseEntity.ok(savedTech);
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateNailTech(
            @PathVariable Long id,
            @RequestBody UserEntity updatedTech,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        return userRepository.findByTenantIdAndId(tenantId, id)
                .map(tech -> {
                    tech.setName(updatedTech.getName());
                    tech.setEmail(updatedTech.getEmail());
                    tech.setSpecialty(updatedTech.getSpecialty());
                    if (updatedTech.getEmail() != null) {
                        tech.setUsername(updatedTech.getEmail());
                    }
                    userRepository.save(tech);
                    return ResponseEntity.ok(Map.of("message", "Nail technician updated successfully"));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Nail technician not found")));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteNailTech(@PathVariable Long id, HttpServletRequest request) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        Optional<UserEntity> existing = userRepository.findByTenantIdAndId(tenantId, id);
        if (existing.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Nail technician not found"));
        }
        userRepository.delete(existing.get());
        return ResponseEntity.ok(Map.of("message", "Nail technician deleted successfully"));
    }
}
