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

import java.util.List;
import java.util.Map;

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

        String serviceNameLower = service.getName().toLowerCase();
        String[] keywords = serviceNameLower.split("\\s+");

        List<UserEntity> qualifiedTechs = allTechs.stream()
                .filter(tech -> {
                    String spec = tech.getSpecialty();
                    if (spec == null || spec.isBlank()
                            || spec.equalsIgnoreCase("general")
                            || spec.equalsIgnoreCase("all")
                            || spec.toLowerCase().contains("all services")) {
                        return true;
                    }
                    String specLower = spec.toLowerCase();
                    if (specLower.contains(serviceNameLower) || serviceNameLower.contains(specLower)) {
                        return true;
                    }
                    for (String word : keywords) {
                        if (word.length() >= 3 && specLower.contains(word)) {
                            return true;
                        }
                    }
                    return false;
                })
                .toList();

        // Fallback: if no specific specialty match, return all available nail technicians
        return qualifiedTechs.isEmpty() ? allTechs : qualifiedTechs;
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
