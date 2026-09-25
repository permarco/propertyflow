# PropertyFlow – Projekt-Kontext

**Version:** 0.1  
**Status:** Block 1 – Konzeption  
**Struktur:** kompakt und an arc42 orientiert

## Zweck

Dieses Dokument ist der verbindliche KI-Rahmen für die Entwicklung von
PropertyFlow. Es fasst die wichtigsten Architektur-, Technologie-,
Qualitäts- und Sicherheitsleitplanken zusammen und verweist für Details auf
die versionierte Projektdokumentation.

Die Struktur orientiert sich an arc42, wird für den aktuellen Projektstand
aber bewusst kompakt gehalten. Nicht benötigte arc42-Kapitel werden nicht
künstlich als leere Abschnitte angelegt.

KI-Werkzeuge sollen dieses Dokument vor repo-weiten oder architektonisch
relevanten Änderungen als Einstiegskontext verwenden. Dieses Dokument ersetzt
die Detaildokumentation nicht.

## 1. Ziele und Scope

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

## 2. Randbedingungen

### Technischer Stack

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

### Entwicklungsrandbedingungen

- Das Backend bleibt eine deploybare Anwendung.
- Architekturentscheidungen werden über ADRs dokumentiert.
- Datenbankschemaänderungen werden mit Flyway versioniert.
- Bereits angewendete Flyway-Migrationen werden nicht nachträglich verändert.
- Kleine, fokussierte Änderungen und automatisierte Tests werden bevorzugt.

## 3. Kontext und Systemgrenzen

PropertyFlow steht zwischen Mieterinnen und Mietern,
Immobilienbewirtschaftung und mehreren externen Systemen.

Wesentliche externe Systeme sind:

- E-Mail-System für eingehende Mieteranliegen
- Immobilienverwaltungs- beziehungsweise Mieterstammdatensystem
- externer LLM-Provider
- Camunda 8 für die technische Workflow-Orchestrierung

Die detaillierte System- und Containerabgrenzung ist in den C4-Diagrammen
dokumentiert:

- `docs/architecture/c4-context.md`
- `docs/architecture/c4-container.md`

Daten- und Vertrauensgrenzen werden zusätzlich in der Evaluations- und
Sicherheitsbasis konkretisiert:

- `docs/evaluation/evaluation-basis.md`

## 4. Lösungsstrategie

Das PropertyFlow-Backend wird als **modularer Monolith** umgesetzt.

Die wichtigsten Architekturentscheidungen der Lösungsstrategie sind:

- fachliche Strukturierung des Backends in klar abgegrenzte Module
- eine gemeinsame Backend-Deployment-Einheit
- Kapselung externer Systeme über explizite Integrationsschnittstellen
- Camunda 8 als separate Orchestrierungskomponente für langlebige Prozesse
- PropertyFlow als Besitzer der fachlichen Daten
- Camunda als Besitzer des technischen Workflow-States
- KI und RAG als unterstützende Komponenten, nicht als alleinige
  Entscheidungsinstanz
- SSR als primäre Rendering-Strategie mit gezielter clientseitiger
  Interaktivität für fachlich begründete Funktionen
- Thymeleaf als serverseitige Template-Engine sowie Vanilla JavaScript für
  klar abgegrenzte interaktive Komponenten

Details:

- `docs/architecture/adr/ADR-001-grundarchitektur.md`
- `docs/architecture/adr/ADR-002-camunda8-workflow-orchestration.md`
- `docs/architecture/adr/ADR-003-praesentationsschicht.md`

## 5. Bausteinsicht und Modulgrenzen

Die fachlichen Backend-Module sind:

- `intake`
- `triage`
- `recommendation`
- `caseprocessing`

Die Module werden nach fachlicher Verantwortung geschnitten. Innerhalb eines
Moduls können technische Verantwortlichkeiten wie Controller, Service,
Domain, Persistence, AI-, Retrieval- oder Workflow-Adapter getrennt werden.

Es gelten folgende Strukturregeln:

- Fachliche Module stehen über globalen technischen Schichten.
- Es werden keine globalen Root-Packages wie `controller`, `service`,
  `repository` oder `entity` eingeführt.
- Controller enthalten keine Geschäftslogik und greifen nicht direkt auf
  Repositories zu.
- Services orchestrieren Anwendungsfälle und fachliche Abläufe.
- Domain-Code soll möglichst unabhängig von Spring, JPA, Camunda und
  konkreten LLM-Providern bleiben.
- Direkte Zugriffe auf Persistence- oder andere interne
  Implementierungsdetails eines fremden Moduls sind nicht erlaubt.
- Modulübergreifende Kommunikation erfolgt über explizite Schnittstellen und
  Verträge.

Details:

- `docs/architecture/module-structure.md`

## 6. Querschnittliche Konzepte

### 6.1 KI und RAG

- LLM-Ausgaben werden als nicht vertrauenswürdige Eingaben behandelt und
  vor fachlicher Verwendung validiert.
- RAG liefert fachlichen Kontext aus freigegebenen Wissensquellen.
- Relevante KI-Entscheidungen und verwendete Wissensquellen müssen
  auditierbar bleiben.
- KI-Empfehlungen ersetzen keine endgültige menschliche Fachentscheidung.

### 6.2 Datenschutz und Sicherheit

- Es werden keine unnötigen Mieter-, Mietvertrags- oder Objektdaten an
  externe LLM-Provider übertragen.
- Personenbezogene Daten werden soweit fachlich möglich minimiert oder
  maskiert.
- Kritische fachliche Aktionen benötigen eine explizite menschliche Prüfung
  oder Freigabe.
