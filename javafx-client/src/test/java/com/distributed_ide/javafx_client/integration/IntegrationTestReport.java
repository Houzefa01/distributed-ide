package com.distributed_ide.javafx_client.integration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class IntegrationTestReport {

    private final List<String> lines = new ArrayList<>();
    private final Path targetFile;

    IntegrationTestReport(Path targetFile) {
        this.targetFile = targetFile;
        lines.add("# Distributed-IDE - Integration Test Report");
        lines.add("");
        lines.add("Generated at: " + Instant.now());
        lines.add("");
    }

    void section(String title) {
        lines.add("## " + title);
        lines.add("");
    }

    void kv(String key, Object value) {
        lines.add("- **" + key + "**: " + String.valueOf(value));
    }

    void line(String value) {
        lines.add(value);
    }

    void blank() {
        lines.add("");
    }

    void write() {
        try {
            Path parent = targetFile.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.write(targetFile, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write report: " + targetFile, e);
        }
    }
}
