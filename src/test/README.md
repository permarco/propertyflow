# Tests für PropertyFlow

Alle PowerShell-Befehle gelten ab der Repository-Wurzel. Entwicklung und manuelle
Demo sind unter [dev/README.md](../../dev/README.md) beschrieben.

## Java und JavaScript

```powershell
.\mvnw.cmd test
node --test src/test/js/*.test.mjs
```

Diese Tests benötigen weder Mailpit noch Camunda oder einen externen LLM.
Der Live-Ollama-Test ist opt-in. Java-Testergebnisse: `target/surefire-reports/`.
Maven löst den Pfad der verwendeten `mockito-core`-JAR auf und übergibt sie als
Java-Agent an Surefire. Ein lokaler absoluter Pfad und dynamisches Self-Attach
sind dadurch nicht nötig.

## CI und zusätzliche Browser

[`.github/workflows/tests.yml`](../../.github/workflows/tests.yml) führt bei Push,
Pull Request und manuellem Start folgende Jobs auf Ubuntu 24.04 aus:

| Job | Umfang |
| --- | --- |
| Java und JavaScript | JDK 23, Node 22, Maven Wrapper `verify`, alle Java- und JavaScript-Tests; anschliessend JAR für die Browserjobs. |
| Chromium | Vollständige Browser-Testreihe einschliesslich der automatisierten Barrierefreiheitsprüfungen. |
| Firefox und WebKit | Je 14 Kernablauf-, Konto- und Tastaturtests mit `--core`. |

Jeder Browserjob startet sein eigenes Mailpit und eine neue Demoanwendung.
Mieter-Kontotests sind in CI verpflichtend; fehlende Konfiguration wird abgewiesen.
Die Browserjobs laufen unabhängig weiter, wenn ein anderer Browser scheitert.
Ein neuer Lauf derselben Git-Referenz löst den vorherigen ab. Die Pipeline benötigt
keine produktiven Zugangsdaten und hat nur lesende Repository-Berechtigungen.

Die Browserwerkzeuge werden mit `npm ci` aus der versionierten Lockdatei installiert.
Playwright installiert die dazugehörigen Browserrevisionen samt Linux-Abhängigkeiten.
Surefire-, TAP-, Barrierefreiheitsberichte, vorhandene Screenshots und Anwendungslogs
werden auch nach Testfehlern als Artefakte für sieben Tage gespeichert. Der
optionale Live-Ollama-Test und die separaten Lighthouse-Messungen bleiben ausserhalb
dieser CI-Prüfung; die Pipeline ist kein Nachweis produktiver KI-/Camunda-Integration.

