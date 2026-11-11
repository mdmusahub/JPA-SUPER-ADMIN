package io.github.mdmusahub.jpaadmin.sample.domain;

import io.github.mdmusahub.jpaadmin.annotation.AdminHidden;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

/**
 * An entity the console must never show: it is annotated {@link AdminHidden}, so it is not in the sidebar, not on
 * the dashboard, and its address answers exactly like an address that was never an entity.
 *
 * <p>It is here to demonstrate the annotation in the demo application and to let the tests prove the rule end to
 * end. Nothing seeds it.
 */
@Entity
@AdminHidden
public class InternalAuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String action;

    private LocalDateTime happenedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public LocalDateTime getHappenedAt() { return happenedAt; }
    public void setHappenedAt(LocalDateTime happenedAt) { this.happenedAt = happenedAt; }
}
