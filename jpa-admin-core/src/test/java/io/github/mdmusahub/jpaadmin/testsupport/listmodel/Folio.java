package io.github.mdmusahub.jpaadmin.testsupport.listmodel;

import io.github.mdmusahub.jpaadmin.annotation.AdminHidden;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A neutral entity for the form tests (create, edit, stale version): a {@code @Version}, a required text of a known length,
 * a unique code, a primitive number, a required and an optional relation, a field named like a secret, and a hidden field.
 * A setter of the secret adds a prefix, so a test can see that the host's own setter runs (the console stores what it is
 * given, and the host's code decides what that means).
 */
@Entity
public class Folio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long revision;

    @Column(nullable = false, length = 30)
    private String title;

    @Column(unique = true, length = 20)
    private String code;

    private int quantity;

    private boolean urgent;

    @Enumerated(EnumType.STRING)
    private ParcelStatus status;

    private LocalDate due;

    private LocalDateTime openedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Depot home;

    @ManyToOne(fetch = FetchType.LAZY)
    private Courier courier;

    /** Sensitive by its name ("secret"). */
    @Column(length = 60)
    private String secretNote;

    @AdminHidden
    private String internalNote;

    /** A test hook: when set, a save of a Folio throws what it returns (a stand-in for a Bean Validation failure). */
    public static volatile java.util.function.Function<Folio, RuntimeException> failure;

    @PrePersist
    @PreUpdate
    void beforeSave() {
        java.util.function.Function<Folio, RuntimeException> hook = failure;
        RuntimeException refused = hook == null ? null : hook.apply(this);
        if (refused != null) {
            throw refused;
        }
    }

    public Long getId() { return id; }
    public long getRevision() { return revision; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public boolean isUrgent() { return urgent; }
    public void setUrgent(boolean urgent) { this.urgent = urgent; }
    public ParcelStatus getStatus() { return status; }
    public void setStatus(ParcelStatus status) { this.status = status; }
    public LocalDate getDue() { return due; }
    public void setDue(LocalDate due) { this.due = due; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime openedAt) { this.openedAt = openedAt; }
    public Depot getHome() { return home; }
    public void setHome(Depot home) { this.home = home; }
    public Courier getCourier() { return courier; }
    public void setCourier(Courier courier) { this.courier = courier; }
    public String getSecretNote() { return secretNote; }
    /** The host's own rule: what is stored carries a prefix. */
    public void setSecretNote(String secretNote) { this.secretNote = secretNote == null ? null : "stored:" + secretNote; }
    public String getInternalNote() { return internalNote; }
    public void setInternalNote(String internalNote) { this.internalNote = internalNote; }
}
