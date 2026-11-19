package com.example.demo.controller;

import com.example.demo.entity.Customer;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/customers")
@CrossOrigin(origins = "*")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private com.example.demo.service.AppointmentAuditService auditService;

    @Autowired
    private com.example.demo.service.TenantContextResolver tenantContextResolver;

    @GetMapping
    public List<Customer> getAllCustomers(jakarta.servlet.http.HttpServletRequest request) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        return customerService.getAllCustomers(tenantId);
    }

    @GetMapping("/lookup")
    public ResponseEntity<?> lookupCustomer(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        Optional<Customer> found = Optional.empty();
        if (email != null && !email.isBlank()) {
            found = customerRepository.findByTenantIdAndEmail(tenantId, email.trim());
        }
        if (found.isEmpty() && phone != null && !phone.isBlank()) {
            String norm = phone.replaceAll("\\D", "");
            if (!norm.isEmpty()) {
                found = customerRepository.findAllByTenantId(tenantId).stream()
                        .filter(c -> c.getPhone() != null && c.getPhone().replaceAll("\\D", "").equals(norm))
                        .findFirst();
            }
        }
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Customer not found."));
        }
        return ResponseEntity.ok(found.get());
    }

    @GetMapping("/{customerId}/history")
    public ResponseEntity<?> getCustomerHistory(
            @PathVariable Long customerId,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        Map<String, Object> history = auditService.getCustomerHistory(tenantId, customerId);
        if (history == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Customer not found."));
        }
        return ResponseEntity.ok(history);
    }

    @PostMapping
    public ResponseEntity<?> createCustomer(
            @RequestBody Customer customer,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        if (customer.getName() == null || customer.getName().isBlank()
                || customer.getEmail() == null || customer.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Name and email are required."));
        }

        String tenantId = tenantContextResolver.resolveTenantId(request);
        customer.setTenantId(tenantId);

        Optional<Customer> existing = customerRepository.findByTenantIdAndEmail(tenantId, customer.getEmail().trim());
        if (existing.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "A customer with this email already exists in this salon."));
        }

        customer.setEmail(customer.getEmail().trim());
        customerService.saveCustomer(customer);
        return ResponseEntity.ok(customer);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCustomer(
            @PathVariable Long id,
            @RequestBody Customer updatedCustomer,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        Customer existing = customerService.getCustomerById(id);
        if (existing == null || (existing.getTenantId() != null && !existing.getTenantId().equalsIgnoreCase(tenantId))) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Customer not found."));
        }

        if (updatedCustomer.getEmail() != null && !updatedCustomer.getEmail().isBlank()) {
            Optional<Customer> emailOwner = customerRepository.findByTenantIdAndEmail(tenantId, updatedCustomer.getEmail().trim());
            if (emailOwner.isPresent() && !emailOwner.get().getId().equals(id)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email is already used by another customer in this salon."));
            }
            existing.setEmail(updatedCustomer.getEmail().trim());
        }

        if (updatedCustomer.getName() != null && !updatedCustomer.getName().isBlank()) {
            existing.setName(updatedCustomer.getName().trim());
        }
        existing.setPhone(updatedCustomer.getPhone());
        if (updatedCustomer.getMedicalFlags() != null) {
            existing.setMedicalFlags(updatedCustomer.getMedicalFlags().trim());
        }
        if (updatedCustomer.getColorFormula() != null) {
            existing.setColorFormula(updatedCustomer.getColorFormula().trim());
        }
        if (updatedCustomer.getLoyaltyPoints() != null) {
            existing.setLoyaltyPoints(Math.max(0, updatedCustomer.getLoyaltyPoints()));
        }
        if (updatedCustomer.getBirthDate() != null) {
            existing.setBirthDate(updatedCustomer.getBirthDate());
        }
        if (updatedCustomer.getOptOutMarketing() != null) {
            existing.setOptOutMarketing(updatedCustomer.getOptOutMarketing());
        }
        if (updatedCustomer.getNailShape() != null) {
            existing.setNailShape(updatedCustomer.getNailShape().trim());
        }
        if (updatedCustomer.getNailLength() != null) {
            existing.setNailLength(updatedCustomer.getNailLength().trim());
        }
        if (updatedCustomer.getNailSystem() != null) {
            existing.setNailSystem(updatedCustomer.getNailSystem().trim());
        }
        if (updatedCustomer.getFavoriteColors() != null) {
            existing.setFavoriteColors(updatedCustomer.getFavoriteColors().trim());
        }
        if (updatedCustomer.getCuticleCareNotes() != null) {
            existing.setCuticleCareNotes(updatedCustomer.getCuticleCareNotes().trim());
        }

        customerService.saveCustomer(existing);
        return ResponseEntity.ok(existing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCustomer(
            @PathVariable Long id,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        String tenantId = tenantContextResolver.resolveTenantId(request);
        Customer existing = customerService.getCustomerById(id);
        if (existing == null || (existing.getTenantId() != null && !existing.getTenantId().equalsIgnoreCase(tenantId))) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Customer not found."));
        }
        customerService.deleteCustomerById(id);
        return ResponseEntity.ok(Map.of("message", "Customer deleted successfully."));
    }
}
