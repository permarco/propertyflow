# C4 Level 3 – Komponentenansicht

Die Ansicht öffnet den Container «PropertyFlow-Anwendung» aus der
[Containeransicht](c4-2-container.md). Fachmodule und Adapter laufen gemeinsam
in einer Spring-Boot-Anwendung. PostgreSQL ist ein eigener Container innerhalb
der PropertyFlow-Systemgrenze. Die Pfeile benennen die wichtigsten Beziehungen.
Der Umsetzungsstand steht unter der Grafik.

```mermaid
flowchart TB
    WEB["Browser"]
    CHAT["LLM-Chat<br/>mit MCP-Client"]

    subgraph PF["Systemgrenze · PropertyFlow"]
        subgraph APP["PropertyFlow-Anwendung · Spring Boot"]
            MVC["Web-Adapter<br/>Spring MVC und Thymeleaf"]
            MCP["MCP-Adapter"]

            ZUGANG["access<br/>Anmeldung und Kontozugriff"]
            INTAKE["intake<br/>Fallannahme"]
            TRIAGE["triage<br/>Analyse und Dringlichkeit"]
            EMPFEHLUNG["recommendation<br/>Zuständigkeit und Vorgehen"]
            BEARBEITUNG["caseprocessing<br/>Fallanlage, Bearbeitung und Kommunikation"]

            SMTP["Versandfunktion<br/>mit SMTP-Adapter"]
            WORKFLOW["Camunda-Client<br/>und Job Worker"]
            KI["Modelladapter<br/>hinter Fachports"]
            ZUORDNUNG["Stammdatenadapter"]
        end

        DB[("Fachdatenbank<br/>PostgreSQL")]
    end

    MAIL["E-Mail-Dienst"]
    CAMUNDA["Camunda 8"]
    MODELL["LLM-Anbieter"]
    STAMMDATEN["Mieterstammdaten"]

    WEB -->|"SS-001"| MVC
    CHAT -->|"SS-004"| MCP
    MVC --> ZUGANG
    MVC --> INTAKE
    MVC --> BEARBEITUNG
    MCP -->|"Benutzer und Delegation"| ZUGANG
    MCP -->|"Mögliche Fallfunktionen"| BEARBEITUNG

    ZUGANG -->|"Einmalcode"| SMTP
    INTAKE -->|"Fallanlage · synchron"| BEARBEITUNG
    BEARBEITUNG -->|"Prozessstartauftrag · asynchron"| WORKFLOW
    INTAKE -->|"Fallannahme"| SMTP
    INTAKE -->|"Initiale Zuordnung"| ZUORDNUNG
    WORKFLOW -->|"Bewertungsjob"| TRIAGE
    WORKFLOW -->|"Empfehlungsjob"| EMPFEHLUNG
    WORKFLOW <-->|"Fallereignisse und Jobs"| BEARBEITUNG
    TRIAGE -->|"AnalysisModel"| KI
    EMPFEHLUNG --> KI
    BEARBEITUNG -->|"Formulierung"| KI
    BEARBEITUNG -->|"Fallbenachrichtigung"| SMTP

    SMTP -->|"SS-002"| MAIL
    WORKFLOW <-->|"SS-003"| CAMUNDA
    KI -->|"SS-005"| MODELL
    ZUORDNUNG -->|"SS-006"| STAMMDATEN
    APP -->|"JDBC"| DB

    classDef module fill:#164e73,stroke:#60a5fa,color:#ffffff
    classDef adapter fill:#334155,stroke:#6d8eae,color:#ffffff
    classDef peer fill:#1e293b,stroke:#8292a8,color:#ffffff
    class ZUGANG,INTAKE,TRIAGE,EMPFEHLUNG,BEARBEITUNG module
    class MVC,MCP,SMTP,WORKFLOW,KI,ZUORDNUNG adapter
    class WEB,CHAT,MAIL,CAMUNDA,MODELL,STAMMDATEN,DB peer
    style PF fill:#1e293b,stroke:#006fbb,stroke-width:2px,color:#ffffff
    style APP fill:#1e293b,stroke:#6d8eae,stroke-width:1px,color:#ffffff
```

## Kommunikation in PropertyFlow

