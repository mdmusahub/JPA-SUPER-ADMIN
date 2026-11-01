package io.github.mdmusahub.jpaadmin.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.io.support.SpringFactoriesLoader.ArgumentResolver;
import org.springframework.mock.env.MockEnvironment;

/** The startup report a user sees: names every wrong property, says what to do, and is really registered. */
class JpaAdminFailureAnalyzerTest {

    private final JpaAdminFailureAnalyzer analyzer = new JpaAdminFailureAnalyzer();

    @Test
    void listsEveryProblemAndSaysWhatToDo() {
        var problems = List.of("jpa.admin.username is required. Set it in application.yml, for example jpa.admin.username=admin.",
                "jpa.admin.path must start with '/'.");

        FailureAnalysis analysis = analyzer.analyze(new JpaAdminConfigurationException(problems));

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription())
                .startsWith("The JPA Admin console is not configured correctly:")
                .contains("  - jpa.admin.username is required. Set it in application.yml, for example jpa.admin.username=admin.")
                .contains("  - jpa.admin.path must start with '/'.");
        assertThat(analysis.getAction()).contains("Fix the properties listed above").contains("jpa.admin.enabled=false");
    }

    @Test
    void findsTheProblemWhenSpringWrapsItInABeanCreationException() {
        var wrapped = new BeanCreationException("Error creating bean",
                new JpaAdminConfigurationException(List.of("jpa.admin.password is required.")));

        FailureAnalysis analysis = analyzer.analyze(wrapped);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).contains("jpa.admin.password is required.");
    }

    @Test
    void leavesUnrelatedFailuresToTheOtherAnalyzers() {
        assertThat(analyzer.analyze(new IllegalStateException("something else"))).isNull();
    }

    @Test
    void isRegisteredInSpringFactoriesSoSpringBootUsesIt() {
        // Spring Boot's own FailureAnalyzers hands every analyzer a BeanFactory and an Environment when it creates it,
        // and skips (does not fail on) an analyzer it cannot create, for example one for a library that is not on the
        // classpath. This test does the same, and checks that ours is neither missing nor skipped.
        ArgumentResolver arguments = ArgumentResolver.of(BeanFactory.class, new DefaultListableBeanFactory())
                .and(Environment.class, new MockEnvironment());
        List<String> skipped = new ArrayList<>();

        List<FailureAnalyzer> registered = SpringFactoriesLoader
                .forDefaultResourceLocation(getClass().getClassLoader())
                .load(FailureAnalyzer.class, arguments, (type, implementation, failure) -> skipped.add(implementation));

        assertThat(registered).anyMatch(JpaAdminFailureAnalyzer.class::isInstance);
        assertThat(skipped).doesNotContain(JpaAdminFailureAnalyzer.class.getName());
    }
}
