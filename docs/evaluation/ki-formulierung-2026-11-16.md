# Technische Prüfung: Formulierung und Konsolidierung

Stand: 16.11.2026. Keine fachliche Modellevaluation und keine Abschlussfreigabe.

## Ausgeführte Prüfungen

- Java-Dienst/HTTP/Adapter: 26 erfolgreiche Tests für Formulierung. Mitarbeiter-
  und CSRF-Prüfung, fremde Sitzung, verfrühter Abbruch, Wiederholungs-/Parallelitätsschutz,
  Abmeldung, Gesamtlaufzeit, Providerfehler, Leer-/Überlänge und ungespeicherte Ausgabe.
- Ein echter Spring-AI-Adapter gegen einen lokalen HTTP-Protokollstub liefert
  Teile vor dem Streamende. Der übertragene Kontext enthält nur Systemanweisung
  und bewusst eingegebenen Text; keine Tools oder gespeicherten Fallinhalte.
- Browser: 18 erfolgreiche Prüfungen der Formulierung sowie Nachrichten-/Memo-
  Regression. Einschliesslich sicherer Textdarstellung, bewusster Übernahme,
  Bearbeitung während Streaming, Abbruch, manueller Versand nach Fehler,
  Speicherungssperren, Kontowechsel, fragmentiertem UTF-8 und Fallabschluss.
- Erste breite Regression: 156 erfolgreiche Tests ohne Fehler/Skip, darunter
  PostgreSQL, Kontoabläufe, Mieterkommunikation, vollständige Camunda-Modelle und
  technische Abschlusssperre (`target/formulation-regression.log`).
- Nach Betriebsübersicht, BPMN-Verlagerung und konfigurierbarem Operate-Link:
  Paketbau mit 106 erfolgreichen Tests, ohne Fehler/Skip
  (`target/consolidation-package.log`). Diese Testmengen überschneiden sich und
  werden nicht als 262 verschiedene Tests addiert.
- Die Betriebsabfrage wurde in einem isolierten PostgreSQL-Schema mit Fehler-,
  Ungewissheits-, Warte-, Erfolgs- und Mengenfällen geprüft. Rechteentzug und
  Datenbankfehler sind über HTTP geprüft; Lesezugriff verändert keine Aufträge.
- Alle sieben BPMN-Dateien sind XML-lesbar, gegenüber den bisherigen Quellen nur
  im Ausführbarkeitsattribut geändert und bytegleich im gebauten JAR enthalten.
  Unter `docs` liegen keine BPMN-Duplikate mehr. Vollständige Modelle und Mailmodell
  wurden aus dem Klassenpfad gegen Camunda getestet.

Der erste isolierte Browser-/CI-Durchlauf fand eine falsche Spring-Eigenschaft:
`camunda.client.base-url` wurde ignoriert. Konfiguration und Runner verwenden jetzt
`rest-address` und `grpc-address`; zwei Bindungstests prüfen abweichende Ports mit
und ohne Devprofil. Der anschliessende Paketbau bestand 52 gezielte Tests ohne
Fehler/Skip (`target/endpoint-regression.log`). Die versehentlich in der Dev-Engine
gestarteten zwei Testfallinstanzen wurden gezielt beendet; kein zugehöriger Fall
existierte in der Entwicklungsdatenbank. Keine Entwicklungsdaten wurden gelöscht.

Der nächste Lauf fand das Zeitfenster zwischen gespeichertem Prüfstatus und
sichtbarer Camunda-Aufgabe. Nachladen einer erstmals verfügbaren Aufgabe und
Erhaltung bereits ausgewählter Entscheidungen sind mit vier Browserprüfungen
abgesichert (`target/staff-review-regression.log`).

Die breite Windows-Suite fand ausserdem zwei veraltete Login-Ansichtenprüfungen:
Die inzwischen ausdrücklich als Mieterzugang beschrifteten Links sind zulässig;
eine Selbstregistrierung für Mitarbeitende bleibt ausgeschlossen. Die Erwartungen
wurden aktualisiert. Zwei MCP-HTTP-Tests zeigten das bereits
[dokumentierte Windows-Problem](../ai-usage/initial-workflow-pruefung-2026-11-15.md)
bei der Chunk-Rahmung. Experimentelle Transportänderungen wurden zurückgenommen;
es gibt keine neue MCP-Implementierung oder behauptete Behebung dieses lokalen Problems.

