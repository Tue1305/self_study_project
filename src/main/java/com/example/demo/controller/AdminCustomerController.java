package com.example.demo.controller;

import com.example.demo.entity.Customer;
import com.example.demo.entity.CustomerNailNote;
import com.example.demo.repository.CustomerNailNoteRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.service.AppointmentAuditService;
import com.example.demo.service.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/admin/customers")
@CrossOrigin(origins = "*")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
public class AdminCustomerController {

    private final AppointmentAuditService auditService;
    private final TenantContextResolver tenantContextResolver;
    private final CustomerRepository customerRepository;
    private final CustomerNailNoteRepository nailNoteRepository;

    public AdminCustomerController(
            AppointmentAuditService auditService,
            TenantContextResolver tenantContextResolver
    ) {
        this(auditService, tenantContextResolver, null, null);
    }

    @Autowired
    public AdminCustomerController(
            AppointmentAuditService auditService,
            TenantContextResolver tenantContextResolver,
            CustomerRepository customerRepository,
            CustomerNailNoteRepository nailNoteRepository
    ) {
        this.auditService = auditService;
        this.tenantContextResolver = tenantContextResolver;
        this.customerRepository = customerRepository;
        this.nailNoteRepository = nailNoteRepository;
    }

    /**
     * Tenant-scoped customer appointment history, reliability summary, audit log,
     * and digital nail card consultation notes.
     * Accessible via GET /api/admin/customers/{customerId}/history
     */
    @GetMapping("/{customerId}/history")
    public ResponseEntity<?> getCustomerHistory(
            @PathVariable Long customerId,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        Map<String, Object> history = auditService.getCustomerHistory(tenantId, customerId);
        if (history == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Customer not found."));
        }
        return ResponseEntity.ok(history);
    }

    @GetMapping("/by-email/history")
    public ResponseEntity<?> getCustomerHistoryByEmail(
            @RequestParam String email,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        Map<String, Object> history = auditService.getCustomerHistoryByEmail(tenantId, email);
        if (history == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No customer history found for email."));
        }
        return ResponseEntity.ok(history);
    }

    /**
     * Returns all nail consultation session notes and photo records for a customer.
     */
    @GetMapping("/{customerId}/nail-notes")
    public ResponseEntity<?> getCustomerNailNotes(
            @PathVariable Long customerId,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        if (nailNoteRepository == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        List<CustomerNailNote> notes = nailNoteRepository
                .findAllByTenantIdAndCustomerIdOrderByServiceDateDescCreatedAtDesc(tenantId, customerId);
        return ResponseEntity.ok(notes);
    }

    /**
     * Creates a new nail consultation session note & photo record for a customer.
     */
    @PostMapping("/{customerId}/nail-notes")
    public ResponseEntity<?> addCustomerNailNote(
            @PathVariable Long customerId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);

        if (customerRepository != null) {
            Optional<Customer> custOpt = customerRepository.findById(customerId);
            if (custOpt.isEmpty() || (custOpt.get().getTenantId() != null && !custOpt.get().getTenantId().equalsIgnoreCase(tenantId))) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Customer not found."));
            }
        }

        if (nailNoteRepository == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Nail note repository not initialized."));
        }

        CustomerNailNote note = new CustomerNailNote();
        note.setTenantId(tenantId);
        note.setCustomerId(customerId);

        if (payload.get("appointmentId") != null) {
            try {
                note.setAppointmentId(Long.valueOf(payload.get("appointmentId").toString()));
            } catch (Exception ignored) {}
        }

        note.setTechnicianName(payload.get("technicianName") != null ? payload.get("technicianName").toString().trim() : null);

        if (payload.get("serviceDate") != null && !payload.get("serviceDate").toString().isBlank()) {
            try {
                note.setServiceDate(LocalDate.parse(payload.get("serviceDate").toString().trim()));
            } catch (Exception e) {
                note.setServiceDate(LocalDate.now());
            }
        } else {
            note.setServiceDate(LocalDate.now());
        }

