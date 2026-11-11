package io.github.mdmusahub.jpaadmin.metadata.testmodel;

import io.github.mdmusahub.jpaadmin.annotation.AdminHidden;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** Its identifier is annotated @AdminHidden, which the console must refuse to honour: links need the id. */
@Entity
public class Gizmo {

    @Id
    @AdminHidden
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
