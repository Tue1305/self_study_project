package com.example.demo.repository;

import com.example.demo.entity.StaffDayOff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StaffDayOffRepository extends JpaRepository<StaffDayOff, Long> {
    List<StaffDayOff> findAllByTenantId(String tenantId);
    List<StaffDayOff> findAllByTenantIdAndOffDate(String tenantId, LocalDate offDate);
    List<StaffDayOff> findAllByTenantIdAndStaffId(String tenantId, Long staffId);
    Optional<StaffDayOff> findByTenantIdAndStaffIdAndOffDate(String tenantId, Long staffId, LocalDate offDate);
    boolean existsByTenantIdAndStaffIdAndOffDate(String tenantId, Long staffId, LocalDate offDate);
    void deleteByTenantIdAndStaffIdAndOffDate(String tenantId, Long staffId, LocalDate offDate);
}
