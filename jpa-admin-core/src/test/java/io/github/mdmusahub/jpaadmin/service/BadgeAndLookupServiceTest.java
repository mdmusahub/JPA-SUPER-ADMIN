package io.github.mdmusahub.jpaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.AnnotationProblems;
import io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings;
import io.github.mdmusahub.jpaadmin.metadata.EntityScanner;
import io.github.mdmusahub.jpaadmin.testsupport.RecordingStatementInspector;
import io.github.mdmusahub.jpaadmin.testsupport.ToneTestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.tonemodel.Dial;
import io.github.mdmusahub.jpaadmin.testsupport.tonemodel.Gauge;
import io.github.mdmusahub.jpaadmin.testsupport.tonemodel.GaugeState;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import io.github.mdmusahub.jpaadmin.web.view.FormView;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Phase 13 against a real Hibernate and a real database (D-120 to D-122): the tone of a badge comes from the host and nowhere
 * else, a String with a badge is still searched and sorted, and the label of a relation is the property a lookup names when
 * that property is usable, and the rule of D-54 or the record's number when it is not. A sensitive property never becomes a
 * label.
 */
class BadgeAndLookupServiceTest {

    private static final AdminEntityRegistry REGISTRY = EntityScanner.scan(ToneTestPersistence.factory().getMetamodel(),
            DiscoverySettings.defaults());
    private static final AdminEntity GAUGE = REGISTRY.find("gauge").orElseThrow();

    private EntityManager entityManager;
    private ListQueryService lists;

    @BeforeEach
    void seed() {
        ToneTestPersistence.empty();
        ToneTestPersistence.inTransaction(em -> {
            Dial withSerial = dial(em, "Front dial", "SER-001", "NOTE-SECRET-1");
            Dial withoutSerial = dial(em, "Back dial", null, "NOTE-SECRET-2");
            gauge(em, GaugeState.OK, GaugeState.PENDING, "A", withSerial);
            gauge(em, GaugeState.LATE, GaugeState.OK, "B", withoutSerial);
            gauge(em, GaugeState.PENDING, GaugeState.LATE, "z", withSerial);
            gauge(em, GaugeState.IDLE, GaugeState.IDLE, "C", null);
            gauge(em, null, null, null, null);
        });
        entityManager = ToneTestPersistence.entityManager();
        lists = ListQueryService.over(entityManager, REGISTRY);
    }

    @AfterEach
    void close() {
        entityManager.close();
    }

    private static Dial dial(EntityManager em, String name, String serial, String secretNote) {
        Dial dial = new Dial();
        dial.setName(name);
        dial.setSerial(serial);
        dial.setSecretNote(secretNote);
        em.persist(dial);
        return dial;
    }

    private static void gauge(EntityManager em, GaugeState state, GaugeState phase, String grade, Dial dial) {
        Gauge gauge = new Gauge();
        gauge.setState(state);
        gauge.setPhase(phase);
        gauge.setGrade(grade);
        gauge.setLabel("label-" + grade);
        gauge.setDial(dial);
        gauge.setSpare(dial);
        gauge.setGhost(dial);
        gauge.setPlain(dial);
        em.persist(gauge);
    }

    private ListPage page(ListQuery query) {
        return lists.list(GAUGE, query);
    }

    private static int at(ListPage page, String key) {
        for (int i = 0; i < page.columns().size(); i++) {
            if (page.columns().get(i).key().equals(key)) {
                return i;
            }
        }
        throw new AssertionError("no column " + key);
    }

    /** The cells of one column in the order of the rows, the order of the ids when nothing is sorted. */
    private static List<Cell> column(ListPage page, String key) {
        int index = at(page, key);
        return page.rows().stream().map(row -> row.cells().get(index)).toList();
    }

    // ---- the registry the check reads ---------------------------------------------------------------------------------

    @Test
    void theScannerReadsTheAnnotationsAndTheCheckNamesExactlyTheTwoWrongLookups() {
        assertThat(GAUGE.visibleField("state").orElseThrow().badge()).isNotNull();
        assertThat(GAUGE.visibleField("phase").orElseThrow().badge()).isNull();
        assertThat(GAUGE.visibleField("dial").orElseThrow().lookup()).isEqualTo("serial");
        assertThat(GAUGE.visibleField("plain").orElseThrow().lookup()).isNull();

        List<String> problems = AnnotationProblems.of(REGISTRY);

        assertThat(problems).hasSize(2);
        assertThat(problems).anySatisfy(problem -> assertThat(problem).contains(Gauge.class.getName() + ".spare"));
        assertThat(problems).anySatisfy(problem -> assertThat(problem).contains(Gauge.class.getName() + ".ghost"));
        assertThat(problems).allSatisfy(problem -> assertThat(problem).doesNotContain("secretNote").doesNotContain("nope"));
    }

