package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Hiding an entity, end to end in the demo application: one entity is hidden by the property
 * {@code jpa.admin.excluded-entities}, another by the annotation {@code @AdminHidden}.
 *
 * <p>Both must be invisible in the same way: not in the sidebar, not on the dashboard, and their address answers
 * exactly like an address that was never an entity, so the console never reveals that they exist (SEC-2).
 */
@SpringBootTest(properties = "jpa.admin.excluded-entities=tag")
@AutoConfigureMockMvc
class ConsoleHidesEntitiesTest {

    @Autowired
    private MockMvc mvc;

    private MockBrowser browser() throws Exception {
        return MockBrowser.signedIn(mvc);
    }

    @Test
    void anExcludedEntityIsInNoSidebarAndOnNoDashboard() throws Exception {
        String html = browser().get("/admin").getContentAsString();

        assertThat(html).doesNotContain("data-nav-item=\"Tag\"", "href=\"/admin/tag\"");
        assertThat(html).contains("data-nav-item=\"Product\"", "href=\"/admin/product\"");
        // Four entities left, and the stat card says so.
        assertThat(html).contains("<p class=\"stat-value\">4</p>", "Across 4 entities");
    }

    @Test
    void theExcludedNameWasWrittenInAnotherCaseAndStillMatched() throws Exception {
        // The property says "tag"; the class is Tag. The match ignores case on purpose.
        assertThat(browser().get("/admin/tag").getStatus()).isEqualTo(404);
    }

    @Test
    void anEntityHiddenByTheAnnotationIsJustAsInvisible() throws Exception {
        String html = browser().get("/admin").getContentAsString();

        assertThat(html).doesNotContain("Internal audit entry", "internalauditentry");
        assertThat(browser().get("/admin/internalauditentry").getStatus()).isEqualTo(404);
    }

    @Test
    void aHiddenEntityAndAnEntityThatNeverExistedAnswerTheSameThing() throws Exception {
        MockBrowser browser = browser();

        MockHttpServletResponse excluded = browser.get("/admin/tag");
        MockHttpServletResponse annotated = browser.get("/admin/internalauditentry");
        MockHttpServletResponse neverExisted = browser.get("/admin/there-is-no-such-entity");

        for (MockHttpServletResponse response : List.of(excluded, annotated, neverExisted)) {
            assertThat(response.getStatus()).isEqualTo(404);
        }
        assertThat(excluded.getContentAsString()).isEqualTo(neverExisted.getContentAsString());
        assertThat(annotated.getContentAsString()).isEqualTo(neverExisted.getContentAsString());
        assertThat(excluded.getContentType()).isEqualTo(neverExisted.getContentType());
    }

    @Test
    void aHiddenEntityIsNotEvenMentionedInTheAnswerAboutIt() throws Exception {
        String body = browser().get("/admin/tag").getContentAsString();

        // Nothing says "this entity is hidden" or names it: that would reveal what the console is hiding.
        assertThat(body).doesNotContain("Tag", "/admin/tag", "excluded");
        assertThat(body.toLowerCase()).doesNotContain("is hidden", "not allowed to see");
    }
}
