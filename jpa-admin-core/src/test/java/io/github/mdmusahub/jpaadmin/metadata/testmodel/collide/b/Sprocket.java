package io.github.mdmusahub.jpaadmin.metadata.testmodel.collide.b;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The other of two classes with the simple name Sprocket, in two packages. */
@Entity(name = "SprocketB")
@Table(name = "sprocket_b")
public class Sprocket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
