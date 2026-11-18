package io.github.mdmusahub.jpaadmin.testsupport.uploadmodel;

import io.github.mdmusahub.jpaadmin.annotation.AdminUpload;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * A neutral entity for the upload tests: an upload field with every default, one that names a storage, a folder, its own
 * types and its own limit, one marked on its getter, one that is not a String (a mistake the startup check reports), and
 * an ordinary text field.
 */
@Entity
public class Dossier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 40)
    private String title;

    @AdminUpload
    @Column(length = 255)
    private String cover;

    @AdminUpload(storage = "vault", folder = "docs/scans", accept = {"Application/PDF", " image/png "}, maxSize = "2MB")
    @Column(length = 255)
    private String contract;

    @Column(length = 255)
    private String scan;

    @AdminUpload
    private Integer wrongType;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    /** Marked on the getter, not on the field. */
    @AdminUpload(folder = "scans")
    public String getScan() {
        return scan;
    }

    public void setScan(String scan) {
        this.scan = scan;
    }

    public Integer getWrongType() {
        return wrongType;
    }

    public void setWrongType(Integer wrongType) {
        this.wrongType = wrongType;
    }
}
