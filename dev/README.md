# Lokale Entwicklung und Demo

Alle Befehle werden in PowerShell ab der Repository-Wurzel ausgeführt.
Voraussetzungen: Java 23 oder neuer, Docker Desktop mit Compose >= 2.24.4 und Git.
Maven wird über den Wrapper gestartet. Für Browsertests zusätzlich Node.js und Edge;
die Anleitung steht unter [Tests](../src/test/README.md).

## Mailpit

Wenn Mailpit bereits auf SMTP-Port 1025 und Web-Port 8025 läuft, verwende diese
Instanz direkt. Starte keinen zweiten Container auf denselben Ports.

Für eine neue, eigenständige Demo-Umgebung:

```powershell
docker compose -f dev/compose.yaml up -d --wait
```

Oberfläche: [Mailpit](http://127.0.0.1:8025). SMTP: `127.0.0.1:1025`.
Die Compose-Datei bindet beide Ports nur an Loopback und richtet keinen Relay ein.
Konten dürfen fiktive Adressen wie `verwaltung@example.test` verwenden;
den Anmeldecode liest du in Mailpit. Der Mailpit-Container speichert Nachrichten
nur während seiner Lebensdauer, ohne persistentes Volume.

Die Camunda-Varianten mit Web Modeler veröffentlichen Mailpit dagegen auf
Web-Port **8075** und ebenfalls SMTP-Port **1025**. Diese Varianten und unsere
Demo-Compose dürfen nicht gleichzeitig denselben SMTP-Port belegen.
Der Camunda-Stack bleibt separat unter [infra/camunda](../infra/camunda/README.md).

Nur die mit dieser Datei gestartete Demo-Infrastruktur stoppen:

```powershell
docker compose -f dev/compose.yaml down
```

Das entfernt deren aufgefangene Mails. Ein bereits separat betriebenes Mailpit
wird damit weder übernommen noch gestoppt. Die Tests nutzen das eigene
Compose-Projekt `propertyflow-test` mit Ports 1026/8026.

## Mitarbeiterkonto konfigurieren und Mieterkonto eröffnen

Hinterlege deine Anmeldeadresse in der lokalen UTF-8-Datei
`.propertyflow-local/staff-bootstrap.properties`. Git ignoriert diesen Ordner.

```properties
propertyflow.staff.bootstrap.email=verwaltung@example.test
propertyflow.tenant.demo.email=mieter@example.test
```

Die Frontend-Demo lädt diese Adresse beim Start als Mitarbeiter-Testkonto.
Die optionale zweite Adresse aktiviert ein separates Demo-Mieterkonto; beide Adressen müssen unterschiedlich sein.
Verwende die jeweilige Adresse auf `/anmelden`, um einen Einmalcode anzufordern.
Nach Anmeldung führt das Mieterkonto zu «Meine Fälle», das Mitarbeiterkonto zur Mitarbeiter-Fallübersicht.
Ein Mieterkonto erhält keine Mitarbeiterrechte. Die konfigurierte Mieteradresse ist optional;
für eine Selbstregistrierung ist kein Konfigurationseintrag erforderlich.

So eröffnest du ein Mieterkonto und erstellst einen Fall:

1. Nach dem Start [Userkonto eröffnen](http://127.0.0.1:8081/registrieren) wählen.
2. Eine Testadresse eingeben und «Code anfordern» wählen.
3. Den Code in [Mailpit](http://127.0.0.1:8025) lesen und mit «E-Mail bestätigen» absenden.
4. Unter «Meine Fälle» erscheinen alle Anliegen mit derselben Kontaktadresse, auch frühere Gast-Anliegen.
5. «Neues Anliegen melden» wählen und Betreff, Objekt/Wohnung sowie Beschreibung ausfüllen.
   Die Kontaktadresse übernimmt der Server aus dem Konto.

Ein neuer Account wird erst nach erfolgreicher Codeprüfung aktiviert. Besteht das Konto
bereits, wird es nach Verifikation geöffnet; es entsteht kein Duplikat. Später ist die
Anmeldung über `/anmelden` möglich. Die Registrierung und Anmeldung verwenden dieselben
Code- und Anforderungslimits. Konten und Fälle verschwinden beim Neustart der Demo.

Oben rechts zeigen die Seiten das angemeldete Konto und «Abmelden». Abmelden beendet
die Browsersitzung und öffnet das öffentliche Mieterformular. Persönliche Fall-Links bleiben gültig.
Beim angemeldeten Mieter entfällt das E-Mail-Feld; der Server verwendet die Konto-Adresse,
auch bei manipulierten Formularwerten. Ohne Anmeldung bleibt die Kontaktadresse erforderlich.

## Anwendung starten

Für lokales Mailpit keine SMTP-Anmeldung und kein STARTTLS verwenden. Falls in
deinem Terminal noch Einstellungen eines externen Maildienstes gesetzt sind,
überschreibe sie für diesen Start:

```powershell
$env:SPRING_MAIL_HOST = '127.0.0.1'
$env:SPRING_MAIL_PORT = '1025'
$env:PROPERTYFLOW_SMTP_AUTH = 'false'
$env:PROPERTYFLOW_SMTP_STARTTLS = 'false'
$env:PROPERTYFLOW_STAFF_MAIL_FROM = 'propertyflow@example.test'
```

In IntelliJ die gemeinsame Run-Konfiguration **PropertyFlow - Frontend Demo**
auswählen und mit ▶ starten. Sie ist unter `.run/` versioniert und erscheint
auch im Services-Fenster, wenn dort Spring-Boot-Konfigurationen aktiviert sind.
Sie verwendet das Projekt-JDK, das Profil `frontend-demo`, Port 8081 und den
kurzen Windows-Socket-Pfad. Ein vorheriger Demo-Prozess auf Port 8081 muss
zuerst beendet werden.

Starten mit Java 23 oder neuer:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=frontend-demo'
```

Danach [http://127.0.0.1:8081](http://127.0.0.1:8081) öffnen.
Die Startseite verlinkt Neuerfassung, aktive und abgeschlossene Mieteransicht,
Mitarbeiterübersicht und Falldetail. Die Demo bindet nur an die lokale Adresse.
Der Mitarbeiterbereich führt zuerst zu `/anmelden`. Dort die hinterlegte Adresse
eingeben, den Einmalcode anfordern und den zugestellten Code eingeben. Der Code
gilt zehn Minuten mit höchstens fünf Versuchen. Pro Adresse ist eine Anforderung
pro Minute und bis zu fünfmal pro Stunde möglich.
56 fiktive Fälle decken Suche, Filter, Sortierung und Seitennavigation ab.
Änderungen liegen ausschliesslich im Arbeitsspeicher und verschwinden beim Neustart.

Nur UI3 bietet «Mit KI formulieren». Unter «Lokalen Mock testen» lassen sich
Erfolg, Fehler während der Ausgabe und eine HTML-Testzeichenfolge auswählen.
Abbrechen stoppt den lokalen Ausgabetimer; nur vollständige Vorschläge sind
übernehmbar. Übernahme und bewusstes Senden sind getrennte Aktionen.
Der Mitarbeiterbereich prüft die Anmeldung und die Rolle auch bei direkten
Aufrufen. Das öffentliche Mieterformular und die persönlichen Demo-Fall-Links
sind ohne Anmeldung nutzbar. Formular-CSRF, sichere Textausgabe, getrennte
Mieterdaten und Schreibsperren sind berücksichtigt. Produktive Falldatenhaltung,
Fallbenachrichtigungen, Camunda, produktive Mieterkontoverwaltung und MCP-Anbindung sind noch offen.

## Optional: eigenes SMTP-Postfach

Für den Versand an ein echtes Postfach stelle den SMTP-Zugang deines Maildienstes
vor dem Start bereit. Beispiel für einen Dienst mit SMTP-Authentifizierung und STARTTLS:

```powershell
$env:SPRING_MAIL_HOST = '<SMTP-Host>'
$env:SPRING_MAIL_PORT = '587'
$env:SPRING_MAIL_USERNAME = '<SMTP-Benutzer>'
$staffSmtpPassword = Read-Host 'SMTP-Passwort' -AsSecureString
$env:SPRING_MAIL_PASSWORD = [Net.NetworkCredential]::new('', $staffSmtpPassword).Password
$env:PROPERTYFLOW_SMTP_AUTH = 'true'
$env:PROPERTYFLOW_SMTP_STARTTLS = 'true'
$env:PROPERTYFLOW_STAFF_MAIL_FROM = '<erlaubte Absenderadresse>'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=frontend-demo'
```

Verwende die Host-, Port- und TLS-Vorgaben deines Maildienstes. Für die lokale
Demo genügt Mailpit; die Browsertests verwenden ausschliesslich Test-Mailpit.

## Weitere Infrastruktur

Die `compose.yaml` an der Repository-Wurzel enthält die bisherige PostgreSQL-
Konfiguration für den späteren Persistenzausbau. Sie wird für die Frontend-Demo
und die Browsertests nicht gestartet. Camunda wird über seine eigene Compose-
Konfiguration betrieben. Die neuen Dateien unter `dev/` starten nur Mailpit;
PropertyFlow läuft weiterhin in IntelliJ oder als Java-Prozess auf dem Host.

## Windows: Socket-Pfad

Falls Java mit «Unable to establish loopback connection» und
«UnixDomainSockets / Invalid argument» scheitert, nach dem ersten Build:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=frontend-demo' '-Dspring-boot.run.jvmArguments=-Djdk.net.unixdomain.tmpdir=C:\Dev\propertyflow\target'
```

Den absoluten Pfad bei einem anderen Checkout anpassen. Die IntelliJ-Demo-
Run-Konfiguration verwendet bereits den kurzen Projektpfad.
