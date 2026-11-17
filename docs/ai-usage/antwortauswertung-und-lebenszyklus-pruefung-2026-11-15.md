# Antwortauswertung und Falllebenszyklus: Umsetzung und Prüfung

Stand: 16.11.2026, lokale Arbeitskopie. Fortsetzung des autonomen Implementierungsauftrags.
Dieser Bericht dokumentiert ausgeführte Prüfungen, keine produktive Gesamtabnahme.

## Umgesetzter Umfang

| Bereich | Verhalten und Nachweis |
|---|---|
| Zentrale Auswertung | Ein dauerhafter Koordinator für Automatik, zentralen Nachrichteneingang und Reply-Pfad. Geprüfte Frage-/Nachrichtenbezüge, wörtliche Belege, Versionsschutz, gemeinsame laufende Auswertung und nachvollziehbarer Fehlerpfad. `ConversationEvaluationPostgresTest`, `ConversationEvaluationCamundaTest`, `ConversationAnalyzerTest`. |
| Empfehlung | Suche in ausdrücklich freigegebenen, versionierten Quelldateien. Strikte Schema-/Quellenprüfung, begrenzte Modellwartezeit, UI3-Projektion und Mitarbeiterprüfung bei fehlenden Quellen oder Fehlern. `RecommendationServiceTest`, `BoundedRecommendationModelTest`. |
| Warteentscheidungen | Native Mitarbeiterentscheidungen für Wiedervorlage/externe Wartephase, Camunda-Timer, exklusive Auflösung bei Termin/Kontakt/Prüfbedarf. Terminänderung und Löschung mit aktueller Aufrufkennung; alte Marker wirken nicht auf neue Termine. `CaseWaitPostgresTest`, `CaseWaitCamundaTest`, `FollowUpDateTest`. |
| Wiedereröffnung | Dauerhafter Startauftrag, bestätigter beendeter Vorgänger, neue führende Instanz, bestehende Fallidentität/Link/Historie. Keine Wiederbelebung alter Automatikjobs. `CaseReopenPostgresTest`, `CaseReopenCamundaTest`. |
| Mieterwarten | Rückfrage aus der nativen SWork-Aufgabe; geprüfte Veröffentlichung und anschliessend registrierter Reply-Aufruf. Frühe Ergebnisse werden nachgeliefert. Teilantworten erhalten das Warten, vollständige Antworten führen genau einmal zur Prüfung. `TenantReplyCamundaTest`. |
| Kommunikation in Wartephasen | Informationen und Rückfragen bleiben Nebenaktionen der aktuellen Wiedervorlage, externen Wartephase oder Reply-Instanz. Alte unveröffentlichte Aufträge werden nach Fortsetzung abgewiesen. `StaffMessagePostgresTest`, `CaseWaitCamundaTest`, `TenantReplyCamundaTest`. |
| Antwortbedarf aufheben | Eigener Camunda-Eingang, Berechtigung und aktueller Fall-/Aufrufstand. Audit mit Nachricht, Mitarbeiter und Zeitpunkt. Letzter Bedarf führt ohne Modellaufruf zur Bearbeitung oder vorrangigen Prüfung; verbleibende Fragen und andere Wartephasen bleiben erhalten. Späte Auswertung darf Aufhebung nicht in Beantwortung umwandeln. `TenantReplyCamundaTest`, `StaffMessagePostgresTest`. |
| Oberfläche | Bestätigte Aktionen, Rollen-/Versionsprüfung, keine automatische Wiederholung unklarer Entscheidungen, Erhalt von Entwürfen. Verlauf unterscheidet beantwortet, manuell aufgehoben und durch Abschluss beendet. Browserdateien unter `src/test/browser/stage3/`. |

Die Migrationen V18–V37 erweitern die bisherige Persistenz. Neue Kommentare folgen
den [Java-Richtlinien](../specifications/JAVA_GUIDELINES.md). Technische Annahmen
stehen in [Implementierungsannahmen](../architecture/implementierungsannahmen-2026-11-15.md).

## Ausgeführte Prüfungen bis V29

| Prüfung | Ergebnis | Lokales Protokoll |
|---|---|---|
| Breite PostgreSQL-/Camunda-/Mail-/Web-/Vertragsregression | 199 Tests, keine Fehler, keine übersprungenen Tests | `target/lifecycle-complete-integration.log` |
| Gesamte Java-Regression mit Java 23.0.2 im lokalen Linux-Container | 499 erfasste Tests; 339 ausgeführt und bestanden, 160 umgebungsabhängig übersprungen | `target/linux-complete-regression.log` |
| Letzte Reply-/Bindungsprüfung und Paketbau | 17 Tests bestanden; Spring-Boot-JAR gebaut | `target/lifecycle-package.log` |
| Edge, Firefox, WebKit | Je 29 Tests bestanden, insgesamt 87 Ausführungen | `target/lifecycle-browser-final-{msedge,firefox,webkit}.log` |
| JavaScript-Regression | 6 Tests bestanden | `target/lifecycle-js-final.log` |
| XML-/Referenzprüfung der sieben BPMN-Dateien | Keine doppelten IDs oder ungültigen Sequenzflussziele | Lokale statische Prüfung |

Die Java-23-Ausführung verwendet keine angebundene Datenbank/Engine; die
entsprechenden Tests laufen separat auf PostgreSQL Port 5434, Camunda Port 8085
und Mailpit Port 8027. Die Zahlen verschiedener Läufe dürfen wegen Überschneidungen
nicht als Anzahl unterschiedlicher Tests addiert werden. Entwicklungsfälle auf
Port 5433 wurden nicht verändert. Der JAR-Bau startet oder migriert keine Anwendung.

