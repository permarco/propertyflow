# Backend-Umsetzungsplan PropertyFlow

Stand: 14.11.2026 · Ausgangscommit: `dbbe652` plus aktueller Arbeitsbaum.

**Status: Vorschlag zur gemeinsamen Umsetzung, keine Freigabe neuer Fachregeln.** Dieser Plan ändert keine ADR, keinen HTTP-Vertrag und keinen Code. Abweichungen und offene Entscheidungen sind unten ausdrücklich bezeichnet. Der Plan wird nach jedem abgenommenen Abschnitt anhand der tatsächlichen Ergebnisse fortgeschrieben.

## 1. Ziel und Vorgehen

PropertyFlow wird schrittweise von der lokalen Frontend-Demo zu einem Backend mit PostgreSQL und Camunda entwickelt. Die bestehende Oberfläche und ihre Verhaltenstests dienen als Ausgangspunkt. Fachmodule bleiben in einer Spring-Boot-Anwendung. PostgreSQL hält die fachlichen Daten; Camunda steuert den Fallprozess und veranlasst nach Intake die Statuswechsel.

Zuerst stimmen wir den fachlichen Hauptprozess ab. Anschliessend implementieren wir einen kleinen vollständigen Abschnitt und testen ihn tatsächlich mit PostgreSQL und Camunda. Wir warten weder mit jeder Implementierung auf die vollständige Modellierung aller Sonderfälle noch bauen wir alle Controller und Entities vor dem ersten Integrationstest.

**Erstes Ziel:** Ein Mieter reicht einen Fall ein. PropertyFlow bestätigt ihn erst nach dauerhafter Speicherung. Wiederholungen erzeugen keinen zweiten Fall. Camunda übernimmt den Fall nachgelagert, lässt seine Dringlichkeit bewerten und führt ihn zur ersten Mitarbeiterprüfung. Neustarts und erneut zugestellte Jobs verlieren keine bestätigten Daten und duplizieren keine fachlichen Änderungen.

Die generelle Weiterleitung auch erfolgreich bewerteter Fälle zur Mitarbeiterprüfung ist für diesen ersten Ausbau eine vorgeschlagene Begrenzung. Sie ersetzt nicht die vorgesehenen automatischen Rückfragen und Systemabschlüsse; deren Regeln werden später modelliert und umgesetzt.

## 2. Verifizierter Ausgangspunkt

| Bereich | Im untersuchten Arbeitsbaum vorhanden | Noch zu leisten |
| --- | --- | --- |
| Fallverwaltung | `caseprocessing/demo/DemoController`, `DemoCases`: Anlage, Listen, Nachrichten, Statusaktionen und Wiederholungsschutz im Speicher | Dauerhafte Datenhaltung, reguläre Anwendungsdienste und prozessgeführte Aktionen |
| Intake | `intake/contract/CaseDescription`: gemeinsame Normalisierung und Grenze von 2’000 UTF-16-Codeeinheiten | Eigener Annahmedienst und HTTP-Adapter gegen den öffentlichen Fallservice |
| Access | OTP-Anmeldung, Registrierung, Sessions, `EmailAddress`, SMTP-Adapter; Konten in `DemoAccounts` | Persistente Konten, aktueller Berechtigungskontext und reguläre Profilkonfiguration |
| Triage | `AnalyzeTool`, `AnalysisModel`, `SpringAiAnalysisModel`; validierte vier Dringlichkeitsstufen und typisierte Fehler | Spring-Verdrahtung für den regulären Ablauf, Worker, Speicherung und Ausfallbehandlung |
| MCP | Uncommittete bzw. teilweise gestagte Dateien `FallstandTool`, `McpConfiguration`, `DemoMcpGrants`, Controller, Template und HTTP-Test | Anbindung an regulären Lesedienst, produktiver delegierter Zugriff; Teststatus neu feststellen |
| Datenbank | JPA, PostgreSQL und Flyway in `pom.xml`; Root-Compose mit PostgreSQL 16; Verzeichnis `db/migration` | Fachliche Migrationen, Entities, Repositories, persistentes Volume und isolierte Testkonfiguration |
| Laufzeit | Standardkonfiguration nutzt H2 im Speicher; `frontend-demo` deaktiviert Datenbank-/Flyway- und Camunda-Anbindung | Explizites reguläres Profil mit PostgreSQL und kontrolliertem Startverhalten |
| Camunda | Starter-Abhängigkeit, eigene Infrastruktur und Infrastruktur-Test-BPMNs | PropertyFlow-BPMN, fachliche Worker und Startauftragsverarbeitung |
| Tests | Umfangreiche Demo-, Zugriffs-, Browser- und Triage-Tests | PostgreSQL-, Prozess-, Neustart- und Wiederholungsnachweise des regulären Backends |

