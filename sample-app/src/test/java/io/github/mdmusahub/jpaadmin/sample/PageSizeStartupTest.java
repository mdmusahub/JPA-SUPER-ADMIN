package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * A real start of the sample application with {@code jpa.admin.page-size} (Phase 13, D-119): an allowed value starts it, any
 * other value stops startup with a message that names the property, says what is allowed and never repeats the value or the
 * password (SEC-6).
 */
@ExtendWith(OutputCaptureExtension.class)
class PageSizeStartupTest {

    private static final String STRONG = "Zx9-fake-strong-pw-QWERTY-7731";

    private static ConfigurableApplicationContext start(String... arguments) {
        String[] all = Stream.concat(Stream.of("--server.port=0", "--spring.main.banner-mode=off",
                "--jpa.admin.username=admin", "--jpa.admin.password=" + STRONG), Stream.of(arguments)).toArray(String[]::new);
        return SpringApplication.run(SampleApplication.class, all);
    }

    @Test
    void anAllowedSizeStartsTheApplication() {
        for (String size : new String[] {"10", "25", "50", "100"}) {
            try (ConfigurableApplicationContext context = start("--jpa.admin.page-size=" + size)) {
                assertThat(context.isRunning()).as("page-size=" + size).isTrue();
            }
        }
    }

    @Test
    void aSizeThatIsNotOnOfferStopsStartupAndNamesTheProperty(CapturedOutput output) {
        assertThatThrownBy(() -> start("--jpa.admin.page-size=7")).isInstanceOf(RuntimeException.class);

        assertThat(output.getAll()).contains("APPLICATION FAILED TO START")
                .contains("jpa.admin.page-size must be one of 10, 25, 50, 100").doesNotContain(STRONG);
    }

    @Test
    void anAbsurdSizeIsReportedByTheSameMessageAndNeverRepeated(CapturedOutput output) {
        assertThatThrownBy(() -> start("--jpa.admin.page-size=987654321987")).isInstanceOf(RuntimeException.class);

        assertThat(output.getAll()).contains("jpa.admin.page-size must be one of 10, 25, 50, 100")
                .doesNotContain("987654321987").doesNotContain(STRONG);
    }

    @Test
    void aSizeThatIsNotANumberStopsStartupAndNamesTheProperty(CapturedOutput output) {
        assertThatThrownBy(() -> start("--jpa.admin.page-size=lots")).isInstanceOf(RuntimeException.class);

        assertThat(output.getAll()).contains("APPLICATION FAILED TO START").contains("jpa.admin.page-size")
                .doesNotContain(STRONG);
    }

    @Test
    void aBadSizeIsReportedTogetherWithABadColour(CapturedOutput output) {
        assertThatThrownBy(() -> start("--jpa.admin.page-size=3", "--jpa.admin.primary-color=red"))
                .isInstanceOf(RuntimeException.class);

        assertThat(output.getAll()).contains("jpa.admin.page-size must be one of").contains("jpa.admin.primary-color must be a colour")
                .doesNotContain(STRONG);
    }
}
