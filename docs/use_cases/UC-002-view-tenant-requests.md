# Use Case: Mieteranliegen anzeigen

## Overview

**Use Case ID:** UC-002  
**Use Case Name:** Mieteranliegen anzeigen  
**Primary Actor:** Immobilienbewirtschaftung  
**Goal:** Eingegangene Mieteranliegen in einer übersichtlichen Liste sichten und ein Anliegen zur Detailprüfung auswählen.  
**Status:** Planned

## Preconditions

- Die Immobilienbewirtschaftung hat Zugriff auf das PropertyFlow-Cockpit.
- Im System können null, ein oder mehrere Mieteranliegen vorhanden sein.

## Main Success Scenario

1. Die Immobilienbewirtschaftung öffnet das Cockpit.
2. Das System zeigt die vorhandenen Mieteranliegen als Liste.
3. Für jedes Anliegen zeigt das System mindestens Referenz, Kurzbeschreibung, Dringlichkeit und Bearbeitungsstatus.
4. Die Immobilienbewirtschaftung wählt ein Anliegen aus.
5. Das System öffnet die Detailansicht des gewählten Anliegens (UC-003).

## Alternative Flows

### A1: Keine Anliegen vorhanden

**Trigger:** Es sind keine Mieteranliegen vorhanden.

1. Das System zeigt eine verständliche Leermeldung.
2. Es wird keine leere oder irreführende Tabelle dargestellt.
3. Der Use Case endet.

### A2: Anliegen kann nicht geladen werden

**Trigger:** Die Liste kann wegen eines technischen Fehlers nicht geladen werden.

1. Das System zeigt eine verständliche Fehlermeldung.
2. Die Benutzerin oder der Benutzer kann den Ladevorgang erneut versuchen.

## Postconditions

### Success Postconditions

- Die vorhandenen Anliegen sind sichtbar.
- Nach Auswahl eines Eintrags ist dessen Detailansicht geöffnet.

### Failure Postconditions

- Es werden keine unvollständigen oder irreführenden Daten als erfolgreich geladen dargestellt.

## Business Rules

### BR-001: Dringlichkeit verständlich darstellen

Die Dringlichkeit eines Anliegens darf nicht ausschliesslich durch Farbe kommuniziert werden; ihre Bedeutung muss zusätzlich textlich erkennbar sein.

### BR-002: Bearbeitungsstatus sichtbar

Der aktuelle Bearbeitungsstatus muss in der Listenansicht schnell erkennbar sein.
