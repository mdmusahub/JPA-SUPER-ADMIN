package io.github.mdmusahub.jpaadmin.autoconfigure.tonemodel.good;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** The related entity of {@link Valve}. */
@Entity
public class Pipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tag;

    public Long getId() { return id; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
}
