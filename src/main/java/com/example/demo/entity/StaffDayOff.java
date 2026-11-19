package com.example.demo.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "staff_days_off",
        indexes = {
                @Index(name = "idx_staff_day_off_tenant_date", columnList = "tenant_id, off_date"),
                @Index(name = "idx_staff_day_off_tenant_staff", columnList = "tenant_id, staff_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_staff_day_off", columnNames = {"tenant_id", "staff_id", "off_date"})
        }
)
public class StaffDayOff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "staff_name")
    private String staffName;

    @Column(name = "off_date", nullable = false)
    private LocalDate offDate;

    @Column(name = "reason", length = 255)
    private String reason = "Day Off";

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public StaffDayOff() {}

    public StaffDayOff(String tenantId, Long staffId, String staffName, LocalDate offDate, String reason, String createdBy) {
        this.tenantId = (tenantId != null && !tenantId.isBlank()) ? tenantId.trim().toLowerCase() : "default";
        this.staffId = staffId;
        this.staffName = staffName;
        this.offDate = offDate;
        this.reason = (reason != null && !reason.isBlank()) ? reason.trim() : "Day Off";
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public Long getStaffId() { return staffId; }
    public void setStaffId(Long staffId) { this.staffId = staffId; }

    public String getStaffName() { return staffName; }
    public void setStaffName(String staffName) { this.staffName = staffName; }

    public LocalDate getOffDate() { return offDate; }
    public void setOffDate(LocalDate offDate) { this.offDate = offDate; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
