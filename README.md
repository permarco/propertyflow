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
Die drei Oberflächen sind als lokale Frontend-Demo mit Testdaten implementiert.
Die produktiven Integrationen folgen separat.

## Einstieg

- [Entwicklung und Demo](dev/README.md): Mailpit, Mieterregistrierung, Mitarbeiterkonto und Start in IntelliJ oder per Maven.
- [Tests](src/test/README.md): Java-, JavaScript- und Browserprüfungen mit separatem Test-Mailpit.
- [Camunda-Infrastruktur](infra/camunda/README.md): separate Workflow-Umgebung.

Die Frontend-Demo benötigt keine Datenbank und keinen laufenden Camunda-Server.
Die Anwendung läuft lokal auf Port 8081, die separate Browser-Testinstanz auf 8082.

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
│   │   ├── block1-grundgeruest-pruefung.md
│   │   └── block2-oberflaechen-pruefung.md
│   ├── frontend/
│   ├── specifications/
│   ├── use_cases/
│   └── architecture/
│       ├── c4-1-context.md
│       ├── c4-2-container.md
│       ├── c4-3-components.md
│       ├── module-structure.md
│       └── adr/
│           ├── ADR-001-grundarchitektur.md
│           ├── ADR-002-camunda8-workflow-orchestrierung.md
│           ├── ADR-003-praesentationsschicht.md
│           └── ADR-004-tokenbasierter-mieterzugriff.md
│
├── dev/
│   ├── README.md
│   ├── compose.yaml
│   └── compose.test.yaml
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

## Dokumentation

Die Projektdokumentation unter `docs/` ist die fachliche und
architektonische Source of Truth.

- [docs/vision.md](docs/vision.md)
  Problemstellung, Vision, Stakeholder und Kernfunktionen

- [docs/project-context.md](docs/project-context.md)
  Versionierter, kompakt an arc42 orientierter KI-Rahmen mit Architektur, Stack, Konventionen, NFRs und Sicherheitsleitplanken

- [Fallzugriff und Sicherheit](docs/specifications/fallzugriff-und-sicherheit.md): Persönlicher Fall-Link, freiwilliges Mieterkonto, Mitarbeiteranmeldung per E-Mail-Einmalcode und Zugriffsmatrix für Web und MCP.

- [Benachrichtigungen und Zustellung](docs/specifications/benachrichtigungen-und-zustellung.md): E-Mail bei sichtbaren Falländerungen, eigenen Nachrichten und Abschluss; Versandpflicht, Wiederholungen und Fehlerbehandlung.

- [Fallverwaltung](docs/specifications/fallverwaltung.md): Fallidentität, Annahme, Ursprungsdaten, Zuordnung und fachlicher Lebenszyklus mit Camunda-Anbindung.

- [Fallkommunikation](docs/specifications/fallkommunikation.md): Gemeinsame fachliche und sicherheitsrelevante Regeln für Frontend, Backoffice, Backend, Camunda, E-Mail und LLM-Integration.

- [Dringlichkeitsbewertung](docs/specifications/dringlichkeitsbewertung.md): Vereinbarte Stufen und Farben, automatische Neubewertung, Vorrang manueller Einstufungen und transparente Änderungshistorie.

- [Screen 01 – Mieter-Fallansicht](docs/frontend/ansicht-01-mieter-fallansicht.md): Erfassung, Kommunikation und lesbare Dringlichkeitshistorie unter dem persönlichen Falllink. Die sichtbare Ansicht aktualisiert aktive und abgeschlossene Fälle alle 20 Sekunden und zeigt nach Wiedereröffnung die Nachrichteneingabe wieder an. Fachliche Bedienung und visuelle Entwürfe sind dokumentiert; die lokale Demo ist implementiert. Produktive Integrationen folgen separat.

- [Screen 02 – Mitarbeiter-Fallübersicht](docs/frontend/ansicht-02-mitarbeiter-falluebersicht.md): Sieben Spalten einschliesslich sortierbarem Wiedervorlagedatum, Suche in veröffentlichten Nachrichten und internen Memos, Filter, Seitennavigation und Aktualisierung alle 20 Sekunden. Visuelle Entwürfe und lokale Demo sind vorhanden; produktive Integrationen folgen separat.

- [Screen 03 – Mitarbeiter-Falldetailansicht](docs/frontend/ansicht-03-mitarbeiter-falldetail.md): Falldaten, Verlauf und fachliche Aktionen wie «Bearbeitung fortsetzen», Wiedervorlage oder Abschluss. Im Zielablauf führt Camunda die Fallbearbeitung. Eine neue Mieternachricht beendet eine laufende Wiedervorlage und legt den Fall sofort zur Mitarbeiterprüfung vor. Die lokale Demo zeigt noch die bisherige direkte Statusauswahl.

