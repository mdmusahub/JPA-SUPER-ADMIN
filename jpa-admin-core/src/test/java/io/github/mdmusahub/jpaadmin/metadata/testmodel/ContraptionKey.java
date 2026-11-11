package io.github.mdmusahub.jpaadmin.metadata.testmodel;

import jakarta.persistence.Embeddable;
import java.io.Serializable;

/** The composite key of Contraption. */
@Embeddable
public class ContraptionKey implements Serializable {

    private String region;
    private int serial;

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public int getSerial() { return serial; }
    public void setSerial(int serial) { this.serial = serial; }
}
