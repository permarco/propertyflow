# Use Case: Mieteranliegen anzeigen

## Overview

**Use Case ID:** UC-002  
**Use Case Name:** Mieteranliegen anzeigen  
**Primary Actor:** Immobilienbewirtschaftung  
**Goal:** Fälle in der Mitarbeiter-Fallübersicht sichten, suchen, filtern und sortieren sowie einen Fall zur Detailprüfung öffnen.

**Status:** Planned

**Darstellung und Bedienung:** [Screen 02 – Mitarbeiter-Fallübersicht](../frontend/ansicht-02-mitarbeiter-falluebersicht.md) führt die vereinbarten Listenfunktionen und den technischen Wireframe. Die Umsetzung steht noch aus.

## Preconditions

- Die Mitarbeiterin oder der Mitarbeiter nutzt UI2 ohne Anmeldung gemäss ZUG-04.
- Alle Mitarbeitenden haben dieselben Adminrechte und Zugriff auf sämtliche Fälle und internen Memos gemäss ZUG-04.
- Im System können null, ein oder mehrere Fälle vorhanden sein.

## Main Success Scenario

1. Die Mitarbeiterin oder der Mitarbeiter öffnet die Fallübersicht.
2. PropertyFlow autorisiert den Zugriff und lädt standardmässig nur aktive Fälle.
3. Die Liste zeigt die sieben Spalten Fall (Case-ID und ursprünglicher Betreff), Objekt/Wohnung, Eingang, Letzte Aktion des Mieters, Wiedervorlage, Dringlichkeit und Bearbeitungsstatus. Eine zusätzliche Zusammenfassung sowie die Anzeige fallbezogener Problemhinweise entfallen.
4. Noch nicht bewertete Fälle erscheinen zuerst. Danach folgen bewertete Fälle nach absteigender Dringlichkeit. Innerhalb gleicher Bewertung steht der älteste letzte Mieterkontakt zuerst; die eindeutige Fallreferenz stabilisiert vollständige Gleichstände.
5. Bei Bedarf nutzt die Mitarbeiterin oder der Mitarbeiter die Volltextsuche, den Dringlichkeitsfilter, den Button für abgeschlossene Fälle oder die auf-/absteigende Sortierung einer beliebigen Spalte. Texteingaben lösen die Suche nach einer Sekunde Eingabepause aus; Filteränderungen wirken sofort. In der normalen Bedienung ist kein Such- oder Anwenden-Button erforderlich.
6. PropertyFlow wendet Suche, Filter und Sortierung auf die gesamte ausgewählte Treffermenge an und zeigt höchstens 50 Fälle je Seite. Weitere Treffer sind über «Vorwärts» und «Rückwärts» erreichbar.
7. Die sichtbare Liste aktualisiert gespeicherte Änderungen zusätzlich alle 20 Sekunden über PropertyFlow. Aktuelle Auswahl, Seite, Sucheingabetext, Fokus und Leseposition folgen dem Verhalten aus Screen 02. Die Live-Suche wartet nicht auf diesen periodischen Abruf. Manuelles Aktualisieren bleibt verfügbar.
8. Die Mitarbeiterin oder der Mitarbeiter öffnet einen Fall über die Case-ID. PropertyFlow autorisiert den Aufruf erneut und öffnet die separate Mitarbeiter-Falldetailseite gemäss UC-003.

## Alternative Flows

### A1: Keine Fälle oder keine passenden Treffer

**Trigger:** Es gibt keine Fälle oder die aktuelle Auswahl liefert keine Treffer.

1. Bei erfolgreich geladener Standardauswahl ohne aktive Fälle erscheint «Heute nichts zu bearbeiten.» statt der Liste. Standardauswahl bedeutet: keine Suche, Dringlichkeit «Alle» und abgeschlossene Fälle ausgeblendet. Dies gilt auch, wenn noch gar keine Fälle existieren; ein Datumsfilter wird nicht eingeführt.
2. Liefert eine Suche oder ein einschränkender Filter keine Treffer, erscheint «Keine Fälle für diese Auswahl.»; Suche und Filter bleiben erhalten, Zurücksetzen wird angeboten. Sind abgeschlossene Fälle eingeblendet und existieren insgesamt keine Fälle, erscheint «Keine Fälle verfügbar.».
3. Gibt es keine aktiven Fälle, bleibt der Button zum Einblenden abgeschlossener Fälle verfügbar. Der Leerzustand behauptet nicht, dass überhaupt keine Fälle existieren. Die automatische Aktualisierung läuft weiter und zeigt neu hinzugekommene aktive Fälle an.

### A2: Liste kann nicht geladen oder aktualisiert werden

**Trigger:** Der erste Listenabruf oder ein späterer Aktualisierungsabruf scheitert technisch.

1. Beim fehlgeschlagenen Erstabruf zeigt das System «Die Fallübersicht konnte nicht geladen werden.» und bietet «Erneut versuchen» an.
2. Bei gescheitertem Erstabruf wird keine erfolgreiche leere oder unvollständige Liste vorgetäuscht.
3. Bei gescheiterter späterer Aktualisierung bleiben zuvor gelesene Daten mit «Aktualisierung momentan nicht möglich. Stand: …» und dem letzten erfolgreichen Lesezeitpunkt sichtbar. Die Wiederholung folgt Screen 02; konkrete Wartezeiten bleiben technisch zu bestimmen.