**Keine Tests wurden für diesen Plan ausgeführt.** Der früher berichtete MCP-Testfehler ist ein Hinweis aus dem anderen Chat, kein erneut verifiziertes Testergebnis. Dass der aktuelle Testcode fremde Fälle und Widerruf prüft, beweist noch keinen erfolgreichen Lauf.

Der Arbeitsbaum enthält fremde laufende Änderungen, unter anderem an `pom.xml`, MCP, DemoCases und Abgabedokumentation. Vor Implementierungsbeginn den Besitz dieser Änderungen klären; nichts pauschal zurücksetzen, stagen oder übernehmen.

## 3. Entscheidungen vor dem jeweiligen Implementierungsschritt

| ID | Entscheidung | Vorschlag / Grenze | Spätestens nötig |
| --- | --- | --- | --- |
| D1 | Erster aktiver Status | `In Abklärung`; Prozesszuordnung und erster aktiver Status gemeinsam speichern, damit `Eingegangen` nicht trotz bestätigter Registrierung bestehen bleibt. Bekannter vorrangiger Prüfbedarf muss berücksichtigt werden. ADR-002 legt bisher nur die Zuordnung fest; FALL-05 bezeichnet den Einstieg noch als Entwurf. | Vor BPMN-/Worker-Implementierung |
| D2 | Ausgang der ersten erfolgreichen Bewertung | Für den ersten Ausbau Mitarbeiterprüfung als klarer Haltepunkt. Empfehlung und automatische Fortsetzungen danach ergänzen. | Vor erstem ausführbaren BPMN |
| D3 | Java-Modell und JPA | Pragmatisch ein internes Fallmodell mit JPA-Mapping verwenden, ohne gleichartige Klassen nur für Mapping zu duplizieren. Öffentliche Commands/Results bleiben separate Verträge. Bestehende Dokumentation zur möglichst frameworkunabhängigen Domäne gezielt präzisieren. | Vor Entities |
| D4 | Identität und Wiederholung | Bestehende Regeln beibehalten: Gast über ursprüngliche Sitzung, Konto über serverseitig ermittelte Konto-ID. Kein frei übergebener Benutzer als Identitätsnachweis. Verhalten nach Gast-Sitzungsverlust dokumentieren. | Vor Anlage-Endpunkt |
| D5 | Zeitbudgets und Betriebsparameter | Modelltimeout, Jobtimeout, Wiederholungsabstand, Starter-Intervall, parallele Verarbeitung und Antwortzeitbudget festlegen. Keine unbegrenzten HTTP-Wartezeiten. | Vor Ausfalltests |
| D6 | Schlüssel und Fall-Links | Schlüsselbereitstellung für verschlüsselte Versandkopie, Rotation und Wiederherstellung festlegen; kein produktives Klartexttoken in der Falldatentabelle. ADR-004 bleibt verbindlich. | Vor regulärem Tokenzugriff |

Fallbesitz ist inzwischen geklärt: `caseprocessing` vergibt die Case-ID und speichert Fall und Startauftrag. `intake` ruft diesen Dienst synchron auf. Dies nicht erneut als offene Grundsatzfrage behandeln.

## 4. Zuständigkeiten und konkrete Bausteine

Namen neuer Java-Klassen sind Arbeitsvorschläge, keine bereits vorhandenen APIs. Keine leeren Packages auf Vorrat anlegen.

