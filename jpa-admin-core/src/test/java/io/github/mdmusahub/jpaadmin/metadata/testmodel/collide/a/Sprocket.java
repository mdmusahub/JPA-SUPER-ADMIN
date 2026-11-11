package io.github.mdmusahub.jpaadmin.metadata.testmodel.collide.a;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One of two classes with the simple name Sprocket, in two packages. */
@Entity(name = "SprocketA")
@Table(name = "sprocket_a")
public class Sprocket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
