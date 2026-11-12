package com.example.demo.controller;

import com.example.demo.dto.BookingRequestDto;
import com.example.demo.entity.Appointment;
import com.example.demo.entity.Customer;
import com.example.demo.entity.ServiceEntity;
import com.example.demo.entity.UserEntity;
import com.example.demo.repository.AppointmentRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.ServiceRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping({"/api/appointments", "/api/bookings"})
@CrossOrigin(origins = "*")
public class AppointmentController {

    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final TenantContextResolver tenantContextResolver;

    public AppointmentController(
            AppointmentRepository appointmentRepository,
            CustomerRepository customerRepository,
            ServiceRepository serviceRepository,
            UserRepository userRepository,
            TenantContextResolver tenantContextResolver
    ) {
        this.appointmentRepository = appointmentRepository;
        this.customerRepository = customerRepository;
        this.serviceRepository = serviceRepository;
        this.userRepository = userRepository;
        this.tenantContextResolver = tenantContextResolver;
    }

    @GetMapping
    public List<Appointment> getAllAppointments(HttpServletRequest request) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        return appointmentRepository.findAllByTenantId(tenantId);
    }

    @PostMapping
    public ResponseEntity<?> bookAppointment(@RequestBody BookingRequestDto bookingRequest, HttpServletRequest request) {
        if (bookingRequest.getAppointmentTime() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Please select an appointment date and time."));
        }

        String tenantId = tenantContextResolver.resolveTenantId(request);

        // Validate technician belongs to active tenant and has no conflict at the requested time slot
        UserEntity resolvedTech = null;
        if (bookingRequest.getNailTech() != null && bookingRequest.getNailTech().getId() != null) {
            Long techId = bookingRequest.getNailTech().getId();
            Optional<UserEntity> tenantTech = userRepository.findByTenantIdAndId(tenantId, techId);
            if (tenantTech.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Selected technician does not belong to this salon."));
            }
            resolvedTech = tenantTech.get();

            boolean hasConflict = appointmentRepository.existsByTenantIdAndNailTechIdAndAppointmentTimeAndStatusNot(
                    tenantId,
                    techId,
                    bookingRequest.getAppointmentTime(),
                    Appointment.AppointmentStatus.CANCELLED
            );
            if (hasConflict) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "This technician is already booked at the selected time slot. Please choose another time or technician."));
            }
        }

        boolean isMultiGuestRequest = bookingRequest.getGuests() != null && !bookingRequest.getGuests().isEmpty();
        boolean isTopLevelMultiService = bookingRequest.getServiceIds() != null && bookingRequest.getServiceIds().size() > 1;

        // Branch 1: Multi-guest or multi-service booking flow
        if (isMultiGuestRequest || isTopLevelMultiService) {
            List<BookingRequestDto.GuestBookingItem> guestItems = new ArrayList<>();
            if (isMultiGuestRequest) {
                guestItems.addAll(bookingRequest.getGuests());
            } else {
                BookingRequestDto.GuestBookingItem singleGuest = new BookingRequestDto.GuestBookingItem();
                singleGuest.setGuestNumber(1);
                singleGuest.setCustomerName(bookingRequest.getCustomerName());
                singleGuest.setCustomerPhone(bookingRequest.getCustomerPhone());
                singleGuest.setCustomerEmail(bookingRequest.getCustomerEmail());
                singleGuest.setNotes(bookingRequest.getNotes());
                singleGuest.setServiceIds(bookingRequest.getServiceIds());
                guestItems.add(singleGuest);
            }

            List<Appointment> savedAppointments = new ArrayList<>();
            List<Map<String, Object>> guestReceipts = new ArrayList<>();
            BigDecimal grandTotal = BigDecimal.ZERO;
            int totalDurationMinutes = 0;

            for (int i = 0; i < guestItems.size(); i++) {
                BookingRequestDto.GuestBookingItem guest = guestItems.get(i);
                int guestNum = guest.getGuestNumber() != null ? guest.getGuestNumber() : (i + 1);
                String label = "Guest " + guestNum;

                if (guest.getCustomerName() == null || guest.getCustomerName().isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("message", label + " name is required."));
                }
                if (guest.getCustomerEmail() == null || guest.getCustomerEmail().isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("message", label + " email is required."));
                }
                if (guest.getCustomerPhone() == null || guest.getCustomerPhone().isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("message", label + " phone number is required."));
                }

                List<Long> requestedServiceIds = extractGuestServiceIds(guest);
                if (requestedServiceIds.isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Please select at least one service for " + label + "."));
                }

                List<ServiceEntity> guestServices = new ArrayList<>();
                BigDecimal guestSubtotal = BigDecimal.ZERO;
                int guestDuration = 0;

                for (Long serviceId : requestedServiceIds) {
                    Optional<ServiceEntity> tenantService = serviceRepository.findByTenantIdAndId(tenantId, serviceId);
                    if (tenantService.isEmpty()) {
                        return ResponseEntity.badRequest().body(Map.of("message", "Selected service does not belong to this salon."));
                    }
                    ServiceEntity srv = tenantService.get();
                    guestServices.add(srv);
                    if (srv.getPrice() != null) {
                        guestSubtotal = guestSubtotal.add(srv.getPrice());
                    }
                    guestDuration += (srv.getDurationMinutes() != null ? srv.getDurationMinutes() : 45);
                }

                syncCustomerRecord(guest.getCustomerName(), guest.getCustomerEmail(), guest.getCustomerPhone());

                for (ServiceEntity srv : guestServices) {
                    Appointment appt = new Appointment();
                    appt.setTenantId(tenantId);
                    appt.setCustomerName(guest.getCustomerName().trim());
                    appt.setCustomerEmail(guest.getCustomerEmail().trim());
                    appt.setCustomerPhone(guest.getCustomerPhone().trim());
                    appt.setAppointmentTime(bookingRequest.getAppointmentTime());
                    appt.setStationPreference(bookingRequest.getStationPreference());

                    // Check if per-service staff selection was assigned for this service
                    UserEntity serviceSpecificTech = resolvedTech;
                    if (guest.getServiceStaffAssignments() != null && srv.getId() != null) {
                        Long assignedTechId = guest.getServiceStaffAssignments().get(String.valueOf(srv.getId()));
                        if (assignedTechId == null) {
                            // Try Long key if any
                            for (Map.Entry<String, Long> entry : guest.getServiceStaffAssignments().entrySet()) {
                                if (String.valueOf(srv.getId()).equals(entry.getKey())) {
                                    assignedTechId = entry.getValue();
                                    break;
                                }
                            }
                        }
                        if (assignedTechId != null) {
                            serviceSpecificTech = userRepository.findByTenantIdAndId(tenantId, assignedTechId).orElse(resolvedTech);
                        }
                    }

                    appt.setNailTech(serviceSpecificTech);
                    appt.setService(srv);

                    String baseNotes = guest.getNotes() != null ? guest.getNotes().trim() : "";
                    if (guestItems.size() > 1) {
                        String prefix = "[" + label + "]";
                        appt.setNotes(baseNotes.isEmpty() ? prefix : prefix + " " + baseNotes);
                    } else {
                        appt.setNotes(baseNotes);
                    }

                    Appointment saved = appointmentRepository.save(appt);
                    savedAppointments.add(saved);
                }

                grandTotal = grandTotal.add(guestSubtotal);
                totalDurationMinutes += guestDuration;

                Map<String, Object> guestSummary = new LinkedHashMap<>();
                guestSummary.put("guestNumber", guestNum);
                guestSummary.put("customerName", guest.getCustomerName().trim());
                guestSummary.put("customerPhone", guest.getCustomerPhone().trim());
                guestSummary.put("customerEmail", guest.getCustomerEmail().trim());
                guestSummary.put("notes", guest.getNotes() != null ? guest.getNotes().trim() : "");
                guestSummary.put("services", guestServices);
                guestSummary.put("subtotal", guestSubtotal);
                guestSummary.put("durationMinutes", guestDuration);
                guestReceipts.add(guestSummary);
            }

            Map<String, Object> responseBody = new LinkedHashMap<>();
            Appointment primary = savedAppointments.isEmpty() ? null : savedAppointments.get(0);
            responseBody.put("id", primary != null ? primary.getId() : null);
            responseBody.put("tenantId", tenantId);
            responseBody.put("appointmentTime", bookingRequest.getAppointmentTime());
            responseBody.put("stationPreference", bookingRequest.getStationPreference());
            responseBody.put("nailTech", resolvedTech);
            responseBody.put("guests", guestReceipts);
            responseBody.put("grandTotal", grandTotal);
            responseBody.put("totalDurationMinutes", totalDurationMinutes);
            responseBody.put("appointments", savedAppointments);

            return ResponseEntity.ok(responseBody);
        }

        // Branch 2: Standard single-service booking (backward compatible)
        if (bookingRequest.getCustomerName() == null || bookingRequest.getCustomerName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Customer name is required."));
        }
        if (bookingRequest.getCustomerEmail() == null || bookingRequest.getCustomerEmail().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Customer email is required."));
        }
        if (bookingRequest.getCustomerPhone() == null || bookingRequest.getCustomerPhone().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Customer phone number is required."));
        }

        Long singleServiceId = null;
        if (bookingRequest.getService() != null && bookingRequest.getService().getId() != null) {
            singleServiceId = bookingRequest.getService().getId();
        } else if (bookingRequest.getServiceIds() != null && !bookingRequest.getServiceIds().isEmpty()) {
            singleServiceId = bookingRequest.getServiceIds().get(0);
        }

        if (singleServiceId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Please select a service."));
        }

        Optional<ServiceEntity> tenantService = serviceRepository.findByTenantIdAndId(tenantId, singleServiceId);
        if (tenantService.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Selected service does not belong to this salon."));
        }

        syncCustomerRecord(
                bookingRequest.getCustomerName(),
                bookingRequest.getCustomerEmail(),
                bookingRequest.getCustomerPhone()
        );

        Appointment appointment = new Appointment();
        appointment.setTenantId(tenantId);
        appointment.setCustomerName(bookingRequest.getCustomerName().trim());
        appointment.setCustomerEmail(bookingRequest.getCustomerEmail().trim());
        appointment.setCustomerPhone(bookingRequest.getCustomerPhone().trim());
        appointment.setAppointmentTime(bookingRequest.getAppointmentTime());
        appointment.setNotes(bookingRequest.getNotes());
        appointment.setStationPreference(bookingRequest.getStationPreference());
        appointment.setService(tenantService.get());
        appointment.setNailTech(resolvedTech);

        Appointment saved = appointmentRepository.save(appointment);
        return ResponseEntity.ok(saved);
    }

    private List<Long> extractGuestServiceIds(BookingRequestDto.GuestBookingItem guest) {
        List<Long> ids = new ArrayList<>();
        if (guest.getServiceIds() != null) {
            for (Long id : guest.getServiceIds()) {
                if (id != null && !ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        if (guest.getServices() != null) {
            for (ServiceEntity s : guest.getServices()) {
                if (s != null && s.getId() != null && !ids.contains(s.getId())) {
                    ids.add(s.getId());
                }
            }
        }
        return ids;
    }

    private void syncCustomerRecord(String name, String email, String phone) {
        if (email == null || email.isBlank()) {
            return;
        }
        try {
            Customer customer = customerRepository.findByEmail(email.trim())
                    .orElseGet(Customer::new);
            customer.setName(name != null ? name.trim() : "");
            customer.setEmail(email.trim());
            customer.setPhone(phone != null ? phone.trim() : "");
            customerRepository.save(customer);
        } catch (Exception ignored) {
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateAppointmentStatus(
            @PathVariable Long id,
            @RequestParam String status,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        return appointmentRepository.findByTenantIdAndId(tenantId, id)
                .map(appointment -> {
                    try {
                        appointment.setStatus(Appointment.AppointmentStatus.valueOf(status.toUpperCase()));
                        appointmentRepository.save(appointment);
                        return ResponseEntity.ok(Map.of("message", "Appointment status updated successfully"));
                    } catch (IllegalArgumentException e) {
                        return ResponseEntity.badRequest().body(Map.of("message", "Invalid status value provided"));
                    }
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Appointment not found")));
    }
}
