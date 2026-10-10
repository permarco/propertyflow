# Use Case: Externe Nachricht mit KI formulieren

## Overview

**Use Case ID:** UC-004
**Use Case Name:** Externe Nachricht mit KI formulieren
**Primary Actor:** Immobilienbewirtschaftung
**Goal:** Aus einer kurzen fachlichen Mitarbeitereingabe einen freundlichen externen Nachrichtenvorschlag erzeugen, prüfen und bewusst senden.
**Status:** Planned
**Fachlich präzisiert:** 10.10.2026

## Geltungsbereich

Die automatische Fallanalyse und Dringlichkeitsbewertung erfolgen im Camunda-Fallprozess. Der Mitarbeiter startet in UI3 keine separate Fallanalyse. Die Ergebnisse der automatischen Verarbeitung liegen zur Mitarbeiterbearbeitung bereits vor; bei Fehlern bleiben Speicherung und manuelle Bearbeitung gemäss FALL-05 möglich.

Streaming und wirksamer Abbruch betreffen in UI3 die KI-Formulierung einer externen Nachricht. Der Abbruch beendet diesen Formulierungsversuch, nicht den führenden Camunda-Fallprozess. SSR und gezieltes JavaScript folgen ADR-003; die dort bisher als Analyse bezeichnete interaktive Funktion wird fachlich als Formulierungshilfe konkretisiert.

## Preconditions

- Der Mitarbeiter befindet sich ohne vorgängige Anmeldung in UI3 eines aktiven Falls gemäss ZUG-04.
- Die Eingabe ist als extern ausgewählt («Intern» ausgeschaltet).
- In der Fallansicht läuft keine Nachrichten- oder Memo-Speicherung; währenddessen sind Eingabe, KI-Start und Vorschlagsübernahme gesperrt.
- Eine kurze, fachlich geprüfte Aussage ist eingegeben, beispielsweise «Wir haben den Techniker aufgeboten». Bei leerem oder ausschliesslich aus Leerzeichen bestehendem Text ist «Mit KI formulieren» deaktiviert; der Server lehnt einen solchen Generierungsversuch ebenfalls ab.
- Der Kommunikationskontext entspricht KOM-02: Das System übernimmt keine internen Memos oder automatisch daraus abgeleiteten Inhalte, unabhängig vom Memo-Absender. Bewusst eingegebene kurze Sachanweisungen des Mitarbeiters sind zulässig, auch wenn dieselbe Information bereits in einem Memo steht.

## Main Success Scenario

1. Der Mitarbeiter fordert zur kurzen Eingabe einen KI-Nachrichtenvorschlag an.
2. Das System zeigt «Wartet»; sobald erste Teile eintreffen, erscheint die Formulierung als Text im Stream.
3. Der Vorschlag erscheint als ungespeicherter Stream unter dem Eingabefeld; die Mitarbeitereingabe bleibt erhalten und während Warten und Streaming bearbeitbar, sofern keine Nachrichten- oder Memo-Speicherung läuft. Änderungen verändern den laufenden KI-Kontext nicht. Nach vollständiger Ausgabe kann der Mitarbeiter ihn ausserhalb einer laufenden Speicherung bewusst mit «Vorschlag übernehmen» ins Eingabefeld übernehmen; dies ersetzt den zu diesem Zeitpunkt aktuellen Eingabetext. Ohne Übernahme wird nichts automatisch ersetzt.
4. Der Mitarbeiter prüft den Text und kann ihn vor dem Senden bearbeiten.
5. Erst die bewusste Aktion «Nachricht senden» speichert und veröffentlicht die externe Nachricht nach den serverseitigen Fachprüfungen. Bis zum Ende der Speicherung sind die Nachrichten-Eingabe und die zugehörigen Bedienelemente nach UI3 gesperrt.
6. Die veröffentlichte Nachricht erscheint im Fallverlauf und beim Mieter; BEN-01 veranlasst die E-Mail-Benachrichtigung.

Bestätigte Bedienung in UI3: «Mit KI formulieren» steht neben dem gemeinsamen Eingabefeld zusammen mit «Intern» und ist bei interner Auswahl deaktiviert. Der Stream erscheint darunter; «Abbrechen» ist während Warten und Streaming verfügbar, «Vorschlag übernehmen» erst nach vollständiger Ausgabe. Übernahme und Abbruch persistieren oder veröffentlichen nichts. Dieser Use Case führt keine Entwurfsspeicherung ein.

## Alternative Flows

### A1: Langsame Antwort

Das System bleibt in «Wartet». Der Mitarbeiter kann den Formulierungsversuch wirksam abbrechen.

### A2: Abbruch

Während Warten oder Streaming kann der Mitarbeiter abbrechen. Danach werden keine weiteren Antwortteile verarbeitet oder angezeigt; der Zustand ist «Abgebrochen». Es wird keine Nachricht gespeichert oder veröffentlicht. Die aktuelle Mitarbeitereingabe einschliesslich zwischenzeitlicher Bearbeitungen bleibt erhalten; ein abgebrochener Teiltext kann nicht als vollständiger Vorschlag übernommen werden. Ein neuer Formulierungsversuch bleibt möglich. Gespeicherte Fallinhalte bleiben erhalten.

### A3: Formulierungsfehler

**Bestätigt am 10.10.2026:** Das System zeigt «Die Nachricht konnte nicht formuliert werden.» Die aktuelle Mitarbeitereingabe und gespeicherte Fallinhalte bleiben erhalten. Der Mitarbeiter kann erneut «Mit KI formulieren» wählen oder den Text manuell bearbeiten und mit «Nachricht senden» veröffentlichen, sofern die geltenden Fachregeln dies zulassen. Es wird nichts automatisch gesendet. Dieser Fehler ist keine fehlgeschlagene automatische Fallanalyse; die in FALL-05 definierte Behandlung von Analysefehlern gilt weiterhin für die Analyse im Camunda-Prozess.

