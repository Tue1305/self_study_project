package com.example.demo.repository;

import com.example.demo.entity.EodRegisterClose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EodRegisterCloseRepository extends JpaRepository<EodRegisterClose, Long> {

    Optional<EodRegisterClose> findByTenantIdAndClosingDate(String tenantId, LocalDate closingDate);

    List<EodRegisterClose> findAllByTenantIdOrderByClosingDateDesc(String tenantId);

    boolean existsByTenantIdAndClosingDate(String tenantId, LocalDate closingDate);
}
