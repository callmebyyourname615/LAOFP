package com.example.switching.phaseii;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.reportdelivery.ReportArtifact;
import com.example.switching.reportdelivery.ReportArtifactGenerator;
import com.example.switching.reportdelivery.ReportArtifactService;

class ReportArtifactIdempotencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanArtifacts() {
        jdbc.update("DELETE FROM report_delivery_audit");
        jdbc.update("DELETE FROM report_delivery_run");
        jdbc.update("DELETE FROM report_artifact");
    }

    @Test
    void redeliveryUsesSameStoredArtifactAndDoesNotRegenerate() {
        AtomicInteger generations = new AtomicInteger();
        byte[] content = "phase-ii-report".getBytes(StandardCharsets.UTF_8);
        ReportArtifactGenerator generator = new ReportArtifactGenerator() {
            @Override
            public String reportType() {
                return "TEST_REPORT";
            }

            @Override
            public ReportArtifact generate(String participant, String generationKey) {
                generations.incrementAndGet();
                return new ReportArtifact(
                        reportType(),
                        participant,
                        generationKey,
                        "phase-ii-report.txt",
                        "text/plain",
                        content);
            }
        };
        ReportArtifactService service = new ReportArtifactService(
                jdbc,
                List.of(generator));

        ReportArtifactService.StoredArtifact first = service.getOrGenerate(
                "TEST_REPORT", "BANK_A", "2026-06-22T00:00:00Z", 30);
        ReportArtifactService.StoredArtifact replay = service.getOrGenerate(
                "TEST_REPORT", "BANK_A", "2026-06-22T00:00:00Z", 30);

        assertEquals(first.id(), replay.id());
        assertEquals(first.sha256(), replay.sha256());
        assertArrayEquals(content, replay.content());
        assertEquals(1, generations.get());
    }
}
