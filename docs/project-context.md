# PropertyFlow – Projekt-Kontext

**Version:** 0.1  
**Status:** Block 1 – Konzeption

## Zweck

Dieses Dokument ist der verbindliche KI-Rahmen für die Entwicklung von
PropertyFlow. Es fasst die wichtigsten Architektur-, Technologie-,
Qualitäts- und Sicherheitsleitplanken zusammen und verweist für Details auf
die versionierte Projektdokumentation.

KI-Werkzeuge sollen dieses Dokument vor repo-weiten oder architektonisch
relevanten Änderungen als Einstiegskontext verwenden. Dieses Dokument ersetzt
die Detaildokumentation nicht.

## Vision und Scope

PropertyFlow ist eine KI-gestützte Webanwendung zur Triage und Bearbeitung
von Mieteranliegen für Immobilienverwaltungen.

Die Kernfunktionen sind:

1. Erfassung und Strukturierung von Mieteranliegen
2. KI-gestützte Dringlichkeitsbewertung
3. RAG-gestützte Zuständigkeits- und Handlungsempfehlung
4. Prüfung und Bearbeitung durch die Immobilienbewirtschaftung

Die endgültige fachliche Entscheidung verbleibt beim Menschen.

Details:

- `docs/vision.md`

## Architektur

Das PropertyFlow-Backend wird als **modularer Monolith** umgesetzt.

Die fachlichen Backend-Module sind:

- `intake`
- `triage`
- `recommendation`
- `caseprocessing`

Die Module werden nach fachlicher Verantwortung geschnitten. Innerhalb eines
Moduls können technische Verantwortlichkeiten wie Controller, Service,
Domain, Persistence, AI-, Retrieval- oder Workflow-Adapter getrennt werden.

Direkte Zugriffe auf Persistence- oder andere interne
Implementierungsdetails eines fremden Moduls sind nicht erlaubt.
Modulübergreifende Kommunikation erfolgt über explizite Schnittstellen und
Verträge.

Camunda 8 wird als separate Orchestrierungskomponente für langlebige
Bearbeitungsprozesse eingesetzt. PropertyFlow besitzt die fachlichen Daten;
Camunda besitzt den technischen Workflow-State.

Details:

- `docs/architecture/c4-context.md`
- `docs/architecture/c4-container.md`
- `docs/architecture/module-structure.md`
- `docs/architecture/adr/ADR-001-grundarchitektur.md`
- `docs/architecture/adr/ADR-002-camunda8-workflow-orchestration.md`

## Technologie-Stack

Der aktuelle Stack wird durch die versionierten Build- und
Infrastrukturdateien im Repository festgelegt.

Aktueller Stand:

- Java 23
- Spring Boot 4.1.1
- Spring AI 2.0.1
- Camunda 8.9.19
- PostgreSQL als relationale Zieldatenbank
- JPA / Hibernate für relationale Persistenz
- Flyway für versionierte Datenbankmigrationen
- Maven
- Docker / Docker Compose

Konkrete Dependency-Versionen werden nicht zusätzlich in diesem Dokument
gepflegt, wenn sie bereits durch `pom.xml` oder andere Build-Dateien
eindeutig festgelegt sind.

## Entwicklungs- und Strukturkonventionen

- Fachliche Module stehen über globalen technischen Schichten.
- Es werden keine globalen Root-Packages wie `controller`, `service`,
  `repository` oder `entity` eingeführt.
- Controller enthalten keine Geschäftslogik und greifen nicht direkt auf
  Repositories zu.
- Services orchestrieren Anwendungsfälle und fachliche Abläufe.
- Domain-Code soll möglichst unabhängig von Spring, JPA, Camunda und
  konkreten LLM-Providern bleiben.
- Job Worker sind Adapter zwischen Camunda und PropertyFlow und enthalten
  keine umfangreiche Fachlogik.
- Externe Systeme werden über explizite Integrationsschnittstellen gekapselt.
- Datenbankschemaänderungen werden mit Flyway versioniert.
- Bereits angewendete Flyway-Migrationen werden nicht nachträglich verändert.
- Kleine, fokussierte Änderungen und automatisierte Tests werden bevorzugt.

Details:

- `docs/architecture/module-structure.md`
- `.github/copilot-instructions.md`

## Massgebende nichtfunktionale Anforderungen

### Änderbarkeit

Externe Integrationen und fachliche Module sollen so gekapselt sein, dass
Änderungen möglichst lokal bleiben. Insbesondere darf ein Wechsel des
LLM-Providers keine Änderungen an der fachlichen Kernlogik anderer Module
erzwingen.

