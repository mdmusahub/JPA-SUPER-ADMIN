package io.github.mdmusahub.jpaadmin.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.mdmusahub.jpaadmin.metadata.testmodel.Widget;
import io.github.mdmusahub.jpaadmin.metadata.testmodel.WidgetStatus;
import io.github.mdmusahub.jpaadmin.testsupport.TestPersistence;
import jakarta.persistence.metamodel.Metamodel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

/**
 * The scanner against a real JPA metamodel (Hibernate over H2, {@code src/test/resources/META-INF/persistence.xml}).
 *
 * <p>The entities are the neutral test model, never the sample application's (ARCH-12). Every rule of Phase 3 has a
 * test here, and the negative side of each one: an excluded or hidden entity is not findable, a hidden field is not
 * visible, {@code passport} is not masked, and an entity the console cannot manage is not reachable.
 */
class EntityScannerTest {

    private static final String MODEL = "io.github.mdmusahub.jpaadmin.metadata.testmodel.";
    private static final String SPROCKET_A = "io-github-mdmusahub-jpaadmin-metadata-testmodel-collide-a-sprocket";
    private static final String SPROCKET_B = "io-github-mdmusahub-jpaadmin-metadata-testmodel-collide-b-sprocket";

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger scannerLog;

    private static Metamodel metamodel() {
        return TestPersistence.factory().getMetamodel();
    }

    private static AdminEntityRegistry scan() {
        return EntityScanner.scan(metamodel(), DiscoverySettings.defaults());
    }

    private static AdminEntityRegistry scanExcluding(String... excluded) {
        return EntityScanner.scan(metamodel(),
                new DiscoverySettings(List.of(excluded), DiscoverySettings.DEFAULT_SENSITIVE_FIELDS));
    }

    private static AdminField field(AdminEntityRegistry registry, String entity, String field) {
        AdminEntity found = registry.find(entity).orElseThrow();
        return found.fields().stream().filter(candidate -> candidate.name().equals(field)).findFirst().orElseThrow();
    }

    @BeforeEach
    void captureTheScannersLog() {
        scannerLog = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(EntityScanner.class);
        logged = new ListAppender<>();
        logged.start();
        scannerLog.addAppender(logged);
    }

    @AfterEach
    void releaseTheScannersLog() {
        scannerLog.detachAppender(logged);
    }

