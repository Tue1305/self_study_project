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
}