package com.pertegato.propertyflow.access.service;

/**
 * Meldet eine Kontoverwaltungsaktion, die mit dem gespeicherten Kontostand kollidiert,
 * etwa eine bereits vergebene Adresse oder den Entzug des letzten Verwaltungskontos.
 * Die Controller bilden diesen Fall gemäss UC-007 und UC-029 als HTTP 409 ab;
 * übrige ungültige Eingaben ergeben weiterhin HTTP 400.
 */
public class AccountConflictException extends IllegalArgumentException {

    public AccountConflictException(String message) {
        super(message);
    }
}
