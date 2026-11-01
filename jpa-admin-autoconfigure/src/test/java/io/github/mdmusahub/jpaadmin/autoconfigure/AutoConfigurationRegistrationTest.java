package io.github.mdmusahub.jpaadmin.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.core.io.support.SpringFactoriesLoader;

/** The imports file is what makes the starter work with no code in the host, so it is checked as a file and as a result. */
class AutoConfigurationRegistrationTest {

    private static final String IMPORTS = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
    private static final String NAME = "io.github.mdmusahub.jpaadmin.autoconfigure.JpaAdminAutoConfiguration";

    /** This module's own copy of the imports file, not the ones that Spring Boot's jars carry under the same name. */
    private static List<String> ownImportsFile() throws IOException {
        URL ownCode = JpaAdminAutoConfiguration.class.getProtectionDomain().getCodeSource().getLocation();
        for (URL candidate : Collections.list(JpaAdminAutoConfiguration.class.getClassLoader().getResources(IMPORTS))) {
            if (candidate.toString().startsWith(ownCode.toString().replaceAll("/$", ""))) {
                try (InputStream in = candidate.openStream()) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                            .map(String::strip).filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
                }
            }
        }
        throw new AssertionError("This module has no " + IMPORTS);
    }

    @Test
    void theImportsFileListsTheAutoConfigurationAndNothingElse() throws IOException {
        assertThat(ownImportsFile()).containsExactly(NAME);
    }

    @Test
    void springBootFindsTheAutoConfigurationExactlyOnce() {
        List<String> candidates = StreamSupport
                .stream(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).spliterator(), false)
                .collect(Collectors.toList());

        assertThat(candidates).containsOnlyOnce(NAME);
        assertThat(candidates).filteredOn(name -> name.startsWith("io.github.mdmusahub")).containsExactly(NAME);
    }

    @Test
    void theListedClassExistsAndIsAnAutoConfiguration() throws ClassNotFoundException {
        Class<?> type = Class.forName(NAME);

        assertThat(type).isEqualTo(JpaAdminAutoConfiguration.class);
        assertThat(type.isAnnotationPresent(AutoConfiguration.class)).isTrue();
    }

    @Test
    void doesNotAlsoUseTheOldSpringFactoriesRegistration() {
        // Boot 4 reads the imports file. Registering under EnableAutoConfiguration in spring.factories would be dead
        // weight at best and a double registration at worst.
        List<String> oldStyle = SpringFactoriesLoader.loadFactoryNames(EnableAutoConfiguration.class,
                getClass().getClassLoader());

        assertThat(oldStyle).doesNotContain(NAME);
    }
}
