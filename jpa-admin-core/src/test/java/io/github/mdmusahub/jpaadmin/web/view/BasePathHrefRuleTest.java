package io.github.mdmusahub.jpaadmin.web.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.mdmusahub.jpaadmin.web.BasePath;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * D-29: from Phase 1 on, every link the console builds must pass the contract's link rule and lie under the base
 * path. This test sits in the {@code web.view} package because {@code Hrefs} is package-private. It proves that
 * whatever {@link BasePath} accepts and returns is accepted by {@code Hrefs} and by {@link PageChrome}, so the two
 * rules cannot drift apart.
 */
class BasePathHrefRuleTest {

    private static final List<String> ROUTES = List.of("", "/assets/css/tokens.css", "/assets/js/theme.js",
            "/item", "/item/new", "/item/12", "/item/12/edit", "/item/export.csv", "/api/stats",
            "/login", "/uploads", "/files/2026/11/photo.png");

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/backoffice", "/Admin2", "/my-admin", "/my_admin", "/tools/admin", "/a/b/c/d"})
    void theBasePathPassesTheLinkRuleAndPageChrome(String path) {
        BasePath base = BasePath.of(path);

        assertThatCode(() -> Hrefs.require("basePath", base.value())).doesNotThrowAnyException();
        assertThatCode(() -> chromeWith(base.value())).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/backoffice", "/tools/admin"})
    void everyBuiltLinkPassesTheLinkRuleAndLiesUnderTheBasePath(String path) {
        BasePath base = BasePath.of(path);

        for (String route : ROUTES) {
            String link = base.link(route);
            assertThatCode(() -> Hrefs.require("link", link)).doesNotThrowAnyException();
            assertThat(Hrefs.isUnder(base.value(), link)).as(link).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/backoffice", "/tools/admin"})
    void theAssetsPatternWithoutItsWildcardIsAValidLinkUnderTheBasePath(String path) {
        BasePath base = BasePath.of(path);
        String pattern = base.assetsPattern();
        String directory = pattern.substring(0, pattern.length() - "/**".length());

        assertThatCode(() -> Hrefs.require("assets", directory)).doesNotThrowAnyException();
        assertThat(Hrefs.isUnder(base.value(), directory)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/backoffice", "/tools/admin"})
    void breadcrumbsBuiltFromTheBasePathAreAccepted(String path) {
        BasePath base = BasePath.of(path);
        List<PageChrome.Breadcrumb> trail = List.of(new PageChrome.Breadcrumb("Home", base.value()),
                new PageChrome.Breadcrumb("Items", base.link("/item")), new PageChrome.Breadcrumb("Edit", null));

        assertThatCode(() -> new PageChrome("Items", base.value(), PageChrome.Theme.SYSTEM, List.of(), null, trail,
                List.of(), Samples.csrf())).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/backoffice"})
    void aSiblingPathIsNotUnderTheBasePath(String path) {
        // "/admin" must not claim "/administrator" or "/admin-old" (the prefix trap).
        assertThat(Hrefs.isUnder(path, path + "istrator")).isFalse();
        assertThat(Hrefs.isUnder(path, path + "-old/x")).isFalse();
        assertThat(Hrefs.isUnder(path, "/other" + path)).isFalse();
    }

    private static PageChrome chromeWith(String basePath) {
        return new PageChrome("Items", basePath, PageChrome.Theme.SYSTEM, List.of(), null, List.of(), List.of(),
                Samples.csrf());
    }
}
