package io.github.mdmusahub.jpaadmin.testsupport.tonemodel;

import io.github.mdmusahub.jpaadmin.annotation.AdminBadge;
import io.github.mdmusahub.jpaadmin.annotation.AdminLookup;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/**
 * The entity of the Phase 13 tests. {@code state} and {@code grade} carry badges; {@code phase} is an enum with none (it
 * keeps the word list); {@code dial} has a good lookup, {@code spare} a lookup that names a sensitive property, {@code ghost}
 * one that names nothing, and {@code plain} none. The last two lookups are wrong on purpose: the startup check must report
 * them and the services must not use them.
 */
@Entity
public class Gauge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @AdminBadge({"OK=green", "LATE=Red"})
    private GaugeState state;

    @Enumerated(EnumType.STRING)
    private GaugeState phase;

    @AdminBadge({"A=green", "B = amber", "C=red"})
    private String grade;

    private String label;

    @ManyToOne(fetch = FetchType.LAZY)
    @AdminLookup("serial")
    private Dial dial;

    @ManyToOne(fetch = FetchType.LAZY)
    @AdminLookup("secretNote")
    private Dial spare;

    @ManyToOne(fetch = FetchType.LAZY)
    @AdminLookup("nope")
    private Dial ghost;

    @ManyToOne(fetch = FetchType.LAZY)
    private Dial plain;

    public Long getId() { return id; }
    public GaugeState getState() { return state; }
    public void setState(GaugeState state) { this.state = state; }
    public GaugeState getPhase() { return phase; }
    public void setPhase(GaugeState phase) { this.phase = phase; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Dial getDial() { return dial; }
    public void setDial(Dial dial) { this.dial = dial; }
    public Dial getSpare() { return spare; }
    public void setSpare(Dial spare) { this.spare = spare; }
    public Dial getGhost() { return ghost; }
    public void setGhost(Dial ghost) { this.ghost = ghost; }
    public Dial getPlain() { return plain; }
    public void setPlain(Dial plain) { this.plain = plain; }
}
