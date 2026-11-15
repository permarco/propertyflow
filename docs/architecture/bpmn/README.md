# PropertyFlow-Fallprozess

[propertyflow-fallprozess-entwurf.bpmn](propertyflow-fallprozess-entwurf.bpmn)
ist der fachliche BPMN-Entwurf zur gemeinsamen Besprechung, Stand 15.11.2026.
Er enthält BPMN-Diagrammkoordinaten und kann im Camunda Modeler geöffnet werden.

## Ansichten

Die Hauptansicht zeigt den Rahmen **«Aktiven Fall bearbeiten»** zwischen
technischer Startvorbereitung und Abschlussbenachrichtigung. Im Modeler diesen
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

Die eingebetteten Teilprozesse lassen sich im Modeler über ihr
Navigationssymbol öffnen:

1. **Aktiven Fall bearbeiten:** bisheriger Hauptablauf und ein durchgängiger
   Mieternachrichteneingang, der während der gesamten aktiven Bearbeitung gilt.
2. **Mieterantwort abwarten und Fortsetzung prüfen:** auf eine aktuelle
   Auswertung aus dem zentralen Nachrichteneingang warten und die zulässige
   Fortsetzung prüfen. Dieselbe Nachricht wird hier nicht nochmals empfangen
   oder unabhängig neu ausgewertet.
3. **Mitarbeiterprüfung und weitere Bearbeitung:** Fortsetzen, Rückfrage,
   Wiedervorlage oder bestätigter Abschluss. Neue Nachrichten während der
   Prüfung ergänzen dieselbe Aufgabe.
4. **Wiedervorlage verwalten:** Termin, Änderung, Löschung und Abschluss.
   Ein unterbrechendes Randereignis im übergeordneten Mitarbeiterpfad reagiert
   auf einen zentral empfangenen Mieterkontakt. Eine Mitarbeiterrückfrage ist
   weiterhin eine nicht unterbrechende Nebenaktion; sie verändert den Termin nicht.
5. **Mieternachrichten durchgängig verarbeiten:** der neue Ereignis-Teilprozess
   oben links in «Aktiven Fall bearbeiten». Er ordnet den Eingang zu, berücksichtigt
   die aktuelle Bearbeitung und stellt eine geschützte Neubewertung bereit.

Damit liegen sechs Diagrammansichten in einer Datei. Die Teilprozesse
gehören zu derselben führenden Fallprozessinstanz. Der fachliche Abschluss
erfolgt ausschliesslich im Service Task **«Abschluss prüfen und speichern»**.
Erst danach beendet das terminierende Ende den Bereich «Aktiven Fall bearbeiten»
einschliesslich laufender Nachrichtenauswertungen und Empfang. Der äussere
Prozess veranlasst anschliessend die Abschlussbenachrichtigung. Die normalen
Endereignisse der übrigen Teilprozesse sind keine Fallabschlüsse.

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
kein freigegebener Variablenvertrag**. Eingangs-ID und Auswertungskontext müssen
je Ereignispfad lokal bleiben. Die für Hauptpfad und Warteereignisse nötigen
Steuerinformationen werden dagegen ausdrücklich im gemeinsamen
`ActiveCase`-Bereich bereitgestellt. Ein unversioniertes «neue Nachricht»-Flag
genügt nicht: Ein alter Eingang darf keine später neu gesetzte Wiedervorlage
aufheben und keine Auswertung zweimal auslösen.

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
   Abschlussbenachrichtigung wird veranlasst; verspätete Ergebnisse wirken nicht mehr.
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
gesonderte Textfreigabe; `Close` prüft die Systemabschlussbedingungen unmittelbar
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

Die Regeln geben noch keinen konkreten Zuständigkeitskatalog frei. Ebenso bleiben
die Veröffentlichung automatischer Antworttexte und der Fehlerfall zwischen
gespeicherter Antwort und gescheitertem Abschluss separat auszuarbeiten.

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
Nachweisanforderungen sind in FALL-07 bestätigt. Konkreter Zuständigkeitskatalog,
Veröffentlichungsregeln für automatische Antworten und Fehlerbehandlung zwischen
Antwort und Abschluss bleiben separat zu präzisieren. Wiedereröffnung und externe
Wartephasen gehören noch nicht zum hier gemeinsam besprochenen Prozessabschnitt.

Die BPMN-Datei ist unter `docs` abgelegt und wird nicht als Prozessressource
der Anwendung ausgeliefert. Dieser Auftrag ergänzt keine Backend-Logik.

Grundlagen: [Fallverwaltung](../../specifications/fallverwaltung.md),
[Fallkommunikation](../../specifications/fallkommunikation.md),
[ADR-002](../adr/ADR-002-camunda8-workflow-orchestrierung.md),
[UC-018](../../use_cases/UC-018-zustaendigkeit-und-vorgehen-empfehlen.md) und
[UC-021](../../use_cases/UC-021-ki-rueckfrage-vorschlagen.md).
Der Einstieg in «In Abklärung» und die Regeln für automatische Katalogrückfragen
wurden im Chat am 14.11.2026 bestätigt. Spezifikation, Use Cases und Entwurf
sind darauf abgestimmt; es wurde keine Backend-Logik ergänzt.
