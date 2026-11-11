package io.github.mdmusahub.jpaadmin.testsupport.listmodel;

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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The entity most list tests read. It has a field of every kind the list shows, three relations with three different
 * labels, a hidden target, a collection, a sensitive field by name ({@code accessToken}), a sensitive field by
 * annotation ({@code memo}), a hidden field, binary data and an embedded value.
 */
@Entity
public class Parcel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String reference;

    @Column(length = 4000)
    private String notes;

    private BigDecimal weight;

    private int pieces;

    private boolean fragile;

    @Enumerated(EnumType.STRING)
    private ParcelStatus status;

    private LocalDate shippedOn;

    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private Depot depot;

    @ManyToOne(fetch = FetchType.LAZY)
    private Courier courier;

    @ManyToOne(fetch = FetchType.LAZY)
    private Crate crate;

    @ManyToOne(fetch = FetchType.LAZY)
    private Vault vault;

    @OneToMany(mappedBy = "parcel")
    private List<ParcelItem> items = new ArrayList<>();

    /** Masked by the default {@code sensitive-fields}: the name contains "token". */
    private String accessToken;

    /** Masked because of the annotation only. */
    @AdminSensitive
    private String memo;

    @AdminHidden
    private String internalCode;

    @Lob
    private byte[] picture;

    @Embedded
    private Dimensions dimensions;

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public int getPieces() { return pieces; }
    public void setPieces(int pieces) { this.pieces = pieces; }
    public boolean isFragile() { return fragile; }
    public void setFragile(boolean fragile) { this.fragile = fragile; }
    public ParcelStatus getStatus() { return status; }
    public void setStatus(ParcelStatus status) { this.status = status; }
    public LocalDate getShippedOn() { return shippedOn; }
    public void setShippedOn(LocalDate shippedOn) { this.shippedOn = shippedOn; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Depot getDepot() { return depot; }
    public void setDepot(Depot depot) { this.depot = depot; }
    public Courier getCourier() { return courier; }
    public void setCourier(Courier courier) { this.courier = courier; }
    public Crate getCrate() { return crate; }
    public void setCrate(Crate crate) { this.crate = crate; }
    public Vault getVault() { return vault; }
    public void setVault(Vault vault) { this.vault = vault; }
    public List<ParcelItem> getItems() { return items; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public String getInternalCode() { return internalCode; }
    public void setInternalCode(String internalCode) { this.internalCode = internalCode; }
    public byte[] getPicture() { return picture; }
    public void setPicture(byte[] picture) { this.picture = picture; }
    public Dimensions getDimensions() { return dimensions; }
    public void setDimensions(Dimensions dimensions) { this.dimensions = dimensions; }
}
