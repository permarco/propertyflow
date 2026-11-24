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

## Lombok für Konstruktoren und Datenklassen

**Vereinbart am 24.11.2026:** Lombok wird projektweit für geeigneten mechanischen
Java-Code verwendet. Entscheidend ist die unveränderte Bedeutung der Klasse;
nicht jede Klasse braucht eine Lombok-Annotation.

### Konstruktoren

- Für Konstruktoren, die ausschliesslich erforderliche Abhängigkeiten zuweisen,
  `@RequiredArgsConstructor` verwenden. Abhängigkeiten bleiben `private final`.
  Das gilt insbesondere für Services, Controller, Worker und Persistenzadapter.
- Lombok übernimmt die Reihenfolge der Felder als Parameterreihenfolge. Bei einer
  Umstellung MÜSSEN Parametertypen, Reihenfolge und Sichtbarkeit erhalten bleiben.
  Bereits initialisierte `final`-Felder werden nicht zu Konstruktorparametern.
- Konstruktoren mit Validierung, Normalisierung, defensiven Kopien, `super(...)`,
  Ressourcenaufbau oder sonstiger Initialisierungslogik bleiben explizit.
  Dasselbe gilt für erforderliche Parameterannotationen wie `@Value` oder
  `@Qualifier`, solange deren Übernahme nicht ausdrücklich abgesichert ist.
- Beispiel: `ConversationEvaluationService` erzeugt aus dem
  `PlatformTransactionManager` ein `TransactionTemplate` und setzt
  `PROPAGATION_REQUIRES_NEW`. Dieser Konstruktor bleibt explizit.
  `@RequiredArgsConstructor` würde diese Konfiguration nicht ausführen.
  Die Transaktionsgrenze DARF NICHT zur Einsparung eines Konstruktors verändert werden.
- `@AllArgsConstructor` nur verwenden, wenn tatsächlich alle Instanzfelder zum
  vorgesehenen Konstruktorvertrag gehören. Keine internen Zustandsfelder oder
  Framework-Felder unbeabsichtigt als Parameter veröffentlichen.
- Für den leeren JPA-Konstruktor `@NoArgsConstructor(access = AccessLevel.PROTECTED)`
  verwenden. Validierende fachliche Konstruktoren bleiben zusätzlich bestehen.
  Kein `force = true`, um uninitialisierte Pflichtfelder zu erzwingen.
- Private Konstruktoren reiner Konstanten- und Hilfsklassen dürfen explizit bleiben.

Beispiel für reine Konstruktorinjektion:

```java
@Service
@RequiredArgsConstructor
public class TenantAccountService {

    private final TenantAccountStore accounts;
}
```

### Getter, Setter und unveränderliche Daten

- Einfache Getter mit `@Getter` erzeugen. Feldbezogene Annotationen bevorzugen,
  wenn nur bestimmte Felder öffentlich lesbar sein sollen. Berechnete Getter
  und Getter mit besonderen Rückgaberegeln bleiben explizit.
- `@Setter` nur für ausdrücklich veränderliche Felder verwenden. Bestehende
  Methoden mit Validierung oder fachlichen Zustandsübergängen bleiben erhalten.
  Eine Umstellung DARF keine zusätzlichen öffentlichen Schreibzugriffe erzeugen.
- Bei JPA-Entities kein pauschales `@Data`: Getter, geschützter Leerkonstruktor
  und gegebenenfalls einzelne Setter werden gezielt gewählt. `equals`, `hashCode`
  und `toString` gehören nicht automatisch zum gewünschten Datenzugriff.
- Unveränderliche DTOs und Wertobjekte bleiben vorzugsweise Java-Records.
  Records benötigen keine Lombok-Konstruktoren, Getter oder Setter; kompakte
  Record-Konstruktoren mit Validierung bleiben bestehen.
- `@Data`, `@Value` oder `@ToString` nicht mechanisch ergänzen. Geheimnisse,
  Zugangstokens und personenbezogene Inhalte dürfen nicht durch neu erzeugte
  Methoden unbeabsichtigt ausgegeben werden. Identität und Gleichheit einer
  Klasse werden nicht durch eine Boilerplate-Umstellung neu festgelegt.

### Build und Prüfung

- Die Lombok-Version wird zentral durch das Spring-Boot-Dependency-Management
  festgelegt. Maven deklariert Lombok als optionale `provided`-Abhängigkeit und
  explizit als Annotation Processor; letzteres ist ab JDK 23 erforderlich.
  Lombok wird aus dem ausführbaren Spring-Boot-JAR ausgeschlossen.
- Nach einer Umstellung kompilieren und die betroffenen vorhandenen Tests
  ausführen. Besonders Konstruktorinjektion, Persistenz und Serialisierung
  müssen mit unverändertem Vertrag funktionieren.

Referenzen: [Lombok-Konstruktoren](https://projectlombok.org/features/constructor),
[Getter und Setter](https://projectlombok.org/features/GetterSetter),
[Maven-Konfiguration](https://projectlombok.org/setup/maven).

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
