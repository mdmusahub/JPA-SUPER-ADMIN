package io.github.mdmusahub.jpaadmin.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code String} field as a file or image field: the console stores an uploaded file in a storage and keeps the
 * file's address in the field.
 *
 * <p>Put it on the field or on its getter; both are read. The field must be a {@code String} (long enough for an
 * address, about 200 characters, for example {@code @Column(length = 255)}); anything else stops startup with a message that
 * names the field.
 *
 * <pre>{@code
 * @Entity
 * public class Member {
 *     @AdminUpload(folder = "avatars", accept = {"image/png", "image/jpeg"}, maxSize = "2MB")
 *     private String avatar;     // null until a file is uploaded
 * }
 * }</pre>
 *
 * <p>Every upload is checked by what the file really is (its first bytes), never by its name or the type the browser claims;
 * the file is stored under a random name, and the console serves it only to a signed-in admin unless the storage has a
 * {@code public-path}. Replacing or removing a file in a form keeps the old file on disk in this version (UPL-9).
 *
 * <p>The storage named here is configured with {@code jpa.admin.storage.<name>.*}; the storage {@code default} exists without
 * any property (local folder {@code ./uploads}, 5MB). A field that names a storage that is not configured stops startup,
 * with the list of the fields (UPL-8).
 *
 * <p>Part of the console's public API (ARCH-9).
 *
 * @see AdminSensitive
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface AdminUpload {

    /** Name of the storage. Default {@code "default"}, which always exists. */
    String storage() default "default";

    /**
     * Sub-folder inside the storage, for example {@code "avatars"} or {@code "docs/invoices"}. Default empty (the storage's
     * root). Each part is letters, digits, {@code -} and {@code _}, starting with a letter or digit; at most 4 parts. Anything
     * else stops startup. Files go to {@code folder/yyyy/MM/}.
     */
    String folder() default "";

    /**
     * The file types the field accepts, as MIME types. Default: {@code image/png}, {@code image/jpeg}, {@code image/gif},
     * {@code image/webp} and {@code application/pdf}. The console knows how to recognise these five by their content and
     * no others: a type it cannot recognise (including {@code image/svg+xml} and {@code text/html}, which are never
     * accepted) stops startup with a message that names the field.
     */
    String[] accept() default {"image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf"};

    /**
     * Largest file for this field, written like {@code "512KB"} or {@code "2MB"}. Default empty: the storage's
     * {@code max-size}. A field may lower the limit of its storage, never raise it.
     */
    String maxSize() default "";
}
