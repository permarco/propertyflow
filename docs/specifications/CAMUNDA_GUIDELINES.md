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
Die sieben BPMN-Dateien sind derzeit nicht ausführbare Fachentwürfe.

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

Für reine Layoutarbeit gelten die detaillierten Regeln in der BPMN-README:

- Positionen, Grössen, Abstände, Linienpunkte und Beschriftungspositionen dürfen
  geändert werden. Reine Zeilenumbrüche dürfen die Bedeutung nicht verändern.
- IDs, Elementtypen, Verbindungen, Scopes, Randereignis-Zuordnungen, Bedingungen,
  Timer, Call-Verweise, Mappings und technische Eigenschaften bleiben unverändert.
- Während manueller Layoutarbeit ändern Agenten keine BPMN-Dateien parallel.
- Die Farbkonvention bleibt einheitlich: **Blau = Service Task**, **Orange = User
  Task**, **Violett = Call Activity**, **Türkis = Send Task**. Farben bezeichnen
  Aufgabenarten, keine Status, Dringlichkeiten oder Berechtigungen.

## 8. Prüfung und Ausführungsreife

| Änderung / Nachweis | Erforderliche Prüfung |
|---|---|
| Reines Layout | Semantischer Diff, XML-/Referenzkonsistenz, Modeler-Import/-Export und visuelle Lesbarkeit |
| Prozesslogik | Zusätzlich Abgleich mit Status-/Aktionsmatrix, Fachregeln und Teilprozess-Verträgen |
| Ausführbare Bindung | Jobtypen, FEEL-Bedingungen, Mappings, Ereigniszuordnung, Timer und Deployment auf der vorgesehenen Engine prüfen |
| Backend-/Worker-Implementierung | Rechte, Idempotenz, Versionskonflikte, konkurrierende Aktionen, verlorene Antworten und Ausfälle mit Persistenz/Engine testen |

Ein XML-Import oder Screenshot ist kein Laufzeitnachweis. Die Kennzeichnung als
ausführbar darf nicht allein gesetzt werden, um einen Entwurf deploybar erscheinen
zu lassen. B10 umfasst die vollständigen Bindungen und die tatsächlichen Tests.

Bei Änderungen MÜSSEN betroffene Spezifikation, Teilprozess-Vertrag und Review
konsistent nachgeführt werden. Historische Modellstände sind als solche zu
kennzeichnen; offene Prüffälle dürfen nicht als bestandene Tests erscheinen.
