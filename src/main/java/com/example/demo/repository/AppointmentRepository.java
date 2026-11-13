package com.example.demo.repository;

import com.example.demo.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByAppointmentTimeBetween(LocalDateTime start, LocalDateTime end);

    List<Appointment> findAllByTenantId(String tenantId);

    Optional<Appointment> findByTenantIdAndId(String tenantId, Long id);

    Optional<Appointment> findByCancellationToken(String cancellationToken);

    Optional<Appointment> findByTenantIdAndCancellationToken(String tenantId, String cancellationToken);

    List<Appointment> findByTenantIdAndAppointmentTimeBetween(String tenantId, LocalDateTime start, LocalDateTime end);

    boolean existsByTenantIdAndNailTechIdAndAppointmentTimeAndStatusNot(
            String tenantId,
            Long nailTechId,
            LocalDateTime appointmentTime,
            Appointment.AppointmentStatus status
    );
}
