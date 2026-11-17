# KI-Tools und manueller Ersatzbetrieb

**Stand:** 16.11.2026. **Status:** Verbindliche fachliche Anforderung; die Kriterien
unten sind keine Behauptung einer bereits bestandenen technischen Abnahme.

## KI-01: KI unterstützt die Bearbeitung

KI-Tools unterstützen Triage/Dringlichkeitsbewertung, zentrale Antwortauswertung,
Empfehlung mit RAG, Rückfragen, Abschlussbewertung und individuelle
Abschlussmitteilungen sowie die Formulierung externer Nachrichten.
Ein erfolgreicher KI-Aufruf ist keine Voraussetzung für die manuelle Fallbearbeitung.
Das gilt auch bei vollständig deaktivierter KI-Anbindung.

## KI-02: Verbindlicher manueller Ersatzbetrieb

Bei Nichterreichbarkeit, fehlender Konfiguration, Zeitüberschreitung, erschöpftem
Providerkontingent, RAG-Ausfall oder ungültiger bzw. fachlich nicht freigegebener
KI-Ausgabe bleibt der gespeicherte Fall erhalten und für berechtigte Mitarbeitende
bearbeitbar. Ein KI-Fehler darf weder die Erfassung noch den manuellen Bearbeitungsweg
blockieren oder einen endlos wiederholten Modellaufruf voraussetzen.

Der führende Camunda-Prozess übergibt einen automatisch nicht weiter bearbeitbaren
Fall mit nachvollziehbarem Grund an die Mitarbeiterprüfung. Besteht bereits eine
Mitarbeiteraufgabe, bleibt diese erhalten; es entsteht keine zweite Aufgabe.
KI-Aufrufe haben eine konfigurierte endliche Wartezeit. Nach deren Ablauf muss der
manuelle Weg erreichbar sein, ohne auf eine Wiederherstellung des Providers zu warten.

Mitarbeitende können gemäss Fallzustand und Berechtigung weiterhin:

- Fälle übernehmen, Angaben prüfen und die Dringlichkeit manuell festlegen;
- Mieterantworten selbst beurteilen, Rückfragen und externe Nachrichten selbst
  formulieren und senden sowie interne Memos erfassen;
- Zuständigkeit und weiteres Vorgehen selbst bestimmen;
- externe Wartezustände und Wiedervorlagen setzen und bearbeiten;
- den Fall nach den geltenden Fachregeln manuell abschliessen, eine individuelle
  Abschlussmitteilung selbst verfassen und einen abgeschlossenen Fall wiedereröffnen.

Fehlende Empfehlungen werden als nicht verfügbar dargestellt. Bestehende gültige
Eingaben und Entscheidungen bleiben erhalten. Es werden keine Ersatzempfehlungen,
Dringlichkeiten, Freigaben oder angeblichen KI-Erfolge erfunden. Bei einer
Formulierungshilfe bleiben Mitarbeitereingaben auch nach Fehler oder Abbruch erhalten;
das manuelle Senden erfordert keine erfolgreiche Generierung.

## KI-03: Prozessführung, Sicherheit und Wiederanlauf

Der Ersatzbetrieb verwendet dieselben Serverprüfungen, PostgreSQL-Persistenz,
Auditierung, Benachrichtigungsaufträge und Camunda-Prozesse wie die normale
Mitarbeiterbearbeitung. Die Oberfläche umgeht keine Prozess- oder Berechtigungssperre.
Die Ausnahme betrifft die KI-Verfügbarkeit: Bei einem gleichzeitigen **Camunda-Ausfall**
gilt weiterhin [FALL-06](fallverwaltung.md#fall-06-fachlicher-status-und-technischer-workflow).
Bereits angenommene Fälle bleiben lesbar; fachliche Bearbeitungsaktionen bleiben bis
zur bestätigten Prozessverfügbarkeit gesperrt.

Fehlergrund, betroffener Verarbeitungsschritt und Übergabe an die Mitarbeiterprüfung
müssen intern nachvollziehbar sein, ohne Providergeheimnisse offenzulegen. Verspätete
KI-Ergebnisse und Wiederholungen dürfen manuelle Entscheidungen nicht überschreiben,
keine doppelten Nachrichten oder Aufgaben erzeugen und keinen abgeschlossenen Fall
verändern. Die Rückkehr der KI aktiviert keine rückwirkenden automatischen Aktionen.

Automatische Abschlüsse bleiben bis zur bestandenen fachlichen Evaluation und
ausdrücklichen Freigabe gesperrt. Ein KI-Ausfall lockert diese Sperre nicht. Manuelle
Abschlüsse unter Mitarbeiterverantwortung benötigen keine bestandene Modellevaluation;
die fachlichen Abschlussbedingungen und die individuelle Abschlussmitteilung bleiben
verbindlich.

## KI-AK: Abnahmekriterien

| ID | Nachweis |
|---|---|
| KI-AK-01 | Mit deaktiviertem Provider einen Mieterfall erfassen; Fall und Startauftrag sind dauerhaft gespeichert, eine echte Camunda-Instanz wird registriert und erreicht die Mitarbeiterprüfung. |
| KI-AK-02 | Nichterreichbaren Provider, Timeout, ungültige Ausgabe und RAG-Ausfall gezielt auslösen; innerhalb der konfigurierten Wartezeit endet der KI-Versuch und der manuelle Weg bleibt erreichbar. |
| KI-AK-03 | Ohne KI übernehmen, Dringlichkeit setzen, Rückfrage senden, Mieterantwort speichern und manuell beurteilen; Verlauf, Rechte und Mailaufträge bleiben korrekt. |
| KI-AK-04 | Ohne KI externes Warten, Wiedervorlage, manuellen Abschluss mit selbst formulierter individueller Mitteilung und Wiedereröffnung durchspielen; der tatsächliche Prozessschritt ist in Camunda sichtbar. |
| KI-AK-05 | Bei Fehler oder Abbruch der Formulierungshilfe bleibt die aktuelle Mitarbeitereingabe erhalten und kann nach den Fachprüfungen manuell gesendet werden. |
| KI-AK-06 | Backend und Infrastruktur kontrolliert neu starten; Fälle, Nachrichten, Entscheidungen und Prozesszuordnung bleiben gespeichert. |
| KI-AK-07 | Verspätete KI-Ausgabe nach manueller Entscheidung zustellen; keine Überschreibung, doppelte Aufgabe, Nachricht oder automatische Abschlussfreigabe. |
| KI-AK-08 | Camunda getrennt von KI unterbrechen; die serverseitige Bearbeitungssperre aus FALL-06 bleibt wirksam. |

## Zugehörige Verträge

- [Dringlichkeitsbewertung](dringlichkeitsbewertung.md)
- [Fallverwaltung](fallverwaltung.md), [Fallkommunikation](fallkommunikation.md)
- [Externe Nachricht mit KI formulieren](../use_cases/UC-004-ki-analyse-durchfuehren.md)
- [KI-Servicevertrag des ursprünglichen Analysebausteins](../ai-usage/ki-service-vertrag.md)
- [Freigabeevaluation automatischer Abschlüsse](../evaluation/automatischer-abschluss.md)
