# Priorisierte offene Punkte

**Aktuelle Konsolidierung:** Die [Umsetzungsmatrix](../use_cases/umsetzungsstand-2026-11-16.md)
und der [Konten-Prüfbericht](../evaluation/konten-und-zugriffe-2026-11-16.md) enthalten
den neueren Stand des integrierten Devbetriebs. Die nachfolgenden Prioritäten
sind eine historische Übersicht und kein aktueller Implementierungsplan.

Stand der betrachteten Dokumente: 16.11.2026. Die Punkte 1–3 wurden nach der
Implementierung der Abschlussaufgaben aktualisiert. Die übrigen Punkte sind eine
historische Planungsübersicht und keine erneut durchgeführte Gesamtabnahme.

## 1. Priorisierte Übersicht

**Kurzfazit:** Die Dokumente beschreiben erhebliche Fortschritte, aber noch keinen vollständig freigegebenen Fallprozess. Die Reihenfolge unten ist eine Einschätzung nach **Dringlichkeit und Auswirkung**; die Dokumente nennen dafür keine verbindlichen Termine.

| Titel | Dringlichkeit | Kurze Zusammenfassung | Mögliche Action Points | Auswirkung |
|---|---|---|---|---|
| 1. Gesamtprozess nicht produktionsbereit | **Kritisch** | Alle Serviceaufgaben besitzen inzwischen Bindungen; vollständige Modelle werden als ausführbare Testkopien geprüft. Eine produktive Gesamtabnahme ist damit nicht erteilt. | B10-Abnahme und Betriebsfreigabe anhand der aktuellen Nachweise durchführen; Aktivierung bis dahin gesperrt lassen. | **Sehr hoch:** Technische Integration ersetzt keine Betriebsfreigabe. |
| 2. Automatischer Abschluss fachlich nicht freigegeben | **Kritisch** | Die aktuelle fachliche Evaluation ist nicht bestanden: Grundbewertung 45/51, individuelle Mitteilung 29/34. Die Anwendung verweigert weiterhin jede automatische Freigabe. | Aktuelle Einzelberichte auswerten; tatsächliche Modellkonfiguration und produktive Regeln qualifizieren. | **Sehr hoch:** Falsche Vorschläge dürfen keine Fälle beenden. |
| 3. Abschlussaufgaben implementiert, Freigabe ausstehend | **Hoch** | `AutoAnswer`, `AutoClose`, `AClosed` und `PrepareCloseAuto` sind angebunden, einschliesslich individueller Mitteilung, Inhaltsprüfung und Wiederholungsschutz. | Fachliche Evaluation abschliessen; aktuelle PostgreSQL-/Camunda-Nachweise berücksichtigen. | **Sehr hoch:** Die technische Umsetzung bleibt hinter der Freigabesicherung. |
| 4. Fehlerfälle und unklare Aufträge | **Hoch** | Die B07-/B09-Verträge sind noch nicht für alle Zielphasen vollständig. Insbesondere die kontrollierte Wiederaufnahme unklarer Mitarbeiteraufträge und eine Betriebsoberfläche fehlen noch. | Fehlende Phasen und Wiederaufnahmeverträge ergänzen; technische Fehler sichtbar machen; kontrollierte Klärungs- und Wiederaufnahmeabläufe festlegen und prüfen. | **Hoch:** Fälle oder Aufträge können hängen bleiben und müssen unter Umständen manuell geklärt werden. |
| 5. Freigegebener Wissensbestand fehlt | **Hoch** | Die technische Suche unterstützt lokale, versionierte und ausdrücklich freigegebene Quellen. Ein konkreter fachlich freigegebener produktiver Wissensbestand ist laut Dokumentation noch offen. | Verantwortliche bestimmen; geeignete Fachquellen auswählen, prüfen und versioniert freigeben; Abschlussregeln bei Bedarf zusätzlich gesondert freigeben. | **Hoch:** Ohne passende Quellen gibt es keine fundierte automatische Empfehlung; Fälle gehen zur menschlichen Prüfung. |
| 6. Fehlende Mail- und Prüfbedarfsereignisse | **Hoch** | Für noch fehlende Prozessphasen stehen weitere Ereignisarten für Mailversand und Prüfbedarf aus. Die vorhandenen Nachweise decken deshalb nicht automatisch alle Zielphasen ab. | Eine Ereignis- und Phasenmatrix erstellen; fehlende Auslöser anbinden; Zustellung, Fehler und Wiederholungen gezielt prüfen. | **Hoch:** Wichtige Benachrichtigungen oder notwendige Mitarbeiterprüfungen könnten in noch nicht angebundenen Phasen fehlen. |
| 7. Abnahmelücken und übersprungene Tests | **Mittel bis hoch** | Die Dokumente nennen erfolgreiche Integrationsprüfungen, aber auch 254 übersprungene Tests einer separaten Java-23-Suite wegen fehlender externer Testumgebung. Diese Zahlen sind kein Nachweis einer vollständigen B10-Abnahme. | Fehlende Testumgebung bereitstellen; übersprungene Tests ausführen und auswerten; Abnahmekriterien und Nachweise je Prozesspfad dokumentieren. | **Hoch:** Fehler in nicht ausgeführten oder nicht vollständig integrierten Pfaden könnten unentdeckt bleiben. |
| 8. Dokumentationsstand bereinigen | **Mittel** | Ältere Abschnitte bezeichnen inzwischen umgesetzte Funktionen noch als offen, beispielsweise Katalogfragen und einzelne Mitarbeiteraktionen. Spätere Ergänzungen korrigieren den Stand, sind aber über beide Dokumente verteilt. | Historische Aussagen sichtbar kennzeichnen oder aktualisieren; eine verbindliche Übersicht „offen / implementiert / getestet / freigegeben“ pflegen. | **Mittel:** Falsche Priorisierung, Doppelarbeit oder eine Fehleinschätzung der Einsatzbereitschaft sind möglich. |

