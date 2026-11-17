# Abschlussbewertung und individuelle Mitteilung: fachliche Evaluation

**Auf Benutzerwunsch zurückgestellt. Aktueller fachlicher Stand: nicht bestanden.** Bewertungsfassung v7 besteht
45/51 Fälle; Kommunikationsfassung v7 besteht 29/34 Fälle. Automatische Abschlüsse
bleiben durch die feste Anwendungssperre deaktiviert. Die technischen Korrekturen
und die dokumentierten Modellfehler werden getrennt beurteilt. Der abschliessende
Paketbau und alle 107 technischen Tests sind erfolgreich.

## Auftrag und Rahmen

Die bestehenden Evaluationsfehler werden ohne Rückfragen untersucht und technisch
so weit wie möglich behoben. Automatische Abschlüsse bleiben gesperrt. Neue
Kommentare sind deutsch; Annahmen stehen zusätzlich in den
[Implementierungsannahmen](../architecture/implementierungsannahmen-2026-11-15.md).
Es werden keine Fachregeln erfunden, produktiv freigegeben oder Testmails verschickt.

Fachliche Grundlage sind FALL-07/FALL-10 und KOM-06. Insbesondere genügt eine klare
eigene Mieterbestätigung der vollständigen Behebung; ein unabhängiger Technikerbeleg
oder bestimmte Wörter sind nicht zusätzlich verlangt. Offene Teilprobleme,
Folgeschäden, Forderungen und spätere Widersprüche verhindern den Systemabschluss.
Die beiden Gründe „behoben“ und „kein Verwaltungsbedarf nach freigegebener Regel“
sind fachlich verschiedene Alternativen.

## Tatsächliche Umgebung

- Lokales Ollama-Modell `qwen3.5:4b`, Revision
  `d8b0f5e9760cd1682034f292d7ef72ec46f432149be0df7574bf2d6e92e38c04`.
- Vor dem Lauf aus `/api/tags` gelesen; keine kostenpflichtige externe Modell-API.
- `/api/ps` meldete CPU-Ausführung (`size_vram=0`) und Kontextlänge 4096.
  Diese lokale Laufzeitumgebung wurde nicht durch Treiber- oder Serveränderungen verändert.
- Spring-AI-Adapter und produktive Bewertungs-/Kommunikationsdienste,
  Temperatur 0, 4096 Ausgabetokens, 60 Sekunden pro Aufruf. Reasoning `none`
  und `low` werden als getrennte Profile ausgewertet.
- Ausschliesslich versionierte synthetische Eingaben; PostgreSQL-Testkontext auf
  Port 5434. Keine Falländerungen durch die Modellevaluation.
- Die Testadapter ergänzen Rohantworten und synthetische Regeln. Das ist keine
  Abnahme der gesamten Produktivverdrahtung. Andere Aufrufoptionen, insbesondere
  der Backend-Standard von 20 Sekunden, sind nicht mitgeprüft.

## Befunde und Korrekturen

Die älteren Läufe v1/v2 (je 21/24) und v3 (22/24) bleiben unverändert archiviert.
Der neue vollständige v4-Lauf erreicht 20/24 im ursprünglichen Bestand, 13/14 in
zusätzlichen Verläufen und 9/11 bei synthetischen Regeln: insgesamt 42/49,
**nicht bestanden**. Die Rohantworten zeigen:

- Bei einem unklaren Bezug auf mehrere ursprüngliche Probleme wird erneut
  unzulässig RESOLVED vorgeschlagen.
- Vier Antworten über beide Verlaufsbestände enthalten veränderte Zitate,
  falsche Nachrichtenbezüge oder Belege bei NONE. Die formale Prüfung lehnt sie ab.
- Zwei vollständig belegte synthetische Regeln werden zwar richtig beschrieben,
  aber mit dem falschen Ergebnis NONE beantwortet.

