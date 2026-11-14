# Use Case: Mitarbeiterzugriff entziehen

## Overview

**Use Case ID:** UC-029  
**Primary Actor:** Angemeldete Person der Verwaltung mit aktiver Mitarbeiterrolle und dem Zusatzrecht «Mitarbeiterkonten verwalten»\
**Goal:** Mitarbeitenden bei Bedarf den Zugriff auf den Mitarbeiterbereich entziehen.  
**Status:** Zielablauf und [HTTP-Vertrag](../architecture/openapi/fallannahme.yaml) spezifiziert. Oberfläche und Umsetzung stehen aus.

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

- Die handelnde Person besitzt die aktive Mitarbeiterrolle und das Zusatzrecht «Mitarbeiterkonten verwalten». Das Zielkonto ist über seine Benutzer-ID bestimmt.

## Main Success Scenario

1. Die berechtigte Person sperrt das Konto oder entzieht ihm die Mitarbeiterrolle.
2. PropertyFlow prüft die aktuellen Rechte und speichert die Änderung zusammen mit dem Audit-Eintrag. Die Kontosperre verhindert die Anmeldung mit diesem Konto. Der Rollenentzug beendet den Mitarbeiter- und den darauf beruhenden Kontoverwaltungszugriff.
3. PropertyFlow prüft ab dem nächsten Aufruf den aktuellen Kontostand und die Rolle erneut. Der Dienst verweigert betroffene Lese- und Schreibzugriffe im Web sowie über bestehende MCP-Delegationen.

## Alternative Flows

- Eine noch offene Browsersitzung ändert die serverseitige Prüfung der aktuellen Rechte nicht.
- Bei erneutem Anmeldeversuch erhält ein gesperrtes Konto keine Mitarbeitersitzung.
- Würde die Aktion das letzte aktive Konto mit Mitarbeiterrolle und Kontoverwaltungsrecht sperren oder seine Rolle entziehen, lehnt PropertyFlow die Änderung ab. Dies gilt auch bei gleichzeitigen Verwaltungsaktionen.

## Postconditions

- Der betroffene Mitarbeiterzugriff ist ab dem nächsten Aufruf gesperrt.

## Business Rules und Prüfkriterien

- **UC-029-BR-001:** Anmeldung und delegierter Zugriff setzen ein aktuell berechtigtes Konto voraus. [ZUG-04 · Rollen und Zusatzrechte](../specifications/fallzugriff-und-sicherheit.md#rollen-und-zusatzrechte)
- **UC-029-AK-01:** Einer bereits angemeldeten Person die Mitarbeiterrolle entziehen. Der nächste direkte Lese- und Schreibaufruf im Mitarbeiterbereich wird abgewiesen.
- **UC-029-AK-02:** Eine frühere MCP-Delegation erlaubt nach Rollenentzug keine Mitarbeiteraktion mehr.
- **UC-029-AK-03:** Eine laufende Mitarbeitersitzung und eine MCP-Delegation bestehen. Das Konto wird gesperrt. Der nächste Lese- und Schreibzugriff wird in beiden Kanälen abgewiesen. Ein neuer Anmeldeversuch eröffnet keine Mitarbeitersitzung.
- **UC-029-AK-04:** Ein Mitarbeiter ohne Kontoverwaltungsrecht ruft den Dienst direkt auf. Der Dienst lehnt ab. Auch nach gleichzeitigen berechtigten Versuchen, die letzten Verwaltungskonten zu sperren oder ihnen die Mitarbeiterrolle zu entziehen, bleibt mindestens ein aktives Verwaltungskonto bestehen.
