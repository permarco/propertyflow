# Dringlichkeitsbewertung

**Status:** Stufen, Farben, Bewertungsablauf, manuelle Anpassung und transparente Änderungshistorie vereinbart; technische Umsetzung und konkurrierende Änderungen noch zu konkretisieren.  
**Stand:** 09.10.2026  
**Geltungsbereich:** Triage, fachliche Fallbearbeitung und Darstellung der Dringlichkeit in PropertyFlow.

## Zweck und Verbindlichkeit

Dieses Dokument ist die zentrale Quelle für die vereinbarten Dringlichkeitsstufen und ihre Bedeutung. Screen-Spezifikationen verlinken diese Regeln und beschreiben Darstellung, Sortierung und Filter. Die Stufen sind eine fachliche Festlegung, keine Architekturentscheidung.

Die [Vision](../vision.md) sieht KI-gestützte Dringlichkeitsbewertung unter fachlicher Verantwortung der Immobilienbewirtschaftung vor. Gemäss [Modulstruktur](../architecture/module-structure.md) verantwortet `triage` die Dringlichkeitsbewertung. Fachlicher Fallstatus und technische Analyse-/Workflow-Zustände bleiben gemäss [FALL-06](fallverwaltung.md#fall-06-fachlicher-status-und-technischer-workflow) getrennt. Die Bedingungen für Mitarbeiter- und Systemabschlüsse werden gesondert unter FALL-07 geführt.

## DRING-01: Fachliche Stufen

**Vereinbart mit Marco am 09.10.2026:**

| Stufe | Bedeutung |
|---|---|
| **Kritisch** | Unmittelbare Gefahr oder drohender erheblicher Folgeschaden. |
| **Hoch** | Wesentliche Beeinträchtigung, die rasche Bearbeitung erfordert. |
| **Normal** | Reguläres Anliegen ohne erkennbare akute Gefahr. |
| **Niedrig** | Geringfügiges Anliegen ohne wesentliche Beeinträchtigung. |

Die fachliche Rangfolge lautet **Kritisch > Hoch > Normal > Niedrig**. Die Stufen legen keine konkreten Bearbeitungsfristen, SLA, automatische Beauftragung oder Abschlussberechtigung fest.

## DRING-02: Noch nicht bewertet

**«Noch nicht bewertet» ist ein eigener Bewertungszustand, keine fünfte Dringlichkeitsstufe.** Ohne fachlich verwendbare Bewertung wird keine niedrige Dringlichkeit angenommen. Ein technischer Analysefehler ist ebenfalls keine Dringlichkeitsstufe; vorhandene fachliche Daten bleiben erhalten und manuelle Bearbeitung bleibt möglich gemäss [ADR-001](../architecture/adr/ADR-001-grundarchitektur.md).

Die Platzierung unbewerteter Fälle und der zweite Sortierwert der Mitarbeiterliste sind in [Screen 02](../frontend/ansicht-02-mitarbeiter-falluebersicht.md) vereinbart. Diese Darstellungsentscheidung ändert nicht die fachliche Rangfolge der vier Stufen.

## DRING-03: Menschliche Prüfung und Darstellung

KI-Empfehlungen ersetzen die endgültige menschliche Entscheidung nicht. LLM-Ausgaben sind nicht vertrauenswürdig und müssen vor fachlicher Verwendung validiert werden; relevante Empfehlungen bleiben gemäss Projekt-Kontext und ADR-001 nachvollziehbar.

Dringlichkeit wird gemäss [UC-002](../use_cases/UC-002-mieteranliegen-anzeigen.md) textlich dargestellt und nicht ausschliesslich über Farbe vermittelt. Die aktuelle Stufe und ihre Änderungsquelle werden unterschieden: «System» für die automatische Prozessbewertung, «Immobilienverwaltung» für die manuelle Anpassung durch einen berechtigten Mitarbeiter. Eine automatisch gespeicherte Dringlichkeit ist keine abschliessende menschliche Entscheidung über die weitere Bearbeitung oder den Fallabschluss.

## DRING-04: Farbkennzeichnung

**Vereinbart am 09.10.2026:** Die gesamte folgende Farbzuordnung ist bestätigt:

| Stufe / Bewertungszustand | Farbe | Stand |
|---|---|---|
| Kritisch | Rot | Vereinbart |
| Hoch | Orange | Vereinbart |
| Normal | Gelb | Vereinbart |
| Niedrig | Grün | Vereinbart |
| Noch nicht bewertet | Blau | Vereinbart; weiterhin keine Dringlichkeitsstufe |

Die Kennzeichnung ergänzt immer den sichtbaren Text. Konkrete Farbtöne, Text-/Hintergrundkombinationen und ausreichende Kontraste werden bei der späteren gemeinsamen Gestaltung festgelegt und geprüft. Die Farbfamilien legen noch keine endgültige visuelle Gestaltung fest.

## DRING-05: Erst- und Neubewertung im Prozess

**Vereinbart:** Nach dauerhafter Annahme der ursprünglichen Mieter-Mitteilung wird die Dringlichkeit erstmals im Camunda-gesteuerten Ablauf berechnet. Jede weitere dauerhaft gespeicherte Mieter-Nachricht veranlasst erneut eine Bewertung im bestehenden Camunda-Fallprozess. PropertyFlow-Worker und fachliche Services führen die Bewertung aus, validieren das Ergebnis und speichern die fachlich verwendete Stufe in PropertyFlow. Eine Nachricht eröffnet keinen zweiten führenden Fallprozess (FALL-06, KOM-03).

Solange weder eine erfolgreiche Systembewertung noch eine manuelle Einstufung vorliegt, gilt «Noch nicht bewertet». Eine bereits vor dem ersten Systemergebnis manuell gesetzte Stufe ist wirksam und geschützt gemäss DRING-06. Während einer Neubewertung bleibt die bisher gespeicherte Stufe sichtbar. Ein fehlgeschlagener Versuch ersetzt sie nicht durch eine erfundene Stufe und erzeugt keine erfolgreiche Stufenänderung. Bei aktivem Fall gilt für den Bearbeitungsstatus und den Analysehinweis FALL-05; die manuelle Bearbeitung bleibt möglich.

## DRING-06: Manuelle Anpassung

**Vereinbart:** Ein berechtigter, separat authentifizierter Mitarbeiter kann die Dringlichkeitsstufe in der Mitarbeiter-Falldetailansicht ändern. «User» bezeichnet in diesem Ablauf den Mitarbeiter, nicht den Mieter. Die Mieteransicht bleibt bezüglich Dringlichkeit ausschliesslich lesend gemäss ZUG-04.

Die manuelle Anpassung wird in PropertyFlow gespeichert und mit der Änderungsquelle «Immobilienverwaltung» nachvollziehbar.

**Vereinbart:** Eine manuell gesetzte Dringlichkeitsstufe hat Vorrang und darf vom System nicht überschrieben werden. Dies gilt auch nach weiteren Mieter-Nachrichten und bei gleichzeitig oder verspätet eintreffenden automatischen Ergebnissen. Eine weitere manuelle Anpassung durch berechtigte Mitarbeitende bleibt möglich und wird erneut historisiert.

Die automatische Neubewertung nach jeder Mieter-Nachricht bleibt bestehen. Ihr Ergebnis wird getrennt von der wirksamen manuellen Stufe als Systembewertung geführt und ersetzt diese nicht. Ohne Änderung der wirksamen Stufe entsteht kein mieteröffentlicher Stufenwechsel und keine Änderungsmail. Die Darstellung eines abweichenden Systemergebnisses im Mitarbeiterdetail bleibt zu konkretisieren. Ein Zurücksetzen auf automatische Einstufung ist nicht vereinbart; eine solche Funktion wird nicht stillschweigend eingeführt. Alle authentifizierten Mitarbeitenden haben diese Berechtigung über die einheitliche Adminrolle gemäss ZUG-04. Die technische Synchronisierung bleibt festzulegen.

## DRING-07: Änderungshistorie und Mietertransparenz

**Vereinbart:** Jede gespeicherte Änderung der Dringlichkeit wird in einer dauerhaften, chronologischen Änderungshistorie festgehalten. Dazu gehört auch die erste Einstufung aus «Noch nicht bewertet». Frühere Einträge werden durch eine Neubewertung oder manuelle Anpassung nicht überschrieben.

Jeder Eintrag enthält mindestens vorherigen Bewertungszustand bzw. vorherige Stufe, neue Stufe, serverseitigen Zeitpunkt und Änderungsquelle (System oder Mitarbeiter). Intern muss die tatsächliche Mitarbeiteridentität bzw. die auslösende Prozess-/Bewertungsreferenz nachvollziehbar bleiben. Die externe Anzeige verwendet die Rollenbezeichnungen «System» und «Immobilienverwaltung»; persönliche Mitarbeiternamen werden dadurch nicht automatisch veröffentlicht. Speicherung erfolgt in UTC, Anzeige in Europe/Zurich.

Die aktuelle Stufe und alle fachlichen Stufenänderungen werden dem berechtigten Mieter in [Screen 01](../frontend/ansicht-01-mieter-fallansicht.md) transparent angezeigt. Diese Einträge sind eigene strukturierte fachliche Ereignisse, keine nachträglich generierten Kommunikationsnachrichten. Interne Memos, interne Begründungen, Prompts und technische Prozessdaten bleiben ausgeschlossen. Die Historie bestätigt die gespeicherte Änderung, nicht die Zustellung einer E-Mail.

Eine erneute Bewertung mit unveränderter Stufe erzeugt keine fingierte Stufenänderung; die Nachvollziehbarkeit des Bewertungsversuchs bleibt intern erforderlich. Wiederholungen derselben Änderung erzeugen keinen zweiten Historieneintrag. Historie und aktuelle Stufe müssen konsistent gespeichert werden. Mieteröffentliche Stufenänderungen lösen eine Benachrichtigung gemäss [BEN-01](benachrichtigungen-und-zustellung.md#ben-01-auslöser-und-ausschlüsse) aus.

## Offene Konkretisierungen

- Kriterien und repräsentative Grenzfälle zur Einordnung in die vereinbarten Stufen.
- Konkrete Validierung und Übernahme einer automatischen Bewertung; die manuelle Anpassung ist für alle authentifizierten Mitarbeitenden mit einheitlichen Adminrechten vorgesehen.
- Technische Synchronisierung konkurrierender Bewertungen und manueller Änderungen, veralteter Ergebnisse sowie mehrerer rasch aufeinanderfolgender Mieter-Nachrichten. Der vereinbarte Vorrang manueller Einstufungen muss auch bei gleichzeitigem Speichern wirksam bleiben.
- Zulässigkeit einer Dringlichkeitsänderung nach Fallabschluss; nachträgliche Nachrichtenerzeugung bleibt durch KOM-04 gesperrt.
- Technische Werte, Validierung und Service-/Persistenzverträge.

Diese offenen Umsetzungs- und Konfliktregeln ändern die vereinbarten Auslöser, die manuelle Anpassung und die Transparenz der Historie nicht. Konkrete Fristen benötigen bei Bedarf eine separate fachliche Vereinbarung.

## Prüfkriterien für die spätere Umsetzung

- **DRING-AK-01:** Die vier Stufen werden mit den vereinbarten Bedeutungen und der Rangfolge aus DRING-01 verwendet.
- **DRING-AK-02:** Fehlende Bewertung bleibt von Niedrig unterscheidbar; Analysefehler und Fallstatus werden nicht als Dringlichkeitsstufe behandelt.
- **DRING-AK-03:** Die Anzeige bleibt ohne Farbwahrnehmung verständlich; eine KI-Empfehlung wird nicht als abschliessende menschliche Entscheidung ausgegeben.
- **DRING-AK-04:** Die Farbkennzeichnung folgt der bestätigten Zuordnung aus DRING-04 und enthält immer die textliche Bezeichnung. Die bei der Gestaltung gewählten Kombinationen werden auf ausreichende Kontraste geprüft.
- **DRING-AK-05:** Die ursprüngliche Einreichung und jede weitere gespeicherte Mieter-Nachricht veranlassen eine Bewertung im bestehenden Camunda-Prozess. Wiederholungen eröffnen keinen zweiten führenden Prozess und duplizieren keine Stufenänderung.
- **DRING-AK-06:** Berechtigte Mitarbeitende können die Stufe im Detail ändern; Mieter können dies auch über direkte Backend-Aufrufe nicht. Die Änderung bleibt intern einem konkreten Mitarbeiter zugeordnet.
- **DRING-AK-07:** Erstbewertung und spätere Stufenänderungen erhalten eine konsistente Historie mit altem/neuem Wert, Zeitpunkt und Quelle. Der Mieter sieht diese strukturierten Änderungen, jedoch keine internen Memos, Begründungen oder Prozessreferenzen.
- **DRING-AK-08:** Bei fehlgeschlagener Neubewertung bleibt die bisherige Stufe erhalten. Unveränderte Neubewertung erzeugt keinen Stufenwechsel oder zusätzliche Änderungsmail. Konkurrenz- und Wiederholungsfälle werden nach dem noch festzulegenden Vertrag geprüft.
- **DRING-AK-09:** Eine manuelle Einstufung bleibt nach neuen Mieter-Nachrichten und automatischen Neubewertungen wirksam. Ein gezielter Konkurrenztest mit verspätetem Systemergebnis weist nach, dass das System die manuelle Stufe nicht überschreibt. Das getrennte Systemergebnis erzeugt keinen mieteröffentlichen Stufenwechsel oder Änderungs-Mailauftrag. Eine weitere berechtigte manuelle Änderung wird historisiert.

Dies sind Anforderungen an spätere Prüfungen, keine ausgeführten Testnachweise.
