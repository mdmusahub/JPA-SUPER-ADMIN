package io.github.mdmusahub.jpaadmin.autoconfigure.uploadmodel.good;

import io.github.mdmusahub.jpaadmin.annotation.AdminUpload;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** A neutral entity with one upload field that uses every default (ARCH-12: never a demo name). */
@Entity
public class Portrait {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @AdminUpload
    @Column(length = 255)
    private String photo;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoto() {
        return photo;
    }

    public void setPhoto(String photo) {
        this.photo = photo;
    }
}
