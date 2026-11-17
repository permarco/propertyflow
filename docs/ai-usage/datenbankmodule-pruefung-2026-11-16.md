# Prüfung der modulweisen Datenbank-Initialskripte

Datum: 16.11.2026.

**Aktueller Stand nach neuer Benutzerentscheidung:** Hard Reset ohne Sicherung
freigegeben. Legacy-Kette, Umschaltlogik und Historienumstellungswerkzeug sind
entfernt. Die früheren Abschnitte unten dokumentieren verworfene Zwischenstände;
verbindlich sind die [aktuelle Migrationsstruktur](../architecture/datenbankmigrationen.md)
und die abschliessende Ergänzung zum Hard Reset.

## Auftrag und Umsetzung

Der ursprüngliche Auftrag zur Konsolidierung wurde auf eine Initialdatei pro
Modul präzisiert. Die vorhandene Tabellenzuständigkeit bleibt erhalten:
zwei Tabellen in `access`, 47 in `caseprocessing`, keine eigenen SQL-Tabellen
in `intake`, `triage` und `recommendation`. Deren Dateien dokumentieren diesen
Stand. Sie erzeugen keine neue Persistenz allein aufgrund des Modulnamens.

Die fünf neuen Initialdateien erzeugen den Endstand der bisherigen 38 Migrationen.
Diese bleiben bytegleich in `db/backend-legacy` erhalten. Der Backend-Start wählt
für bestehende Historien die ursprüngliche Kette; neue Datenbanken verwenden die
Moduldateien. Weitere Änderungen beginnen gemeinsam bei V39.

Details und Annahmen stehen unter
[Datenbankmigrationen](../architecture/datenbankmigrationen.md) und
[Implementierungsannahmen](../architecture/implementierungsannahmen-2026-11-15.md).

## Geprüfte Korrektur

Der erste Schemaabgleich erkannte eine unterschiedliche interne Darstellung von
CHECK-Ausdrücken: PostgreSQL interpretiert explizite Array-Casts aus einem
Schema-Dump beim erneuten Einlesen anders als die ursprünglichen `IN`-Ausdrücke.
Die Initialdatei verwendet deshalb wieder lesbare `IN`-/`NOT IN`-Ausdrücke.
Der Vergleich wurde nicht gelockert; die resultierenden Katalogdefinitionen
stimmen einschliesslich der Constraints überein. Fehlermeldungen des Tests
zeigen einzelne abweichende Schemaobjekte statt des gesamten Schemas.

## Ausgeführte Prüfungen

- `BackendMigrationPostgresTest`: vier Tests bestanden, keine Fehler und keine
  übersprungenen Tests. Verglichen werden alle Spalten, Defaults, Constraints,
  Fremdschlüssel und Indizes beider Wege sowie der Einrichtungsdatensatz.
- Ein künstlich befüllter V2-Bestand erhält seine restlichen 36 Migrationen.
  Konto, Fallursprung, Zugang, Startauftrag und die bestehenden Historieneinträge
  bleiben erhalten. Ein zweiter Lauf führt keine Migration mehr aus.
- Eine absichtlich falsche historische Prüfsumme wird abgelehnt. Die Anwendung
  repariert oder ersetzt weder Historie noch Fachdaten.
- SHA-256-Abgleich: alle 38 archivierten SQL-Dateien stimmen bytegenau mit dem
  vor der Aufteilung gesicherten Stand überein.
- Ausschliesslich der verifizierte PostgreSQL-Testcontainer des Compose-Projekts
  `propertyflow-backend-test` wurde neu erstellt. Er verwendet Port 5434 und tmpfs.
  Der anschliessende Spring-Boot-Start hat die fünf neuen Migrationen auf der
  leeren Testdatenbank angewendet; weitere Starts validieren Version 5.
- Vollständiger ausgewählter Backend-Regressionslauf nach dem Reset:
  **376 Tests bestanden, keine Fehler, keine übersprungenen Tests**. Enthalten sind
  alle `*PostgresTest`, `*CamundaTest`, Workflow-Bindungen, Projektionen und
  Fall-Webtests sowie die Fachtests für Antwortauswertung, Empfehlung,
  Abschlussbewertung und individuelle Abschlussmitteilung. Maven `package` war
  am 16.11.2026 um 08:50:34 CET erfolgreich (Dauer 7:25 Minuten, Java 25.0.5,
  Compiler-Ziel Java 23, PostgreSQL 16.16, isolierte Camunda-8.9.19-Umgebung).
