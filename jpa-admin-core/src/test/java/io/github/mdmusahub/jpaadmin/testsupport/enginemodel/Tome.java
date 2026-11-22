package io.github.mdmusahub.jpaadmin.testsupport.enginemodel;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

/**
 * An entity with one {@code @Lob} text: the "large text" column of D-60, alone on its entity so that a failure belongs to that
 * column and to no other. Whether {@code lower(...)}, {@code like} and {@code order by} work on it is database specific.
 */
@Entity
public class Tome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Lob
    private String body;

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