### Testbarkeit

Zentrale fachliche Abläufe müssen ohne reale Aufrufe externer Systeme wie
LLM-Provider, E-Mail-System oder Mieterstammdatensystem automatisiert testbar
sein.

### Zuverlässigkeit und Robustheit

Ein Mieteranliegen wird persistent gespeichert, bevor eine KI-Analyse
durchgeführt wird. Der Ausfall von KI, RAG oder einer anderen externen
Komponente darf die Erfassung und manuelle Weiterbearbeitung nicht verhindern.

### Nachvollziehbarkeit und Auditierbarkeit

Relevante KI-Empfehlungen müssen auf Modell beziehungsweise Modellversion,
Prompt-Version, verwendete Wissensquellen, Zeitpunkt und anschliessende
menschliche Entscheidung zurückgeführt werden können.

### Datenschutz und Sicherheit

Personen- und Mieterdaten dürfen nicht unkontrolliert an externe KI-Dienste
übertragen werden. Es werden nur die für den jeweiligen KI-Aufruf notwendigen
Daten übertragen; personenbezogene Daten werden soweit fachlich möglich
minimiert oder maskiert.

### Betriebliche Einfachheit

Das PropertyFlow-Backend bleibt eine deploybare Anwendung. Die fachlichen
Module benötigen untereinander keine Netzwerkkommunikation und keine
separaten Deployments.

### Verfügbarkeit

Die Erfassung von Mieteranliegen ist grundsätzlich 24/7 vorgesehen. Der
Ausfall von KI oder RAG darf die Erfassung eines Anliegens nicht verhindern.

### Benutzerfreundlichkeit

Dringlichkeit, KI-Empfehlung, Zuständigkeit und Bearbeitungsstatus müssen für
die Immobilienbewirtschaftung schnell und verständlich erkennbar sein.
Technische KI-Details stehen dabei nicht im Vordergrund.

Die vollständigen Prüfkriterien und Begründungen sind in
`docs/architecture/adr/ADR-001-grundarchitektur.md` dokumentiert.

## Sicherheits- und KI-Leitplanken

Für KI-gestützte Funktionen gelten mindestens folgende Leitplanken:

- LLM-Ausgaben werden als nicht vertrauenswürdige Eingaben behandelt und
  vor fachlicher Verwendung validiert.
- Es werden keine unnötigen Mieter-, Mietvertrags- oder Objektdaten an
  externe LLM-Provider übertragen.
- KI-Empfehlungen ersetzen keine endgültige menschliche Fachentscheidung.
- Kritische fachliche Aktionen benötigen eine explizite menschliche Prüfung
  oder Freigabe.
- Relevante KI-Entscheidungen und die verwendeten Wissensquellen müssen
  auditierbar bleiben.
- Ein Ausfall von KI oder RAG darf weder die Persistenz eines Anliegens noch
  die manuelle Bearbeitung verhindern.
- Camunda-Prozessvariablen enthalten nur die für die Orchestrierung
  notwendigen Daten; vollständige fachliche Objekte und unnötige
  personenbezogene Daten werden dort nicht dupliziert.
- Job Worker müssen Wiederholungen berücksichtigen und für relevante
  Seiteneffekte idempotent ausgelegt werden.

## Regeln für KI-gestützte Entwicklung

Bei generierten oder vorgeschlagenen Änderungen gilt:

1. Zuerst dieses Dokument und die referenzierten Detaildokumente lesen.
2. Bestehende ADRs und Modulgrenzen einhalten.
3. Keine neuen fachlichen Anforderungen erfinden.
4. Architekturentscheidungen nicht stillschweigend ändern.
5. Bei einem Konflikt zwischen einem KI-Vorschlag und einem akzeptierten ADR
   gilt der ADR.
6. Externe Integrationen nicht direkt in die Domain-Logik einbauen.
7. Generierten Code und generierte Tests fachlich und technisch prüfen.
8. Relevante Übernahmen, Korrekturen und Vetos bei KI-Vorschlägen
   nachvollziehbar dokumentieren.

## Pflege dieses Dokuments

Dieses Dokument wird über die Projektblöcke weiterentwickelt.

Für Block 1 ist dies Version **0.1**. Spätere Versionen ergänzen den
KI-Rahmen um weitere verbindliche Regeln, beispielsweise für
Präsentationsschicht, Service-Verträge, MCP/Agenten, Persistenz, Evaluation,
Deployment und Observability.

Detailinformationen sollen nicht unnötig dupliziert werden. Wo eine
versionierte Detailquelle existiert, verweist dieses Dokument auf diese
Quelle.