Die letzte Paketprüfung enthält zusätzlich die Wiederholung bei verzögert sichtbarem
Abschluss der vorherigen Mitarbeiterinstanz. Ein kurz hinterherlaufender Suchindex
erzeugt dabei keinen dauerhaften Vertragsfehler.

Auswahl für die breite lokale Regression:

```powershell
$env:PROPERTYFLOW_TEST_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:5434/propertyflow_test'
$env:PROPERTYFLOW_TEST_CAMUNDA_URL = 'http://127.0.0.1:8085'
$env:PROPERTYFLOW_TEST_SMTP_PORT = '1027'
$env:PROPERTYFLOW_TEST_MAILPIT_URL = 'http://127.0.0.1:8027'
.\mvnw.cmd -B -ntp '-Dtest=*PostgresTest,*CamundaTest,*WorkflowBindingTest,*ProjectionsTest,*CaseWebTest,FollowUpDateTest,ConversationAnalyzerTest,RecommendationServiceTest,BoundedRecommendationModelTest,CaseMailTransportTest' test
```

Die CI-Auswahl enthält die neuen Integrationsklassen und Browserprüfungen. Die CI
wurde in diesem Abschnitt nicht durch Push gestartet; die Nachweise sind lokal.

## Gefundene und behobene Probleme

- Der zentrale Automatikpfad verwendete zunächst noch die alte isolierte Triage.
  Er nutzt jetzt denselben persistenten Koordinator wie die Nachrichtenverarbeitung.
- Eine neue Referenz beim Wechsel der Wartephase muss vor Freigabe der Zustellung
  bereitstehen. Die Registrierung enthält deshalb eine explizite Initialisierungsbarriere.
- Ein neuer Pflichtprüfgrund und eine normale Reply-Rückgabe dürfen keine zwei
  Mitarbeiterfortsetzungen erzeugen. Die Entscheidung wird pro Reply-Aufruf exklusiv
  gespeichert; beide Zustellwege verwenden denselben Bezug.
- Durch die Erweiterung um Reply-Kommunikation entstand ein Nullwertfehler im
  bestehenden Informationsversand. Die Regression erkannte ihn; die optionale
  Elternreferenz wird wieder ohne unbeabsichtigtes Auspacken behandelt.
- Eine textbasierte BPMN-Ergänzung traf zunächst eine benachbarte Bedingung.
  Originalbedingungen wurden korrigiert und die Bindungsprüfung um den Fragepfad
  und die neue Aufhebungsfortsetzung erweitert.
- Die neue Browser-Testseite benötigte eine explizite UTF-8-Angabe. Nach der
  Korrektur bestehen alle drei Browserläufe; die Anwendungsseite hatte bereits UTF-8.

## Grenzen und verbleibende Arbeit

Die Anwendung ist **noch nicht vollständig implementiert**. Sechs Gesamtmodelle
bleiben `isExecutable=false`; die ausführbaren Integrationsmodelle sind begrenzte
Prüf-Fixtures. Die neuen Diagrammelemente sind in den bestehenden DI-Daten ergänzt,
eine visuelle Gesamtabnahme im Modeler wurde hier nicht durchgeführt.

Die statische Bestandsaufnahme findet noch 7 Service-Tasks ohne Laufzeitbindung:

- Automatische Abklärung: `AutoAnswer`, `AutoClose`, `AClosed`.
- Hauptprozess: `StaffFollowPublish`, `StaffFollowSignal`,
  `StaffFollowContinue`, `PrepareCloseAuto`.

Nächste Umsetzungsschritte sind Freigabe/Publikation individueller Abschlussantworten,
weitere direkte Mitarbeiteraktionen aus Abklärung und Mieterwartephase sowie die
vollständige B07/B09/B10-Absicherung und Deploymentprüfung. Weitere Katalogfragerunden
und die direkte Mitarbeiterübernahme sind inzwischen über V32/V33 angebunden;
bekannte Katalogvorschläge werden nicht erneut veröffentlicht.

Ein produktiver freigegebener Wissensbestand und eine semantische Modellevaluation
sind weiterhin offen. Die Tests verwenden kontrollierte Modellantworten. Strikte
Schema- und Belegprüfung beweist weder fachliche Relevanz der Frageauswahl noch
inhaltliche Richtigkeit einer Empfehlung oder zulässige Abschlussbegründung.
Es wurden keine kostenpflichtigen Modellaufrufe, Commits, Pushes oder produktiven
Deployments ausgeführt.


## Fortsetzung am 16.11.2026: Abschluss aus Wiedervorlage

V30 erweitert den gemeinsamen Abschlussnachweis um die Herkunft aus einem
bestätigten Wiedervorlage-Auftrag. `WCloseTask` ist gebunden, die Nachricht korreliert
an die aktuelle Terminversion und die explizite Rückgabe erreicht den bestehenden
Abschlussdienst. Die Oberfläche enthält eine eigene verbindliche Bestätigung.
Status, Termin, offene Antwortbedarfe und Nachweis werden erst dort übernommen.
Konkurrierende Änderungen führen zur aktuellen Prüfung; eine vorherige Unterbrechung
verwirft den Auftrag. Die ursprüngliche V1–V29-Regression oben bleibt als damaliger
Prüfstand erhalten; ergänzende Prüfergebnisse werden nach Abschluss eingetragen.


## Fortsetzung am 16.11.2026: Katalogfragen und Übergabe zum Mieterwarten

