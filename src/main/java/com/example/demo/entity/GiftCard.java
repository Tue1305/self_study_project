package com.example.demo.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "gift_cards", indexes = {
        @Index(name = "idx_gift_cards_tenant_code", columnList = "tenant_id, code", unique = true),
        @Index(name = "idx_gift_cards_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_gift_cards_tenant_created", columnList = "tenant_id, created_at")
})
public class GiftCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "initial_balance", nullable = false, precision = 10, scale = 2)
    private BigDecimal initialBalance = BigDecimal.ZERO;

    @Column(name = "current_balance", nullable = false, precision = 10, scale = 2)
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(nullable = false, length = 32)
    private String status = "ACTIVE"; // ACTIVE, DEPLETED, EXPIRED, CANCELLED

    @Column(name = "purchaser_name", length = 128)
    private String purchaserName;

    @Column(name = "purchaser_email", length = 128)
    private String purchaserEmail;

    @Column(name = "recipient_name", length = 128)
    private String recipientName;

    @Column(name = "recipient_email", length = 128)
    private String recipientEmail;

    @Column(length = 1000)
    private String notes;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public GiftCard() {}

    public GiftCard(String tenantId, String code, BigDecimal initialBalance) {
        this.tenantId = (tenantId == null || tenantId.isBlank()) ? "default" : tenantId.trim().toLowerCase();
        this.code = code != null ? code.trim().toUpperCase() : "";
        this.initialBalance = initialBalance != null ? initialBalance : BigDecimal.ZERO;
        this.currentBalance = this.initialBalance;
        this.status = (this.currentBalance.compareTo(BigDecimal.ZERO) > 0) ? "ACTIVE" : "DEPLETED";
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = (tenantId == null || tenantId.isBlank()) ? "default" : tenantId.trim().toLowerCase(); }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code != null ? code.trim().toUpperCase() : ""; }

    public BigDecimal getInitialBalance() { return initialBalance != null ? initialBalance : BigDecimal.ZERO; }
    public void setInitialBalance(BigDecimal initialBalance) { this.initialBalance = initialBalance != null ? initialBalance : BigDecimal.ZERO; }

    public BigDecimal getCurrentBalance() { return currentBalance != null ? currentBalance : BigDecimal.ZERO; }
    public void setCurrentBalance(BigDecimal currentBalance) {
        this.currentBalance = currentBalance != null ? currentBalance : BigDecimal.ZERO;
        if (this.currentBalance.compareTo(BigDecimal.ZERO) <= 0 && "ACTIVE".equalsIgnoreCase(this.status)) {
            this.status = "DEPLETED";
        }
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status != null ? status.trim().toUpperCase() : "ACTIVE"; }

    public String getPurchaserName() { return purchaserName; }
    public void setPurchaserName(String purchaserName) { this.purchaserName = purchaserName; }

    public String getPurchaserEmail() { return purchaserEmail; }
    public void setPurchaserEmail(String purchaserEmail) { this.purchaserEmail = purchaserEmail; }

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }

    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt != null ? createdAt : LocalDateTime.now(); }
}
