package io.github.mdmusahub.jpaadmin.metadata.testmodel;

import io.github.mdmusahub.jpaadmin.annotation.AdminHidden;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** Hidden by the annotation: the console must not know it at all. */
@Entity
@AdminHidden
public class Doohickey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
