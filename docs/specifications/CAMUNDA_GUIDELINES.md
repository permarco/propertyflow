# Camunda Guidelines für PropertyFlow

**Stand:** 15. November 2026  
**Status:** Konsolidierte Projektregeln aus den bestätigten Architektur- und
Prozessentscheidungen. Kein Nachweis einer implementierten oder abgenommenen Lösung.

## 1. Geltung, Verbindlichkeit und Quellen

Diese Richtlinien gelten für die Bearbeitung der BPMN-Dateien, die Camunda-Anbindung,
Job Worker und zugehörige Tests. Sie bündeln bestehende Regeln; sie definieren
keine zusätzlichen Fachfunktionen. **MUSS** und **DARF NICHT** kennzeichnen verbindliche
Projektregeln, **SOLL** eine begründet abweichbare Empfehlung.

Fachregeln werden an ihrer bestehenden Quelle gepflegt, nicht unabhängig hier
weiterentwickelt. Bei einem Widerspruch ist dieser anhand der bestätigten
Entscheidungen zu klären, statt stillschweigend die Prozesslogik zu verändern.

| Quelle | Verantwortung |
|---|---|
| [ADR-002](../architecture/adr/ADR-002-camunda8-workflow-orchestrierung.md) | Prozessführung, Integration und Ausfallverhalten |
| [Modulstruktur](../architecture/module-structure.md) | Modulgrenzen und interne Verträge |
| [Fallverwaltung](fallverwaltung.md) | Status, erlaubte Aktionen, Fortsetzung und B07/B09-Verträge |
| [Fallkommunikation](fallkommunikation.md) | Nachrichten, Antwortbedarf und Veröffentlichung |
| [Zugriff und Sicherheit](fallzugriff-und-sicherheit.md) | Identitäten, Rechte und erneute Autorisierung |
| [Benachrichtigungen](benachrichtigungen-und-zustellung.md) | Mailpflicht und Zustellung |
| [Teilprozess-Verträge](../architecture/bpmn/teilprozess-vertraege.md) | Call-Hierarchie, Ein-/Ausgaben und Unterbrechungen |
| [BPMN-README](../architecture/bpmn/README.md) | Dateien, Navigation, Farben und Layoutarbeit |
| [Status-/Aktionsreview](../architecture/bpmn/status-aktionsmatrix-review-2026-11-15.md) | Bearbeitete Befunde und verbleibende Nachweise |

Eine beschlossene Regel, ein modellierter Entwurf, implementierter Code und ein
bestandener Laufzeittest MÜSSEN als unterschiedliche Nachweisstände bezeichnet werden.
Seit 16.11.2026 liegen alle sieben ausführbaren Modelle als verbindliche
Anwendungsressourcen unter `src/main/resources/bpmn`. Deployment und Tests verwenden
diese Quellen ohne nachträgliches Umschalten von `isExecutable`. Unter `docs` bleiben
Beschreibungen und Verweise; dort DARF KEINE zweite bearbeitete Modellkopie entstehen. Der jeweils nachgewiesene Umfang steht in der BPMN-README und der
zugehörigen Laufzeitdokumentation; eine vollständige B10-Abnahme folgt daraus nicht.

## 2. Prozessführung und Datenverantwortung

- Nach der Fallannahme MUSS Camunda die fachlichen Statuswechsel veranlassen.
  UI, HTTP- und MCP-Adapter dürfen diese Prozessführung nicht umgehen.
- Die Fachlogik und Geschäftsdaten bleiben in PropertyFlow. Worker rufen die
  zuständigen Anwendungsdienste auf; sie bauen keine zweite Fachlogik auf.
- Camunda läuft ausserhalb des Spring-Boot-Backends. Die Anbindung folgt den
  vereinbarten Client-/Worker-Verträgen und den Modulgrenzen.
- Fallannahme und dauerhafte Speicherung dürfen nicht von der Verfügbarkeit
  von Camunda, KI oder Mail abhängen. Die weitere Bearbeitung folgt dem
  Ausfallvertrag aus ADR-002/FALL-06; keine Ersatzorchestrierung im Controller.
