package io.github.mdmusahub.jpaadmin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** SEC-14: the console path follows a strict pattern and can never be {@code /}. */
class BasePathTest {

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/backoffice", "/Admin2", "/a", "/my-admin", "/my_admin", "/tools/admin",
            "/a/b/c/d", "/9lives"})
    void acceptsAStrictPath(String path) {
        assertThat(BasePath.isValid(path)).isTrue();
        assertThat(BasePath.of(path).value()).isEqualTo(path);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "/",            // the site root
            "admin",        // no leading slash
            "/admin/",      // trailing slash
            "//admin",      // protocol-relative
            "/admin//users",
            "/admin/../etc",
            "/admin/./x",
            "/..",
            "/.hidden",
            "/-admin",      // segment must start with a letter or digit
            "/_admin",
            "/admin x",     // space
            "/admin\t",
            "/admin\n",
            "/admin\r\n",
            " /admin",
            "/admin ",
            "/admin\\users",
            "/admin?x=1",
            "/admin#top",
            "/admin;jsessionid=1",
            "/admin%2fusers",
            "/admin/{id}",
            "/admin/*",
            "/admin/**",
            "/admïn",  // non-ASCII letter
            "/١٢", // Arabic-Indic digits are not [0-9]
            "http://evil.example/admin",
            "javascript:alert(1)",
            "/admin\u0000",
    })
    void rejectsEverythingElse(String path) {
        assertThat(BasePath.isValid(path)).isFalse();
        assertThatThrownBy(() -> BasePath.of(path)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsTheLongestPathAndRejectsOneCharacterMore() {
        String longest = "/" + "a".repeat(BasePath.MAX_LENGTH - 1);
        assertThat(BasePath.isValid(longest)).isTrue();
        assertThat(BasePath.isValid(longest + "a")).isFalse();
    }

    @Test
    void theErrorMessageNeverRepeatsTheInput() {
        assertThatThrownBy(() -> BasePath.of("/evil<script>"))
                .hasMessageNotContaining("evil")
                .hasMessageNotContaining("script");
    }

    @Test
    void theDefaultPathIsValid() {
        assertThat(BasePath.DEFAULT).isEqualTo("/admin");
        assertThat(BasePath.isValid(BasePath.DEFAULT)).isTrue();
    }

    @Test
    void buildsFixedRoutesBelowThePath() {
        BasePath base = BasePath.of("/backoffice");
        assertThat(base.link("")).isEqualTo("/backoffice");
        assertThat(base.link("/assets/css/tokens.css")).isEqualTo("/backoffice/assets/css/tokens.css");
        assertThat(base.link("/item/12/edit")).isEqualTo("/backoffice/item/12/edit");
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "assets", "/", "/assets/", "//host", "/a//b", "/..", "/a/../b", "/a/.", "/a b", "/a?x=1",
            "/a#f", "/a\\b", "/a\tb", "/a%2fb", "/a:b", "/é"})
    void refusesRoutesThatCouldLeaveThePathOrCarryData(String relative) {
        BasePath base = BasePath.of("/admin");
        assertThatThrownBy(() -> base.link(relative)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesANullRoute() {
        assertThatThrownBy(() -> BasePath.of("/admin").link(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void exposesTheAssetsPattern() {
        assertThat(BasePath.of("/admin").assetsPattern()).isEqualTo("/admin/assets/**");
        assertThat(BasePath.of("/tools/admin").assetsPattern()).isEqualTo("/tools/admin/assets/**");
    }

    @Test
    void comparesByValue() {
        assertThat(BasePath.of("/admin")).isEqualTo(BasePath.of("/admin")).hasSameHashCodeAs(BasePath.of("/admin"));
        assertThat(BasePath.of("/admin")).isNotEqualTo(BasePath.of("/other")).isNotEqualTo("/admin");
        assertThat(BasePath.of("/admin")).hasToString("/admin");
    }
}
