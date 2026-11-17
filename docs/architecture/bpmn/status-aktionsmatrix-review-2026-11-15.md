# Status-/Aktionsmatrix und BPMN-Abgleich

**Konsolidierter Stand:** 15.11.2026, Hauptprozess und Mitarbeiterbearbeitung 0.35, Wiedervorlage 0.33; Automatik 0.31; Mieterantwort 0.32.
Dieser Bericht ersetzt die überholte Erstprüfungs-Matrix. Die Befundkennungen
B01–B10 bleiben für Nachverfolgung erhalten. Frühere Modellstände sind keine
zusätzlichen offenen Anforderungen.

**Umfang:** Konsolidierter Abgleich bis Gesamtstand 0.35 mit den dokumentierten
B09.1–B09.5-Verträgen. B02.1–B02.3 sind im
Entwurf behandelt; die festgestellten Dokumentationswidersprüche B01/B08 sind bereinigt.
Backend-Implementierung und Laufzeitnachweise des Gesamtprozesses stehen weiterhin aus.
INITIAL, Mailversand, Prüfbedarf aus der Automatik und interne Memos sind separat
implementiert und mit PostgreSQL/Camunda beziehungsweise HTTP/Browser geprüft;
siehe [technische Nachführung](initial-workflow-runtime.md). B10 bleibt insgesamt offen.

## Ergebnis und verbleibende Arbeit

**Abschluss der fachlichen Definition – bestätigt am 15.11.2026:** Der
Projektverantwortliche erklärt alle sieben Prozesse einschliesslich der
Nebenabläufe als fachlich definiert. Die Entscheidungen und Verträge zu B01–B09
bilden die bestätigte Umsetzungsgrundlage. Die unten aufgeführten technischen
Restarbeiten und Laufzeitnachweise bleiben offen; B10 ist nicht abgeschlossen.
Die visuelle Nachprüfung umfasst 17 Diagrammansichten. Die statischen Prüfungen
und der Modeler-Import/-Export ersetzen keine Ausführung auf Camunda.

Die bisher markierten Mitarbeiterübergänge sind fachlich entschieden und im
Entwurf modelliert. Automatische Abklärung, gemeinsame Unterbrechung,
Analysefehlerpfad und Mailversand sind ebenfalls modelliert. Das bedeutet noch
keine ausführbare oder getestete Gesamtlösung.

| Befund | Aktueller Stand | Verbleibende Arbeit |
|---|---|---|
| B01 – Erster aktiver Status | Dokumentation bereinigt | Keine offene Fachentscheidung; technische Umsetzung bei B10. |
| B02 – Mitarbeiter-Nebenaktionen | Im Entwurf behandelt | B02.1–B02.3 im Entwurf behandelt; technische Umsetzung bei B07/B09/B10. |
| B03 – Mitarbeiterübergänge | Im Entwurf behandelt | Ausführbare Umsetzung und Prüfung mit B07/B09/B10. Keine erneute Entscheidung über Übergänge 29–33. |
| B04 – Prüfbedarf unterbricht Automatik | Für INITIAL-Automatik implementiert; realer Incident wird unterbrochen, eine SReview-Aufgabe entsteht | Weitere Phasenwechsel, Rückkehrwege und Gesamtmodell abnehmen. |
| B05 – Fehlerpfad der Vorgehensbestimmung | Im Entwurf behandelt | Fachliche Fehler im Worker ausdrücklich melden; technische Fehler getrennt testen. |
| B06 – Mailanbindung | Annahme-/Statusmail, dauerhafte Versuche und unabhängiger Originalprozess implementiert; SMTP-Annahme und Fehlerpfade getestet | Neue Ereignisarten mit den weiteren Fachfunktionen anbinden; Betrieb ergänzen. |
| B07 – Gleichzeitige Aktionen und einmalige Fortsetzung | Vertrag festgelegt, Ausgänge im Entwurf 0.35 ergänzt | Technische Umsetzung und Laufzeitnachweise FALL-AK-44 stehen aus. |
| B08 – Dokumentationskonsistenz | Festgestellte Widersprüche bereinigt | Aktuelle Übersicht 0.34 verwenden; historische technische Stände sind ausdrücklich gekennzeichnet. |
| B09 – Rechtebindung und Aktionsbezug | B09.1–B09.5 in der Spezifikation festgelegt | Transportformat, HTTP-/UI-Anbindung, technische Worker-Zugänge und ausführbare Zielbindung implementieren und prüfen. Kein Laufzeitnachweis. |
| B10 – Ausführbare Bindungen und Tests | Offen | Worker-Typen, Gateway-Bedingungen, Korrelation, Variablen und Integrationstests schrittweise ergänzen. |