- Ein gespeicherter Fall ist noch keine bestätigte aktive Prozesszuordnung.
  Erstanlage, Wiedereröffnung und Fortsetzung MÜSSEN unterschieden werden.

## 3. BPMN-Struktur und Teilprozesse

- Jede der sieben Dateien ist die einzige bearbeitete Quelle ihres Prozesses.
  Die Zuordnung steht in der BPMN-README; Dateinamen sind keine Call-Schlüssel.
- Call Activities MÜSSEN auf die vereinbarte Prozess-ID verweisen. Ein- und
  Ausgaben, Bindung und Ergebniswerte folgen den Teilprozess-Verträgen.
- Eltern- und Kindinstanzen dürfen nicht als gemeinsam veränderlicher Speicher
  behandelt werden. Variablenübergaben erfolgen explizit und scopegebunden.
- Unbekannte Ergebnisse sind Vertragsfehler, keine stillschweigende Freigabe
  eines Standardpfads.
- Gemeinsame Unterbrechungen der automatischen Abklärung bleiben am gemeinsamen
  Call. Zusätzliche Einzel-Randereignisse dürfen die vereinfachte Struktur nicht
  ohne fachlich begründete Modelländerung wieder vervielfachen.
- Eine Nebenaktion darf Aufgabe, Wartephase oder Timer nur dann beenden, wenn
  der Fachvertrag das ausdrücklich vorsieht. Ein normaler Nebenprozessabschluss
  darf nicht versehentlich den führenden Ablauf beenden.
- Neue oder geänderte BPMN-Konstrukte MÜSSEN vor ihrer ausführbaren Verwendung
  gegen die tatsächlich konfigurierte Camunda-Version geprüft werden. Ein
  erfolgreiches Öffnen im Modeler belegt keine Engine-Unterstützung.

### 3.1 Explizite Zusammenführung von Pfaden

**Ergänzt am 15.11.2026:** Alternative Pfade MÜSSEN immer über ein explizites
zusammenführendes **exklusives Gateway (XOR)** geführt werden. Mehrere alternative
eingehende Sequenzflüsse dürfen nicht direkt an einer Aufgabe, einem Ereignis
oder einem anschliessenden verzweigenden Gateway zusammenlaufen.

Das zusammenführende XOR-Gateway hat mehrere Eingänge und einen Ausgang.
Zusammenführung und anschliessende Verzweigung werden getrennt dargestellt.
Beispiel: Ersteintritt und Rückweg nach Terminänderung treffen sich vor
«Auf erstes Ereignis warten» in einem eigenen XOR-Gateway.

**Semantische Grenze:** Diese Regel gilt für alternative Pfade. Ein XOR-Gateway
synchronisiert keine parallelen Ausführungen und verhindert keine Doppelwirkung.
Müssen parallele Pfade gemeinsam abgewartet werden, MUSS eine zur Verzweigung
passende Synchronisation modelliert werden; ein XOR ist dafür kein Ersatz.
Die Fortsetzungs- und Idempotenzregeln aus B07 bleiben unabhängig davon bestehen.

Das Einfügen eines Gateways ändert die Modellstruktur und ist keine reine
Layoutänderung. Bestehende Modelle werden deshalb separat geprüft und angepasst;
diese Richtlinie allein bestätigt noch keine vollständige Umsetzung in den Dateien.

### 3.2 Namen und IDs in BPMN-Modellen

Sichtbare Elementnamen MÜSSEN den fachlichen Schritt kurz beschreiben. Umlaute
werden als `ae`, `oe` und `ue` geschrieben, `ß` als `ss`. Ein Zeilenumbruch
im Modeler zählt für die ID wie ein Leerzeichen. Die XML-ID übernimmt die
Wörter des Namens, trennt sie mit Unterstrichen und endet mit dem Typ.

