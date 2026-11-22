package io.github.mdmusahub.jpaadmin.versiontest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

/**
 * A test-only entity with a {@code @Version} (Phase 14a, D-128), so a stale form can be proven through the real application. The
 * demo's own entities have no {@code @Version} and none was added (D-80). This class lives in the test sources, in a package
 * outside the demo application's own, so no other test's context can see it: only the stale-form tests add this package to the
 * entity scan.
 */
@Entity
public class Tally {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long revision;

    @Column(length = 40)
    private String title;

    public Tally() {
    }

    public Tally(String title) {
        this.title = title;
    }

    public Long getId() { return id; }
    public long getRevision() { return revision; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
