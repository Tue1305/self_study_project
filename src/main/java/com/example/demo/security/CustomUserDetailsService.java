package com.example.demo.security;

import com.example.demo.entity.UserEntity;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.TenantContext;
import com.example.demo.service.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Optional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final TenantContextResolver tenantContextResolver;

    public CustomUserDetailsService(UserRepository userRepository,
                                    TenantContextResolver tenantContextResolver) {
        this.userRepository = userRepository;
        this.tenantContextResolver = tenantContextResolver;
    }

    /**
     * Tenant-scoped user details loading. Searches within the given tenant scope first,
     * then gracefully falls back to default tenant or first matching username.
     */
    public UserDetails loadUserByUsernameAndTenant(String username, String tenantId) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("Username or email cannot be empty");
        }

        String trimmedUsername = username.trim();
        String targetTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId.trim().toLowerCase() : null;
        Optional<UserEntity> userOpt = Optional.empty();

        if (targetTenant != null) {
            userOpt = userRepository.findByTenantIdAndUsernameIgnoreCase(targetTenant, trimmedUsername)
                    .or(() -> userRepository.findByTenantIdAndEmailIgnoreCase(targetTenant, trimmedUsername));
        }

        if (userOpt.isEmpty()) {
            // Fallback for single-tenant / default accounts
            userOpt = userRepository.findByTenantIdAndUsernameIgnoreCase("default", trimmedUsername)
                    .or(() -> userRepository.findByTenantIdAndEmailIgnoreCase("default", trimmedUsername))
                    .or(() -> userRepository.findFirstByUsernameIgnoreCase(trimmedUsername));
        }

        UserEntity user = userOpt.orElseThrow(() ->
                new UsernameNotFoundException("User not found for: " + trimmedUsername +
                        (targetTenant != null ? " in tenant: " + targetTenant : "")));

        String rawRole = user.getRole();
        String role = rawRole;
        if (role == null || role.trim().isEmpty()) {
            role = "ROLE_USER";
        } else {
            role = role.trim().toUpperCase();
            if (!role.startsWith("ROLE_")) {
                role = "ROLE_" + role;
            }
        }

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority(role))
        );
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String tenantId = TenantContext.getCurrentTenant();

        if (tenantId == null || tenantId.isBlank()) {
            try {
                RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
                if (attrs instanceof ServletRequestAttributes sra) {
                    HttpServletRequest req = sra.getRequest();
                    if (req != null && tenantContextResolver != null) {
                        tenantId = tenantContextResolver.resolveTenantId(req);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return loadUserByUsernameAndTenant(username, tenantId);
    }
}
