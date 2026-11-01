package io.github.mdmusahub.jpaadmin.sample;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Demo shop used for manual checks and integration tests. Not published.
 */
@SpringBootApplication
public class SampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleApplication.class, args);
    }

    /** Clock used by the demo data loader, so tests can check dates against the same clock. */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