V31 speichert den Katalogentscheid zur konkreten zentralen Auswertung. Die
Veröffentlichung verwendet ausschliesslich die vier freigegebenen KOM-05-Texte,
mit Systemherkunft und einem getrennten Antwortbedarf pro Frage. Rückgabe aus der
beendeten Automatik, Reply-Registrierung und Teilantworten nutzen die bestehenden
Verträge. Neue Prüfgründe vor und nach Veröffentlichung sowie die manuelle Aufhebung
des letzten Bedarfs in beiden Übergabefenstern wurden mit Camunda geprüft.
Die Benachrichtigung weist bei noch offenem Bedarf ausdrücklich auf die erforderliche
Antwort hin und kopiert keinen Frageinhalt in die Mail.

Für V31 war damit die automatische Erstveröffentlichung umgesetzt. Weitere Fragerunden
sind im nachfolgenden V32-Abschnitt beschrieben; individuelle Abschlussantworten bleiben offen.
Die semantische Qualität einer realen Modellentscheidung ist weiterhin gesondert
zu evaluieren. Die Laufzeitprüfungen verwenden kontrollierte Modellantworten.

Ergänzende gezielte Nachweise: 44 Tests für Wiedervorlage-Abschluss und angrenzende
Verträge (`target/follow-up-closure-verified.log`), 49 für Katalog-/Mail-/Reply-Verträge
(`target/catalog-expanded-tests.log`) und 29 für die beiden Übergabefenster und
BPMN-Bindungen (`target/catalog-handover-verified.log`). Diese Läufe überschneiden
sich und sind keine addierbaren unabhängigen Testmengen.


## Gemeinsame Regression bis V31 (16.11.2026)

| Prüfung | Ergebnis | Lokaler Nachweis |
|---|---|---|
| PostgreSQL/Camunda/Mailpit, Web- und Vertragsprüfungen | 221 erfolgreich, keine übersprungen | `target/lifecycle-v31-integration.log` |
| Vollständige Testsuite mit Java 23 | 521 erfasst, 342 erfolgreich, 179 mangels Integrationsumgebung übersprungen | `target/lifecycle-v31-java23.log` |
| Browserprüfungen Edge, Firefox und WebKit | je 31 erfolgreich, 93 Ausführungen | `target/lifecycle-v31-browser-*.log` |
| JavaScript-Tests | 6 erfolgreich | `target/lifecycle-v31-js.log` |

Die übersprungenen Integrationstests des isolierten Java-23-Laufs sind im separaten
Integrationslauf ausgeführt worden. Die Testmengen überlappen; Zahlen nicht addieren.
Diese Ergebnisse validieren V31. Der nachfolgende V32-Abschnitt erhält einen eigenen Nachweis.


## Fortsetzung am 16.11.2026: QUESTION-Wiedereintritt und Mitarbeiterübernahme

V32 ergänzt eigene aktuelle Automatik-Eintritte aus tatsächlichen Reply-Entscheiden.
Der Wartestatus bleibt bei weiteren Katalogfragen erhalten. Wiederholung, neue Grundlage,
Pflichtprüfvorrang, vollständige Beantwortung, manuelle Aufhebung und alte Marker wurden
in Camunda geprüft. Eine neue Freigabeprüfung wurde auf zusätzliche automatische Aktionen
begrenzt, nachdem die Regression eine unerwünschte Auswirkung auf reines Weiterwarten zeigte.
Eine Warteprüfung berücksichtigt nun auch das kurz verzögerte Sichtbarwerden eines
beendeten Elements im Camunda-Suchindex.

V33 ergänzt die direkte Mitarbeiterübernahme, die bestätigende Oberfläche und den
serverseitigen Akteurnachweis. Tests decken veraltete Aufträge, Rollenentzug, neue Prüfgründe
zwischen Commit und Mitarbeitereintritt, Wiederholungen sowie die Übergaben nach
Automatikende und vor Reply-Registrierung ab. Bereits vorbereitete Prüfungen werden
wiederverwendet. Neue Kommentare sind deutsch; bestehende Kommentare wurden nicht übersetzt.

Gezielte Prüfung mit V33: 73 erfolgreiche Tests in `target/takeover-first.log`.
Der spätere gemeinsame Lauf enthält zusätzliche HTTP-, Bindungs- und Browserprüfungen.
Java-23-Prüfung bis V32: 529 Tests, davon 343 erfolgreich und 186 umgebungsbedingt
übersprungen (`target/lifecycle-v32-java23.log`). Die V32-Gesamtintegration hatte
228 erfolgreiche Tests und den oben beschriebenen Suchindexfehler in der Warteprüfung;
sie wird durch die gemeinsame Prüfung des korrigierten V33-Stands ersetzt.


## Gemeinsame Regression bis V33 (16.11.2026)

| Prüfung | Ergebnis | Lokaler Nachweis |
|---|---|---|
| PostgreSQL/Camunda/Mailpit, Web- und Vertragsprüfungen | 239 erfolgreich, keine übersprungen | `target/lifecycle-v33-integration.log` |
| Vollständige Testsuite mit Java 23 | 539 erfasst, 344 erfolgreich, 195 mangels Integrationsumgebung übersprungen | `target/lifecycle-v33-java23.log` |
| Edge und Firefox | je 35 erfolgreich | `target/lifecycle-v33-browser-msedge.log`, `target/lifecycle-v33-browser-firefox.log` |
| WebKit | 31 bestehende Prüfungen und 4 Übernahmeprüfungen erfolgreich | `target/lifecycle-v33-browser-webkit.log`, `target/takeover-webkit-verified.log` |