| BPMN-Typ | Typ-Postfix | Beispiel |
|---|---|---|
| Prozess | `_PROCESS` | `Fall_bearbeiten_PROCESS` |
| Service Task | `_SERVICE` | `Eintritt_und_aktuellen_Fallstand_pruefen_SERVICE` |
| User Task | `_USERTASK` | `Fall_pruefen_und_Vorgehen_entscheiden_USERTASK` |
| Send Task | `_SENDTASK` | `Statusmail_versenden_SENDTASK` |
| Call Activity / eingebetteter Teilprozess | `_CALL` / `_SUBPROCESS` | `Automatische_Abklaerung_CALL` |
| Start / Ende | `_START` / `_END` | `Auf_Fallaktion_warten_START` |
| Zwischenereignis (empfangend / auslösend) | `_CATCH` / `_THROW` | `Auf_Mieterantwort_warten_CATCH` |
| Randereignis | `_BOUNDARY` | `Wiedervorlage_unterbrochen_BOUNDARY` |
| Exklusives / ereignisbasiertes Gateway | `_XOR` / `_EVENT_GATEWAY` | `Welche_Fallaktion_XOR` |
| Sequenzfluss | `_FLOW` | Die ID beschreibt Ausgangspunkt und Ziel. |
| Lane / Gruppe | `_LANE` / `_GROUP` | `Aktionsempfang_LANE` |

Bei gleichlautenden Namen ergänzt die ID den fachlichen Kontext vor dem
Typ-Postfix. Zusammenführungen und andere Elemente ohne sichtbaren Namen
erhalten eine ID aus ihrer fachlichen Funktion. Bei Flüssen kann der Name des
Zweigs zusätzlich in die ID einfliessen. Für XML- und Diagrammobjekte den
fachlichen Bezug beibehalten und den passenden Typ ergänzen, etwa `_SHAPE`,
`_EDGE` und `_DIAGRAM`. Eine ID darf im selben Modell nur einmal vorkommen.

Job-Typen, korrelierte Nachrichtennamen, Link-Namen, Fehlercodes und FEEL-
Variablen sind technische Verträge. Sie folgen ihren bestehenden Regeln.
Bei einer Umbenennung sämtliche XML-Verweise, Call-IDs, Engine-Abfragen,
Worker-Bindungen, Tests und Deployment-Auswahl prüfen. Vor der Produktion
verwenden die Anwendung und die Testdeployments ausschliesslich die aktuellen IDs.

### 3.3 Java-Konstanten für BPMN-Prozesse

Pro BPMN-Datei gibt es genau eine öffentliche, finale Konstantenklasse im Paket
`com.pertegato.propertyflow.caseprocessing.contract.bpmn`. Ihr Name MUSS dem
Schema `<FachlicherProzessname>BpmnConstants` folgen. Der fachliche Prozessname wird
auf Englisch in `PascalCase` geschrieben. `BpmnConstants` ist der feste Suffix.
Die `.java`-Datei trägt denselben Namen wie die Klasse. Die Klassen enthalten
Prozess- und Element-IDs sowie weitere prozessbezogene Konstanten; ihr Name
beschränkt sich deshalb nicht auf IDs. Prozessübergreifende Konstanten stehen
in der übergeordneten Sammlung `CommonBpmnConstants` im selben Paket.
Diese gemeinsame Sammlung ist keine Basisklasse: Die Prozessklassen bleiben
`final` und verwenden keine Vererbung für Konstanten.

| BPMN-Datei | Java-Klasse |
|---|---|
| `propertyflow-fallprozess-entwurf.bpmn` | `CaseProcessBpmnConstants` |
| `automatische-abklaerung.bpmn` | `AutomaticClarificationBpmnConstants` |
| `gespraech-auswerten.bpmn` | `ConversationEvaluationBpmnConstants` |
| `mieterantwort-pruefen.bpmn` | `TenantReplyBpmnConstants` |
| `mitarbeiterbearbeitung.bpmn` | `StaffProcessingBpmnConstants` |
| `wiedervorlage.bpmn` | `FollowUpBpmnConstants` |
| `mieternachricht-verarbeiten.bpmn` | `TenantMessageBpmnConstants` |
| `fallbenachrichtigung-versenden.bpmn` | `CaseNotificationBpmnConstants` |

