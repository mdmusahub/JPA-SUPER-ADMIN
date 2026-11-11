package io.github.mdmusahub.jpaadmin.metadata.testmodel;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;

/** A composite primary key through @EmbeddedId: unsupported in v0.1. */
@Entity
public class Contraption {

    @EmbeddedId
    private ContraptionKey key;

    private String name;

    public ContraptionKey getKey() { return key; }
    public void setKey(ContraptionKey key) { this.key = key; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
