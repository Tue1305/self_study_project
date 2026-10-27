package com.example.demo.config;

import com.example.demo.entity.UserEntity;
import com.example.demo.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminSeeder {

    @Bean
    public CommandLineRunner createDefaultAdmin(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            String adminUsername = "admin@jennysnailstudio.com";

            if (!userRepository.existsByUsername(adminUsername)) {
                UserEntity admin = new UserEntity();
                admin.setUsername(adminUsername);
                admin.setPassword(passwordEncoder.encode("adminSecurePass123!"));
                admin.setRole("ROLE_ADMIN");
                admin.setName("Jenny Admin");

                userRepository.save(admin);
                System.out.println(">>> Default Admin account created: " + adminUsername + " / adminSecurePass123!");
            }
        };
    }
}