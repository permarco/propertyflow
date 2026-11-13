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
Wenn Mockito sich unter deinem JDK nicht selbst anhängen kann, kann die lokal
aufgelöste `mockito-core`-JAR als `-javaagent` über Maven `-DargLine` übergeben
werden. Version und Pfad müssen zur tatsächlich aufgelösten Dependency passen.

## Browser-Werkzeuge installieren

Voraussetzungen: Java 23+, Node.js mit npm, Microsoft Edge und Docker Compose
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
