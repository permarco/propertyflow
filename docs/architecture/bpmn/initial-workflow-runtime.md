# Technische Umsetzung der bisherigen Backend-Abläufe

Stand: 16.11.2026. Implementierter Backend-Abschnitt mit isolierten PostgreSQL-
und Camunda-8.9.19-Tests. Der Mailprozess ist ausführbar; die sechs übrigen
Gesamtmodelle bleiben noch nicht ausführbar. Dies ist keine Abnahme des vollständigen Fallprozesses.

Die nachfolgenden Versionsangaben V1–V38 bezeichnen die historischen
Implementierungsschritte. Seit der Konsolidierung vom 16.11.2026 entsteht
bei neuen Datenbanken derselbe Endstand aus fünf Moduldateien. Nach dem ausdrücklich freigegebenen Hard Reset ist die historische
Kette entfernt; siehe
[Datenbankmigrationen nach Modulen](../datenbankmigrationen.md).

## Ergänzung: Auswertung, Empfehlung und Warteentscheidungen

Die Migrationen V18–V38 ergänzen folgende nachgewiesene Abschnitte:

- Zentrale Auswertung aller veröffentlichten externen Nachrichten und jeder offenen
  Rückfrage. Automatische Abklärung und Nachrichtenprozess teilen eine persistente
  Auswertung je Fall-/Analyseversion. Laufende Auswertung wird abgewartet, aktuelle
  Auswertung übernommen, überholte Grundlage verworfen. Modellaufrufe laufen ohne
  Datenbanksperre; Ergebnisse benötigen weiterhin die aktuelle Bearbeitung.
- Strikte Prüfung von Antwortbelegen: bekannte Frage, spätere gespeicherte
  Mieternachricht und wörtliche Belegstelle. Teilantworten erledigen nur belegte
  Fragen. Analyse-/Empfehlungsfehler lassen Antwortbedarf und Dringlichkeit bestehen
  und erzeugen nachvollziehbaren Prüfbedarf. Manuelle Dringlichkeit hat Vorrang.
- Empfehlung aus versionierten freigegebenen Quelldateien, nachvollziehbar in UI3.
  Quellenkennungen, Versionen und Belegstellen werden gegen das Suchergebnis geprüft.
  Ohne passende freigegebene Quelle gibt es keine erfundene Empfehlung. Das ist
  bislang eine begrenzte Dateisuche, kein angebundener produktiver Wissensbestand.
- Native Mitarbeiterentscheidungen für Wiedervorlage und externe Wartephase.
  Termine werden einmalig als absolute 08:00-Uhr-Zeitpunkte in Europe/Zurich
  berechnet. Camunda betreibt den Timer. Kontakt und Timer teilen dieselbe
  Erledigung; neuer zwingender Prüfbedarf unterbricht laufende Bearbeitung und
  Wartephase. Bereits berücksichtigte Gründe bleiben als Historie erhalten.
- Termin ändern/löschen über den konkreten Wiedervorlage-Aufruf. Version, Rolle,
  Prüfgrundstand und Terminbezug werden vor Speicherung erneut geprüft. Der
  neue Terminstand wird vor Freigabe wartender Kontakte ausdrücklich in die
  Mitarbeiterinstanz übertragen. Unveränderte Termine erzeugen keine neue
  Terminversion oder Mailpflicht. Löschen führt zur nativen Bearbeitungsaufgabe.
  Eine unklare Korrelationsantwort wird nicht automatisch erneut gesendet.
- Nicht unterbrechende Mitarbeiterkommunikation bei externer Wartephase und
  Wiedervorlage, auch mit Antwortbedarf. Aufgabe, Terminversion und Timer bleiben
  erhalten; eine spätere Mieternachricht hebt die Wiedervorlage weiterhin auf.
- Rückfrage aus «In Bearbeitung» über die konkrete native SWork-Aufgabe. Die
  Veröffentlichung wird gegen Rolle, Fallversion und Prüfgrundstand geprüft.
  Der anschliessende tatsächliche Reply-Aufruf setzt «Wartet auf Mieterantwort»
  mit Statusereignis und Mailpflicht. Veraltete Frageaufträge führen ohne Versand
  zurück zur Mitarbeiterprüfung.
- Dauerhafter Ergebnisverbrauch im Reply-Kindprozess: frühe Auswertungen werden
  nachgeliefert, Teilantworten führen ohne neue Nachricht/Statusmail zum Weiterwarten,
  vollständige Antworten oder Pflichtprüfbedarf zu genau einer Mitarbeiterprüfung.
  Überholte Ergebnisse verwenden erneut den gemeinsamen Auswertungskoordinator.
- Zusätzliche Mitarbeiterinformationen und Rückfragen im Mieterwarten bleiben
  Nebenaktionen desselben Aufrufs. Alte unveröffentlichte Aufträge werden nach
  einer Fortsetzung abgewiesen. Neue Antwortbedarfe schützen vor alten Ergebnissen.
- Gezieltes Aufheben eines Antwortbedarfs über UI3 und den eigenen Camunda-Eingang.
  Die ursprüngliche Frage bleibt erhalten. Mitarbeiterkennung, Zeitpunkt und
  Nachrichtenbezug werden gespeichert. Beim letzten offenen Bedarf führt der
  Reply-Pfad ohne Modellaufruf in die Bearbeitung oder eine vorrangige Prüfung.
  Weitere offene Fragen, externe Wartephase und Wiedervorlage bleiben bestehen.
  UI1/UI3 unterscheiden beantwortet, manuell aufgehoben und durch Abschluss beendet.
- Bewusste Wiedereröffnung mit neuem, dauerhaftem Startauftrag und neuer führender
  Instanz. Der abgeschlossene Vorgänger muss tatsächlich beendet sein; der neue
  Empfangsbereich muss bereits bestehen. Fallidentität, Historie und persönlicher
  Link bleiben erhalten. Alte Jobs/Ergebnisse erhalten keine neue Fachwirkung.

Die Webansicht enthält die dazugehörigen Warte- und Wiedereröffnungsaktionen.
Sie hält Entwürfe bei unklarer Bestätigung, bindet Aktionen an den angezeigten
Fallstand und sendet keine native Entscheidung automatisch erneut.

Für diese Abschnitte sind isolierte ausführbare Fixtures vorhanden. Katalogfragen,
weitere QUESTION-Runden und Mitarbeiterübernahme sind angebunden. Die nachfolgenden
Abschnitte ergänzen manuelle Rückfragen und direkten Abschluss während Abklärung.
**Individuelle automatische Abschlussantworten, Systemabschluss und die vollständige
Integration der Gesamtmodelle sind noch nicht fertig.** Frühere Abschnitte bleiben
als historischer Detailnachweis erhalten.

### Freigegebene Wissensquellen

`PROPERTYFLOW_KNOWLEDGE_DIRECTORY` bezeichnet ein lokales Verzeichnis mit JSON-
Dateien. Jede Quelle benötigt `id`, `version`, `title`, `location`, `text` und
`approved: true`. Kennung und Version müssen den fachlich freigegebenen Inhalt
bezeichnen; eine Modellantwort kann keine Quelle freigeben. Beispiel einer noch
nicht freigegebenen Datei:

```json
{
  "id": "BEISPIEL",
  "version": "1",
  "title": "Noch fachlich zu prüfen",
  "location": "Interne Richtlinie, Abschnitt 1",
  "text": "Hier muss die tatsächlich freigegebene Fachregel stehen.",
  "approved": false
}
```

Es werden höchstens 100 Dateien (je höchstens 100 kB) gelesen und fünf Quellen
nach textlicher Übereinstimmung ausgewählt. Symlink-Dateien werden nicht gelesen.
Private Falldaten, interne Memos und interne Entscheidungsbegründungen werden
nicht in den Kommunikationskontext übernommen. Konkrete fachlich freigegebene
Quellen sind nach UC-018 weiterhin offen; im Repository wird kein Regelbestand
als bereits freigegeben ausgegeben. `PROPERTYFLOW_TRIAGE_PROVIDER` bleibt ohne
bewusste Konfiguration auf `none`. Die bisherigen Tests verwenden kontrollierte
Modellantworten und ersetzen keine fachliche Evaluation eines produktiven Modells.

