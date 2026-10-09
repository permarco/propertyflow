# ADR-002: Camunda 8 für die Orchestrierung langlebiger Prozesse

## Status

Accepted

## Kontext

Die Bearbeitung eines Mieteranliegens in PropertyFlow kann sich über einen
längeren Zeitraum erstrecken und mehrere automatisierte sowie manuelle
Bearbeitungsschritte enthalten.

Ein typischer Ablauf kann beispielsweise folgende Schritte umfassen:

1. Anliegen erfassen und persistent speichern
2. Mieter und Mietobjekt zuordnen
3. KI-gestützte Strukturierung und Dringlichkeitsbewertung durchführen
4. relevante Informationen über RAG ermitteln
5. Handlungsempfehlung erstellen
6. Empfehlung durch die Immobilienbewirtschaftung prüfen lassen
7. auf weitere Informationen oder eine externe Bearbeitung warten
8. Fall fortsetzen oder abschliessen

Ein Prozess kann dabei Minuten, Stunden oder mehrere Tage dauern.
Insbesondere menschliche Freigaben, Rückfragen, externe Abklärungen,
Timer sowie vorübergehend nicht verfügbare KI- oder RAG-Komponenten
führen zu Wartezuständen.

Der Zustand eines solchen Prozesses darf nicht nur im Arbeitsspeicher
der PropertyFlow-Anwendung gehalten werden. Ein Neustart oder temporärer
Ausfall einer Komponente darf nicht dazu führen, dass der
Bearbeitungsprozess verloren geht.

PropertyFlow verwendet gemäss ADR-001 als Backend einen modularen Monolithen.
Für die Orchestrierung der langlebigen Prozesse wird eine geeignete
Workflow-Lösung benötigt.

Zusätzlich soll PropertyFlow einen aktuellen Spring-Boot- und
Spring-AI-Stack verwenden, damit moderne KI-Integrationen und die im CAS
behandelten Technologien eingesetzt und evaluiert werden können.


## Relevante Qualitätsanforderungen

### NFR-03 – Zuverlässigkeit / Robustheit

Ein Mieteranliegen darf nicht verloren gehen oder unbearbeitbar werden,
wenn eine KI-, RAG- oder andere externe Komponente vorübergehend
nicht verfügbar ist.

**Bedeutung für die Workflow-Architektur:**

Der Prozesszustand muss unabhängig von einzelnen Worker-Ausführungen
persistiert werden können. Fehler müssen wiederholt oder kontrolliert
zur manuellen Bearbeitung überführt werden können.


### NFR-04 – Nachvollziehbarkeit / Auditierbarkeit

Der Bearbeitungsablauf sowie menschliche und KI-gestützte Entscheidungen
müssen später nachvollzogen werden können.

**Bedeutung für die Workflow-Architektur:**

Der aktuelle Prozesszustand und die ausgeführten Prozessschritte müssen
rekonstruierbar sein.


### NFR-06 – Betriebliche Einfachheit

Zusätzliche Infrastruktur soll nur eingeführt werden, wenn sie einen
klaren fachlichen oder qualitativen Nutzen bietet.

**Bedeutung für die Workflow-Architektur:**

Eine separate Workflow-Komponente erhöht den Betriebsaufwand.
Dieser zusätzliche Aufwand muss durch die zuverlässige Persistenz und
Orchestrierung langlebiger Prozesse gerechtfertigt sein.


### Human-in-the-Loop

KI-Empfehlungen dürfen bestimmte fachliche Entscheidungen nicht autonom
abschliessen.

**Bedeutung für die Workflow-Architektur:**

Ein Prozess muss auf eine menschliche Prüfung oder Freigabe warten und
anschliessend zuverlässig fortgesetzt werden können.


## Technische Rahmenbedingungen

PropertyFlow soll einen aktuellen Spring-Boot- und Spring-AI-Stack
verwenden.

Die Workflow-Lösung soll diese technologische Ausrichtung nicht unnötig
auf eine ältere Spring-Generation begrenzen.

Die Workflow-Orchestrierung soll ausserdem:

- BPMN-basierte Prozesse unterstützen,
- langlebige Prozessinstanzen persistieren,
- Timer und Wartezustände ermöglichen,
- Human Tasks unterstützen,
- automatisierte Prozessschritte an PropertyFlow delegieren können,
- Fehler und Wiederholungen kontrolliert behandeln können.


## Betrachtete Alternativen

### Alternative 1 – Prozesssteuerung direkt im PropertyFlow-Code

Der Bearbeitungsprozess wird vollständig durch Anwendungscode,
Statusfelder und bedingte Logik innerhalb des modularen Monolithen umgesetzt.

Beispielsweise:

```text
RECEIVED
   ↓
ANALYSING
   ↓
WAITING_FOR_REVIEW
   ↓
PROCESSING
   ↓
CLOSED
```

