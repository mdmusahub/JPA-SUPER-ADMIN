package io.github.mdmusahub.jpaadmin.testsupport.perfmodel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** The third small table a {@link PerfItem} points at (2,000 rows, labelled by {@code name}). */
@Entity
public class PerfPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 60)
    private String name;

    public Long getId() { return id; }
    public String getName() { return name; }
}
