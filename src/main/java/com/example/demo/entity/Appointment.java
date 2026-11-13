package com.example.demo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "appointments", indexes = @Index(name = "idx_appointments_tenant_id", columnList = "tenant_id"))
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String customerPhone;

    @Column(nullable = false)
    private String customerEmail;

    @Column(nullable = false)
    private LocalDateTime appointmentTime;

    @Column(length = 1000)
    private String notes;

    @Column(length = 120)
    private String stationPreference;

    @Column(name = "cancellation_token", length = 64)
    private String cancellationToken;

    @ManyToOne
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceEntity service;

    @ManyToOne
    @JoinColumn(name = "nail_tech_id")
    private UserEntity nailTech; // <-- Added proper relationship annotations

    @Enumerated(EnumType.STRING)
    private AppointmentStatus status = AppointmentStatus.PENDING;

    public enum AppointmentStatus {
        PENDING, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW, RESCHEDULED
    }

    // Constructors
    public Appointment() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public LocalDateTime getAppointmentTime() { return appointmentTime; }
    public void setAppointmentTime(LocalDateTime appointmentTime) { this.appointmentTime = appointmentTime; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getStationPreference() { return stationPreference; }
    public void setStationPreference(String stationPreference) { this.stationPreference = stationPreference; }

    public String getCancellationToken() { return cancellationToken; }
    public void setCancellationToken(String cancellationToken) { this.cancellationToken = cancellationToken; }

    public ServiceEntity getService() { return service; }
    public void setService(ServiceEntity service) { this.service = service; }

    public UserEntity getNailTech() { return nailTech; }
    public void setNailTech(UserEntity nailTech) { this.nailTech = nailTech; }

    public AppointmentStatus getStatus() { return status; }
    public void setStatus(AppointmentStatus status) { this.status = status; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = (tenantId == null || tenantId.isBlank()) ? "default" : tenantId.trim().toLowerCase(); }
}
