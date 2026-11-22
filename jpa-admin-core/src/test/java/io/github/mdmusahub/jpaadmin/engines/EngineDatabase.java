package io.github.mdmusahub.jpaadmin.engines;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One database engine the shared scenarios run on (Phase 14a, D-128): H2 in memory, PostgreSQL or MySQL in a container.
 *
 * <p><b>Credentials</b> of a container are made up at run time ({@link SecureRandom}), used only to connect from the test, and never
 * written to a file, a log or a message by this code (no secret in the repository, AI-10). Testcontainers itself logs the JDBC URL
 * (host, random port and database name, never the credentials).
 *
 * <p><b>Images</b> are pinned by tag, and each tag was pushed at least 14 days before it was chosen (see the Phase 14a plan). Another
 * version can be tried with {@code -Djpa.admin.test.postgres-image=...} or {@code -Djpa.admin.test.mysql-image=...}.
 */
public abstract class EngineDatabase implements AutoCloseable {

    /** PostgreSQL 17.11, pushed to Docker Hub on 2026-11-06. */
    static final String POSTGRES_IMAGE = "postgres:17.11";

    /** MySQL 8.4.11 (the long-term-support line), pushed to Docker Hub on 2026-10-19. */
    static final String MYSQL_IMAGE = "mysql:8.4.11";

    private static final SecureRandom RANDOM = new SecureRandom();

    /** The engine's short name, used in every failure and skip message. */
    public abstract String name();

    /** The test class that runs this engine, for the command in a skip message. */
    public abstract String testClass();

    /** Whether the engine needs a Docker daemon. */
    public abstract boolean needsDocker();

    /** Starts the engine. Called once per test class, after the gate said run. */
    public abstract void start();

    /** The JPA properties (URL, user, password, driver) that point the persistence unit at this engine. */
    public abstract Map<String, Object> properties();

    /** Stops the engine and forgets the credentials. */
    @Override
    public abstract void close();

    /** The product name and version the server reports, for example {@code PostgreSQL 17.11}. */
    public String version() {
        Map<String, Object> properties = properties();
        try (Connection connection = DriverManager.getConnection((String) properties.get("jakarta.persistence.jdbc.url"),
                (String) properties.get("jakarta.persistence.jdbc.user"),
                (String) properties.get("jakarta.persistence.jdbc.password"))) {
            return connection.getMetaData().getDatabaseProductName() + " "
                    + connection.getMetaData().getDatabaseProductVersion();
        } catch (SQLException unreadable) {
            return name();
        }
    }

    // ---- the three engines -------------------------------------------------------------------------------------------------

    /** H2 in memory: no Docker, always available. */
    public static EngineDatabase h2() {
        return new H2();
    }

    /** PostgreSQL in a container. */
    public static EngineDatabase postgres() {
        return new PostgreSql(System.getProperty("jpa.admin.test.postgres-image", POSTGRES_IMAGE));
    }

    /** MySQL in a container. */
    public static EngineDatabase mysql() {
        return new MySql(System.getProperty("jpa.admin.test.mysql-image", MYSQL_IMAGE));
    }

    private static Map<String, Object> jpa(String url, String user, String password, String driver) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("jakarta.persistence.jdbc.url", url);
        properties.put("jakarta.persistence.jdbc.user", user);
        properties.put("jakarta.persistence.jdbc.password", password);
        properties.put("jakarta.persistence.jdbc.driver", driver);
        return properties;
    }

    private static String random(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }

    private static final class H2 extends EngineDatabase {

        @Override
        public String name() {
            return "H2";
        }

        @Override
        public String testClass() {
            return "H2EngineTest";
        }

        @Override
        public boolean needsDocker() {
            return false;
        }

        @Override
        public void start() {
            // In memory: nothing to start.
        }

        @Override
        public Map<String, Object> properties() {
            return jpa("jdbc:h2:mem:engine-h2;DB_CLOSE_DELAY=-1", "sa", "", "org.h2.Driver");
        }

        @Override
        public void close() {
            // The database lives as long as the JVM.
        }
    }

    /** A database in a container: one subclass per engine only says which container to build. */
    private abstract static class Container extends EngineDatabase {

        private JdbcDatabaseContainer<?> container;
        private String user;
        private String password;

        abstract JdbcDatabaseContainer<?> build(String user, String password);

        @Override
        public boolean needsDocker() {
            return true;
        }

        @Override
        public void start() {
            user = "u" + random(6);
            password = random(16);
            container = build(user, password);
            container.start();
        }

        @Override
        public Map<String, Object> properties() {
            if (container == null) {
                throw new IllegalStateException("the " + name() + " container has not been started");
            }
            return jpa(container.getJdbcUrl(), user, password, container.getDriverClassName());
        }

        @Override
        public void close() {
            if (container != null) {
                container.stop();
                container = null;
            }
            user = null;
            password = null;
        }
    }

    private static final class PostgreSql extends Container {

        private final String image;

        PostgreSql(String image) {
            this.image = image;
        }

        @Override
        public String name() {
            return "PostgreSQL";
        }

        @Override
        public String testClass() {
            return "PostgreSqlEngineTest";
        }

        @Override
        JdbcDatabaseContainer<?> build(String user, String password) {
            return new PostgreSQLContainer(image).withDatabaseName("jpa_admin_engines").withUsername(user)
                    .withPassword(password);
        }
    }

    private static final class MySql extends Container {

        private final String image;

        MySql(String image) {
            this.image = image;
        }

        @Override
        public String name() {
            return "MySQL";
        }

        @Override
        public String testClass() {
            return "MySqlEngineTest";
        }

        @Override
        JdbcDatabaseContainer<?> build(String user, String password) {
            return new MySQLContainer(image).withDatabaseName("jpa_admin_engines").withUsername(user).withPassword(password);
        }
    }
}