Die Bewertungsanweisung wird deshalb gestrafft und trennt Gesamtfallabdeckung,
zeitliche Aktualität und die beiden Abschlussgründe. Die fallbezogene Begründung
soll vor der Entscheidung die Teilprobleme benennen. Belege sollen unveränderte
Originalnachrichten verwenden; Zitat- und Regelprüfungen bleiben streng.

Der Folgelauf v5 besteht 23/24 ursprüngliche, 12/14 zusätzliche und 7/11 Regel-Fälle,
zusammen erneut 42/49. Alle ursprünglichen negativen und mehrdeutigen Verläufe werden
korrekt abgewiesen. Eine positive Behebung wird jedoch wegen einer vermeintlich
nötigen Zuständigkeitsregel verneint. Die Formulierung „ohne Regel bleibt ein Problem
offen“ war missverständlich und wird ersetzt: NONE gilt, wenn weder bestätigte
vollständige Behebung noch eine vollständig anwendbare freigegebene Regel vorliegt.
RESOLVED verlangt ausdrücklich keinen Regelbestand.

Bei drei negativen Regelbeispielen schlägt v5 unzulässig TENANT_RESPONSIBILITY vor:
unbekannter Einbau, fehlende Angaben zu Ausnahmen und tatsächlich vermietetes Gerät.
Ein später korrigierter Fall erzeugt zudem ungültige Belege. Die Rohantworten verwechseln
teilweise Ausnahmen von der Abschlussregel mit einem Ausschluss von Verwaltungshilfe.
Die neue Anweisung definiert die Richtung der Ausnahmen ausdrücklich und prüft alle
Voraussetzungen/Ausnahmen einzeln mit unbekannt als nicht ausreichend.

Der erste Kommunikationslauf mit `closure-message-v2` und Bestand v1 besteht
24/27 automatisierte Fälle. Die separate Sichtung der fünf erzeugten Texte
entdeckt zusätzlich einen fachlich falschen, vom lokalen Modell akzeptierten
Türtext: „Die Wohnungstür klemmt.“ wiederholt den ursprünglichen Zustand trotz
späterer bestätigter Behebung. Nur vier von fünf Texten bestehen diese Sichtung.
Die automatisierten Fehler sind ein akzeptierter generischer Abschlusstext und
zwei Ablehnungen mit falschem Digest. Die übrigen geprüften problematischen
Entwürfe werden korrekt zurückgewiesen.

Die Kommunikationsanweisung verlangt deshalb nun ausdrücklich den neuesten
belegten Fallstand, konkreten Fallbezug und direkte Mieteransprache. Der Digest
muss konkret kopiert werden; der missverständliche Beispielplatzhalter entfällt.
Bestand v2 behält die bisherigen Szenarien bei und ergänzt einen zulässigen
Türtext sowie zwei gezielt veraltete Störungsaussagen. Die Begriffsprüfung für
den generierten Türtext wird verschärft, ohne sie als semantischen Beweis auszugeben.

Die Bewertungsfassung v6 besteht 20/24 ursprüngliche, 13/14 zusätzliche und
7/11 Regelfälle, insgesamt 40/49. Die Mehrfachbelege sind technisch korrigiert;
alle sieben positiven Zusatzfälle bestehen. Trotzdem schlägt das Modell bei
vorläufiger Besserung und einem später neu gemeldeten Problem einen Abschluss vor.
Die Fassung v7 trennt deshalb die reine Behebungsprüfung ohne Regelbestand von
der Prüfung beider Gründe bei vorhandenen Regeln. Neue Tests prüfen insbesondere,
dass ein unpassender Regelbestand eine tatsächlich belegte Behebung nicht verhindert.

Kommunikationsfassung v3 besteht 21/30 Fälle. Vier Generierungsaufrufe verwechseln
ihren Auftrag mit der Inhaltsprüfung; zwei Prüfantworten liefern falsche Digests,
drei unzulässige Texte werden akzeptiert. Der einzige formal akzeptierte generierte
Text erfindet zudem das Meldungsdatum 01.01.2024. Die getrennte Sichtung ist ebenfalls
nicht bestanden. Bestand v3 ergänzt zulässige und erfundene Datumsangaben.