Die Prozess-ID heisst immer `PROCESS_ID` und steht direkt in der Modellklasse.
Element-IDs liegen nach BPMN-Typ in `public static final` Unterklassen. Der
Unterklassenname übernimmt den BPMN-Elementtyp in `PascalCase`. Eine Gruppe
wird angelegt, sobald Java eine ID dieses Typs benötigt.

| BPMN-Elementtyp | Java-Unterklasse |
|---|---|
| `serviceTask`, `userTask`, `sendTask` | `ServiceTask`, `UserTask`, `SendTask` |
| `callActivity`, `subProcess` | `CallActivity`, `SubProcess` |
| `startEvent`, `endEvent`, `boundaryEvent` | `StartEvent`, `EndEvent`, `BoundaryEvent` |
| `intermediateCatchEvent`, `intermediateThrowEvent` | `IntermediateCatchEvent`, `IntermediateThrowEvent` |
| `exclusiveGateway`, `eventBasedGateway` | `ExclusiveGateway`, `EventBasedGateway` |
| `error`, `message` | `Error`, `Message` |

IDs werden als `public static final String` in `UPPER_SNAKE_CASE` deklariert.
`AutomaticClarificationBpmnConstants.ServiceTask.CHECK_ENTRY` referenziert zum
Beispiel `Eintritt_und_aktuellen_Fallstand_pruefen_SERVICE`. Worker und Engine-
Abfragen MÜSSEN diese Konstanten verwenden. Ändert sich eine ID im BPMN, wird
ihr Wert in der zugehörigen Klasse angepasst. Der Bindungstest gleicht jede Konstante
mit ihrer ID und ihrem Typ im Originalmodell ab.

### 3.4 Jobtypen, Fehlercodes und Eskalationscodes

**Verbindlich seit 24.11.2026:** Zu den BPMN-Konstanten gehören neben IDs und
Variablennamen auch die technischen Bindungswerte. Diese stehen ausschliesslich
in den zuständigen `BpmnConstants`-Klassen, jeweils als `public static final String` in einer eigenen `public static final`
Unterklasse mit privatem Konstruktor:

| Unterklasse | BPMN-Vertrag |
|---|---|
| `JobType` | `zeebe:taskDefinition/@type`, verwendet von `@JobWorker(type = ...)` und Jobabfragen |
| `ErrorCode` | `bpmn:error/@errorCode`, verwendet beim Auslösen und Empfangen eines BPMN-Fehlers |
| `EscalationCode` | `bpmn:escalation/@escalationCode` |

Ein Wert gehört zu der Prozessklasse, deren Modell ihn verwendet. Von mehreren
Prozessen verwendete Werte stehen einmalig in der passenden Unterklasse von
`CommonBpmnConstants`. Beispielsweise stehen die drei Analyse-/Empfehlungs-/
Abschlussbewertungsjobs unter `ConversationEvaluationBpmnConstants.JobType`,
während `ANALYSIS_BASIS_STALE` unter `CommonBpmnConstants.ErrorCode` liegt.

Worker und Adapter definieren diese Werte weder selbst noch über weiterleitende
Alias-Konstanten. Annotationen, Fehlermeldungen an Camunda, Jobabfragen und
Java-Testaufbauten verwenden die zentrale Definition direkt, vorzugsweise mit
expliziten statischen Imports. Unabhängige Vertragserwartungen in Tests bleiben
zulässig. Technische Diagnosetexte und fachliche Fehler sind von BPMN-Fehlercodes
zu unterscheiden; sie werden nicht allein wegen ihrer Schreibweise verschoben.

`Error` enthält weiterhin XML-Element-IDs; `ErrorCode` enthält die tatsächlich
übertragenen Fehlercodes. Ein Jobtyp ist ebenso wenig eine Service-Task-ID.
Bei Modellumbauten entscheidet die tatsächliche Bindung über die Zuständigkeit,
nicht ein historischer Namenspräfix wie `propertyflow.automatic`.

