# Modulstruktur des PropertyFlow-Backends

## Zweck

Dieses Dokument beschreibt die interne Struktur des PropertyFlow-Backends.

Die Entscheidung für einen modularen Monolithen ist in
`docs/architecture/adr/ADR-001-grundarchitektur.md` dokumentiert.

Dieses Dokument konkretisiert diese Entscheidung und definiert:

- die fachlichen Module des Backends
- die interne Struktur eines Moduls
- die erlaubten Abhängigkeiten
- die Kommunikation zwischen Modulen
- den Umgang mit Persistenz, externen Systemen und Camunda 8

Ziel ist eine klare Trennung der fachlichen Verantwortlichkeiten und eine
Struktur, die Änderungen und Tests einzelner Module erleichtert.


## Grundprinzip

Das Backend wird primär nach fachlichen Verantwortlichkeiten strukturiert.

Nicht vorgesehen ist eine globale technische Schichtenstruktur wie:

```text
propertyflow/
├── config/
├── controller/
├── service/
├── repository/
└── entity/
```

Stattdessen werden technische Verantwortlichkeiten innerhalb der
fachlichen Module gekapselt:

```text
propertyflow/
├── intake/
├── triage/
├── recommendation/
└── caseprocessing/
```

Innerhalb eines Moduls kann anschliessend nach technischen
Verantwortlichkeiten strukturiert werden.


## Fachliche Module

### Intake

Das Modul `intake` ist für die Erfassung und initiale Verwaltung von
Mieteranliegen verantwortlich.

Typische Verantwortlichkeiten:

- Entgegennahme neuer Mieteranliegen
- Validierung der Eingangsdaten
- initiale Zuordnung von Mieter und Mietobjekt
- persistente Speicherung des Mieteranliegens
- Bereitstellung des Anliegens für die weitere Verarbeitung


### Triage

Das Modul `triage` ist für die Analyse und erste fachliche Einordnung eines
Mieteranliegens verantwortlich.

Typische Verantwortlichkeiten:

- Strukturierung von Freitext
- Ermittlung relevanter Merkmale
- Dringlichkeitsbewertung
- Validierung der KI-Ausgabe
- Bereitstellung eines Triage-Ergebnisses


### Recommendation

Das Modul `recommendation` erstellt eine fachlich begründete
Zuständigkeits- und Handlungsempfehlung.

Typische Verantwortlichkeiten:

- Retrieval relevanter Wissensinhalte
- Verwendung von Richtlinien, Regelwerken und geeigneten früheren Fällen
- Erstellung einer Handlungsempfehlung
- Nachweis der für die Empfehlung verwendeten Quellen
- Validierung der KI-Ausgabe


### Case Processing

Das Modul `caseprocessing` unterstützt die weitere Bearbeitung eines Falls
durch die Immobilienbewirtschaftung.

Typische Verantwortlichkeiten:

- Darstellung des fachlichen Bearbeitungszustands
- menschliche Prüfung von KI-Empfehlungen
- Annahme, Korrektur oder Ablehnung einer Empfehlung
- fachliche Weiterbearbeitung eines Mieteranliegens
- Integration der langlebigen Bearbeitung mit Camunda 8


## Interne Struktur eines Moduls

Ein Modul kann abhängig von seinen Anforderungen beispielsweise folgende
interne Struktur besitzen:

```text
intake/
├── controller/
├── service/
├── domain/
├── persistence/
└── config/
```

Nicht jedes Modul muss alle diese Packages besitzen.

Packages werden nur eingeführt, wenn im jeweiligen Modul eine entsprechende
Verantwortung vorhanden ist.


## Controller

Controller bilden eine Schnittstelle zwischen dem PropertyFlow-Backend und
externen Aufrufern wie dem Web-Frontend.

Sie sind insbesondere verantwortlich für:

- Entgegennahme von HTTP-Requests
- technische Eingabevalidierung
- Umwandlung von Request-Daten
- Aufruf eines geeigneten Application Service
- Erzeugung der HTTP-Response