    private List<String> warnings() {
        return logged.list.stream().filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage).toList();
    }

    // ---- what is found ----------------------------------------------------------------------------------------

    @Test
    void everyManageableEntityIsFoundWithItsAddressLabelGroupAndIcon() {
        AdminEntityRegistry registry = scan();

        assertThat(registry.all().stream().map(AdminEntity::urlName))
                .containsExactlyInAnyOrder("widget", "widgetpart", "gadget", "gizmo", "thingamajig",
                        "httplogentry", SPROCKET_A, SPROCKET_B);
        AdminEntity widget = registry.find("widget").orElseThrow();
        assertThat(widget.label()).isEqualTo("Widget");
        assertThat(widget.group()).isEqualTo("Entities").isEqualTo(AdminEntity.DEFAULT_GROUP);
        assertThat(widget.icon()).isEqualTo("table").isEqualTo(AdminEntity.DEFAULT_ICON);
        assertThat(widget.idAttribute()).isEqualTo("id");
        assertThat(widget.entityName()).isEqualTo("Widget");
        assertThat(widget.className()).isEqualTo(Widget.class.getName());
        assertThat(widget.supported()).isTrue();
        assertThat(widget.unsupportedReason()).isNull();
        assertThat(registry.find("httplogentry").orElseThrow().label()).isEqualTo("Http log entry");
        assertThat(registry.find("widgetpart").orElseThrow().label()).isEqualTo("Widget part");
    }

    @Test
    void theOrderIsStableAndTheSameOnEveryScan() {
        List<String> first = scan().all().stream().map(AdminEntity::urlName).toList();
        List<String> second = scan().all().stream().map(AdminEntity::urlName).toList();

        assertThat(first).isEqualTo(second);
        // Group, then label, then class name: with one group this is the labels in alphabetical order.
        assertThat(first).isSorted();
    }

    @Test
    void theMetadataCannotBeChangedAfterwards() {
        AdminEntity widget = scan().find("widget").orElseThrow();

        assertThatThrownBy(() -> widget.fields().add(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> widget.visibleFields().add(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> scan().all().add(widget)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> field(scan(), "widget", "status").enumValues().add("X"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ---- hidden and excluded ----------------------------------------------------------------------------------

    @Test
    void anEntityAnnotatedAdminHiddenIsNotInTheConsoleAtAll() {
        AdminEntityRegistry registry = scan();

        assertThat(registry.find("doohickey")).isEmpty();
        assertThat(registry.all().stream().map(AdminEntity::className)).noneMatch(name -> name.endsWith("Doohickey"));
        assertThat(registry.unsupported().stream().map(AdminEntity::className))
                .as("a hidden entity is not even in the unsupported list, so nothing reveals it")
                .noneMatch(name -> name.endsWith("Doohickey"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Thingamajig", "thingamajig", "THINGAMAJIG", MODEL + "Thingamajig",
            "io.github.mdmusahub.jpaadmin.metadata.testmodel.THINGAMAJIG"})
    void anExcludedEntityIsNotInTheConsoleWhateverTheCaseAndWhicheverNameIsUsed(String entry) {
        AdminEntityRegistry registry = scanExcluding(entry);

        assertThat(registry.find("thingamajig")).isEmpty();
        assertThat(registry.all().stream().map(AdminEntity::urlName)).doesNotContain("thingamajig");
    }

    @Test
    void withoutTheExclusionTheSameEntityIsThere() {
        assertThat(scan().find("thingamajig")).isPresent();
    }

    @Test
    void anExcludedNameThatMatchesNothingChangesNothing() {
        AdminEntityRegistry registry = scanExcluding("NoSuchEntity", "com.example.Missing");

        assertThat(registry.all()).hasSize(8);
    }

    @Test
    void severalEntitiesCanBeExcludedAtOnce() {
        AdminEntityRegistry registry = scanExcluding("Thingamajig", MODEL + "Gizmo");

        assertThat(registry.find("thingamajig")).isEmpty();
        assertThat(registry.find("gizmo")).isEmpty();
        assertThat(registry.find("widget")).isPresent();
    }

    @Test
    void aFieldAnnotatedAdminHiddenIsNotVisibleAndCannotBeLookedUp() {
        AdminEntityRegistry registry = scan();
        AdminEntity widget = registry.find("widget").orElseThrow();

        assertThat(field(registry, "widget", "internalNote").hidden()).isTrue();
        assertThat(widget.visibleFields().stream().map(AdminField::name)).doesNotContain("internalNote");
        assertThat(widget.visibleField("internalNote")).isEmpty();
        assertThat(widget.visibleField("name")).isPresent();
        assertThat(widget.visibleField("noSuchField"))
                .as("a hidden field and a field that does not exist answer the same")
                .isEqualTo(widget.visibleField("internalNote"));
    }

    @Test
    void theIdentifierIsNeverHiddenEvenWhenItIsAnnotatedAndTheConsoleSaysSoOnce() {
        AdminEntityRegistry registry = scan();

        assertThat(field(registry, "gizmo", "id").hidden()).isFalse();
        assertThat(registry.find("gizmo").orElseThrow().visibleField("id")).isPresent();
        assertThat(warnings()).anyMatch(message -> message.contains("@AdminHidden on the identifier")
                && message.contains("Gizmo"));
    }

    // ---- field kinds ------------------------------------------------------------------------------------------

    @Test
    void everyFieldKindIsRecognised() {
        AdminEntityRegistry registry = scan();

        assertThat(field(registry, "widget", "name").kind()).isEqualTo(AdminField.Kind.TEXT);
        assertThat(field(registry, "widget", "price").kind()).isEqualTo(AdminField.Kind.NUMBER);
        assertThat(field(registry, "widget", "quantity").kind()).isEqualTo(AdminField.Kind.NUMBER);
        assertThat(field(registry, "widget", "active").kind()).isEqualTo(AdminField.Kind.BOOLEAN);
        assertThat(field(registry, "widget", "status").kind()).isEqualTo(AdminField.Kind.ENUM);
        assertThat(field(registry, "widget", "releasedOn").kind()).isEqualTo(AdminField.Kind.DATE);
        assertThat(field(registry, "widget", "createdAt").kind()).isEqualTo(AdminField.Kind.DATETIME);
        assertThat(field(registry, "widget", "drawing").kind()).isEqualTo(AdminField.Kind.BINARY);
        assertThat(field(registry, "widget", "address").kind()).isEqualTo(AdminField.Kind.EMBEDDED);
        assertThat(field(registry, "widget", "gadget").kind()).isEqualTo(AdminField.Kind.TO_ONE);
        assertThat(field(registry, "widget", "parts").kind()).isEqualTo(AdminField.Kind.TO_MANY);
    }

    @Test
    void theJavaTypeOfEachFieldIsKept() {
        AdminEntityRegistry registry = scan();

        assertThat(field(registry, "widget", "name").javaType()).isEqualTo(String.class);
        assertThat(field(registry, "widget", "price").javaType()).isEqualTo(BigDecimal.class);
        assertThat(field(registry, "widget", "releasedOn").javaType()).isEqualTo(LocalDate.class);
        assertThat(field(registry, "widget", "createdAt").javaType()).isEqualTo(LocalDateTime.class);
        assertThat(field(registry, "widget", "status").javaType()).isEqualTo(WidgetStatus.class);
    }

    @Test
    void anEnumFieldCarriesItsConstantsInDeclarationOrderAndNothingElseDoes() {
        AdminEntityRegistry registry = scan();

        assertThat(field(registry, "widget", "status").enumValues()).containsExactly("DRAFT", "ACTIVE", "RETIRED");
        assertThat(field(registry, "widget", "name").enumValues()).isEmpty();
    }

    @Test
    void aRelationFieldNamesTheEntityItPointsAt() {
        AdminEntityRegistry registry = scan();

        assertThat(field(registry, "widget", "gadget").targetEntity()).isEqualTo("gadget");
        assertThat(field(registry, "widget", "parts").targetEntity()).isEqualTo("widgetpart");
        assertThat(field(registry, "widgetpart", "widget").targetEntity()).isEqualTo("widget");
        assertThat(field(registry, "widget", "name").targetEntity()).isNull();
    }

    @Test
    void theIdentifierAndTheVersionAreMarkedAndTheIdentifierComesFirst() {
        AdminEntityRegistry registry = scan();
        AdminEntity widget = registry.find("widget").orElseThrow();

        assertThat(widget.fields().get(0).name()).isEqualTo("id");
        assertThat(field(registry, "widget", "id").id()).isTrue();
        assertThat(field(registry, "widget", "id").nullable()).isFalse();
        assertThat(field(registry, "widget", "revision").version()).isTrue();
        assertThat(field(registry, "widget", "name").id()).isFalse();
        assertThat(field(registry, "widget", "name").version()).isFalse();
        assertThat(widget.idField().orElseThrow().name()).isEqualTo("id");
    }

    @Test
    void aNotNullColumnIsRequiredAndANullableOneIsNot() {
        AdminEntityRegistry registry = scan();

        assertThat(field(registry, "widget", "name").nullable()).isFalse();
        assertThat(field(registry, "widget", "name").required()).isTrue();
        assertThat(field(registry, "widget", "description").nullable()).isTrue();
        assertThat(field(registry, "widget", "description").required()).isFalse();
        assertThat(field(registry, "widget", "parts").required())
                .as("a collection is never something the form demands").isFalse();
    }

    @Test
    void everyFieldHasAHumanizedLabel() {
        AdminEntityRegistry registry = scan();

        assertThat(field(registry, "widget", "userPassword").label()).isEqualTo("User password");
        assertThat(field(registry, "widget", "createdAt").label()).isEqualTo("Created at");
        assertThat(field(registry, "httplogentry", "apiURL").label()).isEqualTo("Api URL");
        assertThat(field(registry, "httplogentry", "response_code").label()).isEqualTo("Response code");
    }

    // ---- sensitive fields -------------------------------------------------------------------------------------

    @Test
    void aFieldWhoseNameLooksLikeACredentialIsSensitiveAndOneThatOnlyLooksLikeOneIsNot() {
        AdminEntityRegistry registry = scan();

        assertThat(field(registry, "widget", "userPassword").sensitive()).isTrue();
        assertThat(field(registry, "widget", "api_key").sensitive()).isTrue();
        assertThat(field(registry, "widget", "passport").sensitive()).isFalse();
        assertThat(field(registry, "widget", "name").sensitive()).isFalse();
    }

    @Test
    void anAnnotatedFieldIsSensitiveWhateverItIsCalled() {
        assertThat(field(scan(), "widget", "recoveryAnswer").sensitive()).isTrue();
    }

    @Test
    void theConfiguredNamesDecideWhichFieldsAreMaskedByName() {
        AdminEntityRegistry registry = EntityScanner.scan(metamodel(),
                new DiscoverySettings(List.of(), List.of("passport")));

        assertThat(field(registry, "widget", "passport").sensitive()).isTrue();
        assertThat(field(registry, "widget", "userPassword").sensitive()).isFalse();
        assertThat(field(registry, "widget", "recoveryAnswer").sensitive())
                .as("the annotation adds to the list and is never taken off it").isTrue();
    }

    // ---- entities the console cannot manage -------------------------------------------------------------------

    @Test
    void anEntityWithACompositeKeyIsUnsupportedUnreachableAndLoggedOnce() {
        AdminEntityRegistry registry = scan();

        assertThat(registry.find("contraption")).isEmpty();
        assertThat(registry.find("whatsit")).isEmpty();
        assertThat(registry.unsupported().stream().map(AdminEntity::className))
                .containsExactly(MODEL + "Contraption", MODEL + "Whatsit");
        assertThat(registry.unsupported().stream().map(AdminEntity::unsupportedReason))
                .allMatch(reason -> reason.contains("composite primary key"));
        assertThat(registry.unsupported().get(0).unsupportedReason()).contains("@EmbeddedId");
        assertThat(registry.unsupported().get(1).unsupportedReason()).contains("@IdClass");
        assertThat(warnings().stream().filter(message -> message.contains("cannot manage"))).hasSize(1);
        assertThat(warnings()).anyMatch(message -> message.contains("Contraption") && message.contains("Whatsit"));
    }

    @Test
    void anUnsupportedEntityIsNeverInTheNavigationOrTheGroups() {
        AdminEntityRegistry registry = scan();

        assertThat(registry.all().stream().map(AdminEntity::className))
                .doesNotContain(MODEL + "Contraption", MODEL + "Whatsit");
        assertThat(registry.all()).allMatch(AdminEntity::supported);
        assertThat(registry.groups()).containsExactly("Entities");
    }

    @Test
    void theRegistryRefusesToHoldAnUnsupportedEntity() {
        AdminEntity broken = new AdminEntity(Widget.class, "Widget", "widget", "Widget", "Entities", "table", null,
                "a reason", List.of());

        assertThatThrownBy(() -> new AdminEntityRegistry(List.of(broken), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- address collisions -----------------------------------------------------------------------------------

    @Test
    void twoEntitiesWithTheSameSimpleNameBothMoveToTheirFullyQualifiedAddress() {
        AdminEntityRegistry registry = scan();

        assertThat(registry.find("sprocket")).as("neither keeps the short address").isEmpty();
        assertThat(registry.find(SPROCKET_A)).isPresent();
        assertThat(registry.find(SPROCKET_B)).isPresent();
        assertThat(registry.find(SPROCKET_A).orElseThrow().className())
                .isEqualTo(MODEL + "collide.a.Sprocket");
        assertThat(registry.find(SPROCKET_B).orElseThrow().className())
                .isEqualTo(MODEL + "collide.b.Sprocket");
        assertThat(registry.find(SPROCKET_A).orElseThrow().label()).isEqualTo("Sprocket");
    }

    @Test
    void anAddressIsNeverGivenToTwoEntities() {
        AdminEntityRegistry registry = scan();

        assertThat(registry.all().stream().map(AdminEntity::urlName)).doesNotHaveDuplicates();
    }

    @Test
    void excludingOneOfTwoCollidingEntitiesLeavesTheOtherWithTheShortAddress() {
        AdminEntityRegistry registry = scanExcluding(MODEL + "collide.b.Sprocket");

        assertThat(registry.find("sprocket")).isPresent();
        assertThat(registry.find("sprocket").orElseThrow().className()).isEqualTo(MODEL + "collide.a.Sprocket");
        assertThat(registry.find(SPROCKET_B)).isEmpty();
    }

    // ---- lookups ----------------------------------------------------------------------------------------------

    @Test
    void aLookupWithAnythingAVisitorCouldTypeAnswersEmptyAndNeverThrows() {
        AdminEntityRegistry registry = scan();

        for (String typed : List.of("", " ", "Widget", "WIDGET", "widget/", "/widget", "widget?x=1", "../widget",
                "wid get", "widget%20", "doohickey", "contraption", "whatsit", "sprocket", "internalNote")) {
            assertThat(registry.find(typed)).as(typed).isEmpty();
        }
        assertThat(registry.find(null)).isEmpty();
        assertThat(registry.find("widget")).isPresent();
    }

    @Test
    void anEmptyRegistryAnswersNothingAndIsEmpty() {
        assertThat(AdminEntityRegistry.empty().find("widget")).isEqualTo(Optional.empty());
        assertThat(AdminEntityRegistry.empty().all()).isEmpty();
        assertThat(AdminEntityRegistry.empty().isEmpty()).isTrue();
        assertThat(AdminEntityRegistry.empty().groups()).isEmpty();
    }

    @Test
    void anAddressThatCouldNotBeAPathIsRefusedByTheMetadataItself() {
        assertThatThrownBy(() -> new AdminEntity(Widget.class, "Widget", "Widget", "Widget", "Entities", "table",
                "id", null, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AdminEntity(Widget.class, "Widget", "wid get", "Widget", "Entities", "table",
                "id", null, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AdminEntity(Widget.class, "Widget", "widget/x", "Widget", "Entities", "table",
                "id", null, List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