Vor der Produktion werden abgelöste Jobtypen und ihre Worker gemeinsam mit
den zugehörigen Legacy-Tests entfernt. Bindungstests prüfen
die Werte gegen die kanonischen Modelle, sämtliche Worker-Bindungen gegen
die Konstanten und verhindern neue lokale Definitionen im Workflow-Adapter.

## 4. Ereignisse, Variablen und Unterbrechungen

- Nachrichten und Mitarbeiteraufträge MÜSSEN dem verifizierten Fall, Durchlauf
  und passenden Call-/Warte-/Task-Kontext zugeordnet werden.
- Eine Datenbankänderung allein ist kein Engine-Ereignis. Die technische
  Zustellung beziehungsweise Aktualisierung im zuständigen Scope muss erfolgen.
- Unterbrechungen verwenden gespeicherte, geprüfte Entscheide. Auf normale
  Rückgaben eines bereits abgebrochenen Kindes darf nicht vertraut werden.
- Späte Ergebnisse dürfen keine neue Mitarbeiterentscheidung überschreiben,
  keine beendete Bearbeitung wiederbeleben und keinen abgeschlossenen Fall
  eigenmächtig wiedereröffnen.
- Prozessvariablen enthalten nur erforderliche Steuerungsdaten und Referenzen.
  Zugangstokens, Einmalcodes und Sitzungsschlüssel gehören nicht hinein.

### 4.1 Zentrale Namen der Prozessvariablen

**Verbindlich seit 24.11.2026:** Prozessvariablennamen stehen als
`public static final String` in einer Unterklasse `Variablen`; die Bezeichner
verwenden `UPPER_SNAKE_CASE`. Die Unterklasse ist `public static final` und
besitzt einen privaten Konstruktor.

- Namen, die mehrere BPMN-Prozesse beziehungsweise deren Übergaben verwenden,
  MÜSSEN genau einmal unter `CommonBpmnConstants.Variablen` definiert werden.
  Dazu gehören beispielsweise `CASE_ID`, `AUTOMATIC_DECISION_REF` und
  `AUTOMATIC_OUTCOME`.
- Ausschliesslich prozessspezifische Namen stehen unter
  `<FachlicherProzessname>BpmnConstants.Variablen`.
- Verbraucher importieren die gemeinsame Definition direkt. Keine erneuten
  Definitionen oder weiterleitenden Alias-Konstanten in den Prozessklassen.
  Wird ein bisher lokaler Name Teil eines gemeinsamen Vertrags, wird seine
  Definition in `CommonBpmnConstants.Variablen` verschoben und jeder Zugriff
  angepasst.

Die Regel umfasst Eingaben, Ausgaben und lokale Steuerungsvariablen, auch
wenn ein Name nur in einem Worker oder einer BPMN-Bedingung verwendet wird.
Worker, Prozessstarter, Nachrichtenadapter, Variablenabfragen und die
zugehörigen Java-Testaufbauten MÜSSEN die Konstanten beim Lesen und Schreiben
verwenden. Wiederholte String-Literale für Variablennamen sind dort nicht
zulässig. Unabhängige Erwartungen in Vertragstests bleiben zulässig.

Die gemeinsame Klasse beschreibt Namen des Orchestrierungsvertrags, keinen
neuen Datentyp und keinen gemeinsam veränderlichen Variablenspeicher. Werte
bleiben an ihre Camunda-Instanz und ihren Scope gebunden. Übergaben zwischen
Prozessen erfolgen weiterhin über die vereinbarten Mappings und Nachrichten.

Beispiel mit expliziten statischen Imports:

```java
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.ENTRY_CONTEXT_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.AUTOMATIC_CALL_REF;
import static com.pertegato.propertyflow.caseprocessing.contract.bpmn.CommonBpmnConstants.Variablen.LEADING_PROCESS_INSTANCE_KEY;

UUID entry = UUID.fromString(text(job, ENTRY_CONTEXT_REF));
UUID call = UUID.fromString(text(job, AUTOMATIC_CALL_REF));
long leader = Long.parseLong(text(job, LEADING_PROCESS_INSTANCE_KEY));
```

