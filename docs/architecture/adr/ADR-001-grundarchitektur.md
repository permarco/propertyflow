# ADR-001: Modularer Monolith als Grundarchitektur des Backends

## Status

Accepted

## Kontext

PropertyFlow ist eine KI-gestützte Webanwendung zur Triage und Bearbeitung
von Mieteranliegen für Immobilienverwaltungen.

Die Anwendung umfasst die fachlichen Bereiche:

- Erfassung und Strukturierung von Mieteranliegen
- Dringlichkeitsbewertung
- Zuständigkeits- und HandlungsempfehlungF
- Prüfung und Bearbeitung durch die Immobilienbewirtschaftung

PropertyFlow integriert zudem mehrere externe Systeme:

- E-Mail-System
- Immobilienverwaltungs- bzw. Mieterstammdatensystem
- LLM-/AI-Provider
- Wissensbasis für semantisches Retrieval

Die Bearbeitung eines Mieteranliegens kann sich über einen längeren Zeitraum
erstrecken und Warte- oder Freigabeschritte enthalten.
Für langlebige Bearbeitungsprozesse ist eine externe Workflow-Orchestrierung vorgesehen.
Die konkrete Entscheidung für Camunda 8 wird in einem separaten
Architecture Decision Record dokumentiert.

Für das Backend wird eine Grundarchitektur benötigt, welche klare fachliche
Verantwortlichkeiten ermöglicht und gleichzeitig Robustheit, Testbarkeit,
Nachvollziehbarkeit und Datenschutz unterstützt, ohne für den Projektumfang
unnötige betriebliche Komplexität einzuführen.


## Qualitätsanforderungen

### NFR-01 – Änderbarkeit

Die fachlichen Bereiche und externen Integrationen von PropertyFlow müssen
so gekapselt sein, dass Änderungen möglichst lokal vorgenommen werden können.

**Prüfkriterium:** Der Austausch eines externen Systems, beispielsweise des
LLM-Providers, darf keine Änderungen an der fachlichen Kernlogik anderer
Module erfordern.


### NFR-02 – Testbarkeit

Die fachliche Logik muss unabhängig von externen Systemen automatisiert
testbar sein.

**Prüfkriterium:** Die zentralen fachlichen Abläufe können ohne reale Aufrufe
des LLM-Providers, des E-Mail-Systems oder des Mieterstammdatensystems
automatisiert getestet werden. Externe Abhängigkeiten sind durch
Testimplementierungen oder Mocks ersetzbar.


### NFR-03 – Zuverlässigkeit / Robustheit

Ein Mieteranliegen darf nicht verloren gehen oder unbearbeitbar werden,
nur weil eine KI-, RAG- oder andere externe Komponente nicht verfügbar ist.

**Prüfkriterium:** Ein Anliegen wird persistent gespeichert, bevor eine
KI-Analyse durchgeführt wird. Ist KI oder RAG nicht verfügbar, bleibt der
Fall für die manuelle Bearbeitung verfügbar und der Analysezustand wird
entsprechend gekennzeichnet.


### NFR-04 – Nachvollziehbarkeit / Auditierbarkeit

Eine KI-gestützte Empfehlung muss später nachvollzogen werden können.

**Prüfkriterium:** Für jede relevante KI-Empfehlung können mindestens
Modell-/Modellversion, Prompt-Version, verwendete Wissensquellen,
Zeitpunkt sowie die anschliessende menschliche Entscheidung
rekonstruiert werden.


### NFR-05 – Datenschutz / Sicherheit

Personen- und Mieterdaten dürfen nicht unkontrolliert an externe
KI-Dienste übertragen werden.

**Prüfkriterium:** Die Vertrauensgrenze zum externen LLM-Provider wird
zentral kontrolliert. Es werden nur die für den jeweiligen KI-Aufruf
notwendigen Daten übertragen. Personenbezogene Daten werden soweit
fachlich möglich minimiert oder maskiert.


### NFR-06 – Betriebliche Einfachheit

Der MVP soll mit möglichst wenigen unabhängig zu betreibenden
Anwendungskomponenten bereitgestellt werden können.

**Prüfkriterium:** Das PropertyFlow-Backend wird als eine deploybare
Anwendung gebaut und betrieben. Die fachlichen Backend-Module benötigen
untereinander keine Netzwerkkommunikation und keine separaten Deployments.


### NFR-07 – Verfügbarkeit

Mieterinnen und Mieter müssen Anliegen auch ausserhalb der Bürozeiten und
am Wochenende erfassen können. Die Immobilienbewirtschaftung benötigt die
Anwendung während der definierten Betriebszeiten für die tägliche
Fallbearbeitung.

**Prüfkriterien:**

- Die Erfassung von Mieteranliegen ist grundsätzlich 24/7 vorgesehen.
- Der Ausfall von KI oder RAG darf die Erfassung eines Anliegens nicht
  verhindern.
