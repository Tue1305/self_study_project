package com.example.demo;

import com.example.demo.entity.ShowcaseItem;
import com.example.demo.entity.TenantConfig;
import com.example.demo.entity.UserEntity;
import com.example.demo.entity.ServiceEntity;
import com.example.demo.repository.ServiceRepository;
import com.example.demo.repository.ShowcaseRepository;
import com.example.demo.repository.TenantConfigRepository;
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
    private final TenantConfigRepository tenantConfigRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomerService customerService;

    public DataInitializer(UserRepository userRepository,
                           ServiceRepository serviceRepository,
                           ShowcaseRepository showcaseRepository,
                           TenantConfigRepository tenantConfigRepository,
                           PasswordEncoder passwordEncoder,
                           CustomerService customerService) {
        this.userRepository = userRepository;
        this.serviceRepository = serviceRepository;
        this.showcaseRepository = showcaseRepository;
        this.tenantConfigRepository = tenantConfigRepository;
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

        // Seed or update the 10 Staff Members with specific capabilities
        seedOrUpdateStaffMembers();

        // Seed multi-tenant UI layout & theme configurations
        seedTenantConfigs();

        // Seed full salon services menu into database if count < 10
        if (serviceRepository.count() < 10) {
            List<ServiceEntity> menuServices = List.of(
                    // Manicures
                    new ServiceEntity("Express Manicure", "Warm hand soak, nail trim, shape, buff, cuticle clean, polish application.", new BigDecimal("23.00"), 25),
                    new ServiceEntity("Express Manicure with Shellac", "Express manicure with long-lasting Shellac polish. ($5 extra for removal)", new BigDecimal("38.00"), 35),
                    new ServiceEntity("Essential Manicure", "Warm hand soak, trim, shape, buff, cuticle clean, sugar scrub hand massage, polish.", new BigDecimal("28.00"), 30),
                    new ServiceEntity("Essential Manicure with Shellac", "Essential manicure with sugar scrub hand massage and Shellac. ($5 extra for removal)", new BigDecimal("43.00"), 40),
                    new ServiceEntity("Deluxe Manicure", "Warm hand soak, trim, buff, cuticle clean, sugar scrub, paraffin, hand massage, polish.", new BigDecimal("38.00"), 40),
                    new ServiceEntity("Deluxe Manicure with Shellac", "Deluxe manicure with warm paraffin treatment, massage, and Shellac polish.", new BigDecimal("53.00"), 50),

                    // Pedicures & Combos
                    new ServiceEntity("Express Pedicure", "Warm spa soak, nail trim, shape, buff, cuticle clean, heels rub, polish.", new BigDecimal("34.00"), 25),
                    new ServiceEntity("Express Pedicure with Shellac", "Express pedicure with Shellac gel application.", new BigDecimal("49.00"), 35),
                    new ServiceEntity("Essential Pedicure", "Warm spa soak, trim, buff, cuticle clean, heels rub, callus remove, sugar scrub, foot massage.", new BigDecimal("40.00"), 40),
                    new ServiceEntity("Essential Pedicure with Shellac", "Essential pedicure with callus removal, scrub, foot massage & Shellac polish.", new BigDecimal("55.00"), 50),
                    new ServiceEntity("Deluxe Pedicure", "Warm spa soak, trim & shape, buff, heels rub, callus remove, sugar scrub, paraffin, feet massage.", new BigDecimal("52.00"), 50),
                    new ServiceEntity("Deluxe Pedicure with Shellac", "Deluxe spa pedicure with warm paraffin wrap, foot massage, and Shellac polish.", new BigDecimal("67.00"), 60),
                    new ServiceEntity("Express Pedicure-Manicure Combo", "Complete express manicure and express pedicure combo.", new BigDecimal("52.00"), 50),
                    new ServiceEntity("Essential Pedicure-Manicure Combo", "Essential manicure and pedicure combo with scrub and massage.", new BigDecimal("63.00"), 70),
                    new ServiceEntity("Deluxe Pedicure-Manicure Combo", "Full luxury deluxe manicure and pedicure combo with paraffin treatment.", new BigDecimal("85.00"), 90),
                    new ServiceEntity("Shellac Add-On for Hands or Feet to Any Combo", "Shellac upgrade addition for hands or feet to any combination service.", new BigDecimal("15.00"), 15),

                    // Kid Services
                    new ServiceEntity("Kids Hands & Toes Polish", "Fun polish application on little hands and toes (10 & under).", new BigDecimal("20.00"), 20),
                    new ServiceEntity("Kids Manicure", "Gentle hand soak, light shaping, massage, and polish (10 & under).", new BigDecimal("18.00"), 20),
                    new ServiceEntity("Kids Pedicure", "Gentle foot soak, light nail trim/shape, lotion, and polish (10 & under).", new BigDecimal("25.00"), 25),
                    new ServiceEntity("Kids Pedicure-Manicure Combo", "Full pampering mani-pedi duo for kids 10 and under.", new BigDecimal("40.00"), 40),

                    // Nail Enhancement & Art
                    new ServiceEntity("Shellac Polish Change", "Shape, buff, and Shellac application. ($5 extra for removal)", new BigDecimal("27.00"), 25),
                    new ServiceEntity("Bio Sculpture Gel New Set (with Plastic Tips Extension)", "Healthy Bio Sculpture Gel full set with plastic tips extension.", new BigDecimal("65.00"), 75),
                    new ServiceEntity("Bio Sculpture Gel New Set (with Sculpting Extension)", "Custom hand-sculpted Bio Sculpture Gel extensions.", new BigDecimal("80.00"), 90),
                    new ServiceEntity("Bio Sculpture Gel New Set (No Extension)", "Bio Sculpture Gel overlay on natural nails.", new BigDecimal("50.00"), 60),
                    new ServiceEntity("Bio Sculpture Gel Refill", "Refill maintenance for Bio Sculpture Gel sets.", new BigDecimal("50.00"), 60),
                    new ServiceEntity("Gel Powder New Set (with Shellac Color)", "Full set gel powder extensions with choice of vibrant Shellac color.", new BigDecimal("65.00"), 75),
                    new ServiceEntity("Gel Powder Refill (with Shellac Color)", "Refill for gel powder with new Shellac color.", new BigDecimal("50.00"), 60),
                    new ServiceEntity("Dipping Powder New Set with Extension", "Dipping powder full set with extension tips.", new BigDecimal("65.00"), 75),
                    new ServiceEntity("Dipping Powder New Set with No Extension", "Dipping powder overlay on natural nails.", new BigDecimal("50.00"), 50),
                    new ServiceEntity("Nail Polish Change for Hands", "Quick reshape and fresh regular polish application on hands.", new BigDecimal("14.00"), 15),
                    new ServiceEntity("Nail Polish Change for Feet", "Quick reshape and fresh regular polish application on toes.", new BigDecimal("18.00"), 15),
                    new ServiceEntity("Nail Art Design", "Custom hand-painted designs, accents, or gems.", new BigDecimal("5.00"), 20),
                    new ServiceEntity("Hand Paint French Tip Add-On", "Classic or custom hand-painted crisp French tips.", new BigDecimal("10.00"), 15),
                    new ServiceEntity("Ombre Airbrush French Tip Add-On", "Smooth gradient airbrushed French ombre effect.", new BigDecimal("15.00"), 20),
                    new ServiceEntity("Chrome Finish Add-On", "Mirror chrome, glazed donut, or iridescent metallic powder overlay.", new BigDecimal("10.00"), 15),
                    new ServiceEntity("Nail Cut for Hands", "Hand nail trim, reshape, and clean.", new BigDecimal("8.00"), 10),
                    new ServiceEntity("Nail Cut for Toes", "Toe nail trim and edge filing.", new BigDecimal("12.00"), 10),
                    new ServiceEntity("Nail Cut for Toes", "Toe nail trim and edge filing.", new BigDecimal("12.00"), 15),
                    new ServiceEntity("Nail Repair", "Individual nail fix for broken, cracked, or split nails.", new BigDecimal("10.00"), 15),
                    new ServiceEntity("Cuticle Trim", "Gentle cuticle softening, pushback, and precise clean-up.", new BigDecimal("12.00"), 15),
                    new ServiceEntity("Paraffin Treatment (Hands/Feet)", "Warm soothing paraffin wax bath for hands ($12) or feet ($14).", new BigDecimal("14.00"), 15),
                    new ServiceEntity("Shellac Removal Take Off", "Gentle, non-damaging Shellac polish removal.", new BigDecimal("15.00"), 15),
                    new ServiceEntity("Bio Gel Take Off", "Safe soak-off and clean removal of Bio Sculpture Gel.", new BigDecimal("20.00"), 20),
                    new ServiceEntity("Acrylic / Dipping Powder Take Off", "Complete removal of acrylic or dipping powder extensions.", new BigDecimal("25.00"), 25),

                    // Body Massage
                    new ServiceEntity("Hot Stone Therapy Massage", "Basalt hot stones placed along tension points to melt muscle fatigue.", new BigDecimal("110.00"), 75),
                    new ServiceEntity("Professional Relaxing Massage (Non RMT) Full Body", "Full body restorative relaxation massage.", new BigDecimal("90.00"), 60),
                    new ServiceEntity("Hand / Foot / Shoulder Massage (10 mins)", "Targeted express massage for hands, feet, or shoulders.", new BigDecimal("17.00"), 10),
                    new ServiceEntity("Hand / Foot / Shoulder Massage (15 mins)", "Invigorating 15-minute tension relief massage.", new BigDecimal("25.00"), 15),
                    new ServiceEntity("Hand / Foot / Shoulder Massage (1/2 hour)", "Deep therapeutic 30-minute focus on hands, feet, or shoulders.", new BigDecimal("45.00"), 30),

                    // Waxing & Combos
                    new ServiceEntity("Eyebrows Waxing", "Precise brow shaping and waxing.", new BigDecimal("14.00"), 15),
                    new ServiceEntity("Upper Lip Waxing", "Gentle upper lip hair removal.", new BigDecimal("8.00"), 10),
                    new ServiceEntity("Chin Waxing", "Quick chin waxing.", new BigDecimal("8.00"), 10),
                    new ServiceEntity("Side Burns Waxing", "Clean sideburn line definition.", new BigDecimal("12.00"), 10),
                    new ServiceEntity("Ears Waxing", "Ear hair removal.", new BigDecimal("12.00"), 10),
                    new ServiceEntity("Jaw Line Waxing", "Jaw line hair removal.", new BigDecimal("12.00"), 10),
                    new ServiceEntity("Neck Waxing", "Smooth neck wax.", new BigDecimal("15.00"), 15),
                    new ServiceEntity("Full Face Waxing", "Complete full face waxing including brows, lip, chin, and cheeks.", new BigDecimal("35.00"), 30),
                    new ServiceEntity("Underarms Waxing", "Quick and gentle underarm waxing.", new BigDecimal("15.00"), 15),
                    new ServiceEntity("Shoulders Waxing", "Shoulder hair removal.", new BigDecimal("20.00"), 15),
                    new ServiceEntity("Chest Waxing", "Smooth chest waxing.", new BigDecimal("30.00"), 20),
                    new ServiceEntity("Stomach Waxing", "Stomach hair removal.", new BigDecimal("15.00"), 15),
                    new ServiceEntity("Full Back Waxing", "Complete upper and lower back waxing.", new BigDecimal("45.00"), 30),
                    new ServiceEntity("Half Arms Waxing", "Forearm or upper arm wax.", new BigDecimal("20.00"), 20),
                    new ServiceEntity("Full Arms Waxing", "Entire arm hair removal.", new BigDecimal("30.00"), 30),
                    new ServiceEntity("Half Legs Waxing", "Lower or upper legs wax.", new BigDecimal("30.00"), 25),
                    new ServiceEntity("Full Legs Waxing", "Complete leg waxing.", new BigDecimal("45.00"), 40),
                    new ServiceEntity("Bikini Lines Waxing", "Clean bikini line tidy-up.", new BigDecimal("25.00"), 20),
                    new ServiceEntity("Brazilian Waxing", "Complete Brazilian waxing.", new BigDecimal("35.00"), 30),
                    new ServiceEntity("Toes Waxing", "Toe hair removal.", new BigDecimal("10.00"), 10),
                    new ServiceEntity("Knuckles Waxing", "Finger knuckles waxing.", new BigDecimal("10.00"), 10),
                    new ServiceEntity("Half Legs & Bikini Lines Combo", "Half legs wax paired with bikini line.", new BigDecimal("50.00"), 35),
                    new ServiceEntity("Full Legs & Bikini Lines Combo", "Full legs waxing paired with bikini line.", new BigDecimal("65.00"), 45),
                    new ServiceEntity("Half Legs & Brazilian Combo", "Half legs waxing paired with Brazilian.", new BigDecimal("60.00"), 45),
                    new ServiceEntity("Full Legs & Brazilian Combo", "Full legs waxing paired with full Brazilian.", new BigDecimal("75.00"), 55),
                    new ServiceEntity("Full Body Wax", "All-inclusive full body smooth waxing service.", new BigDecimal("160.00"), 90),

                    // Facials (Babor®)
                    new ServiceEntity("Express Facial", "Fast refreshing cleansing, gentle exfoliation, and replenishing hydration (Approx. 40 mins).", new BigDecimal("85.00"), 40),
                    new ServiceEntity("Hydra & Plump Facial", "Repairs moisture deficits in the skin, leaving skin thoroughly hydrated, plump and smooth.", new BigDecimal("110.00"), 60),
                    new ServiceEntity("Calm & Soothe Facial", "Designed for extremely dry sensitive skin, rosacea and dermatitis.", new BigDecimal("110.00"), 60),
                    new ServiceEntity("Collagen Infusion Facial", "Plump and densify skin with collagen boosting therapy & hyaluronic acid.", new BigDecimal("130.00"), 75),

                    // Specialty Services
                    new ServiceEntity("Eyelash Tinting", "Semi-permanent lash tinting for rich lashes.", new BigDecimal("17.00"), 20),
                    new ServiceEntity("Eyebrow Tinting", "Custom tinted eyebrows to naturally define and frame your eyes.", new BigDecimal("17.00"), 15),

                    // Gift Packages
                    new ServiceEntity("Rejuvenation for Him/Her", "Hydra & Plump Facial & Essential Manicure-Pedicure (Approx. 2.5 hrs).", new BigDecimal("165.00"), 150),
                    new ServiceEntity("Executive for Him/Her", "Essential Manicure-Pedicure & 60 Minutes Relaxing Body Massage (Approx. 2.5 hrs).", new BigDecimal("147.00"), 150)
            );

            serviceRepository.saveAll(menuServices);
            System.out.println("✅ Full salon services menu seeded into database!");
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

    /**
     * Seeds or synchronizes the 10 technicians according to exact store service capabilities:
     * - Tony: ALL services (regular staff in UI, can do Nail Art 1 of 2)
     * - Jenny Tran: ALL services (can do Nail Art 2 of 2)
     * - Michael Chen, David Nguyen: ALL services (except Nail Art: Manicure, Pedicure, Kids, Massage, Waxing, Facial)
     * - Jessica Miller, Lisa Anderson (Female): Body Massage (+ Pedicures & Kid Services)
     * - Hannah Brown: Waxing (+ Pedicures & Kid Services)
     * - Olivia Taylor: Facial Treatments (+ Pedicures & Kid Services)
     * - Emily Davis, Sarah Wilson: Manicures (+ Pedicures & Kid Services)
     */
    private void seedOrUpdateStaffMembers() {
        record StaffConfig(String username, String name, String email, String specialty) {}

        List<StaffConfig> staffConfigs = List.of(
                // 1. Tony: Can perform EVERY service (Nail Art Specialist 1 of 2)
                new StaffConfig("tony@jennysnailstudio.com", "Tony", "tony@jennysnailstudio.com", "ALL, NAIL_ART"),

                // 2. Jenny Tran: Full everything (Nail Art Specialist 2 of 2)
                new StaffConfig("jenny@jennysnailstudio.com", "Jenny Tran", "jenny@jennysnailstudio.com", "ALL, NAIL_ART"),

                // 3. Michael Chen: Full everything (all services except Nail Art)
                new StaffConfig("michael@jennysnailstudio.com", "Michael Chen", "michael@jennysnailstudio.com", "MANICURE, BODY_MASSAGE, WAXING, FACIAL"),

                // 4. David Nguyen: Full everything (all services except Nail Art)
                new StaffConfig("david@jennysnailstudio.com", "David Nguyen", "david@jennysnailstudio.com", "MANICURE, BODY_MASSAGE, WAXING, FACIAL"),

                // 5. Jessica Miller (Female): Body Massage (+ Pedicures & Kid Services)
                new StaffConfig("jessica@jennysnailstudio.com", "Jessica Miller", "jessica@jennysnailstudio.com", "BODY_MASSAGE"),

                // 6. Lisa Anderson (Female): Body Massage (+ Pedicures & Kid Services)
                new StaffConfig("lisa@jennysnailstudio.com", "Lisa Anderson", "lisa@jennysnailstudio.com", "BODY_MASSAGE"),

                // 7. Hannah Brown: Waxing (+ Pedicures & Kid Services)
                new StaffConfig("hannah@jennysnailstudio.com", "Hannah Brown", "hannah@jennysnailstudio.com", "WAXING"),

                // 8. Olivia Taylor: Facial Treatments (+ Pedicures & Kid Services)
                new StaffConfig("olivia@jennysnailstudio.com", "Olivia Taylor", "olivia@jennysnailstudio.com", "FACIAL"),

                // 9. Emily Davis: Manicures (+ Pedicures & Kid Services)
                new StaffConfig("emily@jennysnailstudio.com", "Emily Davis", "emily@jennysnailstudio.com", "MANICURE"),

                // 10. Sarah Wilson: Manicures (+ Pedicures & Kid Services)
                new StaffConfig("sarah@jennysnailstudio.com", "Sarah Wilson", "sarah@jennysnailstudio.com", "MANICURE")
        );

        for (StaffConfig config : staffConfigs) {
            UserEntity tech = userRepository.findByUsername(config.username()).orElseGet(UserEntity::new);
            tech.setUsername(config.username());
            tech.setName(config.name());
            tech.setEmail(config.email());
            tech.setSpecialty(config.specialty());
            tech.setRole("ROLE_NAIL_TECH");
            if (tech.getPassword() == null || tech.getPassword().isEmpty()) {
                tech.setPassword(passwordEncoder.encode("defaultPassword123"));
            }
            userRepository.save(tech);
        }
        System.out.println("✅ Configured all 10 salon technicians with customized service capabilities!");
    }

    private void seedTenantConfigs() {
        if (tenantConfigRepository.count() > 0) {
            return;
        }

        TenantConfig defaultConfig = new TenantConfig();
        defaultConfig.setTenantKey("default");
        defaultConfig.setSalonName("Jenny's Nail Studio");
        defaultConfig.setPrimaryColor("#e83e8c");
        defaultConfig.setSecondaryColor("#343a40");
        defaultConfig.setFontFamily("'Poppins', sans-serif");
        defaultConfig.setStepOrder("SERVICE,STAFF,STATION,DETAILS");
        defaultConfig.setEnableStationSelection(false);
        defaultConfig.setAvailableStations("Main Floor Chair 1,Main Floor Chair 2,VIP Window Suite,Quiet Spa Room");
        defaultConfig.setEnabledCategories("");
        defaultConfig.setShowAppointmentNotes(true);
        defaultConfig.setShowStaffQualificationTags(false);
        defaultConfig.setLayoutVariant("classic");
        defaultConfig.setDefaultTenant(true);

        TenantConfig luxeConfig = new TenantConfig();
        luxeConfig.setTenantKey("luxe");
        luxeConfig.setCustomDomain("luxe.jennysnailstudio.com");
        luxeConfig.setSalonName("Luxe Nail & Spa Sanctuary");
        luxeConfig.setPrimaryColor("#00b4d8");
        luxeConfig.setSecondaryColor("#03045e");
        luxeConfig.setFontFamily("'Playfair Display', Georgia, serif");
        luxeConfig.setStepOrder("SERVICE,STAFF,STATION,DETAILS");
        luxeConfig.setEnableStationSelection(true);
        luxeConfig.setAvailableStations("Private Zen Suite A,Private Zen Suite B,Window Lounge Station 1,Window Lounge Station 2");
        luxeConfig.setEnabledCategories("Manicures,Pedicures,Mani / Pedi Combos,Nail Enhancement & Art,Facial Treatments");
        luxeConfig.setShowAppointmentNotes(true);
        luxeConfig.setShowStaffQualificationTags(false);
        luxeConfig.setLayoutVariant("blue-sky");
        luxeConfig.setDefaultTenant(false);

        tenantConfigRepository.saveAll(List.of(defaultConfig, luxeConfig));
        System.out.println("✅ Seeded default and sample Luxe multi-tenant configurations!");
    }
}
