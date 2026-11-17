# Integrierter Devbetrieb ohne KI – Prüfung vom 16.11.2026

## Ziel und Umgebung

Lokales PostgreSQL-Backend mit tatsächlichen Camunda-Prozessen und Mailpit,
manueller Mitarbeiterbearbeitung und persönlichen Mieter-Links. KI-Anbindung
deaktiviert; Modellevaluation nicht ausgeführt. `AutomaticClosureRelease.approved`
bleibt unverändert `false`.

PropertyFlow 8081; Camunda 8.9.19 auf 8080/26500 mit eigener PostgreSQL-Datenbank;
PropertyFlow-PostgreSQL 16 auf 5433; Mailpit 1.21.8 auf 1025/8025 mit dauerhaftem
SQLite-Volume. Ausschliesslich lokale Schnittstellen. Die isolierten Testdienste
auf 5434/8085/8027 wurden nicht für die Devfälle verwendet.

## Durchgeführt

| Prüfung | Ergebnis |
|---|---|
| Sieben kanonische Modelle gemeinsam deployen | Erfolgreich; unveränderte erneute Deployments behalten die Definitionsschlüssel |
| Fall ohne KI erfassen und führende Instanz bestätigen | PostgreSQL-Fall und Camunda-Prozess entstehen; Mitarbeiterprüfung nach Analysefehler |
| Sieben Beispiele durch echte HTTP-Aktionen führen | Prüfung, Bearbeitung, externes Warten, Wiedervorlage, Mieterwarten, Abschluss, Wiedereröffnung erreicht |
| Individuelle manuelle Mitteilung | Vor dem Abschluss als selbst formulierter externer Text veröffentlicht und gespeichert |
| Mitarbeiteranmeldung im Browser | Einmalcode aus lokalem Mailpit, echtes persistentes Mitarbeiterkonto |
| Browserdialog | Mieterfall → Mitarbeiterprüfung → Fortsetzen → Rückfrage → Mieterantwort → erneute Mitarbeiterprüfung ohne KI bestanden |
| Operate-Verknüpfung | Vom Mitarbeiterdetail zur tatsächlichen führenden Instanz; aktiver Mitarbeiterknoten im geöffneten Teilprozess nachgewiesen |
| Browser-JavaScript-Fehler | Keine im bestandenen Durchlauf |
| Zugriffstests Prozesslink | 4 Tests bestanden: Gast abweisen, Nicht-Mitarbeiter abweisen, aktuelle Instanz nach Wiedereröffnung, ausstehender Start ohne erfundenen Link |
| Vollständiger Neustart | Backend, beide Dev-PostgreSQL-Dienste, Camunda und Mailpit gestoppt und erneut gestartet |
| Datenvergleich nach Neustart | 10 Fälle, 1 Konto, 3 Mieternachrichten, 6 Mitarbeiternachrichten und 62 Mails unverändert; Prüfsummen der Fallzustände/Versionen und Prozesszuordnungen identisch |
| Zustand nach Neustart | Backend-Health `UP`, Devcontainer gesund, 0 Camunda-Incidents |
| Erneuter Seed-Aufruf | Keine neuen Beispiele, kein Zurücksetzen vorhandener Fälle |

Der erfolgreiche vollständige Browserlauf dauerte rund 57 Sekunden. Zwei vorherige
Testläufe scheiterten erst bei der Operate-Navigation: Zuerst wurde ein Knoten im
noch eingeklappten Teilprozess gesucht, danach wurde die Diagrammseite vor Abschluss
der Anmeldung geöffnet. Der Test wartet nun auf die Anmeldung und öffnet den
Teilprozess ausdrücklich. Die Mieter-/Mitarbeiterdialoge dieser Läufe blieben als
synthetische Fälle erhalten. Nach dem Neustart wurde zusätzlich die Diagrammansicht
mit «Reset diagram zoom» visuell geprüft.

Beim ersten Stop-Skript wurde eine automatische JSON-Datumskonvertierung erkannt;
die Prozessidentität verwendet jetzt exakt gespeicherte UTC-Ticks. Der vollständige
Stop-/Startnachweis erfolgte anschliessend mit dieser Korrektur. Ein Paketbau bei
noch laufender Java-Anwendung scheiterte an der Windows-JAR-Sperre; nach dem
kontrollierten Stop bestand der Paketbau. Das Startskript verlangt deshalb bei
laufender Anwendung zuerst `stop.ps1 -ApplicationOnly` für einen Neubau.

## Reproduzierbare Nachweise

- [Startanleitung und Prüfschritte](../../dev/README.md)
- [Browser-Test](../../src/test/browser/dev-runtime.test.mjs)
- [Zugriffstests](../../src/test/java/com/pertegato/propertyflow/caseprocessing/controller/DevelopmentControllerTest.java)
- Lokale Ergebnisse: `target/dev-verification/browser-result.json`,
  `before-restart.json`, `after-restart.json`, `camunda-case.png`, `staff-case.png`,
  `tenant-case.png` sowie `target/surefire-reports/`.
- Private Seed-Zuordnung: `.propertyflow-local/dev-cases.json` (enthält persönliche
  Zugangslinks und wird deshalb nicht versioniert).

## Grenzen

Das ist eine Dev-Integrationsprüfung, keine vollständige Produktionsabnahme oder
fachliche Modellevaluation. Die acht [KI-Fallback-Kriterien](../specifications/ki-tools.md)
umfassen zusätzliche gezielte Provider-, RAG-, Konkurrenz- und Ausfallprüfungen.
Die freiwillige Mieterkonto-Selbstregistrierung und interaktive KI-Formulierung
sind weiterhin nicht im integrierten Backend abgenommen. Der geprüfte manuelle
Abschluss verwendet bewusst zwei Aktionen: individuelle Nachricht senden, danach
Abschluss bestätigen. Es gibt keine neue automatische Freigabe.
