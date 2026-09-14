# C4 Level 1 – System Context

PropertyFlow unterstützt Immobilienbewirtschaftungen bei der
KI-gestützten Triage und Bearbeitung von Mieteranliegen.

```mermaid
flowchart LR
    M["👤 Mieterin / Mieter"]
    B["👤 Immobilienbewirtschaftung"]

    PF["PropertyFlow
    KI-gestützte Triage und Bearbeitung
    von Mieteranliegen"]

    MAIL["E-Mail-System
    Externes System"]

    ERP["Immobilienverwaltungs- /
    Mieterstammdaten-System
    Externes System"]

    LLM["LLM-Provider
    Externer KI-Dienst"]

    M -->|"reicht Anliegen über Webformular ein"| PF
    M -->|"schickt Anliegen per E-Mail"| MAIL
    MAIL -->|"liefert per E-Mail eingegangene Anliegen"| PF

    B <-->|"prüft Anliegen, KI-Empfehlungen
    und steuert die Bearbeitung"| PF

    PF <-->|"bezieht Daten zur Identifikation
    von Mieter, Mietverhältnis und Objekt"| ERP

    PF <-->|"sendet aufbereitete Eingaben /
    erhält Analyse und Empfehlungen"| LLM