Der erste WebKit-Lauf enthielt vier fehlgeschlagene neue Übernahmeprüfungen, weil
in der isolierten Testseite die explizite UTF-8-Metaangabe fehlte. Die korrigierte
Testseite besteht alle vier Prüfungen. Die Anwendungsseite hatte bereits UTF-8.
Zusammen sind 105 Browserausführungen erfolgreich geprüft; Mengen überlappen mit
früheren Läufen und sind nicht zu addieren. Der Java-23-Lauf enthält keine konfigurierte
Integrationsumgebung; dafür gilt der separate gemeinsame Integrationsnachweis.


## Fortsetzung V34/V35 und Fortsetzungsabgleich (16.11.2026)

V34 verbindet Mitarbeiterrückfragen aus Abklärung mit atomarer Veröffentlichung und
zuverlässiger Ablösung der Automatik. V35 verbindet den direkten Mitarbeiterabschluss
mit demselben Abschlussdienst wie die bestehenden Abschlusswege und einer bewussten
UI-Bestätigung. Neue Fragen, Kommentare und Änderungen wurden auf Deutsch dokumentiert.

Gezielte erste Rückfrageprüfung: 72 erfolgreiche Tests (`target/manual-question-first.log`).
Die erweiterten Läufe fanden einen ungültigen Dringlichkeitswert in neu angelegten
Testdaten; dessen Quelle ist korrigiert. Die neuen Abschlussprüfungen selbst bestanden.
Fünf neue Browserprüfungen bestehen jeweils in Edge, Firefox und WebKit (15 Ausführungen,
`target/clarification-close-browser-*.log`). Sie prüfen Bestätigung, Konto-/Versionswechsel,
neue Prüfgründe, Abbrechen und ausschliesslich lesenden Abgleich unklarer Antworten.

Der anschliessende Fortsetzungsabgleich schützt alte START-Ansprüche und überträgt noch
relevante Prüfgründe an einen nachweislich lebenden Staff-Nachfolger. Die künstliche
Aktivierung redundanter Pfade erfolgt ausschliesslich in der isolierten Test-Fixture.
Sie ist kein produktiver Prozessmodifikations-Endpunkt.


## Gemeinsame Prüfung V35 und Fortsetzungsabgleich (16.11.2026)

| Prüfung | Ergebnis | Lokaler Nachweis |
|---|---|---|
| Gemeinsame Integration bis V35 | 258 erfolgreich, keine übersprungen | `target/lifecycle-v35-integration.log` |
| Gezielter Fortsetzungsabgleich mit angrenzenden Abläufen | 75 erfolgreich, keine übersprungen | `target/continuation-reconcile-first.log` |
| Vollständige Java-23-Suite mit Fortsetzungsabgleich | 563 erfasst, 347 erfolgreich, 216 umgebungsbedingt übersprungen | `target/continuation-reconcile-java23.log` |
| Abschlussdialog in Edge, Firefox und WebKit | jeweils 5 erfolgreich, 15 Ausführungen | `target/clarification-close-final-*.log` |

Die 216 übersprungenen Prüfungen benötigen die separat konfigurierte Integrationsumgebung.
Der danach ergänzte Camunda-Test hält einen doppelten Fortsetzungsauftrag bis zur bestätigten
Mitarbeiterprüfung im Retry. Sein Nachweis gehört zum abschliessenden gemeinsamen Lauf,
nicht zum bereits ausgeführten Java-23-Lauf. Die Testmengen überlappen und sind nicht zu addieren.

## Konkrete Fortsetzung der noch offenen Prozesswege

1. Direkter Mitarbeiterabschluss aus Reply: dauerhaften Auftrag am aktuellen Warte-Call
   prüfen und reservieren; vor Unterbrechung alte Wirkungen sperren; denselben Abschlussdienst
   nach tatsächlich beendeter Reply-Unterbrechung verwenden. Neue Grundlage nach Reservierung
   führt über den vorhandenen Abschlusskonflikt in eine aktuelle Mitarbeiterprüfung.
2. Direkte Wiedervorlage aus Abklärung und Mieterwarten: Termin und Fortsetzung atomar
   speichern; bestehende Staff→FollowUp-Hierarchie verwenden; frühe Kontakte, Prüfgründe
   und Timer vor oder während der Registrierung zuverlässig nachliefern.
3. Systemabschluss: beide spezifizierten Belegarten, ausdrücklich freigegebene Fachregeln,
   individuelle Antwort und zweimalige aktuelle Freigabe verbinden. Eine allgemeine
   RAG-Quelle ersetzt keine freigegebene Abschlussregel. Semantische Evaluation bleibt nötig.
4. Sämtliche Originalmodelle gemeinsam ausführbar binden und B07/B09/B10 mit konkurrierenden
   Ereignissen, Wiederaufnahme und tatsächlichem Anwendungsneustart prüfen. Erst danach
   die sechs derzeit nicht ausführbaren Gesamtmodelle freigeben.


## Abschliessender gemeinsamer Integrationsnachweis (16.11.2026)

`target/lifecycle-final-integration.log`: **264 Tests erfolgreich, keine Fehler,
keine übersprungenen Tests**. Dieser Lauf enthält die 42 Automatik-/Rückfrage-/Abschlussprüfungen,
alle sieben Camunda-Prüfungen zur Mitarbeiterfortsetzung sowie HTTP-, Datenbank-,
Mailpit-, Wiedervorlage-, Wiedereröffnungs- und BPMN-Vertragsprüfungen.
Auch der zuletzt ergänzte Retry vor Bestätigung des Staff-Nachfolgers besteht.

