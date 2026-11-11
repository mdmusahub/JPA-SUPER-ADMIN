package io.github.mdmusahub.jpaadmin.metadata.testmodel;

import io.github.mdmusahub.jpaadmin.annotation.AdminHidden;
import io.github.mdmusahub.jpaadmin.annotation.AdminSensitive;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One entity with a field of every kind the scanner has to recognise, plus the sensitive and hidden cases.
 *
 * <p>The field names are the ones the rules talk about: {@code userPassword} and {@code api_key} must be masked by
 * the default configuration, {@code passport} must not (it only looks like {@code password}), and
 * {@code recoveryAnswer} is masked only because of {@code @AdminSensitive}.
 */
@Entity
@Table(name = "widget")
public class Widget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long revision;

    @Column(nullable = false)
    private String name;

    private String description;

    private BigDecimal price;

    private int quantity;

    private boolean active;

    @Enumerated(EnumType.STRING)
    private WidgetStatus status;

    private LocalDate releasedOn;

    private LocalDateTime createdAt;

    @Lob
    private byte[] drawing;

    @Embedded
    private Address address;

    @ManyToOne(fetch = FetchType.LAZY)
    private Gadget gadget;

    @OneToMany(mappedBy = "widget")
    private List<WidgetPart> parts = new ArrayList<>();

    /** Masked by the default {@code sensitive-fields}, although the name is not exactly "password". */
    private String userPassword;

    /** Masked by the default list as well: the match ignores the underscore. */
    private String api_key;

    /** Must NOT be masked: it only looks like "password". */
    private String passport;

    /** Masked only because of the annotation; nothing in the name says "secret". */
    @AdminSensitive
    private String recoveryAnswer;

    /** Removed from the console completely. */
    @AdminHidden
    private String internalNote;

    public Long getId() { return id; }
    public Long getRevision() { return revision; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public WidgetStatus getStatus() { return status; }
    public void setStatus(WidgetStatus status) { this.status = status; }
    public LocalDate getReleasedOn() { return releasedOn; }
    public void setReleasedOn(LocalDate releasedOn) { this.releasedOn = releasedOn; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public byte[] getDrawing() { return drawing; }
    public void setDrawing(byte[] drawing) { this.drawing = drawing; }
    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }
    public Gadget getGadget() { return gadget; }
    public void setGadget(Gadget gadget) { this.gadget = gadget; }
    public List<WidgetPart> getParts() { return parts; }
    public String getUserPassword() { return userPassword; }
    public void setUserPassword(String userPassword) { this.userPassword = userPassword; }
    public String getApi_key() { return api_key; }
    public void setApi_key(String api_key) { this.api_key = api_key; }
    public String getPassport() { return passport; }
    public void setPassport(String passport) { this.passport = passport; }
    public String getRecoveryAnswer() { return recoveryAnswer; }
    public void setRecoveryAnswer(String recoveryAnswer) { this.recoveryAnswer = recoveryAnswer; }
    public String getInternalNote() { return internalNote; }
    public void setInternalNote(String internalNote) { this.internalNote = internalNote; }
}
