package io.github.mdmusahub.jpaadmin.preview;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The UI rules as code (RULES.md UI-2, UI-4, UI-12, UI-13 and SEC-9), in two forms: {@link #templateViolations} reads a
 * Thymeleaf template as written, {@link #renderedViolations} reads the HTML a page produces. Both return a list of
 * plain-language problems, empty when the input is clean.
 *
 * <p>The checks are text patterns, not a parser. That is deliberate: a pattern is easy to read, and the tests of this
 * class (see {@code HtmlRulesTest}) feed it deliberately bad input to prove each pattern really fires.
 */
final class HtmlRules {

    private static final Pattern PARSER_COMMENT = Pattern.compile("(?s)<!--/\\*.*?\\*/-->");
    private static final Pattern SCRIPT_OPEN = Pattern.compile("(?i)<script\\b([^>]*)>");
    private static final Pattern STYLE_TAG = Pattern.compile("(?i)<style\\b");
    private static final Pattern STYLE_ATTRIBUTE = Pattern.compile("(?i)[\\s\"']style\\s*=");
    private static final Pattern EVENT_HANDLER = Pattern.compile("(?i)\\son[a-z]+\\s*=\\s*[\"']");
    private static final Pattern EXTERNAL_URL = Pattern.compile("(?i)https?://");
    private static final Pattern PROTOCOL_RELATIVE = Pattern.compile("(?i)\\b(?:src|href|action)\\s*=\\s*[\"']//");
    private static final Pattern BUTTON = Pattern.compile("(?is)<button\\b([^>]*)>(.*?)</button>");
    private static final Pattern INPUT = Pattern.compile("(?is)<input\\b([^>]*)>");
    private static final Pattern LABEL_FOR = Pattern.compile("(?is)<label\\b[^>]*\\sfor\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern ARIA_LABEL = Pattern.compile("(?is)\\saria-label\\s*=\\s*\"[^\"]+\"");
    private static final Pattern EXPRESSION = Pattern.compile("\\$\\{([^}]*)}");
    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:[^'\\\\]|\\\\.)*'");
    private static final Pattern ROOT_IDENTIFIER = Pattern.compile("(?<![\\w.#@$])([A-Za-z_][A-Za-z0-9_]*)");

    /** Names that may start a path in an expression without being a variable. */
    private static final Set<String> KEYWORDS = Set.of("true", "false", "null", "and", "or", "not", "lt", "gt", "le",
            "ge", "eq", "ne", "div", "mod");

    /** The only two objects a template may read (UI-12). Everything else must be a local declared in the template. */
    static final Set<String> MODEL_ROOTS = Set.of("chrome", "view");

    private HtmlRules() {
    }

    // ---- templates as written ---------------------------------------------------------------------------------------

    static List<String> templateViolations(String source) {
        String template = PARSER_COMMENT.matcher(source).replaceAll("");
        List<String> problems = new ArrayList<>();

        contains(template, "th:utext", "uses th:utext, which prints unescaped text (SEC-9)", problems);
        contains(template, "data-th-utext", "uses data-th-utext, which prints unescaped text (SEC-9)", problems);
        contains(template, "[(", "uses the unescaped inline form [(...)] (SEC-9)", problems);
        contains(template, "th:inline", "uses th:inline, which can print unescaped text and scripts (UI-13)", problems);
        inlineScripts(template, "template", problems);
        if (STYLE_TAG.matcher(template).find()) {
            problems.add("has a <style> block (UI-13, SEC-15)");
        }
        if (STYLE_ATTRIBUTE.matcher(template).find() || template.contains("th:style") || template.contains("th:styleappend")
                || template.contains("th:classappend=\"style")) {
            problems.add("has a style attribute (UI-13, SEC-15)");
        }
        if (EVENT_HANDLER.matcher(template).find() || Pattern.compile("th:on[a-z]+").matcher(template).find()) {
            problems.add("has an inline event handler (UI-13)");
        }
        if (Pattern.compile("th:attr\\s*=\\s*\"[^\"]*\\b(?:style|on[a-z]+)\\s*=").matcher(template).find()) {
            problems.add("sets a style attribute or event handler through th:attr (UI-13)");
        }
        if (EXTERNAL_URL.matcher(template).find() || PROTOCOL_RELATIVE.matcher(template).find()) {
            problems.add("contains an external address (UI-2)");
        }
        expressionProblems(template, problems);
        return problems;
    }

    /** Every {@code ${...}} may read only {@code chrome}, {@code view} or a variable that the template itself declares. */
    private static void expressionProblems(String template, List<String> problems) {
        Set<String> locals = declaredLocals(template);
        Matcher expressions = EXPRESSION.matcher(template);
        while (expressions.find()) {
            String expression = expressions.group(1);
            String code = STRING_LITERAL.matcher(expression).replaceAll("''");
            for (String forbidden : new String[] {"#request", "#session", "#servletContext", "#ctx", "#vars", "#httpSession",
                    "#httpServletRequest", "#response", "#application", "T(", "new ", "@"}) {
                if (code.contains(forbidden)) {
                    problems.add("expression ${" + expression + "} uses '" + forbidden
                            + "', which reaches outside the model (UI-12)");
                }
            }
            Matcher roots = ROOT_IDENTIFIER.matcher(code);
            while (roots.find()) {
                String name = roots.group(1);
                if (!KEYWORDS.contains(name) && !MODEL_ROOTS.contains(name) && !locals.contains(name)) {
                    problems.add("expression ${" + expression + "} reads '" + name
                            + "', which is neither chrome, view nor a variable declared in the template (UI-12)");
                }
            }
        }
    }

    /** Variables a template declares for itself: th:each names, th:with names and fragment parameters. */
    private static Set<String> declaredLocals(String template) {
        Set<String> locals = new HashSet<>();
        Matcher each = Pattern.compile("th:each\\s*=\\s*\"\\s*(\\w+)\\s*(?:,\\s*(\\w+))?\\s*:").matcher(template);
        while (each.find()) {
            locals.add(each.group(1));
            if (each.group(2) != null) {
                locals.add(each.group(2));
            }
        }
        Matcher with = Pattern.compile("th:with\\s*=\\s*\"([^\"]*)\"").matcher(template);
        while (with.find()) {
            Matcher name = Pattern.compile("(\\w+)\\s*=").matcher(with.group(1));
            while (name.find()) {
                locals.add(name.group(1));
            }
        }
        Matcher fragment = Pattern.compile("th:fragment\\s*=\\s*\"\\w[\\w-]*\\(([^)]*)\\)\"").matcher(template);
        while (fragment.find()) {
            for (String parameter : fragment.group(1).split(",")) {
                if (!parameter.isBlank()) {
                    locals.add(parameter.strip());
                }
            }
        }
        return locals;
    }

    // ---- rendered pages ---------------------------------------------------------------------------------------------

    static List<String> renderedViolations(String html) {
        List<String> problems = new ArrayList<>();

        if (!Pattern.compile("(?i)<html\\b[^>]*\\slang=\"[a-z-]+\"").matcher(html).find()) {
            problems.add("<html> has no lang attribute (UI-4)");
        }
        if (html.contains("th:") || html.contains("${") || html.contains("[[")) {
            problems.add("contains unprocessed template syntax");
        }
        if (html.toLowerCase().contains("jsessionid")) {
            problems.add("puts a session id in the page");
        }
        inlineScripts(html, "page", problems);
        themeScript(html, problems);
        if (STYLE_TAG.matcher(html).find()) {
            problems.add("has a <style> block (UI-13)");
        }
        if (STYLE_ATTRIBUTE.matcher(html).find()) {
            problems.add("has a style attribute (UI-13)");
        }
        if (EVENT_HANDLER.matcher(html).find()) {
            problems.add("has an inline event handler (UI-13)");
        }
        if (EXTERNAL_URL.matcher(html).find() || PROTOCOL_RELATIVE.matcher(html).find()) {
            problems.add("contains an external address (UI-2)");
        }
        unlabelledButtons(html, problems);
        unlabelledInputs(html, problems);
        return problems;
    }

    /** theme.js must be present once, in the head, before the first stylesheet, and blocking (UI-13). */
    private static void themeScript(String html, List<String> problems) {
        Matcher script = Pattern.compile("(?i)<script\\b([^>]*\\bsrc=\"[^\"]*/js/theme\\.js\"[^>]*)>").matcher(html);
        if (!script.find()) {
            problems.add("has no theme.js script");
            return;
        }
        String attributes = script.group(1).toLowerCase();
        if (attributes.contains("async") || attributes.contains("defer") || attributes.contains("module")) {
            problems.add("theme.js is not a blocking script, so the page could flash the wrong theme");
        }
        int headEnd = html.toLowerCase().indexOf("</head>");
        if (headEnd < 0 || script.start() > headEnd) {
            problems.add("theme.js is not inside <head>");
        }
        int firstStylesheet = html.toLowerCase().indexOf("<link rel=\"stylesheet\"");
        if (firstStylesheet >= 0 && firstStylesheet < script.start()) {
            problems.add("theme.js comes after a stylesheet, so the first paint could use the wrong theme");
        }
        if (script.find()) {
            problems.add("has theme.js more than once");
        }
    }

    /** A button with no visible text, only an icon, needs an aria-label (UI-4). */
    private static void unlabelledButtons(String html, List<String> problems) {
        Matcher buttons = BUTTON.matcher(html);
        while (buttons.find()) {
            String text = buttons.group(2).replaceAll("(?s)<[^>]*>", "").strip();
            if (text.isEmpty() && !ARIA_LABEL.matcher(" " + buttons.group(1)).find()) {
                problems.add("an icon-only button has no aria-label (UI-4)");
            }
        }
    }

    /** Every visible input needs a label that points at it (or an aria-label) (UI-4). */
    private static void unlabelledInputs(String html, List<String> problems) {
        Set<String> labelled = new HashSet<>();
        Matcher labels = LABEL_FOR.matcher(html);
        while (labels.find()) {
            labelled.add(labels.group(1));
        }
        Matcher inputs = INPUT.matcher(html);
        while (inputs.find()) {
            String attributes = inputs.group(1);
            if (Pattern.compile("(?i)\\btype\\s*=\\s*\"hidden\"").matcher(attributes).find()) {
                continue;
            }
            Matcher id = Pattern.compile("(?i)\\bid\\s*=\\s*\"([^\"]+)\"").matcher(attributes);
            boolean hasLabel = id.find() && labelled.contains(id.group(1));
            if (!hasLabel && !ARIA_LABEL.matcher(" " + attributes).find()) {
                problems.add("an input has no label (UI-4)");
            }
        }
    }

    // ---- shared -----------------------------------------------------------------------------------------------------

    /** Every script needs a src; an inline script would break a strict Content-Security-Policy (UI-13). */
    private static void inlineScripts(String text, String what, List<String> problems) {
        Matcher scripts = SCRIPT_OPEN.matcher(text);
        while (scripts.find()) {
            if (!Pattern.compile("(?i)\\bsrc\\s*=").matcher(scripts.group(1)).find()) {
                problems.add("the " + what + " has an inline <script> (UI-13)");
            }
        }
    }

    private static void contains(String text, String needle, String message, List<String> problems) {
        if (text.contains(needle)) {
            problems.add(message);
        }
    }
}