**«Im Entwurf behandelt» ist kein Laufzeitnachweis.** Der Anbindungsentscheid für B02.3 ist mit CaseDataSide und dem gemeinsamen
Aktionsvertrag getroffen. B01/B08 sind dokumentarisch erledigt. B07/B09/B10
sind technische Umsetzungsvoraussetzungen; sie verlangen nicht automatisch neue
Geschäftsfunktionen oder zusätzliche Diagrammzweige.

## Quellen und Modellbestand

Fachliche Quellen bleiben [Fallverwaltung](../../specifications/fallverwaltung.md),
[Fallkommunikation](../../specifications/fallkommunikation.md),
[Dringlichkeitsbewertung](../../specifications/dringlichkeitsbewertung.md),
[Benachrichtigungen](../../specifications/benachrichtigungen-und-zustellung.md),
[UI3](../../frontend/ansicht-03-mitarbeiter-falldetail.md) und
[ADR-002](../adr/ADR-002-camunda8-workflow-orchestrierung.md).
Die technische Aufteilung beschreibt [teilprozess-vertraege.md](teilprozess-vertraege.md),
die Navigation [README.md](README.md).

| Datei | Modellstand | Zuständigkeit |
|---|---|---|
| [propertyflow-fallprozess-entwurf.bpmn](../../../src/main/resources/bpmn/propertyflow-fallprozess-entwurf.bpmn) | 0.35 | Führender Ablauf, zentrale Eingänge, gemeinsame Unterbrechung, Mitarbeiterabschluss |
| [automatische-abklaerung.bpmn](../../../src/main/resources/bpmn/automatische-abklaerung.bpmn) | 0.31 | Analyse, Ausführungsprüfung, automatische Rückfrage/Antwort/Systemabschluss |
| [mieterantwort-pruefen.bpmn](../../../src/main/resources/bpmn/mieterantwort-pruefen.bpmn) | 0.32 | Antwortauswertung, fachliche Fortsetzung und manueller Sendeweg |
| [mitarbeiterbearbeitung.bpmn](../../../src/main/resources/bpmn/mitarbeiterbearbeitung.bpmn) | 0.35 | Prüfung, Bearbeitung und externe Wartephase |
| [wiedervorlage.bpmn](../../../src/main/resources/bpmn/wiedervorlage.bpmn) | 0.33 | Termin, Timer und Mitarbeiteraktionen zur Wiedervorlage |
| [mieternachricht-verarbeiten.bpmn](../../../src/main/resources/bpmn/mieternachricht-verarbeiten.bpmn) | 0.12 | Zentrale Verarbeitung gespeicherter Mieternachrichten |
| [fallbenachrichtigung-versenden.bpmn](../../../src/main/resources/bpmn/fallbenachrichtigung-versenden.bpmn) | 0.23 | Eigenständiger Versandprozess mit Mail-Worker |

Alle sieben Modelle tragen `isExecutable=false`. Unterschiedliche Modellstände
je Datei sind beabsichtigt. Die führende Fallzuordnung bleibt im Hauptprozess;
Call Activities erzeugen untergeordnete Instanzen, keine neuen Fälle.

## Aktuelle Status-/Aktionsmatrix

«Daten ergänzen» bezeichnet die zentrale Freigabe aus FALL-06: internes Memo,
reine externe Information ohne neuen Antwortbedarf, Objektzuordnung und manuelle
Dringlichkeit. In allen sechs aktiven Status ist dies erlaubt und beendet weder
Aufgabe noch Wartephase. Neuer zwingender Prüfbedarf wirkt über seinen eigenen
Pfad. Die noch fehlende technische Anbindung dieser Freigabe ist B02/B09.

