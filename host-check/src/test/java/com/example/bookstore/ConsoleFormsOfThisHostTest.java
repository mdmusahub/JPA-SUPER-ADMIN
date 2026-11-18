package com.example.bookstore;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bookstore.domain.Book;
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
 * Phase 8 in the second host: a project with different entities and <b>no Bean Validation at all</b> (its pom has no
 * validation starter) gets the create and edit forms for its own entities with no library change (ARCH-12, D-26). A book is
 * created with its author and publisher chosen from their labels, edited, and its secret token is masked, never shown back,
 * kept by a blank value and replaced by a typed one. Without a validator the console still saves through the persistence
 * layer, checks what it can itself, and refuses an unknown relation.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConsoleFormsOfThisHostTest {

    private static final String TOKEN_VALUE = "needle-host-secret-5521";

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
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> entityManager
                .createQuery("delete from Book b where b.title like 'Disposable%'").executeUpdate());
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

    private static String action(String html) {
        Matcher form = Pattern.compile("(?s)<form class=\"card form-card\"[^>]*?\\saction=\"([^\"]+)\"").matcher(html);
        assertThat(form.find()).isTrue();
        return form.group(1).replace("&amp;", "&");
    }

    private long firstId(String entity) {
        return entityManager.createQuery("select e.id from " + entity + " e order by e.id", Long.class).setMaxResults(1)
                .getSingleResult();
    }

    private MockHttpServletResponse post(String path, String csrf, String... fields) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.post(java.net.URI.create(path));
        for (int i = 0; i + 1 < fields.length; i += 2) {
            request.param(fields[i], fields[i + 1]);
        }
        if (csrf != null) {
            request.param("_csrf", csrf);
        }
        return send(request);
    }

    private String storedToken(long id) {
        return new TransactionTemplate(transactionManager).execute(status -> entityManager.find(Book.class, id)
                .getSecretToken());
    }

    @Test
    void thisHostHasNoBeanValidationSoTheTestsBelowProveTheDefault() {
        try {
            Class.forName("org.hibernate.validator.HibernateValidator");
            throw new AssertionError("this host is meant to have no validator");
        } catch (ClassNotFoundException expected) {
            // good: the console works without one
        }
    }

    @Test
    void aBookIsCreatedWithItsAuthorAndPublisherChosenFromTheirLabelsAndItsSecretIsMasked() throws Exception {
        long author = firstId("Author");
        long publisher = firstId("Publisher");
        String form = page("/admin/book/new");

        assertThat(form).contains("<h1 class=\"page-title\">New book</h1>").contains(">Author 1</option>")
                .contains(">Publisher 1</option>").contains("type=\"password\"");

        MockHttpServletResponse created = post("/admin/book", token(form), "title", "Disposable book", "author",
                String.valueOf(author), "publisher", String.valueOf(publisher), "secretToken", TOKEN_VALUE);

        assertThat(created.getStatus()).isEqualTo(303);
        assertThat(created.getHeader("Location")).isEqualTo("/admin/book");
        long id = entityManager.createQuery("select b.id from Book b where b.title = 'Disposable book'", Long.class)
                .getSingleResult();
        assertThat(storedToken(id)).isEqualTo(TOKEN_VALUE);

        String edit = page("/admin/book/" + id + "/edit");
        assertThat(edit).doesNotContain(TOKEN_VALUE).contains("value=\"Disposable book\"");
        post(action(edit), token(edit), "title", "Disposable book two", "author", String.valueOf(author), "publisher", "",
                "secretToken", "");
        assertThat(storedToken(id)).as("a blank token keeps the stored one").isEqualTo(TOKEN_VALUE);
        assertThat(page("/admin/book/" + id)).contains("Disposable book two").doesNotContain(TOKEN_VALUE);

        post(action(edit), token(edit), "title", "Disposable book two", "author", String.valueOf(author), "publisher", "",
                "secretToken", "replaced-token-value");
        assertThat(storedToken(id)).isEqualTo("replaced-token-value");
    }

    @Test
    void anUnknownAuthorIsRefusedWithAFieldErrorEvenWithoutAValidator() throws Exception {
        String form = page("/admin/book/new");
        long before = entityManager.createQuery("select count(b) from Book b", Long.class).getSingleResult();

        MockHttpServletResponse refused = post("/admin/book", token(form), "title", "Disposable bad", "author", "999999",
                "publisher", "", "secretToken", TOKEN_VALUE);

        assertThat(refused.getStatus()).isEqualTo(422);
        assertThat(refused.getContentAsString()).contains("id=\"e-author\"").doesNotContain(TOKEN_VALUE);
        assertThat(entityManager.createQuery("select count(b) from Book b", Long.class).getSingleResult()).isEqualTo(before);
    }

    @Test
    void aPostWithoutTheCsrfTokenIsRefusedAndAnUnknownEntityIsNotFound() throws Exception {
        assertThat(post("/admin/book", null, "title", "Disposable csrf").getStatus()).isEqualTo(403);
        assertThat(send(MockMvcRequestBuilders.get("/admin/product/new")).getStatus()).isEqualTo(404);
        assertThat(send(MockMvcRequestBuilders.get("/admin/book/999999/edit")).getStatus()).isEqualTo(404);
    }

    @Test
    void theDetailPageOffersEditAndDuplicateAndADuplicateStartsWithoutTheToken() throws Exception {
        long book = firstId("Book");

        assertThat(page("/admin/book/" + book)).contains("/admin/book/" + book + "/edit")
                .contains("/admin/book/new?copyFrom=" + book);
        String copy = page("/admin/book/new?copyFrom=" + book);
        assertThat(copy).contains("This form starts from a copy of book #" + book + ".");
        Matcher password = Pattern.compile("(?is)<input\\b[^>]*type=\"password\"[^>]*>").matcher(copy);
        assertThat(password.find()).isTrue();
        assertThat(password.group()).doesNotContain("value=");
    }
}
