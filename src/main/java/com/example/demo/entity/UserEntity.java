package com.example.demo.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(
        name = "users",
        indexes = {
                @Index(name = "idx_users_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_users_tenant_username", columnList = "tenant_id, username"),
                @Index(name = "idx_users_tenant_email", columnList = "tenant_id, email")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_tenant_username", columnNames = {"tenant_id", "username"}),
                @UniqueConstraint(name = "uk_users_tenant_email", columnNames = {"tenant_id", "email"})
        }
)
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(nullable = false)
    private String username;

    @JsonIgnore
    private String password;

    @Column(name = "role")
    private String role = "ROLE_USER";

    private String subscriptionPlan = "None";
    private String subscriptionStatus = "INACTIVE";

    // Fields for Nail Technician management & User Profile
    private String name;
    private String email;
    private String phoneNumber;
    private String specialty;

    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionRate = new BigDecimal("60.00");

    @Column(name = "shift_start_time", length = 8)
    private String shiftStartTime;

    @Column(name = "shift_end_time", length = 8)
    private String shiftEndTime;

    public UserEntity() {}

    public UserEntity(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public UserEntity(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getSubscriptionPlan() { return subscriptionPlan; }
    public void setSubscriptionPlan(String subscriptionPlan) { this.subscriptionPlan = subscriptionPlan; }

    public String getSubscriptionStatus() { return subscriptionStatus; }
    public void setSubscriptionStatus(String subscriptionStatus) { this.subscriptionStatus = subscriptionStatus; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }

    public BigDecimal getCommissionRate() { return commissionRate != null ? commissionRate : new BigDecimal("60.00"); }
    public void setCommissionRate(BigDecimal commissionRate) { this.commissionRate = commissionRate != null ? commissionRate : new BigDecimal("60.00"); }

    public String getShiftStartTime() { return shiftStartTime; }
    public void setShiftStartTime(String shiftStartTime) { this.shiftStartTime = (shiftStartTime != null && !shiftStartTime.isBlank()) ? shiftStartTime.trim() : null; }

    public String getShiftEndTime() { return shiftEndTime; }
    public void setShiftEndTime(String shiftEndTime) { this.shiftEndTime = (shiftEndTime != null && !shiftEndTime.isBlank()) ? shiftEndTime.trim() : null; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = (tenantId == null || tenantId.isBlank()) ? "default" : tenantId.trim().toLowerCase(); }
}