| Status | Mieternachricht | Mitarbeiteraktion / Fortsetzung | Abschluss | Neuer zwingender Prüfbedarf |
|---|---|---|---|---|
| Eingegangen | Aktivierung und aktuelle Zuordnung sind Voraussetzung des aktiven Eingangs | Nur berechtigt lesen; Änderungen bis Aktivierung gesperrt | Kein aktiver Mitarbeiter-Abschlusspfad | Bei Aktivierung aktuellen Stand berücksichtigen; technische Übergabe offen |
| In Abklärung | Zentral verarbeiten; alte Auswertungen dürfen aktuelle Grundlage nicht überschreiben | Daten ergänzen; Rückfrage → Mieterwartephase (29); Termin → Wiedervorlage (31); Übernahme → Bearbeitung mit Prüfvorrang (33) | Systemabschluss unter FALL-07/10; direkter Mitarbeiterabschluss (30) | Gesamten aktuellen Automatic-Call unterbrechen, auch bei blockiertem KI-Job; Mitarbeiterprüfung |
| Wartet auf Mieterantwort | Teilantworten und Restfragen berücksichtigen; nach vollständiger Antwort Prüfung, sofern kein zulässiger Systemabschluss | Daten ergänzen; manuelles Nachfragen als Nebenaktion in RStaffMessageSide; letzter Antwortbedarf aufgehoben → Bearbeitung ohne vorrangigen Prüfbedarf; Termin → Wiedervorlage (32) | Geprüfter Systemabschluss oder direkter Mitarbeiterabschluss (28) | Reply unterbrechen und zur Mitarbeiterprüfung |
| Mitarbeiterprüfung erforderlich | Dieselbe Prüfung ergänzen; kein automatisches Verlassen | Daten ergänzen; Nachricht mit/ohne Antwortbedarf als Nebenaktion; bewusste Entscheidung über Fortsetzung, Rückfrage, Termin oder Abschluss | Nur bestätigter Mitarbeiterabschluss | Dieselbe Aufgabe ergänzen, keine zweite Prüfung |
| In Bearbeitung | Aktuelle Grundlage ergänzen; menschlichen Vorrang erhalten | Daten ergänzen; Rückfrage → Mieterwartephase; Wiedervorlage, externe Wartephase oder Prüfung wählen | Mitarbeiterabschluss; kein Systemabschluss | Bearbeitungsaufgabe ablösen und zur Mitarbeiterprüfung |
| Wartet auf externe Rückmeldung | Ohne neuen zwingenden Prüfbedarf bleibt die Wartephase bestehen | Daten ergänzen; Rückfrage mit Antwortbedarf bleibt Nebenaktion; bewusst fortsetzen, Termin setzen oder abschliessen | Mitarbeiterabschluss; kein Systemabschluss | Externe Wartephase beenden und zur Mitarbeiterprüfung |
| Wartet auf Wiedervorlage | Termin/Timer aufheben und sofort zur Mitarbeiterprüfung, ohne KI abzuwarten | Daten ergänzen; Rückfrage lässt Termin bestehen; Termin ändern oder löschen; Löschen → Bearbeitung auch bei offenen Fragen | Mitarbeiterabschluss; kein Systemabschluss | Wiedervorlage samt Timer beenden und zur Mitarbeiterprüfung |
| Abgeschlossen | Keine neuen fachlichen Nachrichten | Berechtigt lesen; bewusste Wiedereröffnung separat, neue führende Instanz für denselben Fall | Identischen erfolgreichen Auftrag idempotent bestätigen | Nur interner Hinweis, keine automatische Wiedereröffnung |

Diese Matrix beschreibt vereinbartes Entwurfsverhalten, nicht die Implementierung
von UI, Backend oder Engine. Aus der allgemeinen Freigabe «Daten ergänzen» folgt
keine Freigabe zusätzlicher statusändernder Aktionen.

## B02 im Detail: Was bleibt tatsächlich offen?

### B02.1 – Manuelles Nachfragen (im Entwurf 0.32 behandelt)

Die bestehende Nachrichtenfunktion mit optionalem «Mieterantwort erforderlich»
verwendet `RStaffMessageSide` in der aktuellen Reply-Kindinstanz. Nicht unterbrechender
Message-Eingang → `RStaffMessagePublish` → Ende nur der Nebenaktion. Status und
bestehender Warteaufruf bleiben erhalten; neue Fragen ergänzen alte Antwortbedarfe.
Reine Information erzeugt keinen neuen Bedarf. Berechtigungs-, Fallstands- und
Call-Prüfung sind im Vertrag beschrieben. Veralteter unveröffentlichter Auftrag
wird abgelehnt; erfolgreiche Wiederholung erzeugt keine zweite Nachricht/Mailpflicht.
Bewusst erneut gesendeter gleicher Text ist eine neue Nachricht, keine automatische
semantische Dublettenprüfung. Laufende Auswertung berücksichtigt neue Antwortbedarfe.

