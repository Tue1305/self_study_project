package com.example.demo.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {
    // UserDetailsService is provided by CustomUserDetailsService (@Service)
    // so that database roles (e.g. ROLE_ADMIN) are preserved instead of hardcoded to ROLE_USER.
}
