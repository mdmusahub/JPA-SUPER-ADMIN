package io.github.mdmusahub.jpaadmin.metadata.testmodel;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** For the humanized label: "HttpLogEntry" becomes "Http log entry", and "apiURL" becomes "Api URL". */
@Entity
public class HttpLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String apiURL;

    private String response_code;

    public Long getId() { return id; }
    public String getApiURL() { return apiURL; }
    public void setApiURL(String apiURL) { this.apiURL = apiURL; }
    public String getResponse_code() { return response_code; }
    public void setResponse_code(String response_code) { this.response_code = response_code; }
}
