package io.github.mdmusahub.jpaadmin.web;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The few configuration values the console's pages need, handed over by the auto-configuration so this module does
 * not have to know the property names.
 *
 * <p>The accent colour is written into a generated stylesheet, so the record checks it again and accepts only
 * {@code #rrggbb} (SEC-15). The properties are validated at startup as well; this is the second lock on the door.
 *
 * <p>Internal, not stable API. Public only because the auto-configuration module builds it.
 *
 * @param title        title shown in the header, the browser tab and on the sign-in page
 * @param primaryColor accent colour, exactly {@code #} followed by six hexadecimal digits
 */
public record ConsoleSettings(String title, String primaryColor) {

    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    public ConsoleSettings {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(primaryColor, "primaryColor");
        if (!COLOR.matcher(primaryColor).matches()) {
            // Never repeat the value: it ends up in a stylesheet, so a bad one must not travel any further.
            throw new IllegalArgumentException("primaryColor must be a colour written as #rrggbb");
        }
    }
}
