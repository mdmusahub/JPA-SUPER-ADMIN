package io.github.mdmusahub.jpaadmin.autoconfigure.tonemodel.good;

import io.github.mdmusahub.jpaadmin.annotation.AdminBadge;
import io.github.mdmusahub.jpaadmin.annotation.AdminLookup;
import io.github.mdmusahub.jpaadmin.annotation.AdminResource;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/** An entity that uses all three Phase 13 options correctly. */
@Entity
@AdminResource(plural = "Valves (all kinds)")
public class Valve {

    public enum Flow { OPEN, SHUT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @AdminBadge({"OPEN=green", "SHUT=Grey"})
    private Flow flow;

    @AdminBadge({"fine=green"})
    private String grade;

    @ManyToOne
    @AdminLookup("tag")
    private Pipe pipe;

    public Long getId() { return id; }
    public Flow getFlow() { return flow; }
    public void setFlow(Flow flow) { this.flow = flow; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public Pipe getPipe() { return pipe; }
    public void setPipe(Pipe pipe) { this.pipe = pipe; }
}
