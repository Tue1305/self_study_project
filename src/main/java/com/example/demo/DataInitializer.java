package com.example.demo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
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
    }
}