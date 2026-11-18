package com.example.demo.service;

import com.example.demo.entity.Appointment;
import com.example.demo.entity.AppointmentTransaction;
import com.example.demo.entity.Customer;
import com.example.demo.entity.GiftCard;
import com.example.demo.repository.AppointmentRepository;
import com.example.demo.repository.AppointmentTransactionRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.GiftCardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class MarketingAutomationService {

    private static final Logger logger = LoggerFactory.getLogger(MarketingAutomationService.class);
    private final CustomerRepository customerRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentTransactionRepository transactionRepository;
    private final GiftCardRepository giftCardRepository;
    private final AppointmentAuditService auditService;
    private final NotificationService notificationService;
    private final SecureRandom random = new SecureRandom();

    public MarketingAutomationService(
            CustomerRepository customerRepository,
            AppointmentRepository appointmentRepository,
            AppointmentTransactionRepository transactionRepository,
            @Autowired(required = false) GiftCardRepository giftCardRepository,
            @Autowired(required = false) AppointmentAuditService auditService,
            @Autowired(required = false) NotificationService notificationService
    ) {
        this.customerRepository = customerRepository;
        this.appointmentRepository = appointmentRepository;
        this.transactionRepository = transactionRepository;
        this.giftCardRepository = giftCardRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    /**
     * Aggregated metrics for marketing & retention dashboard.
     */
    public Map<String, Object> getMarketingSummary(String tenantId) {
        List<Customer> allCustomers = customerRepository.findAllByTenantId(tenantId);
        int currentYear = LocalDate.now().getYear();
        int currentMonth = LocalDate.now().getMonthValue();

        long birthdayThisMonthCount = allCustomers.stream()
                .filter(c -> c.getBirthDate() != null && c.getBirthDate().getMonthValue() == currentMonth)
                .count();

        long birthdaysAwardedThisYear = allCustomers.stream()
                .filter(c -> c.getLastBirthdayPerkYear() != null && c.getLastBirthdayPerkYear() == currentYear)
                .count();

        List<Map<String, Object>> lapsed = getLapsedCustomers(tenantId, 30);

        long winBacksSentLast30Days = allCustomers.stream()
                .filter(c -> c.getLastRetentionReminderSentAt() != null
                        && c.getLastRetentionReminderSentAt().isAfter(LocalDateTime.now().minusDays(30)))
                .count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCustomers", allCustomers.size());
        summary.put("birthdaysThisMonth", birthdayThisMonthCount);
        summary.put("birthdaysAwardedThisYear", birthdaysAwardedThisYear);
        summary.put("lapsedCustomersCount", lapsed.size());
        summary.put("winBacksSentLast30Days", winBacksSentLast30Days);

        return summary;
    }

    /**
     * Lists customers with birthdays in given month (defaults to current month).
     */
    public List<Map<String, Object>> getBirthdayCustomers(String tenantId, Integer month) {
        int targetMonth = (month != null && month >= 1 && month <= 12) ? month : LocalDate.now().getMonthValue();
        int currentYear = LocalDate.now().getYear();

        List<Customer> customers = customerRepository.findAllByTenantId(tenantId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Customer c : customers) {
            if (c.getBirthDate() != null && c.getBirthDate().getMonthValue() == targetMonth) {
                boolean awarded = c.getLastBirthdayPerkYear() != null && c.getLastBirthdayPerkYear() >= currentYear;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", c.getId());
                item.put("name", c.getName());
                item.put("email", c.getEmail());
                item.put("phone", c.getPhone());
                item.put("birthDate", c.getBirthDate().toString());
                item.put("dayOfMonth", c.getBirthDate().getDayOfMonth());
                item.put("loyaltyPoints", c.getLoyaltyPoints());
                item.put("awardedThisYear", awarded);
                item.put("optOutMarketing", c.getOptOutMarketing());
                result.add(item);
            }
        }

        result.sort(Comparator.comparingInt(m -> (Integer) m.get("dayOfMonth")));
        return result;
    }

    /**
     * Automatically awards Birthday Perks (100 VIP Points or $10 Gift Card Voucher) and notifies clients.
     */
    @Transactional
    public Map<String, Object> dispatchBirthdayPerks(
            String tenantId,
            String perkType,
            Integer pointsAward,
            BigDecimal voucherAmount,
            Long specificCustomerId
    ) {
        int currentYear = LocalDate.now().getYear();
        int currentMonth = LocalDate.now().getMonthValue();
        String type = (perkType != null && perkType.equalsIgnoreCase("VOUCHER")) ? "VOUCHER" : "POINTS";
        int pts = (pointsAward != null && pointsAward > 0) ? pointsAward : 100;
        BigDecimal voucher = (voucherAmount != null && voucherAmount.compareTo(BigDecimal.ZERO) > 0)
                ? voucherAmount : new BigDecimal("10.00");

        List<Customer> candidates;
        if (specificCustomerId != null) {
            Optional<Customer> opt = customerRepository.findById(specificCustomerId);
            candidates = (opt.isPresent() && opt.get().getTenantId().equalsIgnoreCase(tenantId))
                    ? List.of(opt.get()) : List.of();
        } else {
            candidates = customerRepository.findAllByTenantId(tenantId).stream()
                    .filter(c -> c.getBirthDate() != null && c.getBirthDate().getMonthValue() == currentMonth)
                    .filter(c -> !Boolean.TRUE.equals(c.getOptOutMarketing()))
                    .filter(c -> c.getLastBirthdayPerkYear() == null || c.getLastBirthdayPerkYear() < currentYear)
                    .toList();
        }

        int awardedCount = 0;
        List<Map<String, Object>> details = new ArrayList<>();

        for (Customer c : candidates) {
            String giftCardCode = null;
            if ("VOUCHER".equals(type) && giftCardRepository != null) {
                giftCardCode = generateUniqueGiftCardCode(tenantId);
                GiftCard gc = new GiftCard(tenantId, giftCardCode, voucher);
                gc.setRecipientName(c.getName());
                gc.setRecipientEmail(c.getEmail());
                gc.setNotes("Birthday VIP Gift Voucher " + currentYear);
                gc.setExpiresAt(LocalDateTime.now().plusDays(60)); // 60 days to redeem
                giftCardRepository.save(gc);
            } else {
                c.setLoyaltyPoints(c.getLoyaltyPoints() + pts);
            }

            c.setLastBirthdayPerkYear(currentYear);
            customerRepository.save(c);
            awardedCount++;

            // Audit Log
            if (auditService != null) {
                auditService.logActivity(
                        tenantId,
                        c.getId(),
                        null,
                        c.getName(),
                        c.getEmail(),
                        c.getPhone(),
                        "BIRTHDAY_PERK",
                        auditService.resolvePerformedBy("SYSTEM"),
                        String.format("Birthday perk awarded: %s",
                                "VOUCHER".equals(type) ? "$" + voucher + " Gift Card (" + giftCardCode + ")" : pts + " VIP Points")
                );
            }

            // Dispatch notification
            if (notificationService != null) {
                notificationService.sendBirthdayPerkNotification(
                        c,
                        "POINTS".equals(type) ? pts : 0,
                        giftCardCode,
                        voucher,
                        tenantId
                );
            }

            details.add(Map.of(
                    "customerId", c.getId(),
                    "customerName", c.getName(),
                    "customerEmail", c.getEmail() != null ? c.getEmail() : "",
                    "perkType", type,
                    "pointsAwarded", "POINTS".equals(type) ? pts : 0,
                    "giftCardCode", giftCardCode != null ? giftCardCode : ""
            ));
        }

        return Map.of(
                "message", String.format("Successfully celebrated %d birthday customer(s) with %s!",
                        awardedCount, "VOUCHER".equals(type) ? "$" + voucher + " gift vouchers" : pts + " VIP points"),
                "awardedCount", awardedCount,
                "recipients", details
        );
    }

    /**
     * Identifies lapsed customers who haven't completed an appointment in X days (default 30)
     * and do not have an active upcoming appointment.
     */
    public List<Map<String, Object>> getLapsedCustomers(String tenantId, int lapsedDaysThreshold) {
        int threshold = lapsedDaysThreshold > 0 ? lapsedDaysThreshold : 30;
        LocalDate cutoffDate = LocalDate.now().minusDays(threshold);
        LocalDateTime now = LocalDateTime.now();

        List<Customer> allCustomers = customerRepository.findAllByTenantId(tenantId);
        List<AppointmentTransaction> allTransactions = transactionRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
        List<Appointment> allAppointments = appointmentRepository.findAllByTenantId(tenantId);

        List<Map<String, Object>> lapsedList = new ArrayList<>();

        for (Customer c : allCustomers) {
            if (Boolean.TRUE.equals(c.getOptOutMarketing())) continue;

            // Check if customer has upcoming booked appointments
            boolean hasUpcoming = allAppointments.stream()
                    .filter(a -> a.getCustomerEmail() != null && a.getCustomerEmail().equalsIgnoreCase(c.getEmail()))
                    .anyMatch(a -> a.getAppointmentTime() != null
                            && a.getAppointmentTime().isAfter(now)
                            && a.getStatus() != Appointment.AppointmentStatus.CANCELLED);

            if (hasUpcoming) continue;

            // Find most recent visit date from transactions or completed appointments
            LocalDateTime lastVisitTime = null;
            String lastServiceName = "Salon Service";

            // From transactions
            Optional<AppointmentTransaction> lastTx = allTransactions.stream()
                    .filter(tx -> tx.getCustomerId() != null && tx.getCustomerId().equals(c.getId()))
                    .max(Comparator.comparing(AppointmentTransaction::getCreatedAt));

            if (lastTx.isPresent()) {
                lastVisitTime = lastTx.get().getCreatedAt();
            } else {
                // Check completed appointments
                Optional<Appointment> lastAppt = allAppointments.stream()
                        .filter(a -> a.getCustomerEmail() != null && a.getCustomerEmail().equalsIgnoreCase(c.getEmail()))
                        .filter(a -> a.getStatus() == Appointment.AppointmentStatus.COMPLETED)
                        .max(Comparator.comparing(Appointment::getAppointmentTime));

                if (lastAppt.isPresent()) {
                    lastVisitTime = lastAppt.get().getAppointmentTime();
                    if (lastAppt.get().getService() != null) {
                        lastServiceName = lastAppt.get().getService().getName();
                    }
                }
            }

            if (lastVisitTime != null && lastVisitTime.toLocalDate().isBefore(cutoffDate)) {
                long daysInactive = java.time.temporal.ChronoUnit.DAYS.between(lastVisitTime.toLocalDate(), LocalDate.now());
                boolean recentlyReminded = c.getLastRetentionReminderSentAt() != null
                        && c.getLastRetentionReminderSentAt().isAfter(now.minusDays(21));

                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", c.getId());
                map.put("name", c.getName());
                map.put("email", c.getEmail());
                map.put("phone", c.getPhone());
                map.put("lastVisitDate", lastVisitTime.toLocalDate().toString());
                map.put("daysInactive", daysInactive);
                map.put("lastServiceName", lastServiceName);
                map.put("loyaltyPoints", c.getLoyaltyPoints());
                map.put("recentlyReminded", recentlyReminded);
                map.put("lastReminderSentAt", c.getLastRetentionReminderSentAt() != null ? c.getLastRetentionReminderSentAt().toString() : null);

                lapsedList.add(map);
            }
        }

        lapsedList.sort((a, b) -> Long.compare((Long) b.get("daysInactive"), (Long) a.get("daysInactive")));
        return lapsedList;
    }

    /**
     * Dispatches Win-Back / Lapsed Customer Retention Campaign to inactive clients.
     */
    @Transactional
    public Map<String, Object> dispatchWinBackCampaign(String tenantId, int lapsedDaysThreshold, List<Long> customerIds) {
        List<Map<String, Object>> lapsed = getLapsedCustomers(tenantId, lapsedDaysThreshold);
        Set<Long> targetIds = (customerIds != null && !customerIds.isEmpty()) ? new HashSet<>(customerIds) : null;

        int dispatchedCount = 0;
        List<Map<String, Object>> recipients = new ArrayList<>();

        for (Map<String, Object> item : lapsed) {
            Long cid = (Long) item.get("id");
            if (targetIds != null && !targetIds.contains(cid)) continue;

            // Skip if recently reminded in last 21 days
            if (Boolean.TRUE.equals(item.get("recentlyReminded")) && (targetIds == null || targetIds.size() > 1)) {
                continue;
            }

            Optional<Customer> custOpt = customerRepository.findById(cid);
            if (custOpt.isEmpty()) continue;
            Customer cust = custOpt.get();

            LocalDate lastVisitDate = LocalDate.parse((String) item.get("lastVisitDate"));
            String lastServiceName = (String) item.get("lastServiceName");

            cust.setLastRetentionReminderSentAt(LocalDateTime.now());
            customerRepository.save(cust);
            dispatchedCount++;

            // Audit Log
            if (auditService != null) {
                auditService.logActivity(
                        tenantId,
                        cust.getId(),
                        null,
                        cust.getName(),
                        cust.getEmail(),
                        cust.getPhone(),
                        "WIN_BACK_CAMPAIGN",
                        auditService.resolvePerformedBy("SYSTEM"),
                        String.format("Win-back retention reminder dispatched (Inactive for %d days since %s)",
                                (Long) item.get("daysInactive"), lastVisitDate)
                );
            }

            // Dispatch SMS / Email
            if (notificationService != null) {
                notificationService.sendWinBackReminderNotification(cust, lastVisitDate, lastServiceName, tenantId);
            }

            recipients.add(Map.of(
                    "customerId", cust.getId(),
                    "customerName", cust.getName(),
                    "customerEmail", cust.getEmail() != null ? cust.getEmail() : "",
                    "daysInactive", item.get("daysInactive")
            ));
        }

        return Map.of(
                "message", String.format("Successfully sent win-back retention reminders to %d lapsed clients!", dispatchedCount),
                "dispatchedCount", dispatchedCount,
                "recipients", recipients
        );
    }

    private String generateUniqueGiftCardCode(String tenantId) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        for (int i = 0; i < 50; i++) {
            StringBuilder sb = new StringBuilder("GC-BDAY-");
            for (int k = 0; k < 4; k++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            String candidate = sb.toString();
            if (giftCardRepository == null || !giftCardRepository.existsByTenantIdAndCodeIgnoreCase(tenantId, candidate)) {
                return candidate;
            }
        }
        return "GC-BDAY-" + System.currentTimeMillis();
    }
}