Quelle: KOM-03, geplante Prüfungen KOM-AK-30. Der Anbindungsentscheid ist getroffen
und im BPMN ergänzt. Technische Korrelation, Konkurrenzschutz, Versand und
Laufzeittests bleiben bei B07/B09/B10 offen; kein Implementierungsnachweis.

### B02.2 – Informationsnachrichten (im Entwurf 0.33 behandelt)

Die fachlich bestätigte statusneutrale Information ist jetzt in allen aktiven
Phasen einem bestehenden Sendeweg zugeordnet:

| Phase | Aktueller Sendeweg | Schutz |
|---|---|---|
| In Abklärung | StaffQuestionSide mit Informationsausgang StaffInformationDone | Kein Ablösemarker; nur Rückfrage mit Antwortbedarf führt zu StaffQuestionSignal. |
| In Bearbeitung | SMessageSide für aktuelle SWork-Aufgabe | Nur Information ohne neuen Antwortbedarf; Aufgabe bleibt offen. Rückfrage bleibt SQuestion-Fortsetzung. |
| Mitarbeiterprüfung / externe Wartephase | SMessageSide wie bisher | Dieselbe Aufgabe/Wartephase bleibt bestehen. |
| Mieterwartephase | RStaffMessageSide (0.32) | Bestehender Reply-Aufruf bleibt bestehen. |
| Wiedervorlage | WQuestionSide für Information oder Rückfrage | Termin, Terminversion und Timer bleiben unverändert. |

Nachricht und Mailpflicht konsistent speichern; alte Fragen erhalten. Aktueller
Fall-/Prozessbezug und Berechtigungen bleiben Pflicht. Veraltete unveröffentlichte
Aufträge ablehnen; erfolgreichen Retry ohne Duplikat bestätigen. Relevante neue
Information muss bei weiteren automatischen Wirkungen berücksichtigt werden,
aber das Senden selbst unterbricht die Automatik nicht. Neuer zwingender Prüfbedarf
bleibt separater Pfad. Quelle: FALL-06 und KOM-AK-31. B02.2 ist im Entwurf behandelt;
technische Umsetzung und Laufzeittests bleiben B07/B09/B10.

### B02.3 – Falldaten ergänzen (im Entwurf 0.34 behandelt)

FALL-06 definiert den gemeinsamen Auftragsrahmen mit Fall-ID, festgelegter
Aktionsart, Aktionsdaten, bestätigter Fallversion und Operations-ID. Identität
und führende Prozesszuordnung ermittelt der Server. CaseDataSide im ActiveCase
ist ein nicht unterbrechender Ereignis-Teilprozess: CaseDataApply prüft und delegiert
an die zuständige Fachlogik für Memo, Zuordnung oder manuelle Dringlichkeit.
Keine Mitarbeiter-User-Task erforderlich; Status, Aufgabe und Timer bleiben bestehen.

Berechtigung und aktueller Fallstand werden vor Speicherung erneut geprüft.
Änderung, erforderlicher Nachweis, Operationsausgang und nötige Mailpflicht werden
konsistent gesichert. Memo bleibt intern; Zuordnung schützt aktuelle Grundlage;
manuelle Dringlichkeit behält Vorrang. Neue zwingende Prüfgründe folgen separat.
Veraltete/unberechtigte Aufträge ohne Wirkung ablehnen; erfolgreiche Wiederholung
ohne zweite Wirkung. Kein Erfolg ohne Bestätigung. Quelle: FALL-06 / FALL-AK-43.

Damit ist B02.3 im Entwurf behandelt. Technische Korrelation, atomare Speicherung,
Auftrags-/HTTP-Rückmeldung und Ausführungstests sind weiterhin B07/B09/B10.
Keine Backend-Implementierung durch die Modellergänzung.

### B02: Bereits geklärt – nicht erneut öffnen

- Daten ergänzen in allen aktiven Status; gesperrt vor Aktivierung und nach Abschluss.
- Nachricht während Mitarbeiterprüfung beendet dieselbe Prüfaufgabe nicht.
- Rückfrage während externer Wartephase beendet diese nicht.
- Rückfrage während Wiedervorlage lässt Termin/Timer bestehen.
- Mitarbeiterrückfrage aus Abklärung führt kontrolliert in die Mieterwartephase.
- KI-Formulierungshilfe allein veröffentlicht nichts und braucht keinen eigenen BPMN-Task.