| Modul / Ort | Vorgeschlagene Bausteine | Verantwortung |
| --- | --- | --- |
| `intake/service` | `SubmitCaseService` | Kanalbezogene Eingaben normalisieren und validieren; Fallanlage aufrufen |
| `intake/controller` | Formularadapter | Bestehendes `POST /mieter/fall`, HTML-Fehler und 303 gemäss OpenAPI; kein neues JSON-API erfinden |
| `caseprocessing/contract` | `CreateCaseCommand`, `CaseAccepted`, `CaseSummary`, Zugriffskontext | Stabile öffentliche Eingaben und Ergebnisse ohne Entities, Servlet- oder Camunda-Typen |
| `caseprocessing/service` | `CreateCaseService`, `ReadCaseService`, `RegisterCaseProcessService`, `RecordAssessmentService` | Fachregeln, Berechtigungen, Transaktionen und Wiederholungsschutz |
| `caseprocessing/persistence` | Fall-/Nachrichten-/Auftragsmodell und Repositories | Modulinterne Speicherung und Constraints; kein Zugriff fremder Module auf Repositories |
| `caseprocessing/workflow` | `CaseProcessStarter`, Registrierungs- und Bewertungsworker | Camunda-Protokoll in Fachservice-Aufrufe übersetzen; keine Fachregeln im Worker |
| `access/contract`, `access/service`, `access/persistence` | Verifizierter Aufrufer, Kontozugriff, persistentes Konto | Identität und aktuelle Rechte aus vertrauenswürdigem Kontext bestimmen |
| `caseprocessing/mcp` | Vorhandenes `FallstandTool` umbinden | Nur Adapter zum geprüften `ReadCaseService`; kein Zugriff auf `DemoCases` |
| `triage/service`, `triage/ai` | Vorhandene Klassen weiterverwenden | Analyse ohne Speicherung; Provider hinter `AnalysisModel` |
| `src/main/resources/processes` | `propertyflow-case.bpmn` | Vorgeschlagener Ablageort des ersten fachlichen Prozesses |
| `src/main/resources/db/migration` | Versionierte Flyway-Skripte | Fachschema; bestehende Migrationen später nicht überschreiben |

Der HTTP-/MCP-Chat besitzt die Adapter und deren Protokolltests. Der Backend-Chat besitzt Services, Entities, Migrationen, Prozess und Worker. Ein gemeinsamer Vertragscommit wird vor der parallelen Implementierung übernommen. `pom.xml`, Konfiguration, gemeinsame Contracts und BPMN werden jeweils nur von einem Chat bearbeitet; Abweichungen zuerst abstimmen.

## 5. Daten und Transaktionsgrenzen

Minimaler erster Datenumfang, genaue Tabellen-/Spaltennamen werden beim Implementieren festgelegt:

| Datenbestand | Benötigte Inhalte und Schutz |
| --- | --- |
| Fall | Interne ID, eindeutige Case-ID, ursprüngliche Angaben, aktuelle Kontaktadresse, fachlicher Status, wirksame Dringlichkeit/Quelle, Version, Zeitpunkte |
| Nachricht / Verlauf | Ursprüngliche Beschreibung genau einmal im externen Verlauf; spätere Nachrichten und strukturierte Status-/Dringlichkeitsänderungen unterscheidbar |
| Persönlicher Zugang | Token-Hash, verschlüsselte Versandkopie, Gültigkeit/Widerruf; keine Tokens in Logs |
| Wiederholungszuordnung | Kontext, Operationskennung, normalisierte Vergleichsdaten bzw. definierter Fingerprint, Ergebnisreferenz; eindeutiger Constraint auf Kontext und Operation |
| Prozessstartauftrag | Feste Startkennung, Fallreferenz, Zustand, Versuchs-/Fehlerdaten; technische Reservierung für parallele Starter |
| Prozesszuordnung | Fall, Startkennung und bestätigter Prozessinstanzschlüssel; Schutz gegen Überschreiben durch andere Instanzen |
| Bewertung | Auslöser/Jobbezug, Ergebnis oder kontrollierter Fehler, Herkunft und Übernahmezustand; eindeutiger fachlicher Wiederholungsschlüssel |
| Konto / Rollen | Im Access-Modul; verifizierte Adresse, aktive Rechte und nachvollziehbare Änderungen |
| Benachrichtigungsauftrag | Fachlicher Auslöser und eindeutige Referenz, Zustellzustand; Versand nach Speicherung |

**T1 – Annahme:** Fall, erste Mitteilung/Referenz, Zugang, Idempotenzzuordnung, Startauftrag und erforderlicher Annahme-Mailauftrag werden lokal atomar gespeichert. Kein Camunda-, Modell- oder SMTP-Aufruf in dieser Transaktion. Erst nach erfolgreichem Commit antwortet HTTP mit 303.

**T2 – Registrierung:** Prozesszuordnung und Erledigung des Startauftrags gemeinsam speichern. Den ersten aktiven Status nach D1 integrieren. Jobabschluss erst nach Commit melden.

