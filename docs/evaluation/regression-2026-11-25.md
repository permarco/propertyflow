# Findings und technische Regression vom 25.11.2026

## Abschluss und wichtigste Befunde

Die Prüfung wurde auf Wunsch des Nutzers beendet. Es werden keine weiteren
Testwiederholungen ausgeführt. **Ein vollständig grüner Gesamtlauf liegt nicht
vor.** Der Anwendungscode unter `src/main` wurde nicht geändert.

- **Offen, zuerst untersuchen:** Navigations-Timeout im Chromium-Kontentest beim
  Laden einer Mitarbeiterfalldetailseite. Ein ähnlicher Timeout trat zuvor im
  Firefox-Kernablauf auf; dessen Wiederholung bestand.
- **Offen, sporadisch:** Ein Camunda-Test scheiterte im Aufbau. Die gesamte
  betroffene Klasse bestand anschliessend unverändert mit 50/50 Tests.
- **Behoben:** Veralteter Browserselektor für «Mieterantwort erforderlich».
- **Eingegrenzt:** MCP-HTTP-Fehler im lokalen Windows-Kontext; dieselben Tests
  bestehen mit unverändertem Anwendungscode unter Linux.
- **Behoben, vom Arbeitslauf verursacht:** Ein Paketbau beeinträchtigte die
  laufende Entwicklungs-JAR. Wiederherstellung und gezielter Neustart sind
  abgeschlossen; der Gesamt-Health-Endpunkt meldet wieder `UP`.

Der letzte Firefox-Lauf wurde bewusst abgebrochen und wird nicht als bestanden
gezählt. Seine eigenen Prozesse und Compose-Container wurden entfernt; Port
8084 ist wieder frei. Die Entwicklungsanwendung auf Port 8081 läuft weiter.
Es wurde weder committed noch gepusht.

## Grundlage und Umfang

Geprüfte Quellbasis: `e6965e4640ff7fe5494c7ff9113db0e9e61a3753`, Branch
`CleanUp-and-fix-tests`. Der Rebase wurde vor dem Lauf übernommen. Die Java-Tests
verwenden ein frisch entpacktes Git-Archiv ohne alte Klassen oder Testmodelle.
Die Browserprüfung verwendet zusätzlich die unten beschriebene Selektorkorrektur.
Testanleitung, CI und Statusdokumentation wurden in diesem Arbeitsstand
aktualisiert; es gibt keine fachliche Freigabe oder Produktionsabnahme.

Die Browserläufe finden lokal unter Windows mit Node.js 22.23.5 und
Playwright 1.64.0 statt; die integrierte Testanwendung läuft mit Java 25.0.5.
Der vollständige Java-Lauf verwendet Linux und Java 23.0.2. Das sind lokale
Nachweise, kein neu gestarteter GitHub-Actions-Lauf.

## MCP-HTTP-Diagnose

Die zwei Fehler in `FallstandMcpHttpTest` sind im lokalen Windows-Kontext
reproduzierbar. Der Servlet-Endpunkt setzt vor und nach dem Flush eine Länge
von null ohne Transfer-Encoding. Ein unabhängiger Socketserver ohne Spring
bestätigt die Abweichung: Für `/mcp` wird eine korrekt gesendete leere
401-Antwort beim Empfang als unvollständiges `chunked` geliefert, für `/`
bleibt sie unverändert. Das betrifft auch GET und lässt sich mit einem
zweiten HTTP-Client reproduzieren. Die genaue lokale Zwischenkomponente ist
nicht identifiziert; ein Fehler in PropertyFlows Autorisierung ist dadurch
nicht belegt.

Alle Diagnoseänderungen am Anwendungscode wurden zurückgenommen. Dieselben
zwei Tests bestehen unter Linux mit Temurin 23.0.2, Maven 3.9.16 und den
unveränderten Projektabhängigkeiten: 2 Tests, 0 Fehler, 0 übersprungen.
Die Linux-CI schliesst den Test deshalb nicht länger aus.

Der Test verwendet `frontend-demo`. Backend-MCP hat zusätzliche MockMvc-,
OAuth- und persistente Diensttests; daraus folgt keine vollständige
Backend-OAuth-Prüfung über HTTP oder ChatGPT-Workspace-Abnahme.

## Gesamtlauf

