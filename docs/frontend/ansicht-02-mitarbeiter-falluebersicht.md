# Screen 02 – Mitarbeiter-Fallübersicht

**Projekt:** PropertyFlow – FFHS CAS AISE  
**Status:** Fachliche Spezifikation bestätigt; am 10.10.2026 um die Wiedervorlage ergänzt. Technische Umsetzung und visuelle Ausgestaltung folgen.

**Stand:** 10.10.2026\
**Zielgruppe:** Berechtigte Mitarbeitende der Immobilienbewirtschaftung  
**Darstellung:** SSR mit Thymeleaf; gezieltes Vanilla JavaScript nur bei begründetem Bedarf

## 1. Bestandsaufnahme und Verbindlichkeit

Diese Spezifikation beschreibt die eigenständige Mitarbeiter-Fallübersicht. Zusammen mit der [Mieter-Fallansicht](ansicht-01-mieter-fallansicht.md) und der [Mitarbeiter-Falldetailansicht](ansicht-03-mitarbeiter-falldetail.md) bildet sie die vereinbarten drei Screens. Unter `src` liegt bisher das Anwendungsgrundgerüst mit Hello-World-Funktion und Tests; die fachlichen Screens sind noch nicht implementiert.

**Bestehende Grundlage:** [UC-002](../use_cases/UC-002-mieteranliegen-anzeigen.md) verlangt mindestens Referenz, Kurzbeschreibung, Dringlichkeit und Bearbeitungsstatus, verständliche Leer-/Fehlerzustände und die Auswahl eines Falls für [UC-003](../use_cases/UC-003-mieteranliegen-details-anzeigen.md). Dringlichkeit darf nicht allein durch Farbe erkennbar sein. Die [Vision](../vision.md) und [ADR-001](../architecture/adr/ADR-001-grundarchitektur.md) begründen schnelle fachliche Orientierung und manuelle Bearbeitbarkeit bei KI-Ausfall.

**Verbindlichkeit:** Die sieben Spalten, Standardsortierung, Sortierfunktion aller Spalten, Volltextsuche samt Inhaltsgrenzen, Dringlichkeitsfilter, Umschaltbutton für abgeschlossene Fälle, Aktualisierung alle 20 Sekunden und maximal 50 Fälle je Seite sind vereinbart. Nicht entschiedene technische Details und Layoutvarianten sind ausdrücklich als Vorschlag oder offene Konkretisierung bezeichnet. Prüfkriterien beschreiben den Zielstand, keine vorhandene Implementierung oder bereits ausgeführten Tests.

| Quelle | Verbindliche Verantwortung |
|---|---|
| [Fallverwaltung](../specifications/fallverwaltung.md) | Fallidentität, Zuordnung, Lebenszyklus und fachlicher Status; insbesondere FALL-01, FALL-04 bis FALL-11 |
| [Dringlichkeitsbewertung](../specifications/dringlichkeitsbewertung.md) | Stufen, Farben, automatische Bewertung, Vorrang manueller Einstufungen und transparente Historie; DRING-01 bis DRING-07 |
| [Fallkommunikation](../specifications/fallkommunikation.md) | Sichtbarkeit, Veröffentlichung, LLM-Datengrundlage und Nachrichtensperre; KOM-01 bis KOM-04 |
| [Fallzugriff und Sicherheit](../specifications/fallzugriff-und-sicherheit.md) | Getrennter Mieter-/Backoffice-Zugang; insbesondere ZUG-04 und ZUG-06 |
| [Benachrichtigungen und Zustellung](../specifications/benachrichtigungen-und-zustellung.md) | E-Mail-Auslöser und unabhängige Versandzustände; BEN-01 bis BEN-05 |
| [Projekt-Kontext](../project-context.md), [C4-Kontext](../architecture/c4-context.md), [C4-Container](../architecture/c4-container.md), [Modulstruktur](../architecture/module-structure.md) | Systemgrenzen und Integrationsverträge |
| [ADR-001](../architecture/adr/ADR-001-grundarchitektur.md), [ADR-002](../architecture/adr/ADR-002-camunda8-workflow-orchestrierung.md), [ADR-003](../architecture/adr/ADR-003-praesentationsschicht.md), [ADR-004](../architecture/adr/ADR-004-tokenbasierter-mieterzugriff.md) | Akzeptierte Architekturentscheidungen |

Gemeinsame Regeln werden an diesen Quellen gepflegt. Die Zuordnung einer Funktion zur Übersicht oder zum Detail ist eine UI-/Use-Case-Entscheidung und erfordert für sich keine ADR-Änderung.

## 2. Zweck und Umfang

Die Übersicht beantwortet: **Welche Fälle liegen vor, wie dringend sind sie und welchen Fall möchte ich als Nächstes prüfen?** Alle Mitarbeitenden sehen dieselben Fälle gemäss ZUG-04.

**Vereinbart am 09.10.2026:** Die Auswahl öffnet die Mitarbeiter-Falldetailansicht als eigene Seite. Damit werden drei eigenständige Screens spezifiziert: Mieter-Fallansicht, Mitarbeiter-Fallübersicht und Mitarbeiter-Falldetailansicht. Ein gleichzeitig in die Übersicht eingeblendetes Detailpanel ist nicht vorgesehen. Rücknavigation und weitere Detailbedienung werden erst bei Screen 03 besprochen.

Die Übersicht dient dem Lesen, Suchen, Filtern und Öffnen. Fachliche Bearbeitung, interne Memos, externe Nachrichten und Veröffentlichung, Prüfung von Empfehlungen sowie KI-Formulierungshilfe werden in der Falldetail-Spezifikation konkretisiert. Gemäss der Präzisierung vom 10.10.2026 laufen Fallanalyse und Dringlichkeitsbewertung automatisch im Camunda-Prozess; UC-004 beschreibt die interaktive Formulierung einer externen Nachricht aus einer kurzen Mitarbeitereingabe. Ungesendete externe Vorschläge werden nicht persistiert. Streaming und Abbruch bleiben Mitarbeiterfunktionen gemäss [UC-004](../use_cases/UC-004-ki-analyse-durchfuehren.md); dieser Entwurf ergänzt keinen Analysebutton in der Liste.

Massenbearbeitung, Fallabschluss aus einer Tabellenzeile, Zuweisung an einzelne Mitarbeitende, Export, Löschen und Wiedereröffnung sind für diesen Screen nicht vorgeschlagen. Ein Verantwortlichen-/«Meine Fälle»-Modell ist bisher nicht definiert und wird nicht durch einen Filter eingeführt.

## 3. Inhalte und Spalten

| Spalte | Inhalt und Verhalten | Herleitung |
|---|---|---|
| **Fall** | Case-ID als eindeutiger Link ins Detail; darunter der ursprüngliche Betreff als Kurzbeschreibung. Lange Betreffe umbrechen; keine zusätzliche Zusammenfassung. | Referenz und Kurzbeschreibung aus UC-002; Betreff ohne zusätzliche Zusammenfassung vereinbart. |
| **Objekt / Wohnung** | Ursprüngliche Referenz; bei bestätigter Zuordnung eine eindeutig als zugeordnet bezeichnete Objektangabe. Bei offener Zuordnung bleibt die ursprüngliche Referenz sichtbar. | Bestätigte Änderungen aus UI3 werden nach Speicherung und Aktualisierung hier übernommen; Unterscheidung gemäss FALL-04. |
| **Eingang** | Zeitpunkt der dauerhaften Fallannahme, z. B. `09.10.2026, 09:12`; Anzeige in `Europe/Zurich`, Speicherung gemäss FALL-08 in UTC. | Zusätzliche Spalte zur zeitlichen Orientierung. |
| **Letzte Aktion des Mieters** | Datum und Uhrzeit der letzten dauerhaft gespeicherten Mieter-Nachricht; ohne weitere Nachricht Zeitpunkt der ursprünglichen Einreichung. Anzeige in `Europe/Zurich`, auf-/absteigend sortierbar. | Spalte und Verwendung des letzten Mieterkontakts für die Standardsortierung vereinbart. |
| **Wiedervorlage** | Gespeichertes Wiedervorlagedatum; auf- und absteigend sortierbar. Ohne gesetzten Termin «–» mit verständlicher Bedeutung «Keine Wiedervorlage». | Zusätzliche Spalte vereinbart am 10.10.2026; Termin und Lebenszyklus gemäss FALL-11. |
| **Dringlichkeit** | Gespeicherte, fachlich verwendbare Bewertung mit Text; Änderungsquelle «System» oder «Immobilienverwaltung» kennzeichnen. Fehlende Bewertung: «Noch nicht bewertet». | Dringlichkeit aus UC-002; Bewertungsablauf und Änderungsquelle gemäss DRING-05 bis DRING-07. |
| **Bearbeitungsstatus** | Letzter bestätigter fachlicher Stand aus PropertyFlow. Anzeige eines der acht vereinbarten Bearbeitungsstatus gemäss FALL-05. «Aktiv» umfasst die ersten sieben Status und dient der Filterung. | Status aus UC-002 und FALL-05/FALL-06. |

**Vereinbart am 09.10.2026, ergänzt am 10.10.2026:** Die sieben Spalten Fall, Objekt/Wohnung, Eingang, Letzte Aktion des Mieters, Wiedervorlage, Dringlichkeit und Bearbeitungsstatus sind bestätigt. Hinweise werden nicht in der Liste angezeigt. Der ursprüngliche Betreff dient als Kurzbeschreibung; eine zusätzliche Zusammenfassung entfällt. Die vier Dringlichkeitsstufen und ihre Rangfolge sind zentral in [Dringlichkeitsbewertung](../specifications/dringlichkeitsbewertung.md) vereinbart; die acht Bearbeitungsstatus sind in FALL-05 vereinbart.