**T3 – Bewertung:** Benötigte Daten lesen, Transaktion beenden, Modell aufrufen, dann Ergebnis in einer neuen kurzen Transaktion prüfen und speichern. Verspätete Ergebnisse dürfen manuelle Einstufungen oder Abschluss nicht überschreiben. Historie und gegebenenfalls Benachrichtigungsauftrag atomar mitführen.

Der Startauftrag ist die konkrete zuverlässige Übergabe an Camunda nach dem Outbox-Prinzip; ein zusätzlicher Broker ist dafür nicht geplant. Kein Exactly-once-Versprechen für Netzwerkaufrufe. Fachliche Mehrfachwirkungen werden über persistente Schlüssel und Constraints verhindert. Ein Absturz nach Modellantwort vor Ergebnis-Commit kann einen erneuten Modellaufruf verursachen; nach gespeichertem Ergebnis muss eine wiederholte Jobzustellung dieses wiederverwenden.

## 6. Arbeitspakete und Abnahmetests

### P0 – Ausgangspunkt sichern und Prozessabschnitt abstimmen

- Aktuellen MCP-Zwischenstand gemeinsam mit seinem Bearbeiter getrennt sichern und Teststatus feststellen.
- D1/D2 entscheiden und den ersten BPMN-Abschnitt fachlich zeichnen: Start → Zuordnung/Abklärung → Bewertung → Ergebnis übernehmen → Mitarbeiterprüfung.
- Passende Fehlerwege und den Haltepunkt bestimmen. Eine offene User Task ist ein fachlicher Wartezustand, kein künstlich abgeschlossener Fallprozess.
- Verträge für Anlage, Lesen, Registrierung und Bewertungsübernahme gemeinsam festhalten; HTTP bleibt beim vorhandenen Formularvertrag.

**Abnahme:** Keine konkurrierenden Statusdefinitionen; Services haben konkrete Eingaben, Ergebnisse und Fehler; alle Beteiligten verwenden denselben Vertragsstand. Relevante vorhandene Java-Tests als Baseline laufen lassen, bekannte Fehler separat dokumentieren.

### P1 – PostgreSQL und erstes Schema

- Reguläres Profil mit PostgreSQL einführen; Demo bleibt ausdrücklich getrennt. Kein stiller H2-Fallback bei Datenbankausfall.
- Root-Compose für verlässliche lokale Speicherung konkretisieren, Testdatenbank mit eigenem Namen/Volume/Port isolieren. Keine Nutzung der Camunda-internen DB als PropertyFlow-Schema.
- Erste Flyway-Migration und JPA-Modell für den benötigten Datenumfang erstellen; Hibernate validiert das Schema statt es automatisch zu erzeugen.
- PostgreSQL-Integrationstests über Testcontainers oder eine explizit isolierte Compose-Testumgebung einrichten. Die gewählte Variante dokumentieren, keine Versionsannahmen ungeprüft übernehmen.

**Tests:** Leere DB migriert erfolgreich; erneuter Start verändert Migrationen nicht; Schema passt zu JPA; Constraints verhindern doppelte Kennungen; Daten überleben App-Neustart und DB-Container-Neuerstellung mit demselben Volume.

### P2 – Dauerhafte Fallanlage und geschütztes Lesen

- Fachlogik gezielt aus `DemoCases` übernehmen, nicht die gesamte Klasse umbenennen.
- Vorhandene `EmailAddress`- und `CaseDescription`-Regeln wiederverwenden.
- `CreateCaseService` mit T1 und `ReadCaseService` implementieren. Mitarbeiter-/Mieteransichten verwenden berechtigungsabhängige Projektionen; interne Daten nicht erst im Browser ausblenden.
- Annahmeformular und Token-Leseseite an die neuen Services anschliessen. Kontokontext nur aus geprüfter Identität; kontobezogene End-to-End-Abnahme folgt nach P3.

**Tests:**
- Gültige Gastanlage liefert 303, gültigen persönlichen Link, eine Case-ID, einen Fall und einen Startauftrag.
- Ungültige Eingabe/CSRF erzeugt keinen Teilbestand; korrigierte Wiederholung bleibt möglich.
- Identischer Retry nach verlorener HTTP-Antwort liefert denselben Fall; abweichender Inhalt ergibt 409.
- Zwei gleichzeitige identische Einreichungen ergeben genau einen Fall; unterschiedliche Konten/Gastsitzungen teilen keine Ergebnisreferenz.
- Erzwungener Fehler innerhalb T1 rollt alle zugehörigen Daten zurück; keine Erfolgsantwort.
- Ungültiger/widerrufener Token erlaubt keinen Zugriff; Logs enthalten keine geheimen Links oder Freitexte.
- Nach Neustart lässt sich der bestätigte Fall lesen. Gast-Retry über eine verlorene Sitzung wird nicht entgegen dem Vertrag versprochen.

