package com.example.demo;

import com.example.demo.entity.UserEntity;
import com.example.demo.entity.ServiceEntity;
import com.example.demo.repository.ServiceRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, ServiceRepository serviceRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.serviceRepository = serviceRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // Seed default user
        if (!userRepository.existsByUsername("user")) {
            UserEntity user = new UserEntity("user", passwordEncoder.encode("password"));
            userRepository.save(user);
        }

        // Seed admin user
        if (!userRepository.existsByUsername("admin")) {
            UserEntity admin = new UserEntity("admin", passwordEncoder.encode("admin123"));
            userRepository.save(admin);
        }

        if (serviceRepository.count() == 0) {
            serviceRepository.save(new ServiceEntity("Gel Manicure", "Long-lasting gel polish with cuticle care and hand massage.", new BigDecimal("45.00"), 45));
            serviceRepository.save(new ServiceEntity("Acrylic Full Set", "Full set of custom length acrylic extensions with choice of shape.", new BigDecimal("65.00"), 75));
            serviceRepository.save(new ServiceEntity("Pedicure Deluxe", "Soak, scrub, nail shaping, mask, and regular polish.", new BigDecimal("55.00"), 60));
            serviceRepository.save(new ServiceEntity("Nail Art Add-On", "Custom hand-painted designs, foils, or charms per nail.", new BigDecimal("15.00"), 20));
            System.out.println("✅ Sample nail services seeded into the database!");
        }
    }
}

