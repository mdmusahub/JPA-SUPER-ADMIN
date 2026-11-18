package io.github.mdmusahub.jpaadmin.sample.domain;

import io.github.mdmusahub.jpaadmin.annotation.AdminUpload;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Entity
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 80)
    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Size(max = 255)
    private String description;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * The one upload field of the sample (Phase 9a): the address of a picture of the category. Optional, and empty in every
     * seed row. Images only, at most 2 MB, stored under {@code categories/} in the default storage.
     */
    @AdminUpload(folder = "categories", accept = {"image/png", "image/jpeg", "image/gif", "image/webp"}, maxSize = "2MB")
    @Size(max = 255)
    private String image;

    protected Category() {
    }

    public Category(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
}