### P3 – Regulärer Kontozugang und Zugriffskontext

- `StaffLogin` vom konkreten `DemoAccounts`-Speicher entkoppeln; Konten und Rollen dauerhaft im Access-Modul speichern.
- Vorhandenen OTP-/SMTP-Ablauf übernehmen, mit expliziter Entscheidung über Sitzung und Challenge-Verhalten bei Neustart. Kontopersistenz bedeutet nicht automatisch persistente Browsersitzungen.
- Aktuelle Berechtigungen im Fachservice prüfen; der Web-Interceptor allein reicht für MCP/Worker nicht.

**Tests:** Registrierung und Login über Mailpit; Ablauf/Einmalverwendung/Versandfehler; Konto bleibt nach Neustart erhalten; erneute Anmeldung sieht dieselben eigenen Fälle; fremde Fälle bleiben gesperrt; Konto-Adresse wird serverseitig übernommen; Rollenentzug wirkt beim nächsten geschützten Aufruf.

**Meilenstein M1:** Dauerhafte Fallannahme und geschütztes Lesen mit Gast- und Kontozugang. Camunda darf abgeschaltet sein. Dies ist noch keine vollständige Ablösung aller Demo-Aktionen.

### P4 – Camunda-Start und Prozesszuordnung

- Kleines BPMN-Modell deployen und seine tatsächlichen Jobtypen mit den Workern abstimmen.
- Starter liest persistierte Aufträge und verwendet den SS-003-Vertrag. Unterstützung und Aktivierung der beschriebenen Business-ID-Eindeutigkeit gegen die tatsächlich eingesetzte Camunda-Version prüfen.
- Parallele Starter koordinieren; kein Datenbank-Lock über einen langsamen Netzwerkaufruf halten. Wiederanlauf nach abgelaufener Reservierung ermöglichen.
- Registrierungsworker führt T2 aus. Eine andere Instanz darf eine vorhandene Zuordnung nicht überschreiben.
- Prozessinstanzschlüssel verlustfrei behandeln, insbesondere bei Browser-/JSON-Grenzen.

**Tests:** Camunda zehn Minuten unerreichbar: 20 von 20 gültigen Annahmen gemäss OpenAPI bleiben nach Neustart gespeichert, mit genau einem offenen Startauftrag je Fall. Nach Wiederanlauf werden sie übernommen. Zusätzlich verlorene Startantwort, parallele Starter, DB-Fehler bei Zuordnung, erneute Zustellung nach Commit und konkurrierende abweichende Instanz prüfen. Fachliche Bearbeitung bleibt vor Registrierung bzw. bei Camunda-Ausfall gesperrt; Lesen bleibt möglich.

### P5 – Erstbewertung und erste Mitarbeiteraufgabe

- `AnalyzeTool` als Fachbaustein verdrahten; keine neue zweite KI-Implementierung erzeugen.
- Worker lädt Beschreibung über Fallservice, ruft Triage mit Timeout auf und übergibt Ergebnis an `RecordAssessmentService`.
- Kontrollierter Modellfehler ist ein fachlicher Prüfgrund; ein Speicherfehler benötigt technische Wiederholung. Diese Wege im BPMN unterscheiden.
- Mitarbeiterprüfung als vereinbarten Haltepunkt öffnen. Status und gespeicherter Fall müssen dazu passen.

**Tests:** Alle vier gültigen Stufen; unzulässige Ausgabe; Modelltimeout; kein erfundenes Niedrig-Ergebnis; Ergebnis-Commit vor Jobabschluss; erneute Zustellung nach verlorenem Abschluss erzeugt keinen zweiten Verlauf/Mailauftrag und keinen erneuten Modellaufruf bei bereits vorhandenem Ergebnis. Manuelle Einstufung und abgeschlossene Fälle gegen verspätete Ergebnisse schützen. Ein langsames Testmodell verzögert die Annahmebestätigung nicht.

