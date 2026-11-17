# Betriebsübersicht, BPMN-Ressourcen und integrierte CI

Aktualisierung: Die UI-Regression wird auf das integrierte Backend umgestellt. Massgeblich für den aktuellen Runner und die Entfernung der Demo-Oberflächen ist die [Übertragungsmatrix](frontend-demo-ablosung-2026-11-16.md); die untenstehende Ablöseplanung beschreibt den vorherigen Stand.


Stand: 16.11.2026. Ergänzung zur produktionsnahen Konsolidierung und zur Umsetzungsmatrix.

## Betriebsübersicht

Unter `/mitarbeiter/betrieb` können angemeldete Mitarbeitende auffällige Aufträge
lesen und den betroffenen Fall öffnen. Die Oberfläche ist im Mitarbeiterkopf über
«Aufträge prüfen» erreichbar. Die aktuelle Mitarbeiterrolle wird bei jedem Abruf
erneut geprüft. Mieter und persönliche Fall-Links erhalten keinen Zugang.

Die Abfrage liest ausschliesslich technische Referenzen, Fall-ID, Auftragsart,
Zustand und Zeitpunkt aus den bestehenden Tabellen. Nachrichtentexte, interne Memos,
Adressen, Zugangstokens, Prüfcodes und Identitätsbelege fehlen in dieser Projektion.
Es gibt keine neue Tabelle, keinen Statuswechsel und keinen Wiederholungsbefehl.
Ein Datenbankfehler liefert 503 statt einer scheinbar leeren Erfolgsliste.

Dokumentierte Betriebsannahmen:

- Blockierte Starts/Wiedereröffnungen und fehlgeschlagene oder ungewisse Mails
  erscheinen unabhängig von ihrem Alter.
- Seit mehr als fünf Minuten unbestätigte Aufträge erscheinen zur Prüfung. Die
  Zeitspanne ist eine Sichtbarkeitsschwelle und erklärt einen Auftrag nicht für
  fehlgeschlagen. Laufende Camunda-Aufgaben werden dadurch nicht verändert.
- Abgewiesene Befehle erscheinen sieben Tage lang. Die Liste zeigt die neuesten
  100 Einträge und weist auf weitere hin. Sie ersetzt kein vollständiges Auditarchiv.
- Überwacht werden Fallstart, Fallmail, Ersatzlinkmail, Falldatenbefehl,
  Mitarbeiternachricht, Wiedervorlagenbefehl, Mitarbeiterentscheidung und Wiedereröffnung.
  Weitere Workerfehler bleiben über Camunda/Operate zu prüfen.
- Bei ungewisser Zustellung muss der tatsächliche Versand zuerst geklärt werden.
  Es gibt bewusst noch keinen UI-Befehl, der einen unbekannten Ausgang als nicht
  gesendet behandelt. Ein auditierter Klärungs-/Wiederholungsablauf bleibt offen.

## Einzige Quelle der BPMN-Modelle

Die sieben BPMN-Dateien liegen unter `src/main/resources/bpmn` und sind dort
ausführbar. Der frühere Dev-Deployment-Schritt, der nur eine Laufzeitkopie auf
`isExecutable=true` setzte, entfällt. Das JAR enthält dieselben Dateien unter
`BOOT-INF/classes/bpmn`. Die Integrationstests laden diese Ressourcen; die
Bindungstests vergleichen weiterhin gezielte Test-Fixtures mit den kanonischen Modellen.

Die Verlagerung verändert weder Prozess-IDs, Call-Verträge, Fachabläufe noch
Diagrammkoordinaten. Vorher-/Nachhervergleich erlaubt ausschliesslich das bisher
beim Deployment gesetzte Ausführbarkeitsattribut. Die Bezeichnungen `Draft` und
`Entwurf` bleiben als historische Identifikatoren erhalten. Sie werden nicht als
Ausführbarkeits- oder Freigabestatus interpretiert.

Unter `docs/architecture/bpmn` bleiben Beschreibung und Versionsgeschichte mit
korrigierten Links. Es gibt keine zweite bearbeitete Modellkopie. Deployment bleibt
explizit; bestehende Prozessinstanzen werden durch die Verlagerung nicht migriert.
Die automatische Abschlusssperre bleibt unabhängig von `isExecutable` wirksam.

## Demo und Teststrategie

Für manuelle Demonstrationen ist die integrierte Anwendung der Standard:
PostgreSQL, Camunda, Mailpit, dauerhaft gespeicherte Fälle und echte Mitarbeiter-/Mieteraktionen.
`frontend-demo` ist dafür nicht erforderlich. Die bestehende Demo bleibt vorerst
als Quelle alter UI-Regressionen erhalten. Ihre vollständige Entfernung benötigt
die Übertragung der noch gebundenen Tests und eine bewusste Bereinigung der
Demo-Endpunkte, Mockdaten, Templates und Profilkonfiguration.

Die Ablösung erfolgt in dieser Reihenfolge:

1. Reale Mieter-/Mitarbeiterabläufe, Speicherung, Operate und Neustart im eigenen
   integrierten CI-Job absichern (in diesem Arbeitsstand ergänzt).
2. Die übrigen profilgebundenen Ansichten-/Anmelde-/Sitzungstests auf die echten
   Backendverträge übertragen und fehlende Szenarien in der Matrix sichtbar halten.
   Gezielte Browser-Fehlerfenster dürfen weiterhin isolierte HTTP-Antworten verwenden.
