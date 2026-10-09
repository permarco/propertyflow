# Use Case: Mieteranliegen im Detail anzeigen

## Overview

**Use Case ID:** UC-003  
**Use Case Name:** Mieteranliegen im Detail anzeigen  
**Primary Actor:** Immobilienbewirtschaftung  
**Goal:** Ein ausgewähltes Mieteranliegen mit den für die fachliche Prüfung relevanten Informationen anzeigen.  
**Status:** Planned

## Preconditions

- Die Immobilienbewirtschaftung ist separat für das Backoffice authentifiziert; alle Mitarbeitenden haben dieselben Adminrechte gemäss ZUG-04.
- Ein Mieteranliegen wurde in UC-002 ausgewählt oder direkt über seine Referenz aufgerufen.

## Main Success Scenario

1. Die Immobilienbewirtschaftung öffnet ein Mieteranliegen.
2. Das System zeigt die erfassten Angaben des Anliegens.
3. Das System zeigt den aktuellen Bearbeitungsstatus.
4. Das System zeigt den Bereich für die KI-Analyse.
5. Falls bereits ein Analyseergebnis vorhanden ist, wird dieses als unterstützende Information angezeigt.
6. Die Immobilienbewirtschaftung kann die KI-Analyse starten (UC-004).

Die Auswahl aus [Screen 02](../frontend/ansicht-02-mitarbeiter-falluebersicht.md) öffnet eine eigene Detailseite. Deren konkrete Gestaltung und Rücknavigation werden separat spezifiziert. Die bereits vereinbarte manuelle Dringlichkeitsänderung folgt [DRING-06](../specifications/dringlichkeitsbewertung.md#dring-06-manuelle-anpassung): Systembewertungen dürfen die manuelle Einstufung nicht überschreiben; wirksame Änderungen werden nach DRING-07 historisiert und dem Mieter angezeigt. Diese Fachregel legt noch keine zusätzlichen Bedienelemente oder Detailanordnung fest.

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

Eine KI-Ausgabe wird als Empfehlung beziehungsweise Unterstützung dargestellt. Vorgeschriebene menschliche Prüfungen und die fachliche Verantwortung verbleiben bei der Immobilienbewirtschaftung. Mitarbeitende können Fälle abschliessen; die vereinbarten Systemabschlüsse folgen den gesonderten, serverseitig geprüften Fachregeln aus [FALL-07](../specifications/fallverwaltung.md#fall-07-abschluss-und-zeit-danach). Eine beliebige KI-Ausgabe ist keine Abschlussberechtigung. Die konkrete Bedienung des Mitarbeiterabschlusses wird mit Screen 03 spezifiziert.

### BR-002: Technische KI-Details stehen nicht im Vordergrund

Die Detailansicht priorisiert fachlich verständliche Informationen wie Dringlichkeit, Empfehlung, Zuständigkeit und Bearbeitungsstatus.

## Gemeinsame fachliche Grundlage

Fallidentität, fachliche Statusbedeutung und Abgrenzung zum technischen Camunda-Zustand sind in der [Fallverwaltung](../specifications/fallverwaltung.md) definiert, insbesondere FALL-01, FALL-05 und FALL-06. Die Ansicht verwendet diese fachliche Grundlage; ein technischer Incident ist kein eigener fachlicher Abschlussstatus. Die sieben fachlichen Bearbeitungsstatus sind unter FALL-05 vereinbart; konkrete Prozessübergänge und das BPMN-Mapping bleiben zu konkretisieren.