- Die Kernfunktionen zur manuellen Fallbearbeitung sind während der
  definierten Betriebszeit zu mindestens 99,5 % verfügbar.


### NFR-08 – Benutzerfreundlichkeit

Ein Bewirtschafter soll unmittelbar erkennen können, welche Fälle zuerst
bearbeitet werden müssen und wie der aktuelle Bearbeitungsstand ist.

**Prüfkriterium:** Ein neu eingearbeiteter Mitarbeiter kann innerhalb von
30 Sekunden nach Öffnen eines Falls Dringlichkeit, KI-Empfehlung,
Zuständigkeit und Bearbeitungsstatus korrekt erkennen.

Technische KI-Details werden dafür nicht in den Vordergrund gestellt.


## Architekturtreiber

Nicht alle Qualitätsanforderungen unterscheiden gleich stark zwischen
möglichen Architekturstilen.

Für die Wahl der Grundarchitektur sind insbesondere folgende Anforderungen
entscheidend:

1. Änderbarkeit
2. Testbarkeit
3. Zuverlässigkeit / Robustheit
4. Nachvollziehbarkeit / Auditierbarkeit
5. Datenschutz / Sicherheit
6. Betriebliche Einfachheit

Verfügbarkeit und Benutzerfreundlichkeit sind ebenfalls verbindliche
Qualitätsanforderungen, entscheiden jedoch nicht unmittelbar zwischen
modularem Monolithen und Microservices. Sie beeinflussen spätere
Design- und Implementierungsentscheidungen.


## Betrachtete Alternativen

### Alternative 1 – Klassischer Schichtenmonolith

Das Backend wird primär nach technischen Schichten strukturiert, zum Beispiel:

- Controller
- Service
- Repository

#### Vorteile

- einfaches und bekanntes Architekturmodell
- geringer initialer Architekturaufwand
- einfache Bereitstellung als eine Anwendung

#### Nachteile

- fachlich zusammengehörender Code verteilt sich über technische Schichten
- Abhängigkeiten zwischen Fachbereichen können mit zunehmendem Umfang
  schwer kontrollierbar werden
- externe Integrationen können sich stärker mit der Fachlogik vermischen
- fachliche Grenzen sind im Code weniger deutlich sichtbar


### Alternative 2 – Modularer Monolith

Das Backend wird als eine deploybare Anwendung betrieben, intern jedoch
entlang fachlicher Verantwortlichkeiten in klar abgegrenzte Module
strukturiert.

Vorgesehene fachliche Bereiche sind beispielsweise:

- Intake
- Triage
- Recommendation
- Case Processing

Die Module kommunizieren über definierte Schnittstellen miteinander.

#### Vorteile

- fachliche Verantwortlichkeiten sind klar sichtbar
- Änderungen können weitgehend lokal innerhalb eines Moduls erfolgen
- externe Systeme können zentral gekapselt werden
- Module und Fachlogik sind isoliert testbar
- nur eine Backend-Anwendung muss betrieben werden
- Audit-, Datenschutz- und Fehlerbehandlungsregeln können einheitlich
  durchgesetzt werden
- ein späteres Herauslösen einzelner Module bleibt möglich

#### Nachteile

- Modulgrenzen müssen aktiv definiert und eingehalten werden
- alle Module werden gemeinsam deployed
- einzelne Module können nicht unabhängig skaliert werden
- ein technischer Fehler kann grundsätzlich Auswirkungen auf die gesamte
  Anwendung haben
- ohne Architekturregeln besteht langfristig die Gefahr eines stark
  gekoppelten Monolithen


### Alternative 3 – Microservices

Die fachlichen Bereiche werden als eigenständig deploybare Services
realisiert.

#### Vorteile

- starke technische Isolation
- unabhängige Deployments
- unabhängige Skalierung einzelner Services
- klare technische Systemgrenzen

#### Nachteile

- zusätzliche Netzwerkkommunikation
- mehr potentielle Fehlerquellen
- komplexere Fehlerbehandlung und Wiederholungsmechanismen
- höhere Anforderungen an Monitoring und Distributed Tracing
- komplexere Audit-Korrelation über mehrere Services hinweg
- zusätzlicher Aufwand für Authentifizierung und Absicherung interner
  Service-Kommunikation
- höhere Komplexität bei Entwicklung, Deployment und Betrieb
- verteilte Prozesse und Datenkonsistenz müssen zusätzlich gelöst werden

Für den aktuellen Umfang von PropertyFlow gibt es keine Anforderung an
unabhängige Release-Zyklen oder unabhängige Skalierung, welche diese
zusätzliche Komplexität rechtfertigt.


## Bewertung

