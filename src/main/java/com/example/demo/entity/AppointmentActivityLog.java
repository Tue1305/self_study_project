package com.example.demo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "appointment_activity_logs",
    indexes = {
        @Index(name = "idx_appointment_logs_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_appointment_logs_customer_id", columnList = "customer_id"),
        @Index(name = "idx_appointment_logs_appointment_id", columnList = "appointment_id"),
        @Index(name = "idx_appointment_logs_customer_email", columnList = "customer_email"),
        @Index(name = "idx_appointment_logs_created_at", columnList = "created_at")
    }
)
public class AppointmentActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "action_type", nullable = false, length = 64)
    private String actionType; // BOOKED, CONFIRMED, CANCELLED, NO_SHOW, COMPLETED, RESCHEDULED

    @Column(name = "performed_by", length = 255)
    private String performedBy = "SYSTEM"; // ADMIN: <staff_name>, CUSTOMER, SYSTEM

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public AppointmentActivityLog() {}

    public AppointmentActivityLog(
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
        this.tenantId = (tenantId != null && !tenantId.isBlank()) ? tenantId : "default";
        this.customerId = customerId;
        this.appointmentId = appointmentId;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.customerPhone = customerPhone;
        this.actionType = actionType;
        this.performedBy = (performedBy != null && !performedBy.isBlank()) ? performedBy : "SYSTEM";
        this.notes = notes;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public Long getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
