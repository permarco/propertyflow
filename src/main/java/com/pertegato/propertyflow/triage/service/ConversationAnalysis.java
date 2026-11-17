package com.pertegato.propertyflow.triage.service;

import java.util.List;
import java.util.UUID;

/**
 * Public conversation only: callers must exclude access data and internal notes.
 */
public interface ConversationAnalysis {

    Result analyze(Input input);

    record Message(UUID id, String author, String text, long sequence) {
    }

    record Question(UUID id, String text, long sequence) {
    }

    record Input(List<Message> messages, List<Question> questions) {
        public Input {
            messages = List.copyOf(messages);
            questions = List.copyOf(questions);
        }

        @Override
        public String toString() {
            return "Conversation input [redacted]";
        }
    }

    record Answer(UUID questionId, UUID messageId, String evidence) {
    }

    record Result(String urgency, boolean humanRequired, List<Answer> answers, String failure,
            String model, String promptVersion) {
        public Result {
            answers = List.copyOf(answers);
        }
    }
}
