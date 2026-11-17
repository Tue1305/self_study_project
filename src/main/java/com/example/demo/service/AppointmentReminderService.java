package com.example.demo.service;

import com.example.demo.entity.Appointment;
import com.example.demo.entity.Customer;
import com.example.demo.repository.AppointmentRepository;
import com.example.demo.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AppointmentReminderService {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentReminderService.class);

    private final AppointmentRepository appointmentRepository;
    private final NotificationService notificationService;
    private final CustomerRepository customerRepository;
    private final AppointmentAuditService auditService;

    @Autowired
    public AppointmentReminderService(
            AppointmentRepository appointmentRepository,
            @Autowired(required = false) NotificationService notificationService,
            CustomerRepository customerRepository,
            @Autowired(required = false) AppointmentAuditService auditService
    ) {
        this.appointmentRepository = appointmentRepository;
        this.notificationService = notificationService;
        this.customerRepository = customerRepository;
        this.auditService = auditService;
    }

    /**
     * Automated hourly background cron job that dispatches reminders for any upcoming
     * appointments within the next 24 hours that haven't received a reminder yet.
     */
    @Scheduled(cron = "0 0 * * * *") // Runs every hour at minute 0
    public void scheduled24HourReminderJob() {
        logger.info("[AppointmentReminderService] Running automated hourly 24-hour reminder scan...");
        try {
            Map<String, Object> result = dispatchUpcomingReminders(null);
            logger.info("[AppointmentReminderService] Automated reminder scan complete: {}", result);
        } catch (Exception e) {
            logger.error("[AppointmentReminderService] Error during automated reminder execution: {}", e.getMessage(), e);
        }
    }

    /**
     * Dispatches 24-hour reminders for appointments starting between now and now + 24 hours.
     * Can be invoked automatically by the scheduler or on-demand by administrators.
     *
     * @param tenantId optional filter for a specific tenant, or null for all tenants.
     * @return summary map containing totalChecked, remindersSent, and list of notified appointments.
     */
    @Transactional
    public Map<String, Object> dispatchUpcomingReminders(String tenantId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowEnd = now.plusHours(24);

        List<Appointment> candidates;
        if (tenantId != null && !tenantId.isBlank() && !"all".equalsIgnoreCase(tenantId)) {
            candidates = appointmentRepository.findByTenantIdAndAppointmentTimeBetweenAndReminderSentAtIsNullAndStatusNot(
                    tenantId.trim().toLowerCase(),
                    now,
                    windowEnd,
                    Appointment.AppointmentStatus.CANCELLED
            );
        } else {
            candidates = appointmentRepository.findByAppointmentTimeBetweenAndReminderSentAtIsNullAndStatusNot(
                    now,
                    windowEnd,
                    Appointment.AppointmentStatus.CANCELLED
            );
        }

        int remindersSent = 0;
        List<Map<String, Object>> sentDetails = new ArrayList<>();

        for (Appointment appt : candidates) {
            // Must have a valid status (PENDING or CONFIRMED)
            if (appt.getStatus() == Appointment.AppointmentStatus.CANCELLED ||
                appt.getStatus() == Appointment.AppointmentStatus.COMPLETED ||
                appt.getStatus() == Appointment.AppointmentStatus.NO_SHOW) {
                continue;
            }

            try {
                if (notificationService != null) {
                    notificationService.sendAppointmentReminder(appt);
                }

                appt.setReminderSentAt(LocalDateTime.now());
                appointmentRepository.save(appt);
                remindersSent++;

                // Log audit activity
                if (auditService != null) {
                    Customer customer = null;
                    if (appt.getCustomerEmail() != null && !appt.getCustomerEmail().isBlank()) {
                        customer = customerRepository.findByTenantIdAndEmail(appt.getTenantId(), appt.getCustomerEmail().trim()).orElse(null);
                    }
                    auditService.logActivity(
                            appt.getTenantId(),
                            customer != null ? customer.getId() : null,
                            appt.getId(),
                            appt.getCustomerName(),
                            appt.getCustomerEmail(),
                            appt.getCustomerPhone(),
                            "REMINDER_SENT",
                            "SYSTEM: REMINDER_SCHEDULER",
                            "Automated 24-hour reminder dispatched for appointment at " + appt.getAppointmentTime()
                    );
                }

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("appointmentId", appt.getId());
                item.put("tenantId", appt.getTenantId());
                item.put("customerName", appt.getCustomerName());
                item.put("customerEmail", appt.getCustomerEmail());
                item.put("appointmentTime", appt.getAppointmentTime());
                item.put("status", appt.getStatus() != null ? appt.getStatus().name() : "PENDING");
                sentDetails.add(item);
            } catch (Exception ex) {
                logger.warn("[AppointmentReminderService] Failed to dispatch reminder for appointment #{}: {}",
                        appt.getId(), ex.getMessage());
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("timestamp", LocalDateTime.now());
        summary.put("windowStart", now);
        summary.put("windowEnd", windowEnd);
        summary.put("tenantId", tenantId != null ? tenantId : "ALL");
        summary.put("totalCandidatesChecked", candidates.size());
        summary.put("remindersSent", remindersSent);
        summary.put("dispatchedAppointments", sentDetails);

        return summary;
    }
}
