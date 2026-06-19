package com.example.switching.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Prevents future schedulers or Kafka listeners from bypassing the migration-profile boundary. */
class MigrationRuntimeIsolationContractTest {

    @Test
    void everyScheduledOrKafkaListenerSourceHasMigrationProfileGuard() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        List<String> violations = new ArrayList<>();

        try (var sources = Files.walk(sourceRoot)) {
            sources.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> inspect(path, violations));
        }

        assertThat(violations)
                .as("runtime side-effect beans must declare @Profile(\"!migration\")")
                .isEmpty();
    }

    private static void inspect(Path path, List<String> violations) {
        try {
            String source = Files.readString(path);
            if ((source.contains("@Scheduled") || source.contains("@KafkaListener"))
                    && !source.contains("@Profile(\"!migration\")")) {
                violations.add(path.toString());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to inspect " + path, exception);
        }
    }
}