## Implementierter Ablauf

1. Die bestehende Gastannahme speichert Fall und Startauftrag gemeinsam.
2. `ProcessStartDispatcher` übernimmt fällige Aufträge atomar und zeitlich begrenzt.
   Der Netzwerkaufruf hält keine Datenbanktransaktion offen.
3. `CamundaProcessStartGateway` gleicht die gespeicherte Startkennung ab und
   startet gegebenenfalls mit derselben `businessId`. Definition und Tenant
   werden beim ersten Versuch festgeschrieben, auch über Neustarts hinweg.
4. `PrepareProcessKey` prüft den Auftrag und die Engine-Herkunft. Es stellt den
   Schlüssel vor Aktivierung des Empfangsbereichs bereit, ohne den Fall zu registrieren.
5. `Register` speichert führende Instanz und Erledigung des Startauftrags atomar.
   Der Status bleibt zunächst `RECEIVED`.
6. `PrepareInitialAuto` erzeugt einen gespeicherten INITIAL-Eintritt und Call-Bezug.
   `AEntry` registriert die tatsächliche Kindinstanz. Elterninstanz und `Automatic`-
   Call werden anhand von Engine-Daten geprüft, nicht nur anhand von Variablen.
7. `Clarification` speichert `UNDER_CLARIFICATION`, Übergangsnachweis und
   Statusmailauftrag atomar. Der persönliche Link zeigt den gespeicherten Status.

Worker liegen unter `caseprocessing/workflow`; Transaktionen und Fachprüfungen
in `CaseWorkflowService`, SQL in `CaseWorkflowStore`. Die neue Migration ist
`V3__case_workflow.sql`. Kein SMTP-Aufruf liegt in einer Fachtransaktion.

## Wiederholung und Fehler

| Situation | Verhalten |
|---|---|
| Start bestätigt oder Business-ID bereits aktiv | Auftrag bleibt bis zum Registrierungscommit offen. |
| Startantwort unbekannt | Dieselbe Kennung abgleichen; niemals eine Ersatzkennung erzeugen. |
| Übernahme abgelaufen | Neuer Versuch möglich; alte Antworten überschreiben weder neue Übernahme noch Registrierung. |
| Prozess beendet, aber nicht registriert | `BLOCKED`; kein automatischer neuer Durchlauf. |
| Fehlerhafter Startvertrag / nicht nutzbare Definition | `BLOCKED` mit technischem Grund. |
| Vorübergehender Speicher-/Verbindungsfehler im Worker | Verzögerter Retry mit positiven verbleibenden Versuchen. |
| Fremde Instanz / falscher Auftrag / veralteter Eintritt | Keine Fachwirkung; Incident mit sicherem Fehlercode. |
| Commit erfolgreich, Jobabschluss verloren | Gespeicherte Wirkung idempotent bestätigen. |
| Später Statusjob | Nie einen fortgeschrittenen oder geschlossenen Fall zurücksetzen. |

Der Starter verarbeitet höchstens 20 Aufträge je Lauf, mit zwei Minuten
Übernahmedauer und Wiederholungsabständen von 5 bis maximal 300 Sekunden.
Worker warten bei vorübergehenden Fehlern fünf Sekunden. Diese technischen
Startwerte verändern keine fachlichen Timer.

Die anfänglichen Zuordnungstabellen bilden die Erstanlage ab. Wiedereröffnung
und QUESTION werden durch diese Dienste nicht freigegeben. Mitarbeiterfortsetzung
und Mitarbeiterabschluss aus SReview/SWork sind unten separat abgegrenzt.

## Unabhängiger Mailversand

`V4__mail_delivery.sql`, `MailDeliveryStore`, `CaseMailService`, `MailProcessDispatcher`
und `CaseMailWorkers` verarbeiten die atomar gespeicherten Annahme-/Statusmailpflichten.
Die nachholbare Projektion erhält dieselbe Auftragskennung. Ein unabhängiger Prozess
aus `fallbenachrichtigung-versenden.bpmn` erhält ausschliesslich `mailOrderRef` und
wird niemals führende Fallinstanz. Er bleibt vom aktiven Fallbereich unabhängig.

Der Versand lädt beim Versuch die aktuelle Kontaktadresse und den aktuell gültigen
persönlichen Link. Texte enthalten nur kurze Ereignisinformation, Fall-ID und
Vertraulichkeitshinweis. Mieterbeschreibung, Memotext, Token und Adresse gelangen
nicht in Prozessvariablen. `SENT` bedeutet dokumentierte SMTP-Annahme, keine
garantierte Zustellung oder Kenntnisnahme.

- Höchstens fünf eindeutig fehlgeschlagene SMTP-Versuche; 30 Sekunden Job-Backoff.
- Bekannter Erfolg wird bei Jobwiederholung nicht erneut versendet.
- Stabile Message-ID pro Auftrag. SMTP selbst bietet keine Exactly-once-Garantie.
- Unklarer Ausgang oder über zwei Minuten unterbrochener Versuch: `UNCERTAIN`,
  kein automatisches erneutes Senden. Dauerhafte Ablehnung: `FAILED`.
- Fachlicher Fehlerpfad speichert einen Prüfgrund. Nach Abschluss entsteht nur ein
  interner Hinweis, keine Wiedereröffnung. Eine spätere positive Bestätigung kann
  den dokumentierten Versand korrigieren, aber keine Mitarbeiterprüfung automatisch verlassen.

Zusätzliche Konfiguration: `PROPERTYFLOW_CASE_MAIL_ENABLED=true`,
`PROPERTYFLOW_MAIL_DEFINITION_KEY`, `PROPERTYFLOW_CASE_MAIL_FROM`,
`PROPERTYFLOW_PUBLIC_BASE_URL` sowie die vorhandenen SMTP-Parameter.
Der Starter bindet Definition und Tenant pro Auftrag dauerhaft.

## Prüfbedarf und Mitarbeiteraufgabe

`V5__staff_review.sql` und `StaffReviewService` koordinieren den unabhängigen
Prüfeingang. `ReviewSignalDispatcher` korreliert eine interne Referenz an die
gespeicherte führende Instanz mit dem synchronen Correlation-API aus ADR-002.
Es wird keine gepufferte Publish-Nachricht mit TTL verwendet. Erst der empfangende
Worker bestätigt die fachliche Übernahme; verlorene Antworten bleiben mit derselben Referenz wiederholbar.
Parallele Eingänge haben eigene lokale Variablen im Event-Subprozess.

Für den aktuellen INITIAL-Automatiklauf sperrt die Bestätigung alte Wirkungsfreigaben
in PostgreSQL. Der Worker überträgt den Marker explizit in den Elternscope;
die Bedingung vergleicht zusätzlich den Call-Bezug. Die Engine unterbricht auch
einen Kindprozess mit Incident. Die Fortsetzung wird exklusiv beansprucht und
die tatsächliche Staff-Kindinstanz registriert. `SReviewStatus` speichert Status,
Nachweis und Mailpflicht zusammen. Danach wird die echte Camunda-User-Task
`SReview` aktiviert.

Mehrere Gründe teilen dieselbe Fortsetzung. Ein reservierter Anspruch gilt noch
nicht als aktive Aufgabe. Derselbe eigene Anspruch bleibt bei Wiederholung `START`;
`EXISTING` erfordert zusätzlich eine nachgewiesene laufende Staff-Instanz mit
aktueller SReview-Aufgabe. Späte Gründe ergänzen diese Aufgabe ohne neue Statusmail.
Ein zwingender Grund geht bei zwischenzeitlicher Datenänderung nicht verloren;
Status, Generation und Call werden am aktuellen gesperrten Fall neu geprüft.

