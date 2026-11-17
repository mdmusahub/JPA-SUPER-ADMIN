package io.github.mdmusahub.jpaadmin.testsupport.listmodel;

import io.github.mdmusahub.jpaadmin.annotation.AdminResource;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** An entity that can be looked at but not deleted (SEC-11): {@code deletable = false}. */
@Entity
@AdminResource(deletable = false)
public class Anchor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
