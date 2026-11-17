package com.example.demo.service;

import com.example.demo.entity.Customer;
import com.example.demo.entity.UserEntity;
import com.example.demo.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerService {

    @Autowired
    private CustomerRepository customerRepository;

    public CustomerService() {}

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    public List<Customer> getAllCustomers(String tenantId) {
        if (tenantId == null || tenantId.isBlank() || "all".equalsIgnoreCase(tenantId)) {
            return customerRepository.findAll();
        }
        String cleanTenant = tenantId.trim().toLowerCase();
        List<Customer> tenantCustomers = customerRepository.findByTenantId(cleanTenant);
        if (tenantCustomers.isEmpty() && "default".equalsIgnoreCase(cleanTenant)) {
            return customerRepository.findAll().stream()
                    .filter(c -> c.getTenantId() == null || "default".equalsIgnoreCase(c.getTenantId()))
                    .toList();
        }
        return tenantCustomers;
    }

    public void saveCustomer(Customer customer) {
        customerRepository.save(customer);
    }

    public Customer getCustomerById(Long id) {
        return customerRepository.findById(id).orElse(null);
    }

    public void deleteCustomerById(Long id) {
        customerRepository.deleteById(id);
    }

    /**
     * Syncs a UserEntity with ROLE_USER into the Customer table.
     * Skips admin and nail technician accounts.
     */
    public void syncUserToCustomer(UserEntity user) {
        if (user == null || user.getUsername() == null || user.getUsername().isBlank()) {
            return;
        }

        String role = user.getRole() == null ? "ROLE_USER" : user.getRole().trim().toUpperCase();
        if (!role.equals("ROLE_USER") && !role.equals("USER")) {
            return;
        }

        String username = user.getUsername().trim();
        String displayName = (user.getName() != null && !user.getName().isBlank())
                ? user.getName().trim()
                : username;
        String email = (user.getEmail() != null && !user.getEmail().isBlank())
                ? user.getEmail().trim()
                : username;
        String phone = user.getPhoneNumber();
        String tenantId = (user.getTenantId() != null && !user.getTenantId().isBlank())
                ? user.getTenantId().trim().toLowerCase()
                : "default";

        try {
            Customer customer = customerRepository.findByTenantIdAndEmail(tenantId, email)
                    .or(() -> customerRepository.findByTenantIdAndEmail(tenantId, username))
                    .or(() -> customerRepository.findByTenantIdAndName(tenantId, displayName))
                    .orElseGet(Customer::new);

            customer.setName(displayName);
            customer.setEmail(email);
            customer.setTenantId(tenantId);
            if (phone != null && !phone.isBlank()) {
                customer.setPhone(phone.trim());
            }

            customerRepository.save(customer);
        } catch (Exception ignored) {
            // Keep auth/profile flows resilient even if a unique constraint edge case occurs
        }
    }
}
