package io.github.mdmusahub.jpaadmin.sample;

/** The upload tests with {@code spring.jpa.open-in-view=true}, Spring Boot's default (D-29). */
class UploadOverHttpOpenInViewEnabledTest extends AbstractUploadOverHttpTest {

    @Override
    String openInView() {
        return "true";
    }
}
