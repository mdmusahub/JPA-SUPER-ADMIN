package io.github.mdmusahub.jpaadmin.testsupport.listmodel;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigInteger;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

/**
 * An entity made to prove the filters on every number and date type, and the cap: twelve filterable fields, one for
 * each type the console accepts for a range (whole numbers of four sizes, a decimal type of two sizes, an integer of any
 * size, and every date and date-time type), plus a text field.
 */
@Entity
public class Ledger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String label;
    private Long amount;
    private Short level;
    private Double ratio;
    private Float tilt;
    private BigInteger big;
    private Instant seenAt;
    private OffsetDateTime openedAt;
    private ZonedDateTime closedAt;
    private java.util.Date oldDate;
    private java.sql.Timestamp stamped;
    private java.sql.Date plainDate;

    public Long getId() { return id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
    public Short getLevel() { return level; }
    public void setLevel(Short level) { this.level = level; }
    public Double getRatio() { return ratio; }
    public void setRatio(Double ratio) { this.ratio = ratio; }
    public Float getTilt() { return tilt; }
    public void setTilt(Float tilt) { this.tilt = tilt; }
    public BigInteger getBig() { return big; }
    public void setBig(BigInteger big) { this.big = big; }
    public Instant getSeenAt() { return seenAt; }
    public void setSeenAt(Instant seenAt) { this.seenAt = seenAt; }
    public OffsetDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(OffsetDateTime openedAt) { this.openedAt = openedAt; }
    public ZonedDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(ZonedDateTime closedAt) { this.closedAt = closedAt; }
    public java.util.Date getOldDate() { return oldDate; }
    public void setOldDate(java.util.Date oldDate) { this.oldDate = oldDate; }
    public java.sql.Timestamp getStamped() { return stamped; }
    public void setStamped(java.sql.Timestamp stamped) { this.stamped = stamped; }
    public java.sql.Date getPlainDate() { return plainDate; }
    public void setPlainDate(java.sql.Date plainDate) { this.plainDate = plainDate; }
}
