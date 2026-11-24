package com.example.demo.controller;

import com.example.demo.entity.Appointment;
import com.example.demo.entity.AppointmentTransaction;
import com.example.demo.entity.Customer;
import com.example.demo.entity.StaffDayOff;
import com.example.demo.entity.UserEntity;
import com.example.demo.repository.AppointmentRepository;
import com.example.demo.repository.AppointmentTransactionRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.StaffDayOffRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/staff-portal")
@CrossOrigin(origins = "*")
public class StaffPortalController {

    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentTransactionRepository transactionRepository;
    private final CustomerRepository customerRepository;
    private final StaffDayOffRepository staffDayOffRepository;
    private final TenantContextResolver tenantContextResolver;

    @Autowired(required = false)
    private PasswordEncoder passwordEncoder;

    public void setPasswordEncoder(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Autowired
    public StaffPortalController(
            UserRepository userRepository,
            AppointmentRepository appointmentRepository,
            AppointmentTransactionRepository transactionRepository,
            CustomerRepository customerRepository,
            @Autowired(required = false) StaffDayOffRepository staffDayOffRepository,
            TenantContextResolver tenantContextResolver
    ) {
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.transactionRepository = transactionRepository;
        this.customerRepository = customerRepository;
        this.staffDayOffRepository = staffDayOffRepository;
        this.tenantContextResolver = tenantContextResolver;
    }

    /**
     * Returns the authenticated technician's own schedule, client nail formulas/allergy flags,
     * day-off status, and personal commission + tips tracker for today and the past 7 days.
     */
    @GetMapping("/me")
    @PreAuthorize("hasAnyAuthority('ROLE_NAIL_TECH', 'NAIL_TECH', 'ROLE_STAFF', 'STAFF', 'ROLE_ADMIN', 'ADMIN')")
    public ResponseEntity<?> getMyPortalData(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Long staffId,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equalsIgnoreCase(auth.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Authentication required."));
        }

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()) || "ADMIN".equalsIgnoreCase(a.getAuthority()));

        UserEntity tech = resolveTargetTech(tenantId, auth.getName(), isAdmin ? staffId : null);
        if (tech == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Technician profile not found for this salon."));
        }

        LocalDate targetDate = LocalDate.now();
        if (date != null && !date.isBlank()) {
            try {
                targetDate = LocalDate.parse(date.trim());
            } catch (Exception ignored) {}
        }

        Long techId = tech.getId();
        BigDecimal commRate = (tech.getCommissionRate() != null && tech.getCommissionRate().compareTo(BigDecimal.ZERO) >= 0)
                ? tech.getCommissionRate()
                : new BigDecimal("60.00");

        // 1. Check Day-Off status for selected date
        boolean isOffToday = false;
        String dayOffReason = null;
        if (staffDayOffRepository != null) {
            Optional<StaffDayOff> offOpt = staffDayOffRepository.findByTenantIdAndStaffIdAndOffDate(tenantId, techId, targetDate);
            if (offOpt.isPresent()) {
                isOffToday = true;
                dayOffReason = offOpt.get().getReason();
            }
        }

        // 2. Fetch ONLY this technician's appointments on targetDate
        List<Appointment> dayAppts = appointmentRepository.findByTenantIdAndAppointmentTimeBetween(
                tenantId,
                targetDate.atStartOfDay(),
                targetDate.plusDays(1).atStartOfDay()
        );
        if (dayAppts == null) dayAppts = Collections.emptyList();

        List<Appointment> myDayAppts = dayAppts.stream()
                .filter(a -> a.getNailTech() != null && techId.equals(a.getNailTech().getId()))
                .filter(a -> a.getStatus() != Appointment.AppointmentStatus.CANCELLED)
                .sorted(Comparator.comparing(Appointment::getAppointmentTime))
                .toList();

