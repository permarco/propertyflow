# Backend-Fortsetzung: Umsetzung und Prüfung

Stand: 15.11.2026. Fortsetzung nach dem [INITIAL-Nachweis](initial-workflow-pruefung-2026-11-15.md).
Die Umsetzung erfolgte im vorhandenen Arbeitsverzeichnis; keine produktive Freigabe
und kein GitHub-CI-Ergebnis werden hier behauptet.

## Umgesetzter Umfang

- Unabhängiger Camunda-Mailprozess aus der kanonischen BPMN-Datei; aktuelle
  Kontaktadresse und persönlicher Link beim Versand, nachvollziehbare SMTP-Annahme,
  Behandlung eindeutig fehlgeschlagener und unklarer Zustellung.
- Dauerhafte Prüfgründe, Unterbrechung des aktuellen automatischen Aufrufs und
  genau eine zugeordnete native Camunda-Prüfaufgabe. Parallele und verspätete Gründe
  ergänzen dieselbe Prüfung. Andere Generationen und veraltete Worker bleiben wirkungslos.
- Mitarbeiter-Memos, manuelle Dringlichkeit und Objektzuordnung mit autorisierten,
  versionsgebundenen Aufträgen, synchroner Korrelation und idempotenter Wirkung.
  Eingaben bleiben bei Konflikt, Sitzungsverlust und unklarem Ausgang erhalten.
- Formularverträge für Memo, Dringlichkeit und Freitextzuordnung mit freien
  Absende-IDs, leerer HTTP-200-Antwort und korrekter Unterscheidung zwischen
  Korrelation und dauerhaftem Speichern. Keine automatische erneute Übermittlung
  von Mitarbeiteraktionen nach einem Timeout; lesende Ergebnisabfrage bleibt erlaubt.
- Kontaktmaskierung der Mieteransicht gemäss Ansicht 01, einschliesslich kurzer
  Adressen ohne vollständige Domain; Aktualisierung im vereinbarten 20-Sekunden-Takt.
- Initiale Triage über den vorhandenen Analyse-Port, ausserhalb der Datenbanktransaktion.
  Validiertes Systemergebnis, manueller Vorrang, getrennte interne Nachvollziehbarkeit,
  Stufenhistorie und Mailpflicht. Analysefehler führen über den Eltern-Fehlerpfad
  zur Mitarbeiterprüfung; technische Speicherfehler bleiben Retries.
- Native Mitarbeiterentscheidung zum Fortsetzen und Abschluss aus SReview/SWork,
  gebunden an tatsächliche Aufgabe und aktuellen Fallstand. Nachträgliche Konflikte
  führen in eine neue Prüfung; unbekannte Completion-Antworten werden nicht automatisch wiederholt.
- Interne Änderungsnachweise für Fortsetzen, Abschluss, Dringlichkeit und Zuordnung
  mit Mitarbeiter und Zeitpunkt in der autorisierten Mitarbeiterhistorie.
- Dauerhafte Mieternachrichten mit geschützter Veröffentlichung, idempotenter
  Absende-ID, Mailpflicht, zentraler Korrelation und einmaliger Kindzuordnung.
  Die weitere zentrale Neubewertung bleibt offen.
- Externe Mitarbeiterinformationen während Abklärung, Prüfung und Bearbeitung;
  Rückfragen während Prüfung mit dauerhaftem Antwortbedarf. Veröffentlichung und
  Mailpflicht sind atomar; alte Aufgabenaufträge bleiben nach Fortsetzen wirkungslos.
  Native Aufgaben werden durch die Nebenaktion nicht abgeschlossen. Weitere Phasen
  und die zentrale Erledigung des Antwortbedarfs bleiben offen.
- Datenbankmigrationen V4 bis V17 und technische Bindungen in den Originalmodellen.
  Bestehende Diagrammkoordinaten bleiben erhalten. Für den nachträglichen
  Fortsetzungskonflikt wurden SProgressStale und die Rückkante ergänzt.

Details, Konfiguration und Abnahmegrenzen:
[Laufzeitdokumentation](../architecture/bpmn/initial-workflow-runtime.md).

## Ausgeführte Prüfung

