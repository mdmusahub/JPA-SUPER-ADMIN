package io.github.mdmusahub.jpaadmin.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UploadLimitsTest {

    @Test
    void theDefaultsAreTheDocumentedOnes() {
        assertThat(UploadLimits.DEFAULT_STORAGE).isEqualTo("default");
        assertThat(UploadLimits.DEFAULT_DIRECTORY).isEqualTo("./uploads");
        assertThat(UploadLimits.DEFAULT_MAX_BYTES).isEqualTo(5L * 1024 * 1024);
        assertThat(UploadLimits.CEILING_BYTES).isEqualTo(100L * 1024 * 1024);
    }

    @Test
    void sizesAreReadWithOneThousandAndTwentyFourUnits() {
        assertThat(UploadLimits.parseSize("2MB")).hasValue(2L * 1024 * 1024);
        assertThat(UploadLimits.parseSize("512kb")).hasValue(512L * 1024);
        assertThat(UploadLimits.parseSize(" 2048 ")).hasValue(2048L);
        assertThat(UploadLimits.parseSize("1 GB")).hasValue(1024L * 1024 * 1024);
        assertThat(UploadLimits.parseSize("10B")).hasValue(10L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "MB", "2 megabytes", "-1MB", "1.5MB", "0", "0MB", "1e3", "2TB", "9999999999999MB",
            "2MB;", "0x10"})
    void textThatIsNotASizeOrIsZeroIsNothing(String text) {
        assertThat(UploadLimits.parseSize(text)).isEqualTo(OptionalLong.empty());
    }

    @Test
    void nullIsNothing() {
        assertThat(UploadLimits.parseSize(null)).isEmpty();
    }

    @Test
    void aSizeForAPersonIsShortAndPlain() {
        assertThat(UploadLimits.describe(500)).isEqualTo("500 bytes");
        assertThat(UploadLimits.describe(1024)).isEqualTo("1 KB");
        assertThat(UploadLimits.describe(1536)).isEqualTo("1.5 KB");
        assertThat(UploadLimits.describe(5L * 1024 * 1024)).isEqualTo("5 MB");
        assertThat(UploadLimits.describe(1024L * 1024 + 512 * 1024)).isEqualTo("1.5 MB");
    }
}
