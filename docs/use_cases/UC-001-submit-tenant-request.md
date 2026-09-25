# Use Case: Mieteranliegen erfassen

## Overview

**Use Case ID:** UC-001  
**Use Case Name:** Mieteranliegen erfassen  
**Primary Actor:** Mieterin / Mieter  
**Goal:** Ein Mieteranliegen vollständig erfassen und an PropertyFlow übermitteln.  
**Status:** Planned

## Preconditions

- PropertyFlow ist erreichbar.
- Die Erfassungsmaske kann ohne Anmeldung verwendet werden.

## Main Success Scenario

1. Die Mieterin oder der Mieter öffnet die Erfassungsmaske.
2. Das System zeigt ein Formular für das Mieteranliegen.
3. Die Mieterin oder der Mieter erfasst mindestens Betreff/Kurzbeschreibung, Beschreibung sowie Objekt- oder Wohnungsreferenz.
4. Die Mieterin oder der Mieter sendet das Formular ab.
5. Das System prüft die Pflichtfelder.
6. Das System übernimmt das Anliegen.
7. Das System zeigt eine Bestätigung mit einer Referenz des erfassten Anliegens.

## Alternative Flows

### A1: Pflichtfeld fehlt

**Trigger:** Beim Absenden fehlt mindestens ein Pflichtfeld.

1. Das System übernimmt das Anliegen nicht.
2. Das System markiert die betroffenen Felder mit verständlichen Fehlermeldungen.
3. Bereits eingegebene Werte bleiben erhalten.
4. Die Mieterin oder der Mieter korrigiert die Eingaben.
5. Der Use Case wird bei Schritt 4 des Main Success Scenario fortgesetzt.

### A2: Technischer Fehler beim Absenden

**Trigger:** Das Anliegen kann wegen eines technischen Fehlers nicht übernommen werden.

1. Das System zeigt eine verständliche Fehlermeldung.
2. Die Mieterin oder der Mieter kann die Eingabe erneut absenden.
3. Es wird keine erfolgreiche Erfassung bestätigt, solange das Anliegen nicht übernommen wurde.

## Postconditions

### Success Postconditions

- Das Anliegen wurde vom System angenommen.
- Eine Referenz für das Anliegen ist für die Benutzerin oder den Benutzer sichtbar.

### Failure Postconditions

- Es wird keine erfolgreiche Erfassung bestätigt.
- Die Benutzerin oder der Benutzer erhält einen verständlichen Fehlerhinweis.

## Business Rules

### BR-001: Verständliche Validierung

Pflichtfelder werden vor der Übernahme geprüft. Validierungsfehler werden am betroffenen Feld verständlich angezeigt.

### BR-002: Keine KI-Abhängigkeit für die Erfassung

Die Erfassung eines Mieteranliegens darf nicht davon abhängen, dass ein KI-Dienst verfügbar ist.
