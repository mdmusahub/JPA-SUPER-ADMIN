package io.github.mdmusahub.jpaadmin.service;

/**
 * What the CSV export is allowed to do, as the auto-configuration reads it from {@code jpa.admin.export-*} (D-107). The core
 * module knows no property names, only this record.
 *
 * <p>{@link #cap()} is the most rows one file holds: {@code min(maxRows, }{@value #CEILING}{@code )}. The ceiling is fixed
 * inside the console and the property can only lower it (D-12, ARCH-10), so even a record built by hand with a larger number
 * cannot raise the cap.
 *
 * <p>Internal, not stable API. Public because the auto-configuration module creates it.
 *
 * @param enabled whether the export is available at all ({@code jpa.admin.export-enabled})
 * @param maxRows the configured cap, at least 1 ({@code jpa.admin.export-max-rows})
 * @param bom     whether the file starts with a UTF-8 byte order mark ({@code jpa.admin.export-bom})
 */
public record ExportSettings(boolean enabled, long maxRows, boolean bom) {

    /** The fixed ceiling of rows in one file. The configuration can lower the cap, never raise it. */
    public static final int CEILING = 50_000;

    public ExportSettings {
        if (maxRows < 1) {
            throw new IllegalArgumentException("maxRows must be at least 1");
        }
    }

    /** The settings of a console that was not configured: on, up to the ceiling, no byte order mark. */
    public static ExportSettings defaults() {
        return new ExportSettings(true, CEILING, false);
    }

    /** Settings with the export switched off, for a console that has no entity manager or in a test. */
    public static ExportSettings off() {
        return new ExportSettings(false, CEILING, false);
    }

    /** The most rows one file holds: the configured number, never more than {@value #CEILING}. */
    public int cap() {
        return (int) Math.min(maxRows, CEILING);
    }
}
