package io.github.mdmusahub.jpaadmin.sample;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A test application context whose {@code Clock} is fixed at noon UTC on 2026-11-10 (Phase 10). The demo data loader seeds the
 * creation dates relative to this clock and the console's chart reads the same one, so the seed is known to the day and
 * the 5-second cache can never expire during a test. The seed uses at most ten hours before noon, so every row stays on
 * the day the loader meant.
 */
@TestConfiguration(proxyBeanMethods = false)
class FixedClockConfiguration {

    static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

    @Bean
    @Primary
    Clock chartTestClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }
}