Kommunikationsfassung v4 besteht 21/32 Fälle. Die getrennten Systemrollen und das
native JSON-Schema beheben die beobachteten Rollen- und Digestfehler. Inhaltlich
lehnt das Modell jedoch sämtliche positiven Fälle ab, darunter alle sechs
kontrollierten zulässigen Entwürfe. Die Sichtung der generierten Texte zeigt
zusätzlich einen Datumsplatzhalter, indirekte Ansprache und eine technische
Regelkennung. Fassung v5 entfernt unnötige Regelmetadaten aus dem Modellkontext
und präzisiert die versandfertige Form; Bestand v4 ergänzt den Datumsplatzhalter.

## Technische Absicherung

Freie Bewertungsbegründungen werden nicht mehr an die Kommunikationsschritte
weitergegeben. Der reduzierte Kontext enthält öffentliche Nachrichten, Grund,
Belege und gegebenenfalls die fachlichen Bedingungen der freigegebenen Regel.
Technische Regelkennungen und Versionen bleiben ab Kommunikationsfassung v5
serverseitig geprüft und auditiert, gelangen aber nicht in den Modellkontext. Ein Canary-Test
weist nach, dass eine erfundene Zusage in der freien Begründung in keinem der
beiden Modellaufrufe erscheint.

Die Berichte unterscheiden fachliche Ablehnung, formale Textgrenze und technischen
Fehler. Ein falscher Digest, JSON-Fehler oder Timeout zählt bei Negativfällen
nicht als bestandene Inhaltsprüfung. Zwischenberichte tragen auch nach bisher
erfolgreichen Fällen immer `passed: false`. Tests prüfen diese Fehlerfälle und
den Abbruch eines Evaluationslaufs. Neue Codekommentare sind auf Deutsch.

Die zusätzliche technische Korrektur `closure-ground-validation-v3` folgt FALL-07:
mehrere unterschiedliche Originalbelege desselben Kriteriums sind bei getrennten
Teilproblemen zulässig. Die frühere Beschränkung auf genau vier Einträge war eine
Implementierungsannahme, keine Fachanforderung. Alle Pflichtkriterien bleiben
verbindlich; identische Belegtripel, unbekannte Kriterien, veränderte Zitate und
unvollständige Abdeckung werden weiterhin verworfen. Neue Tests prüfen gültige
verteilte Belege ebenso wie zusätzliche Kopien, fehlende Kriterien und erfundene
Belege. Die Veröffentlichungsbasis wird nach Kriterium, Nachrichten-ID und Zitat
kanonisch sortiert; ein Reihenfolgenwechsel erzeugt keine neue Versandidentität.

Die Spring-AI-Anbindung setzt pro Operation ein natives JSON-Schema. Die Erzeugung
kann nur `text` und den vorgegebenen Abschlussgrund liefern. Die Prüfung verwendet
ab Schema v2 zusätzlich eine interne quellenbezogene Begründung vor dem freien
Boolean `approved` und dem exakten serverseitigen Entwurfsdigest. Die Prüfung
im Dienst bleibt trotzdem strikt. Der Schemaadapter setzt weder ein positives
Prüfergebnis noch einen anderen Modellnamen. Ein Vertragstest entdeckte zunächst,
dass ein vorzeitig gebautes Optionsobjekt den Provider-Standard `gpt-5-mini`
einsetzen würde. Der korrigierte, unverbaute Options-Customizer erhält Modell,
Temperatur, Tokenlimit und Reasoning-Profil. Dies wurde vor den echten Läufen behoben.

## Aktuelle Prüfergebnisse

