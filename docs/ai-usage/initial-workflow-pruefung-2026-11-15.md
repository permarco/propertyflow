# INITIAL-Workflow: Umsetzung und Prüfnachweis

Stand: 15.11.2026. Ausgangspunkt: Commit `c6cfb90`, Branch
`feat/block3-server-architektur-interfaces-http`. Die beschriebenen Änderungen
liegen im Arbeitsverzeichnis; kein Commit oder produktives Deployment.

## Auftrag und Umsetzung

Der Projektverantwortliche beauftragte nach Prüfung und Planung die selbständige
Umsetzung anhand der vorhandenen Spezifikationen. Der erste durchgängige Abschnitt
verwendet die persistente Gastanlage und ergänzt Startverarbeitung, führende
Registrierung, INITIAL-Call-Zuordnung und den Statuscommit «In Abklärung».
[Technische Beschreibung und verbleibender Umfang](../architecture/bpmn/initial-workflow-runtime.md).

Die KI erstellte Migration, Dienste, Camunda-Adapter, Tests und CI-Erweiterung.
Die verbindlichen Fachregeln wurden nicht durch eine neue Prozessvariante ersetzt.
Die Originalmodelle erhielten fünf Jobtyp-Bindungen; ihre bisherigen Elemente,
Attribute, Flüsse, Dokumentation und Diagrammkoordinaten wurden gegen Git-HEAD
verglichen und blieben erhalten. Die Gesamtmodelle bleiben nicht ausführbar.

## Ausgeführte Prüfungen

| Prüfung | Ergebnis und Grenze |
|---|---|
| Java-Kompilierung | Erfolgreich, Java-Release 23. Host-JDK 25. |
| Kombinierte Workflow-Abnahme | 37 Tests erfolgreich, keine Fehler oder übersprungenen Tests. PostgreSQL 16 und Camunda 8.9.19 in getrennten Testcontainern. |
| PostgreSQL | Zehn neue Workflowtests; daneben bestehende Gastanlage-, Mitarbeiterkonto- und Neustarttests. |
| Echte Camunda-Engine | Sechs Tests mit aktivierten Engine-Jobs: Haupt-/Kindregistrierung, verlorene Start-/Jobantworten, zwei gleichzeitige Starter und fremde Startidentität. |
| Automatischer Spring-Betrieb | Backendstart und Fallannahme bei nicht erreichbarer Engine; Anwendungsneustart, automatische Worker-Verarbeitung und Statusanzeige über den persönlichen HTTP-Link erfolgreich. |
| Java-Regression unter Linux/Java 23 | 346 erkannte Tests: 314 erfolgreich, 32 bedingte Integrationstests übersprungen. Die benötigten PostgreSQL-/Camunda-Prüfungen liefen separat im obigen Abnahmelauf. |
| CI-Datei | Actionlint erfolgreich. Remote-CI noch nicht ausgeführt; kein Push. |
| BPMN | XML-/Bindungsprüfung und Vergleich der unveränderten Modellstruktur und Layoutdaten erfolgreich. Keine erneute visuelle Modeler-Abnahme. |

Lokale Rohprotokolle liegen unter `target/`: `workflow-acceptance.log`,
`workflow-concurrency.log`, `linux-java-regression.log` und `linux-quality/`.
Diese Dateien sind Build-Artefakte und werden nicht versioniert.

## Fehlerfälle und Erkenntnisse

- Ein erfolgreicher Startaufruf erledigt keinen Startauftrag. Erst die
  Registrierung bestätigt die führende Zuordnung.
- Engine-Suche ist eventual consistent. Die aktivierte Business-ID-Eindeutigkeit
  schützt parallele Starts; die dauerhafte Backend-Zuordnung schützt vor
  späteren doppelten Wirkungen.
- Lease-Kennungen verhindern, dass eine verspätete Starter-Antwort eine neue
  Übernahme oder einen abgeschlossenen Registrierungscommit überschreibt.
- Der Kindprozess wird anhand echter Eltern-/Call-Metadaten geprüft.
- Status, Änderungsnachweis und Mailpflicht werden gemeinsam gespeichert.
  Ein gesperrter Link nimmt den Statuswechsel nicht zurück: Die Mailpflicht
  bleibt erhalten; der Versand muss später den aktuellen gültigen Link prüfen.
- In der Windows-Gesamtregression scheiterten zwei bestehende MCP-HTTP-Tests
  reproduzierbar an der HTTP-Chunk-Rahmung. Beide bestanden unverändert unter
  Linux mit Java 23. Experimentelle MCP-Änderungen wurden vollständig zurückgenommen.
  Der genaue Ursprung des Windows-Netzwerkverhaltens ist nicht abschliessend geklärt.

## Abnahmegrenze

Die Engine-Tests deployen ausdrücklich begrenzte INITIAL-Fixtures in die
Testengine. Sie belegen keine Ausführbarkeit des vollständigen Fachprozesses.
Triage/RAG, eigentlicher Fallmailversand, Nachrichten und Mitarbeiteraktionen,
Wiedervorlage, Wiedereröffnung sowie die allgemeinen B07/B09-Verträge sind damit
nicht implementiert oder abgenommen. Für die gesamte Anwendung ist weitere
Umsetzung nötig. Produktive Falldaten wurden nicht als Testdaten verwendet.
