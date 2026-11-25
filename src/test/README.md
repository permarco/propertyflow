# Tests für PropertyFlow

Alle PowerShell-Befehle gelten ab der Repository-Wurzel. Entwicklung und manuelle
Demo sind unter [dev/README.md](../../dev/README.md) beschrieben.

## Java und JavaScript

```powershell
.\mvnw.cmd test
node --test src/test/js/*.test.mjs
```

Diese Tests benötigen weder Mailpit noch Camunda oder einen externen LLM.
Live-Ollama- und Modellevaluationen sind opt-in. Java-Testergebnisse:
`target/surefire-reports/`. Die PostgreSQL-/Camunda-Tests benötigen zusätzlich
die unten beschriebene isolierte Infrastruktur; ohne deren Umgebungsvariablen
werden sie übersprungen.
Maven löst den Pfad der verwendeten `mockito-core`-JAR auf und übergibt sie als
Java-Agent an Surefire. Ein lokaler absoluter Pfad und dynamisches Self-Attach
sind dadurch nicht nötig.

## MCP-HTTP und lokale Netzwerkdiagnose

`FallstandMcpHttpTest` startet das Profil `propertyflow-backend` auf einem
zufälligen lokalen Port. Er prüft Anmeldung per Einmalcode, OAuth mit PKCE,
Fallstandabruf mit delegiertem Zugriff und Widerruf über echtes HTTP. Er
migriert ein eigenes Schema in `propertyflow_test` und entfernt es anschliessend.
In CI läuft der Test im PostgreSQL-Integrationsjob. `McpOAuthFlowTest`,
`BackendMcpConfigurationTest` und die persistenten Diensttests ergänzen die
Prüfungen. Die ChatGPT-Workspace-Abnahme ist separat.

Am 25.11.2026 wurde im lokalen Windows-Ausführungskontext eine Veränderung
von Antworten auf `/mcp` nachgewiesen: Ein separater minimaler Socketserver
sendete `Content-Length: 0`, beim Client kam stattdessen eine unvollständige
`Transfer-Encoding: chunked`-Antwort an. Bei `/` blieb dieselbe Antwort
unverändert. Die verursachende Zwischenkomponente ist nicht identifiziert.
Die beiden damaligen Demo-HTTP-Tests bestanden im isolierten Linux-/Java-23-Lauf.

Bei `chunked transfer encoding, state: READING_LENGTH` deshalb zuerst mit
unverändertem Code in einer unabhängigen Linux-Umgebung gegenprüfen:

```sh
export PROPERTYFLOW_TEST_DATABASE_URL=jdbc:postgresql://127.0.0.1:5434/propertyflow_test
sh ./mvnw -B -ntp -Dtest=FallstandMcpHttpTest test
```

Keine Berechtigungsprüfung abschwächen und den Test nicht dauerhaft aus CI
entfernen. [Regression und Nachweisgrenzen vom 25.11.2026](../../docs/evaluation/regression-2026-11-25.md).

## Workflowtests mit den Anwendungsmodellen

Alle `*CamundaTest`-Klassen deployen über `WorkflowTestModels` die acht
unveränderten BPMN-Dateien aus `src/main/resources/bpmn`. Haupt- und Teilprozesse
werden gemeinsam deployt, damit Aufrufe mit `bindingType="deployment"` dieselbe
Modellversion verwenden. Separate BPMN-Kopien unter den Testressourcen und die
Übersetzung auf historische Test-IDs entfallen.

Die Tests steuern die echten Worker und Engine-Schritte; fachliche KI-Antworten
bleiben kontrollierte Testeingaben. Modellbedingungen, Variablenübergaben,
Ereignisse und Timer werden für die Tests nicht umgeschrieben. Die statischen
Bindungsprüfungen prüfen ihre Erwartungen direkt an den Originaldateien.

Die Engine-Tests benötigen eine **isolierte** PostgreSQL-Datenbank namens
`propertyflow_test` und Camunda. Die Tests leeren die Falltabellen. Parallele
Testläufe müssen eigene Datenbanken und Engines verwenden.

