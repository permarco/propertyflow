# C4 Level 1 – System Context

## Systemübersicht

PropertyFlow ist das zentrale System für die Verwaltung von Immobilien-, Miet- und Betriebsprozessen. Es bildet die fachliche Klammer zwischen Immobilienmanagement, operativem Service und Managementsicht.

## Aktoren

- Immobilienverwalter: pflegen Objektdaten, Verträge und Zuständigkeiten
- Betriebsservice: dokumentiert Wartungs- und Reparaturarbeiten
- Management: nutzt Kennzahlen und aktuellen Status für Entscheidungen
- Mieter/Kunden: erhalten je nach Prozess Sicht auf relevante Informationen


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
