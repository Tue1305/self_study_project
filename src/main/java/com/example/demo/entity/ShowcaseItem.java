package com.example.demo.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "showcase_items")
public class ShowcaseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String category; // "gel", "acrylic", "art"

    private String badgeText; // e.g., "Gel-X", "Acrylic", "Custom Art"

    private String techName; // e.g., "@NailArtistry"

    private String duration; // e.g., "1 hr 15 mins"

    @Column(columnDefinition = "TEXT", nullable = false)
    private String imageUrl; // Relative path, external URL, or Base64 data URI

    public ShowcaseItem() {}

    public ShowcaseItem(String title, String description, String category, String badgeText,
                        String techName, String duration, String imageUrl) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.badgeText = badgeText;
        this.techName = techName;
        this.duration = duration;
        this.imageUrl = imageUrl;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getBadgeText() { return badgeText; }
    public void setBadgeText(String badgeText) { this.badgeText = badgeText; }

    public String getTechName() { return techName; }
    public void setTechName(String techName) { this.techName = techName; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
