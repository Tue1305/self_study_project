package com.example.demo.repository;

import com.example.demo.entity.AppointmentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentTransactionRepository extends JpaRepository<AppointmentTransaction, Long> {

    List<AppointmentTransaction> findAllByTenantIdOrderByCreatedAtDesc(String tenantId);

    Optional<AppointmentTransaction> findByTenantIdAndAppointmentId(String tenantId, Long appointmentId);

    List<AppointmentTransaction> findAllByTenantIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            String tenantId, LocalDateTime start, LocalDateTime end);

    List<AppointmentTransaction> findAllByTenantIdAndNailTechIdOrderByCreatedAtDesc(
            String tenantId, Long nailTechId);

    List<AppointmentTransaction> findAllByTenantIdAndCustomerIdOrderByCreatedAtDesc(
            String tenantId, Long customerId);

    List<AppointmentTransaction> findAllByTenantIdAndGiftCardCodeOrderByCreatedAtDesc(
            String tenantId, String giftCardCode);
}