Die Anwendung entscheidet anhand des aktuellen Status, welcher
Bearbeitungsschritt als Nächstes durchgeführt werden muss.

#### Vorteile

- keine zusätzliche Workflow-Infrastruktur
- geringer initialer technischer Aufwand
- vollständige Kontrolle innerhalb der PropertyFlow-Anwendung
- keine zusätzliche externe Laufzeitkomponente

#### Nachteile

- langlebige Prozesse müssen selbst implementiert werden
- Timer, Wiederholungen und Wartezustände benötigen eigene Logik
- Human Tasks und Prozessfortsetzung müssen selbst verwaltet werden
- zunehmende Gefahr komplexer Zustands- und Verzweigungslogik
- Prozessabläufe sind weniger unmittelbar visualisierbar
- Änderungen am Prozess führen stärker zu Änderungen im Anwendungscode

#### Bewertung

Diese Variante wird nicht gewählt.

PropertyFlow besitzt explizit langlebige, unterbrechbare und
nachvollziehbare Bearbeitungsprozesse. Die dafür benötigten Mechanismen
sollen nicht vollständig als eigene Workflow-Infrastruktur implementiert
werden.


### Alternative 2 – Camunda 7 als eingebettete Process Engine

Camunda 7 wird direkt in die Spring-Boot-Anwendung eingebettet.

```text
PropertyFlow Backend
┌───────────────────────────────┐
│ Spring Boot                   │
│                               │
│ Fachmodule                    │
│                               │
│ Embedded Camunda 7 Engine     │
└───────────────────────────────┘
```

#### Vorteile

- vorhandene Erfahrung mit Camunda 7
- bekannte BPMN- und Java-Konzepte
- bekannte APIs
- JavaDelegate-Modell ist vertraut
- Engine und Fachanwendung laufen im selben Prozess
- geringe zusätzliche Laufzeit-Infrastruktur
- einfache lokale Integration

#### Nachteile

- stärkere Kopplung zwischen Process Engine und PropertyFlow-Anwendung
- die eingebettete Engine beeinflusst die Wahl kompatibler Spring-Versionen
- Camunda 7 passt weniger gut zur gewünschten aktuellen
  Spring-Boot-/Spring-AI-Technologiebasis
- PropertyFlow würde stärker an die ältere Camunda-Generation gekoppelt
- zukünftige technologische Weiterentwicklung wäre eingeschränkt

#### Bewertung

Camunda 7 war aufgrund der vorhandenen Erfahrung zunächst die bevorzugte
Variante.

Nach Prüfung der gewünschten aktuellen Spring-Boot- und
Spring-AI-Technologiebasis wurde die Variante jedoch verworfen.

Die vorhandene Erfahrung mit Camunda 7 reduziert zwar den
Entwicklungsaufwand, rechtfertigt aber nicht die Einschränkung der
übrigen Technologiebasis des Projekts.


### Alternative 3 – Camunda 8 als separate Workflow-Orchestrierung

Camunda 8 führt die BPMN-Prozesse als separate Orchestrierungskomponente aus.

PropertyFlow integriert sich über die unterstützten Client-Schnittstellen
und Job Worker.

```text
PropertyFlow Backend
        │
        │ Prozesse starten
        │ Jobs verarbeiten
        │
        ▼
    Camunda 8
```

Die PropertyFlow-Anwendung enthält weiterhin die Fachlogik.
Camunda steuert, wann welcher Bearbeitungsschritt ausgeführt werden soll.

#### Vorteile

- langlebige Prozesse werden unabhängig von einzelnen HTTP-Requests
  persistiert
- persistente Wartezustände werden unterstützt
- Timer können im Prozess modelliert werden
- Human Tasks können explizit im Prozess dargestellt werden
- BPMN macht Prozessabläufe sichtbar und nachvollziehbar
- Fehler und Wiederholungen können auf Workflow-Ebene behandelt werden
- Prozess-Orchestrierung bleibt von der Implementierung der Fachlogik
  getrennt
- kompatibel mit der aktuellen Spring-/AI-Technologiebasis
- gute Grundlage für langlebige und teilweise automatisierte Prozesse

#### Nachteile

- zusätzliche Laufzeitkomponente
- höhere Infrastruktur- und Betriebskomplexität
- PropertyFlow und Camunda besitzen getrennte persistente Zustände
- keine gemeinsame lokale Datenbanktransaktion zwischen Camunda und
  PropertyFlow
- Job Worker müssen mögliche Wiederholungen berücksichtigen
- Idempotenz muss bewusst umgesetzt werden
- Umstellung gegenüber bekannten Camunda-7-APIs und Konzepten
- zusätzliche Lernkurve


## Bewertung

