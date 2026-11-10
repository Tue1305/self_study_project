package com.example.demo.controller;

import com.example.demo.entity.Appointment;
import com.example.demo.entity.Customer;
import com.example.demo.repository.AppointmentRepository;
import com.example.demo.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/appointments", "/api/bookings"})
@CrossOrigin(origins = "*")
public class AppointmentController {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @GetMapping
    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> bookAppointment(@RequestBody Appointment appointment) {
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

        // If "Anyone Available" was chosen (no nail tech ID), set nailTech to null
        if (appointment.getNailTech() != null && appointment.getNailTech().getId() == null) {
            appointment.setNailTech(null);
        }

        // Check for scheduling conflicts with the selected technician at the same time slot
        if (appointment.getNailTech() != null && appointment.getNailTech().getId() != null) {
            Long techId = appointment.getNailTech().getId();
            boolean hasConflict = appointmentRepository.findAll().stream()
                    .anyMatch(existing -> existing.getStatus() != Appointment.AppointmentStatus.CANCELLED
                            && existing.getNailTech() != null
                            && techId.equals(existing.getNailTech().getId())
                            && appointment.getAppointmentTime().equals(existing.getAppointmentTime()));
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
                // Keep booking resilient even if customer sync encounters a constraint issue
            }
        }

        Appointment saved = appointmentRepository.save(appointment);
        return ResponseEntity.ok(saved);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateAppointmentStatus(
            @PathVariable Long id,
            @RequestParam String status) {

        return appointmentRepository.findById(id)
                .map(appointment -> {
                    try {
                        // Updates the status (CONFIRMED or CANCELLED)
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
