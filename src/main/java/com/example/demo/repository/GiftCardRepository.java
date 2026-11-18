package com.example.demo.repository;

import com.example.demo.entity.GiftCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GiftCardRepository extends JpaRepository<GiftCard, Long> {

    Optional<GiftCard> findByTenantIdAndId(String tenantId, Long id);

    Optional<GiftCard> findByTenantIdAndCodeIgnoreCase(String tenantId, String code);

    List<GiftCard> findAllByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<GiftCard> findAllByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, String status);

    boolean existsByTenantIdAndCodeIgnoreCase(String tenantId, String code);
}