Abgegrenzt sind hier INITIAL → Prüfbedarf → SReview sowie Ergänzung einer offenen
Prüfung. Übergaben aus Mieterwartephase, Bearbeitung, Wiedervorlage und Wiedereröffnung
sind damit noch nicht implementiert. Solche Eingänge werden nicht als erledigt verworfen.

## Bewusste Mitarbeiterentscheidungen und Abschluss

`StaffDecisionService`, `CamundaStaffTaskGateway` und Migrationen V11/V12 binden
die Entscheidung an die tatsächlich aktive native User Task in der registrierten
Staff-Kindinstanz. Führende Instanz, Tenant, Eltern-Call, Aufgaben-Key, Mitarbeiterrolle,
Fallversion und aktueller Prüfgrundbestand werden serverseitig geprüft. Ein alter Tab
darf keine neue Aufgabe automatisch übernehmen. Die Oberfläche lädt eine neue
Entscheidungsgrundlage nur auf bewusste Anforderung.

- `GET /mitarbeiter/faelle/{caseId}/pruefung` liefert die aktuelle Aufgabenfreigabe.
- `POST /fortsetzen` bestätigt ausschliesslich SReview; `POST /abschliessen`
  akzeptiert die aktuell gebundene SReview- oder SWork-Aufgabe.
- Beide POSTs benötigen `operationId`, `userTaskKey`, `version`, `reviewToken` und
  CSRF-Schutz. Das Fortsetzen akzeptiert zusätzlich eine optionale interne `reason`.
- HTTP 200 bedeutet bestätigte native Completion; der fachliche Status folgt erst
  durch den Worker. Ungültige oder veraltete Entscheidungen ergeben HTTP 400,
  technische beziehungsweise unbekannte Ausgänge HTTP 500. Eine unbekannte
  Completion wird weder vom Browser noch von einem Scheduler automatisch wiederholt.

`SProgress` speichert nach erneuter Prüfung «In Bearbeitung», Statusnachweis,
optionale interne Begründung und Mailpflicht atomar. Bei zwischenzeitlicher Änderung
führt `STAFF_DECISION_STALE` über die ergänzte Boundary zurück zu einer neuen
SReview-Aufgabe. Ein bereits gleicher Prüfstatus erzeugt dabei keine weitere Mail.
Eindeutig abgelehnte Entscheidungen verwerfen die Begründung; vorbereitete oder
unklare Aufträge erscheinen nicht als bestätigte Änderungen in der Historie.

«Fall abschliessen» öffnet die vereinbarte Bestätigung ohne zusätzliches Textfeld.
`CaseClosureWorkers` prüft den tatsächlichen abgeschlossenen Staff-Call und lädt
den unveränderlichen Entscheidungsnachweis. `CaseClosureService` speichert unter
Fallsperre Status, Quelle Mitarbeiter, Mitarbeiteridentität, Zeitpunkt und den festen
Abschlussvermerk. Es entsteht weder ein Memo noch eine zusätzliche Nachricht oder
Abschlussmail. Vorhandene Mailpflichten bleiben erhalten. Das terminierende ActiveEnd
beendet danach den aktiven Bereich; alte automatische Schreibfreigaben sind gesperrt.

Ist die bestätigte Abschlussgrundlage inzwischen veraltet, bleibt der Fall offen.
Eine neue exklusive Staff-Fortsetzung ersetzt die beendete Kindinstanz und führt
zur erneuten Prüfung. Wiederholungen erzeugen nur dieselbe Fortsetzung. Das explizite
Output-Mapping an `CloseConflict` überträgt die neue Referenz aus dem Fehler-Scope
in den Elternscope. Ohne dieses Mapping würde der Folgejob den alten Call lesen.

Die Mitarbeiterhistorie zeigt bestätigtes Fortsetzen, Abschluss, manuelle
Dringlichkeit und Objektzuordnung mit Zeitpunkt und Mitarbeiter. Interne Gründe
bleiben ausschliesslich in dieser autorisierten Projektion; sie gehören weder zur
Mieterprojektion noch zum Mail- oder Analysekontext. Die Abschlussimplementierung
ist auf SReview/SWork begrenzt. Abschluss aus Reply, Wiedervorlage und externer
Wartephase sowie Systemabschluss und Wiedereröffnung bleiben weitere Arbeit.

## Interne Memos in der Mitarbeiteroberfläche

`V6__staff_memos.sql` speichert autorisierte ADD_MEMO-Aufträge und interne Memos.
`CaseDataService` prüft Mitarbeiterrolle, Fallversion und serverseitig ermittelte
führende Instanz. Neue Aufträge benötigen erreichbares Camunda und einen aktiven Fall.
`CaseDataSide` empfängt ausschliesslich die interne Auftragsreferenz. `CaseDataApply`
prüft unmittelbar vor Commit Rolle, Generation, Status und bestätigte Version erneut.
Memo, neue Version und Auftragsergebnis werden atomar gespeichert. Aufgabe, Status,
Mailpflichten und letzter Mieterkontakt bleiben unverändert.

Die Oberfläche verwendet `POST /mitarbeiter/faelle/{caseId}/internes-memo-speichern`
mit `text`, `operation` und `csrf`. Die Absende-ID ist eine fallgebundene Zeichenfolge
mit 1–80 UTF-16-Einheiten, keine vorgeschriebene UUID (`V15__staff_action_correlation.sql`).
HTTP 200 ohne Körper bestätigt ausschliesslich das gespeicherte Memo. Der Adapter wartet
nach synchroner Korrelation höchstens zwei Sekunden lesend auf den Worker-Commit;
fehlende Speicherbestätigung ergibt HTTP 500 und erhält Text und Absende-ID.
Die Wartezeit umfasst keine offene Datenbanktransaktion und keinen erneuten Engine-Aufruf.

401/403 schützen Sitzung, Rolle und CSRF; 409 meldet Fall-/Operationskonflikte.
Identische Wiederholung liefert dasselbe Ergebnis, auch nach späterem Fallabschluss.
Geänderter Inhalt unter derselben Operations-ID ist unzulässig. Bereits bestätigte
Korrelation wird dauerhaft vermerkt und nicht nochmals versendet. Eindeutige
Korrelationsablehnung gibt die Absende-ID frei (`V16__rejected_staff_operation.sql`);
der abgelehnte alte Auftrag bleibt ohne Wirkung nachvollziehbar. Nach unbekanntem
Ausgang ist nur eine ausdrückliche Browserwiederholung erlaubt; kein Hintergrundstarter
sendet Mitarbeiter-Falldaten erneut. Die vorhandenen Camunda-Jobs bleiben wiederholbar.
Ein Kontowechsel überträgt keine Eingabe. Interne Memos sind nur im Mitarbeiterverlauf
und in dessen Suche enthalten, nicht in Mieterprojektionen, E-Mails oder Kommunikations-LLM-Kontexten.

Die vorhandenen JSON-Hilfswege `/memos`, `/aktionen/verfuegbar` und
`/aktionen/{operationId}` bleiben für Kompatibilität und lesende Statusabfragen erhalten.
Sie sind keine zusätzlichen fachlichen Aktionen. Berechtigte Mitarbeitende können
fallgebundene Auftragsergebnisse nach erneuter Rollenprüfung lesen.

## Manuelle Dringlichkeit

`V7__case_urgency.sql` erweitert den gemeinsamen Auftragsrahmen um
`SET_MANUAL_URGENCY`. Die vier erlaubten Stufen werden serverseitig geprüft;
Fallstatus, Version und Berechtigung verwenden denselben Nebenaktionsvertrag wie Memos.
`CaseUrgencyService` speichert wirksame Stufe, Quelle, Änderungsnachweis und nötigen
Mailauftrag in der bestehenden Fachtransaktion. Die UI verlangt bewusstes Speichern.