- Das erzeugte JAR enthält genau fünf aktive Initialskripte und die 38
  unveränderten Legacy-Skripte sowie die Migrationsauswahl. Die SQL-Inhalte im
  Artefakt stimmen bytegleich mit den Quellen überein; alte aktive Root-Skripte
  sind nicht mehr enthalten.
- Das Docker-Volume-Inventar enthält vor und nach dem Testreset dieselben
  32 Volumes; auch die 13 Container-Namen sind unverändert. Neu erstellt wurde
  ausschliesslich der ausdrücklich gewählte tmpfs-PostgreSQL-Testcontainer.

Lokale Rohprotokolle und der gesicherte ursprüngliche Schema-Dump liegen unter
`target/schema-consolidation/` und sind nicht Bestandteil des Git-Repositories.
Die Migrationstests sind im PostgreSQL-/Camunda-CI-Job eingetragen; ein externer
CI-Lauf wurde hier nicht gestartet.

## Grenzen

Entwicklungsdatenbanken und persistente Entwicklungsvolumes wurden nicht
zurückgesetzt oder migriert. Der Datenerhalt beim nächsten regulären Start ist
mit synthetischen Bestandsdaten in der isolierten Testdatenbank geprüft; dies
ist keine Prüfung realer Entwicklungsdatensätze.

Die Konsolidierung ändert keine Fachregeln, keine BPMN-Freigaben und keine
Evaluationsentscheidung. Automatische Abschlüsse bleiben technisch gesperrt.

## Folgeauftrag: reale Bestandsumstellung und anschliessende Legacy-Entfernung

Der Benutzer hat die einmalige Umstellung der bestehenden Entwicklungsdatenbank
unter Erhalt aller Fachdaten ausdrücklich beauftragt. Dafür wurde ein
[Wartungswerkzeug](../../dev/database/README.md) umgesetzt. Es prüft die vollständige
V38-Historie anhand eines aus der originalen Kette gewonnenen Prüfsummenmanifests,
vergleicht das Schema, sperrt Schreibzugriffe, erstellt eine vollständige Sicherung
und restauriert sie in eine temporäre Datenbank. Die Historienumstellung wird zuerst
an dieser Kopie geprobt und erst danach am Original transaktional ausgeführt.

Die reale Ausführung ist hier blockiert: Es gibt keinen Backend-PostgreSQL-Container
auf Port 5433, keine `.propertyflow-local/backend.properties` und keine gesetzten
Backend-Verbindungsvariablen im Prozess-, Benutzer- oder Maschinenkontext.
Der lokale PostgreSQL-18-Dienst auf 5432 verlangt nicht verfügbare Zugangsdaten.
Die vorhandene IDE-Konfiguration verwendet H2. Keine Datenbank wurde durch eine
vermutete Verbindung oder eine neu angelegte leere Entwicklungsdatenbank ersetzt.
Der H2-Bestand und die laufende Anwendung wurden nicht verändert.

Ausgeführte Prüfungen des finalen Werkzeugs:

- **12 PostgreSQL-Migrationstests bestanden**, keine Fehler oder übersprungenen Tests
  (`BackendHistoryRebasePostgresTest` und `BackendMigrationPostgresTest`,
  16.11.2026, 09:10:10 CET). Geprüft sind Datenerhalt einschliesslich unveränderter
  Tabellen-/Indexidentitäten, gültige neue Historie, Wiederholung, falsche Prüfsummen,
  unvollständige Historie, Schemaabweichungen, geänderte Indexbedingungen, fehlgeschlagene
  Sicherung, Rollback bei unerwarteten Fachänderungen und gesperrte parallele Writes.
- Vollständiger Kommandozeilenlauf mit Java 23 und PostgreSQL-16-Werkzeugen gegen einen
  separat angelegten, synthetisch befüllten V38-Testbestand im isolierten Testserver.
  Sicherung und Wiederherstellung, Probeumstellung der Kopie und Umstellung des
  Testbestands erfolgreich. Anschliessend sind alle fünf Modulmigrationen gültig;
  `migrate` führt null weitere Migrationen aus. Der zweite Werkzeuglauf validiert
  nur noch den bereits umgestellten Stand. Abschluss um 09:10:42 CET.
- Temporäre Prüf- und Quelldatenbanken wurden wieder entfernt. Die verifizierte
  Sicherung dieses **synthetischen Testbestands**, nicht einer Entwicklungsdatenbank,
  liegt unter `.propertyflow-local/backups/backend-rebase-1122a23c-5856-4173-9b44-5b1ef128a360/`.
