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

- Browser-Testreihe: `target/frontend-quality/ui-tests.tap`
- Konto-Screenshots: `target/account-review/`
- Barrierefreiheit: `node src/test/browser/frontend-quality.mjs`
- Performance: `node src/test/browser/frontend-performance.mjs`
- Zugehörige Berichte: `target/frontend-quality/browser.json` und `performance.json`

Die ergänzenden Skripte verwenden dieselben Einstellungen für Demo und Mailpit.
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
