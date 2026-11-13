# Use Case: Fall fachlich steuern

## Overview

**Use Case ID:** UC-012  
**Use Case Name:** Fall fachlich steuern  
**Primary Actor:** Mitarbeitende der Immobilienbewirtschaftung  
**Supporting Actor:** Camunda-Fallprozess\
**Goal:** Nach einer Prüfung die Bearbeitung fortsetzen oder eine zulässige Dringlichkeits- oder Zuordnungsänderung veranlassen.\
**Status:** Zielablauf über Camunda. Die lokale Frontend-Demo enthält noch die bisherige direkte Statusauswahl und speichert Falländerungen im Arbeitsspeicher.

## Use-Case-Diagramm

```mermaid
flowchart LR
AKTEUR["Immobilienbewirtschaftung"]:::participant
CAMUNDA["Camunda-Fallprozess (System)"]:::participant

subgraph PF["PropertyFlow"]
    UC(["UC-012 · Fall fachlich steuern"]):::goal
end

AKTEUR --- UC
UC --- CAMUNDA
classDef participant fill:#1e293b,stroke:#94a3b8,color:#ffffff
classDef goal fill:#334155,stroke:#6d8eae,color:#ffffff
style PF fill:#1e293b,stroke:#006fbb,stroke-width:2px,color:#ffffff
```

## Preconditions

