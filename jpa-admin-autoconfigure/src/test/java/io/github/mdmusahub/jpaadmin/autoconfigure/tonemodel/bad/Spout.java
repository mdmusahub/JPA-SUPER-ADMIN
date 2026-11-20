package io.github.mdmusahub.jpaadmin.autoconfigure.tonemodel.bad;

import io.github.mdmusahub.jpaadmin.annotation.AdminSensitive;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** The related entity of {@link Faucet}: it has a name and a sensitive note. */
@Entity
public class Spout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @AdminSensitive
    private String secretNote;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSecretNote() { return secretNote; }
    public void setSecretNote(String secretNote) { this.secretNote = secretNote; }
}
