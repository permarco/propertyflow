# Java-Richtlinien für PropertyFlow

Diese Richtlinien ergänzen die verbindlichen Vorgaben aus
[copilot-instructions.md](../../.github/copilot-instructions.md).
Die Fachspezifikationen und akzeptierten Architekturentscheidungen bleiben massgeblich.

## Sprache der Kommentare

- Neue Kommentare und neue Javadocs werden auf Deutsch geschrieben.
- Bestehende Kommentare müssen nicht allein wegen ihrer Sprache geändert werden.
- Kommentare erklären den Zweck, eine fachliche Grenze oder einen nicht offensichtlichen
  technischen Zusammenhang. Sie wiederholen nicht lediglich den Java-Code.
- Technische Bezeichner, Fehlercodes und zitierte API-Namen behalten ihre definierte
  Schreibweise. Klassen-, Methoden- und Variablennamen bleiben auf Englisch.
- Die Sprachvorgabe gilt auch für neu geschriebene Kommentare in Tests, SQL,
  JavaScript und Konfigurationsdateien des Projekts.

## Struktur und Lesbarkeit

- Code nach fachlichen Modulen gliedern. Keine direkten Zugriffe auf die Persistenz
  eines anderen Moduls; öffentliche Verträge verwenden.
- Fachlogik gehört in die zuständigen Dienste, nicht in Controller oder Camunda-Worker.
- Externe Aufrufe über eigene Schnittstellen kapseln und ausserhalb langer
  Datenbanktransaktionen ausführen.
- Explizite Typen statt `var`, verständliche englische Namen und einfache,
  auf die aktuellen Anforderungen begrenzte Lösungen verwenden.
- Vier Leerzeichen pro Einrückung; Klammern auch bei einzeiligen Kontrollblöcken.
  Jede Anweisung und Annotation erhält eine eigene Zeile.
- Explizite, alphabetisch sortierte Imports; statische Imports getrennt danach.
  Keine Wildcard- oder unbenutzten Imports.
- Methoden und geschachtelte Typen durch Leerzeilen trennen. Lange Argumentlisten
  und Aufrufketten umbrechen; bei längeren Ketten ein Aufruf pro Zeile.
- Höchstens etwa 150 Zeichen pro Zeile anstreben. Unteilbare URLs und unverändert
  benötigte Literale sind Ausnahmen.
- Stabile Vertragswerte und längere Prompts sinnvoll benennen. Keine globale
  Konstantensammlung und keine mechanische Auslagerung jedes einzelnen Literals.

## Prozess- und Ergebnisprüfung

- Camunda veranlasst Statusänderungen nach der Fallannahme. Worker müssen Wiederholung,
  konkrete Prozessherkunft, aktuelle Fallversion und Bearbeitungsgeneration prüfen.
- LLM-Ausgaben gelten als nicht vertrauenswürdige Eingaben. Fachliche Freigaben
  werden serverseitig geprüft; interne Inhalte bleiben aus dem Kommunikationskontext.
- Neue Geschäftslogik mit aussagekräftigen Tests prüfen. Ein technischer Schematest
  ersetzt keine fachliche Evaluation einer Modellentscheidung.
- Synthetische Freigaben für automatische Abschlüsse gehören ausschliesslich in
  Tests. Sie dürfen weder als Anwendungskonfiguration noch als produktive Freigabe
  verfügbar sein. Die fachliche Evaluation muss Grundbewertung und individuelle
  Abschlussmitteilung einschliesslich ihrer Inhaltsprüfung abdecken.
- Umformatierungen dürfen keine Literale, Textblock-Inhalte oder Fachwirkungen ändern.

Stand der Sprachvorgabe: ausdrückliche Benutzeranweisung vom 15.11.2026.
