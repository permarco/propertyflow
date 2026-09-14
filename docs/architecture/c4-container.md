# C4 Level 2 – Container View

```mermaid
flowchart LR
    MIETER["👤 Mieterin / Mieter"]
    BEWIRTSCHAFTER["👤 Immobilienbewirtschaftung"]

    MAIL["E-Mail-System<br/>Externes System"]

    ERP["Immobilienverwaltungs- /<br/>Mieterstammdaten-System<br/>Externes System"]

    LLM["LLM-Provider<br/>Fine-Tuned Model / Foundation Model<br/>Externes System"]
    
    CAMUNDA["<b>Camunda 8</b><br/><br/>Prozess-Orchestrierung<br/>Workflow-State<br/>Timer / Wait States<br/>Human Tasks"]
    
    subgraph PF["PropertyFlow"]
        direction TB

        WEB["<b>Web Frontend</b><br/>Erfassung und Bearbeitung<br/>von Mieteranliegen"]
        
        BACKEND["<b>Backend Application</b><br/>Geschäftslogik, Triage,<br/>Workflow-Integration / Job Worker,<br/>KI-Orchestrierung und RAG"]

        DB[("Application Database<br/>Anliegen, Bearbeitungsstatus,<br/>KI-Ergebnisse, Bearbeitungsverlauf")]

        VS[("Knowledge Store / Vector Store<br/>Richtlinien, Regelwerke,<br/>freigegebene frühere Fälle")]

        WEB -->|"HTTPS / REST"| BACKEND
        BACKEND -->|"liest / schreibt"| DB
        BACKEND -->|"semantische Suche / Retrieval"| VS
    end  
    
    MIETER -->|"erfasst Anliegen"| WEB
    MIETER -->|"sendet Anliegen per E-Mail"| MAIL
    BEWIRTSCHAFTER -->|"prüft und bearbeitet Anliegen"| WEB
    MAIL -->|"liefert Mieteranliegen"| BACKEND
    BACKEND <-->|"Mieter-, Mietvertrags-<br/>und Objektdaten"| ERP
    BACKEND <-->|"KI-Aufruf / strukturierte Ausgabe"| LLM
    BACKEND <-->|"Prozesse starten<br/>Jobs bearbeiten<br/>User Tasks abschliessen"| CAMUNDA

