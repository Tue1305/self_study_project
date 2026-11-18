package com.example.demo.controller;

import com.example.demo.service.MarketingAutomationService;
import com.example.demo.service.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/marketing")
public class MarketingController {

    private final MarketingAutomationService marketingService;
    private final TenantContextResolver tenantContextResolver;

    public MarketingController(
            MarketingAutomationService marketingService,
            TenantContextResolver tenantContextResolver
    ) {
        this.marketingService = marketingService;
        this.tenantContextResolver = tenantContextResolver;
    }

    /**
     * Marketing & Retention Overview KPIs
     */
    @GetMapping("/summary")
    public ResponseEntity<?> getMarketingSummary(HttpServletRequest request) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        Map<String, Object> summary = marketingService.getMarketingSummary(tenantId);
        return ResponseEntity.ok(summary);
    }

    /**
     * Lists clients with birthdays in specified or current month
     */
    @GetMapping("/birthdays")
    public ResponseEntity<?> getBirthdays(
            @RequestParam(required = false) Integer month,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        List<Map<String, Object>> list = marketingService.getBirthdayCustomers(tenantId, month);
        return ResponseEntity.ok(list);
    }

    /**
     * Dispatches Birthday Perks (Points or Gift Card Vouchers)
     */
    @PostMapping("/birthdays/dispatch")
    public ResponseEntity<?> dispatchBirthdayPerks(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);

        String perkType = body != null && body.get("perkType") != null ? String.valueOf(body.get("perkType")) : "POINTS";
        Integer pointsAward = 100;
        BigDecimal voucherAmount = new BigDecimal("10.00");
        Long customerId = null;

        if (body != null) {
            if (body.get("pointsAward") != null) {
                try {
                    pointsAward = Integer.parseInt(String.valueOf(body.get("pointsAward")));
                } catch (Exception ignored) {}
            }
            if (body.get("voucherAmount") != null) {
                try {
                    voucherAmount = new BigDecimal(String.valueOf(body.get("voucherAmount")));
                } catch (Exception ignored) {}
            }
            if (body.get("customerId") != null) {
                try {
                    customerId = Long.parseLong(String.valueOf(body.get("customerId")));
                } catch (Exception ignored) {}
            }
        }

        Map<String, Object> result = marketingService.dispatchBirthdayPerks(tenantId, perkType, pointsAward, voucherAmount, customerId);
        return ResponseEntity.ok(result);
    }

    /**
     * Lists lapsed customers (inactive for X days, default 30)
     */
    @GetMapping("/lapsed")
    public ResponseEntity<?> getLapsedCustomers(
            @RequestParam(defaultValue = "30") int days,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        List<Map<String, Object>> list = marketingService.getLapsedCustomers(tenantId, days);
        return ResponseEntity.ok(list);
    }

    /**
     * Dispatches Win-Back Retention Reminder SMS/Email to lapsed customers
     */
    @PostMapping("/win-back/dispatch")
    public ResponseEntity<?> dispatchWinBack(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);

        int daysThreshold = 30;
        List<Long> customerIds = null;

        if (body != null) {
            if (body.get("daysThreshold") != null) {
                try {
                    daysThreshold = Integer.parseInt(String.valueOf(body.get("daysThreshold")));
                } catch (Exception ignored) {}
            }
            if (body.get("customerIds") instanceof List<?> rawList) {
                customerIds = rawList.stream()
                        .map(item -> Long.parseLong(String.valueOf(item)))
                        .toList();
            }
        }

        Map<String, Object> result = marketingService.dispatchWinBackCampaign(tenantId, daysThreshold, customerIds);
        return ResponseEntity.ok(result);
    }
}
