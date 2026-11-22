package io.github.mdmusahub.jpaadmin.testsupport.enginemodel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * The one entity that only the engine tests (Phase 14a) need: three kinds of long text and a creation moment of type
 * {@link Instant}, so each database is asked the questions the other test models cannot ask.
 *
 * <ul>
 *   <li>{@code headline}: a column of 4,000 characters;</li>
 *   <li>{@code summary}: a column of 20,000 characters (more than a {@code varchar} holds on some databases, so the
 *       provider's mapping is part of the test);</li>
 *   <li>{@code createdAt}: an instant, so the day a record belongs to depends on the zone the chart is read in.</li>
 * </ul>
 * The {@code @Lob} text is on {@link Tome}, alone, so a failure of that column cannot hide the others.
 */
@Entity
public class Essay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(length = 4000)
    private String headline;

    @Column(length = 20000)
    private String summary;

    private Instant createdAt;

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
