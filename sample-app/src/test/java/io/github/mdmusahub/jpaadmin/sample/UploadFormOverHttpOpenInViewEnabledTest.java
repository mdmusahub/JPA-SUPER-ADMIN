package io.github.mdmusahub.jpaadmin.sample;

/** The upload form tests with {@code spring.jpa.open-in-view=true}, Spring Boot's default (D-29). */
class UploadFormOverHttpOpenInViewEnabledTest extends AbstractUploadFormOverHttpTest {

    @Override
    String openInView() {
        return "true";
    }
}
