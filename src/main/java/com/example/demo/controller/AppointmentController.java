package com.example.demo.controller;

import com.example.demo.dto.BookingRequestDto;
import com.example.demo.entity.Appointment;
import com.example.demo.entity.Customer;
import com.example.demo.entity.ServiceEntity;
import com.example.demo.entity.StaffDayOff;
import com.example.demo.entity.TenantConfig;
import com.example.demo.entity.UserEntity;
import com.example.demo.repository.AppointmentRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.ServiceRepository;
import com.example.demo.repository.StaffDayOffRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import com.example.demo.service.AppointmentAuditService;
import com.example.demo.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final com.example.demo.repository.AppointmentTransactionRepository transactionRepository;
    private com.example.demo.service.AppointmentReminderService reminderService;
    private StaffDayOffRepository staffDayOffRepository;

    @Autowired(required = false)
    public void setReminderService(com.example.demo.service.AppointmentReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Autowired(required = false)
    public void setStaffDayOffRepository(StaffDayOffRepository staffDayOffRepository) {
        this.staffDayOffRepository = staffDayOffRepository;
    }

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
            @Autowired(required = false) NotificationService notificationService,
            @Autowired(required = false) com.example.demo.repository.AppointmentTransactionRepository transactionRepository
    ) {
        this.appointmentRepository = appointmentRepository;
        this.customerRepository = customerRepository;
        this.serviceRepository = serviceRepository;
        this.userRepository = userRepository;
        this.tenantContextResolver = tenantContextResolver;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.transactionRepository = transactionRepository;
    }

    public AppointmentController(
            AppointmentRepository appointmentRepository,
            CustomerRepository customerRepository,
            ServiceRepository serviceRepository,
            UserRepository userRepository,
            TenantContextResolver tenantContextResolver,
            @Autowired(required = false) AppointmentAuditService auditService,
            @Autowired(required = false) NotificationService notificationService
    ) {
        this(appointmentRepository, customerRepository, serviceRepository, userRepository, tenantContextResolver, auditService, notificationService, null);
    }

    public AppointmentController(
            AppointmentRepository appointmentRepository,
            CustomerRepository customerRepository,
            ServiceRepository serviceRepository,
            UserRepository userRepository,
            TenantContextResolver tenantContextResolver
    ) {
        this(appointmentRepository, customerRepository, serviceRepository, userRepository, tenantContextResolver, null, null, null);
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN', 'ROLE_STAFF', 'STAFF', 'ROLE_NAIL_TECH')")
    @GetMapping
    public List<Appointment> getAllAppointments(HttpServletRequest request) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        try {
            autoMarkNoShowAppointments(tenantId);
        } catch (Exception e) {
            // Log but don't prevent appointments fetch
        }
        List<Appointment> list = appointmentRepository.findAllByTenantId(tenantId);
        for (Appointment apt : list) {
            if ((apt.getMedicalFlags() == null || apt.getMedicalFlags().isBlank() || apt.getColorFormula() == null || apt.getColorFormula().isBlank())
                    && apt.getCustomerEmail() != null && !apt.getCustomerEmail().isBlank()) {
                customerRepository.findByTenantIdAndEmail(tenantId, apt.getCustomerEmail().trim()).ifPresent(c -> {
                    if ((apt.getMedicalFlags() == null || apt.getMedicalFlags().isBlank()) && c.getMedicalFlags() != null && !c.getMedicalFlags().isBlank()) {
                        apt.setMedicalFlags(c.getMedicalFlags());
                    }
                    if ((apt.getColorFormula() == null || apt.getColorFormula().isBlank()) && c.getColorFormula() != null && !c.getColorFormula().isBlank()) {
                        apt.setColorFormula(c.getColorFormula());
                    }
                });
            }
        }
        return list;
    }

    /**
     * Automatically scans for any PENDING appointments past their scheduled time by 15+ minutes
     * and marks them as NO_SHOW, logging into customer history.
     */
    @Transactional
    public List<Appointment> autoMarkNoShowAppointments(String tenantId) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(15);
        List<Appointment> candidateList = (tenantId != null && !tenantId.isBlank() && !"all".equalsIgnoreCase(tenantId))
                ? appointmentRepository.findAllByTenantId(tenantId)
                : appointmentRepository.findAll();

        List<Appointment> autoMarked = new ArrayList<>();
        for (Appointment apt : candidateList) {
            if (apt.getAppointmentTime() != null &&
                apt.getAppointmentTime().isBefore(cutoff) &&
                apt.getStatus() == Appointment.AppointmentStatus.PENDING) {

                apt.setStatus(Appointment.AppointmentStatus.NO_SHOW);
                String existingNotes = apt.getNotes() != null ? apt.getNotes().trim() : "";
                String noShowTag = "[Auto NO_SHOW: 15+ minutes past scheduled time without arrival]";
                apt.setNotes(existingNotes.isEmpty() ? noShowTag : existingNotes + " | " + noShowTag);
                Appointment saved = appointmentRepository.save(apt);
                autoMarked.add(saved);

                if (auditService != null) {
                    Customer customer = null;
                    if (apt.getCustomerEmail() != null && !apt.getCustomerEmail().isBlank()) {
                        customer = customerRepository.findByTenantIdAndEmail(apt.getTenantId(), apt.getCustomerEmail().trim()).orElse(null);
                    }
                    String serviceName = (apt.getService() != null && apt.getService().getName() != null)
                            ? apt.getService().getName() : "Nail Service";
                    String staffName = (apt.getNailTech() != null)
                            ? (apt.getNailTech().getName() != null ? apt.getNailTech().getName() : apt.getNailTech().getUsername())
                            : "Anyone Available";

                    auditService.logActivity(
                            apt.getTenantId(),
                            customer != null ? customer.getId() : null,
                            saved.getId(),
                            saved.getCustomerName(),
                            saved.getCustomerEmail(),
                            saved.getCustomerPhone(),
                            "NO_SHOW",
                            "SYSTEM: AUTO_NO_SHOW",
                            "Automatically marked NO_SHOW: 15+ minutes passed since " + apt.getAppointmentTime() +
                                    " for " + serviceName + " with " + staffName
                    );
                }
            }
        }
        return autoMarked;
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN', 'ROLE_STAFF', 'STAFF', 'ROLE_NAIL_TECH')")
    @PostMapping("/auto-no-show")
    public ResponseEntity<?> runAutoNoShowScan(HttpServletRequest request) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        List<Appointment> updated = autoMarkNoShowAppointments(tenantId);
        return ResponseEntity.ok(Map.of(
                "message", "Auto-scan for 15+ minute overdue appointments completed.",
                "markedCount", updated.size(),
                "appointments", updated
        ));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN', 'ROLE_STAFF', 'STAFF', 'ROLE_NAIL_TECH')")
    @GetMapping("/customer-history")
    public ResponseEntity<?> getCustomerAppointmentHistory(
            @RequestParam String email,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email parameter is required."));
        }
        if (auditService != null) {
            Map<String, Object> history = auditService.getCustomerHistoryByEmail(tenantId, email.trim());
            if (history != null) {
                return ResponseEntity.ok(history);
            }
        }
        return ResponseEntity.ok(Map.of(
                "appointments", List.of(),
                "activityLogs", List.of(),
                "reliability", Map.of("totalBooked", 0, "totalConfirmed", 0, "totalCompleted", 0, "totalCancelled", 0, "totalNoShows", 0)
        ));
    }

    /**
     * Tenant-scoped dynamic availability pre-check endpoint.
     * Supports both /api/appointments/availability and /api/appointments/available-slots.
     * Checks available time slots for a given date, duration, turnover buffer, and single or sequential multi-service technicians.
     */
    @GetMapping({"/availability", "/available-slots"})
    public ResponseEntity<?> getAvailableSlots(
            @RequestParam String date,
            @RequestParam(defaultValue = "45") int durationMinutes,
            @RequestParam(defaultValue = "15") int bufferMinutes,
            @RequestParam(required = false) Long techId,
            @RequestParam(required = false) List<Long> techIds,
            @RequestParam(required = false) List<Long> serviceIds,
            @RequestParam(required = false) List<Integer> serviceDurations,
            @RequestParam(required = false) List<String> serviceTechIds,
            @RequestParam(required = false) String clientNow,
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
        int effectiveBuffer = Math.max(0, bufferMinutes);

        // Fetch all non-cancelled appointments for this tenant on this date
        List<Appointment> dayAppointments = appointmentRepository.findByTenantIdAndAppointmentTimeBetween(
                tenantId,
                targetDate.atStartOfDay(),
                targetDate.plusDays(1).atStartOfDay()
        ).stream()
         .filter(a -> a.getStatus() != Appointment.AppointmentStatus.CANCELLED)
         .toList();

        // Query technicians who have a marked day-off on this date
        Set<Long> offStaffIds = Collections.emptySet();
        if (staffDayOffRepository != null) {
            offStaffIds = staffDayOffRepository.findAllByTenantIdAndOffDate(tenantId, targetDate).stream()
                    .map(StaffDayOff::getStaffId)
                    .collect(Collectors.toSet());
        }
        final Set<Long> finalOffStaffIds = offStaffIds;

        // Get active tenant technicians and non-off working technicians
        List<UserEntity> tenantTechs = userRepository.findAllByTenantIdAndRole(tenantId, "ROLE_NAIL_TECH");
        List<UserEntity> workingTenantTechs = tenantTechs.stream()
                .filter(t -> t.getId() != null && !finalOffStaffIds.contains(t.getId()))
                .collect(Collectors.toList());

        // Determine which technicians are being checked
        List<UserEntity> targetTechs = new ArrayList<>();
        if (techId != null) {
            userRepository.findByTenantIdAndId(tenantId, techId).ifPresent(targetTechs::add);
        } else if (techIds != null && !techIds.isEmpty()) {
            for (Long tid : techIds) {
                userRepository.findByTenantIdAndId(tenantId, tid).ifPresent(targetTechs::add);
            }
        }

        // If no specific tech requested, "Anyone Available" mode evaluates against all qualified techs who are not OFF
        if (targetTechs.isEmpty() && techId == null && (techIds == null || techIds.isEmpty())) {
            targetTechs.addAll(workingTenantTechs);
        }

        // Flatten any comma-separated values passed in query params
        List<Long> flatServiceIds = new ArrayList<>();
        if (serviceIds != null) {
            for (Object sid : serviceIds) {
                if (sid != null) {
                    for (String part : String.valueOf(sid).split(",")) {
                        try {
                            long p = Long.parseLong(part.trim());
                            if (p > 0) flatServiceIds.add(p);
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        List<Integer> flatServiceDurations = new ArrayList<>();
        if (serviceDurations != null) {
            for (Object sd : serviceDurations) {
                if (sd != null) {
                    for (String part : String.valueOf(sd).split(",")) {
                        try {
                            int dur = Integer.parseInt(part.trim());
                            if (dur > 0) flatServiceDurations.add(dur);
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        List<String> flatServiceTechIds = new ArrayList<>();
        if (serviceTechIds != null) {
            for (String st : serviceTechIds) {
                if (st != null) {
                    for (String part : st.split(",")) {
                        flatServiceTechIds.add(part.trim());
                    }
                }
            }
        }

        // Build sequential service segments configuration
        List<ServiceSegmentConfig> segments = new ArrayList<>();
        int segmentCount = 0;
        if (!flatServiceDurations.isEmpty()) {
            segmentCount = flatServiceDurations.size();
        } else if (!flatServiceIds.isEmpty()) {
            segmentCount = flatServiceIds.size();
        } else if (techIds != null && !techIds.isEmpty()) {
            segmentCount = techIds.size();
        } else {
            segmentCount = 1;
        }

        for (int i = 0; i < segmentCount; i++) {
            ServiceSegmentConfig seg = new ServiceSegmentConfig();

            // Service ID
            if (i < flatServiceIds.size()) {
                seg.serviceId = flatServiceIds.get(i);
            }

            // Duration
            if (i < flatServiceDurations.size() && flatServiceDurations.get(i) != null && flatServiceDurations.get(i) > 0) {
                seg.durationMinutes = flatServiceDurations.get(i);
            } else if (seg.serviceId != null) {
                Optional<ServiceEntity> srvOpt = serviceRepository.findByTenantIdAndId(tenantId, seg.serviceId);
                seg.durationMinutes = (srvOpt.isPresent() && srvOpt.get().getDurationMinutes() != null && srvOpt.get().getDurationMinutes() > 0)
                        ? srvOpt.get().getDurationMinutes() : 45;
            } else {
                seg.durationMinutes = (segmentCount == 1) ? Math.max(15, durationMinutes) : 45;
            }

            // Service Name
            if (seg.serviceId != null) {
                Optional<ServiceEntity> srvOpt = serviceRepository.findByTenantIdAndId(tenantId, seg.serviceId);
                seg.serviceName = srvOpt.map(ServiceEntity::getName).orElse("Service " + (i + 1));
            } else {
                seg.serviceName = (segmentCount == 1) ? "Salon Service" : ("Service " + (i + 1));
            }

            // Tech ID
            Long resolvedTid = null;
            if (i < flatServiceTechIds.size()) {
                String raw = flatServiceTechIds.get(i);
                if (raw != null && !raw.isBlank() && !"anyone".equalsIgnoreCase(raw.trim()) && !"null".equalsIgnoreCase(raw.trim())) {
                    try {
                        long parsed = Long.parseLong(raw.trim());
                        if (parsed > 0) resolvedTid = parsed;
                    } catch (NumberFormatException ignored) {}
                }
            } else if (techIds != null && i < techIds.size() && techIds.get(i) != null && techIds.get(i) > 0) {
                resolvedTid = techIds.get(i);
            } else if (techId != null && techId > 0 && (segmentCount == 1 || flatServiceTechIds.isEmpty())) {
                resolvedTid = techId;
            }
            seg.techId = resolvedTid;

            // Tech Name
            if (resolvedTid != null) {
                Optional<UserEntity> uOpt = userRepository.findByTenantIdAndId(tenantId, resolvedTid);
                seg.techName = uOpt.map(u -> (u.getName() != null && !u.getName().isBlank()) ? u.getName() : u.getUsername())
                        .orElse("Specialist #" + resolvedTid);
            } else {
                seg.techName = "Anyone Available";
            }

            segments.add(seg);
        }

        int calculatedSegmentsDuration = segments.stream().mapToInt(seg -> seg.durationMinutes).sum();
        int effectiveDuration = Math.max(Math.max(durationMinutes, calculatedSegmentsDuration), 15);

        // Determine current real-time clock (prefer clientNow if valid, else server clock)
        LocalDateTime now = LocalDateTime.now();
        if (clientNow != null && !clientNow.isBlank()) {
            try {
                now = LocalDateTime.parse(clientNow.trim());
            } catch (Exception ignored) {
            }
        }

        // Generate all standard 15-minute slots between 10:00 AM and 7:00 PM
        List<Map<String, Object>> slotResults = new ArrayList<>();

        for (int hour = 10; hour <= 19; hour++) {
            int maxMin = (hour == 19) ? 0 : 45;
            for (int min = 0; min <= maxMin; min += 15) {
                LocalDateTime slotStart = targetDate.atTime(hour, min);
                LocalDateTime slotEnd = slotStart.plusMinutes(effectiveDuration);

                // Skip slots that exceed store closing time (19:00 / 7:00 PM + 15m grace)
                if (slotEnd.isAfter(dayEnd.plusMinutes(15))) {
                    continue;
                }

                // Real-Time Today Filter: if date is today, slot must be at least 15 minutes after current local time
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
                String label12 = formatTime12Hour(slotStart);
                String endValue24 = String.format("%02d:%02d:00", slotEnd.getHour(), slotEnd.getMinute());
                String endLabel12 = formatTime12Hour(slotEnd);
                String timeRangeLabel = label12 + " – " + endLabel12;

                boolean isAvailable = !isPast;
                List<Map<String, Object>> staffBreakdown = new ArrayList<>();
                List<String> conflictMessages = new ArrayList<>();
                String staffBookedDesc = null;

                LocalDateTime segCursor = slotStart;
                for (ServiceSegmentConfig seg : segments) {
                    int segDur = seg.durationMinutes;
                    LocalDateTime segStart = segCursor;
                    LocalDateTime segEnd = segStart.plusMinutes(segDur);
                    segCursor = segEnd;

                    boolean segAvailable = true;
                    String segBusyWindow = null;
                    String segNextAvailable = null;
                    String segMessage = null;

                    if (seg.techId != null && seg.techId > 0) {
                        final long targetTid = seg.techId;
                        if (finalOffStaffIds.contains(targetTid)) {
                            segAvailable = false;
                            isAvailable = false;
                            segBusyWindow = "Off on this date";
                            segNextAvailable = "N/A";
                            segMessage = seg.serviceName + " — " + seg.techName + " is OFF on this date";
                            conflictMessages.add(segMessage);
                            if (staffBookedDesc == null) {
                                staffBookedDesc = seg.techName + " is OFF on this date";
                            }
                        } else {
                            Optional<Appointment> conflictOpt = dayAppointments.stream()
                                .filter(a -> a.getNailTech() != null &&
                                        a.getNailTech().getId() != null &&
                                        a.getNailTech().getId().equals(targetTid) &&
                                        hasOverlapWithBuffer(a, segStart, segEnd, effectiveBuffer))
                                .findFirst();

                        if (conflictOpt.isPresent()) {
                            segAvailable = false;
                            isAvailable = false;
                            Appointment ca = conflictOpt.get();
                            LocalDateTime caStart = ca.getAppointmentTime();
                            int caDur = (ca.getService() != null && ca.getService().getDurationMinutes() != null)
                                    ? ca.getService().getDurationMinutes() : 45;
                            LocalDateTime caBusyEnd = caStart.plusMinutes(caDur + effectiveBuffer);

                            // Follow consecutive chain to calculate true next open time
                            LocalDateTime nextOpen = caBusyEnd;
                            boolean chained = true;
                            while (chained) {
                                final LocalDateTime checkTime = nextOpen;
                                Optional<Appointment> nextConflict = dayAppointments.stream()
                                        .filter(a -> a.getNailTech() != null &&
                                                a.getNailTech().getId() != null &&
                                                a.getNailTech().getId().equals(targetTid))
                                        .filter(a -> a.getAppointmentTime() != null &&
                                                !a.getAppointmentTime().isAfter(checkTime) &&
                                                a.getAppointmentTime().plusMinutes(
                                                        (a.getService() != null && a.getService().getDurationMinutes() != null
                                                                ? a.getService().getDurationMinutes() : 45) + effectiveBuffer
                                                ).isAfter(checkTime))
                                        .findFirst();
                                if (nextConflict.isPresent()) {
                                    Appointment nc = nextConflict.get();
                                    int ncDur = (nc.getService() != null && nc.getService().getDurationMinutes() != null)
                                            ? nc.getService().getDurationMinutes() : 45;
                                    nextOpen = nc.getAppointmentTime().plusMinutes(ncDur + effectiveBuffer);
                                } else {
                                    chained = false;
                                }
                            }

                            segBusyWindow = formatTime12Hour(caStart) + " – " + formatTime12Hour(caBusyEnd);
                            segNextAvailable = formatTime12Hour(nextOpen);

                            // Format: "Pedicure — Staff A: Booked 3:00 PM – 4:15 PM (Next available: 4:15 PM)"
                            segMessage = seg.serviceName + " — " + seg.techName + ": Booked " + segBusyWindow + " (Next available: " + segNextAvailable + ")";
                            conflictMessages.add(segMessage);
                            if (staffBookedDesc == null) {
                                staffBookedDesc = seg.techName + " booked " + segBusyWindow;
                            }
                        } else {
                            segMessage = seg.serviceName + " — " + seg.techName + ": Available at " + formatTime12Hour(segStart);
                        }
                    }
                } else {
                    // "Anyone Available": check if at least one qualified non-off technician is free
                    boolean atLeastOneFree = !workingTenantTechs.isEmpty() && workingTenantTechs.stream().anyMatch(t ->
                            dayAppointments.stream().noneMatch(a ->
                                    a.getNailTech() != null &&
                                    a.getNailTech().getId() != null &&
                                    a.getNailTech().getId().equals(t.getId()) &&
                                    hasOverlapWithBuffer(a, segStart, segEnd, effectiveBuffer)
                            )
                    );
                    if (!atLeastOneFree) {
                        segAvailable = false;
                        isAvailable = false;
                        segMessage = seg.serviceName + " — Anyone Available: All specialists " + (workingTenantTechs.isEmpty() ? "off" : "booked") + " at " + formatTime12Hour(segStart);
                        conflictMessages.add(segMessage);
                            if (staffBookedDesc == null) {
                                staffBookedDesc = "All staff booked";
                            }
                        } else {
                            segMessage = seg.serviceName + " — Anyone Available: Specialist available at " + formatTime12Hour(segStart);
                        }
                    }

                    Map<String, Object> segMap = new LinkedHashMap<>();
                    segMap.put("serviceId", seg.serviceId);
                    segMap.put("serviceName", seg.serviceName);
                    segMap.put("techId", seg.techId);
                    segMap.put("techName", seg.techName);
                    segMap.put("windowStart", formatTime12Hour(segStart));
                    segMap.put("windowEnd", formatTime12Hour(segEnd));
                    segMap.put("available", segAvailable);
                    segMap.put("busyWindow", segBusyWindow);
                    segMap.put("nextAvailable", segNextAvailable);
                    segMap.put("message", segMessage);
                    staffBreakdown.add(segMap);
                }

                String conflictReason = null;
                if (isPast) {
                    conflictReason = "Slot has passed";
                } else if (!conflictMessages.isEmpty()) {
                    conflictReason = String.join("; ", conflictMessages);
                } else if (!isAvailable) {
                    conflictReason = "Booked";
                }

                Map<String, Object> slot = new LinkedHashMap<>();
                slot.put("value", value24);
                slot.put("label", label12);
                slot.put("endValue", endValue24);
                slot.put("endLabel", endLabel12);
                slot.put("timeRangeLabel", timeRangeLabel);
                slot.put("durationMinutes", effectiveDuration);
                slot.put("bufferMinutes", effectiveBuffer);
                slot.put("available", isAvailable);
                slot.put("isPast", isPast);
                slot.put("staffBreakdown", staffBreakdown);
                if (staffBookedDesc != null) {
                    slot.put("staffBookedDesc", staffBookedDesc);
                }
                if (conflictReason != null) {
                    slot.put("reason", conflictReason);
                }
                slotResults.add(slot);
            }
        }

        // Identify earliest valid sequential window
        Map<String, Object> earliestSlot = slotResults.stream()
                .filter(s -> Boolean.TRUE.equals(s.get("available")))
                .findFirst()
                .orElse(null);

        // Collect distinct staff conflict breakdowns for unbookable slots
        List<Map<String, Object>> staffConflicts = new ArrayList<>();
        for (Map<String, Object> s : slotResults) {
            if (!Boolean.TRUE.equals(s.get("available")) && !Boolean.TRUE.equals(s.get("isPast"))) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> bList = (List<Map<String, Object>>) s.get("staffBreakdown");
                if (bList != null && bList.stream().anyMatch(b -> Boolean.FALSE.equals(b.get("available")))) {
                    Map<String, Object> sc = new LinkedHashMap<>();
                    sc.put("time", s.get("label"));
                    sc.put("value", s.get("value"));
                    sc.put("timeRangeLabel", s.get("timeRangeLabel"));
                    sc.put("staffBookedDesc", s.get("staffBookedDesc"));
                    sc.put("reason", s.get("reason"));
                    sc.put("breakdown", bList);
                    staffConflicts.add(sc);
                }
            }
        }

        // Build per-service availability breakdown allowing customers to choose each staff/service timeslot
        List<Map<String, Object>> servicesAvailability = new ArrayList<>();
        for (ServiceSegmentConfig seg : segments) {
            List<Map<String, Object>> segSlots = new ArrayList<>();
            int segDur = seg.durationMinutes;

            for (int hour = 10; hour <= 19; hour++) {
                int maxMin = (hour == 19) ? 0 : 45;
                for (int min = 0; min <= maxMin; min += 15) {
                    LocalDateTime segStart = targetDate.atTime(hour, min);
                    LocalDateTime segEnd = segStart.plusMinutes(segDur);

                    if (segEnd.isAfter(dayEnd.plusMinutes(15))) {
                        continue;
                    }

                    boolean isPast = false;
                    if (targetDate.isEqual(now.toLocalDate())) {
                        if (!segStart.isAfter(now.plusMinutes(15))) {
                            isPast = true;
                        }
                    } else if (targetDate.isBefore(now.toLocalDate())) {
                        isPast = true;
                    }

                    String hh = String.format("%02d", hour);
                    String mm = String.format("%02d", min);
                    String val24 = hh + ":" + mm + ":00";
                    String lbl12 = formatTime12Hour(segStart);
                    String endVal24 = String.format("%02d:%02d:00", segEnd.getHour(), segEnd.getMinute());
                    String endLbl12 = formatTime12Hour(segEnd);
                    String rngLabel = lbl12 + " – " + endLbl12;

                    boolean segAvailable = !isPast;
                    String busyWindow = null;
                    String nextAvail = null;
                    List<String> freeSpecialists = new ArrayList<>();

                    // Check which specialists are free at this exact slot (excluding off specialists)
                    for (UserEntity tech : workingTenantTechs) {
                        boolean techFree = dayAppointments.stream().noneMatch(a ->
                                a.getNailTech() != null &&
                                a.getNailTech().getId() != null &&
                                a.getNailTech().getId().equals(tech.getId()) &&
                                hasOverlapWithBuffer(a, segStart, segEnd, effectiveBuffer)
                        );
                        if (techFree) {
                            String tName = (tech.getName() != null && !tech.getName().isBlank()) ? tech.getName() : tech.getUsername();
                            freeSpecialists.add(tName);
                        }
                    }
                    boolean anyoneAvailableAtSlot = !freeSpecialists.isEmpty();
                    String statusMessage;

                    if (seg.techId != null && seg.techId > 0) {
                        final long targetTid = seg.techId;
                        if (finalOffStaffIds.contains(targetTid)) {
                            segAvailable = false;
                            busyWindow = "Off on this date";
                            nextAvail = "N/A";
                            if (anyoneAvailableAtSlot) {
                                statusMessage = seg.techName + " is OFF on this date. Choose Anyone Available at " + lbl12 + " or pick another date.";
                            } else {
                                statusMessage = seg.techName + " is OFF on this date.";
                            }
                        } else {
                            Optional<Appointment> conflictOpt = dayAppointments.stream()
                                    .filter(a -> a.getNailTech() != null &&
                                            a.getNailTech().getId() != null &&
                                            a.getNailTech().getId().equals(targetTid) &&
                                            hasOverlapWithBuffer(a, segStart, segEnd, effectiveBuffer))
                                    .findFirst();

                            if (conflictOpt.isPresent()) {
                                segAvailable = false;
                                Appointment ca = conflictOpt.get();
                                LocalDateTime caStart = ca.getAppointmentTime();
                                int caDur = (ca.getService() != null && ca.getService().getDurationMinutes() != null)
                                        ? ca.getService().getDurationMinutes() : 45;
                                LocalDateTime caBusyEnd = caStart.plusMinutes(caDur + effectiveBuffer);

                                LocalDateTime nextOpen = caBusyEnd;
                                boolean chained = true;
                                while (chained) {
                                    final LocalDateTime checkTime = nextOpen;
                                    Optional<Appointment> nextConflict = dayAppointments.stream()
                                            .filter(a -> a.getNailTech() != null &&
                                                    a.getNailTech().getId() != null &&
                                                    a.getNailTech().getId().equals(targetTid))
                                            .filter(a -> a.getAppointmentTime() != null &&
                                                    !a.getAppointmentTime().isAfter(checkTime) &&
                                                    a.getAppointmentTime().plusMinutes(
                                                            (a.getService() != null && a.getService().getDurationMinutes() != null
                                                                    ? a.getService().getDurationMinutes() : 45) + effectiveBuffer
                                                    ).isAfter(checkTime))
                                            .findFirst();
                                    if (nextConflict.isPresent()) {
                                        Appointment nc = nextConflict.get();
                                        int ncDur = (nc.getService() != null && nc.getService().getDurationMinutes() != null)
                                                ? nc.getService().getDurationMinutes() : 45;
                                        nextOpen = nc.getAppointmentTime().plusMinutes(ncDur + effectiveBuffer);
                                    } else {
                                        chained = false;
                                    }
                                }

                                busyWindow = formatTime12Hour(caStart) + " – " + formatTime12Hour(caBusyEnd);
                                nextAvail = formatTime12Hour(nextOpen);

                                if (anyoneAvailableAtSlot) {
                                    statusMessage = seg.techName + " is booked at this time. The next available is " + nextAvail + ", or choose Anyone Available at " + lbl12;
                                } else {
                                    statusMessage = seg.techName + " is booked at this time. The next available is " + nextAvail + " (all specialists booked at " + lbl12 + ")";
                                }
                            } else {
                                statusMessage = seg.serviceName + " — " + seg.techName + ": Available at " + lbl12;
                            }
                        }
                    } else {
                        // "Anyone Available" mode
                        if (!anyoneAvailableAtSlot) {
                            segAvailable = false;
                            statusMessage = workingTenantTechs.isEmpty() ? "All specialists are off on this date" : ("All specialists booked at " + lbl12);
                        } else {
                            statusMessage = seg.serviceName + ": Specialist available at " + lbl12;
                        }
                    }

                    Map<String, Object> sMap = new LinkedHashMap<>();
                    sMap.put("value", val24);
                    sMap.put("label", lbl12);
                    sMap.put("endValue", endVal24);
                    sMap.put("endLabel", endLbl12);
                    sMap.put("timeRangeLabel", rngLabel);
                    sMap.put("durationMinutes", segDur);
                    sMap.put("available", segAvailable);
                    sMap.put("isPast", isPast);
                    sMap.put("busyWindow", busyWindow);
                    sMap.put("nextAvailable", nextAvail);
                    sMap.put("anyoneAvailableAtSlot", anyoneAvailableAtSlot);
                    sMap.put("freeSpecialists", freeSpecialists);
                    sMap.put("statusMessage", statusMessage);
                    segSlots.add(sMap);
                }
            }

            Map<String, Object> segRes = new LinkedHashMap<>();
            segRes.put("serviceId", seg.serviceId);
            segRes.put("serviceName", seg.serviceName);
            segRes.put("durationMinutes", seg.durationMinutes);
            segRes.put("techId", seg.techId);
            segRes.put("techName", seg.techName);
            segRes.put("slots", segSlots);
            segRes.put("availableSlots", segSlots.stream().filter(s -> Boolean.TRUE.equals(s.get("available"))).toList());
            servicesAvailability.add(segRes);
        }

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("date", date);
        responseBody.put("isClosed", false);
        responseBody.put("durationMinutes", effectiveDuration);
        responseBody.put("bufferMinutes", effectiveBuffer);
        if (earliestSlot != null) {
            responseBody.put("earliestAvailableSlot", earliestSlot);
        }
        responseBody.put("staffConflicts", staffConflicts);
        responseBody.put("servicesAvailability", servicesAvailability);
        responseBody.put("availableSlots", slotResults);

        return ResponseEntity.ok(responseBody);
    }

    private String formatTime12Hour(LocalDateTime dt) {
        int hour = dt.getHour();
        int min = dt.getMinute();
        String period = hour >= 12 ? "PM" : "AM";
        int hour12 = hour > 12 ? hour - 12 : (hour == 0 ? 12 : hour);
        return String.format("%d:%02d %s", hour12, min, period);
    }

    private boolean hasOverlap(Appointment a, LocalDateTime slotStart, LocalDateTime slotEnd) {
        return hasOverlapWithBuffer(a, slotStart, slotEnd, 0);
    }

    private boolean hasOverlapWithBuffer(Appointment a, LocalDateTime slotStart, LocalDateTime slotEnd, int bufferMinutes) {
        if (a.getAppointmentTime() == null) return false;
        LocalDateTime apptStart = a.getAppointmentTime();
        int apptDuration = (a.getService() != null && a.getService().getDurationMinutes() != null)
                ? a.getService().getDurationMinutes()
                : 45;
        LocalDateTime apptBusyEnd = apptStart.plusMinutes(apptDuration + Math.max(0, bufferMinutes));

        // Direct overlap: candidate slot overlaps existing appointment busy window
        if (slotStart.isBefore(apptBusyEnd) && slotEnd.isAfter(apptStart)) {
            return true;
        }

        // Insufficient lead-time buffer: if candidate slot starts within 15 minutes before an existing appointment,
        // it is impossible for the technician to complete a viable session and prepare for the next client.
        if (bufferMinutes > 0 && slotStart.isBefore(apptStart)) {
            long minutesUntilAppt = java.time.Duration.between(slotStart, apptStart).toMinutes();
            if (minutesUntilAppt <= 15) {
                return true;
            }
        }

        return false;
    }

    private boolean isTechBusyAtWindow(
            Long techId,
            LocalDateTime start,
            LocalDateTime end,
            int bufferMinutes,
            List<Appointment> dbAppts,
            List<Appointment> batchAppts
    ) {
        if (techId == null) return false;
        if (dbAppts != null) {
            boolean conflict = dbAppts.stream()
                    .filter(a -> a.getStatus() != Appointment.AppointmentStatus.CANCELLED)
                    .filter(a -> a.getNailTech() != null && techId.equals(a.getNailTech().getId()))
                    .anyMatch(a -> hasOverlapWithBuffer(a, start, end, bufferMinutes));
            if (conflict) return true;
        }
        if (batchAppts != null) {
            // Buffer is 0 for batch appointments (sequential services within the same customer visit)
            return batchAppts.stream()
                    .filter(a -> a.getStatus() != Appointment.AppointmentStatus.CANCELLED)
                    .filter(a -> a.getNailTech() != null && techId.equals(a.getNailTech().getId()))
                    .anyMatch(a -> hasOverlapWithBuffer(a, start, end, 0));
        }
        return false;
    }

    @Transactional
    @PostMapping
    public ResponseEntity<?> bookAppointment(@RequestBody BookingRequestDto bookingRequest, HttpServletRequest request) {
        try {
            return doBookAppointment(bookingRequest, request);
        } catch (DataIntegrityViolationException dive) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "This time slot was just booked by another customer. Please choose another time."));
        }
    }

    private ResponseEntity<?> doBookAppointment(BookingRequestDto bookingRequest, HttpServletRequest request) {
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

        List<UserEntity> tenantTechs = userRepository.findAllByTenantIdAndRole(tenantId, "ROLE_NAIL_TECH");
        if (tenantTechs == null) {
            tenantTechs = Collections.emptyList();
        }

        boolean isMultiGuestRequest = bookingRequest.getGuests() != null && !bookingRequest.getGuests().isEmpty();
        boolean isTopLevelMultiService = bookingRequest.getServiceIds() != null && bookingRequest.getServiceIds().size() > 1;

        // Validate technician belongs to active tenant and has no conflict at the requested time slot
        UserEntity resolvedTech = null;
        if (bookingRequest.getNailTech() != null && bookingRequest.getNailTech().getId() != null) {
            Long techId = bookingRequest.getNailTech().getId();
            Optional<UserEntity> tenantTech = userRepository.findByTenantIdAndId(tenantId, techId);
            if (tenantTech.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Selected technician does not belong to this salon."));
            }
            resolvedTech = tenantTech.get();

            if (!Boolean.TRUE.equals(bookingRequest.getManualOverride()) && staffDayOffRepository != null) {
                LocalDate aptDate = bookingRequest.getAppointmentTime().toLocalDate();
                if (staffDayOffRepository.existsByTenantIdAndStaffIdAndOffDate(tenantId, techId, aptDate)) {
                    String sName = (resolvedTech.getName() != null && !resolvedTech.getName().isBlank()) ? resolvedTech.getName() : "Selected specialist";
                    return ResponseEntity.badRequest().body(Map.of("message", sName + " is off on this date. Please choose another specialist or Anyone Available."));
                }
            }

            boolean hasConflict = false;
            // Only perform top-level conflict check for single-guest single-service bookings.
            // Multi-service and multi-guest requests perform per-service specialist scheduling below.
            if (!isMultiGuestRequest && !isTopLevelMultiService && !Boolean.TRUE.equals(bookingRequest.getManualOverride())) {
                boolean exactConflict = appointmentRepository.existsByTenantIdAndNailTechIdAndAppointmentTimeAndStatusNot(
                        tenantId, techId, bookingRequest.getAppointmentTime(), Appointment.AppointmentStatus.CANCELLED);
                if (exactConflict) {
                    hasConflict = true;
                } else {
                    int estDuration = 45;
                    if (bookingRequest.getService() != null && bookingRequest.getService().getId() != null) {
                        estDuration = serviceRepository.findByTenantIdAndId(tenantId, bookingRequest.getService().getId())
                                .map(s -> s.getDurationMinutes() != null && s.getDurationMinutes() > 0 ? s.getDurationMinutes() : 45)
                                .orElse(45);
                    } else if (bookingRequest.getServiceIds() != null && !bookingRequest.getServiceIds().isEmpty()) {
                        estDuration = serviceRepository.findByTenantIdAndId(tenantId, bookingRequest.getServiceIds().get(0))
                                .map(s -> s.getDurationMinutes() != null && s.getDurationMinutes() > 0 ? s.getDurationMinutes() : 45)
                                .orElse(45);
                    }
                    LocalDateTime bookingSlotEnd = bookingRequest.getAppointmentTime().plusMinutes(estDuration);

                    List<Appointment> dayAppts = appointmentRepository.findByTenantIdAndAppointmentTimeBetween(
                            tenantId,
                            bookingRequest.getAppointmentTime().toLocalDate().atStartOfDay(),
                            bookingRequest.getAppointmentTime().toLocalDate().plusDays(1).atStartOfDay()
                    );

                    if (dayAppts != null) {
                        hasConflict = dayAppts.stream()
                                .filter(a -> a.getStatus() != Appointment.AppointmentStatus.CANCELLED)
                                .filter(a -> a.getNailTech() != null && a.getNailTech().getId().equals(techId))
                                .anyMatch(a -> hasOverlapWithBuffer(a, bookingRequest.getAppointmentTime(), bookingSlotEnd, 15));
                    }
                }
            }
            if (hasConflict) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "This technician is already booked at the selected time slot. Please choose another time or technician."));
            }
        }

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

            List<Appointment> dayAppts = appointmentRepository.findByTenantIdAndAppointmentTimeBetween(
                    tenantId,
                    bookingRequest.getAppointmentTime().toLocalDate().atStartOfDay(),
                    bookingRequest.getAppointmentTime().toLocalDate().plusDays(1).atStartOfDay()
            );
            if (dayAppts == null) {
                dayAppts = Collections.emptyList();
            }

            for (int i = 0; i < guestItems.size(); i++) {
                BookingRequestDto.GuestBookingItem guest = guestItems.get(i);
                int guestNum = guest.getGuestNumber() != null ? guest.getGuestNumber() : (i + 1);
                String label = "Guest " + guestNum;

                if (guest.getCustomerName() == null || guest.getCustomerName().isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("message", label + " name is required."));
                }
                if (guest.getCustomerEmail() != null && !guest.getCustomerEmail().isBlank() && !isValidEmail(guest.getCustomerEmail())) {
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

                LocalDateTime currentServiceStart = bookingRequest.getAppointmentTime();

                for (ServiceEntity srv : guestServices) {
                    int totalSrvDuration = (srv.getDurationMinutes() != null && srv.getDurationMinutes() > 0) ? srv.getDurationMinutes() : 45;
                    Map<String, String> timeSlots = (guest.getServiceTimeSlots() != null && !guest.getServiceTimeSlots().isEmpty())
                            ? guest.getServiceTimeSlots()
                            : bookingRequest.getServiceTimeSlots();

                    // Detect combo split keys (e.g., "10_mani", "10_pedi") so each combo component can have its own specialist & time slot
                    Set<String> splitKeys = new LinkedHashSet<>();
                    if (srv.getId() != null) {
                        String prefix = srv.getId() + "_";
                        if (guest.getServiceStaffAssignments() != null) {
                            for (String k : guest.getServiceStaffAssignments().keySet()) {
                                if (k != null && k.startsWith(prefix)) splitKeys.add(k);
                            }
                        }
                        if (timeSlots != null) {
                            for (String k : timeSlots.keySet()) {
                                if (k != null && k.startsWith(prefix)) splitKeys.add(k);
                            }
                        }
                    }
                    List<String> keysToSchedule = splitKeys.size() >= 2
                            ? new ArrayList<>(splitKeys)
                            : Collections.singletonList(srv.getId() != null ? String.valueOf(srv.getId()) : "");

                    for (int partIdx = 0; partIdx < keysToSchedule.size(); partIdx++) {
                        String srvKey = keysToSchedule.get(partIdx);
                        boolean isComboPart = splitKeys.size() >= 2;
                        int srvDuration = totalSrvDuration;
                        String comboPartTag = null;
                        if (isComboPart) {
                            String suffix = srvKey.substring((srv.getId() + "_").length()).toLowerCase();
                            if ("mani".equals(suffix)) {
                                srvDuration = Math.max(20, (int) (Math.round((totalSrvDuration * 0.45) / 5.0) * 5));
                                comboPartTag = "[Combo: Manicure]";
                            } else if ("pedi".equals(suffix)) {
                                int maniPart = Math.max(20, (int) (Math.round((totalSrvDuration * 0.45) / 5.0) * 5));
                                srvDuration = Math.max(25, totalSrvDuration - maniPart);
                                comboPartTag = "[Combo: Pedicure]";
                            } else {
                                srvDuration = Math.max(15, totalSrvDuration / keysToSchedule.size());
                                comboPartTag = "[Combo Part " + (partIdx + 1) + "]";
                            }
                        }

                        Appointment appt = new Appointment();
                        appt.setTenantId(tenantId);
                        appt.setCustomerName(guest.getCustomerName().trim());
                        appt.setCustomerEmail(guest.getCustomerEmail() != null ? guest.getCustomerEmail().trim() : "");
                        appt.setCustomerPhone(guest.getCustomerPhone().trim());
                        // Resolve per-service (or per-combo-part) time slot if provided
                        LocalDateTime serviceApptTime = currentServiceStart;
                        if (timeSlots != null && !srvKey.isEmpty()) {
                            String customSlot = timeSlots.get(srvKey);
                            if (customSlot != null && !customSlot.isBlank()) {
                                try {
                                    if (customSlot.contains("T")) {
                                        serviceApptTime = LocalDateTime.parse(customSlot.trim());
                                    } else {
                                        LocalTime parsedTime = LocalTime.parse(customSlot.trim());
                                        serviceApptTime = bookingRequest.getAppointmentTime().toLocalDate().atTime(parsedTime);
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                        }
                        appt.setAppointmentTime(serviceApptTime);
                        appt.setStationPreference(bookingRequest.getStationPreference());
                        appt.setCancellationToken(UUID.randomUUID().toString());
                        if (guestCustomer != null) {
                            if (guestCustomer.getMedicalFlags() != null && !guestCustomer.getMedicalFlags().isBlank()) {
                                appt.setMedicalFlags(guestCustomer.getMedicalFlags());
                            }
                            if (guestCustomer.getColorFormula() != null && !guestCustomer.getColorFormula().isBlank()) {
                                appt.setColorFormula(guestCustomer.getColorFormula());
                            }
                        }

                        // Check if per-service (or per-combo-part) staff selection was assigned
                        UserEntity serviceSpecificTech = resolvedTech;
                        if (guest.getServiceStaffAssignments() != null && !srvKey.isEmpty()) {
                            if (guest.getServiceStaffAssignments().containsKey(srvKey)) {
                                Long assignedTechId = guest.getServiceStaffAssignments().get(srvKey);
                                if (assignedTechId != null && assignedTechId > 0) {
                                    serviceSpecificTech = userRepository.findByTenantIdAndId(tenantId, assignedTechId).orElse(null);
                                } else {
                                    // Explicitly unassigned or "Anyone Available" for this service/part
                                    serviceSpecificTech = null;
                                }
                            }
                        }

                        LocalDateTime serviceSlotEnd = serviceApptTime.plusMinutes(srvDuration);

                        // Conflict & Overbooking check
                        boolean bookedAsAnyoneAvailable = (serviceSpecificTech == null && !Boolean.TRUE.equals(bookingRequest.getManualOverride()));
                        if (!Boolean.TRUE.equals(bookingRequest.getManualOverride())) {
                            if (serviceSpecificTech != null && serviceSpecificTech.getId() != null) {
                                Long checkTechId = serviceSpecificTech.getId();
                                if (staffDayOffRepository != null && staffDayOffRepository.existsByTenantIdAndStaffIdAndOffDate(tenantId, checkTechId, serviceApptTime.toLocalDate())) {
                                    String tName = (serviceSpecificTech.getName() != null && !serviceSpecificTech.getName().isBlank()) ? serviceSpecificTech.getName() : "Selected specialist";
                                    return ResponseEntity.badRequest().body(Map.of("message", tName + " is off on this date. Please choose another specialist or Anyone Available."));
                                }
                                boolean hasConf = isTechBusyAtWindow(checkTechId, serviceApptTime, serviceSlotEnd, 15, dayAppts, savedAppointments);
                                if (hasConf) {
                                    String tName = serviceSpecificTech.getName() != null ? serviceSpecificTech.getName() : "Selected specialist";
                                    return ResponseEntity.status(HttpStatus.CONFLICT)
                                            .body(Map.of("message", tName + " is already booked at " + formatTime12Hour(serviceApptTime) + ". Please choose another time or specialist."));
                                }
                            } else if (!tenantTechs.isEmpty()) {
                                // Anyone Available mode: find a free specialist who is not booked at this window and NOT off
                                UserEntity freeTech = null;
                                for (UserEntity tech : tenantTechs) {
                                    if (tech.getId() == null) continue;
                                    if (staffDayOffRepository != null && staffDayOffRepository.existsByTenantIdAndStaffIdAndOffDate(tenantId, tech.getId(), serviceApptTime.toLocalDate())) {
                                        continue;
                                    }
                                    boolean busy = isTechBusyAtWindow(tech.getId(), serviceApptTime, serviceSlotEnd, 15, dayAppts, savedAppointments);
                                    if (!busy) {
                                        freeTech = tech;
                                        break;
                                    }
                                }
                                if (freeTech == null) {
                                    return ResponseEntity.status(HttpStatus.CONFLICT)
                                            .body(Map.of("message", "All specialists are booked for " + srv.getName() + " at " + formatTime12Hour(serviceApptTime) + ". Please choose another time."));
                                }
                                serviceSpecificTech = freeTech;
                            }
                        }

                        appt.setNailTech(serviceSpecificTech);
                        appt.setService(srv);

                        String baseNotes = guest.getNotes() != null ? guest.getNotes().trim() : "";
                        String formattedNotes = baseNotes;
                        if (comboPartTag != null) {
                            formattedNotes = formattedNotes.isEmpty() ? comboPartTag : comboPartTag + " " + formattedNotes;
                        }
                        if (guestItems.size() > 1) {
                            String prefix = "[" + label + "]";
                            formattedNotes = formattedNotes.isEmpty() ? prefix : prefix + " " + formattedNotes;
                        }
                        if (bookedAsAnyoneAvailable && !formattedNotes.contains("[ANYONE AVAILABLE]")) {
                            formattedNotes = formattedNotes.isEmpty() ? "[ANYONE AVAILABLE]" : "[ANYONE AVAILABLE] " + formattedNotes;
                        }
                        appt.setNotes(formattedNotes);

                        Appointment saved = appointmentRepository.save(appt);
                        savedAppointments.add(saved);

                        // Advance sequential start time for the next service/part in this guest's visit
                        currentServiceStart = serviceApptTime.plusMinutes(srvDuration);

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
                }

                grandTotal = grandTotal.add(guestSubtotal);
                totalDurationMinutes += guestDuration;

                Map<String, Object> guestSummary = new LinkedHashMap<>();
                guestSummary.put("guestNumber", guestNum);
                guestSummary.put("customerName", guest.getCustomerName().trim());
                guestSummary.put("customerPhone", guest.getCustomerPhone().trim());
                guestSummary.put("customerEmail", guest.getCustomerEmail() != null ? guest.getCustomerEmail().trim() : "");
                guestSummary.put("notes", guest.getNotes() != null ? guest.getNotes().trim() : "");
                guestSummary.put("services", guestServices);
                guestSummary.put("subtotal", guestSubtotal);
                guestSummary.put("durationMinutes", guestDuration);
                guestReceipts.add(guestSummary);
            }

            LocalDateTime estimatedEndTime = bookingRequest.getAppointmentTime().plusMinutes(Math.max(15, totalDurationMinutes));
            Map<String, Object> responseBody = new LinkedHashMap<>();
            Appointment primary = savedAppointments.isEmpty() ? null : savedAppointments.get(0);
            responseBody.put("id", primary != null ? primary.getId() : null);
            responseBody.put("cancellationToken", primary != null ? primary.getCancellationToken() : null);
            responseBody.put("tenantId", tenantId);
            responseBody.put("appointmentTime", bookingRequest.getAppointmentTime());
            responseBody.put("estimatedEndTime", estimatedEndTime.toString());
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
        if (bookingRequest.getCustomerEmail() != null && !bookingRequest.getCustomerEmail().isBlank() && !isValidEmail(bookingRequest.getCustomerEmail())) {
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

        LocalDateTime singleApptTime = bookingRequest.getAppointmentTime();
        if (bookingRequest.getServiceTimeSlots() != null && singleServiceId != null) {
            String customSlot = bookingRequest.getServiceTimeSlots().get(String.valueOf(singleServiceId));
            if (customSlot != null && !customSlot.isBlank()) {
                try {
                    if (customSlot.contains("T")) {
                        singleApptTime = LocalDateTime.parse(customSlot.trim());
                    } else {
                        LocalTime parsedTime = LocalTime.parse(customSlot.trim());
                        singleApptTime = bookingRequest.getAppointmentTime().toLocalDate().atTime(parsedTime);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        int estDuration = (tenantService.get().getDurationMinutes() != null && tenantService.get().getDurationMinutes() > 0)
                ? tenantService.get().getDurationMinutes() : 45;
        LocalDateTime singleSlotEnd = singleApptTime.plusMinutes(estDuration);

        boolean singleBookedAsAnyoneAvailable = (resolvedTech == null && !Boolean.TRUE.equals(bookingRequest.getManualOverride()));
        if (!Boolean.TRUE.equals(bookingRequest.getManualOverride())) {
            if (resolvedTech == null && !tenantTechs.isEmpty()) {
                List<Appointment> dayAppts = appointmentRepository.findByTenantIdAndAppointmentTimeBetween(
                        tenantId,
                        singleApptTime.toLocalDate().atStartOfDay(),
                        singleApptTime.toLocalDate().plusDays(1).atStartOfDay()
                );
                UserEntity freeTech = null;
                for (UserEntity tech : tenantTechs) {
                    if (tech.getId() == null) continue;
                    if (staffDayOffRepository != null && staffDayOffRepository.existsByTenantIdAndStaffIdAndOffDate(tenantId, tech.getId(), singleApptTime.toLocalDate())) {
                        continue;
                    }
                    boolean busy = isTechBusyAtWindow(tech.getId(), singleApptTime, singleSlotEnd, 15, dayAppts, null);
                    if (!busy) {
                        freeTech = tech;
                        break;
                    }
                }
                if (freeTech == null) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of("message", "All specialists are booked at the selected time slot. Please choose another time or specialist."));
                }
                resolvedTech = freeTech;
            }
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
        appointment.setCustomerEmail(bookingRequest.getCustomerEmail() != null ? bookingRequest.getCustomerEmail().trim() : "");
        appointment.setCustomerPhone(bookingRequest.getCustomerPhone().trim());
        appointment.setAppointmentTime(singleApptTime);
        String singleNotes = bookingRequest.getNotes() != null ? bookingRequest.getNotes().trim() : "";
        if (singleBookedAsAnyoneAvailable && !singleNotes.contains("[ANYONE AVAILABLE]")) {
            singleNotes = singleNotes.isEmpty() ? "[ANYONE AVAILABLE]" : "[ANYONE AVAILABLE] " + singleNotes;
        }
        appointment.setNotes(singleNotes.isEmpty() ? bookingRequest.getNotes() : singleNotes);
        appointment.setStationPreference(bookingRequest.getStationPreference());
        appointment.setCancellationToken(UUID.randomUUID().toString());
        if (customer != null) {
            if (customer.getMedicalFlags() != null && !customer.getMedicalFlags().isBlank()) {
                appointment.setMedicalFlags(customer.getMedicalFlags());
            }
            if (customer.getColorFormula() != null && !customer.getColorFormula().isBlank()) {
                appointment.setColorFormula(customer.getColorFormula());
            }
        }
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
        String cleanTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId.trim().toLowerCase() : "default";
        try {
            Customer customer = customerRepository.findByTenantIdAndEmail(cleanTenant, email.trim())
                    .orElseGet(Customer::new);
            customer.setName(name != null ? name.trim() : "");
            customer.setEmail(email.trim());
            customer.setPhone(phone != null ? phone.trim() : "");
            customer.setTenantId(cleanTenant);
            return customerRepository.save(customer);
        } catch (Exception ignored) {
            return null;
        }
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN', 'ROLE_STAFF', 'STAFF', 'ROLE_NAIL_TECH')")
    @Transactional
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateAppointmentStatus(
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) String colorFormula,
            @RequestParam(required = false) String medicalFlags,
            @RequestParam(required = false, defaultValue = "true") Boolean notifyCustomer,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        String effectiveStatus = status;
        String effectiveReason = reason != null ? reason : notes;
        String effectiveColor = colorFormula;
        String effectiveFlags = medicalFlags;
        boolean shouldNotify = (notifyCustomer != null) ? notifyCustomer : true;
        if (body != null) {
            if (effectiveStatus == null && body.containsKey("status")) {
                effectiveStatus = String.valueOf(body.get("status"));
            }
            if (effectiveReason == null) {
                if (body.containsKey("reason")) effectiveReason = String.valueOf(body.get("reason"));
                else if (body.containsKey("notes")) effectiveReason = String.valueOf(body.get("notes"));
            }
            if (effectiveColor == null && body.containsKey("colorFormula")) {
                effectiveColor = String.valueOf(body.get("colorFormula"));
            }
            if (effectiveFlags == null && body.containsKey("medicalFlags")) {
                effectiveFlags = String.valueOf(body.get("medicalFlags"));
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
        final String finalColor = (effectiveColor != null && !effectiveColor.isBlank()) ? effectiveColor.trim() : null;
        final String finalFlags = (effectiveFlags != null && !effectiveFlags.isBlank()) ? effectiveFlags.trim() : null;
        final boolean finalShouldNotify = shouldNotify;

        Optional<Appointment> apptOpt = appointmentRepository.findByTenantIdAndId(tenantId, id);
        if (apptOpt.isEmpty()) {
            apptOpt = appointmentRepository.findById(id);
        }

        if (apptOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Appointment not found for ID: " + id));
        }

        Appointment appointment = apptOpt.get();
        try {
            Appointment.AppointmentStatus newStatus = Appointment.AppointmentStatus.valueOf(finalStatus.toUpperCase());
            appointment.setStatus(newStatus);

            if (finalColor != null) {
                appointment.setColorFormula(finalColor);
            }
            if (finalFlags != null) {
                appointment.setMedicalFlags(finalFlags);
            }

            // Look up customer record to link customer_id & sync color/safety profile
            Customer customer = null;
            if (appointment.getCustomerEmail() != null && !appointment.getCustomerEmail().isBlank()) {
                customer = customerRepository.findByTenantIdAndEmail(appointment.getTenantId(), appointment.getCustomerEmail().trim()).orElse(null);
                if (customer != null) {
                    boolean customerUpdated = false;
                    if (finalColor != null) {
                        customer.setColorFormula(finalColor);
                        customerUpdated = true;
                    }
                    if (finalFlags != null) {
                        customer.setMedicalFlags(finalFlags);
                        customerUpdated = true;
                    }
                    if (customerUpdated) {
                        customerRepository.save(customer);
                    }
                }
            }

            String serviceName = (appointment.getService() != null && appointment.getService().getName() != null)
                    ? appointment.getService().getName() : "Nail Service";
            String staffName = (appointment.getNailTech() != null)
                    ? (appointment.getNailTech().getName() != null ? appointment.getNailTech().getName() : appointment.getNailTech().getUsername())
                    : "Anyone Available";

            String auditDescription;
            String noteTag;

            if (newStatus == Appointment.AppointmentStatus.CONFIRMED) {
                auditDescription = "Customer arrived & confirmed for " + serviceName + " with " + staffName +
                        (finalReason != null && !finalReason.isBlank() ? " | " + finalReason : "");
                noteTag = "[CONFIRMED: Customer arrived for " + serviceName + " with " + staffName +
                        (finalReason != null && !finalReason.isBlank() ? " | " + finalReason : "") + "]";
            } else if (newStatus == Appointment.AppointmentStatus.CANCELLED) {
                String why = (finalReason != null && !finalReason.isBlank()) ? finalReason : "Cancelled by salon administrator";
                auditDescription = "Cancelled: " + why;
                noteTag = "[CANCELLED Reason: " + why + "]";
            } else if (newStatus == Appointment.AppointmentStatus.NO_SHOW) {
                String why = (finalReason != null && !finalReason.isBlank()) ? finalReason : "Customer did not show up (Marked by admin)";
                auditDescription = "Marked NO_SHOW: " + why;
                noteTag = "[NO_SHOW Reason: " + why + "]";
            } else if (newStatus == Appointment.AppointmentStatus.COMPLETED) {
                auditDescription = "Service completed: " + serviceName + " with " + staffName +
                        (finalReason != null && !finalReason.isBlank() ? " | " + finalReason : "") +
                        (finalColor != null ? " | Formula: " + finalColor : "");
                noteTag = "[COMPLETED: " + serviceName + " with " + staffName +
                        (finalReason != null && !finalReason.isBlank() ? " | Notes: " + finalReason : "") +
                        (finalColor != null ? " | Formula: " + finalColor : "") + "]";
            } else {
                auditDescription = "Status changed to " + newStatus.name() +
                        (finalReason != null && !finalReason.isBlank() ? " (" + finalReason + ")" : "");
                noteTag = "[" + newStatus.name() + " Reason: " + (finalReason != null ? finalReason : "Status updated") + "]";
            }

            String existingNotes = appointment.getNotes() != null ? appointment.getNotes().trim() : "";
            appointment.setNotes(existingNotes.isEmpty() ? noteTag : existingNotes + " | " + noteTag);

            Appointment updated = appointmentRepository.save(appointment);

            if (newStatus == Appointment.AppointmentStatus.COMPLETED && transactionRepository != null && body != null) {
                try {
                    java.math.BigDecimal subtotal = body.containsKey("subtotal") ? new java.math.BigDecimal(String.valueOf(body.get("subtotal"))) :
                            (appointment.getService() != null && appointment.getService().getPrice() != null ? appointment.getService().getPrice() : java.math.BigDecimal.ZERO);
                    java.math.BigDecimal taxRate = body.containsKey("taxRate") ? new java.math.BigDecimal(String.valueOf(body.get("taxRate"))) : new java.math.BigDecimal("0.0825");
                    java.math.BigDecimal taxAmount = body.containsKey("taxAmount") ? new java.math.BigDecimal(String.valueOf(body.get("taxAmount"))) : subtotal.multiply(taxRate).setScale(2, java.math.RoundingMode.HALF_UP);
                    java.math.BigDecimal tipAmount = body.containsKey("tipAmount") ? new java.math.BigDecimal(String.valueOf(body.get("tipAmount"))) : java.math.BigDecimal.ZERO;
                    java.math.BigDecimal discount = body.containsKey("discountAmount") ? new java.math.BigDecimal(String.valueOf(body.get("discountAmount"))) : java.math.BigDecimal.ZERO;
                    java.math.BigDecimal totalAmount = body.containsKey("totalAmount") ? new java.math.BigDecimal(String.valueOf(body.get("totalAmount"))) : subtotal.add(taxAmount).add(tipAmount).subtract(discount);
                    String payMethod = body.containsKey("paymentMethod") ? String.valueOf(body.get("paymentMethod")).toUpperCase() : "CARD";
                    String splitDetails = body.containsKey("splitDetails") ? String.valueOf(body.get("splitDetails")) : null;

                    com.example.demo.entity.AppointmentTransaction tx = new com.example.demo.entity.AppointmentTransaction();
                    tx.setTenantId(tenantId);
                    tx.setAppointmentId(updated.getId());
                    tx.setCustomerId(customer != null ? customer.getId() : null);
                    tx.setNailTechId(appointment.getNailTech() != null ? appointment.getNailTech().getId() : null);
                    tx.setNailTechName(staffName);
                    tx.setCustomerName(updated.getCustomerName());
                    tx.setSubtotal(subtotal);
                    tx.setTaxRate(taxRate);
                    tx.setTaxAmount(taxAmount);
                    tx.setTipAmount(tipAmount);
                    tx.setDiscountAmount(discount);
                    tx.setTotalAmount(totalAmount);
                    tx.setPaymentMethod(payMethod);
                    tx.setSplitDetails(splitDetails);
                    tx.setNotes(finalReason);
                    tx.setStatus("COMPLETED");
                    tx.setCreatedAt(java.time.LocalDateTime.now());
                    transactionRepository.save(tx);
                } catch (Exception ignored) {
                }
            }

            if (auditService != null) {
                String performedBy = auditService.resolvePerformedBy("ADMIN");
                auditService.logActivity(
                        updated.getTenantId(),
                        customer != null ? customer.getId() : null,
                        updated.getId(),
                        updated.getCustomerName(),
                        updated.getCustomerEmail(),
                        updated.getCustomerPhone(),
                        newStatus.name(),
                        performedBy,
                        auditDescription
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
                    "message", "Appointment status updated successfully to " + newStatus.name(),
                    "status", newStatus.name(),
                    "appointment", updated
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid status value provided: " + finalStatus));
        }
    }

    /**
     * Admin freely updates the appointment notes without changing status.
     */
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN', 'ROLE_STAFF', 'STAFF', 'ROLE_NAIL_TECH')")
    @Transactional
    @PatchMapping("/{id}/notes")
    public ResponseEntity<?> updateAppointmentNotes(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(required = false) String notes,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        Optional<Appointment> apptOpt = appointmentRepository.findByTenantIdAndId(tenantId, id);
        if (apptOpt.isEmpty()) {
            apptOpt = appointmentRepository.findById(id);
        }
        if (apptOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Appointment not found for ID: " + id));
        }

        Appointment appointment = apptOpt.get();
        String effectiveNotes = notes;
        if (body != null && body.containsKey("notes")) {
            effectiveNotes = String.valueOf(body.get("notes"));
        }
        appointment.setNotes(effectiveNotes != null ? effectiveNotes.trim() : "");
        Appointment saved = appointmentRepository.save(appointment);

        if (auditService != null) {
            Customer customer = null;
            if (saved.getCustomerEmail() != null && !saved.getCustomerEmail().isBlank()) {
                customer = customerRepository.findByTenantIdAndEmail(saved.getTenantId(), saved.getCustomerEmail().trim()).orElse(null);
            }
            String performedBy = auditService.resolvePerformedBy("ADMIN");
            auditService.logActivity(
                    saved.getTenantId(),
                    customer != null ? customer.getId() : null,
                    saved.getId(),
                    saved.getCustomerName(),
                    saved.getCustomerEmail(),
                    saved.getCustomerPhone(),
                    "NOTE_UPDATED",
                    performedBy,
                    "Admin edited notes: " + (saved.getNotes().isEmpty() ? "[Cleared]" : saved.getNotes())
            );
        }

        return ResponseEntity.ok(Map.of(
                "message", "Notes updated successfully",
                "appointment", saved
        ));
    }

    /**
     * Sends a reminder notification to a specific appointment customer.
     */
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN', 'ROLE_STAFF', 'STAFF', 'ROLE_NAIL_TECH')")
    @PostMapping("/{id}/remind")
    public ResponseEntity<?> sendSingleReminder(
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        Optional<Appointment> apptOpt = appointmentRepository.findByTenantIdAndId(tenantId, id);
        if (apptOpt.isEmpty()) {
            apptOpt = appointmentRepository.findById(id);
        }
        if (apptOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Appointment not found for ID: " + id));
        }

        Appointment appointment = apptOpt.get();
        String performedBy = (auditService != null) ? auditService.resolvePerformedBy("ADMIN") : "ADMIN";
        if (reminderService != null) {
            Map<String, Object> result = reminderService.sendSingleAppointmentReminder(appointment, performedBy);
            return ResponseEntity.ok(result);
        } else if (notificationService != null) {
            notificationService.sendAppointmentReminder(appointment);
            appointment.setReminderSentAt(LocalDateTime.now());
            appointmentRepository.save(appointment);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Reminder sent successfully to " + appointment.getCustomerName(),
                    "reminderSentAt", appointment.getReminderSentAt()
            ));
        }
        return ResponseEntity.ok(Map.of("success", false, "message", "Notification service unavailable"));
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

    @Transactional
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
                            customer = customerRepository.findByTenantIdAndEmail(appt.getTenantId(), appt.getCustomerEmail().trim()).orElse(null);
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

    @Transactional
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
                    .anyMatch(a -> hasOverlapWithBuffer(a, newTime, newSlotEnd, 15));

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

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN', 'ROLE_STAFF', 'STAFF', 'ROLE_NAIL_TECH')")
    @Transactional
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

        LocalDateTime newTime;
        if (body != null && (body.containsKey("appointmentTime") || body.containsKey("date") || body.containsKey("startTime") || body.containsKey("time"))) {
            try {
                if (body.containsKey("appointmentTime") && body.get("appointmentTime") != null && !String.valueOf(body.get("appointmentTime")).isBlank()) {
                    newTime = LocalDateTime.parse(String.valueOf(body.get("appointmentTime")));
                } else {
                    String dateStr = body.containsKey("date") ? String.valueOf(body.get("date")).trim() : appt.getAppointmentTime().toLocalDate().toString();
                    String timeStr = String.valueOf(body.get("startTime") != null ? body.get("startTime") : (body.get("time") != null ? body.get("time") : appt.getAppointmentTime().toLocalTime().toString())).trim();
                    if (!timeStr.contains(":")) timeStr += ":00";
                    if (timeStr.length() == 5) timeStr += ":00";
                    newTime = LocalDateTime.parse(dateStr + "T" + timeStr);
                }
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid date/time format."));
            }
        } else {
            newTime = appt.getAppointmentTime();
        }

        // Optional technician reassignment
        if (body != null && body.containsKey("techId")) {
            Object rawTech = body.get("techId");
            if (rawTech == null || "-1".equals(String.valueOf(rawTech).trim()) || "null".equalsIgnoreCase(String.valueOf(rawTech).trim()) || String.valueOf(rawTech).isBlank()) {
                appt.setNailTech(null);
            } else {
                try {
                    Long techId = Long.parseLong(String.valueOf(rawTech).trim());
                    userRepository.findByTenantIdAndId(tenantId, techId).ifPresent(appt::setNailTech);
                } catch (Exception ignored) {
                }
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
                    .anyMatch(a -> hasOverlapWithBuffer(a, newTime, newSlotEnd, 15));

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

    /**
     * Reassigns an appointment to a specific staff member (or unassigns to 'Anyone Available')
     * with conflict checking against the target technician's active schedule.
     */
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN', 'ROLE_STAFF', 'STAFF', 'ROLE_NAIL_TECH')")
    @Transactional
    @PatchMapping("/{id}/assign-tech")
    public ResponseEntity<?> assignTechnician(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(required = false) Long techId,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        Optional<Appointment> existingOpt = appointmentRepository.findByTenantIdAndId(tenantId, id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Appointment not found"));
        }

        Appointment appt = existingOpt.get();
        Long targetTechId = techId;
        if (targetTechId == null && body != null && body.containsKey("techId")) {
            Object raw = body.get("techId");
            if (raw != null && !"-1".equals(String.valueOf(raw).trim()) && !"null".equalsIgnoreCase(String.valueOf(raw).trim()) && !String.valueOf(raw).isBlank()) {
                try {
                    targetTechId = Long.parseLong(String.valueOf(raw).trim());
                } catch (Exception ignored) {}
            }
        }

        UserEntity targetTech = null;
        if (targetTechId != null) {
            Optional<UserEntity> techOpt = userRepository.findByTenantIdAndId(tenantId, targetTechId);
            if (techOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Selected technician not found"));
            }
            targetTech = techOpt.get();
        }

        // Conflict check if assigned to a specific technician
        if (targetTech != null) {
            LocalDateTime apptTime = appt.getAppointmentTime();
            int duration = (appt.getService() != null && appt.getService().getDurationMinutes() != null)
                    ? appt.getService().getDurationMinutes() : 45;
            LocalDateTime slotEnd = apptTime.plusMinutes(duration);

            List<Appointment> dayAppts = appointmentRepository.findByTenantIdAndAppointmentTimeBetween(
                    tenantId,
                    apptTime.toLocalDate().atStartOfDay(),
                    apptTime.toLocalDate().plusDays(1).atStartOfDay()
            );

            Long techIdToCheck = targetTech.getId();
            if (staffDayOffRepository != null && staffDayOffRepository.existsByTenantIdAndStaffIdAndOffDate(tenantId, techIdToCheck, apptTime.toLocalDate())) {
                String techName = targetTech.getName() != null ? targetTech.getName() : targetTech.getUsername();
                return ResponseEntity.badRequest()
                        .body(Map.of("message", techName + " is off on this date. Please choose another specialist."));
            }
            boolean conflict = dayAppts.stream()
                    .filter(a -> !a.getId().equals(appt.getId()))
                    .filter(a -> a.getStatus() != Appointment.AppointmentStatus.CANCELLED)
                    .filter(a -> a.getNailTech() != null && a.getNailTech().getId().equals(techIdToCheck))
                    .anyMatch(a -> hasOverlapWithBuffer(a, apptTime, slotEnd, 15));

            if (conflict) {
                String techName = targetTech.getName() != null ? targetTech.getName() : targetTech.getUsername();
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", techName + " is already booked at this time slot."));
            }
        }

        String oldTechName = appt.getNailTech() != null ? (appt.getNailTech().getName() != null ? appt.getNailTech().getName() : appt.getNailTech().getUsername()) : "Anyone Available";
        String newTechName = targetTech != null ? (targetTech.getName() != null ? targetTech.getName() : targetTech.getUsername()) : "Anyone Available";

        appt.setNailTech(targetTech);
        String reason = (body != null && body.containsKey("reason")) ? String.valueOf(body.get("reason")) : "Reassigned from " + oldTechName + " to " + newTechName;
        String existingNotes = appt.getNotes() != null ? appt.getNotes().trim() : "";
        if (targetTech != null) {
            existingNotes = existingNotes.replace("[ANYONE AVAILABLE]", "").replaceAll("\\s{2,}", " ").trim();
        }
        String reassignTag = "[REASSIGNED: " + oldTechName + " -> " + newTechName + "]";
        appt.setNotes(existingNotes.isEmpty() ? reassignTag : existingNotes + " | " + reassignTag);

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
                    "REASSIGNED",
                    performedBy,
                    reason
            );
        }

        return ResponseEntity.ok(Map.of(
                "message", "Appointment reassigned successfully to " + newTechName,
                "status", updated.getStatus().name(),
                "appointment", updated
        ));
    }

    @PostMapping({"/dispatch-reminders", "/admin/dispatch-reminders"})
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    public ResponseEntity<?> dispatchReminders(HttpServletRequest request) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        if (reminderService != null) {
            Map<String, Object> result = reminderService.dispatchUpcomingReminders(tenantId);
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.ok(Map.of("message", "Reminder service not active", "remindersSent", 0));
    }

    private static class ServiceSegmentConfig {
        Long serviceId;
        String serviceName;
        int durationMinutes;
        Long techId;
        String techName;
    }
}