Die endgültige Abschlussoberfläche ist mit den spezifizierten Dialogaktionen
«Fall wirklich abschliessen?», «Abschliessen» und «Abbrechen» umgesetzt. Nach einer
Bestätigung schliesst der Dialog; ein unklarer Ausgang blockiert die restliche
Fallansicht nicht und wird weiterhin ausschliesslich lesend abgeglichen.
Die fünf Browserprüfungen bestehen in jedem der drei Browser
(`target/clarification-close-final-msedge.log`, `target/clarification-close-final-firefox.log`,
`target/clarification-close-final-webkit.log`).

Die neun ungebundenen Service-Tasks oben und die fehlende Gesamtintegration bleiben
unverändert offen. Es erfolgten keine Freigabe der Gesamtmodelle, kein Commit,
kein Push und keine Migration der Entwicklungsdatenbank.


## Weiterarbeit nach dem V35-Gesamtnachweis: Reply-Abschluss (V36)

Der in der vorangehenden Liste an erster Stelle genannte direkte Mitarbeiterabschluss
im Mieterwarten ist nun angebunden. Früh abgelehnte Aufträge erhalten den vorhandenen
Wartepfad, angenommene Aufträge sperren dessen weitere Wirkungen und werden dauerhaft
an den Hauptprozess übergeben. Der gemeinsame Abschlussdienst behandelt eine spätere
Änderung der Zustimmung als Abschlusskonflikt. Die frühere Liste mit neun offenen Tasks
beschreibt den V35-Stand; aktuell sind noch sieben Service-Tasks ungebunden.
Die konkreten Nachweise für V36 werden getrennt vom bestandenen V35-Lauf geführt.

V36 wurde am 16.11.2026 geprüft: 93 gezielte Tests erfolgreich
(`target/reply-closure-expanded.log`), anschliessend 272 Integrations-/Vertragstests
erfolgreich (`target/reply-closure-full.log`, Abschluss 02:03:59). Die sechs
Dialogtests bestehen je in Edge, Firefox und WebKit, insgesamt 18 Prüfungen
(`target/reply-close-browser-*.log`). Der späte Konflikt wurde einschliesslich
neuer Rückfrage und erneutem Mieterwarten geprüft; alte Abschlussmarker wirken
nicht auf den neuen Aufruf.

## Weiterarbeit: direkte Wiedervorlage (V37)

StaffFollowPublish, StaffFollowSignal und StaffFollowContinue sind an den dauerhaften
Auftrag und die bestehende Hauptprozess–Mitarbeiter–Wiedervorlage-Hierarchie gebunden.
Die neue Migration wurde ausschliesslich in der isolierten Testdatenbank angewendet.
Der gezielte Lauf deckt auch frühe Kontakte, neue Gründe, unveränderte Terminversion
und den Einstieg aus Mieterwarten ab. Die ersten Läufe fanden korrigierbare Fehler
in XML-Reihenfolge, Korrelationsvariable und Reply-Spaltenname. Zwei beim Abbruch
verbliebene Testinstanzen wurden gezielt beendet. Ein Kontakt-Test wurde an den
vorgesehenen direkten Prüfpfad während Wiedervorlage angepasst.

Die 17 HTTP-/Persistenztests und 18 Modellvergleiche bestehen bereits; die sechs
neuen Browserprüfungen bestehen je in Edge, Firefox und WebKit
(`target/direct-follow-browser-*.log`). Der vollständige neue Ablaufnachweis ist
noch in Prüfung; diese Teilnachweise sind keine Gesamtfreigabe.


Der anschliessende V37-Gesamtlauf ist erfolgreich: **284 Tests, keine Fehler,
keine übersprungenen Tests**, `target/direct-follow-full.log`, 16.11.2026 02:22:36.
Enthalten sind 49 Automatikfälle und 18 Reply-Fälle. Zusätzliche Szenarien prüfen
Rechteentzug, neue Gründe vor Annahme, Wiederverwendung einer bereits beanspruchten
Mitarbeiterfortsetzung sowie Löschen der direkten Wiedervorlage bei offenen Fragen
und erneutes Mieterwarten ohne Wirkung alter Marker. Der vorangehende gezielte
bereinigte Lauf war mit 41 Tests erfolgreich (`target/direct-follow-fourth.log`).
Vier Service-Tasks bleiben fachlich und technisch offen: AutoAnswer, AutoClose,
AClosed, PrepareCloseAuto. Die früheren Listen mit sieben beziehungsweise neun
Tasks dokumentieren die jeweiligen Vorstände.

## Fortsetzungsabgleich und Java-23-Prüfung (16.11.2026)

101 gezielte Tests bestehen (`target/external-owner-regression.log`, 02:27:42).
Sie enthalten die Übergabe alter Mitarbeiterfortsetzungen an einen tatsächlich
aktiven neuen Automatikaufruf oder Reply. Der ergänzte Konkurrenztest reserviert
zwischen Eigentümerprüfung und Abgleich einen Reply-Abschluss: der Abgleich bleibt
im Retry, während der geschützte Abschluss genau einmal erfolgt. Der anschliessende
Reply-Lauf besteht mit 20 Tests (`target/external-owner-closure-race.log`, 02:30:29).

Unter Java 23 bestehen 349 Tests; 237 von externen Testdiensten abhängige Tests
wurden mangels Umgebungsfreigabe erwartungsgemäss übersprungen: insgesamt 586,
keine Fehler (`target/direct-follow-java23.log`, 01:25:57 UTC). Dieser Lauf enthält
noch nicht den danach ergänzten Abschlusskonkurrenztest.

