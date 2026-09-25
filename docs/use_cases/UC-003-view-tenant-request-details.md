# Use Case: Mieteranliegen im Detail anzeigen

## Overview

**Use Case ID:** UC-003  
**Use Case Name:** Mieteranliegen im Detail anzeigen  
**Primary Actor:** Immobilienbewirtschaftung  
**Goal:** Ein ausgewähltes Mieteranliegen mit den für die fachliche Prüfung relevanten Informationen anzeigen.  
**Status:** Planned

## Preconditions

- Die Immobilienbewirtschaftung hat Zugriff auf das PropertyFlow-Cockpit.
- Ein Mieteranliegen wurde in UC-002 ausgewählt oder direkt über seine Referenz aufgerufen.

## Main Success Scenario

1. Die Immobilienbewirtschaftung öffnet ein Mieteranliegen.
2. Das System zeigt die erfassten Angaben des Anliegens.
3. Das System zeigt den aktuellen Bearbeitungsstatus.
4. Das System zeigt den Bereich für die KI-Analyse.
5. Falls bereits ein Analyseergebnis vorhanden ist, wird dieses als unterstützende Information angezeigt.
6. Die Immobilienbewirtschaftung kann die KI-Analyse starten (UC-004).

## Alternative Flows

### A1: Anliegen nicht gefunden

**Trigger:** Für die angeforderte Referenz existiert kein Anliegen.

1. Das System zeigt eine verständliche Meldung, dass das Anliegen nicht gefunden wurde.
2. Das System bietet einen Weg zurück zum Cockpit.
3. Der Use Case endet.

### A2: KI-Analyse noch nicht vorhanden

**Trigger:** Für das Anliegen liegt noch kein Analyseergebnis vor.

1. Das System zeigt den KI-Analysebereich im Zustand „Bereit“.
2. Die Immobilienbewirtschaftung kann die Analyse starten (UC-004).

## Postconditions

### Success Postconditions

- Die fachlich relevanten Angaben des ausgewählten Anliegens sind sichtbar.
- Die KI-Analyse kann aus der Detailansicht gestartet werden.

### Failure Postconditions

- Bei einer ungültigen Referenz werden keine Daten eines anderen Anliegens angezeigt.

## Business Rules

### BR-001: KI-Empfehlung ist keine Entscheidung

Eine KI-Ausgabe wird als Empfehlung beziehungsweise Unterstützung dargestellt. Die abschliessende fachliche Entscheidung verbleibt bei der Immobilienbewirtschaftung.

### BR-002: Technische KI-Details stehen nicht im Vordergrund

Die Detailansicht priorisiert fachlich verständliche Informationen wie Dringlichkeit, Empfehlung, Zuständigkeit und Bearbeitungsstatus.
