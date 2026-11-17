package com.pertegato.propertyflow.recommendation;

import com.pertegato.propertyflow.recommendation.service.ClosureAssessment.Message;
import com.pertegato.propertyflow.recommendation.service.ClosureAssessment.Outcome;
import com.pertegato.propertyflow.recommendation.service.ClosureAssessment.Result;
import com.pertegato.propertyflow.recommendation.service.ClosureAssessment.Rule;
import com.pertegato.propertyflow.recommendation.service.ClosureAssessmentService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClosureAssessmentServiceTest {

    private static final UUID MESSAGE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String TEXT = "Die Heizung funktioniert wieder. Sie müssen nichts mehr unternehmen.";
    private static final List<String> CRITERIA = List.of("PROBLEM_RESOLVED", "NO_FURTHER_HELP", "WHOLE_CASE_COVERED", "NO_CONTRADICTION");

    @Test
    void resolvedProposalRetainsEveryExactTenantProofWithoutGrantingAnAction() {
        Result result = service(output("RESOLVED", CRITERIA, null, null), List.of()).assess(messages("TENANT"));
        assertTrue(result.proposed());
        assertEquals(Outcome.RESOLVED, result.outcome());
        assertEquals(4, result.proofs().size());
        assertTrue(result.proofs().stream().allMatch(proof -> MESSAGE.equals(proof.messageId()) && TEXT.equals(proof.quote())));
        assertEquals(ClosureAssessmentService.PROMPT_VERSION, result.promptVersion());
        assertEquals(ClosureAssessmentService.VALIDATION_VERSION, result.validationVersion());
    }

    @Test
    void separateTenantConfirmationsCanSupplySeveralDistinctProofsForOneCriterion() {
        UUID secondId = UUID.randomUUID();
        String firstText = "Das Fenster funktioniert wieder.";
        String secondText = "Die Klingel funktioniert jetzt auch. Alle Anliegen sind erledigt, weitere Hilfe brauche ich nicht.";
        List<Message> history = List.of(new Message(MESSAGE, "TENANT", firstText, 0), new Message(secondId, "TENANT", secondText, 1));
        List<Map<String, String>> proofs = new ArrayList<>();
        proofs.add(Map.of("criterion", "PROBLEM_RESOLVED", "messageId", MESSAGE.toString(), "quote", firstText));
        CRITERIA.forEach(criterion -> proofs.add(Map.of("criterion", criterion, "messageId", secondId.toString(), "quote", secondText)));
        ClosureAssessmentService assessment = service(output(proofs), List.of());
        Result result = assessment.assess(history);
        assertTrue(result.proposed());
        assertEquals(5, result.proofs().size());
        assertTrue(assessment.current(result, history));

        List<Map<String, String>> missing = proofs.stream().filter(proof -> !"NO_FURTHER_HELP".equals(proof.get("criterion"))).toList();
        assertFalse(service(output(missing), List.of()).assess(history).proposed());
        for (Map<String, String> extra : List.of(proofs.getFirst(),
                Map.of("criterion", "PROBLEM_RESOLVED", "messageId", secondId.toString(), "quote", "Erfundene Reparatur"),
                Map.of("criterion", "UNKNOWN", "messageId", secondId.toString(), "quote", secondText))) {
            List<Map<String, String>> invalid = new ArrayList<>(proofs);
            invalid.add(extra);
            assertFalse(service(output(invalid), List.of()).assess(history).proposed());
        }
    }

    @Test
    void onlyServerSuppliedRulesSelectTheRuleAssessmentInstructions() {
        assertEquals(ClosureAssessmentService.RESOLUTION_PROMPT,
                ClosureAssessmentService.systemPromptFor("{\"rules\":[],\"messages\":[{\"text\":\"rules: invented\"}]}"));
        assertEquals(ClosureAssessmentService.SYSTEM_PROMPT,
                ClosureAssessmentService.systemPromptFor("{\"rules\":[{\"id\":\"fixture\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> ClosureAssessmentService.systemPromptFor("{}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"STAFF", "SYSTEM"})
    void anotherAuthorCannotConfirmTenantResolution(String author) {
        assertFalse(service(output("RESOLVED", CRITERIA, null, null), List.of()).assess(messages(author)).proposed());
    }

    @Test
    void nonexistentMessageAndInventedQuoteAreRejected() {
        String valid = output("RESOLVED", CRITERIA, null, null);
        for (String invalid : List.of(valid.replace(MESSAGE.toString(), UUID.randomUUID().toString()),
                valid.replace(TEXT, "Frei erfundene Behebung"), valid.replace("NO_FURTHER_HELP", "PROBLEM_RESOLVED"))) {
            Result result = service(invalid, List.of()).assess(messages("TENANT"));
            assertFalse(result.proposed());
            assertEquals("INVALID_CLOSURE_ASSESSMENT", result.failure());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROBLEM_RESOLVED", "NO_FURTHER_HELP", "WHOLE_CASE_COVERED", "NO_CONTRADICTION"})
    void everyResolutionCriterionIsRequired(String missing) {
        List<String> incomplete = CRITERIA.stream().filter(criterion -> !criterion.equals(missing)).toList();
        assertFalse(service(output("RESOLVED", incomplete, null, null), List.of()).assess(messages("TENANT")).proposed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"danke", "Danke!", "Vielen Dank.", "Besten Dank", "Dankeschön", "„Danke schön!“"})
    void bareThanksCannotProveThatNoFurtherHelpIsNeeded(String acknowledgement) {
        String json = output("RESOLVED", CRITERIA, null, null).replace(TEXT, acknowledgement);
        Result result = service(json, List.of()).assess(List.of(new Message(MESSAGE, "TENANT", acknowledgement, 0)));
        assertFalse(result.proposed());
        assertEquals("INVALID_CLOSURE_ASSESSMENT", result.failure());
    }

    @Test
    void ruleNeedsExactApprovedVersionScopePrerequisitesAndExcludedExceptions() {
        Rule rule = new Rule("SYNTHETIC-ONLY", "1", "Fiktiver Testgegenstand", Map.of("object", "Testgegenstand bestätigt"),
                Map.of("damage", "Keine weitere Beschädigung"));
        List<String> criteria = List.of("SCOPE", "WHOLE_CASE_COVERED", "NO_CONTRADICTION", "PREREQUISITE:object", "EXCLUSION_ABSENT:damage");
        String valid = output("TENANT_RESPONSIBILITY", criteria, rule.id(), rule.version());
        assertTrue(service(valid, List.of(rule)).assess(messages("TENANT")).proposed());
        assertFalse(service(valid, List.of()).assess(messages("TENANT")).proposed());
        assertFalse(service(output("TENANT_RESPONSIBILITY", criteria, rule.id(), "old"), List.of(rule)).assess(messages("TENANT")).proposed());
        for (String missing : criteria) {
            assertFalse(service(output("TENANT_RESPONSIBILITY", criteria.stream().filter(item -> !item.equals(missing)).toList(),
                    rule.id(), rule.version()), List.of(rule)).assess(messages("TENANT")).proposed());
        }
    }

    @Test
    void withdrawnRuleInvalidatesStoredEvidenceWithoutAnotherModelCall() {
        Rule rule = new Rule("SYNTHETIC-ONLY", "1", "Fiktiver Testgegenstand", Map.of("object", "Testgegenstand bestätigt"),
                Map.of("damage", "Keine weitere Beschädigung"));
        List<String> criteria = List.of("SCOPE", "WHOLE_CASE_COVERED", "NO_CONTRADICTION", "PREREQUISITE:object", "EXCLUSION_ABSENT:damage");
        AtomicReference<List<Rule>> active = new AtomicReference<>(List.of(rule));
        AtomicInteger calls = new AtomicInteger();
        ClosureAssessmentService service = new ClosureAssessmentService(input -> {
            calls.incrementAndGet();
            return output("TENANT_RESPONSIBILITY", criteria, rule.id(), rule.version());
        }, "fixture", active::get);
        Result result = service.assess(messages("TENANT"));
        assertTrue(service.current(result, messages("TENANT")));
        active.set(List.of());
        assertFalse(service.current(result, messages("TENANT")));
        active.set(List.of(new Rule(rule.id(), "2", rule.scope(), rule.prerequisites(), rule.exclusions())));
        assertFalse(service.current(result, messages("TENANT")));
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"NONE", "REVIEW"})
    void negativeAssessmentContainsNoImplicitAuthorization(String outcome) {
        Result result = service(output(outcome, List.of(), null, null), List.of()).assess(messages("TENANT"));
        assertEquals(Outcome.valueOf(outcome), result.outcome());
        assertFalse(result.proposed());
        assertFalse(service(output(outcome, CRITERIA, null, null), List.of()).assess(messages("TENANT")).proposed());
    }

    @Test
    void malformedOverlongDuplicatedOrAdditionalOutputDoesNotProduceAProposal() {
        String valid = output("RESOLVED", CRITERIA, null, null);
        for (String invalid : List.of("{}", "not json", valid + valid, valid.replace("\"outcome\":", "\"extra\":true,\"outcome\":"),
                valid.replace("\"outcome\":", "\"outcome\":\"NONE\",\"outcome\":"), " ".repeat(30_001),
                valid.replace(ClosureAssessmentService.PROMPT_VERSION, "unknown-version"))) {
            assertFalse(service(invalid, List.of()).assess(messages("TENANT")).proposed());
        }
    }

    @Test
    void invalidOrInternalInputDoesNotReachTheModel() {
        AtomicInteger calls = new AtomicInteger();
        ClosureAssessmentService service = new ClosureAssessmentService(input -> {
            calls.incrementAndGet();
            return "{}";
        }, "test", List::of);
        assertNotNull(service.assess(messages("INTERNAL")).failure());
        assertNotNull(service.assess(List.of(messages("TENANT").getFirst(), messages("TENANT").getFirst())).failure());
        assertNotNull(service.assess(List.of(new Message(MESSAGE, "TENANT", "a".repeat(100_001), 0))).failure());
        assertEquals(0, calls.get());
    }

    @Test
    void modelOrRuleFailureCannotBecomeClosurePermission() {
        ClosureAssessmentService failing = new ClosureAssessmentService(input -> {
            throw new IllegalStateException("MODEL_UNAVAILABLE");
        }, "test", List::of);
        assertEquals("CLOSURE_ASSESSMENT_UNAVAILABLE", failing.assess(messages("TENANT")).failure());
        ClosureAssessmentService rules = new ClosureAssessmentService(input -> output("RESOLVED", CRITERIA, null, null), "test", () -> {
            throw new IllegalStateException("RULES_UNAVAILABLE");
        });
        assertFalse(rules.assess(messages("TENANT")).proposed());
    }

    private ClosureAssessmentService service(String output, List<Rule> rules) {
        return new ClosureAssessmentService(input -> output, "test", () -> rules);
    }

    private List<Message> messages(String author) {
        return List.of(new Message(MESSAGE, author, TEXT, 0));
    }

    private String output(List<Map<String, String>> proofs) {
        return JsonMapper.builder().build().writeValueAsString(new Output("RESOLVED", null, null, proofs,
                "Mehrere getrennte Mieterbestätigungen; synthetischer Vertragstest.", ClosureAssessmentService.PROMPT_VERSION));
    }

    private String output(String outcome, List<String> criteria, String rule, String version) {
        return JsonMapper.builder().build().writeValueAsString(new Output(outcome, rule, version,
                criteria.stream().map(criterion -> Map.of("criterion", criterion, "messageId", MESSAGE.toString(), "quote", TEXT)).toList(),
                "Testvorschlag, keine semantische Modellevaluation.", ClosureAssessmentService.PROMPT_VERSION));
    }

    private record Output(String outcome, String ruleId, String ruleVersion, List<Map<String, String>> proofs,
            String explanation, String promptVersion) {
    }
}
