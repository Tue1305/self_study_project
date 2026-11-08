package com.example.demo;

import com.example.demo.entity.ShowcaseItem;
import com.example.demo.entity.UserEntity;
import com.example.demo.entity.ServiceEntity;
import com.example.demo.repository.ServiceRepository;
import com.example.demo.repository.ShowcaseRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.CustomerService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final ShowcaseRepository showcaseRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomerService customerService;

    public DataInitializer(UserRepository userRepository,
                           ServiceRepository serviceRepository,
                           ShowcaseRepository showcaseRepository,
                           PasswordEncoder passwordEncoder,
                           CustomerService customerService) {
        this.userRepository = userRepository;
        this.serviceRepository = serviceRepository;
        this.showcaseRepository = showcaseRepository;
        this.passwordEncoder = passwordEncoder;
        this.customerService = customerService;
    }

    @Override
    public void run(String... args) {
        // Seed default user
        if (!userRepository.existsByUsername("user")) {
            UserEntity user = new UserEntity("user", passwordEncoder.encode("password"), "ROLE_USER");
            userRepository.save(user);
        }

        // Seed admin user and ensure its role is ROLE_ADMIN
        UserEntity admin = userRepository.findByUsername("admin").orElse(null);
        if (admin == null) {
            admin = new UserEntity("admin", passwordEncoder.encode("admin123"), "ROLE_ADMIN");
            userRepository.save(admin);
        } else if (!"ROLE_ADMIN".equals(admin.getRole())) {
            admin.setRole("ROLE_ADMIN");
            userRepository.save(admin);
        }

        // Sync all existing ROLE_USER accounts into the Customer database
        userRepository.findAll().forEach(customerService::syncUserToCustomer);

        if (serviceRepository.count() == 0) {
            serviceRepository.save(new ServiceEntity("Gel Manicure", "Long-lasting gel polish with cuticle care and hand massage.", new BigDecimal("45.00"), 45));
            serviceRepository.save(new ServiceEntity("Acrylic Full Set", "Full set of custom length acrylic extensions with choice of shape.", new BigDecimal("65.00"), 75));
            serviceRepository.save(new ServiceEntity("Pedicure Deluxe", "Soak, scrub, nail shaping, mask, and regular polish.", new BigDecimal("55.00"), 60));
            serviceRepository.save(new ServiceEntity("Nail Art Add-On", "Custom hand-painted designs, foils, or charms per nail.", new BigDecimal("15.00"), 20));
            System.out.println("✅ Sample nail services seeded into the database!");
        }

        if (showcaseRepository.count() == 0) {
            showcaseRepository.saveAll(List.of(
                    new ShowcaseItem("Short Almond Chrome", "Milky white base with iridescent pearl chrome dust finish.", "gel", "Gel-X", "@NailArtistry", "1 hr 15 mins", "nail_collection/nail4.png"),
                    new ShowcaseItem("Hand-Painted Botanical", "Matte nude background with fine line leaf detail and gold foil highlights.", "art", "Custom Art", "@NailArtistry", "1 hr 45 mins", "nail_collection/nail5.png"),
                    new ShowcaseItem("Coffin French Ombre", "Classic pink-and-white gradient extended coffin sculpt.", "acrylic", "Acrylic", "@NailArtistry", "1 hr 30 mins", "nail_collection/nail6.png"),
                    new ShowcaseItem("3D Y2K Jelly Sculpt", "Translucent pink jelly base layered with raised 3D gel swirls and charms.", "art", "Custom Art", "@NailArtistry", "2 hrs", "nail_collection/nail7.png"),
                    new ShowcaseItem("3D Y2K Jelly Sculpt", "Translucent pink jelly base layered with raised 3D gel swirls and charms.", "art", "Custom Art", "@NailArtistry", "2 hrs", "nail_collection/nail8.png"),
                    new ShowcaseItem("3D Y2K Jelly Sculpt", "Translucent pink jelly base layered with raised 3D gel swirls and charms.", "art", "Custom Art", "@NailArtistry", "2 hrs", "nail_collection/nail1.png"),
                    new ShowcaseItem("3D Y2K Jelly Sculpt", "Translucent pink jelly base layered with raised 3D gel swirls and charms.", "art", "Custom Art", "@NailArtistry", "2 hrs", "nail_collection/nail10.png"),
                    new ShowcaseItem("3D Y2K Jelly Sculpt", "Translucent pink jelly base layered with raised 3D gel swirls and charms.", "art", "Custom Art", "@NailArtistry", "2 hrs", "nail_collection/nail11.png"),
                    new ShowcaseItem("3D Y2K Jelly Sculpt", "Translucent pink jelly base layered with raised 3D gel swirls and charms.", "art", "Custom Art", "@NailArtistry", "2 hrs", "nail_collection/nail12.png"),
                    new ShowcaseItem("3D Y2K Jelly Sculpt", "Translucent pink jelly base layered with raised 3D gel swirls and charms.", "art", "Custom Art", "@NailArtistry", "2 hrs", "nail_collection/nail13.png"),
                    new ShowcaseItem("3D Y2K Jelly Sculpt", "Translucent pink jelly base layered with raised 3D gel swirls and charms.", "art", "Custom Art", "@NailArtistry", "2 hrs", "nail_collection/nail2.png"),
                    new ShowcaseItem("3D Y2K Jelly Sculpt", "Translucent pink jelly base layered with raised 3D gel swirls and charms.", "art", "Custom Art", "@NailArtistry", "2 hrs", "nail_collection/nail3.png")
            ));
            System.out.println("✅ Default showcase items seeded into the database!");
        }
    }
}
