package com.example.demo.controller;

import com.example.demo.entity.ShowcaseItem;
import com.example.demo.repository.ShowcaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/showcase")
@CrossOrigin(origins = "*")
public class ShowcaseController {

    @Autowired
    private ShowcaseRepository showcaseRepository;

    @GetMapping
    public List<ShowcaseItem> getAllShowcaseItems() {
        return showcaseRepository.findAllByOrderByIdAsc();
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    @PostMapping
    public ResponseEntity<?> createShowcaseItem(@RequestBody ShowcaseItem item) {
        if (item.getTitle() == null || item.getTitle().isBlank()
                || item.getImageUrl() == null || item.getImageUrl().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Title and picture are required."));
        }
        if (item.getCategory() == null || item.getCategory().isBlank()) {
            item.setCategory("art");
        }
        if (item.getBadgeText() == null || item.getBadgeText().isBlank()) {
            item.setBadgeText("Custom Art");
        }
        if (item.getTechName() == null || item.getTechName().isBlank()) {
            item.setTechName("@NailArtistry");
        }
        if (item.getDuration() == null || item.getDuration().isBlank()) {
            item.setDuration("1 hr 30 mins");
        }

        ShowcaseItem saved = showcaseRepository.save(item);
        return ResponseEntity.ok(saved);
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateShowcaseItem(@PathVariable Long id, @RequestBody ShowcaseItem updated) {
        return showcaseRepository.findById(id)
                .map(existing -> {
                    if (updated.getTitle() != null && !updated.getTitle().isBlank()) {
                        existing.setTitle(updated.getTitle().trim());
                    }
                    existing.setDescription(updated.getDescription());
                    if (updated.getCategory() != null && !updated.getCategory().isBlank()) {
                        existing.setCategory(updated.getCategory().trim());
                    }
                    if (updated.getBadgeText() != null && !updated.getBadgeText().isBlank()) {
                        existing.setBadgeText(updated.getBadgeText().trim());
                    }
                    if (updated.getTechName() != null && !updated.getTechName().isBlank()) {
                        existing.setTechName(updated.getTechName().trim());
                    }
                    if (updated.getDuration() != null && !updated.getDuration().isBlank()) {
                        existing.setDuration(updated.getDuration().trim());
                    }
                    if (updated.getImageUrl() != null && !updated.getImageUrl().isBlank()) {
                        existing.setImageUrl(updated.getImageUrl());
                    }
                    ShowcaseItem saved = showcaseRepository.save(existing);
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Showcase item not found.")));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteShowcaseItem(@PathVariable Long id) {
        if (!showcaseRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Showcase item not found."));
        }
        showcaseRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Showcase item deleted successfully."));
    }
}
