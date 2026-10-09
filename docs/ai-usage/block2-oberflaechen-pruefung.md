# KI-Nutzung und Review des Block-2-UI

## Zweck und Stand

Dieses Dokument hält die KI-Unterstützung bei der Spezifikation der PropertyFlow-Oberflächen und deren Dokumentprüfung fest. Die fachlichen Screens sind noch nicht implementiert. Es werden keine ausgeführten Anwendungs-, Sicherheits-, A11y- oder Performance-Tests behauptet.

**Stand:** 09.10.2026

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