| Prüfung | Ergebnis |
| --- | --- |
| JavaScript | 2 bestanden |
| Stage-3-Browserfehlerfälle | Je 78 bestanden in Chromium, Firefox und WebKit; 234 insgesamt |
| Vollständiges Java-/PostgreSQL-/Camunda-`verify` | 617 Tests: 611 bestanden, 1 Fehler, 5 übersprungen |
| Gezielte Java-Wiederholung | `AutomaticQuestionCamundaTest`: 50 bestanden, unveränderter Code |
| Integriert / WebKit | 19 bestanden; einschliesslich Konten, Backendneustart und Sitzungsablauf |
| Integriert / Chromium | Kernablauf und 15 UI-Tests bestanden; Timeout in der anschliessenden Kontenprüfung |
| Integriert / Firefox | Wiederholung auf Nutzerwunsch abgebrochen: Kernablauf und 10 UI-Tests bis dahin bestanden; kein vollständiges Ergebnis |
| Lighthouse / Chromium | Vier Seiten, je drei Messungen: alle Performance-Scores 100 |
| Workflow-Syntax | `actionlint` ohne Befund |

Die Firefox-/WebKit-Laufzeiten fehlten zunächst lokal. Ihre Installation gelang
mit Nodes `--use-system-ca` und dem Windows-Zertifikatsspeicher, ohne die
TLS-Prüfung abzuschalten. Die danach gestarteten Stage-3-Läufe bestehen.

### Java-Erstlauf

Der vollständige Lauf endete nach 11:50 Minuten mit einem Fehler im Aufbau von
`AutomaticQuestionCamundaTest.partialAnswerToSystemQuestionKeepsRemainingQuestionWithoutDuplicatePublication`:
Der erwartete Job `propertyflow-register-automatic` wurde nicht bereit.
Die übrigen 49 Tests dieser Klasse bestanden. Die isolierte Wiederholung der
gesamten Klasse bestand anschliessend mit 50/50 Tests ohne Codeänderung. Die
Ursache des sporadischen Fehlers ist nicht abschliessend geklärt; eine gelungene
Wiederholung macht den Erstlauf nicht nachträglich grün. Es wurden weder
Prüfungen entfernt noch Wartezeiten erhöht.

Bewusst nicht aktiviert wurden vier reale Abschluss-/Abschlussnachricht-
Modellevaluationen (`PROPERTYFLOW_RUN_CLOSURE_EVALUATION`) sowie ein Live-Ollama-
Test (`ollama.live`). PostgreSQL- und Camunda-Tests waren aktiviert.

### Gefundener veralteter Browserselektor

`dev-runtime.test.mjs` suchte den Antwortbedarf-Schalter als Kind von
`#staff-message-form`. Im aktuellen Template steht er ausserhalb und ist
korrekt über `form="staff-message-form"` zugeordnet. Der Test verwendet nun
Rolle und sichtbare Beschriftung des Schalters. Anwendungscode und
Formularvertrag wurden dafür nicht verändert.

### Browser-Navigationsfehler

Der erste Firefox-Lauf mit korrigiertem Selektor scheiterte beim Neuladen der
Mitarbeiterdetailseite nach einer Mieterantwort (`dev-runtime.test.mjs`). Die
Wiederholung dieses Kernablaufs bestand mit unveränderten fachlichen Prüfungen;
zusätzliches Diagnoselogging blieb auf das ignorierte Testarbeitsverzeichnis
beschränkt.

Im Chromium-Wiederholungslauf bestanden der Kernablauf, alle 15 UI-Tests und
alle zwölf Lighthouse-Messungen. Anschliessend scheiterte `dev-accounts.test.mjs`
beim Laden der Mitarbeiterdetailseite nach der Anmeldung (`page.goto`, 30 s,
Zeile 89). Dadurch wurden die nachfolgenden Chromium-Prüfungen für Backendneustart
und Sitzungsablauf in diesem Lauf nicht mehr ausgeführt. Die Ursache des
Navigations-Timeouts ist noch nicht geklärt; die Timeoutgrenzen und Assertions
wurden nicht abgeschwächt.

Die Firefox-Wiederholung wurde während der weiteren UI-Prüfung auf ausdrücklichen
Nutzerwunsch beendet. Bis dahin waren der Kernablauf sowie 10 UI-Tests bestanden;
für den übrigen Lauf liegt kein abschliessendes Ergebnis vor. Der Abbruch ist
kein zusätzlicher fachlicher Testfehler. Die initialen fehlgeschlagenen Läufe
bleiben als Nachweise erhalten.

WebKit führte alle Phasen erfolgreich aus. Die zwei dort angezeigten Skips
in `dev-accounts.test.mjs` sind die gegenseitigen Phasenfilter: Kontenanlage vor
Neustart und Erhalt der Konten nach Neustart wurden jeweils tatsächlich geprüft.

