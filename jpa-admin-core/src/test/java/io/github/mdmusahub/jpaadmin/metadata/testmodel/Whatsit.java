package io.github.mdmusahub.jpaadmin.metadata.testmodel;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;

/** A composite primary key through @IdClass: unsupported in v0.1. */
@Entity
@IdClass(WhatsitKey.class)
public class Whatsit {

    @Id
    private String site;

    @Id
    private String code;

    private String name;

    public String getSite() { return site; }
    public void setSite(String site) { this.site = site; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
