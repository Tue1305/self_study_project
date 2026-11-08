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
@RequestMapping("/api/appointments")
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
    public Appointment bookAppointment(@RequestBody Appointment appointment) {
        // If "Anyone Available" was chosen (no nail tech ID), set nailTech to null
        if (appointment.getNailTech() != null && appointment.getNailTech().getId() == null) {
            appointment.setNailTech(null);
        }

        // Automatically sync customer details into the Customer table
        if (appointment.getCustomerEmail() != null && !appointment.getCustomerEmail().isBlank()) {
            try {
                Customer customer = customerRepository.findByEmail(appointment.getCustomerEmail().trim())
                        .orElseGet(Customer::new);
                customer.setName(appointment.getCustomerName());
                customer.setEmail(appointment.getCustomerEmail().trim());
                customer.setPhone(appointment.getCustomerPhone());
                customerRepository.save(customer);
            } catch (Exception ignored) {
                // Keep booking resilient even if customer sync encounters a constraint issue
            }
        }

        return appointmentRepository.save(appointment);
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
