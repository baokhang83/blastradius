package io.github.baokhang83.blastradius.validator.build;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Explicit, class-level test exclusions supplied to the validator. */
public record SkippedTests(List<String> classes) {

    private static final Pattern CLASS_NAME = Pattern.compile(
            "[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+");

    public SkippedTests {
        Objects.requireNonNull(classes, "classes");
        classes = List.copyOf(classes);
    }

    public static SkippedTests none() {
        return new SkippedTests(List.of());
    }

    /** Parses one or more comma-separated CLI values into ordered, distinct class names. */
    public static SkippedTests parse(List<String> values) {
        Objects.requireNonNull(values, "values");
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String value : values) {
            Objects.requireNonNull(value, "skipped test value");
            for (String entry : value.split(",", -1)) {
                String className = entry.trim();
                if (!CLASS_NAME.matcher(className).matches()) {
                    throw new IllegalArgumentException("skipped tests must be fully qualified class names: " + entry);
                }
                names.add(className);
            }
        }
        return new SkippedTests(List.copyOf(names));
    }

    /** Adds Surefire negative patterns to a nullable positive selector. */
    public String appendTo(String selector) {
        if (classes.isEmpty()) {
            return selector;
        }
        List<String> selectors = new java.util.ArrayList<>();
        if (selector != null) {
            selectors.add(selector);
        }
        classes.forEach(className -> selectors.add("!" + className));
        return String.join(",", selectors);
    }
}
