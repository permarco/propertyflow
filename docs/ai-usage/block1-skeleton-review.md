# KI-Nutzung und Review des Block-1-Skeletts

## Zweck

Dieses Dokument beschreibt, wie KI beim Aufbau und bei der Weiterentwicklung
des lauffähigen PropertyFlow-Skeletts verwendet wurde und an welchen Stellen
KI-Ausgaben geprüft, korrigiert oder bewusst nicht übernommen wurden.

Ziel ist der Nachweis, dass KI als Entwicklungswerkzeug eingesetzt wurde,
während Architektur-, Qualitäts- und Technologieentscheidungen weiterhin
bewusst durch den Entwickler verantwortet wurden.

## 1. Eingesetzte Werkzeuge

Für die Entwicklung des Block-1-Skeletts wurden folgende Werkzeuge verwendet:

- Spring Initializr für die initiale Erstellung des Spring-Boot-Projekts
- GitHub Copilot für Code- und Boilerplate-Unterstützung in der IDE
- ChatGPT für Review, Architekturabgleich, Dokumentation und gezielte technische Verbesserungsvorschläge

Die architektonischen Grundentscheidungen, Qualitätsanforderungen und
Sicherheitsleitplanken wurden nicht an die KI delegiert.

## 2. Initiale Projektgrundlage

Das initiale Spring-Boot-Projekt wurde bewusst **nicht durch KI erzeugt**.

Die Projektgrundlage wurde mit dem offiziellen Spring Initializr erstellt:

`https://start.spring.io/`

Dadurch konnten insbesondere die verwendeten Spring-Boot-Abhängigkeiten,
Projektmetadaten und die technische Basis kontrolliert und reproduzierbar
festgelegt werden.

Diese Entscheidung wurde bewusst getroffen, um zu vermeiden, dass ein
KI-Werkzeug ungeprüft veraltete, inkompatible oder nicht existierende
Dependency-Versionen in das Projekt einführt.

Die Wahl des Technologie-Stacks und die initiale Projektkonfiguration waren
damit eine bewusst nicht delegierte Entwicklerentscheidung.

## 3. KI zur Generierung

Nach der Erstellung der Projektgrundlage wurde KI eingesetzt, um das
lauffähige Skelett technisch zu erweitern und Boilerplate schneller zu
erzeugen.

### 3.1 Hello-World-Skelett

KI wurde zur Unterstützung bei der Implementierung des Hello-World-Beispiels
verwendet.

Dazu gehörten unter anderem:

- REST-Controller für den Hello-World-Endpoint
- Service-Schicht
- Response-Modell
- JPA Entity für einen Demo-Counter
- Spring-Data-Repository
- Testcode
- Flyway-Migration für die benötigte Datenbanktabelle

Das Ergebnis ist ein lauffähiger Endpoint:

`GET /api/hello`

Der Endpoint liefert eine Hello-World-Antwort und erhöht gleichzeitig einen
persistierten Counter.

Das Hello-World-Beispiel dient nicht als fachliches PropertyFlow-Modul,
sondern als technischer Nachweis dafür, dass der gewählte Stack mit
Spring Boot, JPA, Flyway und REST grundsätzlich lauffähig integriert ist.

## 4. Review und Korrekturen von KI-Vorschlägen

Die generierten beziehungsweise vorgeschlagenen Änderungen wurden nicht
ungeprüft übernommen.

Mehrere Punkte wurden nach einem Review gezielt angepasst.

### 4.1 Flyway und Hibernate

In einer früheren Version war die Schema-Validierung durch Hibernate noch
deaktiviert beziehungsweise provisorisch konfiguriert.

Beim Review wurde entschieden, die Verantwortung klar zu trennen:

- Flyway ist für Erstellung und Weiterentwicklung des Datenbankschemas verantwortlich.
- Hibernate verändert das Schema nicht automatisch.
- Hibernate validiert lediglich, ob das vorhandene Schema zur JPA-Abbildung passt.

Daraus wurde folgende Konfiguration abgeleitet:

`spring.jpa.hibernate.ddl-auto=validate`

Zusätzlich wurde die PostgreSQL-Unterstützung für Flyway ergänzt.

Diese Änderung stellt sicher, dass Datenbankschema und Anwendungscode nicht
unabhängig voneinander auseinanderlaufen.

### 4.2 Testbarkeit ohne lokale Camunda-Instanz

Beim Review wurde erkannt, dass Anwendungstests unnötig von einer lokal
laufenden Camunda-Instanz abhängig sein könnten.

Deshalb wurde ein separates Testprofil ergänzt.

Im Testprofil wird die Camunda-Verbindung deaktiviert, sodass die Tests des
Spring-Boot-Skeletts unabhängig von externer Workflow-Infrastruktur
ausgeführt werden können.

