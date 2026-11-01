package io.github.mdmusahub.jpaadmin.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mdmusahub.jpaadmin.web.BasePath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * SEC-7 (required credentials), SEC-14 (console path), SEC-15 (colour) and SEC-6 (the password is never printed),
 * checked on the properties object itself. Binding from real property sources is in
 * {@link JpaAdminPropertiesBindingTest}.
 */
class JpaAdminPropertiesTest {

    private static final String PASSWORD = "S3cret-test-value-NOT-REAL";

    private static JpaAdminProperties valid() {
        JpaAdminProperties properties = new JpaAdminProperties();
        properties.setUsername("admin");
        properties.setPassword(PASSWORD);
        return properties;
    }

    @Test
    void hasTheDocumentedDefaults() {
        JpaAdminProperties properties = new JpaAdminProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getPath()).isEqualTo("/admin").isEqualTo(BasePath.DEFAULT);
        assertThat(properties.getTitle()).isEqualTo("JPA Admin");
        assertThat(properties.getPrimaryColor()).isEqualTo("#0d9f6e");
        assertThat(properties.getUsername()).isNull();
        assertThat(properties.getPassword()).isNull();
    }

    @Test
    void acceptsTheTwoRequiredValuesWithEverythingElseDefaulted() {
        assertThatCode(() -> valid().validate()).doesNotThrowAnyException();
    }

    @Test
    void afterPropertiesSetRunsTheSameChecks() {
        assertThatThrownBy(() -> new JpaAdminProperties().afterPropertiesSet())
                .isInstanceOf(JpaAdminConfigurationException.class);
    }

    // ---- username and password ---------------------------------------------------------------------------------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "  \n  "})
    void aMissingOrBlankUsernameNamesTheProperty(String username) {
        JpaAdminProperties properties = valid();
        properties.setUsername(username);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(JpaAdminConfigurationException.class)
                .hasMessageContaining("jpa.admin.username is required. Set it in application.yml, "
                        + "for example jpa.admin.username=admin.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "  \n  "})
    void aMissingOrBlankPasswordNamesTheProperty(String password) {
        JpaAdminProperties properties = valid();
        properties.setPassword(password);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(JpaAdminConfigurationException.class)
                .hasMessageContaining("jpa.admin.password is required. Set it in application.yml")
                .hasMessageContaining("jpa.admin.password=${ADMIN_PASSWORD}");
    }

    @Test
    void reportsEveryProblemInOnePass() {
        JpaAdminProperties properties = new JpaAdminProperties();
        properties.setPath("/");
        properties.setPrimaryColor("red");

        assertThatThrownBy(properties::validate)
                .isInstanceOfSatisfying(JpaAdminConfigurationException.class, e -> {
                    assertThat(e.problems()).hasSize(4);
                    assertThat(e.problems().get(0)).startsWith("jpa.admin.username");
                    assertThat(e.problems().get(1)).startsWith("jpa.admin.password");
                    assertThat(e.problems().get(2)).startsWith("jpa.admin.path");
                    assertThat(e.problems().get(3)).startsWith("jpa.admin.primary-color");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"${ADMIN_USERNAME}", " ${ADMIN_USERNAME} ", "${a.b.c}", "${X}"})
    void aUsernameThatIsStillAPlaceholderCountsAsMissing(String username) {
        JpaAdminProperties properties = valid();
        properties.setUsername(username);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(JpaAdminConfigurationException.class)
                .hasMessageContaining("jpa.admin.username refers to a variable that has no value");
    }

    @ParameterizedTest
    @ValueSource(strings = {"${ADMIN_PASSWORD}", " ${ADMIN_PASSWORD} ", "${a.b.c}", "${X}"})
    void aPasswordThatIsStillAPlaceholderCountsAsMissing(String password) {
        JpaAdminProperties properties = valid();
        properties.setPassword(password);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(JpaAdminConfigurationException.class)
                .hasMessageContaining("jpa.admin.password refers to a variable that has no value");
    }

    @ParameterizedTest
    @ValueSource(strings = {"pa$$w{0}rd-long-enough", "abc${X}def", "$", "{x}", "$ {x}", "${x", "x}"})
    void textThatOnlyContainsPlaceholderCharactersIsARealPassword(String password) {
        JpaAdminProperties properties = valid();
        properties.setPassword(password);

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    // ---- path (SEC-14) -----------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/backoffice", "/Admin2", "/my-admin", "/my_admin", "/tools/admin"})
    void acceptsAStrictPath(String path) {
        JpaAdminProperties properties = valid();
        properties.setPath(path);

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"/", "admin", "/admin/", "//admin", "/admin//x", "/admin/../x", "/admin/./x", "/.hidden",
            "/-x", "/a b", "/a\tb", "/a\nb", "/admin\\x", "/admin?x=1", "/admin#x", "/admin;x", "/admin%2fx",
            "/{x}", "/admin/*", "/admin/**", "/admïn", "http://evil.example/admin", "javascript:alert(1)"})
    void rejectsABadPathAndNamesTheProperty(String path) {
        JpaAdminProperties properties = valid();
        properties.setPath(path);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(JpaAdminConfigurationException.class)
                .hasMessageContaining("jpa.admin.path must start with '/'")
                .hasMessageContaining("for example jpa.admin.path=/admin");
    }

    @Test
    void rejectsAPathThatIsTooLong() {
        JpaAdminProperties properties = valid();
        properties.setPath("/" + "a".repeat(100));

        assertThatThrownBy(properties::validate).hasMessageContaining("jpa.admin.path");
    }

    // ---- primary colour (SEC-15) -------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"#0d9f6e", "#ABCDEF", "#000000", "#ffffff", "#12aB9f"})
    void acceptsAColourWrittenAsRrggbb(String colour) {
        JpaAdminProperties properties = valid();
        properties.setPrimaryColor(colour);

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"#fff", "#ffff", "#fffffff", "#GGGGGG", "0d9f6e", "#0d9f6e00", "red", " #0d9f6e",
            "#0d9f6e ", "#0d9f6e\n", "#0d9f6e;}", "#0d9f6e;color:red", "rgb(1,2,3)", "url(javascript:alert(1))",
            "</style><script>", "#١٢٣٤٥٦"})
    void rejectsAnythingButRrggbbAndNamesTheProperty(String colour) {
        JpaAdminProperties properties = valid();
        properties.setPrimaryColor(colour);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(JpaAdminConfigurationException.class)
                .hasMessageContaining("jpa.admin.primary-color must be a colour written as #rrggbb");
    }

    // ---- the password is never printed (SEC-6) -----------------------------------------------------------------

    @Test
    void toStringNeverContainsThePassword() {
        assertThat(valid().toString()).doesNotContain(PASSWORD).contains("<hidden>").contains("username=admin");
    }

    @Test
    void toStringSaysWhenNoPasswordIsSet() {
        assertThat(new JpaAdminProperties().toString()).contains("<not set>");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"/", "admin/"})
    void theFailureMessageNeverContainsThePassword(String path) {
        JpaAdminProperties properties = valid();
        properties.setPath(path);
        properties.setPrimaryColor("red");
        properties.setUsername("");

        assertThatThrownBy(properties::validate)
                .isInstanceOfSatisfying(JpaAdminConfigurationException.class, e -> {
                    assertThat(e.getMessage()).doesNotContain(PASSWORD);
                    assertThat(e.problems()).noneMatch(problem -> problem.contains(PASSWORD));
                });
    }
}
