package io.github.mdmusahub.jpaadmin.testsupport.listmodel;

import io.github.mdmusahub.jpaadmin.annotation.AdminResource;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** A read-only entity: the console shows it and never deletes from it (SEC-11). */
@Entity
@AdminResource(readOnly = true)
public class Archive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