        note.setNailShape(payload.get("nailShape") != null ? payload.get("nailShape").toString().trim() : null);
        note.setNailLength(payload.get("nailLength") != null ? payload.get("nailLength").toString().trim() : null);
        note.setNailSystem(payload.get("nailSystem") != null ? payload.get("nailSystem").toString().trim() : null);
        note.setColorDetails(payload.get("colorDetails") != null ? payload.get("colorDetails").toString().trim() : null);
        note.setNotes(payload.get("notes") != null ? payload.get("notes").toString().trim() : null);
        note.setPhotoUrl(payload.get("photoUrl") != null ? payload.get("photoUrl").toString().trim() : null);
        note.setCreatedAt(LocalDateTime.now());

        CustomerNailNote saved = nailNoteRepository.save(note);

        // Optionally update the customer's top-level default preference if specified
        if (customerRepository != null) {
            customerRepository.findById(customerId).ifPresent(c -> {
                boolean changed = false;
                if (saved.getNailShape() != null && !saved.getNailShape().isBlank()) {
                    c.setNailShape(saved.getNailShape());
                    changed = true;
                }
                if (saved.getNailLength() != null && !saved.getNailLength().isBlank()) {
                    c.setNailLength(saved.getNailLength());
                    changed = true;
                }
                if (saved.getNailSystem() != null && !saved.getNailSystem().isBlank()) {
                    c.setNailSystem(saved.getNailSystem());
                    changed = true;
                }
                if (saved.getColorDetails() != null && !saved.getColorDetails().isBlank()) {
                    c.setColorFormula(saved.getColorDetails());
                    changed = true;
                }
                if (changed) customerRepository.save(c);
            });
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Deletes a nail note by ID.
     */
    @DeleteMapping("/{customerId}/nail-notes/{noteId}")
    public ResponseEntity<?> deleteCustomerNailNote(
            @PathVariable Long customerId,
            @PathVariable Long noteId,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        if (nailNoteRepository == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Nail note repository not initialized."));
        }

        Optional<CustomerNailNote> noteOpt = nailNoteRepository.findByIdAndTenantId(noteId, tenantId);
        if (noteOpt.isEmpty() || !noteOpt.get().getCustomerId().equals(customerId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Nail note not found."));
        }

        nailNoteRepository.delete(noteOpt.get());
        return ResponseEntity.ok(Map.of("message", "Nail note deleted successfully.", "deletedId", noteId));
    }

    /**
     * Updates customer nail profile preferences directly.
     */
    @PutMapping("/{customerId}/nail-profile")
    public ResponseEntity<?> updateNailProfile(
            @PathVariable Long customerId,
            @RequestBody Map<String, Object> profileData,
            HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        if (customerRepository == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Customer repository not initialized."));
        }

        Optional<Customer> custOpt = customerRepository.findById(customerId);
        if (custOpt.isEmpty() || (custOpt.get().getTenantId() != null && !custOpt.get().getTenantId().equalsIgnoreCase(tenantId))) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Customer not found."));
        }

        Customer c = custOpt.get();
        if (profileData.containsKey("nailShape")) {
            c.setNailShape(profileData.get("nailShape") != null ? profileData.get("nailShape").toString().trim() : null);
        }
        if (profileData.containsKey("nailLength")) {
            c.setNailLength(profileData.get("nailLength") != null ? profileData.get("nailLength").toString().trim() : null);
        }
        if (profileData.containsKey("nailSystem")) {
            c.setNailSystem(profileData.get("nailSystem") != null ? profileData.get("nailSystem").toString().trim() : null);
        }
        if (profileData.containsKey("favoriteColors")) {
            c.setFavoriteColors(profileData.get("favoriteColors") != null ? profileData.get("favoriteColors").toString().trim() : null);
        }
        if (profileData.containsKey("colorFormula")) {
            c.setColorFormula(profileData.get("colorFormula") != null ? profileData.get("colorFormula").toString().trim() : null);
        }
        if (profileData.containsKey("medicalFlags")) {
            c.setMedicalFlags(profileData.get("medicalFlags") != null ? profileData.get("medicalFlags").toString().trim() : null);
        }
        if (profileData.containsKey("cuticleCareNotes")) {
            c.setCuticleCareNotes(profileData.get("cuticleCareNotes") != null ? profileData.get("cuticleCareNotes").toString().trim() : null);
        }

        Customer saved = customerRepository.save(c);
        return ResponseEntity.ok(saved);
    }
}
