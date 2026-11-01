package io.github.mdmusahub.jpaadmin.preview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Preview app that renders the real console templates with fixture data. It is never published and never a
 * dependency of another module (ARCH-11). It has no database and no login.
 */
@SpringBootApplication
public class JpaAdminUiPreviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(JpaAdminUiPreviewApplication.class, args);
    }
}
