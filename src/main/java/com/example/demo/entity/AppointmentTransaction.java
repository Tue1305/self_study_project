package com.example.demo.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "appointment_transactions", indexes = {
        @Index(name = "idx_appt_trans_tenant", columnList = "tenant_id"),
        @Index(name = "idx_appt_trans_appt", columnList = "tenant_id, appointment_id"),
        @Index(name = "idx_appt_trans_tech", columnList = "tenant_id, nail_tech_id"),
        @Index(name = "idx_appt_trans_created", columnList = "tenant_id, created_at")
})
public class AppointmentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "appointment_id", nullable = false)
    private Long appointmentId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "nail_tech_id")
    private Long nailTechId;

    @Column(name = "nail_tech_name")
    private String nailTechName;

    @Column(name = "customer_name")
    private String customerName;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal taxRate = new BigDecimal("0.0825");

    @Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "tip_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal tipAmount = BigDecimal.ZERO;

    @Column(name = "tip_percent", precision = 5, scale = 2)
    private BigDecimal tipPercent;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "payment_method", nullable = false, length = 64)
    private String paymentMethod = "CARD";

    @Column(name = "split_details", length = 1000)
    private String splitDetails;

    @Column(name = "line_items_json", columnDefinition = "TEXT")
    private String lineItemsJson;

    @Column(name = "points_earned")
    private Integer pointsEarned = 0;

    @Column(name = "points_redeemed")
    private Integer pointsRedeemed = 0;

    @Column(name = "points_discount_amount", precision = 10, scale = 2)
    private BigDecimal pointsDiscountAmount = BigDecimal.ZERO;

    @Column(name = "gift_card_code", length = 64)
    private String giftCardCode;

    @Column(name = "gift_card_amount", precision = 10, scale = 2)
    private BigDecimal giftCardAmount = BigDecimal.ZERO;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false, length = 32)
    private String status = "COMPLETED";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public AppointmentTransaction() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = (tenantId == null || tenantId.isBlank()) ? "default" : tenantId.trim().toLowerCase(); }

    public Long getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public Long getNailTechId() { return nailTechId; }
    public void setNailTechId(Long nailTechId) { this.nailTechId = nailTechId; }

    public String getNailTechName() { return nailTechName; }
    public void setNailTechName(String nailTechName) { this.nailTechName = nailTechName; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal != null ? subtotal : BigDecimal.ZERO; }

    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate != null ? taxRate : BigDecimal.ZERO; }

    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount != null ? taxAmount : BigDecimal.ZERO; }

    public BigDecimal getTipAmount() { return tipAmount; }
    public void setTipAmount(BigDecimal tipAmount) { this.tipAmount = tipAmount != null ? tipAmount : BigDecimal.ZERO; }

    public BigDecimal getTipPercent() { return tipPercent; }
    public void setTipPercent(BigDecimal tipPercent) { this.tipPercent = tipPercent; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount != null ? totalAmount : BigDecimal.ZERO; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod != null ? paymentMethod : "CARD"; }

    public String getSplitDetails() { return splitDetails; }
    public void setSplitDetails(String splitDetails) { this.splitDetails = splitDetails; }

    public String getLineItemsJson() { return lineItemsJson; }
    public void setLineItemsJson(String lineItemsJson) { this.lineItemsJson = lineItemsJson; }

    public Integer getPointsEarned() { return pointsEarned != null ? pointsEarned : 0; }
    public void setPointsEarned(Integer pointsEarned) { this.pointsEarned = pointsEarned != null ? pointsEarned : 0; }

    public Integer getPointsRedeemed() { return pointsRedeemed != null ? pointsRedeemed : 0; }
    public void setPointsRedeemed(Integer pointsRedeemed) { this.pointsRedeemed = pointsRedeemed != null ? pointsRedeemed : 0; }

    public BigDecimal getPointsDiscountAmount() { return pointsDiscountAmount != null ? pointsDiscountAmount : BigDecimal.ZERO; }
    public void setPointsDiscountAmount(BigDecimal pointsDiscountAmount) { this.pointsDiscountAmount = pointsDiscountAmount != null ? pointsDiscountAmount : BigDecimal.ZERO; }

    public String getGiftCardCode() { return giftCardCode; }
    public void setGiftCardCode(String giftCardCode) { this.giftCardCode = giftCardCode; }

    public BigDecimal getGiftCardAmount() { return giftCardAmount != null ? giftCardAmount : BigDecimal.ZERO; }
    public void setGiftCardAmount(BigDecimal giftCardAmount) { this.giftCardAmount = giftCardAmount != null ? giftCardAmount : BigDecimal.ZERO; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status != null ? status : "COMPLETED"; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt != null ? createdAt : LocalDateTime.now(); }
}
