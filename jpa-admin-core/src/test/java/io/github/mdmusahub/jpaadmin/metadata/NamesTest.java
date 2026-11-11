package io.github.mdmusahub.jpaadmin.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The naming rules on their own: addresses, labels and the sensitive-name match (SEC-2, D-11). */
class NamesTest {

    // ---- addresses --------------------------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "Widget, widget",
            "WidgetPart, widgetpart",
            "HTTPLog, httplog",
            "Item2Line, item2line",
            "My_Entity, my-entity",
            "Shop$Item, shop-item",
            "_Leading, leading",
            "Trailing_, trailing"})
    void anAddressIsTheLowerCaseNameWithOnlyLettersDigitsAndSingleHyphens(String simpleName, String expected) {
        assertThat(Names.urlName(simpleName)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"___", "$", "-"})
    void anAddressIsEmptyWhenTheNameHoldsNothingUsable(String simpleName) {
        assertThat(Names.urlName(simpleName)).isEmpty();
    }

    @Test
    void theFallbackAddressIsTheFullyQualifiedNameWithHyphens() {
        assertThat(Names.qualifiedUrlName("com.example.shop.Item")).isEqualTo("com-example-shop-item");
    }

    @Test
    void everyAddressIsSafeInAPathAndCannotCarryASchemeOrASlash() {
        for (String name : List.of("Widget", "My_Entity", "Shop$Item", "HTTPLog")) {
            assertThat(Names.urlName(name)).matches("[a-z0-9]+(-[a-z0-9]+)*");
        }
        assertThat(Names.urlName("a/b")).doesNotContain("/");
        assertThat(Names.urlName("a:b")).doesNotContain(":");
        assertThat(Names.urlName("a.b")).doesNotContain(".");
    }

    // ---- labels -----------------------------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "Widget, Widget",
            "WidgetPart, Widget part",
            "HttpLogEntry, Http log entry",
            "HTTPLog, HTTP log",
            "apiURL, Api URL",
            "userPassword, User password",
            "api_key, Api key",
            "response_code, Response code",
            "createdAt, Created at",
            "id, Id"})
    void aLabelIsTheJavaNameInWords(String name, String expected) {
        assertThat(Names.label(name)).isEqualTo(expected);
    }

    // ---- sensitive names --------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"password", "Password", "PASSWORD", "userPassword", "user_password", "passwordHash",
            "secret", "clientSecret", "token", "refresh_token", "apikey", "api_key", "API-KEY", "ApiKey"})
    void theDefaultNamesCoverTheUsualCredentialFields(String fieldName) {
        assertThat(Names.looksSensitive(fieldName, DiscoverySettings.DEFAULT_SENSITIVE_FIELDS)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"passport", "name", "description", "price", "tokyoOffice", "keyboard", "api", "key"})
    void aNameThatOnlyLooksLikeACredentialIsNotMasked(String fieldName) {
        assertThat(Names.looksSensitive(fieldName, DiscoverySettings.DEFAULT_SENSITIVE_FIELDS)).isFalse();
    }

    @Test
    void theMatchIsASubstringMatchSoItAlsoCatchesNamesNobodyMeantToMask() {
        // Documented in PROJECT_DOCS: masking too much is the safer mistake, and the property can be narrowed.
        assertThat(Names.looksSensitive("tokenizer", DiscoverySettings.DEFAULT_SENSITIVE_FIELDS)).isTrue();
        assertThat(Names.looksSensitive("secretarySeat", DiscoverySettings.DEFAULT_SENSITIVE_FIELDS)).isTrue();
    }

    @Test
    void onlyTheConfiguredNamesCount() {
        List<String> configured = List.of("vault");

        assertThat(Names.looksSensitive("vaultKey", configured)).isTrue();
        assertThat(Names.looksSensitive("password", configured)).isFalse();
    }

    @Test
    void anEmptyConfigurationMasksNothing() {
        assertThat(Names.looksSensitive("password", List.of())).isFalse();
    }
}
