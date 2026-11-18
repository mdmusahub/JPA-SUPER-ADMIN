package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.sample.domain.Category;
import io.github.mdmusahub.jpaadmin.sample.domain.Customer;
import io.github.mdmusahub.jpaadmin.sample.domain.Order;
import io.github.mdmusahub.jpaadmin.sample.domain.Product;
import io.github.mdmusahub.jpaadmin.sample.domain.Tag;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 5, item 3: the sort proof on real data (D-60). Every sortable column of the five sample entities, in both
 * directions, with and without a search, through the real flow (sign in, open the list page). The two subclasses run it
 * with {@code spring.jpa.open-in-view} on and off.
 *
 * <p>The expected order does <em>not</em> come from the code under test. It is computed here, in Java, from the rows as
 * the database holds them: the same rule written a second time, with missing values last in both directions and the
 * identifier as the tie-breaker. Each assertion compares the identifiers of the returned rows, in order, with that
 * list, so a page that merely carried the right parameter, or sorted the wrong way, fails.
 *
 * <p>Per page the headers are checked too: exactly the sorted header says {@code ascending} or {@code descending} and
 * links to the other direction, every other sortable header says {@code none} and links to ascending, and a header that
 * cannot be sorted has no {@code aria-sort} and no link.
 *
 * <p>The demo entity names appear here because this is the sample application, not a library module (ARCH-12).
 */
abstract class AbstractSortProofTest {

    /** A category with no description, so a nullable text column has a missing value (removed again after each test). */
    private static final String NULL_DESCRIPTION_CATEGORY = "TEST-NULL-DESCRIPTION";

    private static final Pattern HEADER = Pattern.compile("(?s)<th\\b(?![^>]*select-col)([^>]*)>(.*?)</th>");
    private static final Pattern ROW = Pattern.compile("(?s)<tr>(.*?)</tr>");
    // The checkbox column of bulk delete (Phase 7) is not a data cell: its cell and header are skipped.
    private static final Pattern CELL = Pattern.compile("(?s)<td\\b(?![^>]*select-col)[^>]*>(.*?)</td>");

    @Autowired
    protected MockMvc mvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MockBrowser browser;

