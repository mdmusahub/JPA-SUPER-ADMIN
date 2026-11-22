package io.github.mdmusahub.jpaadmin.sample;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The database the demo application is started on in the engine check (Phase 14a, D-128): its own H2 (the application's default),
 * or a PostgreSQL or MySQL in a container. The container's user name and password are made up at run time and handed to the
 * application as properties; nothing writes them to a file or a log. The same images as the core engine tests; another version
 * can be tried with {@code -Djpa.admin.test.postgres-image=...} or {@code -Djpa.admin.test.mysql-image=...}.
 */
abstract class SampleEngineDatabase implements AutoCloseable {

    private static final SecureRandom RANDOM = new SecureRandom();

    abstract String name();

    abstract String testClass();

    abstract boolean needsDocker();

    abstract void start();

    /** The command-line settings that point the application at this database (none for the application's own H2). */
    abstract List<String> arguments();

    @Override
    public abstract void close();

    static SampleEngineDatabase h2() {
        return new SampleEngineDatabase() {
            @Override String name() { return "H2"; }
            @Override String testClass() { return "H2SampleEngineTest"; }
            @Override boolean needsDocker() { return false; }
            @Override void start() { }
            @Override List<String> arguments() { return List.of(); }
            @Override public void close() { }
        };
    }

    static SampleEngineDatabase postgres() {
        return new Container("PostgreSQL", "PostgreSqlSampleEngineTest",
                System.getProperty("jpa.admin.test.postgres-image", "postgres:17.11")) {
            @Override JdbcDatabaseContainer<?> build(String image, String user, String password) {
                return new PostgreSQLContainer(image).withDatabaseName("jpa_admin_sample").withUsername(user).withPassword(password);
            }
        };
    }

    static SampleEngineDatabase mysql() {
        return new Container("MySQL", "MySqlSampleEngineTest", System.getProperty("jpa.admin.test.mysql-image", "mysql:8.4.11")) {
            @Override JdbcDatabaseContainer<?> build(String image, String user, String password) {
                return new MySQLContainer(image).withDatabaseName("jpa_admin_sample").withUsername(user).withPassword(password);
            }
        };
    }

    private abstract static class Container extends SampleEngineDatabase {

        private final String name;
        private final String testClass;
        private final String image;
        private JdbcDatabaseContainer<?> container;
        private String user;
        private String password;

        Container(String name, String testClass, String image) {
            this.name = name;
            this.testClass = testClass;
            this.image = image;
        }

        abstract JdbcDatabaseContainer<?> build(String image, String user, String password);

        @Override String name() { return name; }

        @Override String testClass() { return testClass; }

        @Override boolean needsDocker() { return true; }

        @Override
        void start() {
            user = "u" + random(6);
            password = random(16);
            container = build(image, user, password);
            container.start();
        }

        @Override
        List<String> arguments() {
            return List.of("--spring.datasource.url=" + container.getJdbcUrl(), "--spring.datasource.username=" + user,
                    "--spring.datasource.password=" + password,
                    "--spring.datasource.driver-class-name=" + container.getDriverClassName());
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

        private static String random(int bytes) {
            byte[] buffer = new byte[bytes];
            RANDOM.nextBytes(buffer);
            return HexFormat.of().formatHex(buffer);
        }
    }
}
