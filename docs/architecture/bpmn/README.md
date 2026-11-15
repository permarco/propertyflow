# PropertyFlow-Fallprozess

[propertyflow-fallprozess-entwurf.bpmn](propertyflow-fallprozess-entwurf.bpmn)
ist der führende BPMN-Entwurf 0.34 zur gemeinsamen Besprechung, Stand 15.11.2026.
Er enthält BPMN-Diagrammkoordinaten und kann im Camunda Modeler geöffnet werden.

## Dateien – hier bearbeiten

| Datei | Inhalt / typische Änderung |
|---|---|
| [propertyflow-fallprozess-entwurf.bpmn](propertyflow-fallprozess-entwurf.bpmn) | Hauptablauf, durchgängiger Mieternachrichteneingang, gemeinsame Automatik-Unterbrechung und Mitarbeiterabschluss |
| [automatische-abklaerung.bpmn](automatische-abklaerung.bpmn) | Automatische Analyse, Ausführungsprüfung, Katalogrückfrage, individuelle Antwort und Systemabschluss; aktuelle Rückwege |
| [mieterantwort-pruefen.bpmn](mieterantwort-pruefen.bpmn) | Offene Fragen auswerten, Teilantworten, Fortsetzungen nach der Mieterantwort |
| [mitarbeiterbearbeitung.bpmn](mitarbeiterbearbeitung.bpmn) | Mitarbeiterprüfung, weitere Bearbeitung, Entscheidung und Aufruf der Wiedervorlage |
| [wiedervorlage.bpmn](wiedervorlage.bpmn) | Termin/Timer, Änderung, Löschung und Mitarbeiterrückfrage während der Wartephase |
| [mieternachricht-verarbeiten.bpmn](mieternachricht-verarbeiten.bpmn) | Gespeicherten Eingang prüfen, Mitarbeiterprüfung schützen, Wiedervorlage wecken und Nachricht auswerten |

Jede Datei ist die einzige bearbeitete Quelle ihres Teilprozesses, keine generierte
Ansichtskopie. In Camunda Modeler die gewünschte Datei direkt öffnen; der Dateiname
steht auch in der Dokumentation der aufrufenden Call Activity. Der Dateipfad ist
kein BPMN-Aufrufschlüssel: Die Verknüpfung verwendet die Prozess-ID.
Der bisherige Hauptdateiname bleibt für bestehende Links erhalten.

Die [Teilprozess-Verträge](teilprozess-vertraege.md) beschreiben Call-Hierarchie,
explizite Eingaben und Rückgaben, laufende Aktualisierungen, Abbruch und Rechtebindung.
Alle sieben Dateien bleiben **nicht ausführbare Fachentwürfe**. Die Dateiaufteilung
ist umgesetzt; die notwendige Übergabe zwischen den Kindinstanzen und die
Präzisierung von ADR-002/SS-003 sind noch nicht im Backend implementiert.

## Aktuelle Struktur und Navigation (0.31)

Im Hauptprozess «Aktiven Fall bearbeiten» öffnen. Der Call **«Automatische
Abklärung»** verweist auf `automatische-abklaerung.bpmn`. An ihm liegen genau
zwei Randereignisse: **«Bestätigte Ablösung»** (Conditional) und **«Fachlicher
Analysefehler»** (Error). Die zuvor 32 einzelnen Conditional-Randereignisse an
acht automatischen Schritten entfallen. Die bestehenden vier Randereignisse
am Mieterwarte-Call bleiben erhalten; sie betreffen eine andere Wartephase.

Die Mitarbeiteraufträge und der zentrale Nachrichteneingang sind weiterhin
im Hauptprozess vorhanden, jetzt als zugeklappte Ereignis-Teilprozesse mit eigenen
Diagrammansichten. Im Modeler über das Navigationssymbol öffnen. Kein zusätzlicher
öffentlicher Eingang und keine veränderte fachliche Freigabe.

Die neue Automatik-Datei zeigt die Analyse und anschliessende Handlung. INITIAL
setzt wie bisher «In Abklärung». Fortsetzungen QUESTION/CLOSE nach Mieterantwort
steigen über aktuelle Auswertung und Ausführungsprüfung ein, ohne Statusreset.
Die Prüfung verhindert alte Veröffentlichungs-/Abschlussfreigaben. Die fachlichen
Ergebnisse WAIT_FOR_REPLY, REVIEW oder CLOSED werden im Elternprozess geprüft.
Ein Systemabschluss bleibt «Nachricht veröffentlichen, danach Abschluss prüfen
und speichern». Der Mitarbeiterabschluss bleibt ohne Nachrichtenpflicht im Hauptprozess.

`AutomaticInterrupted` unterbricht den gesamten aktuellen Call, auch bei einem
blockierten Kindjob. `AutomaticInterruptionPrepare` lädt den gespeicherten Entscheid
und verzweigt zur Mieterwartephase oder Mitarbeiterbearbeitung; dort zu Prüfung,
Bearbeitung oder Wiedervorlage. Zwingender Prüfbedarf hat Vorrang. Der bestätigte
Mitarbeiterabschluss unterbricht weiterhin den gesamten ActiveCase über dessen
vorhandenes Eskalations-Randereignis.