    @Test
    void thePluralTheHostGaveIsTheEntitysPluralAndTheGuessIsTheRest() {
        assertThat(REGISTRY.find("shelf").orElseThrow().plural()).isEqualTo("Shelves");
        assertThat(REGISTRY.find("gauge").orElseThrow().plural()).isEqualTo("Gauges");
        assertThat(REGISTRY.find("dial").orElseThrow().plural()).isEqualTo("Dials");
    }

    // ---- badges on an enum --------------------------------------------------------------------------------------------

    @Test
    void anEnumWithABadgeTakesItsTonesFromTheHostAndANamedToneIsTheOnlyWayToColour() {
        ListPage page = page(ListQuery.defaults());

        List<Cell> states = column(page, "state");

        assertThat(states).extracting(Cell::display, Cell::tone, Cell::type).containsExactly(
                org.assertj.core.groups.Tuple.tuple("Ok", Cell.Tone.GREEN, Cell.Type.ENUM),
                org.assertj.core.groups.Tuple.tuple("Late", Cell.Tone.RED, Cell.Type.ENUM),
                org.assertj.core.groups.Tuple.tuple("Pending", Cell.Tone.GREY, Cell.Type.ENUM),
                org.assertj.core.groups.Tuple.tuple("Idle", Cell.Tone.GREY, Cell.Type.ENUM),
                org.assertj.core.groups.Tuple.tuple("", Cell.Tone.NONE, Cell.Type.EMPTY));
    }

    @Test
    void aConstantTheBadgeLeavesOutIsGreyEvenWhenTheWordListWouldColourIt() {
        // PENDING is amber to the word list; the host listed the field, so the host decides: grey.
        assertThat(column(page(ListQuery.defaults()), "state").get(2)).extracting(Cell::display, Cell::tone)
                .containsExactly("Pending", Cell.Tone.GREY);
    }

    @Test
    void anEnumWithoutABadgeKeepsTheWordList() {
        List<Cell> phases = column(page(ListQuery.defaults()), "phase");

        assertThat(phases).extracting(Cell::display, Cell::tone).containsExactly(
                org.assertj.core.groups.Tuple.tuple("Pending", Cell.Tone.AMBER),
                org.assertj.core.groups.Tuple.tuple("Ok", Cell.Tone.GREY),
                org.assertj.core.groups.Tuple.tuple("Late", Cell.Tone.GREY),
                org.assertj.core.groups.Tuple.tuple("Idle", Cell.Tone.GREY),
                org.assertj.core.groups.Tuple.tuple("", Cell.Tone.NONE));
    }

    // ---- badges on a String -------------------------------------------------------------------------------------------

    @Test
    void aStringWithABadgeIsAnEnumColumnShownAsStoredWithTheHostsToneAndGreyForTheRest() {
        ListPage page = page(ListQuery.defaults());

        assertThat(page.columns().get(at(page, "grade")).type()).isEqualTo(Cell.Type.ENUM);
        assertThat(column(page, "grade")).extracting(Cell::display, Cell::tone, Cell::type).containsExactly(
                org.assertj.core.groups.Tuple.tuple("A", Cell.Tone.GREEN, Cell.Type.ENUM),
                org.assertj.core.groups.Tuple.tuple("B", Cell.Tone.AMBER, Cell.Type.ENUM),
                org.assertj.core.groups.Tuple.tuple("z", Cell.Tone.GREY, Cell.Type.ENUM),
                org.assertj.core.groups.Tuple.tuple("C", Cell.Tone.RED, Cell.Type.ENUM),
                org.assertj.core.groups.Tuple.tuple("", Cell.Tone.NONE, Cell.Type.EMPTY));
    }

    @Test
    void aStringBadgeIsStillSortableAndStillSearched() {
        ListPage sorted = page(new ListQuery("", "grade", "asc", 0, 25));
        ListPage found = page(new ListQuery("label-b", null, null, 0, 25));

        assertThat(sorted.columns().get(at(sorted, "grade")).sortable()).isTrue();
        assertThat(column(sorted, "grade")).extracting(Cell::display).containsExactly("A", "B", "C", "z", "");
        assertThat(found.total()).isEqualTo(1);
        assertThat(found.searched()).contains("Grade", "Label");
        assertThat(page(new ListQuery("z", null, null, 0, 25)).total()).as("the badge text itself is searched").isEqualTo(1);
    }

    @Test
    void theValueIsComparedAsStoredSoAnotherCaseGetsTheGreyBadge() {
        // "z" is stored, "Z" is not listed, "A" is: only an exact match takes a tone.
        assertThat(column(page(ListQuery.defaults()), "grade").get(2).tone()).isEqualTo(Cell.Tone.GREY);
    }

    // ---- the detail page ----------------------------------------------------------------------------------------------