**Meilenstein M2:** Erster kompletter Weg von Browser über PostgreSQL und Camunda bis zur Mitarbeiterprüfung. Zuerst deterministischer Modelladapter für Integrationstests; zusätzlich dokumentierter echter Modellversuch für den KI-Nachweis.

### P6 – Benachrichtigungen und manueller erster Bearbeitungsschritt

- Annahme-/Statusmailaufträge und Mail-Worker gemäss BEN/SS-002 anbinden. Vorher bleibt ein Auftrag offen; ein erfolgreicher Versand darf nicht vorgetäuscht werden.
- Erste User-Task-Aktion „Bearbeitung fortsetzen“ umsetzen; konkrete Aufgabe, Benutzerrechte und Fallversion prüfen.
- Keine freie Statusauswahl aus der Demo ins reguläre Backend übernehmen.

**Tests:** Mailpit empfängt vorgesehene Nachricht ohne interne Daten; temporärer SMTP-Ausfall verliert keinen Auftrag; Wiederholung dupliziert keinen logischen Auftrag. Unklarer SMTP-Ausgang kann externe Doppelzustellung bedeuten und wird entsprechend dokumentiert. Alte User Task aus zweitem Tab darf neue Aufgabe nicht abschliessen; Camunda-Timeout wird nicht als bestätigter Erfolg ausgegeben; „In Bearbeitung“ erst nach gespeichertem Statuswechsel sichtbar.

### P7 – MCP auf reguläre Dienste umstellen

- Vorhandenes `FallstandTool` vom Demo-Speicher lösen und denselben autorisierenden Lesedienst wie Web verwenden.
- Regulären OAuth-/Delegationsweg nach ADR-005 und MCP Guidelines umsetzen; manuell erzeugte Demo-Bearer-Tokens nicht als vollständige OAuth-Implementierung deklarieren.
- Audit mit Benutzer, Client, Aktion, Fallreferenz und Ergebnis ohne Geheimnisse; technische Fehlermeldungen bleiben neutral.

**Tests:** Echter HTTP-MCP-Aufruf ohne Token, mit ungültigem/abgelaufenem/widerrufenem Zugriff und ohne delegiertes Recht wird abgewiesen. Mieter A liest eigenen Fall; A liest B nicht; unbekannte und fremde Case-ID verraten keine fremden Inhalte. Kontosperre/Adresskorrektur greifen auch bei bestehender Delegation. Direkter Service-Test ergänzt den Transporttest. Ausgabe enthält nur vereinbarte Felder.

P7 kann parallel zu P4/P5 laufen, sobald P2/P3 und der gemeinsame Lesedienst stabil sind. Der Nachweis über eine reine Demo-Konfiguration ersetzt diese Abnahme nicht.

## 7. Anschliessende Erweiterungen des Gesamtprozesses

| Reihenfolge | Umfang | Wichtigster Abnahmetest |
| --- | --- | --- |
| E1 | Neue Mieternachrichten, Neubewertung und einzelne Rückfragen, UC-009/017/028 | Teilantwort erledigt nur passende Frage; Nachricht während anderer Arbeit bleibt verarbeitbar; keine zweite führende Instanz |
| E2 | Externe Veröffentlichung, interne Memos und Zuordnung, UC-010/011/012 | Erfolg erst an vertraglich definierter Speichergrenze; Memo weder mieteröffentlich noch im Kommunikations-LLM-Kontext |
| E3 | Wiedervorlage, UC-013 | Mieternachricht hebt Timer auf und löst sofort Prüfung aus; gleichzeitig fälliger/alter Timer erzeugt keine zweite Änderung |
| E4 | Kritische Entscheidungen und Abschluss, UC-027/014 | Freigabe gilt nur für konkrete Aktion/Fallstand; Abschluss sperrt Schreiben und beendet offene Wartezustände |
| E5 | Wiedereröffnung, UC-015 | Gleiche Case-ID, neue Bearbeitungsphase; alte Timer/Worker dürfen sie nicht verändern; Fortsetzung beendeter Prozessinstanz zuvor entscheiden |
| E6 | Empfehlung, RAG und zulässige Systemaktionen, UC-018/021 | Quellen-/Berechtigungsprüfung, fehlende Evidenz, menschliche Freigabegrenzen; Retrieval-Evaluation für Block 4 |
| E7 | KI-Formulierung, UC-004 | Streaming/Abbruch auch vor erstem Text, keine automatische Veröffentlichung, keine internen Memos im Kontext |