## 2. Etwas ausführlicher zu den Fällen

### 1. Gesamtprozess nicht produktionsbereit

Die vollständigen Modelltests setzen ausschliesslich in Testkopien die Ausführbarkeitsflags.
Sie verlangen inzwischen Bindungen für alle Serviceaufgaben und ersetzen die vier
Abschlussaufgaben nicht mehr durch unerfüllbare Jobtypen. Die Originalflags und die
B10-Gesamtabnahme bleiben davon getrennt; Workflow-Aktivierung ist standardmässig aus.

### 2. Automatischer Abschluss fachlich nicht freigegeben

Massgeblich sind die aktuellen versionierten Berichte in
[Abschlussbewertung und Mitteilung](../evaluation/automatischer-abschluss.md).
Die früheren 21/24-Läufe bleiben historische Nachweise. Die erweiterten Prüfungen
umfassen positive und negative Gesamtfallverläufe, synthetische Regeln sowie die
individuelle Mitteilung und ihre Inhaltsprüfung. Formale Sicherheit ersetzt keine
semantische Zuverlässigkeit. `AutomaticClosureRelease` verweigert weiterhin jede Freigabe.

### 3. Abschlussaufgaben implementiert, Freigabe ausstehend

Die vier Aufgaben sind technisch umgesetzt. Die individuelle LLM-Mitteilung wird
inhaltlich geprüft, dauerhaft veröffentlicht und mit einer Mailpflicht verbunden.
Vor dem Abschluss werden Prozessherkunft, Nachrichtenstand und Version erneut geprüft.
Die Integrationsprüfungen verwenden ausschliesslich eine synthetische Testfreigabe;
diese ist kein Anwendungsprofil und keine produktive Konfigurationsoption.

### 4. Fehlerfälle und unklare Aufträge