Die Java-Integration verwendet `propertyflow_test` auf Port 5434, Camunda auf
8085 und Mailpit auf 1027/8027. Der Browser-Runner erstellt eigene temporäre
Compose-Projekte mit `propertyflow_e2e` auf 5440, Anwendung auf 8084, Camunda
auf 8086 und Mailpit auf 1028/8028 und entfernt sie nach jedem Lauf. Die
Entwicklungsdatenbank und laufenden Entwicklungsfälle gehören nicht zum Testziel.

Lokale Berichte: `target/regression-2026-11-25/`. Zugangsdaten, Browsersitzungen
und persönliche Fall-Links gehören nicht in die versionierte Dokumentation.

## Lokaler Build und Entwicklungsbetrieb

Ein Paketbau im Hauptcheckout kollidierte mit der dort bereits laufenden
Entwicklungs-JAR. Die Datei wurde zunächst durch den Maven-Paketbau verändert;
die laufende JVM meldete danach Klassenladefehler, und der Gesamt-Health-
Endpunkt antwortete nicht mehr. Das vollständige ausführbare JAR aus dem
isolierten Build derselben Quellbasis wurde wiederhergestellt und per SHA-256
verglichen. Anschliessend wurde ausschliesslich der zugehörige Java-Prozess
auf Port 8081 mit denselben Argumenten, der bestehenden lokalen Konfiguration
und den vorhandenen Daten neu gestartet. Der Gesamt-Health-Endpunkt meldet
wieder `UP`, das neue Fehlerprotokoll ist leer.

Die Testläufe selbst verwenden das getrennte Archiv beziehungsweise
Browser-Arbeitsverzeichnis. Weitere Paketbauten dürfen die JAR einer laufenden
Entwicklungsanwendung nicht überschreiben; dafür vorher wie in `dev/start.ps1`
vorgesehen stoppen oder vollständig ausserhalb ihres Buildverzeichnisses bauen.

## Empfohlene nächste gezielte Untersuchung

1. **Navigations-Timeout:** Zunächst nur den Kontenablauf aus
   `src/test/browser/dev-accounts.test.mjs` in der isolierten Testumgebung
   reproduzieren. Playwright-Trace und Netzwerkereignisse erfassen und die
   konkrete `GET /mitarbeiter/faelle/{id}`-Anfrage mit Serverantwortzeit und
   Datenbankzustand korrelieren. Feststellen, ob bereits das HTML oder eine
   nachgeladene Ressource auf sich warten lässt. Der bisherige Befund belegt
   noch keine bestimmte Ursache im Backend oder Browser. Kein weiterer
   Gesamtlauf und keine pauschale Timeout-Erhöhung als Ersatz für diese Diagnose.
2. **Camunda-Testaufbau:** Für den fehlenden Registrierungsjob bei einem erneuten
   Fehler Prozessinstanzen, aktive Jobs und Incidents sichern, bevor die
   Testbereinigung läuft. Mit einer eigenen frischen Test-Engine prüfen, ob
   Testisolation oder verzögerte Verarbeitung beteiligt ist. Die erfolgreiche
   Klassenwiederholung allein klärt diese Ursache nicht.
3. Erst nach einer belegten Korrektur den betroffenen Ablauf prüfen; ein neuer
   Gesamtlauf ist anschliessend der Abschlussnachweis.

## Änderungen dieses Arbeitsstands

- `src/test/browser/dev-runtime.test.mjs`: semantischer Selektor für den
  Antwortbedarf-Schalter; keine Abschwächung der fachlichen Assertions.
- `.github/workflows/tests.yml`: `FallstandMcpHttpTest` wieder in der Linux-CI;
  Workflow-Syntax mit `actionlint` geprüft. Es wurde kein neuer GitHub-Actions-
  Lauf ausgelöst.
- `src/test/README.md`, `dev/README.md`, `docs/project-context.md`,
  `docs/use_cases/umsetzungsstand-2026-11-16.md` und
  `docs/abgabe/block-3-services.md`: Testhinweise und Implementierungsstand
  abgeglichen, insbesondere Backend-MCP, acht BPMN-Modelle, Migrationen und
  weiterhin offene fachliche Freigaben.
- Dieser Bericht enthält die Ergebnisse und Grenzen. Architekturdateien und
  Anwendungscode bleiben unverändert. Temporäre Diagnosen und Rohberichte
  liegen ausschliesslich im ignorierten `target/regression-2026-11-25/`.

## Verbleibende Freigaben

Automatischer Abschluss, Modellqualität, vollständige OpenAPI-Parität,
UC-027 als eigenständiger kritischer Entscheidungsablauf, der Nachweis im
ChatGPT-Workspace und die betriebliche Gesamtabnahme bleiben separate Aufgaben.
Ein grüner technischer Testlauf erteilt diese Freigaben nicht.