3. Den bisher ausschliesslich demonstrierten MCP-Zugang gesondert behandeln;
   seine Entfernung wäre noch kein Ersatz durch produktives OAuth/Delegation.
4. Erst nach nachgewiesener Testparität ungenutzte Demo-Controller, Datenhaltung,
   Templates und das Profil entfernen. Die gemeinsame Oberfläche bleibt erhalten.

H2 ersetzt die PropertyFlow-Datenbank nicht: Migrationen und Abfragen verwenden
PostgreSQL-spezifische Typen, Konfliktbehandlung und Sperrverfahren. Ein H2-Test
würde diese Persistenzgrenze nicht gleichwertig prüfen. Die neue integrierte Suite
verwendet PostgreSQL sowohl für PropertyFlow als auch für Camundas Sekundärspeicher.
Die ältere isolierte Workflow-Testkonfiguration nutzt intern weiterhin Camundas
H2-Sekundärspeicher; PropertyFlows Fachdatenbank bleibt auch dort PostgreSQL.

Der portable Einstieg `node src/test/browser/run-integrated-regression.mjs`:

1. prüft freie eigene Ports 5440, 8084, 8086, 26506, 1028 und 8028;
2. startet über `dev/compose.integration.yaml` einen eigenen Compose-Projektnamen
   mit kurzlebigen Testdatenbanken und Mailpit;
3. entpackt und deployt die sieben BPMN-Ressourcen aus dem gebauten JAR;
4. richtet ein synthetisches Verwaltungskonto ein und startet dasselbe Backend-JAR;
5. prüft Browserdialog, Formulierungsfehler mit manuellem Versand, Operate,
   Konten/Rechte/Linkersatz und danach die Konto-Fallzuordnung nach Backendneustart;
6. beendet nur die selbst gestartete Anwendung und Testcontainer. Entwicklungsdaten
   und die gewöhnliche Testumgebung auf 5434/8085 sind keine Testziele.

Artefakte liegen unter `target/integrated-e2e`. Der Runner benötigt Java 23+ mit
`jar`, Node 22+, Docker Compose und die installierten Playwright-Werkzeuge.
Der vorhandene GitHub-Workflow erhält dafür einen eigenen Job. Derselbe Einstieg
kann in einem GitLab-Runner mit diesen Werkzeugen und Zugriff auf Docker laufen;
eine Docker-Berechtigung und genug Speicher für Camunda müssen dort bereitstehen.
Die veröffentlichten Ports müssen vom Node-Prozess unter `127.0.0.1` erreichbar
sein, etwa auf einem GitLab-Shell-Runner mit lokalem Docker. Ein entfernter
Docker-in-Docker-Dienst benötigt eine angepasste Netz-/Adresskonfiguration und ist
mit den festen lokalen Adressen nicht ohne Weiteres gleichzusetzen.
Eine separate GitLab-Pipelinedatei wird nicht parallel zur vorhandenen GitHub-Pipeline eingeführt.

Gezielte UI-Tests für Timing, Timeout, Fehler und Konkurrenz bleiben ergänzend
bestehen. Sie benötigen keinen laufenden Demo-Server. Vollständige End-to-End-Tests
ersetzen diese reproduzierbaren Fehlerfenster nicht automatisch.

Kein realer Modellaufruf, keine Modellevaluation und keine Freischaltung automatischer
Abschlüsse sind Teil dieser CI. Ein lokal bestandener Lauf ist kein behaupteter
Remote-Pipeline-Erfolg; letzterer folgt erst nach einem tatsächlichen Push/CI-Lauf.

## Korrekturen aus der integrierten Prüfung

Der Spring-Starter 8.9.19 bindet `camunda.client.rest-address` und
`camunda.client.grpc-address`. Das frühere `camunda.client.base-url` wurde ignoriert.
Deshalb griffen die ersten beiden isolierten Läufe versehentlich auf die lokale
Standard-Engine zu. Beide Testfallinstanzen wurden anhand Startzeit, Ablauf und
Fallreferenz identifiziert, gegen die Entwicklungsdatenbank abgeglichen und gezielt
beendet. Dort existierte keiner der beiden Fälle. Historische, bereits beendete
Testprozesse bleiben in Operate nachvollziehbar; bestehende Entwicklungsdaten wurden
nicht gelöscht. Nach der Bereinigung meldete die Dev-Engine null aktive Incidents.

Alle betroffenen Konfigurationen verwenden jetzt die gültigen Eigenschaften.
`PROPERTYFLOW_CAMUNDA_URL` und `PROPERTYFLOW_CAMUNDA_GRPC_URL` werden auch mit dem
Devprofil übernommen. `CamundaEndpointConfigurationTest` prüft beide Profile mit
abweichenden Adressen gegen die echten Konfigurationseigenschaften des Starters.
Die CI-Engine erhält eigene REST- und gRPC-Ports. Referenz:
[Camunda-Konfiguration 8.9](https://docs.camunda.io/docs/8.9/apis-tools/camunda-spring-boot-starter/configuration/).

Ein weiteres Zeitfenster betrifft die Mitarbeiterprüfung: Der persistente Status
kann sichtbar sein, bevor die Camunda-Aufgabe lesbar ist. Die Oberfläche hält die
erneute Abfrage zugänglich und lädt eine erstmals verfügbare Aufgabe beim regulären
Aktualisieren nach. Eine ausgewählte Aufgabe, eine eingegebene Begründung oder eine
bereits abgesendete Entscheidung wird dabei nicht stillschweigend auf einen neuen
Vorgang übertragen. Vier gezielte Browserprüfungen sichern dies ab.