Controller enthalten keine eigentliche Geschäftslogik und greifen nicht
direkt auf Datenbank-Repositories zu.

Beispiel:

```text
HTTP Request
     ↓
Controller
     ↓
Service
```


## Service

Services implementieren beziehungsweise orchestrieren die fachlichen
Anwendungsfälle eines Moduls.

Sie koordinieren beispielsweise:

- Domain-Objekte
- Persistenzzugriffe
- fachliche Prüfungen
- Aufrufe externer Schnittstellen
- Übergänge zu anderen fachlichen Verarbeitungsschritten

Ein Service beschreibt damit primär, **was im Rahmen eines Anwendungsfalls
geschehen muss**.

Technische Details eines externen Systems sollen nicht direkt in der
Service-Logik implementiert werden.


## Domain

Das Domain-Package enthält das fachliche Modell eines Moduls.

Dazu können gehören:

- fachliche Objekte
- Value Objects
- fachliche Zustände
- fachliche Regeln
- fachliche Enums

Beispiele:

```text
TenantRequest
Urgency
TriageResult
Recommendation
CaseDecision
```

Domain-Code soll möglichst unabhängig von technischen Frameworks wie
Spring, JPA, Camunda oder konkreten LLM-Providern bleiben.


## Persistence

Das Persistence-Package kapselt die technische Datenhaltung eines Moduls.

Dazu können gehören:

- JPA Entities
- Spring-Data-Repositories
- Mapper
- Persistence Adapter

Beispiel:

```text
Service / Domain
       ↓
Persistence Adapter
       ↓
JPA Repository
       ↓
PostgreSQL
```

Ein Modul darf nicht direkt auf die Persistence-Implementierung eines
anderen Moduls zugreifen.


## Externe Integrationen

Technische Integrationen zu externen Systemen werden innerhalb des
zuständigen Moduls gekapselt.

Dazu gehören beispielsweise:

- LLM-Provider
- E-Mail-System
- Mieterstammdatensystem
- Vector Store
- Camunda 8

Die Fachlogik soll nicht direkt von einer konkreten Provider-Bibliothek
abhängen.

Stattdessen werden geeignete Schnittstellen beziehungsweise Adapter
verwendet.

Beispiel:

```text
TriageService
     ↓
AI Interface
     ↓
Spring-AI-Adapter
     ↓
LLM Provider
```

Dadurch kann ein externer Provider ausgetauscht werden, ohne die
Fachlogik des Moduls wesentlich zu verändern.


## Camunda-Integration

Camunda 8 ist für die technische Orchestrierung langlebiger Prozesse
verantwortlich.

Job Worker befinden sich auf der Integrationsgrenze zwischen Camunda und
der PropertyFlow-Fachlogik.

Ein Job Worker:

- nimmt einen von Camunda bereitgestellten Job entgegen
- liest die notwendigen Prozessvariablen
- ruft den zuständigen PropertyFlow-Service auf
- meldet das Ergebnis an Camunda zurück

Der Job Worker selbst soll keine umfangreiche Fachlogik enthalten.

Beispiel:

```text
Camunda 8
    ↓
Job Worker
    ↓
TriageService
    ↓
fachliche Verarbeitung
```

Die genaue Entscheidung zur Workflow-Orchestrierung ist in
`docs/architecture/adr/ADR-002-camunda8-workflow-orchestration.md`
dokumentiert.


## Abhängigkeitsregeln

Für die Module gelten folgende Regeln:

1. Ein Modul darf seine eigene interne Struktur frei verwenden.

2. Controller greifen auf Services des eigenen Moduls zu, nicht direkt auf
   Repositories.

3. Fachlogik gehört nicht in Controller, Job Worker oder technische Adapter.

4. Ein Modul darf nicht direkt auf JPA-Repositories, Entities oder andere
   interne Implementierungsdetails eines anderen Moduls zugreifen.

