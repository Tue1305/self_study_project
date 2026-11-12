package com.example.demo.repository;

import com.example.demo.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);

    Boolean existsByUsername(String username); // Required for registration check

    List<UserEntity> findAllByTenantIdAndRole(String tenantId, String role);

    List<UserEntity> findAllByTenantId(String tenantId);

    Optional<UserEntity> findByTenantIdAndId(String tenantId, Long id);

    boolean existsByTenantIdAndId(String tenantId, Long id);
}