**Wiedervorlage – vereinbart am 10.10.2026:** Die zusätzliche Datumsspalte folgt [FALL-11](../specifications/fallverwaltung.md#fall-11-wiedervorlage-und-parallele-nachrichtenverarbeitung). Terminänderungen, eine automatische Vorverlegung und das Löschen in UI3 werden mit dem nächsten erfolgreichen Listenabruf sichtbar. Sortierung und Anzeige verwenden denselben gespeicherten Termin. Die Übersicht dient auch hier dem Lesen und Sortieren; die Bearbeitung des Datums erfolgt in UI3.

E-Mail-Adresse, vollständige Beschreibung, weitere Nachrichteninhalte, Memos, Entwürfe, KI-Begründungen und technische Prozesskennungen stehen nicht in dieser Liste. Zuständigkeits- und Handlungsempfehlung bleiben für die Detailprüfung vorgesehen; fachlich empfohlene Zuständigkeit ist keine bereits erfolgte Mitarbeiterzuweisung.

Die [Dringlichkeitsbewertung](../specifications/dringlichkeitsbewertung.md) definiert die vereinbarten Stufen und ihre Rangfolge. Erstbewertung nach Einreichung und Neubewertung nach jeder Mieter-Nachricht erfolgen im Camunda-gesteuerten Ablauf. Die Liste zeigt, filtert und sortiert nach der **wirksamen gespeicherten Stufe**. Eine manuelle Einstufung hat Vorrang und darf auch durch verspätete Systemergebnisse nicht überschrieben werden. Getrennt gespeicherte Systembewertungen ersetzen diesen Listenwert nicht (DRING-06). Während einer Neubewertung und bei ihrem Fehlschlag bleibt die bisherige Stufe erhalten. Ohne wirksame Bewertung erscheint «Noch nicht bewertet». Alle wirksamen Stufenänderungen werden mit ihrer Quelle historisiert und dem Mieter angezeigt (DRING-07). Offen ist die technische Synchronisierung, nicht der fachliche Vorrang manueller Einstufungen. Laufend und fehlgeschlagen sind Analyseinformationen, keine Dringlichkeitsstufen.

**Letzte Aktion des Mieters – vereinbartes Zeitmodell:** Der Wert folgt [FALL-08](../specifications/fallverwaltung.md#fall-08-nachvollziehbarkeit): Gezählt wird eine gespeicherte Mieter-Mitteilung, nicht das Öffnen des Falllinks, Polling oder eine Lesebestätigung. Nachrichten der Verwaltung, KI-/Camunda-Schritte und interne Memos verändern diesen Zeitpunkt nicht. Bei tokenbasierten Nachrichten bezeichnet «Mieter» die Absenderrolle des Fallzugangs, keine nachgewiesene persönliche Identität (ZUG-01). Der Wert wird bei der automatischen Listenaktualisierung mit aktualisiert. Die vereinbarte Standardsortierung verwendet zuerst Dringlichkeit und bei gleicher Dringlichkeit den letzten Mieterkontakt aufsteigend: Der am längsten zurückliegende Kontakt steht zuerst. Die separate Eingangsspalte bleibt erhalten.

Interne Problemhinweise bleiben gemäss FALL-05/FALL-06 am Fall nachvollziehbar, werden aber nicht in der Übersicht angezeigt, auch nicht als Zusatz in anderen Spalten. Bei aktiven Fällen zeigt der Bearbeitungsstatus «Mitarbeiterprüfung erforderlich» den Prüfbedarf. Die Darstellung der Gründe wird bei Screen 03 konkretisiert. Allgemeine Lade-, Such- und Aktualisierungsfehler der Übersicht bleiben verständlich sichtbar. Es werden keine Live-Abfragen an LLM, Maildienst oder Camunda pro Tabellenzeile vorausgesetzt.

**Farbkennzeichnung – vereinbart:** Die Dringlichkeitsanzeige verwendet die bestätigte zentrale Zuordnung aus [DRING-04](../specifications/dringlichkeitsbewertung.md#dring-04-farbkennzeichnung). Die Bezeichnung bleibt immer als Text sichtbar. Konkrete Farbtöne und Kontraste folgen in der späteren gemeinsamen Gestaltung.

## 4. Suche, Filter und Sortierung

| Bedienelement | Verhalten |
|---|---|
| Volltextsuche | **Vereinbart:** Mitarbeiter-Fallübersicht durchsuchen, einschliesslich gespeicherter veröffentlichter externer Nachrichten und sämtlicher gespeicherter interner Memos; externe Entwürfe ausschliessen. Die Liste aktualisiert sich nach kurzer Eingabepause automatisch; Konkretisierung: 1 Sekunde nach der letzten Textänderung. Kein Suchbutton in der normalen Bedienung. Durchsuchbare Fallfelder: Case-ID, Betreff, ursprüngliche Beschreibung und ursprüngliche bzw. bestätigte Objekt-/Wohnungsreferenz. |
| Abgeschlossene Fälle ein-/ausblenden | **Vereinbart:** Standardmässig nur aktive Fälle. Ein Button schaltet abgeschlossene Fälle zusätzlich ein oder wieder aus. «Inaktiv» bezeichnet hier abgeschlossene Fälle gemäss FALL-05, keinen neuen Status. Buttontexte: «Abgeschlossene Fälle einblenden» bzw. «Abgeschlossene Fälle ausblenden». |
| Dringlichkeit | **Vereinbart:** Filter mit «Alle» als Vorgabe, den vier Stufen gemäss [DRING-01](../specifications/dringlichkeitsbewertung.md#dring-01-fachliche-stufen) sowie «Noch nicht bewertet». Die Auswahl schränkt die Liste auf die gewählte Stufe bzw. den unbewerteten Zustand ein. |
| Hinweise | **Vereinbart:** Keine Hinweisspalte, keine fallbezogenen Problemhinweise und kein Hinweisfilter in der Liste. |
| Bearbeitungsstatus | **Vereinbart:** Kein zusätzlicher Bearbeitungsstatus-Filter. Die Statusspalte bleibt sichtbar und auf-/absteigend sortierbar. |
| Sortierung | **Vereinbart:** Standardmässig höchste Dringlichkeit zuerst, bei gleicher Dringlichkeit nach Letzter Aktion des Mieters, ältester Kontakt zuerst. Alle sieben Spalten sind per Klick auf den Spaltentitel sortierbar: erster Klick aufsteigend mit ↑, zweiter Klick absteigend mit ↓. «Fall» wird nach dem ursprünglichen Betreff sortiert. Noch nicht bewertete Fälle stehen vor allen bewerteten Fällen; innerhalb dieser Gruppe gilt ältester letzter Mieterkontakt zuerst. Bei gleichen Sortierwerten sorgt eine eindeutige Fallreferenz für stabile Reihenfolge. |
| Filteränderung / Zurücksetzen | **Vereinbart:** Filteränderungen wirken sofort, ohne zusätzlichen Anwenden-Button. Geänderte Suche oder Filter beginnen auf Seite 1. «Filter zurücksetzen» stellt die Vorgaben wieder her und aktualisiert die Liste unmittelbar. |
| Seitennavigation | **Vereinbart:** Maximal 50 Fälle pro Seite; bei mehr Treffern Navigation mit «Vorwärts» und «Rückwärts». Ergebniszahl und aktuelle Seite anzeigen. Zählung erfolgt nur über berechtigte Treffer. |

Die Filter wirken kombiniert (UND). Suchtext und gewählte Filter bleiben sichtbar. Gezieltes Vanilla JavaScript löst Such- und Filterabrufe aus und ersetzt den Ergebnisbereich ohne vollständiges Seitenneuladen. Serverseitige Suche, Sortierung und Seitennavigation bleiben auch ohne JavaScript nutzbar; dafür erscheint als Fallback ein Formularbutton «Suchen / Filter anwenden». Die Suchbegriffe können als Queryparameter in der Browserhistorie stehen; Hinweise und Formularbeispiele verwenden keine Zugangstokens oder Nachrichtentexte.

### Automatische Suche und Filteranwendung

**Vereinbart am 09.10.2026:** Suche und Filter wirken ohne ausdrückliches Absenden. Für Texteingaben ist eine kurze Verzögerung von etwa 1–2 Sekunden vorgesehen; diese Spezifikation konkretisiert sie auf **1 Sekunde nach der letzten Textänderung** (Debounce). Jede weitere Eingabe startet die Wartezeit erneut. Dies gilt auch beim Löschen von Text; ein leeres Suchfeld hebt die Suchbeschränkung nach derselben kurzen Pause auf. Die Suche wartet nicht auf den nächsten 20-Sekunden-Abruf.

Der Dringlichkeitsfilter und der Button zum Ein-/Ausblenden abgeschlossener Fälle wirken sofort. Dabei wird die gesamte aktuelle Such-/Filterauswahl übernommen; eine noch laufende Eingabewartezeit entfällt. Zurücksetzen wirkt ebenfalls sofort. Eine neue Suche oder Filterauswahl startet auf Seite 1 und erhält die gewählte Sortierung; Zurücksetzen stellt dagegen die vereinbarten Vorgaben wieder her.

Während eines Abrufs bleibt der eingegebene Text erhalten. Ein sichtbarer Ladezustand kennzeichnet, dass die bisherigen Ergebnisse noch zur vorherigen Auswahl gehören. Ältere Antworten dürfen eine neuere Eingabe oder Filterauswahl nicht überschreiben. Die Anfragekoordination mit der periodischen Aktualisierung folgt Abschnitt 5. Ohne JavaScript übernimmt ausschliesslich der oben beschriebene Formular-Fallback die Auslösung.

### Volltextsuche über Fälle und Nachrichten

**Vereinbart:** Die Volltextsuche gehört ausschliesslich zur Mitarbeiter-Fallübersicht und umfasst auch alle internen Memos. Alle Mitarbeitenden haben dieselben Adminrechte und Zugriff auf alle Fälle gemäss ZUG-04. Sie wird nicht in der Mieteransicht angeboten. Ein Begriff aus einer Nachricht oder einem internen Memo kann den zugehörigen Fall als Treffer liefern. Mehrere passende Nachrichten desselben Falls ergeben nur einen Listeneintrag. Die Suche gilt über alle berechtigten Fälle innerhalb der aktuellen Filter, nicht nur über die gerade sichtbare Seite. Abgeschlossene Fälle werden nur bei eingeschaltetem Button berücksichtigt.

**Suchverhalten – vereinbart am 09.10.2026:** Die Suche findet vollständige Wörter und Teilwörter. Jedes Suchwort darf als zusammenhängender Teil eines längeren Wortes vorkommen, auch innerhalb oder am Ende des Wortes; eine Eingabe von Platzhaltern ist dafür nicht nötig. Bei mehreren Suchwörtern müssen **alle** innerhalb desselben Falls gefunden werden (UND-Verknüpfung). Für jedes Suchwort gilt die Teilwortsuche. Die Treffer dürfen auf verschiedene durchsuchbare Fallfelder, veröffentlichte externe Nachrichten oder gespeicherte interne Memos verteilt sein; mehrere Suchwörter dürfen auch im selben Wort vorkommen. Fehlt ein Suchwort im zulässigen Suchumfang des Falls, ist der Fall kein Treffer. Beispiel: «Heiz ausfall» findet «Heizungsausfall»; «Heiz kalt» findet einen Fall mit «Heizung» im Betreff und «kalt» in einer veröffentlichten Nachricht.

**Gross-/Kleinschreibung – vereinbart am 09.10.2026:** Die Suche unterscheidet nicht zwischen Gross- und Kleinschreibung. Dies gilt für vollständige Wörter und Teilwörter sowie für jedes Suchwort einer UND-verknüpften Suche im gesamten zulässigen Suchumfang. «heiz», «Heiz» und «HEIZ» liefern bei ansonsten gleicher Auswahl dieselben Treffer.

**Sonderzeichen – vereinbart am 09.10.2026:** Satz- und Sonderzeichen wie Bindestriche, Kommas, Punkte und Ausrufezeichen werden für den Suchvergleich ignoriert, sowohl in der Eingabe als auch in den durchsuchbaren Inhalten. Buchstaben einschliesslich Umlauten und Ziffern bleiben erhalten. Leerzeichen trennen weiterhin Suchwörter; die UND-Verknüpfung gilt für alle nach dieser Bereinigung verbleibenden Suchwörter. Beispiele: «REQ2026001» und «REQ-2026-001» finden dieselbe Case-ID; «Heiz, kalt!» liefert dieselben Treffer wie «Heiz kalt». Sonderzeichen aktivieren keine Platzhalter oder Suchoperatoren. Die gespeicherten Originaltexte und ihre Anzeige werden nicht verändert. Es werden weiterhin keine Fundstellen aus unterschiedlichen Fällen oder durch unzulässiges Zusammenfügen verschiedener Felder zu einem Wort kombiniert.

**Weitere Konkretisierung:** Leere Suche hebt die Suchbeschränkung auf; dies gilt auch, wenn nach Entfernen von Sonderzeichen keine Suchwörter übrig bleiben. Exakte Case-ID-Suche bleibt möglich. Die technische Normalisierung wird im Suchvertrag entsprechend diesen Regeln festgelegt. Technische Suchfehler werden nicht als null Treffer ausgegeben. Eine darüber hinausgehende Suche nach Wortformen oder Synonymen ist durch die Teilwortsuche nicht festgelegt.

**Vereinbart:** Externe Entwürfe sind von der Volltextsuche ausgeschlossen; bei externer Kommunikation werden nur gespeicherte, veröffentlichte Nachrichten durchsucht. «Versendet» bezeichnet hier die Veröffentlichung im Fallverlauf, nicht eine bestätigte E-Mail-Zustellung. Sämtliche gespeicherten internen Memos bleiben im Suchumfang; eine zusätzliche Memofreigabe oder Einschränkung auf einzelne Mitarbeitende gibt es nicht. Nicht gespeicherte Eingaben und KI-Teilantworten gehören nicht zum Suchumfang. Mieter und Aufrufer ohne Mitarbeiterberechtigung erhalten weder Suchzugriff noch Trefferzahlen oder Fundstellen. Die Berechtigungsprüfung gilt auch bei Nutzung eines Suchindexes und bei jedem automatischen Aktualisierungsabruf. Ein interner Suchindex ist keine Freigabe für LLM-/RAG-Kommunikationsgenerierung; KOM-02 gilt unverändert.

**Trefferanzeige – abgeglichen am 10.10.2026:** Die sieben vereinbarten Spalten bleiben erhalten; die Liste zeigt keine Nachrichtenauszüge. Der Falllink öffnet die normale Detailansicht ohne automatischen Sprung zur Fundstelle und ohne Hervorhebung. Der Betreff bleibt die Kurzbeschreibung des Falls.

Bis zu 50 passende Fälle erscheinen gemeinsam auf einer Seite und sind durch Scrollen erreichbar. Weitere Treffer folgen auf den nächsten Seiten. Filter und Sortierung werden serverseitig auf die gesamte berechtigte Treffermenge angewandt, bevor diese in Seiten aufgeteilt wird. «Vorwärts» und «Rückwärts» erhalten die Auswahl; an erster bzw. letzter Seite ist die jeweils nicht verfügbare Richtung deaktiviert. Die automatische Aktualisierung bleibt auf der gewählten Seite. Entfällt diese durch eine geänderte Treffermenge, gilt das Verhalten aus Abschnitt 7.

Die vereinbarte Standardsortierung verwendet die zentrale Rangfolge aus [DRING-01](../specifications/dringlichkeitsbewertung.md#dring-01-fachliche-stufen). Unbewertete Fälle dürfen nicht stillschweigend als niedrige Dringlichkeit eingeordnet werden; sie stehen in der Standardsortierung als eigene Gruppe vor allen bewerteten Fällen, nach ältestem letztem Mieterkontakt sortiert.

**Sortierbedienung und Betreff – vereinbart am 09.10.2026:** Ein Klick auf einen Spaltentitel wählt die aufsteigende Sortierung und zeigt direkt beim Titel den Pfeil **↑**. Der zweite Klick auf denselben Titel schaltet auf absteigend und zeigt **↓**; weitere Klicks wechseln zwischen diesen beiden Richtungen. Bei Wechsel auf eine andere Spalte beginnt deren Sortierung aufsteigend. Der Pfeil zeigt die aktuell angewandte Richtung. Nur der Titel der aktiv gewählten Sortierspalte trägt einen Richtungspfeil. Die Spalte «Fall» sortiert nach dem ursprünglichen Betreff: zuerst A–Z, danach Z–A. Die Sortierung wird unmittelbar angewandt und bleibt bei Suche, Filtern, Seitenwechsel und automatischer Aktualisierung erhalten. Spaltenlinks erhalten Suche und Filter, beginnen aber auf Seite 1. Die aktive Sortierspalte und Richtung sind zusätzlich textlich und über `aria-sort` erkennbar. Die vereinbarte mehrteilige Standardsortierung wird weiterhin gesondert beschrieben; vor einer manuellen Spaltenauswahl wird sie nicht durch einen irreführenden einzelnen Richtungspfeil dargestellt.

**Weitere Sortierwerte für die Umsetzung:** Objekt/Wohnung nach angezeigter Referenz, Eingang und Letzte Aktion des Mieters jeweils nach Zeitpunkt, Dringlichkeit nach fachlichem Rang, Bearbeitungsstatus nach angezeigtem Statustext. «Wiedervorlage» sortiert aufsteigend nach frühestem und absteigend nach spätestem gespeicherten Termin. Als Konkretisierung stehen Fälle ohne Wiedervorlage in beiden Richtungen nach den Fällen mit Termin; vollständige Gleichstände werden durch die eindeutige Fallreferenz stabilisiert. Die Sortierung gilt vor der Seiteneinteilung und erhält Suche und Filter. Die bisherige Standardsortierung nach Dringlichkeit und letztem Mieterkontakt bleibt bestehen. Der genaue Textvergleich, etwa für Umlaute oder Ziffern in Texten, bleibt eine technische Konkretisierung. Ohne Bewertung erscheint «Noch nicht bewertet», ohne bestätigte Objektzuordnung die ursprüngliche Referenz, ohne weitere Mieternachricht der Eingangszeitpunkt und ohne Wiedervorlage «–». Ein GET-Link mit Buttondarstellung schaltet abgeschlossene Fälle auch ohne JavaScript um; er erhält Suche und Sortierung und beginnt auf Seite 1.

## 5. Aktionen und Aktualisierung

- **Fall öffnen:** Normaler Link an der Case-ID; keine ausschliesslich per Maus anklickbare Tabellenzeile. Das Ziel wird bei jedem Aufruf erneut autorisiert.
- **Ansicht aktualisieren:** Manueller GET mit den aktuellen Filtern, Sortierung und Seite. Zeigt den gespeicherten Stand erneut; startet keine Analyse, keinen Prozess und keine E-Mail.

**Korrigiert am 10.10.2026:** Es gibt keine Mitarbeiteranmeldung und keinen «Abmelden»-Button. Auch ein Ablauf für eine abgelaufene Anmeldung entfällt gemäss ZUG-04.

**Vereinbart am 09.10.2026:** Auch die Mitarbeiter-Fallübersicht aktualisiert den gespeicherten Stand automatisch alle **20 Sekunden**. Damit werden zwischenzeitliche Ergebnisse der Camunda-gesteuerten Verarbeitung sichtbar, sobald PropertyFlow sie fachlich gespeichert hat. Vanilla JavaScript aktualisiert den Ergebnisbereich ohne vollständiges Seitenneuladen. Der Browser liest ausschliesslich über PropertyFlow; er fragt Camunda nicht direkt ab und startet oder beendet durch diese Abrufe keine Verarbeitung.

**Bedienungs- und Fehlerverhalten:** Die nachfolgend ausdrücklich als vereinbart bezeichneten Regeln sind am 09.10.2026 bestätigt. Ergänzende technische Abläufe bleiben Konkretisierungsvorschläge.

- Die periodische Aktualisierung erhält Suche, Filter, Ein-/Ausblenden abgeschlossener Fälle, Sortierung und aktuelle Seite. Eine Such- oder Filteränderung beginnt dagegen gemäss Abschnitt 4 auf Seite 1. Texteingaben während der kurzen Debounce-Pause werden nicht überschrieben.
- **Vereinbart – Listenzugehörigkeit:** Status, Dringlichkeit, Trefferzahl und Listenzugehörigkeit werden aus derselben autorisierten Auswahl aktualisiert. Ein abgeschlossener Fall entfällt aus der Nur-aktiv-Auswahl; bei eingeblendeten abgeschlossenen Fällen bleibt er sichtbar, sofern die übrigen Filter passen.
- **Vereinbart – verschobene Zeilen:** Geänderte Sortierwerte ordnen die Fälle entsprechend der gewählten Sortierung neu ein. Fokus und Leseposition bleiben soweit möglich am bisherigen Fall verankert; kein ungefragtes Scrollen zum Listenanfang. Entfällt der fokussierte Fall, wird der Fokus kontrolliert auf den Ergebnisbereich gesetzt und die Änderung verständlich gemeldet. DOM-Aktualisierungen dürfen keine unbeabsichtigte Öffnung eines anderen Falls bewirken.
- **Vereinbart – entfallene Seiten:** Existiert die aktuelle Seite nach einer Aktualisierung nicht mehr, wechselt die Ansicht auf die letzte gültige Seite derselben Auswahl. Beispiel: Aus Seite 3 von 3 wird Seite 2 von 2. Eine kurze Meldung erklärt den Wechsel; Suche, Filter und Sortierung bleiben erhalten. Bei null Treffern erscheint die passende Leermeldung gemäss Abschnitt 7.
- Höchstens eine Listenanfrage läuft gleichzeitig; Live-Suche, Filterabrufe und periodische Aktualisierung werden gemeinsam koordiniert. Während der Debounce-Pause und eines Such-/Filterabrufs startet kein zusätzlicher 20-Sekunden-Abruf. Bereits bei Änderung der Eingabe oder Auswahl gelten ältere Antworten als überholt und werden verworfen; eine noch laufende Anfrage wird beendet oder die neueste Auswahl anschliessend verarbeitet. Ein ausgeblendeter Tab pausiert automatische Abrufe; bei Rückkehr wird die aktuelle Auswahl neu gelesen und danach das Intervall fortgesetzt.
- **Vereinbart – Ladefehler:** Scheitert der erste Abruf, erscheint «Die Fallübersicht konnte nicht geladen werden.» mit «Erneut versuchen». Scheitert eine spätere Aktualisierung, bleiben zuletzt erfolgreich gelesene Daten sichtbar, ergänzt um «Aktualisierung momentan nicht möglich. Stand: …» mit dem letzten erfolgreichen Lesezeitpunkt. Fehlgeschlagene Abrufe erzeugen keine falsche Leerliste. Technische Wiederholungen erfolgen verzögert; konkrete Abstände bleiben festzulegen, Rate Limits beachten gegebenenfalls `Retry-After`.
- Eine ausdrückliche serverseitige Zugriffsverweigerung stoppt die Abrufe und entfernt die Falldaten aus dem sichtbaren Ergebnisbereich; es erscheint ein Zugriffshinweis ohne Anmeldeaufforderung. Ein technischer Ladefehler wird nicht als Zugriffsverweigerung behandelt. Jeder Abruf wahrt die Datenabgrenzung nach ZUG-04; ihre technische Umsetzung bleibt dort offen.
- «Zuletzt aktualisiert am …» bezeichnet den letzten erfolgreichen Lesezeitpunkt, nicht die letzte fachliche Falländerung. Manuelles Aktualisieren bleibt verfügbar; ohne JavaScript erfolgt Aktualisierung über erneuten Seitenaufruf.

Die gesamte Liste kann aktive Fälle enthalten, auch wenn einzelne Fälle abgeschlossen wurden; ein einzelner Abschluss beendet daher das Listen-Polling nicht. Hintergrundverarbeitung läuft unabhängig vom Seitenwechsel weiter. Die technische Route für ergänzende Leseabrufe wird im Servicevertrag konkretisiert.

### Geplanter HTTP-Vertrag – neuer Vorschlag, keine vorhandenen Controller

| Methode und Route | Zweck |
|---|---|
| `GET /mitarbeiter/faelle` | Berechtigte Fallübersicht, validierte Such-/Filter-/Sortier-/Seitenparameter; SSR. |
| `GET /mitarbeiter/faelle/{caseId}` | Separat autorisiertes Falldetail gemäss UC-003; Route im nächsten Screen abstimmen. |

Case-ID ist hier eine Referenz im Mitarbeiterbereich und gewährt allein keinen Zugriff. Mieter-Tokenlinks bleiben getrennt. GETs sind rein lesend. Serverseitig werden Parameterlängen, zulässige Filter-/Sortierwerte und Seitengrenzen geprüft; konkrete technische Namen und Grenzwerte folgen im Servicevertrag.

## 6. Rechte und Datenabgrenzung

**Korrigierte Grundlage am 10.10.2026:** Der Mitarbeiterbereich hat keine Anmeldung. Alle Mitarbeitenden besitzen dieselben Adminrechte gemäss ZUG-04. Ein Mieterzugang berechtigt nicht zur Übersicht; das technische Verfahren zur Absicherung dieser Trennung ist noch festzulegen. Der Browser greift nicht direkt auf Camunda zu.

**UI-/Vertragsvorschlag:** Vor Ausgabe der Liste, Trefferzahl und Filteroptionen muss der Server die für den Mitarbeiter lesbaren Fälle bestimmen. Suche, Pagination und direkte Detaillinks dürfen diese Berechtigung nicht erweitern. **Vereinbart:** Alle Mitarbeitenden haben dieselben Adminrechte und Zugriff auf alle Fälle sowie interne Memos gemäss ZUG-04. Die Prüfung schützt die Trennung zum Mieterzugang und zu Aufrufern ohne Mitarbeiterberechtigung; zwischen Mitarbeitenden bestehen keine unterschiedlichen Fallrechte. Fachliche Schreibregeln bleiben verbindlich.

Die Übersicht enthält keine Mieter-Zugriffstokens, verschlüsselten Versandkopien oder Möglichkeiten zum Rekonstruieren persönlicher Links. Interne Memos bleiben gemäss KOM-01 ausschliesslich intern; externe Entwürfe werden erst durch Veröffentlichung mieterzugänglich. Die Liste enthält keine Kommunikationsgenerierung. Für die Formulierungshilfe im Detail gilt KOM-02: keine systemseitige Übernahme von Memos oder ihren Ableitungen; bewusst eingegebene kurze Sachanweisungen des Mitarbeiters sind zulässig. Nach Abschluss gilt KOM-04 für alle Absender. Die Auswahl oder Aktualisierung eines Falls sendet keine Mieter-E-Mail; fachliche Änderungen im Detail richten sich nach BEN-01.

## 7. Zustände und Fehlerfälle – Darstellungsvorschlag

| Zustand | Darstellung / nächste Aktion |
|---|---|
| Liste geladen | Berechtigte Treffer, Ergebniszahl, angewandte Filter und Lesezeitpunkt. |
| Keine Fälle bei eingeblendeten abgeschlossenen Fällen und ohne weitere Such-/Filtereinschränkung | «Keine Fälle verfügbar.» Keine irreführende leere Tabelle oder Aussage über fremde Fälle. |
| Keine Treffer für Suche oder einschränkende Filter | «Keine Fälle für diese Auswahl.» Suche und Filter beibehalten; «Filter zurücksetzen» anbieten. Nicht die Meldung «Heute nichts zu bearbeiten.» verwenden, da ausserhalb der Auswahl aktive Fälle vorhanden sein können. |
| Keine aktiven Fälle bei Standardauswahl: Suchfeld leer, Dringlichkeit «Alle», abgeschlossene Fälle ausgeblendet | **Vereinbart:** «Heute nichts zu bearbeiten.» anstelle der Tabelle. Der Button «Abgeschlossene Fälle einblenden» bleibt verfügbar. Die Meldung gilt auch, wenn noch gar keine Fälle existieren; sie führt keinen Datumsfilter ein und wird nur nach erfolgreichem Abruf angezeigt. |
| Erster Listenabruf fehlgeschlagen | **Vereinbart:** «Die Fallübersicht konnte nicht geladen werden.» mit «Erneut versuchen»; Fehler nicht als null Treffer oder vollständige Liste darstellen. |
| Spätere Listenaktualisierung fehlgeschlagen | **Vereinbart:** Bisherige Daten bleiben sichtbar, ergänzt um «Aktualisierung momentan nicht möglich. Stand: …» mit dem letzten erfolgreichen Lesezeitpunkt. |
| Analyse fehlgeschlagen, Objektzuordnung offen oder E-Mail-Versand zu klären | **Vereinbart:** Die Liste zeigt bei aktivem Fall den Status «Mitarbeiterprüfung erforderlich». Die jeweiligen Gründe bleiben intern am Fall gemäss FALL-05 nachvollziehbar und erscheinen nicht in der Liste. Automatische Wiederholungen ändern diese Regel nicht. Ein erst nach Abschluss festgestelltes Versandproblem wird intern festgehalten; die Liste zeigt weiterhin «Abgeschlossen» und eröffnet den Fall nicht wieder. |
| Menschliche Entscheidung nach FALL-10 ausstehend | Bei aktivem Fall «Mitarbeiterprüfung erforderlich»; Gründe und Freigaben gehören ins separate Mitarbeiterdetail. Keine zusätzlichen Hinweise oder Aktionen in der Liste. |
| Keine Backoffice-Berechtigung | Verständlicher Zugriffshinweis ohne Falldaten oder Trefferzahlen. |
| Einzelner Fall nicht mehr lesbar/verfügbar | Im Detail neutral «Fall nicht verfügbar» und Weg zur Übersicht; keine Daten anderer Fälle anzeigen. |
| Fall inzwischen geändert/abgeschlossen | Detail liest den aktuellen fachlichen Stand und prüft Schreibrechte erneut. Die zuvor geladene Liste ist keine Berechtigungs- oder Statusgarantie. |
| Ungültige Such-/Filterparameter | Verständlicher Hinweis mit korrigierbarer Auswahl; keine ungeprüften Werte übernehmen und nicht stillschweigend erweitern. |
| Seite nach Datenänderung ausserhalb des Ergebnisses | **Vereinbart:** Auf letzte gültige Seite derselben Auswahl wechseln und Wechsel kurz erklären; bei null Treffern die zur aktuellen Auswahl passende Leermeldung. |

## 8. Technischer Wireframe – Layoutentwurf

**Abgleich am 09.10.2026:** Die Grundansichten und ergänzenden Zustandsausschnitte bilden die bestätigten UI2-Entscheidungen ab. Die Ausschnitte zeigen alternative Zustände derselben Seite; unveränderte Bereiche werden nicht jedes Mal wiederholt.

Die Skizze bildet die vereinbarten Listenfunktionen ab. Für schmale Bildschirme sind Fallblöcke mit sieben Angaben sowie Suche, Filter und Sortierauswahl darüber vereinbart. Die genaue visuelle Gestaltung bleibt ein Entwurf; Farbfamilien folgen DRING-04, genaue Farbtöne und Schriften werden später gestaltet. Alle Beispieldaten sind fiktiv. «Aktiv» ist die Filtergruppe, die Statusspalte zeigt die fachlichen Bearbeitungsstatus aus FALL-05.

### 8.1 Breiter Bildschirm – Standardansicht

```text
PropertyFlow · Mitarbeiterbereich
Fallübersicht                                                    [Ansicht aktualisieren]
Zuletzt aktualisiert: 09.10.2026, 11:00 · automatisch alle 20 Sekunden

Volltextsuche: [Fälle, versendete Nachrichten und interne Memos______________]
Suche automatisch nach 1 Sekunde Eingabepause · Filter wirken sofort
[Abgeschlossene Fälle einblenden]    Dringlichkeit: [Alle v]
[Filter zurücksetzen]
Auswahl: Nur aktive Fälle

Standardsortierung: Noch nicht bewertet zuerst; dann Kritisch > Hoch > Normal > Niedrig.
Innerhalb gleicher Bewertung: letzte Aktion des Mieters, ältester Kontakt zuerst.
5 Treffer · Seite 1 von 1 · maximal 50 Fälle pro Seite

+------------------------+--------------------------+--------------------+----------------------------+----------------+------------------------------+--------------------------+
| Fall / Betreff         | Objekt / Wohnung         | Eingang            | Letzte Aktion des          | Wiedervorlage  | Dringlichkeit                | Bearbeitungsstatus       |
|                        |                          |                    | Mieters                    |                |                              |                          |
+------------------------+--------------------------+--------------------+----------------------------+----------------+------------------------------+--------------------------+
| [REQ-2026-003]         | Beispielweg 8, Whg. 2    | 09.10.2026, 10:45  | 09.10.2026, 10:45          | –              | Noch nicht bewertet (Blau)   | Mitarbeiterprüfung       |
| Fenster undicht        |                          |                    |                            |                | Quelle: –                    | erforderlich             |
+------------------------+--------------------------+--------------------+----------------------------+----------------+------------------------------+--------------------------+
| [REQ-2026-002]         | Musterweg 4, Wohnung 1   | 09.10.2026, 10:10  | 09.10.2026, 10:50          | –              | Noch nicht bewertet (Blau)   | Mitarbeiterprüfung       |
| Tür klemmt             | Zuordnung bestätigt      |                    |                            |                | Quelle: –                    | erforderlich             |
+------------------------+--------------------------+--------------------+----------------------------+----------------+------------------------------+--------------------------+
| [REQ-2026-004]         | Musterweg 6, Wohnung 3   | 09.10.2026, 10:55  | 09.10.2026, 10:55          | –              | Kritisch (Rot)               | In Bearbeitung           |
| Wasser aus der Decke   | Zuordnung bestätigt      |                    |                            |                | Quelle: System               |                          |
+------------------------+--------------------------+--------------------+----------------------------+----------------+------------------------------+--------------------------+
| [REQ-2026-001]         | Musterstr. 12, Wohnung 4 | 09.10.2026, 09:12  | 09.10.2026, 10:00          | 13.10.2026     | Hoch (Orange)                | Wartet auf               |
| Heizung defekt         | Zuordnung bestätigt      |                    |                            |                | Quelle: System               | Wiedervorlage            |
+------------------------+--------------------------+--------------------+----------------------------+----------------+------------------------------+--------------------------+
| [REQ-2026-005]         | Beispielweg 2, Whg. 1    | 09.10.2026, 09:30  | 09.10.2026, 10:30          | 12.10.2026     | Hoch (Orange)                | Wartet auf               |
| Warmwasser ausgefallen | Zuordnung bestätigt      |                    |                            |                | Quelle: Immobilienverwaltung | Wiedervorlage            |
+------------------------+--------------------------+--------------------+----------------------------+----------------+------------------------------+--------------------------+

[Rückwärts – deaktiviert]                  Seite 1 von 1                  [Vorwärts – deaktiviert]
```

Die ersten beiden unbewerteten Fälle stehen nach ältestem letztem Mieterkontakt vor den bewerteten Fällen. Danach folgt Kritisch vor Hoch; innerhalb von Hoch steht der Kontakt um 10:00 vor 10:30. Die Änderungsquelle ist unabhängig vom Rang. Bei fehlender Bewertung gibt es noch keine Änderungsquelle. Die ausgeschriebenen Farben erläutern den Entwurf; die Umsetzung verwendet eine Farbkennzeichnung mit sichtbarem Stufentext.

Alle sieben Spaltentitel sind anklickbar. Die Skizze zeigt die Standardsortierung ohne manuell gewählte Einzelspalte. Nach dem ersten Klick auf «Fall / Betreff» erscheint **Fall / Betreff ↑**, nach dem zweiten **Fall / Betreff ↓**. Nur die aktuell ausgewählte Spalte zeigt den Richtungspfeil; die aktive Sortierung wird zusätzlich verständlich angegeben. Der genaue Textvergleich bleibt die technische Konkretisierung aus Abschnitt 4.

Pagination-Links ohne Ziel sind deaktiviert. Lange Texte werden umbrochen, fachliche Werte nicht in Tooltips versteckt. Die Beispielkürzungen «Whg.» und «Musterstr.» sind fiktive gespeicherte Referenztexte, keine automatische Kürzung von Falldaten. Die Skizze zeigt die normale Bedienung mit automatischer Suche und sofortiger Filteranwendung. Der zusätzliche Formularbutton ist ausschliesslich für den Fallback ohne JavaScript vorgesehen.

### 8.2 Schmaler Bildschirm

**Vereinbart am 09.10.2026:** Jeder Fall erscheint als kompakter, beschrifteter Block mit denselben sieben Angaben untereinander und derselben Feldreihenfolge. Suche und Filter stehen oberhalb der Fallblöcke. Case-ID bleibt Link. Die Sortierauswahl darüber bietet die Standardsortierung und alternativ jede der sieben Spalten mit auf-/absteigender Richtung und demselben Pfeil ↑ bzw. ↓; alle sieben Angaben bleiben auch auf schmalen Bildschirmen verfügbar. Genaue Abstände, Schrift und Umschaltbreite werden bei der Gestaltung konkretisiert.

```text
PropertyFlow · Mitarbeiterbereich
Fallübersicht       [Aktualisieren]
Zuletzt aktualisiert: 09.10.2026, 11:00
Automatisch alle 20 Sekunden
Volltextsuche: [________________]
In Fällen, versendeten Nachrichten und internen Memos
Suche nach 1 Sekunde Eingabepause · Filter sofort
[Abgeschlossene Fälle einblenden]
Dringlichkeit: [Alle v]
Sortierung:    [Standardsortierung v]
Auswahl: Nur aktive Fälle
Unbewertete zuerst; dann Dringlichkeit.
Bei gleicher Bewertung: ältester Mieterkontakt zuerst.
[Filter zurücksetzen]

5 Treffer · Seite 1 von 1 · maximal 50 Fälle pro Seite
[REQ-2026-003] · Fenster undicht
Objekt/Wohnung: Beispielweg 8, Whg. 2
Eingang: 09.10.2026, 10:45
Letzte Aktion des Mieters: 09.10.2026, 10:45
Wiedervorlage: – (keine gesetzt)
Dringlichkeit: Noch nicht bewertet (Blau)
Änderungsquelle: –
Bearbeitungsstatus: Mitarbeiterprüfung erforderlich

... vier weitere Fälle in derselben Reihenfolge ...
[Rückwärts – deaktiviert] [Vorwärts – deaktiviert]
```

### 8.3 Suche, Dringlichkeitsfilter und Sortierzustände

Die geöffnete Dringlichkeitsauswahl enthält sämtliche vereinbarten Werte. Die Farbangaben erläutern die Kennzeichnung der jeweiligen Stufe; «Alle» ist die Filtervorgabe und keine eigene Stufe.

```text
Dringlichkeit: [Alle v]
+-----------------------------+
| Alle                        |
| Kritisch (Rot)              |
| Hoch (Orange)               |
| Normal (Gelb)               |
| Niedrig (Grün)              |
| Noch nicht bewertet (Blau)  |
+-----------------------------+
```

**Suche und sofortiger Filterwechsel:** Der folgende Ausschnitt zeigt eine eingegebene Suche bei ausgewählter Dringlichkeit «Hoch». Während des Abrufs ist erkennbar, dass die bisherigen Ergebnisse noch zur vorherigen Auswahl gehören.

```text
Volltextsuche: [Heiz, kalt!______________________________________________]
Suche nach 1 Sekunde Eingabepause · Filter wirken sofort
[Abgeschlossene Fälle einblenden]    Dringlichkeit: [Hoch v]
[Filter zurücksetzen]
Ergebnisse werden aktualisiert …
Die angezeigten Ergebnisse gehören noch zur vorherigen Auswahl.
```

Die Erläuterung im Ladezustand ist eine konkrete Textfassung des bereits vereinbarten Verhaltens aus Abschnitt 4. Nach erfolgreichem Abruf verschwinden Ladehinweis und Kennzeichnung der alten Auswahl. Suchtext, Fokus und aktuelle Filter bleiben erhalten; veraltete Antworten dürfen die neue Auswahl nicht überschreiben.

**Suchregeln zu diesem Ausschnitt:** «Heiz, kalt!» und «heiz KALT» liefern bei gleicher Auswahl dieselben Treffer. Beide Teilwörter müssen im selben Fall vorkommen, dürfen aber auf Betreff, weitere zulässige Fallfelder, veröffentlichte externe Nachrichten oder gespeicherte interne Memos verteilt sein. Gross-/Kleinschreibung sowie Satz- und Sonderzeichen werden wie in Abschnitt 4 behandelt. Externe Entwürfe, ungespeicherte Texte und KI-Teilantworten bleiben ausgeschlossen. Die Suche gilt für die gesamte ausgewählte Treffermenge vor der Aufteilung in Seiten. Mehrere Fundstellen liefern denselben Fall nur einmal; Nachrichtenauszüge oder Memo-Inhalte werden nicht als zusätzliche Listenspalten eingeblendet.

**Angeklickte Spaltentitel – Ausschnitte des Tabellenkopfs:**

Erster Klick auf «Fall / Betreff»: Betreff A–Z. Zweiter Klick: Betreff Z–A.

```text
Sortierung: Betreff A–Z
+------------------------+--------------------------+--------------------+----------------------------+----------------+------------------------------+--------------------------+
| Fall / Betreff ↑       | Objekt / Wohnung         | Eingang            | Letzte Aktion des          | Wiedervorlage  | Dringlichkeit                | Bearbeitungsstatus       |
|                        |                          |                    | Mieters                    |                |                              |                          |
+------------------------+--------------------------+--------------------+----------------------------+----------------+------------------------------+--------------------------+

Sortierung: Betreff Z–A
+------------------------+--------------------------+--------------------+----------------------------+----------------+------------------------------+--------------------------+
| Fall / Betreff ↓       | Objekt / Wohnung         | Eingang            | Letzte Aktion des          | Wiedervorlage  | Dringlichkeit                | Bearbeitungsstatus       |
|                        |                          |                    | Mieters                    |                |                              |                          |
+------------------------+--------------------------+--------------------+----------------------------+----------------+------------------------------+--------------------------+
```

Die jeweils andere Richtung ersetzt den bisherigen Pfeil. Weitere Klicks wechseln ↑/↓; beim Wechsel auf eine andere Spalte beginnt deren Sortierung mit ↑. Alle sieben Spalten sind sortierbar, auch Datum, Dringlichkeit und Bearbeitungsstatus. Nur die gewählte Spalte zeigt einen Richtungspfeil; die mehrteilige Standardsortierung aus 8.1 bleibt als eigener Zustand erkennbar. Suche und Filter bleiben erhalten, die neue Sortierung beginnt auf Seite 1.

Auf schmalen Bildschirmen übernimmt die Sortierauswahl dieselben Funktionen. Sie bietet «Standardsortierung» und jede der sieben Spalten jeweils auf- und absteigend. Ein gewählter Zustand sieht beispielsweise so aus:

```text
Sortierung: [Letzte Aktion des Mieters ↑ v]
```

### 8.4 Abgeschlossene Fälle zusätzlich eingeblendet

Der Button wechselt zwischen zwei Zuständen; er zeigt immer die nächste Aktion:

```text
Auswahl: Nur aktive Fälle
[Abgeschlossene Fälle einblenden]

                  nach dem Einschalten

Auswahl: Aktive und abgeschlossene Fälle
[Abgeschlossene Fälle ausblenden]
```

Suche, Dringlichkeitsfilter und Sortierung bleiben erhalten; der Wechsel beginnt auf Seite 1. In der gemeinsamen Liste wird ein abgeschlossener Fall mit denselben sieben Spalten angezeigt. Der folgende Ausschnitt zeigt einen solchen zusätzlichen Treffer bei passender Auswahl:

```text
+------------------------+--------------------------+--------------------+----------------------------+----------------+------------------------------+--------------------------+
| Fall / Betreff         | Objekt / Wohnung         | Eingang            | Letzte Aktion des          | Wiedervorlage  | Dringlichkeit                | Bearbeitungsstatus       |
|                        |                          |                    | Mieters                    |                |                              |                          |
+------------------------+--------------------------+--------------------+----------------------------+----------------+------------------------------+--------------------------+
| [REQ-2026-006]         | Beispielweg 9, Whg. 1    | 08.10.2026, 09:00  | 08.10.2026, 16:00          | –              | Niedrig (Grün)               | Abgeschlossen            |
| Balkonlampe defekt     |                          |                    |                            |                | Quelle: System               |                          |
+------------------------+--------------------------+--------------------+----------------------------+----------------+------------------------------+--------------------------+
```

Die Case-ID öffnet auch hier die separate Detailseite. Die Übersicht enthält weder Abschlussaktionen noch Hinweise auf interne Prüfgründe. Eine niedrige Dringlichkeit ist kein Abschlussgrund; das Beispiel stellt einen bereits fachlich abgeschlossenen Fall dar.

### 8.5 Mehrere Seiten und Änderungen bei Aktualisierung

**Beispiel mit 51 Treffern:** Auf der ersten Seite werden höchstens 50 Fälle durch Scrollen erreicht; der verbleibende Fall steht auf Seite 2. Die folgenden Ausschnitte zeigen die Seitennavigation unter derselben Tabelle.

```text
51 Treffer · Seite 1 von 2 · Fälle 1–50
[Rückwärts – deaktiviert]                        [Vorwärts]

51 Treffer · Seite 2 von 2 · Fall 51
[Rückwärts]                        [Vorwärts – deaktiviert]
```

Suche, Filter und Sortierung gelten vor der Seitenaufteilung und bleiben beim Blättern erhalten. Ein automatischer Abruf alle 20 Sekunden behält die gewählte Seite bei, solange sie existiert. Die aktuelle Leseposition bleibt beim Scrollen soweit möglich erhalten.

**Entfallene Seite:** Wenn bei unveränderter Auswahl aus drei Seiten zwei werden, folgt der Wechsel auf die letzte gültige Seite mit kurzer Erklärung. Die Textfassung konkretisiert die bestätigte Rückmeldung aus Abschnitt 5.

```text
Die bisherige Seite entfällt. Sie sehen jetzt Seite 2 von 2.
100 Treffer · Seite 2 von 2 · Fälle 51–100
[Rückwärts]                        [Vorwärts – deaktiviert]
```

**Verschobene Zeilen:** Neue Mieternachrichten oder geänderte wirksame Dringlichkeiten ordnen Zeilen gemäss gewählter Sortierung neu ein. Kein ungefragter Sprung zum Listenanfang; Fokus und Leseposition bleiben soweit möglich am bisherigen Fall. Entfällt ein fokussierter Fall, wird der Fokus kontrolliert auf den Ergebnisbereich gesetzt. Eine laufende Aktualisierung darf keinen unbeabsichtigten Klick auf einen anderen Fall auslösen. Ein abgeschlossener Fall entfällt aus der Standardauswahl; die Aktualisierung der Liste läuft weiter.

### 8.6 Leere Liste und keine Suchtreffer

**Leere Standardliste – vereinbart:** Sind keine aktiven Fälle vorhanden und keine zusätzliche Suche oder Dringlichkeitseinschränkung gesetzt, erscheint auf breiten und schmalen Bildschirmen statt der Liste:

```text
Fallübersicht                                      [Ansicht aktualisieren]
Zuletzt aktualisiert: 09.10.2026, 11:00 · automatisch alle 20 Sekunden
Volltextsuche: [_______________________________________________________]
[Abgeschlossene Fälle einblenden]    Dringlichkeit: [Alle v]
[Filter zurücksetzen]
Auswahl: Nur aktive Fälle

Heute nichts zu bearbeiten.
```

Dies gilt auch, wenn noch gar keine Fälle existieren. Der Button zum Einblenden abgeschlossener Fälle bleibt verfügbar. Die periodische Aktualisierung läuft weiter; ein neu eingegangener aktiver Fall ersetzt die Leermeldung beim nächsten erfolgreichen Abruf durch die Liste. «Heute» führt keinen Datumsfilter ein.

**Keine Treffer bei Suche oder einschränkendem Filter:** Suchtext und Auswahl bleiben sichtbar.

```text
Volltextsuche: [Heiz kalt______________________________________________]
[Abgeschlossene Fälle einblenden]    Dringlichkeit: [Kritisch v]

Keine Fälle für diese Auswahl.
[Filter zurücksetzen]
```

«Filter zurücksetzen» leert die Suche, setzt Dringlichkeit auf «Alle», blendet abgeschlossene Fälle aus, stellt die Standardsortierung wieder her und lädt Seite 1 unmittelbar. Sind abgeschlossene Fälle eingeblendet, Suche leer und Dringlichkeit «Alle», aber insgesamt keine Fälle vorhanden, lautet die Meldung dagegen «Keine Fälle verfügbar.».

### 8.7 Lade- und Aktualisierungsfehler

**Erster Abruf fehlgeschlagen:**

```text
Fallübersicht
Die Fallübersicht konnte nicht geladen werden.
[Erneut versuchen]
```

Ein fehlgeschlagener Abruf zeigt weder eine erfolgreiche leere Liste noch einen vermeintlich aktuellen Lesezeitpunkt.

**Spätere Aktualisierung fehlgeschlagen:**

```text
Fallübersicht                                      [Ansicht aktualisieren]
Aktualisierung momentan nicht möglich. Stand: 09.10.2026, 11:00

... bisherige Suche, Filter und zuletzt erfolgreich gelesene Liste ...
... bisherige Seitennavigation ...
```

Der angegebene Stand bleibt der letzte erfolgreiche Lesezeitpunkt. Vorhandene Daten und Eingaben bleiben erhalten; ein erfolgreicher Abruf entfernt den Fehlerhinweis und aktualisiert den Stand. Eine ausdrückliche serverseitige Zugriffsverweigerung ist davon zu unterscheiden: Sie stoppt die Abrufe und entfernt Falldaten aus der sichtbaren Ansicht; es erscheint ein Zugriffshinweis ohne Anmeldeaufforderung. Konkrete technische Wiederholungsabstände bleiben in Abschnitt 5 zu konkretisieren.

### 8.8 Abdeckung der bestätigten Entscheidungen

| Entscheidung | Darstellung oder zugehörige Bedienungsregel |
|---|---|
| Sieben Spalten; Betreff als Kurzbeschreibung | Desktop in 8.1 und Fallblock in 8.2; keine zusätzliche Zusammenfassung |
| Aktive Fälle als Vorgabe; unbewertete zuerst; dann Dringlichkeit und ältester letzter Mieterkontakt | Standardauswahl und Reihenfolge in 8.1/8.2; bei vollständigem Gleichstand stabile Fallreferenz gemäss Abschnitt 4 |
| Letzte Aktion des Mieters statt letzter beliebiger Änderung | Eigene Datum-/Zeitangabe; nur gespeicherte Mieter-Nachrichten bzw. ursprünglicher Eingang nach Abschnitt 3; Anzeige in Europe/Zurich |
| Alle Spalten sortierbar; erster Klick ↑, zweiter ↓ | Tabellenköpfe und schmale Sortierauswahl in 8.3 |
| Alle Dringlichkeitsstufen und Farben; System/manuelle Quelle | Vollständige Auswahl in 8.3; Quellen in 8.1; wirksame manuelle Stufe bleibt gemäss DRING-06 geschützt |
| Sofortige Filter; Volltextsuche nach Eingabepause | 8.1–8.3; Teilwörter, UND, Gross-/Kleinschreibung, Sonderzeichen und Suchumfang erläutert |
| Abgeschlossene Fälle per Button ein-/ausblenden | Beide Buttonzustände und beispielhafte Zeile in 8.4 |
| Maximal 50 Fälle, Scrollen und Vorwärts/Rückwärts | Einseitige Liste in 8.1; 51-Treffer-Beispiel in 8.5 |
| Automatische Aktualisierung alle 20 Sekunden; manuell aktualisieren | Kopfbereich in 8.1/8.2; Ausnahmen, Eingabe-/Fokuserhalt und Koordination nach Abschnitt 5 |
| Verschobene Zeilen und entfallene Seiten | 8.5; kein Sprung zum Listenanfang und erklärte Korrektur der Seite |
| Leere Standardliste, keine Suchtreffer und keine Fälle überhaupt | Unterschiedliche Meldungen in 8.6 |
| Erstabruf- und spätere Ladefehler | Getrennte Ausschnitte in 8.7; erfolgreiche Leerzustände bleiben davon unterscheidbar |
| Einheitliche Adminrechte für Mitarbeitende | Dieselbe Fall- und Memosuche für alle; keine Auswahl «Meine Fälle», Autorisierung nach Abschnitt 6 |
| Eigene Falldetailseite; keine Problemhinweise oder zusätzlichen Filter | Case-ID ist Link zu UI3; kein Detailpanel, keine Hinweisspalte, kein Hinweis- oder Bearbeitungsstatusfilter |
| Bedienung ohne JavaScript und per Tastatur | Bestehende SSR-/Zugänglichkeitsregeln aus Abschnitt 9; nur ohne JavaScript zusätzlicher Button «Suchen / Filter anwenden» |

**Ergebnis des Abgleichs:** Die bestätigten sichtbaren Elemente und Bedienungszustände sind in den Grundansichten, Zustandsausschnitten und zugehörigen Erläuterungen enthalten. Fachliche Verarbeitung wie der Schutz manueller Einstufungen bleibt an der zentralen Spezifikation verbindlich; sie erzeugt keine zusätzlichen Bedienelemente in UI2. Die Skizzen sind keine nachgewiesene Implementierung und legen weiterhin keine endgültigen Farbtöne, Schriften oder Abstände fest.

## 9. Technische Leitplanken und spätere Prüfkriterien

Thymeleaf rendert das GET-Formular, Ergebnisse und Links. Gezieltes Vanilla JavaScript ergänzt die Live-Suche nach einer Sekunde Eingabepause, sofortige Filteränderungen und die automatische Aktualisierung alle 20 Sekunden. Die Kernabläufe bleiben ohne JavaScript über den Formular-Fallback und Seitenaufrufe nutzbar. Freitext wird sicher als Text ausgegeben; Labels, Tabellenüberschriften und Links sind per Tastatur und assistiven Technologien verständlich. Status und Dringlichkeit sind textlich erkennbar.

Als Modulzuordnung wird ein fallbezogener Lesevertrag über `caseprocessing` vorgeschlagen. Der Controller nutzt Anwendungsdienste, öffentliche Modulverträge liefern benötigte Informationen; direkter Zugriff auf fremde Persistenz entfällt gemäss Modulstruktur. PropertyFlow liefert den fachlichen Stand; Camunda bleibt getrennte Hintergrundorchestrierung. Ein konkretes DTO oder Datenbankschema wird hier nicht festgelegt.

- **ÜB-AK-01:** Die Übersicht zeigt je berechtigtem Fall Referenz, Betreff als Kurzbeschreibung, Dringlichkeit bzw. eindeutig fehlende Bewertung und fachlichen Status. Die sieben Spalten aus Abschnitt 3 sind vollständig enthalten. Eine zusätzliche Zusammenfassung, eine Hinweisspalte oder fallbezogene Problemhinweise in anderen Spalten werden weder auf breiten noch auf schmalen Bildschirmen ausgegeben.
- **ÜB-AK-02:** Dringlichkeit und Status bleiben ohne Farbwahrnehmung verständlich. Farbe und Änderungsquelle folgen DRING-03/DRING-04; «System» wird nicht als menschliche Entscheidung ausgegeben. Anzeige, Filter und Sortierung verwenden die wirksame Stufe; eine abweichende Systembewertung überschreibt keine manuelle Einstufung.
- **ÜB-AK-03:** Standardmässig erscheinen nur aktive Fälle; unbewertete Fälle zuerst, innerhalb dieser Gruppe nach ältestem letztem Mieterkontakt. Bewertete Fälle werden nach Dringlichkeit und danach Letzter Aktion des Mieters (ältester Kontakt zuerst) sortiert. Alle sieben Spalten sind per Spaltentitel sortierbar: erster Klick ↑/aufsteigend, zweiter Klick ↓/absteigend, dritter Klick wieder ↑. Ein Wechsel der Spalte startet aufsteigend; nur die gewählte Spalte zeigt den Richtungspfeil und einen entsprechenden zugänglichen Sortierzustand. «Fall» sortiert nach Betreff A–Z bzw. Z–A. Suche und Filter bleiben erhalten, die Sortierung startet auf Seite 1; der Button blendet abgeschlossene Fälle zusätzlich ein und wieder aus. Suche, Filter, Sortierung, Pagination und manuelles Aktualisieren funktionieren ohne JavaScript und erhalten die Auswahl. Noch offene Sortierdetails werden vor Umsetzung ergänzt.
- **ÜB-AK-04:** Falllink öffnet den richtigen, erneut autorisierten Fall auf einer separaten Detailseite. Direktaufruf und manipulierte Parameter erweitern keine Rechte. Die Gestaltung der Rücknavigation ist Screen 03 vorbehalten.
- **ÜB-AK-05:** Eine erfolgreich geladene Standardauswahl ohne aktive Fälle zeigt «Heute nichts zu bearbeiten.» auf breiten und schmalen Bildschirmen; dies gilt auch für ein System ohne Fälle. Abgeschlossene Fälle bleiben über den Button erreichbar. Ein neuer aktiver Fall wird durch die laufende Aktualisierung sichtbar. Eine erfolglose Suche oder ein einschränkender Filter zeigt dagegen «Keine Fälle für diese Auswahl.»; vorhandene aktive Fälle ausserhalb der Auswahl werden nicht als erledigt dargestellt. Ladefehler und fehlende Zugriffsrechte erzeugen keine dieser Erfolgsmeldungen. Ein Listenfehler wird nicht als erfolgreich geladene Teilliste dargestellt.
- **ÜB-AK-06:** KI-/Mail-/Camunda-Ausfälle schliessen einen gespeicherten Fall nicht; fachlicher Status und intern gespeicherte Problemhinweise bleiben getrennt. Die Liste zeigt bei den Prüfgründen aus FALL-05 den Status «Mitarbeiterprüfung erforderlich», aber keine Problemhinweise. Details können zur manuellen Prüfung geöffnet werden.
- **ÜB-AK-07:** HTML-/Script-Inhalte in Betreff und Objektangaben bleiben Text; Liste, Zähler und Datenantworten enthalten nur berechtigte Inhalte und keine Tokengeheimnisse.
- **ÜB-AK-08:** Bei Änderung oder Abschluss zwischen Liste und Detail wird der aktuelle Stand gelesen; Screen 02 löst keine Nachrichten, E-Mails oder Prozessaktionen aus.
- **ÜB-AK-09:** Tastaturbedienung und schmale Darstellung erreichen alle Filter, Werte und Falllinks. Auf schmalen Bildschirmen erscheinen je Fall die sieben beschrifteten Angaben untereinander; Suche, Filter und die Sortierauswahl mit ↑/↓ stehen darüber. Case-ID bleibt Link. Zeitpunktdarstellung berücksichtigt Sommerzeit in Europe/Zurich.
- **ÜB-AK-10:** Texteingaben aktualisieren die Liste ohne Suchbutton nach einer Sekunde ohne weitere Textänderung; weitere Eingaben starten die Wartezeit erneut. Leeren des Suchfelds hebt die Suchbeschränkung auf. Filteränderungen wirken sofort und berücksichtigen den aktuellen Suchtext; Zurücksetzen stellt die Vorgaben sofort wieder her. Suche und Filterwechsel starten auf Seite 1. Periodische Abrufe alle 20 Sekunden erhalten dagegen die gewählte Seite und Auswahl. Debounce, Suchabruf und Polling erzeugen keine überlappenden Listenanfragen. Fokus und Eingabetext bleiben erhalten; veraltete Antworten überschreiben keine neuere Auswahl. Ein gezielter Ablauf mit schnellem Tippen, unmittelbar folgendem Filterwechsel und verspäteter Antwort prüft dies. Ohne JavaScript bleibt der Formular-Fallback nutzbar.
- **ÜB-AK-11:** Ausgeblendeter Tab, langsame Anfrage, Netzwerkfehler und Rate Limit prüfen Pausieren, höchstens eine laufende Anfrage, verzögerte Wiederholung und sichtbare Aktualisierungsfehler. Ein fehlgeschlagener Erstabruf zeigt «Die Fallübersicht konnte nicht geladen werden.» mit «Erneut versuchen». Bei fehlgeschlagener späterer Aktualisierung bleiben vorhandene Daten mit «Aktualisierung momentan nicht möglich. Stand: …» und dem letzten erfolgreichen Lesezeitpunkt sichtbar. Eine ausdrückliche serverseitige Zugriffsverweigerung stoppt dagegen die Abrufe und entfernt sichtbare Falldaten, ohne eine Anmeldeaufforderung einzuführen. Geänderte Sortierwerte ordnen Zeilen neu ein, ohne zum Listenanfang zu springen; die Leseposition bleibt soweit möglich erhalten und ein Klick darf nicht unbeabsichtigt einen anderen Fall öffnen. Ein abgeschlossener Fall entfällt aus der Standardliste, beendet aber das Listen-Polling nicht. Abrufe haben keine fachlichen Seiteneffekte und greifen nicht direkt auf Camunda zu.
- **ÜB-AK-12:** Maximal 50 Fälle erscheinen je Seite. Bei 51 passenden Fällen ist der weitere Fall über «Vorwärts» erreichbar und «Rückwärts» führt zurück. Filter und Sortierung gelten für die gesamte Treffermenge und bleiben beim Seitenwechsel erhalten. Entfällt Seite 3 von 3, wechselt die Ansicht auf Seite 2 von 2 und erklärt den Wechsel kurz. Suche, Filter und Sortierung bleiben erhalten. Bei null Treffern erscheint die zur Auswahl passende Leermeldung gemäss ÜB-AK-05; Randseiten bleiben korrekt bedienbar.
- **ÜB-AK-13:** Vollständige Wörter und Teilwörter am Wortanfang, innerhalb und am Wortende liefern den zugehörigen Fall. Gross-/Kleinschreibung wird ignoriert: «heiz», «Heiz» und «HEIZ» sowie «heiz KALT» und «HEIZ kalt» liefern bei ansonsten gleicher Auswahl jeweils dieselben Treffer. Bei mehreren Suchwörtern müssen alle als vollständiges Wort oder Teilwort im selben Fall vorkommen; sie dürfen auf Fallfelder, veröffentlichte externe Nachrichten und gespeicherte interne Memos verteilt sein oder im selben Wort vorkommen. «Heiz ausfall» findet «Heizungsausfall». «Heiz kalt» findet «Heizung» im Betreff zusammen mit «kalt» in einer veröffentlichten Nachricht oder einem gespeicherten Memo. Fehlt «kalt» oder steht es ausschliesslich in einem externen Entwurf, wird dieser Fall nicht gefunden. Suchwörter in unterschiedlichen Fällen werden nicht zu einem Treffer kombiniert. Mehrere Fundstellen erzeugen keinen doppelten Fall. Suche, Filter und Sortierung werden vor Pagination angewandt und bei automatischer Aktualisierung erhalten. Sonderzeichen werden in Eingabe und Inhalten gleichermassen ignoriert: «REQ2026001» findet «REQ-2026-001», «Heiz, kalt!» liefert dieselben Treffer wie «Heiz kalt». Reine Sonderzeicheneingaben wirken wie eine leere Suche. Leerzeichen bleiben Suchworttrenner; ein nach Bereinigung verbleibendes fehlendes Suchwort schliesst den Fall weiterhin aus. Originaltexte bleiben unverändert.
- **ÜB-AK-14:** Zwei Mitarbeitende finden bei gleicher Auswahl dieselben Fälle und internen Memos. Die Ansicht verlangt keine Anmeldung und enthält keine Abmeldeaktion. Ein ausschliesslich in einem externen Entwurf vorhandener Marker erzeugt keinen Suchtreffer. Mieter und Aufrufer ohne Mitarbeiterberechtigung erhalten auch bei direkten Suchaufrufen keine Treffer, Zähler oder Fundstellen; das technische Schutzverfahren folgt ZUG-04. Suche erweitert keine zulässigen Kommunikations-LLM-Grundlagen.
- **ÜB-AK-15:** Die Spalte «Letzte Aktion des Mieters» zeigt im vereinbarten Zeitmodell die letzte gespeicherte Mieter-Nachricht bzw. ohne weitere Nachricht die Einreichung. Seitenaufrufe, interne Memos und System-/Verwaltungsnachrichten verändern den Wert nicht. Neue Mieter-Nachrichten aktualisieren ihn beim nächsten erfolgreichen Abruf; Datumssortierung funktioniert in beide Richtungen.
- **ÜB-AK-16:** Der Dringlichkeitsfilter bietet «Alle», «Kritisch», «Hoch», «Normal», «Niedrig» und «Noch nicht bewertet». Standard ist «Alle». Die Auswahl wirkt zusammen mit Volltextsuche und dem Ein-/Ausblenden abgeschlossener Fälle, beginnt auf Seite 1 und bleibt bei Sortierung, Seitenwechsel und automatischer Aktualisierung erhalten. «Filter zurücksetzen» stellt «Alle» wieder her.

- **ÜB-AK-17:** Die zusätzliche Spalte «Wiedervorlage» ist auf breiten und schmalen Bildschirmen sichtbar und in beiden Richtungen sortierbar. Aufsteigend stehen frühere vor späteren Terminen, absteigend umgekehrt; Fälle ohne Termin stehen in beiden Richtungen zuletzt und zeigen «–». Gleiche Termine bleiben über die eindeutige Fallreferenz stabil sortiert. Sortieren berücksichtigt die gesamte Treffermenge vor der Seiteneinteilung. Setzen, Ändern, automatische Vorverlegung und Löschen in UI3 werden beim nächsten erfolgreichen Abruf konsistent in Termin, Status und gewählter Reihenfolge angezeigt. Die Standardsortierung bleibt erhalten, solange keine Spaltensortierung gewählt wird.

Diese Kriterien werden nach Abstimmung bei der späteren Umsetzung geprüft. Für diese Dokumentänderung werden keine Anwendungstests oder Messwerte als ausgeführt behauptet.

### FFHS-Ausbildungskontext und spätere Nachweise

Das am 09.10.2026 übermittelte, von Marco autorisierte Kontextpaket ergänzt die Repositoryquellen. Grundlage dieser Einordnung ist die übermittelte geprüfte Zusammenfassung, keine in diesem Arbeitsschritt vorgenommene Prüfung der Original-PDFs. Die Originalreferenzen liegen schreibgeschützt ausserhalb des Repositorys unter `C:\Users\MarcoPertegato\.codex\.chatgpt-projects\g-p-6a81d62a64508191b7123e66bc7eb807\sources\`: `PropertyFlow.txt`, `Modulinformatinen.pdf`, `modulplan-W4B-C-AS001.AISE.ZH-Sa-1.PVA.HS26_27.pdf` und `hinweise-anforderungen-ki-studierende.pdf`. Sie werden nicht ins Repository kopiert oder verändert.

Für Block 2 sind Struktur, Verhalten und Interaktion der wichtigsten Screens zu spezifizieren. Der technische Wireframe bleibt deshalb zusammen mit der UI-Spezifikation in dieser Datei. Das Ausbildungsbeispiel SupportFlow begründet keine zusätzlichen PropertyFlow-Kategorien, Konfidenzfelder, Freigabe- oder Reportingfunktionen. Die geltenden PropertyFlow-ADRs und der vereinbarte Produktumfang bleiben massgeblich; Streaming-Lerninhalte ändern die asynchrone Mieteransicht nicht.

Bei der späteren Implementierung gegen statische Testdaten sind A11y, sichere Inhaltsausgabe und ein konkret vereinbartes Performance-Budget zu prüfen und mit tatsächlichen Ergebnissen zu dokumentieren. Generierte Unit-Tests, konkrete Korrektur-Diffs und übernommene oder verworfene KI-Vorschläge werden nachvollziehbar festgehalten. Projektkontext v0.2 mit UI-, A11y- und Sicherheitskonventionen ist ein späteres Block-2-Arbeitsergebnis; dieser Entwurf aktualisiert ihn noch nicht.

Entscheidungen, fachliche Prüfung und Verantwortung verbleiben bei Marco. KI als Entwicklungswerkzeug und KI als Produktfunktion werden in der späteren Hilfsmittel-/Quelldeklaration unterschieden. Noch nicht besprochene Vorschläge gelten nicht als übernommen; Veto-, Test- und Messnachweise werden nicht erfunden. Konkrete Abgabetermine werden aus den relativen Modulmeilensteinen nicht abgeleitet.

## 10. Vereinbarter Stand und verbleibende Konkretisierung

| Thema | Vereinbarter Stand |
|---|---|
| Liste und Detail | **Vereinbart:** Eigene Mitarbeiter-Falldetailseite als dritter Screen; kein gleichzeitiges Detailpanel. Rücknavigation wird bei Screen 03 besprochen. |
| Spaltenumfang | **Vereinbart:** Die sieben Spalten aus Abschnitt 3; Betreff als Kurzbeschreibung ohne zusätzliche Zusammenfassung. Keine Hinweisspalte oder fallbezogenen Problemhinweise in der Liste. |
| Standardauswahl und Reihenfolge | **Vereinbart:** Nur aktive Fälle, höchste Dringlichkeit zuerst, danach Letzte Aktion des Mieters, ältester Kontakt zuerst. Noch nicht bewertete Fälle stehen vor allen bewerteten Fällen, innerhalb dieser Gruppe ebenfalls ältester letzter Mieterkontakt zuerst. |
| Spaltensortierung | **Vereinbart:** Klick auf den Spaltentitel: zuerst aufsteigend ↑, danach absteigend ↓, weitere Klicks wechseln die Richtung. «Fall» nach ursprünglichem Betreff. Weitere Sortierwerte und technischer Textvergleich folgen Abschnitt 4. |
| Abgeschlossene Fälle | **Vereinbart:** Per Button zusätzlich einblenden und wieder ausblenden. |
| Bearbeitungsstatus-Filter | **Vereinbart:** Wird nicht aufgenommen; Anzeige und Spaltensortierung bleiben erhalten. |
| Suche und weitere Filter | **Vereinbart:** Volltextsuche ausschliesslich in der Mitarbeiterliste, auch in veröffentlichten Nachrichten und allen gespeicherten internen Memos; vollständige Wörter und Teilwörter ohne Unterscheidung der Gross-/Kleinschreibung und unter Ignorieren von Satz- und Sonderzeichen, bei mehreren Suchwörtern alle innerhalb desselben Falls (UND). Automatische Suche nach kurzer Eingabepause, hier auf 1 Sekunde konkretisiert; Filteränderungen wirken sofort. Dringlichkeitsfilter mit «Alle», den vier Stufen und «Noch nicht bewertet». Kein Hinweisfilter. Externe Entwürfe sind ausgeschlossen. Weitere technische Suchdetails und Trefferanzeige folgen Abschnitt 4. |
| Aktualisierung | **Vereinbart:** Automatisch alle 20 Sekunden über PropertyFlow; manuelles Aktualisieren bleibt verfügbar. Konkretes Verhalten bei Listenänderungen siehe Abschnitt 5. |
| Ergebnisumfang | **Vereinbart:** Maximal 50 Fälle je Seite, Scrollen innerhalb der Seite und «Vorwärts»/«Rückwärts» für weitere Treffer. |
| Leere Standardliste | **Vereinbart:** «Heute nichts zu bearbeiten.» bei erfolgreich geladener Standardauswahl ohne aktive Fälle. Suche oder einschränkende Filter ohne Treffer erhalten eine eigene Meldung. |
| Schmaler Bildschirm | **Vereinbart:** Fallblöcke mit sieben beschrifteten Angaben untereinander; Suche, Filter und Sortierauswahl mit ↑/↓ darüber. |
| Verschobene Zeilen und entfallene Seiten | **Vereinbart:** Gewählte Sortierung nachführen, kein Sprung zum Listenanfang, Leseposition soweit möglich erhalten. Entfällt die Seite, auf die letzte gültige Seite wechseln und den Wechsel erklären; bei null Treffern passende Leermeldung. |
| Ladefehler | **Vereinbart:** Erstabruf mit Fehlermeldung und «Erneut versuchen»; bei späterem Fehler vorhandene Daten mit «Aktualisierung momentan nicht möglich. Stand: …» erhalten. |

**Fachlicher Abschluss am 09.10.2026:** Die Listenfunktionen, Suche, Filter, Sortierung und Bedienungsregeln sind festgelegt, einschliesslich schmaler Ansicht, verschobener Zeilen, entfallener Seiten und Ladefehler. Für die Umsetzung verbleiben technischer Textvergleich, Such- und Leseverträge, Parametergrenzen und Wiederholungsabstände. Die genaue visuelle Gestaltung folgt später. Diese Konkretisierungen öffnen die bestätigten Listenentscheidungen nicht erneut.

Die [Dringlichkeitsbewertung](../specifications/dringlichkeitsbewertung.md) führt Stufen, Farben, Bewertungsablauf, Vorrang manueller Anpassungen und transparente Historie. [FALL-07](../specifications/fallverwaltung.md#fall-07-abschluss-und-zeit-danach) erlaubt den Abschluss durch Mitarbeitende sowie durch das System bei bestätigter Behebung ohne weiteren Hilfebedarf oder eindeutig fehlendem Bearbeitungsbedarf der Verwaltung nach Fachregel. Offene menschliche Entscheidungen nach [FALL-10](../specifications/fallverwaltung.md#fall-10-automatisierung-und-menschliche-freigabe) bleiben verbindlich. Beide Abschlusswege liefern den Status «Abgeschlossen» und wirken gleich auf die Listenauswahl: standardmässig ausblenden, über den Button weiterhin einsehbar. Eine Abschlussaktion in der Liste ist nicht vorgesehen.

Zentral offen bleiben die technische Synchronisierung konkurrierender Bewertungen, konkrete Prozessübergänge und die technische Konkretisierung der vereinbarten Abschlussregeln. Entscheidungen dazu werden an der jeweiligen fachlichen Quelle ergänzt und hier verlinkt. Die [Mitarbeiter-Falldetailansicht](ansicht-03-mitarbeiter-falldetail.md) ist als UI3 fachlich spezifiziert, einschliesslich Verlauf links, Falldaten und Aktionen rechts sowie Rücknavigation mit erhaltener Listenauswahl. Die drei UI-Spezifikationen wurden mit den bestätigten Fachregeln abgeglichen. Gemeinsame stilistische JPG-Entwürfe und Anwendungscode folgen separat.