Die 58 gezielten technischen Vertrags-, Corpus-, Adapter- und
Veröffentlichungsidentitätsprüfungen bestehen für Bewertungsfassung v7 und
Kommunikationsfassung v7 ohne Fehler oder übersprungene Tests. Dazu gehören die
Zurückweisung fehlender, leerer und typfalscher Prüfbegründungen und der Nachweis,
dass die Prüfbegründung nicht in den veröffentlichten Text eingeht.

Die vollständige Abschlussbewertung v7 mit Reasoning `none` besteht 45/51 Fälle:
22/24 ursprüngliche, 14/14 zusätzliche und 9/13 Regelfälle. Die beiden ursprünglichen
Fehler werden formal abgefangen (verändertes Zitat, Personalaussage statt
Mieterbestätigung). Im Regelbestand bleiben ein unzulässiger RESOLVED-Vorschlag,
zwei falsch negative Ergebnisse und ein Timeout. Der unzulässige Vorschlag
setzt fehlenden Verwaltungsbedarf ohne freigegebene Regel mit erfolgter Behebung
gleich. Dieser semantische Fehler wird trotz existierender Originalzitate nicht
von der formalen Belegprüfung erkannt. Die Anwendungssperre verhindert weiterhin
jede tatsächliche automatische Schliessung.

Ein zusätzlicher Versuch der Kommunikationsfassung v5 mit Reasoning `low`
scheitert bereits bei der Generierung: vier protokollierte Fälle liefern innerhalb
der begrenzten Aufrufe keine Modellantwort. Nach drei aufeinanderfolgenden Fehlern
wurde der eigene Testprozess zum Abbruch ausgewählt und nach eindeutiger Prüfung
seiner Prozesskennung beendet; bis dahin war ein vierter Fall abgeschlossen.
Der Bericht bleibt ausdrücklich unvollständig (4 von 33 Fällen), nicht bestanden
und getrennt von vollständigen Modellläufen. Die beobachteten Zeitabstände lagen
bei rund 60 Sekunden. Dieses Profil ist in der vorhandenen CPU-Umgebung bislang
nicht verwendbar; es wurde keine globale Laufzeitgrenze erhöht.

Der vollständige Kommunikationslauf v5 mit `none` besteht 22/33 Fälle: Alle elf
positiven Fälle scheitern, die problematischen Entwürfe werden abgewiesen. Die
Textsichtung findet trotz kürzerem Kontext weiterhin einen Datumsplatzhalter,
einen erfundenen Reparaturakteur und indirekte Ansprache. Eine von fünf erzeugten
Nachrichten ist fachlich akzeptabel, wird aber ebenfalls vom Modell abgewiesen.

Die Kommunikationsfassung v6 fordert deshalb eine kurze, fallbezogene
Prüfbegründung vor dem Urteil. Schema v2 setzt keinen positiven Wert voraus.
Die Generierungsanweisung beschränkt den Text auf ein bis drei direkt adressierte
Sätze zum belegten aktuellen Zustand und unterscheidet die Mieterbestätigung
von einem Nachweis darüber, wer die Behebung ausgeführt hat. Diese Eingriffe
werden als neue gemeinsame Konfiguration geprüft; ein Qualitätsgewinn wird
nicht einem einzelnen Teil der Änderung zugeschrieben.

Kommunikationsfassung v6 besteht vollständig 27/33 Fälle. Drei von fünf erzeugten
Texten bestehen die zusätzliche Sichtung. Die Prüfbegründungen machen falsche
Anforderungen sichtbar: Bei Behebungen zieht das Modell irrtümlich die
Zuständigkeitsregelprüfung heran und verlangt unabhängige Reparaturnachweise.
Zudem werden ein generischer Text und ein erfundenes Datum akzeptiert. Fassung v7
trennt deshalb die beiden fachlichen Prüfaufträge anhand des serverseitigen Grunds.
Die Erzeugung beginnt direkt mit dem Fallinhalt in einem Absatz; die Beleggrenzen
und der frei zu entscheidende Boolean bleiben bestehen.