5. Kommunikation zwischen Modulen erfolgt über explizite öffentliche
   Schnittstellen beziehungsweise klar definierte Verträge.

6. Externe Systeme werden hinter eigenen Integrationsschnittstellen
   gekapselt.

7. Domain-Code soll möglichst unabhängig von Spring, JPA, Camunda und
   konkreten KI-Providern bleiben.

8. Camunda Job Worker sind Adapter zur Prozess-Orchestrierung und keine
   Träger der eigentlichen Geschäftslogik.


## Beispielhafte Paketstruktur

Eine mögliche Struktur des Backends ist:

```text
propertyflow/
├── config/
├── intake/
│   ├── controller/
│   │   └── dto/
│   ├── service/
│   ├── domain/
│   ├── config/
│   └── persistence/
│
├── triage/
│   ├── service/
│   ├── domain/
│   └── ai/
│
├── recommendation/
│   ├── service/
│   ├── domain/
│   ├── retrieval/
│   └── ai/
│
├── caseprocessing/
│   ├── controller/
│   ├── service/
│   ├── domain/
│   └── workflow/
│
└── PropertyFlowApplication.java
```

Diese Struktur ist beispielhaft.

Ein Package wird nur dann eingeführt, wenn die entsprechende technische
Verantwortung tatsächlich benötigt wird.


## Konfiguration

Konfiguration wird möglichst nahe bei der Verantwortung abgelegt, zu der sie
gehört.

Modulspezifische Java-Konfiguration liegt innerhalb des entsprechenden Moduls,
zum Beispiel:

```text
triage/config/
recommendation/config/
caseprocessing/config/
```

Querschnittliche technische Konfiguration, die die gesamte Anwendung betrifft,
kann zentral unter folgendem Package abgelegt werden:

```text
propertyflow/config/
```

Laufzeitkonfiguration wie Datenbankverbindungen, Camunda-Endpunkte oder
umgebungsspezifische Properties liegt unter:

```text
src/main/resources/
```

Konfiguration darf nicht verwendet werden, um fachliche Modulgrenzen zu
umgehen.

## Abhängigkeitsrichtung

Die gewünschte Abhängigkeitsrichtung innerhalb eines Moduls lässt sich
vereinfacht wie folgt darstellen:

```text
Controller
    ↓
Service
    ↓
Domain
    ↑
    │
Persistence / AI / externe Adapter
```

Technische Implementierungen befinden sich möglichst an den Rändern des
Moduls.

Die fachliche Logik soll dadurch unabhängig von konkreten technischen
Integrationen bleiben.

## Datenbankmigrationen

Änderungen am relationalen Datenbankschema werden mit Flyway versioniert.

Die Migrationen befinden sich zentral unter:

```text
src/main/resources/db/migration/
```

Jede Schemaänderung wird durch eine neue versionierte Migration beschrieben.
Bereits angewendete Migrationen werden nicht nachträglich verändert.

Flyway ist für die versionierte Weiterentwicklung des relationalen
Datenbankschemas vorgesehen. Hibernate/JPA soll das Schema nicht automatisch
mit `create` oder `update` verändern.

Die Flyway-Migrationen werden zentral verwaltet; die
Persistence-Implementierungen verbleiben weiterhin innerhalb der jeweiligen
fachlichen Module.

## Zielbild

Die Modulstruktur soll sicherstellen, dass PropertyFlow auch als einzelnes
deploybares Backend klare interne Grenzen besitzt.

Dadurch sollen insbesondere folgende Qualitätsanforderungen unterstützt
werden:

- Änderbarkeit
- Testbarkeit
- Zuverlässigkeit
- Nachvollziehbarkeit
- Austauschbarkeit externer Integrationen

Die Modulgrenzen sollen deshalb auch innerhalb des modularen Monolithen
bewusst eingehalten und nicht durch direkte Zugriffe auf interne
Implementierungsdetails umgangen werden.
