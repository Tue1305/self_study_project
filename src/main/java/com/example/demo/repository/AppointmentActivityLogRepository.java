package com.example.demo.repository;

import com.example.demo.entity.AppointmentActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppointmentActivityLogRepository extends JpaRepository<AppointmentActivityLog, Long> {

    List<AppointmentActivityLog> findByTenantIdAndCustomerIdOrderByCreatedAtDesc(String tenantId, Long customerId);

    List<AppointmentActivityLog> findByTenantIdAndAppointmentIdOrderByCreatedAtDesc(String tenantId, Long appointmentId);

    List<AppointmentActivityLog> findByTenantIdAndCustomerEmailIgnoreCaseOrderByCreatedAtDesc(String tenantId, String customerEmail);

    List<AppointmentActivityLog> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
