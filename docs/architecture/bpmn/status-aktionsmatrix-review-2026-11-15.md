# Status-/Aktionsmatrix und BPMN-Abgleich

Stand der Erstprüfung: 15.11.2026. Nachgeführt um die Entscheidung und Modellierung zu B06 (0.23).
Auftrag: Bericht erstellen; keine Änderung an BPMN, Fachspezifikationen oder Backend.

## Review-Status nach Entscheidung zu B06

**B06: fachlich entschieden und im BPMN-Entwurf ergänzt; Laufzeitumsetzung offen.**
Camunda beauftragt den Mail-Worker in einem eigenständigen Versandprozess anhand
einer dauerhaften Mailpflicht. Zwei Send-Tasks decken Status/Dringlichkeit sowie
Annahme/externe Nachrichten ab. Kein direkter SMTP-Versand durch Fach-Worker und
kein Warten des Fallprozesses auf SMTP. Keine Zusatz-Abschlussmail.
Siehe [Versandmodell](fallbenachrichtigung-versenden.bpmn) und
[BEN-03](../../specifications/benachrichtigungen-und-zustellung.md#ben-03-speicherung-und-zuverlässige-übergabe).

B02/B03 sind teilweise geklärt: Daten ergänzen bleibt in allen aktiven Status eine
Nebenaktion. Übergang 29 (Mitarbeiterrückfrage aus In Abklärung) ist in FALL-06,
im Zustandsdiagramm und im Hauptmodell 0.24 ergänzt. Weitere markierte
Mitarbeiterübergänge sowie technische Synchronisierung bleiben offen.
B01, B04/B05 und B07–B10 bleiben offen. Die folgenden Befunde und Zählwerte dokumentieren
den Stand der Erstprüfung mit fünf Modellen; nun existiert zusätzlich das sechste
Versandmodell mit Jobtypen und Routingbedingungen. B10 bleibt für die
Ausführungsreife der Gesamtlösung relevant. Der nachfolgende ursprüngliche B06-Befund
ist historisch und nicht mehr als fehlendes Versanddiagramm zu verstehen.

## Ergebnis der Erstprüfung

Die besprochenen Kernabläufe sind als Fachentwurf weitgehend vorhanden. Der Prozess
ist aber noch **nicht vollständig modelliert und nicht ausführungsreif**. Die
wichtigsten offenen Stellen betreffen Mitarbeiter-Nebenaktionen, Prüfbedarf während
automatischer Verarbeitung und die explizit vereinbarte Statusmail-Anbindung.
Mehrere Konfliktpfade beschreiben ihre Sicherheit nur im Worker-Text; die sichere
Ausführung ist dadurch noch nicht nachgewiesen.

Eine neue Fachentscheidung ist nicht für jede Lücke nötig. Vieles ist bereits
fachlich festgelegt und muss konsistent modelliert oder technisch konkretisiert
werden. Es wäre falsch, für jeden Eintrag pauschal einen zusätzlichen Service Task
oder eine neue Funktion einzuführen.

## Quellen und Bewertung

- [Fallverwaltung](../../specifications/fallverwaltung.md): FALL-02, 04–07, 10–12 und Prüfkriterien.
- [Fallkommunikation](../../specifications/fallkommunikation.md): KOM-01–06 und Prüfkriterien.
- [Benachrichtigungen](../../specifications/benachrichtigungen-und-zustellung.md): BEN-01, BEN-03, BEN-05.
- [Dringlichkeit](../../specifications/dringlichkeitsbewertung.md), [UI3](../../frontend/ansicht-03-mitarbeiter-falldetail.md).
- [ADR-002](../adr/ADR-002-camunda8-workflow-orchestrierung.md), [Projektkontext](../../project-context.md), [Modulstruktur](../module-structure.md).
- [Hauptprozess](propertyflow-fallprozess-entwurf.bpmn), [Mieterantwort](mieterantwort-pruefen.bpmn), [Mieternachrichten](mieternachricht-verarbeiten.bpmn), [Mitarbeiterbearbeitung](mitarbeiterbearbeitung.bpmn), [Wiedervorlage](wiedervorlage.bpmn).
- [Teilprozess-Verträge](teilprozess-vertraege.md) und [BPMN-README](README.md).

**A – abgedeckt:** Fachregel und sichtbarer Ablauf passen im Entwurf zusammen;
keine Aussage über funktionierende Laufzeit. **L – Lücke:** erlaubte Aktion oder
benötigte Fortsetzung fehlt bzw. ist nur textlich delegiert. **W – Widerspruch:**
Quellen passen nicht eindeutig zusammen. **T – Technik offen:** Fachentscheidung
steht, technische Realisierung und Nachweis fehlen. **–:** nicht vorgesehen,
verboten oder kein BPMN-Schritt nötig. Mehrere Kennzeichen können zusammen gelten.

## Status-/Aktionsmatrix

Abkürzungen: H = Hauptprozess, R = Mieterantwort, S = Mitarbeiterbearbeitung,
Wv = Wiedervorlage, N = Mieternachrichten. Die Element-IDs sind im Modeler/XML suchbar.

| Status | Mieternachricht | Mitarbeiternachricht / Memo | Mitarbeiter-Fortsetzung | Abschluss | Neuer zwingender Prüfbedarf |
|---|---|---|---|---|---|
| Eingegangen | Aktivierung/Empfang erst nach Prozesszuordnung; Zeitpunkt und Rechte technisch binden (T) | Schreibaktionen vor Prozessstart gesperrt (A als Dienstregel) | H: Register → Clarification (A/W, B01) | Kein freigegebener Mitarbeiterpfad vor Aktivierung (–) | Noch kein aktiver Empfang; bei Aktivierung aktuelle Gründe berücksichtigen (T) |
| In Abklärung | H: TenantInbox → N, aktuelle Grundlage vor Wirkung prüfen (A/T) | Prozessfreigabe verlangt, allgemeiner Nebenaktionsweg fehlt (L, B02) | Über Validate/Route zur passenden Fortsetzung; manuelle Übernahme/Terminsetzung nicht eindeutig freigegeben (L, B03) | System: AutoAnswer → Close (A); manueller Abschluss hier nicht explizit modelliert/freigegeben (L, B03) | TriageFailure vorhanden; unabhängiger Grund während Recommend/anderen laufenden Jobs nur generisch delegiert (L, B04/B05) |
| Wartet auf Mieterantwort | RMessage → REvaluate → RValidate; Teilantworten/Restfragen/zulässiger Systemabschluss (A/T) | Manuelles Nachfragen ist zugesagt; Eingang dafür fehlt. Memo-Freigabe nicht konkretisiert (L, B02) | ClearAnswerSide/ReplyCleared: letzter Bedarf → SWork; Teilaufhebung wartet weiter (A/T). Direkte Wiedervorlage nicht modelliert (L, B03) | ReplyStaffClose → Close; Systemweg RClose → AutoAnswer → Close; Konfliktpfade vorhanden (A/T) | ReviewNeedSide → ReplyReviewNeeded → ReviewNeedPrepare → Staff (A/T) |
| Mitarbeiterprüfung erforderlich | NKeepReview ergänzt dieselbe Aufgabe; kein automatisches Verlassen (A/T) | SMessageSide sendet als Nebenaktion, auch mit Antwortbedarf. Memo bleibt Dienst-/Freigabelücke (A/L, B02) | SReview/SDecision: Fortsetzen, Wiedervorlage oder Abschluss (A) | SCloseEnd → H.Close; keine Nachricht nötig (A/T) | ReviewNeedExisting ergänzt dieselbe aktuelle Aufgabe (A/T) |
| In Bearbeitung | NRefresh/NApply aktualisieren Grundlage; menschlicher Vorrang (A/T) | Rückfrage über SQuestion vorhanden; reine Informationsnachricht ohne Aufgabenabschluss sowie Memo nicht explizit angebunden (L, B02) | SWorkDecision: Rückfrage, Wiedervorlage, externe Wartephase, Abschluss, Prüfung (A) | Mitarbeiterweg vorhanden, Systemabschluss ausgeschlossen (A) | SWorkReviewNeeded → SReviewStatus (A/T) |
| Wartet auf externe Rückmeldung | Nachricht allein beendet Wartephase nicht; neuer Prüfbedarf separat (A/T) | Allgemeine Nebenaktion für Information/Memo fehlt; Wirkung einer neuen Rückfrage mit Antwortbedarf nicht vollständig abgegrenzt (L, B02/B03) | SExternalDecision: Fortsetzen, Wiedervorlage, Abschluss (A) | Mitarbeiterabschluss; kein Systemabschluss (A) | SExternalReview → SReviewStatus (A/T) |
| Wartet auf Wiedervorlage | NWake → SContactDuringFollowUp → SStopFollowUp; nicht auf KI warten (A/T) | WQuestionSide/WPublish für Rückfrage; reine Information/Memo nicht ausdrücklich abgegrenzt (A/L, B02) | Termin ändern/löschen; Timer 08:00 Europe/Zurich; keine automatische Wiederherstellung (A/T) | WClose → WCloseTask → SCloseEnd → H.Close (A/T) | SFollowUpReviewNeeded → SStopFollowUp; Timer/Termin beenden (A/T) |
| Abgeschlossen | Nachrichtensperre, Lesen bleibt möglich (A als Dienstregel) | Keine neuen Nachrichten/Memos; zulässige technische Hinweise separat (A) | Mitarbeiter-Wiedereröffnung: neue Instanz, gleiche Case-ID, direkter Mitarbeitereintritt (A/T) | Identischen erfolgreichen Abschluss idempotent bestätigen (A/T) | Nur interner Hinweis, keine Wiedereröffnung; bewusst ausserhalb des beendeten Empfängers (A/T) |

### Weitere Aktionen über Statusgrenzen

| Aktion/Ereignis | Abgleich | Ergebnis |
|---|---|---|
| Keine Mieterantwort | Keine Antwortfrist, Erinnerung, Schweigeeskalation oder automatischer Schweigeabschluss; R hat keinen Timer | A |
| Antwortbedarf aufheben | Gezielte Aufhebung ohne erfundene Antwort; letzte Frage führt aus Reply direkt zu SWork; Prüfung/Wartezustände geschützt | A/T; ältere Ausnahmetexte siehe B08 |
| Manuelle Dringlichkeit | DRING schützt menschliche Einstufung; konkrete Freigabe/Nebenaktionszuordnung fehlt im Modell | L/T, B02 |
| Objektzuordnung bestätigen/korrigieren | FALL-04/UI3 erlauben aktive Bearbeitung; kein ausdrücklicher Prozessvertrag für Freigabe und Prüfgrundfortsetzung | L/T, B02 |
| KI-Formulierungshilfe | UI-Hilfe erzeugt keinen automatischen Versand/Statuswechsel; muss kein eigener BPMN-Task sein | – für eigenen Prozesspfad; Rechte-/Veröffentlichungsprüfung erforderlich |
| Status-/Dringlichkeitsmail | BEN-01 verlangt Versand, BEN-03 nennt einen Camunda-Send-Task; ein solcher Pfad fehlt | L/W, B06 |
| Automatische Antwort vor Abschluss | AutoAnswer erzeugt/prüft/veröffentlicht individuell; danach erneute Close-Prüfung. Keine Zusatz-Abschlussmail | A/T |
| Neuer Prüfgrund nach Mitarbeiterentscheidung | Neue Erkenntnis kann prüfen lassen; gleiche berücksichtigte Erkenntnis nicht | A/T, sichere Erkenntnisidentität fehlt noch |
| Fall lesen | Autorisierte Datenabfrage, keine Prozessfortsetzung nötig | – für BPMN; Zugriffsschutz im Dienst |

## Befunde und Prioritäten

### B01 – Dokumentationswiderspruch zum ersten aktiven Status (W, mittel)

FALL-05 bezeichnet Tabellenpfeil 2 und den Text darunter weiterhin als Vorschlag/
Entwurf. Der Hauptprozess modelliert fest `Register → Clarification → Triage`,
wobei `Clarification` «In Abklärung» speichert. Dieser Einstieg wurde bereits
bestätigt. Keine neue Statusentscheidung nötig: den widersprüchlichen Alttext
später nachführen. Registrierung und Status-Commit bleiben technische Schritte;
«Prozessstart» ist weiterhin kein zusätzlicher fachlicher Status.

### B02 – Mitarbeiter-Nebenaktionen nicht durchgängig zugeordnet (L, hoch)

KOM-01 verlangt eine passende laufende Prozessaktion für externe Veröffentlichung
und Memo. UI3 und DRING erlauben weitere geschützte Änderungen. Vorhanden sind
`SMessageSide` nur für offene `SReview`, `WQuestionSide` für Wiedervorlage und
`SQuestion` als Übergang in den Mieterwartepfad. Nicht vollständig abgedeckt sind
reine Information ohne Aufgabenabschluss, manuelles Nachfragen während `Reply`,
Memo, Zuordnung und manuelle Dringlichkeit in den jeweils erlaubten aktiven Phasen.

Konkreter Testfall: Fall wartet auf Mieterantwort; Mitarbeiter möchte manuell
nachfragen. Das ist in KOM-03 zugesagt, der neue `ClearAnswerSide`-Eingang ist dafür
aber nicht zuständig. Ein Versand darf nicht ersatzweise die Aufgabe abschliessen.

Nächster Entscheid: pro Aktion erlaubte Phasen und statusneutrale Nebenwirkung
festlegen; anschliessend gemeinsamen Freigabevertrag oder gezielte Nebenaktionen
modellieren. Nicht für jedes Memo einen eigenen grossen Prozess bauen.

### B03 – Breite UI-Aktionsbeschreibung versus engere Prozessfreigabe (L, mittel)

FALL-11/UI3 beschreiben Wiedervorlage für aktive Fälle, schränken aber auf
Prozessfreigabe ein. Sichtbar setzen können sie SReview, SWork und SExternalWait.
Aus Reply oder laufender Erstabklärung gibt es keinen direkten Setzpfad. Ähnlich
ist Mitarbeiterabschluss aus In Abklärung nicht ausdrücklich modelliert; die
neu bestätigte Direktaktion gilt nur für Reply.

Das ist **kein Beweis**, dass jede Aktion in jedem aktiven Status erlaubt sein
muss. Es fehlt eine verbindliche Phasenliste. Vor Umsetzung entscheiden: dort
bewusst sperren oder vorhandene Aktion auch dort zulassen. Für externe Wartephase
zusätzlich festhalten, ob eine neu veröffentlichte Rückfrage ihren Status erhält;
FALL-12 klärt den Eingang von Mieternachrichten, nicht alle Sendeaktionen.

### B04 – Neuer Prüfbedarf während automatischer Jobs bleibt generisch (L, hoch)

`ReviewNeedOther` benennt jetzt konkrete Ziele für Bearbeitung, Wiedervorlage und
externe Wartephase. Für `Triage`, `Recommend`, `Question`, `AutoAnswer` oder einen
technisch blockierten automatischen Job gibt es keinen gleichwertig konkreten
Unterbrechungs-/Übernahmepfad. `Validate` und `Close` prüfen Gründe, werden bei
einem blockierten vorgelagerten Job aber eventuell noch nicht erreicht.

Testfall: `Recommend` hängt in einem technischen Fehlerzustand; unabhängig davon
wird klärungsbedürftiger Versand festgestellt. Der neue Grund muss gespeichert
bleiben, aber der sichtbare Entwurf erklärt nicht abschliessend, wie die
Mitarbeiterprüfung dann tatsächlich erreicht wird. Das ist eine Fortsetzungslücke,
kein Beleg für Datenverlust. Fachlich/technisch festlegen, wann unterbrochen wird
und wann der laufende Worker kontrolliert übergibt; keine parallelen Staff-Calls.

### B05 – Unterschiedliche Fehlerpfade für die LLM-Empfehlung (L, hoch)

In N haben sowohl `NAnalyze` als auch `NRecommend` einen Analysefehler-Randpfad zu
`NFailure`. Im Hauptprozess hat `Triage` einen solchen Randpfad, `Recommend` jedoch
nicht. FALL-05 verlangt Mitarbeiterprüfung bei fehlgeschlagener KI-Analyse.

Für ungültige Empfehlung oder endgültig festgestellten fachlichen Analysefehler
in `Recommend` fehlt die eindeutige Abbildung. Technische Retries und fachliche
Fehler nicht gleichsetzen. Entweder geregeltes Fehlerergebnis bis `Validate`
weiterreichen oder ausdrücklichen fachlichen Fehlerpfad vorsehen; Entscheidung
und zugehörige Tests dokumentieren. Das blosse Wort «Fehler» am Route-Ausgang
beantwortet nicht, wie ein fehlgeschlagener Task dorthin gelangt.

### B06 – Statusmail-Anbindung (Entwurf erledigt, technische Umsetzung offen)

BEN-03 beschreibt ausdrücklich: Status-Worker liefert Ereignisreferenz nach
Commit, Camunda-Send-Task ruft wiederverwendbaren Mail-Worker auf. In den fünf
BPMN-Dateien existiert kein solcher Statusmail-Sendeschritt. AutoAnswer und
Mitarbeiter-Veröffentlichungen erwähnen Versandaufträge, ersetzen aber nicht die
Benachrichtigung für reine Status-/Dringlichkeitsänderungen.

Zu klären ist der bereits gewählte Integrationsweg: Modell ergänzen oder die
Send-Task-Vorgabe bewusst neu entscheiden und konsistent dokumentieren. Nicht
versehentlich die abgeschaffte Abschlussmail zurückbringen: BEN-05 nimmt den
Statuswechsel zu Abgeschlossen weiterhin aus. Versandfehler dürfen keinen
unkontrollierten Kreislauf aus Prüfstatusmail und neuem Prüfgrund erzeugen;
Ereignis-/Grund-Deduplizierung ist dafür notwendig.

### B07 – Rückwege setzen bereits vorhandene Prüfung textlich voraus (T/L, hoch)

`ReviewNeedPrepare` und `ClearAnswerContinue` verlangen, eine bereits gleichzeitig
entstandene Prüfung wiederzuverwenden und keine zweite anzulegen. Die Flows
`PN11`/`CA05` führen jedoch direkt zum Call `Staff`. Ein normaler Call-Eintritt
allein beschreibt keine Wiederverwendung einer anderen laufenden Kindinstanz.

Kein nachgewiesener Laufzeitfehler: noch fehlen Worker und Bedingungen. Aber der
Vertrag muss vor Implementierung einen klaren Ausgang für «bereits erledigt /
anderer aktueller Pfad besitzt die Bearbeitung» erhalten oder die exklusive
Übernahme vor Call-Eintritt garantieren. Dasselbe gilt für konkurrierende
`ReplyCleared`, `ReplyStaffClose` und `ReplyReviewNeeded`: die erste Unterbrechung
und der weiterhin gültige fachliche Auftrag müssen abgestimmt werden. Andere
Marker dürfen weder verloren gehen noch einen neuen Call irrtümlich unterbrechen.

### B08 – Nachträge und ältere Texte sind nicht vollständig konsolidiert (W, mittel)

Die neue FALL-05-Regel zur manuellen Aufhebung schützt ausdrücklich auch die
externe Wartephase. Ältere KOM-03-Formulierungen nennen als Ausnahmen beim letzten
Antwortbedarf nur Wiedervorlage und Mitarbeiterprüfung; der neuere Absatz weiter
unten schützt wiederum externe Wartephase. Implementierer könnten unterschiedliche
Absätze als Grundlage wählen. Ebenso enthält die Hauptprozess-Dokumentation noch
«Fachentwurf 0.13», während der Prozessname 0.21 lautet; der Vertragskopf nennt
0.10, obwohl Nachträge bis 0.22 existieren. Unterschiedliche Revisionen je Datei
sind an sich kein Fehler, veraltete Aussagen innerhalb derselben Quelle schon.

Empfehlung: bestätigte Regeln später in die jeweiligen Kernabschnitte integrieren;
Nachträge als Entscheidungshistorie behalten. Keine neuen Regeln aus alten
«offen»-Listen ableiten, bevor ihr aktueller Stand geprüft wurde.

### B09 – Rechtebindung über Kindinstanzen und neue Aktionsarten (T, hoch)

ADR-002 beschreibt den synchronen Abschluss nativer User Tasks anhand Aufgabe,
Fallstand und Prozesszugehörigkeit. Die Teilprozess-Verträge erkennen selbst an,
dass Eltern-/Kindbindung noch präzisiert werden muss. Die neu ergänzten Aktionen
während Reply besitzen ausdrücklich keine Mitarbeiter-User-Task; hier braucht es
einen anderen geprüften Aktionsbezug, ohne die bestehenden Rechte zu lockern.

Vor Umsetzung gemeinsame Verträge festlegen: Mitarbeiteridentität, Fall,
führender Durchlauf, Kind-/Call-/Task-Bezug soweit vorhanden, bestätigter Fallstand,
Idempotenzkennung und Antwort bei früher/später Ablehnung. Eine frei mitgesendete
Prozess-ID ist keine Autorisierung. Das ist primär technische Ausarbeitung,
keine neue Fachfunktion.

### B10 – Fehlende ausführbare Bindungen sind erwartete Arbeit, keine neuen Fachfragen (T)

Alle fünf Prozesse tragen `isExecutable=false`. Statisch vorhanden sind insgesamt
46 Service Tasks, aber keine `zeebe:taskDefinition`; keine der Sequence Flows hat
eine `conditionExpression`. Benannte Gateway-Ausgänge sind damit noch keine
implementierten Entscheidungsbedingungen. Die drei Mitarbeiter-User-Tasks besitzen
noch keine explizite `zeebe:userTask`-Erweiterung. Engine-konforme Task-Typen,
Message-Korrelation, FEEL-Bedingungen, lokale Variablen, Fehlercodes und sichere
Übergaben müssen vor Deployment konkretisiert und gegen die Zielversion geprüft
werden. Dieser Bericht behauptet keine Engine-Kompatibilitätsprüfung.

`followUpDueAt` ist bereits als Timerreferenz eingetragen, aber seine Bereitstellung
fehlt noch. Die Output-Propagation-Flags und expliziten Mappings der Calls müssen
im Engine-Test gemeinsam geprüft werden; aus einem XML-Flag allein wird hier kein
Variablenleck behauptet. Ohne Laufzeittests ist weder die behauptete Einmaligkeit
von Prüfungen noch die Transaktionssicherheit bewiesen.

## Bereits konsistent und nicht erneut zu entscheiden

- Wiedereröffnung: gleiche Case-ID, neue führende Instanz; alte Arbeit bleibt gesperrt.
- Menschliche Prüfung bleibt bis zur bewussten Mitarbeiterentscheidung bestehen.
- Automatischer Abschluss nur in den freigegebenen Status mit fachlichen Belegen;
  individuelle Nachricht vor Abschluss, keine zusätzliche Abschlussmail.
- Mitarbeiterabschluss ohne Nachricht/Kommentar; aktueller Fallstand bleibt Pflicht.
- Antwortbedarf aufheben bedeutet nicht «beantwortet» und nicht «Fall abschliessen».
- Keine Antwortfrist, automatische Erinnerung oder Schliessung wegen Schweigens.
- Wiedervorlage: Kalendertage, 08:00 Europe/Zurich, versionsgebundener Timer;
  neuer Mieterkontakt oder zwingender Prüfbedarf beendet die betroffene Wartephase.
- Externe Wartephase wird nicht durch normale Mieternachrichten automatisch beendet.

## Empfohlene Reihenfolge

1. **B02/B03:** verbindliche Mitarbeiter-Aktionsfreigabe je Status festlegen. Das
   begrenzt den Umfang und verhindert weitere ad-hoc Nebenprozesse.
2. **B04/B05:** Übergabe aus automatischer Verarbeitung bei neuem Prüfbedarf und
   Analysefehlern vervollständigen.
3. **B06:** Statusmail-Integration mit BEN-03/SS-002 abgleichen.
4. **B01/B08:** widersprüchliche Alttexte konsolidieren, ohne Fachregeln neu zu erfinden.
5. **B07/B09/B10:** Worker-/Ereignisverträge und Konfliktprotokoll definieren,
   danach einen kleinen ausführbaren Ablauf implementieren und in Camunda testen.

## Durchgeführte Prüfung und Grenzen

Die fünf XML-Dateien wurden gelesen und ihre Ablaufkanten mit den oben genannten
Spezifikationsabschnitten verglichen. Aktuell geprüft: XML lesbar, eindeutige
Element-IDs, auflösbare Sequence-Flow-Endpunkte, passende incoming/outgoing-Referenzen
und derselbe BPMN-Scope für Quelle, Ziel und Flow. Diese statischen Prüfungen
bestanden. Keine Deployment-, Worker-, Datenbank-, Berechtigungs- oder
Nebenläufigkeitstests durchgeführt. Frühere erfolgreiche Modeler-Importprüfungen
sind keine fachlichen Ausführungstests.

Es wurden nur dieser Bericht erstellt und bestehende Quellen gelesen. Die
Matrix beschreibt das Ziel-/Entwurfsverhalten, nicht den Implementierungsstand
von Demo-UI oder produktivem Backend. Eine vollständige Implementierungsprüfung
und eine Prüfung der konkreten Camunda-Versionsunterstützung sind nicht Teil
dieses Berichts.
