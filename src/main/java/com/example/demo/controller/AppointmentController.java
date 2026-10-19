package com.example.demo.controller;

import com.example.demo.entity.Appointment;
import com.example.demo.repository.AppointmentRepository;
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

    @GetMapping
    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    @PostMapping
    public Appointment bookAppointment(@RequestBody Appointment appointment) {
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