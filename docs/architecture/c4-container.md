# C4 Level 2 – Container View

Die geplante Architektur folgt ADR-001 bis ADR-004: Der modulare
Spring-Boot-Monolith rendert UI1 bis UI3 mit Thymeleaf. Im Browser ergänzt
gezieltes JavaScript die periodische Aktualisierung und die KI-Formulierung
mit Streaming in UI3. Die Darstellung ist kein Implementierungsnachweis.

```mermaid
flowchart LR
    MIETER["👤 Mieterin / Mieter"]
    BEWIRTSCHAFTER["👤 Immobilienbewirtschaftung"]

    WEB["<b>Browser</b><br/>UI1: Mieter-Fallansicht<br/>UI2: Mitarbeiterübersicht<br/>UI3: Mitarbeiter-Falldetail<br/>HTML / gezieltes JavaScript"]

    MAIL["E-Mail-System<br/>Externes System"]

    ERP["Immobilienverwaltungs- /<br/>Mieterstammdaten-System<br/>Externes System"]

    LLM["LLM-Provider<br/>Fine-Tuned Model / Foundation Model<br/>Externes System"]
    
    CAMUNDA["<b>Camunda 8</b><br/><br/>Prozess-Orchestrierung<br/>technischer Workflow-State<br/>Jobs<br/>Timer / Wait States<br/>User-Task-State"]
    
    subgraph PF["PropertyFlow"]
        direction TB

        BACKEND["<b>Backend Application</b><br/>Spring Boot / Thymeleaf SSR<br/>Geschäftslogik und Triage<br/>Camunda Client / Job Worker<br/>KI-Orchestrierung und RAG"]

        DB[("Application Database<br/>Anliegen, fachlicher Bearbeitungsstatus,<br/>KI-Ergebnisse, Bearbeitungsverlauf,<br/>Auditinformationen")]

        VS[("Knowledge Store / Vector Store<br/>Richtlinien, Regelwerke,<br/>freigegebene frühere Fälle")]

        BACKEND -->|"liest / schreibt"| DB
        BACKEND -->|"semantische Suche / Retrieval"| VS
    end  
    
    WEB <-->|"HTTPS: HTML / Formulare<br/>ergänzende Leseabrufe / KI-Stream"| BACKEND
    MIETER -->|"erfasst und kommuniziert über UI1"| WEB
    MIETER -.->|"weitere Projektvision: E-Mail-Annahme"| MAIL
    BEWIRTSCHAFTER -->|"prüft und bearbeitet Anliegen"| WEB
    MAIL -.->|"Eingangskanal: Vertrag noch offen"| BACKEND
    BACKEND -->|"veranlasst Benachrichtigungen mit Fall-Link"| MAIL
    MAIL -->|"stellt Fallbenachrichtigungen zu"| MIETER
    BACKEND <-->|"Mieter-, Mietvertrags-<br/>und Objektdaten"| ERP
    BACKEND <-->|"KI-Aufruf / strukturierte Ausgabe"| LLM
    BACKEND <-->|"Prozesse starten / Nachrichten korrelieren<br/>Jobs bearbeiten / Wiedervorlage-Timer<br/>Mitarbeiterentscheidungen abstimmen"| CAMUNDA
```

PropertyFlow hält Nachrichten, Memos, fachlichen Status, wirksame Dringlichkeit,
Wiedervorlagetermin und Auditdaten. Camunda führt den technischen Prozess und
verarbeitet neue Mieternachrichten auch während einer Wiedervorlage.
Browserzugriffe laufen über PropertyFlow; alle drei Ansichten lesen gespeicherte
Ergebnisse, und nur UI3 bietet den interaktiven Formulierungs-Stream.

Die fachlichen Regeln und noch offenen technischen Verträge stehen in
[Fallverwaltung](../specifications/fallverwaltung.md),
[Fallkommunikation](../specifications/fallkommunikation.md),
[Benachrichtigungen](../specifications/benachrichtigungen-und-zustellung.md) und
[Fallzugriff](../specifications/fallzugriff-und-sicherheit.md).
Für Mitarbeitende gibt es keine Anmeldung in PropertyFlow; die technische
Absicherung des Mitarbeiterbereichs und die individuelle Zuordnung von
Änderungen sind unter ZUG-04 noch zu konkretisieren.
