# Use Case: Mieterantwort auswerten

## Overview

**Use Case ID:** UC-017  
**Use Case Name:** Mieterantwort auswerten  
**Primary Actor:** Camunda-Fallprozess  
**Supporting Actor:** PropertyFlow-Worker und zuständige Anwendungsdienste  
**Goal:** Für jede offene Rückfrage feststellen, ob die neue Mieternachricht sie eindeutig beantwortet.  
**Status:** Die fachlichen Regeln sind vereinbart. Die automatische inhaltliche Auswertung und ihre Camunda-Anbindung gehören zum Zielbild.

## Use-Case-Diagramm

```mermaid
flowchart LR
AKTEUR["Camunda-Fallprozess (System)"]:::participant

subgraph PF["PropertyFlow"]
    UC(["UC-017 · Mieterantwort auswerten"]):::goal
end

AKTEUR --- UC
classDef participant fill:#1e293b,stroke:#94a3b8,color:#ffffff
classDef goal fill:#334155,stroke:#6d8eae,color:#ffffff
style PF fill:#1e293b,stroke:#006fbb,stroke-width:2px,color:#ffffff
```

## Preconditions

- Eine neue externe Mieternachricht ist am aktiven Fall gespeichert. Für die inhaltliche Zuordnung steht mindestens eine veröffentlichte Rückfrage mit offenem Antwortbedarf zur Prüfung an. [UC-009](UC-009-mieteranliegen-ergaenzen.md) · [KOM-03](../specifications/fallkommunikation.md#inhaltliche-prüfung-offener-rückfragen)

## Main Success Scenario

1. Der bestehende Camunda-Fallprozess veranlasst die Prüfung der gespeicherten Nachricht. Der zuständige PropertyFlow-Dienst lädt die offenen Rückfragen und die zugehörigen externen Inhalte.
2. Der Dienst beurteilt jede Rückfrage einzeln. Eine Nachricht kann mehrere Fragen beantworten, wenn die Antwort für jede dieser Fragen eindeutig ist.
3. PropertyFlow speichert die bestätigte Beantwortung mit Bezug zur Frage, zur Antwort und zum Verarbeitungszeitpunkt. Fallansichten zeigen den aktuellen Antwortbedarf.
4. Sind alle zuvor offenen Fragen beantwortet, setzt der Fallprozess die Bearbeitung gemäss FALL-05 fort. Für einen weiterhin aktiven Fall folgt «Mitarbeiterprüfung erforderlich», sofern kein zulässiger Systemabschluss erfolgt. Bei einer durch den Nachrichteneingang bereits veranlassten Mitarbeiterprüfung gelten FALL-06 und der folgende Alternativablauf.

## Alternative Flows

- **Teilantwort, themenfremde oder unklare Antwort.** Nur eindeutig beantwortete Rückfragen werden erledigt. Die übrigen bleiben offen.
- **Auswertungsfehler.** PropertyFlow führt die betroffenen Fragen weiterhin als offen. Die gespeicherte Mieternachricht bleibt im Verlauf.
- **Beim Nachrichteneingang läuft eine Wiedervorlage.** Camunda hebt sie auf und legt den Fall sofort zur Mitarbeiterprüfung vor. Die automatische Auswertung läuft nach den Regeln von FALL-06. Sie wartet nicht auf den bisherigen Termin. Die laufende Mitarbeiterprüfung hat Vorrang vor späteren automatischen Änderungen oder einem Systemabschluss. [UC-013](UC-013-wiedervorlage-verwalten.md) · [FALL-06](../specifications/fallverwaltung.md#fall-06-fachlicher-status-und-technischer-workflow)
- **Antwortbedarf bereits beendet.** Eine verspätete oder wiederholte Auswertung reaktiviert keinen beendeten Antwortbedarf und speichert keine zusätzliche Erledigung.

## Flussdiagramm · Rückfragen einzeln prüfen

```mermaid
flowchart TD
    NACHRICHT["Gespeicherte Mieternachricht"] --> PRUEFEN{"Rückfrage eindeutig beantwortet?"}
    PRUEFEN -->|"Ja"| ERLEDIGT["Beantwortung mit Nachrichtenbezug speichern"]
    PRUEFEN -->|"Unklar, unvollständig oder Fehler"| OFFEN["Rückfrage offen lassen"]
    ERLEDIGT --> WEITERE{"Weitere Rückfrage zu prüfen?"}
    OFFEN --> WEITERE
    WEITERE -->|"Ja"| PRUEFEN
    WEITERE -->|"Nein"| REST{"Alle zuvor offenen Fragen beantwortet?"}
    REST -->|"Ja"| FORTSETZEN["Fallprozess prüft den nächsten fachlichen Schritt"]
    REST -->|"Nein"| WARTEBEDARF["Offenen Antwortbedarf anzeigen"]
```

## Postconditions

- Der aktuelle Antwortbedarf ist pro Frage nachvollziehbar. Die ursprünglich veröffentlichte Nachricht und ihre Kennzeichnung bleiben erhalten.

## Business Rules und Prüfkriterien

- **UC-017-BR-001** Der blosse Nachrichteneingang genügt für die Erledigung einer Frage nicht. Die Speicherung, Dringlichkeitsneubewertung und inhaltliche Erledigung haben jeweils ihre eigenen fachlichen Regeln. [KOM-03](../specifications/fallkommunikation.md#inhaltliche-prüfung-offener-rückfragen) · [DRING-05](../specifications/dringlichkeitsbewertung.md#dring-05-erst--und-neubewertung-im-prozess)
- **UC-017-BR-002** Der Folgestatus nach vollständiger Beantwortung berücksichtigt den aktuellen Fallstand, eine bereits veranlasste Mitarbeiterprüfung und die Regeln zum Systemabschluss. [FALL-05](../specifications/fallverwaltung.md#status-nach-beantwortung-aller-rückfragen)
- **UC-017-AK-01** Bei zwei offenen Fragen beantwortet die Nachricht eine Frage eindeutig und die andere nur teilweise. Genau die erste Frage ist erledigt. Nach einer Wiederholung bleibt es bei derselben Erledigung.
- **UC-017-AK-02** Die letzte offene Frage wird eindeutig beantwortet. Ohne zulässigen Abschluss erhält der weiterhin aktive Fall «Mitarbeiterprüfung erforderlich». Eine laufende Mitarbeiterprüfung wird fortgeführt.
- **UC-017-AK-03** Bei laufender Wiedervorlage eine neue Mieternachricht speichern und ihre Auswertung fehlschlagen lassen. Der offene Antwortbedarf bleibt erhalten. Der Nachrichteneingang hat die Wiedervorlage bereits aufgehoben und «Mitarbeiterprüfung erforderlich» veranlasst.
