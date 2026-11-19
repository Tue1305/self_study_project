package com.example.demo.repository;

import com.example.demo.entity.CustomerNailNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerNailNoteRepository extends JpaRepository<CustomerNailNote, Long> {

    List<CustomerNailNote> findAllByTenantIdAndCustomerIdOrderByServiceDateDescCreatedAtDesc(String tenantId, Long customerId);

    List<CustomerNailNote> findAllByTenantIdAndAppointmentId(String tenantId, Long appointmentId);

    Optional<CustomerNailNote> findByIdAndTenantId(Long id, String tenantId);

    void deleteAllByCustomerId(Long customerId);
}