**Abschlusskriterium B02:** Für jede oben erlaubte Aktion ist ein konkreter,
statusgerechter Auftragsweg oder ein gemeinsamer Freigabevertrag benannt; keine
Aktion muss ersatzweise eine Aufgabe oder Wartephase beenden. Laufzeitprüfung
bleibt anschliessend Teil von B09/B10. Dieses Kriterium ist im Entwurf 0.34 für B02.1–B02.3 erfüllt;
die technische Ausführbarkeit ist weiterhin offen.

## B01 / B08: Dokumentationsbereinigung abgeschlossen (0.34)

**B01:** Übergang 2 und der Erläuterungstext in FALL-05 benennen «In Abklärung»
jetzt als bestätigten ersten aktiven Status bei Erstanlage. Registrierung und
Statusspeicherung bleiben getrennte technische Schritte; Wiedereröffnung und
Mieterantwort-Fortsetzungen werden nicht pauschal auf diesen Einstieg zurückgesetzt.

**B08:** Der KOM-03-Kerntext zum letzten aufgehobenen Antwortbedarf schützt nun
auch die externe Wartephase. Der SMessageSide-Vertrag in KOM-01 und den Teilprozess-
Verträgen enthält die Freigabe für Informationsnachrichten während SWork; Rückfragen
mit Antwortbedarf bleiben dort im SQuestion-Fortsetzungsweg.

README und Teilprozess-Verträge besitzen aktuelle Gesamtübersichten 0.34 mit allen
sieben Dateien und den Nebenaktionswegen. Unterschiedliche Modellrevisionen bleiben
sichtbar. Ersetzte Einzelmarker/-randpfade und alte Zählwerte sind als Historie
gekennzeichnet. Frühere Review-Nachführungen «B02.3 bleibt offen» stehen unten nur
noch als damaliger Stand. Keine neue Fachregel, keine BPMN- oder Backend-Änderung
in dieser Bereinigungsrunde. Laufzeitnachweise sind davon unberührt weiterhin offen.

## B03–B06: Im Entwurf behandelt

**B03:** Übergänge 29–33 decken Mitarbeiterrückfrage, direkten Abschluss,
Wiedervorlage und Übernahme aus Abklärung sowie Wiedervorlage aus der Mieterwartephase
ab. Terminlöschung führt auch bei offenen Fragen zu «In Bearbeitung» (17).
Die Mitarbeiterrückfrage während externer Wartephase bleibt statusneutral.
Nachweise sind als FALL-AK-35 bis FALL-AK-40 geplant, nicht ausgeführt.

**B04:** `ReviewNeedSide` prüft den neuen Grund. Bei aktiver Automatik stellt
`ReviewNeedOther` den aufrufgebundenen Entscheid im Elternscope bereit.
`AutomaticInterrupted` unterbricht den gesamten Call, auch bei blockierten Jobs.
`AutomaticInterruptionPrepare` berücksichtigt Prüfvorrang und die aktuelle
Fortsetzung. Die alten `AutoReviewAt…`-Randereignisse sind durch 0.31 ersetzt.
Geplant: FALL-AK-41/42. Die Sperre verspäteter Worker ab Bestätigung und zuverlässige
Übergabe sind technische Anforderungen, kein Beweis durch das Diagramm.

**B05:** Triage und Recommend melden festgestellte fachliche Analysefehler
explizit als `TRIAGE_ANALYSIS_FAILED`. `AutomaticFailure` am gemeinsamen Call führt
über `AutomaticFailurePrepare` zur Mitarbeiterprüfung. Ungültiger Nachrichtentext
und veraltete Grundlage behalten ihre eigenen Kindpfade. Technische Fehler sind
Retry/Incident, nicht automatisch BPMN-Fehler. Worker und Ausführungstests fehlen.

**B06:** Der eigenständige Mailprozess enthält zwei Send-Tasks. Fachliche Änderungen
sichern die nötige Mailpflicht dauerhaft; Camunda beauftragt den Mail-Worker.
Der Fallprozess wartet nicht auf SMTP. Keine zusätzliche Abschlussmail;
Benachrichtigung zu bereits veröffentlichten Nachrichten bleibt erhalten.
Implementieren/testen: zuverlässiger Start, Wiederholungsschutz, SMTP-Ungewissheit
und Prüfgrund-Deduplizierung ohne Benachrichtigungsschleife.

