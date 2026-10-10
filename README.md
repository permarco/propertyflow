# PropertyFlow

PropertyFlow ist eine KI-gestützte Webanwendung zur Triage und Bearbeitung
von Mieteranliegen für Immobilienverwaltungen.

Eingehende Anliegen werden über ein Webformular oder per E-Mail erfasst,
strukturiert und hinsichtlich ihrer Dringlichkeit bewertet. Eine
RAG-gestützte Wissensbasis unterstützt bei der Ermittlung relevanter
Richtlinien, Regelwerke und früherer Fälle. Auf dieser Grundlage erstellt
PropertyFlow Zuständigkeits- und Handlungsempfehlungen.

Der aktuelle Spezifikationsstand beschreibt die Web-Erfassung und die weitere
Kommunikation über den persönlichen Fall-Link (UI1), die Mitarbeiterübersicht
(UI2) und die Fallbearbeitung (UI3). Analyse und Dringlichkeitsbewertung laufen
automatisch im Camunda-Prozess. Die interaktive KI-Funktion in UI3 hilft beim
Formulieren einer Nachricht, die der Mitarbeiter prüft und bewusst sendet.
Die E-Mail-Annahme aus der Projektvision benötigt weiterhin einen eigenen
Kanalvertrag; die vereinbarten Benachrichtigungen verweisen auf die Fallansicht.
Die Oberflächen sind fachlich spezifiziert; ihre Implementierung folgt separat.

