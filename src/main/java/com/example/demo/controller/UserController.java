package com.example.demo.controller;

import com.example.demo.dto.PasswordChangeDto;
import com.example.demo.dto.UserUpdateDto;
import com.example.demo.entity.UserEntity;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.CustomerService;
import com.example.demo.service.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomerService customerService;

    public UserController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          CustomerService customerService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.customerService = customerService;
    }

    private UserEntity resolveCurrentUser(UserDetails userDetails) {
        String username = userDetails.getUsername();
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId != null && !tenantId.isBlank()) {
            return userRepository.findByTenantIdAndUsernameIgnoreCase(tenantId, username)
                    .or(() -> userRepository.findByTenantIdAndEmailIgnoreCase(tenantId, username))
                    .or(() -> userRepository.findFirstByUsernameIgnoreCase(username))
                    .orElseThrow(() -> new RuntimeException("User not found"));
        }
        return userRepository.findByTenantIdAndUsernameIgnoreCase("default", username)
                .or(() -> userRepository.findFirstByUsernameIgnoreCase(username))
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // Get current logged-in user profile details
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        UserEntity user = resolveCurrentUser(userDetails);

        Map<String, Object> response = new HashMap<>();
        response.put("username", user.getUsername());
        response.put("email", user.getEmail());
        response.put("phoneNumber", user.getPhoneNumber());
        response.put("role", user.getRole());

        return ResponseEntity.ok(response);
    }

    // Update profile details
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@AuthenticationPrincipal UserDetails userDetails, @RequestBody UserUpdateDto updateDto) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        UserEntity user = resolveCurrentUser(userDetails);

        if (updateDto.getUsername() != null && !updateDto.getUsername().trim().isEmpty()) {
            user.setUsername(updateDto.getUsername());
        }
        if (updateDto.getEmail() != null) {
            user.setEmail(updateDto.getEmail());
        }
        if (updateDto.getPhoneNumber() != null) {
            user.setPhoneNumber(updateDto.getPhoneNumber());
        }

        UserEntity savedUser = userRepository.save(user);

        // Sync updated email/phone/username to the Customer database as well
        customerService.syncUserToCustomer(savedUser);

        return ResponseEntity.ok().body("Profile updated successfully");
    }

    // Change password
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@AuthenticationPrincipal UserDetails userDetails, @RequestBody PasswordChangeDto dto) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        UserEntity user = resolveCurrentUser(userDetails);

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);

        return ResponseEntity.ok().body("Password changed successfully");
    }
}
