package com.example.demo.repository;

import com.example.demo.entity.ServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceRepository extends JpaRepository<ServiceEntity, Long> {
    List<ServiceEntity> findAllByTenantId(String tenantId);

    Optional<ServiceEntity> findByTenantIdAndId(String tenantId, Long id);

    long countByTenantId(String tenantId);

    boolean existsByTenantIdAndId(String tenantId, Long id);
}
