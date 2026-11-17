# Prüfung und Migration der überarbeiteten BPMN-Modelle

Stand: 16.11.2026. Lokale Entwicklungsumgebung, Camunda 8.9.19.

## Vergleich und Korrektur

Die sieben Anwendungsmodelle wurden gegen die zuvor deployte Version 1 verglichen.
Die neuen Link-Ereignisse wurden innerhalb ihres jeweiligen Scopes aufgelöst;
unbedingte XOR-Zusammenführungen wurden beim Pfadvergleich durchlaufen.
Verglichen wurden die erreichbaren fachlichen Ziele samt Bedingungen sowie
Aufgabentypen, Scopes, Worker-Bindungen, Call-Bindungen, Datenübergaben und
Ereignisbedingungen. Unterschiede in Positionen, Namen, Dokumentation und
technischen IDs der Ereignisdefinitionen wurden davon getrennt behandelt.

Im Hauptprozess war die Verzweigung `StaffRoute` mit dem bedingten Abschlussweg
`F16` entfernt worden. `Merge_Close` hatte dadurch zwei Ausgänge und das
Deployment wurde abgelehnt. Wiederhergestellt wurden:

- `Staff → StaffRoute`;
- `staffOutcome = "CLOSE" → Merge_Close → Close`;
- `staffOutcome = "WAIT_FOR_REPLY" → Merge_Reply`;
- der separate Abschlussrückweg `CR06 → Merge_Close`.

Das neue Layout und die Link-Ereignisse bleiben erhalten. Nach der Korrektur
ergab der beschriebene Vergleich für alle sieben Modelle keine Abweichung der
fachlichen Pfade oder der geprüften Aufgaben-/Ereignisverträge.

## Tests und Deployment

`InitialWorkflowBindingTest` und `ReviewWorkflowBindingTest`: 21 Tests bestanden.
Die Wiedervorlage-Tests prüfen Rückwege auch über Link-Ereignisse bis zur
expliziten Zusammenführung vor dem Ereignis-Gateway. Die Assertions auf
eindeutigen Link-Empfang im selben Scope bleiben verbindlich.

Camunda akzeptierte alle sieben Modelle als Version 3. Der Hauptprozess hat
den Definitionsschlüssel `2251799813846910`, der Mailprozess
`2251799813846909`. Die lokale `runtime.properties` verweist auf diese Versionen.

## Migration und Nachkontrolle

38 aktive Instanzen wurden mit identischen Element-IDs auf Version 3 migriert:

| Prozess | Anzahl |
| --- | ---: |
| Hauptprozess | 19 |
| Mitarbeiterbearbeitung | 18 |
| Mieterantwort | 1 |

Eine Hauptinstanz war zwischenzeitlich bereits auf Version 2; ihr Ausgangsstand
wurde vor der Migration erneut abgeglichen. Alle übrigen Instanzen verwendeten
Version 1. Haupt- und aufgerufene Kindinstanzen wurden separat migriert.
Bestehende Randereignisse, Ereignis-Teilprozessstarts und Scopes wurden in die
Zuordnung aufgenommen, um ihre Abonnements zu erhalten.

Nachkontrolle: alle 38 Instanzen aktiv auf Version 3, keine Incidents. Die 57
aktiven Elemente einschliesslich ihrer Elementinstanzschlüssel sind unverändert.
PropertyFlow wurde mit der neuen Konfiguration gestartet; Health meldet `UP`.
Es wurden keine Instanzen oder Daten gelöscht.

Lokale Detailbelege liegen unter `target/camunda-migration/` (nicht versioniert).
Der Nachweis umfasst Modellvergleich, Bindungstests, Deployment und Migration;
ein erneuter vollständiger fachlicher End-to-End-Durchlauf aller Prozesspfade
wurde dabei nicht ausgeführt.