        // Enrich appointments with customer nail profile, formulas, and medical flags
        List<Map<String, Object>> enrichedAppointments = new ArrayList<>();
        for (Appointment apt : myDayAppts) {
            Customer cust = findCustomerForAppointment(tenantId, apt);

            String medicalFlags = (apt.getMedicalFlags() != null && !apt.getMedicalFlags().isBlank())
                    ? apt.getMedicalFlags()
                    : (cust != null ? cust.getMedicalFlags() : null);
            String colorFormula = (apt.getColorFormula() != null && !apt.getColorFormula().isBlank())
                    ? apt.getColorFormula()
                    : (cust != null ? cust.getColorFormula() : null);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", apt.getId());
            item.put("appointmentTime", apt.getAppointmentTime() != null ? apt.getAppointmentTime().toString() : null);
            item.put("status", apt.getStatus() != null ? apt.getStatus().name() : "PENDING");
            item.put("customerName", apt.getCustomerName());
            item.put("customerPhone", apt.getCustomerPhone());
            item.put("customerEmail", apt.getCustomerEmail());
            item.put("serviceName", apt.getService() != null ? apt.getService().getName() : "Salon Service");
            item.put("servicePrice", apt.getService() != null && apt.getService().getPrice() != null ? apt.getService().getPrice() : BigDecimal.ZERO);
            item.put("durationMinutes", apt.getService() != null && apt.getService().getDurationMinutes() != null ? apt.getService().getDurationMinutes() : 45);
            item.put("notes", apt.getNotes());
            item.put("medicalFlags", medicalFlags);
            item.put("colorFormula", colorFormula);
            item.put("nailShape", cust != null ? cust.getNailShape() : null);
            item.put("nailLength", cust != null ? cust.getNailLength() : null);
            item.put("nailSystem", cust != null ? cust.getNailSystem() : null);
            item.put("favoriteColors", cust != null ? cust.getFavoriteColors() : null);
            item.put("cuticleCareNotes", cust != null ? cust.getCuticleCareNotes() : null);
            enrichedAppointments.add(item);
        }

        // 3. Personal Earnings & Tips Tracker (Day + Rolling 7-Day Week)
        Map<String, Object> dayEarnings = computeTechEarnings(
                tenantId, techId, commRate,
                targetDate.atStartOfDay(),
                targetDate.plusDays(1).atStartOfDay()
        );
        Map<String, Object> weekEarnings = computeTechEarnings(
                tenantId, techId, commRate,
                targetDate.minusDays(6).atStartOfDay(),
                targetDate.plusDays(1).atStartOfDay()
        );

        Map<String, Object> staffInfo = new LinkedHashMap<>();
        staffInfo.put("id", tech.getId());
        staffInfo.put("name", (tech.getName() != null && !tech.getName().isBlank()) ? tech.getName() : tech.getUsername());
        staffInfo.put("email", tech.getEmail() != null ? tech.getEmail() : tech.getUsername());
        staffInfo.put("specialty", tech.getSpecialty());
        staffInfo.put("commissionRate", commRate);
        staffInfo.put("isOffToday", isOffToday);
        staffInfo.put("dayOffReason", dayOffReason);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tenantId", tenantId);
        response.put("date", targetDate.toString());
        response.put("staff", staffInfo);
        response.put("appointments", enrichedAppointments);
        response.put("dayEarnings", dayEarnings);
        response.put("weekEarnings", weekEarnings);

