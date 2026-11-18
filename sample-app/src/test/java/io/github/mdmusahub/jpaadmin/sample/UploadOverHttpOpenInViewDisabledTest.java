package io.github.mdmusahub.jpaadmin.sample;

/** The upload tests with {@code spring.jpa.open-in-view=false}, the sample app's own setting (D-29). */
class UploadOverHttpOpenInViewDisabledTest extends AbstractUploadOverHttpTest {

    @Override
    String openInView() {
        return "false";
    }
}
