package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code jpa.admin.page-size} in the real demo application (Phase 13, D-119): the number of rows of a list whose address says
 * no size, the links and the default of the views script, and what a size in the address or an unacceptable one does. The
 * demo orders (30 of them) are the only table with more than 25 rows. The demo entity names appear here because this is the
 * sample application (ARCH-12).
 */
abstract class AbstractPageSizePagesTest {

    @Autowired
    protected MockMvc mvc;

    private MockBrowser browser;

    @BeforeEach
    void signIn() throws Exception {
        browser = MockBrowser.signedIn(mvc);
    }

    protected String page(String path) throws Exception {
        MockHttpServletResponse response = browser.get(path);
        assertThat(response.getStatus()).as(path).isEqualTo(200);
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    /** The data rows of the table in a page. */
    protected static int rows(String html) {
        if (!html.contains("<tbody")) {
            return 0;
        }
        String body = html.substring(html.indexOf("<tbody"), html.indexOf("</tbody>"));
        Matcher row = Pattern.compile("<tr>").matcher(body);
        int count = 0;
        while (row.find()) {
            count++;
        }
        return count;
    }

    protected static String attribute(String html, String name) {
        Matcher match = Pattern.compile("\\b" + Pattern.quote(name) + "=\"([^\"]*)\"").matcher(html);
        assertThat(match.find()).as(name).isTrue();
        return match.group(1).replace("&amp;", "&");
    }
}
