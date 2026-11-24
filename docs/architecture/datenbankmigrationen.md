# Datenbankmigrationen nach Modulen

Stand: 16.11.2026 nach ausdrücklich freigegebenem Hard Reset ohne Sicherung.
Diese historische Freigabe galt für den abgeschlossenen Reset. Im aktuellen
Konsolidierungsauftrag bleiben bestehende Entwicklungsdaten erhalten; es gibt
keine fortdauernde Freigabe für weitere Resets.

## Initialdateien

Flyway durchsucht `src/main/resources/db/backend-migration/` rekursiv.
Alle Module verwenden eine gemeinsame Versionsfolge und Historientabelle.

| Modul | Initialdatei unter `db/backend-migration/` | Aktueller Inhalt |
|---|---|---|
| `access` | `access/V1__initial_schema.sql` | Mitarbeiterkonto und einmalige Einrichtung; zwei Tabellen, einschliesslich Einrichtungsdatensatz |
| `caseprocessing` | `caseprocessing/V2__initial_schema.sql` | 47 Tabellen für Fälle, Kommunikation, fachliche Ergebnisse, Prozessaufträge und Audit |
| `intake` | `intake/V3__initial_schema.sql` | Dokumentiert den Stand ohne eigene Tabellen; Fallanlage und Idempotenz liegen in `caseprocessing` |
| `triage` | `triage/V4__initial_schema.sql` | Dokumentiert den Stand ohne eigene Tabellen; fallbezogene Analyseergebnisse werden durch `caseprocessing` gespeichert |
| `recommendation` | `recommendation/V5__initial_schema.sql` | Dokumentiert den Stand ohne eigene Tabellen; Wissen und Regeln kommen aus Dateien, fallbezogene Ergebnisse gehören zu `caseprocessing` |

Die letzten drei Dateien enthalten bewusst nur SQL-Kommentare. Es werden keine
Tabellen auf Vorrat angelegt und keine fremden Repositories einem Modul zugeordnet.
Insbesondere gehören `pf_case_analysis`, `pf_conversation_evaluation` und die
Abschlussnachweise weiterhin zur fachlichen Fallpersistenz.

## Ein einziger Migrationsweg

Neue Datenbanken werden aus den fünf Moduldateien und allen nachfolgenden
Migrationen im Ordner `updates/` aufgebaut.
Die ursprünglichen 38 Legacy-Skripte, ihre automatische Auswahl und das vorbereitete
Werkzeug zur Historienumstellung sind entfernt. Es gibt weder automatische
Reparatur noch Baseline-Umschreibung beim Anwendungsstart.

Ein Altbestand mit der früheren Historie wird nicht mehr unterstützt. Er benötigt
den bewusst autorisierten Neuaufbau. Angewendete Moduldateien bleiben unverändert;
weitere Schemaänderungen kommen als neue Migration hinzu.

## Weitere Schemaänderungen

Die Migrationen V6 bis V12 ergänzen Kontenbetrieb, MCP-Zugang und Prozessverträge:

- V6: Mieterkonten, Mitarbeiterrechte, Konto-Audit und Zuordnung von Kontonachrichten.
- V7: Kontaktprüfnachweise, ursprüngliche Kontaktadresse und Ersatzlink-Versandaufträge.
- V8: bestätigte Prüfnachweise, getrenntes Zugriffs-Audit und idempotente Kontobefehle.
- V9: Freigaben und widerrufbare Tokens für den integrierten Authorization Server.
- V10 und V11: MCP-Bestätigungen, Audit und Fallaktionen.
- V12: Gespeicherte Mitarbeiterentscheide und Nachrichtenaufträge auf die aktuellen BPMN-Aufgaben-IDs umstellen.

Die nächste freie Version ist **V13** im Ordner `db/backend-migration/updates/`.
Versionsnummern bleiben backendweit eindeutig. Angewendete Migrationen werden nicht verändert.
Hibernate/JPA darf das Schema nicht mit `create` oder `update` verändern.

## Lokale Umgebungen

| Zweck | PostgreSQL | Datenbank | Lebensdauer |
|---|---|---|---|
| Entwicklungs-Backend | `127.0.0.1:5433` | `propertyflow` | Eigenes Compose-Projekt `propertyflow-backend` mit persistentem Volume |
| Integrationstests | `127.0.0.1:5434` | `propertyflow_test` | Eigenes Compose-Projekt `propertyflow-backend-test`, tmpfs |

Die lokale Entwicklungsverbindung und der Link-Schlüssel stehen in der nicht
versionierten `.propertyflow-local/backend.properties`. Das Entwicklungs-Backend
wurde frisch eingerichtet, da zuvor kein zugehöriger PostgreSQL-Container und
keine Backend-Verbindung vorhanden waren. Der nicht zugeordnete lokale PostgreSQL-
Dienst auf Port 5432 und die separate H2-/Frontend-Demo sind keine Reset-Ziele.

Die identifizierte Testdatenbank wurde gemeinsam mit der zugehörigen Camunda-
Testinstanz auf Port 8085 und dem Test-Mailpit auf 1027/8027 zurückgesetzt.
So laufen keine alten Testprozesse gegen gelöschte Fälle weiter. Die fachliche
Sperre für automatische Abschlüsse bleibt unverändert; ein leerer Datenbestand
ist keine fachliche Evaluationsfreigabe.

## Prüfung und erneuter Testreset

`BackendMigrationPostgresTest` prüft den vollständigen Aufbau von 58 Tabellen,
die fünf Modulmigrationen und sieben Updates, den initialen Einrichtungsdatensatz sowie einen zweiten
Start ohne weitere Migrationen. Die PostgreSQL-/Camunda-Regression prüft die
fachlichen Abläufe auf dem neu aufgebauten Schema.

Ein erneuter Reset löscht Testdaten und Prozesszustände unwiderruflich. Die folgenden
Befehle gehören ausschliesslich zu den genannten wegwerfbaren Testprojekten:

```powershell
docker compose -f dev/compose.workflow-test.yaml stop camunda
docker compose -f dev/compose.backend-test.yaml up -d --force-recreate postgres
docker compose -f dev/compose.workflow-test.yaml up -d --force-recreate
```

Vor den Tests die Bereitschaft beider Dienste abwarten. PostgreSQL-Integrationstests
laufen nacheinander und ausschliesslich gegen `propertyflow_test` auf Port 5434.
Die Entwicklungsdatenbank ist kein Ziel destruktiver Integrationstests.
