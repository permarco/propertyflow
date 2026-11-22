# ADR-006 - Serverseitige Bestätigung wichtiger MCP-Schreibaktionen

## Status

**Entschieden am 17.11.2026. Umsetzung offen.**

## Kontext

Mieter und Mitarbeitende sollen über MCP im regulären PropertyFlow-Backend
arbeiten können. Der Agent handelt mit delegierten Rechten gemäss
[ADR-005](ADR-005-zugriffsmodell.md). Bei wichtigen Schreibaktionen muss
PropertyFlow die konkrete Zustimmung der handelnden Person prüfen können.

## Entscheidung

PropertyFlow nutzt MCP Elicitation im URL-Modus und holt die Bestätigung
auf einer eigenen Seite ein. Der Dienst bindet diese an die angemeldete
Person, die Delegation, die konkrete Aktion, das Ziel und die geprüften
Daten. Das delegierte Schreibrecht erlaubt die Vorbereitung. Der Dienst
prüft die Bestätigung und die aktuellen Voraussetzungen vor der Ausführung.

Für fachlich kritische Aktionen gilt die berechtigte Mitarbeiterentscheidung
nach [FALL-10](../../specifications/fallverwaltung.md#fall-10-automatisierung-und-menschliche-freigabe).

Die [Spezifikation der MCP-Werkzeuge](../../specifications/mcp-werkzeuge.md)
definiert Aktionen, Verträge, Ablauf und Prüffälle für Mieter und Bewirtschaftung.

## Alternativen und Konsequenzen

Ein Formular im Chat wäre mit Form-Mode-Elicitation möglich. PropertyFlow
würde für den Nachweis der Benutzerinteraktion auf den MCP-Client vertrauen.
Die Bestätigung auf der eigenen Seite bindet den Klick an die angemeldete
Person und lässt den Dienst die Freigabe selbst erzwingen. Der Preis dafür ist
ein zusätzlicher Seitenaufruf und ein befristeter Bestätigungsvorgang.