Die erste Prüfung vollständiger BPMN-Quellen fand XML-Reihenfolgefehler in drei
Modellen, fehlende Bedingungen an F_Initial und S13 sowie die fehlende native
Bindung von SExternalWait. Die Korrekturen erhalten alle Diagrammdaten. Nach den
Korrekturen bestehen Erstanlage/Übernahme und direkter Mitarbeiterabschluss mit
vollständigen Modellen (`target/canonical-models-third.log`, 02:33:56, zwei Tests).
Weitere vollständige Pfade werden separat geprüft; das ist noch keine Gesamtfreigabe.

### Vollständige Prozessquellen: erweiterter Nachweis

Sieben Tests mit vollständigen BPMN-Quellen plus 18 Vertragsprüfungen bestehen
(`target/canonical-models-fourth.log`, 16.11.2026 02:39:18). Abgedeckt sind:
Erstanlage und Mitarbeiterübernahme, direkter Abschluss, externes Warten mit
Wiedervorlage/Löschen/Abschluss, direkte Wiedervorlage mit zentralem Mieterkontakt,
Katalogfrage mit vollständiger Antwort, manuelle Rückfrage mit Teil- und Vollantwort
sowie Wiedereröffnung mit neuem führenden Prozess und bestehendem Fall-Link.
Ein erster erweiterter Lauf wartete in NWake zu Recht auf das Ende des Termins;
der Test führt diese Kontaktübergabe jetzt vor der Auswertung aus.

Die Modelltransformation im Test setzt nur die Ausführbarkeitsflags und vier
unerfüllte Platzhalter-Jobtypen. Diese automatischen Abschlussaufgaben werden
nicht ausgeführt. Ihre fachliche Implementierung und semantische Freigabe bleiben
separate Arbeit. Die sieben Gesamtquellen wurden nicht als produktiv freigegeben.


## Echte Abschlussevaluation und aktuelle Grenze

Der vollständige lokale Lauf mit qwen3.5:4b, Revision
`d8b0f5e9760cd1682034f292d7ef72ec46f432149be0df7574bf2d6e92e38c04`,
Prompt `closure-ground-v1`, Temperatur 0 und `reasoning_effort=none` besteht
**21 von 24 Fällen**. Der [gesicherte Einzelbericht](../evaluation/evidence/closure/qwen35-4b-2026-11-16.json)
enthält Bestandshash, Modelleinstellungen und sämtliche Ergebnisse.
Zwei Positivfälle liefern formal ungültige Ausgaben. Im Fall `unconfirmed-scope`
bewertet das Modell die Reparatur eines Fensters samt «danke» als Erledigung auch
des zuvor gemeldeten Wassers im Keller. Die Belegstellen existieren, tragen die
behauptete Vollständigkeit aber nicht. Das ist ein tatsächlich unzulässiger
Abschlussvorschlag, kein Transportfehler. **Keine automatische Abschlussfreigabe.**

Die vorangehenden technischen Fehlversuche mit fehlendem `/v1`-Basispfad und
überschrittenem Modellzeitlimit sind nicht als semantische Ergebnisse gewertet.
Ihre lokalen Protokolle bleiben unter `target/closure-model-*.log` erhalten.
Der anschliessende direkte Aufruf des synthetischen Leck-Falls lieferte eine
formal gültige Ausgabe; das ändert den nicht bestandenen Gesamtlauf nicht.

Aus dem konkreten Fehler wurde zusätzlich die bereits in FALL-07 ausdrücklich
verlangte Grenze umgesetzt: blosser Dank ist kein Beleg für fehlenden Hilfebedarf.
`closure-ground-validation-v2` weist einen solchen NO_FURTHER_HELP-Beleg zurück.
Dafür gibt es eine eigene parametrische Regression. Der gesicherte Modellbericht
stammt von der vorherigen Validierung; er wird nicht nachträglich als bestanden
umgedeutet. Eine korrekte semantische Abdeckung aller Teilprobleme ist damit
weiterhin nicht nachgewiesen. Der neue Prüfdienst wird noch nicht für automatische
Falländerungen verwendet.

Die aktuelle Java-23-Suite besteht: 613 erfasste Tests, davon 367 ausgeführt und
bestanden, 246 umgebungsabhängig übersprungen (`target/canonical-closure-java23.log`,
16.11.2026 02:01:29 UTC). Darin enthalten sind die 18 neuen technischen
Abschlussgrund-/Regel-/Bestandsprüfungen, noch nicht die anschliessende Dank-Regel.
Die breite Integration fand einen Testfehler beim Warten auf die sichtbare
Call-Activity-Beendigung (293 von 294 bestanden). Der Test wartet jetzt ausdrücklich
auf Kind **und** Call. Der abschliessende Integrationslauf wird getrennt berichtet.

### Zentrale Abschlussbewertung und Modellstruktur

Der breite Integrationslauf besteht mit 312 Tests ohne Fehler oder Überspringen
(`target/canonical-full-final.log`, 16.11.2026 03:09:39). Danach bestehen 60 gezielte
Tests einschliesslich der sechs Dank-Varianten und des korrigierten Terminrückwegs
(`target/final-merge-closure-regression.log`, 03:14:40).

Die anschliessende zentrale Anbindung der Abschlussbewertung besteht mit 50 Tests
(`target/central-closure-regression.log`, 03:19:12). Darunter sind 17 PostgreSQL-Tests
des gemeinsamen Auswertungskoordinators und neun Abläufe mit vollständigen Modellen.
Positive und fehlgeschlagene Abschlussbewertungen führen in beiden echten
Prozessläufen zu genau einer Mitarbeiteraufgabe, ohne Veröffentlichung oder Abschluss.
Weitere Fälle prüfen Wiederholung, frühere gespeicherte JSON-Ergebnisse, die
Falländerung während der Bewertung und ein verspätetes Ergebnis nach Abschluss.