- [Screen 04 – Anmeldung](docs/frontend/ansicht-04-login.md): E-Mail-Einmalcode, Rückmeldungen und Rückkehr zur angeforderten Seite sind in der lokalen Demo umgesetzt. Die MCP-Freigabe ist als Zielablauf beschrieben.

- [Block-2-Prüfprotokoll](docs/ai-usage/block2-oberflaechen-pruefung.md): Dokumentierter Abgleich der UI-Spezifikationen mit den Fachregeln und Use Cases; Unit-Tests, Veto-Notiz sowie Browser- und Performance-Nachweise sind enthalten.

- [docs/evaluation/evaluationsgrundlage.md](docs/evaluation/evaluationsgrundlage.md)
  Repräsentative Evaluationsfälle, Guardrails und Human-in-the-Loop-Grenzen

- [docs/ai-usage/block1-grundgeruest-pruefung.md](docs/ai-usage/block1-grundgeruest-pruefung.md)
  Nachweis der KI-Nutzung beim Skelett: Generierung, Review, Korrekturen und Veto-Entscheidungen

- [docs/architecture/c4-1-context.md](docs/architecture/c4-1-context.md)
  C4 Level 1 – System Context

- [docs/architecture/c4-2-container.md](docs/architecture/c4-2-container.md)
  C4 Level 2 – Container View

- [C4-Komponentenansicht](docs/architecture/c4-3-components.md)
  Module, Adapter und Kommunikationswege in der PropertyFlow-Anwendung

- [Schnittstellenentwurf](docs/architecture/schnittstellenentwurf-block-3.md)
  Schnittstellen und Stand ihrer Verträge

- [docs/architecture/module-structure.md](docs/architecture/module-structure.md)
  Interne Modulstruktur und Abhängigkeitsregeln des Backends

- [docs/architecture/adr/ADR-001-grundarchitektur.md](docs/architecture/adr/ADR-001-grundarchitektur.md)
  Entscheidung für den modularen Monolithen

- [docs/architecture/adr/ADR-002-camunda8-workflow-orchestrierung.md](docs/architecture/adr/ADR-002-camunda8-workflow-orchestrierung.md)
  Entscheidung für Camunda 8 zur Orchestrierung langlebiger Prozesse

- [ADR-003: Präsentationsschicht](docs/architecture/adr/ADR-003-praesentationsschicht.md)
  Entscheidung für SSR mit Thymeleaf und gezieltem Vanilla JavaScript

- [ADR-004: Tokenbasierter Mieterzugriff](docs/architecture/adr/ADR-004-tokenbasierter-mieterzugriff.md)
  Zugang ohne Benutzerkonto über einen wiederverwendbaren persönlichen Link; Hash und verschlüsselte Versandkopie

### Use Cases und gezielte Nachweise

Geschäftsregeln (`BR`) und Akzeptanzkriterien (`AK`) beginnen mit der Nummer ihres Use Cases, zum Beispiel `UC-001-BR-001` und `UC-005-AK-01`.

- [Use-Case-Übersicht](docs/use_cases/README.md) mit Links auf die einzelnen Abläufe und ihrem Umsetzungsstand
- [Generierte Unit-Tests und Veto](docs/ai-usage/block2-oberflaechen-pruefung.md#ergänzung-vom-12102026-generierte-unit-tests-und-mutation)
- [Browser- und Performance-Nachweise](docs/ai-usage/block2-oberflaechen-pruefung.md#ergänzung-browser-qualitätsnachweis-und-präsentationsentscheidung)
- [Camunda-Umgebung](infra/camunda/README.md)

## KI-gestützte Entwicklung

Der [KI-Service-Baustein](docs/ai-usage/ki-service-vertrag.md) kapselt einen
Spring-AI-Aufruf als internes Analyse-Tool mit Eingabevalidierung, typisierten
Erfolgs-/Fehlerantworten und einem opt-in Live-Test gegen lokales Ollama.

Für die Entwicklung wird GitHub Copilot eingesetzt.

Repository-weite Anweisungen für KI-Werkzeuge befinden sich unter:

```text
.github/copilot-instructions.md
```

[AGENTS.md](AGENTS.md) verweist auf diese zentralen Instruktionen.

Die Architekturentscheidungen, Qualitätsanforderungen und
Sicherheitsgrenzen werden durch die Projektdokumentation vorgegeben und
nicht an das KI-Werkzeug delegiert.

### Fachlicher Abgleich

Der [fachliche Abgleich vom 13.10.2026](docs/ai-usage/block2-oberflaechen-pruefung.md#fachlicher-abgleich-vom-13102026) dokumentiert die Übereinstimmung von Fachregeln und Demo, verbleibende Implementierungsabweichungen, Dokumentwidersprüche und bewusst ausstehende Produktivintegrationen.
