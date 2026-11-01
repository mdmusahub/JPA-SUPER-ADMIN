package io.github.mdmusahub.jpaadmin.web.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Every record and enum of the contract is public and carries the draft/internal Javadoc statement (ARCH-9). */
class ViewContractDocumentationTest {

    private static final Path VIEW_SOURCES = Path.of("src/main/java/io/github/mdmusahub/jpaadmin/web/view");

    private static final Pattern PUBLIC_TYPE = Pattern.compile("(?m)^[ \\t]*public (?:static )?(record|enum) (\\w+)");
    private static final Pattern ANY_RECORD = Pattern.compile("(?m)^[ \\t]*(?:\\w+ )*record (\\w+)\\(");
    private static final Pattern ANY_ENUM = Pattern.compile("(?m)^[ \\t]*(?:\\w+ )*enum (\\w+) \\{");

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.list(VIEW_SOURCES)) {
            return files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals("package-info.java"))
                    .sorted().toList();
        }
    }

    @Test
    void contractHasTheTenTopLevelRecordsAndOnePackagePrivateHelper() throws IOException {
        List<String> names = sources().stream()
                .map(p -> p.getFileName().toString().replace(".java", "")).toList();

        assertThat(names).containsExactlyInAnyOrder("PageChrome", "LoginView", "DashboardView", "ChartSeries",
                "ListView", "Cell", "DetailView", "FormView", "UploadResult", "ErrorView", "Hrefs");
    }

    @Test
    void theLinkRuleHelperIsPackagePrivateAndNotARecord() throws IOException {
        String helper = Files.readString(VIEW_SOURCES.resolve("Hrefs.java"));

        assertThat(helper).contains("\nfinal class Hrefs {");
        assertThat(ANY_RECORD.matcher(helper).find()).as("declares a record").isFalse();
        assertThat(ANY_ENUM.matcher(helper).find()).as("declares an enum").isFalse();
        assertThat(helper).doesNotContainPattern("(?m)^\\s*public ");
    }

    @Test
    void everyRecordAndEnumIsPublic() throws IOException {
        for (Path source : sources()) {
            String text = Files.readString(source);
            long records = ANY_RECORD.matcher(text).results().count();
            long enums = ANY_ENUM.matcher(text).results().count();
            long publicTypes = PUBLIC_TYPE.matcher(text).results().count();

            assertThat(publicTypes).as(source.getFileName() + ": all records and enums are public")
                    .isEqualTo(records + enums);
        }
    }

    @Test
    void everyRecordAndEnumStatesItIsDraftInternalAndHoldsNoSensitiveValues() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path source : sources()) {
            String text = Files.readString(source);
            Matcher matcher = PUBLIC_TYPE.matcher(text);
            while (matcher.find()) {
                String before = text.substring(0, matcher.start()).stripTrailing();
                String name = source.getFileName() + ": " + matcher.group(2);
                if (!before.endsWith("*/")) {
                    problems.add(name + " has no Javadoc");
                    continue;
                }
                String javadoc = before.substring(before.lastIndexOf("/**"));
                if (!javadoc.contains("Draft, internal, not stable API (ARCH-9 exception)")
                        || !javadoc.contains("Holds no sensitive values (SEC-1)")) {
                    problems.add(name + " Javadoc lacks the draft/internal/no-sensitive-values statement");
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void recordsUseNoFrameworkTypes() throws IOException {
        for (Path source : sources()) {
            assertThat(Files.readString(source)).as(source.getFileName().toString())
                    .doesNotContain("org.springframework", "org.thymeleaf", "jakarta.");
        }
    }
}
