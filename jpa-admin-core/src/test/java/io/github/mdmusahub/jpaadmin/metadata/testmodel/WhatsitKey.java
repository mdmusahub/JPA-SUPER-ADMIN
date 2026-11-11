package io.github.mdmusahub.jpaadmin.metadata.testmodel;

import java.io.Serializable;
import java.util.Objects;

/** The id class of Whatsit. */
public class WhatsitKey implements Serializable {

    private String site;
    private String code;

    public String getSite() { return site; }
    public void setSite(String site) { this.site = site; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    @Override
    public boolean equals(Object other) {
        return other instanceof WhatsitKey that && Objects.equals(site, that.site) && Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(site, code);
    }
}
