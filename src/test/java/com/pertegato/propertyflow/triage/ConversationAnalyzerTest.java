package com.pertegato.propertyflow.triage;

import com.pertegato.propertyflow.triage.service.ConversationAnalysis.Input;
import com.pertegato.propertyflow.triage.service.ConversationAnalysis.Message;
import com.pertegato.propertyflow.triage.service.ConversationAnalysis.Question;
import com.pertegato.propertyflow.triage.service.ConversationAnalysis.Result;
import com.pertegato.propertyflow.triage.service.ConversationAnalyzer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConversationAnalyzerTest {

    private final UUID question = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final UUID message = UUID.randomUUID();

    @Test
    void partialAnswerResolvesOnlyTheQuestionWithItsOwnEvidence() {
        Result result = analyze(output(question, message, "Seit gestern"), input("TENANT", 3));
        assertNull(result.failure());
        assertEquals(1, result.answers().size());
        assertEquals(question, result.answers().getFirst().questionId());
    }

    @Test
    void rejectsInventedEvidenceAndWrongAuthorAndEarlierMessage() {
        assertEquals("INVALID_MODEL_OUTPUT", analyze(output(question, message, "Seit einem Jahr"), input("TENANT", 3)).failure());
        assertEquals("INVALID_MODEL_OUTPUT", analyze(output(question, message, "Seit gestern"), input("STAFF", 3)).failure());
        assertEquals("INVALID_MODEL_OUTPUT", analyze(output(question, message, "Seit gestern"), input("TENANT", 0)).failure());
    }

    @Test
    void rejectsUnknownOrDuplicateQuestionsAndIncompleteCoverage() {
        assertEquals("INVALID_MODEL_OUTPUT", analyze(output(UUID.randomUUID(), message, "Seit gestern"), input("TENANT", 3)).failure());
        String duplicate = output(question, message, "Seit gestern").replace(other.toString(), question.toString());
        assertEquals("INVALID_MODEL_OUTPUT", analyze(duplicate, input("TENANT", 3)).failure());
        assertEquals("INVALID_MODEL_OUTPUT", analyze("{\"urgency\":\"NORMAL\",\"humanRequired\":false,\"questions\":[]}",
                input("TENANT", 3)).failure());
    }

    @Test
    void providerFailureHasNoInventedUrgencyOrAnswers() {
        Result result = new ConversationAnalyzer(text -> {
            throw new IllegalStateException("provider unavailable");
        }, "test").analyze(input("TENANT", 3));
        assertEquals("MODEL_UNAVAILABLE", result.failure());
        assertNull(result.urgency());
        assertEquals(List.of(), result.answers());
    }

    private Result analyze(String output, Input input) {
        return new ConversationAnalyzer(text -> output, "test").analyze(input);
    }

    private Input input(String author, long sequence) {
        return new Input(List.of(new Message(message, author, "Seit gestern ist die Heizung kalt.", sequence)),
                List.of(new Question(question, "Seit wann?", 1), new Question(other, "Welcher Raum?", 2)));
    }

    private String output(UUID questionId, UUID messageId, String evidence) {
        return """
                {"urgency":"HIGH","humanRequired":false,"questions":[
                {"questionId":"%s","answered":true,"messageId":"%s","evidence":"%s"},
                {"questionId":"%s","answered":false}]}
                """.formatted(questionId, messageId, evidence, other);
    }
}
