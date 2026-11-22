package io.github.mdmusahub.jpaadmin.buildguard;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.testsupport.PinnedTestZone;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The tests of this module give the same result on every machine because the JVM's time zone is pinned once, before the first test
 * class (Phase 14a; see {@link PinnedTestZone} for why it has to be once). Two things would bring back the CI failure of a runner in
 * UTC, where 12 tests saw a {@code LocalDate} one day early: the pin not being registered, and a test changing the zone in the middle
 * of the run. This test fails for either.
 */
class PinnedTestZoneTest {

    /** The only sources that may call {@code TimeZone.setDefault}: the pin itself, and the one test about the server's own zone. */
    private static final List<String> MAY_CHANGE_THE_ZONE = List.of("PinnedTestZone.java", "ListQueryServiceFilterTest.java");

    @Test
    void theJvmRunsInThePinnedZoneWhateverTheMachineIsSetTo() {
        PinnedTestZone.requirePinned();

        assertThat(TimeZone.getDefault().getID()).isEqualTo("Asia/Kolkata");
        assertThat(ZoneId.systemDefault()).isEqualTo(PinnedTestZone.zone());
    }

    @Test
    void noTestChangesTheZoneInTheMiddleOfTheRunExceptTheOneAboutTheServersZone() throws IOException {
        // Built in two pieces so that this file does not match its own search.
        String call = "TimeZone" + ".setDefault(";
        Path sources = Path.of(System.getProperty("basedir", ".")).resolve("src").resolve("test").resolve("java");

        try (Stream<Path> files = Files.walk(sources)) {
            List<String> offenders = files.filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> !MAY_CHANGE_THE_ZONE.contains(file.getFileName().toString()))
                    .filter(file -> contains(file, call))
                    .map(file -> sources.relativize(file).toString().replace('\\', '/'))
                    .sorted().toList();

            assertThat(offenders).as("test sources that change the JVM's default zone in the middle of the run: H2 keeps the zone it "
                    + "first saw, so a LocalDate then comes back a day early (see PinnedTestZone)").isEmpty();
        }
    }

    private static boolean contains(Path file, String text) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).contains(text);
        } catch (IOException failure) {
            throw new AssertionError("cannot read " + file, failure);
        }
    }
}