Bei unbekanntem Erfolg werden kritische Mitarbeiterentscheidungen bewusst nicht automatisch erneut gesendet. Das vermeidet doppelte oder veraltete Entscheidungen, bedeutet aber auch, dass ein ungeklärter Zustand zuverlässig erkannt und kontrolliert bearbeitet werden muss. Die Dokumentation nennt sowohl unvollständige B07-/B09-Verträge als auch eine fehlende Betriebsoberfläche. Wichtig ist hier nicht bloss ein Retry-Knopf, sondern ein nachvollziehbarer Ablauf, der den tatsächlichen Engine- und Fachzustand vor einer Wiederaufnahme prüft.

### 5. Freigegebener Wissensbestand fehlt

Der vorhandene Mechanismus kann versionierte JSON-Quellen laden, aber er erzeugt keine fachliche Freigabe und keinen produktiven Regelbestand. Ohne geeignete Quelle bleibt die Fallannahme möglich; die Auswertung führt nachvollziehbar zur menschlichen Prüfung. Für dich liegt hier daher vor allem eine fachliche Organisationsaufgabe: festlegen, wer welche Quellen prüft, freigibt und aktuell hält. Eine allgemeine Freigabe für die Wissenssuche genügt zudem nicht automatisch als Freigabe einer Abschlussregel.

### 6. Fehlende Mail- und Prüfbedarfsereignisse

Die Dokumente benennen weitere Ereignisarten in noch fehlenden Phasen, ohne an dieser Stelle eine vollständige Liste aller konkreten Lücken zu liefern. Deshalb wäre eine Matrix sinnvoll: Prozessphase, Ereignis, erwartete Mail oder Prüfung und vorhandener Nachweis. Daraus lassen sich die tatsächlich fehlenden Implementierungen ableiten, statt bereits abgedeckte Pfade erneut zu bearbeiten. Besonders wichtig sind Fehlerfälle und Übergänge, in denen eine Nachricht oder ein neuer Prüfgrund während eines Phasenwechsels eintrifft.

### 7. Abnahmelücken und übersprungene Tests

Die dokumentierten 333 erfolgreichen Integrations-, Vertrags- und HTTP-Prüfungen sind ein guter Teilnachweis, aber keine Gesamtabnahme. Die separate Java-23-Suite meldet zusätzlich 254 übersprungene Tests wegen fehlender externer Testumgebung. Daraus folgt nicht, dass diese Tests fehlschlagen würden; ihr Ergebnis ist schlicht noch nicht belegt. Vor einer Freigabe sollten die fehlenden Umgebungen und die für B10 erforderlichen Szenarien gezielt nachgeholt werden.

### 8. Dokumentationsstand bereinigen

Das Laufzeitdokument bewahrt ältere Abschnitte ausdrücklich als historische Detailnachweise, enthält aber dadurch auch überholte Aussagen neben späteren Ergänzungen. Beispielsweise sind Katalogfragen und weitere QUESTION-Runden in den neueren Abschnitten angebunden, obwohl ein älterer Abschnitt sie noch als ausstehend nennt. Die Implementierungsannahmen korrigieren ebenfalls eine frühere Einschränkung ausdrücklich durch den späteren V32-Abschnitt. Eine kurze aktuelle Statusübersicht würde verhindern, dass erledigte Arbeit erneut priorisiert oder ein Teilnachweis mit einer Freigabe verwechselt wird.

**Grundlage:** [Technische Umsetzung der bisherigen Backend-Abläufe](bpmn/initial-workflow-runtime.md), insbesondere „Noch ausstehend“ und die abschliessenden Prüfnachweise, sowie [Implementierungsannahmen vom 15.11.2026](implementierungsannahmen-2026-11-15.md), insbesondere „Vollständige BPMN-Quellen im Integrationstest“ und die anschliessenden Abschnitte zur Abschlussbewertung.
