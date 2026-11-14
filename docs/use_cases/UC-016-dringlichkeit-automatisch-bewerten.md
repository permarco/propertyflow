# Use Case: Dringlichkeit automatisch bewerten

## Overview

**Use Case ID:** UC-016  
**Use Case Name:** Dringlichkeit automatisch bewerten  
**Primary Actor:** Camunda-Fallprozess  
**Supporting Actor:** PropertyFlow-Worker und Modellanbieter  
**Goal:** Nach der Fallannahme oder einer neuen Mieternachricht eine nachvollziehbare Systembewertung der Dringlichkeit erhalten.  
**Status:** Der Triage-Java-Vertrag und der Modelladapter sind als isolierter Prototyp vorhanden. Die Anbindung an den Fallprozess und die dauerhafte Übernahme der Ergebnisse sind geplant. [Triage-Vertrag](../ai-usage/ki-service-vertrag.md)

## Use-Case-Diagramm

```mermaid
flowchart LR
AKTEUR["Camunda-Fallprozess (System)"]:::participant
MODELL["LLM-Anbieter (System)"]:::participant

subgraph PF["PropertyFlow"]
    UC(["UC-016 · Dringlichkeit automatisch bewerten"]):::goal
end

AKTEUR --- UC
UC --- MODELL
classDef participant fill:#1e293b,stroke:#94a3b8,color:#ffffff
classDef goal fill:#334155,stroke:#6d8eae,color:#ffffff
style PF fill:#1e293b,stroke:#006fbb,stroke-width:2px,color:#ffffff
```

## Preconditions

