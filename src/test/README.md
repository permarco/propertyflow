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