Eine Umbenennung wird für Java an der zentralen Definition vorgenommen.
BPMN-XML, FEEL-Ausdrücke, Ein-/Ausgabemappings und externe Verträge können
Java-Konstanten nicht direkt auflösen und MÜSSEN konsistent nachgeführt
werden. Bindungstests prüfen die Namen gegen die kanonischen BPMN-Dateien
und verhindern doppelte Definitionen in gemeinsamen und lokalen Sammlungen.
Bei bereits laufenden Instanzen sind gespeicherte alte Variablennamen zu
berücksichtigen; eine Konstantenänderung migriert diese Instanzen nicht.

## 5. Worker, Rechte und KI

Die verbindlichen Einzelregeln stehen in FALL-06 unter B09.1–B09.5.

- Ein Job allein ist keine fachliche Freigabe. Der Dienst MUSS Auftragsherkunft,
  Berechtigung, Fallstand und Bearbeitungsbezug vor der Wirkung prüfen.
- Jeder Worker darf nur seine vorgesehene Fachaktion ausführen. Er lädt die
  massgebenden Daten anhand einer geprüften Auftragsreferenz im Backend.
- Mitarbeiteridentität kommt aus der authentifizierten Auftragserteilung.
  Die aktuellen Rechte werden vor Ausführung erneut geprüft. Logout widerruft
  einen gültig angenommenen Auftrag nicht; Rechteentzug verhindert eine noch
  nicht ausgeführte Mitarbeiteraktion.
- Frei gesetzte Prozessvariablen und LLM-Ausgaben erteilen keine Rechte.
  Automatische Aktionen bleiben an die vereinbarten Systemregeln gebunden.
- Zwingender menschlicher Prüfbedarf hat Vorrang. Ein LLM darf Empfehlungen
  liefern; die Ausführungsbedingungen prüft der Dienst.
- Mail-Worker verwenden den gespeicherten Versandauftrag. Die Mailpflicht einer
  bereits veröffentlichten Nachricht bleibt von einem späteren Abschluss getrennt.

## 6. Nebenläufigkeit, Wiederholung und Fehler

- Fallversion, Operations-ID und geprüfter Bearbeitungsbezug MÜSSEN gemeinsam
  berücksichtigt werden. Eine alte Entscheidung darf nicht auf die neueste
  Aufgabe übertragen werden.
- Fachliche Änderung und erforderlicher Fortsetzungsauftrag werden gemäss B07
  konsistent gespeichert. Datenbank und Camunda sind keine gemeinsame atomare
  Transaktion; verlorene Antworten verlangen Abgleich statt blindem Neustart.
- Die exklusive Übernahme vor Staff folgt START / EXISTING / SUPERSEDED.
  Reservierungen allein beweisen keine laufende Aufgabe. Es dürfen weder doppelte
  Mitarbeiteraufgaben noch verlorene Fortsetzungen entstehen.
- Technische Fehler folgen Retry-/Incident-Verträgen. Fachliche Ablehnungen und
  veraltete Grundlagen folgen den ausdrücklich modellierten Fachpfaden.
- Eine angenommene Aktion ist noch kein Erfolg. Verarbeitung, bestätigte Wirkung,
  Ablehnung und technisch unklarer Ausgang bleiben im UI unterscheidbar.
  Timeout darf keinen neuen logischen Auftrag auslösen.

## 7. Darstellung und Zusammenarbeit

**Leserichtung:** Prozesse MÜSSEN möglichst von links nach rechts angeordnet
werden. Vorwärtsführende Pfade SOLLEN nicht nach links zeigen. Alternative Zweige
werden untereinander dargestellt und rechts wieder zusammengeführt. Notwendige
Rückschleifen werden ausserhalb des Hauptablaufs geführt; ihre Pfeilspitze soll
nach Möglichkeit von links in das Ziel führen. Die Leserichtung darf keine
Fachlogik verändern und keine künstlichen Prozesskopien erzeugen.

