# Use Case: Mieteranliegen im Detail anzeigen

## Overview

**Use Case ID:** UC-003  
**Use Case Name:** Mieteranliegen im Detail anzeigen  
**Primary Actor:** Immobilienbewirtschaftung  
**Goal:** Ein ausgewähltes Mieteranliegen mit den für die fachliche Prüfung relevanten Informationen anzeigen.  
**Status:** Planned

## Preconditions

- Die Immobilienbewirtschaftung nutzt UI3 ohne Anmeldung; alle Mitarbeitenden haben dieselben Adminrechte gemäss ZUG-04.
- Ein Mieteranliegen wurde in UC-002 ausgewählt oder direkt über seine Referenz aufgerufen.

## Main Success Scenario

1. Die Immobilienbewirtschaftung öffnet ein Mieteranliegen.
2. Das System zeigt die erfassten Angaben des Anliegens.
3. Das System zeigt den aktuellen Bearbeitungsstatus.
4. Das System zeigt die Nachrichten der automatischen Camunda-Verarbeitung und die wirksame Dringlichkeit.
5. Die Immobilienbewirtschaftung prüft die bereits vorliegenden Analyse- und Bearbeitungsergebnisse sowie offenen Prüfbedarf.
6. Die Immobilienbewirtschaftung kann aus einer kurzen fachlichen Eingabe eine externe Nachricht mit KI formulieren lassen, als Stream prüfen, bearbeiten und bewusst senden (UC-004).

