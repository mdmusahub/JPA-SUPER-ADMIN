package io.github.mdmusahub.jpaadmin.testsupport.listmodel;

import io.github.mdmusahub.jpaadmin.annotation.AdminHidden;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** A relation target that is hidden from the console: a relation to it shows {@code #id}, never its name. */
@Entity
@AdminHidden
public class Vault {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
