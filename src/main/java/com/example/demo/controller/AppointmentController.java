package com.example.demo.controller;

import com.example.demo.dto.BookingRequestDto;
import com.example.demo.entity.Appointment;
import com.example.demo.entity.Customer;
import com.example.demo.entity.ServiceEntity;
import com.example.demo.entity.TenantConfig;
import com.example.demo.entity.UserEntity;
import com.example.demo.repository.AppointmentRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.ServiceRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import com.example.demo.entity.Customer;
import com.example.demo.service.AppointmentAuditService;
import com.example.demo.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping({"/api/appointments", "/api/bookings"})
@CrossOrigin(origins = "*")
public class AppointmentController {

    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final TenantContextResolver tenantContextResolver;
    private final AppointmentAuditService auditService;
    private final NotificationService notificationService;

    private static final java.util.regex.Pattern STRICT_EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}$");

    private boolean isValidEmail(String email) {
        return email != null && STRICT_EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    @Autowired
    public AppointmentController(
            AppointmentRepository appointmentRepository,
            CustomerRepository customerRepository,
            ServiceRepository serviceRepository,
            UserRepository userRepository,
            TenantContextResolver tenantContextResolver,
            @Autowired(required = false) AppointmentAuditService auditService,
            @Autowired(required = false) NotificationService notificationService
    ) {
        this.appointmentRepository = appointmentRepository;
        this.customerRepository = customerRepository;
        this.serviceRepository = serviceRepository;
        this.userRepository = userRepository;
        this.tenantContextResolver = tenantContextResolver;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    public AppointmentController(
            AppointmentRepository appointmentRepository,
            CustomerRepository customerRepository,
            ServiceRepository serviceRepository,
            UserRepository userRepository,
            TenantContextResolver tenantContextResolver
    ) {
        this(appointmentRepository, customerRepository, serviceRepository, userRepository, tenantContextResolver, null, null);
    }

    @GetMapping
    public List<Appointment> getAllAppointments(HttpServletRequest request) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        return appointmentRepository.findAllByTenantId(tenantId);
    }

    /**
     * Tenant-scoped dynamic availability pre-check endpoint.
     * Checks available time slots for a given date, duration, and optional technician / assignment mode.
     */
    @GetMapping("/availability")
    public ResponseEntity<?> getAvailableSlots(
            @RequestParam String date,
            @RequestParam(defaultValue = "45") int durationMinutes,
            @RequestParam(required = false) Long techId,
            @RequestParam(required = false) List<Long> techIds,
            @RequestParam(required = false) List<Long> serviceIds,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);

        java.time.LocalDate targetDate;
        try {
            targetDate = java.time.LocalDate.parse(date);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid date format. Expected YYYY-MM-DD."));
        }

        // Closed day check: Sundays are closed (dayOfWeek == 7)
        if (targetDate.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            return ResponseEntity.ok(Map.of(
                    "date", date,
                    "isClosed", true,
                    "reason", "Sunday is closed. Store hours: Mon - Sat 10:00 AM - 7:00 PM.",
                    "availableSlots", List.of()
            ));
        }

        LocalDateTime dayStart = targetDate.atTime(10, 0);
        LocalDateTime dayEnd = targetDate.atTime(19, 0);

        // Fetch all non-cancelled appointments for this tenant on this date
        List<Appointment> dayAppointments = appointmentRepository.findByTenantIdAndAppointmentTimeBetween(
                tenantId,
                targetDate.atStartOfDay(),
                targetDate.plusDays(1).atStartOfDay()
        ).stream()
         .filter(a -> a.getStatus() != Appointment.AppointmentStatus.CANCELLED)
         .toList();

        // Get active tenant technicians
        List<UserEntity> tenantTechs = userRepository.findAllByTenantIdAndRole(tenantId, "ROLE_NAIL_TECH");

        // Determine which technicians are being checked
        List<UserEntity> targetTechs = new ArrayList<>();
        if (techId != null) {
            userRepository.findByTenantIdAndId(tenantId, techId).ifPresent(targetTechs::add);
        } else if (techIds != null && !techIds.isEmpty()) {
            for (Long tid : techIds) {
                userRepository.findByTenantIdAndId(tenantId, tid).ifPresent(targetTechs::add);
            }
        }

        // If no specific tech requested, "Anyone Available" mode evaluates against all qualified techs
        if (targetTechs.isEmpty()) {
            targetTechs.addAll(tenantTechs);
        }

        // Generate all standard 15-minute slots between 10:00 AM and 7:00 PM
        List<Map<String, Object>> slotResults = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        int effectiveDuration = Math.max(durationMinutes, 15);

        for (int hour = 10; hour <= 19; hour++) {
            int maxMin = (hour == 19) ? 0 : 45;
            for (int min = 0; min <= maxMin; min += 15) {
                LocalDateTime slotStart = targetDate.atTime(hour, min);
                LocalDateTime slotEnd = slotStart.plusMinutes(effectiveDuration);

                // Skip slots that exceed store closing time (19:00 / 7:00 PM)
                if (slotEnd.isAfter(dayEnd.plusMinutes(15))) {
                    continue;
                }

                // Past-time buffer: if date is today, slot must be at least 15 minutes after current local time
                boolean isPast = false;
                if (targetDate.isEqual(now.toLocalDate())) {
                    if (!slotStart.isAfter(now.plusMinutes(15))) {
                        isPast = true;
                    }
                } else if (targetDate.isBefore(now.toLocalDate())) {
                    isPast = true;
                }

                String hh = String.format("%02d", hour);
                String mm = String.format("%02d", min);
                String value24 = hh + ":" + mm + ":00";
                String period = hour >= 12 ? "PM" : "AM";
                int hour12 = hour > 12 ? hour - 12 : (hour == 0 ? 12 : hour);
                String label12 = String.format("%d:%02d %s", hour12, min, period);

                boolean isAvailable = !isPast;
                String conflictReason = isPast ? "Slot has passed" : null;

                if (isAvailable) {
                    if (techId != null) {
                        // Single specified technician: check if this tech has any overlapping appointment
                        boolean techHasConflict = dayAppointments.stream().anyMatch(a -> {
                            if (a.getNailTech() == null || !a.getNailTech().getId().equals(techId)) {
                                return false;
                            }
                            return hasOverlap(a, slotStart, slotEnd);
                        });
                        if (techHasConflict) {
                            isAvailable = false;
                            conflictReason = "Technician booked";
                        }
                    } else if (techIds != null && !techIds.isEmpty()) {
                        // Specific Staff Per Service: verify ALL selected technicians are individually free for their durations
                        // or free sequentially
                        boolean anyAssignedTechBusy = targetTechs.stream().anyMatch(t ->
                            dayAppointments.stream().anyMatch(a ->
                                a.getNailTech() != null &&
                                a.getNailTech().getId().equals(t.getId()) &&
                                hasOverlap(a, slotStart, slotEnd)
                            )
                        );
                        if (anyAssignedTechBusy) {
                            isAvailable = false;
                            conflictReason = "Assigned staff has a scheduling conflict";
                        }
                    } else {
                        // "Anyone Available": available if AT LEAST ONE qualified technician is free
                        boolean atLeastOneTechFree = targetTechs.stream().anyMatch(t ->
                            dayAppointments.stream().noneMatch(a ->
                                a.getNailTech() != null &&
                                a.getNailTech().getId().equals(t.getId()) &&
                                hasOverlap(a, slotStart, slotEnd)
                            )
                        );
                        if (!atLeastOneTechFree && !targetTechs.isEmpty()) {
                            isAvailable = false;
                            conflictReason = "All technicians busy";
                        }
                    }
                }

                Map<String, Object> slot = new LinkedHashMap<>();
                slot.put("value", value24);
                slot.put("label", label12);
                slot.put("available", isAvailable);
                slot.put("isPast", isPast);
                if (conflictReason != null) {
                    slot.put("reason", conflictReason);
                }
                slotResults.add(slot);
            }
        }

        return ResponseEntity.ok(Map.of(
                "date", date,
                "isClosed", false,
                "durationMinutes", effectiveDuration,
                "availableSlots", slotResults
        ));
    }

    private boolean hasOverlap(Appointment a, LocalDateTime slotStart, LocalDateTime slotEnd) {
        if (a.getAppointmentTime() == null) return false;
        LocalDateTime apptStart = a.getAppointmentTime();
        int apptDuration = (a.getService() != null && a.getService().getDurationMinutes() != null)
                ? a.getService().getDurationMinutes()
                : 45;
        LocalDateTime apptEnd = apptStart.plusMinutes(apptDuration);
        return slotStart.isBefore(apptEnd) && apptEnd.isAfter(apptStart) && slotEnd.isAfter(apptStart);
    }

    @PostMapping
    public ResponseEntity<?> bookAppointment(@RequestBody BookingRequestDto bookingRequest, HttpServletRequest request) {
        if (bookingRequest.getAppointmentTime() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Please select an appointment date and time."));
        }

        String tenantId = tenantContextResolver.resolveTenantId(request);

        // For manual/walk-in admin overrides, provide friendly defaults if omitted
        if (Boolean.TRUE.equals(bookingRequest.getManualOverride())) {
            if (bookingRequest.getCustomerName() == null || bookingRequest.getCustomerName().isBlank()) {
                bookingRequest.setCustomerName("Walk-In Guest");
            }
            if (bookingRequest.getCustomerEmail() == null || bookingRequest.getCustomerEmail().isBlank()) {
                bookingRequest.setCustomerEmail("walkin@salon.com");
            }
            if (bookingRequest.getCustomerPhone() == null || bookingRequest.getCustomerPhone().isBlank()) {
                bookingRequest.setCustomerPhone("(555) 000-0000");
            }
        }

        // Validate technician belongs to active tenant and has no conflict at the requested time slot
        UserEntity resolvedTech = null;
        if (bookingRequest.getNailTech() != null && bookingRequest.getNailTech().getId() != null) {
            Long techId = bookingRequest.getNailTech().getId();
            Optional<UserEntity> tenantTech = userRepository.findByTenantIdAndId(tenantId, techId);
            if (tenantTech.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Selected technician does not belong to this salon."));
            }
            resolvedTech = tenantTech.get();

            boolean hasConflict = !Boolean.TRUE.equals(bookingRequest.getManualOverride()) &&
                    appointmentRepository.existsByTenantIdAndNailTechIdAndAppointmentTimeAndStatusNot(
                            tenantId,
                            techId,
                            bookingRequest.getAppointmentTime(),
                            Appointment.AppointmentStatus.CANCELLED
                    );
            if (hasConflict) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "This technician is already booked at the selected time slot. Please choose another time or technician."));
            }
        }

        boolean isMultiGuestRequest = bookingRequest.getGuests() != null && !bookingRequest.getGuests().isEmpty();
        boolean isTopLevelMultiService = bookingRequest.getServiceIds() != null && bookingRequest.getServiceIds().size() > 1;

        // Branch 1: Multi-guest or multi-service booking flow
        if (isMultiGuestRequest || isTopLevelMultiService) {
            List<BookingRequestDto.GuestBookingItem> guestItems = new ArrayList<>();
            if (isMultiGuestRequest) {
                guestItems.addAll(bookingRequest.getGuests());
            } else {
                BookingRequestDto.GuestBookingItem singleGuest = new BookingRequestDto.GuestBookingItem();
                singleGuest.setGuestNumber(1);
                singleGuest.setCustomerName(bookingRequest.getCustomerName());
                singleGuest.setCustomerPhone(bookingRequest.getCustomerPhone());
                singleGuest.setCustomerEmail(bookingRequest.getCustomerEmail());
                singleGuest.setNotes(bookingRequest.getNotes());
                singleGuest.setServiceIds(bookingRequest.getServiceIds());
                guestItems.add(singleGuest);
            }

            List<Appointment> savedAppointments = new ArrayList<>();
            List<Map<String, Object>> guestReceipts = new ArrayList<>();
            BigDecimal grandTotal = BigDecimal.ZERO;
            int totalDurationMinutes = 0;

            for (int i = 0; i < guestItems.size(); i++) {
                BookingRequestDto.GuestBookingItem guest = guestItems.get(i);
                int guestNum = guest.getGuestNumber() != null ? guest.getGuestNumber() : (i + 1);
                String label = "Guest " + guestNum;

                if (guest.getCustomerName() == null || guest.getCustomerName().isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("message", label + " name is required."));
                }
                if (guest.getCustomerEmail() == null || guest.getCustomerEmail().isBlank() || !isValidEmail(guest.getCustomerEmail())) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Please enter a valid email address for " + label + " (e.g. name@example.com)."));
                }
                if (guest.getCustomerPhone() == null || guest.getCustomerPhone().isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("message", label + " phone number is required."));
                }

                List<Long> requestedServiceIds = extractGuestServiceIds(guest);
                if (requestedServiceIds.isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Please select at least one service for " + label + "."));
                }

                List<ServiceEntity> guestServices = new ArrayList<>();
                BigDecimal guestSubtotal = BigDecimal.ZERO;
                int guestDuration = 0;

                for (Long serviceId : requestedServiceIds) {
                    Optional<ServiceEntity> tenantService = serviceRepository.findByTenantIdAndId(tenantId, serviceId);
                    if (tenantService.isEmpty()) {
                        return ResponseEntity.badRequest().body(Map.of("message", "Selected service does not belong to this salon."));
                    }
                    ServiceEntity srv = tenantService.get();
                    guestServices.add(srv);
                    if (srv.getPrice() != null) {
                        guestSubtotal = guestSubtotal.add(srv.getPrice());
                    }
                    guestDuration += (srv.getDurationMinutes() != null ? srv.getDurationMinutes() : 45);
                }

                Customer guestCustomer = syncCustomerRecord(
                        guest.getCustomerName(),
                        guest.getCustomerEmail(),
                        guest.getCustomerPhone(),
                        tenantId
                );

                for (ServiceEntity srv : guestServices) {
                    Appointment appt = new Appointment();
                    appt.setTenantId(tenantId);
                    appt.setCustomerName(guest.getCustomerName().trim());
                    appt.setCustomerEmail(guest.getCustomerEmail().trim());
                    appt.setCustomerPhone(guest.getCustomerPhone().trim());
                    appt.setAppointmentTime(bookingRequest.getAppointmentTime());
                    appt.setStationPreference(bookingRequest.getStationPreference());
                    appt.setCancellationToken(UUID.randomUUID().toString());

                    // Check if per-service staff selection was assigned for this service
                    UserEntity serviceSpecificTech = resolvedTech;
                    if (guest.getServiceStaffAssignments() != null && srv.getId() != null) {
                        Long assignedTechId = guest.getServiceStaffAssignments().get(String.valueOf(srv.getId()));
                        if (assignedTechId == null) {
                            // Try Long key if any
                            for (Map.Entry<String, Long> entry : guest.getServiceStaffAssignments().entrySet()) {
                                if (String.valueOf(srv.getId()).equals(entry.getKey())) {
                                    assignedTechId = entry.getValue();
                                    break;
                                }
                            }
                        }
                        if (assignedTechId != null) {
                            serviceSpecificTech = userRepository.findByTenantIdAndId(tenantId, assignedTechId).orElse(resolvedTech);
                        }
                    }

                    appt.setNailTech(serviceSpecificTech);
                    appt.setService(srv);

                    String baseNotes = guest.getNotes() != null ? guest.getNotes().trim() : "";
                    if (guestItems.size() > 1) {
                        String prefix = "[" + label + "]";
                        appt.setNotes(baseNotes.isEmpty() ? prefix : prefix + " " + baseNotes);
                    } else {
                        appt.setNotes(baseNotes);
                    }

                    Appointment saved = appointmentRepository.save(appt);
                    savedAppointments.add(saved);

                    if (auditService != null) {
                        String performedBy = Boolean.TRUE.equals(bookingRequest.getManualOverride())
                                ? auditService.resolvePerformedBy("ADMIN")
                                : auditService.resolvePerformedBy("CUSTOMER");
                        auditService.logActivity(
                                tenantId,
                                guestCustomer != null ? guestCustomer.getId() : null,
                                saved.getId(),
                                saved.getCustomerName(),
                                saved.getCustomerEmail(),
                                saved.getCustomerPhone(),
                                "BOOKED",
                                performedBy,
                                saved.getNotes()
                        );
                    }
                }

                grandTotal = grandTotal.add(guestSubtotal);
                totalDurationMinutes += guestDuration;

                Map<String, Object> guestSummary = new LinkedHashMap<>();
                guestSummary.put("guestNumber", guestNum);
                guestSummary.put("customerName", guest.getCustomerName().trim());
                guestSummary.put("customerPhone", guest.getCustomerPhone().trim());
                guestSummary.put("customerEmail", guest.getCustomerEmail().trim());
                guestSummary.put("notes", guest.getNotes() != null ? guest.getNotes().trim() : "");
                guestSummary.put("services", guestServices);
                guestSummary.put("subtotal", guestSubtotal);
                guestSummary.put("durationMinutes", guestDuration);
                guestReceipts.add(guestSummary);
            }

            Map<String, Object> responseBody = new LinkedHashMap<>();
            Appointment primary = savedAppointments.isEmpty() ? null : savedAppointments.get(0);
            responseBody.put("id", primary != null ? primary.getId() : null);
            responseBody.put("tenantId", tenantId);
            responseBody.put("appointmentTime", bookingRequest.getAppointmentTime());
            responseBody.put("stationPreference", bookingRequest.getStationPreference());
            responseBody.put("nailTech", resolvedTech);
            responseBody.put("guests", guestReceipts);
            responseBody.put("grandTotal", grandTotal);
            responseBody.put("totalDurationMinutes", totalDurationMinutes);
            responseBody.put("appointments", savedAppointments);

            if (notificationService != null) {
                try {
                    notificationService.sendMultiGuestBookingConfirmations(
                            savedAppointments,
                            guestReceipts,
                            tenantId,
                            grandTotal
                    );
                } catch (Exception ignored) {
                }
            }

            return ResponseEntity.ok(responseBody);
        }

        // Branch 2: Standard single-service booking (backward compatible)
        if (bookingRequest.getCustomerName() == null || bookingRequest.getCustomerName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Customer name is required."));
        }
        if (bookingRequest.getCustomerEmail() == null || bookingRequest.getCustomerEmail().isBlank() || !isValidEmail(bookingRequest.getCustomerEmail())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Please enter a valid email address (e.g. name@example.com)."));
        }
        if (bookingRequest.getCustomerPhone() == null || bookingRequest.getCustomerPhone().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Customer phone number is required."));
        }

        Long singleServiceId = null;
        if (bookingRequest.getService() != null && bookingRequest.getService().getId() != null) {
            singleServiceId = bookingRequest.getService().getId();
        } else if (bookingRequest.getServiceIds() != null && !bookingRequest.getServiceIds().isEmpty()) {
            singleServiceId = bookingRequest.getServiceIds().get(0);
        }

        if (singleServiceId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Please select a service."));
        }

        Optional<ServiceEntity> tenantService = serviceRepository.findByTenantIdAndId(tenantId, singleServiceId);
        if (tenantService.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Selected service does not belong to this salon."));
        }

        Customer customer = syncCustomerRecord(
                bookingRequest.getCustomerName(),
                bookingRequest.getCustomerEmail(),
                bookingRequest.getCustomerPhone(),
                tenantId
        );

        Appointment appointment = new Appointment();
        appointment.setTenantId(tenantId);
        appointment.setCustomerName(bookingRequest.getCustomerName().trim());
        appointment.setCustomerEmail(bookingRequest.getCustomerEmail().trim());
        appointment.setCustomerPhone(bookingRequest.getCustomerPhone().trim());
        appointment.setAppointmentTime(bookingRequest.getAppointmentTime());
        appointment.setNotes(bookingRequest.getNotes());
        appointment.setStationPreference(bookingRequest.getStationPreference());
        appointment.setCancellationToken(UUID.randomUUID().toString());
        appointment.setService(tenantService.get());
        appointment.setNailTech(resolvedTech);

        Appointment saved = appointmentRepository.save(appointment);

        if (auditService != null) {
            String performedBy = Boolean.TRUE.equals(bookingRequest.getManualOverride())
                    ? auditService.resolvePerformedBy("ADMIN")
                    : auditService.resolvePerformedBy("CUSTOMER");
            auditService.logActivity(
                    tenantId,
                    customer != null ? customer.getId() : null,
                    saved.getId(),
                    saved.getCustomerName(),
                    saved.getCustomerEmail(),
                    saved.getCustomerPhone(),
                    "BOOKED",
                    performedBy,
                    saved.getNotes()
            );
        }

        if (notificationService != null) {
            try {
                notificationService.sendBookingConfirmation(saved);
            } catch (Exception ignored) {
            }
        }

        return ResponseEntity.ok(saved);
    }

    private List<Long> extractGuestServiceIds(BookingRequestDto.GuestBookingItem guest) {
        List<Long> ids = new ArrayList<>();
        if (guest.getServiceIds() != null) {
            for (Long id : guest.getServiceIds()) {
                if (id != null && !ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        if (guest.getServices() != null) {
            for (ServiceEntity s : guest.getServices()) {
                if (s != null && s.getId() != null && !ids.contains(s.getId())) {
                    ids.add(s.getId());
                }
            }
        }
        return ids;
    }

    private Customer syncCustomerRecord(String name, String email, String phone) {
        return syncCustomerRecord(name, email, phone, "default");
    }

    private Customer syncCustomerRecord(String name, String email, String phone, String tenantId) {
        if (email == null || email.isBlank()) {
            return null;
        }
        try {
            Customer customer = customerRepository.findByEmail(email.trim())
                    .orElseGet(Customer::new);
            customer.setName(name != null ? name.trim() : "");
            customer.setEmail(email.trim());
            customer.setPhone(phone != null ? phone.trim() : "");
            if (customer.getTenantId() == null || customer.getTenantId().isBlank()) {
                customer.setTenantId(tenantId != null ? tenantId : "default");
            }
            return customerRepository.save(customer);
        } catch (Exception ignored) {
            return null;
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateAppointmentStatus(
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false, defaultValue = "true") Boolean notifyCustomer,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        String effectiveStatus = status;
        String effectiveReason = reason != null ? reason : notes;
        boolean shouldNotify = (notifyCustomer != null) ? notifyCustomer : true;
        if (body != null) {
            if (effectiveStatus == null && body.containsKey("status")) {
                effectiveStatus = String.valueOf(body.get("status"));
            }
            if (effectiveReason == null) {
                if (body.containsKey("reason")) effectiveReason = String.valueOf(body.get("reason"));
                else if (body.containsKey("notes")) effectiveReason = String.valueOf(body.get("notes"));
            }
            if (body.containsKey("notifyCustomer")) {
                shouldNotify = Boolean.parseBoolean(String.valueOf(body.get("notifyCustomer")));
            }
        }
        if (effectiveStatus == null || effectiveStatus.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Status parameter is required."));
        }

        String tenantId = tenantContextResolver.resolveTenantId(request);
        final String finalStatus = effectiveStatus.trim();
        final String finalReason = effectiveReason != null ? effectiveReason.trim() : null;
        final boolean finalShouldNotify = shouldNotify;

        return appointmentRepository.findByTenantIdAndId(tenantId, id)
                .map(appointment -> {
                    try {
                        Appointment.AppointmentStatus newStatus = Appointment.AppointmentStatus.valueOf(finalStatus.toUpperCase());
                        appointment.setStatus(newStatus);
                        if (finalReason != null && !finalReason.isBlank()) {
                            String existingNotes = appointment.getNotes() != null ? appointment.getNotes().trim() : "";
                            String reasonTag = "[" + newStatus.name() + " Reason: " + finalReason + "]";
                            appointment.setNotes(existingNotes.isEmpty() ? reasonTag : existingNotes + " | " + reasonTag);
                        }
                        Appointment updated = appointmentRepository.save(appointment);

                        // Look up customer record to link customer_id
                        Customer customer = null;
                        if (appointment.getCustomerEmail() != null && !appointment.getCustomerEmail().isBlank()) {
                            customer = customerRepository.findByEmail(appointment.getCustomerEmail().trim()).orElse(null);
                        }

                        if (auditService != null) {
                            String performedBy = auditService.resolvePerformedBy("ADMIN");
                            auditService.logActivity(
                                    tenantId,
                                    customer != null ? customer.getId() : null,
                                    updated.getId(),
                                    updated.getCustomerName(),
                                    updated.getCustomerEmail(),
                                    updated.getCustomerPhone(),
                                    newStatus.name(),
                                    performedBy,
                                    finalReason != null ? finalReason : ("Status changed to " + newStatus.name())
                            );
                        }

                        if (finalShouldNotify && notificationService != null) {
                            try {
                                if (newStatus == Appointment.AppointmentStatus.CANCELLED) {
                                    notificationService.sendCancellationNotice(updated, finalReason);
                                }
                            } catch (Exception ignored) {
                            }
                        }

                        return ResponseEntity.ok(Map.of(
                                "message", "Appointment status updated successfully",
                                "status", newStatus.name(),
                                "appointment", updated
                        ));
                    } catch (IllegalArgumentException e) {
                        return ResponseEntity.badRequest().body(Map.of("message", "Invalid status value provided: " + finalStatus));
                    }
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Appointment not found")));
    }

    @GetMapping("/by-token/{token}")
    public ResponseEntity<?> getAppointmentByToken(@PathVariable String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Token is required"));
        }
        return appointmentRepository.findByCancellationToken(token.trim())
                .map(appt -> {
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("id", appt.getId());
                    details.put("tenantId", appt.getTenantId());
                    details.put("customerName", appt.getCustomerName());
                    details.put("customerEmail", appt.getCustomerEmail());
                    details.put("customerPhone", appt.getCustomerPhone());
                    details.put("appointmentTime", appt.getAppointmentTime());
                    details.put("service", appt.getService());
                    details.put("serviceName", appt.getService() != null ? appt.getService().getName() : "Service");
                    details.put("nailTech", appt.getNailTech());
                    details.put("stationPreference", appt.getStationPreference());
                    details.put("status", appt.getStatus());
                    details.put("notes", appt.getNotes());
                    details.put("cancellationToken", appt.getCancellationToken());

                    if (notificationService != null) {
                        TenantConfig config = notificationService.resolveTenantConfig(appt.getTenantId());
                        if (config != null) {
                            details.put("salonName", config.getSalonName());
                            details.put("primaryColor", config.getPrimaryColor());
                            details.put("salonAddress", notificationService.resolveSalonAddress(config.getTenantKey()));
                            details.put("supportPhone", notificationService.resolveSupportPhone(config.getTenantKey()));
                        }
                    }

                    return ResponseEntity.ok(details);
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Appointment not found for provided token")));
    }

    @PostMapping("/by-token/{token}/cancel")
    public ResponseEntity<?> cancelAppointmentByToken(
            @PathVariable String token,
            @RequestParam(required = false) String reason,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Token is required"));
        }
        String effectiveReason = reason;
        if (body != null && effectiveReason == null && body.containsKey("reason")) {
            effectiveReason = String.valueOf(body.get("reason"));
        }

        final String finalReason = effectiveReason != null ? effectiveReason.trim() : "Cancelled by customer via self-service portal";

        return appointmentRepository.findByCancellationToken(token.trim())
                .map(appt -> {
                    appt.setStatus(Appointment.AppointmentStatus.CANCELLED);
                    String existingNotes = appt.getNotes() != null ? appt.getNotes().trim() : "";
                    String reasonTag = "[CANCELLED Reason: " + finalReason + "]";
                    appt.setNotes(existingNotes.isEmpty() ? reasonTag : existingNotes + " | " + reasonTag);
                    Appointment updated = appointmentRepository.save(appt);

                    if (auditService != null) {
                        Customer customer = null;
                        if (appt.getCustomerEmail() != null && !appt.getCustomerEmail().isBlank()) {
                            customer = customerRepository.findByEmail(appt.getCustomerEmail().trim()).orElse(null);
                        }
                        auditService.logActivity(
                                appt.getTenantId(),
                                customer != null ? customer.getId() : null,
                                updated.getId(),
                                updated.getCustomerName(),
                                updated.getCustomerEmail(),
                                updated.getCustomerPhone(),
                                "CANCELLED",
                                "CUSTOMER",
                                finalReason
                        );
                    }

                    if (notificationService != null) {
                        try {
                            notificationService.sendCancellationNotice(updated, finalReason);
                        } catch (Exception ignored) {
                        }
                    }

                    return ResponseEntity.ok(Map.of(
                            "message", "Appointment cancelled successfully",
                            "status", "CANCELLED",
                            "appointment", updated
                    ));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Appointment not found for provided token")));
    }

    @PostMapping("/by-token/{token}/reschedule")
    public ResponseEntity<?> rescheduleAppointmentByToken(
            @PathVariable String token,
            @RequestBody Map<String, Object> body
    ) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Token is required"));
        }
        if (body == null || (!body.containsKey("appointmentTime") && !body.containsKey("date"))) {
            return ResponseEntity.badRequest().body(Map.of("message", "New appointment date/time is required."));
        }

        Optional<Appointment> existingOpt = appointmentRepository.findByCancellationToken(token.trim());
        if (existingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Appointment not found for provided token"));
        }

        Appointment appt = existingOpt.get();
        if (appt.getStatus() == Appointment.AppointmentStatus.CANCELLED) {
            return ResponseEntity.badRequest().body(Map.of("message", "Cancelled appointments cannot be rescheduled. Please book a new appointment."));
        }
        if (appt.getStatus() == Appointment.AppointmentStatus.COMPLETED) {
            return ResponseEntity.badRequest().body(Map.of("message", "Completed appointments cannot be rescheduled."));
        }

        LocalDateTime newTime;
        try {
            if (body.containsKey("appointmentTime") && body.get("appointmentTime") != null && !String.valueOf(body.get("appointmentTime")).isBlank()) {
                newTime = LocalDateTime.parse(String.valueOf(body.get("appointmentTime")));
            } else {
                String dateStr = String.valueOf(body.get("date")).trim();
                String timeStr = String.valueOf(body.get("startTime") != null ? body.get("startTime") : body.get("time")).trim();
                if (!timeStr.contains(":")) {
                    timeStr = timeStr + ":00";
                }
                if (timeStr.length() == 5) {
                    timeStr = timeStr + ":00";
                }
                newTime = LocalDateTime.parse(dateStr + "T" + timeStr);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid date/time format. Please select an available slot."));
        }

        // Sunday validation
        if (newTime.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            return ResponseEntity.badRequest().body(Map.of("message", "We are closed on Sundays. Please choose Monday through Saturday."));
        }

        // Past-time check: must be at least 15 minutes in the future
        LocalDateTime now = LocalDateTime.now();
        if (newTime.isBefore(now.plusMinutes(15))) {
            return ResponseEntity.badRequest().body(Map.of("message", "Appointment time must be at least 15 minutes in the future."));
        }

        // Business hours check (10:00 to 19:00)
        int hour = newTime.getHour();
        if (hour < 10 || hour > 19 || (hour == 19 && newTime.getMinute() > 0)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Appointment time must be between 10:00 AM and 7:00 PM."));
        }

        // Conflict check for assigned technician
        if (appt.getNailTech() != null && appt.getNailTech().getId() != null) {
            int duration = (appt.getService() != null && appt.getService().getDurationMinutes() != null)
                    ? appt.getService().getDurationMinutes() : 45;
            LocalDateTime newSlotEnd = newTime.plusMinutes(duration);

            List<Appointment> dayAppts = appointmentRepository.findByTenantIdAndAppointmentTimeBetween(
                    appt.getTenantId(),
                    newTime.toLocalDate().atStartOfDay(),
                    newTime.toLocalDate().plusDays(1).atStartOfDay()
            );

            boolean conflict = dayAppts.stream()
                    .filter(a -> !a.getId().equals(appt.getId()))
                    .filter(a -> a.getStatus() != Appointment.AppointmentStatus.CANCELLED)
                    .filter(a -> a.getNailTech() != null && a.getNailTech().getId().equals(appt.getNailTech().getId()))
                    .anyMatch(a -> hasOverlap(a, newTime, newSlotEnd));

            if (conflict) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "The assigned technician is not available at this time. Please choose another time slot."));
            }
        }

        LocalDateTime oldTime = appt.getAppointmentTime();
        appt.setAppointmentTime(newTime);
        appt.setStatus(Appointment.AppointmentStatus.CONFIRMED);
        if (newTime.isAfter(LocalDateTime.now().plusHours(24))) {
            appt.setReminderSentAt(null);
        }
        String reason = body.containsKey("reason") ? String.valueOf(body.get("reason")) : "Rescheduled by customer via self-service portal";
        String existingNotes = appt.getNotes() != null ? appt.getNotes().trim() : "";
        String reschedTag = "[RESCHEDULED: " + oldTime + " -> " + newTime + "]";
        appt.setNotes(existingNotes.isEmpty() ? reschedTag : existingNotes + " | " + reschedTag);

        Appointment updated = appointmentRepository.save(appt);

        if (auditService != null) {
            Customer customer = null;
            if (appt.getCustomerEmail() != null && !appt.getCustomerEmail().isBlank()) {
                customer = customerRepository.findByEmail(appt.getCustomerEmail().trim()).orElse(null);
            }
            auditService.logActivity(
                    appt.getTenantId(),
                    customer != null ? customer.getId() : null,
                    updated.getId(),
                    updated.getCustomerName(),
                    updated.getCustomerEmail(),
                    updated.getCustomerPhone(),
                    "RESCHEDULED",
                    "CUSTOMER",
                    reason + " (New time: " + newTime + ")"
            );
        }

        if (notificationService != null) {
            try {
                notificationService.sendRescheduleNotice(updated, oldTime);
            } catch (Exception ignored) {
            }
        }

        return ResponseEntity.ok(Map.of(
                "message", "Appointment rescheduled successfully",
                "status", "CONFIRMED",
                "appointmentTime", newTime,
                "newAppointmentTime", newTime.toString(),
                "appointment", updated
        ));
    }

    @PatchMapping("/{id}/reschedule")
    public ResponseEntity<?> adminRescheduleAppointment(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        Optional<Appointment> existingOpt = appointmentRepository.findByTenantIdAndId(tenantId, id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Appointment not found"));
        }

        Appointment appt = existingOpt.get();
        if (body == null || (!body.containsKey("appointmentTime") && !body.containsKey("date"))) {
            return ResponseEntity.badRequest().body(Map.of("message", "New appointment date/time is required."));
        }

        LocalDateTime newTime;
        try {
            if (body.containsKey("appointmentTime") && body.get("appointmentTime") != null && !String.valueOf(body.get("appointmentTime")).isBlank()) {
                newTime = LocalDateTime.parse(String.valueOf(body.get("appointmentTime")));
            } else {
                String dateStr = String.valueOf(body.get("date")).trim();
                String timeStr = String.valueOf(body.get("startTime") != null ? body.get("startTime") : body.get("time")).trim();
                if (!timeStr.contains(":")) timeStr += ":00";
                if (timeStr.length() == 5) timeStr += ":00";
                newTime = LocalDateTime.parse(dateStr + "T" + timeStr);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid date/time format."));
        }

        // Optional technician reassignment
        if (body.containsKey("techId") && body.get("techId") != null && !String.valueOf(body.get("techId")).isBlank()) {
            try {
                Long techId = Long.parseLong(String.valueOf(body.get("techId")));
                userRepository.findByTenantIdAndId(tenantId, techId).ifPresent(appt::setNailTech);
            } catch (Exception ignored) {
            }
        }

        // Sunday validation
        if (newTime.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            return ResponseEntity.badRequest().body(Map.of("message", "Sundays are closed."));
        }

        // Conflict check
        if (appt.getNailTech() != null && appt.getNailTech().getId() != null) {
            int duration = (appt.getService() != null && appt.getService().getDurationMinutes() != null)
                    ? appt.getService().getDurationMinutes() : 45;
            LocalDateTime newSlotEnd = newTime.plusMinutes(duration);

            List<Appointment> dayAppts = appointmentRepository.findByTenantIdAndAppointmentTimeBetween(
                    tenantId,
                    newTime.toLocalDate().atStartOfDay(),
                    newTime.toLocalDate().plusDays(1).atStartOfDay()
            );

            boolean conflict = dayAppts.stream()
                    .filter(a -> !a.getId().equals(appt.getId()))
                    .filter(a -> a.getStatus() != Appointment.AppointmentStatus.CANCELLED)
                    .filter(a -> a.getNailTech() != null && a.getNailTech().getId().equals(appt.getNailTech().getId()))
                    .anyMatch(a -> hasOverlap(a, newTime, newSlotEnd));

            if (conflict) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "The technician is already booked at this time slot."));
            }
        }

        LocalDateTime oldTime = appt.getAppointmentTime();
        appt.setAppointmentTime(newTime);
        appt.setStatus(Appointment.AppointmentStatus.CONFIRMED);
        if (newTime.isAfter(LocalDateTime.now().plusHours(24))) {
            appt.setReminderSentAt(null);
        }

        String reason = body.containsKey("reason") ? String.valueOf(body.get("reason")) : "Rescheduled by admin";
        String existingNotes = appt.getNotes() != null ? appt.getNotes().trim() : "";
        String reschedTag = "[RESCHEDULED: " + oldTime + " -> " + newTime + "]";
        appt.setNotes(existingNotes.isEmpty() ? reschedTag : existingNotes + " | " + reschedTag);

        Appointment updated = appointmentRepository.save(appt);

        if (auditService != null) {
            Customer customer = null;
            if (appt.getCustomerEmail() != null && !appt.getCustomerEmail().isBlank()) {
                customer = customerRepository.findByEmail(appt.getCustomerEmail().trim()).orElse(null);
            }
            String performedBy = auditService.resolvePerformedBy("ADMIN");
            auditService.logActivity(
                    tenantId,
                    customer != null ? customer.getId() : null,
                    updated.getId(),
                    updated.getCustomerName(),
                    updated.getCustomerEmail(),
                    updated.getCustomerPhone(),
                    "RESCHEDULED",
                    performedBy,
                    reason + " (New time: " + newTime + ")"
            );
        }

        boolean notifyCustomer = true;
        if (body.containsKey("notifyCustomer")) {
            notifyCustomer = Boolean.parseBoolean(String.valueOf(body.get("notifyCustomer")));
        }

        if (notifyCustomer && notificationService != null) {
            try {
                notificationService.sendRescheduleNotice(updated, oldTime);
            } catch (Exception ignored) {
            }
        }

        return ResponseEntity.ok(Map.of(
                "message", "Appointment rescheduled successfully",
                "status", "CONFIRMED",
                "appointment", updated
        ));
    }
}
