# Use Case: MCP-Zugriff freigeben und widerrufen

## Overview

**Use Case ID:** UC-023  
**Primary Actor:** Angemeldeter Mieter oder Mitarbeiter  
**Supporting Actor:** MCP-Client im LLM-Chat  
**Goal:** Einem Client ausgewählte PropertyFlow-Aktionen erlauben und diese Berechtigung wieder entziehen können.  
**Status:** Zielablauf gemäss ADR-005 und ZUG-04. «Fallstand abrufen» ist als erstes Werkzeug für Mieter festgelegt. Die Umsetzung der OAuth-Delegation und die Bedienung der Widerrufsfunktion sind zu konkretisieren.

## Use-Case-Diagramm

```mermaid
flowchart LR
MIETER["Angemeldete Mieter"]:::participant
MITARBEITER["Angemeldete Mitarbeitende"]:::participant
MCP["MCP-Client im LLM-Chat (System)"]:::participant

subgraph PF["PropertyFlow"]
    UC(["UC-023 · MCP-Zugriff freigeben und widerrufen"]):::goal
end

MIETER --- UC
MITARBEITER --- UC
UC --- MCP
classDef participant fill:#1e293b,stroke:#94a3b8,color:#ffffff
classDef goal fill:#334155,stroke:#6d8eae,color:#ffffff
style PF fill:#1e293b,stroke:#006fbb,stroke-width:2px,color:#ffffff
```

## Preconditions

- Die Person hat ein berechtigtes persönliches Konto gemäss [UC-005](UC-005-userkonto-eroeffnen.md) oder [UC-006](UC-006-mitarbeiter-anmelden.md).
- Der MCP-Client fordert Rechte für PropertyFlow an.

## Main Success Scenario

1. Der Client führt die Person zum gemeinsamen Anmeldedienst. Eine gültige Anmeldung im selben Browserprofil genügt.
2. Die Person prüft die angeforderten Rechte und erteilt die Delegation. Der Client erhält ein OAuth-Zugriffstoken für den MCP-Endpunkt.
3. Bei jedem Aufruf prüft der Anwendungsdienst Token, aktuelles Konto, Rolle, Client-Delegation, Fallzugriff und die gewünschte Aktion. Bei einer kritischen Aktion prüft er auch die menschliche Freigabe für genau diese Handlung.
4. Widerruft die Person später die Delegation, weist PropertyFlow weitere Aufrufe mit dieser Delegation ab.

### Erstes MCP-Werkzeug für Mieter · «Fallstand abrufen»

Der Client übergibt die Case-ID. Der Anwendungsdienst prüft bei jedem Aufruf das delegierte Leserecht und gleicht die aktuell verifizierte E-Mail-Adresse des Mieterkontos mit der aktuellen Kontaktadresse des Falls ab. Bei erfolgreichem Abruf liefert das Werkzeug ausschliesslich die Case-ID, den fachlichen Bearbeitungsstatus und die aktuell wirksame Dringlichkeit. Ohne wirksame Einstufung lautet die Angabe «Noch nicht bewertet». [SS-004](../architecture/schnittstellenentwurf-block-3.md#ss-004--mcp-werkzeug-fallstand-abrufen) · [DRING-02](../specifications/dringlichkeitsbewertung.md#dring-02-noch-nicht-bewertet)

## Alternative Flows

- Lehnt die Person die Delegation ab, erhält der Client kein Zugriffstoken.
- Bei ungültigem Token, fehlender Berechtigung oder fehlender Freigabe erhält der Client keine geschützten Daten und die Aktion bleibt ohne fachliche Wirkung.
- Für eine fremde Case-ID antwortet «Fallstand abrufen» genauso wie für eine nicht vorhandene Case-ID. Der Client erhält weder Falldaten noch eine Bestätigung, dass der Fall existiert.

## Postconditions

- Eine gültige Delegation erlaubt die freigegebenen Aktionen innerhalb der aktuellen Kontorechte. Ein Widerruf wirkt ab dem nächsten Zugriff.

## Business Rules und Prüfkriterien

- **UC-023-BR-001:** Web und MCP rufen dieselben fachlichen Anwendungsdienste auf. PropertyFlow prüft die aktuellen [Kontorechte](../specifications/fallzugriff-und-sicherheit.md#rollen-und-zusatzrechte), die [MCP-Delegation](../specifications/fallzugriff-und-sicherheit.md#gemeinsame-anmeldung-und-mcp-delegation) und die Freigabegrenzen gemäss [FALL-10](../specifications/fallverwaltung.md#fall-10-automatisierung-und-menschliche-freigabe). Für das Verhältnis von lokaler Abmeldung und Delegation gilt [ZUG-04 · Web-Abmeldung](../specifications/fallzugriff-und-sicherheit.md#web-abmeldung).
- **UC-023-BR-002:** Das Audit erfasst bei Schreibversuchen Benutzer-ID, Kanal, Client, Aktion, Fallreferenz, Zeitpunkt und Ergebnis gemäss [ADR-005](../architecture/adr/ADR-005-zugriffsmodell.md#autorisierung-über-mcp).
- **UC-023-AK-01:** Ein Client mit delegiertem Leserecht kann einen berechtigten Fall lesen. Schreibversuche und Abrufe fremder Fälle werden abgewiesen.
- **UC-023-AK-02:** Nach Widerruf wird der nächste MCP-Aufruf mit dem bisherigen Token abgewiesen.
- **UC-023-AK-03:** Eine kritische Aktion ohne passende menschliche Freigabe bleibt ohne fachliche Wirkung. Der abgewiesene Schreibversuch ist im Audit nachvollziehbar.
- **UC-023-AK-04 – Fremde Case-ID:** Ein Client mit gültigem Token und delegiertem Leserecht für «Fallstand abrufen» erhält für einen eigenen Fall genau die drei vereinbarten Angaben. Mit demselben Token fragt er anschliessend einen existierenden Fall mit einer anderen Kontaktadresse ab. Der Dienst verweigert den Abruf. Die Antwort enthält keine Falldaten und ist von der Antwort für eine gültig formatierte, nicht vorhandene Case-ID nicht unterscheidbar.
