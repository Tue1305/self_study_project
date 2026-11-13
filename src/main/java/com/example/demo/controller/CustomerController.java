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
    public List<Customer> getAllCustomers() {
        return customerService.getAllCustomers();
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
    public ResponseEntity<?> createCustomer(@RequestBody Customer customer) {
        if (customer.getName() == null || customer.getName().isBlank()
                || customer.getEmail() == null || customer.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Name and email are required."));
        }

        Optional<Customer> existing = customerRepository.findByEmail(customer.getEmail().trim());
        if (existing.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "A customer with this email already exists."));
        }

        customer.setEmail(customer.getEmail().trim());
        customerService.saveCustomer(customer);
        return ResponseEntity.ok(customer);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCustomer(@PathVariable Long id, @RequestBody Customer updatedCustomer) {
        Customer existing = customerService.getCustomerById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Customer not found."));
        }

        if (updatedCustomer.getEmail() != null && !updatedCustomer.getEmail().isBlank()) {
            Optional<Customer> emailOwner = customerRepository.findByEmail(updatedCustomer.getEmail().trim());
            if (emailOwner.isPresent() && !emailOwner.get().getId().equals(id)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email is already used by another customer."));
            }
            existing.setEmail(updatedCustomer.getEmail().trim());
        }

        if (updatedCustomer.getName() != null && !updatedCustomer.getName().isBlank()) {
            existing.setName(updatedCustomer.getName().trim());
        }
        existing.setPhone(updatedCustomer.getPhone());

        customerService.saveCustomer(existing);
        return ResponseEntity.ok(existing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCustomer(@PathVariable Long id) {
        if (customerService.getCustomerById(id) == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Customer not found."));
        }
        customerService.deleteCustomerById(id);
        return ResponseEntity.ok(Map.of("message", "Customer deleted successfully."));
    }
}
