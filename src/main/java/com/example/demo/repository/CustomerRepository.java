package com.example.demo.repository;

import com.example.demo.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByEmail(String email);
    Optional<Customer> findByName(String name);

    List<Customer> findByTenantId(String tenantId);
    List<Customer> findByTenantIdOrderByNameAsc(String tenantId);
    Optional<Customer> findByTenantIdAndEmail(String tenantId, String email);
    Optional<Customer> findByTenantIdAndName(String tenantId, String name);
}
