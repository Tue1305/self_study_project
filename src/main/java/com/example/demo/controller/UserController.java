package com.example.demo.controller;

import com.example.demo.dto.PasswordChangeDto;
import com.example.demo.dto.UserUpdateDto;
import com.example.demo.entity.Customer;
import com.example.demo.entity.CustomerNailNote;
import com.example.demo.entity.UserEntity;
import com.example.demo.repository.CustomerNailNoteRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.CustomerService;
import com.example.demo.service.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final CustomerNailNoteRepository nailNoteRepository;

    public UserController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          CustomerService customerService) {
        this(userRepository, passwordEncoder, customerService, null, null);
    }

    @Autowired
    public UserController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          CustomerService customerService,
                          CustomerRepository customerRepository,
                          CustomerNailNoteRepository nailNoteRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.customerService = customerService;
        this.customerRepository = customerRepository;
        this.nailNoteRepository = nailNoteRepository;
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

    // Get current client's nail style preferences & photo history
    @GetMapping("/me/nail-profile")
    public ResponseEntity<?> getClientNailProfile(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        UserEntity user = resolveCurrentUser(userDetails);
        String tenantId = (user.getTenantId() != null && !user.getTenantId().isBlank())
                ? user.getTenantId().trim().toLowerCase() : "default";

        Customer customer = null;
        if (customerRepository != null) {
            String email = user.getEmail() != null ? user.getEmail().trim() : user.getUsername();
            customer = customerRepository.findByTenantIdAndEmail(tenantId, email)
                    .or(() -> customerRepository.findByTenantIdAndName(tenantId, user.getUsername()))
                    .orElse(null);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        if (customer != null) {
            resp.put("customerId", customer.getId());
            resp.put("nailShape", customer.getNailShape());
            resp.put("nailLength", customer.getNailLength());
            resp.put("nailSystem", customer.getNailSystem());
            resp.put("favoriteColors", customer.getFavoriteColors());
            resp.put("colorFormula", customer.getColorFormula());
            resp.put("medicalFlags", customer.getMedicalFlags());
            resp.put("cuticleCareNotes", customer.getCuticleCareNotes());

            List<CustomerNailNote> notes = (nailNoteRepository != null)
                    ? nailNoteRepository.findAllByTenantIdAndCustomerIdOrderByServiceDateDescCreatedAtDesc(tenantId, customer.getId())
                    : Collections.emptyList();
            resp.put("nailNotes", notes);
        } else {
            resp.put("nailShape", null);
            resp.put("nailLength", null);
            resp.put("nailSystem", null);
            resp.put("favoriteColors", null);
            resp.put("cuticleCareNotes", null);
            resp.put("nailNotes", Collections.emptyList());
        }

        return ResponseEntity.ok(resp);
    }

    // Update current client's nail style preferences
    @PutMapping("/me/nail-profile")
    public ResponseEntity<?> updateClientNailProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> payload
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        UserEntity user = resolveCurrentUser(userDetails);
        String tenantId = (user.getTenantId() != null && !user.getTenantId().isBlank())
                ? user.getTenantId().trim().toLowerCase() : "default";

        if (customerRepository == null) {
            return ResponseEntity.badRequest().body("Customer repository unavailable");
        }

        String email = user.getEmail() != null ? user.getEmail().trim() : user.getUsername();
        Customer customer = customerRepository.findByTenantIdAndEmail(tenantId, email)
                .or(() -> customerRepository.findByTenantIdAndName(tenantId, user.getUsername()))
                .orElseGet(() -> {
                    Customer c = new Customer(user.getUsername(), email, user.getPhoneNumber(), tenantId);
                    return customerRepository.save(c);
                });

        if (payload.containsKey("nailShape")) {
            customer.setNailShape(payload.get("nailShape") != null ? payload.get("nailShape").toString().trim() : null);
        }
        if (payload.containsKey("nailLength")) {
            customer.setNailLength(payload.get("nailLength") != null ? payload.get("nailLength").toString().trim() : null);
        }
        if (payload.containsKey("nailSystem")) {
            customer.setNailSystem(payload.get("nailSystem") != null ? payload.get("nailSystem").toString().trim() : null);
        }
        if (payload.containsKey("favoriteColors")) {
            customer.setFavoriteColors(payload.get("favoriteColors") != null ? payload.get("favoriteColors").toString().trim() : null);
        }
        if (payload.containsKey("cuticleCareNotes")) {
            customer.setCuticleCareNotes(payload.get("cuticleCareNotes") != null ? payload.get("cuticleCareNotes").toString().trim() : null);
        }

        Customer saved = customerRepository.save(customer);
        return ResponseEntity.ok(Map.of(
                "message", "Nail preferences updated successfully!",
                "nailShape", saved.getNailShape() != null ? saved.getNailShape() : "",
                "nailLength", saved.getNailLength() != null ? saved.getNailLength() : "",
                "nailSystem", saved.getNailSystem() != null ? saved.getNailSystem() : "",
                "favoriteColors", saved.getFavoriteColors() != null ? saved.getFavoriteColors() : "",
                "cuticleCareNotes", saved.getCuticleCareNotes() != null ? saved.getCuticleCareNotes() : ""
        ));
    }
}