Weitere statische Prüfungen: GitHub-Workflow mit `actionlint` fehlerfrei, Workflow,
Compose und OpenAPI ohne doppelte YAML-Schlüssel lesbar, sechs JavaScript-Tests
erfolgreich. Alle relativen Dateilinks der bearbeiteten Markdown-Dokumente lösen
auf vorhandene Ziele auf. Der abschliessende Paketvergleich bestätigt erneut alle
sieben BPMN-Dateien bytegleich im JAR.

## Abschliessender integrierter Lauf

`target/consolidation-java23-regression.log`: **448 ausgeführte Tests bestanden**,
keine Fehler; 282 umgebungsabhängige Tests erwartungsgemäss übersprungen
(Maven: 730 insgesamt). Ausführung unter Linux/Java 23 mit den auf `release 23`
kompilierten Klassen. Die separaten PostgreSQL-/Camunda-Nachweise stehen oben;
die zurückgestellten Modellevaluationen wurden nicht aktiviert.

`target/integrated-regression-final.log`: erfolgreich beendet. Drei ausgeführte
Szenarien: vollständiger Dialog bis Operate und Betriebsübersicht, Konten/Rechte/
Link- und Adresswechsel sowie Neuanmeldung und persistente Fallzuordnung nach
Backendneustart. Die Kontendatei enthält jeweils einen durch den Laufmodus bewusst
übersprungenen Gegentest; beide Modi wurden nacheinander ausgeführt.

Die Berichte `browser-result.json`, `accounts-result.json` und
`account-restart-result.json` unter `target/integrated-e2e` dokumentieren die
synthetischen Fälle. Der Operate-Screenshot wurde visuell geprüft; die aktive
Mitarbeiterbearbeitung ist markiert. Keine Browser-Seitenfehler in den Berichten.
Die eigene Camunda-Testinstanz meldete null Incidents. Die eigenen Testcontainer
und die eigene Anwendung wurden anschliessend entfernt.

Die reguläre Entwicklung läuft mit dem finalen JAR auf 8081. Abschliessend:
Health UP, Operate auf 8080 und Mailpit auf 8025 erreichbar, null aktive Dev-Incidents.
Die bestehenden 20 Fälle, 4 Mieterkonten und 3 Mitarbeiterkonten sind vorhanden.
Der lokale Build verwendet Java 25 mit `release 23`; die separate Java-23/Linux-
Suite führt diese kompilierten Klassen aus. Die Remote-Pipeline baut selbst mit
JDK 23; deren tatsächlicher Lauf steht nach einem späteren Push noch aus.

MCP-Anforderungen und MCP-Fehler sind auf nachträglichen ausdrücklichen
Benutzerwunsch aus der Abnahme dieses Arbeitslaufs ausgeklammert.

## Abgrenzung

Der Timeout-Test fand zunächst eine nicht zuverlässig weitergereichte Kündigung
des Anbieter-Streams. Eine eigene Gesamtfrist beendet nun ausdrücklich die
Subscription; der erneute Test mit kontinuierlichen Teilantworten besteht.
Beim BPMN-Umzug fand der Test einen noch zusammengesetzten alten Dokumentpfad;
dieser Bindungstest wurde auf die ausführbaren Ressourcen umgestellt und erneut geprüft.

Keine neue Migration; V1–V8 und bestehende Entwicklungsdaten bleiben erhalten.
Die Formulierungshilfe speichert keine Eingaben oder Vorschläge. Die Betriebsübersicht
führt keine Wiederholungen aus. Modellqualität, produktiver Wissensbestand, MCP/OAuth,
E-Mail-Eingang, vollständiger kritischer Entscheidungsvertrag und auditierte
betriebliche Wiederaufnahme bleiben eigenständige offene Punkte.

[Formulierungsannahmen](../architecture/ki-formulierung-2026-11-16.md) ·
[Betrieb, BPMN und CI](../architecture/betriebsuebersicht-und-ci-2026-11-16.md) ·
[Umsetzungsmatrix](../use_cases/umsetzungsstand-2026-11-16.md)