GitHub Actions führt die Integrationstests in parallelen Gruppen mit jeweils
eigener Datenbank aus. Jede Camunda-Gruppe startet ihre eigene Engine. Das
Limit pro Integrationsjob beträgt fünf Minuten, einschliesslich Einrichtung.

```powershell
docker compose -f dev/compose.backend-test.yaml up -d --wait
docker compose -f dev/compose.workflow-test.yaml up -d --wait
$env:PROPERTYFLOW_TEST_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:5434/propertyflow_test'
$env:PROPERTYFLOW_TEST_CAMUNDA_URL = 'http://127.0.0.1:8085'
$env:PROPERTYFLOW_TEST_SMTP_PORT = '1027'
$env:PROPERTYFLOW_TEST_MAILPIT_URL = 'http://127.0.0.1:8027'
.\mvnw.cmd '-Dtest=*CamundaTest,InitialWorkflowBindingTest,ReviewWorkflowBindingTest,BpmnTechnicalConstantsTest,ProcessVariablesContractTest' test
```

### Störfall: Camunda zehn Minuten nicht erreichbar

`CamundaOutageIntakeTest` weist den Abnahmetest J2 nach. Er läuft nur, wenn
zusätzlich `PROPERTYFLOW_TEST_OUTAGE_MINUTES` gesetzt ist; die Abnahme verlangt
`10`. Für eine schnelle lokale Kontrolle genügt `1`. Der Test arbeitet in einem
eigenen Datenbankschema und entfernt es danach. Im CI läuft er als eigener Job
auf `main` und über «Run workflow».

```powershell
$env:PROPERTYFLOW_TEST_OUTAGE_MINUTES = '10'
.\mvnw.cmd '-Dtest=CamundaOutageIntakeTest' test
```

Ohne Datenbank- und Camunda-URL werden die Engine-Tests übersprungen;
für die SMTP-Prozessprüfung ist zusätzlich der SMTP-Port erforderlich; ein solcher
Lauf ist kein Nachweis der Prozessausführung. Ergebnisse stehen in
`target/surefire-reports/`.

## Stufe 1 · Gastanlage und persönlicher Fall-Link

`BackendMigrationPostgresTest` prüft den Neuaufbau aus den fünf Modulskripten,
49 Fachtabellen, den Einrichtungsdatensatz und die erneute Flyway-Validierung.
[Migrationen und Reset der isolierten Testdatenbank](../../docs/architecture/datenbankmigrationen.md)
beschreiben die Aufteilung und den Testbetrieb. Die Legacy- und
Historienumstellungstests sind nach dem freigegebenen Hard Reset entfernt.

Der Adaptertest verwendet die echten MVC-Controller und Thymeleaf-Templates
mit kontrollierten Testimplementierungen von `CaseSubmission` und `CaseReading`.

```powershell
.\mvnw.cmd '-Dtest=Stage1GuestWebTest' test
```

Die PostgreSQL-Abnahme verwendet die wegwerfbare Datenbank
`propertyflow_test`. Der Servicetest leert ihre Falltabellen. Danach prüft der
Neustarttest zwei aufeinanderfolgende Starts mit echtem HTTP, derselben
Datenbank und demselben Link-Schlüssel. Er bestätigt den Gast-Retry in der
ursprünglichen Sitzung, denselben Fall-Link nach Neustart und genau einen
Fall mit genau einem offenen Startauftrag. Der alte Browser-POST erhält nach
Verlust der Sitzung 403. Der Fachservice erkennt den ursprünglichen
Wiederholungsschlüssel auch nach Neustart.

```powershell
docker compose -f dev/compose.backend-test.yaml up -d --wait
$env:PROPERTYFLOW_TEST_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:5434/propertyflow_test'
.\mvnw.cmd '-Dtest=GuestCasePostgresTest,GuestCaseRestartPostgresTest,Stage1GuestWebTest' package
```

Für den Browsertest die Browser-Werkzeuge unten installieren und die URL einer
isolierten Anwendung mit `propertyflow-backend`, PostgreSQL und ohne Camunda
setzen. Der Test legt künstliche Fälle an. Die vollständige Abnahme erfolgt im integrierten Runner unten.

```powershell
$env:PROPERTYFLOW_STAGE1_URL = 'http://127.0.0.1:8084'
node --test src/test/browser/stage1/guest-intake.test.mjs
```