## B07 / B09: Technische Verträge vor ausführbarer Umsetzung

**B07 – Vertrag und Modell ergänzt (0.35):** FALL-06 beschreibt Fallversion,
Operations-ID, verifizierten Bearbeitungsbezug, dauerhaften Fortsetzungsauftrag
und exklusive Übernahme. Alle bisherigen Staff-Eingänge führen über
StaffContinuationClaim/StaffContinuationRoute. START startet einmalig den Call;
EXISTING ergänzt die laufende Bearbeitung; SUPERSEDED gleicht den alten Auftrag
mit der aktuellen Zuständigkeit ab. Gespeicherte neue Prüfgründe gehen bei einem
Versionskonflikt nicht verloren. Veraltete Mitarbeiterentscheidungen werden abgelehnt.

Eigene Übernahme bleibt bei Retry START. Unbestätigte Reservierung verlangt
Abgleich, keinen zweiten Call. Die redundanten Endpfade sind nur bei gesichertem
anderem lebendem Hauptpfad zulässig; kein versehentliches Ende von ActiveCase.
SEntryCheck bindet die tatsächliche Kindinstanz an den Anspruch. Prüfplan FALL-AK-44.
Keine implementierte Transaktion/Korrelation und kein Laufzeitnachweis; technische
Ausführungsarbeit bleibt B09/B10. Der ursprüngliche fehlende Fortsetzungsvertrag
und die fehlenden Ausgänge sind damit im Entwurf behandelt.

**B09 – Rechtebindung:** Die Verträge verlangen bereits Mitarbeiteridentität,
Fall, führenden Durchlauf, Kind-/Call-Bezug, gegebenenfalls Task-Schlüssel,
bestätigten Fallstand und Idempotenzkennung. Für statusneutrale Aktionen und
Aktionen während Reply/Automatic darf keine nicht vorhandene Mitarbeiter-User-Task
vorausgesetzt werden. Ein frei übergebener Prozessschlüssel autorisiert nichts.

