package io.github.mdmusahub.jpaadmin.testsupport;

import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Pins the JVM's default time zone to {@link #ID} <b>once, before the first test class of the run</b>, so every test of this module
 * gives the same result on a laptop in India, on a CI runner in UTC and anywhere else. It is registered by
 * {@code META-INF/services/org.junit.jupiter.api.extension.Extension} and switched on by {@code junit-platform.properties}
 * ({@code junit.jupiter.extensions.autodetection.enabled}), so a test class does not have to ask for it.
 *
 * <h2>Why once, and why before everything</h2>
 *
 * <p>H2 reads the JVM's default zone the first time it converts a date or a time and <b>keeps it</b> ({@code
 * org.h2.util.DateTimeUtils#getTimeZone}). Hibernate converts a {@code LocalDate} to a {@code java.sql.Date} with the zone that is the
 * default <i>at that moment</i>. When a test changed the default after H2 had looked at it (the engine tests did, to be the same on every
 * machine) the two disagreed and a stored {@code LocalDate} came back one day earlier: 12 tests failed on a CI runner in UTC and passed
 * on a laptop in India, where the "change" was no change. A zone that is set once, before H2 is first used, and never changed during
 * the run cannot disagree with anything. (Phase 14a, the CI run after D-128.)
 *
 * <p>The zone is a fixed, modern one that is not UTC, so a zone mistake in the code under test still shows (a JVM in UTC hides it). It
 * is also the name the PostgreSQL driver sends as the session's time zone: the official image no longer knows the legacy alias
 * {@code Asia/Calcutta}, which some JVMs report for the same zone (see {@code EngineTestBase}).
 *
 * <p>A test that is about the <i>server's</i> zone changes it for the length of one test and puts it back
 * ({@code ListQueryServiceFilterTest}); it must not touch a date column in between. Nothing else may call
 * {@code TimeZone.setDefault}: {@code PinnedTestZoneTest} reads the sources and fails if something does. A class that needs the
 * pin (a database that reads the zone) calls {@link #requirePinned()} instead of setting a zone itself.
 */
public final class PinnedTestZone implements BeforeAllCallback {

    /** The zone every test of this module runs in. */
    public static final String ID = "Asia/Kolkata";

    private static final Object LOCK = new Object();
    private static TimeZone original;

    /**
     * Called when the engine builds its extensions, which is before any test class is initialised: the zone is pinned here and not in
     * {@link #beforeAll}, because a test class whose static fields start a database would otherwise run first.
     */
    public PinnedTestZone() {
        pin();
    }

    /** The zone the tests run in, as a {@link ZoneId}. */
    public static ZoneId zone() {
        return ZoneId.of(ID);
    }

    /**
     * Fails, saying what to look at, when the zone is not the pinned one: the registration of this extension is missing, or a test
     * changed the zone and did not put it back. A silent difference here is what failed 12 tests on the first CI run.
     */
    public static void requirePinned() {
        String now = TimeZone.getDefault().getID();
        if (!ID.equals(now)) {
            throw new AssertionError("The JVM's default time zone is " + now + ", not the pinned " + ID + ". Check that "
                    + "src/test/resources/META-INF/services/org.junit.jupiter.api.extension.Extension names " + PinnedTestZone.class.getName()
                    + ", that junit-platform.properties sets junit.jupiter.extensions.autodetection.enabled=true, and that no test "
                    + "leaves TimeZone.setDefault changed.");
        }
    }

    private static void pin() {
        synchronized (LOCK) {
            if (original == null) {
                original = TimeZone.getDefault();
                TimeZone.setDefault(TimeZone.getTimeZone(ID));
            }
        }
    }

    private static void restore() {
        synchronized (LOCK) {
            if (original != null) {
                TimeZone.setDefault(original);
                original = null;
            }
        }
    }

    /** Puts the machine's own zone back when the whole run is over (an IDE can keep the JVM). */
    @Override
    public void beforeAll(ExtensionContext context) {
        context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL).computeIfAbsent(Restore.class, key -> new Restore());
    }

    private static final class Restore implements ExtensionContext.Store.CloseableResource {
        @Override
        public void close() {
            restore();
        }
    }
}
