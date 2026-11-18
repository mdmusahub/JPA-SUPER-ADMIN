package io.github.mdmusahub.jpaadmin.testsupport.uploadmodel;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * A neutral entity whose console address would be {@code uploads}, the address the console keeps for its upload endpoint
 * (Phase 9a). The scanner must report it as unsupported instead of letting its create form post to the upload endpoint.
 */
@Entity
public class Uploads {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String note;

    public Long getId() {
        return id;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
