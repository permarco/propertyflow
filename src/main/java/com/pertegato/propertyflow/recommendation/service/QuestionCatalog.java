package com.pertegato.propertyflow.recommendation.service;

import java.util.Map;

/**
 * Veröffentlichbare Sachfragen aus der ausdrücklich freigegebenen Fassung von KOM-05.
 */
public final class QuestionCatalog {

    public static final String VERSION = "KOM-05-2026-11-14";
    public static final Map<String, String> QUESTIONS = Map.of(
            "RQ-001", "Seit wann besteht die Störung?",
            "RQ-002", "In welchem Raum tritt sie auf?",
            "RQ-003", "Tritt die Störung ständig oder zeitweise auf?",
            "RQ-004", "Welche Fehlermeldung wird angezeigt?");

    private QuestionCatalog() {
    }
}
