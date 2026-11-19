package io.github.mdmusahub.jpaadmin.autoconfigure.exportmodel;

import io.github.mdmusahub.jpaadmin.annotation.AdminResource;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** An entity whose records the console never exports ({@code @AdminResource(exportable = false)}). */
@Entity
@AdminResource(exportable = false)
public class Withheld {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
