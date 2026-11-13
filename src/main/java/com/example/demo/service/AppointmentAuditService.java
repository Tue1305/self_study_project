package com.example.demo.service;

import com.example.demo.entity.Appointment;
import com.example.demo.entity.AppointmentActivityLog;
import com.example.demo.entity.Customer;
import com.example.demo.repository.AppointmentActivityLogRepository;
import com.example.demo.repository.AppointmentRepository;
import com.example.demo.repository.CustomerRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AppointmentAuditService {

    private final AppointmentActivityLogRepository activityLogRepository;
    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;

    public AppointmentAuditService(
            AppointmentActivityLogRepository activityLogRepository,
            AppointmentRepository appointmentRepository,
            CustomerRepository customerRepository
    ) {
        this.activityLogRepository = activityLogRepository;
        this.appointmentRepository = appointmentRepository;
        this.customerRepository = customerRepository;
    }

    /**
     * Resolves the performed_by label based on active Spring Security context.
     */
    public String resolvePerformedBy(String defaultRole) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equalsIgnoreCase(auth.getName())) {
                String name = auth.getName();
                boolean isAdmin = auth.getAuthorities().stream().anyMatch(a ->
                        a.getAuthority().equalsIgnoreCase("ROLE_ADMIN") || a.getAuthority().equalsIgnoreCase("ADMIN"));
                if (isAdmin) {
                    return "ADMIN: " + name;
                }
                return name;
            }
        } catch (Exception ignored) {}
        return (defaultRole != null && !defaultRole.isBlank()) ? defaultRole : "CUSTOMER";
    }

    /**
     * Logs an appointment status or lifecycle action.
     */
    public AppointmentActivityLog logActivity(
            String tenantId,
            Long customerId,
            Long appointmentId,
            String customerName,
            String customerEmail,
            String customerPhone,
            String actionType,
            String performedBy,
            String notes
    ) {
        AppointmentActivityLog log = new AppointmentActivityLog(
                tenantId,
                customerId,
                appointmentId,
                customerName,
                customerEmail,
                customerPhone,
                actionType != null ? actionType.toUpperCase() : "UPDATED",
                performedBy,
                notes
        );
        return activityLogRepository.save(log);
    }

    /**
     * Retrieves customer appointment history, reliability metrics, and activity logs.
     */
    public Map<String, Object> getCustomerHistory(String tenantId, Long customerId) {
        Optional<Customer> customerOpt = customerRepository.findById(customerId);
        if (customerOpt.isEmpty()) {
            return null;
        }

        Customer customer = customerOpt.get();
        String email = customer.getEmail() != null ? customer.getEmail().trim() : "";
        String phone = customer.getPhone() != null ? customer.getPhone().trim() : "";

        // 1. Fetch appointments for this customer in the given tenant
        List<Appointment> allTenantAppointments = appointmentRepository.findAllByTenantId(tenantId);
        List<Appointment> customerAppointments = allTenantAppointments.stream()
                .filter(apt -> {
                    if (email.isEmpty() && phone.isEmpty()) return false;
                    boolean emailMatch = !email.isEmpty() && email.equalsIgnoreCase(apt.getCustomerEmail());
                    boolean phoneMatch = !phone.isEmpty() && phone.equals(apt.getCustomerPhone());
                    return emailMatch || phoneMatch;
                })
                .sorted(Comparator.comparing(Appointment::getAppointmentTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        // 2. Fetch activity logs by customerId or matching email
        Set<Long> seenLogIds = new HashSet<>();
        List<AppointmentActivityLog> combinedLogs = new ArrayList<>();

        if (!email.isEmpty()) {
            activityLogRepository.findByTenantIdAndCustomerEmailIgnoreCaseOrderByCreatedAtDesc(tenantId, email)
                    .forEach(log -> {
                        if (seenLogIds.add(log.getId())) combinedLogs.add(log);
                    });
        }
        activityLogRepository.findByTenantIdAndCustomerIdOrderByCreatedAtDesc(tenantId, customerId)
                .forEach(log -> {
                    if (seenLogIds.add(log.getId())) combinedLogs.add(log);
                });

        combinedLogs.sort(Comparator.comparing(AppointmentActivityLog::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        // 3. Compute reliability statistics
        long totalBooked = customerAppointments.size();
        long totalConfirmed = 0;
        long totalCompleted = 0;
        long totalCancelled = 0;
        long totalNoShows = 0;

        for (Appointment apt : customerAppointments) {
            if (apt.getStatus() == null) continue;
            switch (apt.getStatus()) {
                case CONFIRMED -> totalConfirmed++;
                case COMPLETED -> totalCompleted++;
                case CANCELLED -> totalCancelled++;
                case NO_SHOW -> totalNoShows++;
                default -> {}
            }
        }

        // Also check if any activity logs recorded NO_SHOW or CANCELLED that might not be on current status
        long logNoShows = combinedLogs.stream().filter(l -> "NO_SHOW".equalsIgnoreCase(l.getActionType())).count();
        long logCancelled = combinedLogs.stream().filter(l -> "CANCELLED".equalsIgnoreCase(l.getActionType())).count();
        long effectiveNoShows = Math.max(totalNoShows, logNoShows);
        long effectiveCancelled = Math.max(totalCancelled, logCancelled);

        Map<String, Object> reliability = new LinkedHashMap<>();
        reliability.put("totalBooked", totalBooked);
        reliability.put("totalConfirmed", totalConfirmed);
        reliability.put("totalCompleted", totalCompleted);
        reliability.put("totalCancelled", effectiveCancelled);
        reliability.put("totalNoShows", effectiveNoShows);
        reliability.put("hasHighNoShowRisk", effectiveNoShows >= 2);

        // Format appointments list for response
        List<Map<String, Object>> formattedAppointments = customerAppointments.stream().map(apt -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", apt.getId());
            m.put("appointmentTime", apt.getAppointmentTime());
            m.put("status", apt.getStatus() != null ? apt.getStatus().name() : "PENDING");
            m.put("serviceName", apt.getService() != null ? apt.getService().getName() : "Nail Service");
            m.put("servicePrice", apt.getService() != null ? apt.getService().getPrice() : null);
            m.put("serviceDuration", apt.getService() != null ? apt.getService().getDurationMinutes() : null);
            m.put("technicianName", apt.getNailTech() != null ? (apt.getNailTech().getName() != null ? apt.getNailTech().getName() : apt.getNailTech().getUsername()) : "Anyone Available");
            m.put("notes", apt.getNotes());
            return m;
        }).toList();

        // Format activity logs list for response
        List<Map<String, Object>> formattedLogs = combinedLogs.stream().map(log -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", log.getId());
            m.put("appointmentId", log.getAppointmentId());
            m.put("actionType", log.getActionType());
            m.put("performedBy", log.getPerformedBy());
            m.put("notes", log.getNotes());
            m.put("createdAt", log.getCreatedAt());

            // Enrich with appointment snapshot details if available
            customerAppointments.stream()
                    .filter(a -> a.getId().equals(log.getAppointmentId()))
                    .findFirst()
                    .ifPresent(a -> {
                        m.put("appointmentTime", a.getAppointmentTime());
                        m.put("serviceName", a.getService() != null ? a.getService().getName() : null);
                        m.put("technicianName", a.getNailTech() != null ? (a.getNailTech().getName() != null ? a.getNailTech().getName() : a.getNailTech().getUsername()) : null);
                    });
            return m;
        }).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("customer", customer);
        response.put("reliability", reliability);
        response.put("appointments", formattedAppointments);
        response.put("activityLogs", formattedLogs);

        return response;
    }
}
