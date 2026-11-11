package com.example.demo.repository;

import com.example.demo.entity.TenantConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantConfigRepository extends JpaRepository<TenantConfig, Long> {

    Optional<TenantConfig> findByTenantKeyIgnoreCase(String tenantKey);

    Optional<TenantConfig> findByCustomDomainIgnoreCase(String customDomain);

    Optional<TenantConfig> findFirstByDefaultTenantTrue();
}