Eine unveränderte manuelle Stufe verändert weder Version noch Historie oder Mailpflicht;
die optionale Begründung wird nach Verarbeitung verworfen. Der Fingerabdruck des
Auftrags schützt weiterhin vor verändertem Inhalt unter derselben Operations-ID.
Die erstmalige manuelle Bestätigung einer gleichlautenden Systemstufe ändert die
Quelle und den internen Nachweis, erzeugt aber keinen öffentlichen Stufenwechsel.
Neue kritische Einstufung erzeugt einen unabhängigen Prüfgrund; niedrigere Stufen
erledigen vorhandene Prüfgründe nicht automatisch.

Mieter sehen nur Stufe, Rollenquelle und öffentliche Stufenwechsel. Mitarbeiteridentität
und optionale Begründung bleiben intern. Die Mitarbeiterliste filtert und sortiert
die gespeicherte Stufe; die Standardsortierung setzt unbewertete Fälle vor Kritisch,
Hoch, Normal und Niedrig, jeweils mit ältestem Mieterkontakt zuerst.
`POST /mitarbeiter/faelle/{caseId}/dringlichkeit-aendern` übernimmt `urgency`
(Kritisch/Hoch/Normal/Niedrig) und optional `reason` (maximal 1'000 Zeichen)
zusätzlich zu `operation`, `version` und `csrf`. HTTP 200 ohne Körper bestätigt
hier die synchrone Korrelation; die sichtbare Stufe folgt dem Worker-Commit.
Veraltete oder unzulässige Aktionen ergeben 400, technische/unklare Ausgänge 500.
Dasselbe gilt für `/objekt-wohnung-zuordnen` mit dem Freitextfeld `assignment`.
Strukturierte Stammdatenreferenzen bleiben gesperrt, solange der Stammdatenport fehlt.
Die gemeinsamen Lesewege liegen unter `/aktionen/verfuegbar` und `/aktionen/{operationId}`.
Die bisherigen Memo-Lesewege bleiben kompatibel.

## Initiale Dringlichkeitsanalyse

Die automatische Abklärung ruft den Teilprozess «Gespraech auswerten» auf.
`AutomaticEvaluationWorkers` führt Analyse, Empfehlung und bei Bedarf die
Abschlussbeurteilung in einzelnen Jobs aus. `ConversationEvaluationService`
prüft den tatsächlichen Aufruf und speichert versuchs- und versionsgebundene
Ergebnisse über `ConversationEvaluationStore`. Die Analyse berücksichtigt den
relevanten Gesprächsstand gemäss KOM-02.

Der externe Aufruf hält keine Datenbanktransaktion offen. Vor einer neuen
Auswertung prüft der Dienst, ob bereits ein aktuelles Ergebnis vorliegt. Vor
dem Speichern und Anwenden prüft er erneut Aufruf und Analysegrundlage.

Eine manuelle Stufe bleibt wirksam. Das Systemergebnis bleibt getrennt nachvollziehbar;
ohne wirksamen Stufenwechsel entstehen weder öffentliche Historie noch Mailauftrag.
Kritische Systemhinweise erzeugen trotzdem verpflichtenden Prüfbedarf, auch bei
niedrigerer manueller Stufe. Eine unveränderte Systemstufe erzeugt ebenfalls keine
zweite Änderungsmail. Fallversionen aus blossen Memos verwerfen keine gültige Analyse;
eine geänderte Zuordnungs-/Analysegrundlage dagegen schon.

Bei einem gespeicherten Analysefehler entfällt die Empfehlung. Die Prüfung des
Auswertungsergebnisses meldet `TRIAGE_ANALYSIS_FAILED` an den Elternprozess.
«Analysefehler und Pruefkontext sichern» prüft den gespeicherten aktuellen
Fehler für die Fortsetzung zur Mitarbeiterprüfung. Speicher- und Enginefehler
führen zu technischen Wiederholungen.

Ohne `PROPERTYFLOW_TRIAGE_PROVIDER` bleibt der Chat-Provider deaktiviert und der
Analyseversuch liefert einen typisierten Fehler für die Mitarbeiterprüfung.
Ein realer Provider wird über die vorhandene Spring-AI-Konfiguration extern
bereitgestellt. Die Tests verwenden kontrollierte Modellantworten und belegen
keine fachliche Qualität eines LLM und keine vollständige Neubewertung von Nachrichtenverläufen.

## Objektzuordnung

`SET_OBJECT_ASSIGNMENT` im gemeinsamen `CaseDataService` und
`V9__object_assignment.sql` bestätigen oder korrigieren die Zuordnung.
`POST /mitarbeiter/faelle/{caseId}/zuordnung` akzeptiert `assignment` mit 1–200 Zeichen
nach Entfernen äusserer Leerzeichen. Innere Leerzeichen bleiben erhalten.
Der Dienst bewahrt die ursprüngliche Mieterangabe und speichert vorherigen/neuen
Wert, Zeitpunkt und Mitarbeiteridentität getrennt. Derselbe gespeicherte Wert ist
ein No-op. Eine erste Bestätigung der ursprünglichen Angabe ist eine echte Änderung.

Die rechte Falldatenkarte bietet eine zunächst geschlossene Bearbeitungsbox mit
«Zuordnung bestätigen» und «Abbrechen». Vorschläge sind bestehende bestätigte
Freitextzuordnungen, ausschliesslich für berechtigte Mitarbeitende; gesucht wird
über `/zuordnung/vorschlaege?q=...`, begrenzt auf 20 Ergebnisse. Es werden keine
Stammdaten erfunden. Strukturierte Objekt-/Wohnungs-IDs werden an diesem reinen
Freitext-Endpunkt abgewiesen, bis ein geprüfter Stammdatenvertrag vorhanden ist.

UI2, UI3 und die berechtigte Mieteransicht lesen dieselbe gespeicherte Zuordnung.
Die Mitarbeiter-Suche berücksichtigt sowohl Ursprungsangabe als auch Zuordnung.
Die Änderung erhöht die Analyseversion, verändert aber keine Kontaktadresse,
Zugangsberechtigung, Mieterkontaktzeit, Mitarbeiteraufgabe oder Mailpflicht.
Die Formular-DOM-Knoten bleiben bei Hintergrundaktualisierung und Sitzungsverlust
erhalten. Konflikte und unbekannter Ausgang verwenden den gemeinsamen Aktionsschutz.

## Mieternachrichten: dauerhafte Annahme und zentraler Eingang

`V13__tenant_messages.sql`, `V14__tenant_message_operation_keys.sql`, `TenantMessageService` und `TenantMessageStore`
speichern bewusst gesendete Mieternachrichten als externe veröffentlichte
Kommunikation. Der persönliche Link wird vor Annahme und unter der Fallsperre
erneut geprüft. Auftraggeber ist der berechtigte Fallzugang; Autor, Sichtbarkeit,
Veröffentlichung, Zeitpunkt und führende Instanz stammen ausschliesslich vom Server.
Eine Absende-ID ist je Fall eindeutig (Formularfeld `operation`, 1–80 UTF-16-Codeeinheiten). Geänderter Text unter derselben ID
wird abgewiesen. CRLF/CR wird vor der Grenze von 2'000 UTF-16-Codeeinheiten zu LF
normalisiert. Entwürfe werden nicht gespeichert.

Vor neuer Speicherung muss die registrierte Instanz tatsächlich aktiv und die
Engine erreichbar sein. Nachricht, Fall-/Analyseversion, letzter Mieterkontakt und
Mailpflicht werden danach gemeinsam gespeichert. Der anschliessende synchrone
Korrelationsaufruf überträgt nur `tenantMessageId`. HTTP 303 am Formular-Endpunkt
`POST /mieter/fall/zugang/{token}/nachrichten` bestätigt Commit und Korrelation und führt zur persönlichen Fallansicht;
unbekannte Übergabe ergibt HTTP 500, ohne einen bereits gespeicherten Text zu löschen.
CSRF und Cookie-Sitzung sind zusätzlich zum Fall-Link erforderlich. Vor Prozessstart
oder bei fachlichem Konflikt wird die neue Speicherung abgewiesen.

Die Oberfläche erhält unbekannte Eingaben mit derselben Absende-ID für eine
bewusste Wiederholung. Zu lange Texte bleiben vollständig editierbar. Hintergrund-
aktualisierung ersetzt ausschliesslich die Lesebereiche. Erst ein erfolgreich
gelesener geschlossener Fall leert und verbirgt das Mieterformular; Netzfehler tun
dies nicht. Mieter- und Mitarbeiterverlauf lesen die gespeicherte Nachricht;
die Mitarbeiter-Volltextsuche findet ihren Text ebenfalls.

`TenantMessageDispatcher` holt noch nicht vom Worker übernommene Eingänge nach.
`TenantInbox` hält jede Nachrichten-ID lokal. `ProcessTenantMessage` erhält nur
Fall-, Führungs- und Nachrichtenreferenz. `NIdentify` prüft die tatsächliche
Eltern-Call-Zuordnung und reserviert genau eine Kindinstanz je Eingang. Ein zweiter
korrelierter Aufruf endet ohne zweite fachliche Wirkung. Geschlossene oder abgelöste
Bearbeitungen dürfen keine neue Wirkung erhalten. Empfang beendet keine Mitarbeiteraufgabe.

Die unabhängige Mailpflicht bestätigt die gespeicherte Nachricht ohne ihren Text
und bleibt nach Abschluss bestehen. Die initiale Analyse berücksichtigt inzwischen
gespeicherte Ergänzungen und kann eine überholte Analyseversion nicht übernehmen.
Sie kürzt den Kontext nicht stillschweigend: Überschreitet die zusammengeführte
Grundlage den bisherigen Triage-Portvertrag, ergibt sich ein typisierter Analysefehler.
Eine grössere zentrale Verlaufsauswertung ist damit noch nicht implementiert.

**Grenze dieses Abschnitts:** Der dauerhafte Eingang ist angebunden; die weiteren
NKeepReview-/NWake-/NAnalyze-/NRecommend-/NApply-Schritte sind noch ausstehend.
Die Nachrichten-Fixture wartet nach NIdentify ausdrücklich vor dieser Auswertung.
`RECEIVED` behauptet deshalb keine fertige Neubewertung oder beantwortete Rückfrage.

## Externe Mitarbeiterkommunikation

`V17__staff_messages.sql`, `StaffMessageService`, `StaffMessageStore` und
`StaffMessageWorkers` binden die vorhandenen Nebenaktionen StaffQuestionSide und
SMessageSide an. Der freigegebene Umfang dieses Abschnitts ist:

- Information während In Abklärung über StaffQuestionPublish, ohne Unterbrechung
  des automatischen Aufrufs und ohne Statuswechsel.
- Information oder Rückfrage während Mitarbeiterprüfung über SMessagePublish;
  dieselbe native SReview-Aufgabe bleibt offen. Rückfragen erhalten einen dauerhaften
  offenen Antwortbedarf; Informationen heben vorhandenen Antwortbedarf nicht auf.
- Information während In Bearbeitung; dieselbe native SWork-Aufgabe bleibt offen.
  Rückfragen aus dieser Phase müssen weiterhin über die noch fehlende native
  SQuestion-Fortsetzung geführt werden und sind in dieser Nebenaktion gesperrt.

Der Mitarbeiter-Aufruf nutzt die bereits beim Kindstart vorhandene `entryContextRef`
als Korrelationsschlüssel. Zielinstanz, Eltern-Call und Tenant werden zusätzlich
gegen die bestätigte Engine-Antwort und vor der Wirkung geprüft. Die Auftragsreferenz
und das Ergebnis bleiben lokal zur jeweiligen Ereignis-Teilprozessinstanz.
Ein erst im Einstiegsworker gesetzter Kindschlüssel wäre zu spät: Camunda legt die
Subscription bereits beim Kindstart an. Der Laufzeittest deckte diesen Fehler auf;
der aktuelle Vertrag verwendet die persistierte Aufrufreferenz.

`POST /mitarbeiter/faelle/{caseId}/externe-nachricht-veroeffentlichen` nimmt nur
`text`, `answer`, `operation` und `csrf` an. HTTP 200 ohne Körper bestätigt die
veröffentlichte Nachricht nach Commit; die Antwort wartet höchstens zwei Sekunden
lesend auf den Worker, niemals auf SMTP. Unbestätigte Veröffentlichung ergibt 500.
Text, Antwortbedarf und Absende-ID bleiben im Browser erhalten; es gibt keinen
automatischen erneuten Sendeaufruf. Eine lesende Auftragsabfrage kann den späteren
Erfolg bestätigen. Identische Wiederholung ist auch nach Fallabschluss erfolgreich.
Ein Wechsel zwischen internem Memo und externer Nachricht unter derselben Absende-ID
ist nicht erlaubt. Eindeutig abgelehnte Aufträge geben ihre ID wieder frei.

Vor Veröffentlichung prüft der Worker Rolle, Fallversion, führenden Durchlauf,
Call-/Aufgabenbezug und die weiterhin offene konkrete Aufgabe. Bereits begonnene
native Entscheidungen sperren eine alte Nebenaktion unter derselben Fallsperre,
auch wenn die Engine-Suche noch die alte Aufgabe anzeigt. Veröffentlichte Nachricht,
Antwortbedarf, neue Analysegrundlage und unabhängige Mailpflicht werden atomar
gespeichert. Mieterkontaktzeit und Status ändern sich dabei nicht. Der Mailprozess
versendet nur den Hinweis auf den Fallverlauf mit Link; der Nachrichtentext gehört
nicht in die E-Mail. Bestehende Mailpflichten überleben einen Fallabschluss.

Mieter- und Mitarbeiteransichten zeigen nur veröffentlichte externe Nachrichten;
interne Memos bleiben getrennt. Beide Fallansichten kennzeichnen den gespeicherten
Antwortbedarf direkt bei der betreffenden Rückfrage; eine reine Information erhält
keine solche Kennzeichnung. Die Mitarbeitersuche und der erlaubte öffentliche
Analysekontext berücksichtigen die neuen Veröffentlichungen. Der bereits bestehende
begrenzte Triage-Port kürzt längeren Kontext weiterhin nicht stillschweigend.

**Fortgeschriebener Stand:** Rückfragen aus der Bearbeitung, Kommunikation in den
Wartephasen, zentraler Antwortabgleich und manuelle Aufhebung sind inzwischen
implementiert und in der Ergänzung am Dokumentanfang beschrieben. Automatische
Katalogveröffentlichung und individueller Systemabschluss stehen weiter aus.
Die sechs Gesamtmodelle bleiben bis zur vollständigen Bindung nicht ausführbar.

## Konfiguration und Betriebsgrenze

- `PROPERTYFLOW_WORKFLOW_ENABLED=true`
- `PROPERTYFLOW_CAMUNDA_URL`: erreichbare Camunda-REST-Adresse
- `PROPERTYFLOW_PROCESS_DEFINITION_KEY`: Schlüssel der geprüften Deployment-Version
- `PROPERTYFLOW_CAMUNDA_TENANT`: standardmässig `<default>`

Die Engine muss `camunda.process-instance-creation.business-id-uniqueness-enabled=true`
setzen. Die Suchschnittstelle ist eventual consistent und ersetzt diese
Eindeutigkeitsprüfung nicht. Für entfernte Umgebungen ist zusätzlich die passende
Clientauthentifizierung extern zu konfigurieren.

Der Abschnitt bleibt standardmässig deaktiviert, solange kein freigegebener
ausführbarer Gesamtprozess vorhanden ist. Die Test-Fixtures dürfen nur in die
isolierte Testengine deployt werden: Sie verwenden dieselben Prozess-IDs,
decken INITIAL, Triage sowie die oben benannten Review-/Falldaten-/Abschlusspfade ab und
warten an nicht implementierten Folgeaktionen. Sie sind keine alternative fachliche Prozessquelle.

Startaufträge enthalten für die Diagnose `state`, `attempts`, `next_attempt_at`,
`lease_until` und `last_result`, keine Tokens oder Mietertexte. Vor Wiederaufnahme
eines blockierten Auftrags sind Engine-Instanz, Zuordnung und Incident abzugleichen.
Keine neue Startkennung vergeben oder führende Zuordnung per Retry überschreiben.

## Tests lokal ausführen

```powershell
docker compose -f dev/compose.backend-test.yaml up -d
docker compose -f dev/compose.workflow-test.yaml up -d
$env:PROPERTYFLOW_TEST_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:5434/propertyflow_test'
$env:PROPERTYFLOW_TEST_CAMUNDA_URL = 'http://127.0.0.1:8085'
$env:PROPERTYFLOW_TEST_SMTP_PORT = '1027'
.\mvnw.cmd '-Dtest=CaseWorkflowPostgresTest,InitialCaseCamundaTest,WorkflowRestartCamundaTest,CaseMailPostgresTest,CaseMailCamundaTest,StaffReviewPostgresTest,StaffReviewCamundaTest,CaseDataPostgresTest,CaseAnalysisPostgresTest,TriageCamundaTest,StaffDecisionPostgresTest,StaffDecisionCamundaTest,CaseClosureCamundaTest,TenantMessagePostgresTest,TenantMessageCamundaTest' test
node --test src/test/browser/stage3/staff-memo.test.mjs src/test/browser/stage3/staff-review.test.mjs src/test/browser/stage3/staff-close.test.mjs src/test/browser/stage3/tenant-messages.test.mjs
```

Die Engine muss vor dem Test bereit sein. Die Datenbanktests prüfen vor
destruktiven Operationen den Namen `propertyflow_test`. Niemals Entwicklungsdaten
verwenden. Beide Compose-Dateien haben getrennte wegwerfbare Datenhaltung und
Ports ausschliesslich auf Loopback.

`InitialWorkflowBindingTest` vergleicht Worker- und Call-Bindungen mit den
Originalmodellen. `WorkflowRestartCamundaTest` betreibt echte Spring-Worker,
prüft Annahme bei nicht erreichbarer Engine und Nachholen nach Anwendungsneustart.
Die CI enthält einen eigenen Job für die PostgreSQL-/Camunda-Integration.

## Noch ausstehend

- B10-Abnahme und bewusste Ausführungsfreigabe der Gesamtmodelle. Alle Service-Tasks
  sind seit V38 technisch angebunden; die Ausführbarkeitsflags bleiben unverändert.
- Fachlich freigegebener Wissensbestand und semantische Evaluation von Abschlussgrund
  und individueller Mitteilung. Die Produktionssperre bleibt geschlossen.
- Vollständige B07/B09-Verträge über alle Zielphasen und kontrollierte Wiederaufnahme
  unklarer Mitarbeiteraufträge. Der nachgewiesene Abgleich aktiver Staff-Nachfolger ist unten beschrieben.
- Weitere Ereignisarten für Mailversand und Prüfbedarf in den noch fehlenden Phasen.
- Betriebsoberfläche für technische Fehler und kontrollierte Wiederaufnahme.

Die UI-Demo ist kein Nachweis persistenter Implementierung dieser weiteren Funktionen.


### Abschluss während Wiedervorlage · V30

Die bestätigte Mitarbeiteraktion verwendet einen dauerhaften Auftrag zur aktuellen
Terminversion und die vorhandene WClose-Nachricht. WCloseTask gibt ausschliesslich
`CLOSE` und den geprüften Auftragsbezug zurück. Der gemeinsame Abschlussdienst prüft
zusätzlich die tatsächlich beendete Mitarbeiterinstanz, aktuelle Berechtigung,
Fallversion und Prüfgründe. Neue Informationen führen zur Mitarbeiterprüfung;
der Fall wird nicht auf einer veralteten Grundlage geschlossen. Die Statusänderung
beendet Termin und offene Antwortbedarfe atomar. Unbekannte Korrelationsergebnisse
werden nicht automatisch wiederholt. Die Gesamtmodelle bleiben bis zur vollständigen
Integration nicht ausführbar.


### Freigegebene Katalogfragen · V31

`Validate`, `Question`, `AWait`, `AReview` und `AutomaticResult` sind an den
Katalogentscheidungsdienst angebunden. Der initiale Pfad verwendet die bereits
angewandte zentrale Auswertung, prüft unter Fallsperre aktuelle Version, Zuordnung,
Prüfgründe und frühere Fragen und veröffentlicht nur feste KOM-05-Texte. Eigene
Systemherkunft und je Frage getrennter Antwortbedarf erscheinen im bestehenden Verlauf.
Die gemeinsame Mailpflicht wird in derselben Transaktion gespeichert. Ein neuer
Prüfgrund verhindert die Veröffentlichung beziehungsweise übernimmt nach erfolgter
Veröffentlichung dieselbe Mitarbeiterfortsetzung. Ein reiner Versionskonflikt führt
zur aktuellen zentralen Auswertung.

Der regulär beendete automatische Aufruf übergibt den vorbereiteten Mieterwarte-Eintritt.
Die Reply-Registrierung prüft nun ausdrücklich Mitarbeiter- oder Automatikherkunft.
Aufhebung des letzten Antwortbedarfs während dieser Übergabe verwendet versionierte
Randereignisse an `AutomaticResult` und `Reply`. Die weitere Anbindung von
`PrepareQuestionAuto` ist im nachfolgenden V32-Abschnitt beschrieben. Automatische
Abschlussantworten bleiben offen.


### Weitere Katalogfragen nach Teilantwort · V32

`RValidate` kann eine notwendige neue Katalogfrage als QUESTION zurückgeben. Der
gebundene `PrepareQuestionAuto` prüft den tatsächlichen abgeschlossenen Reply-Aufruf
und legt einen neuen aktuellen Automatik-Eintritt an. Frühere Eintritte bleiben als
Nachweis erhalten und sind für neue Wirkungen gesperrt. AEntry liefert den gespeicherten
Eintrittstyp; QUESTION durchläuft keine erneute Statussetzung «In Abklärung».
Die aktuelle zentrale Auswertung wird wiederverwendet oder bei geändertem Stand erneuert.
Nur neue Katalogfragen werden veröffentlicht; vollständige Beantwortung und Pflichtprüfung
haben Vorrang. Prüfgründe und manuelle Aufhebung unterbrechen auch den neuen Automatikaufruf.
Reply-Marker prüfen zusätzlich die Eintrittsreferenz, damit alte Marker keinen späteren
Aufruf beenden. Ein während des Call-Wechsels überholter Wake-Job endet; der dauerhaft
vorhandene Prüfgrund wird an den aktuellen Empfangsbereich weitergegeben.

### Direkte Mitarbeiterübernahme · V33

`StaffTakeoverPublish` und `StaffTakeoverSignal` verwenden den dauerhaften Übernahmeauftrag.
POST `/mitarbeiter/faelle/{caseId}/uebernehmen` akzeptiert ausschliesslich Operationskennung,
Fallversion und CSRF; der Zielstatus kommt aus dem geprüften Dienst. Rolle und Version
werden bei Ausführung erneut geprüft. Der Commit erzeugt genau einen Akteurnachweis,
Statuswechsel und Mailauftrag und sperrt alte Automatikfreigaben. Der Hauptpfad übernimmt
SWork oder SReview über den bestehenden exklusiven Mitarbeitereintritt. Eine bereits
beanspruchte Prüfung wird wiederverwendet. Nachträglicher Pflichtprüfbedarf vor dem
Mitarbeitereintritt ändert denselben Eintritt auf REVIEW.

Die Oberfläche holt eine aktuelle Verfügbarkeit ein und verlangt eine bewusste Bestätigung.
Sie zeigt keinen unbestätigten Zielstatus an und wiederholt bei unklarem Ausgang keinen
POST. Der dauerhafte Zusteller schliesst die Übergabefenster nach Automatikende und vor
Reply-Registrierung. Die sechs Gesamtmodelle bleiben weiterhin nicht ausführbar.


### Manuelle Rückfrage in Abklärung · V34

`StaffQuestionPublish` veröffentlicht Frage, Antwortbedarf und Wartestatus atomar.
`StaffQuestionSignal` und ein dauerhafter Zusteller lösen den aktuellen Automatikaufruf
ab, auch nach dessen Ende oder unmittelbar vor der bisherigen Reply-Registrierung.
`AutomaticInterruptionPrepare` lädt den aktuellen Entscheid: Reply oder vorrangige
Mitarbeiterprüfung. Reply prüft die gespeicherte Herkunft und den tatsächlichen Zustand
des abgelösten Automatik-Kindes. Die vorhandene Nachrichtenoberfläche nutzt dieselbe
Verfügbarkeit. Es gibt keinen zweiten Nachrichtenversand.

### Direkter Mitarbeiterabschluss in Abklärung · V35

`StaffCloseClarificationApply` verwendet den gemeinsamen Abschlussdienst. Die neue
POST-Aktion `/mitarbeiter/faelle/{caseId}/abklaerung-abschliessen` akzeptiert nur
Operationskennung, Fallversion, Prüfgrund-Fingerprint und CSRF. UI-Bestätigung und
lesender Ergebnisabgleich erhalten Entwürfe. Der Worker prüft die tatsächliche
führende Prozessherkunft, wiederholt den gespeicherten Erfolg und beendet nach Commit
über `STAFF_CLARIFICATION_CLOSURE_COMMITTED` den aktiven Fallbereich. Es entsteht
kein zusätzlicher Abschluss-Mailauftrag. Originalmodell und Fixture verwenden dieselbe
Subscription, lokale Auftragsvariablen, Ausgangsbedingungen und Eskalation.


### Überholte Mitarbeiterfortsetzungen

`StaffContinuationClaim` prüft den aktuellen Eigentümer vor der Wiederholung eines
früheren Anspruchs. `StaffContinuationReconcile` und der bestehende Merge-Worker
verifizieren den tatsächlich aktiven Staff-Call samt Kindprozess, Elternbezug und Tenant.
Der Dienst prüft den Eigentümer anschliessend erneut und übergibt offene Prüfgründe
dauerhaft. Das normale Ende betrifft nur den redundanten Pfad. Eine Reservierung,
ein unbestätigter Nachfolger oder ein abgeschlossener Fall erhalten keine Endfreigabe.
Zusätzlich prüft der Worker inzwischen aktuelle Reply- und neue Automatik-Nachfolger.
Offene Gründe gehen nach erneuter Eigentümerprüfung in den dauerhaften Eingang zurück.
Ein inzwischen reservierter Reply-Abschluss bleibt geschützt; der alte Abgleich erhält
keine normale Endfreigabe und wiederholt technisch.


### Direkter Mitarbeiterabschluss im Mieterwarten · V36

`StaffCloseReplyCheck` reserviert den aktuellen Reply-Call nach erneuter Prüfung des
Auftrags. Die technische Phase CLOSING sperrt weitere Wirkungen desselben Calls.
`StaffCloseReplySignal` und ein dauerhafter Zusteller übermitteln Call, Eintritt und
Entscheidungsreferenz. `ReplyStaffClose` unterbricht ausschliesslich diesen Aufruf.
Der gemeinsame Close-Worker prüft die tatsächliche Unterbrechung samt Eltern-/Tenant-Bezug.
Bei unveränderter Zustimmung speichert dieselbe Abschlussroutine den Abschluss;
späte Änderungen führen über `STAFF_CLOSURE_STALE` zu einer aktuellen Mitarbeiterprüfung.
Die UI-Aktion `/mitarbeiter/faelle/{caseId}/mieterwarten-abschliessen` verwendet dieselben
Dialog-, CSRF-, Akteur-, Versions- und Fingerprintregeln wie der Abschluss aus Abklärung.


### Direkte Wiedervorlage aus Abklärung und Mieterwarten · V37

Der bestehende StaffFollowSide-Eingang nimmt einen rollen-, versions- und
prüfgrundgebundenen Auftrag entgegen. Datum/Kalendertage, absolute Fälligkeit und
optionale interne Begründung bleiben an dieselbe Operations-ID gebunden. Der
Commit speichert Terminversion, Status, Mailpflicht und bewusste Berücksichtigung
bekannter Gründe gemeinsam und sperrt alte Automatik-/Reply-Wirkungen. Offene
Antwortbedarfe bleiben erhalten.

Ein dauerhafter Dispatcher liefert aufrufgebundene Unterbrechungsmarker nach.
Der bestehende Staff-Claim übernimmt die Fortsetzung; ein bereits beanspruchter,
noch nicht registrierter Mitarbeiter-Eintritt wird wiederverwendet. Registrierung
prüft frühere Kontakte und neuen Prüfbedarf. Ohne Unterbrechungsgrund folgt derselbe
Termin über die vorhandene Hierarchie Hauptprozess → Mitarbeiter → Wiedervorlage.
Ein während der Übergabe fälliger gültiger Termin wird nicht neu berechnet.

Die UI verlangt eine ausdrückliche Bestätigung und zeigt erst den gespeicherten
Ausgang als Erfolg. Unklare Antworten werden nur lesend abgeglichen. Löschen der
direkten Wiedervorlage führt auch bei offenen Fragen zu In Bearbeitung; ein alter
Marker unterbricht eine spätere neue Mieterwartephase nicht.

Nachweise: 284 erfolgreiche Integrations-/Vertragstests am 16.11.2026, 02:22:36
(`target/direct-follow-full.log`) sowie sechs Browserfälle je in Edge, Firefox und
WebKit. Verbliebene ungebundene Service-Tasks: AutoAnswer, AutoClose, AClosed und
PrepareCloseAuto. Die Gesamtmodelle bleiben bis zur vollständigen Integration
nicht ausführbar; produktive Quellen und semantische Modellevaluation sind damit
nicht freigegeben.


### Prüfung vollständiger Quelldateien vor V38 (historischer Nachweis)

CanonicalModelsCamundaTest stellt die sieben Quellen gemeinsam in der Testengine
bereit. Ausschliesslich die Testkopien setzen Ausführbarkeitsflags und vier unerfüllte
Abschluss-Jobtypen. Die implementierten Hauptpfade laufen durch die vollständigen
Scopes, Gateways und Mappings. Sieben Szenarien mit Erstanlage, Übernahme, Abschluss,
externer Wartephase, Wiedervorlage, Teil-/Vollantworten und Wiedereröffnung bestehen;
18 Vertragsprüfungen ergänzen diesen Nachweis (target/canonical-models-fourth.log).
Die Quelldateien behalten ihre Ausführbarkeitsflags. Der automatische Abschluss
und dessen semantische Freigabe bleiben offen; siehe
[Abschlussevaluation](../../evaluation/automatischer-abschluss.md).

### Zentrale Abschlussbewertung und Mitarbeiterhinweis

Die zentrale Auswertung speichert zusätzlich den beleggebundenen Abschlussvorschlag.
Die bestehende Fall-/Analyseversion, Prozessgeneration und Versuchssperre gelten
auch für diesen Modellaufruf. Es entsteht keine zweite unabhängige Auswertung.
In Mitarbeiterprüfung, Bearbeitung, externer Wartephase und Wiedervorlage wird
keine Abschlussbewertung angefordert; erkennbar zwingender menschlicher Prüfbedarf
aus Analyse oder Empfehlung hat ebenfalls Vorrang.

Positive und unklare Vorschläge verwenden bis zum erfolgreichen fachlichen
Modellnachweis den vorhandenen Mitarbeiterpfad. Ein Fehler dieses zusätzlichen
Aufrufs ergänzt den Prüfbedarf, ohne gültige Antwortergebnisse zurückzunehmen.
Die Mitarbeiteransicht zeigt die Erklärung und genannten Belegstellen ausdrücklich
zur Prüfung. Inhalt und Belege werden als Text ausgegeben; sie erscheinen weder
als Systemnachricht für den Mieter noch als bereits erteilter Abschlussauftrag.

Die neun vollständigen Modelltests umfassen nun auch diesen Rückweg für positiven
Vorschlag und Bewertungsfehler. Mit Katalog-, Reply-, zentralen Engine- und
Strukturtests bestehen 101 Prüfungen (`target/central-closure-models-regression.log`,
16.11.2026 03:25:28). Drei Wiedervorlagerückwege und die Zusammenführungen vor
ClearAnswerContinue sowie AutomaticInterruptionPrepare verwenden explizite XORs.
Alle sieben Modelle durchlaufen warnungsfreien Modeler-Import/-Export mit
unveränderten BPMN-/Zeebe-Semantiksignaturen. Die elf Hauptprozessdiagramme bleiben erhalten.

Stand vor V38: 333 erfolgreiche Integrations-/Vertrags-/HTTP-Prüfungen,
keine übersprungenen Tests; anschliessendes JAR-Packaging erfolgreich
(`target/central-closure-full-final.log`, 16.11.2026 03:39:20). Die getrennte
Java-23-Suite erfasst 630 Tests, davon 376 bestanden und 254 mangels externer
Testumgebung übersprungen. Die beiden echten Modellläufe bestehen jeweils nur
21 von 24 Fällen; der automatische Abschluss ist damit weiterhin nicht freigegeben.


### Automatischer Abschluss und individuelle Mitteilung · V38

Alle vier zuvor offenen Aufgaben besitzen jetzt echte Worker-Bindungen:

| BPMN-Aufgabe | Jobtyp | Wirkung |
|---|---|---|
| PrepareCloseAuto | propertyflow.automatic.prepare-close-round | Persistierten CLOSE-Entscheid und tatsächlich beendeten Reply-Call prüfen; neuen Automatik-Eintritt vorbereiten. |
| AutoAnswer | propertyflow.automatic.publish-closure | Individuellen Text ausserhalb der Datenbanktransaktion erzeugen und prüfen; Veröffentlichung und Mailpflicht atomar speichern. |
| AutoClose | propertyflow.automatic.close | Aktuelle Freigabe, Belege, Fallstand, führenden Durchlauf und Mitarbeiterpriorität erneut prüfen; gemeinsamen Abschlussvertrag anwenden. |
| AClosed | propertyflow.automatic.confirm-closure | Tatsächlich gespeicherten Abschluss bestätigen; erst danach CLOSED an den Elternprozess liefern. |

`AutomaticResult` akzeptiert CLOSED nur mit gespeichertem Abschluss und tatsächlich
beendetem Automatik-Kind samt zugehörigem Call. Die Referenz eines anderen Falls,
Kinds oder früheren führenden Durchlaufs erteilt keine Berechtigung.
Der CLOSE-Eintritt erhält «Wartet auf Mieterantwort» und verwendet die zentrale
Auswertung; er setzt den Fall nicht auf «In Abklärung» zurück.

`AutomaticClosureRelease` verweigert in der Anwendung jede Freigabe. Es existiert
kein Property-/Umgebungsschalter zum Aktivieren. Die technischen Positivtests ersetzen
ausschliesslich diese Komponente durch eine synthetische Testfreigabe. Die bisherigen
fehlgeschlagenen Modellberichte bleiben gültige Gegenbelege zur Betriebsfreigabe.

`ClosureMessageService` erzeugt einen freien individuellen Text und lässt den exakten
Entwurf in einem getrennten, begrenzten Modellaufruf auf Aussagen, Widersprüche,
Zusagen, Fragen und unzulässige Inhalte prüfen. Striktes JSON, Grundzuordnung,
Längen-/Textgrenzen und die Bindung der Prüfung an den SHA-256 des Entwurfs sind
zusätzliche technische Kontrollen. Das ist kein Beweis semantischer Richtigkeit.
Beide Aufrufe erhalten nur öffentlichen Verlauf, belegten Grund und gegebenenfalls
die aktuell freigegebene Fachregel. Interne Memos, Akteure und Zugangsgeheimnisse
gehören nicht zum Kontext. Nicht veröffentlichte Entwürfe werden nicht persistiert.

V38 trennt den versionierten Abschlussversuch (`pf_automatic_closure`) von der
stabilen Veröffentlichung (`pf_closure_publication`). Der Veröffentlichungsschlüssel
umfasst Fall, führenden Durchlauf und kanonisch sortierte Belege einschliesslich
Grund und Regelversion. Eine erneute Auswertung derselben Grundlage verwendet die
bereits gespeicherte Nachricht samt Mailpflicht. Die Generierungsreservierung gilt
drei Minuten und ist durch eine eigene Versuchs-ID gegen verspätete Ergebnisse geschützt.

Vor Veröffentlichung führt ein Versionskonflikt über PUBLICATION_BASIS_STALE zur
aktuellen Auswertung. Nach Veröffentlichung greift AUTO_CLOSURE_STALE; Nachricht
und Versandpflicht bleiben erhalten. Inhaltsablehnung verwendet
CLOSURE_MESSAGE_INVALID und die bestehende Mitarbeiterfortsetzung. Technische
Modell-/Speicherfehler verbrauchen Job-Retries und enden bei Ausschöpfung im Incident.
Eine gerade anderweitig laufende Generierung wird ohne Verbrauch des Fehlerbudgets abgewartet.

System- und Mitarbeiterabschluss verwenden dieselbe Bereinigung: Status speichern,
offenen Antwortbedarf als CASE_CLOSED aufheben, Wiedervorlage beenden, alte Automatik
sperren und Prüfhinweise erhalten. Ein Systemabschluss wird ausdrücklich mit Quelle
SYSTEM, ohne erfundenen Mitarbeiter und mit Bezug auf Bewertung, Belege sowie
Veröffentlichung auditiert. Die Mitarbeiterhistorie zeigt diese Herkunft korrekt.
Es entsteht keine zusätzliche Abschlussmail. Ein identischer erfolgreicher Auftrag
bleibt auch nach Entzug der Freigabe erfolgreich; eine frühere Generation darf nach
Wiedereröffnung keine neue Wirkung auslösen.

Die Testkopien aller sieben Modelle ändern nur die Ausführbarkeitsflags. Keine Aufgabe
wird durch einen Platzhalter ersetzt. Die Diagrammgeometrie einschliesslich der elf
Hauptprozessdiagramme bleibt unverändert. Aktuelle Prüfergebnisse stehen im
[Entwicklungsbericht](../../ai-usage/antwortauswertung-und-lebenszyklus-pruefung-2026-11-15.md).


V38-Nachweise: 363 erfolgreiche Prüfungen im Gesamtlauf, danach 95 erfolgreiche
gezielte Prüfungen einschliesslich Rollback und vollständiger Mitarbeiterprojektion,
sowie abschliessend 16 erfolgreiche Pfade der vollständigen kanonischen Modelle.
Die Läufe überschneiden sich. Java 23.0.2 bestätigt 394 ausgeführte Tests; 270
umgebungsabhängige Prüfungen sind dort ausdrücklich übersprungen. Das aktuelle JAR
ist erstellt; semantische Freigabe und produktive Aktivierung bleiben gesperrt.