- Sicherheits- und Vertrauensgrenzen werden in
  `docs/evaluation/evaluation-basis.md` dokumentiert.

### 6.3 Persistenz

- PropertyFlow besitzt die fachlichen Daten.
- Flyway ist für versionierte Datenbankmigrationen verantwortlich.
- Bereits angewendete Migrationen werden nicht nachträglich verändert.
- Ein Mieteranliegen wird persistent gespeichert, bevor eine KI-Analyse
  durchgeführt wird.

### 6.4 Workflow-Orchestrierung

- Camunda 8 wird für langlebige Bearbeitungsprozesse eingesetzt.
- Camunda-Prozessvariablen enthalten nur die für die Orchestrierung
  notwendigen Daten.
- Vollständige fachliche Objekte und unnötige personenbezogene Daten werden
  nicht in Camunda dupliziert.
- Job Worker sind Adapter zwischen Camunda und PropertyFlow und enthalten
  keine umfangreiche Fachlogik.
- Job Worker müssen Wiederholungen berücksichtigen und für relevante
  Seiteneffekte idempotent ausgelegt werden.

### 6.5 Präsentationsschicht

- Server-Side Rendering (SSR) ist die primäre Rendering-Strategie.
- Thymeleaf wird als serverseitige Template-Engine für die HTML-Erzeugung
  eingesetzt.
- Formulare, Listen, Detailansichten und normale Bearbeitungsabläufe werden
  serverseitig gerendert.
- Eine vollständige CSR-/SPA-Anwendung wird nicht eingesetzt.
- Vanilla JavaScript wird nur als klar abgegrenzte interaktive Komponente
  eingesetzt, wenn ein konkreter fachlicher oder qualitativer Nutzen besteht.
- Die KI-Analyse muss ihren aktuellen Status anzeigen, schrittweise Ausgaben
  darstellen, einen wirksamen Abbruch ermöglichen und definierte Fehlerzustände
  unterstützen.
- Ein Abbruch der KI-Analyse muss mindestens die clientseitige Verbindung und
  Darstellung beenden. Das Backend soll den Abbruch erkennen und die laufende
  Verarbeitung kontrolliert beenden oder weitere Ausgabe verwerfen.
- Die Weiterleitung des Abbruchs an den externen LLM-Provider wird genutzt,
  sofern der eingesetzte Provider und Client dies unterstützen.
- Die Zustandsübergänge der Analyse müssen mindestens `waiting`,
  `streaming`, `completed`, `aborted` und `error` unterscheiden.
- Benutzer- und KI-generierte Inhalte werden standardmässig sicher als Text
  ausgegeben und nicht ungeprüft als HTML interpretiert.
- Interaktive Funktionen müssen per Tastatur bedienbar sein.

Details:

- `docs/architecture/adr/ADR-003-praesentationsschicht.md`

### 6.6 Testbarkeit und Fehlerverhalten

- Zentrale fachliche Abläufe müssen ohne reale externe Systeme automatisiert
  testbar sein.
- Externe Abhängigkeiten sollen durch Testimplementierungen oder Mocks
  ersetzbar sein.
- Ein Ausfall von KI oder RAG darf weder die Persistenz eines Anliegens noch
  die manuelle Bearbeitung verhindern.

## 7. Qualitätsanforderungen

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

## 8. Architekturentscheidungen

Akzeptierte Architekturentscheidungen sind:

- `ADR-001-grundarchitektur.md`: modularer Monolith als Grundarchitektur
- `ADR-002-camunda8-workflow-orchestration.md`: Camunda 8 für langlebige
  Workflow-Orchestrierung
- `ADR-003-praesentationsschicht.md`: SSR mit gezielter clientseitiger
  Interaktivität

Bei einem Konflikt zwischen einem KI-Vorschlag und einem akzeptierten ADR
gilt der ADR.

Details:

- `docs/architecture/adr/`

## 9. Regeln für KI-gestützte Entwicklung

Bei generierten oder vorgeschlagenen Änderungen gilt:

1. Zuerst dieses Dokument und die referenzierten Detaildokumente lesen.
2. Bestehende ADRs und Modulgrenzen einhalten.
3. Keine neuen fachlichen Anforderungen erfinden.
4. Architekturentscheidungen nicht stillschweigend ändern.
5. Externe Integrationen nicht direkt in die Domain-Logik einbauen.
6. Generierten Code und generierte Tests fachlich und technisch prüfen.
7. Relevante Übernahmen, Korrekturen und Vetos bei KI-Vorschlägen
   nachvollziehbar dokumentieren.

Repository-weite KI-Anweisungen:

- `.github/copilot-instructions.md`

## 10. Evaluation und Pflege

Die initiale Evaluations- und Sicherheitsbasis für Block 1 ist unter
`docs/evaluation/evaluation-basis.md` versioniert.

Sie definiert repräsentative Fälle, erwartete Eigenschaften, Guardrails und
Human-in-the-Loop-Grenzen für spätere KI- und Implementierungsprüfungen.

Dieses Dokument wird über die Projektblöcke weiterentwickelt.

Für Block 1 ist dies Version **0.1**. Spätere Versionen ergänzen den
KI-Rahmen um weitere verbindliche Regeln, beispielsweise für
Präsentationsschicht, Service-Verträge, MCP/Agenten, Persistenz, Evaluation,
Deployment und Observability.

Detailinformationen sollen nicht unnötig dupliziert werden. Wo eine
versionierte Detailquelle existiert, verweist dieses Dokument auf diese
Quelle.
