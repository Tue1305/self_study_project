package com.example.demo.controller;

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
    public ResponseEntity<?> bookAppointment(@RequestBody Appointment appointment, HttpServletRequest request) {
        if (appointment.getCustomerName() == null || appointment.getCustomerName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Customer name is required."));
        }
        if (appointment.getCustomerEmail() == null || appointment.getCustomerEmail().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Customer email is required."));
        }
        if (appointment.getCustomerPhone() == null || appointment.getCustomerPhone().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Customer phone number is required."));
        }
        if (appointment.getService() == null || appointment.getService().getId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Please select a service."));
        }
        if (appointment.getAppointmentTime() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Please select an appointment date and time."));
        }

        String tenantId = tenantContextResolver.resolveTenantId(request);

        // Enforce that the booked service belongs to the active tenant
        Optional<ServiceEntity> tenantService = serviceRepository.findByTenantIdAndId(
                tenantId,
                appointment.getService().getId()
        );
        if (tenantService.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Selected service does not belong to this salon."));
        }
        appointment.setService(tenantService.get());
        appointment.setTenantId(tenantId);

        // If "Anyone Available" was chosen (no nail tech ID), set nailTech to null
        if (appointment.getNailTech() != null && appointment.getNailTech().getId() == null) {
            appointment.setNailTech(null);
        }

        // Enforce technician belongs to tenant & check conflicts ONLY inside current tenant scope
        if (appointment.getNailTech() != null && appointment.getNailTech().getId() != null) {
            Long techId = appointment.getNailTech().getId();
            Optional<UserEntity> tenantTech = userRepository.findByTenantIdAndId(tenantId, techId);
            if (tenantTech.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Selected technician does not belong to this salon."));
            }
            appointment.setNailTech(tenantTech.get());

            boolean hasConflict = appointmentRepository.existsByTenantIdAndNailTechIdAndAppointmentTimeAndStatusNot(
                    tenantId,
                    techId,
                    appointment.getAppointmentTime(),
                    Appointment.AppointmentStatus.CANCELLED
            );
            if (hasConflict) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "This technician is already booked at the selected time slot. Please choose another time or technician."));
            }
        }

        // Automatically sync customer details into the Customer table
        if (appointment.getCustomerEmail() != null && !appointment.getCustomerEmail().isBlank()) {
            try {
                Customer customer = customerRepository.findByEmail(appointment.getCustomerEmail().trim())
                        .orElseGet(Customer::new);
                customer.setName(appointment.getCustomerName().trim());
                customer.setEmail(appointment.getCustomerEmail().trim());
                customer.setPhone(appointment.getCustomerPhone().trim());
                customerRepository.save(customer);
            } catch (Exception ignored) {
            }
        }

        Appointment saved = appointmentRepository.save(appointment);
        return ResponseEntity.ok(saved);
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
