package com.example.demo.repository;

import com.example.demo.entity.ShowcaseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShowcaseRepository extends JpaRepository<ShowcaseItem, Long> {
    List<ShowcaseItem> findAllByOrderByIdAsc();
}
