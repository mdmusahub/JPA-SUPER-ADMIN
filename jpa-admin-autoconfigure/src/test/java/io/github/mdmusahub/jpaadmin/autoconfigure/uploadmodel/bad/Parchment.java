package io.github.mdmusahub.jpaadmin.autoconfigure.uploadmodel.bad;

import io.github.mdmusahub.jpaadmin.annotation.AdminUpload;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * A neutral entity whose upload fields are declared wrongly in two ways, so the startup check has several offenders to list: a
 * storage that is not configured, and a field that is not a String. The first field is fine.
 */
@Entity
public class Parchment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @AdminUpload
    @Column(length = 255)
    private String cover;

    @AdminUpload(storage = "vault")
    @Column(length = 255)
    private String contract;

    @AdminUpload
    private Integer broken;

    public Long getId() {
        return id;
    }

    public String getCover() {
        return cover;
    }

    public void setCover(String cover) {
        this.cover = cover;
    }

    public String getContract() {
        return contract;
    }

    public void setContract(String contract) {
        this.contract = contract;
    }

    public Integer getBroken() {
        return broken;
    }

    public void setBroken(Integer broken) {
        this.broken = broken;
    }
}
