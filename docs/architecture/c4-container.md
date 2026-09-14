# C4 Level 2 – Container View

```mermaid
flowchart LR
    MIETER["👤 Mieterin / Mieter"]
    BEW["👤 Immobilienbewirtschaftung"]

    MAIL["E-Mail-System<br/>Externes System"]

    ERP["Immobilienverwaltungs- /<br/>Mieterstammdaten-System<br/>Externes System"]

    LLM["LLM-Provider<br/>Fine-Tuned Model / Foundation Model<br/>Externes System"]

    subgraph PF["PropertyFlow"]
        direction TB

        WEB["<b>Web Frontend</b><br/>Erfassung und Bearbeitung<br/>von Mieteranliegen"]

        APP["<b>Backend Application</b><br/>Geschäftslogik, Triage,<br/>Embedded Camunda 7,<br/>Workflow-Steuerung,<br/>KI-Orchestrierung und RAG"]

        DB[("Application Database<br/>Anliegen, Status,<br/>KI-Ergebnisse, Bearbeitungsverlauf, Workflow-State")]

        VS[("Knowledge Store / Vector Store<br/>Richtlinien, Regelwerke,<br/>freigegebene frühere Fälle")]

        WEB -->|"HTTPS / REST"| APP
        APP -->|"liest / schreibt"| DB
        APP -->|"semantische Suche / Retrieval"| VS
    end

    MIETER -->|"erfasst Anliegen"| WEB
    MIETER -->|"sendet Anliegen per E-Mail"| MAIL
    
    BEW -->|"prüft und bearbeitet Anliegen"| WEB

    MAIL -->|"liefert Mieteranliegen"| APP

    APP <-->|"Mieter-, Mietvertrags-<br/>und Objektdaten"| ERP

    APP <-->|"KI-Aufruf / strukturierte Ausgabe"| LLM
