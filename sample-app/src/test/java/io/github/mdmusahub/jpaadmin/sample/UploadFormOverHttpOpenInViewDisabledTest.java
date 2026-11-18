package io.github.mdmusahub.jpaadmin.sample;

/** The upload form tests with {@code spring.jpa.open-in-view=false}, the sample app's own setting (D-29). */
class UploadFormOverHttpOpenInViewDisabledTest extends AbstractUploadFormOverHttpTest {

    @Override
    String openInView() {
        return "false";
    }
}
