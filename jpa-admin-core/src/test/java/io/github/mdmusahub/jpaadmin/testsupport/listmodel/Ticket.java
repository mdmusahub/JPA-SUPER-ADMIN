package io.github.mdmusahub.jpaadmin.testsupport.listmodel;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

/** An entity with a UUID identifier. */
@Entity
public class Ticket {

    @Id
    private UUID id;

    @jakarta.persistence.Column(length = 20000)
    private String subject;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
}