Der erste Import-/Exportlauf aller sieben Modelle im lokal vorhandenen bpmn-js-Modeler
meldet keine Warnungen (`target/bpmn-runtime-review-2026-11-16/validation.json`).
Die Bildprüfung der neuen Zusammenführungen fand einen Terminrückweg durch den
Nebenprozessrahmen; die Linienführung wurde anschliessend ausserhalb korrigiert.
Das ist keine produktive Gesamtfreigabe und ersetzt keinen Engine-Nachweis.

Der zweite Render-/Import-/Exportlauf enthält die korrigierte Linienführung.
Die erneut betrachteten Ausschnitte führen die Terminrückwege ausserhalb des
Nebenprozessrahmens. Alle sieben BPMN-/Zeebe-Semantiksignaturen stimmen vor und
nach dem Modeler-Roundtrip überein
(`target/bpmn-runtime-review-2026-11-16/semantic-roundtrip.json`).
Der bestehende Hauptprozess behält elf Diagramme. Die visuelle Prüfung betrifft
die geänderten Zusammenführungen, keine vollständige gestalterische Abnahme aller Ansichten.

### Zweiter echter Modelllauf und aktuelle Laufzeit

Der vollständige Lauf mit Prompt `closure-ground-v2`, Prüfung
`closure-ground-validation-v2` und unverändertem Modell/Bestand besteht ebenfalls
21 von 24 Fällen (`target/closure-model-evaluation-v2.log`, 16.11.2026 03:31:39).
Alle 20 negativen/mehrdeutigen Fälle bestehen, aber nur einer der vier Positivfälle.
Bei Heizung und Leck erfindet das Modell zusätzliche Nachweispflichten und liefert
NONE; der Fall mit mehreren behobenen Problemen liefert eine ungültige Ausgabe.
Der [gesicherte zweite Bericht](../evaluation/evidence/closure/qwen35-4b-2026-11-16-v2.json)
ergänzt den früheren Befund. Er ersetzt ihn nicht und ist ausdrücklich keine
semantische Freigabe. Der echte Modelltest scheitert entsprechend; er ist im
normalen Testlauf weiterhin ausschliesslich über die optierende Umgebungsvariable aktivierbar.

Die Mitarbeiterprojektion ergänzt den Vorschlag mit Belegen und Prüfhinweis;
Modelltext wird HTML-escaped dargestellt und ein älterer Fallstand gekennzeichnet.
Der aktuelle Java-23-Lauf besteht mit 630 erfassten Tests: 376 ausgeführt und
bestanden, 254 umgebungsabhängig übersprungen
(`target/central-closure-java23-final.log`, 16.11.2026 02:34:54 UTC).
Der getrennte vollständige Lauf mit PostgreSQL, Camunda und Mailpit sowie den
zugehörigen Vertrags-, Projektions-, HTTP- und Modellvertragsprüfungen besteht mit
**333 Tests, null Fehlern und null Überspringen**
(`target/central-closure-full-final.log`, 16.11.2026 03:39:20). Das anschliessende
Packaging erstellt `target/propertyflow-0.0.1-SNAPSHOT.jar` erfolgreich.
Dies ist ein lokaler Build, keine produktive Bereitstellung der sechs Fallmodelle.

Offen bleiben AutoAnswer, AutoClose, AClosed und PrepareCloseAuto einschliesslich
individueller geprüfter Abschlusskommunikation, dauerhafter automatischer
Veröffentlichungs-/Abschlusskoordination und des fachlichen Modellnachweises.
Ein produktiv freigegebener Regel-/Wissensbestand liegt weiterhin nicht vor.
Die übrigen in der Laufzeitdokumentation genannten Betriebs- und B10-Nachweise
sind ebenfalls nicht durch diese Tests als vollständig erledigt erklärt.


## V38: Vier Abschlussaufgaben und individuelle Mitteilung (16.11.2026)

Der neueste Benutzerauftrag erlaubt die vollständige technische Implementierung,
hält die fachliche Betriebsfreigabe aber ausdrücklich gesperrt. Die vier bisher
ungebundenen Aufgaben AutoAnswer, AutoClose, AClosed und PrepareCloseAuto sind jetzt
angebunden. Der obige Stand mit vier Platzhaltern beschreibt die frühere Prüfung.

Implementiert wurden:

- Versionierte Abschlussversuche, stabile Veröffentlichungskennung und atomare
  Speicherung von öffentlicher Systemnachricht und bestehender Mailpflicht.
- Individuelle LLM-Mitteilung mit strikter Ausgabeprüfung und getrenntem
  Inhaltsprüfaufruf, ausschliesslich aus öffentlichem Kontext und freigegebener Regel.
  Kein persistierter Entwurf und keine feste Antwortvorlage.
- Erneute Prüfung vor Veröffentlichung und Abschluss; exakter Belegbezug und aktuelle
  Regelgültigkeit werden ohne erneute Grundbewertungsanfrage kontrolliert.
- Gemeinsamer Abschlussvertrag für Mitarbeiter und System, mit unterschiedlicher
  auditierter Herkunft und derselben Beendigung offener Rückfragen/Wiedervorlagen.
- Tatsächlich verifizierte Kind-/Call-Abschlüsse, CLOSE-Wiedereintritt aus Reply,
  Stale-Rückwege, Mitarbeiterfortsetzung bei Inhaltsablehnung und begrenzte
  technische Retries bis zum Incident.
