package com.example.demo.controller;

import com.example.demo.entity.GiftCard;
import com.example.demo.repository.GiftCardRepository;
import com.example.demo.service.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/gift-cards")
public class GiftCardController {

    private final GiftCardRepository giftCardRepository;
    private final TenantContextResolver tenantContextResolver;
    private final SecureRandom random = new SecureRandom();

    public GiftCardController(GiftCardRepository giftCardRepository, TenantContextResolver tenantContextResolver) {
        this.giftCardRepository = giftCardRepository;
        this.tenantContextResolver = tenantContextResolver;
    }

    /**
     * Issue / Create a new gift card for the active tenant
     */
    @PostMapping
    @Transactional
    public ResponseEntity<?> issueGiftCard(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String tenantId = tenantContextResolver.resolveTenantId(request);

        BigDecimal initialAmount = BigDecimal.ZERO;
        if (body.get("initialBalance") != null) {
            try {
                initialAmount = new BigDecimal(String.valueOf(body.get("initialBalance")).trim());
            } catch (Exception ignored) {}
        } else if (body.get("amount") != null) {
            try {
                initialAmount = new BigDecimal(String.valueOf(body.get("amount")).trim());
            } catch (Exception ignored) {}
        }

        if (initialAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Gift card initial balance must be greater than zero."));
        }

        // Custom code or auto-generated code
        String code = body.get("code") != null ? String.valueOf(body.get("code")).trim().toUpperCase() : "";
        if (code.isBlank()) {
            code = generateUniqueCode(tenantId);
        } else {
            // Check uniqueness within active tenant
            if (giftCardRepository.existsByTenantIdAndCodeIgnoreCase(tenantId, code)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "A gift card with code '" + code + "' already exists in this salon."));
            }
        }

        GiftCard gc = new GiftCard(tenantId, code, initialAmount);
        if (body.get("purchaserName") != null) gc.setPurchaserName(String.valueOf(body.get("purchaserName")).trim());
        if (body.get("purchaserEmail") != null) gc.setPurchaserEmail(String.valueOf(body.get("purchaserEmail")).trim());
        if (body.get("recipientName") != null) gc.setRecipientName(String.valueOf(body.get("recipientName")).trim());
        if (body.get("recipientEmail") != null) gc.setRecipientEmail(String.valueOf(body.get("recipientEmail")).trim());
        if (body.get("notes") != null) gc.setNotes(String.valueOf(body.get("notes")).trim());

        if (body.get("expiresAt") != null) {
            try {
                gc.setExpiresAt(LocalDateTime.parse(String.valueOf(body.get("expiresAt")).trim()));
            } catch (Exception ignored) {}
        }

        GiftCard saved = giftCardRepository.save(gc);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Gift card issued successfully.",
                "giftCard", saved
        ));
    }

    /**
     * List all gift cards for the active tenant
     */
    @GetMapping
    public ResponseEntity<?> listGiftCards(HttpServletRequest request) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        List<GiftCard> list = giftCardRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
        return ResponseEntity.ok(list);
    }

    /**
     * Verify gift card balance & validity (used by checkout and balance checkers)
     */
    @GetMapping("/verify")
    public ResponseEntity<?> verifyGiftCard(@RequestParam String code, HttpServletRequest request) {
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Gift card code is required."));
        }
        String tenantId = tenantContextResolver.resolveTenantId(request);
        Optional<GiftCard> opt = giftCardRepository.findByTenantIdAndCodeIgnoreCase(tenantId, code.trim());
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Gift card code not found.", "valid", false));
        }

        GiftCard gc = opt.get();

        // Check expiration
        boolean expired = gc.getExpiresAt() != null && gc.getExpiresAt().isBefore(LocalDateTime.now());
        if (expired && "ACTIVE".equalsIgnoreCase(gc.getStatus())) {
            gc.setStatus("EXPIRED");
            giftCardRepository.save(gc);
        }

        boolean isValid = "ACTIVE".equalsIgnoreCase(gc.getStatus()) && gc.getCurrentBalance().compareTo(BigDecimal.ZERO) > 0;
        return ResponseEntity.ok(Map.of(
                "valid", isValid,
                "code", gc.getCode(),
                "status", gc.getStatus(),
                "currentBalance", gc.getCurrentBalance(),
                "initialBalance", gc.getInitialBalance(),
                "recipientName", gc.getRecipientName() != null ? gc.getRecipientName() : "",
                "expiresAt", gc.getExpiresAt() != null ? gc.getExpiresAt().toString() : ""
        ));
    }

    /**
     * Get gift card by code
     */
    @GetMapping("/{code}")
    public ResponseEntity<?> getGiftCardByCode(@PathVariable String code, HttpServletRequest request) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        Optional<GiftCard> opt = giftCardRepository.findByTenantIdAndCodeIgnoreCase(tenantId, code.trim());
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Gift card not found."));
        }
        return ResponseEntity.ok(opt.get());
    }

    /**
     * Update gift card status (ACTIVE, DEPLETED, CANCELLED)
     */
    @PatchMapping("/{id}/status")
    @Transactional
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest request) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        Optional<GiftCard> opt = giftCardRepository.findByTenantIdAndId(tenantId, id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Gift card not found."));
        }

        String newStatus = body.get("status");
        if (newStatus == null || newStatus.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Status is required."));
        }

        GiftCard gc = opt.get();
        gc.setStatus(newStatus.trim().toUpperCase());
        giftCardRepository.save(gc);

        return ResponseEntity.ok(Map.of("message", "Gift card status updated.", "giftCard", gc));
    }

    private String generateUniqueCode(String tenantId) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        for (int attempt = 0; attempt < 100; attempt++) {
            StringBuilder sb = new StringBuilder("GC-");
            for (int i = 0; i < 4; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            sb.append("-");
            for (int i = 0; i < 4; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            String candidate = sb.toString();
            if (!giftCardRepository.existsByTenantIdAndCodeIgnoreCase(tenantId, candidate)) {
                return candidate;
            }
        }
        return "GC-" + System.currentTimeMillis();
    }
}
