# Ablösung der Frontend-Demo

Auftrag: Browser- und HTTP-Verträge auf das integrierte Backend übertragen; MCP bleibt ein separater Lauf. Die Modellevaluation bleibt zurückgestellt, automatische Abschlüsse gesperrt.

## Annahmen und Abgrenzung

- PostgreSQL, die sieben produktiven BPMN-Ressourcen, Camunda und Mailpit bilden den verbindlichen Integrationstest. H2 ersetzt diesen Nachweis nicht.
- Echte Fälle werden über die Fallaufnahme angelegt. Tests warten auf die bestätigte Prozessphase, bevor sie prozessabhängige Aktionen ausführen. Freie Demo-Statuswechsel werden durch explizite Camunda-Aktionen ersetzt.
- Mitarbeiter-Nachrichten und interne Memos sind getrennte Formulare. Die früheren Prüfungen des Intern/Extern-Umschalters werden auf beide Formulare und deren gegenseitige Schreibsperre übertragen.
- Backend-Einmalcodes sind an den Anmeldevorgang gebunden. Unterschiedliche Browserfenster dürfen sich ihre Codes nicht überschreiben. Die frühere globale Ablösung aller Codes einer E-Mail-Adresse ist kein Backend-Vertrag.
- Fremde bzw. nicht authentifizierte kontogebundene Mieterfälle liefern einheitlich HTTP 404. Mitarbeiterzugriffe verwenden den dokumentierten Unterschied zwischen Navigation und API-Abruf.
- Nach Abmeldung während einer Kontoaufnahme wird der alte CSRF-geschützte Auftrag mit 403 abgewiesen; seine Eingaben bleiben in der Fehleransicht erhalten. Eine Gastanlage verlangt ein frisches Formular und eine bewusst angegebene Kontaktadresse. Der Backend-Nachweis prüft auch, dass der abgewiesene Auftrag keinen Fall anlegt und der ursprüngliche Kontofall unverändert bleibt.
- Ohne JavaScript bleiben Mieteraufnahme und Mieternachrichten als Formulare nutzbar. Mitarbeiteraktionen benötigen die serverseitig geladene Prozessfreigabe.
- Die In-Memory-Klassen `DemoAccounts`, `DemoCases`, `StaffLogin`, die dazugehörige Anmeldung und das Profil `frontend-demo` blieben zunächst für den ausgeklammerten MCP-Prototyp bestehen. Am 25.11.2026 wurde der HTTP-Test auf das reguläre Backend mit OAuth und PostgreSQL umgestellt und das Demo-Profil nach erfolgreichem CI-Test entfernt. Siehe [MCP-HTTP-Test](../../src/test/README.md#mcp-http-und-lokale-netzwerkdiagnose).
- Die Listensortierung verwendet den angezeigten deutschen Status, bei Texten ohne Unterscheidung der Gross-/Kleinschreibung. Fehlende Wiedervorlagen stehen in beiden Richtungen am Ende; gleiche Werte werden nach Fall-ID stabil geordnet. Die PostgreSQL-Leseprüfung legt dafür Daten ausschliesslich im eigenen Testschema an; echte Statusübergänge bleiben Aufgabe der Camunda-Tests.
- Cookie-Attribute werden am tatsächlichen `Set-Cookie` geprüft: Browser-API beim Formularaufruf sowie Java-HTTP-Client bei der Mitarbeiter- und Mieter-Codebestätigung. Das lokale WebKit unter Windows meldet über `context.cookies()` bei einem nachweislichen `SameSite=Lax`-Header `None`; zusätzlich liefert die Browser-API bei der 303-Weiterleitung keinen Cookie-Header. Diese Browser-Metadaten ersetzen deshalb nicht den HTTP-Nachweis. `HttpOnly` wird zusätzlich im Browser geprüft.
- Entwicklungsdaten werden weder zurückgesetzt noch für Browserfixtures verwendet. Der Runner besitzt eigene Container, Ports und Datenbanken und entfernt nur diese Container.

## Übertragene Prüffamilien

| Bisheriger Bereich | Backend-Nachweis |
|---|---|
| `StaffLoginTest`, `StaffLoginQueueTest` | `StaffOtpSecurityTest`, `StaffOtpQueueTest`: Ablauf, Rate Limits, Queue, Rollenentzug, Einmaligkeit und konkurrierende Bestätigung |
| `TenantRegistrationTest` | `TenantOtpServiceTest`, `BackendAccessParityPostgresTest`, `AccountLifecyclePostgresTest`: Aktivierung erst nach Code, Persistenz, Kontotrennung und Eigentum |
| `SmtpDeliveryTest` | Echter SMTP-Adapter und `TenantOtpService`, einschliesslich Ablehnung, Timeout und Wiederherstellung; HTTP-Verträge separat gegen PostgreSQL |
| `CsrfWebTest`, `HttpProtectionTest`, `LoginRecoveryWebTest`, `StaffAccessWebTest` | `BackendAccessParityPostgresTest`, `StaffCaseWebTest`, `GuestCasePostgresTest`, `StaffAccountPostgresTest` und `AccountLifecyclePostgresTest` |
| `DemoRenderingTest` und Aufnahme-Browserfälle | `BackendAccessParityPostgresTest`, `intake-validation.test.mjs`, `integrated-ui.test.mjs`: echte Templates, Grenzlängen, Escape, Sitzung und Idempotenz |
| Demo-Fallmutationen, Konflikte und Nebenläufigkeit | `CaseDataPostgresTest`, `StaffDecisionPostgresTest`, `TenantMessagePostgresTest`, `StaffMessagePostgresTest`, `CaseWaitPostgresTest`, `CaseReopenPostgresTest` und die zugehörigen Camunda-Tests |
| Kernabläufe und fachliche Statuswechsel | `dev-runtime.test.mjs`, `integrated-ui.test.mjs`, `dev-accounts.test.mjs`: reale Zustellungen, manuelle Aktionen, Operate und Neustartpersistenz |
| Suche, Sortierung, Seitennavigation und Fokus | `integrated-ui.test.mjs`, `list-pagination.test.mjs`, `stage3/staff-reader.test.mjs` sowie `BackendAccessParityPostgresTest` mit tatsächlicher Reihenfolge aller sieben Spalten in beiden Richtungen, UND-Suche und stabilen Gleichständen |
| Schreibfehler, unbekannte Ergebnisse, Versionskonflikte und Doppeleingaben | Backend-Java-Verträge plus `stage3/staff-memo`, `staff-messages`, `tenant-messages`, `staff-review`, `staff-close` und `staff-lifecycle` |
| KI-Stream, Abbruch und Übernahme | `stage3/staff-formulation.test.mjs`, Backend-Formulierungsprüfungen und realer manueller Fallback in `dev-runtime.test.mjs` |
| Tatsächlicher Sitzungsablauf | `integrated-session-expiry.test.mjs`: beide Rollen, 65 Sekunden ohne HTTP-Aktivität, echte Servlet-Sitzungen, kein Löschen von Cookies |
| Barrierefreiheit, schmale Ansicht und Tastatur | `integrated-ui.test.mjs` mit Axe auf tatsächlichen Backend-Seiten |
| Lighthouse | `frontend-performance.mjs` verwendet den integrierten Teststack und echte Fall-URLs; lokal mit `PROPERTYFLOW_PERFORMANCE=true`, im Chromium-Pipelinejob automatisch |

Fehlerfenster mit kontrollierten HTTP-Antworten in `stage3` laden ausschliesslich die Backend-JavaScript-Dateien. Sie ersetzen keine Persistenz- oder Camunda-Prüfung, sondern ergänzen diese für reproduzierbare Netzabbrüche, verspätete Antworten und Rollenwechsel.

## Ausführung

Nach dem Maven-Build mit MCP-Ausschluss und Installation der gesperrten Browserwerkzeuge gemäss [Test-README](../../src/test/README.md):

```text
node src/test/browser/run-integrated-regression.mjs
node --test --test-concurrency=1 src/test/browser/stage3/*.test.mjs
```

Die Browserauswahl erfolgt über `PROPERTYFLOW_TEST_BROWSER` (Vorgabe `chromium`); `DEMO_BROWSER` wird nur als kompatibler bisheriger Variablenname akzeptiert. Die GitHub-Pipeline verwendet denselben Runner für Chromium, Firefox und WebKit. Der Runner ist auch aus GitLab ausführbar, sofern Java, Node, Docker Compose und die Playwright-Systemabhängigkeiten vorhanden sind. Er enthält keine GitHub-spezifischen Startvoraussetzungen. Für GitLab eignet sich beispielsweise ein isolierter Linux-Shell-Runner mit lokalem Dockerdaemon; die veröffentlichten Containerports müssen für den Testprozess über `127.0.0.1` erreichbar sein. Parallele Browserjobs benötigen getrennte Runner-/Dockerumgebungen, da die Testports fest sind.

## Bereinigung und gefundene Fehler

55 nicht mehr benötigte Dateien wurden entfernt: Demo-Controller und dessen Mitarbeiter-Interceptor, sieben Demo-Seiten, der Mock-Stream samt Tests, der bisherige gemeinsame Demo-JavaScript-Code, abgelöste Demo-Tests und Runner sowie die IDE-Demo-Startkonfiguration. Die oben genannten MCP-Abhängigkeiten bleiben erhalten.

Die Migration hat folgende tatsächliche Backend-Lücken geschlossen:

- Nachrichtenentwürfe bleiben bei verlorenem Zugriff erhalten, während weitere Übertragung und Hintergrundabrufe gesperrt werden.
- Mitarbeiter erhalten beim Verlassen mit ungesendetem Nachrichten- oder Memotext eine Textverlustwarnung.
- Sortier- und Seitenlinks aktualisieren die Liste ohne vollständiges Neuladen. Ein weiterhin vorhandener Falllink behält den Tastaturfokus; beim Wegfall geht der Fokus auf den Ergebnisbereich. Ein wieder sichtbarer Tab aktualisiert sofort.
- PostgreSQL sortiert Textspalten ohne Gross-/Kleinschreibung, Status nach deutschem Anzeigenamen und fehlende Wiedervorlagen stets zuletzt.
- Freigabeabfragen der Mitarbeiterformulare verwenden im Leerlauf den [in UI3 spezifizierten 20-Sekunden-Takt](../frontend/ansicht-03-mitarbeiter-falldetail.md). Bereits gestartete Aufträge behalten ihren schnellen Abgleich; unmittelbare Prüfungen vor Schreibaktionen bleiben erhalten. Die vorherigen unabhängigen 3-/5-Sekunden-Abfragen verhinderten vollständige Lighthouse-Messungen. Ungültige Messungen werden als Fehler gewertet.
- Lange Fallreferenzen und Kontomenüs verursachen in der schmalen Ansicht keinen horizontalen Überlauf.

## Prüfstand

- Vollständige Java-Regression unter Linux/Java 23 mit PostgreSQL, Camunda und Mailpit, MCP ausgeschlossen: **542 bestanden, 5 bewusst übersprungen**, keine Fehler. Modellevaluation und explizit aktivierbare Modellaufrufe bleiben deaktiviert.
- Finaler gezielter Build und erneuter Lauf unter Linux/Java 23: **je 36 Tests bestanden**, darunter die zusätzliche PostgreSQL-Sortierprüfung. Nach Ergänzung der Gastaufnahme- und Cookie-Nachweise bestanden nochmals alle sieben Zugriffstests. Das JAR enthält die sieben BPMNs und die aktuellen Backend-Skripte; die entfernten Demo-Controller und Demo-Ressourcen fehlen auch im Archiv.
- Ergänzende Backend-JavaScript-Fehlerfenster: **je 68 bestanden** in Chromium, Firefox und WebKit. Zwei reine JavaScript-Prüfungen bestanden ebenfalls.
- Lighthouse gegen den finalen integrierten Stack: **zwölf gültige Desktop-Messungen mit jeweils 100 Punkten**, drei pro Aufnahme-, Mieterfall-, Mitarbeiterlisten- und Mitarbeiterdetailseite. Die Hintergrundfunktionen bleiben während der Messung aktiv.
- Workflow-Lint, YAML-/OpenAPI-Syntax, JavaScript-Syntax und die aktualisierten Dokumentlinks geprüft.

Der finale integrierte Chromium-Gesamtlauf ist mit **19 ausgeführten Prüfungen** einschliesslich realem Sitzungsablauf und Neustartpersistenz bestanden. Die zwei modusabhängigen Überspringungen gehören zum gemeinsamen Kontentest: Er wird vor und nach dem Backendneustart jeweils im passenden Modus ausgeführt. WebKit ist in den bestandenen Teilprüfungen bestätigt: 16 Runtime-/UI-Prüfungen sowie drei Konten-, Neustart- und Sitzungsprüfungen. Nach der WebKit-spezifischen Logout-Synchronisierung bestand zusätzlich die gezielte Registrierungsprüfung in Chromium und WebKit. Der finale Firefox-Gesamtlauf ist ebenfalls mit **19 ausgeführten Prüfungen** und ohne Fehler bestanden. Ein konfigurierter Pipelinejob ist kein Nachweis eines ausgeführten Remote-CI-Laufs.


Der Devstack wurde mit dem finalen JAR und `-SkipBuild -SkipSeed` wieder gestartet.
PropertyFlow-Health, Camunda Operate und Mailpit antworteten mit HTTP 200; beim
Wiederanlauf waren die bestehenden 20 Entwicklungsfälle vorhanden. Es wurden keine
zusätzlichen Entwicklungsfixtures angelegt und keine Entwicklungsdaten zurückgesetzt.
