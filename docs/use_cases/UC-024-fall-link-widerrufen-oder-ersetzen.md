# Use Case: Persönlichen Fall-Link ersetzen

## Overview

**Use Case ID:** UC-024  
**Primary Actor:** Für den administrativen Fallzugang zuständige Person der Verwaltung  
**Goal:** Einen verlorenen oder kompromittierten Fall-Link durch einen neuen Link zum selben Fall ersetzen.<br>
**Status:** [HTTP-Zielvertrag](../architecture/openapi/fallannahme.yaml) für atomaren Ersatz mit serverseitigem Prüfnachweis beschrieben. Prüfverfahren, Rechtevergabe, Bedienung und Umsetzung stehen aus.

## Use-Case-Diagramm

```mermaid
flowchart LR
AKTEUR["Zuständige Person der Verwaltung"]:::participant

subgraph PF["PropertyFlow"]
    UC(["UC-024 · Persönlichen Fall-Link ersetzen"]):::goal
end

AKTEUR --- UC
classDef participant fill:#1e293b,stroke:#94a3b8,color:#ffffff
classDef goal fill:#334155,stroke:#6d8eae,color:#ffffff
style PF fill:#1e293b,stroke:#006fbb,stroke-width:2px,color:#ffffff
```

## Preconditions

- Die Verwaltung kann den betroffenen Fall und den bisherigen persönlichen Zugang eindeutig bestimmen.
- Die handelnde Person hat das Zusatzrecht «Fallzugänge verwalten». Ein serverseitiger Prüfnachweis bestätigt Identität und aktuelle Kontaktadresse für diesen Ersatz.

## Main Success Scenario

1. Die Verwaltung prüft die Anfrage und bestimmt den betroffenen Fallzugang.
2. PropertyFlow erzeugt einen neuen, unabhängigen Token. In derselben Datenbanktransaktion aktiviert es den neuen Zugang und sperrt den bisherigen. Beide Änderungen werden mit demselben Commit wirksam.
3. PropertyFlow speichert Audit und Versandauftrag gemeinsam mit dem Ersatz. Die E-Mail folgt nach dem Commit an die bestätigte Kontaktadresse. Beim Versand lädt der Dienst den aktuell gültigen Fall-Link.

## Alternative Flows

- Bei unzureichender Identitätsprüfung oder unklarer Kontaktadresse bleibt der bisherige Zugang gültig. Ein Adresswechsel folgt [UC-025](UC-025-fall-kontaktadresse-aendern.md).
- Scheitert die Speicherung des Ersatzes, bleibt der bisherige Zugang gültig. PropertyFlow bestätigt den Ersatz erst nach erfolgreichem Commit.

## Postconditions

- Nach dem Commit öffnet der neue Link denselben Fall. PropertyFlow weist Zugriffe mit dem bisherigen Token ab. Case-ID, Verlauf und Fallstatus bleiben erhalten.

## Business Rules und Prüfkriterien

- **UC-024-BR-001:** Der persönliche Link gilt zeitlich unbegrenzt. Die Sperrung des bisherigen Zugangs erfolgt ausschliesslich zusammen mit der Aktivierung des Ersatzlinks gemäss [ZUG-05](../specifications/fallzugriff-und-sicherheit.md#zug-05-dauerhafte-gültigkeit-und-direkter-ersatz). Der Ersatz ist ein administrativer Vorgang ausserhalb der Fallbearbeitung in UI3.
- **UC-024-AK-01:** Den Link ersetzen und den alten Token in einem bereits geöffneten Tab zum Lesen und Schreiben verwenden. Beide Zugriffe werden abgewiesen. Der neue Link führt zum selben Fall.
- **UC-024-AK-02:** Eine Benachrichtigung vor dem Versand anhalten und den Link erfolgreich ersetzen. Beim Versand enthält die Benachrichtigung den aktuell gültigen Ersatzlink zum selben Fall.
- **UC-024-AK-03:** Den Ersatz mit unzureichender Identitätsprüfung und separat mit einem erzwungenen Speicherfehler versuchen. In beiden Fällen bleibt der bisherige Link gültig und PropertyFlow bestätigt keinen Ersatz.
- **UC-024-AK-04:** Den Link bei ausgefallenem Camunda ersetzen. Nach dem Commit gilt der neue Link. Eine identische Wiederholung erzeugt keinen weiteren Token oder Versandauftrag.