- Zwei durch die Probe erkannte Fälle sind berücksichtigt: unterschiedliche
  PostgreSQL-Hauptversionen der Werkzeuge werden abgelehnt; äquivalente Verschiebungen
  von VARCHAR-Literal-Casts durch `pg_dump` werden beim Vergleich von CHECK-Ausdrücken
  und Indexbedingungen vereinheitlicht. Werte und Prädikate werden nicht verändert.
  Ein Test bestätigt ausdrücklich, dass eine abweichende Indexbedingung weiter abbricht.

Die Legacy-Skripte und die bisherige Auswahl bleiben bis zur nachgewiesenen Umstellung
der tatsächlich verwendeten Entwicklungsdatenbank erhalten. Sie wurden nicht auf
Grundlage eines erfolgreichen synthetischen Tests entfernt. Das Werkzeug wird nicht
beim Backend-Start ausgeführt und ist nicht im Anwendungs-JAR enthalten.

Lokale Protokolle: `target/schema-consolidation/history-rebase-tests.log`,
`rebase-cli.log`, `rebase-cli-verify.log` und `rebase-cli-repeat.log`.


## Abschliessender Auftrag: Hard Reset ohne Sicherung

Der Benutzer hat den bisherigen Datenerhalt ausdrücklich aufgehoben: Die nicht
mehr benötigten Testdaten dürfen gelöscht werden; eine Sicherung ist nicht gewünscht.
Die oben dokumentierten Kompatibilitäts- und Umstellungsansätze sind deshalb
historische Zwischenstände und nicht mehr Bestandteil des Systems.

Umgesetzt:

- Die 38 Legacy-SQL-Dateien, `BackendMigrationConfiguration`, der manuelle
  Historienumsteller, sein Prüfsummenmanifest und die dazugehörigen Tests sind entfernt.
- Die fünf Modulinitialdateien bilden den einzigen Migrationsweg. Weitere Änderungen
  beginnen mit V6. CI, Entwicklungsanleitung, Architektur und Annahmen sind angepasst.
- Die eindeutig zugeordneten Testcontainer für PostgreSQL (5434), Camunda (8085/26505)
  und Test-Mailpit (1027/8027) wurden gemeinsam neu erstellt, ohne Sicherung.
- Das vorher fehlende Entwicklungs-Backend wurde auf Port 5433 mit eigener Datenbank
  `propertyflow` und privater lokaler Konfiguration frisch eingerichtet. Der lokale
  PostgreSQL-Dienst auf 5432 wurde nicht als PropertyFlow-Ziel angenommen oder verändert.
- Zwei echte Backend-Starts gegen diese Entwicklungsdatenbank waren erfolgreich:
  zuerst fünf Migrationen, danach nur Validierung ohne weitere Migration. Das
  Fallformular auf dem temporären Prüfport 8084 lieferte jeweils HTTP 200.
  Der Prüfprozess wurde anschliessend beendet. Die Datenbank enthält 49 Fachtabellen,
  keine Fälle und keine Mitarbeiterkonten; nur der erforderliche Einrichtungsdatensatz
  und die Flyway-Historie sind vorbefüllt.
- Frühere Sicherungen ausschliesslich synthetischer Historienumstellungsproben
  wurden anhand ihres archivierten Testdatenbanknamens identifiziert und entfernt.
  Es wurde für diesen Reset keine neue Sicherung erzeugt.

Die Sperre automatischer Abschlüsse bleibt unverändert aktiv. Der Hard Reset
ersetzt weder die fachliche Modellevaluation noch eine Freigabe des Gesamtprozesses.


Abschliessende Prüfung des Hard Resets: **373 Tests bestanden, keine Fehler und
keine übersprungenen Tests**, Maven `package` erfolgreich. Die Auswahl umfasst
PostgreSQL-, Camunda-, Workflow-Bindungs-, Projektions- und Fall-Webtests sowie
Antwortauswertung, Empfehlung, Abschlussbewertung und Abschlussmitteilung.
Nach diesem Lauf wurden die dabei erzeugten Testfälle und Prozesszustände erneut
verworfen. Die leere Testdatenbank wurde ausschliesslich mit den fünf Moduldateien
initialisiert und validiert. Das Entwicklungs-Backend bleibt ebenfalls ohne Fälle
und Konten. Rohprotokolle liegen unter `target/hard-reset/`.

Die abschliessende Kontrolle bestätigt jeweils fünf Flyway-Einträge und null
Fälle/Konten in beiden Backend-Datenbanken sowie null Prozessinstanzen in der
Camunda-Testumgebung. Sowohl das ausführbare JAR als auch das Maven-Originalartefakt
enthalten genau die fünf aktuellen Modulskripte und keine Legacy-Ressourcen,
Legacy-Auswahl oder Historienumsteller. Die SQL-Inhalte stimmen bytegleich mit
den Quellen überein.
