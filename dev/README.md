# Lokale integrierte Entwicklung

Stand: 16.11.2026. Alle Befehle ab der Repository-Wurzel in **PowerShell 7**.
Voraussetzungen: Java 23 oder neuer, Docker Desktop mit Compose, Node.js 22 oder neuer.
Der Maven Wrapper ist enthalten. Für den separaten Browsertest zusätzlich die
[vorhandenen Playwright-Testwerkzeuge](../src/test/README.md) installieren.

## Starten und stoppen

```powershell
pwsh -File dev/start.ps1
```

Der Befehl startet die bestehende PropertyFlow-PostgreSQL-Datenbank, eine getrennte
Camunda-PostgreSQL-Datenbank, Camunda 8.9.19 mit Operate/Tasklist und Mailpit. Er deployt
alle sieben kanonischen BPMN-Modelle, baut das Backend, richtet das erste Mitarbeiterkonto
idempotent ein, startet PropertyFlow im Hintergrund und erzeugt einmalig sieben
synthetische Beispiele über echte HTTP-Befehle und Camunda-Worker.

| Oberfläche | Adresse / Zugang |
|---|---|
| Dev-Einstieg | <http://127.0.0.1:8081/> |
| Mieter: neues Anliegen | <http://127.0.0.1:8081/mieter/fall> |
| Mitarbeiter | <http://127.0.0.1:8081/mitarbeiter/faelle> |
| Camunda Operate | <http://127.0.0.1:8080/operate> – `demo` / `demo` |
| Camunda Tasklist | <http://127.0.0.1:8080/tasklist> – `demo` / `demo` |
| Mailpit | <http://127.0.0.1:8025> – keine Anmeldung |

Mitarbeiteradresse: `.propertyflow-local/staff-bootstrap.properties`.
Eine bestehende Konfiguration bleibt erhalten. Auf einer neuen Umgebung ist die
synthetische Adresse `verwaltung@example.test` voreingestellt. Die Dev-Anmeldeseite
zeigt die tatsächlich eingerichtete Mitarbeiteradresse unmittelbar an. Den angeforderten
Einmalcode in Mailpit lesen. Mieter erhalten einen persönlichen Fall-Link in der
Eingangsbestätigung und können darüber lesen und antworten; kein Konto erforderlich.

```powershell
# Nur das Backend beenden, beispielsweise vor einem Start aus IntelliJ:
pwsh -File dev/stop.ps1 -ApplicationOnly

# Alle zugehörigen Devdienste stoppen; Daten und Container erhalten:
pwsh -File dev/stop.ps1

# Ohne erneuten Paketbau starten:
pwsh -File dev/start.ps1 -SkipBuild

# Ohne Beispieldaten starten:
pwsh -File dev/start.ps1 -SkipSeed
```

Ein zweiter Start beendet keinen fremden Prozess und dupliziert keine fertigen Seeds.
Ist Port 8081 durch einen anderen Start belegt, diesen gezielt beenden. Für geänderten
Java-Code den eigenen Backendprozess stoppen und ohne `-SkipBuild` neu starten.
Logs: `.propertyflow-local/application.log` und `application-error.log`.

## Prozessstand im Camunda-Web-Interface

1. Im Mitarbeiterbereich einen Fall öffnen.
2. **Prozess in Camunda anzeigen** wählen. Der Link ermittelt die aktuell führende
   Instanz serverseitig, auch nach einer Wiedereröffnung.
3. In Operate mit `demo` / `demo` anmelden. Falls die Anmeldung zur Übersicht führt,
   den Fall-Link erneut öffnen.
4. Im BPMN-Diagramm den kleinen Pfeil **Open Aktiven Fall bearbeiten** am
   eingeklappten Teilprozess wählen, danach bei Bedarf **Reset diagram zoom**.
   Das Diagramm zeigt die aktuelle Position. Die Aktivität für die
   Mitarbeiterbearbeitung oder das Mieterwarten führt zum zugehörigen Unterprozess;
   dort ist die konkrete Aufgabe bzw. Warteposition sichtbar.

