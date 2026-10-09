# Use Case: Mieteranliegen erfassen

## Overview

**Use Case ID:** UC-001  
**Use Case Name:** Mieteranliegen erfassen  
**Primary Actor:** Mieterin / Mieter  
**Goal:** Ein Mieteranliegen erfassen und seine dauerhafte Annahme mit Case-ID erkennen.  
**Status:** Planned

**Fachliche Grundlage:** [Fallverwaltung](../specifications/fallverwaltung.md), insbesondere FALL-01 bis FALL-04. Darstellung und Web-Routen beschreibt [Screen 01](../frontend/ansicht-01-mieter-fallansicht.md); Nachrichtenregeln beschreibt die [Fallkommunikation](../specifications/fallkommunikation.md). Persönlicher Tokenlink und sicherer Erstzugriff folgen [Fallzugriff und Sicherheit](../specifications/fallzugriff-und-sicherheit.md), insbesondere ZUG-01 bis ZUG-03.

Dieser Use Case beschreibt die Web-Erfassung. Die sichere Annahme und Zuordnung eingehender E-Mails benötigt einen eigenen Kanalvertrag unter denselben fachlichen Fallregeln.

## Preconditions

- PropertyFlow ist erreichbar.
- Die Erfassungsmaske kann ohne Benutzerkonto geöffnet werden.

## Main Success Scenario

1. Die Mieterin oder der Mieter öffnet die Erfassungsmaske.
2. Das System zeigt das Formular für ein neues Anliegen; das Öffnen legt keinen Fall an.
3. Die Mieterin oder der Mieter erfasst Betreff, Beschreibung, Objekt-/Wohnungsreferenz und E-Mail-Adresse.
4. Die Mieterin oder der Mieter sendet das Formular ab.
5. Das System prüft die Angaben serverseitig und behandelt Wiederholungen gemäss FALL-03.
6. Das System nimmt das Anliegen dauerhaft gemäss FALL-02 an und vergibt eine Case-ID. Die zuverlässige Übergabe an Camunda und die Bestätigungsbenachrichtigung werden veranlasst.
7. Das System bestätigt die Speicherung und öffnet unmittelbar die aktive Fallansicht mit Case-ID; das Öffnen der E-Mail ist dafür nicht erforderlich.
8. Der persönliche Fall-Link mit ausschliesslich einem geheimen Token als Zugangswert wird zusätzlich per E-Mail versendet. PropertyFlow ordnet den Token serverseitig dem Fall zu; die lesbare Case-ID bleibt in der Ansicht. Der Versandzustand ist vom fachlichen Annahmeerfolg getrennt. Auslöser, Inhalt und Fehlerbehandlung folgen [Benachrichtigungen und Zustellung](../specifications/benachrichtigungen-und-zustellung.md).

## Alternative Flows

### A1: Pflichtfeld fehlt oder Eingabe ist ungültig

**Trigger:** Die serverseitige Prüfung lehnt die Eingabe ab.

1. Das System nimmt das Anliegen nicht an.
2. Die betroffenen Felder erhalten verständliche Fehlermeldungen; bereits eingegebene Werte bleiben erhalten.
3. Nach der Korrektur wird der Use Case bei Schritt 4 fortgesetzt.

### A2: Technischer Fehler vor dauerhafter Annahme

**Trigger:** Es ist bekannt, dass die Speicherung nicht erfolgreich war.

1. Das System bestätigt keine erfolgreiche Erfassung und zeigt einen verständlichen Fehlerhinweis.
2. Die Mieterin oder der Mieter kann einen sicheren erneuten Versuch auslösen.

### A3: Ausgang nach dem Absenden unklar

**Trigger:** Die Antwort geht verloren oder ein Timeout lässt offen, ob bereits gespeichert wurde.

1. Die Oberfläche erklärt den unklaren Ausgang; sie behauptet weder Erfolg noch eine sicher fehlgeschlagene Speicherung.
2. Eine Wiederholung verwendet dieselbe Absende-ID gemäss FALL-03.
3. Falls bereits angenommen, liefert das System die bestehende Case-ID und erzeugt keinen zweiten Fall. Andernfalls kann die gültige Einreichung einmalig angenommen werden.

### A4: Nachgelagerte Komponente nicht verfügbar

**Trigger:** Der Fall ist gespeichert, aber Camunda-Prozessstart, Mailversand oder KI-Verarbeitung können vorübergehend nicht abgeschlossen werden.

1. Die Annahmebestätigung und Case-ID bleiben gültig.
2. Technische Folgeaufträge werden gemäss dem Integrationsvertrag wiederholt oder zur Bearbeitung sichtbar gemacht.
3. Der Fall bleibt nach FALL-06 aktiv und für manuelle Bearbeitung zugänglich. Die Oberfläche zeigt nur den jeweils passenden fachlichen oder Versandhinweis.

### A5: Objektzuordnung noch unklar

**Trigger:** Die eingegebene Objekt-/Wohnungsreferenz kann noch nicht eindeutig den Stammdaten zugeordnet werden.

1. Das gültige Anliegen wird gemäss FALL-04 angenommen.
2. Die offene Zuordnung bleibt für die interne Bearbeitung erkennbar. Die Ursprungseingabe bleibt erhalten.

## Postconditions

### Success Postconditions

- Der Fall wurde dauerhaft angenommen; Case-ID und Ursprungseingabe sind gespeichert.
- Der Fall befindet sich in der aktiven Phase. Der Camunda-Prozessstart kann noch ausstehen.
- Die Mieterin oder der Mieter sieht die Bestätigung und kann den angelegten Fall direkt unter dem gültigen persönlichen Tokenlink öffnen. Die Case-ID ist im Inhalt sichtbar; der GET-Fallaufruf leitet nicht auf eine andere Falladresse weiter.
- Nachgelagerte Verarbeitung und Benachrichtigung sind zuverlässig veranlasst; ihr Abschluss ist keine Voraussetzung für den Annahmeerfolg.

### Failure Postconditions

- Bei sicher gescheiterter Annahme wird kein Erfolg bestätigt.
- Bei unklarem Ausgang bleibt eine sichere Wiederholung möglich; ein bereits gespeicherter Fall wird weder verworfen noch doppelt angelegt.
- Ein Fehler nach Annahme macht den gespeicherten Fall nicht zu einer fehlgeschlagenen Erfassung.

## Business Rules

### BR-001: Verständliche Validierung

Die Eingabeprüfung folgt [FALL-02](../specifications/fallverwaltung.md#fall-02-erfolgreiche-annahme). Die Oberfläche ordnet Fehler den betroffenen Feldern zu; konkrete Labels und Hilfstexte stehen in Screen 01.

### BR-002: Keine KI-Abhängigkeit für die Erfassung

Die Annahme und das Verhalten bei ausgefallenen Folgekomponenten richten sich nach [FALL-02](../specifications/fallverwaltung.md#fall-02-erfolgreiche-annahme), [FALL-06](../specifications/fallverwaltung.md#fall-06-fachlicher-status-und-technischer-workflow) und [FALL-09](../specifications/fallverwaltung.md#fall-09-fehler-und-grenzfälle).

### BR-003: Eindeutige Fallidentität

Wiederholung, Ursprungsdaten und Zuordnung folgen FALL-01, FALL-03 und FALL-04. Die fachlichen Prüfkriterien FALL-AK-01 bis FALL-AK-04 werden in der zentralen [Fallverwaltung](../specifications/fallverwaltung.md#prüfkriterien) gepflegt.