### A3: Abgeschlossene Fälle ein- oder ausblenden

1. Der Button nimmt abgeschlossene Fälle zusätzlich in die Auswahl auf oder schliesst sie wieder aus.
2. Suche, Dringlichkeitsfilter und Sortierung bleiben erhalten; die Auswahl beginnt auf Seite 1.
3. Der Buttontext zeigt die jeweils nächste Aktion an.

### A4: Geänderte Fälle oder entfallene Seite

1. Ein neuer Status, Mieterkontakt oder eine wirksame Dringlichkeitsänderung ordnet Fälle entsprechend der gewählten Sortierung neu ein. Die Ansicht springt nicht zum Listenanfang; die Leseposition bleibt soweit möglich erhalten. Eine Aktualisierung darf keinen unbeabsichtigten Klick auf einen anderen Fall verursachen.
2. Ein abgeschlossener Fall entfällt bei der Standardauswahl. Sein Abschluss beendet die Aktualisierung der übrigen Liste nicht.
3. Entfällt die gewählte Seite, wechselt die Ansicht auf die letzte gültige Seite derselben Auswahl und erklärt dies kurz, beispielsweise von Seite 3 von 3 auf Seite 2 von 2. Suche, Filter und Sortierung bleiben erhalten; bei null Treffern erscheint die zur aktuellen Auswahl passende Leermeldung aus A1.

### A5: Zugriff wird verweigert

1. Aufrufer ohne Mitarbeiterberechtigung und Mieter erhalten keine Fallliste, Suchtreffer oder Zähler.
2. Bei einer ausdrücklichen serverseitigen Zugriffsverweigerung enden automatische Abrufe; Falldaten werden aus dem sichtbaren Ergebnisbereich entfernt. Ein normaler Ladefehler ist davon zu unterscheiden.
3. Es erscheint ein Zugriffshinweis ohne Anmeldeaufforderung. Eine Mitarbeiteranmeldung und ein Ablauf für deren Erneuerung sind nicht vorgesehen; die technische Trennung zum Mieterzugang folgt der offenen Konkretisierung in ZUG-04.

## Postconditions

### Success Postconditions

- Die gewählte Treffermenge ist mit dem zuletzt erfolgreich gelesenen Stand sichtbar.
- Nach Auswahl eines Eintrags ist dessen separate Detailansicht geöffnet.
- Listenaufrufe verändern keine fachlichen Daten und lösen keine KI-/Camunda-Verarbeitung oder E-Mail aus.

### Failure Postconditions

- Fehler werden nicht als erfolgreiche leere oder unvollständige Liste dargestellt.
- Fehlender Zugriff gibt keine Falldaten oder Trefferzahlen frei.

## Business Rules

### BR-001: Dringlichkeit und Bearbeitungsstatus

Stufen, Farben, wirksame Bewertung und Änderungsquelle folgen [DRING-01 bis DRING-07](../specifications/dringlichkeitsbewertung.md). Die Darstellung bleibt auch ohne Farbe verständlich. Eine manuelle Einstufung bleibt für Anzeige, Filter und Sortierung massgeblich und darf nicht durch eine Systembewertung überschrieben werden.

