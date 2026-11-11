package com.example.demo.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "tenant_configs")
public class TenantConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String tenantKey;

    @Column(unique = true, length = 160)
    private String customDomain;

    @Column(nullable = false, length = 120)
    private String salonName = "Jenny's Nail Studio";

    @Column(nullable = false, length = 20)
    private String primaryColor = "#e83e8c";

    @Column(nullable = false, length = 20)
    private String secondaryColor = "#343a40";

    @Column(nullable = false, length = 120)
    private String fontFamily = "'Poppins', sans-serif";

    @Column(length = 500)
    private String logoUrl;

    @Column(length = 500)
    private String bannerImageUrl;

    @Column(nullable = false, length = 120)
    private String stepOrder = "SERVICE,STAFF,STATION,DETAILS";

    @Column(nullable = false)
    private boolean enableStationSelection = false;

    @Column(length = 500)
    private String availableStations = "Main Floor Chair 1,Main Floor Chair 2,VIP Window Suite,Quiet Spa Room";

    @Column(length = 500)
    private String enabledCategories = "";

    @Column(nullable = false)
    private boolean showAppointmentNotes = true;

    @Column(nullable = false)
    private boolean showStaffQualificationTags = false;

    @Column(nullable = false, length = 40)
    private String layoutVariant = "classic";

    @Column(nullable = false)
    private boolean defaultTenant = false;

    public TenantConfig() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTenantKey() {
        return tenantKey;
    }

    public void setTenantKey(String tenantKey) {
        this.tenantKey = tenantKey;
    }

    public String getCustomDomain() {
        return customDomain;
    }

    public void setCustomDomain(String customDomain) {
        this.customDomain = customDomain;
    }

    public String getSalonName() {
        return salonName;
    }

    public void setSalonName(String salonName) {
        this.salonName = salonName;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public void setPrimaryColor(String primaryColor) {
        this.primaryColor = primaryColor;
    }

    public String getSecondaryColor() {
        return secondaryColor;
    }

    public void setSecondaryColor(String secondaryColor) {
        this.secondaryColor = secondaryColor;
    }

    public String getFontFamily() {
        return fontFamily;
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getBannerImageUrl() {
        return bannerImageUrl;
    }

    public void setBannerImageUrl(String bannerImageUrl) {
        this.bannerImageUrl = bannerImageUrl;
    }

    public String getStepOrder() {
        return stepOrder;
    }

    public void setStepOrder(String stepOrder) {
        this.stepOrder = stepOrder;
    }

    public boolean isEnableStationSelection() {
        return enableStationSelection;
    }

    public void setEnableStationSelection(boolean enableStationSelection) {
        this.enableStationSelection = enableStationSelection;
    }

    public String getAvailableStations() {
        return availableStations;
    }

    public void setAvailableStations(String availableStations) {
        this.availableStations = availableStations;
    }

    public String getEnabledCategories() {
        return enabledCategories;
    }

    public void setEnabledCategories(String enabledCategories) {
        this.enabledCategories = enabledCategories;
    }

    public boolean isShowAppointmentNotes() {
        return showAppointmentNotes;
    }

    public void setShowAppointmentNotes(boolean showAppointmentNotes) {
        this.showAppointmentNotes = showAppointmentNotes;
    }

    public boolean isShowStaffQualificationTags() {
        return showStaffQualificationTags;
    }

    public void setShowStaffQualificationTags(boolean showStaffQualificationTags) {
        this.showStaffQualificationTags = showStaffQualificationTags;
    }

    public String getLayoutVariant() {
        return layoutVariant;
    }

    public void setLayoutVariant(String layoutVariant) {
        this.layoutVariant = layoutVariant;
    }

    public boolean isDefaultTenant() {
        return defaultTenant;
    }

    public void setDefaultTenant(boolean defaultTenant) {
        this.defaultTenant = defaultTenant;
    }
}