Die Auswahl aus [Screen 02](../frontend/ansicht-02-mitarbeiter-falluebersicht.md) öffnet eine eigene Detailseite. [Screen 03](../frontend/ansicht-03-mitarbeiter-falldetail.md) hält die bestätigte Grundanordnung und Bedienung fest: Falltitel, Status und Dringlichkeit oben, Verlauf links sowie Falldaten und Aktionen rechts. «Zur Fallübersicht» erhält Suche, Filter, Sortierung und zuletzt gewählte Seite gemäss den UI2-Regeln. Die manuelle Dringlichkeitsänderung erfolgt über «Dringlichkeit ändern», Stufenauswahl und «Speichern» nach [DRING-06](../specifications/dringlichkeitsbewertung.md#dring-06-manuelle-anpassung): Systembewertungen dürfen die manuelle Einstufung nicht überschreiben; wirksame Änderungen werden nach DRING-07 historisiert und dem Mieter angezeigt. Die optionale Begründung bleibt intern. Visuelle Ausgestaltung und technische Umsetzung folgen separat.

## Alternative Flows

### A1: Anliegen nicht gefunden

**Trigger:** Für die angeforderte Referenz existiert kein Anliegen.

1. Das System zeigt «Dieser Fall ist nicht verfügbar.» ohne Falldaten.
2. Das System bietet «Zur Fallübersicht» zurück zur Mitarbeiter-Fallübersicht.
3. Der Use Case endet.

### A2: KI-Analyse noch nicht vorhanden

**Trigger:** Für das Anliegen liegt noch kein Analyseergebnis vor.

1. Das System zeigt den gespeicherten Fallstand und kennzeichnet ein noch fehlendes Ergebnis der automatischen Verarbeitung.
2. Die manuelle Bearbeitung bleibt möglich; die automatische Analyse läuft im Camunda-Prozess. Bei Analysefehlern gilt FALL-05. Es gibt keinen manuellen Analysestart in UI3.

## Postconditions

### Success Postconditions

- Die fachlich relevanten Angaben des ausgewählten Anliegens sind sichtbar.
- Die KI-Formulierungshilfe kann für eine externe Nachricht aus der Detailansicht genutzt werden (UC-004).

### Failure Postconditions

- Bei einer ungültigen Referenz werden keine Daten eines anderen Anliegens angezeigt.

## Business Rules

### BR-001: KI-Empfehlung ist keine Entscheidung

Eine KI-Ausgabe wird als Empfehlung beziehungsweise Unterstützung dargestellt. Vorgeschriebene menschliche Prüfungen und die fachliche Verantwortung verbleiben bei der Immobilienbewirtschaftung. Mitarbeitende können Fälle abschliessen; die vereinbarten Systemabschlüsse folgen den gesonderten, serverseitig geprüften Fachregeln aus [FALL-07](../specifications/fallverwaltung.md#fall-07-abschluss-und-zeit-danach). Eine beliebige KI-Ausgabe ist keine Abschlussberechtigung. Bei Kosten bzw. verbindlicher externer Beauftragung, sehr dringlichen oder schwerwiegenden Fällen sowie starken oder eskalierten Mieterbeschwerden ist die menschliche Entscheidung nach [FALL-10](../specifications/fallverwaltung.md#fall-10-automatisierung-und-menschliche-freigabe) verpflichtend. Offener Prüfbedarf darf nicht durch einen Systemabschluss umgangen werden. Mitarbeiterprüfungen werden über interne Memos dokumentiert; es gibt keine separate Aktion «Prüfung erledigt» und keine externe Auftragsausführung. Statusänderung und Mitarbeiterabschluss folgen der bestätigten Bedienung in Screen 03.

Nach Prüfung kann der Mitarbeiter gemäss [FALL-06](../specifications/fallverwaltung.md#direkter-übergang-in-die-bearbeitung) auch ohne Wiedervorlage direkt von «Mitarbeiterprüfung erforderlich» auf «In Bearbeitung» wechseln. Der bestätigte manuelle Statuswechsel ist die bewusste Mitarbeiterentscheidung. Bereits berücksichtigte Prüfgründe führen nicht sofort zurück zur Prüfung; neue Erkenntnisse können sie erneut auslösen. Der geprüfte Fallstand bleibt nachvollziehbar; eine zusätzliche Prüfbestätigung ist nicht erforderlich.

Beim erfolgreichen Mitarbeiterabschluss speichert PropertyFlow intern «Vom Mitarbeiter geprüft: keine weitere Bearbeitung erforderlich.» mit Mitarbeiter, Zeitpunkt und Abschlussquelle gemäss FALL-07. Die bewusste Bestätigung bildet die Grundlage. Ein zusätzliches Begründungsfeld oder Pflichtmemo entfällt; eine ausführlichere Erklärung ist vor dem Abschluss freiwillig als Memo möglich. Der Vermerk ist keine Kommunikationsnachricht. Bei Systemabschlüssen bleibt der tatsächlich geprüfte Systemgrund erforderlich.

### BR-003: Wiedereröffnung ausschliesslich durch Mitarbeitende

Ein Mitarbeiter kann einen abgeschlossenen Fall in Screen 03 bewusst wiedereröffnen gemäss FALL-07 und ZUG-04. Case-ID und bisherige Historie bleiben erhalten; die Handlung bleibt mit Zeitpunkt und Mitarbeiteridentität nachvollziehbar. Nach bestätigter Wiedereröffnung gelten die Regeln für aktive Fälle. Mieter und System dürfen den Fall nicht wiedereröffnen. «Fall wiedereröffnen» öffnet die Bestätigung mit «Wiedereröffnen» und «Abbrechen». Nach bestätigter Wiedereröffnung gilt «In Bearbeitung», bei offenem Prüfbedarf «Mitarbeiterprüfung erforderlich». Eine optionale Begründung wird danach als internes Memo erfasst.

Verspätete automatische Ergebnisse aus der Bearbeitung vor dem letzten Abschluss dürfen den wiedereröffneten Fall nicht verändern. Die Weiterbearbeitung richtet sich nach dem aktuellen Fallstand; die gespeicherte Historie bleibt erhalten. Diese fachliche Grenze steht in [FALL-07](../specifications/fallverwaltung.md#verspätete-automatische-ergebnisse-nach-wiedereröffnung); die technische Prozessfortsetzung wird später ausgearbeitet.

### BR-002: Technische KI-Details stehen nicht im Vordergrund

Die Detailansicht priorisiert fachlich verständliche Informationen wie Dringlichkeit, Empfehlung, Zuständigkeit und Bearbeitungsstatus.

### BR-004: Wiedervorlage mit paralleler Verarbeitung

Der Mitarbeiter kann in UI3 eine optionale Wiedervorlage in Tagen oder als festes Datum setzen und im aktiven Fall jederzeit ändern oder löschen. PropertyFlow speichert den bestimmten Termin; Camunda wartet im bestehenden Fallprozess über einen Timer. Setzen und Ändern führen zu «Wartet auf Wiedervorlage». Löschen entfernt den aktuellen Termin, beendet die Wartezeit und setzt «In Bearbeitung». UI2 zeigt das gespeicherte Datum in einer zusätzlichen sortierbaren Spalte. Neue Mieternachrichten werden währenddessen weiter automatisch verarbeitet. Neue Erkenntnisse können eine frühere Wiedervorlage erfordern; andernfalls bleibt der Termin bestehen. Am wirksamen Termin erhält der aktive Fall «Mitarbeiterprüfung erforderlich». Terminänderung, Löschung, Abschluss und Nachvollziehbarkeit folgen [FALL-11](../specifications/fallverwaltung.md#fall-11-wiedervorlage-und-parallele-nachrichtenverarbeitung); die Darstellung folgt UI3.

### BR-005: Objekt und Wohnung zuordnen

Bei der Bearbeitung eines aktiven Falls kann der Mitarbeiter rechts in UI3 «Zuordnung ändern» wählen, das Objekt und die zugehörige Wohnung auswählen und mit «Speichern» bestätigen. Eine offene Zuordnung wird damit geklärt oder eine bisherige Zuordnung korrigiert. Die ursprüngliche Mieterangabe bleibt getrennt von der bestätigten Zuordnung nachvollziehbar. Berechtigung, gültige Auswahl, Konfliktprüfung und Speicherung folgen [FALL-04](../specifications/fallverwaltung.md#fall-04-ursprungsdaten-und-zuordnung); UI2 übernimmt den gespeicherten Stand in seine bestehende Spalte. Mitarbeitereingabe und laufender KI-Stream bleiben erhalten. Die Zuordnung ändert weder Kontaktadresse noch persönlichen Fall-Link und ersetzt keine separate manuelle Statusänderung.

## Gemeinsame fachliche Grundlage

Fallidentität, fachliche Statusbedeutung und Abgrenzung zum technischen Camunda-Zustand sind in der [Fallverwaltung](../specifications/fallverwaltung.md) definiert, insbesondere FALL-01, FALL-05 und FALL-06. Die Ansicht verwendet diese fachliche Grundlage; ein technischer Incident ist kein eigener fachlicher Abschlussstatus. Die acht fachlichen Bearbeitungsstatus sind unter FALL-05 vereinbart; konkrete Prozessübergänge und das BPMN-Mapping bleiben zu konkretisieren.