        return ResponseEntity.ok(response);
    }

    /**
     * Allows a technician to update colorFormula, medicalFlags, or notes on their own assigned appointment.
     */
    @Transactional
    @PatchMapping("/appointments/{id}/note")
    @PreAuthorize("hasAnyAuthority('ROLE_NAIL_TECH', 'NAIL_TECH', 'ROLE_STAFF', 'STAFF', 'ROLE_ADMIN', 'ADMIN')")
    public ResponseEntity<?> updateMyAppointmentNote(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Authentication required."));
        }

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()) || "ADMIN".equalsIgnoreCase(a.getAuthority()));

        Optional<Appointment> apptOpt = appointmentRepository.findByTenantIdAndId(tenantId, id);
        if (apptOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Appointment not found."));
        }
        Appointment appt = apptOpt.get();

        if (!isAdmin) {
            UserEntity currentTech = resolveTargetTech(tenantId, auth.getName(), null);
            if (currentTech == null || appt.getNailTech() == null || !currentTech.getId().equals(appt.getNailTech().getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "You can only update notes for your own assigned appointments."));
            }
        }

        if (body != null) {
            if (body.containsKey("colorFormula")) {
                appt.setColorFormula(body.get("colorFormula") != null ? body.get("colorFormula").trim() : null);
            }
            if (body.containsKey("medicalFlags")) {
                appt.setMedicalFlags(body.get("medicalFlags") != null ? body.get("medicalFlags").trim() : null);
            }
            if (body.containsKey("notes")) {
                appt.setNotes(body.get("notes") != null ? body.get("notes").trim() : null);
            }
        }
        appointmentRepository.save(appt);

        // Sync colorFormula & medicalFlags back to customer profile
        Customer cust = findCustomerForAppointment(tenantId, appt);
        if (cust != null && body != null) {
            boolean custChanged = false;
            if (body.containsKey("colorFormula")) {
                cust.setColorFormula(appt.getColorFormula());
                custChanged = true;
            }
            if (body.containsKey("medicalFlags")) {
                cust.setMedicalFlags(appt.getMedicalFlags());
                custChanged = true;
            }
            if (custChanged) {
                customerRepository.save(cust);
            }
        }

        return ResponseEntity.ok(Map.of(
                "message", "Client formula & notes updated.",
                "appointmentId", appt.getId(),
                "colorFormula", appt.getColorFormula() != null ? appt.getColorFormula() : "",
                "medicalFlags", appt.getMedicalFlags() != null ? appt.getMedicalFlags() : "",
                "notes", appt.getNotes() != null ? appt.getNotes() : ""
        ));
    }

    /**
     * Allows an authenticated staff member (or admin) to change their own password
     * (e.g., replacing the initial defaultPassword123) after verifying their current password.
     */
    @PostMapping("/change-password")
    @PreAuthorize("hasAnyAuthority('ROLE_NAIL_TECH', 'NAIL_TECH', 'ROLE_STAFF', 'STAFF', 'ROLE_ADMIN', 'ADMIN')")
    @Transactional
    public ResponseEntity<?> changeMyPassword(
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equalsIgnoreCase(auth.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Authentication required."));
        }

        String currentPassword = body != null ? body.get("currentPassword") : null;
        String newPassword = body != null ? body.get("newPassword") : null;

        if (currentPassword == null || currentPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Current password is required."));
        }
        if (newPassword == null || newPassword.trim().length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("message", "New password must be at least 6 characters."));
        }

        UserEntity user = resolveTargetTech(tenantId, auth.getName(), null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Account not found."));
        }

        if (passwordEncoder == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Password service unavailable."));
        }

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Current password is incorrect."));
        }

        user.setPassword(passwordEncoder.encode(newPassword.trim()));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Password updated successfully!"));
    }

    private UserEntity resolveTargetTech(String tenantId, String username, Long requestedStaffId) {
        if (requestedStaffId != null) {
            return userRepository.findByTenantIdAndId(tenantId, requestedStaffId).orElse(null);
        }
        Optional<UserEntity> byTenant = userRepository.findByTenantIdAndUsernameIgnoreCase(tenantId, username);
        if (byTenant.isPresent()) return byTenant.get();
        Optional<UserEntity> byEmail = userRepository.findByTenantIdAndEmailIgnoreCase(tenantId, username);
        if (byEmail.isPresent()) return byEmail.get();
        return userRepository.findByUsername(username).orElse(null);
    }

    private Customer findCustomerForAppointment(String tenantId, Appointment apt) {
        if (apt.getCustomerEmail() != null && !apt.getCustomerEmail().isBlank()) {
            Optional<Customer> byEmail = customerRepository.findByTenantIdAndEmail(tenantId, apt.getCustomerEmail().trim());
            if (byEmail.isPresent()) return byEmail.get();
        }
        return null;
    }

    private Map<String, Object> computeTechEarnings(
            String tenantId,
            Long techId,
            BigDecimal commRate,
            LocalDateTime startDt,
            LocalDateTime endDt
    ) {
        List<AppointmentTransaction> txs = transactionRepository
                .findAllByTenantIdAndCreatedAtBetweenOrderByCreatedAtDesc(tenantId, startDt, endDt);
        if (txs == null) txs = Collections.emptyList();

        BigDecimal serviceSales = BigDecimal.ZERO;
        BigDecimal cardTips = BigDecimal.ZERO;
        BigDecimal cashTips = BigDecimal.ZERO;
        int txCount = 0;

        for (AppointmentTransaction tx : txs) {
            if (tx.getNailTechId() == null || !techId.equals(tx.getNailTechId())) continue;
            txCount++;
            BigDecimal sub = tx.getSubtotal() != null ? tx.getSubtotal() : BigDecimal.ZERO;
            BigDecimal tip = tx.getTipAmount() != null ? tx.getTipAmount() : BigDecimal.ZERO;
            serviceSales = serviceSales.add(sub);
            if ("CASH".equalsIgnoreCase(tx.getPaymentMethod())) {
                cashTips = cashTips.add(tip);
            } else {
                cardTips = cardTips.add(tip);
            }
        }

        BigDecimal totalTips = cardTips.add(cashTips);
        BigDecimal commissionEarned = serviceSales
                .multiply(commRate)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal totalTakeHome = commissionEarned.add(totalTips);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("serviceSales", serviceSales);
        res.put("commissionRate", commRate);
        res.put("commissionEarned", commissionEarned);
        res.put("cardTips", cardTips);
        res.put("cashTips", cashTips);
        res.put("totalTips", totalTips);
        res.put("totalTakeHome", totalTakeHome);
        res.put("completedCount", txCount);
        return res;
    }
}
