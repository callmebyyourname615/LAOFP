package com.example.switching.crossborder;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class CrossBorderTemporalBindingRegressionTest {

    private static final Pattern INSTANT_VARIABLE = Pattern.compile("\\bInstant\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern SET_OBJECT = Pattern.compile("\\bsetObject\\s*\\(([^;]+)\\)", Pattern.DOTALL);

    @Test
    void instantBindingsSpecifyTimestampWithTimezone() throws IOException {
        Path root = Path.of("src/main/java/com/example/switching/crossborder");
        if (!Files.exists(root)) {
            return;
        }
        List<String> violations = new ArrayList<>();
        try (var files = Files.walk(root)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                List<String> instantNames = new ArrayList<>();
                Matcher variables = INSTANT_VARIABLE.matcher(source);
                while (variables.find()) {
                    instantNames.add(variables.group(1));
                }
                Matcher calls = SET_OBJECT.matcher(source);
                while (calls.find()) {
                    String call = calls.group(1);
                    boolean hasInstant = call.contains("Instant.now(")
                            || instantNames.stream().anyMatch(name -> Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(call).find());
                    if (hasInstant && !call.contains("Types.TIMESTAMP_WITH_TIMEZONE")) {
                        long line = source.substring(0, calls.start()).lines().count() + 1;
                        violations.add(path + ":" + line);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "Instant values must use Types.TIMESTAMP_WITH_TIMEZONE in PreparedStatement.setObject: " + violations);
    }
}