Damit wird die Testbarkeit des Projekts verbessert und eine zentrale
Qualitätsanforderung aus der Architektur unterstützt.

### 4.3 Persistenztest des Hello-Counters

Zusätzlich wurde ein Integrationstest ergänzt, der prüft, ob der
Hello-Counter tatsächlich über das von Flyway erzeugte Schema persistiert
und bei mehreren Aufrufen korrekt erhöht wird.

Damit wird nicht nur der Controller isoliert getestet, sondern auch die
Integration zwischen:

- Spring Boot
- Service
- JPA
- Repository
- Flyway
- Datenbank

## 5. Bewusst nicht übernommene Vorschläge

KI-Vorschläge wurden nicht allein deshalb umgesetzt, weil sie technisch
möglich waren.

### 5.1 Keine KI-generierte Initialisierung des Spring-Boot-Projekts

Die vollständige Initialisierung des Projekts durch KI wurde bewusst nicht
verwendet.

Stattdessen wurde Spring Initializr eingesetzt, damit die technische
Projektbasis kontrolliert erstellt werden konnte.

Der Grund dafür ist insbesondere das Risiko, dass ein KI-Werkzeug
inkompatible, veraltete oder erfundene Versionskombinationen vorschlägt.

### 5.2 Keine leeren fachlichen Module

Es wurde diskutiert, die geplanten fachlichen Module bereits als leere
Package-Struktur anzulegen:

- `intake`
- `triage`
- `recommendation`
- `caseprocessing`

Dieser Vorschlag wurde bewusst nicht umgesetzt.

Die Module sind bereits in der Architektur und in
`docs/architecture/module-structure.md` definiert.

Leere Packages ohne konkrete fachliche Implementierung würden jedoch keinen
zusätzlichen fachlichen Wert erzeugen.

Die Module werden deshalb erst dann im Code angelegt, wenn die jeweilige
Funktionalität tatsächlich implementiert wird.

### 5.3 Keine nachträgliche Änderung angewendeter Flyway-Migrationen

Bestehende und bereits angewendete Flyway-Migrationen werden nicht
nachträglich verändert.

Änderungen am Datenbankschema werden stattdessen durch neue,
versionierte Migrationen umgesetzt.

Damit bleibt die Schemahistorie reproduzierbar und nachvollziehbar.

## 6. Veto-Notiz

KI-generierte Vorschläge wurden nur übernommen, wenn sie mit den bestehenden
Architekturentscheidungen, Qualitätsanforderungen und technischen
Konventionen von PropertyFlow vereinbar waren.

Insbesondere wurde die initiale Projektkonfiguration nicht an die KI
delegiert. Ebenso wurden keine leeren fachlichen Module nur zur Darstellung
einer gewünschten Struktur erzeugt.

Wo KI-Vorschläge die Wartbarkeit, Testbarkeit oder Nachvollziehbarkeit des
Systems nicht verbessert hätten, wurden sie verworfen oder angepasst.

## 7. Nicht an KI delegierte Entscheidungen

Folgende Entscheidungen wurden bewusst durch den Entwickler selbst getroffen:

- Verwendung eines modularen Monolithen
- Definition der fachlichen Module und ihrer Verantwortlichkeiten
- Einsatz von Camunda 8 für langlebige Workflow-Orchestrierung
- Trennung zwischen fachlichen Daten in PropertyFlow und technischem Workflow-State in Camunda
- Verwendung von Flyway als führendes Werkzeug für Schemaänderungen
- Sicherheits- und Datenschutzleitplanken
- Human-in-the-Loop als verpflichtende Grenze für kritische fachliche Entscheidungen
- Verwendung von Spring Initializr für die kontrollierte Erstellung der Projektbasis

KI wurde für Generierung, Review und technische Unterstützung eingesetzt,
nicht für die abschliessende Verantwortung dieser Entscheidungen.

## 8. Nachweise im Repository

Relevante Nachweise befinden sich unter anderem in:

- `src/main/java/com/pertegato/propertyflow/helloworld/`
- `src/main/resources/db/migration/`
- `src/test/java/com/pertegato/propertyflow/helloworld/`
- `src/test/resources/application-test.properties`
- `docs/architecture/adr/ADR-001-grundarchitektur.md`
- `docs/architecture/adr/ADR-002-camunda8-workflow-orchestration.md`
- `docs/architecture/module-structure.md`
- `docs/project-context.md`

Relevante Pull Requests beziehungsweise Commits:

- PR #8 – Erweiterung des technischen Skeletts mit Persistenz und Flyway
- PR #12 – technischer Review und Härtung des Block-1-Skeletts
