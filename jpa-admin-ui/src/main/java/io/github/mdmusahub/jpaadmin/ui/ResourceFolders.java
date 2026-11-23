package io.github.mdmusahub.jpaadmin.ui;

/**
 * Where the console's resources are on the class path, written once so the documentation of this module has something to
 * describe. Nothing reads these constants: the console's auto-configuration names the folders itself. The class is
 * package-private on purpose, so it adds nothing to the public API (ARCH-9); it exists so that this module, which has no
 * other Java, still has a Javadoc jar (Maven Central asks for one with every jar, D-142).
 */
final class ResourceFolders {

    /** The folder of the Thymeleaf templates, below the class path root. */
    static final String TEMPLATES = "templates/jpa-admin/";

    /** The folder of the stylesheets and the plain JavaScript files, below the class path root. */
    static final String ASSETS = "jpa-admin/assets/";

    private ResourceFolders() {
    }
}