**B09.1 – festgelegt (15.11.2026):** Berechtigung bei Annahme und unmittelbar vor
der fachlichen Ausführung erneut prüfen. Kontosperre oder Entzug erforderlicher
Rechte lehnen einen noch nicht ausgeführten Auftrag ohne fachliche Änderung ab;
der Grund wird am Auftrag protokolliert. Logout allein widerruft einen gültig
angenommenen Auftrag nicht. Bereits wirksame Änderungen bleiben bestehen.
Verbindliche Regel und geplante Prüffälle:
[ZUG-04 · verzögerte Mitarbeiteraufträge](../../specifications/fallzugriff-und-sicherheit.md#berechtigung-bei-verzögerter-ausführung-von-mitarbeiteraufträgen-b09).
Dokumentarisch festgelegt; keine Backend-Implementierung oder Laufzeittests.

**B09.2 – festgelegt (15.11.2026):** Gemeinsamer Auftragsrahmen mit Fall-ID,
Aktion/Eingaben, bestätigter Fallversion, Operations-ID, serverseitig übernommener
Mitarbeiteridentität und geprüftem Bearbeitungsbezug. Alte Entscheidungen werden
nicht auf die jeweils neueste Aufgabe oder einen neuen Durchlauf übertragen.
Nebenaktionen setzen keine nicht vorhandene Mitarbeiter-User-Task voraus.
Vertrag und geplante Prüffälle stehen zentral in
[FALL-06 · gemeinsamer Auftragsrahmen](../../specifications/fallverwaltung.md#gemeinsamer-auftragsrahmen-für-mitarbeiteraktionen-b092).
Kein neuer universeller HTTP-Endpunkt, keine Backend- oder BPMN-Änderung und kein
Laufzeitnachweis. Zielauflösung siehe B09.3; das konkrete Transportformat bleibt
Implementierungsarbeit.

**B09.3 – festgelegt (15.11.2026):** Das Backend liefert den Bearbeitungsbezug zur
angezeigten Aktion und prüft ihn beim Absenden gegen die verifizierte Zuordnung.
Aktionsart und Kontext bestimmen Aufgabe, Nebenprozess, Wiedervorlage oder
Wiedereröffnungseinstieg. Überholte Bezüge werden ohne Übertragung auf neue Ziele
abgelehnt. Unklare Zuordnung verlangt technischen Abgleich gemäss B07, keinen
Ersatzprozess und keine falsche Erfolgsmeldung. Eine unklare Engine-Antwort ist
keine nachgewiesene Ablehnung.
Vertrag und geplante Prüffälle:
[FALL-06 · serverseitige Zielauflösung](../../specifications/fallverwaltung.md#serverseitige-zielauflösung-für-mitarbeiteraufträge-b093).
Keine Backend- oder BPMN-Änderung und kein Laufzeitnachweis.

**B09.4 – festgelegt (15.11.2026):** Auftragsrückmeldungen unterscheiden
angenommen/in Verarbeitung, erfolgreich ausgeführt, abgelehnt und technisch
unklaren Ausgang. Erst bestätigte fachliche Wirkung erlaubt die Erfolgsmeldung.
Frühe und späte Ablehnung bleiben sichtbar, Eingaben erhalten. Timeout erzeugt
weder eine sichere Ablehnung noch einen neuen Auftrag; Abgleich mit derselben
Identität gemäss B07. Keine zusätzlichen Fallstatus.
Vertrag und geplante Prüffälle:
[FALL-06 · Auftragszustände und UI-Rückmeldungen](../../specifications/fallverwaltung.md#auftragszustände-und-ui-rückmeldungen-b094).
Keine Backend- oder BPMN-Änderung und kein Laufzeitnachweis.

**B09.5 – festgelegt (15.11.2026):** Worker führen nur ihre vorgesehene Fachaktion
aus. Der Dienst lädt den Auftrag über die Referenz und prüft Herkunft, aktuelle
Rechte beziehungsweise Systemregeln, Fallstand und Bearbeitungsbezug vor der
Wirkung. Job, frei gesetzte Freigabevariable oder LLM-Empfehlung allein autorisieren
nichts. Mailversand verwendet den gespeicherten Versandauftrag; eine bereits
bestehende Mailpflicht bleibt vom späteren Fallabschluss getrennt.
Vertrag und geplante Prüffälle:
[FALL-06 · Worker-Rechte](../../specifications/fallverwaltung.md#worker-rechte-und-prüfungen-vor-der-ausführung-b095).
Keine Backend- oder BPMN-Änderung und kein Laufzeitnachweis.

**B09 ist damit auf Vertragsebene behandelt.** Offen bleiben das konkrete
Transportformat, HTTP-Anbindung und UI-Aktualisierung, technische Worker-Zugänge
mit begrenzten Rechten sowie die ausführbare Zielbindung und deren Tests unter B10.
Unterbrechungen verwenden gespeicherte
Entscheide, nicht normale Outputs des abgebrochenen Kindes. Keine zusätzliche
Fachfunktion nötig; Abgleich mit ADR-002/SS-003 und Implementierung erforderlich.

## B10: Ausführungsreife und Prüfungen

Aktuelle statische Zählung der sieben Dateien am 15.11.2026:

| Merkmal | Anzahl / Stand |
|---|---|
| Prozesse | 7, alle `isExecutable=false` |
| Service Tasks | 72 |
| Send Tasks | 2 |
| `zeebe:taskDefinition` | 4 |
| Sequence-Flow-`conditionExpression` | 3 |
| BPMN-User-Tasks | 3 |
| `zeebe:userTask`-Erweiterungen | 0 |

Benannte Gateway-Ausgänge sind überwiegend noch keine ausführbaren Bedingungen.
Task-Typen, Jobverträge, FEEL-Routing, Message-Korrelation, lokale Variablen,
Timerbereitstellung und Call-Mappings müssen vollständig gebunden werden.
Camunda 8.9 unterstützt Conditional Events; daraus folgt keine nachgewiesene
Ausführbarkeit unserer Dateien. Die konfigurierte Zielversion ist 8.9.19.

Vorherige Strukturprüfungen für 0.31: XML-Schema, eindeutige IDs, Flussreferenzen,
Scopes und Call-Ziele sowie Modeler-Import/Export ohne Warnungen. Diese Prüfungen
wurden nicht im Rahmen der jetzigen reinen Berichtskonsolidierung wiederholt.
Keine Deployment-, Worker-, Datenbank-, Berechtigungs- oder Nebenläufigkeitstests
für diesen gesamten BPMN-Entwurf nachgewiesen. FALL-AK-34 bis FALL-AK-42 sind
Prüfpläne, keine erfolgreichen Testläufe.

## Nächste Schritte

1. Für B10 einen ersten durchgängigen Ablauf abgrenzen und die ausführbaren
   Jobverträge, Zielbindungen und HTTP-Rückmeldungen anhand B07/B09 festlegen.
2. Nach gesonderter Freigabe schrittweise implementieren und gegen
   PostgreSQL/Camunda testen, einschliesslich Rechte-, Konflikt- und Retry-Fällen.

Bestehende Entscheidungen bleiben verbindlich: menschlicher Prüfvorrang,
Nachricht vor Systemabschluss, Mitarbeiterabschluss ohne Nachrichtenpflicht,
keine Schweigefristen, Wiedereröffnung mit neuer führender Instanz sowie die
bestätigten Timer- und Wartephasenregeln. Dieser Bericht eröffnet diese Fragen
nicht erneut.

## Nachführung B02.1 (0.32) – historischer Zwischenstand

Die folgenden offenen Punkte beziehen sich auf diesen damaligen Stand. Aktuell
sind B02.1–B02.3 im Entwurf behandelt; siehe Zusammenfassung oben.

Mieterantwort-Nebenaktion, Spezifikation und Verträge wurden ergänzt. B02.1 ist
im Entwurf behandelt; B02.2 ist für die Mieterwartephase ebenfalls abgedeckt.
B02.3 bleibt offen. Diese Nachführung ersetzt die frühere Aussage, der Reply-
Teilprozess habe keinen Mitarbeiter-Sendeweg. Noch keine Backend-Implementierung.

Statische Prüfung zu 0.32: alle sieben Modelle bestehen XML-Schema-, ID-/
Flussreferenz-/Scope-Prüfung und Modeler-Import/Export ohne Warnungen.
Die neue Nebenaktion wurde in der gerenderten Mieterantwort-Ansicht kontrolliert.
Keine Laufzeittests oder Backend-Änderungen.

## Nachführung B02.2 (0.33) – historischer Zwischenstand

Die folgenden offenen Punkte beziehen sich auf diesen damaligen Stand. Aktuell
sind B02.1–B02.3 im Entwurf behandelt; siehe Zusammenfassung oben.

Bestehende Sendewege für Informationen erweitert, ohne neue grosse Teilprozesse.
B02.1 und B02.2 sind im Entwurf behandelt; B02.3 bleibt die Anbindungslücke.
KOM-AK-31 ist ein Prüfplan, kein Laufzeitnachweis. Keine Backend-Logik geändert.

Statische Prüfung zu 0.33: alle sieben BPMN-Dateien bestehen XML-Schema-,
ID-/Flussreferenz-/Scope-Prüfung und Modeler-Import/Export ohne Warnungen.
Informationsausgang in der gerenderten Abklärungs-Nebenaktion kontrolliert.
Keine Ausführungstests für die Sendewege.

## Nachführung B02.3 (0.34)

Gemeinsamer Aktionsvertrag in FALL-06 und CaseDataSide im Hauptmodell ergänzt.
B02.1, B02.2 und B02.3 sind im Entwurf behandelt. Frühere Nachführungen mit
«B02.3 bleibt offen» beschreiben ausschliesslich den damaligen Stand.
FALL-AK-43 bleibt Prüfplan; keine Backend-Logik geändert.

Statische Prüfung zu 0.34: sieben BPMN-Dateien bestehen XML-Schema-,
ID-/Flussreferenz-/Scope-Prüfung und Modeler-Import/Export ohne Warnungen.
Detailansicht von CaseDataSide gerendert und kontrolliert. Keine Laufzeittests.

## Nachführung B01/B08 (Dokumentation 0.34)

Die benannten Widersprüche wurden in den Kerntexten korrigiert; aktuelle Übersichten
und historische Stände sind getrennt. Geprüft wurden lokale Dokumentlinks und die
betroffenen Aussagen. BPMN und Backend wurden in dieser Runde nicht geändert;
keine erneuten Engine-/Laufzeittests erforderlich oder behauptet.

## Nachführung B07 (0.35)

Fortsetzungsvertrag und drei Ausgänge vor Staff ergänzt, Mitarbeiter-Kindeintritt
an exklusiven Übernahmebezug gebunden. B07 ist im Entwurf behandelt; Umsetzung
und Nebenläufigkeitstests bleiben offen. Keine Backend-Logik geändert.