Fachliche Entscheidungen erfolgen in **PropertyFlow**. Native Aufgaben nicht direkt
in Tasklist abschliessen: PropertyFlow prüft und speichert Entscheidungsbezug,
Fallversion und Berechtigungen vor der Camunda-Bestätigung.

Unmittelbar nach einer Einreichung bleibt der Fall kurz «Eingegangen», während der
Startauftrag verarbeitet wird. Danach registrieren die Worker die führende Instanz.
Ohne KI führt die Abklärung zur Mitarbeiterprüfung. Operate kann Änderungen wegen
asynchroner Indexierung kurz verzögert anzeigen.

## Beispielsfälle und manuelle Prüfung

Die sieben `[Dev]`-Fälle liegen in folgenden Zuständen vor:

| Beispiel | Zielzustand |
|---|---|
| Heizung bleibt kalt | Mitarbeiterprüfung erforderlich |
| Fenstergriff klemmt | In Bearbeitung |
| Offerte für Gegensprechanlage | Wartet auf externe Rückmeldung |
| Termin für Kellerbesichtigung | Wartet auf Wiedervorlage, in sieben Tagen |
| Geräusch an der Lüftung | Wartet auf Mieterantwort |
| Briefkastenschloss ersetzt | Abgeschlossen |
| Türschliesser erneut auffällig | Nach Abschluss wiedereröffnet, In Bearbeitung |

Abgeschlossene Fälle über den entsprechenden Filter einblenden. Die privaten
Mieter-Links stehen zusätzlich in `.propertyflow-local/dev-cases.json`.
Diese Datei enthält Zugangslinks und gehört nicht in Git. Bereits fertig angelegte
Beispiele werden bei späteren Starts nicht zurückgesetzt oder überschrieben.
Manuelle Änderungen an den Fällen bleiben erhalten.

Für einen eigenen Durchlauf:

1. Mieteranliegen einreichen und Eingangsbestätigung in Mailpit öffnen.
2. Als Mitarbeiter den Fall öffnen, Prüfgrund lesen und «Bearbeitung fortsetzen» wählen.
3. Dringlichkeit setzen, ein internes Memo speichern und eine selbst formulierte
   Rückfrage mit «Antwort des Mieters erforderlich» senden.
4. Über den Mieter-Link antworten. Ohne KI führt die Antwort zur manuellen Prüfung.
5. Externes Warten oder Wiedervorlage setzen und den Unterprozess in Operate prüfen.
6. Vor einem manuellen Abschluss eine individuelle externe Abschlussmitteilung senden;
   danach den Abschluss separat bestätigen. Anschliessend bei Bedarf wiedereröffnen.

Die KI bleibt standardmässig deaktiviert; alle Beispiele funktionieren ohne Modell.
Automatische Abschlüsse bleiben fest gesperrt. Die fachliche Modellevaluation wird
nicht durch Devstarts ausgeführt. Siehe [KI-Tools und manueller Ersatzbetrieb](../docs/specifications/ki-tools.md).

## Speicherung und Konfiguration

| Daten | Speicher |
|---|---|
| Konten, Fälle, Nachrichten, Entscheidungen, Start- und Versandaufträge | bestehendes Volume `propertyflow-backend_backend-data`, PostgreSQL 16, Port 5433 |
| Camunda-Ausführungszustand | `propertyflow-dev_camunda-engine` und `propertyflow-dev_camunda-data` |
| Camunda-Suche und Web-Apps | `propertyflow-dev_camunda-postgres`, separate PostgreSQL-Datenbank |
| Abgefangene E-Mails | `propertyflow-dev_mailpit-data`, lokale SQLite-Datei |
| Passwörter, stabiler Link-Schlüssel, aktuelle Definitionsschlüssel, PID, Seed-Links | ignoriertes Verzeichnis `.propertyflow-local/` |