| Prüfung | Ergebnis und Grenze |
|---|---|
| Kombinierte Backend-Abnahme | 135 Tests, 0 Fehler, 0 übersprungen; echte PostgreSQL-16-, Camunda-8.9.19- und Mailpit-Testdienste. |
| Vollständige Java-Regression unter Linux / Java 23 | 450 Tests, 0 Fehler; 328 ausgeführt, 122 wegen fehlender optionaler Testumgebung übersprungen. Die Datenbank-/Engine-Prüfung lief separat wie oben. |
| Erneuter Nachrichten-Vertragsabgleich | 34 gezielte Tests bestanden: Formularfeld `operation`, bis 80 UTF-16-Codeeinheiten, HTTP 303, erhaltenes Fehlerformular, Migration V14 und native Entscheidungssicherung. |
| Abschliessender Formular-/Projektionsabgleich | 51 Tests bestanden: freie Mitarbeiter-Absende-IDs, eindeutige Ablehnung versus Timeout, Memo-Commit vor Erfolg, Wiederholung nach Versionsänderung und Kontaktmaskierung. |
| Mitarbeiterkommunikation und Versand | 50 Integrationstests bestanden: echte Camunda-Nebenaktionen, Aufgabenwechsel, eindeutige Veröffentlichung, Rollenentzug, parallele Wiederholung und SMTP-Versand nach Fallabschluss; einschliesslich angrenzender Falldaten-/Triage-Prüfungen. |
| Antwortbedarfsanzeige und Leseverträge | 60 gezielte Tests bestanden; öffentliche Kennzeichnung in Mieter- und Mitarbeiteransicht, kompatible Nachrichtenprojektion und erneute PostgreSQL-Prüfung der Veröffentlichung. |
| JavaScript-Regression | 6 Tests bestanden. |
| Browser-Verträge | 57 Prüfungen bestanden (je 19 mit Edge, Firefox und WebKit). Reale Aktions- und Lese-Skripte mit kontrollierten HTTP-Antworten: unbekannter Speicherausgang, identische Wiederholung, Versionskonflikt, Kontowechsel, Sitzungsverlust, Auswahl ohne Speicherung, Abbruch der Zuordnung, native Aufgabenbindung, Abschlussbestätigung und Mieternachrichten mit Textgrenze/Abschlusssperre. Kein vollständiger Browser-End-to-End-Nachweis aller Backend-Funktionen. |
| Paketierung | Spring-Boot-JAR erfolgreich gebaut. |
| Modellabgleich | Workerbindungen, Call-Scopes, Message-Korrelation, Unterbrechungsbedingung, native Prüfaufgabe und Analysefehlerpfad gegen Originalquellen geprüft. |

Die Backend-Abnahme umfasst `GuestCasePostgresTest`, `GuestCaseRestartPostgresTest`,
`StaffAccountPostgresTest`, `CaseWorkflowPostgresTest`, `InitialCaseCamundaTest`,
`WorkflowRestartCamundaTest`, `CaseMailPostgresTest`, `CaseMailCamundaTest`,
`CaseMailTransportTest`, `StaffReviewPostgresTest`, `StaffReviewCamundaTest`,
`CaseDataPostgresTest`, `CaseAnalysisPostgresTest`, `TriageCamundaTest`,
`InitialWorkflowBindingTest`, `ReviewWorkflowBindingTest`, `ProcessStartDispatcherTest`,
`StaffCaseWebTest`, `BoundedAnalysisModelTest`, `StaffDecisionPostgresTest`,
`StaffDecisionCamundaTest`, `CaseClosureCamundaTest`, `TenantMessagePostgresTest`
und `TenantMessageCamundaTest`.

Lokale Ausgaben liegen unter `target/backend-workflow-acceptance.log`,
`target/linux-regression.log`, `target/linux-quality/`, `target/js-regression.log`,
`target/workflow-browser-*.log` und `target/tenant-message-*.log`. Die letzte Paketierung und die Lesevertragsprüfung stehen in `target/staff-message-projections.log`; die Kommunikationsprüfung in `target/staff-message-acceptance.log`; die 51 Formular-/Projektionsprüfungen in `target/final-contract-acceptance.log`; der Nachrichten-Vertragsabgleich in `target/message-contract-acceptance.log`. Diese generierten Dateien
sind nicht Bestandteil des Repositorys. Der CI-Integrationsjob und die Browsermatrix
wurden um die neuen Tests ergänzt; sie wurden in diesem Arbeitsgang nicht auf GitHub ausgeführt.

## Erkenntnisse und Korrekturen

1. Der erste Korrelationsadapter verwendete eine gepufferte Nachricht. Der erneute
   Abgleich mit ADR-002 führte zur Korrektur auf synchrone Korrelation. Ein gespeicherter
   Auftrag allein ist keine positive Übergabebestätigung. Verlorene Antworten behalten
   ihre Operationskennung und dürfen keine zweite Wirkung erzeugen.