**Linienführung:** Verbindungen SOLLEN möglichst gerade und ohne unnötige Knicke
verlaufen. Zusammengehörige Elemente werden dafür horizontal beziehungsweise
vertikal ausgerichtet. Wenn Umwege wegen Verzweigungen, Rückschleifen oder anderer
Elemente nötig sind, werden wenige rechtwinklige Knicke verwendet. Linien dürfen
nicht zur Erzwingung einer geraden Verbindung durch Aufgaben oder Beschriftungen
geführt werden. Lesbarkeit und eindeutige Anschlussstellen haben Vorrang.

**Rückwege und Fehlerpfade:** Rückflüsse nach links SOLLEN bevorzugt oberhalb
des Hauptablaufs verlaufen. Error-Boundary-Events werden möglichst an der
Unterseite ihrer Aktivität platziert; ihre Fehlerpfade führen zunächst nach unten
und unterhalb des Hauptablaufs weiter. Muss ein solcher Fehlerpfad zu einem
früheren Schritt zurückführen, hat die untere Fehlerführung Vorrang. Diese
Darstellungskonvention ändert weder Ereignistyp noch Unterbrechungsverhalten.

Für reine Layoutarbeit gelten die detaillierten Regeln in der BPMN-README:

- Positionen, Grössen, Abstände, Linienpunkte und Beschriftungspositionen dürfen
  geändert werden. Reine Zeilenumbrüche dürfen die Bedeutung nicht verändern.
- IDs, Elementtypen, Verbindungen, Scopes, Randereignis-Zuordnungen, Bedingungen,
  Timer, Call-Verweise, Mappings und technische Eigenschaften bleiben unverändert.
- Während manueller Layoutarbeit ändern Agenten keine BPMN-Dateien parallel.
- Die Farbkonvention bleibt einheitlich: **Blau = Service Task**, **Rosa = Service
  Task mit möglichem LLM-Aufruf**, **Orange = User Task**, **Violett = Call
  Activity**, **Türkis = Send Task**. Rosa gilt auch für bedingte Neuauswertungen.
  Farben bezeichnen Aufgabenarten, keine Status, Dringlichkeiten oder
  Berechtigungen.

## 8. Prüfung und Ausführungsreife

| Änderung / Nachweis | Erforderliche Prüfung |
|---|---|
| Reines Layout | Semantischer Diff, XML-/Referenzkonsistenz, Modeler-Import/-Export und visuelle Lesbarkeit |
| Prozesslogik | Zusätzlich Abgleich mit Status-/Aktionsmatrix, Fachregeln und Teilprozess-Verträgen |
| Ausführbare Bindung | Jobtypen, FEEL-Bedingungen, Mappings, Ereigniszuordnung, Timer und Deployment auf der vorgesehenen Engine prüfen |
| Backend-/Worker-Implementierung | Rechte, Idempotenz, Versionskonflikte, konkurrierende Aktionen, verlorene Antworten und Ausfälle mit Persistenz/Engine testen |

Workflow-Integrationstests MÜSSEN die unveränderten Anwendungsmodelle aus
`src/main/resources/bpmn` verwenden. Zusammengehörige Prozesse werden gemeinsam
deployt, damit deployment-gebundene Calls auf die geprüften Teilprozesse zeigen.
Vereinfachte BPMN-Testkopien und XML-Umschreibungen von Bedingungen, Timern,
Scopes oder Bindungen sind kein Ersatz für diese Prüfung. Kontrollierte
Facheingaben und Worker-Aufrufe gehören in den Testaufbau. Bindungstests prüfen
explizite Erwartungen direkt an den Originalen und verwenden die aktuellen
Prozesskonstanten ohne Übersetzung auf historische Test-IDs.

Ein XML-Import oder Screenshot ist kein Laufzeitnachweis. Die Kennzeichnung als
ausführbar darf nicht allein gesetzt werden, um einen Entwurf deploybar erscheinen
zu lassen. B10 umfasst die vollständigen Bindungen und die tatsächlichen Tests.

Bei Änderungen MÜSSEN betroffene Spezifikation, Teilprozess-Vertrag und Review
konsistent nachgeführt werden. Historische Modellstände sind als solche zu
kennzeichnen; offene Prüffälle dürfen nicht als bestandene Tests erscheinen.
