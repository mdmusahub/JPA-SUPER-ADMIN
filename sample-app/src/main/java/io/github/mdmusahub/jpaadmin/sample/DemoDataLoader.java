package io.github.mdmusahub.jpaadmin.sample;

import io.github.mdmusahub.jpaadmin.sample.domain.Category;
import io.github.mdmusahub.jpaadmin.sample.domain.Customer;
import io.github.mdmusahub.jpaadmin.sample.domain.Order;
import io.github.mdmusahub.jpaadmin.sample.domain.OrderStatus;
import io.github.mdmusahub.jpaadmin.sample.domain.Product;
import io.github.mdmusahub.jpaadmin.sample.domain.ProductStatus;
import io.github.mdmusahub.jpaadmin.sample.domain.Tag;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Loads deterministic demo data when the database is empty. Creation dates are spread over the last 30 days so the
 * dashboard chart has data later. All values are fake (AI-10).
 */
@Component
class DemoDataLoader implements ApplicationRunner {

    static final int CATEGORY_COUNT = 6;
    static final int TAG_COUNT = 10;
    static final int PRODUCT_COUNT = 25;
    static final int CUSTOMER_COUNT = 12;
    static final int ORDER_COUNT = 30;
    static final int SPREAD_DAYS = 30;

    private static final String[] CATEGORIES = {
        "Books", "Electronics", "Garden", "Home", "Toys", "Sports"
    };
    private static final String[] TAGS = {
        "new", "sale", "popular", "eco", "gift", "limited", "bundle", "premium", "outdoor", "kids"
    };
    private static final String[] PRODUCT_NAMES = {
        "Paperback novel", "Cookbook", "Travel guide", "Wireless mouse", "USB-C hub", "Desk lamp", "Garden hose",
        "Plant pot", "Pruning shears", "Throw blanket", "Coffee grinder", "Wall clock", "Building blocks",
        "Puzzle 1000 pieces", "Board game", "Yoga mat", "Water bottle", "Running belt", "Notebook set",
        "Headphones", "Phone stand", "Watering can", "Picture frame", "Foam football", "Tent pegs"
    };
    private static final String[] CUSTOMER_NAMES = {
        "Ava Stone", "Ben Carter", "Chloe Reed", "Dan Moore", "Eva Lane", "Finn Hale", "Grace Wood", "Hugo Park",
        "Iris Cole", "Jack Dean", "Kara Ford", "Liam Gray"
    };

    private final EntityManager entityManager;
    private final TransactionTemplate transactions;
    private final Clock clock;

    DemoDataLoader(EntityManager entityManager, PlatformTransactionManager transactionManager, Clock clock) {
        this.entityManager = entityManager;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        transactions.executeWithoutResult(status -> {
            if (hasData()) {
                return;
            }
            LocalDateTime now = LocalDateTime.now(clock);
            List<Category> categories = createCategories(now);
            List<Tag> tags = createTags();
            createProducts(categories, tags, now);
            List<Customer> customers = createCustomers(now);
            createOrders(customers, now);
        });
    }

    private boolean hasData() {
        Long count = entityManager.createQuery("select count(c) from Category c", Long.class).getSingleResult();
        return count > 0;
    }

    private List<Category> createCategories(LocalDateTime now) {
        List<Category> result = new ArrayList<>();
        for (int i = 0; i < CATEGORY_COUNT; i++) {
            Category category = new Category(CATEGORIES[i], "Demo category: " + CATEGORIES[i]);
            category.setCreatedAt(daysAgo(now, i * 5, 2));
            entityManager.persist(category);
            result.add(category);
        }
        return result;
    }

    private List<Tag> createTags() {
        List<Tag> result = new ArrayList<>();
        for (int i = 0; i < TAG_COUNT; i++) {
            Tag tag = new Tag(TAGS[i]);
            entityManager.persist(tag);
            result.add(tag);
        }
        return result;
    }

    private void createProducts(List<Category> categories, List<Tag> tags, LocalDateTime now) {
        for (int i = 0; i < PRODUCT_COUNT; i++) {
            BigDecimal price = BigDecimal.valueOf(5 + (i * 7L) % 95).add(new BigDecimal("0.99"));
            Product product = new Product(PRODUCT_NAMES[i], String.format("SKU-%04d", i + 1), price,
                    categories.get(i % CATEGORY_COUNT));
            product.setStock((i * 13) % 80);
            product.setStatus(productStatus(i));
            product.setFeatured(i % 6 == 0);
            product.setCreatedAt(daysAgo(now, i % SPREAD_DAYS, 1 + i % 10));
            product.getTags().add(tags.get(i % TAG_COUNT));
            product.getTags().add(tags.get((i * 3 + 1) % TAG_COUNT));
            entityManager.persist(product);
        }
    }

    private static ProductStatus productStatus(int index) {
        if (index % 5 == 0) {
            return ProductStatus.DRAFT;
        }
        return index % 7 == 0 ? ProductStatus.ARCHIVED : ProductStatus.PUBLISHED;
    }

    private List<Customer> createCustomers(LocalDateTime now) {
        List<Customer> result = new ArrayList<>();
        for (int i = 0; i < CUSTOMER_COUNT; i++) {
            String name = CUSTOMER_NAMES[i];
            String email = name.toLowerCase().replace(' ', '.') + "@example.com";
            Customer customer = new Customer(name, email, Customer.DEMO_PASSWORD_PLACEHOLDER);
            customer.setActive(i != 3);
            customer.setCreatedAt(daysAgo(now, (i * 2 + 1) % SPREAD_DAYS, 3));
            entityManager.persist(customer);
            result.add(customer);
        }
        return result;
    }

    private void createOrders(List<Customer> customers, LocalDateTime now) {
        OrderStatus[] statuses = OrderStatus.values();
        for (int i = 0; i < ORDER_COUNT; i++) {
            BigDecimal total = BigDecimal.valueOf(10 + (i * 17L) % 240).add(new BigDecimal("0.50"));
            Order order = new Order(customers.get(i % CUSTOMER_COUNT), total, statuses[i % statuses.length]);
            order.setNote(i % 4 == 0 ? "Gift wrap please" : null);
            order.setCreatedAt(daysAgo(now, (i * 11) % SPREAD_DAYS, 1 + i % 8));
            entityManager.persist(order);
        }
    }

    /** Always in the past, so seeded dates are never after "now" whatever the time of day. */
    private static LocalDateTime daysAgo(LocalDateTime now, int days, int hoursBack) {
        return now.minusDays(days).minusHours(hoursBack).withNano(0);
    }
}
