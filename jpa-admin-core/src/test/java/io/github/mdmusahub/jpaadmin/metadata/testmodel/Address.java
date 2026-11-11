package io.github.mdmusahub.jpaadmin.metadata.testmodel;

import jakarta.persistence.Embeddable;

/** An embeddable, so one field of Widget is of kind EMBEDDED. */
@Embeddable
public class Address {

    private String street;
    private String city;

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
}