Die Einrichtung folgt den [Playwright-CI-Hinweisen](https://playwright.dev/docs/ci).
Ein tatsächlicher GitHub-Actions-Lauf ist erst nach Push der Workflow-Datei möglich.

### Lokal denselben Browserlauf ausführen

Nach Installation der Browserwerkzeuge (siehe unten), Start des separaten Mailpit
und `mvnw.cmd package`:

```powershell
$env:PLAYWRIGHT_BROWSERS_PATH = Join-Path (Get-Location) 'target/playwright-browsers'
node target/frontend-tools/node_modules/playwright/cli.js install chromium firefox webkit
$env:DEMO_BROWSER = 'firefox'
node src/test/browser/run-browser-regression.mjs --core
$env:DEMO_BROWSER = 'webkit'
node src/test/browser/run-browser-regression.mjs --core
$env:DEMO_BROWSER = 'chromium'
node src/test/browser/run-browser-regression.mjs
Remove-Item Env:DEMO_BROWSER
```

`run-browser-regression.mjs` prüft den freien lokalen Port und Mailpit, startet
eine frische Anwendung aus dem JAR, wartet auf Bereitschaft und stoppt sie nach
dem Testlauf auch bei Fehlern. Standard ist `http://127.0.0.1:8083`, mit
`staff-ci@example.test` und `tenant-ci@example.test` als Wegwerfkonten. Überschreibbar
sind `DEMO_URL`, `DEMO_STAFF_EMAIL`, `DEMO_TENANT_EMAIL`, `MAILPIT_URL` und
`TEST_SMTP_PORT` (Standard 1026). Mailpit selbst wird vom Skript nicht gestoppt.

Für eine bereits gestartete isolierte Anwendung bleibt `run-ui-tests.mjs` verfügbar.
Ohne `DEMO_BROWSER` verwendet es wie bisher Edge; weitere Werte sind `chromium`,
`firefox` und `webkit`. Berichte stehen unter `target/frontend-quality/ui-tests-<browser>.tap`
und zusätzlich unter dem bisherigen Namen `ui-tests.tap`. Der verwaltete Starter
schreibt `app-<browser>.log` in dasselbe Verzeichnis. Browserwechsel benötigen für
unabhängige OTP-Limits jeweils eine frische Anwendung.

`--core` umfasst `core-workflows.test.mjs`, `tenant-account.test.mjs`,
`tenant-registration.test.mjs` und `keyboard-focus.test.mjs`. Die drei neuen
Kernabläufe prüfen mobile Gastanlage mit Pflichtfeldkorrektur und Nachricht,
Mitarbeitersuche mit interner/externer Kommunikation und Abschluss/Wiedereröffnung
sowie KI-Übernahme mit anschliessendem explizitem Speichern. Die bestehenden Tests
ergänzen echte OTP-Anmeldung, Registrierung, Eigentümerprüfung, Logout und neun
Tastatur-/Fokusabläufe. WebKit unter Playwright ersetzt keinen Test auf einem
echten iPhone oder mit der ausgelieferten Safari-Version.

Die CI verwendet Ubuntu 24.04 als WebKit-Testplattform. Bei der lokalen Prüfung
mit Playwright 1.64.0 / WebKit 2370 unter Windows überspringt die Standard-
Tabnavigation normale Links, bereits auf einer minimalen HTML-Seite. Deshalb
scheitern dort zwei bestehende Tastaturtests (Falllink nach Polling und Fehlerlink
im Formular), während dieselben 14 Tests unter Linux bestehen. Die Tests bleiben
aktiv; es werden weder Links künstlich fokussiert noch die Anwendung für den
Test verändert. Für den vollständigen WebKit-Nachweis die Linux-CI verwenden.

### Lokale Validierung am 13. November 2026

| Prüfung | Ergebnis |
| --- | --- |
| Maven `package` (Windows, Java 27) | 214 Tests: 213 bestanden, ein opt-in Live-Ollama-Test übersprungen. |
| JavaScript (Node 22) | 6 bestanden. |
| Vollständige Chromium-Suite (Windows) | 84 bestanden, keine übersprungen. |
| Firefox `--core` (Windows und Ubuntu 24.04) | Je 14 bestanden, keine übersprungen. |
| WebKit `--core` (Ubuntu 24.04) | 14 bestanden, keine übersprungen. |
| WebKit `--core` (Windows) | 12 bestanden, zwei Link-Tastaturtests fehlgeschlagen; siehe Plattformhinweis oben. |
| GitHub-Actions-Workflow | `actionlint` erfolgreich; noch kein Lauf auf GitHub. |

Die Linux-Gegenprüfung lief im offiziellen Playwright-1.64.0-Noble-Container mit
einer ergänzten Temurin-23-JRE und jeweils einer frischen Demoanwendung. Sie prüft
die Browserläufe lokal und ersetzt nicht den ersten vollständigen GitHub-Actions-Lauf.

## CSRF-Matrix und SMTP-Adapter

Die zusätzlichen Sicherheits- und Mailprüfungen sind Teil von `mvnw.cmd test`.
Gezielter Lauf:

```powershell
.\mvnw.cmd test '-Dtest=CsrfWebTest,SmtpDeliveryTest,StaffAccessWebTest,LoginRecoveryWebTest,StaffLoginTest,StaffLoginQueueTest,TenantRegistrationTest'
```

`CsrfWebTest` umfasst 62 Prüfungen: fehlende, falsche und aus einer anderen
Sitzung übernommene CSRF-Tokens bei Fallanlage (Gast, Mieter, Mitarbeiter),
Nachrichten (Token, Mieterkonto, Mitarbeiter), allen acht Mitarbeiteraktionen,
Codeanforderung und Codeprüfung bei Anmeldung/Registrierung sowie Abmeldung
beider Kontorollen. Abgewiesene Falloperationen lassen den gesamten bisherigen
Fallstand unverändert; ein korrigierter Versuch mit derselben Operationskennung
und geändertem Inhalt gelingt. Die Anmeldung/Registrierung wird zusätzlich mit
einem anschliessenden Schreibversuch mit dem alten CSRF-Token geprüft.
Controller, Templates und Dienste sind echt; der Codeversand ist hier durch
eine synchrone Test-Mailbox ersetzt.

`SmtpDeliveryTest` umfasst zehn Prüfungen mit dem produktiven SMTP-Adapter,
Thymeleaf-Mailtemplate und JavaMailSender. Ein lokaler SMTP-Testpartner auf einem
dynamischen Loopback-Port prüft Envelope, Empfänger/Absender, Betreff sowie
UTF-8-Text und HTML in `multipart/alternative`. Er erzeugt echte TCP-Verbindungsablehnung,
einen Lese-Timeout und eine SMTP-550-Ablehnung nach der Datenübertragung.
Für Anmeldung und Registrierung werden danach ungültiger fehlgeschlagener Code,
fehlende Anmeldung bzw. Kontoaktivierung, weiterhin wirksames Versandlimit und
erfolgreicher Neuversand mit Codeprüfung über MVC geprüft.

Die SMTP-Tests brauchen weder Docker noch Mailpit und verändern keinen vorhandenen
Maildienst. Sie benötigen lokale Socket-Verbindungen. Für die Fehlerprüfung gelten
500 ms Transport-Timeouts; die fachliche Minutenfrist wird mit einer Testuhr
weitergeschaltet und der Versand synchron ausgeführt. Produktions-Executor und
Queue bleiben durch `StaffLoginQueueTest` separat abgedeckt. TLS, SMTP-Authentifizierung,
produktive Zustellbarkeit und fachliche Fallbenachrichtigungen sind nicht Gegenstand
dieser Tests. Alle Test-Sockets werden am Ende geschlossen.

## Browser-Werkzeuge installieren

Voraussetzungen: Java 23+, Node.js mit npm, Microsoft Edge (Standard) oder ein
installierter Playwright-Browser und Docker Compose
ab 2.24.4. Der Wrapper übernimmt Maven. Die Werkzeuge bleiben unter `target/`:

```powershell
New-Item -ItemType Directory -Force target/frontend-tools | Out-Null
Copy-Item src/test/browser/tools-package.json target/frontend-tools/package.json
Copy-Item src/test/browser/tools-package-lock.json target/frontend-tools/package-lock.json
npm ci --prefix target/frontend-tools
```

## Separates Mailpit starten

```powershell
docker compose -f dev/compose.yaml -f dev/compose.test.yaml up -d --wait
```

Das Compose-Projekt heisst `propertyflow-test`. Der Override **ersetzt** die
Portliste: SMTP `127.0.0.1:1026`, [Web/API](http://127.0.0.1:8026).
Dein normales Mailpit auf 1025/8025 bleibt unabhängig davon erreichbar.
Es gibt weder ein gemeinsam genutztes Volume noch einen festen Containernamen.

## Wegwerfbare Testanwendung starten

In einem eigenen Terminal die Anwendung bauen und mit expliziten Testwerten starten:

```powershell
.\mvnw.cmd package -DskipTests
java -jar target/propertyflow-0.0.1-SNAPSHOT.jar `
    --spring.profiles.active=frontend-demo --server.port=8082 `
    --spring.mail.host=127.0.0.1 --spring.mail.port=1026 `
    --spring.mail.properties.mail.smtp.auth=false `
    --spring.mail.properties.mail.smtp.starttls.enable=false `
    --spring.mail.properties.mail.smtp.starttls.required=false `
    --propertyflow.staff.mail.from=propertyflow@example.test `
    --propertyflow.staff.bootstrap.email=staff@example.test `
    --propertyflow.tenant.demo.email=tenant@example.test
```

Das JAR läuft ohne DevTools-Neustarts. Die Parameter überschreiben die lokalen
Demo-Konten und SMTP-Einstellungen. Es wird keine Datenbank für diese Demo benötigt.
Tests verändern die Fälle im Arbeitsspeicher; verwende eine eigene Testinstanz.

## Browser-Testreihe ausführen

In einem weiteren Terminal:

```powershell
$env:DEMO_URL = 'http://127.0.0.1:8082'
$env:MAILPIT_URL = 'http://127.0.0.1:8026'
$env:DEMO_STAFF_EMAIL = 'staff@example.test'
$env:DEMO_TENANT_EMAIL = 'tenant@example.test'
node src/test/browser/run-ui-tests.mjs
```

Die drei priorisierten Regressionstestgruppen lassen sich auch gezielt mit gemeinsamem
Mitarbeiter-Testlogin ausführen:

```powershell
node src/test/browser/run-ui-tests.mjs session-loss.test.mjs lost-response.test.mjs
```

`session-loss.test.mjs` meldet echte Mitarbeiter- und Mieterkontositzungen in einem
zweiten Tab ab. Geprüft werden der nächste Lese- und Schreibzugriff, ausgeblendete
Falldaten, gestoppte Aktualisierungen, erneute OTP-Anmeldung ohne automatischen
Schreib-Retry sowie weiterhin zugängliche Tokenlinks. Bei der Fallanlage aus dem Mieterkonto
bleiben nach der Abmeldung alle Eingaben erhalten; vor der Anlage ist eine explizite Gastadresse erforderlich.
Die Logout-Tests verwenden eigene Sitzungen und warten bei Bedarf auf die echte
OTP-Minutenfrist; die gemeinsam genutzte Testsitzung wird nicht abgemeldet.

`lost-response.test.mjs` lässt Anlage und Feldänderung tatsächlich speichern und
bricht erst danach die Antwort ab. Der erneute Versand erfolgt über die Oberfläche
mit derselben Operationskennung. Fallanzahl, Verlauf und Feldversion belegen, dass
kein zusätzlicher Schreibeffekt entsteht. Ohne JavaScript bleibt die native
Formularübermittlung verfügbar; die Wiederholung bei verlorener Antwort nutzt JavaScript.

### Login-Fehler und Wiederanmeldung

```powershell
node src/test/browser/run-ui-tests.mjs login-errors.test.mjs tenant-account.test.mjs tenant-registration.test.mjs
.\mvnw.cmd test '-Dtest=LoginRecoveryWebTest,StaffAccessWebTest,StaffLoginTest,StaffLoginQueueTest,TenantRegistrationTest'
```

`login-errors.test.mjs` prüft Anmeldung und Registrierung mit echten Mailpit-Codes:
falsche Eingaben, Ungültigkeit nach fünf Fehlversuchen, HTTP 429 bei zu früher
Codeanforderung, erhaltenes Codeformular und erfolgreichen Versand samt Anmeldung
nach der Wartefrist. Die Anmeldung prüft ausserdem die Korrektur einer unbekannten
Adresse und dieselbe neutrale Versandmeldung für bekannte und unbekannte Konten.
Jeder Test verwendet eine eigene Adresse; die echte Minutenfrist bleibt aktiv.
Der gezielte Browserlauf dauert deshalb etwa drei Minuten.

`LoginRecoveryWebTest` verwendet den echten Login-Service, Controller und die
gerenderte Thymeleaf-Seite mit kontrollierter Serveruhr und Test-Mailbox. Er prüft
für beide Zugangswege die Ablehnung exakt nach zehn Minuten, fehlenden Kontozugriff
und erfolgreiche Wiederanmeldung mit neuem Code. Ein zu früher erneuter Versand
darf den noch gültigen Code nicht entwerten. Der Zeitablauf ist damit ein MVC-Test;
im Browser wird keine zehnminütige Serverablaufzeit simuliert.

`DEMO_TENANT_EMAIL` aktiviert auch den Mieter-Kontotest. Ohne diese Variable wird
nur dieser zusätzliche Test ausgelassen. Die Selbstregistrierung wird unabhängig
davon mit einer neuen Testadresse und echtem SMTP-Versand an Mailpit geprüft.
Dabei werden frühere Gast-Anliegen zugeordnet, fremde Fälle gesperrt und ein neues
Anliegen nach der Registrierung erstellt. Einzeltests:

```powershell
node --test src/test/browser/tenant-account.test.mjs
node --test src/test/browser/tenant-registration.test.mjs
```

Die Tests verwenden die [Mailpit-API v1](https://mailpit.axllent.org/docs/api-v1/).
Vor einer Codeanforderung merken sie sich vorhandene Nachrichten-IDs. Danach
berücksichtigen sie nur neue Nachrichten mit passendem Empfänger und Betreff.
IDs werden als undurchsichtige Kennungen behandelt, nicht numerisch verglichen.
Der bisherige eigene SMTP-Testserver entfällt. Es werden keine Mails gelöscht.
Parallele unabhängige Testläufe benötigen eigene Konten und Instanzen.

Bei raschen Wiederholungen gelten die Login-Limits: eine Codeanforderung je Minute
und maximal fünf pro Stunde je Adresse. Für einen frischen Testlauf die eigene
Testanwendung neu starten; vorhandene Mailpit-Nachrichten dürfen bleiben.

## Berichte und ergänzende Prüfungen

Die Tastatur- und Fokusregressionen laufen auch separat:

```powershell
node src/test/browser/run-ui-tests.mjs keyboard-focus.test.mjs
```

Die neun Tests erreichen Bedienelemente über Tab und aktivieren sie über Enter.
Sie prüfen Editor-Abbruch mit und ohne zwischenzeitliche Aktualisierung, Speichern,
Versionskonflikte, Nachrichtenfehler und die Tastaturkorrektur eines Anlagefehlers.
Zusätzlich werden der zugängliche Dialogname, Schliessen mit Escape und Enter,
Rückkehr zum Entwurf samt Schreibposition sowie der Listenfokus nach Aktualisierung
oder Wegfall eines Falls geprüft. Eine verzögerte Speicherantwort darf einen
inzwischen weiterbewegten Fokus nicht zurückholen. Die Testuhr wird vor der
Seitennavigation installiert, damit die echten Polling-Intervalle kontrolliert laufen.

Die Lebenszyklusprüfungen der lokalen KI-Formulierung laufen gezielt mit:

```powershell
node src/test/browser/run-ui-tests.mjs ai-lifecycle.test.mjs ai-layout.test.mjs staff-workflows.test.mjs session-loss.test.mjs
```

`ai-lifecycle.test.mjs` prüft Abbruch während der Wartephase und während der Ausgabe,
Neustart ohne Textreste des alten Versuchs sowie Übernahme ohne automatische
Speicherung. Ein KI-Fehler während einer noch laufenden Speicherung darf die
Bedienelemente nicht entsperren. Geprüft werden ein erfolgreicher Schreibzugriff
und eine simulierte HTTP-503-Antwort mit anschliessendem erfolgreichem Retry.
Eine abgebrochene Navigation muss den Entwurf und den laufenden KI-Versuch erhalten.
Die Prüfungen betreffen den lokalen Mock-Stream, keine externe Modellanbindung.

Auch `session-loss.test.mjs` installiert seine Testuhr vor dem Laden der geschützten
Fallansichten. Damit werden nach Abmeldung tatsächlich deren Polling-Intervalle
weitergeschaltet und die ausbleibenden Abrufe geprüft.

- Browser-Testreihe: `target/frontend-quality/ui-tests.tap`
- Konto-Screenshots: `target/account-review/`
- Barrierefreiheit (auch im regulären Testlauf): `node src/test/browser/run-ui-tests.mjs frontend-quality.test.mjs`
- Separater Aufruf derselben Prüfung: `node src/test/browser/frontend-quality.mjs`
- Performance: `node src/test/browser/frontend-performance.mjs`
- Zugehörige Berichte: `target/frontend-quality/browser.json` und `performance.json`

Die ergänzenden Skripte verwenden dieselben Einstellungen für Demo und Mailpit.
`frontend-quality.test.mjs` wird vom normalen Browser-Teststarter automatisch
gefunden. Die Prüfung umfasst 19 Ansichten und Zustände: Basisansichten,
Anlage- und Loginfehler, Codeeingabe, leere und gefüllte Mieterkontoübersicht,
Kontodetails, geöffneten Editor, Aktualisierungsdialog und Streaming-Zustände.
Anlagefehler, Kontodetails und abgeschlossene KI-Ausgaben werden zusätzlich bei
390 Pixel Breite geprüft. Die vorhandenen Prüfungen für Streaming-Abbruch,
Tastaturbedienung und sichere Textausgabe bleiben enthalten.

axe prüft die automatisierbaren Regeln mit den Tags `wcag2a`, `wcag2aa`,
`wcag21a` und `wcag21aa`. Wie bisher lassen Verstösse mit Schweregrad `serious`
oder `critical` den Test fehlschlagen. Alle gefundenen Verstösse sowie Hinweise
auf nötige manuelle Prüfungen stehen in `browser.json`; dieser Bericht wird
auch bei einem Testfehler geschrieben. Die Formularfehlerprüfung schützt unter
anderem den Kontrast der Korrekturlinks auf dem roten Fehlerhintergrund.

Methodik und frühere Ergebnisse stehen im
[Block-2-Prüfprotokoll](../../docs/ai-usage/block2-oberflaechen-pruefung.md).

## Beenden

Die Testanwendung im eigenen Terminal mit `Ctrl+C` beenden. Danach:

```powershell
docker compose -f dev/compose.yaml -f dev/compose.test.yaml down
```

Das entfernt ausschliesslich die Test-Compose-Umgebung und deren aufgefangene Mails.
Das separate Entwicklungs-Mailpit und Camunda bleiben bestehen.

## Ergänzte Backend- und Zugriffsprüfungen

Die Regressionstests für die Backend-Testlückenanalyse vom 13.11.2026 liegen bei den
jeweiligen Fähigkeiten:

| Testklasse / Browserdatei | Abgedeckte Grenze |
| --- | --- |
| `DemoCasesConflictTest`, `operation-conflicts.test.mjs` | Identische Wiederholung und HTTP 409 bei geändertem Anlageinhalt bzw. geändertem Wert oder Grund für sämtliche Feldaktionen; ursprünglicher Zustand und Verlauf bleiben erhalten. |
| `DemoCasesBoundaryTest` | Serverseitige Pflichtfelder und Längengrenzen; unveränderter Zustand nach Ablehnung und korrigierter Retry; Nachrichtenkonflikte für Text, Sichtbarkeit, Antwortbedarf und Absenderzugang; gleichzeitige Fallanlage und konkurrierende Änderungen mit gemeinsamer Startbarriere. |
| `StaffLoginQueueTest` | Veralteter wartender Versandauftrag, Fehler einer älteren laufenden Zustellung, abgewiesene Queue, abgelaufener Auftrag und Adressobergrenze. Executor und Uhr sind kontrolliert; keine Wartezeiten für den fachlichen Zeitablauf. |
| `TenantRegistrationTest` | Gleichzeitige Codeprüfung mit Startbarriere: genau ein erfolgreiches Ergebnis und ein aktiviertes Konto. |
| `StaffAccessWebTest` | Isolation der Anlagekennung zwischen Konten und Gastsitzungen; Mitarbeiter-Nachrichtenendpoint für Mieter gesperrt; Tokenzugriff darf weder interne Nachrichten noch Antwortbedarf setzen; Kontozugriff benötigt Anmeldung; direkte HTTP-Eingabevalidierung. |
| `remaining-interactions.test.mjs` | Token-Polling und verlorene Speicherantwort mit identischem Retry in einem Gastkontext ohne Mitarbeiterrechte; Mitarbeiterantworten aus einem getrennten Kontext. |
| `ui-boundaries.test.mjs` | Gastkontext für Tokenzugriffsfehler und Versand ohne JavaScript; Mitarbeiter-Listennavigation in einem getrennten Kontext. |
| `tenant-account.test.mjs` | Nachricht über die Kontoroute mit echtem, per OTP angemeldetem Mieterkonto; Mitarbeiterzugriff bleibt gesperrt. |

Die Gast-Fixture prüft vor dem Test, dass ein Mitarbeiter-Lesezugriff HTTP 401 liefert.
Konto- und Tokenzugriff werden dadurch nicht mit Mitarbeiterrechten verwechselt.
Die vorhandenen Gast- und Token-Schreibrechte bleiben ausdrücklich erhalten.

Diese Tests belegen weiterhin keine dauerhafte Speicherung, Camunda-Prozessübergabe,
MCP-Autorisierung oder fachliche E-Mail-Zustellung. Der OTP-Queue-Test simuliert
Versandfehler am Delivery-Port; er ist kein SMTP-Netzwerkausfalltest. Der Live-Ollama-Test
bleibt ausdrücklich opt-in.

### Wiederholungsvertrag der Demo

Für Fallanlage, Nachrichten und Feldaktionen gilt: Gleiche Operationskennung im
jeweiligen Kontext und unveränderte fachliche Eingaben wiederholen das erfolgreiche
Ergebnis ohne zusätzlichen Schreibeffekt. Geänderter Inhalt führt zu HTTP 409.
Verglichen werden die ursprünglichen Eingaben vor dem Kürzen äusserer Leerzeichen.
Fallanlagen sind nach Konto bzw. Gastsitzung getrennt; Feldaktionen weiterhin nach Fall
und Aktion. Die Version ist die Vorbedingung für eine neue Feldänderung und kein
Inhaltsfeld eines bereits erfolgreichen Retries. Deshalb bleiben identische Retries
auch nach weiteren Änderungen möglich. Ein abgewiesener erster Versuch verbraucht
keine Kennung. Die Speicherung bleibt prozesslokal und geht beim Neustart verloren.

### Überlappende Browseraktionen

```powershell
node src/test/browser/run-ui-tests.mjs overlapping-actions.test.mjs lost-response.test.mjs operation-conflicts.test.mjs
```

`overlapping-actions.test.mjs` ergänzt vier Prüfungen mit echten Browseranfragen:
Doppelklick plus Enter während einer laufenden Fallanlage erzeugen nur einen POST
und einen Fall. In Gast-Token- und Mitarbeiteransichten wird eine ältere Leseantwort
zurückgehalten, während eine Nachricht gespeichert wird. Der Test beobachtet den
Abbruch des Browserabrufs, gibt danach die alte Antwort frei und prüft, dass Verlauf
und Version aktuell bleiben und das Polling wieder läuft.

Zwei Tabs derselben Mitarbeitersitzung starten unterschiedliche Feldänderungen
mit derselben Ausgangsversion und unterschiedlichen Operationskennungen. Beide
POSTs werden an einer gemeinsamen Barriere freigegeben. Genau eine Änderung gelingt,
die andere meldet HTTP 409. Beide Ansichten zeigen anschliessend den gespeicherten
Wert; ihre Nachrichtenentwürfe bleiben erhalten. Eine frisch geöffnete Ansicht
bestätigt genau eine zusätzliche Version und einen zusätzlichen Verlaufseintrag.
