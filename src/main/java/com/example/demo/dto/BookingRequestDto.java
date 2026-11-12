package com.example.demo.dto;

import com.example.demo.entity.ServiceEntity;
import com.example.demo.entity.UserEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified booking payload supporting both:
 * 1) Multi-guest, multi-service bookings (`guests` list where each guest has `serviceIds` or `services`)
 * 2) Single-guest, multi-service bookings (`serviceIds` list)
 * 3) Legacy single-service bookings (`service: { id }`)
 */
public class BookingRequestDto {

    // Shared booking fields
    private LocalDateTime appointmentTime;
    private String stationPreference;
    private UserEntity nailTech;

    // Multi-guest payload
    private List<GuestBookingItem> guests = new ArrayList<>();

    // Single-guest / Legacy fields
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private String notes;
    private ServiceEntity service;
    private List<Long> serviceIds = new ArrayList<>();
    private java.util.Map<String, Long> serviceStaffAssignments = new java.util.HashMap<>();

    public static class GuestBookingItem {
        private Integer guestNumber;
        private String customerName;
        private String customerPhone;
        private String customerEmail;
        private String notes;
        private List<Long> serviceIds = new ArrayList<>();
        private List<ServiceEntity> services = new ArrayList<>();
        private java.util.Map<String, Long> serviceStaffAssignments = new java.util.HashMap<>();

        public GuestBookingItem() {
        }

        public Integer getGuestNumber() {
            return guestNumber;
        }

        public void setGuestNumber(Integer guestNumber) {
            this.guestNumber = guestNumber;
        }

        public String getCustomerName() {
            return customerName;
        }

        public void setCustomerName(String customerName) {
            this.customerName = customerName;
        }

        public String getCustomerPhone() {
            return customerPhone;
        }

        public void setCustomerPhone(String customerPhone) {
            this.customerPhone = customerPhone;
        }

        public String getCustomerEmail() {
            return customerEmail;
        }

        public void setCustomerEmail(String customerEmail) {
            this.customerEmail = customerEmail;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }

        public List<Long> getServiceIds() {
            return serviceIds;
        }

        public void setServiceIds(List<Long> serviceIds) {
            this.serviceIds = serviceIds != null ? serviceIds : new ArrayList<>();
        }

        public List<ServiceEntity> getServices() {
            return services;
        }

        public void setServices(List<ServiceEntity> services) {
            this.services = services != null ? services : new ArrayList<>();
        }

        public java.util.Map<String, Long> getServiceStaffAssignments() {
            return serviceStaffAssignments;
        }

        public void setServiceStaffAssignments(java.util.Map<String, Long> serviceStaffAssignments) {
            this.serviceStaffAssignments = serviceStaffAssignments != null ? serviceStaffAssignments : new java.util.HashMap<>();
        }
    }

    public BookingRequestDto() {
    }

    public LocalDateTime getAppointmentTime() {
        return appointmentTime;
    }

    public void setAppointmentTime(LocalDateTime appointmentTime) {
        this.appointmentTime = appointmentTime;
    }

    public String getStationPreference() {
        return stationPreference;
    }

    public void setStationPreference(String stationPreference) {
        this.stationPreference = stationPreference;
    }

    public UserEntity getNailTech() {
        return nailTech;
    }

    public void setNailTech(UserEntity nailTech) {
        this.nailTech = nailTech;
    }

    public List<GuestBookingItem> getGuests() {
        return guests;
    }

    public void setGuests(List<GuestBookingItem> guests) {
        this.guests = guests != null ? guests : new ArrayList<>();
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public ServiceEntity getService() {
        return service;
    }

    public void setService(ServiceEntity service) {
        this.service = service;
    }

    public List<Long> getServiceIds() {
        return serviceIds;
    }

    public void setServiceIds(List<Long> serviceIds) {
        this.serviceIds = serviceIds != null ? serviceIds : new ArrayList<>();
    }

    public java.util.Map<String, Long> getServiceStaffAssignments() {
        return serviceStaffAssignments;
    }

    public void setServiceStaffAssignments(java.util.Map<String, Long> serviceStaffAssignments) {
        this.serviceStaffAssignments = serviceStaffAssignments != null ? serviceStaffAssignments : new java.util.HashMap<>();
    }
}
