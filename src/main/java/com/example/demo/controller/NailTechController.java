package com.example.demo.controller;

import com.example.demo.entity.ServiceEntity;
import com.example.demo.entity.UserEntity;
import com.example.demo.repository.ServiceRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/nail-techs")
@CrossOrigin(origins = "*")
public class NailTechController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Public or authenticated so booking page & admin can fetch the list of nail techs
    // Supports optional ?serviceId={id} filtering for the multi-step booking wizard
    @GetMapping
    public List<UserEntity> getAllNailTechs(@RequestParam(required = false) Long serviceId) {
        List<UserEntity> allTechs = userRepository.findAll()
                .stream()
                .filter(u -> "ROLE_NAIL_TECH".equals(u.getRole()))
                .toList();

        if (serviceId == null) {
            return allTechs;
        }

        ServiceEntity service = serviceRepository.findById(serviceId).orElse(null);
        if (service == null || service.getName() == null) {
            return allTechs;
        }

        String targetCategory = categorizeService(service.getName());

        List<UserEntity> qualifiedTechs = allTechs.stream()
                .filter(tech -> isTechQualifiedForCategory(tech, targetCategory))
                .toList();

        // Fallback: if no specific specialty match, return all available nail technicians
        return qualifiedTechs.isEmpty() ? allTechs : qualifiedTechs;
    }

    /**
     * Determines the operational category of a service based on its name.
     */
    private String categorizeService(String serviceName) {
        String lower = serviceName.toLowerCase().trim();

        // 1. Pedicures (Universal - all staff qualified)
        if (lower.contains("pedicure") || lower.contains("combo")) {
            return "PEDICURE";
        }
        // 2. Kid Services (Universal - all staff qualified)
        if (lower.contains("kid") || lower.contains("kids")) {
            return "KID_SERVICES";
        }
        // 3. Body Massage
        if (lower.contains("massage") || lower.contains("hot stone")) {
            return "BODY_MASSAGE";
        }
        // 4. Waxing
        if (lower.contains("wax") || lower.contains("waxing")) {
            return "WAXING";
        }
        // 5. Facial Treatments
        if (lower.contains("facial") || lower.contains("babor") || lower.contains("collagen")) {
            return "FACIAL";
        }
        // 6. Nail Enhancement & Art (Strict: only Staff with NAIL_ART)
        if (lower.contains("bio sculpture") || lower.contains("gel powder") || lower.contains("dipping powder")
                || lower.contains("nail art") || lower.contains("french tip") || lower.contains("ombre")
                || lower.contains("chrome") || lower.contains("take off") || lower.contains("extension")
                || lower.contains("refill") || lower.contains("cuticle") || lower.contains("paraffin")
                || lower.contains("nail cut") || lower.contains("nail repair") || lower.contains("shellac")) {
            return "NAIL_ART";
        }
        // 7. Manicures
        if (lower.contains("manicure")) {
            return "MANICURE";
        }
        // 8. Specialty / Tinting / Packages
        if (lower.contains("tint") || lower.contains("tinting")) {
            return "FACIAL"; // Grouped under aesthetic/facial specialists
        }
        if (lower.contains("rejuvenation") || lower.contains("executive") || lower.contains("package")) {
            return "ALL"; // Package spans multiple services, full capability staff
        }

        return "MANICURE";
    }

    /**
     * Verifies if a technician is qualified for the given category according to store capability rules.
     */
    private boolean isTechQualifiedForCategory(UserEntity tech, String category) {
        // Universal Rule: EVERY staff member can perform Pedicures and Kid Services
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

        // Universal staff capability tag
        if (capabilityTags.contains("ALL") || capabilityTags.contains("FULL EVERYTHING") || capabilityTags.contains("GENERAL")) {
            return true;
        }

        return capabilityTags.contains(category);
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    @PostMapping
    public ResponseEntity<?> createNailTech(@RequestBody UserEntity tech) {
        String identifier = tech.getEmail() != null ? tech.getEmail() : tech.getUsername();

        if (identifier == null || userRepository.existsByUsername(identifier)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email or username is already taken!"));
        }

        // Assign default username/password if created from admin panel
        if (tech.getUsername() == null && tech.getEmail() != null) {
            tech.setUsername(tech.getEmail());
        }
        if (tech.getPassword() == null || tech.getPassword().isEmpty()) {
            tech.setPassword(passwordEncoder.encode("defaultPassword123")); // Default secure pass
        }

        // Explicitly set the role to ROLE_NAIL_TECH
        tech.setRole("ROLE_NAIL_TECH");

        UserEntity savedTech = userRepository.save(tech);
        return ResponseEntity.ok(savedTech);
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateNailTech(@PathVariable Long id, @RequestBody UserEntity updatedTech) {
        return userRepository.findById(id)
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
    public ResponseEntity<?> deleteNailTech(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Nail technician not found"));
        }
        userRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Nail technician deleted successfully"));
    }
}