    @Test
    void theDetailPageShowsTheSameBadges() {
        DetailQueryService details = DetailQueryService.over(entityManager, REGISTRY);
        Long first = firstId();

        DetailRecord record = details.find(GAUGE, String.valueOf(first)).orElseThrow();

        assertThat(record.items()).filteredOn(item -> item.label().equals("State")).singleElement()
                .extracting(item -> item.cell().tone()).isEqualTo(Cell.Tone.GREEN);
        assertThat(record.items()).filteredOn(item -> item.label().equals("Grade")).singleElement()
                .extracting(item -> item.cell().type(), item -> item.cell().tone()).containsExactly(Cell.Type.ENUM, Cell.Tone.GREEN);
        assertThat(record.items()).filteredOn(item -> item.label().equals("Phase")).singleElement()
                .extracting(item -> item.cell().tone()).isEqualTo(Cell.Tone.AMBER);
    }

    private Long firstId() {
        return entityManager.createQuery("select min(g.id) from Gauge g", Long.class).getSingleResult();
    }

    // ---- lookups ------------------------------------------------------------------------------------------------------

    @Test
    void aLookupNamesTheRelationsLabelAndAnEmptyValueShowsTheRecordsNumber() {
        ListPage page = page(ListQuery.defaults());

        List<Cell> dials = column(page, "dial");

        assertThat(dials.get(0)).extracting(Cell::display, Cell::type).containsExactly("SER-001", Cell.Type.RELATION);
        assertThat(dials.get(1).display()).as("the dial has no serial: its number").startsWith("#");
        assertThat(dials.get(2).display()).isEqualTo("SER-001");
        assertThat(dials.get(3).type()).as("no dial at all").isEqualTo(Cell.Type.EMPTY);
    }

    @Test
    void withoutALookupTheRuleOfTheNameApplies() {
        assertThat(column(page(ListQuery.defaults()), "plain")).extracting(Cell::display)
                .containsExactly("Front dial", "Back dial", "Front dial", "", "");
    }

    @Test
    void aLookupThatNamesASensitivePropertyIsNotUsedAndTheValueNeverAppears() {
        ListPage page = page(ListQuery.defaults());

        assertThat(column(page, "spare")).extracting(Cell::display).containsExactly("Front dial", "Back dial", "Front dial", "", "");
        assertThat(page.rows().toString()).doesNotContain("NOTE-SECRET");
    }

    @Test
    void aLookupThatNamesNothingFallsBackToTheRuleOfTheName() {
        assertThat(column(page(ListQuery.defaults()), "ghost")).extracting(Cell::display)
                .containsExactly("Front dial", "Back dial", "Front dial", "", "");
    }

    @Test
    void aRelationSortsByTheLookupLabelAndItsFilterListsThatLabel() {
        ListPage sorted = page(new ListQuery("", "dial", "asc", 0, 25));
        ListPage plain = page(ListQuery.defaults());

        assertThat(sorted.columns().get(at(sorted, "dial")).sortable()).isTrue();
        assertThat(column(sorted, "dial").stream().map(Cell::display).limit(2).collect(Collectors.toList()))
                .containsExactly("SER-001", "SER-001");
        FilterControl control = plain.controls().stream().filter(candidate -> candidate.key().equals("dial")).findFirst()
                .orElseThrow();
        assertThat(control.options()).extracting(FormView.Option::label).as("a dial without a serial has no label to list")
                .contains("SER-001");
        assertThat(plain.controls().stream().filter(candidate -> candidate.key().equals("plain")).findFirst().orElseThrow()
                .options()).extracting(FormView.Option::label).containsExactlyInAnyOrder("Front dial", "Back dial");
    }

    @Test
    void theFormsChoiceListUsesTheLookupLabelToo() {
        FormQueryService forms = FormQueryService.over(entityManager, REGISTRY);

        FormView.Field dial = forms.blank(GAUGE).fields().stream().filter(field -> field.key().equals("dial")).findFirst()
                .orElseThrow();
        FormView.Field plain = forms.blank(GAUGE).fields().stream().filter(field -> field.key().equals("plain")).findFirst()
                .orElseThrow();

        assertThat(dial.options()).extracting(FormView.Option::label).contains("SER-001");
        assertThat(dial.options()).extracting(FormView.Option::label).doesNotContain("Front dial");
        assertThat(plain.options()).extracting(FormView.Option::label).contains("Front dial", "Back dial");
        assertThat(forms.blank(GAUGE).fields().toString()).doesNotContain("NOTE-SECRET");
    }

    @Test
    void theRelationCellsOfTheListHaveNoSqlThatSelectsTheSensitiveProperty() {
        // The label is selected through the join; the sensitive property must not be in any statement of a page.
        RecordingStatementInspector.clear();

        lists.list(GAUGE, ListQuery.defaults());

        assertThat(RecordingStatementInspector.statements().toString().toLowerCase())
                .doesNotContain("secret_note").doesNotContain("secretnote");
    }
}
