# Use Case: Mitarbeiterzugriff entziehen

## Overview

**Use Case ID:** UC-029  
**Primary Actor:** Für die Kontoverwaltung zuständige Person der Verwaltung  
**Goal:** Mitarbeitenden bei Bedarf den Zugriff auf den Mitarbeiterbereich entziehen.  
**Status:** Die Wirkung eines gesperrten Kontos oder entzogenen Rollenrechts ist spezifiziert. Der administrative Ablauf und seine Berechtigungen sind zu konkretisieren.

## Use-Case-Diagramm

```mermaid
flowchart LR
AKTEUR["Zuständige Person der Kontoverwaltung"]:::participant

subgraph PF["PropertyFlow"]
    UC(["UC-029 · Mitarbeiterzugriff entziehen"]):::goal
end

AKTEUR --- UC
classDef participant fill:#1e293b,stroke:#94a3b8,color:#ffffff
classDef goal fill:#334155,stroke:#6d8eae,color:#ffffff
style PF fill:#1e293b,stroke:#006fbb,stroke-width:2px,color:#ffffff
```

## Preconditions

- Ein Mitarbeiterkonto oder eine Mitarbeiterrolle soll gesperrt beziehungsweise entzogen werden.

## Main Success Scenario

1. Die zuständige Stelle veranlasst die Sperrung des Kontos oder den Entzug der Mitarbeiterrolle im vorgesehenen Verwaltungsablauf.
2. PropertyFlow prüft beim nächsten Mitarbeiterzugriff den aktuellen Kontostand und die Rolle erneut.
3. PropertyFlow verweigert betroffene Lese- und Schreibzugriffe im Web sowie über delegierte MCP-Zugänge.

## Alternative Flows

- Eine noch offene Browsersitzung ändert die serverseitige Prüfung der aktuellen Rechte nicht.
- Bei erneutem Anmeldeversuch erhält ein gesperrtes Konto keine Mitarbeitersitzung.

## Postconditions

- Der betroffene Mitarbeiterzugriff ist ab dem nächsten Aufruf gesperrt.

## Business Rules und Prüfkriterien

- **UC-029-BR-001:** Anmeldung und delegierter Zugriff setzen ein aktuell berechtigtes Konto voraus. [ZUG-04 · Rollen und Zusatzrechte](../specifications/fallzugriff-und-sicherheit.md#rollen-und-zusatzrechte)
- **UC-029-AK-01:** Einer bereits angemeldeten Person die Mitarbeiterrolle entziehen. Der nächste direkte Lese- und Schreibaufruf im Mitarbeiterbereich wird abgewiesen.
- **UC-029-AK-02:** Eine frühere MCP-Delegation erlaubt nach Rollenentzug keine Mitarbeiteraktion mehr.