### A4: Markup in der Ausgabe

Benutzer- und KI-Inhalte werden als nicht vertrauenswürdiger Text dargestellt; HTML und Skripte werden nicht ausgeführt.

### A5: Zwischenzeitlicher Abschluss

Die Nachrichtensperre wird beim Senden serverseitig geprüft. Ein verspäteter KI-Vorschlag darf keine Nachricht im abgeschlossenen Fall speichern oder veröffentlichen. **Bestätigt am 10.10.2026:** Ein während der Formulierung automatisch erkannter Status- oder Dringlichkeitswechsel zeigt in UI3 ein Popup, beendet aber den Stream nicht. Mitarbeitereingabe und KI-Ausgabe bleiben erhalten, auch bei zwischenzeitlichem Abschluss. Die erhaltene Ausgabe berechtigt nicht zum Senden im abgeschlossenen Zustand.

### A6: Umschalten auf Intern während der Formulierung

**Bestätigt am 10.10.2026:** Der bereits gestartete Formulierungs-Stream läuft beim Umschalten auf «Intern» weiter. Bei interner Auswahl sind «Mit KI formulieren» und «Vorschlag übernehmen» deaktiviert. Ein laufender Versuch bleibt wirksam abbrechbar. Nach Zurückschalten auf extern ist ein vollständiger Vorschlag wieder übernehmbar. Der Versuch verwendet weiterhin ausschliesslich seine beim Start geprüfte externe Eingabe und den zulässigen Kontext; spätere interne Eingaben gelangen nicht in den laufenden KI-Aufruf. KOM-02 bleibt unverändert wirksam.

### A7: Eingabesperre während der Speicherung

Die Sperre nach [UI3](../frontend/ansicht-03-mitarbeiter-falldetail.md#rückmeldung-nach-senden-oder-memo-speicherung) gilt auch bei gleichzeitig laufender KI-Formulierung: Textfeld, «Intern», «Mieterantwort erforderlich», KI-Start, Vorschlagsübernahme und Sende-/Speicheraktion sind bis zum Ende der Nachrichten- oder Memo-Speicherung deaktiviert. Der bestehende Stream läuft weiter; auch sein Abschluss oder Fehler darf die Eingabe nicht vorzeitig freigeben oder ersetzen. Nach erfolgreicher Speicherung werden Text und Auswahl wie vereinbart zurückgesetzt; nach einem Speicherfehler bleiben sie erhalten. Anschliessend gelten die Bedieneinschränkungen des aktuellen Fall- und KI-Zustands. FD-AK-14 und FD-AK-16 prüfen dieses Zusammenspiel.

## Business Rules und Prüfkriterien

- **BR-001:** Zustände: bereit, wartet, Streaming, vollständig, abgebrochen oder fehlgeschlagen. Pro Fallansicht höchstens ein gleichzeitig laufender Formulierungsversuch. Während Warten und Streaming ist «Mit KI formulieren» deaktiviert; ein erneuter Start ist erst nach Abschluss, wirksamem Abbruch oder Fehler und nur bei externer Auswahl ausserhalb einer laufenden Nachrichten- oder Memo-Speicherung möglich.
- **BR-002:** Ein wirksamer Abbruch beendet den Formulierungsversuch und hat keine automatische Wirkung auf Fallstatus oder Camunda-Fallprozess.
- **BR-003:** Kurzeingabe, KI-Teilantwort und vollständiger Vorschlag werden nicht als Nachrichtenentwürfe persistiert, nicht im Verlauf veröffentlicht und nicht durchsucht.
- **BR-004:** Ein vollständiger Vorschlag ist keine automatische Veröffentlichung, Dringlichkeitsänderung, Freigabe, Beauftragung oder Abschlussentscheidung.
- **BR-005:** Die KI formuliert die vorgegebene Aussage, ohne neue Tatsachen, Zusagen oder verbindliche Aufträge hinzuzuerfinden. «Techniker aufgeboten» beschreibt eine bereits fachlich autorisierte Handlung; die Formulierungshilfe beauftragt keinen Techniker und umgeht FALL-10 nicht.
- **BR-006:** KOM-02 gilt vor jedem Generierungsversuch und bei Wiederholungen. Das System lädt keine Mitarbeiter- oder Systemmemos und keine daraus erzeugten Zusammenfassungen oder Fakten zur Kommunikationsgenerierung nach. Der Mitarbeiter darf bewusst eine kurze externe Sachanweisung eingeben, auch wenn deren Sachverhalt in einem Memo steht. «Techniker kommt am Dienstag. Bitte Zugang zur Wohnung ermöglichen.» ist zulässig; nur im Memo enthaltene Kostenfreigaben und Offertvorgaben fehlen im KI-Kontext. Systemseitige Memo-Inhalte dürfen nicht als Mitarbeitereingabe ausgegeben werden. Das Abschalten von «Intern» allein lädt keine Memos und startet keine Formulierung. Der Mitarbeiter prüft und sendet den Vorschlag gemäss Hauptablauf; KOM-AK-04/05 prüfen diese Abgrenzung.
- **BR-007:** Manuelle Bearbeitung bleibt bei KI-Fehler oder Abbruch möglich, sofern keine Nachrichten- oder Memo-Speicherung läuft und der aktuelle Fallzustand dies erlaubt. Bereits gespeicherte Inhalte und die wirksame Dringlichkeit bleiben erhalten.