Die Berichtsdarstellung wurde zunächst als doppelte Maskierung von Zeilenumbrüchen
interpretiert. Die Prüfung der tatsächlichen Zeichen widerlegt dies: Alle fünf
Texte enthalten echte LF-Zeichen und keinen Backslash. Die vorbereitete Zusatzsperre
und ihr Gegenbeispiel wurden vor jedem Testlauf entfernt. Ein zulässiger Entwurf
mit echten Absätzen ergänzt den neuen Bestand; seine Erwartungen bleiben positiv.

Der vollständige Kommunikationslauf v7 besteht 29/34 Fälle. Alle zwölf positiven
Fälle (fünf Generierungen und sieben zulässige Entwürfe) bestehen automatisiert.
Fünf unzulässige Entwürfe werden trotzdem akzeptiert: generischer Text ohne Fallbezug,
eingebettete Freigabeanweisung, veraltete Türstörung, erfundenes Datum und unausgefüllter
Datumsplatzhalter. Die Trennung der Aufträge verbessert die positiven Fälle,
behebt die semantischen Fehlfreigaben des lokalen Modells aber nicht zuverlässig.

Die separate Sichtung der fünf generierten v7-Texte findet vier quellengebundene
und drei unmittelbar versandfertige Texte. Der Türtext vertauscht die Adressaten
bei einer Aussage über fehlenden Handlungsbedarf; beim Heizungstext bleiben
Grammatik und Ansprache zu verbessern. Die Modellselbstprüfung hat beide akzeptiert.
Die detaillierte Sichtung weist Inhaltsbindung und Sprachqualität getrennt aus.

