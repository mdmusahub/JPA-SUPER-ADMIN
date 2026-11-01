package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mdmusahub.jpaadmin.sample.domain.Category;
import io.github.mdmusahub.jpaadmin.sample.domain.Customer;
import io.github.mdmusahub.jpaadmin.sample.domain.Order;
import io.github.mdmusahub.jpaadmin.sample.domain.OrderStatus;
import io.github.mdmusahub.jpaadmin.sample.domain.Product;
import io.github.mdmusahub.jpaadmin.sample.domain.Tag;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class SampleDataTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Validator validator;

    @Autowired
    private Clock clock;

    @Test
    void seedsExpectedRowCounts() {
        assertThat(count(Category.class)).isEqualTo(DemoDataLoader.CATEGORY_COUNT);
        assertThat(count(Tag.class)).isEqualTo(DemoDataLoader.TAG_COUNT);
        assertThat(count(Product.class)).isEqualTo(DemoDataLoader.PRODUCT_COUNT);
        assertThat(count(Customer.class)).isEqualTo(DemoDataLoader.CUSTOMER_COUNT);
        // Order is a keyword-like entity name: this also proves Criteria queries work for it.
        assertThat(count(Order.class)).isEqualTo(DemoDataLoader.ORDER_COUNT);
    }

    @Test
    void productsHaveEnoughRowsForPagingAndSearch() {
        assertThat(count(Product.class)).isGreaterThanOrEqualTo(25);
    }

    @Test
    void creationDatesAreSpreadOverTheLast30Days() {
        LocalDateTime now = LocalDateTime.now(clock);
        for (Class<?> type : List.of(Category.class, Product.class, Customer.class, Order.class)) {
            List<LocalDateTime> dates = createdAtValues(type);
            assertThat(dates).as(type.getSimpleName()).isNotEmpty();
            assertThat(dates).allSatisfy(date -> assertThat(date)
                    .isAfter(now.minusDays(DemoDataLoader.SPREAD_DAYS + 1L))
                    .isBeforeOrEqualTo(now));
        }
        Set<Object> productDays = createdAtValues(Product.class).stream()
                .map(LocalDateTime::toLocalDate).collect(Collectors.toSet());
        assertThat(productDays.size()).isGreaterThanOrEqualTo(20);
    }

    @Test
    void ordersCoverEveryStatus() {
        Set<OrderStatus> statuses = entityManager.createQuery("select o.status from Order o", OrderStatus.class)
                .getResultList().stream().collect(Collectors.toSet());
        assertThat(statuses).containsExactlyInAnyOrder(OrderStatus.values());
    }

    @Test
    void customerPasswordsAreOnlyTheFakePlaceholder() {
        List<String> passwords = entityManager.createQuery("select c.password from Customer c", String.class)
                .getResultList();
        assertThat(passwords).hasSize(DemoDataLoader.CUSTOMER_COUNT)
                .containsOnly(Customer.DEMO_PASSWORD_PLACEHOLDER);
    }

    @Test
    void beanValidationRejectsInvalidValues() {
        Product product = new Product(" ", "", new BigDecimal("-1.00"), null);
        product.setStock(-5);
        Customer customer = new Customer("", "not-an-email", "short");

        assertThat(fields(validator.validate(product)))
                .contains("name", "sku", "price", "stock", "category");
        assertThat(fields(validator.validate(customer)))
                .contains("fullName", "email", "password");
    }

    @Test
    void persistingAnInvalidEntityIsRejected() {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transactions.executeWithoutResult(
                status -> entityManager.persist(new Category("", "invalid"))))
                .isInstanceOf(ConstraintViolationException.class);
    }

    private long count(Class<?> type) {
        return entityManager.createQuery("select count(e) from " + type.getSimpleName() + " e", Long.class)
                .getSingleResult();
    }

    private List<LocalDateTime> createdAtValues(Class<?> type) {
        return entityManager.createQuery(
                "select e.createdAt from " + type.getSimpleName() + " e", LocalDateTime.class).getResultList();
    }

    private static <T> Set<String> fields(Set<ConstraintViolation<T>> violations) {
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }
}
