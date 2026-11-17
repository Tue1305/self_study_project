package com.example.demo.repository;

import com.example.demo.entity.ShowcaseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShowcaseRepository extends JpaRepository<ShowcaseItem, Long> {
    List<ShowcaseItem> findAllByTenantIdOrderByIdAsc(String tenantId);

    Optional<ShowcaseItem> findByTenantIdAndId(String tenantId, Long id);

    boolean existsByTenantIdAndId(String tenantId, Long id);

    void deleteByTenantIdAndId(String tenantId, Long id);

    List<ShowcaseItem> findAllByOrderByIdAsc();
}
