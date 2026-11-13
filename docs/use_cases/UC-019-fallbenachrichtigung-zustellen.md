# Use Case: Fallbenachrichtigung zustellen

## Overview

**Use Case ID:** UC-019  
**Use Case Name:** Fallbenachrichtigung zustellen  
**Primary Actor:** Mieterin oder Mieter als Empfänger  
**Supporting Actors:** Camunda-Fallprozess für Statusmails und E-Mail-Dienst\
**Goal:** Über eine gespeicherte, für den Mieter sichtbare Änderung informiert werden und die zugehörige Fallansicht öffnen können.  
**Status:** Fachlich vereinbart. Der produktive Versand von Fallbenachrichtigungen steht aus.

**Fachliche Grundlage:** [Benachrichtigungen und Zustellung](../specifications/benachrichtigungen-und-zustellung.md) · [Persönlicher Fall-Link](../architecture/adr/ADR-004-tokenbasierter-mieterzugriff.md)

## Use-Case-Diagramm

```mermaid
flowchart LR
AKTEUR["Mieterin / Mieter"]:::participant
MAIL["E-Mail-Dienst (System)"]:::participant
CAMUNDA["Camunda-Fallprozess (System)"]:::participant

subgraph PF["PropertyFlow"]
    UC(["UC-019 · Fallbenachrichtigung zustellen"]):::goal
end

AKTEUR --- UC
UC --- MAIL
CAMUNDA ---|"veranlasst Statusmail"| UC
classDef participant fill:#1e293b,stroke:#94a3b8,color:#ffffff
classDef goal fill:#334155,stroke:#6d8eae,color:#ffffff
style PF fill:#1e293b,stroke:#006fbb,stroke-width:2px,color:#ffffff
```

## Preconditions

- Ein nach BEN-01 benachrichtigungspflichtiges Ereignis ist dauerhaft gespeichert.
- Der Fall hat eine Kontaktadresse.

## Main Success Scenario

1. PropertyFlow veranlasst für das gespeicherte Ereignis einen nachvollziehbaren Versand. Bei einer Statusmail beauftragt Camunda den wiederverwendbaren Mail-Worker über einen Send Task mit der Referenz auf den gespeicherten Statuswechsel.
2. Der Mail-Worker lädt über den PropertyFlow-Anwendungsdienst das auslösende Ereignis, die aktuelle Fall-Kontaktadresse und den aktuell gültigen persönlichen Zugang aus der Falldatenbank. Bei einer geprüften Adresskorrektur desselben Mieters vor dem Versand verwendet er die neue Adresse. Die E-Mail bezieht sich weiterhin auf das ursprüngliche Ereignis. [BEN-02](../specifications/benachrichtigungen-und-zustellung.md#ben-02-empfänger-inhalt-und-persönlicher-link)
3. Die Versandfunktion erstellt aus einer festen Vorlage einen kurzen Hinweis mit Case-ID, gültigem Fall-Link und Vertraulichkeitshinweis. Bei einer veröffentlichten Rückfrage mit Antwortbedarf macht die Vorlage den Handlungsbedarf deutlich.
4. Die Versandfunktion übergibt die E-Mail nachgelagert an den E-Mail-Dienst und hält dessen Annahme fest.
5. Der Mieter kann den persönlichen Link öffnen und den aktuellen, für ihn freigegebenen Fallstand lesen.

### Sequenzdiagramm · Statusmail

Das Diagramm zeigt den Versand einer Statusmail nach Speicherung des Statuswechsels. Falländerung und Versandpflicht müssen auch nach einem Neustart nachvollziehbar sein.

```mermaid
---
config:
  sequence:
    actorMargin: 35
    mirrorActors: false
    width: 115
---
sequenceDiagram
    participant Prozess as Camunda-Fallprozess
    participant Versand as Mail-Worker
    participant Fall as PropertyFlow-Anwendungsdienst
    participant Mail as E-Mail-Dienst

    Note over Prozess,Fall: Statuswechsel ist gespeichert
    Versand->>Prozess: Job für Send Task abrufen
    Prozess-->>Versand: Referenz auf Statuswechsel
    Versand->>Fall: Versanddaten zur Referenz laden
    Fall-->>Versand: Ereignis, aktuelle Adresse und gültiger Zugang
    Versand->>Versand: Statusmail erstellen
    Versand->>Mail: E-Mail übergeben
    alt Annahme durch E-Mail-Dienst bestätigt
        Mail-->>Versand: Annahme bestätigen
        Versand->>Fall: Übergabe vermerken
        Versand->>Prozess: Job abschliessen
    else Timeout nach möglicher Übergabe
        Note over Versand,Mail: Übermittlung unklar
        Versand->>Fall: Unklaren Ausgang festhalten
    end
```

## Alternative Flows

- **Vorübergehender Versandfehler.** Die Versandpflicht bleibt nachvollziehbar und der Auftrag kann wiederholt werden. Die gespeicherte Falländerung bleibt wirksam.
- **Unklarer Übermittlungsausgang, Rückläufer oder dauerhafter Fehler.** PropertyFlow hält den Zustand nachvollziehbar fest. Bei klärungsbedürftigem Versand gilt für aktive Fälle der Prüfhinweis aus FALL-05. Ein abgeschlossener Fall bleibt abgeschlossen.
- **Fallzugang ungültig oder Versandkopie unlesbar.** Die Versandfunktion blockiert die E-Mail mit Fall-Link und macht den Klärungsbedarf sichtbar.

## Postconditions

- PropertyFlow kann den Versandauftrag und seinen Bearbeitungsstand nachvollziehen.
- Die bestätigte Annahme durch den E-Mail-Dienst ist als Übergabe erfasst. Ein Nachweis der Posteingangszustellung ist ein eigener Zustand.

## Business Rules und Prüfkriterien

- **UC-019-BR-001:** Je vereinbartem Ereignis und vorgesehenem Empfänger entsteht ein logischer Versandauftrag. Interne Memos, KI-Teilantworten und reine Lesezugriffe lösen keine Fallbenachrichtigung aus.
- **UC-019-BR-002:** Die E-Mail verwendet die beim Versand aktuelle Fall-Kontaktadresse und den aktuell gültigen Fall-Link. Vollständige Nachrichten, interne Memos und technische Fehlerdetails bleiben aus der Vorlage ausgeschlossen.
- **UC-019-AK-01:** Eine veröffentlichte Rückfrage mit Antwortbedarf löst einen Versandauftrag aus. Die E-Mail verweist mit Case-ID und gültigem Link auf die Fallansicht und macht den Antwortbedarf deutlich.
- **UC-019-AK-02:** Den E-Mail-Dienst während einer Falländerung abschalten und PropertyFlow neu starten. Die Änderung und der Auftrag bleiben erhalten. Eine Wiederholung desselben Ereignisses erzeugt keinen zusätzlichen logischen Auftrag.
- **UC-019-AK-03:** Einen ungültigen Zugang und einen unklaren Übermittlungsausgang prüfen. Der Versandstatus macht die jeweilige Situation nachvollziehbar.
- **UC-019-AK-04:** Eine Statusmail wartet auf den ersten Versand. Die Verwaltung korrigiert die Fall-Kontaktadresse desselben Mieters geprüft von A auf B. Der Mail-Worker lädt die Versanddaten und sendet die Mail an B mit dem aktuell gültigen Fall-Link. Die Mail beschreibt den ursprünglich gespeicherten Statuswechsel. A erhält diese Mail nicht.
