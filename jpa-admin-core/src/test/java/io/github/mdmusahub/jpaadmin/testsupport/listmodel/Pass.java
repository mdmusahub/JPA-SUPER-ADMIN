package io.github.mdmusahub.jpaadmin.testsupport.listmodel;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * An entity whose identifier is a sensitive field by name ("token"): its records have no address, because the address
 * would print the identifier (SEC-1). The console lists it with the identifier masked and offers no detail page, link,
 * checkbox or delete for it.
 */
@Entity
public class Pass {

    @Id
    private Long accessToken;

    private String holder;

    public Long getAccessToken() { return accessToken; }
    public void setAccessToken(Long accessToken) { this.accessToken = accessToken; }
    public String getHolder() { return holder; }
    public void setHolder(String holder) { this.holder = holder; }
}
