package com.example.demo.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "customer", uniqueConstraints = {
        @UniqueConstraint(name = "uk_customer_tenant_email", columnNames = {"tenant_id", "email"})
})
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    private String phone;

    @Column(name = "tenant_id", length = 64)
    private String tenantId = "default";

    @Column(name = "medical_flags", length = 1000)
    private String medicalFlags;

    @Column(name = "color_formula", length = 1000)
    private String colorFormula;

    @Column(name = "loyalty_points", nullable = false)
    private Integer loyaltyPoints = 0;

    @Column(name = "birth_date")
    private java.time.LocalDate birthDate;

    @Column(name = "last_birthday_perk_year")
    private Integer lastBirthdayPerkYear;

    @Column(name = "last_retention_reminder_sent_at")
    private java.time.LocalDateTime lastRetentionReminderSentAt;

    @Column(name = "opt_out_marketing", nullable = false)
    private Boolean optOutMarketing = false;

    @Column(name = "nail_shape", length = 64)
    private String nailShape;

    @Column(name = "nail_length", length = 64)
    private String nailLength;

    @Column(name = "nail_system", length = 64)
    private String nailSystem;

    @Column(name = "favorite_colors", length = 1000)
    private String favoriteColors;

    @Column(name = "cuticle_care_notes", length = 1000)
    private String cuticleCareNotes;

    public Customer() {}

    public Customer(String name, String email, String phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    public Customer(String name, String email, String phone, String tenantId) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.tenantId = tenantId;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getMedicalFlags() { return medicalFlags; }
    public void setMedicalFlags(String medicalFlags) { this.medicalFlags = medicalFlags; }

    public String getColorFormula() { return colorFormula; }
    public void setColorFormula(String colorFormula) { this.colorFormula = colorFormula; }

    public Integer getLoyaltyPoints() { return loyaltyPoints != null ? loyaltyPoints : 0; }
    public void setLoyaltyPoints(Integer loyaltyPoints) { this.loyaltyPoints = loyaltyPoints != null ? loyaltyPoints : 0; }

    public java.time.LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(java.time.LocalDate birthDate) { this.birthDate = birthDate; }

    public Integer getLastBirthdayPerkYear() { return lastBirthdayPerkYear; }
    public void setLastBirthdayPerkYear(Integer lastBirthdayPerkYear) { this.lastBirthdayPerkYear = lastBirthdayPerkYear; }

    public java.time.LocalDateTime getLastRetentionReminderSentAt() { return lastRetentionReminderSentAt; }
    public void setLastRetentionReminderSentAt(java.time.LocalDateTime lastRetentionReminderSentAt) { this.lastRetentionReminderSentAt = lastRetentionReminderSentAt; }

    public Boolean getOptOutMarketing() { return optOutMarketing != null ? optOutMarketing : false; }
    public void setOptOutMarketing(Boolean optOutMarketing) { this.optOutMarketing = optOutMarketing != null ? optOutMarketing : false; }

    public String getNailShape() { return nailShape; }
    public void setNailShape(String nailShape) { this.nailShape = nailShape; }

    public String getNailLength() { return nailLength; }
    public void setNailLength(String nailLength) { this.nailLength = nailLength; }

    public String getNailSystem() { return nailSystem; }
    public void setNailSystem(String nailSystem) { this.nailSystem = nailSystem; }

    public String getFavoriteColors() { return favoriteColors; }
    public void setFavoriteColors(String favoriteColors) { this.favoriteColors = favoriteColors; }

    public String getCuticleCareNotes() { return cuticleCareNotes; }
    public void setCuticleCareNotes(String cuticleCareNotes) { this.cuticleCareNotes = cuticleCareNotes; }
}