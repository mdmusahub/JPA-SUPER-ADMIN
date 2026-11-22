package io.github.mdmusahub.jpaadmin.testsupport.perfmodel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The big table of the performance measurements (Phase 14a, D-131): 200,000 rows, three text columns, three to-one relations (so a
 * list can show none, one, two or three joins) and a creation moment (so the chart can be measured). Neutral names (ARCH-12).
 * The measurements load it with JDBC batches from {@code AbstractPerfMeasurement}; the console reads it with its own services.
 */
@Entity
public class PerfItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 120)
    private String title;

    @Column(length = 400)
    private String details;

    @Column(length = 24)
    private String code;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private PerfStatus status;

    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private PerfOwner owner;

    @ManyToOne(fetch = FetchType.LAZY)
    private PerfGroup team;

    @ManyToOne(fetch = FetchType.LAZY)
    private PerfPlace place;

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDetails() { return details; }
    public String getCode() { return code; }
    public BigDecimal getAmount() { return amount; }
    public PerfStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public PerfOwner getOwner() { return owner; }
    public PerfGroup getTeam() { return team; }
    public PerfPlace getPlace() { return place; }
}