- Die Person ist persönlich angemeldet und hat die Mitarbeiterrolle. Der aktive Fall ist im Mitarbeiterdetail geöffnet. [UC-003](UC-003-mieteranliegen-details-anzeigen.md) · [ZUG-04 · Rollen und Zusatzrechte](../specifications/fallzugriff-und-sicherheit.md#rollen-und-zusatzrechte)
- Der führende Camunda-Fallprozess ist erreichbar und gibt die gewählte Aktion im aktuellen Prozessschritt für Mitarbeitende frei. [FALL-06](../specifications/fallverwaltung.md#fall-06-fachlicher-status-und-technischer-workflow)

## Main Success Scenario

1. Der Mitarbeiter prüft den aktuellen Fallstand und wählt eine vom Prozess freigegebene Aktion in UI3.
2. Bei «Mitarbeiterprüfung erforderlich» bestätigt er «Bearbeitung fortsetzen», wenn das weitere Vorgehen feststeht und die Verwaltung es umsetzt. Bei «Dringlichkeit ändern» wählt er eine der vier Stufen. Für die Objektzuordnung wählt er einen Vorschlag oder gibt die Zuordnung frei ein. Bei der Fortsetzung und der Dringlichkeit ist eine interne Begründung optional.
3. PropertyFlow prüft die Rolle, den aktuellen Fallstand und die Eingabe. Bei einer strukturierten Objektzuordnung prüft der Dienst zusätzlich die Zusammengehörigkeit von Objekt und Wohnung. PropertyFlow ermittelt die dem Fall zugeordnete Camunda-Prozessinstanz serverseitig.
4. PropertyFlow sendet die fachliche Aktion an Camunda und wartet synchron auf die bestätigte Korrelation mit der passenden wartenden Stelle dieser Instanz. Erst danach antwortet PropertyFlow dem Browser mit `200`. Das UI lädt den gespeicherten Fallstand neu.
5. Camunda veranlasst bei «Bearbeitung fortsetzen» den Wechsel auf «In Bearbeitung». Der zuständige PropertyFlow-Dienst speichert die Änderung mit dem fachlichen Nachweis. Fallübersicht und Falldetail zeigen den neuen Status nach der Speicherung. Sichtbare Status- und wirksame Dringlichkeitsänderungen lösen eine Benachrichtigung nach BEN-01 aus.

### Synchroner Vertrag für die Mitarbeiteraktion

Die zugeordnete Camunda-Prozessinstanz-ID (`processInstanceKey`) bestimmt das Ziel. Für die Nachrichtenkorrelation verwendet PropertyFlow den Prozessinstanzschlüssel als `correlationKey` und den Nachrichtennamen für die fachliche Aktion. Camunda ordnet die Aktion einer passenden wartenden Stelle des Fallprozesses zu. Der gleiche Vertrag gilt für Mitarbeiteraktionen, die einen bestehenden Fallprozess ändern. Der Start eines neuen Fallprozesses nach der Fallannahme erfolgt asynchron. [ADR-002](../architecture/adr/ADR-002-camunda8-workflow-orchestrierung.md#synchrone-nachrichtenkorrelation) · [SS-003](../architecture/schnittstellenentwurf-block-3.md#ss-003--camunda-integrationsvertrag)

| HTTP-Antwort von PropertyFlow nach Berechtigungsprüfung | Bedeutung |
| --- | --- |
| `200` | Camunda bestätigt die Korrelation mit der zugeordneten Prozessinstanz. Die Speicherung des neuen Fallstands durch den Worker kann noch ausstehen. |
| `400` | Die fachliche Aktion ist ungültig oder Camunda findet keine passende wartende Stelle für diese Instanz. |
| `500` | Die Korrelation kann wegen eines technischen Fehlers oder Timeouts nicht bestätigt werden. Bei einem Timeout kann Camunda die Aktion bereits übernommen haben. Das UI lädt den gespeicherten Stand neu. PropertyFlow wiederholt den Aufruf nicht automatisch. |

## Alternative Flows

- Bei Abbruch, unzulässiger Eingabe oder fehlender Berechtigung bleibt der bisherige Stand erhalten.
- Bei einem Versionskonflikt lädt UI3 den aktuellen Stand und zeigt den Konflikt. Nachrichteneingabe und laufende KI-Formulierung bleiben erhalten.
- Bei ausstehendem Prozessstart oder unerreichbarem Camunda ist die Ansicht lesend. Direkte Schreibaufrufe werden abgewiesen und der gespeicherte Fallstand bleibt erhalten.
- Für Wiedervorlage, Abschluss und Wiedereröffnung verwendet der Mitarbeiter die jeweiligen Aktionen. [UC-013](UC-013-wiedervorlage-verwalten.md) · [UC-014](UC-014-fall-abschliessen.md) · [UC-015](UC-015-fall-wiedereroeffnen.md)

## Postconditions

- Der bestätigte Wert und seine Änderungshistorie sind nachvollziehbar. Die ursprüngliche Objekt-/Wohnungsangabe des Mieters bleibt erhalten. Der gespeicherte Status folgt dem vom führenden Fallprozess veranlassten Übergang.

## Business Rules und Prüfkriterien

- **UC-012-BR-001** Bei erfolgreicher Fortsetzung nach der Mitarbeiterprüfung werden die geprüften Gründe als berücksichtigt in den internen Änderungsnachweis übernommen. Neue Erkenntnisse können erneut eine Prüfung erfordern. [FALL-06](../specifications/fallverwaltung.md#fall-06-fachlicher-status-und-technischer-workflow)
- **UC-012-BR-002** Eine manuelle Dringlichkeitsstufe hat Vorrang vor automatischen Ergebnissen. Eine Begründung bleibt intern. [DRING-06](../specifications/dringlichkeitsbewertung.md#dring-06-manuelle-anpassung)
- **UC-012-BR-003** Eine bestätigte Objektzuordnung klärt den zugehörigen Prüfhinweis. Kontaktadresse, Fall-Link und andere Prüfgründe bleiben erhalten. [FALL-04](../specifications/fallverwaltung.md#fall-04-ursprungsdaten-und-zuordnung)
- **UC-012-AK-01** Nach einer bestätigten Zuordnung zeigen UI2 und UI3 denselben Wert. Die ursprüngliche Angabe bleibt in der Detailansicht nachvollziehbar.
- **UC-012-AK-02** Zwei Mitarbeitende bearbeiten dieselbe Einstellung parallel. Der zweite Speicherversuch auf veraltetem Stand lässt den bestätigten Wert und seine Historie unverändert.
- **UC-012-AK-03** Nach manueller Einstufung wird ein abweichendes automatisches Ergebnis verarbeitet. Die manuelle Stufe bleibt wirksam.
- **UC-012-AK-04** «Bearbeitung fortsetzen» an der freigegebenen Mitarbeiteraufgabe führt erst nach Ausführung des Camunda-Übergangs zum gespeicherten Status «In Bearbeitung». Ein ausstehender Prozessstart oder eine veraltete Mitarbeiteraufgabe erlaubt keinen direkten Schreibzugriff auf den Fall.
- **UC-012-AK-05** PropertyFlow antwortet mit `200` nach Korrelation zur richtigen Fallprozessinstanz. Ungültige Aktion oder fehlende wartende Stelle ergeben `400`. Technischer Fehler oder Timeout ergeben `500`. Bei verlorener Antwort zeigt der erneute Abruf den gespeicherten Stand, sobald der Worker den Übergang ausgeführt hat. Der Timeout löst keine automatische Wiederholung aus.
