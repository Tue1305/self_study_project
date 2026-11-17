package com.example.demo.controller;

import com.example.demo.dto.JwtAuthResponse;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.entity.UserEntity;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtUtil;
import com.example.demo.service.CustomerService;
import com.example.demo.service.TenantContext;
import com.example.demo.service.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomerService customerService;
    private final TenantContextResolver tenantContextResolver;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtUtil jwtUtil,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          CustomerService customerService,
                          TenantContextResolver tenantContextResolver) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.customerService = customerService;
        this.tenantContextResolver = tenantContextResolver;
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        String initialTenant = null;
        if (loginRequest != null && loginRequest.getTenantId() != null && !loginRequest.getTenantId().isBlank()) {
            initialTenant = loginRequest.getTenantId().trim().toLowerCase();
        } else if (request != null && tenantContextResolver != null) {
            initialTenant = tenantContextResolver.resolveTenantId(request);
        }
        final String resolvedTenant = initialTenant;

        try {
            if (resolvedTenant != null && !resolvedTenant.isBlank()) {
                TenantContext.setCurrentTenant(resolvedTenant);
            }

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            // Look up UserEntity tenant-scoped
            UserEntity user = null;
            if (resolvedTenant != null && !resolvedTenant.isBlank()) {
                user = userRepository.findByTenantIdAndUsernameIgnoreCase(resolvedTenant, userDetails.getUsername())
                        .or(() -> userRepository.findByTenantIdAndEmailIgnoreCase(resolvedTenant, userDetails.getUsername()))
                        .orElse(null);
            }
            if (user == null) {
                user = userRepository.findByTenantIdAndUsernameIgnoreCase("default", userDetails.getUsername())
                        .or(() -> userRepository.findFirstByUsernameIgnoreCase(userDetails.getUsername()))
                        .orElse(null);
            }

            String tenantId = (user != null && user.getTenantId() != null && !user.getTenantId().isBlank())
                    ? user.getTenantId().trim().toLowerCase()
                    : (resolvedTenant != null ? resolvedTenant : "default");
            String role = (user != null && user.getRole() != null) ? user.getRole() : "ROLE_USER";

            String jwt = jwtUtil.generateToken(userDetails, tenantId);

            // Ensure ROLE_USER accounts are synced into the Customer database on login
            if (user != null) {
                customerService.syncUserToCustomer(user);
            }

            JwtAuthResponse authResponse = new JwtAuthResponse(jwt, tenantId, role);
            return ResponseEntity.ok(authResponse);

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid username or password"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "An unexpected error occurred: " + e.getMessage()));
        } finally {
            TenantContext.clear();
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest registerRequest, HttpServletRequest request) {
        String tenantId = (tenantContextResolver != null && request != null)
                ? tenantContextResolver.resolveTenantId(request)
                : "default";

        String username = registerRequest.getUsername() != null ? registerRequest.getUsername().trim() : null;
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body("Username cannot be empty!");
        }

        if (Boolean.TRUE.equals(userRepository.existsByTenantIdAndUsername(tenantId, username))
                || Boolean.TRUE.equals(userRepository.existsByTenantIdAndUsernameIgnoreCase(tenantId, username))) {
            return ResponseEntity.badRequest().body("Username is already taken!");
        }

        UserEntity user = new UserEntity();
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setRole("ROLE_USER");

        UserEntity savedUser = userRepository.save(user);

        // Automatically add new ROLE_USER account into the Customer database
        customerService.syncUserToCustomer(savedUser);

        return ResponseEntity.ok("User registered successfully!");
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email address is required"));
        }

        // TODO: Integrate JavaMailSender to send actual reset token/link here
        System.out.println("Password reset requested for email: " + email);

        // Return a generic success message for security best practices (prevents email enumeration)
        return ResponseEntity.ok(Map.of("message", "If an account with this email exists, password reset instructions have been sent."));
    }

    // Admin page to display all users
    @GetMapping("/admin/users")
    public ResponseEntity<List<UserEntity>> getAllUsers() {
        List<UserEntity> users = userRepository.findAll();
        return ResponseEntity.ok(users);
    }
}
