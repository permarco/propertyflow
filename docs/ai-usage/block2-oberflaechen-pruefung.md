# KI-Nutzung und Review des Block-2-UI

## Zweck und Stand

Dieses Dokument hält Spezifikation, Dokumentprüfung und die anschliessenden Nachweise der lokalen Frontend-Demo fest. Die datierten Abschnitte bis 10.10.2026 beschreiben den damaligen Spezifikationsstand, nicht den heutigen Implementierungsstand. Die [Unit-Tests und Veto-Notiz](#ergänzung-vom-12102026-generierte-unit-tests-und-mutation) sowie die [Browser- und Performance-Nachweise](#ergänzung-browser-qualitätsnachweis-und-präsentationsentscheidung) dokumentieren die später ausgeführten Prüfungen und ihre Grenzen.

**Stand:** Nachweise vom 12.10.2026; Dokumentverweise geprüft am 13.10.2026

## Auftrag und Grundlagen

Marco beauftragte Codex, die [Mitarbeiter-Fallübersicht](../frontend/ansicht-02-mitarbeiter-falluebersicht.md) nochmals vertieft zu analysieren und die betroffenen Dateien auf Übereinstimmung zu prüfen. Grundlage waren die zuvor bestätigten Chat-Entscheidungen, die Repository-Anweisungen, der [Projektkontext](../project-context.md), Vision, C4-Sichten, Modulstruktur und die akzeptierten ADRs.

Geprüft wurden beide vorhandenen Screen-Spezifikationen, alle fünf Fachspezifikationen, UC-001 bis UC-004 sowie Projektkontext, README und Evaluationsgrundlage. Quellcode und Infrastruktur wurden auf vorhandene fachliche Implementierung eingeordnet: Unter `src` ist bisher das Hello-World-Grundgerüst vorhanden. Die Camunda-BPMN-Dateien unter `infra/camunda/tests/resources` sind Infrastruktur-Testressourcen, kein implementierter PropertyFlow-Fachprozess.

## Festgestellte Abweichungen und Korrekturen

| Befund | Korrektur und führende Quelle |
|---|---|
| Bestätigte Listenfunktionen waren teilweise weiterhin pauschal als Vorschläge markiert. | Verbindlichkeit in Screen 02 bereinigt; offene technische Details und Layoutentwürfe bleiben ausdrücklich erkennbar. |
| Der Listen-Wireframe enthielt «Aktiv» statt Bearbeitungsstatus, einen Dringlichkeitsplatzhalter und mobil eine zusätzliche Beschreibung. | Vereinbarte Spalten, wirksame Stufen und Änderungsquellen, korrekte Prüfstatus und Beispiele für die bestätigte Sortierreihenfolge eingetragen. |
| Vorrang manueller Einstufungen und Erhalt der bisherigen Stufe wirkten in der Liste noch wie offene Fachregeln. | Anzeige, Filter und Sortierung verwenden die wirksame Stufe nach DRING-05/DRING-06; technische Synchronisierung bleibt offen. Auch eine manuelle Einstufung vor dem ersten Systemergebnis ist geschützt. |
| Allgemeine Fehlertexte in FALL-06, UC-004 und BEN-AK-06 widersprachen dem Prüfstatus aus FALL-05. | Erhalt von Ursprungseingaben und Nachrichten von zulässigen Status-/Hinweisänderungen getrennt; bei aktiven Fällen gelten die drei vereinbarten Prüfgründe, auch während Retries. Abgeschlossene Fälle bleiben abgeschlossen. |
| UC-002 beschrieb noch eine einfache Liste ohne die inzwischen bestätigten Bedienfunktionen. | Vereinbarte Spalten, Suche, Filter, Sortierung, 50er-Seiten, Aktualisierung und separate Detailseite übernommen; UC-001/003/004 gezielt abgeglichen. |
| Suchumfang und einheitliche Adminrechte waren nicht durchgehend in den zentralen Quellen und Prüfkriterien berücksichtigt. | KOM-01, ZUG-04 und ihre Prüfkriterien abgeglichen: veröffentlichte externe Nachrichten und alle gespeicherten internen Memos; keine externen Entwürfe und kein Mieter-Suchzugriff. |
| Ältere Beschreibungen der Mieterhistorie begrenzten sie ausschliesslich auf Nachrichten. | Freigegebene Dringlichkeitsereignisse in Kommunikationsregeln, Mieteransicht, Aktualisierung, Benachrichtigungen und abgeschlossenem Wireframe berücksichtigt. Interne Auditdaten bleiben ausgeschlossen. |
| Der letzte Mieterkontakt sowie neue Regeln und Prüfkriterien fehlten in übergreifenden Verweisen. | Zeitmodell unter FALL-08 geführt; Quellenverweise in Projektkontext, README und Evaluationsgrundlage ergänzt. |

## Prüfung der Dokumentänderungen

Neben dem inhaltlichen Abgleich wurden die lokalen Markdown-Verweise einschliesslich Abschnittsankern, geschlossene Codeblöcke, eindeutige und fortlaufende Prüfkriterien-IDs sowie die Spaltenausrichtung des Listen-Wireframes geprüft. Die Leerzeichenprüfung bezieht sich auf die Änderungen der Arbeitskopie gegenüber dem vorhandenen Index; bereits vorgemerkte Änderungen wurden nicht umgeschrieben.

Diese Dokumentprüfungen ersetzen keine späteren Tests der Anwendung. Insbesondere wurde keine tatsächlich funktionierende Suche, Autorisierung, automatische Aktualisierung oder Camunda-Integration nachgewiesen.

## Bewahrte Entscheidungen und Grenzen

- Kein Bearbeitungsstatusfilter und kein Hinweisfilter. Die Bearbeitungsstatusspalte bleibt sortierbar; fallbezogene Problemhinweise erscheinen nicht in der Liste.
- Betreff als Kurzbeschreibung ohne zusätzliche Zusammenfassung.
- Standardsortierung: unbewertet zuerst, dann Kritisch bis Niedrig; innerhalb gleicher Bewertung ältester letzter Mieterkontakt zuerst.
- Eine eigene Detailseite ist vereinbart; deren Gestaltung und Rücknavigation werden erst in Screen 03 besprochen.
- Keine Änderung akzeptierter Architekturentscheidungen, kein neuer führender Prozess und keine Abschwächung des Memo-Ausschlusses aus Kommunikations-LLM-Eingaben.
- Kein Anwendungscode und keine JPG-Entwürfe in diesem Prüfschritt; gemeinsame JPGs folgen nach allen drei Screen-Spezifikationen samt technischen Wireframes.

Noch zu konkretisieren sind unter anderem der technische Suchvertrag, weitere Sortierwerte und genauer Textvergleich gemäss Screen 02, Lese-/Serviceverträge, technische Bewertungssynchronisierung, BPMN-Übergänge und die technische Umsetzung der inzwischen vereinbarten Abschlussregeln. Die fachlichen Entscheidungen verbleiben bei Marco; die Dokumentbereinigung bestätigt keine neue Nutzerfreigabe für diese offenen Punkte.

## Nachtrag zur Suchentscheidung am 09.10.2026

Marco hat Teilwortsuche und die UND-Verknüpfung mehrerer Suchwörter ausdrücklich bestätigt. Jedes Suchwort darf als Teilwort vorkommen; alle müssen im selben Fall innerhalb des vereinbarten Suchumfangs gefunden werden. Screen 02 und UC-002 wurden entsprechend ergänzt. ÜB-AK-13 enthält Beispiele für Teilwörter, verteilte Fundstellen und fehlende Suchwörter einschliesslich eines ausschliesslich im Entwurf vorhandenen Begriffs. Diese Ergänzung dokumentiert eine bestätigte Anforderung und spätere Prüfkriterien, keinen ausgeführten Anwendungstest.

Ergänzend hat Marco bestätigt, dass die Suche Gross-/Kleinschreibung ignoriert. Screen 02, UC-002 und ÜB-AK-13 berücksichtigen dies für vollständige Wörter, Teilwörter und jedes Suchwort einer UND-verknüpften Suche.

Die vorgeschlagene Auslösung über einen Button hat Marco anschliessend verworfen. Suche und Filter sollen automatisch wirken; für Texteingaben nannte er eine Eingabepause von etwa 1–2 Sekunden. Screen 02 konkretisiert dies auf eine Sekunde nach der letzten Textänderung. Filteränderungen wirken sofort. Wireframes, UC-002 und ÜB-AK-10 wurden angepasst; ein Formularbutton bleibt ausschliesslich als Fallback ohne JavaScript vorgesehen. Die gemeinsame Koordination mit dem 20-Sekunden-Abruf schützt vor überholten Antworten und Eingabeverlust.

## Nachtrag zu Abschlussregeln am 09.10.2026

Marco hat den Fallabschluss durch Mitarbeitende und durch das System bestätigt. Als Systemgründe nannte er die eindeutige Mieterbestätigung «Fehler behoben, keine weitere Hilfe nötig» sowie fehlenden Bearbeitungsbedarf der Verwaltung, beispielsweise einen vom Mieter selbst auszuführenden Glühbirnenwechsel. FALL-07 führt diese Gründe jetzt zentral. «Kein konkreter Fall» wurde als fehlender weiterer Verwaltungsbedarf präzisiert: Der angenommene Fall bleibt gespeichert und wird abgeschlossen. Die konkrete mieterseitige Zuständigkeit richtet sich nach hinterlegten Fachregeln, nicht allein nach einem Stichwort.

Die betroffenen Screens, Zugriffs- und Benachrichtigungsregeln, UC-003, Projektkontext, Vision, README und Evaluationsgrundlage wurden abgeglichen. Frühere pauschale Formulierungen zur stets menschlichen Einzelfallentscheidung wurden an die ausdrücklich erlaubten Systemabschlüsse angepasst; verpflichtende menschliche Prüfungen und Freigaben bleiben bestehen. FALL-AK-12 und FALL-AK-13 erfassen die Abschlusswege und Gegenbeispiele für unklare oder widersprüchliche Einordnungen. Die Architekturentscheidung zu Camunda und PropertyFlow wurde nicht geändert. Diese Ergänzung beschreibt Anforderungen, keine implementierten Abschlussfunktionen oder ausgeführten Anwendungstests.

## Nachtrag zum Spaltenumfang am 09.10.2026

Marco hat die Anzeige von Hinweisen in der Liste ausdrücklich gestrichen. Screen 02 und UC-002 enthalten jetzt sechs sortierbare Spalten: Fall, Objekt/Wohnung, Eingang, Letzte Aktion des Mieters, Dringlichkeit und Bearbeitungsstatus. Die Hinweisspalte und fallbezogene Problemhinweise wurden aus beiden Wireframes entfernt; die Gründe werden auch nicht als Zusatz in andere Spalten verschoben. Bei offener Objektzuordnung bleibt die ursprüngliche Referenz sichtbar. FALL-05 und BEN-04 behalten die interne Speicherung und Nachvollziehbarkeit der Gründe sowie den vereinbarten Prüfstatus bei; ihre Darstellung wird bei Screen 03 konkretisiert. Mieteransicht, README und Prüfkriterien wurden abgeglichen. Allgemeine Lade- und Aktualisierungsfehler bleiben in der Übersicht erkennbar. Alle sechs Spalten haben bei gültigen Fällen einen Anzeigewert; eine gesonderte fachliche Sortierregel für leere Listenzellen entfällt damit.

## Nachtrag zu Sortierung, Sonderzeichen und leerer Liste am 09.10.2026

Marco hat die Sortierung per Spaltentitel mit Richtungspfeil bestätigt: erster Klick aufsteigend ↑, zweiter Klick absteigend ↓. Für «Fall» wird der ursprüngliche Betreff verwendet. Screen 02, Wireframe-Erläuterung, UC-002 und ÜB-AK-03 halten die Bedienung fest. Die vereinbarte Standardsortierung bleibt erhalten; weitere Sortierwerte und der genaue Textvergleich sind gesondert gekennzeichnet.

Sonderzeichen sollen bei der Suche nach Marcos Vorgabe möglichst ignoriert werden. Die Spezifikation konkretisiert dies als gleichen Vergleich von Eingabe und Inhalten ohne Satz- und Sonderzeichen, mit Leerzeichen als Suchworttrennern und unveränderter UND-Verknüpfung. Beispiele sind «REQ2026001» für «REQ-2026-001» sowie «Heiz, kalt!» für «Heiz kalt». ÜB-AK-13 wurde ergänzt; die technische Normalisierung ist noch umzusetzen und nicht als funktionierende Suche nachgewiesen.

Für eine erfolgreich geladene Standardliste ohne aktive Fälle wurde «Heute nichts zu bearbeiten.» übernommen. Such-/Filter-Leerzustände und Abruffehler bleiben davon unterscheidbar. Screen 02, UC-002 und ÜB-AK-05 wurden angepasst. Die übrigen Vorschläge zur schmalen Darstellung, zu verschobenen Zeilen, entfallenen Seiten und Ladefehlern waren zu diesem Zeitpunkt noch nicht bestätigt; ihre anschliessende Bestätigung ist im folgenden Nachtrag dokumentiert.

## Nachtrag zur Darstellung und Bedienung am 09.10.2026

Marco hat die vier Punkte einzeln bestätigt: schmale Darstellung als Fallblöcke mit sechs Angaben und Suche, Filtern sowie Sortierauswahl mit ↑/↓ darüber; Neueinordnung verschobener Zeilen ohne Sprung zum Listenanfang und mit möglichst erhaltener Leseposition; Wechsel auf die letzte gültige Seite mit kurzer Erklärung, wenn die aktuelle Seite entfällt; sowie getrennte Rückmeldungen für Erstabruf- und Aktualisierungsfehler. Beim Erstabruf lautet die Meldung «Die Fallübersicht konnte nicht geladen werden.» mit «Erneut versuchen». Bei einem späteren Fehler bleiben vorhandene Daten mit «Aktualisierung momentan nicht möglich. Stand: …» sichtbar.

Screen 02, UC-002 und ÜB-AK-09/11/12 wurden abgeglichen. Genaue visuelle Gestaltung und technische Wiederholungsabstände bleiben spätere Konkretisierungen. Die Bestätigung betrifft die Mitarbeiterliste; sie bestätigt nicht automatisch weitere Entwurfsdetails der Mieteransicht. Es wurden Dokumente angepasst, keine Anwendungsfunktionen implementiert oder Anwendungstests ausgeführt.

## Abschluss von UI1/UI2 und Start von UI3 am 09.10.2026

Marco hat anschliessend auch die besprochenen Randfälle der Mieteransicht bestätigt: schmale Darstellung, Erhalt der Leseposition und ungesendeten Eingabe bei Aktualisierung, Ladefehler sowie Warnung bei manuellem Verlassen mit ungesendetem Text. UI1 hält den Erstabruftext «Die Fallansicht konnte nicht geladen werden.» mit «Erneut versuchen» fest. AK-19, AK-26 und AK-28 bilden diese Anforderungen ab. Eine Seitennavigation bleibt in UI1 ausgeschlossen.

Die pauschale Aussage, dass jede endgültige fachliche Entscheidung beim Menschen liegen müsse, wurde nach Marcos Präzisierung durch die konkreten Grenzen aus FALL-10 ersetzt: Kosten oder verbindliche externe Beauftragung, sehr dringliche oder schwerwiegende Fälle sowie starke oder eskalierte Mieterbeschwerden. Offener menschlicher Prüfbedarf wird nicht durch einen Systemabschluss umgangen. Beschwerde und Dringlichkeit bleiben getrennt; manuelle Einstufungen bleiben geschützt. FALL-AK-14/15 ergänzen die vorgesehenen Prüfkriterien. Repository-Anweisung, Fachregeln, Screens, Use-Cases, Projektkontext, Vision, README und Evaluationsgrundlage verweisen auf diese Grenzen. Akzeptierte ADRs werden nicht verändert.

UI1 und UI2 sind als fachlich abgeschlossen gekennzeichnet. Verbleibende technische Verträge, Parameter und visuelle Ausgestaltung sowie optionale Erweiterungen werden weiter getrennt geführt; daraus wird kein Implementierungsnachweis abgeleitet.

[UI3 – Mitarbeiter-Falldetailansicht](../frontend/ansicht-03-mitarbeiter-falldetail.md) wurde begonnen. Marco hat die Grundanordnung bestätigt: Verlauf links, Falldaten und Aktionen rechts; Falltitel, Status und Dringlichkeit oben. Die Datei übernimmt die verbindlichen Fachregeln und kennzeichnet weitere Bedienungsdetails, schmale Darstellung und Rücknavigation als noch zu besprechen. Nächster Abstimmungspunkt sind externe Antworten und interne Memos.

## Nachtrag zur Wiedervorlage am 10.10.2026

Marco hat eine optionale Wiedervorlage durch Mitarbeitende in Tagen oder als festes Datum sowie die Umsetzung der Wartezeit als Camunda-Timer vorgeschlagen. Der besprochene Ablauf sieht die erneute Mitarbeiterprüfung am Termin, eine Anpassung bei Terminänderung und das Ende der offenen Wiedervorlage beim Fallabschluss vor. Neue Mieternachrichten sollen ausdrücklich parallel zum Warten verarbeitet werden. Neue Erkenntnisse können eine frühere Wiedervorlage erfordern; eine Nachricht ohne solchen Handlungsbedarf lässt den Termin bestehen.

FALL-11 führt diesen Ablauf zentral. UI3, KOM-03, UC-003 und Projektkontext wurden ergänzt; FALL-AK-17/18 und FD-AK-19 beschreiben die späteren Prüfungen. Tagesberechnung und Uhrzeit, die technische Zuordnung der Warteentscheidung zum geprüften Fallstand und Konkurrenzfälle bleiben zu konkretisieren. Die bestehende Verantwortungsteilung aus ADR-002 bleibt bestehen. Es wurden Spezifikationen ergänzt, keine Timer oder Anwendungsfunktionen implementiert.

Marco hat anschliessend das Wiedervorlagedatum als zusätzliche sortierbare Spalte in UI2 sowie jederzeitiges Ändern und Löschen in UI3 für aktive Fälle verlangt. Löschen führt zu «In Bearbeitung». Den vorgeschlagenen achten Status «Wartet auf Wiedervorlage» hat er ausdrücklich bestätigt: Setzen und Ändern führen in diesen Wartezustand, Fälligkeit zu «Mitarbeiterprüfung erforderlich». UI2 enthält damit sieben Spalten; die früheren Einträge dieses Prüfprotokolls mit sechs Spalten dokumentieren den damaligen Stand. UI1–UI3, UC-002/003, Projektkontext, README und Evaluationsverweise wurden abgeglichen. ÜB-AK-17 beschreibt Anzeige, Sortierung und Aktualisierung des Datums. Fälle ohne Termin werden für die Umsetzung als «–» angezeigt und bei manueller Terminsortierung in beiden Richtungen nach den datierten Fällen eingeordnet.

## Nachtrag zur direkten Mitarbeiterentscheidung am 10.10.2026

Marco hat bestätigt: Nach Prüfung kann ein Mitarbeiter direkt von «Mitarbeiterprüfung erforderlich» auf «In Bearbeitung» wechseln, auch ohne Wiedervorlage. Der bewusst gespeicherte Statuswechsel gilt als Mitarbeiterentscheidung. Bereits berücksichtigte Prüfgründe führen nicht sofort zurück zur Prüfung; neue Erkenntnisse oder neu entstandene Gründe können erneut eine Prüfung auslösen. Eine zusätzliche Aktion «Prüfung erledigt» entfällt weiterhin.

FALL-05/FALL-06, UI3 und UC-003 führen diese Regel; FALL-AK-19 und FD-AK-05 bilden sie in den späteren Prüfkriterien ab. Mitarbeiteridentität, Zeitpunkt und geprüfter Fallstand bleiben nachvollziehbar. Bestehende Hinweise werden nicht als technisch behoben ausgegeben; Folgehandlungen und Systemabschluss behalten ihre eigenen Freigabegrenzen. Reviewpunkt 1 ist damit fachlich geklärt. Die technische Umsetzung und ihre Nachweise folgen separat.

## Nachtrag zur Beantwortung von Mieterfragen am 10.10.2026

Marco hat für Reviewpunkt 2 die inhaltliche Einzelprüfung offener Rückfragen bestätigt: Camunda veranlasst die Prüfung neuer Mieternachrichten. Eindeutig beantwortete Fragen werden erledigt; unvollständige, unklare oder themenfremde Antworten lassen die jeweilige Frage offen. Bei mehreren Rückfragen wird jede einzeln beurteilt. Der blosse Nachrichteneingang genügt nicht zur Erledigung.

KOM-03 führt die Regel zentral; FALL-05, UI1 und UI3 wurden abgeglichen. Der aktuelle Antwortbedarf bleibt von der ursprünglichen veröffentlichten Nachricht unterscheidbar. KOM-AK-09 und die Evaluationsgrundlage erfassen die späteren Prüfungen einschliesslich mehrerer Fragen, Wiederholungen, Auswertungsfehlern und laufender Wiedervorlage. Noch zu klären sind der Folgestatus nach Beantwortung der letzten offenen Frage und der Umgang mit gegenstandslosen oder beim Fallabschluss noch offenen Rückfragen. Reviewpunkt 2 ist damit teilweise geklärt. Es wurden Spezifikationen ergänzt, keine Anwendungsfunktionen implementiert oder Anwendungstests ausgeführt.

## Nachtrag zum Antwortbedarf bei Fallabschluss am 10.10.2026

Marco hat bestätigt, dass beim Fallabschluss noch offener Antwortbedarf endet und Mieter keine weiteren Nachrichten senden können. Er hat ausdrücklich präzisiert, dass «nicht mehr erforderlich» nur eine Beschreibung ist und keinen zusätzlichen Status bezeichnet. Der Fall hat den bestehenden Status «Abgeschlossen». Veröffentlichte Rückfragen bleiben im Verlauf nachvollziehbar; unbeantwortete Fragen gelten dadurch nicht als beantwortet.

KOM-04 und KOM-AK-07 führen diese Abschlussfolge und die bestehende serverseitige Nachrichtensperre. FALL-05/FALL-07, UI1, UI3 und BEN-05 wurden abgeglichen. Ein abgebrochener oder fehlgeschlagener Abschluss beendet den Antwortbedarf nicht. Offen in Reviewpunkt 2 bleiben der Folgestatus nach Beantwortung der letzten Frage und der Umgang mit während eines aktiven Falls gegenstandslosen Rückfragen. Die Änderungen betreffen Spezifikationen und spätere Prüfkriterien; es wurde keine Anwendungsfunktion implementiert.

## Nachtrag zum Folgestatus nach vollständiger Beantwortung am 10.10.2026

Marco hat bestätigt: Nach Beantwortung aller offenen Rückfragen verarbeitet Camunda die Antworten. Erfolgt kein zulässiger automatischer Abschluss und läuft keine Wiedervorlage, erhält der weiterhin aktive Fall anschliessend «Mitarbeiterprüfung erforderlich». Der Mitarbeiter entscheidet über das weitere Vorgehen. Eine laufende Wiedervorlage bleibt nach FALL-11 bestehen; neue Erkenntnisse können eine frühere Prüfung verlangen.

FALL-05 führt die Statusregel zentral; KOM-03, UI1, UI3 und die Evaluationsgrundlage wurden abgeglichen. KOM-AK-09 umfasst nun auch die Statusfortsetzung, Teilantworten, laufende Wiedervorlage, zulässigen Systemabschluss und wiederholte Verarbeitung bereits berücksichtigter Antworten. Offen in Reviewpunkt 2 bleibt der Umgang mit während eines aktiven Falls gegenstandslosen Rückfragen. Die Dokumentprüfung bestätigt die Spezifikation, keine implementierte Statusänderung oder ausgeführten Anwendungstests.

## Nachtrag zum manuellen Aufheben des Antwortbedarfs am 10.10.2026

Marco hat die UI3-Aktion «Antwortbedarf aufheben» an der betreffenden Nachricht bestätigt. Sie beendet deren Antwortbedarf und erhält die veröffentlichte Nachricht. Der Fall bleibt offen, beispielsweise während der Techniker den Fehler noch behebt. Es entsteht kein zusätzlicher Status. Anschliessend hat Marco bestätigt: Entfällt dadurch der letzte offene Antwortbedarf, folgt «In Bearbeitung»; eine laufende Wiedervorlage oder vorrangiger aktueller Mitarbeiterprüfbedarf bleiben wirksam.

KOM-03 beschreibt die Aktion zentral und FALL-05 die Statusfolge. UI1, UI3, FD-AK-15 und die Evaluationsgrundlage wurden abgeglichen. KOM-AK-10 beschreibt die späteren Prüfungen zu gezielter Aufhebung, verbleibendem Antwortbedarf, Statusfolge, Berechtigung, Nachvollziehbarkeit, konkurrierenden Änderungen und Erhalt von Eingabe und KI-Stream. Reviewpunkt 2 ist damit fachlich geklärt. Technische Umsetzung und Anwendungstests stehen weiterhin aus.

## Nachtrag zur KI-Formulierung aus Mitarbeitereingaben am 10.10.2026

Marco hat für Reviewpunkt 3 die Abgrenzung anhand des Technikerbeispiels bestätigt: Das System übernimmt interne Memos niemals automatisch in den KI-Kontext. Ein Mitarbeiter darf bewusst eine kurze Sachanweisung zur externen Formulierung eingeben, auch wenn dieselbe Information bereits in einem Memo steht. Er prüft den Vorschlag vor dem Senden. Im Beispiel übergibt er den Technikertermin und die Bitte um Zugang; ausschliesslich im Memo enthaltene Kostenfreigaben und Offertvorgaben werden nicht nachgeladen.

Diese Bestätigung präzisiert den früheren pauschalen Ausschluss aller Memo-Ableitungen. KOM-01/KOM-02 unterscheiden nun die bewusste Mitarbeitereingabe von systemseitig bereitgestelltem Kontext. Automatische Memo-Abrufe, Zusammenfassungen, Retrieval- und Tool-Zugriffe sowie die Weiterverwendung eines mit Memos befüllten Gesprächskontexts bleiben ausgeschlossen. Eine technisch nicht verlässlich feststellbare Herkunft frei formulierter Sachanweisungen aus dem Wissen des Mitarbeiters wird nicht als Prüfpflicht behauptet. Es entsteht weder eine Memo-Veröffentlichung noch eine Entwurfsspeicherung.

UI1–UI3, UC-004, Projektkontext und Evaluationsgrundlage wurden abgeglichen. KOM-AK-04/05 und FD-AK-18 enthalten die vorgesehenen Nachweise für ausgeschlossene Memo-Quellen und zulässige bewusste Eingaben. Die akzeptierten ADRs enthalten keinen abweichenden Memo-Kontextvertrag; ihre Architekturentscheidungen bleiben unverändert. Reviewpunkt 3 ist fachlich geklärt. Es wurden Spezifikationen geändert, keine Anwendungsfunktionen implementiert oder Anwendungstests ausgeführt.

## Nachtrag zu verspäteten Ergebnissen nach Wiedereröffnung am 10.10.2026

Marco hat für Reviewpunkt 4 bestätigt: Verspätete automatische Ergebnisse aus der Bearbeitung vor dem letzten Abschluss dürfen einen wiedereröffneten Fall nicht verändern. Ein alter Abschlussvorschlag darf ihn beispielsweise nicht wieder schliessen. Die Weiterbearbeitung richtet sich nach dem aktuellen Fallstand; Case-ID und bisheriger Verlauf bleiben erhalten.

FALL-07 führt diese fachliche Grenze zentral; KOM-04, UI3 und UC-003 wurden abgeglichen. FALL-AK-16, KOM-AK-07 und FD-AK-08 beschreiben die späteren Nachweise. Auf Marcos Nachfrage wurde die konkrete LLM-Logik und technische Prozesssteuerung ausdrücklich der späteren Ausarbeitung zugeordnet. Dieser Nachtrag klärt die fachliche Wirkung; er schliesst die technischen Fragen aus Reviewpunkt 4 zur Prozessfortsetzung und Ausfallsynchronisierung nicht ab. Architekturentscheidungen und Anwendungscode wurden dafür nicht geändert.

## Nachtrag zum Abschlussvermerk am 10.10.2026

Marco hat für Reviewpunkt 5 bestätigt: Beim erfolgreichen Mitarbeiterabschluss speichert das System intern «Vom Mitarbeiter geprüft: keine weitere Bearbeitung erforderlich.» zusammen mit Mitarbeiter und Zeitpunkt. Die bewusste Bestätigung liefert die Grundlage; ein zusätzliches Eingabefeld und ein Pflichtmemo entfallen. Eine ausführlichere Erklärung bleibt vor dem Abschluss freiwillig als internes Memo möglich. Bei automatischen Abschlüssen wird weiterhin der tatsächlich geprüfte Systemgrund gespeichert.

FALL-07, UI3 und UC-003 wurden abgeglichen; FALL-AK-12 und FD-AK-17 beschreiben die vorgesehenen Nachweise. Der feste Vermerk gehört zur strukturierten Abschlussdokumentation und erzeugt keine zusätzliche Nachricht oder Memo-Speicherung. Abgebrochene oder fehlgeschlagene Abschlüsse erhalten keinen erfolgreichen Abschlussnachweis. Reviewpunkt 5 ist damit fachlich geklärt. Die Änderungen betreffen die Spezifikation, keine implementierte Abschlussfunktion.

## Nachtrag zur Objekt- und Wohnungszuordnung am 10.10.2026

Marco hat als ersten Teil von Reviewpunkt 6 bestätigt: Rechts bei Objekt/Wohnung in UI3 führt «Zuordnung ändern» zur Auswahl von Objekt und Wohnung und zur bewussten Speicherung. Die ursprüngliche Mieterangabe bleibt nachvollziehbar erhalten; die bestätigte Zuordnung wird separat geführt.

FALL-04, UI3 einschliesslich Wireframe, UI2, UC-003 und Evaluationsgrundlage wurden abgeglichen. FALL-AK-04 und FD-AK-20 erfassen die vorgesehenen Nachweise. Berechtigungs- und Konfliktprüfung, Mitarbeiter und Zeitpunkt sowie Erhalt von Nachrichteneingabe und KI-Stream folgen den bestehenden Bearbeitungsregeln. Eine Zuordnungsänderung ersetzt weder Identitätsprüfung noch Kontakt- oder Zugangsänderung und führt keine Stammdatenpflege ein. Der Bedienungsweg für den persönlichen Fall-Link aus Reviewpunkt 6 bleibt noch zu besprechen. Es wurden Spezifikationen ergänzt, keine Stammdatenintegration oder Anwendungsfunktion implementiert.

## Nachtrag zur dauerhaften Linkgültigkeit am 10.10.2026

Marco hat die vorgeschlagene UI3-Aktion «Fall-Link ersetzen» abgelehnt und festgelegt, dass der persönliche Fall-Link zeitlich nie abläuft. Der bisherige unbestätigte Laufzeitvorschlag entfällt. Es gibt keine Verlängerungs- oder Ersatzaktion in UI3 aufgrund des Alters des Links; auch Inaktivität oder Fallabschluss setzen keine Ablauffrist.

ZUG-05 führt die Entscheidung zentral. Die bisher ausdrücklich offene Laufzeit wurde in ADR-004 fachlich konkretisiert; das direkte Tokenmodell bleibt bestehen. UI1, UI3, Fallverwaltung, Benachrichtigungsregeln, Projektkontext und Evaluationsgrundlage wurden abgeglichen. ZUG-AK-04/06/10/11, UI1-AK-17 und BEN-AK-07 berücksichtigen dauerhafte Gültigkeit statt zeitlichem Ablauf. Bereits festgelegte Regeln zum bewussten Widerruf und zu einem erforderlichen administrativen Sicherheitsersatz bleiben davon getrennt; deren technische Durchführung begründet keine zusätzliche UI3-Aktion. Die UI3-Entscheidungen aus Reviewpunkt 6 sind damit geklärt. Es wurden Dokumente angepasst, keine Zugangs- oder Versandfunktionen implementiert.

## Nachtrag zur Eingabesperre beim Speichern am 10.10.2026

Marco hat für Reviewpunkt 7 festgelegt: Während «Nachricht senden» oder «Memo speichern» läuft, sind die Eingabefelder inaktiv. Die vorgeschlagene Behandlung von zwischenzeitlich geändertem Text entfällt. UI3 sperrt das Textfeld, «Intern», «Mieterantwort erforderlich» sowie KI-Start, Vorschlagsübernahme und Sende-/Speicheraktion bis zum Ende der Speicherung. Nach Erfolg werden Text und Auswahl wie vereinbart zurückgesetzt; bei Fehler bleiben sie erhalten.

UI3 einschliesslich FD-AK-14/16 und UC-004 wurden abgeglichen. Ein laufender KI-Stream wird dadurch nicht beendet; sein Abschluss oder Fehler darf die Eingabe während der Speicherung nicht freigeben oder ersetzen. Ausserhalb der Speicherung bleibt die vereinbarte Bearbeitbarkeit während der Formulierung bestehen. Reviewpunkt 7 ist damit fachlich geklärt. Es wurden Spezifikationen geändert, keine Eingabesicherung implementiert oder Anwendungstests ausgeführt.

## Nachtrag zur Erkennung einer Wiedereröffnung in UI1 am 10.10.2026

Marco hat für Reviewpunkt 8 bestätigt: Die sichtbare Mieteransicht aktualisiert sich auch bei abgeschlossenen Fällen alle 20 Sekunden. Nach einer durch einen Mitarbeiter bestätigten Wiedereröffnung erscheinen der aktuelle aktive Status und die Nachrichteneingabe automatisch wieder. Die bisherige Regel, Leseabrufe nach Fallabschluss zu beenden, entfällt.

UI1 beschreibt die Regel für direkt geöffnete abgeschlossene Fälle und für den Abschluss während der Nutzung. Pausen bei ausgeblendetem Tab, erneute Aktualisierung bei Rückkehr, Fehlerbehandlung, Zugangsprüfung und manuelle Aktualisierung ohne JavaScript gelten weiterhin. AK-11/25/26 prüfen fortgesetzte Leseabrufe, die unveränderte Schreibsperre bis zur Wiedereröffnung und den anschliessenden Wechsel zur aktiven Ansicht. FALL-07 verweist auf diesen Ablauf; ADR-003 und ADR-004 bleiben damit vereinbar und benötigen keine Architekturänderung. Reviewpunkt 8 ist fachlich geklärt. Es wurden Spezifikationen angepasst, keine Polling-Funktion implementiert oder Anwendungstests ausgeführt.

## Korrektur zur Mitarbeiteranmeldung am 10.10.2026

Für Reviewpunkt 9 hatte der Assistent einen Ablauf bei abgelaufener Mitarbeiteranmeldung vorgeschlagen. Die Annahme stammte aus der bisherigen Pflicht zur separaten Anmeldung in ZUG-04 und den dazu passenden Anmelde-, Abmelde- und Ablaufregeln in UI2. Marco hat klargestellt, dass es keine Mitarbeiteranmeldung gibt. Der vorgeschlagene Anmeldeablauf entfällt einschliesslich der Wiederherstellung einer Eingabe nach erneuter Anmeldung.

ZUG-04 führt die korrigierte Vorgabe: keine Mitarbeiteranmeldung, keine Mitarbeiterkonten und keine Abmeldefunktion. UI1 bis UI3, UC-002 bis UC-004, Fallverwaltung, Fallkommunikation, Dringlichkeitsbewertung und Projektkontext wurden von der entgegenstehenden Voraussetzung bereinigt. Der Abmelden-Button wurde auch aus dem UI2-Wireframe entfernt; ZUG-AK-14, ÜB-AK-11/14 und FD-AK-02 wurden abgeglichen. Normale Ladefehler behalten das vereinbarte Verhalten mit Erhalt vorhandener Daten und Eingaben. Die fachliche Trennung der Mieter- und Mitarbeiterfunktionen sowie serverseitige Schreibprüfungen bleiben bestehen. Ihre technische Absicherung und die Herkunft einer individuellen Mitarbeiteridentität für Auditnachweise sind in ZUG-04 ausdrücklich offen; sie werden nicht durch eine erfundene Anmeldung ersetzt. Die ADRs fordern keine Mitarbeiter-Loginfunktion; technische Authentifizierung gegenüber Camunda bleibt davon getrennt. Es wurden ausschliesslich Spezifikationen korrigiert, keine Zugriffskontrollen im Anwendungscode entfernt.

## Abschluss der gemeinsamen Prüfrunde am 10.10.2026

Der verbleibende Reviewpunkt 10 wurde anhand der bestätigten Regeln bereinigt: README, Projektkontext, UI1/UI2 und UC-002/003 verweisen auf die inzwischen spezifizierte UI3-Bedienung statt auf noch ausstehende Grundentscheidungen. ADR-003 verwendet konsistent KI-Formulierung für die interaktive Funktion; seine SSR-Architekturentscheidung bleibt bestehen. Die C4-Sichten zeigen serverseitiges Thymeleaf-Rendering, gezielte Browserinteraktivität, ausgehende Fallbenachrichtigungen und die Camunda-Anbindung einschliesslich Wiedervorlage. Die E-Mail-Annahme aus der Vision bleibt gemäss UC-001 als noch auszuarbeitender Eingangskanal erkennbar und wird nicht mit der bestätigten Web-Kommunikation gleichgesetzt.

| Reviewpunkt | Stand |
|---|---|
| 1 – Mitarbeiterentscheidung und Wiedervorlage | Fachliche Regeln, zusätzlicher Status und Bedienung in UI2/UI3 festgehalten. |
| 2 – Lebenszyklus des Mieterantwortbedarfs | Beantwortung, manuelle Aufhebung, Abschluss und Folgestatus festgehalten. |
| 3 – Memos und KI-Kommunikation | Bewusste Mitarbeitereingabe und Ausschluss systemseitiger Memo-Übernahme abgegrenzt. |
| 4 – Prozessfortsetzung und verspätete Ergebnisse | Fachliche Wirkung nach Wiedereröffnung geklärt; technische Prozessfortsetzung und Ausfallsynchronisierung ausdrücklich zurückgestellt. |
| 5 – Abschlussgrund | Fester Mitarbeitervermerk und tatsächlicher Systemgrund unterschieden. |
| 6 – Zuordnung und Fall-Link | UI3-Zuordnung beschrieben; Link ohne Zeitablauf und ohne zusätzliche UI3-Ersatzaktion. |
| 7 – Eingaben während Speicherung | Eingabesperre mit unverändertem Erfolgs- und Fehlerverhalten festgelegt. |
| 8 – Wiedereröffnung in UI1 | Leseabrufe auch nach Abschluss; Nachrichteneingabe nach erkannter Wiedereröffnung wieder sichtbar. |
| 9 – Mitarbeiteranmeldung | Falsche Voraussetzung korrigiert; Anmelde- und Sitzungsablauf entfallen. |
| 10 – Übergreifende Dokumentation | README, C4-Sichten, Projektkontext, Use-Cases und ADR-Begriffe abgeglichen. |

Die Prüfrunde der vereinbarten fachlichen UI-Regeln ist damit abgeschlossen. Weiterhin offen bleiben die bereits gekennzeichneten Prozess-, Service- und Speicherverträge, konkrete LLM-Logik, technische Zugangsabsicherung und individuelle Mitarbeiterzuordnung, Parameter wie Wiedervorlage-Uhrzeit sowie visuelle und kleinere Bedienungsdetails. Diese Punkte sind keine abgeschlossenen Implementierungen oder Nachweise. Es wurden keine neuen Fachfunktionen beschlossen und kein Anwendungscode implementiert.

Die abschliessende Dokumentprüfung umfasst 25 Markdown-Dateien mit 295 lokalen Verweisen einschliesslich Abschnittsankern; alle Ziele wurden gefunden und Codeblöcke sind geschlossen. Die Prüfkriterien-IDs der zentralen Regeln und UI-Spezifikationen wurden auf vollständige Folgen geprüft. Die Leerzeichenprüfung der Änderungen ist erfolgreich. Dies ersetzt keine Anwendungs-, Sicherheits-, A11y- oder Performance-Tests; diese folgen mit der Umsetzung.

## Ergänzung vom 12.10.2026: Generierte Unit-Tests und Mutation

Die früheren Aussagen zum reinen Spezifikationsstand beziehen sich auf die
Prüfrunde vom 10.10.2026. Inzwischen existiert eine lokale Frontend-Demo.
Der folgende Nachweis betrifft ausschliesslich diese Demo mit Testdaten,
nicht Datenbank-, Camunda- oder LLM-Integrationen.

### Auftrag und Kontext

Codex erhielt AGENTS.md, die Copilot-Instruktionen, Projektkontext, Vision,
C4-Sichten, Modulstruktur und ADR-001 bis ADR-004 sowie die relevanten Regeln
aus FALL-06, KOM-01/KOM-02 und UI3/FD-AK-21. Der begrenzte Prüfauftrag war
`DemoCases.change`: manuelle Statusänderungen mit optionaler interner
Begründung, ungültige Übergänge, Versionskonflikte und wiederholte Aufrufe.
Die Geschäftslogik sollte nicht vorsorglich verändert werden.

Die neue Klasse [DemoCasesChangeTest](../../src/test/java/com/pertegato/propertyflow/caseprocessing/demo/DemoCasesChangeTest.java)
enthielt zunächst sechs Testmethoden mit insgesamt zehn Testausführungen;
nach dem unten dokumentierten Veto verbleiben fünf Methoden mit neun
Testausführungen. Sie verwendet
pro Test eine echte DemoCases-Instanz mit lokalen Beispieldaten, keine
gemockten Methoden und keinen Spring-Kontext. Die Prüfungen umfassen den
Änderungsnachweis ohne separates Memo, die Trennung der internen Begründung
von Mieterdaten, unveränderte Fallstände bei Ablehnung und Konflikt sowie
Wiederholungen ohne Mehrfachwirkung oder Rücknahme späterer Entscheidungen.

### Ausgeführter Mutationsnachweis

Bewusst wurde vorübergehend die Entfernung interner Begründungen aus der
Mietersicht ausgeschaltet. Der gezielte Fehler lag in der Ausgabeprojektion,
über die der Test die Sichtbarkeit einer durch `change` gespeicherten
Begründung prüft:

```diff
- .map(e -> tenant ? new Entry(e.id(), e.role(), e.text(), e.at(), false, e.event(), e.answerRequired(), "") : e).toList());
+ .map(e -> tenant ? new Entry(e.id(), e.role(), e.text(), e.at(), false, e.event(), e.answerRequired(), e.reason()) : e).toList());
```

| Lauf | Befehl | Ergebnis |
|---|---|---|
| Original | `.\mvnw.cmd -q -Dtest=DemoCasesChangeTest test` | 10 Tests, 0 Fehler |
| Mutation | gleicher Befehl | 10 Tests, 1 Assertion-Fehlschlag, Prozess-Exitcode 1 |
| Wiederhergestellt | `.\mvnw.cmd -q '-Dtest=DemoCasesTest,DemoCasesChangeTest' test` | 16 Tests, 0 Fehler, 0 übersprungen |
| Vorhandenes Streaming-Modul | `node --test src/test/js/mock-stream.test.mjs` | 4 Tests erfolgreich |

Die Mutation wurde durch
`manualStatusChangeStoresOneEventWithOptionalReason(String)[2]` erkannt:
`expected: <> but was: <Intern: weiteres Vorgehen geprüft>`.
Die Assertion befindet sich zum Prüfzeitpunkt in Zeile 44.
Der Produktionsquelltext wurde im Finally-Block bytegenau wiederhergestellt;
der anschliessende Git-Diff für DemoCases.java war leer. Lokale Rohprotokolle
liegen unter `target/change-mutation-evidence/` und können durch einen Clean-Build
entfernt werden. Obige Resultate halten den ausgeführten Nachweis dauerhaft fest.

Dies ist ein erfolgreicher Mutationsnachweis, kein Veto gegen einen Test.
Die Streaming-Tests prüfen den lokalen Generator und seine Abbruch-/Fehlerpfade;
sie sind kein Browsernachweis für sichere DOM-Ausgabe oder Barrierefreiheit.

### Veto-Notiz: zusätzliche Anforderung aus KI-Test verworfen

**Entscheidung von Marco:** «Test entfernen: keine zusätzliche Anforderung
festschreiben». Die Entscheidung erfolgte nach Vorstellung des konkreten
KI-Vorschlags und seiner fachlichen Begründung; Codex setzte sie anschliessend um.

Der generierte Test `rejectedAttemptDoesNotConsumeOperationId` verlangte,
dass nach einer abgelehnten Statusänderung eine korrigierte Anfrage mit
derselben Vorgangskennung erfolgreich sein muss. Der Test bestand gegen
die aktuelle Demo-Implementierung. Dies allein macht seine Erwartung aber
nicht zu einer verbindlichen fachlichen Anforderung: FALL-06 verlangt,
dass die Wiederholung derselben erfolgreichen Aktion keinen doppelten
Änderungsnachweis erzeugt. Die Wiederverwendung einer Kennung mit einem
geänderten Inhalt nach Ablehnung ist ein anderer, nicht ausdrücklich
festgelegter technischer Vertrag.

Deshalb wurde die vollständige Testmethode entfernt. Andernfalls hätte
der KI-Vorschlag zufällig vorhandenes Implementierungsverhalten als
Anforderung festgeschrieben und spätere zulässige Änderungen am Umgang
mit Vorgangskennungen unnötig eingeschränkt. Es wurde weder beschlossen,
diese Wiederverwendung künftig zu verbieten, noch die Geschäftslogik
verändert. Die Entscheidung begrenzt ausschliesslich den Testvertrag.

Die wichtigen fachlichen Prüfungen bleiben bestehen: Ungültige Änderungen
lassen den Fall einschliesslich Verlauf und Version unverändert;
Wiederholungen erfolgreicher Änderungen erzeugen keine Mehrfachwirkung
und überschreiben auch keine spätere Mitarbeiterentscheidung. Die frühere
Rückfrage zur Eignung statischer Demo-Daten führte dagegen zu keinem Veto,
weil der Ausbildungsauftrag diese ausdrücklich zulässt.

Der folgende gekürzte Diff zeigt den entfernten Kern der Testmethode.
Im Nachher-Stand entfällt die gesamte Methode ersatzlos; der separate
Test für identische erfolgreiche Wiederholungen bleibt unverändert:

```diff
- void rejectedAttemptDoesNotConsumeOperationId() {
-     // Nach Ablehnung von "Unbekannt" mit "corrected-attempt":
-     cases.change(ID, "status", "In Bearbeitung", "Korrigiert", before.version(), "corrected-attempt");
-     var after = cases.get(ID);
-     assertEquals("In Bearbeitung", after.status());
-     assertEquals(before.version() + 1, after.version());
-     assertEquals(before.entries().size() + 1, after.entries().size());
-     assertEquals("Korrigiert", after.entries().getLast().reason());
- }
```

Die gezielte Fehlereinspeisung oben ist davon getrennt: Sie belegt die
Wirksamkeit eines beibehaltenen Tests und wird nicht als menschliches Veto
ausgegeben. Die dortigen Zahlen dokumentieren den Lauf vor der Entfernung.

**Kontrolllauf nach Umsetzung des Vetos:**
`.\mvnw.cmd -q '-Dtest=DemoCasesTest,DemoCasesChangeTest' test`
endete mit Exitcode 0: neun neue und sechs bestehende Testausführungen,
insgesamt **15 erfolgreich, keine Fehler, keine übersprungenen Tests**.
`git diff --check` meldete keine Whitespace-Fehler; DemoCases.java blieb
unverändert. Damit sind die generierten Unit-Tests, die tatsächliche
Veto-Entscheidung mit Diff und der Mutationsnachweis dokumentiert.

### Abgleich mit dem vollständigen Block-2-Auftrag

Der folgende Abgleich entstand vor der anschliessenden Qualitätsprüfung;
die danach erledigten Punkte sind im nächsten Abschnitt mit Ergebnissen belegt.

- Drei Screen-Spezifikationen und visuelle Entwürfe sind unter `docs/frontend/`
  vorhanden; README beschreibt den Start der lokalen Demo und den lokalen LLM-Mock.
- Mitarbeiterliste und Falldetail bilden die Master-Detail-Struktur.
  Ein erneuter interaktiver Browser-Abnahmetest wurde in dieser Prüfrunde nicht ausgeführt.
- ADR-003 entscheidet SSR mit gezieltem JavaScript und nennt Qualitätsziele
  und Alternativen. Ein ausdrücklicher Bezug auf ein Gesetz der Softwarearchitektur
  und eine konkrete Begründung der Framework-Wahl fehlen dort noch.
- Messbare A11y- und Performance-Kriterien samt ausgeführten Messungen fehlen
  als Abgabenachweis weiterhin. Die Screen-Dokumente führen sie als offen.
  Der hier geprüfte Ausschluss interner Begründungen ersetzt auch keinen
  Browser-Sicherheitsnachweis für die Streaming-Ausgabe.

## Ergänzung: Browser-Qualitätsnachweis und Präsentationsentscheidung

### Drei Hauptbildschirme und Rendering

Die verlinkten Spezifikationen enthalten die Wireframes; zusätzlich liegen
Desktop-, Tablet- und Mobilentwürfe im Verzeichnis `docs/frontend/design/`.

| Bildschirm | Render-Strategie und Begründung |
|---|---|
| [UI1: Mieteransicht einschliesslich Erfassung](../frontend/ansicht-01-mieter-fallansicht.md) | SSR mit Thymeleaf liefert Formular und gespeicherten Verlauf unmittelbar als HTML; gezieltes JavaScript ergänzt Sendezustand und Aktualisierungen. |
| [UI2: Mitarbeiterliste](../frontend/ansicht-02-mitarbeiter-falluebersicht.md) | SSR mit gezieltem JavaScript hält Suche, Filter und Seitennavigation serverseitig konsistent und aktualisiert die Liste ohne vollständigen Seitenwechsel. |
| [UI3: Mitarbeiterdetail](../frontend/ansicht-03-mitarbeiter-falldetail.md) | SSR liefert Falldaten und Verlauf; Vanilla JavaScript ergänzt den lokalen LLM-Stream mit Status, Abbruch, Fehlerpfad und sicherer Textausgabe. |

UI2 und UI3 bilden die Master-Detail-Struktur. Die lokale Demo arbeitet mit
56 fiktiven Fällen im Arbeitsspeicher ohne Datenbank oder echte LLM-Verbindung.
Die Framework-Begründung, der Trade-off nach Richards/Ford und die Einordnung
in ADR-001 bis ADR-004 sind in [ADR-003](../architecture/adr/ADR-003-praesentationsschicht.md)
ergänzt. Es wurde keine neue Architekturentscheidung eingeführt.

### Vor der Messung festgelegte Kriterien

| Bereich | Messbares Kriterium | Methode |
|---|---|---|
| Barrierefreiheit | 0 serious/critical Verstösse in den automatisiert prüfbaren WCAG-2.0/2.1-A/AA-Regeln auf den vier Hauptansichten sowie UI3 mit sichtbarem Stream; Eingabe, Stream-Start und Abbruch per Tab/Enter erreichbar | axe-core im echten Browser und automatisierte Tastatureingaben mit Playwright |
| Frontend-Sicherheit | HTML-Testausgabe erzeugt 0 Kindelemente im Ausgabefeld und 0 Alert-Dialoge; `<img …>` und `<script>…` bleiben lesbarer Text | Lokalen Mock-Modus `unsafe` im UI ausführen und DOM/Text sowie Dialogereignisse prüfen |
| Performance | Median des Lighthouse-Performancewerts aus drei Läufen je Ansicht mindestens 90 | Lokales Desktop-Profil ohne künstliche CPU-/Netzdrosselung, 1440 × 1000, warmer Server, je Lauf zurückgesetzter Browser-Speicher |

Die Kriterien sind Nachweisgrenzen für die Ausbildungs-Demo, keine neu
beschlossenen produktiven SLA oder vollständige WCAG-Konformitätsaussage.

### Browser- und Streaming-Ergebnis

Geprüft mit headless Microsoft Edge 155.0.4283.45, Playwright 1.64.0 und
axe-core/Playwright 4.13.0 gegen eine eigene `frontend-demo`-Instanz auf
`http://127.0.0.1:8082`, Windows, Java 27. Die bereits laufende Anwendung auf
8081 wurde nicht beendet oder verändert. Ergebnis des Browserlaufs vom
12.10.2026, 23:39 Uhr Europe/Zurich:

- Erfassung, aktive Mieteransicht, Mitarbeiterliste, Mitarbeiterdetail und
  Mitarbeiterdetail mit abgeschlossenem Stream: jeweils **0 axe-Verstösse**
  der gewählten Regeln, auch keine serious/critical-Verstösse.
- Nachrichteneingabe, Start und Abbruch wurden über tatsächliche
  Tab-Navigation und Enter erreicht. Nach Abbruch blieb die Ausgabe über
  weitere 1000 ms unverändert und Übernahme war gesperrt.
- Im simulierten Fehlerzustand war die unvollständige Ausgabe nicht
  übernehmbar; ein anschliessender neuer Versuch wurde erfolgreich beendet.
- HTML-Testausgabe blieb Text: **0 DOM-Kindelemente, 0 Alert-Dialoge**;
  **0 unbehandelte JavaScript-Seitenfehler**. Der Stream-Status hat eine
  `aria-live="polite"`-Region. Es wurde keine Nachricht veröffentlicht.

**Grenzen:** axe meldet zusätzlich manuell zu prüfende Ergebnisse
(`incomplete`) für Farbkontrast und in Detailansichten für
`aria-prohibited-attr`. Diese gelten nicht als automatisch bestätigte
Verstösse, sind aber auch nicht als vollständig geprüft auszugeben.
Die gezielte Tastaturprüfung ersetzt keinen vollständigen Screenreader-Test.
Der HTML-Test deckt den lokalen Streaming-Ausgabepfad ab, nicht jeden
möglichen XSS-Pfad der Anwendung oder externe LLM-Integrationen.

Die ausführbaren Prüfungen liegen in
[frontend-quality.mjs](../../src/test/browser/frontend-quality.mjs) und
[frontend-performance.mjs](../../src/test/browser/frontend-performance.mjs).
Sie ändern keine fachlichen Falldaten. Rohberichte und Screenshot werden
unter `target/frontend-quality/` erzeugt.

Für die Abgabe sind der [Browserbericht](../evaluation/evidence/block2/browser.json)
und ein [Screenshot der sicheren Textausgabe](../evaluation/evidence/block2/stream-safe-text.png)
dauerhaft abgelegt. Werkzeugversionen und transitive Abhängigkeiten stehen in
[tools-package.json](../../src/test/browser/tools-package.json) und [tools-package-lock.json](../../src/test/browser/tools-package-lock.json).

### Reproduktion (PowerShell ab Repository-Wurzel)

Voraussetzungen: Projekt-JDK, Node.js mit npm, installierter Microsoft Edge.
Die Werkzeuge sind reine Testabhängigkeiten unter `target/`, keine neuen
Frontend-Laufzeitabhängigkeiten. Installation und Browserprozesse benötigen
Netzwerk-/Prozessrechte der lokalen Entwicklungsumgebung.

```powershell
New-Item -ItemType Directory -Force target/frontend-tools | Out-Null
Copy-Item src/test/browser/tools-package.json target/frontend-tools/package.json
Copy-Item src/test/browser/tools-package-lock.json target/frontend-tools/package-lock.json
npm ci --prefix target/frontend-tools --no-audit --no-fund

# In einem eigenen Terminal starten:
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=frontend-demo' '-Dspring-boot.run.arguments=--server.port=8082'

# Nach erfolgreichem Start in einem zweiten Terminal:
node src/test/browser/frontend-quality.mjs
node src/test/browser/frontend-performance.mjs
```

Bei vertrauenswürdigem Unternehmenszertifikat war hier zusätzlich
`$env:NODE_OPTIONS='--use-system-ca'` für npm erforderlich. TLS-Prüfungen
wurden nicht deaktiviert. Beide Skripte beenden sich bei verletztem Kriterium
mit Exitcode 1 und schreiben ihre Berichte vor dem Schliessen des Browsers.
`DEMO_URL` kann eine andere lokale Demo-Adresse vorgeben; für Lighthouse
kann `CHROME_PATH` den Browserpfad überschreiben. Die Skripte sind nur für
die eigene lokale Demo vorgesehen.

### Vollständige Java-Testsuite

Zusätzlich zu den 15 Unit-Testausführungen wurde die vollständige Suite
einschliesslich drei HTTP-/Rendering-Tests und des Anwendungskontexttests
ausgeführt: **19 Tests, 0 Fehler, 0 übersprungen**, Exitcode 0.
Die Demo-Rendering-Tests verwenden keine Datenbank; der separate bestehende
Anwendungskontexttest verwendet das Testprofil mit einer H2-In-Memory-Datenbank.

Der erste Sandbox-Lauf blieb beim dynamischen Anhängen des Mockito-Agenten
stehen und wurde beendet. Erfolgreich war der erneute Lauf mit explizitem
Agenten und lokalen Socket-Rechten (kein Build-/Produktionscode geändert):

```powershell
.\mvnw.cmd -q test '-DargLine=-Djdk.net.unixdomain.tmpdir=C:\Dev\propertyflow\target -javaagent:C:\Users\MarcoPertegato\.m2\repository\org\mockito\mockito-core\5.23.0\mockito-core-5.23.0.jar'
```

Die absoluten Pfade sind protokollierte Werte dieser Entwicklungsumgebung
und müssen auf anderen Rechnern angepasst werden.

### Performance-Ergebnis

Lighthouse 13.5.0, drei Läufe je Route am 12.10.2026 unter dem oben
festgelegten lokalen Desktop-Profil; Prozess-Exitcode 0.
Die Werte sind im [Messbericht](../evaluation/evidence/block2/performance.json)
mit Einzelmessungen erhalten. Ausführliche Lighthouse-Rohberichte liegen
lokal unter `target/frontend-quality/lighthouse-*.json`.

| Ansicht | Performancewerte der drei Läufe | Median | Median LCP |
|---|---|---|---|
| Erfassung | 100 / 100 / 100 | 100 | 73,3 ms |
| Aktive Mieteransicht | 100 / 100 / 100 | 100 | 74,2 ms |
| Mitarbeiterliste | 100 / 100 / 100 | 100 | 79,7 ms |
| Mitarbeiterdetail | 100 / 100 / 100 | 100 | 101,6 ms |

Das Ziel **Median ≥ 90** wurde auf allen vier Ansichten erreicht.
Lighthouse meldete in allen zwölf Läufen CLS 0 und Total Blocking Time 0 ms.
Der erste Erfassungslauf lud 36.098 Byte, weitere Erfassungsläufe 31.700 Byte;
die gemessenen Gesamtgrössen der übrigen Ansichten lagen zwischen 33.486
und 64.766 Byte. Messwerte können zwischen Rechnern und Läufen variieren.

**Einordnung:** Lokaler Loopback, statische Daten, warmer Server und keine
künstliche CPU-/Netzdrosselung begünstigen diese Werte. Die Java-Testsuite
lief während eines Teils der Messung parallel. Es handelt sich weder um
einen kontrollierten Hardware-Benchmark noch um einen Mobilfunk-, Last-
oder Produktivnachweis. Die dokumentierten Ergebnisse gelten ausschliesslich
für dieses Desktop-Demo-Profil; es wird kein allgemeines Ladezeitversprechen
abgeleitet.

## Dokumentprüfung vom 13.10.2026

Alle 28 Markdown-Dokumente des Repositorys (ohne Build-, IDE- und Abhängigkeitsverzeichnisse) wurden auf lokale Markdown-Datei-/Bildlinks und Abschnittsanker geprüft. Ergebnis nach Korrektur: 412 lokale Verweise, keine fehlenden Ziele oder Abschnittsanker; jedes Markdown-Dokument besitzt mindestens einen eingehenden Verweis. Nicht klickbare Dokumentpfade wurden dort in Links umgewandelt, wo das Ziel eindeutig ist. README, Projektkontext, Screens und Evaluationsgrundlage verweisen nun auf die [Unit-Test-/Veto-Nachweise](#ergänzung-vom-12102026-generierte-unit-tests-und-mutation) und die [Browser-/Performance-Nachweise](#ergänzung-browser-qualitätsnachweis-und-präsentationsentscheidung). Die [UI3-Ergänzungen](../frontend/ansicht-03-mitarbeiter-falldetail.md#9-ergänzungen-zur-darstellung-vom-12102026) sind über eigene Abschnittsanker erreichbar. Veraltete aktuelle Standangaben wurden korrigiert; datierte historische Befunde bleiben erhalten. Externe Webseiten wurden nicht auf Verfügbarkeit geprüft; diese Dokumentprüfung ist kein neuer Anwendungstest oder vollständiger fachlicher Konformitätsnachweis.
