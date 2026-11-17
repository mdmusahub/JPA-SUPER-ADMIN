package com.example.bookstore;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bookstore.domain.Author;
import com.example.bookstore.domain.Book;
import com.example.bookstore.domain.Publisher;
import jakarta.persistence.EntityManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 7 in the second host: a project with different entities (books, authors, publishers) gets the detail page, the
 * relation links, delete and bulk delete for its own entities with no library change (ARCH-12, D-26). A book's author is a
 * link, the secret token is masked, a publisher that books still point at is kept with a friendly message, and a
 * publisher nobody uses can be deleted.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConsoleRecordsOfThisHostTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MockHttpSession session = new MockHttpSession();

    @BeforeEach
    void signIn() throws Exception {
        String loginPage = send(MockMvcRequestBuilders.get("/admin/login")).getContentAsString();
        send(MockMvcRequestBuilders.post("/admin/login").param("username", "check-admin")
                .param("password", "check-only-password-NOT-REAL-1").param("_csrf", token(loginPage)));
    }

    @AfterEach
    void removeTheRowsAgain() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.createQuery("delete from Book b where b.title like 'Disposable%'").executeUpdate();
            entityManager.createQuery("delete from Publisher p where p.name like 'Disposable%'").executeUpdate();
        });
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
        if (session.isInvalid()) {
            session = new MockHttpSession();
        }
        String id = session.getId();
        return mvc.perform(request.session(session).with(each -> {
            each.setRequestedSessionId(id);
            return each;
        })).andReturn().getResponse();
    }

    private static String token(String html) {
        Matcher token = Pattern.compile("name=\"_csrf\"[^>]*?value=\"([^\"]+)\"").matcher(html);
        assertThat(token.find()).isTrue();
        return token.group(1);
    }

    private String page(String path) throws Exception {
        MockHttpServletResponse page = send(MockMvcRequestBuilders.get(path));
        assertThat(page.getStatus()).as(path).isEqualTo(200);
        return page.getContentAsString();
    }

    private long newPublisher(String name) {
        long[] id = new long[1];
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Publisher publisher = new Publisher(name);
            entityManager.persist(publisher);
            entityManager.flush();
            id[0] = publisher.getId();
        });
        return id[0];
    }

    private long firstId(String entity) {
        return entityManager.createQuery("select e.id from " + entity + " e order by e.id", Long.class).setMaxResults(1)
                .getSingleResult();
    }

    private boolean exists(Class<?> type, long id) {
        return new TransactionTemplate(transactionManager).execute(status -> entityManager.find(type, id) != null);
    }

    private static String action(String html) {
        Matcher form = Pattern.compile("(?s)<form class=\"confirm-panel\"[^>]*?\\saction=\"([^\"]+)\"").matcher(html);
        assertThat(form.find()).isTrue();
        return form.group(1).replace("&amp;", "&");
    }

    @Test
    void aBooksDetailPageLinksItsAuthorAndPublisherAndMasksTheSecretToken() throws Exception {
        long book = firstId("Book");
        long author = entityManager.createQuery("select b.author.id from Book b where b.id = :id", Long.class)
                .setParameter("id", book).getSingleResult();

        String html = page("/admin/book/" + book);

        assertThat(html).contains("<dt>Title</dt>", "<dt>Author</dt>", "<dt>Publisher</dt>", "<dt>Secret token</dt>")
                .contains("href=\"/admin/author/" + author + "\"").contains("class=\"cell-masked\"", "Hidden value");
        assertThat(html).doesNotContain("not-a-real-secret", "token-0");
    }

    @Test
    void theBookListLinksItsRowsAndItsRelations() throws Exception {
        String html = page("/admin/book");

        assertThat(html).containsPattern("<a class=\"cell-link cell-number\" href=\"/admin/book/[0-9]+\">[0-9]+</a>")
                .containsPattern("<a class=\"cell-link\" href=\"/admin/author/[0-9]+\">Author 1</a>")
                .containsPattern("<a class=\"cell-link\" href=\"/admin/publisher/[0-9]+\">Publisher 1</a>");
    }

    @Test
    void anUnknownRecordAndAnEntityOfAnotherHostAreTheSameNotFound() throws Exception {
        MockHttpServletResponse unknown = send(MockMvcRequestBuilders.get("/admin/book/999999"));
        MockHttpServletResponse text = send(MockMvcRequestBuilders.get("/admin/book/abc"));
        MockHttpServletResponse other = send(MockMvcRequestBuilders.get("/admin/product/1"));

        assertThat(unknown.getStatus()).isEqualTo(404);
        assertThat(text.getStatus()).isEqualTo(404);
        assertThat(other.getStatus()).isEqualTo(404);
        assertThat(text.getContentAsString()).isEqualTo(unknown.getContentAsString());
        assertThat(other.getContentAsString()).isEqualTo(unknown.getContentAsString());
    }

    @Test
    void aPublisherNobodyUsesIsDeletedWithTheTokenAndWithoutItIsRefused() throws Exception {
        long id = newPublisher("Disposable press");
        String detail = page("/admin/publisher/" + id);

        assertThat(send(MockMvcRequestBuilders.post(action(detail))).getStatus()).as("no token").isEqualTo(403);
        assertThat(exists(Publisher.class, id)).isTrue();

        MockHttpServletResponse done = send(MockMvcRequestBuilders.post(action(detail)).param("_csrf", token(detail)));

        assertThat(done.getStatus()).isEqualTo(303);
        assertThat(done.getHeader("Location")).isEqualTo("/admin/publisher");
        assertThat(exists(Publisher.class, id)).isFalse();
        assertThat(page("/admin/publisher")).contains("Deleted publisher “Disposable press”.");
    }

    @Test
    void aPublisherThatBooksPointAtIsKeptWithAFriendlyMessage() throws Exception {
        long id = firstId("Publisher");
        String detail = page("/admin/publisher/" + id);

        MockHttpServletResponse refused = send(MockMvcRequestBuilders.post(action(detail)).param("_csrf", token(detail)));

        assertThat(refused.getStatus()).isEqualTo(303);
        assertThat(exists(Publisher.class, id)).isTrue();
        String after = page(refused.getHeader("Location")).replaceAll("<input[^>]*>", "").toLowerCase();
        assertThat(after).contains("the publisher was not deleted").doesNotContain("constraint", "sql", "foreign key", "exception");
    }

    @Test
    void bulkDeleteWorksOnThisHostsEntitiesAndAFailingRecordKeepsTheWholeBatch() throws Exception {
        long spare = newPublisher("Disposable spare");
        long inUse = firstId("Publisher");
        String list = page("/admin/publisher");
        Matcher form = Pattern.compile("(?s)<form class=\"bulk-form\"[^>]*?\\saction=\"([^\"]+)\"").matcher(list);
        assertThat(form.find()).isTrue();

        MockHttpServletResponse refused = send(MockMvcRequestBuilders.post(form.group(1)).param("_csrf", token(list))
                .param("ids", String.valueOf(spare), String.valueOf(inUse)));

        assertThat(refused.getStatus()).isEqualTo(303);
        assertThat(exists(Publisher.class, spare)).as("all or nothing").isTrue();
        assertThat(page("/admin/publisher")).contains("Nothing was deleted.");

        MockHttpServletResponse done = send(MockMvcRequestBuilders.post(form.group(1)).param("_csrf", token(list))
                .param("ids", String.valueOf(spare)));

        assertThat(done.getStatus()).isEqualTo(303);
        assertThat(exists(Publisher.class, spare)).isFalse();
        assertThat(page("/admin/publisher")).contains("Deleted 1 record.");
    }

    @Test
    void aBookCanBeDeletedAndItsAuthorAndPublisherStay() throws Exception {
        long[] ids = new long[3];
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Author author = entityManager.find(Author.class, firstId("Author"));
            Publisher publisher = entityManager.find(Publisher.class, firstId("Publisher"));
            Book book = new Book("Disposable book", author, publisher);
            book.setSecretToken("disposable-not-a-real-secret");
            entityManager.persist(book);
            entityManager.flush();
            ids[0] = book.getId();
            ids[1] = author.getId();
            ids[2] = publisher.getId();
        });
        String detail = page("/admin/book/" + ids[0]);

        MockHttpServletResponse done = send(MockMvcRequestBuilders.post(action(detail)).param("_csrf", token(detail)));

        assertThat(done.getStatus()).isEqualTo(303);
        assertThat(exists(Book.class, ids[0])).isFalse();
        assertThat(exists(Author.class, ids[1])).isTrue();
        assertThat(exists(Publisher.class, ids[2])).isTrue();
        assertThat(page("/admin/book")).contains("Deleted book “Disposable book”.").doesNotContain("disposable-not-a-real-secret");
    }
}
