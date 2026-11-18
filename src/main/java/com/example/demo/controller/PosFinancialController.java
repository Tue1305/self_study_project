package com.example.demo.controller;

import com.example.demo.dto.CheckoutRequestDto;
import com.example.demo.entity.Appointment;
import com.example.demo.entity.AppointmentTransaction;
import com.example.demo.entity.Customer;
import com.example.demo.entity.GiftCard;
import com.example.demo.entity.UserEntity;
import com.example.demo.repository.AppointmentRepository;
import com.example.demo.repository.AppointmentTransactionRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.GiftCardRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AppointmentAuditService;
import com.example.demo.service.NotificationService;
import com.example.demo.service.TenantContextResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pos")
@CrossOrigin(origins = "*")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN', 'ROLE_STAFF', 'STAFF', 'ROLE_NAIL_TECH')")
public class PosFinancialController {

    private final AppointmentTransactionRepository transactionRepository;
    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final TenantContextResolver tenantContextResolver;
    private final AppointmentAuditService auditService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final GiftCardRepository giftCardRepository;

    @Autowired
    public PosFinancialController(
            AppointmentTransactionRepository transactionRepository,
            AppointmentRepository appointmentRepository,
            CustomerRepository customerRepository,
            TenantContextResolver tenantContextResolver,
            @Autowired(required = false) AppointmentAuditService auditService,
            @Autowired(required = false) NotificationService notificationService,
            @Autowired(required = false) ObjectMapper objectMapper,
            @Autowired(required = false) UserRepository userRepository,
            @Autowired(required = false) GiftCardRepository giftCardRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.appointmentRepository = appointmentRepository;
        this.customerRepository = customerRepository;
        this.tenantContextResolver = tenantContextResolver;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.userRepository = userRepository;
        this.giftCardRepository = giftCardRepository;
    }

    public PosFinancialController(
            AppointmentTransactionRepository transactionRepository,
            AppointmentRepository appointmentRepository,
            CustomerRepository customerRepository,
            TenantContextResolver tenantContextResolver,
            AppointmentAuditService auditService,
            NotificationService notificationService,
            ObjectMapper objectMapper
    ) {
        this(transactionRepository, appointmentRepository, customerRepository, tenantContextResolver, auditService, notificationService, objectMapper, null, null);
    }

