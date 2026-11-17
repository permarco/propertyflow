# Evaluation der automatischen Abschlussgründe

Fachquelle: [FALL-07](../specifications/fallverwaltung.md#regeln-für-den-automatischen-fallabschluss).
Die nachfolgende Prüfung untersucht Abschlussvorschläge. Sie ersetzt nicht die
aktuellen Status-, Prozess-, Rechte-, Veröffentlichungs- und Nebenläufigkeitsprüfungen.

## Implementierte technische Prüfung

`ClosureAssessmentService` akzeptiert nur strukturierte Vorschläge mit exakten
Belegstellen aus tatsächlich übergebenen öffentlichen Mieternachrichten. Für
«Problem behoben» müssen Behebung, fehlender Hilfebedarf, der gesamte Sachverhalt
und Widerspruchsfreiheit jeweils belegt werden. Mitarbeiter- und Systemtexte
können die Bestätigung des Mieters nicht ersetzen. Unbekannte Kriterien, identische Belegkopien, erfundene Nachrichten,
veränderte Zitate und unvollständige Kriterienabdeckung
ergeben keine positive Freigabe.

Für «kein Bearbeitungsbedarf der Verwaltung» müssen zusätzlich Regelkennung und
Version sowie alle Voraussetzungen und ausgeräumten Ausnahmen belegt werden.
`ApprovedClosureRules` liest ausschliesslich ausdrücklich freigegebene, aktuell
gültige Regeln. Eine gewöhnliche RAG-Quelle mit `approved: true` genügt nicht.
Es wurden keine produktiven Zuständigkeitsregeln erfunden oder freigegeben.

Ab `closure-ground-validation-v3` darf ein Kriterium mehrere unterschiedliche
Originalbelege besitzen, etwa aus getrennten Bestätigungen zweier Teilprobleme.
Alle Pflichtkriterien müssen weiterhin vorkommen; insgesamt gelten höchstens
64 Einträge. Identische Belegtripel werden abgewiesen. Die früheren Berichte
werden durch diese Vertragskorrektur nicht nachträglich umgewertet.

Diese Prüfung bestätigt **nicht**, dass ein vorhandenes Zitat die vorgeschlagene
Einordnung tatsächlich trägt. Dafür dient der echte Modelllauf.

Die Abschlussbewertung ist inzwischen in die zentrale Antwortauswertung eingebunden.
Sie verwendet deren öffentlichen Nachrichtenstand, Fallversion, Prozessgeneration
und Versuchssperre. Ein Ergebnis wird zusammen mit Analyse und Empfehlung dauerhaft
gespeichert; die Bewertung wird nicht je Prozesspfad erneut aufgerufen. Veraltete
und nach Abschluss eintreffende Ergebnisse werden verworfen. Frühere gespeicherte
Auswertungen ohne Abschlussfeld bleiben lesbar. Modell-, Prompt- und Prüfversion
bleiben im Vorschlag nachvollziehbar.

Bis zum erfolgreichen fachlichen Nachweis führen positive oder unklare Vorschläge
zur Mitarbeiterprüfung. Technische Bewertungsfehler erzeugen einen Prüfhinweis;
bereits zuverlässig ermittelte Antworten und Dringlichkeit bleiben erhalten.
Dieser Rückweg erzeugt keine Abschlussnachricht und keinen Systemabschluss.
In Mitarbeiterprüfung, Bearbeitung, Wiedervorlage und externer Wartephase wird
keine automatische Abschlussbewertung angefordert. Erkennbar vorrangiger menschlicher
Entscheidungsbedarf aus Analyse oder Empfehlung unterbindet sie ebenfalls.

## Versionierte synthetische Evaluationsbestände

| Bestand unter `src/test/resources/evaluation/` | Umfang | Prüfschwerpunkt |
|---|---:|---|
| `closure-ground-v1.json` | 24 | Unveränderter ursprünglicher Bestand: 4 eindeutige Behebungen, 20 negative/mehrdeutige Verläufe. |
| `closure-ground-additional-v1.json` | 14 | 7 vollständige Bestätigungen, darunter sinngemässer fehlender Hilfebedarf, mehrere Nachrichten und frühere Fehlversuche; 7 Gegenbeispiele. |
| `closure-rule-v1.json` | 11 | Ursprüngliche Regelsuite: 2 vollständig belegte Anwendungen, 9 Gegenbeispiele. |
| `closure-rule-v2.json` | 13 | Zusätzlich 2 vollständige Behebungen bei vorhandenem, aber nicht anwendbarem Regelbestand. |
| `closure-message-v1.json` | 27 | Erster Kommunikationsbestand: 5 Generierungen, 4 zulässige und 15 unzulässige Entwürfe, 3 formale Grenzen. |
| `closure-message-v2.json` | 30 | Zusätzlich 1 zulässiger Türtext und 2 veraltete Störungsaussagen; verschärfte Begriffsprüfung des generierten Türtexts. |
| `closure-message-v3.json` | 32 | Zusätzlich ein erfundenes und ein tatsächlich belegtes Datum. |
| `closure-message-v4.json` | 33 | Zusätzlich ein unausgefüllter Datumsplatzhalter in einem sonst zutreffenden Türtext. |
| `closure-message-v5.json` | 34 | Zusätzlich ein kontrollierter zulässiger Text mit echten Absätzen. |

Die Regel `EVAL-PRIVATE-TIMER/fixture-v1` ist ausschliesslich eine Testannahme.
Sie liegt in keinem produktiven Wissensverzeichnis. Positive synthetische
Regelprüfungen ersetzen keinen fachlich freigegebenen produktiven Regelbestand.

Alle positiven Bewertungsfälle müssen den erwarteten formal belegten Abschlussgrund
liefern. Negative und mehrdeutige Fälle dürfen keinen Abschlussvorschlag erzeugen.
Technische Fehler und ungültige Modellausgaben zählen **auch bei Negativfällen nicht**
als bestandene fachliche Einordnung. Die Belegprüfung wird nicht gelockert, um
fehlerhaft kopierte Zitate zu akzeptieren.

Bei der Kommunikation werden zwei Methoden getrennt:

- `GENERATE` verwendet das echte Modell sowohl zur individuellen Formulierung als
  auch zur getrennten Inhaltsprüfung im produktiven `ClosureMessageService`.
- `CHECK_DRAFT` gibt einen kontrollierten Entwurf vor und verwendet das echte Modell
  für die Inhaltsprüfung desselben Diensts. Bei inhaltlichen Negativfällen muss eine
  formal gültige Ablehnung mit dem korrekten Entwurfsdigest vorliegen. Ein Timeout,
  falsches JSON oder ein falscher Digest gilt nicht als erfolgreiche Inhaltsprüfung.
- Die drei Fälle der Kategorie `format` prüfen separat Fragen, Links und HTML.
  Sie werden bereits vor einem Validierungsaufruf zurückgewiesen und zählen nicht
  als semantischer Modellnachweis.

Geprüft werden unter anderem erfundene Reparaturen und Ursachen, Kostenversprechen,
Aufträge, neue Handlungsanweisungen, interne Inhalte, falscher Gegenstand, fehlender
Fallbezug, falscher Abschlussgrund, spätere Einwände, offene Teilprobleme und
Anweisungen zur Umgehung der Prüfung. Die negativen Kontextfälle verwenden bewusst
überholte oder unzureichende synthetische Eingangsbewertungen, damit die
Inhaltsprüfung diese nicht blind übernimmt.

Automatische Begriffsprüfungen bei generierten Texten ergänzen die Inhaltsprüfung;
sie ersetzen keine fallbezogene Sichtung der tatsächlich erzeugten Texte. Eine
Selbstprüfung durch dasselbe Modell ist keine unabhängige Referenzbewertung.
Alle Bestände sind Entwicklungsregressionen, kein statistischer Nachweis allgemeiner
Zuverlässigkeit. Nach Promptanpassungen sind sie insbesondere keine unangetastete
Validierungsmenge mehr. Erwartete Ergebnisse bleiben unverändert.

## Historische Modellläufe

Alle folgenden Berichte bleiben erhalten, einschliesslich ihrer Fehlschläge:

| Prompt | Ursprünglicher Bestand | Ergänzende Verläufe | Synthetische Regeln |
|---|---:|---:|---:|
| v1 | [21/24](evidence/closure/qwen35-4b-2026-11-16.json) | – | – |
| v2 | [21/24](evidence/closure/qwen35-4b-2026-11-16-v2.json) | – | – |
| v3 | [22/24](evidence/closure/qwen35-4b-2026-11-16-v3.json) | – | – |
| v4 | [20/24](evidence/closure/qwen35-4b-2026-11-16-v4.json) | [13/14](evidence/closure/qwen35-4b-2026-11-16-additional-v4.json) | [9/11](evidence/closure/qwen35-4b-2026-11-16-rule-v4.json) |
| v5 | [23/24](evidence/closure/qwen35-4b-2026-11-16-v5.json) | [12/14](evidence/closure/qwen35-4b-2026-11-16-additional-v5.json) | [7/11](evidence/closure/qwen35-4b-2026-11-16-rule-v5.json) |
| v6 | [20/24](evidence/closure/qwen35-4b-2026-11-16-v6.json) | [13/14](evidence/closure/qwen35-4b-2026-11-16-additional-v6.json) | [7/11](evidence/closure/qwen35-4b-2026-11-16-rule-v6.json) |
| v7, `none` | [22/24](evidence/closure/qwen35-4b-2026-11-16-v7.json) | [14/14](evidence/closure/qwen35-4b-2026-11-16-additional-v7.json) | [9/13](evidence/closure/qwen35-4b-2026-11-16-rule-v7.json) |

v4 enthält weiterhin einen unzulässigen Abschluss bei unklarem Bezug auf mehrere
Probleme, vier formal ungültige Antworten über beide Verlaufsbestände und zwei
falsch negative Regelentscheidungen. Zusammen sind 42 von 49 Bewertungsfällen
bestanden; die Gesamtbewertung ist **nicht bestanden**. Ab v4 werden die rohen
Modellantworten aus den synthetischen Eingaben mitgespeichert. Frühere Fehlerursachen
werden nicht nachträglich aus nicht vorhandenen Rohantworten abgeleitet.

Auch v5 besteht insgesamt 42/49 Fälle und ist **nicht bestanden**. Im ursprünglichen
Bestand bestehen alle 20 Negativ-/Mehrdeutigkeitsfälle; eine klare Behebung wird wegen
einer fälschlich verlangten Zuständigkeitsregel abgelehnt. Bei den Zusatzfällen
scheitern zwei gültige Behebungen an mehreren unterschiedlichen Belegen desselben
Kriteriums. In der Regelsuite werden drei unzulässige Abschlüsse vorgeschlagen;
eine weitere Antwort ist formal ungültig. Die verbesserte Trefferzahl des ursprünglichen
Bestands darf diese neu sichtbar gewordenen Regelfehler nicht verdecken.

Der erste [Kommunikationslauf v2](evidence/closure/qwen35-4b-2026-11-16-message-v2.json)
auf dem Kommunikationsbestand v1 besteht 24/27 automatisierte Fälle. Ein generischer
Text wird falsch akzeptiert; zwei fachliche Ablehnungen liefern falsche Digests.
Die [separate Sichtung](evidence/closure/qwen35-4b-2026-11-16-message-v2-review.json)
entdeckt ausserdem einen falschen generierten Türtext, den die lokale Modellprüfung
akzeptiert hat. Nur 4/5 generierte Texte bestehen diese Sichtung. Der Gesamtnachweis
ist damit ausdrücklich **nicht bestanden**. Bestand v2 ergänzt diese beobachtete
Lücke; der alte Bestand und sein Bericht bleiben unverändert erhalten.

Der [Kommunikationslauf v3](evidence/closure/qwen35-4b-2026-11-16-message-v3.json)
auf Bestand v2 besteht 21/30 Fälle. Vier Generierungen liefern irrtümlich eine
Prüfantwort; zwei Ablehnungen enthalten falsche Digests, drei unzulässige Entwürfe
werden akzeptiert. Der einzige formal akzeptierte generierte Text enthält ein
[erfundenes Meldungsdatum](evidence/closure/qwen35-4b-2026-11-16-message-v3-review.json).
Damit besteht keiner der fünf Generierungsfälle die vollständige Textsichtung.
Die gemeinsame Systemanweisung für Erzeugung und Prüfung wird deshalb durch zwei
serverseitig ausgewählte Rollen ersetzt. Das Schema der Erzeugung lässt nur text
und den vorgegebenen Grund zu. Das Prüfschema lässt approved als freien Boolean
und ausschliesslich den konkreten Digest des geprüften Texts zu. Dies verhindert
technische Rollen-/Kopierfehler, erzwingt aber ausdrücklich keine positive Entscheidung.
Die strenge Nachprüfung im Dienst bleibt bestehen, auch wenn ein Provider das Schema
ignoriert. Schema-Unterstützung und Schema-Version gehören zur getesteten Konfiguration.

Bewertung v6 besteht insgesamt 40/49 Fälle. Mehrfachbelege beheben die früheren
unnötigen Formatfehler der positiven Zusatzfälle; es verbleiben jedoch unter anderem
unzulässige Vorschläge bei vorläufiger Besserung und einem später hinzugekommenen
Anliegen. Der formale Schutz ist kein semantischer Nachweis. Die folgende Fassung
trennt deshalb die reine Behebungsprüfung ohne Regelbestand von der erweiterten
Prüfung mit vorhandenen Regeln. Beide verwenden dieselbe strenge Quellenvalidierung.

Bewertung v7 mit Reasoning `none` besteht 45/51 Fälle: 22/24 ursprüngliche,
14/14 zusätzliche und 9/13 Regelfälle. Ein übersetztes Zitat und eine als
Mieterbestätigung ausgegebene Personalaussage werden formal abgefangen. Eine
vollständig belegte Regel wird falsch verneint. Kritisch bleibt ein formal gültiger,
aber fachlich unzulässiger RESOLVED-Vorschlag bei einem privaten, weiterhin defekten
Gerät ohne passende freigegebene Regel. Der Modelltext setzt fehlenden
Verwaltungsbedarf mit erfolgter Behebung gleich. Zwei neue Behebungsfälle mit
Regelkatalog scheitern durch Timeout beziehungsweise eine unbegründete Verneinung.
Die Gesamtbewertung bleibt **nicht bestanden**.

Der [Kommunikationslauf v4](evidence/closure/qwen35-4b-2026-11-16-message-v4.json)
auf Bestand v3 besteht 21/32 Fälle. Rollenformat und Digest sind jetzt in allen
Modellantworten korrekt; die Inhaltsprüfung lehnt jedoch sämtliche positiven
Fälle ab. Dies ist kein bestandener Nachweis, auch wenn alle Negativfälle abgewehrt
werden. Die [Textsichtung](evidence/closure/qwen35-4b-2026-11-16-message-v4-review.json)
findet zudem einen unausgefüllten Datumsplatzhalter, indirekte Ansprache und
eine interne Regelkennung. Die Kommunikationsfassung v5 entfernt unnötige
Regelmetadaten aus beiden Modellkontexten und präzisiert die versandfertige Form.

Der Versuch mit Kommunikationsfassung v5 und Reasoning `low` wurde nach
wiederholten begrenzten Generierungsaufrufen ohne Modellantwort abgebrochen.
Der [unvollständige Bericht](evidence/closure/qwen35-4b-2026-11-16-message-v5-low-incomplete.json)
enthält vier von 33 Fällen, sämtlich technische Fehler. Die
[Abbruchnotiz](evidence/closure/qwen35-4b-2026-11-16-message-v5-low-termination.json)
dokumentiert die beobachteten 60-Sekunden-Abstände und den gezielten Stopp des
eigenen Testprozesses. Es gibt daraus keinen vollständigen fachlichen Nachweis
und keine bestandenen Negativfälle. Der anschliessende vollständige Lauf verwendet
wieder `none`; der Anwendungsstandard bleibt unverändert.

Der vollständige [Kommunikationslauf v5 mit `none`](evidence/closure/qwen35-4b-2026-11-16-message-v5.json)
besteht 22/33 Fälle. Alle elf positiven Fälle werden abgelehnt; die negativen Fälle
bestehen. Die [Textsichtung](evidence/closure/qwen35-4b-2026-11-16-message-v5-review.json)
findet einen Datumsplatzhalter, einen erfundenen Reparaturakteur und indirekte
Ansprache. Eine der fünf generierten Nachrichten ist fachlich akzeptabel.

Kommunikationsfassung v6 verwendet Schema v2: Die Inhaltsprüfung soll zunächst
eine kurze quellenbezogene `explanation`, danach `approved` und `digest` liefern.
Dies macht die beobachtete pauschale Ablehnung diagnostizierbar. Die Begründung
bleibt interne, unvertrauenswürdige Modellausgabe und wird nie Bestandteil des
veröffentlichten Texts. Ein positiver Boolean bleibt frei zu bewerten; ein formal
korrekter Begründungstext ersetzt keinen semantischen Nachweis. Alte Berichte
behalten ihren damaligen Zweifeld-Prüfvertrag.

Der [Kommunikationslauf v6](evidence/closure/qwen35-4b-2026-11-16-message-v6.json)
besteht 27/33 Fälle. Die Begründung vor dem Urteil beseitigt die pauschale Ablehnung,
zeigt aber auch falsche Prüfannahmen: Behebungsfälle werden mit Zuständigkeitsregeln
verwechselt, und das Modell verlangt unzulässig zusätzliche Reparaturnachweise.
Ein generischer Text und ein erfundenes Datum werden akzeptiert. Die
[Textsichtung](evidence/closure/qwen35-4b-2026-11-16-message-v6-review.json)
bewertet drei von fünf erzeugten Mitteilungen als fachlich akzeptabel; beim Türtext
bleibt ein Objektplatzhalter, beim Wasserhahn eine unklare Behebung und indirekte Ansprache.

Ab Kommunikationsfassung v7 wählt der serverseitige Abschlussgrund zusätzlich die
passende Formulierungs- und Prüfanweisung. Die reine Behebungsprüfung erhält keine
Zuständigkeitsregelprüfung; der regelgestützte Grund erhält seine eigenen
Voraussetzungen und Ausnahmen. Gemeinsame Quellen- und Ausgabegrenzen bleiben
verbindlich. Ein neuer zulässiger Entwurf mit echten Absätzen ergänzt den Bestand.
Die vorübergehend vermutete doppelte Maskierung der erzeugten Zeilenumbrüche wurde
durch Prüfung der tatsächlichen Zeichen widerlegt; dafür besteht keine neue Sperre.

Der aktuelle [Kommunikationslauf v7](evidence/closure/qwen35-4b-2026-11-16-message-v7.json)
besteht 29/34 Fälle. Alle fünf Generierungen und sieben kontrollierten zulässigen
Entwürfe bestehen die automatisierten Prüfungen. Fünf semantische Gegenbeispiele
werden jedoch falsch akzeptiert: ein generischer Text, eine eingebettete
Freigabeanweisung, die veraltete Türstörung, ein erfundenes Datum und ein unausgefüllter
Datumsplatzhalter. Damit ist die fachliche Evaluation **nicht bestanden**.

Die [zusätzliche Textsichtung](evidence/closure/qwen35-4b-2026-11-16-message-v7-review.json)
unterscheidet Quellenbindung und Versandfertigkeit: vier von fünf Texten sind
inhaltlich quellengebunden; drei werden auch sprachlich als versandfertig bewertet.
Beim Türtext wird eine an die Verwaltung gerichtete Aussage über fehlenden
Handlungsbedarf dem Mieter selbst zugeschrieben. Der Heizungstext enthält einen
Grammatikfehler und einen Wechsel zur dritten Person. Diese Sichtung ist ein
Entwicklungsreview durch den Agenten, keine unabhängige menschliche Fachabnahme.

## Echter Modelllauf und Reproduktion

`ClosureModelEvaluationTest` und `ClosureMessageModelEvaluationTest` sind im
Standardlauf deaktiviert. Die explizite Evaluation verwendet die vorhandene
Spring-AI-Anbindung und dieselben produktiven Dienste und Systemprompts. Die
Testadapter ergänzen Rohantwortaufzeichnung und synthetische Regelquellen;
sie sind keine vollständige Workflow- oder Produktivkonfigurationsabnahme.
Der Backend-Testkontext verlangt die isolierte Datenbank `propertyflow_test`.
Die Modelltests verändern keine Fallzustände und verwenden ausschliesslich
synthetische Eingaben. Mehrere Modellläufe werden nacheinander ausgeführt.

```powershell
$env:PROPERTYFLOW_RUN_CLOSURE_EVALUATION='true'
$env:PROPERTYFLOW_TEST_DATABASE_URL='jdbc:postgresql://127.0.0.1:5434/propertyflow_test'
$env:PROPERTYFLOW_EVALUATION_BASE_URL='http://127.0.0.1:11434/v1'
$env:PROPERTYFLOW_EVALUATION_MODEL='qwen3.5:4b'
$env:PROPERTYFLOW_EVALUATION_REASONING_EFFORT='none'
$env:PROPERTYFLOW_EVALUATION_MODEL_REVISION=((Invoke-RestMethod 'http://127.0.0.1:11434/api/tags').models |
    Where-Object { $_.name -eq $env:PROPERTYFLOW_EVALUATION_MODEL }).digest
.\mvnw.cmd -B -ntp '-Dtest=ClosureModelEvaluationTest,ClosureMessageModelEvaluationTest' test
```

Getestet werden Temperatur 0, höchstens 4096 Ausgabetokens, `reasoning_effort=none`
und ein Zeitlimit von 60 Sekunden **je Modellaufruf**. Ein vom Backend verwendetes
anderes Zeitlimit, Modell, Prompt oder Generierungsprofil ist damit nicht qualifiziert.
Insbesondere ist der Standardwert von `propertyflow.triage.timeout` (20 Sekunden)
nicht mit der auf 60 Sekunden gesetzten Evaluationskonfiguration gleichzusetzen.
Der globale Anwendungsstandard wird für diese Evaluation nicht stillschweigend geändert.

Ein API-Schlüssel wird bei Bedarf nur über `PROPERTYFLOW_EVALUATION_API_KEY`
bereitgestellt und nicht im Bericht gespeichert. Abweichender Reasoning-Modus
wird über `PROPERTYFLOW_EVALUATION_REASONING_EFFORT` gesetzt und berichtet.

Die Dateien `target/closure-model-evaluation.json`,
`target/closure-additional-evaluation.json`, `target/closure-rule-evaluation.json`
und `target/closure-message-evaluation.json` enthalten Modellrevision, Prompt- und
Prüfversion, Generierungsoptionen, SHA-256 des Bestands und Einzelergebnisse.
Ein Zwischenbericht bleibt `passed: false`, auch wenn bisher alle Fälle bestanden
sind. Abgeschlossene Berichte werden unter `evidence/closure/` versioniert archiviert.
Die technischen Vertragsprüfungen stehen getrennt in `ClosureAssessmentServiceTest`,
`ApprovedClosureRulesTest`, `ClosureMessageServiceTest` und den beiden Corpus-Tests.

## Abschliessende technische Regression

Der [Paketbau vom 16.11.2026](evidence/closure/technical-regression-2026-11-16.json)
besteht mit 107 Tests ohne Fehler oder übersprungene Tests: 58 Vertrags-, Corpus-
und Adaptertests, 31 PostgreSQL-Tests sowie 18 Camunda-Tests. Diese prüfen unter
anderem die feste Sperre, Veröffentlichungsidentität, Nebenläufigkeit und Prozesspfade.
Die echte fachliche Modellevaluation bleibt davon getrennt und nicht bestanden.

## Technisch umgesetzt, fachliche Freigabe weiterhin gesperrt

Die vier Abschlussaufgaben, individuelle LLM-Mitteilung, getrennte Inhaltsprüfung,
dauerhafte Veröffentlichung/Mailpflicht und erneute Abschlussprüfung sind angebunden.
Konfliktpfade und Wiederholungsschutz werden mit synthetischer Testfreigabe geprüft.
`AutomaticClosureRelease` verweigert jede Anwendungsfreigabe; sie lässt sich nicht
über eine Konfiguration aktivieren. Ein positiver Bericht schaltet nichts frei.

Die Kommunikation erhält ausschliesslich öffentliche Nachrichten, Abschlussgrund,
exakte Belege und gegebenenfalls Geltungsbereich, Voraussetzungen und Ausnahmen
der aktuell freigegebenen Regel. Die genaue Regelkennung und Version werden
weiterhin serverseitig geprüft und auditiert, gelangen aber nicht in den
Formulierungs- oder Prüfauftrag. Freie Bewertungsbegründungen und Modellmetadaten
sind keine bestätigten Tatsachen und werden ebenfalls nicht übernommen.

Erforderlich bleiben ein fachlicher Nachweis für die tatsächlich einzusetzende
Konfiguration, Sichtung der individuellen Texte und produktiv freigegebene Regeln
für den regelgestützten Grund. Technische Tests werten frühere fehlgeschlagene
Modellläufe nicht nachträglich auf.


Der [aktuelle Entwicklungsbericht](../ai-usage/abschluss-evaluation-2026-11-16.md)
hält Korrekturen, tatsächliche Läufe, Textsichtungen und ihre Grenzen zusammen.