Ein stärkerer lokaler Kandidat wurde auf der
[offiziellen Modellseite für qwen3.5:9b](https://ollama.com/library/qwen3.5%3A9b)
als verfügbar geprüft. Nach der Rückfrage zum Zeitbedarf wurde dieser zusätzliche
Modellvergleich zunächst zurückgestellt. Auf die anschliessende Anweisung, hier
weiterzuarbeiten, wurde der Download des offiziellen lokalen 9B-Kandidaten gestartet.
Treiber und Produktivkonfiguration bleiben unverändert. Der nächste
fachliche Versuch muss die unveränderten Gegenbeispiele einschliesslich der
beobachteten Fehlfreigaben bestehen; die erwarteten Ergebnisse werden nicht gelockert.

## Abschliessende technische Regression und Betriebsstand

Der abschliessende Paketbau besteht mit **107 Tests, null Fehlern und null
übersprungenen Tests**: 58 Vertrags-/Corpus-/Adapterprüfungen, 31 PostgreSQL-Tests
und 18 Tests gegen die reale lokale Camunda-Testengine. Der
[technische Nachweis](../evaluation/evidence/closure/technical-regression-2026-11-16.json)
enthält Suite-Ergebnisse und Prüfsummen der betroffenen produktiven Quellen.
`mvn package` erstellt das Backend-Artefakt erfolgreich. Modelltests sind in diesem
technischen Lauf ausdrücklich deaktiviert und werden getrennt bewertet.

Geprüft werden unter anderem einmalige Veröffentlichung, Transaktionsrücknahme,
Nebenläufigkeit, neue Nachrichten während der Generierung, Mitarbeiterpriorität,
veraltete Prozessherkunft und die feste produktive Freigabesicherung. Die
Camunda-Tests verwenden kontrollierte Fachadapter und teilweise eine ausschliesslich
synthetische Testfreigabe. Sie sind kein bestandener fachlicher Modellnachweis.

Der Fallstartpfad ist implementiert: persistierte Fallannahme mit Startauftrag,
`ProcessStartDispatcher`, Camunda-Prozessstart, Registrierung der führenden Instanz
und initialer Übergang nach «In Abklärung». In der geprüften privaten lokalen
Backend-Konfiguration steht `propertyflow.workflow.enabled=false`; auch das
Backend-Profil aktiviert Workflow-Verarbeitung standardmässig nicht. Für den
regulären automatischen Betrieb sind ein erreichbarer Camunda-Dienst, die aktivierte
Workflow-Verarbeitung und der Schlüssel der bereitgestellten Prozessversion nötig.
Die Abschlusssperre ist davon unabhängig und blockiert keine neuen Prozessstarts.
Es wurde in diesem Durchgang keine Laufzeitaktivierung vorgenommen.

## Nachweise und Grenzen

- [Prüfkriterien und Reproduktion](../evaluation/automatischer-abschluss.md)
- [Aktuelle Abschlussbewertung v7](../evaluation/evidence/closure/qwen35-4b-2026-11-16-v7.json)
- [Aktuelle Zusatzverläufe v7](../evaluation/evidence/closure/qwen35-4b-2026-11-16-additional-v7.json)
- [Aktuelle Regelprüfung v7](../evaluation/evidence/closure/qwen35-4b-2026-11-16-rule-v7.json)
- [Aktuelle Mitteilungen v7](../evaluation/evidence/closure/qwen35-4b-2026-11-16-message-v7.json)
- [Sichtung der generierten v7-Texte](../evaluation/evidence/closure/qwen35-4b-2026-11-16-message-v7-review.json)
- [Ursprünglicher Bestand mit v4](../evaluation/evidence/closure/qwen35-4b-2026-11-16-v4.json)
- [Zusätzliche Verläufe mit v4](../evaluation/evidence/closure/qwen35-4b-2026-11-16-additional-v4.json)
- [Synthetische Regeln mit v4](../evaluation/evidence/closure/qwen35-4b-2026-11-16-rule-v4.json)
- [Kommunikationslauf v2](../evaluation/evidence/closure/qwen35-4b-2026-11-16-message-v2.json)
- [Sichtung der generierten v2-Texte](../evaluation/evidence/closure/qwen35-4b-2026-11-16-message-v2-review.json)

Die Textsichtung ist ein Entwicklungsreview durch den Agenten, keine unabhängige
menschliche Fachfreigabe. Modellselbstprüfung und begrenzte synthetische Regressionen
beweisen keine allgemeine semantische Zuverlässigkeit. Nach Anpassungen an bekannte
Fehler sind diese Bestände keine unangetastete Validierungsmenge mehr.

Die Regel `EVAL-PRIVATE-TIMER/fixture-v1` bleibt ausschliesslich Testdatenbestand.
Ein produktiv freigegebener Regelbestand fehlt weiterhin. `AutomaticClosureRelease`
bleibt unverändert bei `false`; weder ein grüner Bericht noch eine Konfiguration
kann automatische Abschlüsse einschalten.


## Rückstellung zugunsten des konsolidierten Betriebs

Auf ausdrücklichen Benutzerwunsch wird die weitere Modellevaluation beendet und
zurückgestellt. Der neue Arbeitsauftrag ist eine konsolidierte produktionsnahe
lokale Version mit persistenter PostgreSQL-Datenhaltung, funktionierenden
Mieter-/Mitarbeiterinteraktionen und aktiver, im Camunda-Web-Interface sichtbarer
Prozessführung. Der Übergabestand steht in
[Konsolidierungsstand und nächster Auftrag](konsolidierungsstand-2026-11-16.md).

Der lokale 9B-Kandidat wurde inzwischen erfolgreich geladen und mit einer
synthetischen Kurzantwort initialisiert. Revision:
`56671c2ab9385f9cfcb404638e32cd62d88e3501d44822208363c010179a3c90`.
Sein 13-Fälle-Regelversuch wurde auf Benutzerwunsch abgebrochen; der archivierte
Teilbericht und die Abbruchnotiz sind keine vollständige Modellevaluation.
Der eigene Testprozess wurde beendet, das Modell aus dem Arbeitsspeicher entladen.
Die etwa 6,55 GB Modelldateien bleiben für eine spätere Fortsetzung lokal vorhanden.
Die feste Sperre automatischer Abschlüsse bleibt unverändert bestehen.