    @BeforeEach
    void signInAndAddARowWithMissingValues() throws Exception {
        browser = MockBrowser.signedIn(mvc);
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> entityManager.persist(new Category(NULL_DESCRIPTION_CATEGORY, null)));
    }

    @AfterEach
    void removeTheRowAgain() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                entityManager.createQuery("delete from Category c where c.name = :name")
                        .setParameter("name", NULL_DESCRIPTION_CATEGORY).executeUpdate());
    }

    // ---- what each entity's list is expected to have ---------------------------------------------------------------

    /** One sortable column: its key in the address, its header text, and its value in a row, read from the entity. */
    private record Col(String key, String label, Function<Object, Object> value) {
    }

    /** One entity: its address, its sortable columns, the text it can be searched by, and a search that finds some. */
    private record Subject(String path, Class<?> type, Function<Object, Object> id, List<Col> columns,
                           List<Function<Object, String>> searchable, String search, List<String> unsortableHeaders) {
    }

    /** A row as the oracle sees it: the identifier, the value of each column and the texts a search looks in. */
    private record OracleRow(Long id, Map<String, Object> values, List<String> texts) {
    }

    @SuppressWarnings("unchecked")
    private static <E> Col col(String key, String label, Function<E, Object> value) {
        return new Col(key, label, entity -> value.apply((E) entity));
    }

    @SuppressWarnings("unchecked")
    private static <E> Function<Object, String> text(Function<E, String> value) {
        return entity -> value.apply((E) entity);
    }

    private static final List<Subject> SUBJECTS = List.of(
            new Subject("category", Category.class, entity -> ((Category) entity).getId(), List.of(
                    col("id", "ID", (Category c) -> c.getId()),
                    col("createdAt", "Created at", (Category c) -> c.getCreatedAt()),
                    col("description", "Description", (Category c) -> c.getDescription()),
                    col("image", "Image", (Category c) -> c.getImage()),
                    col("name", "Name", (Category c) -> c.getName())),
                    List.of(text((Category c) -> c.getName()), text((Category c) -> c.getDescription()),
                            text((Category c) -> c.getImage())), "s", List.of()),
            new Subject("customer", Customer.class, entity -> ((Customer) entity).getId(), List.of(
                    col("id", "ID", (Customer c) -> c.getId()),
                    col("active", "Active", (Customer c) -> c.isActive()),
                    col("createdAt", "Created at", (Customer c) -> c.getCreatedAt()),
                    col("email", "Email", (Customer c) -> c.getEmail()),
                    col("fullName", "Full name", (Customer c) -> c.getFullName())),
                    List.of(text((Customer c) -> c.getEmail()), text((Customer c) -> c.getFullName())), "an",
                    List.of("Password")),
            new Subject("order", Order.class, entity -> ((Order) entity).getId(), List.of(
                    col("id", "ID", (Order o) -> o.getId()),
                    col("createdAt", "Created at", (Order o) -> o.getCreatedAt()),
                    // The related record's label, not its identifier: Customer's label is its full name (D-54, D-60).
                    col("customer", "Customer", (Order o) -> o.getCustomer().getFullName()),
                    col("note", "Note", (Order o) -> o.getNote()),
                    col("status", "Status", (Order o) -> o.getStatus().name()),
                    col("total", "Total", (Order o) -> o.getTotal())),
                    List.of(text((Order o) -> o.getNote())), "gift", List.of()),
            new Subject("product", Product.class, entity -> ((Product) entity).getId(), List.of(
                    col("id", "ID", (Product p) -> p.getId()),
                    col("category", "Category", (Product p) -> p.getCategory().getName()),
                    col("createdAt", "Created at", (Product p) -> p.getCreatedAt()),
                    col("featured", "Featured", (Product p) -> p.isFeatured()),
                    col("name", "Name", (Product p) -> p.getName()),
                    col("price", "Price", (Product p) -> p.getPrice()),
                    col("sku", "Sku", (Product p) -> p.getSku()),
                    col("status", "Status", (Product p) -> p.getStatus().name()),
                    col("stock", "Stock", (Product p) -> p.getStock())),
                    List.of(text((Product p) -> p.getName()), text((Product p) -> p.getSku())), "o", List.of()),
            new Subject("tag", Tag.class, entity -> ((Tag) entity).getId(), List.of(
                    col("id", "ID", (Tag t) -> t.getId()),
                    col("name", "Name", (Tag t) -> t.getName())),
                    List.of(text((Tag t) -> t.getName())), "e", List.of()));

    static Stream<Arguments> everySortableColumnBothWaysWithAndWithoutASearch() {
        List<Arguments> cases = new ArrayList<>();
        for (Subject subject : SUBJECTS) {
            for (Col column : subject.columns()) {
                for (String dir : List.of("asc", "desc")) {
                    for (boolean searched : new boolean[] {false, true}) {
                        cases.add(Arguments.of(subject.path(), column.key(), dir, searched));
                    }
                }
            }
        }
        return cases.stream();
    }

    private static Subject subject(String path) {
        return SUBJECTS.stream().filter(candidate -> candidate.path().equals(path)).findFirst().orElseThrow();
    }

    // ---- the oracle -------------------------------------------------------------------------------------------

    /** Every row of the entity as the database holds it, read inside a transaction so lazy relations can be read. */
    private List<OracleRow> rowsOf(Subject subject) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            CriteriaQuery<Object> query = entityManager.getCriteriaBuilder().createQuery(Object.class);
            Root<?> root = query.from(subject.type());
            query.select(root);
            List<OracleRow> rows = new ArrayList<>();
            for (Object entity : entityManager.createQuery(query).getResultList()) {
                Map<String, Object> values = new LinkedHashMap<>();
                for (Col column : subject.columns()) {
                    values.put(column.key(), column.value().apply(entity));
                }
                List<String> texts = new ArrayList<>();
                for (Function<Object, String> searchable : subject.searchable()) {
                    texts.add(searchable.apply(entity));
                }
                rows.add(new OracleRow((Long) subject.id().apply(entity), values, texts));
            }
            return rows;
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compare(Object left, Object right, boolean descending) {
        if (left == null || right == null) {
            return left == right ? 0 : left == null ? 1 : -1; // missing values last, whichever way the column goes
        }
        int result = ((Comparable) left).compareTo(right);
        return descending ? -result : result;
    }

    /** The identifiers in the order the console must show them: the column, missing values last, then the id. */
    private List<Long> expectedIds(Subject subject, String key, boolean descending, String search) {
        return rowsOf(subject).stream()
                .filter(row -> search == null || row.texts().stream().anyMatch(
                        text -> text != null && text.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))))
                .sorted((a, b) -> {
                    int byColumn = compare(a.values().get(key), b.values().get(key), descending);
                    return byColumn != 0 ? byColumn : a.id().compareTo(b.id());
                })
                .map(OracleRow::id).toList();
    }

    // ---- reading a page ---------------------------------------------------------------------------------------

    private String page(String path) throws Exception {
        MockHttpServletResponse response = browser.getUri(path);
        assertThat(response.getStatus()).as(path).isEqualTo(200);
        return response.getContentAsString();
    }

    /** The first cell (the identifier) of every table row, in the order shown. */
    private static List<Long> shownIds(String html) {
        List<Long> ids = new ArrayList<>();
        Matcher body = Pattern.compile("(?s)<tbody>(.*?)</tbody>").matcher(html);
        if (!body.find()) {
            return ids;
        }
        Matcher row = ROW.matcher(body.group(1));
        while (row.find()) {
            Matcher cell = CELL.matcher(row.group(1));
            assertThat(cell.find()).isTrue();
            ids.add(Long.valueOf(cell.group(1).replaceAll("(?s)<[^>]*>", "").strip()));
        }
        return ids;
    }

    /** The text of one column of every row. */
    private static List<String> shownColumn(String html, int index) {
        List<String> values = new ArrayList<>();
        Matcher body = Pattern.compile("(?s)<tbody>(.*?)</tbody>").matcher(html);
        assertThat(body.find()).isTrue();
        Matcher row = ROW.matcher(body.group(1));
        while (row.find()) {
            List<String> cells = new ArrayList<>();
            Matcher cell = CELL.matcher(row.group(1));
            while (cell.find()) {
                cells.add(cell.group(1).replaceAll("(?s)<[^>]*>", "").strip());
            }
            values.add(cells.get(index));
        }
        return values;
    }

    /** One column header as the page shows it. */
    private record Header(String label, String ariaSort, String href) {
    }

    private static List<Header> headers(String html) {
        List<Header> headers = new ArrayList<>();
        Matcher header = HEADER.matcher(html);
        while (header.find()) {
            if (!header.group(1).contains("scope=\"col\"")) {
                continue;
            }
            Matcher aria = Pattern.compile("aria-sort=\"([a-z]+)\"").matcher(header.group(1));
            Matcher href = Pattern.compile("(?s)<a\\b[^>]*href=\"([^\"]*)\"").matcher(header.group(2));
            headers.add(new Header(header.group(2).replaceAll("(?s)<[^>]*>", "").replaceAll("\\s+", " ").strip(),
                    aria.find() ? aria.group(1) : null, href.find() ? href.group(1).replace("&amp;", "&") : null));
        }
        return headers;
    }

    // ---- the proof --------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0} sorted by {1} {2}, search {3}")
    @MethodSource("everySortableColumnBothWaysWithAndWithoutASearch")
    void everySortableColumnSortsTheRealRowsInTheRightOrderAndItsHeaderSaysSo(String path, String key, String dir,
                                                                                boolean searched) throws Exception {
        Subject subject = subject(path);
        String search = searched ? subject.search() : null;
        String address = "/admin/" + path + "?" + (searched ? "q=" + search + "&" : "") + "sort=" + key + "&dir=" + dir
                + "&size=100";

        String html = page(address);

        List<Long> expected = expectedIds(subject, key, dir.equals("desc"), search);
        if (searched) {
            assertThat(expected).as("the search must find some rows but not all, or it proves nothing")
                    .isNotEmpty().hasSizeLessThan(expectedIds(subject, key, dir.equals("desc"), null).size());
        }
        assertThat(shownIds(html)).as(address + ": the rows, in order").isEqualTo(expected);
        // The same sort read ten rows at a time must give the same rows in the same order: this is what the identifier
        // tie-breaker is for. On one big page a database tends to return equal values in insertion order whatever the
        // query says, so only a small page makes the tie-breaker visible.
        List<Long> paged = new ArrayList<>();
        for (int pageNumber = 0; pageNumber < (expected.size() + 9) / 10; pageNumber++) {
            paged.addAll(shownIds(page(address.replace("size=100", "size=10") + "&page=" + pageNumber)));
        }
        assertThat(paged).as(address + ": read ten rows at a time").isEqualTo(expected);
        assertThat(html).contains(expected.size() + (expected.size() == 1 ? " record<" : " records<"));

        List<Header> headers = headers(html);
        List<String> labels = new ArrayList<>(subject.columns().stream().map(Col::label).toList());
        labels.addAll(subject.unsortableHeaders());
        assertThat(headers.stream().map(Header::label).toList()).as("the columns, in order of the page")
                .containsExactlyInAnyOrderElementsOf(labels);

        String base = "/admin/" + path + "?" + (searched ? "q=" + search + "&" : "");
        for (Col column : subject.columns()) {
            Header header = headers.stream().filter(candidate -> candidate.label().equals(column.label())).findFirst()
                    .orElseThrow();
            if (column.key().equals(key)) {
                assertThat(header.ariaSort()).as(address + ": " + column.label())
                        .isEqualTo(dir.equals("asc") ? "ascending" : "descending");
                assertThat(header.href()).as("a link to the other direction")
                        .isEqualTo(base + "sort=" + key + "&dir=" + (dir.equals("asc") ? "desc" : "asc") + "&size=100");
            } else {
                assertThat(header.ariaSort()).as(address + ": " + column.label()).isEqualTo("none");
                assertThat(header.href()).as("an unsorted header links to ascending")
                        .isEqualTo(base + "sort=" + column.key() + "&dir=asc&size=100");
            }
        }
        for (String label : subject.unsortableHeaders()) {
            Header header = headers.stream().filter(candidate -> candidate.label().equals(label)).findFirst().orElseThrow();
            assertThat(header.ariaSort()).as(label + " cannot be sorted, so it has no aria-sort").isNull();
            assertThat(header.href()).as(label + " has no sort link").isNull();
        }
        assertThat(count(html, "aria-sort=\"ascending\"") + count(html, "aria-sort=\"descending\""))
                .as("exactly one header is sorted").isEqualTo(1);
    }

    @Test
    void withNoSortEveryHeaderOfEveryListSaysNoneAndTheUnsortableOnesSayNothing() throws Exception {
        for (Subject subject : SUBJECTS) {
            String html = page("/admin/" + subject.path());

            assertThat(shownIds(html)).as(subject.path()).isEqualTo(
                    rowsOf(subject).stream().map(OracleRow::id).sorted().limit(25).toList());
            for (Header header : headers(html)) {
                if (subject.unsortableHeaders().contains(header.label())) {
                    assertThat(header.ariaSort()).as(subject.path() + " " + header.label()).isNull();
                    assertThat(header.href()).isNull();
                } else {
                    assertThat(header.ariaSort()).as(subject.path() + " " + header.label()).isEqualTo("none");
                    assertThat(header.href()).as(subject.path() + " " + header.label()).endsWith("&dir=asc");
                }
            }
            assertThat(html).doesNotContain("aria-sort=\"ascending\"", "aria-sort=\"descending\"");
        }
    }

    @Test
    void sortingByARelationOrdersByTheRelatedRecordsLabelAcrossPages() throws Exception {
        for (String[] sort : new String[][] {{"product", "category"}, {"order", "customer"}}) {
            Subject subject = subject(sort[0]);
            for (String dir : List.of("asc", "desc")) {
                List<Long> expected = expectedIds(subject, sort[1], dir.equals("desc"), null);
                List<Long> shown = new ArrayList<>();
                // A page past the end is the last page (D-51), so read exactly the pages that exist.
                for (int page = 0; page < (expected.size() + 9) / 10; page++) {
                    shown.addAll(shownIds(page("/admin/" + sort[0] + "?sort=" + sort[1] + "&dir=" + dir + "&size=10&page="
                            + page)));
                }
                assertThat(shown).as(sort[0] + " by " + sort[1] + " " + dir + ", read ten rows at a time")
                        .isEqualTo(expected).doesNotHaveDuplicates();
            }
        }
        // The label is what is shown, in that order, not the identifier of the related record.
        List<String> categories = shownColumn(page("/admin/product?sort=category&dir=asc&size=100"), 1);
        assertThat(categories).isSorted();
        assertThat(categories.get(0)).isEqualTo("Books");
        List<String> customers = shownColumn(page("/admin/order?sort=customer&dir=desc&size=100"), 2);
        assertThat(customers).isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(customers.get(0)).isEqualTo("Liam Gray");
    }

    @Test
    void missingValuesAreLastInBothDirectionsAndTheyKeepIdentifierOrder() throws Exception {
        // Order.note is nullable: 8 of the 30 demo orders have one. Category.description has one missing value here.
        for (String dir : List.of("asc", "desc")) {
            String orders = page("/admin/order?sort=note&dir=" + dir + "&size=100");
            List<String> notes = shownColumn(orders, 3);

            assertThat(notes).hasSize(30);
            assertThat(notes.subList(0, 8)).as("note " + dir + ": the eight orders with a note come first")
                    .allSatisfy(note -> assertThat(note).isEqualTo("Gift wrap please"));
            assertThat(notes.subList(8, 30)).as("note " + dir + ": the 22 with none come last")
                    .allSatisfy(note -> assertThat(note).doesNotContain("Gift"));
            List<Long> missing = shownIds(orders).subList(8, 30);
            assertThat(missing).as("note " + dir + ": in the nulls the identifier order holds").isSorted();

            List<String> descriptions = shownColumn(page("/admin/category?sort=description&dir=" + dir + "&size=100"), 2);
            assertThat(descriptions).hasSize(7);
            assertThat(descriptions.get(6)).as("description " + dir + ": the category without one is last")
                    .doesNotContain("Demo category");
            assertThat(descriptions.subList(0, 6)).allSatisfy(d -> assertThat(d).startsWith("Demo category"));
        }
    }

    @Test
    void noSensitiveColumnIsSortableOnAnyPageAndItsKeyChangesNothing() throws Exception {
        String plain = page("/admin/customer?size=100");
        for (String query : List.of("sort=password", "sort=password&dir=asc", "sort=password&dir=desc",
                "q=an&sort=password&dir=desc", "sort=Password", "sort=PASSWORD&dir=asc")) {
            String html = page("/admin/customer?" + query + "&size=100");

            assertThat(headers(html).stream().filter(header -> header.label().equals("Password")).findFirst().orElseThrow())
                    .as(query).isEqualTo(new Header("Password", null, null));
            assertThat(html).as(query).doesNotContain("sort=password", "sort=Password", "aria-sort=\"ascending\"",
                    "aria-sort=\"descending\"");
            assertThat(html).doesNotContain(Customer.DEMO_PASSWORD_PLACEHOLDER);
            if (!query.contains("q=")) {
                assertThat(shownIds(html)).as(query + ": the list is in identifier order").isEqualTo(shownIds(plain));
            }
        }
        // Every column of every entity that is masked is a header without aria-sort and without a link.
        for (Subject subject : SUBJECTS) {
            for (String label : subject.unsortableHeaders()) {
                assertThat(headers(page("/admin/" + subject.path())).stream().filter(h -> h.label().equals(label)))
                        .hasSize(1).allSatisfy(header -> assertThat(header).isEqualTo(new Header(label, null, null)));
            }
        }
    }

    @Test
    void aKeyThatIsNotAColumnIsIgnoredWithOrWithoutASearch() throws Exception {
        for (String key : List.of("tags", "nosuch", "password", "category.name", "' or 1=1 --")) {
            String encoded = key.replace(" ", "%20").replace("'", "%27");
            String html = page("/admin/product?sort=" + encoded + "&dir=desc&size=100");

            assertThat(html).as(key).doesNotContain("aria-sort=\"ascending\"", "aria-sort=\"descending\"");
            assertThat(shownIds(html)).as(key).isSorted();
            String searched = page("/admin/product?q=o&sort=" + encoded + "&dir=desc&size=100");
            assertThat(searched).as(key).doesNotContain("aria-sort=\"ascending\"", "aria-sort=\"descending\"");
        }
    }

    // ---- what the search covers and the cut notice (item 4) ---------------------------------------------------

    @Test
    void anEmptySearchNamesTheTextColumnsThatWereSearchedAndNeverAMaskedOne() throws Exception {
        Map<String, String> expected = Map.of(
                "product", "Name, Sku",
                "category", "Description, Image, Name",
                "customer", "Email, Full name",
                "order", "Note",
                "tag", "Name");

        for (Map.Entry<String, String> entity : expected.entrySet()) {
            String html = page("/admin/" + entity.getKey() + "?q=zzzz-nothing-has-this");

            assertThat(html).as(entity.getKey()).contains("No matching records",
                    "The search looked in these text columns: " + entity.getValue() + ".");
            assertThat(html).as(entity.getKey()).doesNotContain("<table");
        }
        String customers = page("/admin/customer?q=zzzz-nothing-has-this");
        assertThat(customers).as("the sensitive Password column is not named in any message").doesNotContain("Password");
        // Since Phase 6 the page also holds the filter panel, whose controls are named after the number, enum and relation
        // columns on purpose. The rule is about the sentence that says what the search looked in, so it is read there.
        String empty = page("/admin/product?q=zzzz");
        String message = empty.substring(empty.indexOf("class=\"card empty-state\""), empty.indexOf("</section>", empty.indexOf("class=\"card empty-state\"")));
        assertThat(message).as("numbers, enums and relations are not searched, so not named")
                .doesNotContain("Price", "Stock", "Status", "Category,", "Featured");
    }

    @Test
    void aSearchThatFindsRowsSaysNothingAboutScope() throws Exception {
        assertThat(page("/admin/product?q=phone")).doesNotContain("The search looked in");
    }

    @Test
    void aSearchOfMoreThanTheLimitSaysItWasCutAndTheLimit() throws Exception {
        String html = page("/admin/product?q=" + "x".repeat(101));

        assertThat(html).contains("Your search was longer than 100 characters, so only the first 100 were used.");
        assertThat(html).contains("class=\"toasts\"", "role=\"status\"");
        assertThat(html).contains("value=\"" + "x".repeat(100) + "\"").doesNotContain("x".repeat(101));
        assertThat(html).as("the notice repeats nothing that was typed").doesNotContain("xxxx</");
    }

    @Test
    void aSearchOfExactlyTheLimitOrLessSaysNothingAboutBeingCut() throws Exception {
        for (String q : List.of("", "phone", "x".repeat(100), "%20".repeat(300) + "phone")) {
            String html = page("/admin/product?q=" + q);

            assertThat(html).as("q=" + q.length() + " chars").doesNotContain("Your search was longer than")
                    .doesNotContain("class=\"toasts\"");
        }
    }

    @Test
    void spacesAroundTheTextDoNotCountButRealTextBeyondTheLimitDoes() throws Exception {
        // The padding is trimmed; what is left is 101 characters, so it is cut.
        String html = page("/admin/product?q=" + "%20".repeat(5) + "p".repeat(101));

        assertThat(html).contains("Your search was longer than 100 characters");
    }

    private static int count(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }

}