Camunda 8.9 unterstützt Conditional Boundary Events. Sie beobachten Variablen im
sichtbaren Engine-Scope, nicht die Datenbank. Die geprüfte Referenz und
`automaticInterruptionReady` werden ausdrücklich im Elternscope bereitgestellt.
Die Kindinstanz bekommt keine unkontrollierte Variablenpropagation.
[Conditional Events](https://docs.camunda.io/docs/8.9/components/modeler/bpmn/conditional-events/),
[Scope und Auswertung](https://docs.camunda.io/docs/8.9/components/concepts/conditionals/).

Alle sieben Dateien bleiben Fachentwürfe, nicht deploybare Laufzeitnachweise.
Worker-Schutz, Übergabe während Phasenwechseln und Incident-Unterbrechung benötigen
Integrationstests. Die nachfolgenden Versionsabschnitte dokumentieren die Entwicklung;
für Dateizuständigkeit, Elementnamen und Unterbrechungsstruktur gilt dieser Stand 0.31.

## Historische Ansichten und Entscheidungen bis 0.30

Die Hauptansicht zeigt den Rahmen **«Aktiven Fall bearbeiten»** zwischen
technischer Startvorbereitung und Prozessende. Im Modeler diesen
Teilprozess öffnen: Dort liegt der bisherige Hauptablauf mit seiner Anordnung.
Er zeigt die bestätigte Prozesszuordnung, den Einstieg in
**«In Abklärung»**, die Dringlichkeitsbewertung und den Service Task
**«Vorgehen bestimmen – LLM + Fachkontext»**. Im Service Task
**«Ausführungsbedingungen und Abschlussgründe prüfen»** prüft der Dienst die Empfehlung. Danach
verzweigt der Ablauf zu Katalogrückfrage, Mitarbeiterprüfung oder zulässiger
automatischer Antwort mit Abschluss.

Ein unterbrechendes Fehlerereignis an **«Dringlichkeit bewerten»** führt bei
festgestelltem Analysefehler direkt zur Mitarbeiterprüfung. Der interne Grund
lautet **«KI-Analyse fehlgeschlagen»**; eine vorhandene Dringlichkeit bleibt
erhalten. Der spätere Worker muss dafür den BPMN-Fehler
`TRIAGE_ANALYSIS_FAILED` ausdrücklich melden. Ein gewöhnlicher technischer
Jobfehler aktiviert diesen Pfad nicht automatisch. Die fachliche Prüfung
wartet nicht auf ausgeschöpfte Analyse-Retries. Das entspricht der
[Camunda-Fehlerereignis-Semantik](https://docs.camunda.io/docs/8.9/components/modeler/bpmn/error-events/)
und der fachlichen Vorgabe aus FALL-05.

Im Hauptmodell lassen sich «Aktiven Fall bearbeiten» und der darin eingebettete
Nachrichteneingang über ihr Navigationssymbol öffnen. Die ausgelagerten Prozesse
werden in ihren eigenen Dateien bearbeitet:

1. **Aktiven Fall bearbeiten:** bisheriger Hauptablauf und ein durchgängiger
   Mieternachrichteneingang, der während der gesamten aktiven Bearbeitung gilt.
2. **Mieterantwort abwarten und Fortsetzung prüfen** (`mieterantwort-pruefen.bpmn`): auf eine aktuelle
   Auswertung aus dem zentralen Nachrichteneingang warten und die zulässige
   Fortsetzung prüfen. Dieselbe Nachricht wird hier nicht nochmals empfangen
   oder unabhängig neu ausgewertet.
3. **Mitarbeiterprüfung und weitere Bearbeitung** (`mitarbeiterbearbeitung.bpmn`): Fortsetzen, Rückfrage,
   Wiedervorlage oder bestätigter Abschluss. Neue Nachrichten während der
   Prüfung ergänzen dieselbe Aufgabe.
4. **Wiedervorlage verwalten** (`wiedervorlage.bpmn`): Termin, Änderung, Löschung und Abschluss.
   Ein unterbrechendes Randereignis im übergeordneten Mitarbeiterpfad reagiert
   auf einen zentral empfangenen Mieterkontakt. Eine Mitarbeiterrückfrage ist
   weiterhin eine nicht unterbrechende Nebenaktion; sie verändert den Termin nicht.
5. **Mieternachrichten durchgängig verarbeiten:** der neue Ereignis-Teilprozess
   oben links in «Aktiven Fall bearbeiten». Er empfängt die Nachricht und ruft
   die Verarbeitung in `mieternachricht-verarbeiten.bpmn` auf. Dort liegen
   Zuordnung, Auswertung und geschützte Bereitstellung.

Damit liegen sieben Diagrammansichten in fünf Dateien: drei im Hauptmodell,
je eine in den vier ausgelagerten Dateien. Call Activities erzeugen untergeordnete
Instanzen mit eigenen Variablenbereichen; sie gehören zum Ausführungsbaum des
einen führenden Fallprozesses. Der fachliche Abschluss
erfolgt ausschliesslich im Service Task **«Abschluss prüfen und speichern»**.
Erst danach beendet das terminierende Ende den Bereich «Aktiven Fall bearbeiten»
einschliesslich laufender Nachrichtenauswertungen und Empfang. Der äussere
Prozess endet danach ohne zusätzliche Benachrichtigung. Die normalen
Endereignisse der übrigen Teilprozesse sind keine Fallabschlüsse.

## Rückweg bei veralteter Nachrichtenauswertung (Stand 0.11)

In `mieternachricht-verarbeiten.bpmn` liegt an **«Aktuelle Auswertung geschützt
bereitstellen»** (`NApply`) das unterbrechende Fehlerereignis **«Auswertung
veraltet»**. Der fachliche Fehler `TENANT_EVALUATION_STALE` führt zu
**«Aktuelle Grundlage übernehmen / abwarten»**, danach zur Prüfung
**«Noch anzuwenden?»**: aktuelle Grundlage erneut durch NApply prüfen oder
den Nachrichtenaufruf ohne neue Wirkung beenden. Eingangswirkungen und
Wiedervorlage-Aufhebung werden auf diesem Rückweg nicht nochmals ausgeführt.

Die [Worker-Regeln in KOM-03](../../specifications/fallkommunikation.md#worker-regeln-bei-mitarbeiterprüfung-und-veralteter-auswertung)
schützen bestehende Aufgaben und Mitarbeiterentscheidungen; KOM-AK-25 nennt
die späteren Prüffälle. Der Fehler ist vom technischen Retry und von einem
Analysefehler getrennt. Die BPMN-Datei bleibt nicht ausführbar; keine Worker implementiert.

## Wiedervorlage: Kontakt, Timer und neuer Termin (Stand 0.12)

Die bestätigte Zeile 5 steht in [FALL-11](../../specifications/fallverwaltung.md#nachricht-timer-und-neuere-terminentscheidung).
Im Nachrichtenmodell stellt `NWake` den versionsgebundenen Kontakt **vor KI** bereit.
Im Mitarbeitermodell unterbricht `SContactDuringFollowUp` den betroffenen
Wiedervorlage-Aufruf; `SStopFollowUp` führt zu `SReviewStatus`.
Der Timerweg in `wiedervorlage.bpmn` führt über `WClearTimer`, Rückgabe `REVIEW`
und `SFollowResult` zur gleichen Prüfung. Die bestehenden Wege bleiben erhalten;
Beschriftungen, sichtbare Hinweise und Worker-Dokumentation präzisieren jetzt
Einmaligkeit, Versionsschutz und den Vorrang einer neueren Terminentscheidung.

Timer und Kontakt benötigen dieselbe logische Prüfzuordnung. Die Bedingung am
Randereignis allein garantiert keinen Schutz vor gleichzeitiger Terminänderung:
Versionswechsel und unterbrechende Zustellung sind vor dem Call-Abbruch zu
koordinieren. FALL-AK-27 definiert dafür die späteren Integrationstests. Diese
technische Koordination ist weiterhin nicht implementiert; keine Laufzeitfreigabe.

## Mieternachrichten und Vorrang der Mitarbeiterprüfung

**Bestätigt am 14.11.2026:** Mieter können in jedem aktiven Bearbeitungsstatus
Nachrichten schicken, auch ohne offene Rückfrage. Der Fallprozess muss während
der aktiven Bearbeitung durchgängigen Nachrichtenempfang bereitstellen.
Zugriffsprüfung, laufender Prozess, Erreichbarkeit von Camunda und die Sperre
nach Abschluss gelten weiterhin. [KOM-03](../../specifications/fallkommunikation.md#kom-03-asynchrone-fallkommunikation)

Während **«Mitarbeiterprüfung erforderlich»** ergänzt eine neue Nachricht
dieselbe offene Aufgabe. Dringlichkeit, Rückfragenbeantwortung und Empfehlung
dürfen nach ihren Fachregeln neu beurteilt werden. Der Bearbeitungsstatus bleibt
jedoch bis zur bewussten Mitarbeiterentscheidung erhalten. Das gilt auch für
«alles erledigt» und unabhängig davon, ob System oder Mensch den Prüfbedarf
ausgelöst haben. Eine automatische Unterscheidung nach dieser Herkunft ist
nicht vorgesehen. [FALL-06](../../specifications/fallverwaltung.md#vorrang-einer-laufenden-mitarbeiterprüfung)

Der Entwurf enthält dafür **genau einen externen Empfänger für Mieternachrichten**:
einen nicht unterbrechenden Ereignis-Teilprozess innerhalb von «Aktiven Fall
bearbeiten». Die bisherigen konkurrierenden Empfänger bei Rückfrage,
Mitarbeiteraufgabe und Wiedervorlage wurden ersetzt. Eine Nachricht startet
keine zweite Fallprozessinstanz und unterbricht die Mitarbeiteraufgabe nicht.
Das entspricht dem Geltungsbereich eines
[Camunda-Ereignis-Teilprozesses](https://docs.camunda.io/docs/8.9/components/modeler/bpmn/event-subprocesses/).

| Aktuelle Bearbeitung | Reaktion auf eine neue Mieternachricht |
|---|---|
| Mitarbeiterprüfung erforderlich | Nachricht und aktuelle Auswertung ergänzen dieselbe Aufgabe; nur eine bewusste Mitarbeiterentscheidung gibt die weitere Bearbeitung frei. |
| Wartet auf Wiedervorlage | Kontakt zur betroffenen Wiedervorlage sofort bereitstellen; der Hauptpfad beendet die Wartephase und veranlasst Mitarbeiterprüfung, ohne die KI-Auswertung abzuwarten. |
| Wartet auf Mieterantwort | Zentral auswerten; der Antwortpfad übernimmt das aktuelle Ergebnis und prüft die Fortsetzung. |
| Andere aktive Prozessposition | Eingang vormerken und auswerten; laufende Empfehlungen vor ihrer Wirkung gegen den neuen Fallstand prüfen. |
| Bereits verarbeitet, falsche Prozessinstanz oder inzwischen abgeschlossen | Keine erneute fachliche Wirkung; keinen alten Status oder eine erledigte Aufgabe reaktivieren. |

Antwortpfad und Wiedervorlage verwenden **interne bedingte Ereignisse** statt
einer zweiten Nachrichtenkorrelation. Eine bereits vor Aktivierung des
Warteschritts bereitgestellte Information muss dadurch weiterhin erkannt werden.
Das Randereignis an der Wiedervorlage unterbricht den gesamten Warte-Teilprozess;
es hängt nicht als weiterer Ausgang am ereignisbasierten Gateway.
[Bedingte Ereignisse in Camunda 8.9](https://docs.camunda.io/docs/8.9/components/modeler/bpmn/conditional-events/)

Die Beispielbedingungen `tenantEvaluationVersion`,
`consumedTenantEvaluationVersion`, `pendingTenantContactFollowUpVersion` und
`activeFollowUpVersion` erläutern die nötige Versionsbindung. Sie sind **noch
kein ausführbarer Variablenvertrag**. Eingangs-ID und Auswertungskontext müssen
je Ereignispfad lokal bleiben. Seit der Dateiaufteilung müssen die nötigen
Steuerinformationen ausdrücklich an die aktuelle Kindinstanz und den dort
zuständigen Bereich übergeben werden. Der Antwort-Wartepunkt liegt in der
Antwortinstanz; das Randereignis für Mieterkontakt zur Wiedervorlage liegt in
der Mitarbeiterinstanz. Eine Änderung nur im `ActiveCase`-Bereich weckt diese
Kindinstanzen nicht. Die Registrierung und lückenlose Nachlieferung beschreibt
der [Teilprozess-Vertrag](teilprozess-vertraege.md#laufende-aktualisierungen-über-instanzgrenzen).
Ein unversioniertes «neue Nachricht»-Flag
genügt nicht: Ein alter Eingang darf keine später neu gesetzte Wiedervorlage
aufheben und keine Auswertung zweimal auslösen.

### Rückweg bei neuer Nachricht während automatischer Analyse

**Bestätigt am 15.11.2026 – Zeile 1:** Die Regel steht in
[KOM-03](../../specifications/fallkommunikation.md#neue-mieternachricht-während-automatischer-analyse),
der spätere Prüfnachweis in KOM-AK-22. Neue Nachrichten während Analyse oder
Empfehlung machen eine sie noch nicht berücksichtigende Grundlage unzulässig.
Der Status bleibt zunächst «In Abklärung»; ältere Ergebnisse lösen keine Aktion aus.

An `Validate` liegt das unterbrechende Fehler-Randereignis `AnalysisStale`.
Der fachliche Platzhalter `ANALYSIS_BASIS_STALE` führt zu `CurrentAnalysis`:
**«Aktuelle Auswertung übernehmen / abwarten»**. Vorhandenes aktuelles Ergebnis
nutzen, laufende zentrale Auswertung abwarten oder eine fehlende über dieselbe
zentrale Verarbeitung veranlassen. Danach zurück zu `Validate`, niemals direkt
zur Veröffentlichung. Alle relevanten gespeicherten Nachrichten müssen enthalten
sein; weitere Eingänge können den Stand erneut überholen.

Der Rückweg erzeugt keine zweite Nachrichtenkorrelation und keinen unabhängigen
LLM-Entscheidungsweg. Mitarbeiterentscheidungen, aktueller Pflichtprüfbedarf und
Abschluss bleiben geschützt. Ein tatsächlicher Analysefehler ist kein
`ANALYSIS_BASIS_STALE`. Worker, Warteverfahren und Variablenvertrag sind noch
nicht implementiert. Zeile 2 ergänzt denselben Rückweg wie nachfolgend beschrieben.

### Rückwege vor automatischer Veröffentlichung

**Bestätigt am 15.11.2026 – Zeile 2:**
[KOM-03](../../specifications/fallkommunikation.md#neue-mieternachricht-vor-automatischer-veröffentlichung)
und KOM-AK-23 regeln die letzte Prüfung gemeinsam mit der Veröffentlichung.
An `Question` und `AutoAnswer` liegen die unterbrechenden Fehler-Randereignisse
`QuestionStale` und `AutoAnswerStale`. Der fachliche Platzhalter
`PUBLICATION_BASIS_STALE` führt jeweils zu `CurrentAnalysis` und danach erneut
zu `Validate`. Die normale Fortsetzung zu Antwortwarten beziehungsweise Abschluss
wird bei dieser unterdrückten Veröffentlichung nicht genommen.

Dieser Rückweg behandelt eine neue, bereits gespeicherte Mieternachricht, die
die Veröffentlichungsgrundlage überholt: keine neue Nachricht, kein neuer Antwortbedarf,
kein zugehöriger Mailauftrag. Vorhandene Daten bleiben bestehen. Die gemeinsame
Prüfung behält den bisherigen Status und berücksichtigt alle bisherigen Rückfragen;
vollständige Beantwortung darf nicht durch neue Fragen umgangen werden.
Eine inzwischen getroffene Mitarbeiterentscheidung bleibt vorrangig.

Ist dagegen die Veröffentlichung zuerst gespeichert, bleibt sie erhalten;
der spätere Eingang wird gegen den aktuellen Verlauf ausgewertet. Kein rückwirkender
Fehlerpfad, kein Löschen und keine doppelte Veröffentlichung beim Retry.
Technische Speicherfehler und bereits erfolgreiche Wiederholungen sind von diesem
fachlichen Konflikt getrennt. Die Transaktions- und Versionsbindung ist vor
Ausführung auszuarbeiten. Der Entwurf enthält keine Backend-Implementierung.

### Fortsetzungen nach der Mieterantwort

**Bestätigt am 15.11.2026 – Zeile 3:**
[KOM-03](../../specifications/fallkommunikation.md#fortsetzung-nach-einer-mieterantwort-im-wartestatus)
und KOM-AK-24 legen die Reihenfolge fest. `RValidate` prüft den aktuellen Stand;
`RResult` gibt genau eine fachliche Fortsetzung zurück:

| Priorität | Bedingung | Modellweg |
|---|---|---|
| 1 | Pflichtprüfbedarf oder Analysefehler | `R08` → `RReview` → `F21` → Mitarbeiterprüfung |
| 2 | Zulässiger Systemabschluss mit anschliessender LLM-Nachricht | `R07` → `RClose` → `F20` → Antwort veröffentlichen, Abschluss erneut prüfen |
| 3 | Alle bisherigen Fragen beantwortet, kein zulässiger Abschluss | Ebenfalls `R08` → `RReview` → `F21`; keine Umgehung durch neue Fragen |
| 4 | Fragen offen, weitere notwendige Katalogfrage zulässig | `R09` → `RQuestion` → `F18` → Katalogfrage veröffentlichen |
| 5 | Fragen offen, keine weitere Frage nötig | `R10` → `RWait` → `F19` → weiter auf Antworten warten |

Inhaltlich unzulässige Abschlussnachricht, unzuverlässige Grundlage oder notwendige Fragen
ausserhalb des Katalogs folgen weiterhin den bestehenden Prüfregeln. Ein
überholtes Ergebnis ist kein Default-Grund für Mitarbeiterprüfung: Das neue
Randereignis `RAnalysisStale` an `RValidate` führt über `RCurrentAnalysis` zur
aktuellen zentralen Auswertung und danach erneut zur Antwortprüfung. Es verwendet
den fachlichen Platzhalter `ANALYSIS_BASIS_STALE`, keinen zweiten externen Empfang.

Der Hauptpfad erhält zusätzlich `F_RefreshWait`: Nach dem gemeinsamen Rückweg
aus Zeile 2 führt «Fragen offen, keine weitere Aktion» von `Route` zurück zu
`Reply`. Dadurch fällt dieser Fall nicht versehentlich in die Mitarbeiterprüfung.
Die gemeinsame Prüfung erhält den Rückfragenkontext und den bestehenden Wartestatus.
Ein verbrauchtes Ergebnis weckt den Wartepfad nicht erneut; ein bereits verfügbares
neueres Ergebnis darf nicht verloren gehen. Die Endereignisse im Antwortteilprozess
liefern nur eine Fortsetzung, keinen fachlichen Fallabschluss.

Der bestehende Status bleibt während der Auswertung zunächst «Wartet auf Mieterantwort».
Mitarbeiterentscheidungen, Wiedervorlage und Abschluss bleiben geschützt.
Ausführbare Ergebniswerte, FEEL-Bedingungen, Fehler- und Warteverfahren sind weiterhin
festzulegen; die gezeichneten fachlichen Wege sind kein Laufzeitnachweis.

### Start und Abschluss des Empfangsbereichs

**Technischer Vorschlag im Entwurf, vor Ausführung mit ADR-002/SS-003 abzugleichen:**
Der Korrelationswert `processInstanceKey` muss vor Aktivierung des
Empfangsbereichs verfügbar sein. Dafür ist eine technische Vorstufe
«Prozessschlüssel bereitstellen» eingezeichnet. Sie speichert noch keine führende
Fallzuordnung und erledigt keinen Startauftrag. Erst der Zuordnungsjob innerhalb
des bereits empfangsbereiten Bereichs bestätigt die Zuordnung mit dem bisherigen
Transaktionsschutz. So soll zwischen fachlicher Aktivierung und Empfang keine
Lücke entstehen. ADR-002 beschreibt bisher den Zuordnungsjob als ersten Job;
die genaue Startsequenz ist deshalb ausdrücklich noch abzustimmen.

Nach erfolgreichem fachlichem Abschluss beendet ein
[terminierendes Ende](https://docs.camunda.io/docs/8.9/components/modeler/bpmn/terminate-events/)
nur den aktiven Teilprozess samt seinen Nebenpfaden. Es ersetzt keine Prüfung
des Fallabschlusses. Bereits laufende externe Modellaufrufe können trotzdem
verspätet antworten; der Dienst muss solche Ergebnisse zusätzlich anhand des
aktuellen Status und der Version abweisen.

### Rückweg bei veraltetem Mitarbeiterabschluss

**Bestätigt am 15.11.2026:** Eine laufende KI-Auswertung allein blockiert keinen
bewussten Mitarbeiterabschluss am aktuellen Fallstand. Eine veraltete
Entscheidungsgrundlage blockiert ihn hingegen. Die vollständige Regel und die
Konfliktfälle stehen in
[FALL-07](../../specifications/fallverwaltung.md#mitarbeiterabschluss-während-laufender-ki-auswertung)
und FALL-AK-22.

Am Service Task **«Abschluss prüfen und speichern»** liegt jetzt das
unterbrechende Fehler-Randereignis **«Mitarbeiterentscheidung veraltet»**.
Erkennt der Dienst diesen fachlichen Konflikt vor erfolgreicher
Abschlussspeicherung, meldet der spätere Worker ausdrücklich den BPMN-Fehler
`STAFF_CLOSURE_STALE`. Der normale Weg zum terminierenden Ende wird unterbrochen;
der Rückweg führt zum bestehenden Teilprozess **«Mitarbeiterprüfung und weitere
Bearbeitung»**. Dort werden der Hinweis **«Fallstand seit Abschlussbestätigung
geändert»** und die erneute Prüfung veranlasst. Eine bereits erledigte Aufgabe
wird durch genau eine neue Aufgabe für den aktuellen Stand ersetzt. Die alte
Abschlussfreigabe wird nicht weiterverwendet.

Der Fall wurde dabei noch nicht fachlich abgeschlossen: kein Abschlussnachweis,
keine Abschlussmail und keine Wiedereröffnung. Der zentrale Nachrichteneingang
bleibt aktiv. Wird die veraltete Aktion bereits vor Abschluss der User Task
abgewiesen, bleibt stattdessen die bisherige Aufgabe offen; dafür wird der neue
Randereignispfad nicht benötigt.

Ein technischer Speicherfehler löst diesen fachlichen Pfad nicht allein aus.
Retries prüfen dieselbe Entscheidung erneut; ein bereits erfolgreich gespeicherter
identischer Abschluss wird idempotent bestätigt. Das explizite Melden und
Unterbrechen folgen der
[Camunda-Fehlerereignis-Semantik](https://docs.camunda.io/docs/8.9/components/modeler/bpmn/error-events/).
Der neue Rückweg betrifft den bestätigten Mitarbeiterabschluss. Die bestehenden
Regeln für überholte automatische Empfehlungen bleiben bestehen; ein bloss
dupliziertes oder überholtes KI-Ergebnis erzeugt für sich keine neue Prüfung.

### Prüffälle für die spätere ausführbare Fassung

Diese Fälle sind Anforderungen an künftige Camunda-/Backend-Integrationstests,
keine bereits ausgeführten Laufzeittests:

1. Nachricht während Analyse, Mitarbeiteraufgabe und zwischen zwei Schritten:
   Korrelation an denselben führenden Prozess gelingt; genau eine fachliche Wirkung.
2. «Alles erledigt» während Mitarbeiterprüfung: Auswertung wird ergänzt,
   Status und dieselbe Aufgabe bleiben offen.
3. Nachricht während Wiedervorlage bei blockiertem LLM: Timer/Wartephase endet
   und Mitarbeiterprüfung beginnt, bevor die Auswertung fertig ist.
4. Auswertung ist bereits vor Eintritt in den Antwort-Warteschritt fertig:
   Ergebnis wird genau einmal übernommen; keine verlorene Benachrichtigung.
5. Zwei Nachrichten, wiederholte Korrelation und vertauschte Analyseergebnisse:
   kein Verlust eines neuen Eingangs, keine doppelte Aufgabe und kein Überschreiben
   des aktuellen Falls durch eine ältere Auswertung.
6. Neuer Mieterkontakt konkurriert mit Timer, Terminänderung oder
   Mitarbeiterentscheidung: genau eine zulässige Fortsetzung; alte Marker heben
   keine inzwischen bewusst neu gesetzte Wiedervorlage auf.
7. Abschluss während laufender Auswertung: Empfang und Nebenpfade enden,
   keine zusätzliche Abschlussmail; verspätete Ergebnisse wirken nicht mehr.
8. Fehler in der zentralen Analyse: vorhandene Dringlichkeit bleibt erhalten;
   der aktuelle Hauptpfad erhält Prüfbedarf, eine bestehende Prüfung wird ergänzt.
9. Veralteter Mitarbeiterabschluss nach erledigter User Task: Fehlerpfad führt
   ohne Abschlussmail zu genau einer neuen Mitarbeiteraufgabe. Alte Freigaben
   und wiederholte Konfliktmeldungen erzeugen weder Abschluss noch weitere Aufgaben.
10. Fachlichen Abschlusskonflikt von technischem Speicherfehler und Retry eines
    bereits gespeicherten Abschlusses unterscheiden; FALL-AK-22 vollständig prüfen.

## Automatische Abschlussgründe

**Bestätigt am 15.11.2026:** Die beiden Gründe und ihre vollständigen Regeln stehen
zentral in [FALL-07](../../specifications/fallverwaltung.md#regeln-für-den-automatischen-fallabschluss).
Die vorhandenen Prüfschritte `Validate` und `RValidate` wenden dieselben Regeln
auf die erste Empfehlung beziehungsweise die Auswertung einer Mieterantwort an:

- Systemabschluss ausschliesslich in **«In Abklärung»** oder
  **«Wartet auf Mieterantwort»**, mit aktueller Prozessfreigabe und ohne offenen
  menschlichen Prüfbedarf, Zuordnungs-, Analyse- oder Versandprobleme.
- **«Problem behoben»:** gespeicherte Mieteraussage belegt die eingetretene
  Behebung des gesamten offenen Sachverhalts ohne weiteren Hilfebedarf oder
  aktuellen Widerspruch. Vorläufige Besserung und angekündigte Massnahmen genügen nicht.
- **«Kein Bearbeitungsbedarf der Verwaltung»:** ausdrücklich freigegebene,
  gültige Fachregel mit Kennung, Version und passendem Geltungsbereich; alle
  Voraussetzungen belegt, keine Ausnahme, kein Widerspruch und kein ungedecktes
  Teilproblem. Das Modell darf keine Regel erfinden oder fehlende Angaben ergänzen.
- Alle relevanten gespeicherten Nachrichten sind berücksichtigt. Neu eingegangene,
  noch nicht ausgewertete Nachrichten sperren den Systemabschluss auf alter Grundlage.

Die positiven Wege `F10`, `R07` und `F20` setzen diese Prüfung voraus. In den
beiden Ansichten erläutern die Prüfhinweise die Gründe und Statusgrenzen.
`AutoAnswer` prüft vor Veröffentlichung erneut die aktuelle Grundlage und die
Inhaltsprüfung im erzeugenden Worker; `Close` prüft die Systemabschlussbedingungen unmittelbar
bei der Speicherung erneut. Der Mitarbeiterweg `F16` folgt weiterhin seinen
eigenen Regeln und wird nicht auf die beiden automatischen Status beschränkt.
Der Rückweg `STAFF_CLOSURE_STALE` bleibt ausschliesslich der Konfliktpfad für
einen veralteten bestätigten Mitarbeiterabschluss.

Unklare oder widersprüchliche fachliche Einordnung führt zur Mitarbeiterprüfung.
Gezielt erfragbare fehlende Angaben folgen gegebenenfalls KOM-05. Eine bereits
laufende Mitarbeiterprüfung bleibt geschützt. Ein überholtes Ergebnis oder
ein inzwischen anderer Status erzeugt für sich keine neue Aufgabe und darf
keine aktuelle Bearbeitung verdrängen. Die technische Koordination dieser
Fortsetzungen bleibt vor Ausführung festzulegen; der Default-Ausgang darf
nicht pauschal für veraltete oder doppelte Ergebnisse verwendet werden.

Offene, durch die belegte Gesamtbehebung gegenstandslos gewordene Rückfragen
verhindern den zulässigen Abschluss nicht allein. Ihr Antwortbedarf endet beim
erfolgreichen Abschluss, ohne sie als inhaltlich beantwortet zu kennzeichnen.
Belegstellen und gegebenenfalls die gültige Regelversion bleiben nachvollziehbar.
Die inhaltliche Erkennung benötigt eine Evaluation gemäss FALL-AK-23 bis
FALL-AK-26; Struktur- und Schema-Prüfung allein belegen ihre Richtigkeit nicht.

Die Regeln geben noch keinen konkreten Zuständigkeitskatalog frei. Die fachlichen
Regeln für Antworttexte und einen ausgebliebenen Abschluss sind in KOM-06
festgelegt und nachfolgend im Modell eingeordnet.

## Automatische Antworten vor Fallabschluss

**Vereinfachter Stand 0.13:** Bei zulässigem Systemabschluss erzeugt der Service Task
**«LLM-Nachricht erstellen und versenden»** (`AutoAnswer`) eine individuelle Nachricht,
prüft ihren Inhalt im Worker und speichert sie samt zuverlässigem Versandauftrag.
Die Nachricht darf den Abschluss mitteilen. Kein fester Vorlagenkatalog und kein
gesonderter Vorlagenprüfschritt; kein neuer Entwurf im Mitarbeiter-UI.

Bei inhaltlich unzulässiger Nachricht führt `CLOSURE_MESSAGE_INVALID` zur
Mitarbeiterprüfung, ohne Veröffentlichung oder automatischen Abschluss. Technische
LLM-/Speicherfehler folgen Job-Retries und Incidents. Vor Veröffentlichung
überholte Grundlage führt weiterhin über `PUBLICATION_BASIS_STALE` zur aktuellen
Auswertung. Nach Veröffentlichung führt `AUTO_CLOSURE_STALE` an `Close` über
`CurrentAnalysis` erneut zu `Validate`. Bereits veröffentlichte Nachricht und
Versandauftrag werden nicht nochmals erzeugt; Mitarbeiterentscheidungen bleiben
vorrangig. Die Worker müssen Aktualität und Idempotenz auch bei Retries prüfen.

Nach erfolgreichem Abschluss endet der Hauptprozess ohne zusätzlichen Mail-Schritt.
Der Mitarbeiterweg führt direkt zur Abschlussprüfung, ohne obligatorische Nachricht
und ohne Abschlussmail. Freiwillige Nachricht und KI-Formulierungshilfe im UI bleiben.
[KOM-06](../../specifications/fallkommunikation.md#kom-06-automatische-antworten-vor-fallabschluss)
und BEN-05 beschreiben das Verhalten; Laufzeitumsetzung steht aus.

## Automatische Rückfragen

Die am 14.11.2026 bestätigten Regeln stehen zentral in
[KOM-05](../../specifications/fallkommunikation.md#kom-05-automatische-rueckfragen).
Das LLM wählt passende Frage-IDs für fehlende notwendige Sachangaben.
Der Dienst prüft Katalog, aktuellen Fallstand, Prozessfreigabe und Prüfgründe.
Er veröffentlicht ausschliesslich den hinterlegten Katalogtext mit Antwortbedarf,
ohne freie LLM-Ergänzungen. Die Prüfung wird unmittelbar vor der Speicherung
wiederholt und mit ihr synchronisiert.

Offene menschliche Entscheidungen, offene Objektzuordnung, Analysefehler und
klärungsbedürftiger Versand führen zur Mitarbeiterprüfung. Dasselbe gilt,
wenn keine zuverlässige Fragewahl möglich ist oder die notwendige Frage nicht
im Katalog steht. Während Mitarbeiterbearbeitung oder Wiedervorlage erfolgt
keine automatische Veröffentlichung; zulässige manuelle Rückfragen bleiben möglich.

Doppelte oder überholte Ergebnisse erzeugen keine neue Nachricht und sind
allein kein Mitarbeiterprüfgrund. Je nach aktuellem Stand wird verworfen,
neu beurteilt oder auf bereits offene Fragen weiter gewartet. Diese Fälle
dürfen nicht pauschal über den Default-Ausgang zur Mitarbeiterprüfung laufen.
Teilantworten lassen andere Fragen offen. Sind alle zuvor offenen Fragen
beantwortet, gilt der vereinbarte Übergang zur Mitarbeiterprüfung, sofern
kein zulässiger Systemabschluss erfolgt und keine Wiedervorlage vorrangig ist.
Neue Katalogfragen dürfen diesen Übergang nicht umgehen.

KOM-02 gilt auch für die Auswahl von Frage-IDs. Ein formal gültiges Ergebnis
beweist keine semantische Richtigkeit; die inhaltliche Auswahl benötigt eine
Evaluation. Die Prüfkriterien KOM-AK-13 bis KOM-AK-16 sind Anforderungen an
die spätere Umsetzung, kein Nachweis bereits ausgeführter Backend-Tests.

## Entwurfsstand

Der Prozess ist mit `isExecutable="false"` gekennzeichnet. Worker-Typen,
freigegebene FEEL-Bedingungen, Ergebnisvariablen, Timerkonfiguration und technische
Nachrichtenkorrelation sind noch nicht implementiert. Die benannten
Nachrichten und Gateway-Ausgänge erläutern fachliche Ereignisse und Ergebnisse.
Hinweise des Modelers zu fehlenden Ausführungseigenschaften sind daher zu erwarten.

Die durchgängige Empfangsstruktur ist jetzt modelliert. Vor einer ausführbaren
Fassung bleiben die vorgeschlagene Startpräzisierung, Initialisierung und
Sichtbarkeit der Bedingungsvariablen, persistenter Ergebnisverbrauch und die
Koordination überlappender Nachrichten festzulegen. Das umfasst die technische
Fortsetzung nach verworfenen, fehlerhaften oder neu zu bewertenden Ergebnissen
an jeder aktiven Hauptprozessposition; diese ist durch die sichtbaren
Hauptausgänge noch nicht vollständig modelliert. Für automatische Rückfragen sind die Fachregeln in
KOM-05 vereinbart; die beiden Systemabschlussgründe samt Statusgrenzen und
Nachweisanforderungen sind in FALL-07 bestätigt. KOM-06 legt individuelle LLM-Nachrichten,
Veröffentlichungsbedingungen und die fachlichen Folgen eines ausgebliebenen
Abschlusses fest. Konkrete freigegebene Zuständigkeitsregeln sowie die
technische Fehler- und Wiederholungskoordination bleiben auszuarbeiten. Wiedereröffnung und externe Wartephase sind in den Abschnitten 0.15 und 0.16
fachlich ergänzt; die technische Ausführung bleibt offen.

Die sechs BPMN-Dateien sind unter `docs` abgelegt und werden nicht als
Prozessressourcen der Anwendung ausgeliefert. Dieser Auftrag ergänzt keine Backend-Logik.

Strukturprüfung zur ersten Aufteilung (Stand 0.9) vom 15.11.2026: Alle fünf Dateien bestehen die
BPMN-2.0-Schemaprüfung sowie Import, Export und erneuten Import mit `bpmn-js`
einschliesslich Zeebe-Erweiterungen ohne Warnungen. Die drei Call-Verknüpfungen
und ihre Mappings sind erhalten; alle 94 bisherigen Sequenzverbindungen und
Element-IDs bleiben vorhanden. Die sechs Diagrammansichten wurden gerendert und
visuell geprüft. Das ist kein Camunda-Laufzeit- oder Deploymentnachweis.

Ergänzende Prüfung zu Stand 0.10: fünf Dateien, vier aufgelöste Call-Aufrufe
und sieben Diagrammansichten; Schema und Import/Export mit Zeebe-Mappings ohne
Warnungen. Der externe Nachrichtenempfang bleibt genau einmal im Hauptmodell.
Die Verarbeitung behält ihre bisherigen Wege; nur ihr Start liegt jetzt hinter
dem Call. Nachrichteneingang und ausgelagerte Verarbeitung wurden visuell geprüft.

Grundlagen: [Fallverwaltung](../../specifications/fallverwaltung.md),
[Fallkommunikation](../../specifications/fallkommunikation.md),
[ADR-002](../adr/ADR-002-camunda8-workflow-orchestrierung.md),
[UC-018](../../use_cases/UC-018-zustaendigkeit-und-vorgehen-empfehlen.md) und
[UC-021](../../use_cases/UC-021-ki-rueckfrage-vorschlagen.md).
Der Einstieg in «In Abklärung» und die Regeln für automatische Katalogrückfragen
wurden im Chat am 14.11.2026 bestätigt. Spezifikation, Use Cases und Entwurf
sind darauf abgestimmt; es wurde keine Backend-Logik ergänzt.

Prüfung des vereinfachten Stands 0.13: alle fünf BPMN-Dateien bestehen die
XML-Schemaprüfung und den Import/Export mit Zeebe-Erweiterungen ohne Warnungen.
Automatischer Nachrichtenweg, direkter Mitarbeiterabschluss, Inhaltsfehlerpfad
und Aktualitätsrückwege sind strukturell geprüft; der Abschlussmail-Schritt ist
entfernt. Geänderte Diagramme wurden gerendert und visuell geprüft. Keine
Backend- oder Camunda-Laufzeittests, da weiterhin nicht ausführbarer Entwurf.

## Mitarbeiternachricht als Nebenaktion (Stand 0.14)

In `mitarbeiterbearbeitung.bpmn` ist unterhalb des Hauptablaufs der aufgeklappte
nicht unterbrechende Ereignis-Teilprozess **«Nachricht senden – Prüfung bleibt offen»**
zu sehen: interner Sendeauftrag → Auftrag prüfen, Nachricht/Versandauftrag speichern
→ nur Nebenaktion beendet. `SReview` wird weder abgeschlossen noch neu angelegt.
Auch mit Antwortbedarf bleibt die Mitarbeiterprüfung bestehen. Der bestehende
Hauptpfad `SQuestion` nach bewusster Fortsetzungsentscheidung bleibt separat.

Der Event-Teilprozess liegt im Mitarbeiterprozess; die Bindung an die aktuelle
Prüfaufgabe wird deshalb zusätzlich serverseitig geprüft. Er ist kein weiterer
Mieterempfang. [KOM-01](../../specifications/fallkommunikation.md#mitarbeiternachricht-als-nebenaktion-während-offener-prüfung)
und KOM-AK-27 definieren Wiederholungsschutz und konkurrierende Entscheidungen.
Laufende Nebenaufträge müssen beendet beziehungsweise als ungültig aufgelöst
werden; ein Incident kann die technische Beendigung der Kindinstanz verzögern.
Keine Backend-Implementierung oder Änderung des HTTP-Vertrags.

## Wiedereröffnung · Entwurf 0.15

Eine Wiedereröffnung startet **eine neue führende Prozessinstanz für denselben
Fall**. Die bisherige Instanz bleibt beendet; Fall-ID und Historie bleiben erhalten.
Im Hauptmodell «Aktiven Fall bearbeiten» öffnen: Am Gateway «Erstanlage oder
Wiedereröffnung?» führt der neue Weg zu «Wiedereröffnung prüfen / Neuen Durchlauf
zuordnen» und anschliessend direkt in die Mitarbeiterbearbeitung. Die erste
Dringlichkeitsbewertung und automatische Vorgehensbestimmung werden übersprungen.

In `mitarbeiterbearbeitung.bpmn` prüft «Eintritt prüfen / Fallstand laden» den
serverseitigen Eintrittskontext. Ohne offenen Prüfbedarf führt der Weg bei
«In Bearbeitung» direkt zu «Weiteres Vorgehen umsetzen / Nächste Aktion wählen».
Mit Prüfbedarf bleibt der reguläre Weg über «Mitarbeiterprüfung erforderlich»
und «Fall prüfen und Vorgehen entscheiden» bestehen.

Ein abgelehnter oder überholter Wiedereröffnungsauftrag beendet nur seinen neuen
Startversuch, ohne den Fall zu verändern. Das äussere «Durchlauf beendet» ist
kein zusätzlicher fachlicher Abschluss. Technische Fehler bleiben am Job.
Die Regeln stehen in [FALL-07](../../specifications/fallverwaltung.md#neuer-prozessdurchlauf-bei-wiedereröffnung)
und der ausdrücklichen Ergänzung in [ADR-002](../adr/ADR-002-camunda8-workflow-orchestrierung.md).
Dies ist ein Fachentwurf; Startverträge und Worker sind noch nicht implementiert.

## Externe Wartephase · Mitarbeiterentwurf 0.16

In [mitarbeiterbearbeitung.bpmn](mitarbeiterbearbeitung.bpmn) führt «Nächste Aktion»
über «Auf externe Rückmeldung warten» zur Statusspeicherung und zur Aufgabe
«Externe Rückmeldung abwarten / Fortsetzung entscheiden». Deren drei Ausgänge
verwenden die bestehenden Wege: Fortsetzen → `SProgress`, Wiedervorlage →
`FollowUp`, Abschluss → `SCloseEnd` und anschliessend die Abschlussprüfung im
Hauptprozess. Der Randpfad `SExternalReview` führt bei neuem zwingendem Prüfbedarf
zu `SReviewStatus`. Der neue Abschnitt liegt oberhalb des bisherigen Ablaufs.

Ein Memo oder eine normale Mieternachricht beendet das Warten nicht. Keine
automatische externe Anbindung, Rückkehr zur Erstabklärung oder Systemabschluss.
[FALL-12](../../specifications/fallverwaltung.md#fall-12-warten-auf-externe-rückmeldung)
ist die fachliche Quelle; FALL-AK-29 beschreibt spätere Integrationsprüfungen.
Alle Modelle bleiben nicht ausführbare Fachentwürfe ohne Backend-Änderung.

## Wiedervorlagetermin · Entwurf 0.17

In [wiedervorlage.bpmn](wiedervorlage.bpmn) verwendet `WTimer` nun ein absolutes
`timeDate` mit `= followUpDueAt`. Der gespeicherte Termin entspricht **08:00 Uhr
Europe/Zurich** am Zieldatum, inklusive Sommer-/Winterzeit. «In N Tagen» zählt
Kalendertage; Wochenenden und Feiertage verschieben den Termin nicht.
Neue vergangene oder bereits erreichte Termine werden abgelehnt. Ein gültig
angenommener, erst verzögert verarbeiteter Termin bleibt dagegen wirksam und wird
nicht neu berechnet. [FALL-11](../../specifications/fallverwaltung.md#terminberechnung-kalendertage-und-schweizer-zeit)
und FALL-AK-30 beschreiben die Regeln und geplanten Prüfungen. Variablenbereitstellung
und Laufzeitverhalten sind noch nicht implementiert; das Modell bleibt Fachentwurf.

## Ausbleibende Mieterantwort · Entwurf 0.18

[mieterantwort-pruefen.bpmn](mieterantwort-pruefen.bpmn) kennzeichnet das Warten
in Version 1 ausdrücklich ohne Antwortfrist, automatische Erinnerung oder
zeitgesteuerte Eskalation wegen Schweigens. Es gibt dafür keinen Timer und keinen
zeitgesteuerten Abschlussweg. Ohne anderen fachlichen Auslöser bleiben Wartestatus
und Antwortbedarf bestehen. Manuelle Aktionen folgen den bisherigen Regeln;
der separate Wiedervorlage-Timer bleibt unverändert. Siehe
[KOM-03](../../specifications/fallkommunikation.md#ausbleibende-mieterantwort-in-version-1)
und KOM-AK-28. Nur Fachentwurf, keine Backend-Implementierung.

## Antwortbedarf aufheben · Entwurf 0.19

Im Hauptmodell «Aktiven Fall bearbeiten» liegt der nicht unterbrechende
Mitarbeitereingang `ClearAnswerSide`. Er prüft und speichert die gezielte Aufhebung.
Bei verbleibenden Fragen oder laufender Prüfung/Wiedervorlage bleibt der Hauptpfad
bestehen. Nur die letzte Aufhebung im aktuellen Mieterwartepfad aktiviert den
Randpfad `ReplyCleared` am Call `Reply`. `ClearAnswerContinue` prüft erneut den
aktuellen Stand und bereitet die Fortsetzung vor: direkt `Staff`/`SWork` ohne
KI-Analyse, bei Prüfbedarf reguläre Prüfung oder bei neuen Fragen wieder `Reply`.
Die externe Wartephase wird durch die Nebenaktion ebenfalls nicht beendet.

Keine Änderung der ursprünglichen Nachricht, keine erfundene Antwort und kein
Fallabschluss. Die Diagramme zeigen die Fachfortsetzung; sichere Markerübergabe,
Versionsbindung, Autorisierung und Worker bleiben zu implementieren. Quelle:
[KOM-03](../../specifications/fallkommunikation.md#prozesseingang-für-antwortbedarf-aufheben).

## Direkter Mitarbeiterabschluss im Mieterwartepfad · Entwurf 0.20

Im Hauptmodell unter «Aktiven Fall bearbeiten» nimmt `StaffCloseReplySide`
den Abschlussauftrag unabhängig von Mieternachricht und KI-Auswertung entgegen.
Frühe Ablehnung lässt den aktuellen Pfad bestehen. Nur der geprüfte Auftrag wird
für den aktuellen `Reply`-Call bereitgestellt. Dessen Randereignis `ReplyStaffClose`
führt direkt zu `Close`, ohne `AutoAnswer`. Bei inzwischen veralteter Grundlage
führt der bestehende Fehlerpfad `CloseConflict` zur Mitarbeiterprüfung.
Offener Antwortbedarf endet erst mit erfolgreicher Abschlussspeicherung.

Der neue Eingang liegt unterhalb der Nebenaktion «Antwortbedarf aufheben».
[FALL-07](../../specifications/fallverwaltung.md#direkter-mitarbeiterabschluss-während-warten-auf-mieterantwort)
und FALL-AK-31 beschreiben Regeln und geplante Prüfungen. Nur Fachentwurf;
keine Backend-Implementierung oder Deployment-Freigabe.

## Prüfbedarf ohne Mieternachricht · Entwurf 0.21

Unter «Aktiven Fall bearbeiten» empfängt `ReviewNeedSide` intern festgestellten
Prüfbedarf. Der Abschnitt liegt unterhalb des Mitarbeitereingangs für direkten
Abschluss. Der Dienst prüft Grund, Erkenntnisstand und aktuelle Bearbeitung:
Bestehende Prüfung ergänzen, neuen Mieterwarte-Prüfbedarf bereitstellen oder
Wiederholungen/bereits berücksichtigte Gründe ohne neue Prüfung beenden. Andere
aktive Phasen verwenden ihre bestehenden Prüfwege; deren technische Übergabe
bleibt phasenspezifisch zu konkretisieren.

`ReplyReviewNeeded` unterbricht den aktuellen Mieterwarte-Call und führt über
`ReviewNeedPrepare` zum bestehenden Mitarbeiterpfad. Keine KI-Neuanalyse und keine
Aufhebung offenen Antwortbedarfs. Nach abgeschlossenem Prozess hält die meldende
Funktion den Hinweis intern fest; der beendete Prozess wird nicht neu gestartet.
[FALL-06](../../specifications/fallverwaltung.md#neuer-prüfbedarf-ohne-mieternachricht)
und FALL-AK-32 beschreiben die Regeln und geplanten Prüfungen. Nur Fachentwurf,
keine Backend-Implementierung.

## Prüfbedarf während Bearbeitung und Wiedervorlage · Entwurf 0.22

In [mitarbeiterbearbeitung.bpmn](mitarbeiterbearbeitung.bpmn) sind zwei
unterbrechende Randereignisse ergänzt:

- `SWorkReviewNeeded` an «Weiteres Vorgehen umsetzen» führt unmittelbar zu
  `SReviewStatus` und löst die bisherige Bearbeitungsaufgabe ab.
- `SFollowUpReviewNeeded` an «Wiedervorlage verwalten» beendet den gesamten
  Wiedervorlage-Call samt Timer. `SStopFollowUp` entfernt anschliessend den
  betroffenen Termin und verwendet die gemeinsame Prüfzuordnung von Kontakt-
  und Timerweg. Keine normale Rückgabe des abgebrochenen Calls.

Der unabhängige Prüfbedarf-Eingang im Hauptmodell übergibt neue Gründe gezielt
an diese aktuellen Mitarbeiter-Aufrufe. Bereits offene Prüfungen werden ergänzt;
bereits berücksichtigte Gründe ohne neue Erkenntnis lösen keine Unterbrechung aus.
[FALL-11](../../specifications/fallverwaltung.md#zwingender-prüfbedarf-während-bearbeitung-und-wiedervorlage)
und FALL-AK-33 beschreiben Regeln und geplante Prüfungen. Technische Koordination
bleibt offen; keine Backend-Implementierung.

## Camunda-Mailbeauftragung · Entwurf 0.23

[fallbenachrichtigung-versenden.bpmn](fallbenachrichtigung-versenden.bpmn) enthält
zwei Send-Tasks: Status-/Dringlichkeitsmail und Annahme-/Nachrichtenmail. Beide
verwenden den Mail-Worker `propertyflow.mail.send`. Zuvor wird der gespeicherte
Auftrag geprüft; bereits erledigte Aufträge benötigen keinen erneuten Versand.
Fachlicher Klärbedarf wird dokumentiert und über den bestehenden Prüfbedarfweg
weitergegeben, technische Fehler bleiben Retry/Incident.

Die eigenständige Versandinstanz wird aus der dauerhaft gespeicherten Mailpflicht
gestartet und läuft unabhängig von `ActiveCase`. Kein synchroner Call vom
Fallprozess; Fallabschluss darf ausstehende Mails nicht abbrechen. Die bestehenden
ändernden Tasks verweisen auf diesen Erzeugungs-/Startvertrag. Annahme und weitere
Mieternachrichten erzeugen Mailpflicht bereits beim Speichern, ohne KI-Abhängigkeit.
Keine zusätzliche Abschlussmail. Siehe [BEN-03](../../specifications/benachrichtigungen-und-zustellung.md#ben-03-speicherung-und-zuverlässige-übergabe).
Alle Dateien bleiben Fachentwürfe ohne Backend-Implementierung und Deployment.

## Mitarbeiterrückfrage während Abklärung (0.24)

Im Hauptmodell unter «Aktiven Fall bearbeiten» zeigt der Ereignis-Teilprozess
**«Mitarbeiterrückfrage während In Abklärung»** den internen Mitarbeitereingang.
Nach erfolgreicher Veröffentlichung lösen unterbrechende Randereignisse den
aktuellen automatischen Schritt ab. Die Fortsetzung führt zum vorhandenen
Mieterwarteprozess, bei zwischenzeitlichem zwingendem Prüfbedarf zur
Mitarbeiterprüfung. Abgelehnte Aufträge lassen den laufenden Pfad bestehen.
Massgeblich ist [FALL-06](../../specifications/fallverwaltung.md#mitarbeiterrückfrage-während-in-abklärung)
mit Übergang 29 und FALL-AK-35. Die bedingten Randereignisse beschreiben das
fachliche Verhalten; Markerzustellung, atomare Koordination und ausführbare
Camunda-Realisierung bleiben zu implementieren und zu testen.

## Direkter Mitarbeiterabschluss aus Abklärung (0.25)

Unter «Aktiven Fall bearbeiten» liegt der neue Ereignis-Teilprozess
**«Direkter Mitarbeiterabschluss während In Abklärung»**. Der Camunda-Service-Task
prüft den bestätigten aktuellen Fallstand und speichert den Abschluss ohne
Nachricht und ohne Warten auf KI. Bei Ablehnung bleibt der aktuelle Prozesspfad
bestehen. Nach erfolgreicher Speicherung beendet ein Eskalations-Endereignis
über das unterbrechende Randereignis am übergeordneten **«Aktiven Fall bearbeiten»**
die gesamte aktive Bearbeitung. Dieses Randereignis ist in der Hauptansicht sichtbar.
Die Eskalation bezeichnet hier den erfolgreichen Abschluss, keinen Fehler.

Fachliche Quelle: [FALL-06, Übergang 30](../../specifications/fallverwaltung.md#direkter-mitarbeiterabschluss-während-in-abklärung),
geplante Prüfungen: FALL-AK-36. Der Entwurf ist weiterhin nicht ausführbar;
Abschlusscommit, zuverlässige Prozessbeendigung und Sperre verspäteter Worker
sind noch technisch umzusetzen.

## Direkte Wiedervorlage aus Abklärung (0.26)

Unter «Aktiven Fall bearbeiten» nimmt **«Direkte Wiedervorlage während In Abklärung»**
den Mitarbeiterauftrag entgegen. Nach geprüfter Speicherung lösen die Randereignisse
mit Bezug auf `staffFollowTakeoverReady` die automatische Abklärung ab.
«Aktuellen Wiedervorlage-Eintritt bereitstellen» führt über den Mitarbeiterprozess
direkt zu dessen bestehendem **«Wiedervorlage verwalten»**-Aufruf. Eine vorgeschaltete
Mitarbeiterprüfung oder «In Bearbeitung» ist dafür nicht erforderlich.

Neue Mieternachrichten oder neue zwingende Prüfgründe seit Terminsetzung führen
zur Mitarbeiterprüfung; ein inzwischen aufgehobener Termin wird nicht erneut
angelegt. Die bestehenden Timer-, Änderungs-, Lösch- und Abschlusswege bleiben
massgeblich. Quelle: [FALL-11, Übergang 31](../../specifications/fallverwaltung.md#direkte-wiedervorlage-während-in-abklärung),
geplante Prüfungen FALL-AK-37. Alle betroffenen Modelle bleiben nicht ausführbare
Fachentwürfe; die technische Übergabe und Synchronisierung sind noch umzusetzen.

## Direkte Wiedervorlage aus Mieterwartephase (0.27)

FALL-11, Übergang 32: Der bestehende Eingang `StaffFollowSide` nimmt auch
Terminaufträge aus «Wartet auf Mieterantwort» entgegen. Nach geprüfter Speicherung
unterbricht `StaffFollowAtReply` den Call «Mieterantwort abwarten und Fortsetzung
prüfen». `StaffFollowContinue` führt über `SEntryCheck` direkt zu `FollowUp`.
Die Übergabegarantien aus 0.26 gelten unverändert; Marker sind an den abgelösten
Aufruf gebunden und müssen auch Aktivitätswechsel abdecken. Ab Commit dürfen
alte Auswertungen nicht mehr wirken; keine Outputs abgebrochener Calls übernehmen.

Offene Rückfragen bleiben erhalten. Nachrichteneingang oder Timer führen nach
FALL-11 zur Mitarbeiterprüfung. Bei gültiger Terminlöschung führt `WDeleteTask`
über die bestehende Rückgabe und `SFollowResult` zu «In Bearbeitung», auch bei
offenen Fragen. Kein automatischer Rücksprung in den alten Mieterwartepfad.
Geplante Prüfungen: FALL-AK-38. Die Modelle bleiben nicht ausführbare Fachentwürfe;
zuverlässige Übergabe und Konfliktbehandlung sind noch zur Laufzeit nachzuweisen.

## Rückfrage während externer Wartephase (Mitarbeiterprozess 0.28)

In `mitarbeiterbearbeitung.bpmn` heisst die bestehende Nebenaktion jetzt
**«Nachricht senden – Prüfung oder externe Wartephase bleibt offen»**.
`SMessageSide` nimmt auch Aufträge zur aktuellen `SExternalWait`-Aufgabe entgegen.
Senden mit Antwortbedarf beendet weder die Warteaufgabe noch ihren Status.
Für einen Wechsel zum Mieterwartepfad: zuerst «Bearbeitung fortsetzen», danach
Rückfrage aus «In Bearbeitung» veröffentlichen. Neuer zwingender Prüfbedarf
verwendet weiterhin `SExternalReview`. Fachregeln: FALL-12, Prüfplan FALL-AK-39.
Kein neuer Statusübergang durch die Nebenaktion; das Zustandsdiagramm bleibt
unverändert. Fachentwurf, noch kein ausgeführter Laufzeitnachweis.

## Direkte Mitarbeiterübernahme aus Abklärung (0.29)

`StaffTakeoverSide` empfängt «Bearbeitung übernehmen». Nach serverseitig geprüfter,
gespeicherter Übernahme unterbrechen `StaffTakeoverAt…` den aktuellen automatischen
Schritt. `StaffTakeoverContinue` stellt den geprüften `staffEntryContextRef` bereit.
`SEntryCheck` führt ohne zwingenden Prüfbedarf über `S_EntryWork` zur Bearbeitung,
sonst über den bestehenden Prüfpfad zur Mitarbeiterprüfung. Prüfgründe werden
durch die Übernahme nicht erledigt; neue Gründe während der Übergabe haben Vorrang.

Der Marker `staffTakeoverReady` ist nur für den abgelösten Lauf gültig,
muss Aktivitätswechsel abdecken und wird beim Eintritt konsumiert. Ab Commit
sind alte automatische Wirkungen gesperrt; keine Rückgaben abgebrochener Jobs.
Keine zweite führende Instanz. Zuverlässige Übergabe, Konflikte und Idempotenz
sind noch technisch umzusetzen. Quelle: FALL-06, Übergang 33; Prüfplan FALL-AK-40.

## Zwingender Prüfbedarf unterbricht Automatik (0.30)

Der bestehende Eingang `ReviewNeedSide` bestätigt den Grund in `ReviewNeedCheck`.
`ReviewNeedOther` stellt für den aktuellen automatischen Lauf `reviewNeedAutoReady`
bereit. Die unterbrechenden Randereignisse `AutoReviewAt…` führen über
`AutoReviewJoin` und `AutoReviewContinue` zur regulären Mitarbeiterprüfung in `Staff`.
Sie umfassen Clarification, Triage, Recommend, Validate, CurrentAnalysis, Question,
AutoAnswer und Close. Auch ein Job mit Retry oder Incident wird abgelöst;
kein Warten auf LLM-Ergebnis oder Reparatur.

Ab dauerhafter Bestätigung sind alte automatische Wirkungen gesperrt. Der Marker
ist an Lauf und Prüfentscheid gebunden, deckt Aktivitätswechsel ab und wird
konsumiert. Bestehende Prüfaufgaben werden ergänzt; kein paralleler Staff-Call.
Bereits veröffentlichte Nachrichten und Mailpflichten bleiben erhalten.
Quellen: FALL-06, Übergang 3 und FALL-AK-41. Fachentwurf: zuverlässige Zustellung,
Abbruch bei Incident und Konkurrenzschutz sind noch zur Laufzeit nachzuweisen.

## Manuell nachfragen während Mieterwartephase (Teilprozess 0.32)

In `mieterantwort-pruefen.bpmn` liegt unter dem bisherigen Hauptpfad die
nicht unterbrechende Nebenaktion **«Manuell nachfragen / informieren – Mieterwartephase
bleibt bestehen»** (`RStaffMessageSide`). Senden speichert Nachricht, optionalen
neuen Antwortbedarf und Mailpflicht; der Reply-Aufruf bleibt bestehen. Alte Fragen
bleiben offen. Keine automatische Dublettenerkennung gleicher Texte; identische
Auftragswiederholungen bleiben idempotent. Regeln und Prüfplan: KOM-03 / KOM-AK-30.
Hauptprozess weiterhin 0.31, Mieterantwort-Teilprozess jetzt 0.32; kein Backend geändert.

## Statusneutraler Informationsversand (0.33)

StaffQuestionSide verarbeitet während In Abklärung jetzt beide Nachrichtenarten.
StaffQuestionResult unterscheidet INFORMATION, QUESTION und REJECTED. Nur QUESTION
führt zu StaffQuestionSignal und damit zur gemeinsamen Automatik-Unterbrechung.
INFORMATION endet in StaffInformationDone ohne Marker oder Statuswechsel.

SMessageSide erlaubt zusätzlich Informationen bei aktueller SWork-Aufgabe;
Rückfragen mit Antwortbedarf bleiben dort im bestehenden SQuestion-Fortsetzungsweg.
WQuestionSide erlaubt Informationen und Rückfragen bei unverändertem Timer/Termin.
Message-IDs bleiben erhalten, die Namen sind erweitert. Keine neue Mieternachrichten-
Subscription. In allen Wegen aktuelle Zuordnung, Berechtigung und Fallversion prüfen,
Nachricht/Mailpflicht konsistent sichern und lokale Auftragsdaten verwenden.
Regeln und geplante Laufzeittests: KOM-AK-31. RStaffMessageSide bleibt Stand 0.32.
BPMN-Fachentwurf; technische Bindungen, Konfliktschutz und Ausführung noch offen.

## Falldaten ergänzen (0.34)

Im Hauptmodell ist CaseDataSide als zugeklappter nicht unterbrechender Ereignis-
Teilprozess im ActiveCase sichtbar; über das Navigationssymbol öffnen.
Message_CaseDataChange → CaseDataStart → CaseDataApply → CaseDataEnd.
Ein gemeinsamer Worker-Auftrag für Memo, Objektzuordnung und manuelle Dringlichkeit;
keine drei zusätzlichen Pfade und keine Beendigung der laufenden Bearbeitung.

Verbindlicher Auftragsrahmen: FALL-06 «Gemeinsamer Aktionsvertrag Falldaten ergänzen».
Der Dienst bestimmt aus authentifizierter Identität und Fall den aktuellen führenden
Durchlauf; kein Kind-/User-Task-Schlüssel vom Client autorisiert die Aktion.
Lokale Auftragsreferenz je Nebenaktion, keine Änderung von outcome/decisionRef.
Neue Eingaben vor Aktivierung/nach Abschluss ablehnen; vor Commit erneut prüfen.
Ergebnis, Änderung, Nachweis und nötige Mailpflicht konsistent sichern. Kein
Erfolg ohne Bestätigung; identische Operations-ID erlaubt Wiederholung ohne
zweite Wirkung. Erforderliche neue Prüfgründe über bestehenden Eingang nachliefern.
Technische Korrelation, Rückmeldung, atomare Konfliktbehandlung und Worker fehlen
weiterhin. FALL-AK-43 ist ein Prüfplan. Hauptmodell 0.34 bleibt nicht ausführbar.
