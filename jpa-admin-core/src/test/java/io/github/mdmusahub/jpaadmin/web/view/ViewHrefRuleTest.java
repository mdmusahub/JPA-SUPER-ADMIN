package io.github.mdmusahub.jpaadmin.web.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * D-28: every link in every record is a local path that starts with a single {@code /}. Rejected forms: a scheme
 * ({@code javascript:}), {@code //}, a backslash, whitespace and control characters. Breadcrumbs lie under the base
 * path.
 */
class ViewHrefRuleTest {

    private static final String PACKAGE = "io.github.mdmusahub.jpaadmin.web.view";
    private static final Path VIEW_SOURCES = Path.of("src/main/java/io/github/mdmusahub/jpaadmin/web/view");

    /** A place that carries a link, and how to build its record with the link under test. */
    private record Place(String name, Function<String, Object> build) {
    }

    /** A rejected link with a short description for the test name. */
    private record Bad(String description, String value) {
    }

    private static Place place(String name, Function<String, Object> build) {
        return new Place(name, build);
    }

    private static ListView.Paging pagingOnTheMiddlePage(String first, String prev, String next, String last) {
        return new ListView.Paging(1, 25, 75, first, prev, next, last, Samples.sizes(25));
    }

    private static final List<Place> PLACES = List.of(
            place("Cell.href", h -> new Cell(Cell.Type.RELATION, "x", Cell.Tone.NONE, h, null)),
            place("Breadcrumb.href", h -> new PageChrome.Breadcrumb("x", h)),
            place("PageChrome.basePath", h -> new PageChrome("t", h, PageChrome.Theme.SYSTEM, List.of(), null,
                    List.of(), List.of(), Samples.csrf())),
            place("Filters.clearAllHref", h -> new ListView.Filters(List.of(), List.of(), h)),
            place("Chip.removeHref", h -> new ListView.Filters.Chip("k", "l", "d", h)),
            place("Column.sortHref", h -> new ListView.Column("k", "l", Cell.Type.TEXT, true, h,
                    ListView.Column.SortState.NONE, ListView.Column.Align.START, true, true)),
            place("Paging.firstHref", h -> pagingOnTheMiddlePage(h, "/a", "/a", "/a")),
            place("Paging.prevHref", h -> pagingOnTheMiddlePage("/a", h, "/a", "/a")),
            place("Paging.nextHref", h -> pagingOnTheMiddlePage("/a", "/a", h, "/a")),
            place("Paging.lastHref", h -> pagingOnTheMiddlePage("/a", "/a", "/a", h)),
            place("SizeOption.href", h -> new ListView.Paging.SizeOption(25, true, h)),
            place("Actions.exportHref", h -> new ListView.Actions(true, true, true, h)),
            place("FormView.actionHref", h -> new FormView(FormView.Mode.CREATE, "t", "e", null, h, "/a", List.of(),
                    List.of())),
            place("FormView.cancelHref", h -> new FormView(FormView.Mode.CREATE, "t", "e", null, "/a", h, List.of(),
                    List.of())),
            place("DetailView.Image.url", h -> new DetailView.Image(h, "l")),
            place("UploadResult.url", h -> UploadResult.success(h, "n", 1)));

    private static final List<Bad> BAD = List.of(
            new Bad("a javascript: scheme", "javascript:alert(1)"),
            new Bad("a mixed-case javascript: scheme", "JaVaScRiPt:alert(1)"),
            new Bad("a javascript: scheme after a space", " javascript:alert(1)"),
            new Bad("a data: scheme", "data:text/html;base64,PHNjcmlwdD4="),
            new Bad("a mailto: scheme", "mailto:a@example.test"),
            new Bad("an https URL", "https://evil.example/x"),
            new Bad("an http URL", "http://evil.example/x"),
            new Bad("a protocol-relative URL", "//evil.example/x"),
            new Bad("three slashes", "///evil.example"),
            new Bad("a slash then a backslash", "/\\evil.example"),
            new Bad("a leading backslash", "\\evil.example"),
            new Bad("two leading backslashes", "\\\\evil.example"),
            new Bad("a backslash in the path", "/admin\\item"),
            new Bad("a backslash in the query", "/admin/item?x=a\\b"),
            new Bad("a tab between slashes", "/\t/evil.example"),
            new Bad("a line feed between slashes", "/\n/evil.example"),
            new Bad("a carriage return", "/admin/x\rSet-Cookie: a=b"),
            new Bad("a header injection with a line feed", "/admin/x\nSet-Cookie: a=b"),
            new Bad("a space", "/admin/my item"),
            new Bad("a NUL character", "/admin/\0"),
            new Bad("a non-breaking space", "/admin/" + (char) 0x00a0 + "item"),
            new Bad("a line separator", "/admin/" + (char) 0x2028 + "item"),
            new Bad("a double slash inside the path", "/admin//item"),
            new Bad("a double slash before the query", "/admin//?x=1"),
            new Bad("a path without a leading slash", "admin/item"),
            new Bad("a query without a path", "?sort=name"),
            new Bad("a fragment without a path", "#top"),
            new Bad("a parent-relative path", "../admin"),
            new Bad("an empty string", ""),
            new Bad("only spaces", "   "));

    private static final List<String> GOOD = List.of(
            "/admin",
            "/admin/item/42/edit",
            "/admin/item?search=a%20b&sort=name&dir=asc&page=0&size=25",
            "/admin/item?search=http://example.test//x#top",
            "/admin/item?from=2026-01-01T10:00:00",
            "/admin/files/a%20b.png",
            "/admin/item/%2F%2Fnot-a-host",
            "/admin/item?search=back%5Cslash");

    static Stream<Arguments> placesWithBadLinks() {
        return PLACES.stream().flatMap(place -> BAD.stream()
                .map(bad -> Arguments.of(place.name(), place.build(), bad.description(), bad.value())));
    }

    static Stream<Arguments> placesWithGoodLinks() {
        return PLACES.stream().flatMap(place -> GOOD.stream().map(good -> Arguments.of(place.name(), place.build(), good)));
    }

    @ParameterizedTest(name = "{0} rejects {2}")
    @MethodSource("placesWithBadLinks")
    void everyPlaceRejectsALinkThatIsNotALocalPath(String place, Function<String, Object> build, String description,
                                                   String value) {
        assertThatThrownBy(() -> build.apply(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0} accepts {2}")
    @MethodSource("placesWithGoodLinks")
    void everyPlaceAcceptsALocalPath(String place, Function<String, Object> build, String value) {
        assertThatCode(() -> build.apply(value)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} rejects {2}")
    @MethodSource("placesWithBadLinks")
    void theErrorNamesTheFieldAndNeverEchoesTheLink(String place, Function<String, Object> build, String description,
                                                    String value) {
        String field = place.substring(place.lastIndexOf('.') + 1);

        assertThatThrownBy(() -> build.apply(value)).satisfies(error -> assertThat(error.getMessage())
                .contains(field).doesNotContain("evil.example").doesNotContain("alert").doesNotContain("Set-Cookie"));
    }

    @ParameterizedTest
    @MethodSource("breadcrumbsUnderTheBasePath")
    void breadcrumbMayLieUnderTheBasePath(String href) {
        assertThatCode(() -> chromeWithBreadcrumb("/admin", href)).doesNotThrowAnyException();
    }

    static Stream<String> breadcrumbsUnderTheBasePath() {
        return Stream.of("/admin", "/admin/item", "/admin/item/42", "/admin?x=1", "/admin#top", null);
    }

    @ParameterizedTest
    @MethodSource("breadcrumbsOutsideTheBasePath")
    void breadcrumbOutsideTheBasePathIsRejected(String href) {
        assertThatThrownBy(() -> chromeWithBreadcrumb("/admin", href))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("under the base path");
    }

    static Stream<String> breadcrumbsOutsideTheBasePath() {
        return Stream.of("/", "/administrator", "/administrator/item", "/other", "/admin-preview/item", "/other/admin");
    }

    @Test
    void breadcrumbIsCheckedAgainstTheChromesOwnBasePath() {
        assertThatCode(() -> chromeWithBreadcrumb("/admin-preview", "/admin-preview/item")).doesNotThrowAnyException();
        assertThatThrownBy(() -> chromeWithBreadcrumb("/admin-preview", "/admin/item"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PageChrome chromeWithBreadcrumb(String basePath, String href) {
        return new PageChrome("t", basePath, PageChrome.Theme.SYSTEM, List.of(), null,
                List.of(new PageChrome.Breadcrumb("Crumb", href)), List.of(), Samples.csrf());
    }

    @Test
    void breadcrumbBelongingToThePageIsStillCheckedForTheLinkRule() {
        assertThatThrownBy(() -> chromeWithBreadcrumb("/admin", "//evil.example/admin"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyLinkComponentIsCheckedByTheSharedRule() throws IOException {
        Map<String, Long> found = new TreeMap<>();
        List<String> unchecked = new ArrayList<>();
        try (Stream<Path> files = Files.list(VIEW_SOURCES)) {
            List<String> names = files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".java"))
                    .map(n -> n.replace(".java", "")).filter(n -> !n.equals("Hrefs")).sorted().toList();
            for (String name : names) {
                Map<String, Long> inFile = new TreeMap<>();
                collect(classNamed(name), inFile);
                String source = Files.readString(VIEW_SOURCES.resolve(name + ".java"));
                inFile.forEach((component, count) -> {
                    long checks = Pattern.compile("Hrefs\\.(?:require|optional)\\(\"" + component + "\"")
                            .matcher(source).results().count();
                    if (checks < count) {
                        unchecked.add(name + "." + component);
                    }
                    found.merge(component, count, Long::sum);
                });
            }
        }

        assertThat(unchecked).as("link components without a Hrefs check").isEmpty();
        assertThat(found).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("href", 3L), Map.entry("sortHref", 1L), Map.entry("clearAllHref", 1L),
                Map.entry("removeHref", 1L), Map.entry("firstHref", 1L), Map.entry("prevHref", 1L),
                Map.entry("nextHref", 1L), Map.entry("lastHref", 1L), Map.entry("exportHref", 1L),
                Map.entry("actionHref", 1L), Map.entry("cancelHref", 1L), Map.entry("url", 2L),
                Map.entry("basePath", 1L)));
        assertThat(PLACES).as("one rejection test place per link component").hasSize(
                found.values().stream().mapToInt(Long::intValue).sum());
    }

    private static Class<?> classNamed(String name) {
        try {
            return Class.forName(PACKAGE + "." + name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final Pattern LINK_COMPONENT = Pattern.compile("(?i).*href|url|basepath");

    private static void collect(Class<?> type, Map<String, Long> into) {
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                if (component.getType() == String.class && LINK_COMPONENT.matcher(component.getName()).matches()) {
                    into.merge(component.getName(), 1L, Long::sum);
                }
            }
        }
        for (Class<?> nested : type.getDeclaredClasses()) {
            collect(nested, into);
        }
    }
}
