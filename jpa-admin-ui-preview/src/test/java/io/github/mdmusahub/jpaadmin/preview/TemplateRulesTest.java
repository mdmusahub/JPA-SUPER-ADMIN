package io.github.mdmusahub.jpaadmin.preview;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * The UI rules, checked on the real files of {@code jpa-admin-ui} (this module sees them as a dependency): no unescaped
 * output (SEC-9), no inline script, style or handler (UI-13, SEC-15), no external address (UI-2), and every template
 * reads only {@code chrome} and {@code view} (UI-12). The checker is tested separately in {@code HtmlRulesTest}.
 */
class TemplateRulesTest {

    private static final PathMatchingResourcePatternResolver RESOLVER = new PathMatchingResourcePatternResolver();

    private static String read(Resource resource) throws IOException {
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static List<Resource> templates() throws IOException {
        return List.of(RESOLVER.getResources("classpath*:templates/jpa-admin/**/*.html"));
    }

    private static List<Resource> assets(String extension) throws IOException {
        return List.of(RESOLVER.getResources("classpath*:jpa-admin/assets/**/*." + extension));
    }

    @Test
    void theU0TemplatesAreAllThere() throws IOException {
        List<String> names = templates().stream().map(r -> r.getFilename()).toList();

        assertThat(names).contains("layout.html", "login.html", "dashboard.html", "error.html", "sidebar.html",
                "topbar.html", "breadcrumbs.html", "toast.html", "head.html", "icons.html", "theme-toggle.html");
    }

    @Test
    void noTemplateBreaksAnyUiRule() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Resource template : templates()) {
            for (String problem : HtmlRules.templateViolations(read(template))) {
                problems.add(template.getFilename() + ": " + problem);
            }
        }

        assertThat(problems).isEmpty();
    }

    @Test
    void everyPageTemplateEitherUsesTheLayoutOrIsTheSignInPage() throws IOException {
        for (String page : List.of("dashboard.html", "error.html")) {
            Resource template = RESOLVER.getResource("classpath:templates/jpa-admin/" + page);
            assertThat(read(template)).as(page).contains("~{jpa-admin/layout :: layout(");
        }
    }

    @Test
    void noStylesheetOrScriptHasAnExternalAddressImportOrRemoteFont() throws IOException {
        Pattern external = Pattern.compile("(?i)https?://|@import|url\\(\\s*['\"]?//|@font-face");
        List<String> problems = new ArrayList<>();
        List<Resource> files = new ArrayList<>(assets("css"));
        files.addAll(assets("js"));
        for (Resource file : files) {
            if (external.matcher(read(file)).find()) {
                problems.add(file.getFilename());
            }
        }

        assertThat(files).isNotEmpty();
        assertThat(problems).isEmpty();
    }

    @Test
    void theScriptsAreSmallPlainFilesWithNoDependencyOnABuildStep() throws IOException {
        for (Resource script : assets("js")) {
            String source = read(script);
            assertThat(source).as(script.getFilename()).doesNotContain("import ", "require(", "export ", "eval(",
                    "document.write", "innerHTML");
        }
        assertThat(assets("js").stream().map(r -> r.getFilename())).contains("theme.js", "nav.js");
    }

    @Test
    void theScriptsStoreOnlyTheThemeChoiceInTheBrowserAndGuardTheStorage() throws IOException {
        String theme = read(RESOLVER.getResource("classpath:jpa-admin/assets/js/theme.js"));

        assertThat(theme).contains("jpa-admin.theme").contains("try {");
        assertThat(theme.split("localStorage", -1).length - 1).as("every storage access is in the two guarded helpers")
                .isEqualTo(2);
        assertThat(theme).doesNotContain("sessionStorage", "document.cookie", "indexedDB");
    }

    @Test
    void theStylesheetsDefineBothThemesAndRespectReducedMotion() throws IOException {
        String tokens = read(RESOLVER.getResource("classpath:jpa-admin/assets/css/tokens.css"));
        String base = read(RESOLVER.getResource("classpath:jpa-admin/assets/css/base.css"));

        assertThat(tokens).contains("design tokens", "--ac:", "prefers-color-scheme: dark", "[data-theme=\"dark\"]");
        assertThat(base).contains("prefers-reduced-motion: reduce", ":focus-visible");
    }
}
