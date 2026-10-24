# KI-Baustein: KI-Aufruf als Service/Tool

Stand: 23.10.2026. Lernbaustein im Modul `triage`, ohne HTTP-Freigabe oder
Änderung fachlicher Falldaten. Grundlage sind ADR-001, die Modulstruktur und
DRING-01/02. Die bestehenden Oberflächen bleiben eine separate Demo.

## Aufruf und Vertrag

```java
AnalyzeTool.Response analyze(AnalyzeTool.Request request)
```

Das Eingabeschema ist der Java-Record `Request(String text)`: Request und Text
müssen vorhanden sein, der Text darf nicht ausschliesslich aus Whitespace
bestehen und höchstens 4000 UTF-16-Codeeinheiten umfassen. Die Längenprüfung
erfolgt vor dem Entfernen äusserer Leerzeichen. Ungültige Eingaben verursachen
keinen Modellaufruf.

Die versiegelte Antwortschnittstelle erlaubt genau zwei Varianten:

| Antwort | Felder und Bedeutung |
|---|---|
| `Success` | `urgency`: `KRITISCH`, `HOCH`, `NORMAL` oder `NIEDRIG` |
| `Failure` | `code`: Fehlercode; `message`: feste, verständliche Meldung |

| Fehlercode | Meldung |
|---|---|
| `INVALID_INPUT` | text muss 1 bis 4000 Zeichen enthalten und darf nicht leer sein. |
| `MODEL_UNAVAILABLE` | KI-Analyse derzeit nicht verfügbar. Bitte manuell prüfen. |
| `INVALID_MODEL_OUTPUT` | KI-Antwort entspricht nicht dem Vertrag. Bitte manuell prüfen. |

Es handelt sich um einen Java-Servicevertrag, nicht um einen HTTP-/JSON-Vertrag.
`AnalysisModel` kapselt den Provider; `SpringAiAnalysisModel` nutzt Spring AI
2.0.1. Systeminstruktion und Nutzereingabe werden als getrennte Nachrichten
übergeben. Die Ausgabe wird gegen die vier erlaubten Werte geprüft; zusätzliche
Erklärungen, HTML, unbekannte Werte und fehlende Ausgaben werden abgelehnt.
Fehler erzeugen keine erfundene Standarddringlichkeit. Provider-Ausnahmen
werden weder als Nachricht noch als Stacktrace im Ergebnis ausgegeben.

## Reproduzieren mit lokalem Ollama

Der opt-in Integrationstest enthält die vollständige Konstruktion von
`OpenAiChatModel`, Adapter und Tool. Er verwendet Ollamas kompatible lokale
Schnittstelle `http://localhost:11434/v1`, das Modell `qwen3.5:4b`, maximal
120 Sekunden Wartezeit und keine automatischen Wiederholungen. `ollama` ist
ein technischer Platzhalter, kein OpenAI-Schlüssel; es wird kein OpenAI-Dienst
aufgerufen. Die Modellwahl kann mit `-Dollama.model=...` geändert werden.

Für diesen kurzen Klassifikationsaufruf ist `reasoningEffort("none")` gesetzt
und die Ausgabe auf 32 Tokens begrenzt. Ollamas kompatible Schnittstelle
unterstützt die [Steuerung des Denkmodus über reasoning_effort](https://github.com/ollama/ollama/blob/main/docs/api/openai-compatibility.mdx).
Am 23.10.2026 dauerte der optimierte Modellaufruf 1,67 Sekunden und die
Live-Testmethode 2,30 Sekunden gegenüber zuvor 35,64 Sekunden. Alle fünf
gezielt ausgeführten Tests bestanden; das Ergebnis blieb `KRITISCH`.
Dies sind einzelne lokale Messungen, kein kontrollierter Benchmark:
Modellladezeit, Cache und Rechnerauslastung können die Laufzeit beeinflussen.
Die Ausgabevalidierung bleibt aktiv. Für andere Modelle und fachlich komplexere
Fälle muss die Qualität ohne Denkmodus separat geprüft werden.

```powershell
.\mvnw.cmd test '-Dtest=AnalyzeToolTest'
.\mvnw.cmd test '-Dtest=OllamaAnalyzeToolTest' '-Dollama.live=true' '-DargLine=-Djdk.net.unixdomain.tmpdir=C:\Dev\propertyflow\target'
```

Der Live-Test ruft `analyze` einmal mit dem synthetischen Text
«Im Keller platzt ein Wasserrohr. Wasser strömt ungehindert aus.» und einmal
mit `"   "` auf. Er prüft einen typisierten Erfolg, `INVALID_INPUT` und
genau einen Modellaufruf. Reguläre Tests decken zusätzlich Nullwerte,
Längengrenzen, Provider-Ausfall mit geheimen Fehlerdetails und ungültige
Modellausgaben ab. Der Live-Test wird ohne ausdrückliches Opt-in übersprungen.

## Grenzen und Review

Ausgeführter Live-Nachweis vom 23.10.2026, 23:02 Europe/Zurich:

```text
Ollama success call: Success[urgency=KRITISCH]
Ollama invalid call: Failure[code=INVALID_INPUT, message=text muss 1 bis 4000 Zeichen enthalten und darf nicht leer sein.]
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Modell: `qwen3.5:4b`, lokaler Digest
`d8b0f5e9760cd1682034f292d7ef72ec46f432149be0df7574bf2d6e92e38c04`.
Der Test ist ein Integrations- und Vertragsnachweis, keine Evaluation der
allgemeinen Klassifikationsqualität.

Die gesamte Java-Testreihe wurde anschliessend erfolgreich ausgeführt:
33 Tests, 0 Fehler, 0 Fehlschläge, 1 erwarteter Skip des separat ausgeführten
Live-Tests. Der erste Sandbox-Lauf war beim Mockito-Self-Attach blockiert;
der Wiederholungslauf mit lokalen Rechten war erfolgreich.

Dieser Baustein liefert einen Vorschlag und führt keine Fallabschlüsse,
Beauftragungen oder Änderungen an manuellen Einstufungen aus. Formale
Ausgabevalidierung beweist keine fachliche Richtigkeit und verhindert nicht
jede semantische Prompt-Injection. Nur synthetische, nicht personenbezogene
Testdaten wurden für den Nachweis vorgesehen; eine automatische Anonymisierung
ist nicht implementiert.

Vor einer produktiven Einbindung müssen Intake-Persistenz vor der Analyse,
Datenminimierung, Auditpersistenz (Modellversion, Prompt-Version
`triage-urgency-v1`, Zeitpunkt und spätere Entscheidung), fachliche Freigaben
und Camunda-Anbindung umgesetzt werden. Das Tool ist bewusst nicht als
öffentlicher Endpoint oder automatisch registrierter LLM-Tool-Callback exponiert.

## Veto-Notiz

Der erste KI-generierte Adaptertest verwendete APIs einer älteren Spring-AI-Version und verletzte damit den vorgegebenen Projektvertrag; der Compiler deckte dies auf, und der Code wurde auf 2.0.1 korrigiert.
Ein Veto gegen die produktive Freigabe bleibt bestehen, weil der kleine Baustein noch keine Datenminimierung und dauerhafte Auditierung durchsetzt.
Die feste Fehlerantwort und die Prüfung erlaubter Ausgabewerte sind umgesetzt, ersetzen aber weder diese Sicherheitsmassnahmen noch eine fachliche Prüfung der KI-Bewertung.
