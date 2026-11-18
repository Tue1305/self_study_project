package com.example.demo.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class CheckoutRequestDto {

    private Long appointmentId;
    private List<LineItemDto> lineItems = new ArrayList<>();
    private BigDecimal subtotal;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal tipAmount;
    private BigDecimal tipPercent;
    private BigDecimal discountAmount;
    private Integer redeemPoints;
    private BigDecimal pointsDiscountAmount;
    private String giftCardCode;
    private BigDecimal giftCardAmount;
    private BigDecimal totalAmount;
    private String paymentMethod; // CARD, CASH, APPLE_PAY, GIFT_CARD, SPLIT
    private List<SplitPaymentDto> splitPayments = new ArrayList<>();
    private String splitDetails;
    private String colorFormula;
    private String medicalFlags;
    private String notes;
    private Boolean notifyCustomer = true;

    public static class LineItemDto {
        private String name;
        private BigDecimal price;
        private String type; // SERVICE, ADDON, ADJUSTMENT, DISCOUNT

        public LineItemDto() {}

        public LineItemDto(String name, BigDecimal price, String type) {
            this.name = name;
            this.price = price;
            this.type = type;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }

    public static class SplitPaymentDto {
        private String method; // CARD, CASH, APPLE_PAY, GIFT_CARD
        private BigDecimal amount;

        public SplitPaymentDto() {}

        public SplitPaymentDto(String method, BigDecimal amount) {
            this.method = method;
            this.amount = amount;
        }

        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
    }

    public CheckoutRequestDto() {}

    // Getters and Setters
    public Long getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }

    public List<LineItemDto> getLineItems() { return lineItems; }
    public void setLineItems(List<LineItemDto> lineItems) { this.lineItems = lineItems != null ? lineItems : new ArrayList<>(); }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }

    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }

    public BigDecimal getTipAmount() { return tipAmount; }
    public void setTipAmount(BigDecimal tipAmount) { this.tipAmount = tipAmount; }

    public BigDecimal getTipPercent() { return tipPercent; }
    public void setTipPercent(BigDecimal tipPercent) { this.tipPercent = tipPercent; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

    public Integer getRedeemPoints() { return redeemPoints; }
    public void setRedeemPoints(Integer redeemPoints) { this.redeemPoints = redeemPoints; }

    public BigDecimal getPointsDiscountAmount() { return pointsDiscountAmount; }
    public void setPointsDiscountAmount(BigDecimal pointsDiscountAmount) { this.pointsDiscountAmount = pointsDiscountAmount; }

    public String getGiftCardCode() { return giftCardCode; }
    public void setGiftCardCode(String giftCardCode) { this.giftCardCode = giftCardCode; }

    public BigDecimal getGiftCardAmount() { return giftCardAmount; }
    public void setGiftCardAmount(BigDecimal giftCardAmount) { this.giftCardAmount = giftCardAmount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public List<SplitPaymentDto> getSplitPayments() { return splitPayments; }
    public void setSplitPayments(List<SplitPaymentDto> splitPayments) { this.splitPayments = splitPayments != null ? splitPayments : new ArrayList<>(); }

    public String getSplitDetails() { return splitDetails; }
    public void setSplitDetails(String splitDetails) { this.splitDetails = splitDetails; }

    public String getColorFormula() { return colorFormula; }
    public void setColorFormula(String colorFormula) { this.colorFormula = colorFormula; }

    public String getMedicalFlags() { return medicalFlags; }
    public void setMedicalFlags(String medicalFlags) { this.medicalFlags = medicalFlags; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Boolean getNotifyCustomer() { return notifyCustomer; }
    public void setNotifyCustomer(Boolean notifyCustomer) { this.notifyCustomer = notifyCustomer; }
}