2. Engine-Suchdaten können nach Jobaktivierung noch fehlen. Die Integrationstests
   warten gezielt auf Metadaten; Worker behandeln technische Lesefehler als Retry.
3. Ein Prüfgrund darf durch eine zwischenzeitliche Memo-Speicherung nicht verloren
   gehen. Die Fortsetzung prüft deshalb den aktuellen Status und Call-Bezug, ohne
   eine alte Fallversion pauschal zurückzuschreiben.
4. Ein späteres Systemergebnis überschreibt keine manuelle Dringlichkeit. Kritische
   neue Systemhinweise bleiben dennoch verpflichtender Prüfbedarf; eine niedrige
   manuelle Stufe erledigt diesen nicht.
5. Die kombinierte Suite erschöpfte zunächst das Verbindungslimit der Testdatenbank.
   Nur für Surefire sind die Hikari-Pools auf vier Verbindungen und ohne Mindestreserve
   begrenzt. Die anschliessende vollständige Backend-Abnahme bestand.
6. Hintergrundaktualisierung ersetzt nur Lesebereiche. Offene Formulare und ihr
   Aktionszustand bleiben erhalten; die Scrollposition des Verlaufs wird übertragen.
7. Derselbe native Entscheidungsauftrag darf auch bei veralteten Engine-Suchdaten
   keine zweite Completion senden. Ein bekannter Auftrag wird deshalb vor einem
   weiteren Engine-Aufruf abgewiesen; sein bestätigter Nachweis wird nicht durch
   eine verspätete Ablehnung überschrieben. Eine neue bewusste Entscheidung benötigt
   erneut geladene Freigabe und eine neue Operationskennung.
8. Fehler-Variablen an der Abschluss-Boundary bleiben ohne Output-Mapping lokal.
   Der reale Konflikttest deckte auf, dass der nächste Claim dadurch die alte
   Staff-Referenz verwendete. Das explizite Mapping ist in Original und Fixture
   ergänzt; der Test prüft neue Kindinstanz, Ablehnung des alten Aufgaben-Keys
   und anschliessenden erfolgreichen Abschluss.

9. Die Formularverträge unterscheiden Speicher- und Übergabebestätigung.
   Das Memo wartet begrenzt auf den Worker-Commit; Dringlichkeit und Zuordnung
   bestätigen die synchrone Korrelation. Eine bestätigte Übergabe wird gespeichert
   und nicht erneut gesendet. Eindeutige Korrelationsablehnung gibt die Absende-ID
   wieder frei, ohne einen alten Workerauftrag wieder wirksam zu machen.
10. Der bisherige Kontaktmaskierer zeigte die vollständige Domain. Der Backend-
    Lesevertrag liefert jetzt die in Ansicht 01 festgelegte kurze Endung; kurze
    Adressen bleiben ebenfalls maskiert. Versanddaten werden nicht geändert.

11. Die Mitarbeitersubscription muss ihren Schlüssel bereits beim Kindstart kennen.
    Eine erst im Einstiegsworker erzeugte Variable führte zum Engine-Incident. Die
    Korrelation verwendet jetzt die dauerhafte `entryContextRef`; zusätzlich werden
    die tatsächliche Zielinstanz und ihr Eltern-Call geprüft. Die Fixture hält den
    Abklärungs-Nebenpfad direkt in ActiveCase, wie das Originalmodell.

## Verbleibende Arbeit

Der Mailprozess ist ausführbar. Die sechs übrigen Gesamtmodelle sind weiterhin
keine freigegebene ausführbare Gesamtlösung. Die Tests deployen begrenzte Fixtures,
die an unimplementierten Folgeaktionen warten. Der Workflow bleibt standardmässig
deaktiviert; ein vorhandener Testprozess ist kein Produktionsdeployment.

Offen bleiben insbesondere Empfehlung/RAG und zentrale Nachrichten-Neubewertung,
die übrigen externen Kommunikationspfade, weitere Mitarbeiterentscheidungen,
Mieterwartephase, Wiedervorlage, externe Wartephase, Abschluss aus diesen Phasen,
Systemabschluss und Wiedereröffnung
sowie die vollständige B07/B09/B10-Abnahme und der OpenAPI-Abgleich der übrigen
Mitarbeiteraktionen. Der native Fortsetzungsadapter nutzt weiterhin `/fortsetzen`;
Stammdatenreferenzen für Objekt/Wohnung sind noch nicht angebunden. Die UI-Demo ersetzt diese Implementierung nicht.
Die Modelltests verwenden kontrollierte Antworten, keine kostenpflichtigen externen
LLM-Aufrufe und keinen Nachweis semantischer Modellqualität.
