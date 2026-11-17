package com.example.demo.repository;

import com.example.demo.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    // Tenant-scoped lookups and duplicate checks
    Optional<UserEntity> findByTenantIdAndUsername(String tenantId, String username);

    Optional<UserEntity> findByTenantIdAndUsernameIgnoreCase(String tenantId, String username);

    Optional<UserEntity> findByTenantIdAndEmail(String tenantId, String email);

    Optional<UserEntity> findByTenantIdAndEmailIgnoreCase(String tenantId, String email);

    Boolean existsByTenantIdAndUsername(String tenantId, String username);

    Boolean existsByTenantIdAndUsernameIgnoreCase(String tenantId, String username);

    Boolean existsByTenantIdAndEmail(String tenantId, String email);

    Boolean existsByTenantIdAndEmailIgnoreCase(String tenantId, String email);

    // Legacy / fallback global methods
    Optional<UserEntity> findFirstByUsername(String username);

    Optional<UserEntity> findFirstByUsernameIgnoreCase(String username);

    Optional<UserEntity> findByUsername(String username);

    Boolean existsByUsername(String username); // Required for registration check

    List<UserEntity> findAllByTenantIdAndRole(String tenantId, String role);

    List<UserEntity> findAllByTenantId(String tenantId);

    Optional<UserEntity> findByTenantIdAndId(String tenantId, Long id);

    boolean existsByTenantIdAndId(String tenantId, Long id);
}
