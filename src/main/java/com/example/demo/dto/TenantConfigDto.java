package com.example.demo.dto;

import java.util.List;

public class TenantConfigDto {

    private String tenantKey;
    private String customDomain;
    private String salonName;
    private String layoutVariant;
    private ThemeConfig theme;
    private WizardLayoutConfig wizard;

    public TenantConfigDto() {
    }

    public TenantConfigDto(
            String tenantKey,
            String customDomain,
            String salonName,
            String layoutVariant,
            ThemeConfig theme,
            WizardLayoutConfig wizard
    ) {
        this.tenantKey = tenantKey;
        this.customDomain = customDomain;
        this.salonName = salonName;
        this.layoutVariant = layoutVariant;
        this.theme = theme;
        this.wizard = wizard;
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

    public String getLayoutVariant() {
        return layoutVariant;
    }

    public void setLayoutVariant(String layoutVariant) {
        this.layoutVariant = layoutVariant;
    }

    public ThemeConfig getTheme() {
        return theme;
    }

    public void setTheme(ThemeConfig theme) {
        this.theme = theme;
    }

    public WizardLayoutConfig getWizard() {
        return wizard;
    }

    public void setWizard(WizardLayoutConfig wizard) {
        this.wizard = wizard;
    }

    public static class ThemeConfig {
        private String primaryColor;
        private String secondaryColor;
        private String fontFamily;
        private String logoUrl;
        private String bannerImageUrl;

        public ThemeConfig() {
        }

        public ThemeConfig(
                String primaryColor,
                String secondaryColor,
                String fontFamily,
                String logoUrl,
                String bannerImageUrl
        ) {
            this.primaryColor = primaryColor;
            this.secondaryColor = secondaryColor;
            this.fontFamily = fontFamily;
            this.logoUrl = logoUrl;
            this.bannerImageUrl = bannerImageUrl;
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
    }

    public static class WizardLayoutConfig {
        private List<String> stepOrder;
        private boolean enableStationSelection;
        private List<String> availableStations;
        private List<String> enabledCategories;
        private boolean showAppointmentNotes;
        private boolean showStaffQualificationTags;

        public WizardLayoutConfig() {
        }

        public WizardLayoutConfig(
                List<String> stepOrder,
                boolean enableStationSelection,
                List<String> availableStations,
                List<String> enabledCategories,
                boolean showAppointmentNotes,
                boolean showStaffQualificationTags
        ) {
            this.stepOrder = stepOrder;
            this.enableStationSelection = enableStationSelection;
            this.availableStations = availableStations;
            this.enabledCategories = enabledCategories;
            this.showAppointmentNotes = showAppointmentNotes;
            this.showStaffQualificationTags = showStaffQualificationTags;
        }

        public List<String> getStepOrder() {
            return stepOrder;
        }

        public void setStepOrder(List<String> stepOrder) {
            this.stepOrder = stepOrder;
        }

        public boolean isEnableStationSelection() {
            return enableStationSelection;
        }

        public void setEnableStationSelection(boolean enableStationSelection) {
            this.enableStationSelection = enableStationSelection;
        }

        public List<String> getAvailableStations() {
            return availableStations;
        }

        public void setAvailableStations(List<String> availableStations) {
            this.availableStations = availableStations;
        }

        public List<String> getEnabledCategories() {
            return enabledCategories;
        }

        public void setEnabledCategories(List<String> enabledCategories) {
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
    }
}
