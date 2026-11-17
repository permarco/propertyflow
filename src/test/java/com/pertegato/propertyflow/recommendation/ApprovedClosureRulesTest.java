package com.pertegato.propertyflow.recommendation;

import com.pertegato.propertyflow.recommendation.knowledge.ApprovedClosureRules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovedClosureRulesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC);
    private static final String RULE = """
            {"approved":true,"id":"SYNTHETIC-ONLY","version":"1","automaticClosure":{
            "approved":true,"validFrom":"2026-01-01","validUntil":"2026-12-31",
            "scope":"Ausschliesslich fiktiver Testgegenstand",
            "prerequisites":{"object":"Testgegenstand bestätigt"},"exclusions":{"damage":"Weiterer Schaden"}}}
            """;
    @TempDir
    private Path directory;

    @Test
    void regularApprovedRagDocumentDoesNotAuthorizeClosure() throws Exception {
        Files.writeString(directory.resolve("knowledge.json"), "{\"approved\":true,\"id\":\"any\",\"text\":\"Mieterzuständigkeit\"}");
        assertTrue(rules().load().isEmpty());
        Files.writeString(directory.resolve("rule.json"), RULE);
        assertEquals(1, rules().load().size());
        assertEquals("1", rules().load().getFirst().version());
    }

    @Test
    void futureExpiredUnapprovedAndStringApprovedRulesAreUnavailable() throws Exception {
        for (String text : new String[] {RULE.replace("2026-01-01", "2026-12-01"), RULE.replace("2026-12-31", "2026-11-15"),
                RULE.replace("\"approved\":true", "\"approved\":false"), RULE.replace("\"approved\":true", "\"approved\":\"true\"")}) {
            Files.writeString(directory.resolve("rule.json"), text);
            assertTrue(rules().load().isEmpty());
        }
    }

    @Test
    void ambiguousIdentityAndMalformedApprovedRulesFailTheLoad() throws Exception {
        Files.writeString(directory.resolve("one.json"), RULE);
        Files.writeString(directory.resolve("two.json"), RULE.replace("\"version\":\"1\"", "\"version\":\"2\""));
        assertThrows(IllegalStateException.class, () -> rules().load());
        Files.delete(directory.resolve("two.json"));
        for (String text : new String[] {RULE.replace("2026-12-31", "2025-12-31"), RULE.replace("\"object\"", "\"invalid:key\""),
                RULE.replace("\"prerequisites\":{\"object\":\"Testgegenstand bestätigt\"}", "\"prerequisites\":{}")}) {
            Files.writeString(directory.resolve("one.json"), text);
            assertThrows(IllegalStateException.class, () -> rules().load());
        }
    }

    private ApprovedClosureRules rules() {
        return new ApprovedClosureRules(directory.toString(), CLOCK);
    }
}
