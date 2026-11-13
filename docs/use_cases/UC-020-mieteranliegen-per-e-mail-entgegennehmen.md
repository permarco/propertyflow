# Use Case: Mieteranliegen per E-Mail entgegennehmen

## Overview

**Use Case ID:** UC-020  
**Use Case Name:** Mieteranliegen per E-Mail entgegennehmen  
**Primary Actor:** Mieterin oder Mieter  
**Supporting Actor:** E-Mail-Dienst  
**Goal:** Ein Anliegen per E-Mail einreichen oder einen bestehenden Fall über einen sicher zugeordneten Mail-Eingang ergänzen.  
**Status:** Eingangskanal aus der Vision. Die sichere Annahme, Feldbelegung und Fallzuordnung benötigen einen eigenen Kanalvertrag.

**Fachliche Grundlage:** [Vision](../vision.md#1-erfassung-und-strukturierung-von-mieteranliegen) · [FALL-01 bis FALL-03](../specifications/fallverwaltung.md#fall-01-fallidentität-und-erfassungskanal) · [KOM-03](../specifications/fallkommunikation.md#kom-03-asynchrone-fallkommunikation)

## Use-Case-Diagramm

```mermaid
flowchart LR
AKTEUR["Mieterin / Mieter"]:::participant
MAIL["E-Mail-Dienst (System)"]:::participant

subgraph PF["PropertyFlow"]
    UC(["UC-020 · Mieteranliegen per E-Mail entgegennehmen"]):::goal
end

AKTEUR --- UC
UC --- MAIL
classDef participant fill:#1e293b,stroke:#94a3b8,color:#ffffff
classDef goal fill:#334155,stroke:#6d8eae,color:#ffffff
style PF fill:#1e293b,stroke:#006fbb,stroke-width:2px,color:#ffffff
```

## Preconditions

- Der E-Mail-Eingang steht gemäss dem noch festzulegenden Kanalvertrag zur Verfügung.
- Der Kanalvertrag beschreibt die erforderlichen Eingaben und die Bedingungen für eine sichere Zuordnung zu einem bestehenden Fall.

## Main Success Scenario

1. Der Mieter sendet sein Anliegen per E-Mail an den vorgesehenen Eingang.
2. PropertyFlow prüft die Eingaben und bestimmt nach den Regeln des Eingangskanals, ob die E-Mail ein neues Anliegen oder eine sicher zugeordnete Ergänzung ist.
3. Ein neues Anliegen wird gemäss FALL-02 dauerhaft angenommen und erhält eine Case-ID. Eine sicher zugeordnete Ergänzung wird im bestehenden Fall verarbeitet.
4. PropertyFlow veranlasst die für den jeweiligen Fallvorgang vereinbarten Folgeschritte. Der Kanalvertrag legt die Rückmeldung an den Absender fest.

## Alternative Flows

- **Angaben fehlen oder die Fallzuordnung bleibt unsicher.** Wie der Eingang weiterbehandelt wird, ist im Kanalvertrag zu entscheiden. Ein bestehender Fall erhält eine Ergänzung erst nach sicherer Zuordnung.
- **Dieselbe Einreichung wird technisch wiederholt.** Die Regel zur eindeutigen Fallidentität nach FALL-03 gilt auch hier. Der Kanalvertrag legt fest, wie PropertyFlow die Wiederholung erkennt.
- **Dauerhafte Speicherung scheitert.** PropertyFlow bestätigt keine erfolgreiche Fallannahme.

## Postconditions

- Ein angenommenes neues Anliegen ist mit einer eindeutigen Case-ID gespeichert. Eine angenommene Ergänzung gehört zum vorhandenen Fall.
- Die konkrete Behandlung ungeklärter Eingänge bleibt Teil des Kanalvertrags.

## Business Rules und Prüfkriterien

- **UC-020-BR-001:** Die Regeln zur dauerhaften Annahme und Fallidentität gelten kanalübergreifend. Eine sicher zugeordnete Ergänzung erzeugt keinen zweiten führenden Fallprozess.
- **UC-020-BR-002:** Die Feldbelegung, sichere Zuordnung und Wiederholungserkennung sind vor Umsetzung des Mail-Eingangs zu spezifizieren.
- **UC-020-AK-01:** Nach Festlegung des Kanalvertrags ein neues Anliegen und eine sicher zugeordnete Ergänzung prüfen. Nur die Neuanlage vergibt eine neue Case-ID.
- **UC-020-AK-02:** Dieselbe Einreichung technisch wiederholen und eine E-Mail mit unsicherer Fallzuordnung prüfen. Die Wiederholung legt keinen zweiten Fall an. Die unsichere Zuordnung verändert keinen bestehenden Fall.
