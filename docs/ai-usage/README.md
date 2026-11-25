# KI-Nutzung, Reviews und Erkenntnisse

Dieser Ordner dokumentiert, **wie KI bei der Entwicklung von PropertyFlow eingesetzt wurde und welche Erkenntnisse daraus entstanden sind**. Dazu gehören geprüfte Vorschläge, Korrekturen, bewusst verworfene Ergebnisse, technische Experimente und ausgeführte Prüfnachweise mit ihren Grenzen.

Datierte Protokolle beschreiben den jeweils geprüften Stand. Sie sind keine pauschale Aussage über den heutigen Implementierungsstand. Verbindliche Fachregeln und Architekturentscheidungen werden in den zuständigen Spezifikationen und ADRs gepflegt und von hier aus referenziert.

## Dokumente

| Datei | Zweck |
|---|---|
| [Block 1: Grundgerüst und Review](block1-grundgeruest-pruefung.md) | KI-Einsatz beim Aufbau des Projektgrundgerüsts, Prüfung der Ergebnisse und bewusst durch den Entwickler getroffene Entscheidungen. |
| [Block 2: Oberflächen und Prüfprotokoll](block2-oberflaechen-pruefung.md) | Dokument- und UI-Reviews, fachliche Abweichungen und Korrekturen sowie Unit-Test-, Mutations-, Browser- und Performance-Nachweise. |
| [KI-Service: Vertrag und lokaler Versuch](ki-service-vertrag.md) | Java-Vertrag des isolierten Triage-Prototyps, Reproduktion mit Ollama, Messungen, Validierungsgrenzen und Veto-Notiz. |
| [INITIAL-Workflow: Umsetzung und Prüfung](initial-workflow-pruefung-2026-11-15.md) | Persistenter Start, Camunda-Zuordnung und erster Statuscommit; PostgreSQL-/Engine-Nachweise und verbleibende Grenzen. |
| [Backend-Fortsetzung: Prozesse, Kommunikation und Falldaten](backend-fortsetzung-pruefung-2026-11-15.md) | Mailversand, Triage, native Mitarbeiterentscheidungen, Mieternachrichten, Mitarbeiterkommunikation und Falldaten; Integrations- und Browsernachweise. |
| [Antwortauswertung und Falllebenszyklus](antwortauswertung-und-lebenszyklus-pruefung-2026-11-15.md) | Zentrale Auswertung, Empfehlung, Wartepfade, Terminänderung, Wiedereröffnung und manuelle Aufhebung von Antwortbedarf; aktuelle Prüfungen und verbleibende Laufzeitlücken. |
| [Abschlussbewertung und individuelle Mitteilung](abschluss-evaluation-2026-11-16.md) | Echte lokale Modellläufe, gezielte Fehlerkorrekturen, Textsichtung und weiterhin gesperrte automatische Abschlüsse. |
| [Datenbank-Initialskripte nach Modulen](datenbankmodule-pruefung-2026-11-16.md) | Schemaabgleich, Erhalt der historischen Migrationskette und Neuaufbau der isolierten Testdatenbank. |
| [Diff-Review: gemeinsame E-Mail-Prüfung](diff-review-1f315c4.md) | Repo-weite Änderung aus Commit `1f315c4`: Rahmen, Ausgangslage, Befunde und Bewertung je Datei. |
| [Diff-Review: BPMN-Umbau](diff-review-bpmn-cleanup.md) | Repo-weiter BPMN-Umbau aus PR #46 mit den Vorgaben des Entwicklers: Neuzeichnung, Konstanten, Farben und eigenes LLM-Modell. |

## Zugehörige Nachweisdateien

Die historischen Nachweise zum Block-2-Prüfprotokoll liegen derzeit unter `docs/evaluation/evidence/block2/`:

- [Browserbericht](../evaluation/evidence/block2/browser.json)
- [Performance-Messbericht](../evaluation/evidence/block2/performance.json)
- [Screenshot der sicheren Textausgabe](../evaluation/evidence/block2/stream-safe-text.png)

## Abgrenzung zur Evaluation

[Evaluation](../evaluation/README.md) definiert, was geprüft werden soll und anhand welcher Kriterien das Verhalten beurteilt wird. Dieser Ordner hält fest, was gemacht und geprüft wurde und welche Erkenntnisse daraus entstanden sind. Neue Erkenntnisse gehören zum passenden Thema oder Prüfprotokoll; ändern sie Anforderungen oder Architekturentscheidungen, müssen auch deren zuständige Quellen nachgeführt werden.


[Konsolidierungsstand und aktiver Betriebsauftrag](konsolidierungsstand-2026-11-16.md) hält die Rückstellung der Modellevaluation und den aktuellen Auftrag für eine produktionsnahe lokale Version fest.
