package io.github.mdmusahub.jpaadmin.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.service.CreationDates;
import io.github.mdmusahub.jpaadmin.service.StatsService;
import io.github.mdmusahub.jpaadmin.testsupport.StatsTestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.statsmodel.StatsEntities;
import io.github.mdmusahub.jpaadmin.web.view.ChartSeries;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The JSON endpoint of the chart (Phase 10): the shape of the body, the whitelist for {@code range}, what is and is not in
 * it, and how it is mapped. The session and GET-only rules belong to the security chain and are proven over the real chain
 * in the sample app; here the mapping itself is read from the class.
 */
class StatsControllerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private StatsController controller;

    @BeforeEach
    void setUp() {
        StatsTestPersistence.clear();
        controller = new StatsController(StatsService.over(CreationDates.over(StatsTestPersistence.entityManager()),
                StatsTestPersistence.registry(), CLOCK));
    }

    private MockHttpServletResponse get(String range) throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.stats(range, response);
        return response;
    }

    private JsonNode body(String range) throws IOException {
        return JSON.readTree(get(range).getContentAsString());
    }

    @Test
    void theBodyHasTheRangeTheEntitiesTheDaysAndTheNotes() throws IOException {
        StatsEntities.Journal row = new StatsEntities.Journal();
        row.createdAt = LocalDateTime.parse("2026-11-10T08:00:00");
        row.title = "a title that must not be in the answer";
        StatsTestPersistence.persist(row, row.getClass().cast(copy(row)));

        MockHttpServletResponse response = get("7");
        JsonNode json = JSON.readTree(response.getContentAsString());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(json.propertyNames()).containsExactlyInAnyOrder("range", "entities", "days", "notes");
        assertThat(json.get("range").longValue()).isEqualTo(7);
        assertThat(json.get("days").size()).isEqualTo(7);
        assertThat(json.get("days").get(6).get("date").asString()).isEqualTo("2026-11-10");
        JsonNode entities = json.get("entities");
        assertThat(entities.size()).isEqualTo(6);
        JsonNode journal = null;
        for (JsonNode entity : entities) {
            assertThat(entity.propertyNames()).containsExactlyInAnyOrder("name", "label", "available", "total", "capped");
            if (entity.get("name").asString().equals("journal")) {
                journal = entity;
            }
        }
        assertThat(journal).isNotNull();
        assertThat(journal.get("label").asString()).isEqualTo("Journal");
        assertThat(journal.get("available").booleanValue()).isTrue();
        assertThat(journal.get("total").longValue()).isEqualTo(2);
        assertThat(journal.get("capped").booleanValue()).isFalse();
        assertThat(json.get("notes").isArray()).isTrue();
        assertThat(response.getContentAsString()).doesNotContain("a title that must not be in the answer");
    }

    private static StatsEntities.Journal copy(StatsEntities.Journal row) {
        StatsEntities.Journal copy = new StatsEntities.Journal();
        copy.createdAt = row.createdAt;
        return copy;
    }

    @Test
    void everyDayCarriesOneCountPerEntityInTheOrderOfTheEntities() throws IOException {
        JsonNode json = body("30");

        assertThat(json.get("days").size()).isEqualTo(30);
        for (JsonNode day : json.get("days")) {
            assertThat(day.propertyNames()).containsExactlyInAnyOrder("date", "counts");
            assertThat(day.get("counts").size()).isEqualTo(json.get("entities").size());
        }
    }

    @Test
    void aRangeThatIsNotOfferedFallsBackToThirtyWithTheNote() throws IOException {
        for (String range : new String[] {"45", "abc", "", "7,30", "<img src=x onerror=alert(1)>"}) {
            JsonNode json = body(range);

            assertThat(json.get("range").longValue()).as(range).isEqualTo(30);
            assertThat(json.get("days").size()).isEqualTo(30);
            assertThat(json.get("notes").size()).isEqualTo(1);
            assertThat(json.get("notes").get(0).asString()).contains("not available").contains("30 days");
            assertThat(get(range).getContentAsString()).as("the typed text is not echoed").doesNotContain("onerror")
                    .doesNotContain("<img");
        }
    }

    @Test
    void noRangeIsThirtyWithoutANote() throws IOException {
        JsonNode json = body(null);

        assertThat(json.get("range").longValue()).isEqualTo(30);
        assertThat(json.get("notes").size()).isZero();
    }

    @Test
    void aHiddenEntityAndAnEntityWithoutACreationFieldAreNotInTheBody() throws IOException {
        StatsEntities.Shadow shadow = new StatsEntities.Shadow();
        shadow.createdAt = LocalDateTime.parse("2026-11-10T08:00:00");
        StatsTestPersistence.persist(shadow);

        String text = get("7").getContentAsString().toLowerCase();

        assertThat(text).doesNotContain("shadow").doesNotContain("masked").doesNotContain("veiled").doesNotContain("plain");
        List<String> names = new ArrayList<>();
        body("7").get("entities").forEach(entity -> names.add(entity.get("name").asString()));
        assertThat(names).containsExactly("beacon", "fallback", "journal", "relic", "stub", "voucher");
    }

    @Test
    void theBodyIsWrittenByHandSoAHostsJsonSettingsCannotChangeIt() {
        ChartSeries series = new ChartSeries(7, List.of(new ChartSeries.Entity("a\"b", "<A> & 'B'", true, 1, false)),
                List.of(new ChartSeries.Day("2026-11-01", List.of(1L))), List.of("note\nwith\ttabs and \\ and  "));

        String text = ChartJson.json(series);

        assertThat(text).doesNotContain("<").doesNotContain(">").doesNotContain("&").doesNotContain("'")
                .doesNotContain("\n").doesNotContain(" ");
        JsonNode parsed = JSON.readTree(text);
        assertThat(parsed.get("entities").get(0).get("name").asString()).isEqualTo("a\"b");
        assertThat(parsed.get("entities").get(0).get("label").asString()).isEqualTo("<A> & 'B'");
        assertThat(parsed.get("notes").get(0).asString()).isEqualTo("note\nwith\ttabs and \\ and  ");
    }

    @Test
    void theOnlyMappingIsAGetOfApiStatsBelowTheConsole() throws NoSuchMethodException {
        Method method = StatsController.class.getMethod("stats", String.class, jakarta.servlet.http.HttpServletResponse.class);

        GetMapping get = method.getAnnotation(GetMapping.class);

        assertThat(get.value()).containsExactly("/api/stats");
        assertThat(StatsController.class.getAnnotation(RequestMapping.class)).as("relative mapping: the base path is added")
                .isNull();
        assertThat(method.getAnnotations()).hasSize(1);
        assertThat(List.of(StatsController.class.getDeclaredMethods())).filteredOn(m -> m.getAnnotations().length > 0)
                .hasSize(1);
    }
}