Die fachliche Verantwortung verbleibt bei der Immobilienbewirtschaftung.
Mitarbeitende und das System können Fälle gemäss den vereinbarten
[Abschlussregeln](docs/specifications/fallverwaltung.md#fall-07-abschluss-und-zeit-danach)
abschliessen; verpflichtende menschliche Prüfungen bleiben bestehen.
Bei Kosten bzw. verbindlicher externer Beauftragung, sehr dringlichen oder
schwerwiegenden Fällen sowie starken oder eskalierten Mieterbeschwerden entscheidet
ein Mitarbeiter gemäss [FALL-10](docs/specifications/fallverwaltung.md#fall-10-automatisierung-und-menschliche-freigabe).

## Kernfunktionen

- Erfassung und Strukturierung von Mieteranliegen
- KI-gestützte Dringlichkeitsbewertung
- RAG-gestützte Zuständigkeits- und Handlungsempfehlung
- Prüfung und Bearbeitung durch die Immobilienbewirtschaftung
- Orchestrierung langlebiger Bearbeitungsprozesse mit Camunda 8

## Architektur

Das PropertyFlow-Backend wird als modularer Monolith umgesetzt.

Die fachlichen Verantwortlichkeiten werden in klar abgegrenzte Module
aufgeteilt. Externe Systeme wie LLM-Provider, E-Mail-System,
Mieterstammdatensystem und Camunda 8 werden über definierte
Integrationsschnittstellen angebunden.

Camunda 8 wird als separate Orchestrierungskomponente für langlebige
Prozesse eingesetzt. Die fachlichen Daten verbleiben in PropertyFlow,
während Camunda den technischen Workflow-State verwaltet.

Die Architekturentscheidungen sind unter `docs/architecture/adr/`
dokumentiert.

## Technologie-Stack

- Java
- Spring Boot
- Spring AI
- Camunda 8
- PostgreSQL
- Maven
- Docker / Docker Compose

Weitere Technologien, insbesondere für semantisches Retrieval und
Vector Storage, werden im Verlauf des Projekts evaluiert.

## Projektstruktur

```text
propertyflow/
├── .github/
│   └── copilot-instructions.md
│
├── docs/
│   ├── vision.md
│   ├── project-context.md
│   ├── evaluation/
│   │   └── evaluationsgrundlage.md
│   ├── ai-usage/
│   │   └── block1-grundgeruest-pruefung.md
│   └── architecture/
│       ├── c4-context.md
│       ├── c4-container.md
│       ├── module-structure.md
│       └── adr/
│           ├── ADR-001-grundarchitektur.md
│           ├── ADR-002-camunda8-workflow-orchestrierung.md
│           ├── ADR-003-praesentationsschicht.md
│           └── ADR-004-tokenbasierter-mieterzugriff.md
│
├── infra/
│   └── camunda/
│       ├── docker-compose.yaml
│       ├── configuration/
│       └── ...
│
├── src/
│   ├── main/
│   └── test/
│
├── AGENTS.md
├── pom.xml
├── compose.yaml
├── mvnw
└── mvnw.cmd
```

## Lokale Entwicklungsumgebung

### Voraussetzungen

Für die lokale Entwicklung werden benötigt:

- Java
- Docker / Docker Compose
- Git

Maven muss nicht separat installiert werden, da das Projekt den Maven
Wrapper verwendet.

## Camunda 8 starten

Die lokale Camunda-8-Umgebung befindet sich unter:

```text
infra/camunda/
```

Camunda starten:

```bash
cd infra/camunda
docker compose up -d
```

Status prüfen:

```bash
docker compose ps
```

Camunda stoppen:

```bash
docker compose down
```

Die Docker-Compose-Konfiguration ist ausschliesslich für die lokale
Entwicklung vorgesehen.

## PropertyFlow starten

Vom Projekt-Root aus:

```bash
./mvnw clean test
./mvnw spring-boot:run
```

Unter Windows:

```powershell
mvnw.cmd clean test
mvnw.cmd spring-boot:run
```

## Dokumentation

Die Projektdokumentation unter `docs/` ist die fachliche und
architektonische Source of Truth.

- `docs/vision.md`  
  Problemstellung, Vision, Stakeholder und Kernfunktionen

- `docs/project-context.md`  
  Versionierter, kompakt an arc42 orientierter KI-Rahmen mit Architektur, Stack, Konventionen, NFRs und Sicherheitsleitplanken

- [Fallzugriff und Sicherheit](docs/specifications/fallzugriff-und-sicherheit.md): Persönlicher Tokenlink ohne zeitliche Ablauffrist, Trennung von Mieter- und Mitarbeiterfunktionen sowie bewusster Widerruf und administrativer Ersatz. Für Mitarbeitende gibt es keine Anmeldung; die technische Absicherung des Mitarbeiterbereichs ist noch zu konkretisieren.

- [Benachrichtigungen und Zustellung](docs/specifications/benachrichtigungen-und-zustellung.md): E-Mail bei sichtbaren Falländerungen, eigenen Nachrichten und Abschluss; Versandpflicht, Wiederholungen und Fehlerbehandlung.

- [Fallverwaltung](docs/specifications/fallverwaltung.md): Fallidentität, Annahme, Ursprungsdaten, Zuordnung und fachlicher Lebenszyklus mit Camunda-Anbindung.

- [Fallkommunikation](docs/specifications/fallkommunikation.md): Gemeinsame fachliche und sicherheitsrelevante Regeln für Frontend, Backoffice, Backend, Camunda, E-Mail und LLM-Integration.

- [Dringlichkeitsbewertung](docs/specifications/dringlichkeitsbewertung.md): Vereinbarte Stufen und Farben, automatische Neubewertung, Vorrang manueller Einstufungen und transparente Änderungshistorie.

- [Screen 01 – Mieter-Fallansicht](docs/frontend/ansicht-01-mieter-fallansicht.md): Erfassung, Kommunikation und lesbare Dringlichkeitshistorie unter dem persönlichen Falllink. Die sichtbare Ansicht aktualisiert aktive und abgeschlossene Fälle alle 20 Sekunden und zeigt nach Wiedereröffnung die Nachrichteneingabe wieder an. Fachliche Bedienung spezifiziert; Umsetzung und visuelle Ausgestaltung folgen.

- [Screen 02 – Mitarbeiter-Fallübersicht](docs/frontend/ansicht-02-mitarbeiter-falluebersicht.md): Sieben Spalten einschliesslich sortierbarem Wiedervorlagedatum, Suche in veröffentlichten Nachrichten und internen Memos, Filter, Seitennavigation und Aktualisierung alle 20 Sekunden. Umsetzung und visuelle Ausgestaltung folgen.

- [Screen 03 – Mitarbeiter-Falldetailansicht](docs/frontend/ansicht-03-mitarbeiter-falldetail.md): Verlauf links sowie Falldaten und Aktionen rechts; externe Nachrichten, interne Memos, KI-Formulierung mit Streaming, manuelle Status-/Dringlichkeitsänderung, Abschluss und Wiedereröffnung. Wiedervorlagen können in Tagen oder als Datum gesetzt, geändert und gelöscht werden; Camunda wartet parallel zur Verarbeitung neuer Mieternachrichten. Fachliche Bedienung spezifiziert; Umsetzung und visuelle Ausgestaltung folgen.

- [Block-2-Prüfprotokoll](docs/ai-usage/block2-oberflaechen-pruefung.md): Dokumentierter Abgleich der UI-Spezifikationen mit den Fachregeln und Use Cases; Umsetzungsnachweise folgen später.

- `docs/evaluation/evaluationsgrundlage.md`  
  Repräsentative Evaluationsfälle, Guardrails und Human-in-the-Loop-Grenzen

- `docs/ai-usage/block1-grundgeruest-pruefung.md`  
  Nachweis der KI-Nutzung beim Skelett: Generierung, Review, Korrekturen und Veto-Entscheidungen

- `docs/architecture/c4-context.md`  
  C4 Level 1 – System Context

- `docs/architecture/c4-container.md`  
  C4 Level 2 – Container View

- `docs/architecture/module-structure.md`  
  Interne Modulstruktur und Abhängigkeitsregeln des Backends

- `docs/architecture/adr/ADR-001-grundarchitektur.md`  
  Entscheidung für den modularen Monolithen

- `docs/architecture/adr/ADR-002-camunda8-workflow-orchestrierung.md`  
  Entscheidung für Camunda 8 zur Orchestrierung langlebiger Prozesse

- [ADR-003: Präsentationsschicht](docs/architecture/adr/ADR-003-praesentationsschicht.md)  
  Entscheidung für SSR mit Thymeleaf und gezieltem Vanilla JavaScript

- [ADR-004: Tokenbasierter Mieterzugriff](docs/architecture/adr/ADR-004-tokenbasierter-mieterzugriff.md)  
  Zugang ohne Benutzerkonto über einen wiederverwendbaren persönlichen Link; Hash und verschlüsselte Versandkopie

## KI-gestützte Entwicklung

Für die Entwicklung wird GitHub Copilot eingesetzt.

Repository-weite Anweisungen für KI-Werkzeuge befinden sich unter:

```text
.github/copilot-instructions.md
```

`AGENTS.md` verweist auf diese zentralen Instruktionen.

Die Architekturentscheidungen, Qualitätsanforderungen und
Sicherheitsgrenzen werden durch die Projektdokumentation vorgegeben und
nicht an das KI-Werkzeug delegiert.