- Schutz angenommener, noch ausstehender Mitarbeiteraufträge: Veröffentlichung und
  Abschluss warten ohne neue fachliche Wirkung auf deren Verarbeitung.
- Geschlossene Produktionssperre ohne Konfigurationsschalter. Positive technische
  Tests verwenden ausdrücklich eine nur im Test vorhandene synthetische Freigabe.

Die neuen Datenbankprüfungen umfassen einmalige Nachricht/Mailpflicht, gemeinsame
Abschlussfolgen, Herkunftsanzeige, Produktionssperre, Entzug einer Testfreigabe,
neue Mieternachricht während Generierung, geänderten Fallstand, Mitarbeiterpriorität,
Versuchsreservierung, falsches Kind/früheren führenden Durchlauf, technische Fehler,
Rollback nach Speicherung sowie Ausschluss interner Memos aus dem Modellkontext.
Die vollständigen Modelle prüfen beide fachlichen Fehlerpfade, erfolgreichen
Abschluss, unzulässigen Text und den CLOSE-Eintritt aus Mieterwarten. Technische
Modellfehler werden bis zum tatsächlichen Engine-Incident verfolgt.

Die Modeler-Prüfung aller sieben Dateien ist warnungsfrei. Der Export gruppiert
beim bestehenden FollowUp-Call Eingabe- und Ausgabemappings; die Reihenfolge innerhalb
jeder Gruppe und sämtliche fachlichen Werte bleiben erhalten. Nach dieser
bedeutungserhaltenden Normalisierung stimmen alle sieben BPMN-/Zeebe-Signaturen
überein. Alle elf Hauptprozessdiagramme bleiben erhalten.
Nachweise: target/automatic-closure-modeler.log und
 target/bpmn-closure-review-2026-11-16/semantic-roundtrip.json.

**Keine fachliche Freigabe:** Die beiden früheren echten Modellläufe mit jeweils
21/24 bleiben unverändert fehlgeschlagen. Für die individuelle Mitteilung und ihren
Inhaltsprüfer besteht noch kein semantischer Freigabenachweis. Die neuen Tests
beweisen technische Verträge und Konfliktbehandlung, nicht die Richtigkeit eines
realen Modellurteils. Produktive Regelquellen und B10-Betriebsabnahme bleiben offen.


### Abschliessende Nachweise für V38

| Prüfung | Ergebnis | Lokaler Nachweis |
|---|---|---|
| Gesamtlauf PostgreSQL, Camunda, Mailpit, Verträge und HTTP | 363 bestanden, keine Fehler/Überspringen; Packaging erfolgreich, 16.11.2026 08:07:17 CET | target/automatic-closure-full.log |
| Ergänzte Datenbank-, Grund-/Mitteilungs- und Mitarbeiteransichtsprüfungen nach Korrekturen | 95 bestanden, keine Fehler/Überspringen; aktuelles JAR gebaut, 08:13:26 CET | target/automatic-closure-final-corrections.log |
| Vollständige kanonische Modelle mit sämtlichen Abschlussbindungen | 16 bestanden, keine Fehler/Überspringen, 08:19:19 CET | target/automatic-closure-canonical-final.log |
| Java 23.0.2, ohne externe Testdienste | 664 erfasst: 394 bestanden, 270 umgebungsabhängig übersprungen; BUILD SUCCESS | target/automatic-closure-java23.log; XML unter target/linux-quality/automatic-closure/surefire-reports |
| Modeler-Import/-Export und Semantik | Sieben Dateien ohne Warnung; Semantik erhalten, elf Hauptprozessdiagramme | target/bpmn-closure-review-2026-11-16/validation.json und semantic-roundtrip.json |

Die Läufe überschneiden sich und werden nicht zu einer künstlichen Gesamtsumme addiert.
Der Zwischenlauf target/automatic-closure-final.log enthält 50 erfolgreiche
Katalog-/Automatik-Engineprüfungen und 20 erfolgreiche Bindungsprüfungen. Er scheiterte
insgesamt an der zuerst ungeeigneten Mockito-Simulation eines Spring-Proxys im
Rollback-Test. Die korrigierte Simulation löst den Fehler gezielt nach der Speicherung
aus; der anschliessende grüne 95er-Lauf prüft die tatsächliche gemeinsame Rücknahme.
Die letzte vollständige Modellprüfung deckt zusätzlich beide Fälle ab: noch offene
Rückfragen werden beim Abschluss aufgehoben, bereits beantwortete bleiben beantwortet.

Die Namensauflösung in der Mitarbeiter-Leseansicht filtert Systemereignisse ohne
Mitarbeiter-ID aus dem Benutzerverzeichnis und erhält die Herkunft «System».
Diese vollständige Leseprojektion wird zusätzlich zum Persistenznachweis geprüft.
Das gepackte JAR enthält die Abschlussdienste, die geschlossene Freigabesperre und V38.
Es wurden keine produktiven Prozesse aktiviert und keine fachliche Evaluation als
bestanden erklärt. Die Prüfungen liefen ausschliesslich gegen die isolierte lokale
Testdatenbank, Camunda-Testinstanz und Mailpit; die Entwicklungsdatenbank blieb unberührt.


Die anschliessenden fachlichen Modellläufe und Korrekturen an Abschlussbewertung
und individueller Mitteilung stehen im [Evaluationsbericht vom 16.11.2026](abschluss-evaluation-2026-11-16.md).
Die dort dokumentierte Freigabesicherung bleibt verbindlich.
