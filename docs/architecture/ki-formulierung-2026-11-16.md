# Interaktive KI-Formulierung: Umsetzung und Annahmen

Stand: 16.11.2026. Grundlage: UC-004, KOM-02, ADR-003 und die beiden bestehenden
HTTP-Verträge unter `/mitarbeiter/faelle/{id}/ki-formulierung`.

Die Formulierungshilfe ist im integrierten Backend angeschlossen. Sie verändert
weder den Fall noch den Camunda-Prozess. Erst das separate, bereits vorhandene
Senden prüft die aktuelle Prozessfreigabe und veröffentlicht eine Nachricht.
Die Modellevaluation bleibt zurückgestellt; `AutomaticClosureRelease` bleibt gesperrt.

## Konkretisierte Annahmen

- Für das sprachliche Ausformulieren genügt zunächst die bewusst eingegebene
  externe Sachanweisung. Es werden keine weiteren Falltexte geladen. Insbesondere
  gelangen weder Memos, interne Änderungsgründe, Empfehlungen noch daraus abgeleitete
  Zusammenfassungen in den Aufruf. Der eigene Spring-AI-Client verwendet keine
  Tools, Retrievalquellen oder Gesprächsspeicher.
- Der vorhandene konfigurierte `ChatModel` wird auch für diese Funktion verwendet.
  Ohne Provider folgt der definierte Fehlerpfad; kein Ersatztext täuscht einen
  erfolgreichen KI-Aufruf vor. Das Devprofil bleibt standardmässig ohne KI.
- Eingabe und Gesamtausgabe sind auf jeweils 5000 UTF-16-Codeeinheiten begrenzt.
  Die Gesamtlaufzeit beträgt standardmässig 60 Sekunden und ist über
  `propertyflow.formulation.timeout` konfigurierbar. Die Browserfrist beträgt
  70 Sekunden; längere Serverfristen bringen deshalb für die Oberfläche keinen Nutzen.
- Es laufen höchstens 32 Versuche im Backend und vier pro Sitzung. Pro Fallansicht
  verhindert das UI einen zweiten gleichzeitigen Versuch. Bis zu 512 technische
  Versuchsreferenzen bleiben während der Sitzung erhalten; bei ausgeschöpfter
  Kapazität wird kein neuer Modellaufruf gestartet. Bestehende IDs werden nicht
  vorzeitig aus dem Wiederholungsschutz entfernt.
- Die Versuchsbuchhaltung liegt nur in der Sitzung und enthält keine gespeicherten
  Entwürfe. Schlüssel sind Fall und UUID; die Sitzung ist zusätzlich an das
  Mitarbeiterkonto gebunden. Ein früher Abbruch sperrt den späteren Start derselben
  ID. Abmeldung/Sitzungsende beendet laufende Versuche. Ein Neustart beendet die
  flüchtigen Sitzungen und Versuche; es gibt keine Wiederaufnahme oder automatischen
  Modellwiederholungen durch die Formulierungshilfe.
- Start erfordert aktuelle Mitarbeiterrechte, existierenden aktiven Fall und
  erreichbare führende Prozessinstanz. Abbrechen benötigt weiterhin Rechte am Fall,
  aber keine aktive Engine und keinen noch offenen Fall. Ein späterer Statuswechsel
  verändert den bereits geprüften Formulierungskontext nicht.
- Die bestehende Backendoberfläche besitzt getrennte externe Nachrichten- und
  Memoformulare. Die KI ist ausschliesslich am externen Formular angeschlossen;
  Memo-Speicherung sperrt KI-Start und Übernahme ebenfalls. Eine gemeinsame
  Eingabe mit Intern-Umschalter wie in der früheren Demo wird hier nicht eingeführt.

## Abbruch und Ausgabe

`MessageFormulationService` beendet bei Abbruch, Gesamtfrist und Verbindungsfehler
die Reactive-Streams-Subscription. Weitere Teile werden verworfen. Der explizite
Abbruch-POST funktioniert auch während des Wartens; beim Verlassen der Seite wird
er mit `keepalive` versucht. Ein Verbindungsabbruch vor dem nächsten Schreibversuch
kann serverseitig erst später sichtbar werden; die Gesamtfrist begrenzt den Versuch.
Ob eine entfernte Modellberechnung tatsächlich gestoppt wird, hängt vom Provider
und dessen Client ab. Es wird keine garantierte Fernabschaltung behauptet.

Das HTTP-Protokoll verwendet `delta`, `complete`, `error` und `aborted`. Fehler
geben keine Providerdiagnosen aus. Fehlendes Abschlussereignis, ungültige Ereignisse,
leere oder überlange Ausgabe gelten als Fehler. Die Darstellung verwendet
`textContent`. Nur ein vollständiger Vorschlag kann ausdrücklich übernommen werden.
Aktuelle Eingaben bleiben bei Fehlern, Abbruch und gleichzeitigem Streamabschluss
während einer Speicherung erhalten. Ein unbestätigter Versand bleibt gegen
nachträgliche Inhaltsänderungen geschützt.

Die Integration verwendet die [Spring-AI-Streaming-API](https://docs.spring.io/spring-ai/reference/api/chatclient.html).
Ein lokaler HTTP-Protokollstub prüft die tatsächliche konfigurierte Bibliothek;
dies ist keine fachliche Modellevaluation.

## Persistenz und Grenzen

Keine neue Tabelle oder Migration ist erforderlich. Die Datenbank bleibt bei V1–V8.
Ungesendete Eingaben und Modellteile fehlen im Verlauf, in der Suche und in Mailaufträgen.
Menschliche Prüfung bleibt nötig: Der Prompt verbietet erfundene Aussagen, garantiert
aber keine semantische Richtigkeit. Technische Tests ersetzen keine Modellqualitätsabnahme.

Der [Prüfbericht](../evaluation/ki-formulierung-2026-11-16.md) und die
[Umsetzungsmatrix](../use_cases/umsetzungsstand-2026-11-16.md) führen den erreichten Stand.
