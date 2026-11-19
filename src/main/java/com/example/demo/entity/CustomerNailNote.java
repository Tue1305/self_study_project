package com.example.demo.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_nail_note", indexes = {
        @Index(name = "idx_nail_note_tenant_cust", columnList = "tenant_id, customer_id")
})
public class CustomerNailNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "technician_name")
    private String technicianName;

    @Column(name = "service_date")
    private LocalDate serviceDate;

    @Column(name = "nail_shape", length = 64)
    private String nailShape;

    @Column(name = "nail_length", length = 64)
    private String nailLength;

    @Column(name = "nail_system", length = 64)
    private String nailSystem;

    @Column(name = "color_details", length = 1000)
    private String colorDetails;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "photo_url", columnDefinition = "TEXT")
    private String photoUrl;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public CustomerNailNote() {}

    public CustomerNailNote(String tenantId, Long customerId, String technicianName, LocalDate serviceDate,
                            String nailShape, String nailLength, String nailSystem,
                            String colorDetails, String notes, String photoUrl) {
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.technicianName = technicianName;
        this.serviceDate = serviceDate != null ? serviceDate : LocalDate.now();
        this.nailShape = nailShape;
        this.nailLength = nailLength;
        this.nailSystem = nailSystem;
        this.colorDetails = colorDetails;
        this.notes = notes;
        this.photoUrl = photoUrl;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public Long getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }

    public String getTechnicianName() { return technicianName; }
    public void setTechnicianName(String technicianName) { this.technicianName = technicianName; }

    public LocalDate getServiceDate() { return serviceDate; }
    public void setServiceDate(LocalDate serviceDate) { this.serviceDate = serviceDate; }

    public String getNailShape() { return nailShape; }
    public void setNailShape(String nailShape) { this.nailShape = nailShape; }

    public String getNailLength() { return nailLength; }
    public void setNailLength(String nailLength) { this.nailLength = nailLength; }

    public String getNailSystem() { return nailSystem; }
    public void setNailSystem(String nailSystem) { this.nailSystem = nailSystem; }

    public String getColorDetails() { return colorDetails; }
    public void setColorDetails(String colorDetails) { this.colorDetails = colorDetails; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
