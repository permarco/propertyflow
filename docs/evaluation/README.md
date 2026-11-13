# Evaluation und Prüfkriterien

Dieser Ordner beschreibt, **was PropertyFlow erfüllen soll und wie dieses Verhalten geprüft wird**. Er enthält die Evaluations- und Sicherheitsbasis sowie geplante Prüffälle für KI-Funktionen und den Fallprozess.

Prüfkriterien und geplante Tests sind noch keine bestandenen Nachweise. Die Dokumente unterscheiden zwischen erwartetem Verhalten, vorhandenen Tests und tatsächlich ausgeführten Prüfungen. Verbindliche Fachregeln bleiben in den jeweils verlinkten Spezifikationen massgebend.

## Dokumente

| Datei | Zweck |
|---|---|
| [Evaluations- und Sicherheitsbasis](evaluationsgrundlage.md) | Qualitative KI-Prüffälle, Akzeptanzkriterien, Vertrauensgrenzen, Guardrails und menschliche Freigaben; ergänzt um die Einordnung der Frontend-Testabdeckung. |
| [Prüffälle zum Fallprozess](fallprozess-prueffaelle.md) | Geplante Integrationstests für PropertyFlow und Camunda: private Lampe, Lüftungsfilter und unklare Zuständigkeit; einschliesslich erwarteter Zustände und Wiederholungsprüfungen. |

## Historische Nachweise aus Block 2

Die folgenden Dateien dokumentieren lokale Demo-Prüfungen vom 12.10.2026. Sie belegen keinen aktuellen Produktivstand. Messbedingungen, Ergebnisse und Grenzen sind im [Block-2-Prüfprotokoll](../ai-usage/block2-oberflaechen-pruefung.md) erläutert.

| Datei | Inhalt |
|---|---|
| [Browserbericht](evidence/block2/browser.json) | Ergebnisse der Browserprüfungen, unter anderem zur Barrierefreiheit. |
| [Performance-Messbericht](evidence/block2/performance.json) | Einzelmessungen und Messprofil der Performance-Prüfung. |
| [Screenshot der sicheren Textausgabe](evidence/block2/stream-safe-text.png) | Visueller Nachweis zur Darstellung gestreamter Inhalte als Text. |

## Abgrenzung zu AI Usage

[AI Usage](../ai-usage/README.md) dokumentiert den KI-Einsatz bei der Entwicklung, Reviews, Korrekturen, Erkenntnisse und ausgeführte Nachweise. Hier stehen die Prüfgrundlagen und erwarteten Ergebnisse. Erkenntnisse aus einer Prüfung werden im passenden Prüfprotokoll festgehalten; daraus abgeleitete Änderungen an Fachregeln oder Prüfkriterien werden in deren zuständigen Dokumenten nachgeführt.
