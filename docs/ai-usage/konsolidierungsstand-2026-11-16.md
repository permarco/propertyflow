# Konsolidierungsstand und aktiver Auftrag – 16.11.2026

Dieser Stand dient als kurze Übergabe nach einer Kontextkomprimierung.

**Neuester Stand:** Die [Fortsetzung unten](#fortsetzung-formulierung-betriebsübersicht-bpmn-und-ci)
und die [Umsetzungsmatrix](../use_cases/umsetzungsstand-2026-11-16.md) haben Vorrang
vor älteren Teilnachweisen. MCP ist auf ausdrücklichen Benutzerwunsch für einen
separaten Lauf zurückgestellt; seine Anforderungen und Fehler sind hier keine
Abschlussbedingung.

## Verbindlicher aktueller Auftrag

Die weitere Modellevaluation ist auf ausdrücklichen Benutzerwunsch **zurückgestellt**.
Jetzt soll eine konsolidierte, produktionsnahe lokale Version entstehen:

1. Mieter können Fälle eröffnen, ihren Fall sehen und mit Mitarbeitern kommunizieren.
2. Mitarbeiter können sich anmelden, Fälle bearbeiten und mit Mietern kommunizieren.
3. Konten, Fälle, Nachrichten und Bearbeitungszustände werden dauerhaft in PostgreSQL gespeichert.
4. Camunda ist im normalen Backendbetrieb aktiviert; ein neuer Fall startet eine Prozessinstanz.
5. Der Benutzer kann Camundas Web-Interface öffnen und den aktuellen Prozessstand sehen.
6. Die komplette Kette wird praktisch geprüft und der Startbetrieb verständlich dokumentiert.
7. Auch ohne erreichbare oder konfigurierte KI bleibt der manuelle Bearbeitungsweg
   bis zur individuellen Abschlussmitteilung und Wiedereröffnung nutzbar.
   Verbindlicher Vertrag: [KI-Tools und manueller Ersatzbetrieb](../specifications/ki-tools.md).

Ohne Rückfragen arbeiten, vernünftige Annahmen dokumentieren. Neue Kommentare und
Javadocs auf Deutsch. Automatische Fallabschlüsse bleiben fest gesperrt.
Es ist eine produktionsnahe lokale Umgebung beauftragt, keine öffentliche Internetbereitstellung.
Bestehende Entwicklungsdaten und der stabile Link-Schlüssel werden beibehalten;
für diesen neuen Auftrag ist kein weiterer Datenbankreset vorgesehen.

## Workspace und Regeln

- Workspace: `C:/Dev/propertyflow`.
- Branch: `feat/block3-server-architektur-interfaces-http`.
- Sehr grosser vorhandener staged/unstaged Arbeitsstand; fremde Änderungen erhalten.
- Kein Commit oder Push durchgeführt oder beauftragt.
- [Repository-Regeln](../../.github/copilot-instructions.md) und die dort verlinkten
  Architektur-/Fachspezifikationen sind verbindlich.
- [Java-Regeln](../specifications/JAVA_GUIDELINES.md),
  [Camunda-Regeln](../specifications/CAMUNDA_GUIDELINES.md),
  [Implementierungsannahmen](../architecture/implementierungsannahmen-2026-11-15.md).

## Verifizierter technischer Stand

Der Paketbau vom 16.11.2026 um 13:32:59 besteht mit **107 Tests, 0 Fehlern, 0 übersprungenen Tests**:
58 Vertrags-/Corpus-/Adapterprüfungen, 31 PostgreSQL- und 18 echte Camunda-Integrationstests.

- [Technischer Nachweis mit Quellprüfsummen](../evaluation/evidence/closure/technical-regression-2026-11-16.json)
- Build-Artefakt: `target/propertyflow-0.0.1-SNAPSHOT.jar`.
- Build-Protokoll: `target/closure-evaluation/final-regression.log`.
- Geprüft: `AutomaticClosurePostgresTest`, `ConversationEvaluationPostgresTest`,
  `CanonicalModelsCamundaTest`, `ConversationEvaluationCamundaTest` und sieben Vertrags-/Corpus-/Adapter-Testklassen.
- Dieser frühere Evaluationslauf enthielt keinen Browsernachweis; die neueren Dev- und Kontenprüfungen unten ergänzen ihn.

Der Fallstartpfad ist implementiert:
`PersistentCaseService` speichert Fall und Startauftrag zusammen;
`ProcessStartDispatcher` übernimmt den Auftrag;
`CamundaProcessStartGateway` startet die Prozessinstanz;
`CaseWorkflowService` bestätigt die führende Instanz und den initialen Statuspfad.
Nach Registrierung und Initialisierung wird der Fall «In Abklärung».
Die Prozessabläufe sind gegen eine reale Camunda-Testengine geprüft.

**Der integrierte Devbetrieb ist jetzt aktiviert:**
`pwsh -File dev/start.ps1` startet PostgreSQL, Camunda 8.9.19 mit eigener PostgreSQL-
Sekundärdatenbank, Mailpit und das Backend mit `propertyflow-backend,propertyflow-dev`.
Die sieben kanonischen BPMN-Modelle werden als ausführbare Laufzeitkopien deployt.
CLI-Argumente aktivieren Workflow und Mail ausdrücklich; die alte private Basiskonfiguration
wird nicht überschrieben. Ein stabiler Start-/Stop-Vertrag verwaltet nur den eigenen Javaprozess.

Sieben synthetische Fälle sind über echte HTTP-Aktionen und Camunda in Mitarbeiterprüfung,
Bearbeitung, externes Warten, Wiedervorlage, Mieterwarten, Abschluss und Wiedereröffnung
geführt worden. Kein SQL wurde zum Vortäuschen der Statuswerte verwendet. Die privaten
Fall-Links und der Seed-Fortschritt stehen in `.propertyflow-local/dev-cases.json`.
Vier neue Zugriffstests für die direkte Operate-Verknüpfung bestehen. Der durchgängige
Browsertest ohne KI besteht. Nach vollständigem Neustart blieben alle 10 Fälle,
Prozesszuordnungen, Nachrichten, das Konto und 62 Mails unverändert; Health UP und
0 Camunda-Incidents. [Prüfbericht](../evaluation/devbetrieb-2026-11-16.md), lokale
Nachweise unter `target/dev-verification/`.

[Aktuelle Dev-Anleitung](../../dev/README.md) · [Startskript](../../dev/start.ps1) ·
[KI-Fallback-Spezifikation](../specifications/ki-tools.md)

## Vorhandene Infrastruktur – vor Änderungen erneut inventarisieren

- Entwicklungs-PostgreSQL: Port 5433, Datenbank `propertyflow`,
  `dev/compose.backend.yaml`, private Verbindung in `.propertyflow-local/backend.properties`.
  Passwort und Verschlüsselungsschlüssel nicht ausgeben oder neu erzeugen.
- Isolierte Tests: PostgreSQL 5434 (`propertyflow_test`), Camunda 8085/26505,
  Mailpit 1027/8027. Camunda-Testdaten liegen in tmpfs; diese Umgebung ist **kein**
  dauerhaftes Ziel für den produktionsnahen Betrieb.
- `dev/compose.workflow-test.yaml` verwendet `camunda/camunda:8.9.19`.
  Die Testkonfiguration aktiviert Business-ID-Eindeutigkeit und verwendet Testzugänge.
- `dev/compose.yaml`: Mailpit auf 1025/8025 mit dauerhaftem SQLite-Volume. Vorhandene Mails wurden konsistent übernommen.
- `dev/compose.runtime.yaml`: dauerhafte Dev-Camunda-Instanz auf 8080/26500, PostgreSQL separat ohne Hostport.
- Bestehende Camunda-Infrastruktur liegt unter `infra/camunda/`; als Nächstes lesen.
- Der frühere IDE-Prozess auf 8081 wurde nach Workspace-Zuordnung beendet. Der eigene
  Devprozess läuft auf 8081; PID und Startzeitkennung stehen in `.propertyflow-local/dev-process.json`.
- Ein nicht zugeordneter PostgreSQL-Dienst auf 5432 wurde bewusst nicht verändert.
- `.propertyflow-local/staff-bootstrap.properties` existiert. Vorhandene Kontokonfiguration
  prüfen und erhalten; keine persönlichen Adressen in versionierte Dokumente kopieren.
- SMTP für die Betriebsprüfung an lokales Mailpit binden; keine realen externen Mails versenden.

Es gibt nur noch fünf Modulinitialskripte (Versionen V1–V5). Legacy-SQL wurde nach einem
früher ausdrücklich autorisierten Reset entfernt. Additive Updates V6–V8 ergänzen Konten und Fallzugangsverwaltung; aktuell 54 Fachtabellen.
Die nächste freie Migration ist V9. Bestehende Entwicklungsdaten bleiben erhalten.

## Zurückgestellte Modellevaluation

- [Vollständiger Evaluationsbericht](abschluss-evaluation-2026-11-16.md).
- `qwen3.5:4b`, Bewertungsfassung v7: **45/51** Fälle bestanden.
- Kommunikationsfassung v7, Schema v2: **29/34** automatisierte Fälle bestanden.
  Verbleibende Fehlfreigaben: generischer Text, eingebettete Freigabeanweisung,
  veraltete Türstörung, erfundenes Datum, unausgefüllter Datumsplatzhalter.
- `AutomaticClosureRelease.approved(...)` bleibt unverändert `false`.
- Synthetische Abschlussregeln liegen nur in Tests; kein produktiver Regelbestand wurde erfunden.
- Der lokale Kandidat `qwen3.5:9b` wurde heruntergeladen (ca. 6,55 GB), aber sein
  13-Fälle-Versuch auf Benutzerwunsch nach einem protokollierten Fall beendet.
  [Teilbericht](../evaluation/evidence/closure/qwen35-9b-2026-11-16-rule-v7-incomplete.json),
  [Abbruchnotiz](../evaluation/evidence/closure/qwen35-9b-2026-11-16-rule-v7-termination.json).
- Der eigene Testprozess ist beendet, das 9B-Modell aus dem Arbeitsspeicher entladen.
  Es läuft kein von diesem Auftrag gestarteter Modell-Evaluationsprozess mehr.
- Diese Evaluation jetzt nicht erneut aufnehmen; der Betriebsauftrag hat Vorrang.

## Noch zu prüfen bzw. separat umzusetzen

1. Den aktuellen Browser- und Neustartnachweis unter `target/dev-verification/` mit
   dem neuesten Protokoll abgleichen; keine früheren Teilschritte als Gesamtabnahme ausgeben.
2. Persistente Mieterkonten, Kontoübersicht, Mitarbeiterkontoverwaltung und geprüfte
   Fallzugangsverwaltung sind inzwischen umgesetzt. Massgeblich sind die
   [aktuelle Umsetzungsmatrix](../use_cases/umsetzungsstand-2026-11-16.md) und der
   [Konten-Prüfbericht](../evaluation/konten-und-zugriffe-2026-11-16.md).
3. Die interaktive KI-Formulierung ist inzwischen technisch implementiert und geprüft;
   siehe die Fortsetzung unten. Ihre Modellqualität ist nicht fachlich abgenommen.
   Manuelle Nachrichten und eine vor dem Abschluss selbst verfasste Mitteilung funktionieren
   ohne KI; Versand und Abschluss sind zwei bewusste Mitarbeiteraktionen.
4. Ein End-to-End-Ausfalltest für jede KI-Funktion und alle konkurrierenden Wiederholungen
   bleibt von der kleinen Dev-Abnahme zu unterscheiden. Die verbindlichen Kriterien
   stehen unter KI-AK-01 bis KI-AK-08.
5. Keine Modellevaluation starten und `AutomaticClosureRelease` nicht freischalten.

Bestehende Entwicklungsdaten, private Schlüssel und fremde Änderungen erhalten.
Die Testinfrastruktur auf 5434/8085/8027 bleibt getrennt. Keine Datenbankbereinigung
oder Tests mit `TRUNCATE` gegen den Entwicklungs-Port 5433 ausführen.


## Fortsetzung: Konten und Zugriffe abgeschlossen

Am 16.11.2026 wurden die vier beauftragten Konsolidierungsschritte umgesetzt:
aktuelle Matrix aller 29 Use Cases, persistente Mieterkonten mit «Meine Fälle»,
Mitarbeiterkontoverwaltung/Zugriffsentzug sowie geprüfte Link- und Adressverwaltung.
Die Dev-Anmeldeseite zeigt die tatsächlich eingerichtete Mitarbeiteradresse und
Mailpit. Die gemeldete Mitarbeiteranmeldung wurde technisch erfolgreich geprüft;
eine konkrete Ursache des ursprünglichen Benutzerfehlers war nicht reproduzierbar.

Nachweise: 130 erfolgreiche Java-/PostgreSQL-/Camunda-Regressionstests; nach letzten
kleinen Anpassungen zusätzlich 53 erfolgreiche Tests im Paketbau. Kontenbrowser,
bestehender Dialog bis Operate und Neuanmeldung nach vollständigem Neustart bestanden.
20 Fälle, 4 Mieterkonten, 3 Mitarbeiterkonten, 6 Mieter-/7 Mitarbeiternachrichten
und 113 Mails vor/nach Neustart identisch; auch Inhalts-/Zugriffs-/Prozessprüfsummen.
Health UP, 0 Incidents. Die laufende Anwendung enthält den finalen Paketbau.

Aktuelle Quellen:
- [Umsetzungsmatrix](../use_cases/umsetzungsstand-2026-11-16.md)
- [Prüfbericht](../evaluation/konten-und-zugriffe-2026-11-16.md)
- [Annahmen](../architecture/konten-und-zugangsverwaltung-2026-11-16.md)

Migrationen V6–V8 sind additiv angewendet, V1–V5 unverändert; nächste Version V9.
Kein Reset, Commit oder Push. Automatischer Abschluss weiterhin fest gesperrt;
keine Modellevaluation gestartet. Vollständige MCP-/Mail-Eingang-/KI-Formulierung
und Betriebs-/Ausfallabnahme bleiben gemäss Matrix offen.

## Fortsetzung: Formulierung, Betriebsübersicht, BPMN und CI

Die integrierte KI-Formulierung ist technisch umgesetzt: Spring-AI-Streaming,
begrenzter Kontext nur aus dem eingegebenen Text, explizite Übernahme, Abbruch,
Sitzungs-/CSRF-/Mitarbeiterprüfung, Gesamtlaufzeit und manueller Ersatzbetrieb.
Keine Entwürfe werden gespeichert. Ohne Modellanbieter bleibt die normale
Nachrichtenbearbeitung nutzbar. Es wurde kein realer Anbieter aktiviert oder evaluiert.

Die neue Mitarbeiterseite `/mitarbeiter/betrieb` zeigt auffällige persistente
Aufträge lesend an. Sie löst weder Wiederholung noch Statuswechsel aus. Eine
auditierte Klärung und Wiederaufnahme ungewisser Aufträge bleibt gesonderte Arbeit.

Alle sieben ausführbaren BPMN-Dateien sind nach `src/main/resources/bpmn`
verschoben. Deployment und Tests nutzen dieselben Dateien aus Ressourcen bzw. JAR.
IDs, Abläufe und Layout bleiben erhalten; nur das bisher beim Deployment gesetzte
Ausführbarkeitsattribut ist nun direkt in der Quelle festgelegt. Keine zweite
Modellkopie unter `docs`.

Der portable Runner `node src/test/browser/run-integrated-regression.mjs` und der
GitHub-Job `integrated-browsers` verwenden echte, getrennte PostgreSQL-/Camunda-/
Mailpit-Dienste. Der lokale Lauf bestand am 16.11.2026 mit Mieter-/Mitarbeiterdialog,
KI-Ausfall mit manueller Fortsetzung, sichtbarem Operate-Diagramm, Konten,
Zugriffswechseln und Neuanmeldung nach Backendneustart. Der Runner beendete danach
seine eigene Anwendung und Testcontainer. Kein Remote-Pipeline-Lauf wurde behauptet
oder durch Push ausgelöst. Die damals noch verbliebenen Frontend-Demo-Regressionen
wurden im anschliessenden Auftrag auf das integrierte Backend übertragen; siehe
[Ablösung und aktuelle Nachweise](../architecture/frontend-demo-ablosung-2026-11-16.md).

Abschliessende normale Java-23/Linux-Regression: 448 ausgeführte Tests bestanden,
282 umgebungsabhängige Tests ohne Freigabevariablen übersprungen, keine Fehler.
Weitere Nachweise: 106 erfolgreiche gezielte Java-/PostgreSQL-/Camunda-Prüfungen,
18 Formulierungs-/Nachrichten-/Memo-Browserprüfungen, vier Prüfungsaufgaben-
Browserprüfungen und sechs JavaScript-Tests. Die Testmengen werden nicht als
überschneidungsfreie Gesamtzahl addiert. Workflow-Lint und Ressourcenvergleich bestanden.

Behoben wurden eine ignorierte Camunda-Eigenschaft (`base-url` statt `rest-address`)
und das UI-Zeitfenster zwischen gespeichertem Prüfstatus und verfügbarer Aufgabe.
Die beiden anfangs fehlgeleiteten eigenen Testinstanzen wurden gezielt beendet;
keine Entwicklungsfälle oder Datenbanken wurden gelöscht. Der Devstack läuft mit
dem finalen JAR: Health UP, Operate/Mailpit erreichbar, null aktive Dev-Incidents,
20 Fälle, 4 Mieterkonten und 3 Mitarbeiterkonten. Migrationen unverändert V1–V8.

MCP wurde auf Benutzerwunsch aus diesem Arbeitsumfang genommen. Es bleiben keine
experimentellen MCP-Codeänderungen. Modellevaluation weiterhin zurückgestellt,
automatische Abschlüsse durch `AutomaticClosureRelease` weiterhin fest gesperrt.
Kein Commit, Push oder Datenbankreset.

Aktuelle Einzelheiten und Nachweise:

- [Formulierung und Annahmen](../architecture/ki-formulierung-2026-11-16.md)
- [Betrieb, BPMN, CI und Demo-Ablösung](../architecture/betriebsuebersicht-und-ci-2026-11-16.md)
- [Prüfbericht](../evaluation/ki-formulierung-2026-11-16.md)
- [Testbefehle](../../src/test/README.md#isolierte-integrierte-browserprüfung-für-ci)


## Folgeauftrag: Frontend-Demo-Tests auf das Backend übertragen

Die Prüffamilien für Anmeldung/SMTP, Aufnahme, Konflikte, Nachrichten, Listen,
Sitzungsablauf, Bedienung und Barrierefreiheit verwenden jetzt Backend-Dienste,
PostgreSQL und den isolierten Camunda-/Mailpit-Stack. 55 abgelöste Dateien wurden
entfernt. Das Profil `frontend-demo` und seine noch benötigten In-Memory-Klassen
bleiben ausschliesslich wegen des separat zurückgestellten MCP-Prototyps erhalten.

Die Migration schliesst Zugriffssperren bei verlorenen Sitzungen, Textverlustwarnung,
Listenaktualisierung und Fokus, mobile Überläufe sowie PostgreSQL-Sortierungslücken.
Der GitHub-Workflow verwendet den portablen integrierten Runner für Chromium,
Firefox und WebKit und schliesst den MCP-HTTP-Test ausdrücklich aus.

Annahmen, Abdeckungszuordnung, ausgeführte Tests und die Abgrenzung zur noch nicht
remote ausgeführten CI stehen in der [Übertragungsmatrix](../architecture/frontend-demo-ablosung-2026-11-16.md).
Modellevaluation bleibt zurückgestellt, automatische Abschlüsse bleiben fest gesperrt.
Entwicklungsdatenbank und angewandte Migrationen bleiben erhalten. Kein Commit oder Push.

Der Devstack läuft wieder mit dem bereinigten JAR. Beim Wiederanlauf waren Health,
Operate und Mailpit erreichbar und alle 20 bestehenden Entwicklungsfälle vorhanden;
`-SkipSeed` hat zusätzliche Beispielanlagen verhindert.

Abschluss der Verifikation: Java-Gesamtlauf unter Linux/Java 23 mit 542 bestandenen
und fünf bewusst deaktivierten Modellprüfungen; 36 gezielte Nachtests bestanden
unter Windows und Linux, danach sieben erweiterte HTTP-Zugriffstests erneut bestanden.
Je 68 Backend-JavaScript-Prüfungen in Chromium, Firefox und WebKit bestanden.
Die 19 integrierten Szenarien je Browser sind bestätigt; WebKit wurde nach
Synchronisierung der Logout-Prüfung gezielt in Teilprüfungen abgeschlossen.
Lighthouse: zwölf gültige Desktop-Messungen, jeweils 100 Punkte. Die Pipeline führt
dieselben Tests aus; ein Remote-CI-Lauf wurde nicht ausgelöst.
