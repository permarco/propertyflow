# Use Case: KI-Analyse eines Mieteranliegens ausführen

## Overview

**Use Case ID:** UC-004  
**Use Case Name:** KI-Analyse ausführen  
**Primary Actor:** Immobilienbewirtschaftung  
**Goal:** Eine KI-gestützte Analyse eines Mieteranliegens starten, den Fortschritt nachvollziehen und das Ergebnis kontrolliert anzeigen lassen.  
**Status:** Planned

## Preconditions

- Die Immobilienbewirtschaftung befindet sich in der Detailansicht eines Mieteranliegens (UC-003).
- Das Anliegen enthält die für den Analyseversuch erforderlichen Eingaben.

## Main Success Scenario

1. Die Immobilienbewirtschaftung startet die KI-Analyse.
2. Das System wechselt vom Zustand `idle` nach `waiting` und zeigt sichtbar an, dass die Analyse angefordert wurde.
3. Sobald erste Antwortteile eintreffen, wechselt das System nach `streaming`.
4. Das System zeigt die Antwort schrittweise als Text an.
5. Nach vollständiger Antwort wechselt das System nach `completed`.
6. Das vollständige Analyseergebnis bleibt in der Detailansicht sichtbar.
7. Die Immobilienbewirtschaftung kann das Ergebnis fachlich prüfen.

## Alternative Flows

### A1: Langsame Antwort

**Trigger:** Nach dem Start liegt noch kein Antwortteil vor.

1. Das System bleibt in `waiting`.
2. Der aktuelle Status bleibt sichtbar.
3. Die Immobilienbewirtschaftung kann die laufende Analyse abbrechen.
4. Sobald erste Daten eintreffen, wird der Main Success Scenario bei Schritt 3 fortgesetzt.

### A2: Analyse wird abgebrochen

**Trigger:** Die Immobilienbewirtschaftung wählt während `waiting` oder `streaming` „Abbrechen“.

1. Das System beendet den laufenden Analysevorgang wirksam.
2. Nach dem Abbruch werden keine weiteren Antwortteile angezeigt.
3. Das System wechselt nach `aborted`.
4. Der Abbruchstatus wird sichtbar angezeigt.
5. Die Analyse kann erneut gestartet werden.

### A3: Fehler beim KI-Aufruf

**Trigger:** Der KI-Aufruf schlägt fehl oder liefert innerhalb des vorgesehenen Zeitraums keine verwertbare Antwort.

1. Das System beendet den laufenden Analysevorgang.
2. Das System wechselt nach `error`.
3. Das System zeigt eine verständliche Fehlermeldung.
4. Bereits vorhandene Daten des Mieteranliegens bleiben unverändert verfügbar.
5. Die Immobilienbewirtschaftung kann die Analyse erneut starten.

### A4: Antwort enthält Markup

**Trigger:** Die KI-Antwort enthält HTML, Skriptcode oder anderes Markup.

1. Das System behandelt die gesamte KI-Ausgabe als nicht vertrauenswürdigen Inhalt.
2. Markup wird als Text dargestellt und nicht als ausführbarer Inhalt interpretiert.
3. Der Main Success Scenario wird normal fortgesetzt.

## Postconditions

### Success Postconditions

- Das System befindet sich in `completed`.
- Das Analyseergebnis ist als Text sichtbar und kann von der Immobilienbewirtschaftung geprüft werden.

### Failure Postconditions

- Bei Abbruch befindet sich das System in `aborted`.
- Bei einem Fehler befindet sich das System in `error`.
- Ein Fehler oder Abbruch verändert das Mieteranliegen nicht und verhindert dessen manuelle Bearbeitung nicht.

## Business Rules

### BR-001: Explizites Zustandsmodell

Die Präsentationsschicht bildet die Zustände `idle → waiting → streaming → completed | aborted | error` explizit ab.

### BR-002: Wirksamer Abbruch

Ein Abbruch verändert nicht nur die Anzeige, sondern beendet den laufenden Analysevorgang. Nach dem Abbruch dürfen keine weiteren Antwortteile verarbeitet werden.

### BR-003: Sichere Textausgabe

Benutzer- und KI-generierte Inhalte werden standardmässig als Text dargestellt. Ungeprüftes HTML oder Skript-Markup darf nicht ausgeführt werden.

### BR-004: KI-Ausfall blockiert die Fachbearbeitung nicht

Ein Fehler oder eine nicht verfügbare KI darf die manuelle Prüfung und weitere Bearbeitung des Mieteranliegens nicht verhindern.