„Wartet auf externe Rückmeldung“, automatische Veröffentlichungen und sichere E-Mail-Annahme benötigen noch konkrete Fachverträge. Nicht anhand eines Statusnamens einen technischen Ablauf erfinden. Die acht Status sind keine lineare Kette von acht BPMN-Tasks.

## 8. Umstellung und Tests ohne parallele Wahrheiten

- Demo und reguläres Profil dürfen nicht dieselben Routen gleichzeitig registrieren. Beide nutzen nie unterschiedliche Speicher für denselben realen Fall.
- Während der Umstellung bestehende Templates/JavaScript nur soweit nötig adaptieren. Noch nicht unterstützte Aktionen im regulären Profil klar deaktivieren statt an `DemoCases` weiterzureichen.
- Testdaten aus Demo gezielt als versionierte Fixtures übernehmen; keine Behauptung einer Migration bisheriger flüchtiger Nutzerdaten.
- Nach jeder Fähigkeit relevante Unit-/MVC-Tests und neue Integrationstests ausführen. Browserregressionen für betroffene Wege gegen reguläres Profil ergänzen.
- PostgreSQL-Semantik mit PostgreSQL prüfen; H2 beweist keine Unique-/Lock-/Transaktionskonkurrenz unter PostgreSQL.
- Test-Mailpit und Testdatenbank getrennt von Entwicklung. SMTP-End-to-End-Prüfungen nutzen Mailpit; isolierte Fehler-/Zeitprüfungen dürfen kontrollierte Ports/Uhren verwenden.
- Vorhandener Java-Startpunkt: `./mvnw.cmd test`. Neue gezielte Testklassennamen und Integrationsprofile erst mit ihrer Implementierung dokumentieren; derzeit existieren sie noch nicht.
- Ausgeführte Nachweise mit Commit, Konfiguration, Testkommando und Ergebnis festhalten. Kein grüner Status aufgrund eines nur geplanten Tests.

## 9. Bezug zu Block 3 und Block 4

Block 3 erhält echte Modulverträge, HTTP-Adapter mit Fehlerfällen, ein gesichertes MCP-Werkzeug, asynchrone Prozessübergabe und KI hinter einem Port. PostgreSQL wird dafür jetzt bereits verwendet; eine künstliche zusätzliche In-Memory-Schicht entfällt.

Block 4 vertieft das entstandene Schema: ER-Sicht, mindestens eine weitere Migration auf versioniertem vorhandenem Testbestand, Datenzugriffsabstraktion, Abfrageplan/Indizes, Retrieval-Spike mit drei evaluierten Varianten sowie Prozess-/Outbox-Nachweise. Schon jetzt KI-Generierung und dateiweises Review mit Übernahme/Korrektur/Veto dokumentieren, damit spätere Belege nicht rekonstruiert werden müssen.

**Nächster konkreter Arbeitsschritt:** P0 abschliessen: ersten Prozessabschnitt mit D1/D2 entscheiden und die vier Service-Verträge festhalten. Danach P1/P2 implementieren. Kein paralleler Vollausbau aller Use Cases.

## Quellen

- [Modulstruktur und Fallanlagevertrag](module-structure.md#fallanlage--intake-und-case-processing)
- [ADR-002: Prozessführung und Registrierung](adr/ADR-002-camunda8-workflow-orchestrierung.md)
- [ADR-004: Token-Speicherung](adr/ADR-004-tokenbasierter-mieterzugriff.md)
- [ADR-005: Zugriff und Delegation](adr/ADR-005-zugriffsmodell.md)
- [SS-001 bis SS-006](schnittstellenentwurf-block-3.md)
- [OpenAPI](openapi/fallannahme.yaml)
- [Fallverwaltung](../specifications/fallverwaltung.md)
- [Dringlichkeitsbewertung](../specifications/dringlichkeitsbewertung.md)
- [MCP Guidelines](../specifications/MCP_GUIDELINES.md)
- [Use Cases](../use_cases/README.md)
- [Bestehende Testanleitung](../../src/test/README.md)
- [Build](../../pom.xml)
- [Fall-Demo](../../src/main/java/com/pertegato/propertyflow/caseprocessing/demo/DemoCases.java)
- [Triage](../../src/main/java/com/pertegato/propertyflow/triage/service/AnalyzeTool.java)
- [MCP-Zwischenstand](../../src/main/java/com/pertegato/propertyflow/caseprocessing/mcp/FallstandTool.java)
