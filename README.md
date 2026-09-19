# PropertyFlow

PropertyFlow ist eine KI-gestützte Webanwendung zur Triage und Bearbeitung
von Mieteranliegen für Immobilienverwaltungen.

Eingehende Anliegen werden über ein Webformular oder per E-Mail erfasst,
strukturiert und hinsichtlich ihrer Dringlichkeit bewertet. Eine
RAG-gestützte Wissensbasis unterstützt bei der Ermittlung relevanter
Richtlinien, Regelwerke und früherer Fälle. Auf dieser Grundlage erstellt
PropertyFlow Zuständigkeits- und Handlungsempfehlungen.

Die abschliessende fachliche Entscheidung verbleibt bei der
Immobilienbewirtschaftung.

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
│   │   └── evaluation-basis.md
│   ├── ai-usage/
│   │   └── block1-skeleton-review.md
│   └── architecture/
│       ├── c4-context.md
│       ├── c4-container.md
│       ├── module-structure.md
│       └── adr/
│           ├── ADR-001-grundarchitektur.md
│           └── ADR-002-camunda8-workflow-orchestration.md
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
  Versionierter KI-Rahmen mit Architektur, Stack, Konventionen, NFRs und Sicherheitsleitplanken

- `docs/evaluation/evaluation-basis.md`  
  Repräsentative Evaluationsfälle, Guardrails und Human-in-the-Loop-Grenzen

- `docs/ai-usage/block1-skeleton-review.md`  
  Nachweis der KI-Nutzung beim Skelett: Generierung, Review, Korrekturen und Veto-Entscheidungen

- `docs/architecture/c4-context.md`  
  C4 Level 1 – System Context

- `docs/architecture/c4-container.md`  
  C4 Level 2 – Container View

- `docs/architecture/module-structure.md`  
  Interne Modulstruktur und Abhängigkeitsregeln des Backends

- `docs/architecture/adr/ADR-001-grundarchitektur.md`  
  Entscheidung für den modularen Monolithen

- `docs/architecture/adr/ADR-002-camunda8-workflow-orchestration.md`  
  Entscheidung für Camunda 8 zur Orchestrierung langlebiger Prozesse

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