Die acht Bearbeitungsstatus und die intern gespeicherten Gründe für «Mitarbeiterprüfung erforderlich» folgen [FALL-05](../specifications/fallverwaltung.md#fall-05-fachlicher-lebenszyklus). «Aktiv» ist die Gruppe der ersten sieben Status, kein zusätzlicher anzuzeigender Bearbeitungsstatus. Die Liste zeigt den Bearbeitungsstatus, aber keine fallbezogenen Problemhinweise. Technische Analyse- oder Mailzustände werden nicht als Dringlichkeitsstufen behandelt.

### BR-002: Volltextsuche und Filter

Die Suche gehört ausschliesslich zur Mitarbeiterliste und umfasst Falldaten, gespeicherte veröffentlichte externe Nachrichten sowie sämtliche gespeicherten internen Memos. Externe Entwürfe, ungespeicherte Eingaben und KI-Teilantworten sind ausgeschlossen. E-Mail-Zustellung ist keine Voraussetzung für die Durchsuchbarkeit einer veröffentlichten Nachricht. Mehrere Fundstellen desselben Falls ergeben nur einen Listeneintrag.

Die Suche findet vollständige Wörter und Teilwörter und ignoriert dabei Gross-/Kleinschreibung. Bei mehreren Suchwörtern müssen alle innerhalb desselben Falls vorkommen (UND-Verknüpfung); jedes darf ein Teilwort sein, unabhängig von seiner Gross-/Kleinschreibung. Die Fundstellen dürfen auf verschiedene zulässige Felder, Nachrichten und interne Memos verteilt sein oder im selben Wort liegen. Ein fehlendes Suchwort schliesst den Fall aus. Satz- und Sonderzeichen werden in Eingabe und durchsuchbaren Inhalten ignoriert; Leerzeichen bleiben Suchworttrenner, Buchstaben und Ziffern bleiben erhalten. «REQ2026001» findet «REQ-2026-001», «Heiz, kalt!» entspricht «Heiz kalt». Eine Eingabe ohne verbleibende Suchwörter wirkt wie eine leere Suche. Originaltexte und Suchumfang bleiben erhalten. Beispiele und Prüfkriterien stehen in Screen 02, Abschnitt 4 und ÜB-AK-13.

Der Dringlichkeitsfilter bietet «Alle», «Kritisch», «Hoch», «Normal», «Niedrig» und «Noch nicht bewertet». Es gibt keinen zusätzlichen Bearbeitungsstatus- oder Hinweisfilter. Suche und Filter wirken gemeinsam; abgeschlossene Fälle gehören nur bei eingeschaltetem Button zum Suchumfang. Interne Suchbarkeit erweitert keine LLM-Kommunikationsgrundlage nach KOM-02.

### BR-003: Sortierung, Zeitmodell und Seiten

Standardsortierung und Sortierfunktion aller sieben Spalten folgen Screen 02. Der erste Klick auf einen Spaltentitel sortiert aufsteigend und zeigt ↑, der zweite Klick absteigend und zeigt ↓. Weitere Klicks wechseln die Richtung; bei einer anderen Spalte beginnt die Sortierung aufsteigend. Nur der aktiv gewählte Spaltentitel zeigt den Richtungspfeil. «Fall» sortiert nach ursprünglichem Betreff A–Z bzw. Z–A. Die Sortierauswahl erhält Suche und Filter und beginnt auf Seite 1. «Letzte Aktion des Mieters» verwendet den Zeitvertrag nach FALL-08, nicht den letzten beliebigen Fall- oder Systemzugriff. Suche und Sortierung gelten vor der Aufteilung in Seiten mit maximal 50 Fällen. Weitere Sortierwerte folgen Screen 02; der genaue technische Textvergleich wird bei der Umsetzung konkretisiert; Teilwortsuche und UND-Verknüpfung sind vereinbart.

Die zusätzliche Spalte «Wiedervorlage» folgt FALL-11. Sie zeigt das gespeicherte Datum oder «–», wenn kein Termin gesetzt ist. Sie ist auf- und absteigend sortierbar; Fälle ohne Termin stehen gemäss Screen 02 in beiden Richtungen zuletzt. Neue oder geänderte Termine, automatische Vorverlegungen und Löschungen werden mit der Liste aktualisiert. Bearbeitung und Löschen des Termins erfolgen in UI3.

### BR-004: Aktualisierung und Rechte

Die schmale Darstellung ist vereinbart: Jeder Fall erscheint als kompakter Block mit den sieben beschrifteten Angaben untereinander. Suche, Filter und eine Sortierauswahl mit ↑/↓ stehen darüber. Case-ID bleibt Link; die Grundfunktionen entsprechen der breiten Ansicht.

PropertyFlow liefert den gespeicherten fachlichen Stand und prüft jeden Zugriff; der Browser liest Camunda nicht direkt. Live-Suche, sofortige Filteranwendung und periodische Aktualisierung werden gemeinsam koordiniert, damit keine überholte Antwort die aktuelle Auswahl überschreibt. Ohne JavaScript bleiben Suche und Filter über einen Formularbutton als Fallback nutzbar; Sortierung, Seitennavigation und manuelles Aktualisieren erfolgen über SSR-Seitenaufrufe. Pausen, Fehlerbehandlung und Eingabeerhalt folgen Screen 02.

## Gemeinsame fachliche Grundlage

- [Fallverwaltung](../specifications/fallverwaltung.md): Fallidentität, acht Status, Problemhinweise, letzter Mieterkontakt und Abgrenzung zum technischen Workflow.
- [Dringlichkeitsbewertung](../specifications/dringlichkeitsbewertung.md): Stufen und Farben, Erst-/Neubewertung, Vorrang manueller Einstufung und Historie.
- [Fallzugriff und Sicherheit](../specifications/fallzugriff-und-sicherheit.md): Einheitliche Mitarbeiter-Adminrechte und Trennung vom Mieterzugriff.
- [Fallkommunikation](../specifications/fallkommunikation.md): Nachrichten-/Memo-Sichtbarkeit, Suchumfang und Kommunikations-LLM-Grenze.
- [Benachrichtigungen und Zustellung](../specifications/benachrichtigungen-und-zustellung.md): Versandhinweise und fachliche Benachrichtigungsauslöser.

Konkrete Prozessübergänge und das BPMN-Mapping bleiben an den zentralen Quellen zu konkretisieren. Die fachliche Bedienung des Mitarbeiterdetails einschliesslich Rückkehr mit erhaltener Suche, Filterung, Sortierung und zuletzt gewählter Seite ist in Screen 03 bestätigt. Visuelle Ausgestaltung und technische Umsetzung folgen separat.