| Kriterium | Eigene Prozesssteuerung | Camunda 7 Embedded | Camunda 8 |
|---|---|---|---|
| Langlebige Prozesse | mittel | hoch | **hoch** |
| Persistente Wartezustände | selbst zu entwickeln | hoch | **hoch** |
| Human-in-the-Loop | selbst zu entwickeln | hoch | **hoch** |
| BPMN-basierte Prozessdarstellung | niedrig | hoch | **hoch** |
| Robustheit bei Worker-Ausfällen | selbst zu entwickeln | mittel bis hoch | **hoch** |
| Betrieblicher Aufwand | **niedrig** | niedrig | mittel |
| Kompatibilität mit aktuellem Spring-/AI-Stack | hoch | niedrig bis mittel | **hoch** |
| Vorhandene Erfahrung | mittel | **hoch** | mittel |
| Zukunftsfähigkeit für PropertyFlow | mittel | mittel | **hoch** |


## Entscheidung

PropertyFlow verwendet **Camunda 8** zur Orchestrierung langlebiger
Bearbeitungsprozesse.

Camunda 8 wird nicht in die PropertyFlow-Anwendung eingebettet.

Das PropertyFlow-Backend bleibt gemäss ADR-001 ein modularer Monolith und
integriert sich über die von Camunda unterstützten Client-Schnittstellen
und Job Worker mit der Workflow Engine.

Camunda übernimmt insbesondere:

- Orchestrierung des Bearbeitungsprozesses
- Persistenz des technischen Workflow-State
- Wartezustände
- Timer
- Wiederholungen
- technische Fehlerzustände
- menschliche Aufgaben und Freigabeschritte

Die fachliche Geschäftslogik verbleibt in PropertyFlow.


## Verantwortungsgrenzen

### PropertyFlow besitzt die fachlichen Daten

PropertyFlow ist die führende Quelle für fachliche Informationen wie:

- Mieteranliegen
- Mieter- und Mietobjekt-Zuordnung
- Dringlichkeitsbewertung
- KI-Ergebnisse
- Handlungsempfehlungen
- fachlicher Bearbeitungsstatus
- Bearbeitungsverlauf
- Auditinformationen

Diese Daten werden in der PropertyFlow-Datenhaltung gespeichert.


### Camunda besitzt den Workflow-State

Camunda hält den für die Prozesssteuerung erforderlichen technischen
Workflow-State.

Dazu gehören beispielsweise:

- aktuelle Position der Prozessinstanz
- aktive Aufgaben
- wartende Jobs
- Timer
- Prozessvariablen
- technische Fehlerzustände
- Retry-Zustände

Fachliche Daten sollen nicht unnötig zwischen PropertyFlow und Camunda
dupliziert werden.

Komplette Mieter-, Mietvertrags- oder Fallobjekte werden deshalb nicht
als Prozessvariablen gespeichert.

Bevorzugt werden Referenzen und die für die Prozesssteuerung notwendigen
Informationen.

Beispiel:

```json
{
  "tenantRequestId": "REQ-4711",
  "priority": "HIGH",
  "processingState": "WAITING_FOR_REVIEW"
}
```


## Integrationsprinzipien

### Job Worker

Automatisierte Prozessschritte werden durch PropertyFlow als
Camunda Job Worker implementiert.

Mögliche Worker sind beispielsweise:

```text
classify-tenant-request
evaluate-urgency
retrieve-knowledge
create-recommendation
```

Die Worker rufen die entsprechende Fachlogik innerhalb des
PropertyFlow-Backends auf.


### Trennung von Workflow und Fachlogik

Camunda steuert den Ablauf.

PropertyFlow implementiert die Fachlogik.

Ein BPMN-Service-Task soll deshalb keine komplexe Fachlogik direkt
enthalten, sondern einen entsprechenden PropertyFlow-Worker aufrufen.

```text
Camunda
   │
   │ Job
   ▼
PropertyFlow Worker
   │
   ▼
fachliches Modul
```


### Idempotenz

Job Worker müssen so implementiert werden, dass eine erneute Ausführung
nicht zu unerwünschten Mehrfachwirkungen führt.

Insbesondere Aktionen gegenüber externen Systemen müssen eine mögliche
Mehrfachausführung berücksichtigen.

Beispielsweise darf ein wiederholter Worker-Aufruf nicht mehrfach denselben
externen Auftrag erzeugen.


### Fehlerbehandlung

Der Ausfall einer KI-, Retrieval- oder anderen externen Komponente darf
nicht dazu führen, dass das Mieteranliegen verloren geht.

Ein fehlgeschlagener automatisierter Schritt kann beispielsweise:

- erneut versucht werden,
- in einen kontrollierten Fehlerzustand wechseln,
- zur manuellen Bearbeitung eskalieren.

