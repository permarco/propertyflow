package com.pertegato.propertyflow.triage.service;

import java.util.Objects;

/**
 * Internal, side-effect-free analysis boundary; input must be minimized by its caller.
 */
public final class AnalyzeTool {

    public static final int MAX_TEXT_LENGTH = 4000;
    private static final String INVALID_INPUT_MESSAGE = "text muss 1 bis 4000 Zeichen enthalten und darf nicht leer sein.";
    private static final String MODEL_UNAVAILABLE_MESSAGE = "KI-Analyse derzeit nicht verfügbar. Bitte manuell prüfen.";
    private static final String INVALID_MODEL_OUTPUT_MESSAGE = "KI-Antwort entspricht nicht dem Vertrag. Bitte manuell prüfen.";

    private final AnalysisModel model;

    public AnalyzeTool(AnalysisModel model) {
        this.model = Objects.requireNonNull(model);
    }

    public record Request(String text) {
    }

    public enum Urgency {
        KRITISCH,
        HOCH,
        NORMAL,
        NIEDRIG
    }

    public enum ErrorCode {
        INVALID_INPUT,
        MODEL_UNAVAILABLE,
        INVALID_MODEL_OUTPUT
    }

    public sealed interface Response permits Success, Failure {
    }

    public record Success(Urgency urgency) implements Response {
    }

    public record Failure(ErrorCode code, String message) implements Response {
    }

    @FunctionalInterface
    public interface AnalysisModel {
        String analyze(String text);
    }

    public Response analyze(Request request) {
        if (request == null || request.text() == null || request.text().isBlank() || request.text().length() > MAX_TEXT_LENGTH) {
            return new Failure(ErrorCode.INVALID_INPUT, INVALID_INPUT_MESSAGE);
        }

        final String output;
        try {
            output = model.analyze(request.text().strip());
        } catch (RuntimeException exception) {
            // Provider messages can contain credentials, prompts and personal data.
            return new Failure(ErrorCode.MODEL_UNAVAILABLE, MODEL_UNAVAILABLE_MESSAGE);
        }

        try {
            if (output != null && output.length() <= 32) {
                return new Success(Urgency.valueOf(output.strip()));
            }
        } catch (IllegalArgumentException exception) {
            // Never replace an invalid assessment with a low/default urgency.
        }

        return new Failure(ErrorCode.INVALID_MODEL_OUTPUT, INVALID_MODEL_OUTPUT_MESSAGE);
    }
}