- Ein Fall wurde dauerhaft angenommen oder eine neue Mieternachricht zu einem aktiven Fall wurde dauerhaft gespeichert. Der Bewertungsauftrag ist dem bestehenden Fallprozess zugeordnet. [DRING-05](../specifications/dringlichkeitsbewertung.md#dring-05-erst--und-neubewertung-im-prozess)

## Main Success Scenario

1. Camunda stellt nach der Fallannahme einen Bewertungsauftrag mit der Case-ID bereit. Bei einer Neubewertung enthält der Auftrag zusätzlich die Nachrichten-ID der gespeicherten Mieternachricht. Die Bewertung läuft nachgelagert zur Fallannahme beziehungsweise zur synchronen Übergabe der Mieternachricht. [SS-003](../architecture/schnittstellenentwurf-block-3.md#ss-003--camunda-integrationsvertrag)
2. Der Worker lädt die ursprüngliche Fallbeschreibung über den zuständigen PropertyFlow-Anwendungsdienst. Bei einer Neubewertung prüft der Dienst die Zuordnung der Nachrichten-ID zum Fall. Der Worker lädt dazu den relevanten gespeicherten Nachrichtenverlauf bis einschliesslich dieser Nachricht. [DRING-05](../specifications/dringlichkeitsbewertung.md#dring-05-erst--und-neubewertung-im-prozess)
3. Der Worker übergibt Triage die Fallbeschreibung und bei einer Neubewertung zusätzlich den Verlauf mit der als Auslöser gekennzeichneten Nachricht. Triage validiert die Ausgabe des Modelladapters.
4. Der zuständige PropertyFlow-Dienst prüft den aktuellen Fallstand und speichert die Systembewertung nachvollziehbar. Ohne manuelle Einstufung kann das bestätigte Ergebnis die wirksame Dringlichkeit setzen oder ändern.
5. Bei einer wirksamen Änderung speichert PropertyFlow die Historie und veranlasst eine Fallbenachrichtigung. Der Worker meldet das Verarbeitungsergebnis an den Fallprozess.

## Alternative Flows

- **Analyse fehlgeschlagen.** Die bisherige wirksame Stufe bleibt bestehen. Camunda veranlasst für den aktiven Fall die Mitarbeiterprüfung gemäss FALL-05. PropertyFlow speichert den Prüfgrund und den veranlassten Status. Die Mitarbeiterbearbeitung setzt die passende Prozessaufgabe voraus.
- **Manuelle Einstufung oder unveränderte Stufe.** Die wirksame Stufe bleibt erhalten. Der Bewertungsversuch bleibt intern nachvollziehbar.
- **Wiederholung, Abschluss oder überholte Bewertung.** PropertyFlow prüft die Gültigkeit am aktuellen Fallstand. Wiederholte oder verspätete Ergebnisse aus der Bearbeitung vor dem letzten Abschluss erzeugen keine zusätzliche wirksame Änderung.

## Sequenzdiagramm · Bewertung im Fallprozess

```mermaid
---
config:
  sequence:
    actorMargin: 35
    mirrorActors: false
    width: 115
---
sequenceDiagram
    participant C as Camunda
    participant W as Job Worker<br/>in PropertyFlow
    participant T as Triage
    participant F as Fallbearbeitung
    participant M as Modellanbieter

    Note over C,W: Fallannahme oder<br/>Mieternachricht gespeichert
    W->>C: Jobs abrufen
    alt Erstbewertung
        C-->>W: Bewertungsjob mit Case-ID
        W->>F: Fallbeschreibung zur Case-ID laden
        F-->>W: Ursprüngliche Fallbeschreibung
    else Neubewertung
        C-->>W: Bewertungsjob mit Case-ID und Nachrichten-ID
        W->>F: Fallbeschreibung und Verlauf<br/>zu Case-ID und Nachrichten-ID laden
        F-->>W: Fallbeschreibung, relevanter Verlauf<br/>und auslösende Nachricht
    end
    W->>T: Dringlichkeit anhand des geladenen Kontexts bewerten
    T->>M: Modell über Adapter aufrufen
    M-->>T: Ausgabe oder Fehler
    T-->>W: Validierte Stufe<br/>oder typisierter Fehler
    W->>F: Ergebnis fachlich verarbeiten
    Note over F: Fallstand prüfen<br/>Manuelle Einstufung schützen
    F-->>W: Verarbeitungsergebnis
    W->>C: Ergebnis an den Fallprozess melden
```

## Postconditions

- Die Systembewertung oder ihr Fehlschlag ist nachvollziehbar. Mieter sehen die wirksame Stufe und die freigegebene Änderungshistorie.

## Business Rules und Prüfkriterien

- **UC-016-BR-001** Die gültigen Stufen sind «Kritisch», «Hoch», «Normal» und «Niedrig». Ohne verwendbare System- oder manuelle Einstufung gilt «Noch nicht bewertet». [DRING-01](../specifications/dringlichkeitsbewertung.md#dring-01-fachliche-stufen) · [DRING-02](../specifications/dringlichkeitsbewertung.md#dring-02-noch-nicht-bewertet)
- **UC-016-BR-002** Manuelle Einstufungen haben Vorrang. Die Stufenänderung und ihre Quelle werden nachvollziehbar gespeichert. Abgeschlossene Fälle bleiben bezüglich Dringlichkeit lesend. [DRING-06 bis DRING-08](../specifications/dringlichkeitsbewertung.md#dring-06-manuelle-anpassung)
- **UC-016-AK-01** Ein Modell-Timeout lässt den Fall und die bisherige Stufe bestehen. Am aktiven Fall ist «KI-Analyse fehlgeschlagen» als interner Prüfgrund sichtbar. [FALL-05](../specifications/fallverwaltung.md#fall-05-fachlicher-lebenszyklus)
- **UC-016-AK-02** Nach einer gespeicherten Mieternachricht erfolgt eine Neubewertung im bestehenden Prozess. Bei einer inzwischen manuell gesetzten Stufe bleibt diese wirksam.
- **UC-016-AK-03** Dasselbe Ergebnis wird erneut zugestellt. Für eine bereits übernommene Änderung entstehen weder ein zweites Historienereignis noch ein zweiter logischer Mailauftrag.
- **UC-016-AK-04** Der Mieter antwortet auf «Läuft das Wasser noch aus der Decke?» mit «Ja» und sendet vor Ausführung des Bewertungsjobs eine weitere Ergänzung. Der Job für «Ja» verwendet die ursprüngliche Fallbeschreibung, die relevante Rückfrage und «Ja» als auslösende Nachricht. Die spätere Ergänzung gehört erst zum Kontext ihres eigenen Jobs.