Die fachlichen Daten des Mieteranliegens bleiben dabei in PropertyFlow
erhalten.


### Human-in-the-Loop

Kritische fachliche Aktionen dürfen nicht ausschliesslich aufgrund einer
KI-Ausgabe ausgeführt werden.

Der BPMN-Prozess enthält deshalb explizite menschliche Prüf- und
Freigabeschritte.

Beispiel:

```text
Mieteranliegen
      ↓
KI-Triage
      ↓
RAG / Empfehlung
      ↓
┌────────────────────────────┐
│ Menschliche Prüfung        │
│ Immobilienbewirtschaftung  │
└─────────────┬──────────────┘
              ↓
       weitere Bearbeitung
```


## Konsequenzen

### Positive Konsequenzen

- langlebige Prozesse werden explizit modelliert
- Wartezeiten von Stunden oder Tagen können zuverlässig abgebildet werden
- Prozesszustände bleiben unabhängig von einzelnen Backend-Requests erhalten
- Human-in-the-Loop wird als Bestandteil des Prozesses sichtbar
- Timer und Wiederholungsmechanismen müssen nicht vollständig selbst
  entwickelt werden
- BPMN-Prozesse können fachlich diskutiert und visualisiert werden
- KI-Schritte werden in einen kontrollierten Geschäftsprozess eingebettet
- PropertyFlow kann einen aktuellen Spring-Boot- und Spring-AI-Stack
  verwenden
- Prozesslogik und Fachlogik bleiben voneinander getrennt


### Negative Konsequenzen

- Camunda 8 muss zusätzlich betrieben werden
- die lokale Entwicklungsumgebung wird komplexer
- Camunda 8 muss zusätzlich überwacht und abgesichert werden
- Entwickler müssen das Job-Worker- und Client-Modell von Camunda 8 kennen
- gegenüber Camunda 7 müssen neue APIs und Konzepte erlernt werden
- zwischen PropertyFlow und Camunda existiert keine gemeinsame lokale
  Datenbanktransaktion
- Idempotenz und verteilte Fehlerfälle müssen bewusst behandelt werden


## Auswirkungen auf die Architektur

Camunda 8 wird im C4-Level-2-Diagramm als separate
Orchestrierungskomponente dargestellt.

### PropertyFlow-Backend

Das PropertyFlow-Backend bleibt für die fachliche Verarbeitung
verantwortlich und enthält:

- die fachlichen Module und deren Geschäftslogik
- den Camunda Client zur Kommunikation mit Camunda 8
- Job Worker zur Ausführung automatisierter BPMN-Service-Tasks
- die Integration der User Tasks in die PropertyFlow-Anwendung

### Camunda 8

Camunda 8 ist für die technische Prozess-Orchestrierung verantwortlich
und verwaltet insbesondere:

- BPMN-Prozessinstanzen
- den technischen Workflow-State
- Jobs für automatisierte Prozessschritte
- Timer und Wartezustände
- den Zustand von User Tasks
- technische Retry- und Fehlerzustände

Die fachliche Geschäftslogik und die fachlichen Daten verbleiben in
PropertyFlow.

Die Immobilienbewirtschaftung interagiert nicht direkt mit Camunda,
sondern weiterhin über das PropertyFlow-Frontend.

Der modulare Monolith aus ADR-001 bleibt bestehen.

Camunda 8 ist nicht Bestandteil des modularen Monolithen selbst.


## Kriterien für eine Neubewertung

Die Entscheidung wird überprüft, wenn mindestens eine der folgenden
Bedingungen eintritt:

- die Bearbeitungsprozesse werden so einfach, dass eine Workflow Engine
  keinen ausreichenden Mehrwert mehr bietet
- der Betriebsaufwand von Camunda 8 wird für PropertyFlow unverhältnismässig
  hoch
- eine andere Workflow-Technologie bietet wesentliche Vorteile für die
  Anforderungen von PropertyFlow
- Lizenz-, Hosting- oder Betriebsbedingungen verändern sich wesentlich
- die Integration mit dem gewählten Spring-/AI-Stack ist langfristig nicht
  mehr sinnvoll möglich


## Nicht Bestandteil dieses ADR

Dieses ADR entscheidet über den Einsatz von Camunda 8 als
Workflow-Orchestrierung.

Folgende Entscheidungen werden separat getroffen:

- konkrete Betriebsform von Camunda 8
- lokale Entwicklungsumgebung für Camunda 8
- Authentifizierung und Autorisierung gegenüber Camunda
- konkrete BPMN-Prozessmodelle
- konkrete Retry- und Incident-Strategie
- detaillierte Job-Worker-Struktur
- Transactional-Outbox-Pattern, falls eine zuverlässige Kopplung von
  PropertyFlow-Datenänderungen und externen Ereignissen erforderlich wird
