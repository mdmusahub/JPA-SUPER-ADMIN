package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/** {@code jpa.admin.page-size=10}: ten rows a page, three pages of orders. */
@SpringBootTest(properties = "jpa.admin.page-size=10")
@AutoConfigureMockMvc
class PageSizeTenPagesTest extends AbstractPageSizePagesTest {

    @Test
    void anAddressWithoutASizeShowsTenRowsAndThreePages() throws Exception {
        String html = page("/admin/order");

        assertThat(rows(html)).isEqualTo(10);
        assertThat(html).contains("Showing 1–10 of 30").contains("Page 1 of 3");
        assertThat(attribute(html, "data-views-default-size")).isEqualTo("10");
    }

    @Test
    void theNextPageLinkNeverCarriesTheDefaultSize() throws Exception {
        String html = page("/admin/order");

        assertThat(html).contains("href=\"/admin/order?page=1\"").doesNotContainPattern("size=10(?![0-9])");
    }

    @Test
    void aSizeThatIsNotOnOfferFallsBackToTen() throws Exception {
        assertThat(rows(page("/admin/order?size=33"))).isEqualTo(10);
    }

    @Test
    void twentyFiveIsNowAnExplicitChoice() throws Exception {
        String html = page("/admin/order?size=25");

        assertThat(rows(html)).isEqualTo(25);
        assertThat(attribute(html, "data-views-state")).isEqualTo("/admin/order?size=25");
    }
}
