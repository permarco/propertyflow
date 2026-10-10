# C4 Level 1 – System Context

PropertyFlow unterstützt Immobilienbewirtschaftungen bei der
KI-gestützten Triage und Bearbeitung von Mieteranliegen.

Die Darstellung beschreibt den geplanten Systemkontext. Der vereinbarte
UI-Umfang umfasst Web-Erfassung und Fallkommunikation über UI1 sowie
Mitarbeiterübersicht und Fallbearbeitung über UI2/UI3. Benachrichtigungen
werden per E-Mail mit dem persönlichen Fall-Link zugestellt.

```mermaid
flowchart LR
    M["👤 Mieterin / Mieter"]
    B["👤 Immobilienbewirtschaftung"]

    PF["PropertyFlow<br/>KI-gestützte Triage und Bearbeitung<br/>von Mieteranliegen"]

    MAIL["E-Mail-System<br/>Externes System"]

    ERP["Immobilienverwaltungs- /<br/>Mieterstammdaten-System<br/>Externes System"]

    LLM["LLM-Provider<br/>Externer KI-Dienst"]

    M <-->|"erfasst Anliegen, liest Verlauf<br/>und sendet Nachrichten über UI1"| PF
    PF -->|"veranlasst Fallbenachrichtigungen"| MAIL
    MAIL -->|"sendet Hinweise mit persönlichem Fall-Link"| M
    M -.->|"weitere Projektvision: Anliegen per E-Mail"| MAIL
    MAIL -.->|"E-Mail-Annahme: Kanalvertrag noch offen"| PF

    B <-->|"prüft Fälle in UI2/UI3,<br/>bearbeitet und kommuniziert"| PF

    PF <-->|"bezieht Daten zur Identifikation<br/>von Mieter, Mietverhältnis und Objekt"| ERP

    PF <-->|"sendet zulässige Eingaben / erhält<br/>Analyse, Empfehlungen und Formulierungen"| LLM
```

Analyse und Dringlichkeitsbewertung erfolgen automatisch im Camunda-Prozess;
die interaktive KI-Funktion unterstützt die bewusste Formulierung einer externen
Mitarbeiternachricht. Für diesen Kommunikationskontext gilt
[KOM-02](../specifications/fallkommunikation.md#kom-02-llm-kommunikation-ohne-interne-memos).
Die weitergehende E-Mail-Annahme aus der Vision benötigt gemäss
[UC-001](../use_cases/UC-001-mieteranliegen-einreichen.md) einen eigenen Kanalvertrag.
Externe Beauftragungen werden durch die Verwaltung ausserhalb von PropertyFlow
organisiert; eine automatische Beauftragungsschnittstelle gehört nicht zum Umfang.