    /**
     * Finalizes Checkout and POS financials:
     * - Records base service + on-the-fly add-ons and price adjustments
     * - Computes sales tax (e.g. 8.25%)
     * - Records tip & gratuity (15%, 18%, 20%, or custom $)
     * - Handles payment method (Card, Cash, Apple Pay, Gift Card, or Split Payment)
     * - Updates appointment to COMPLETED and syncs customer color formula
     */
    @PostMapping("/checkout")
    @Transactional
    public ResponseEntity<?> checkoutAppointment(
            @RequestBody CheckoutRequestDto dto,
            HttpServletRequest request
    ) {
        if (dto.getAppointmentId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Appointment ID is required for checkout."));
        }

        String tenantId = tenantContextResolver.resolveTenantId(request);

        Optional<Appointment> apptOpt = appointmentRepository.findByTenantIdAndId(tenantId, dto.getAppointmentId());
        if (apptOpt.isEmpty()) {
            apptOpt = appointmentRepository.findById(dto.getAppointmentId());
        }
        if (apptOpt.isEmpty() || !apptOpt.get().getTenantId().equalsIgnoreCase(tenantId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Appointment not found for ID: " + dto.getAppointmentId()));
        }

        Appointment appointment = apptOpt.get();

        // 1. Calculate / Verify Line Items and Subtotal
        List<CheckoutRequestDto.LineItemDto> items = dto.getLineItems();
        if (items == null || items.isEmpty()) {
            items = new ArrayList<>();
            BigDecimal basePrice = (appointment.getService() != null && appointment.getService().getPrice() != null)
                    ? appointment.getService().getPrice() : BigDecimal.ZERO;
            String srvName = (appointment.getService() != null && appointment.getService().getName() != null)
                    ? appointment.getService().getName() : "Nail Service";
            items.add(new CheckoutRequestDto.LineItemDto(srvName, basePrice, "SERVICE"));
        }

        BigDecimal calculatedSubtotal = BigDecimal.ZERO;
        for (CheckoutRequestDto.LineItemDto item : items) {
            if (item.getPrice() != null) {
                calculatedSubtotal = calculatedSubtotal.add(item.getPrice());
            }
        }

        BigDecimal subtotal = dto.getSubtotal() != null ? dto.getSubtotal() : calculatedSubtotal;
        if (subtotal.compareTo(BigDecimal.ZERO) < 0) {
            subtotal = BigDecimal.ZERO;
        }

        // 2. Tax Calculation (Default 8.25%)
        BigDecimal taxRate = dto.getTaxRate() != null ? dto.getTaxRate() : new BigDecimal("0.0825");
        BigDecimal taxAmount = dto.getTaxAmount() != null
                ? dto.getTaxAmount()
                : subtotal.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);

        // 3. Tip / Gratuity Calculation
        BigDecimal tipAmount = BigDecimal.ZERO;
        if (dto.getTipAmount() != null && dto.getTipAmount().compareTo(BigDecimal.ZERO) > 0) {
            tipAmount = dto.getTipAmount();
        } else if (dto.getTipPercent() != null && dto.getTipPercent().compareTo(BigDecimal.ZERO) > 0) {
            tipAmount = subtotal.multiply(dto.getTipPercent())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }

        // Resolve Customer info early for loyalty points & safety checks
        Customer customer = null;
        if (appointment.getCustomerEmail() != null && !appointment.getCustomerEmail().isBlank()) {
            customer = customerRepository.findByTenantIdAndEmail(tenantId, appointment.getCustomerEmail().trim()).orElse(null);
        }

        // 4. Loyalty Points Redemption & Discount
        BigDecimal discountAmount = dto.getDiscountAmount() != null ? dto.getDiscountAmount() : BigDecimal.ZERO;
        int pointsRedeemed = (dto.getRedeemPoints() != null && dto.getRedeemPoints() > 0) ? dto.getRedeemPoints() : 0;
        BigDecimal pointsDiscount = dto.getPointsDiscountAmount() != null
                ? dto.getPointsDiscountAmount()
                : (pointsRedeemed > 0 ? BigDecimal.valueOf(pointsRedeemed).multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);

        if (pointsRedeemed > 0) {
            if (customer == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Cannot redeem loyalty points: customer profile not found."));
            }
            int availablePts = customer.getLoyaltyPoints();
            if (availablePts < pointsRedeemed) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", String.format("Customer only has %d points available, cannot redeem %d points.", availablePts, pointsRedeemed)
                ));
            }
            if (pointsDiscount.compareTo(BigDecimal.ZERO) > 0) {
                discountAmount = discountAmount.add(pointsDiscount);
            }
        }

        // 5. Total Calculation
        BigDecimal totalAmount = dto.getTotalAmount() != null
                ? dto.getTotalAmount()
                : subtotal.add(taxAmount).add(tipAmount).subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }

        // 6. Gift Card Redemption
        String giftCardCode = (dto.getGiftCardCode() != null && !dto.getGiftCardCode().isBlank())
                ? dto.getGiftCardCode().trim().toUpperCase() : null;
        BigDecimal giftCardAmount = dto.getGiftCardAmount() != null ? dto.getGiftCardAmount() : BigDecimal.ZERO;

        if (giftCardCode != null && giftCardRepository != null) {
            Optional<GiftCard> gcOpt = giftCardRepository.findByTenantIdAndCodeIgnoreCase(tenantId, giftCardCode);
            if (gcOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Gift card '" + giftCardCode + "' not found."));
            }
            GiftCard gc = gcOpt.get();
            if (!"ACTIVE".equalsIgnoreCase(gc.getStatus()) || gc.getCurrentBalance().compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body(Map.of("message", "Gift card '" + giftCardCode + "' is " + gc.getStatus().toLowerCase() + " and cannot be redeemed."));
            }
            if (giftCardAmount.compareTo(BigDecimal.ZERO) <= 0) {
                giftCardAmount = gc.getCurrentBalance().min(totalAmount);
            }
            if (giftCardAmount.compareTo(gc.getCurrentBalance()) > 0) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", String.format("Gift card balance ($%.2f) is less than requested redemption amount ($%.2f).", gc.getCurrentBalance(), giftCardAmount)
                ));
            }
            gc.setCurrentBalance(gc.getCurrentBalance().subtract(giftCardAmount));
            giftCardRepository.save(gc);
        } else if ("GIFT_CARD".equalsIgnoreCase(dto.getPaymentMethod()) && giftCardAmount.compareTo(BigDecimal.ZERO) <= 0) {
            giftCardAmount = totalAmount;
        }

        // 7. Payment Method & Split Payment Validation
        String paymentMethod = (dto.getPaymentMethod() != null && !dto.getPaymentMethod().isBlank())
                ? dto.getPaymentMethod().trim().toUpperCase() : "CARD";

        String splitDetails = dto.getSplitDetails();
        if ("SPLIT".equalsIgnoreCase(paymentMethod)) {
            List<CheckoutRequestDto.SplitPaymentDto> splits = dto.getSplitPayments();
            if (splits != null && !splits.isEmpty()) {
                BigDecimal splitSum = BigDecimal.ZERO;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < splits.size(); i++) {
                    CheckoutRequestDto.SplitPaymentDto sp = splits.get(i);
                    BigDecimal amt = sp.getAmount() != null ? sp.getAmount() : BigDecimal.ZERO;
                    splitSum = splitSum.add(amt);
                    if (i > 0) sb.append(" + ");
                    sb.append(String.format("$%.2f %s", amt, sp.getMethod()));
                }
                splitDetails = sb.toString();

                if (splitSum.subtract(totalAmount).abs().compareTo(new BigDecimal("0.05")) > 0) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "message", String.format("Split payment total ($%.2f) does not match the invoice total ($%.2f). Please adjust amounts.", splitSum, totalAmount)
                    ));
                }
            } else if (splitDetails == null || splitDetails.isBlank()) {
                splitDetails = "Split Payment";
            }
        }

        // 8. Resolve Staff info
        UserEntity tech = appointment.getNailTech();
        Long nailTechId = tech != null ? tech.getId() : null;
        String nailTechName = tech != null ? (tech.getName() != null ? tech.getName() : tech.getUsername()) : "Anyone Available";

        // Serialize line items
        String lineItemsJson = "[]";
        try {
            lineItemsJson = objectMapper.writeValueAsString(items);
        } catch (Exception ignored) {}

        // Calculate points earned (1 point per $1 on service/addon subtotal)
        int pointsEarned = subtotal.intValue();

        // 9. Create and persist POS Transaction
        AppointmentTransaction transaction = new AppointmentTransaction();
        transaction.setTenantId(tenantId);
        transaction.setAppointmentId(appointment.getId());
        transaction.setCustomerId(customer != null ? customer.getId() : null);
        transaction.setNailTechId(nailTechId);
        transaction.setNailTechName(nailTechName);
        transaction.setCustomerName(appointment.getCustomerName());
        transaction.setSubtotal(subtotal);
        transaction.setTaxRate(taxRate);
        transaction.setTaxAmount(taxAmount);
        transaction.setTipAmount(tipAmount);
        transaction.setTipPercent(dto.getTipPercent());
        transaction.setDiscountAmount(discountAmount);
        transaction.setTotalAmount(totalAmount);
        transaction.setPaymentMethod(paymentMethod);
        transaction.setSplitDetails(splitDetails);
        transaction.setLineItemsJson(lineItemsJson);
        transaction.setPointsEarned(pointsEarned);
        transaction.setPointsRedeemed(pointsRedeemed);
        transaction.setPointsDiscountAmount(pointsDiscount);
        transaction.setGiftCardCode(giftCardCode);
        transaction.setGiftCardAmount(giftCardAmount);
        transaction.setNotes(dto.getNotes());
        transaction.setStatus("COMPLETED");
        transaction.setCreatedAt(LocalDateTime.now());

        AppointmentTransaction savedTransaction = transactionRepository.save(transaction);

        // 9. Update Appointment status and financial remarks
        appointment.setStatus(Appointment.AppointmentStatus.COMPLETED);

        if (dto.getColorFormula() != null && !dto.getColorFormula().isBlank()) {
            appointment.setColorFormula(dto.getColorFormula().trim());
        }
        if (dto.getMedicalFlags() != null && !dto.getMedicalFlags().isBlank()) {
            appointment.setMedicalFlags(dto.getMedicalFlags().trim());
        }

        String staffRemarks = dto.getNotes() != null ? dto.getNotes().trim() : "";
        String paymentLabel = "SPLIT".equalsIgnoreCase(paymentMethod) ? "Split (" + splitDetails + ")" : paymentMethod;
        String financialSummary = String.format(
                "[POS CHECKOUT: Subtotal $%.2f | Tax $%.2f | Tip $%.2f | Total $%.2f via %s | Tech: %s%s%s]",
                subtotal, taxAmount, tipAmount, totalAmount, paymentLabel, nailTechName,
                (staffRemarks.isEmpty() ? "" : " | Notes: " + staffRemarks),
                (dto.getColorFormula() != null && !dto.getColorFormula().isBlank() ? " | Formula: " + dto.getColorFormula().trim() : "")
        );

        String existingNotes = appointment.getNotes() != null ? appointment.getNotes().trim() : "";
        appointment.setNotes(existingNotes.isEmpty() ? financialSummary : existingNotes + " | " + financialSummary);

        Appointment savedAppointment = appointmentRepository.save(appointment);

        // 10. Update Customer Profile with loyalty points, color formula & safety alerts
        if (customer != null) {
            boolean custUpdated = false;
            if (pointsRedeemed > 0) {
                int currentPts = customer.getLoyaltyPoints();
                customer.setLoyaltyPoints(Math.max(0, currentPts - pointsRedeemed));
                custUpdated = true;
            }
            if (pointsEarned > 0) {
                customer.setLoyaltyPoints(customer.getLoyaltyPoints() + pointsEarned);
                custUpdated = true;
            }
            if (dto.getColorFormula() != null && !dto.getColorFormula().isBlank()) {
                customer.setColorFormula(dto.getColorFormula().trim());
                custUpdated = true;
            }
            if (dto.getMedicalFlags() != null && !dto.getMedicalFlags().isBlank()) {
                customer.setMedicalFlags(dto.getMedicalFlags().trim());
                custUpdated = true;
            }
            if (custUpdated) {
                customerRepository.save(customer);
            }
        }

        // 11. Audit Logging
        if (auditService != null) {
            String auditNote = String.format(
                    "Checkout completed: $%.2f total (Subtotal $%.2f + Tax $%.2f + Tip $%.2f to %s) paid via %s%s%s",
                    totalAmount, subtotal, taxAmount, tipAmount, nailTechName, paymentLabel,
                    (pointsEarned > 0 ? String.format(" [Earned %d pts]", pointsEarned) : ""),
                    (giftCardAmount.compareTo(BigDecimal.ZERO) > 0 ? String.format(" [Gift Card $%.2f]", giftCardAmount) : "")
            );
            auditService.logActivity(
                    tenantId,
                    customer != null ? customer.getId() : null,
                    appointment.getId(),
                    appointment.getCustomerName(),
                    appointment.getCustomerEmail(),
                    appointment.getCustomerPhone(),
                    "COMPLETED",
                    auditService.resolvePerformedBy("ADMIN"),
                    auditNote
            );
        }

        // 12. Send Thank-You / Receipt Notification
        if (Boolean.TRUE.equals(dto.getNotifyCustomer()) && notificationService != null) {
            try {
                // Future extension: Send detailed POS email receipt
            } catch (Exception ignored) {}
        }

        Map<String, Object> responseBody = new java.util.LinkedHashMap<>();
        responseBody.put("message", "Appointment checked out and payment recorded successfully.");
        responseBody.put("transaction", savedTransaction);
        responseBody.put("appointment", savedAppointment);
        responseBody.put("pointsEarned", pointsEarned);
        responseBody.put("loyaltyPointsEarned", pointsEarned);
        responseBody.put("pointsRedeemed", pointsRedeemed);
        responseBody.put("pointsDiscountAmount", pointsDiscount);
        responseBody.put("customerLoyaltyBalance", customer != null ? customer.getLoyaltyPoints() : 0);
        responseBody.put("loyaltyPointsBalance", customer != null ? customer.getLoyaltyPoints() : 0);
        responseBody.put("giftCardAmountApplied", giftCardAmount);

        return ResponseEntity.ok(responseBody);
    }

    /**
     * Get transaction receipt details for a specific appointment
     */
    @GetMapping("/transactions/{appointmentId}")
    public ResponseEntity<?> getAppointmentTransaction(
            @PathVariable Long appointmentId,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        Optional<AppointmentTransaction> transOpt = transactionRepository.findByTenantIdAndAppointmentId(tenantId, appointmentId);
        if (transOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No checkout transaction found for appointment ID: " + appointmentId));
        }
        return ResponseEntity.ok(transOpt.get());
    }

    /**
     * List all transactions for the tenant with optional date filtering
     */
    @GetMapping("/transactions")
    public ResponseEntity<?> listTransactions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        if (date != null) {
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.plusDays(1).atStartOfDay();
            return ResponseEntity.ok(transactionRepository.findAllByTenantIdAndCreatedAtBetweenOrderByCreatedAtDesc(tenantId, start, end));
        }
        return ResponseEntity.ok(transactionRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId));
    }

    /**
     * End-of-Day Financial Summary & Staff Payouts / Gratuities calculation.
     * Computes daily gross sales, service revenue, tips breakdown (card vs cash),
     * and exact staff payouts for manager settlement.
     */
    @GetMapping("/daily-summary")
    public ResponseEntity<?> getDailySummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        LocalDate targetDate = date != null ? date : LocalDate.now();

        LocalDateTime dayStart = targetDate.atStartOfDay();
        LocalDateTime dayEnd = targetDate.plusDays(1).atStartOfDay();

        List<AppointmentTransaction> dailyTransactions = transactionRepository
                .findAllByTenantIdAndCreatedAtBetweenOrderByCreatedAtDesc(tenantId, dayStart, dayEnd);

        BigDecimal totalGrossRevenue = BigDecimal.ZERO;
        BigDecimal totalServiceSales = BigDecimal.ZERO;
        BigDecimal totalTaxCollected = BigDecimal.ZERO;
        BigDecimal totalTipsCollected = BigDecimal.ZERO;
        BigDecimal totalDiscountsGiven = BigDecimal.ZERO;

        Map<String, Map<String, Object>> paymentBreakdown = new LinkedHashMap<>();
        for (String method : List.of("CARD", "CASH", "APPLE_PAY", "GIFT_CARD", "SPLIT")) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("count", 0);
            data.put("total", BigDecimal.ZERO);
            paymentBreakdown.put(method, data);
        }

        // Staff Payout Map: nailTechId -> StaffPayoutData
        Map<Long, Map<String, Object>> staffPayoutsMap = new LinkedHashMap<>();

        for (AppointmentTransaction tx : dailyTransactions) {
            totalGrossRevenue = totalGrossRevenue.add(tx.getTotalAmount());
            totalServiceSales = totalServiceSales.add(tx.getSubtotal());
            totalTaxCollected = totalTaxCollected.add(tx.getTaxAmount());
            totalTipsCollected = totalTipsCollected.add(tx.getTipAmount());
            totalDiscountsGiven = totalDiscountsGiven.add(tx.getDiscountAmount());

            // Payment method tally
            String methodKey = tx.getPaymentMethod() != null ? tx.getPaymentMethod().toUpperCase() : "CARD";
            if (!paymentBreakdown.containsKey(methodKey)) {
                Map<String, Object> customData = new LinkedHashMap<>();
                customData.put("count", 0);
                customData.put("total", BigDecimal.ZERO);
                paymentBreakdown.put(methodKey, customData);
            }
            Map<String, Object> pData = paymentBreakdown.get(methodKey);
            int count = (int) pData.get("count") + 1;
            BigDecimal pTotal = ((BigDecimal) pData.get("total")).add(tx.getTotalAmount());
            pData.put("count", count);
            pData.put("total", pTotal);

            // Staff Gratuity & Payout aggregation
            Long techId = tx.getNailTechId() != null ? tx.getNailTechId() : 0L;
            Map<String, Object> staffData = staffPayoutsMap.computeIfAbsent(techId, id -> {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("nailTechId", techId);
                s.put("nailTechName", tx.getNailTechName() != null ? tx.getNailTechName() : "Unassigned");
                s.put("appointmentsCount", 0);
                s.put("serviceSales", BigDecimal.ZERO);
                s.put("cardTips", BigDecimal.ZERO);
                s.put("cashTips", BigDecimal.ZERO);
                s.put("otherTips", BigDecimal.ZERO);
                s.put("totalTips", BigDecimal.ZERO);
                s.put("totalStaffEarnings", BigDecimal.ZERO);
                return s;
            });

            int apptCount = (int) staffData.get("appointmentsCount") + 1;
            BigDecimal sServices = ((BigDecimal) staffData.get("serviceSales")).add(tx.getSubtotal());
            BigDecimal sCardTips = (BigDecimal) staffData.get("cardTips");
            BigDecimal sCashTips = (BigDecimal) staffData.get("cashTips");
            BigDecimal sOtherTips = (BigDecimal) staffData.get("otherTips");
            BigDecimal sTotalTips = ((BigDecimal) staffData.get("totalTips")).add(tx.getTipAmount());

            if ("CASH".equalsIgnoreCase(tx.getPaymentMethod())) {
                sCashTips = sCashTips.add(tx.getTipAmount());
            } else if ("CARD".equalsIgnoreCase(tx.getPaymentMethod()) || "APPLE_PAY".equalsIgnoreCase(tx.getPaymentMethod())) {
                sCardTips = sCardTips.add(tx.getTipAmount());
            } else {
                sOtherTips = sOtherTips.add(tx.getTipAmount());
            }

            staffData.put("appointmentsCount", apptCount);
            staffData.put("serviceSales", sServices);
            staffData.put("cardTips", sCardTips);
            staffData.put("cashTips", sCashTips);
            staffData.put("otherTips", sOtherTips);
            staffData.put("totalTips", sTotalTips);
        }

        BigDecimal totalCommissionPaid = BigDecimal.ZERO;
        BigDecimal totalSalonHouseCut = BigDecimal.ZERO;

        for (Map<String, Object> staffData : staffPayoutsMap.values()) {
            Long techId = (Long) staffData.get("nailTechId");
            BigDecimal sServices = (BigDecimal) staffData.get("serviceSales");
            BigDecimal sTotalTips = (BigDecimal) staffData.get("totalTips");

            BigDecimal commissionRate = resolveTechCommissionRate(tenantId, techId);
            BigDecimal commissionEarned = sServices.multiply(commissionRate)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            BigDecimal salonHouseShare = sServices.subtract(commissionEarned);
            BigDecimal totalStaffPayout = commissionEarned.add(sTotalTips);

            staffData.put("commissionRate", commissionRate);
            staffData.put("commissionEarned", commissionEarned);
            staffData.put("salonHouseShare", salonHouseShare);
            staffData.put("totalStaffPayout", totalStaffPayout);
            staffData.put("totalStaffEarnings", totalStaffPayout);

            totalCommissionPaid = totalCommissionPaid.add(commissionEarned);
            totalSalonHouseCut = totalSalonHouseCut.add(salonHouseShare);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("date", targetDate.toString());
        response.put("tenantId", tenantId);
        response.put("totalGrossRevenue", totalGrossRevenue);
        response.put("totalServiceSales", totalServiceSales);
        response.put("totalTaxCollected", totalTaxCollected);
        response.put("totalTipsCollected", totalTipsCollected);
        response.put("totalDiscountsGiven", totalDiscountsGiven);
        response.put("totalCommissionPaid", totalCommissionPaid);
        response.put("totalSalonHouseShare", totalSalonHouseCut);
        response.put("totalSalonRetained", totalSalonHouseCut.add(totalTaxCollected));
        response.put("transactionCount", dailyTransactions.size());
        response.put("paymentBreakdown", paymentBreakdown);
        response.put("staffPayouts", new ArrayList<>(staffPayoutsMap.values()));

        return ResponseEntity.ok(response);
    }

    /**
     * Aggregated Payroll Report across a custom date range (e.g. Weekly: Monday - Sunday).
     * Computes completed appointments, service sales, commission splits, tips, total staff take-home,
     * and salon house share.
     */
    @GetMapping("/payroll-report")
    public ResponseEntity<?> getPayrollReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long nailTechId,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        LocalDate today = LocalDate.now();
        LocalDate start = startDate != null ? startDate : today.with(java.time.DayOfWeek.MONDAY);
        LocalDate end = endDate != null ? endDate : today.with(java.time.DayOfWeek.SUNDAY);

        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }

        LocalDateTime rangeStart = start.atStartOfDay();
        LocalDateTime rangeEnd = end.plusDays(1).atStartOfDay();

        List<AppointmentTransaction> transactions = transactionRepository
                .findAllByTenantIdAndCreatedAtBetweenOrderByCreatedAtDesc(tenantId, rangeStart, rangeEnd);

        if (nailTechId != null && nailTechId > 0) {
            transactions = transactions.stream()
                    .filter(tx -> nailTechId.equals(tx.getNailTechId()))
                    .toList();
        }

        Map<String, Object> report = buildPayrollReportMap(tenantId, start, end, transactions);
        return ResponseEntity.ok(report);
    }

    /**
     * Exports weekly or date-range payroll report as an RFC-4180 CSV spreadsheet.
     */
    @GetMapping(value = "/payroll-report/export-csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportPayrollCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long nailTechId,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        LocalDate today = LocalDate.now();
        LocalDate start = startDate != null ? startDate : today.with(java.time.DayOfWeek.MONDAY);
        LocalDate end = endDate != null ? endDate : today.with(java.time.DayOfWeek.SUNDAY);

        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }

        LocalDateTime rangeStart = start.atStartOfDay();
        LocalDateTime rangeEnd = end.plusDays(1).atStartOfDay();

        List<AppointmentTransaction> transactions = transactionRepository
                .findAllByTenantIdAndCreatedAtBetweenOrderByCreatedAtDesc(tenantId, rangeStart, rangeEnd);

        if (nailTechId != null && nailTechId > 0) {
            transactions = transactions.stream()
                    .filter(tx -> nailTechId.equals(tx.getNailTechId()))
                    .toList();
        }

        Map<String, Object> report = buildPayrollReportMap(tenantId, start, end, transactions);
        String csvContent = buildPayrollCsvString(report);

        byte[] csvBytes = csvContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String filename = String.format("payroll-report-%s-%s-to-%s.csv", tenantId, start, end);

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(csvBytes);
    }

    private BigDecimal resolveTechCommissionRate(String tenantId, Long nailTechId) {
        if (nailTechId == null || nailTechId <= 0 || userRepository == null) {
            return new BigDecimal("60.00");
        }
        return userRepository.findByTenantIdAndId(tenantId, nailTechId)
                .map(UserEntity::getCommissionRate)
                .filter(rate -> rate != null && rate.compareTo(BigDecimal.ZERO) >= 0)
                .orElse(new BigDecimal("60.00"));
    }

    private Map<String, Object> buildPayrollReportMap(
            String tenantId, LocalDate start, LocalDate end, List<AppointmentTransaction> transactions) {
        BigDecimal totalGrossRevenue = BigDecimal.ZERO;
        BigDecimal totalServiceSales = BigDecimal.ZERO;
        BigDecimal totalTaxCollected = BigDecimal.ZERO;
        BigDecimal totalTipsCollected = BigDecimal.ZERO;
        BigDecimal totalDiscountsGiven = BigDecimal.ZERO;

        Map<Long, Map<String, Object>> staffMap = new LinkedHashMap<>();

        for (AppointmentTransaction tx : transactions) {
            totalGrossRevenue = totalGrossRevenue.add(tx.getTotalAmount());
            totalServiceSales = totalServiceSales.add(tx.getSubtotal());
            totalTaxCollected = totalTaxCollected.add(tx.getTaxAmount());
            totalTipsCollected = totalTipsCollected.add(tx.getTipAmount());
            totalDiscountsGiven = totalDiscountsGiven.add(tx.getDiscountAmount());

            Long techId = tx.getNailTechId() != null ? tx.getNailTechId() : 0L;
            Map<String, Object> staffData = staffMap.computeIfAbsent(techId, id -> {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("nailTechId", techId);
                s.put("nailTechName", tx.getNailTechName() != null ? tx.getNailTechName() : "Unassigned");
                s.put("appointmentsCount", 0);
                s.put("serviceSales", BigDecimal.ZERO);
                s.put("cardTips", BigDecimal.ZERO);
                s.put("cashTips", BigDecimal.ZERO);
                s.put("otherTips", BigDecimal.ZERO);
                s.put("totalTips", BigDecimal.ZERO);
                return s;
            });

            int apptCount = (int) staffData.get("appointmentsCount") + 1;
            BigDecimal sServices = ((BigDecimal) staffData.get("serviceSales")).add(tx.getSubtotal());
            BigDecimal sCardTips = (BigDecimal) staffData.get("cardTips");
            BigDecimal sCashTips = (BigDecimal) staffData.get("cashTips");
            BigDecimal sOtherTips = (BigDecimal) staffData.get("otherTips");
            BigDecimal sTotalTips = ((BigDecimal) staffData.get("totalTips")).add(tx.getTipAmount());

            if ("CASH".equalsIgnoreCase(tx.getPaymentMethod())) {
                sCashTips = sCashTips.add(tx.getTipAmount());
            } else if ("CARD".equalsIgnoreCase(tx.getPaymentMethod()) || "APPLE_PAY".equalsIgnoreCase(tx.getPaymentMethod())) {
                sCardTips = sCardTips.add(tx.getTipAmount());
            } else {
                sOtherTips = sOtherTips.add(tx.getTipAmount());
            }

            staffData.put("appointmentsCount", apptCount);
            staffData.put("serviceSales", sServices);
            staffData.put("cardTips", sCardTips);
            staffData.put("cashTips", sCashTips);
            staffData.put("otherTips", sOtherTips);
            staffData.put("totalTips", sTotalTips);
        }

        BigDecimal totalCommissionPaid = BigDecimal.ZERO;
        BigDecimal totalSalonHouseCut = BigDecimal.ZERO;

        List<Map<String, Object>> staffPayroll = new ArrayList<>();
        for (Map<String, Object> staffData : staffMap.values()) {
            Long techId = (Long) staffData.get("nailTechId");
            BigDecimal sServices = (BigDecimal) staffData.get("serviceSales");
            BigDecimal sTotalTips = (BigDecimal) staffData.get("totalTips");

            BigDecimal commissionRate = resolveTechCommissionRate(tenantId, techId);
            BigDecimal commissionEarned = sServices.multiply(commissionRate)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            BigDecimal salonHouseShare = sServices.subtract(commissionEarned);
            BigDecimal totalStaffPayout = commissionEarned.add(sTotalTips);

            staffData.put("commissionRate", commissionRate);
            staffData.put("commissionEarned", commissionEarned);
            staffData.put("salonHouseShare", salonHouseShare);
            staffData.put("totalStaffPayout", totalStaffPayout);
            staffData.put("totalStaffEarnings", totalStaffPayout);

            totalCommissionPaid = totalCommissionPaid.add(commissionEarned);
            totalSalonHouseCut = totalSalonHouseCut.add(salonHouseShare);
            staffPayroll.add(staffData);
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("tenantId", tenantId);
        report.put("startDate", start.toString());
        report.put("endDate", end.toString());
        report.put("transactionCount", transactions.size());
        report.put("totalGrossRevenue", totalGrossRevenue);
        report.put("totalServiceSales", totalServiceSales);
        report.put("totalTaxCollected", totalTaxCollected);
        report.put("totalTipsCollected", totalTipsCollected);
        report.put("totalTipsDistributed", totalTipsCollected);
        report.put("totalDiscountsGiven", totalDiscountsGiven);
        report.put("totalCommissionPaid", totalCommissionPaid);
        report.put("totalSalonHouseShare", totalSalonHouseCut);
        report.put("totalSalonRetained", totalSalonHouseCut.add(totalTaxCollected));
        report.put("staffPayroll", staffPayroll);

        return report;
    }

    private String buildPayrollCsvString(Map<String, Object> report) {
        StringBuilder sb = new StringBuilder();
        // RFC-4180 CSV header
        sb.append("Technician ID,Technician Name,Commission Rate (%),Completed Appts,Service Sales ($),Commission Earned ($),Card Tips ($),Cash Tips ($),Total Tips ($),Total Staff Payout ($),Salon House Share ($)\r\n");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> staffList = (List<Map<String, Object>>) report.get("staffPayroll");
        int totalAppts = 0;
        BigDecimal totalServices = BigDecimal.ZERO;
        BigDecimal totalComm = BigDecimal.ZERO;
        BigDecimal totalCardTips = BigDecimal.ZERO;
        BigDecimal totalCashTips = BigDecimal.ZERO;
        BigDecimal totalTips = BigDecimal.ZERO;
        BigDecimal totalPayout = BigDecimal.ZERO;
        BigDecimal totalSalonCut = BigDecimal.ZERO;

        if (staffList != null) {
            for (Map<String, Object> s : staffList) {
                Long id = (Long) s.get("nailTechId");
                String name = (String) s.get("nailTechName");
                BigDecimal rate = (BigDecimal) s.get("commissionRate");
                int appts = (int) s.get("appointmentsCount");
                BigDecimal svc = (BigDecimal) s.get("serviceSales");
                BigDecimal comm = (BigDecimal) s.get("commissionEarned");
                BigDecimal card = (BigDecimal) s.get("cardTips");
                BigDecimal cash = (BigDecimal) s.get("cashTips");
                BigDecimal tips = (BigDecimal) s.get("totalTips");
                BigDecimal payout = (BigDecimal) s.get("totalStaffPayout");
                BigDecimal salonCut = (BigDecimal) s.get("salonHouseShare");

                totalAppts += appts;
                totalServices = totalServices.add(svc);
                totalComm = totalComm.add(comm);
                totalCardTips = totalCardTips.add(card);
                totalCashTips = totalCashTips.add(cash);
                totalTips = totalTips.add(tips);
                totalPayout = totalPayout.add(payout);
                totalSalonCut = totalSalonCut.add(salonCut);

                sb.append(escapeCsv(id != null && id > 0 ? id.toString() : "N/A")).append(",");
                sb.append(escapeCsv(name)).append(",");
                sb.append(String.format("%.2f%%", rate != null ? rate : BigDecimal.ZERO)).append(",");
                sb.append(appts).append(",");
                sb.append(String.format("%.2f", svc)).append(",");
                sb.append(String.format("%.2f", comm)).append(",");
                sb.append(String.format("%.2f", card)).append(",");
                sb.append(String.format("%.2f", cash)).append(",");
                sb.append(String.format("%.2f", tips)).append(",");
                sb.append(String.format("%.2f", payout)).append(",");
                sb.append(String.format("%.2f", salonCut)).append("\r\n");
            }
        }

        // Summary row
        sb.append("TOTAL,ALL TECHNICIANS,--,").append(totalAppts).append(",");
        sb.append(String.format("%.2f", totalServices)).append(",");
        sb.append(String.format("%.2f", totalComm)).append(",");
        sb.append(String.format("%.2f", totalCardTips)).append(",");
        sb.append(String.format("%.2f", totalCashTips)).append(",");
        sb.append(String.format("%.2f", totalTips)).append(",");
        sb.append(String.format("%.2f", totalPayout)).append(",");
        sb.append(String.format("%.2f", totalSalonCut)).append("\r\n");

        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