Dieser Lauf prüft den Browserweg und die HTTP-Wiederholung. Die Datenbanktests
prüfen zusätzlich, dass genau ein Fall und ein Startauftrag entstehen.
Für die 429-Abnahme können
`propertyflow.web.guest.submissions-per-minute` und
`propertyflow.web.guest.link-reads-per-minute` je Clientadresse konfiguriert
werden. Der Wert `0` deaktiviert das jeweilige Limit. Die Grenzwerte für
den regulären Einsatz sind noch offen.

## Stufe 2 · Mitarbeiter-Login, Fallübersicht und Falldetail

Die Tests prüfen den Einmalcode-Dienst, die regulären Web-Adapter, die
HTML-Ansichten sowie die einmalige Einrichtung und aktuelle Rollenprüfung
mit PostgreSQL.

```powershell
docker compose -f dev/compose.backend-test.yaml up -d --wait
$env:PROPERTYFLOW_TEST_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:5434/propertyflow_test'
.\mvnw.cmd '-Dtest=StaffOtpServiceTest,StaffAccountPostgresTest,RegularStaffLoginWebTest,StaffLoginViewTest,StaffCaseWebTest' package
```

Der [Zugriffsvertrag](../../docs/architecture/zugriffsvertrag-mitarbeiter-login.md)
führt die HTTP- und Fachprüfungen auf. Der separate Browsertest setzt die
reguläre Anwendung voraus. Für den Lauf die unten beschriebenen
Browser-Werkzeuge installieren und das Test-Mailpit starten. Die isolierte
Anwendung mit `propertyflow-backend` auf Port 8084 und SMTP-Port 1026 starten.
Den [Serverbefehl zur Ersteinrichtung](../../dev/README.md#regulärer-mitarbeiter-login)
für ein Konto mit einer `.test`-Adresse ausführen. Über das Mieterformular einen
Testfall anlegen und `TEST_CASE_ID` durch dessen Case-ID ersetzen.

```powershell
docker compose -f dev/compose.yaml -f dev/compose.test.yaml up -d --wait
$env:PROPERTYFLOW_STAGE2_URL = 'http://127.0.0.1:8084'
$env:PROPERTYFLOW_STAGE2_STAFF_EMAIL = 'staff-stage2@example.test'
$env:PROPERTYFLOW_STAGE2_CASE_ID = 'TEST_CASE_ID'
$env:MAILPIT_URL = 'http://127.0.0.1:8026'
node --test src/test/browser/stage2/staff-login.test.mjs
```

## Browser-Werkzeuge installieren

Java 23+, Node 22 und Docker Compose werden benötigt. Die Browserwerkzeuge bleiben unter `target/`:

```powershell
New-Item -ItemType Directory -Force target/frontend-tools | Out-Null
Copy-Item src/test/browser/tools-package.json target/frontend-tools/package.json
Copy-Item src/test/browser/tools-package-lock.json target/frontend-tools/package-lock.json
npm ci --prefix target/frontend-tools
node target/frontend-tools/node_modules/playwright/cli.js install chromium firefox webkit
```

Unter Linux zusätzlich die Playwright-Systemabhängigkeiten installieren (`install --with-deps`). Bei Übernahme der Ablösung in ein vorhandenes Arbeitsverzeichnis zuerst einmal `mvnw clean verify` ausführen und anschliessend die Browserwerkzeuge erneut installieren. So verbleiben keine bereits gelöschten Demo-Klassen oder Ressourcen im alten `target/`-Verzeichnis.

## Isolierte integrierte Browserprüfung für CI

```powershell
.\mvnw.cmd verify
$env:PROPERTYFLOW_TEST_BROWSER = 'chromium' # alternativ firefox, webkit oder msedge
node src/test/browser/run-integrated-regression.mjs
node --test --test-concurrency=1 src/test/browser/stage3/*.test.mjs
```

Der portable Runner startet PostgreSQL, Camunda und Mailpit über `dev/compose.integration.yaml`, richtet das Mitarbeiterkonto ein, deployt die acht BPMNs aus dem JAR und startet das echte Backend. Er prüft Mieter-/Mitarbeiterinteraktionen, Operate, Kontenverwaltung, Backendneustart und echte Sitzungsabläufe nach 65 Sekunden Leerlauf. Fallaufnahme, 51 persistente Fälle mit Seitennavigation, Such-/Schreibfehler, Barrierefreiheit und Tastatur werden gegen diese Instanz geprüft. Externe KI ist deaktiviert; manuelle Arbeit muss möglich bleiben.

Die Ports 8084, 8086, 26506, 5440, 8028 und 1028 müssen frei sein. Belegte Ports werden nicht übernommen. Die Container und Datenbanken gehören ausschliesslich diesem Lauf; die Entwicklungsdatenbank bleibt erhalten. Testkonten und Link-Schlüssel werden für den Lauf neu eingerichtet. Der Runner entfernt die eigenen Container auch bei Fehlern.

`stage3` prüft dieselben Backend-JavaScript-Dateien mit kontrollierten Fehlerantworten und verzögerten Antworten. Das sind ergänzende Adaptertests, keine In-Memory-Fachsimulation. Die alte Frontend-Demo ist kein Testziel mehr. [Übertragungsmatrix und Annahmen](../../docs/architecture/frontend-demo-ablosung-2026-11-16.md).

Die drei Browserjobs in [`.github/workflows/tests.yml`](../../.github/workflows/tests.yml) verwenden diesen identischen Ablauf. Zusätzlich laufen Java-/JavaScript-Regression und PostgreSQL-/Camunda-Vertragstests. Derselbe Einstieg kann in GitLab mit Java, Node, Docker Compose und Playwright genutzt werden, beispielsweise auf einem isolierten Linux-Shell-Runner mit lokal erreichbaren Docker-Portfreigaben. Parallele Matrixjobs benötigen getrennte Runnerumgebungen. Remote-CI wird erst durch einen tatsächlichen Push/Lauf nachgewiesen.

In CI laufen Oberflächen- und Kontentests parallel je Browser. In der
Kontengruppe bleiben Prüfung, Backendneustart und tatsächlicher Sitzungsablauf
zusammen. Ein separater Chromium-Job misst die Performance mit Lighthouse.
Jeder Job baut seinen eigenen Teststack auf. Die UI-Jobs haben inklusive
JavaScript-Fehlerprüfungen zehn Minuten Zeit, die übrigen acht Minuten.
Die Testinstanz fragt ausstehende Workflow-Aufträge häufiger ab.

Lokal startet der Runner standardmässig alle funktionalen Prüfungen. Mit
`PROPERTYFLOW_BROWSER_SUITE=ui`, `accounts` oder `performance` lässt sich eine
Gruppe einzeln ausführen. Mit `PROPERTYFLOW_PERFORMANCE=true` läuft Lighthouse
auch im vollständigen lokalen Durchlauf. Lighthouse verwendet echte Fall-URLs.
Pro Seite zählt der Median aus drei gültigen Desktop-Messungen. Er muss
mindestens 90 Punkte erreichen.

Diagnosen stehen unter `target/integrated-e2e/` und `target/frontend-quality/`.
Die temporäre Sitzungsdatei unter `target/integrated-e2e/run-*/` bleibt lokal.

## Anmeldung, CSRF und SMTP

```powershell
$env:PROPERTYFLOW_TEST_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:5434/propertyflow_test'
.\mvnw.cmd '-Dtest=StaffOtpSecurityTest,StaffOtpQueueTest,TenantOtpServiceTest,SmtpDeliveryTest,BackendAccessParityPostgresTest' test
```

`BackendAccessParityPostgresTest` verwendet reale HTTP-Anfragen und ein eigenes Schema in `propertyflow_test`. Der Test prüft CSRF, fehlgeschlagene/identische/geänderte Wiederholungen, Kontoeigentum, Session-ID-Wechsel, Logout, Rollenentzug, Schutzheader und URL-Session-Tracking sowie die gespeicherte Reihenfolge aller sieben Listenspalten, fehlende Wiedervorlagen und die feldübergreifende UND-Suche. Er entfernt ausschliesslich sein eigenes Schema. Zusätzliche Fach- und HTTP-Prüfungen liegen in `GuestCasePostgresTest`, `CaseDataPostgresTest`, `StaffCaseWebTest` und den jeweiligen Nachrichten-/Prozesstests.

`SmtpDeliveryTest` verwendet den produktiven SMTP-Adapter, das echte Mailtemplate und einen lokalen TCP-Testpartner. Zehn Prüfungen decken UTF-8/MIME, Verbindungsablehnung, Timeout, SMTP 550 und erfolgreichen Neuversand über den Backend-Einmalcode-Dienst ab. Dafür werden weder Docker noch ein externer SMTP-Dienst benötigt. Die OTP-Queue und konkurrierende Verifikation werden mit kontrollierter Uhr/Executor geprüft.

### Fachliche Evaluation von Abschluss und Mitteilung

`ClosureModelEvaluationTest` prüft den ursprünglichen Abschlussbestand, zusätzliche
Gesamtfallverläufe und ausdrücklich synthetische Regelvoraussetzungen/-ausnahmen.
`ClosureMessageModelEvaluationTest` prüft echte individuelle Generierung sowie
gezielt korrekte und fehlerhafte Entwürfe mit echter Modell-Inhaltsprüfung.
Beide sind standardmässig deaktiviert und benötigen die expliziten
`PROPERTYFLOW_*_EVALUATION`-Variablen und die isolierte Testdatenbank aus
[automatischer-abschluss.md](../../docs/evaluation/automatischer-abschluss.md).

Der gezielte Maven-Selektor lautet
`-Dtest=ClosureModelEvaluationTest,ClosureMessageModelEvaluationTest`.
Die JSON-Berichte unter `target/closure-*-evaluation.json` enthalten auch rohe
Modellantworten aus ausschliesslich synthetischen Eingaben. Unvollständige Läufe,
technische Fehler und ungültige Antworten zählen nicht als fachlicher Erfolg.
Die produktive Freigabesicherung wird durch diese Tests nicht verändert.

## Konten und Fallzugänge im integrierten Backend

Die aktuelle Prüfung ist in [Konten und Zugriffe vom 16.11.2026](../../docs/evaluation/konten-und-zugriffe-2026-11-16.md)
dokumentiert. `AccountLifecyclePostgresTest`, `TenantOtpServiceTest` und
`BackendMigrationPostgresTest` prüfen die neuen Konten, Rechte, Prüfnachweise und
Migrationen. Die PostgreSQL-Prüfungen dürfen nur gegen die isolierte Datenbank
`propertyflow_test` auf Port 5434 laufen.

```powershell
$env:PROPERTYFLOW_TEST_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:5434/propertyflow_test'
.\mvnw.cmd '-Dtest=AccountLifecyclePostgresTest,TenantOtpServiceTest,BackendMigrationPostgresTest' test

$env:PROPERTYFLOW_DEV_SMOKE = 'true'
node --test src/test/browser/dev-accounts.test.mjs
```

Der Browserlauf verwendet den laufenden Devstack und legt synthetische Konten/Fälle
an. Danach kann ein vollständiger Stop/Start durchgeführt und mit
`PROPERTYFLOW_DEV_ACCOUNT_RESTART=true` bei entferntem `PROPERTYFLOW_DEV_SMOKE`
der zweite Test derselben Datei ausgeführt werden. Er verwendet die zuletzt gespeicherte
Zuordnung in `target/dev-verification/accounts-result.json` und meldet bestehende
Mieterkonten erneut an. Keine Fälle oder Konten werden dafür zurückgesetzt.


Die dauerhaften UI-Regressionstests in `browser/stage3/ui-controls.test.mjs` laden die echten Mitarbeiter- und Mieter-Templates sowie die Produktionsskripte. Sie prüfen Radioauswahl und Farben, explizites Speichern mit Fehler-/Erfolgsfällen, Klappkarten, Intern-/Antwort-Toggles, Entwurfserhalt und Verlaufssortierung. HTTP-Antworten sind kontrolliert; dies ersetzt keinen vollständigen Camunda-/Datenbank-End-to-End-Lauf. Die bestehende CI nimmt diese Datei über `stage3/*.test.mjs` automatisch auf.
