package com.example.demo.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "eod_register_closes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_eod_tenant_date", columnNames = {"tenant_id", "closing_date"})
        },
        indexes = {
                @Index(name = "idx_eod_tenant_closed_at", columnList = "tenant_id, closed_at")
        }
)
public class EodRegisterClose {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "closing_date", nullable = false)
    private LocalDate closingDate;

    @Column(name = "closed_at", nullable = false)
    private LocalDateTime closedAt = LocalDateTime.now();

    @Column(name = "closed_by", nullable = false, length = 120)
    private String closedBy = "OWNER";

    @Column(name = "opening_cash_float", nullable = false, precision = 10, scale = 2)
    private BigDecimal openingCashFloat = new BigDecimal("200.00");

    @Column(name = "expected_cash", nullable = false, precision = 10, scale = 2)
    private BigDecimal expectedCash = BigDecimal.ZERO;

    @Column(name = "actual_cash_counted", nullable = false, precision = 10, scale = 2)
    private BigDecimal actualCashCounted = BigDecimal.ZERO;

    @Column(name = "cash_variance", nullable = false, precision = 10, scale = 2)
    private BigDecimal cashVariance = BigDecimal.ZERO;

    @Column(name = "card_batch_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal cardBatchTotal = BigDecimal.ZERO;

    @Column(name = "gift_card_redemptions", nullable = false, precision = 10, scale = 2)
    private BigDecimal giftCardRedemptions = BigDecimal.ZERO;

    @Column(name = "total_service_sales", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalServiceSales = BigDecimal.ZERO;

    @Column(name = "total_tax_collected", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalTaxCollected = BigDecimal.ZERO;

    @Column(name = "total_tips_collected", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalTipsCollected = BigDecimal.ZERO;

    @Column(name = "total_discounts_given", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalDiscountsGiven = BigDecimal.ZERO;

    @Column(name = "total_gross_revenue", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalGrossRevenue = BigDecimal.ZERO;

    @Column(name = "total_commission_paid", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalCommissionPaid = BigDecimal.ZERO;

    @Column(name = "total_salon_house_share", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalSalonHouseShare = BigDecimal.ZERO;

    @Column(name = "transaction_count", nullable = false)
    private Integer transactionCount = 0;

    @Column(length = 2000)
    private String notes;

    @Column(nullable = false, length = 32)
    private String status = "CLOSED";

    @Column(name = "summary_json", columnDefinition = "TEXT")
    private String summaryJson;

    public EodRegisterClose() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = (tenantId == null || tenantId.isBlank()) ? "default" : tenantId.trim().toLowerCase(); }

    public LocalDate getClosingDate() { return closingDate; }
    public void setClosingDate(LocalDate closingDate) { this.closingDate = closingDate; }

    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }

    public String getClosedBy() { return closedBy; }
    public void setClosedBy(String closedBy) { this.closedBy = closedBy; }

    public BigDecimal getOpeningCashFloat() { return openingCashFloat; }
    public void setOpeningCashFloat(BigDecimal openingCashFloat) { this.openingCashFloat = openingCashFloat; }

    public BigDecimal getExpectedCash() { return expectedCash; }
    public void setExpectedCash(BigDecimal expectedCash) { this.expectedCash = expectedCash; }

    public BigDecimal getActualCashCounted() { return actualCashCounted; }
    public void setActualCashCounted(BigDecimal actualCashCounted) { this.actualCashCounted = actualCashCounted; }

    public BigDecimal getCashVariance() { return cashVariance; }
    public void setCashVariance(BigDecimal cashVariance) { this.cashVariance = cashVariance; }

    public BigDecimal getCardBatchTotal() { return cardBatchTotal; }
    public void setCardBatchTotal(BigDecimal cardBatchTotal) { this.cardBatchTotal = cardBatchTotal; }

    public BigDecimal getGiftCardRedemptions() { return giftCardRedemptions; }
    public void setGiftCardRedemptions(BigDecimal giftCardRedemptions) { this.giftCardRedemptions = giftCardRedemptions; }

    public BigDecimal getTotalServiceSales() { return totalServiceSales; }
    public void setTotalServiceSales(BigDecimal totalServiceSales) { this.totalServiceSales = totalServiceSales; }

    public BigDecimal getTotalTaxCollected() { return totalTaxCollected; }
    public void setTotalTaxCollected(BigDecimal totalTaxCollected) { this.totalTaxCollected = totalTaxCollected; }

    public BigDecimal getTotalTipsCollected() { return totalTipsCollected; }
    public void setTotalTipsCollected(BigDecimal totalTipsCollected) { this.totalTipsCollected = totalTipsCollected; }

    public BigDecimal getTotalDiscountsGiven() { return totalDiscountsGiven; }
    public void setTotalDiscountsGiven(BigDecimal totalDiscountsGiven) { this.totalDiscountsGiven = totalDiscountsGiven; }

    public BigDecimal getTotalGrossRevenue() { return totalGrossRevenue; }
    public void setTotalGrossRevenue(BigDecimal totalGrossRevenue) { this.totalGrossRevenue = totalGrossRevenue; }

    public BigDecimal getTotalCommissionPaid() { return totalCommissionPaid; }
    public void setTotalCommissionPaid(BigDecimal totalCommissionPaid) { this.totalCommissionPaid = totalCommissionPaid; }

    public BigDecimal getTotalSalonHouseShare() { return totalSalonHouseShare; }
    public void setTotalSalonHouseShare(BigDecimal totalSalonHouseShare) { this.totalSalonHouseShare = totalSalonHouseShare; }

    public Integer getTransactionCount() { return transactionCount; }
    public void setTransactionCount(Integer transactionCount) { this.transactionCount = transactionCount; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSummaryJson() { return summaryJson; }
    public void setSummaryJson(String summaryJson) { this.summaryJson = summaryJson; }
}
