# Use Case: Mitarbeiter anmelden

## Overview

**Use Case ID:** UC-006  
**Use Case Name:** Mitarbeiter anmelden  
**Primary Actor:** Mitarbeiterin / Mitarbeiter der Immobilienverwaltung  
**Supporting Actors:** Verwaltung für die Kontobereitstellung, E-Mail-Dienst für die Zustellung und optional der MCP-Client im LLM-Chat  
**Goal:** Sich mit einem E-Mail-Einmalcode persönlich anmelden und die zugewiesenen Mitarbeiterfunktionen in PropertyFlow nutzen.  
**Status:** Mitarbeiteranmeldung und geschützter Mitarbeiterbereich sind in der lokalen Frontend-Demo umgesetzt. Der MCP-Zugang beschreibt das Zielbild.

**Fachliche Grundlage:** [ADR-005](../architecture/adr/ADR-005-zugriffsmodell.md#mitarbeiteridentität) beschreibt Mitarbeiteridentität, gemeinsamen Anmeldedienst und MCP-Autorisierung. Die Mitarbeiterübersicht folgt [UC-002](UC-002-mieteranliegen-anzeigen.md).

Die Felder, Rückmeldungen und Übergänge der Anmeldeansicht beschreibt [Screen 04](../frontend/ansicht-04-login.md).

## Use-Case-Diagramm

```mermaid
flowchart LR
MITARBEITER["Mitarbeiterin / Mitarbeiter"]:::participant
MAIL["E-Mail-Dienst (System)"]:::participant
MCP["MCP-Client im LLM-Chat (geplant) (System)"]:::participant

subgraph PF["PropertyFlow"]
    UC(["UC-006 · Mitarbeiter anmelden"]):::goal
end

MITARBEITER --- UC
UC --- MAIL
UC --- MCP
classDef participant fill:#1e293b,stroke:#94a3b8,color:#ffffff
classDef goal fill:#334155,stroke:#6d8eae,color:#ffffff
style PF fill:#1e293b,stroke:#006fbb,stroke-width:2px,color:#ffffff
```

## Preconditions

- Ein aktives Mitarbeiterkonto mit der vorgesehenen E-Mail-Adresse wurde gemäss [UC-007](UC-007-mitarbeiterkonto-anlegen.md) eingerichtet. Es hat die Mitarbeiterrolle gemäss [ZUG-04 · Rollen und Zusatzrechte](../specifications/fallzugriff-und-sicherheit.md#rollen-und-zusatzrechte).
- Die Person hat Zugriff auf das Postfach dieser Adresse.

## Main Success Scenario

1. Die Mitarbeiterin oder der Mitarbeiter öffnet den geschützten Mitarbeiterbereich und gibt die hinterlegte E-Mail-Adresse ein.
2. PropertyFlow prüft serverseitig, ob für diese Adresse ein aktives Mitarbeiterkonto mit Mitarbeiterrolle besteht. Anforderung und Versand folgen [ZUG-04 · E-Mail-Einmalcode](../specifications/fallzugriff-und-sicherheit.md#e-mail-einmalcode-für-registrierung-und-anmeldung).
3. Die Person gibt den Code auf der Anmeldeseite ein. Nach erfolgreicher Codeprüfung eröffnet PropertyFlow eine persönliche Sitzung, sofern das Konto weiterhin über die Mitarbeiterrolle verfügt und aktiv ist.
4. PropertyFlow öffnet den Mitarbeiterbereich. Bei weiteren Aufrufen prüft der Dienst die Mitarbeiterberechtigung und die für die jeweilige Aktion geltenden Fachregeln.

## Flussdiagramm · Mitarbeiteranmeldung

```mermaid
flowchart TD
    Verwaltung["Verwaltung legt Mitarbeiterkonto an und vergibt die Rolle"]
    Web(["Mitarbeiterbereich im Browser öffnen"])
    Chat(["PropertyFlow im LLM-Chat verbinden"])
    Sitzung{"Gültige Mitarbeitersitzung beim gemeinsamen Anmeldedienst?"}
    Adresse["Hinterlegte E-Mail-Adresse eingeben"]
    Versand["Einmalcode an das aktive Mitarbeiterkonto senden"]
    Code["Einmalcode auf der Anmeldeseite eingeben"]
    Pruefung{"Code gültig, unverbraucht und Konto weiterhin berechtigt?"}
    Login["Code verbrauchen und persönliche Sitzung eröffnen"]
    Fehler["Anmeldung abgelehnt · erneuten Versuch anbieten"]
    Kanal{"Welcher Zugang wurde angefragt?"}
    Backoffice(["Mitarbeiterbereich gemäss Rolle öffnen"])
    Freigabe{"Gibt die Person dem MCP-Client die angeforderten Rechte?"}
    Token(["OAuth-Zugriffstoken für den MCP-Client ausstellen"])
    Ende(["Verbindung endet ohne Zugriffstoken"])

    Verwaltung -.->|"Voraussetzung"| Adresse
    Web --> Sitzung
    Chat --> Sitzung
    Sitzung -->|"Ja"| Kanal
    Sitzung -->|"Nein"| Adresse --> Versand --> Code --> Pruefung
    Pruefung -->|"Ja"| Login --> Kanal
    Pruefung -->|"Nein"| Fehler --> Adresse
    Kanal -->|"Web"| Backoffice
    Kanal -->|"LLM-Chat"| Freigabe
    Freigabe -->|"Ja"| Token
    Freigabe -->|"Nein"| Ende
```

## Alternative Flows

### A1: Anmeldung ist bereits gültig

Der gemeinsame Anmeldedienst erkennt die gültige Sitzung im selben Browserprofil. Die Person kann den zweiten Zugang ohne erneute Code-Eingabe verwenden. Beim Verbinden eines LLM-Chats erteilt sie dem MCP-Client die angeforderten Rechte. Erst nach der Freigabe erhält der Client sein OAuth-Zugriffstoken für den MCP-Endpunkt über HTTP.

### A2: Konto fehlt, ist gesperrt oder hat keine Mitarbeiterrolle

PropertyFlow eröffnet keine Mitarbeitersitzung. Eine Anmeldung als Mieter verleiht keine Mitarbeiterrechte. Die Verwaltung ist für die Kontobereitstellung zuständig.

### A3: Code ist ungültig, abgelaufen, bereits verwendet oder wird nicht zugestellt

Der Mitarbeiterbereich bleibt gesperrt. Die Person kann gemäss den [gemeinsamen Einmalcode-Regeln](../specifications/fallzugriff-und-sicherheit.md#e-mail-einmalcode-für-registrierung-und-anmeldung) einen erneuten Versuch starten.

## Postconditions

- Bei erfolgreicher Anmeldung kann PropertyFlow die Person anhand ihrer stabilen Benutzer-ID identifizieren. Die Mitarbeiterrolle und die geltenden Fachregeln bestimmen die Berechtigung.
- Bei erteilter MCP-Freigabe kann der Client im Namen der Person innerhalb der delegierten Rechte handeln. PropertyFlow prüft bei jedem Aufruf Konto, Rolle, delegierte Aktion und Fallberechtigung. Kritische Aktionen folgen [FALL-10](../specifications/fallverwaltung.md#fall-10-automatisierung-und-menschliche-freigabe).

## Prüfkriterien

- **UC-006-AK-01:** Ein durch die Verwaltung angelegtes Mitarbeiterkonto meldet sich mit gültigem Einmalcode an. Der Mitarbeiterbereich ist gemäss Rolle zugänglich. Derselbe Code kann keine zweite Sitzung eröffnen.
- **UC-006-AK-02:** Ein unbekanntes, gesperrtes oder reines Mieterkonto erhält auch bei direktem Aufruf einer Mitarbeiterfunktion keinen Mitarbeiterzugriff.
- **UC-006-AK-03:** Nach der Web-Anmeldung verbindet die Person im selben Browserprofil den LLM-Chat. Bei gültiger Sitzung entfällt ein zweiter Einmalcode. Ein abgelehnter Zugriff ergibt kein MCP-Token. Bei erteilter Freigabe sind nur die delegierten Aktionen möglich.
