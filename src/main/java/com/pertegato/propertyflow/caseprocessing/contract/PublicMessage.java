package com.pertegato.propertyflow.caseprocessing.contract;

import java.time.Instant;
import java.util.Objects;

public record PublicMessage(String id, PublicAuthor author, String text, Instant occurredAt, AnswerState answerState,
        Instant answerResolvedAt, String answerResolution) {

    public PublicMessage(String id, PublicAuthor author, String text, Instant occurredAt, AnswerState answerState) {
        this(id, author, text, occurredAt, answerState, null, null);
    }

    public PublicMessage(String id, PublicAuthor author, String text, Instant occurredAt) {
        this(id, author, text, occurredAt, AnswerState.NONE);
    }

    public PublicMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(answerState, "answerState");
    }

    public enum AnswerState {
        NONE,
        OPEN,
        ANSWERED,
        CANCELLED
    }
}