Alle veröffentlichten Ports sind ausschliesslich an Loopback gebunden. SMTP verwendet
`127.0.0.1:1025` ohne Relay; es werden keine externen E-Mails versendet. Die
Camunda-Client-API ist für diese lokale Umgebung ohne Anmeldung erreichbar. Der
Demo-Zugang und diese API-Konfiguration sind keine Konfiguration für öffentlichen Betrieb.

Das Startskript erzeugt fehlende private Dateien nur bei der Ersteinrichtung. Bestehende
Datenbankkennwörter und der Link-Schlüssel werden nicht verändert. Kein Reset, kein
`down -v`, keine nachträgliche Änderung angewandter Flyway-Migrationen.
Das Deployment verwendet die ausführbaren Quellen unter `src/main/resources/bpmn`
unverändert. Unveränderte Modelle behalten ihre Camunda-Version.

Für Camundas PostgreSQL-Anbindung siehe die [offizielle Konfiguration](https://docs.camunda.io/docs/8.9/self-managed/concepts/databases/relational-db/database-configuration/).
Mailpit verwendet den dokumentierten [dauerhaften SQLite-Speicher](https://mailpit.axllent.org/docs/configuration/email-storage/).

## IDE und getrennte Testumgebung

Einmal `dev/start.ps1` ausführen, danach `dev/stop.ps1 -ApplicationOnly`.
In IntelliJ **PropertyFlow - Dev (PostgreSQL + Camunda)** starten.
Die Infrastruktur läuft weiter. Bei Änderungen der BPMN-Quellen vorher
`dev/start.ps1 -InfrastructureOnly` ausführen. Die lokale Kontoeinrichtung muss
bereits erfolgt sein. Der normale Skriptstart bleibt der vollständige Ein-Befehl-Start.

Die Browserregression startet ihren eigenen integrierten Teststack auf Port 8084.
Die frühere Frontend-Demo-Startkonfiguration wurde nach der Testübertragung entfernt.

Die isolierten Testprojekte auf PostgreSQL 5434, Camunda 8085/26505 und Mailpit
1027/8027 bleiben unverändert. Integrationstests mit Tabellenbereinigung dürfen
niemals auf die Entwicklungsdatenbank 5433 zeigen.

```powershell
.\mvnw.cmd test '-Dtest=DevelopmentControllerTest'
$env:PROPERTYFLOW_DEV_SMOKE = 'true'
node --test src/test/browser/dev-runtime.test.mjs
```

Der bewusst aktivierte Browsertest verwendet die laufende Devumgebung, erzeugt einen
zusätzlichen synthetischen Fall und prüft den Dialog ohne KI sowie Operate. Er löscht
keine Daten. Ergebnis und Screenshots liegen unter `target/dev-verification/`.

## Noch getrennt zu betrachten

Mieterkonten, Kontenverwaltung und geprüfte Fallzugänge sind inzwischen im
integrierten Backend umgesetzt; die Bedienung steht im folgenden Abschnitt.
Die interaktive KI-Formulierung ist im Backend integriert; die fachliche
Modellqualität bleibt ungeprüft. Ohne konfigurierten Provider erscheint der Fehlerpfad. Die manuelle individuelle Nachricht vor dem Abschluss funktioniert.
Eine vollständige Produktionsabnahme aller Prozess- und Ausfallpfade steht weiterhin aus.


## Konten und administrative Fallzugänge

Mieter können unter <http://127.0.0.1:8081/registrieren> ein dauerhaftes Konto eröffnen
und sich später unter <http://127.0.0.1:8081/mieter/anmelden> anmelden. Codes liegen
in Mailpit. «Meine Fälle» zeigt ausschliesslich die Fälle mit der aktuell verifizierten
Kontaktadresse, einschliesslich früherer Gastfälle. Konten überstehen einen Neustart;
Browsersitzungen und ausstehende Codes werden danach neu angefordert.

Die berechtigte Verwaltung öffnet «Kontoverwaltung» im Mitarbeiterkopf. Neue Konten
erhalten ausschliesslich die Mitarbeiterrolle. Sperren und Rollenentzug wirken ab
der nächsten Anfrage auch in bereits offenen Sitzungen. Das letzte Verwaltungskonto
kann nicht gesperrt werden.

«Fallzugänge» ist die separate Verwaltung für Linkersatz und Adresskorrektur. Fall-ID
angeben, die unabhängige Identitätsprüfung dokumentieren, Code an die bestätigte
Kontaktadresse senden und mit diesem Code anwenden. In der lokalen Testumgebung liegt
auch dieser Code in Mailpit. Danach ist der alte Link gesperrt; der neue Link wird
an die bestätigte Adresse gesendet. Für synthetische Tests die Prüfung entsprechend
als Test dokumentieren. Es findet keine automatische Identitätsprüfung durch KI statt.

[Umsetzungsmatrix](../docs/use_cases/umsetzungsstand-2026-11-16.md) ·
[Annahmen](../docs/architecture/konten-und-zugangsverwaltung-2026-11-16.md)

Zusätzlicher Browsernachweis:

```powershell
$env:PROPERTYFLOW_DEV_SMOKE = 'true'
node --test src/test/browser/dev-accounts.test.mjs
```

Browserprüfungen mit demselben Mitarbeiterkonto nacheinander und mit mindestens einer
Minute Abstand zwischen Codeanforderungen ausführen. Der reguläre Missbrauchsschutz
bleibt auch im Devprofil aktiv.

## KI-Formulierung im Mitarbeiterdetail

Eine kurze externe Sachanweisung eingeben und «Mit KI formulieren» wählen.
«Abbrechen» beendet den Versuch; nur ein vollständiger Vorschlag lässt sich
übernehmen. Vor dem separaten Senden den Text prüfen. Interne Memos werden nicht
an das Modell übertragen. Bei standardmässig deaktivierter KI erscheint
«Die Nachricht konnte nicht formuliert werden.»; manuelles Senden funktioniert weiter.

Die Funktion nutzt den vorhandenen Spring-AI-Chat-Provider. Dessen bewusste Aktivierung
über `PROPERTYFLOW_TRIAGE_PROVIDER=openai` sowie die bestehenden
`spring.ai.openai`-Einstellungen betrifft auch die anderen KI-Dienste.
Für diese technische Umsetzung wurde kein realer Modellanbieter aktiviert.
[Grenzen und Annahmen](../docs/architecture/ki-formulierung-2026-11-16.md)

## Isolierte CI-Prüfung

Die manuelle Demo läuft im integrierten Backend mit PostgreSQL, Camunda und Mailpit.
Für die Pipeline steht zusätzlich `node src/test/browser/run-integrated-regression.mjs`
bereit; Voraussetzungen und getrennte Ports stehen in [der Testanleitung](../src/test/README.md#isolierte-integrierte-browserprüfung-für-ci).
`frontend-demo` bleibt nur als isolierte Abhängigkeit des zurückgestellten MCP-Prototyps erhalten. Sämtliche UI-Regressionen verwenden das integrierte Backend; siehe [Übertragungsmatrix](../docs/architecture/frontend-demo-ablosung-2026-11-16.md).

Camunda-Adressen werden als `camunda.client.rest-address` beziehungsweise
`camunda.client.grpc-address` konfiguriert. Die Backendprofile übernehmen dafür
`PROPERTYFLOW_CAMUNDA_URL` und `PROPERTYFLOW_CAMUNDA_GRPC_URL`. Das frühere
`camunda.client.base-url` war für den verwendeten Starter unwirksam.
