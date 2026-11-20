package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/** {@code jpa.admin.page-size=50}: all 30 orders fit on the first page, and the links say the sizes that are not the default. */
@SpringBootTest(properties = "jpa.admin.page-size=50")
@AutoConfigureMockMvc
class PageSizeFiftyPagesTest extends AbstractPageSizePagesTest {

    @Test
    void anAddressWithoutASizeShowsTheConfiguredFiftyRows() throws Exception {
        String html = page("/admin/order");

        assertThat(rows(html)).isEqualTo(30);
        assertThat(html).contains("Showing 1–30 of 30");
        assertThat(attribute(html, "data-views-default-size")).isEqualTo("50");
    }

    @Test
    void aSizeInTheAddressStillWinsWhenItIsOnOffer() throws Exception {
        assertThat(rows(page("/admin/order?size=25"))).isEqualTo(25);
        assertThat(rows(page("/admin/order?size=10"))).isEqualTo(10);
        assertThat(rows(page("/admin/order?size=100"))).isEqualTo(30);
    }

    @Test
    void aSizeThatIsNotOnOfferFallsBackToTheConfiguredFiftyNeverToTwentyFive() throws Exception {
        for (String bad : new String[] {"7", "0", "-1", "abc", "26", "1000", "99999999999", ""}) {
            assertThat(rows(page("/admin/order?size=" + bad))).as("size=" + bad).isEqualTo(30);
        }
    }

    @Test
    void theLinksLeaveTheSizeOutForFiftyAndSayItForTheOthers() throws Exception {
        String html = page("/admin/order");

        assertThat(html).contains("href=\"/admin/order?size=10\"", "href=\"/admin/order?size=25\"", "href=\"/admin/order\"",
                "href=\"/admin/order?size=100\"");
        assertThat(html).doesNotContain("href=\"/admin/order?size=50\"");
        assertThat(attribute(html, "data-views-state")).isEqualTo("/admin/order");
    }

    @Test
    void aStateThatAsksForTwentyFiveKeepsItInTheViewsAddress() throws Exception {
        String html = page("/admin/order?size=25&sort=total&dir=desc");

        assertThat(attribute(html, "data-views-state")).isEqualTo("/admin/order?sort=total&dir=desc&size=25");
    }

    @Test
    void theExportHasNoSizeAtAllAndTheClearSearchLinkIsThePlainAddress() throws Exception {
        String html = page("/admin/order?q=zzzzzz&size=25");

        assertThat(html).contains("href=\"/admin/order/export.csv?q=zzzzzz\"").contains("href=\"/admin/order\">Clear search</a>");
    }

    @Test
    void thePageSizeAppliesToEveryList() throws Exception {
        assertThat(rows(page("/admin/product"))).isEqualTo(25);
        assertThat(rows(page("/admin/customer"))).isEqualTo(12);
        assertThat(rows(page("/admin/category"))).isEqualTo(6);
    }
}
