package io.github.mdmusahub.jpaadmin.testsupport.listmodel;

import io.github.mdmusahub.jpaadmin.annotation.AdminSensitive;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** A relation target with no preferred label name: the first text attribute that is not sensitive is used. */
@Entity
public class Courier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Sensitive and alphabetically first: it must not be picked. */
    @AdminSensitive
    private String badgeNumber;

    private String callSign;

    public Long getId() { return id; }
    public String getBadgeNumber() { return badgeNumber; }
    public void setBadgeNumber(String badgeNumber) { this.badgeNumber = badgeNumber; }
    public String getCallSign() { return callSign; }
    public void setCallSign(String callSign) { this.callSign = callSign; }
}
