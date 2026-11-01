package org.springframework.security.fake;

/** Stands in for a Spring Security exception: the resolver recognises those by package name, and core has no Spring Security. */
public class FakeAccessDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FakeAccessDeniedException() {
        super("access denied");
    }
}
