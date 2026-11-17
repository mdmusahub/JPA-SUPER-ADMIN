package io.github.mdmusahub.jpaadmin.testsupport.listmodel;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** An entity with an {@code Integer} identifier that the application assigns. */
@Entity
public class Counter {

    @Id
    private Integer id;

    private String name;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