| Qualitätsziel | Schichtenmonolith | Modularer Monolith | Microservices |
|---|---|---|---|
| Änderbarkeit | mittel | **hoch** | hoch |
| Testbarkeit | mittel | **hoch** | mittel bis hoch |
| Zuverlässigkeit / Robustheit | mittel | **hoch** | mittel bis hoch |
| Auditierbarkeit | mittel | **hoch** | mittel |
| Datenschutz / Sicherheit | mittel | **hoch** | mittel bis hoch |
| Betriebliche Einfachheit | **hoch** | **hoch** | niedrig |
| Aktueller Projektumfang | mittel | **hoch** | niedrig |

Der klassische Schichtenmonolith ist betrieblich einfach, unterstützt die
gewünschte fachliche Kapselung jedoch weniger stark.

Microservices ermöglichen eine stärkere technische Isolation und unabhängige
Skalierung. Diese Vorteile werden im aktuellen PropertyFlow-MVP jedoch nicht
benötigt. Gleichzeitig würden zusätzliche Netzwerk-, Betriebs-, Security- und
Observability-Aufwände entstehen.

Der modulare Monolith bietet die für PropertyFlow benötigten fachlichen
Grenzen, ohne die zusätzliche Komplexität eines verteilten Systems.


## Entscheidung

Das Backend von PropertyFlow wird als **modularer Monolith** umgesetzt.

PropertyFlow besitzt eine gemeinsame Backend-Deployment-Einheit. Innerhalb
dieser Anwendung werden die fachlichen Verantwortlichkeiten in klar
abgegrenzte Module aufgeteilt.

Die Module dürfen nicht direkt auf interne Implementierungsdetails anderer
Module zugreifen. Die Kommunikation erfolgt über definierte Schnittstellen.

Externe Systeme wie LLM-Provider, E-Mail-System,
Mieterstammdatensystem und Retrieval-Infrastruktur werden gekapselt und
dürfen nicht direkt mit der fachlichen Kernlogik vermischt werden.

Der Ausfall einer unterstützenden KI- oder Retrieval-Komponente darf den
Kernprozess zur Erfassung und manuellen Bearbeitung eines Mieteranliegens
nicht verhindern.


## Konsequenzen

### Positive Konsequenzen

- klare fachliche Modulgrenzen
- geringe betriebliche Komplexität
- keine verteilte Kommunikation zwischen den Fachmodulen
- externe Systeme können ersetzt oder in Tests simuliert werden
- zentrale Durchsetzung von Datenschutz- und Auditregeln
- robuste Fehlerbehandlung kann innerhalb einer Anwendung koordiniert werden
- ein Mieteranliegen kann unabhängig von der KI-Verfügbarkeit verarbeitet
  beziehungsweise manuell weiterbearbeitet werden
- spätere Extraktion einzelner Module bleibt grundsätzlich möglich


### Negative Konsequenzen

- alle Backend-Module werden gemeinsam deployed
- Module können nicht unabhängig voneinander skaliert werden
- ein Ausfall des Backend-Prozesses betrifft grundsätzlich alle Module
- Modulgrenzen werden nicht durch physische Netzwerkgrenzen erzwungen
- die Einhaltung der Modulgrenzen muss durch Paketstruktur, Reviews,
  Tests und gegebenenfalls Architekturtests abgesichert werden


## Verworfene bzw. nicht gewählte Alternative

Eine Microservice-Architektur wird für den aktuellen Projektumfang nicht
gewählt.

Die Vorteile unabhängiger Skalierung und unabhängiger Deployments wiegen
den zusätzlichen Aufwand für Netzwerkkommunikation, Betrieb, Security,
Observability und verteilte Fehlerbehandlung derzeit nicht auf.

Sollten sich die Qualitätsanforderungen später ändern, kann die Entscheidung
neu bewertet werden.


## Kriterien für eine Neubewertung

Die Entscheidung wird überprüft, wenn mindestens eine der folgenden
Bedingungen eintritt:

- ein Modul benötigt einen unabhängigen Release-Zyklus
- einzelne Module müssen unabhängig skaliert werden
- unterschiedliche Sicherheitsanforderungen verlangen eine stärkere
  technische Isolation
- die gemeinsame Anwendung erreicht die geforderten
  Verfügbarkeitsziele nicht mehr
- Modulabhängigkeiten können trotz Architekturregeln nicht mehr ausreichend
  kontrolliert werden
- unterschiedliche Entwicklungsteams übernehmen unabhängig Verantwortung
  für einzelne Fachbereiche


## Nicht Bestandteil dieses ADR

Dieses ADR entscheidet ausschliesslich über die Grundarchitektur und
Deployment-Struktur des PropertyFlow-Backends.

Folgende Entscheidungen werden separat betrachtet:

- interne Architektur der einzelnen Module
- Einsatz von Camunda 8 zur Orchestrierung langlebiger Prozesse
- Kommunikation zwischen PropertyFlow und Camunda 8
- Persistenz und Betrieb des Workflow-State
- konkrete relationale Datenbank
- konkrete Vector-Store-Technologie
- RAG-Strategie
- Fine-Tuning-Strategie
- Wahl und Betrieb des LLM-Providers
