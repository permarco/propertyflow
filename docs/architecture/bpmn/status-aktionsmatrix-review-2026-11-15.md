# Status-/Aktionsmatrix und BPMN-Abgleich

**Konsolidierter Stand:** 15.11.2026, Hauptprozess 0.34, Mitarbeiterbearbeitung und Wiedervorlage 0.33; Automatik 0.31; Mieterantwort 0.32.
Dieser Bericht ersetzt die überholte Erstprüfungs-Matrix. Die Befundkennungen
B01–B10 bleiben für Nachverfolgung erhalten. Frühere Modellstände sind keine
zusätzlichen offenen Anforderungen.

**Umfang:** Konsolidierter Abgleich bis Gesamtstand 0.34. B02.1–B02.3 sind im
Entwurf behandelt; die festgestellten Dokumentationswidersprüche B01/B08 sind bereinigt.
Backend-Implementierung und Laufzeitnachweise stehen weiterhin aus.

## Ergebnis und verbleibende Arbeit

Die bisher markierten Mitarbeiterübergänge sind fachlich entschieden und im
Entwurf modelliert. Automatische Abklärung, gemeinsame Unterbrechung,
Analysefehlerpfad und Mailversand sind ebenfalls modelliert. Das bedeutet noch
keine ausführbare oder getestete Gesamtlösung.

| Befund | Aktueller Stand | Verbleibende Arbeit |
|---|---|---|
| B01 – Erster aktiver Status | Dokumentation bereinigt | Keine offene Fachentscheidung; technische Umsetzung bei B10. |
| B02 – Mitarbeiter-Nebenaktionen | Im Entwurf behandelt | B02.1–B02.3 im Entwurf behandelt; technische Umsetzung bei B07/B09/B10. |
| B03 – Mitarbeiterübergänge | Im Entwurf behandelt | Ausführbare Umsetzung und Prüfung mit B07/B09/B10. Keine erneute Entscheidung über Übergänge 29–33. |
| B04 – Prüfbedarf unterbricht Automatik | Im Entwurf behandelt | Zuverlässige Zustellung, Abbruch bei blockiertem Job und Konflikte zur Laufzeit prüfen. |
| B05 – Fehlerpfad der Vorgehensbestimmung | Im Entwurf behandelt | Fachliche Fehler im Worker ausdrücklich melden; technische Fehler getrennt testen. |
| B06 – Mailanbindung | Im Entwurf behandelt | Dauerhafte Mailpflicht, Start, Wiederholungsschutz und Versandfehler implementieren/testen. |
| B07 – Gleichzeitige Aktionen und einmalige Fortsetzung | Technisch offen | Exklusive Übernahme und Behandlung bereits überholter/erledigter Aufträge verbindlich ausarbeiten. |
| B08 – Dokumentationskonsistenz | Festgestellte Widersprüche bereinigt | Aktuelle Übersicht 0.34 verwenden; historische technische Stände sind ausdrücklich gekennzeichnet. |
| B09 – Rechtebindung und Aktionsbezug | Technischer Vertrag teilweise vorhanden | Rechte über führende Instanz, Kind, Call und gegebenenfalls User Task vollständig konkretisieren. |
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
| [propertyflow-fallprozess-entwurf.bpmn](propertyflow-fallprozess-entwurf.bpmn) | 0.34 | Führender Ablauf, zentrale Eingänge, gemeinsame Unterbrechung, Mitarbeiterabschluss |
| [automatische-abklaerung.bpmn](automatische-abklaerung.bpmn) | 0.31 | Analyse, Ausführungsprüfung, automatische Rückfrage/Antwort/Systemabschluss |
| [mieterantwort-pruefen.bpmn](mieterantwort-pruefen.bpmn) | 0.32 | Antwortauswertung, fachliche Fortsetzung und manueller Sendeweg |
| [mitarbeiterbearbeitung.bpmn](mitarbeiterbearbeitung.bpmn) | 0.33 | Prüfung, Bearbeitung und externe Wartephase |
| [wiedervorlage.bpmn](wiedervorlage.bpmn) | 0.33 | Termin, Timer und Mitarbeiteraktionen zur Wiedervorlage |
| [mieternachricht-verarbeiten.bpmn](mieternachricht-verarbeiten.bpmn) | 0.12 | Zentrale Verarbeitung gespeicherter Mieternachrichten |
| [fallbenachrichtigung-versenden.bpmn](fallbenachrichtigung-versenden.bpmn) | 0.23 | Eigenständiger Versandprozess mit Mail-Worker |

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

**B07 – einmalige Fortsetzung:** Worker-Texte verlangen bestehende Prüfaufgaben
wiederzuverwenden, während Rückwege weiterhin zum Call `Staff` führen. Ein normaler
Call-Aufruf allein verwendet keine andere laufende Kindinstanz wieder. Verbindlich
festlegen: exklusive Übernahme vor Call-Eintritt oder ausdrücklicher Ausgang für
«bereits erledigt / anderer aktueller Pfad zuständig». Keine zweite Aufgabe.

Das betrifft konkurrierende Mitarbeiteraktionen, Mieternachrichten, Prüfbedarf,
Timer und normales Call-Ende. `automaticCallRef` und konsumierbare Marker konkretisieren
die Zuordnung, ersetzen aber noch kein sicheres Transaktions-/Übergabeprotokoll.
Prüfen: Meldung vor Registrierung, während Abbruch/Call-Ende und nach Wiedereröffnung.
Keine verlorene Meldung, kein Wiederbeleben eines alten Aufrufs.

**B09 – Rechtebindung:** Die Verträge verlangen bereits Mitarbeiteridentität,
Fall, führenden Durchlauf, Kind-/Call-Bezug, gegebenenfalls Task-Schlüssel,
bestätigten Fallstand und Idempotenzkennung. Für statusneutrale Aktionen und
Aktionen während Reply/Automatic darf keine nicht vorhandene Mitarbeiter-User-Task
vorausgesetzt werden. Ein frei übergebener Prozessschlüssel autorisiert nichts.

Restarbeit: gemeinsame technische Auftragsform, serverseitige Auflösung des
aktuellen Zielscopes und Verhalten bei früher/später Ablehnung inklusive
HTTP-/UI-Rückmeldung konkretisieren. Unterbrechungen verwenden gespeicherte
Entscheide, nicht normale Outputs des abgebrochenen Kindes. Keine zusätzliche
Fachfunktion nötig; Abgleich mit ADR-002/SS-003 und Implementierung erforderlich.

## B10: Ausführungsreife und Prüfungen

Aktuelle statische Zählung der sieben Dateien am 15.11.2026:

| Merkmal | Anzahl / Stand |
|---|---|
| Prozesse | 7, alle `isExecutable=false` |
| Service Tasks | 69 |
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

1. B07/B09 für den ersten ausführbaren Ablauf technisch konkretisieren, einschliesslich
   der festgelegten Nebenaktionsverträge aus B02.
2. B10 schrittweise implementieren und gegen PostgreSQL/Camunda testen.

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
