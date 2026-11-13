# KI-Nutzung, Reviews und Erkenntnisse

Dieser Ordner dokumentiert, **wie KI bei der Entwicklung von PropertyFlow eingesetzt wurde und welche Erkenntnisse daraus entstanden sind**. Dazu gehören geprüfte Vorschläge, Korrekturen, bewusst verworfene Ergebnisse, technische Experimente und ausgeführte Prüfnachweise mit ihren Grenzen.

Datierte Protokolle beschreiben den jeweils geprüften Stand. Sie sind keine pauschale Aussage über den heutigen Implementierungsstand. Verbindliche Fachregeln und Architekturentscheidungen werden in den zuständigen Spezifikationen und ADRs gepflegt und von hier aus referenziert.

## Dokumente

| Datei | Zweck |
|---|---|
| [Block 1: Grundgerüst und Review](block1-grundgeruest-pruefung.md) | KI-Einsatz beim Aufbau des Projektgrundgerüsts, Prüfung der Ergebnisse und bewusst durch den Entwickler getroffene Entscheidungen. |
| [Block 2: Oberflächen und Prüfprotokoll](block2-oberflaechen-pruefung.md) | Dokument- und UI-Reviews, fachliche Abweichungen und Korrekturen sowie Unit-Test-, Mutations-, Browser- und Performance-Nachweise. |
| [KI-Service: Vertrag und lokaler Versuch](ki-service-vertrag.md) | Java-Vertrag des isolierten Triage-Prototyps, Reproduktion mit Ollama, Messungen, Validierungsgrenzen und Veto-Notiz. |

## Zugehörige Nachweisdateien

Die historischen Nachweise zum Block-2-Prüfprotokoll liegen derzeit unter `docs/evaluation/evidence/block2/`:

- [Browserbericht](../evaluation/evidence/block2/browser.json)
- [Performance-Messbericht](../evaluation/evidence/block2/performance.json)
- [Screenshot der sicheren Textausgabe](../evaluation/evidence/block2/stream-safe-text.png)

## Abgrenzung zur Evaluation

[Evaluation](../evaluation/README.md) definiert, was geprüft werden soll und anhand welcher Kriterien das Verhalten beurteilt wird. Dieser Ordner hält fest, was gemacht und geprüft wurde und welche Erkenntnisse daraus entstanden sind. Neue Erkenntnisse gehören zum passenden Thema oder Prüfprotokoll; ändern sie Anforderungen oder Architekturentscheidungen, müssen auch deren zuständige Quellen nachgeführt werden.