- **Zugriff.** Der Web-Adapter bedient die Anmeldung und Fallfunktionen. Der
  MCP-Adapter soll ausgewählte Funktionen derselben Anwendungsdienste anbieten.
  Die Dienste prüfen die geltenden Konto-, Fall- und Aktionsrechte. Das erste
  Werkzeug für Mieter ist [«Fallstand abrufen»](schnittstellenentwurf-block-3.md#ss-004--mcp-werkzeug-fallstand-abrufen). [ZUG-04](../specifications/fallzugriff-und-sicherheit.md#zug-04-rechte-und-vertrauensgrenzen) · [UC-023](../use_cases/UC-023-mcp-zugriff-delegieren.md)
- **Fallanlage.** Intake nimmt das Anliegen entgegen und ruft die öffentliche
  Fallanlage von Case Processing synchron auf. Case Processing besitzt den
  Fall ab der Anlage und speichert ihn mit «Eingegangen» zusammen mit dem
  Prozessstartauftrag in einer Datenbanktransaktion. Intake bestätigt die
  Annahme nach dem Commit.
- **Fallprozess.** Der Camunda-Client startet den Fallprozess anhand des
  gespeicherten Auftrags asynchron zur Einreichung. Die Worker
  verbinden sich mit Camunda, übernehmen bereitgestellte Jobs, rufen die
  zuständigen Module auf und melden das Ergebnis zurück. Der Triage-Aufruf
  im Worker ist synchron. Nach der Fallannahme veranlasst Camunda alle
  fachlichen Statuswechsel. Mitarbeiteraktionen laufen über den führenden
  Prozess. Bei ausstehendem Prozessstart oder Camunda-Ausfall ist der
  Fallzugriff für Mitarbeitende lesend. [FALL-06](../specifications/fallverwaltung.md#fall-06-fachlicher-status-und-technischer-workflow) · [Erstbewertung](module-structure.md#erstbewertung--job-worker-und-triage) · [ADR-002](adr/ADR-002-camunda8-workflow-orchestrierung.md#integrationsprinzipien)
- **Wiedervorlage.** Camunda veranlasst beim wirksamen Timerablauf die
  Mitarbeiterprüfung. Eine während der Wartezeit gespeicherte Mieternachricht
  hebt die Wiedervorlage auf und führt ebenfalls sofort zur Mitarbeiterprüfung.
  Der Worker ruft die Fallbearbeitung auf, die den aktuellen Fallstand prüft
  und den Übergang speichert. Treffen Nachricht und Timer zusammen, entsteht
  für dieselbe Wiedervorlage nur eine Mitarbeiteraufgabe.
  [FALL-11](../specifications/fallverwaltung.md#fall-11-wiedervorlage)
- **Daten und Integrationen.** Der JDBC-Pfeil steht für die modulinterne
  Persistenz. Module greifen über fachliche Verträge auf fremde Daten zu.
  Der Modelladapter-Kasten bündelt die Sicht auf die Modellanbindung.
  `AnalysisModel` ist der Port für die Triage. Empfehlungen und Formulierung
  haben eigene fachliche Zwecke und Kontextregeln. [Modulstruktur](module-structure.md#abhängigkeitsregeln) · [ADR-001](adr/ADR-001-grundarchitektur.md#entscheidung) · [KOM-02](../specifications/fallkommunikation.md#kom-02-llm-kommunikation-ohne-interne-memos)
- **Versand.** Anmeldung und Registrierung veranlassen Einmalcodes.
  Fallannahme und Fallbearbeitung veranlassen Fallbenachrichtigungen über
  dauerhafte Aufträge nach den vereinbarten Auslösern. [Einmalcode](../specifications/fallzugriff-und-sicherheit.md#e-mail-einmalcode-für-registrierung-und-anmeldung) · [BEN-03](../specifications/benachrichtigungen-und-zustellung.md#ben-03-speicherung-und-zuverlässige-übergabe)

## Umsetzungsstand

Die lokale Demo zeigt Webzugriff, Fallbearbeitung und `access` mit
SMTP-Einmalcodes. Sie hält Fälle und Konten im Arbeitsspeicher. Triage ist als
separater Prototyp erprobt. Dauerhafte Fallannahme, die fachliche Camunda-Anbindung,
Fallbenachrichtigungen, die Modellanbindung für Empfehlungen und Formulierung,
die Stammdatenanbindung sowie MCP sind Zielarchitektur.
[Demo](../../dev/README.md) · [Triage-Vertrag](../ai-usage/ki-service-vertrag.md)

Für den geplanten E-Mail-Eingang fehlt ein eigener Kanalvertrag. Die
Wissensbasis für Empfehlungen ist vorgesehen. Ihre Speichertechnik und ihr
Deployment sind offen. [UC-020](../use_cases/UC-020-mieteranliegen-per-e-mail-entgegennehmen.md) · [UC-018](../use_cases/UC-018-zustaendigkeit-und-vorgehen-empfehlen.md)
