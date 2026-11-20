package io.github.mdmusahub.jpaadmin.autoconfigure.tonemodel.bad;

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

/**
 * Every Phase 13 annotation used wrongly: an inline colour, an unknown enum constant, a badge on a number, a lookup that names
 * a sensitive property, a lookup that names nothing, a lookup on a text field, and a plural with a control character.
 */
@Entity
@AdminResource(plural = "Fau\u0007cets")
public class Faucet {

    public enum Mode { COLD, HOT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @AdminBadge({"A=#ff0000"})
    private String inlineColour;

    @Enumerated(EnumType.STRING)
    @AdminBadge({"COLD=green", "LUKEWARM=amber"})
    private Mode mode;

    @AdminBadge({"1=green"})
    private int pressure;

    @ManyToOne
    @AdminLookup("secretNote")
    private Spout sensitiveLookup;

    @ManyToOne
    @AdminLookup("nothing")
    private Spout missingLookup;

    @AdminLookup("name")
    private String lookupOnText;

    public Long getId() { return id; }
    public String getInlineColour() { return inlineColour; }
    public void setInlineColour(String inlineColour) { this.inlineColour = inlineColour; }
    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public int getPressure() { return pressure; }
    public void setPressure(int pressure) { this.pressure = pressure; }
    public Spout getSensitiveLookup() { return sensitiveLookup; }
    public void setSensitiveLookup(Spout sensitiveLookup) { this.sensitiveLookup = sensitiveLookup; }
    public Spout getMissingLookup() { return missingLookup; }
    public void setMissingLookup(Spout missingLookup) { this.missingLookup = missingLookup; }
    public String getLookupOnText() { return lookupOnText; }
    public void setLookupOnText(String lookupOnText) { this.lookupOnText = lookupOnText; }
}
