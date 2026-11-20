package io.github.mdmusahub.jpaadmin.testsupport.tonemodel;

import io.github.mdmusahub.jpaadmin.annotation.AdminSensitive;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * A relation target with three candidates for its label: {@code name} (what the console picks by itself), {@code serial}
 * (what a lookup can name) and {@code secretNote} (sensitive: it must never be a label, whatever a lookup says).
 */
@Entity
public class Dial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String serial;

    @AdminSensitive
    private String secretNote;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSerial() { return serial; }
    public void setSerial(String serial) { this.serial = serial; }
    public String getSecretNote() { return secretNote; }
    public void setSecretNote(String secretNote) { this.secretNote = secretNote; }
}
