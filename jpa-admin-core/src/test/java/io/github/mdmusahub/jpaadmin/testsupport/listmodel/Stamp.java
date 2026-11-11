package io.github.mdmusahub.jpaadmin.testsupport.listmodel;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** An entity with a text identifier. */
@Entity
public class Stamp {

    @Id
    private String code;

    private String caption;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
}
