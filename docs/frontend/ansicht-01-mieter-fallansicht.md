# Screen 01 – Mieter-Fallansicht

**Projekt:** PropertyFlow – KI-gestützte Triage und Bearbeitung von Mieteranliegen  
**Status:** Fachliche Spezifikation und technischer Wireframe (Entwurf zur Umsetzung)  
**Zielgruppe:** Mieterinnen und Mieter  
**Darstellung:** Server-Side Rendering (Thymeleaf) mit gezieltem Vanilla JavaScript  
**Workflow-Orchestrierung:** Camunda 8

**Gemeinsame fachliche Grundlagen:** [Fallverwaltung](../specifications/fallverwaltung.md) definiert Fallanlage und Lebenszyklus; [Fallkommunikation](../specifications/fallkommunikation.md) definiert Nachrichten und ihre Verwendung; [Dringlichkeitsbewertung](../specifications/dringlichkeitsbewertung.md) definiert Stufen, Farben, manuelle Einstufung und transparente Historie; [Fallzugriff und Sicherheit](../specifications/fallzugriff-und-sicherheit.md) definiert den direkten persönlichen Tokenzugriff und seine Berechtigungen. Dieses Dokument beschreibt die Darstellung und Bedienung von Screen 01 und verweist auf die zentralen Regeln und Prüfkriterien.

## 1. Zweck und Abgrenzung

Diese Seite ist der **einzige Einstieg für Mieterinnen und Mieter** in die Erfassung und Kommunikation zu einem Anliegen. Es gibt **kein Mieterportal, kein Benutzerkonto und keinen Login**. Ein neuer Fall wird zunächst über ein Formular erfasst. Danach bleibt der Benutzer auf derselben funktionalen Seite und sieht den Fall sowie seine fortlaufende Kommunikation. Für den späteren erneuten Zugriff erhält er per E-Mail einen persönlichen Fall-Link.

Die Annahme und Fortführung des Falls richten sich nach [FALL-02](../specifications/fallverwaltung.md#fall-02-erfolgreiche-annahme) und [FALL-05](../specifications/fallverwaltung.md#fall-05-fachlicher-lebenszyklus). Die Seite zeigt den fachlichen Bearbeitungsstand und die freigegebene Kommunikation.

**Verbindliche Abgrenzung:** Screen 01 ist eine asynchrone Fall- und Nachrichtenansicht. Es gibt keine direkte Chatfunktion, keinen Chat-/KI-Stream und keine Möglichkeit für den Mieter, eine KI-Verarbeitung oder den Camunda-Prozess abzubrechen. Die KI-gestützten Teilapplikationen werden ausschliesslich innerhalb des Camunda-8-Prozesses über PropertyFlow-Worker/Services ausgeführt.

**Nicht Teil dieses Bildschirms:** Mieterregistrierung, Fallübersicht über mehrere Fälle, Bearbeitung fremder Fälle, interne Backoffice-Aufgaben oder direkte Bedienung von Camunda durch den Browser.

### Render-Strategie

**SSR mit gezielter clientseitiger Interaktivität:** Thymeleaf rendert Erfassungsformular, Falldaten und gespeicherten Verlauf serverseitig. Vanilla JavaScript unterstützt begrenzte Bedienungshilfen wie den Sendezustand und das Kopieren der Case-ID. Neue veröffentlichte Nachrichten und Statusänderungen werden bei aktivem Fall zusätzlich durch periodische Abrufe mit Vanilla JavaScript aktualisiert, ohne die gesamte Seite neu zu laden. Ohne JavaScript bleiben erneuter Aufruf und manuelles Aktualisieren möglich; Details siehe Abschnitt 4.5.1.

Diese Entscheidung folgt `docs/architecture/adr/ADR-003-praesentationsschicht.md` und `../project-context.md`.

## 2. Eine Seite mit drei Zuständen

Die folgenden Ansichten bilden den [fachlichen Lebenszyklus nach FALL-05](../specifications/fallverwaltung.md#fall-05-fachlicher-lebenszyklus) ab. «Neuer Fall» ist der Formularzustand vor erfolgreicher Annahme.

| Zustand | Aufruf / Auslöser | Sichtbarer Inhalt | Zulässige Aktion |
|---|---|---|---|
| **Neuer Fall** | Neue Erfassungsseite, noch keine Case-ID | Formular mit Pflichtfeldern | Einmalig einen neuen Fall absenden |
| **Aktiver Fall** | Nach erfolgreichem Erstabsenden oder über persönlichen Fall-Link | Case-ID, Status, Dringlichkeit mit Quelle, Betreff, aufklappbares ursprüngliches Anliegen, chronologischer Verlauf aus Nachrichten und Dringlichkeitsereignissen, Eingabe für neue Nachricht | Weitere Nachrichten jederzeit hinzufügen, solange der Fall aktiv ist |
| **Abgeschlossener Fall** | Persönlicher Fall-Link nach bestätigtem fachlichem Abschluss in PropertyFlow | Case-ID, Abschlussstatus, Dringlichkeit mit Quelle, ursprüngliches Anliegen und gesamte bisherige freigegebene Historie | Ausschliesslich lesen; keine neuen Nachrichten durch irgendeinen Absender |

**Direkter Übergang:** Nach dem erfolgreichen Erstabsenden gelangt der Mieter **ohne erneute Anmeldung und ohne zuerst die E-Mail öffnen zu müssen** unmittelbar zur aktiven Fallansicht. Der persönliche Link enthält als variablen Zugangswert nur den geheimen Token und wird zusätzlich per E-Mail zugestellt. Die sichere Übergabe richtet sich nach [ZUG-02](../specifications/fallzugriff-und-sicherheit.md#zug-02-ausgabe-und-erstzugriff); ihre technische Umsetzung wird im Service-Block festgelegt.

**Ursprungsdaten:** Die Erfassungsfelder werden nach Annahme schreibgeschützt angezeigt. Ergänzungen sind über das Nachrichtenformular möglich; die fachliche Regel definiert [FALL-04](../specifications/fallverwaltung.md#fall-04-ursprungsdaten-und-zuordnung).

### 2.1 Aufrufe und HTTP-Vertrag

Die folgenden Routen bilden den zentralen [HTTP-Vertrag nach ZUG-07](../specifications/fallzugriff-und-sicherheit.md#zug-07-geplanter-http-vertrag) für diesen Screen ab. Sie sind ein geplanter Vertrag für die spätere Implementierung, keine Aussage über bereits vorhandene Controller. Änderungen des Zugangsmodells werden an der zentralen Quelle gepflegt.

| Methode und Route | Zweck und Verhalten |
|---|---|
| `GET /mieter/fall` | Neues Erfassungsformular ohne Case-ID und ohne Laden bestehender Fälle. Ein GET legt keinen Fall an. |
| `GET /mieter/fall/zugang/{token}` | Token prüfen, den Fall serverseitig ermitteln und den aktiven oder abgeschlossenen Fall direkt unter derselben URL rendern. |
| `POST /mieter/fall` | Neues Anliegen validieren und dauerhaft erfassen; danach HTTP 303 zur persönlichen Token-Fallansicht. |
| `POST /mieter/fall/zugang/{token}/nachrichten` | Token und Schreibberechtigung prüfen, Nachricht zum zugehörigen aktiven Fall speichern; danach HTTP 303 zurück zur gleichen Token-Fallansicht. |

**Direkter GET und Post/Redirect/Get:** Der persönliche Link liefert die Fallansicht direkt aus; der Benutzer bleibt unter `/mieter/fall/zugang/{token}`. Nur nach einem erfolgreichen POST folgt HTTP 303: Nach der Erfassung erstmals zur persönlichen Tokenadresse, nach einer weiteren Nachricht zurück zur gleichen Tokenadresse. Ein anschliessender Browser-Reload wiederholt keinen POST und erzeugt keine zweite Meldung. Ein vorheriges Öffnen der E-Mail ist nicht erforderlich.

**Ungültiger Zugriff:** Fehlende, falsche, abgelaufene oder widerrufene Berechtigungen führen zu einer neutralen Meldung ohne Falldaten und ohne Bestätigung, ob die Case-ID existiert. Ein ungültiger Fall-Link darf nicht stillschweigend als Neuanlage interpretiert werden.

**Keine fachlichen Seiteneffekte durch GET:** Die Regeln aus [ZUG-03](../specifications/fallzugriff-und-sicherheit.md#zug-03-direkter-linkaufruf-und-tokenprüfung) gelten auch hier: Das Öffnen eines Links erzeugt weder Fälle noch Nachrichten oder Freigaben. E-Mail-Linkscanner dürfen den wiederverwendbaren Token nicht verbrauchen.

## 3. Zustand A – Neuer Fall

### 3.1 Eingabefelder

| Feld | Inhalt und Zweck | Pflicht | Validierung / Verhalten |
|---|---|---|---|
| **Betreff** | Kurze, aussagekräftige Bezeichnung des Anliegens, z. B. «Heizung funktioniert nicht». | Ja | Nicht leer oder nur Leerzeichen; Maximallänge als Implementierungsregel festlegen (Vorschlag: 150 Zeichen). |
| **Beschreibung** | Freitext mit Fehlerbild, Beobachtungen, Zeitpunkt und gegebenenfalls Auswirkungen, z. B. «Seit gestern sind alle Heizkörper kalt». | Ja | Nicht leer oder nur Leerzeichen; serverseitige Längenbegrenzung (Vorschlag: 5'000 Zeichen); als **Text**, niemals ungeprüftes HTML behandeln. |
| **Objekt-/Wohnungsreferenz** | Identifikation der betroffenen Liegenschaft und Wohnung, z. B. «Musterstrasse 12, Wohnung 4». | Ja | Nicht leer; für Block 2 Freitext mit Mock-Wert, spätere Prüfung/Zuordnung durch Backend. |
| **E-Mail-Adresse** | Kontaktadresse des Mieters für die Zustellung des persönlichen Fall-Links und spätere Benachrichtigungen. | Ja | Erforderlich; syntaktisch gültige E-Mail; serverseitig prüfen; keine Offenlegung anderer Fälle über diese Adresse. |

**Absendeaktion:** Schaltfläche **«Anliegen absenden»**. Sie ist während des laufenden Absendens gegen wiederholtes Anklicken gesperrt und der Fortschritt wird sichtbar angezeigt. Dies ergänzt, ersetzt aber **nicht** den serverseitigen Schutz gegen Doppelanlage.

### 3.2 Ablauf beim ersten Absenden

1. Mieter füllt die vier Felder aus und klickt auf **«Anliegen absenden»**.
2. Während der Anfrage erscheint ein Sendezustand; bei Feldfehlern bleiben Eingaben erhalten und werden verständlich markiert.
3. Die Annahme erfolgt nach [FALL-02](../specifications/fallverwaltung.md#fall-02-erfolgreiche-annahme). Nach bestätigter Speicherung zeigt die Anwendung **«Ihr Anliegen wurde gespeichert»** und die Case-ID.
4. Die Weiterleitung gemäss Abschnitt 2.1 öffnet unmittelbar die aktive Fallansicht; die ursprünglichen Felder sind schreibgeschützt. Der Mieter muss dafür nicht zuerst den E-Mail-Link öffnen.
5. Der Zustand der Bestätigungs-E-Mail wird getrennt ausgewiesen: ausstehend, an den Maildienst übergeben oder fehlgeschlagen. Die Übergabe ist keine bestätigte Zustellung und kein Nachweis einer Reparatur oder Bearbeitungsfrist.
6. Eine später veröffentlichte Rückfrage oder Antwort erscheint beim nächsten Laden der Fallansicht.

Für Wiederholungen und unklare Sendeergebnisse gilt [FALL-03](../specifications/fallverwaltung.md#fall-03-wiederholung-und-doppelte-einreichung). Das Sperren des Sendeknopfs ist eine Bedienungshilfe. Bei verzögertem Prozessstart oder technischen Ausfällen bleibt die bestätigte Annahme gültig; die fachlichen Regeln stehen in [FALL-06](../specifications/fallverwaltung.md#fall-06-fachlicher-status-und-technischer-workflow) und [FALL-09](../specifications/fallverwaltung.md#fall-09-fehler-und-grenzfälle).

### 3.3 Ergebnis und Fehlermeldungen

- **Erfolg:** Fallansicht mit Case-ID und klar erkennbarem aktuellem Status.
- **Feldfehler:** Betroffenes Feld markieren und verständlichen Hinweis anzeigen; Eingaben beibehalten.
- **Technischer Fehler vor erfolgreicher Annahme:** Keine falsche Erfolgsmeldung; sicheren erneuten Versuch ermöglichen.
- **KI nicht erreichbar:** Erfasster Fall bleibt bestehen und kann weiterbearbeitet werden. Der Mieter erhält einen verständlichen Status statt einer verlorenen Eingabe.
- **E-Mail-Zustellung verzögert/fehlgeschlagen:** Der Fall darf dadurch nicht verloren gehen; Zustand/Fehler wird im Backend nachvollziehbar behandelt.

### 3.4 Ergänzende Eingabe- und Bedienungsregeln

- Der bestehende Feldumfang von vier Feldern bleibt bestehen. Name, Telefonnummer und separate Adressfelder aus dem früheren Review sind optionale Vorschläge, keine zusätzlichen Pflichtfelder.
- Für die Objekt-/Wohnungsreferenz gilt als Review-Vorschlag eine Grenze von 200 Zeichen, für die E-Mail-Adresse von 254 Zeichen. Alle Grenzen werden serverseitig geprüft.
- Auch eine kurze Beschreibung wie «Heizung kaputt» bleibt erfassbar. Fehlende fachliche Details dürfen nicht erfunden werden.
- Eine syntaktisch gültige E-Mail bestätigt keine Identität oder Mietberechtigung. Die eingegebene Adresse ist zunächst unbestätigt; sensible Mieterstammdaten dürfen darüber nicht automatisch veröffentlicht werden.
- Die eingegebene Objekt-/Wohnungsreferenz wird nach [FALL-04](../specifications/fallverwaltung.md#fall-04-ursprungsdaten-und-zuordnung) behandelt; eine interne Zuordnungsklärung erzeugt keinen zusätzlichen Eingabefehler. Öffentliche Auswahlfelder zeigen keine fremden Mieterdaten.
- Der Screen zeigt einen Datenschutzhinweis mit Link zur Erklärung und einen konfigurierten Hinweis, dass das Formular keinen Notfallkontakt ersetzt. Eine zusätzliche Einwilligungscheckbox benötigt eine ausdrücklich festgelegte Grundlage.
- Fehler werden in einer fokussierbaren Übersicht mit Verweisen auf betroffene Felder zusammengefasst. Statusmeldungen sind für assistive Technologien wahrnehmbar.
- Erfassung, Lesen, manuelles Aktualisieren und Nachrichtensenden funktionieren ohne JavaScript; ergänzende Bedienungshilfen blockieren bei dessen Fehlen nicht die Kernabläufe.
- Anhänge, Mietvertragsupload und frei wählbare interne Priorität gehören nicht zum bestehenden Feldumfang.

## 4. Zustand B – Aktiver Fall

### 4.1 Kopfbereich

| Element | Inhalt / Verhalten |
|---|---|
| **Case-ID** | Lesbare Referenz, z. B. `REQ-2026-001`; dient der Wiedererkennung, **nicht** allein als Zugriffsberechtigung. |
| **Betreff** | Ursprünglicher Betreff, schreibgeschützt. |
| **Fallstatus** | Aktueller fachlicher Bearbeitungsstatus aus PropertyFlow gemäss den sieben vereinbarten Status in FALL-05, z. B. «In Abklärung» oder «Wartet auf Mieterantwort». |
| **Dringlichkeit** | Aktuelle wirksame Stufe mit Text und Farbe gemäss [DRING-01/DRING-04](../specifications/dringlichkeitsbewertung.md); ohne wirksame System- oder manuelle Einstufung «Noch nicht bewertet». Änderungsquelle «System» oder «Immobilienverwaltung» erkennbar; ohne Bewertung noch keine Änderungsquelle. Ausschliesslich lesbar. |
| **Ursprüngliches Anliegen anzeigen** | Standardmässig **eingeklappter** Bereich; beim Aufklappen werden alle vier ursprünglichen Felder **schreibgeschützt** angezeigt. |

Der aufklappbare Bereich wird vorzugsweise mit den nativen HTML-Elementen `<details>` und `<summary>` umgesetzt; hierfür ist kein JavaScript erforderlich. Es gibt **keine Bearbeiten-Funktion** für diese Daten.

### 4.2 Kommunikationshistorie

Die Historie enthält **alle für den Mieter sichtbaren Nachrichten und freigegebenen Dringlichkeitsereignisse** dieses Falls auf einer Seite und bleibt über spätere Aufrufe erhalten. **Vereinbart: ältester Eintrag oben, neuester Eintrag unten; keine Seitennavigation.** Die strukturierten Ereignisse folgen Abschnitt 4.2.3 und sind von Nachrichten unterscheidbar.

Jeder Nachrichteneintrag zeigt mindestens:

- **Absenderrolle:** «Mieter», «KI-Assistent» beziehungsweise «System», oder «Immobilienverwaltung».
- **Zeitpunkt:** Erstellungszeit beziehungsweise Versandzeit in einer verständlichen Darstellung.
- **Inhalt:** Die unveränderte inhaltliche Aussage als sicher ausgegebener Text.

**Vereinbarte Absenderanordnung:** Nachrichten des Mieters rechts; Nachrichten von System/KI und Mitarbeitenden links. Der Absender wird zusätzlich als Text bezeichnet, nicht nur über Farbe oder Ausrichtung kenntlich gemacht.

Automatische Antworten können durch Camunda-Service-Tasks und PropertyFlow-Worker ausgelöst werden; manuelle Antworten entstehen durch Backoffice-Mitarbeitende. Ihre externen, veröffentlichten Nachrichten werden im gleichen Verlauf angezeigt. Interne Notizen, technische Prozessvariablen, Modell-Prompts und vertrauliche Backoffice-Daten gehören **nicht** in die Mieteransicht.

**Ursprungsbeschreibung im Verlauf:** Die ursprüngliche Beschreibung erscheint als erste Mieter-Nachricht oder als eindeutig referenzierter Ersteintrag. Es darf dabei keine fachlich doppelte Nachricht entstehen. Die genaue Datenmodellierung wird später festgelegt.

**Zeitpunkte und Reihenfolge:** Speicherung in UTC, Anzeige in `Europe/Zurich` mit korrekter Sommerzeit. Nachrichten mit identischen Zeitstempeln erhalten eine stabile Reihenfolge. Die letzte öffentliche Aktualisierung ist vom technischen internen Bearbeitungszeitpunkt zu unterscheiden.

**Lange Verläufe:** Alle sichtbaren Nachrichten bleiben auf derselben Seite und sind durch Scrollen erreichbar. Die automatische Aktualisierung nach Abschnitt 4.5.1 erhält die Leseposition und ergänzt neue Einträge unten. Beim Lesen älterer Einträge weist sie auf neue Nachrichten hin, ohne ungefragt nach unten zu springen. Manuelles Aktualisieren bleibt verfügbar; Lesebestätigungen sind nicht vorgesehen.

**Veröffentlichung und Zustellung:** Nur ausdrücklich mieteröffentliche Inhalte werden serverseitig in das Lesemodell aufgenommen; interne Inhalte dürfen auch nicht im HTML oder in ergänzenden Antworten enthalten sein. Ein Historieneintrag belegt Speicherung bzw. Veröffentlichung, nicht die E-Mail-Zustellung. Eingehende E-Mail-Antworten benötigen einen separaten sicheren Zuordnungs- und Autorisierungsvertrag; Case-ID oder Absenderadresse allein genügen nicht.

### 4.2.1 Sichtbare Nachrichten in Screen 01

Die fachliche Unterscheidung von internen Memos und externer Kommunikation ist zentral in [KOM-01](../specifications/fallkommunikation.md#kom-01-interne-und-externe-nachrichten) festgelegt.

Der Nachrichtenteil von Screen 01 zeigt nur veröffentlichte externe Nachrichten des autorisierten Falls; hinzu kommen die freigegebenen Dringlichkeitsereignisse nach Abschnitt 4.2.3. Für interne Memos gibt es weder Inhalt, Platzhalter noch Zählerhinweise. Das Mieterformular bietet keine Auswahl «intern/extern». Die sichtbaren Zeitangaben beziehen sich ausschliesslich auf den mieteröffentlichen Verlauf.

### 4.2.2 Grundlage generierter Kommunikationsnachrichten

Für alle LLM-generierten Kommunikationsnachrichten gilt [KOM-02: LLM-Kommunikation ohne interne Memos](../specifications/fallkommunikation.md#kom-02-llm-kommunikation-ohne-interne-memos). Die zentrale Spezifikation definiert den Ausschluss interner Memos und ihrer Ableitungen vor der Generierung sowie die zugehörigen Prüfkriterien. Screen 01 erhält ausschliesslich das zur Veröffentlichung freigegebene Ergebnis.

### 4.2.3 Transparenter Dringlichkeitsverlauf

**Vereinbart am 09.10.2026:** Die Mieteransicht zeigt zusätzlich zu veröffentlichten externen Nachrichten alle gespeicherten fachlichen Änderungen der Dringlichkeit gemäss [DRING-05 bis DRING-07](../specifications/dringlichkeitsbewertung.md). Die Erstbewertung folgt der ursprünglichen Mieter-Mitteilung; nach jeder weiteren Mieter-Nachricht wird die Dringlichkeit erneut im Camunda-gesteuerten Ablauf bewertet. Berechtigte Mitarbeitende können die Stufe im separaten Mitarbeiterdetail manuell anpassen.

Stufenänderungen werden als strukturierte Ereignisse in den bestehenden Fallverlauf eingeordnet: älteste Einträge oben, neueste unten, alle auf einer Seite. Sie sind links angeordnet und eindeutig von Kommunikationsnachrichten unterscheidbar. Jeder Änderungseintrag zeigt Datum/Uhrzeit in Europe/Zurich, alte und neue Stufe mit Text/Farbkennzeichnung sowie «Geändert durch System» oder «Geändert durch Immobilienverwaltung». Die erste Einstufung zeigt beispielsweise «Noch nicht bewertet → Hoch». Die Änderungsquelle ist auch ohne Farbe erkennbar.

Der Mieter kann die aktuelle Stufe und ihre Historie nur lesen. Persönliche Mitarbeiternamen, interne Begründungen/Memos, Prompts und technische Prozessreferenzen werden nicht ausgegeben. Das externe Lesemodell enthält nur die zentral freigegebenen strukturierten Ereignisdaten; es ist kein Zugriff auf die gesamte interne Audit-Historie.

Eine manuell durch die Immobilienverwaltung gesetzte Stufe bleibt gemäss DRING-06 massgeblich und kann vom System nicht überschrieben werden. Spätere automatische Bewertungen erzeugen deshalb keine Änderung der angezeigten manuellen Stufe oder einen fingierten Wechsel im Mieter-Verlauf. Eine erneute manuelle Stufenänderung bleibt transparent mit der Quelle «Immobilienverwaltung» sichtbar.

Ein unverändertes Neubewertungsergebnis erzeugt keinen Stufenwechsel im sichtbaren Verlauf. Während einer Neubewertung und bei ihrem Fehlschlag bleibt die bisher gespeicherte Stufe sichtbar. Gespeicherte Stufenänderungen lösen gemäss BEN-01 eine E-Mail aus; ein Historieneintrag beweist keine E-Mail-Zustellung. Bei abgeschlossenem Fall bleibt die bestehende Dringlichkeitshistorie lesbar. Strukturierte Ereignisse eröffnen keine Ausnahme von der Nachrichtensperre KOM-04.

### 4.3 Neue Nachricht hinzufügen

| Element | Zweck / Verhalten |
|---|---|
| **Nachricht (Mehrzeilentext)** | Mieter ergänzt jederzeit während eines **aktiven** Falls weitere Informationen oder beantwortet eine Rückfrage. |
| **Nachricht senden** | Prüft die Eingabe und sendet sie dem bestehenden Fall zu; **kein neuer Fall und keine neue Prozessinstanz**. |
| **Status-/Fehlerhinweis** | Zeigt an, ob die Nachricht versendet wird, angenommen wurde oder ein Fehler aufgetreten ist. |

Validierung: Eine Nachricht darf nicht leer oder nur aus Leerzeichen bestehen; Maximallänge festlegen (Vorschlag: 5'000 Zeichen); sicher als Text anzeigen. Eine gesendete Nachricht ist nicht nachträglich editierbar. Wiederholte Sendeversuche dürfen dieselbe Nachricht nicht mehrfach speichern.

### 4.4 Ablauf beim Senden einer weiteren Nachricht

1. Mieter öffnet den Fall über den persönlichen Link oder arbeitet nach der Erfassung direkt weiter.
2. PropertyFlow prüft bei jedem Zugriff die Berechtigung anhand des Zugriffsnachweises.
3. Mieter schreibt eine neue Nachricht und klickt auf **«Nachricht senden»**.
4. Der Server prüft erneut Zugriffsberechtigung, Eingabevalidität, Idempotenz und ob der Fall **noch aktiv** ist.
5. Die Nachricht wird dem bestehenden Fall zugeordnet und chronologisch gespeichert.
6. Die Information wird dem bestehenden Camunda-Prozess entsprechend dem **aktuellen BPMN-Schritt** zugeführt (z. B. Message-Korrelation bei einer wartenden Rückfrage oder vorgesehene weitere Verarbeitung). Jede gespeicherte Mieter-Nachricht veranlasst die Dringlichkeitsneubewertung gemäss DRING-05; geschützte manuelle Einstufungen bleiben wirksam. Die Nachricht schliesst eine offene Rückfrage nicht automatisch ab und startet keinen zweiten führenden Prozess.
7. Der Mieter sieht die übernommene externe Nachricht; eine spätere KI- oder Backoffice-Antwort wird nach externer Veröffentlichung im gleichen Verlauf ergänzt. Interne Memos bleiben ausschliesslich im Backoffice.

**Speicherbestätigung:** Die Eingabe wird erst nach bestätigtem Commit geleert. Nachgelagerte Benachrichtigungen oder Camunda-Korrelationen dürfen die gespeicherte Nachricht nicht verlieren. Auch für Nachrichten gilt eine fallgebundene Absende-ID; Wiederholungen erzeugen keinen zweiten Historieneintrag.

**Unklarer Request-Ausgang:** Bei Timeout oder Netzwerkabbruch darf die Seite nicht behaupten, die Nachricht sei sicher ungespeichert. Eine Wiederholung verwendet dieselbe Absende-ID. Ein Browserabbruch storniert keine möglicherweise bereits erfolgte Speicherung und beendet nicht den Camunda-Prozess.

### 4.5 Darstellung asynchroner Verarbeitung

Die Ausführung und Berechtigungsgrenzen sind in [KOM-03](../specifications/fallkommunikation.md#kom-03-asynchrone-fallkommunikation) definiert. Screen 01 bietet keinen Chat-Stream und keine Abbruchaktion.

Neue vollständige Nachrichten werden bei aktivem Fall automatisch nach Abschnitt 4.5.1 sowie beim erneuten Aufruf oder manuellen Aktualisieren angezeigt. Fachliche Status- und Fehlerhinweise erklären den Bearbeitungsstand; interne KI-Zwischenergebnisse werden nicht dargestellt. Alle Nachrichten werden sicher als Text ausgegeben, ohne ungeprüftes `innerHTML`.

**Block 2:** Mock-Daten bilden veröffentlichte Nachrichten und fachliche Bearbeitungsstände ab. Es wird kein Chat-Stream simuliert und keine Abbruchfunktion implementiert.

### 4.5.1 Automatische Aktualisierung von Nachrichten, Status und Dringlichkeit

Bei einem aktiven Fall ruft die sichtbare Seite periodisch die neuesten veröffentlichten externen Nachrichten, freigegebene Dringlichkeitsereignisse, aktuelle wirksame Dringlichkeit mit Quelle und den fachlichen Fallstatus über PropertyFlow ab (**Polling**). Die periodischen Leseabrufe übertragen ausschliesslich gespeicherte Ergebnisse. Sie erzeugen keine direkte KI-Chatsitzung und lösen keine Camunda-Verarbeitung aus.

**Vereinbartes Intervall:** Ein Abruf alle 20 Sekunden, also ungefähr dreimal pro Minute, solange die aktive Fallansicht sichtbar ist. Es läuft höchstens eine Anfrage gleichzeitig; dauert sie länger als das Intervall, wird kein überlappender Abruf gestartet. Die Pausen und Fehlerregeln unten gelten weiterhin.

- Aktualisiert werden nur Verlauf einschliesslich gespeicherter Dringlichkeitsänderungen, aktuelle Dringlichkeit mit Änderungsquelle, fachlicher Status und die Rückmeldung zur Aktualisierung. Die gesamte Seite wird nicht automatisch neu geladen. Ein ungesendeter Nachrichtentext, Tastaturfokus, aufgeklappte Ursprungsdaten und Leseposition bleiben bei gewöhnlichen Aktualisierungen erhalten. Die Eingabe wird weder automatisch gespeichert noch bei diesen Abrufen an den Server übertragen.
- Nachrichten und Dringlichkeitsereignisse werden anhand stabiler IDs abgeglichen und in stabiler chronologischer Reihenfolge angezeigt. Wiederholte Abrufe dürfen keine doppelten Einträge erzeugen; unveränderte Daten verursachen keine unnötigen sichtbaren Änderungen.
- Liest der Benutzer ältere Einträge, wird nicht automatisch gescrollt. Ein Hinweis «Neue Einträge vorhanden» ermöglicht den bewussten Wechsel zu den neuesten Nachrichten oder Dringlichkeitsereignissen. Die Rückmeldung ist auch für assistive Technologien zugänglich, ohne bei jedem unveränderten Abruf den gesamten Verlauf erneut vorzulesen.
- Ist der Browser-Tab nicht sichtbar, pausieren die Abrufe. Bei Rückkehr erfolgt eine Aktualisierung und danach die Fortsetzung des Intervalls. Navigation oder Schliessen der Seite beendet nur die periodischen Browserabrufe, niemals den Camunda-Prozess oder einen KI-Schritt.
- Ein erkannter Fallabschluss aktualisiert den Status, berücksichtigt den verfügbaren veröffentlichten Abschlussstand und ersetzt die Nachrichteneingabe durch den Abschlusshinweis. Ungesendeter Text wird nicht übertragen oder gespeichert. Danach enden die periodischen Abrufe; manuelles Lesen und Aktualisieren bleiben bei gültigem Zugang möglich.
- Bei einem vorübergehenden Netzwerk- oder Serverfehler bleiben die bereits angezeigten Daten bestehen. Ein Hinweis «Aktualisierung momentan nicht möglich» kennzeichnet den Zustand; Wiederholungen erfolgen mit wachsender Wartezeit statt einer schnellen Endlosschleife. Eine erfolgreiche Aktualisierung entfernt den Fehlerhinweis. Konkrete Wartezeiten bleiben festzulegen.
- Ein Rate Limit wird berücksichtigt, einschliesslich `Retry-After`, soweit angegeben. Bei ungültigem, abgelaufenem oder widerrufenem Zugang enden die Abrufe und weitere Schreibaktionen werden gesperrt; es erscheint die neutrale Zugriffsrückmeldung. Bereits ausgelieferte Inhalte können technisch nicht zurückgerufen werden.
- Jeder Abruf prüft serverseitig den Token und dieselben Fall-/Sichtbarkeitsrechte wie die vollständige Ansicht. Interne Memos, deren Metadaten und KI-Zwischenergebnisse werden niemals übertragen. Token-, Cache-, Referrer- und Protokollschutz gelten auch für diese Anfragen.
- Ohne JavaScript bleibt die SSR-Ansicht mit manueller Aktualisierung und Formular nutzbar. Die vereinbarten E-Mails werden unabhängig von Polling und geöffnetem Tab versendet.

Der konkrete Lesevertrag für die ergänzenden Abrufe (Antwortformat, inkrementeller Abgleich oder Snapshot und gegebenenfalls zusätzliche Route) wird im Service-Design festgelegt. Die Browseradresse bleibt `/mieter/fall/zugang/<geheimer-token>`. Die Umsetzung verwendet Thymeleaf und gezieltes Vanilla JavaScript; eine neue Architekturentscheidung ist für diese begrenzte Aktualisierung nicht erforderlich.

### 4.6 Weitere Anzeige- und Navigationsvorschläge aus dem Review

- Die Case-ID kann kopiert werden. Sichtbare Zeitangaben umfassen den Eingang und die letzte mieteröffentliche Aktualisierung.
- **Vereinbarte Kontaktanzeige:** Die E-Mail-Adresse wird im bestehenden Fall serverseitig maskiert, mit erstem Buchstaben und einer kurzen Endung; Beispiel: `mieter@example.ch` → `m***ple.ch`. Das Beispiel zeigt die letzten sechs Zeichen. Der mittlere Teil einschliesslich der vollständigen Domain bleibt verborgen; kurze Adressen dürfen nicht versehentlich vollständig ausgegeben werden. Auch HTML-Attribute, Tooltips und ergänzende Lesedaten enthalten keine unmaskierte Kontaktadresse. Im Erfassungsformular bleibt die selbst eingegebene Adresse zur Prüfung sichtbar; ihre Speicherung und Verwendung für den Versand bleiben unverändert.
- Die Aktion «Neues Anliegen melden» führt zu `GET /mieter/fall`. Sie ist auch bei einem abgeschlossenen Fall verfügbar; erst das Absenden erzeugt einen separaten Fall. Eine Wiedereröffnung des bisherigen Falls gehört nicht zu Screen 01.
- Die sieben vereinbarten Bearbeitungsstatus und ihre Bedeutungen werden zentral unter [FALL-05](../specifications/fallverwaltung.md#fall-05-fachlicher-lebenszyklus) gepflegt. Bei aktiven Fällen mit offener Objektzuordnung, fehlgeschlagener KI-Analyse oder klärungsbedürftigem E-Mail-Versand wird «Mitarbeiterprüfung erforderlich» angezeigt. Intern gespeicherte Problemhinweise und technische Fehlerdetails werden dadurch nicht mieteröffentlich; auch die Mitarbeiterliste zeigt keine fallbezogenen Problemhinweise.
- Technische Camunda-Incidents, Retries und KI-Analysezustände sind keine fachlichen Mieterstatus. Ein technischer Verzögerungshinweis ersetzt nicht den letzten bestätigten fachlichen Status.
- Frühere Nachrichten werden im Screen weder bearbeitet noch gelöscht; Korrekturen erfolgen als neue Nachricht. Besondere Datenschutz-/Löschprozesse und Aufbewahrungsfristen benötigen eine separate Regelung.

## 5. Zustand C – Abgeschlossener Fall

Sobald PropertyFlow den fachlichen Abschluss nach [FALL-07](../specifications/fallverwaltung.md#fall-07-abschluss-und-zeit-danach) bestätigt, zeigt die Ansicht **«Abgeschlossen»**.

Der Abschluss kann durch einen Mitarbeiter oder durch das System erfolgen. Vereinbarte Systemgründe sind eine eindeutige Bestätigung des Mieters «Problem behoben, keine weitere Hilfe nötig» oder eindeutig fehlender Bearbeitungsbedarf der Verwaltung gemäss hinterlegter Fachregel, etwa ein dem Mieter zugeordneter Glühbirnenwechsel. In beiden Fällen gelten dieselbe Leseansicht, Nachrichtensperre und Abschlussmail. Der Mieter teilt die Behebung über eine Nachricht mit; die eigentliche Statusänderung übernimmt PropertyFlow nach FALL-07.

- Betreff, Case-ID, ursprüngliche Angaben, wirksame Dringlichkeit mit Quelle und der vollständige mieteröffentliche Verlauf einschliesslich Dringlichkeitsereignissen bleiben einsehbar.
- Der Bereich «Ursprüngliches Anliegen anzeigen» bleibt **standardmässig eingeklappt** und jederzeit aufklappbar.
- **Kein Absender** darf weitere Nachrichten erzeugen: weder Mieter noch System/KI noch Backoffice-Mitarbeitende.
- Der Texteingabebereich und der Senden-Button werden durch folgenden Hinweis ersetzt:

> Dieser Fall ist abgeschlossen. Es können keine weiteren Nachrichten hinzugefügt werden.

**Zentrale Regel:** Die für alle Absender geltende Schreibsperre und ihre Durchsetzung sind in [KOM-04](../specifications/fallkommunikation.md#kom-04-nachrichtensperre-nach-fallabschluss) definiert. Screen 01 bildet diese Sperre mit dem obigen Hinweis ab.

**Prozessstatus:** Die Ansicht verwendet den fachlichen Status aus PropertyFlow gemäss [FALL-06](../specifications/fallverwaltung.md#fall-06-fachlicher-status-und-technischer-workflow).

## 6. Persönlicher Fall-Link und Datenschutz

Die zentrale Spezifikation [Fallzugriff und Sicherheit](../specifications/fallzugriff-und-sicherheit.md) führt die Zugriffsregeln und ihre Prüfkriterien. Die [Token-Aufbewahrung für E-Mails](../specifications/fallzugriff-und-sicherheit.md#speicherung-und-wiederverwendung-für-e-mails) verwendet einen Hash und eine verschlüsselte Kopie desselben Tokens. Es bleibt ein gültiger Mieterlink pro Fall; wer ihn erhält, besitzt dieselben Mieterrechte, ohne damit seine Identität nachzuweisen. Screen 01 bildet diese Regeln ab; Tokenstärke, Gültigkeit, Speicherung und Linkersatz werden dort gepflegt.

**Persönlicher E-Mail-Link (Beispiel mit Platzhalter):**

`https://propertyflow.example/mieter/fall/zugang/<geheimer-token>`

Der Link enthält nur den geheimen Token als variablen Zugangswert. PropertyFlow ermittelt den Fall serverseitig. Die Case-ID wird weiterhin in der Fallansicht angezeigt und kann als Referenz kopiert werden; sie ist kein Zugangsschlüssel.

Nach erfolgreicher Tokenprüfung wird die Fallansicht direkt unter derselben URL angezeigt. Gemäss [ZUG-07](../specifications/fallzugriff-und-sicherheit.md#zug-07-geplanter-http-vertrag) gibt es keine Weiterleitung auf eine Case-ID- oder sitzungsbasierte Falladresse. Die Case-ID steht im Inhalt der Ansicht. Der persönliche E-Mail-Link und die Browseradresse sind identisch und innerhalb der Tokengültigkeit wiederverwendbar; die vollständige Adresse ist als Zugangsschlüssel zu behandeln.

### 6.1 E-Mail-Benachrichtigungen

Gemäss [Benachrichtigungen und Zustellung](../specifications/benachrichtigungen-und-zustellung.md) erhält der Mieter eine E-Mail bei Fallannahme, jeder eigenen gespeicherten Nachricht, veröffentlichten externen KI-/Mitarbeiternachrichten, sichtbaren fachlichen Status- und wirksamen Dringlichkeitsänderungen sowie Fallabschluss. Interne Memos, Entwürfe, unveränderte Bewertungen und technische Verarbeitungsschritte lösen keine E-Mail aus.

Die E-Mail enthält eine kurze Änderungsinformation, Case-ID und denselben gültigen persönlichen Link; vollständige Nachrichten bleiben in dieser Fallansicht. Auch eine bereits geöffnete Ansicht ersetzt die E-Mail nicht. Mailfehler nehmen eine bestätigte Speicherung nicht zurück. Die Abschlussmail erzeugt keinen neuen Nachrichteneintrag nach Schliessung.

### 6.2 Zentrale Regeln und Umsetzung im Screen

| Thema | Zentrale Quelle | Auswirkung auf Screen 01 |
|---|---|---|
| Case-ID und geheimer Zugang | [ZUG-01](../specifications/fallzugriff-und-sicherheit.md#zug-01-case-id-und-geheimer-token) | Case-ID anzeigen und kopierbar machen; daraus keinen ungeschützten Fallzugriff ableiten. |
| Zugriff nach Erstabsenden | [ZUG-02](../specifications/fallzugriff-und-sicherheit.md#zug-02-ausgabe-und-erstzugriff) | Aktive Fallansicht unmittelbar öffnen; kein vorgängiger E-Mail-Aufruf erforderlich. |
| Direkter Tokenzugriff | [ZUG-03](../specifications/fallzugriff-und-sicherheit.md#zug-03-direkter-linkaufruf-und-tokenprüfung) | Fall direkt unter der Tokenadresse anzeigen; jeden Lese- und Schreibaufruf serverseitig erneut autorisieren. |
| Rechte | [ZUG-04](../specifications/fallzugriff-und-sicherheit.md#zug-04-rechte-und-vertrauensgrenzen) | Nur zulässige Falldaten und Aktionen darstellen. |
| Ablauf, Widerruf und Ersatz | [ZUG-05](../specifications/fallzugriff-und-sicherheit.md#zug-05-ablauf-widerruf-und-ersatz) | Neutralen Zugriffshinweis zeigen; keinen neuen Fall als Ersatz anlegen. |
| Token- und Datenschutz | [ZUG-06](../specifications/fallzugriff-und-sicherheit.md#zug-06-schutz-von-token-und-falldaten) | Geschützte Formulare und Ausgaben; keine Tokens in Logs, Drittanbieterrequests oder dauerhafter Browserspeicherung. |
| Fehlerverhalten | [ZUG-08](../specifications/fallzugriff-und-sicherheit.md#zug-08-fehlerverhalten) | Die folgenden Rückmeldungen ohne Preisgabe geschützter Inhalte anzeigen. |

**Block-2-Grenze:** Echte Tokenausgabe und echter E-Mail-Versand sind für den Mock noch nicht erforderlich. Die Ansicht darf keinen produktiven Zugriffsschutz vortäuschen. Die Tokenlaufzeit und technischen Schutzparameter bleiben [zentrale Review-Vorschläge](../specifications/fallzugriff-und-sicherheit.md#review-vorschläge-und-offene-entscheidungen).

### 6.3 Weitere Fehlerfälle und Rückmeldungen

| Fehlerfall | Erwartetes Verhalten |
|---|---|
| Ungültiger oder fehlender Fallzugriff | «Dieser Falllink ist nicht verfügbar. Bitte verwenden Sie einen gültigen Link oder kontaktieren Sie die Verwaltung.» Keine Falldaten oder Existenzbestätigung. |
| Token bei bereits geöffneter Seite abgelaufen oder widerrufen | Weitere Zugriffe und Speichern ablehnen; auf einen gültigen Ersatzlink beziehungsweise die Verwaltung hinweisen. Ungesendeten Text nicht automatisch speichern. |
| CSRF-Prüfung fehlgeschlagen | Anfrage ohne Seiteneffekt ablehnen; erneutes Laden des Formulars ermöglichen. |
| Fall während der Eingabe abgeschlossen | Nachricht zurückweisen und aktuellen Status erklären; Text im noch sicheren Browserkontext zur bewussten Übernahme erhalten. |
| Netzwerkabbruch mit unklarem Ergebnis | Unklaren Ausgang erklären und Wiederholung mit derselben Absende-ID ermöglichen. |
| Rate Limit erreicht | Verständliche Warteinformation, gegebenenfalls `Retry-After`; keine automatische Dauerschleife. |
| Unerwarteter Serverfehler | Neutrale Meldung mit nicht sensitiver Referenznummer; keine Stacktraces, Tokens oder internen Inhalte. |

Eingabeerhalt bedeutet keine dauerhafte Browserspeicherung. Freitext und Tokens werden nicht in Local Storage gespeichert. Nach vollständigem Browser- oder Netzwerkausfall ist Eingabeerhalt nicht garantiert.

### 6.4 Darstellung der Berechtigungen

Die verbindliche Rechteübersicht wird zentral in [ZUG-04](../specifications/fallzugriff-und-sicherheit.md#zug-04-rechte-und-vertrauensgrenzen) gepflegt. Screen 01 zeigt ohne Fallberechtigung ausschliesslich die Neuanlage; ungültige Fallaufrufe führen zur neutralen Fehlermeldung. Mit gültiger Berechtigung zeigt er genau den autorisierten Fall und seine freigegebene Historie. Das Nachrichtenformular ist nur bei aktivem Fall verfügbar.

Interne Memos, Prioritätsänderung, Beauftragung, fachliche Freigabe, Schliessung, Wiedereröffnung und KI-/Prozessabbruch haben hier keine Bedienelemente. Das Ausblenden ersetzt die serverseitige Prüfung nicht. Interne Bearbeitung erfolgt in separat authentifizierten Backoffice-Ansichten.

## 7. Camunda-8-Verantwortung und Integration

| Bestandteil | Verantwortung |
|---|---|
| **Thymeleaf-Seite + Vanilla JavaScript** | Formular, Fallverlauf mit veröffentlichten Nachrichten und Dringlichkeitsereignissen, aufklappbare Ursprungsdaten, fachlicher Status und wirksame Dringlichkeit mit Quelle |
| **PropertyFlow-Backend (Spring Boot)** | HTTP-Validierung, Autorisierung per persönlichem Zugriff, Fallanlage, Nachrichtenannahme, Anwendungslogik und Integrationsadapter |
| **PropertyFlow-Datenhaltung (PostgreSQL)** | Persistente fachliche Falldaten, Nachrichten mit Sichtbarkeit und Veröffentlichungszustand, Absender, Zeitpunkte und fachlich relevante Historie |
| **Camunda 8** | Technische Prozessinstanz, Prozessposition, Message-Catch-/Wartezustände, Service Tasks, User Tasks, Gateways, Timer und Prozessabschluss |
| **KI-/RAG-Services** | Fachlich begrenzte Analysen, Rückfragen und Antworten; Aufruf über kontrollierte Worker/Services |
| **Backoffice** | Manuelle Prüfung, Kommunikation und erlaubte Entscheidungen in Camunda-gesteuerten Prozessschritten |

Der Browser ruft **nicht direkt Camunda** auf. Die Fachlogik bleibt im PropertyFlow-Backend. Camunda verwaltet technische Prozessvariablen möglichst referenzbasiert und nicht als vollständige Kopie sensibler Mieter- und Kommunikationsdaten.

**Regel zur KI-Autonomie:** Camunda führt automatische Abschlüsse entlang der vereinbarten, serverseitig geprüften Fachregeln aus FALL-07 aus. Auch automatische Antworten folgen den Kommunikationsregeln. Für kritische fachliche Aktionen bleiben die in den bestehenden ADRs und Guardrails vorgesehenen menschlichen Prüfungen erhalten. Die bestätigten Systemabschlussgründe verlangen bei eindeutiger Einordnung keine zusätzliche Einzelfreigabe; eine unklare Einordnung führt zur Mitarbeiterprüfung.

## 8. Technische Wireframes (Markdown / ASCII)

### 8.1 Neuer Fall – noch keine Case-ID

```text
┌──────────────────────────────────────────────────────────┐
│ PropertyFlow                                             │
├──────────────────────────────────────────────────────────┤
│ Neues Mieteranliegen                                     │
│                                                          │
│ Betreff *                                                │
│ ┌──────────────────────────────────────────────────────┐ │
│ │ Heizung funktioniert nicht                          │ │
│ └──────────────────────────────────────────────────────┘ │
│                                                          │
│ Objekt / Wohnung *                                       │
│ ┌──────────────────────────────────────────────────────┐ │
│ │ Musterstrasse 12, Wohnung 4                         │ │
│ └──────────────────────────────────────────────────────┘ │
│                                                          │
│ E-Mail-Adresse *                                         │
│ ┌──────────────────────────────────────────────────────┐ │
│ │ mieter@example.ch                                   │ │
│ └──────────────────────────────────────────────────────┘ │
│                                                          │
│ Beschreibung *                                           │
│ ┌──────────────────────────────────────────────────────┐ │
│ │ Seit gestern funktionieren alle Heizkörper nicht.  │ │
│ │                                                      │ │
│ └──────────────────────────────────────────────────────┘ │
│                                                          │
│ * Pflichtfelder                            [Absenden]    │
└──────────────────────────────────────────────────────────┘
```

**Aktion:** «Absenden» legt den Fall an, veranlasst zuverlässig dessen Camunda-Prozessstart und öffnet unmittelbar die aktive Fallansicht.

### 8.2 Aktiver Fall – Kommunikation

**Status:** Technischer Entwurf zur gemeinsamen Besprechung, keine endgültige Gestaltung. Die fachlichen Regeln aus Abschnitt 4 bleiben massgeblich.

Die Ansicht verwendet eine Hauptspalte. **Vereinbarte Reihenfolge:** Kopf → Ursprungsdaten → Verlauf → Nachrichteneingabe. Nachrichten des Mieters sind rechts angeordnet, veröffentlichte Nachrichten der Verwaltung und von KI/System links. Absender und Datum bleiben als Text erkennbar. Farben, Schrift, Abstände und endgültige Breite werden später gestaltet.

```text
+------------------------------------------------------------------+
| PropertyFlow                                                     |
+------------------------------------------------------------------+
| Fall REQ-2026-001                              [In Abklärung]    |
| Heizung funktioniert nicht                                       |
| Dringlichkeit: Hoch (Orange) · Geändert durch System              |
|                                                                  |
| > Ursprüngliches Anliegen anzeigen                               |
+------------------------------------------------------------------+
| Fallverlauf                               [Ansicht aktualisieren]|
|                                                                  |
|                          +-----------------------------------+   |
|                          | Mieter · 09.10.2026, 09:12         |  |
|                          | Seit gestern funktioniert die    |    |
|                          | Heizung nicht mehr.               |   |
|                          +-----------------------------------+   |
|                                                                  |
| [Dringlichkeit geändert · System · 09.10.2026, 09:20]             |
| Noch nicht bewertet (Blau) → Hoch (Orange)                        |
|                                                                  |
| +-------------------------------------------+                    |
| | KI-Assistent · 09.10.2026, 09:30           |                   |
| | Sind alle Heizkörper betroffen?           |                    |
| +-------------------------------------------+                    |
|                                                                  |
|                          +-----------------------------------+   |
|                          | Mieter · 09.10.2026, 10:00         |  |
|                          | Ja, alle Heizkörper.              |   |
|                          +-----------------------------------+   |
|                                                                  |
| +-------------------------------------------+                    |
| | Immobilienverwaltung · 09.10.2026, 11:00   |                   |
| | Wir haben den Hauswart informiert.        |                    |
| +-------------------------------------------+                    |
|                                                                  |
+------------------------------------------------------------------+
| Weitere Informationen hinzufügen                                 |
| Nachricht                                                        |
| +------------------------------------------------------------+   |
| |                                                            |   |
| |                                                            |   |
| +------------------------------------------------------------+   |
|                                            [Nachricht senden]    |
|                                                                  |
| Bei Änderungen erhalten Sie eine E-Mail mit Ihrem Falllink.      |
+------------------------------------------------------------------+
```

Die Beispiele enthalten fiktive veröffentlichte externe Nachrichten und ein freigegebenes Dringlichkeitsereignis. «In Abklärung» ist ein vereinbarter Bearbeitungsstatus gemäss FALL-05. Die Ursprungsbeschreibung ist der erste Historieneintrag und erzeugt keine zweite fachliche Nachricht.

#### 8.2.1 Bereiche und Verhalten

| Bereich | Verhalten |
|---|---|
| Kopf | Case-ID, unveränderlicher Betreff, aktueller fachlicher Status und Dringlichkeit mit Änderungsquelle. Die Case-ID ist eine Referenz, kein Zugangsschlüssel. |
| Ursprungsdaten | Standardmässig eingeklappt. Native `<details>`-/`<summary>`-Darstellung mit allen vier ursprünglichen Feldern, ausschliesslich lesbar. |
| Verlauf | Alle sichtbaren Nachrichten auf einer Seite, chronologisch von oben nach unten, neueste Nachricht zuunterst; jeder Eintrag mit Absenderrolle, Datum, Uhrzeit und sicher ausgegebenem Text. |
| Dringlichkeitsereignisse | Im selben Verlauf chronologisch eingeordnet; alter/neuer Wert, Zeitpunkt und Änderungsquelle System/Immobilienverwaltung. Keine internen Auditdetails oder Bearbeitungsaktion für Mieter. |
| Aktualisieren | Automatisch bei aktivem Fall gemäss Abschnitt 4.5.1; vereinbartes Intervall 20 Sekunden. Manueller GET bleibt verfügbar. Kein Stream, kein Prozessstart und keine Schreibaktion. |
| Langer Verlauf | Lesen durch Scrollen, ohne Seitennavigation. Neue Nachrichten werden unten ergänzt; beim Lesen älterer Einträge bleibt die Position erhalten. |
| Eingabe | Sichtbares Label «Nachricht», Mehrzeilentext und «Nachricht senden». Keine Auswahl intern/extern. |
| Rückmeldung | Speicherbestätigung oder verständlicher Fehler bei der Eingabe. Bestätigte Speicherung bedeutet keine bestätigte E-Mail-Zustellung. |
| E-Mail-Hinweis | Auch eigene gespeicherte Nachrichten werden per E-Mail bestätigt. Der Hinweis ist ein Darstellungsvorschlag. |

#### 8.2.2 Aufgeklappte Ursprungsdaten

```text
v Ursprüngliches Anliegen anzeigen
  Betreff:        Heizung funktioniert nicht
  Objekt/Wohnung: Musterstrasse 12, Wohnung 4
  E-Mail-Adresse: m***ple.ch
  Beschreibung:  Seit gestern funktioniert die Heizung nicht mehr.
  (Alle Werte schreibgeschützt)
```

Die Kontaktanzeige verwendet hier fiktive Daten und die vereinbarte Maskierung aus Abschnitt 4.6: erster Buchstabe, maskierter Mittelteil und kurze Endung (`m***ple.ch`).

#### 8.2.3 Rückmeldungen und kleinere Bildschirme

- **Ungültige Eingabe:** Feldbezogener Hinweis unter dem Nachrichtenfeld; kein Historieneintrag. Die Eingabe bleibt bei wieder angezeigtem Formular erhalten, soweit die gültige Berechtigung und der technische Ablauf dies erlauben.
- **Speicherung läuft:** Bei vorhandener JavaScript-Ergänzung vorübergehende Rückmeldung «Nachricht wird gespeichert …». Das Formular bleibt ohne JavaScript nutzbar. Dieser Zustand betrifft die Speicherung, nicht einen laufenden KI-Schritt.
- **Speicherung bestätigt:** Nach der vorgesehenen POST-/303-/GET-Folge erscheint die vollständige eigene Nachricht im Verlauf. Das Eingabefeld wird erst nach bestätigter Speicherung geleert.
- **Aktualisieren mit ungesendeter Eingabe:** Periodische Abrufe erhalten die Eingabe ohne Seitenneuladen. Bei manueller Navigation bleibt ein Hinweis auf möglichen Textverlust ein Vorschlag zur Besprechung. Kein automatisches Speichern und keine dauerhafte Browserspeicherung einführen.
- **Zwischenzeitlicher Abschluss:** Nachrichteneingabe durch den Abschlusshinweis ersetzen; die serverseitige Sperre bleibt massgeblich. Kein KI-Abbruchbutton.
- **Zugang ungültig:** Neutrale Zugangsfehlermeldung statt Falldaten. Kein stiller Wechsel zur Neuanlage.
- **Schmaler Bildschirm:** Gleiche Reihenfolge in einer Spalte; Nachrichten können die verfügbare Breite nutzen. Absendertexte erhalten die Unterscheidung auch ohne deutliche Links-/Rechtsanordnung. Navigation und Senden bleiben ohne horizontales Scrollen erreichbar.

#### 8.2.4 Vereinbarte Anordnung und verbleibende Gestaltung

- **Bereiche:** Kopf → Ursprungsdaten → Verlauf → Nachrichteneingabe.
- **Nachrichtenreihenfolge:** Alle sichtbaren Nachrichten auf einer Seite; älteste oben, neueste zuunterst. Ältere Einträge bleiben durch Scrollen erreichbar.
- **Absenderanordnung:** Mieter rechts, Immobilienverwaltung und KI/System links. Textliche Absenderangaben bleiben zusätzlich sichtbar.

Diese Grundanordnung, die maskierte Kontaktanzeige und das Aktualisierungsintervall von 20 Sekunden sind vereinbart. Die Dringlichkeitsfarben folgen DRING-04; genaue Farbtöne, Schrift, Abstände und endgültige Breite bleiben Gestaltungspunkte. Die automatische Aktualisierung verändert beim Lesen älterer Einträge nicht ungefragt die Leseposition.

Neuerfassung und abgeschlossener Fall werden anhand dieser Festlegungen weiter abgestimmt. Ein stilistischer JPG-Entwurf ist noch nicht erstellt.

### 8.3 Abgeschlossener Fall – nur lesbar

```text
┌──────────────────────────────────────────────────────────┐
│ PropertyFlow                                             │
├──────────────────────────────────────────────────────────┤
│ REQ-2026-001                          [Abgeschlossen]    │
│ Heizung funktioniert nicht                               │
│ Dringlichkeit: Hoch (Orange) · Quelle: System             │
│                                                          │
│ ▸ Ursprüngliches Anliegen anzeigen                       │
│   (standardmässig eingeklappt; schreibgeschützt)         │
├──────────────────────────────────────────────────────────┤
│ Fallverlauf                                              │
│                                                          │
│   ... Nachrichten und Dringlichkeitsereignisse ...        │
│                                                          │
│ ┌───────────────────────────────────────────────┐        │
│ │ Immobilienverwaltung · 16:45                  │        │
│ │ Der Fall wurde erfolgreich abgeschlossen.     │        │
│ └───────────────────────────────────────────────┘        │
├──────────────────────────────────────────────────────────┤
│ Dieser Fall ist abgeschlossen. Es können keine           │
│ weiteren Nachrichten hinzugefügt werden.                │
│                                                          │
│ [Keine Nachrichteneingabe]  [Kein Senden-Button]          │
└──────────────────────────────────────────────────────────┘
```

**Aktion:** Es ist ausschliesslich Lesen zulässig. Zusätzliche Nachrichten müssen auch serverseitig verhindert werden.

## 9. Qualitäts- und Bedienungsregeln für diesen Screen

| Aspekt | Prüfkriterium / Nachweisvorschlag für Block 2 |
|---|---|
| **Barrierefreiheit** | Alle Eingaben haben sichtbare Labels; Fehlermeldungen sind dem Feld zugeordnet; ausklappbarer Bereich und Aktionen sind per Tastatur nutzbar; mindestens ein reproduzierbarer automatisierter A11y-Check (z. B. axe-core) plus Tastaturprüfung. |
| **Sicherheit** | KI- und Benutzernachrichten werden als Text ausgegeben. Test mit HTML-/Script-Payload bestätigt, dass keine Ausführung stattfindet; kein ungeprüftes `innerHTML`. |
| **Performance** | Definiertes Frontend-Performance-Budget und reproduzierbare Messung mit Lighthouse; Zielwert und tatsächliches Ergebnis werden nach der Implementierung dokumentiert. |
| **Asynchrone Kommunikation** | Nach Aktualisierung erscheinen nur vollständig gespeicherte und veröffentlichte Nachrichten. Die Ansicht bietet weder Chat-Stream noch KI-/Prozessabbruch; Seitenwechsel beeinflusst die Hintergrundverarbeitung nicht. |
| **Mutation / Unit-Test** | Mindestens ein KI-generierter Komponententest wird durch gezielte Mutation nachweislich rot und nach Wiederherstellung wieder grün. |

**Ergebnisse sind noch offen:** Dies ist eine Spezifikation, kein bereits ausgeführter Test- oder Messbericht. Messwerte werden erst nach der Implementierung ergänzt.

## 10. Akzeptanzkriterien

- **AK-01:** Beim Aufruf von `GET /mieter/fall` sieht der Mieter das Neuerfassungsformular mit Betreff, Beschreibung, Objekt-/Wohnungsreferenz und E-Mail-Adresse. Der Tokenlink öffnet dagegen nach gültiger Prüfung den bestehenden Fall, obwohl er keine Case-ID enthält.
- **AK-02:** Ungültige Pflichtfelder verhindern die Annahme und führen zu verständlichen, feldbezogenen Fehlermeldungen ohne Verlust der eingegebenen Werte.
- **AK-03:** Nach bestätigter Annahme ist die Case-ID sichtbar. Die fachlichen Annahme-, Wiederholungs- und Prozessstartregeln werden zentral mit [FALL-AK-01](../specifications/fallverwaltung.md#fall-ak-01), [FALL-AK-02](../specifications/fallverwaltung.md#fall-ak-02) und [FALL-AK-03](../specifications/fallverwaltung.md#fall-ak-03) geprüft.
- **AK-04:** Nach dem Erstabsenden wird auf derselben funktionalen Seite unmittelbar die aktive Fallansicht angezeigt; der persönliche Fall-Link wird per E-Mail versendet.
- **AK-05:** Beim bestehenden Fall ist «Ursprüngliches Anliegen anzeigen» zunächst eingeklappt und zeigt nach dem Aufklappen die vier ursprünglichen Angaben ausschliesslich lesbar.
- **AK-06:** Die gesamte für den Mieter bestimmte Kommunikation erscheint chronologisch, mit erkennbarer Absenderrolle und Zeitstempel.
- **AK-07:** Bei aktivem Fall kann der Mieter jederzeit zusätzliche Nachrichten an denselben Fall senden; Ursprungstext und Case-ID ändern sich dabei nicht.
- **AK-08:** Externe, veröffentlichte KI-/Systemantworten und Backoffice-Antworten erscheinen im selben Verlauf. Interne Nachrichten und Memos sind ausschliesslich für berechtigte interne Mitarbeitende zugänglich.
- **AK-09:** Screen 01 bietet keine direkte Chatfunktion, keinen KI-Stream und keine Abbruchaktion für KI oder Camunda. Er zeigt vollständig gespeicherte veröffentlichte Nachrichten und freigegebene Dringlichkeitsereignisse; Neuladen oder Schliessen der Seite stoppt keine Hintergrundverarbeitung. Die Mieterberechtigung vermittelt auch über direkte Backend-Aufrufe kein Abbruchrecht.
- **AK-10:** HTML-/Script-Payloads in Mieter- und KI-Ausgaben werden ohne Skriptausführung als Text dargestellt.
- **AK-11:** Nach fachlichem Abschluss wird der gesamte Fall nur lesbar angezeigt; eine Nachricht kann weder im Browser noch über einen direkten Backend-Aufruf durch irgendeine Rolle gespeichert werden.
- **AK-12:** Die Case-ID bleibt sichtbar und kopierbar. Der persönliche E-Mail-Link enthält nur den geheimen Token; die Case-ID allein berechtigt zu keinem Zugriff. Die zentrale Prüfung erfolgt nach [ZUG-AK-01](../specifications/fallzugriff-und-sicherheit.md#zug-ak-01) und [ZUG-AK-05](../specifications/fallzugriff-und-sicherheit.md#zug-ak-05).
- **AK-13:** Im Frontend ist die Camunda-Steuerung nur über PropertyFlow-Backend-Verträge erreichbar; es gibt keine direkten Camunda-Aufrufe aus dem Browser.

- **AK-14:** `GET /mieter/fall` rendert ausschliesslich die Neuanlage und erzeugt keinen Fall; GET-Aufrufe lösen keine fachlichen Schreibaktionen aus.
- **AK-15:** Erfolgreiche POSTs verwenden HTTP 303; Reload und Wiederholung mit derselben Absende-ID erzeugen keine Duplikate.
- **AK-16:** Bei einer nachgelagerten Störung bleibt die bestätigte Annahme sichtbar; ein Mail- oder Verarbeitungshinweis wird getrennt dargestellt. Die zugrunde liegende Fallstabilität wird nach [FALL-AK-03](../specifications/fallverwaltung.md#fall-ak-03) und [FALL-AK-06](../specifications/fallverwaltung.md#fall-ak-06) geprüft.
- **AK-17:** Ein gültiger Link zeigt den Fall direkt unter derselben Token-URL ohne GET-Weiterleitung oder erforderliche Fallsitzung. Bei abgelaufenem oder widerrufenem Token erscheint die neutrale Rückmeldung. Wiederverwendbarkeit, Prüfung bei jedem Aufruf und Fallbindung werden zentral nach [ZUG-AK-04](../specifications/fallzugriff-und-sicherheit.md#zug-ak-04), [ZUG-AK-05](../specifications/fallzugriff-und-sicherheit.md#zug-ak-05) und [ZUG-AK-06](../specifications/fallzugriff-und-sicherheit.md#zug-ak-06) geprüft.
- **AK-18:** Interne Inhalte fehlen im ausgelieferten HTML und ergänzenden Antworten gemäss KOM-01. Die technischen Schutz- und Fehlerprüfungen sind zentral als [ZUG-AK-08](../specifications/fallzugriff-und-sicherheit.md#zug-ak-08) und [ZUG-AK-09](../specifications/fallzugriff-und-sicherheit.md#zug-ak-09) geführt.
- **AK-19:** Die Kernabläufe bleiben ohne JavaScript nutzbar; Feldfehler und Statusmeldungen sind zugänglich.

- **AK-20:** Zentral geführt als [KOM-AK-01](../specifications/fallkommunikation.md#kom-ak-01).
- **AK-21:** Zentral geführt als [KOM-AK-02](../specifications/fallkommunikation.md#kom-ak-02).
- **AK-22:** Zentral geführt als [KOM-AK-03](../specifications/fallkommunikation.md#kom-ak-03).

- **AK-23:** Zentral geführt als [KOM-AK-04](../specifications/fallkommunikation.md#kom-ak-04).
- **AK-24:** Zentral geführt als [KOM-AK-05](../specifications/fallkommunikation.md#kom-ak-05).

- **AK-25:** Periodische Abrufe aktualisieren gespeicherte veröffentlichte Nachrichten, fachliche Dringlichkeitsereignisse, aktuelle Dringlichkeit und Fallstatus ohne Seitenneuladen, Duplikate, Eingabeverlust oder unerwartete Fokus-/Scrolländerungen. Ein gezielter Test umfasst neue Einträge beim Lesen älterer Einträge auf derselben Seite.
- **AK-26:** Ausgeblendeter Tab, Rückkehr, langsame Anfrage, Netzwerkfehler und Rate Limit prüfen Pausieren, einzelne laufende Anfrage und verzögerte Wiederholung. Abschluss oder ungültiger Zugang beendet Polling; keine dieser Aktionen bricht KI oder Camunda ab. Jeder Abruf wahrt Fallrechte und Inhaltsgrenzen.
- **AK-27:** Erstbewertung und jede gespeicherte wirksame Stufenänderung erscheinen chronologisch mit altem/neuem Wert, Zeitpunkt und «System» oder «Immobilienverwaltung». Der Mieter kann keine Stufe ändern. Manuelle Einstufungen bleiben auch bei späteren Systemergebnissen wirksam. Interne Auditdaten bleiben verborgen; unveränderte Neubewertungen und getrennte Systembewertungen erzeugen keine fingierten Änderungen. Zentrale Prüfkriterien: DRING-AK-05 bis DRING-AK-09.

## 11. Umsetzung im FFHS-Block 2 / spätere Blöcke

**Block 2 – mit statischen Testdaten:**

- Thymeleaf-Vorlage für die drei Zustände derselben funktionalen Mieter-Fallansicht.
- Mock-Falldaten und Mock-Nachrichten, inklusive abgeschlossenem Fall und einem aktiven Fall mit internen Memos sowie externen Nachrichten zur Prüfung der Sichtbarkeitsfilterung.
- Ein- und Ausklappen der ursprünglichen Angaben mit nativem HTML.
- Die [Mitarbeiter-Fallübersicht (Screen 02)](ansicht-02-mitarbeiter-falluebersicht.md) und die später zu spezifizierende Mitarbeiter-Falldetailansicht (Screen 03) sind eigenständige Backoffice-Seiten. Der Falllink wechselt von der Liste ins Detail; ein gleichzeitiges Detailpanel ist nicht vorgesehen.
- Asynchrone Fallkommunikation mit vollständig gespeicherten Mock-Nachrichten, fachlichen Status- und Fehlerfällen sowie sicherer Textausgabe; automatische Aktualisierung alle 20 Sekunden gemäss Abschnitt 4.5.1 und manueller Seitenaufruf als Fallback.
- Generierte und überprüfte Tests sowie A11y-, Sicherheits- und Performance-Nachweise.

**Block 3 – Services:** API-Verträge, tatsächliche Camunda-8-Integration, Prozessstart, Nachrichtenkorrelation, KI-/Worker-Integration, Autorisierungs- und Idempotenzmechanismen sowie überprüfbarer Ausschluss interner Memos und ihrer Ableitungen aus dem Kontext der Kommunikationsgenerierung.

**Block 4 – Persistenz:** Fachliches Datenmodell für Anliegen, Nachrichten, Zugriffsnachweise und Auditdaten; dauerhafte Historie und zuverlässige Koordination von Prozessstart sowie Abschluss-/Nachrichtenschreiboperationen.

## 12. Referenzen und noch zu konkretisierende Punkte

**Bestehende Projektdokumente im Repository:**

- [Zentrale Spezifikation für Fallzugriff und Sicherheit](../specifications/fallzugriff-und-sicherheit.md) – direkter persönlicher Tokenzugriff, Berechtigungen und Linkersatz.

- [Zentrale Spezifikation der Fallverwaltung](../specifications/fallverwaltung.md) – Identität, Annahme, Zuordnung und Lebenszyklus.

- [Zentrale Spezifikation der Fallkommunikation](../specifications/fallkommunikation.md) – Sichtbarkeit, LLM-Datengrundlage, asynchrone Verarbeitung und Nachrichtensperre.

- `../project-context.md` – verbindlicher KI-/Projektkontext.
- `docs/architecture/adr/ADR-001-grundarchitektur.md` – modularer Monolith.
- `docs/architecture/adr/ADR-002-camunda8-workflow-orchestrierung.md` – Camunda 8 und Zuständigkeiten.
- `docs/architecture/adr/ADR-003-praesentationsschicht.md` – SSR mit Thymeleaf und gezieltem JavaScript.
- `docs/use_cases/UC-001-mieteranliegen-einreichen.md` – ursprüngliche Anliegen-Erfassung.
- `docs/use_cases/UC-002-mieteranliegen-anzeigen.md` – Backoffice-Übersicht.
- `docs/use_cases/UC-003-mieteranliegen-details-anzeigen.md` – Backoffice-Detail.
- `docs/use_cases/UC-004-ki-analyse-durchfuehren.md` – Analyse-Use-Case der Mitarbeiteransicht; dessen Streaming-/Abbruchfunktion gehört nicht zu Screen 01.

**Durch diese Screen-Spezifikation präzisiert beziehungsweise erweitert:** durchgängige Mieter-Kommunikationsansicht, asynchrone Veröffentlichung von Ergebnissen der Camunda-gesteuerten KI-Verarbeitung, persönlicher Fall-Link, gesperrte Ursprungsdaten, vollständige Mieter-sichtbare Historie, Nachrichtensperre nach Camunda-Abschluss. Bestehende Use-Cases und ADRs müssen bei der Implementierung **konsistent** ergänzt werden, statt konkurrierende Regeln einzuführen.

**Vereinbartes Zugangsmodell:** Der persönliche E-Mail-Link enthält nur den geheimen Token und zeigt den Fall direkt unter derselben URL; die Case-ID bleibt im Inhalt die lesbare Fallreferenz. Tokenformat, Laufzeit, weitere Backend-Verträge und Umsetzung des Linkersatzes werden zentral unter [Fallzugriff – Review-Vorschläge](../specifications/fallzugriff-und-sicherheit.md#review-vorschläge-und-offene-entscheidungen) geführt. Weitere offene Punkte sind exakte Feldlängen, technische Konkretisierung der [E-Mail-Zustellung](../specifications/benachrichtigungen-und-zustellung.md#offene-umsetzungsentscheidungen-und-abschluss-des-spezifikationsstands) und des Prozessstarts, konkretes BPMN-Nachrichtenmodell, numerisches Performance-Budget sowie die in der [Fallverwaltung](../specifications/fallverwaltung.md#offene-entscheidungen-und-weiterentwicklung) zentral geführten Lebenszyklusentscheidungen. Review-Werte und Umsetzungsdetails sind **keine bereits beschlossenen Projektentscheidungen**.

### 12.1 Bewahrte Vorschläge und offene Entscheidungen aus dem Chat

Die folgenden Vorschläge bleiben dokumentiert, erweitern aber nicht automatisch den aktuellen Umfang mit vier Erfassungsfeldern:

| Vorschlag | Ursprünglich vorgeschlagene Validierung | Einordnung |
|---|---|---|
| Name | 1–120 Zeichen nach Trim; Unicode und übliche Namenszeichen zulassen | Zusätzlicher Kontaktwert, noch zu entscheiden |
| Telefonnummer | Optional, maximal 40 Zeichen; internationale Schreibweisen zulassen | Optionaler Rückrufkontakt |
| Separate Strasse/Hausnummer | 1–200 Zeichen | Alternative zur gemeinsamen Objekt-/Wohnungsreferenz |
| Separate Postleitzahl | 1–20 Zeichen; Schweizer Prüfung nur bei festgelegtem Einsatzgebiet | Teil einer alternativen strukturierten Adresse |
| Separater Ort | 1–120 Zeichen | Teil einer alternativen strukturierten Adresse |
| Wohnung/Lage im Gebäude | Optional, maximal 120 Zeichen | Alternative separate Objektangabe |

**KI-Rückfragen:** Die bestehende Vision führt KI-generierte Rückfragen als optionale Erweiterung. Bei ihrer Aufnahme werden sie innerhalb des Camunda-8-Prozesses erzeugt, geprüft und als vollständige Nachrichten veröffentlicht. Der Mieter antwortet über das Nachrichtenformular. Die verbindliche Abgrenzung aus Abschnitt 4.5 gilt auch für den Block-2-Mock; eine direkte Chat- oder Streaming-Funktion ist nicht vorgesehen.

**Weitere optionale Erweiterungen:** Anhänge, automatischer Linkersatz und sichere E-Mail-Antwortzuordnung werden erst nach ausdrücklicher Aufnahme umgesetzt. Für Anhänge sind Dateitypen, Grössen, Schadsoftwareprüfung, Speicherung und fallgebundener Download festzulegen.

**Vor produktivem Betrieb zu konkretisieren:** Die zentralen offenen Punkte zum [Fallzugriff](../specifications/fallzugriff-und-sicherheit.md#review-vorschläge-und-offene-entscheidungen), Aufbewahrung von Fällen und Absende-IDs, Notfallkontakte und Datenschutzerklärung. Tokenablauf und Datenaufbewahrung sind unterschiedliche Regeln.

**Modulverträge:** Als Zuordnungsvorschlag verantwortet `intake` die Annahme und initiale Speicherung und `caseprocessing` die fallbezogene Kommunikation und Anzeige. Gemeinsame Leseverträge werden im Service-Design festgelegt. Controller verwenden Anwendungsdienste; modulübergreifender Austausch erfolgt über öffentliche Schnittstellen ohne direkten Zugriff auf fremde Persistenz.

**Gestaltung:** Die vorhandenen ASCII-Wireframes bleiben technische Entwürfe. Endgültige Anordnung und stilistische JPG-Darstellung werden anschliessend gemeinsam entwickelt. Die Ergänzungen dieses Reviews sind noch nicht vollständig in den ASCII-Entwürfen dargestellt.

**Zusätzliche Grundlagen:**

- `docs/vision.md` – fachlicher Scope, insbesondere optionale KI-Rückfragen.
- `../architecture/module-structure.md` – Modulgrenzen und Integrationsverträge.
- `docs/evaluation/evaluationsgrundlage.md` – Vertrauensgrenzen, Guardrails und menschliche Prüfung.

### 12.2 Stand und Verbindlichkeit

Abgleich mit diesem Chat: 09.10.2026. Bestehende Projektentscheidungen, vorgeschlagene Konkretisierungen und optionale Erweiterungen werden getrennt gekennzeichnet. Die sieben Statusnamen und die Dringlichkeitsregeln sind fachlich vereinbart; dafür ist keine ADR-Änderung erforderlich. Laufzeiten, Feldalternativen und gekennzeichnete technische Details bleiben offen. Diese Datei beschreibt Anforderungen und Entwürfe, keine nachgewiesene Implementierung oder ausgeführte Sicherheitsprüfung.

### 12.3 Abgleich mit übergreifenden KI-/Frontend-Vorgaben

Der Geltungsbereich ist geklärt: Das Mitarbeiterdetail verwendet Streaming und Abbruch gemäss [ADR-003](../architecture/adr/ADR-003-praesentationsschicht.md) und UC-004. Die Mitarbeiterliste aktualisiert gespeicherte Listenwerte. Die Mieteransicht folgt KOM-03 und zeigt gespeicherte Nachrichten und freigegebene Dringlichkeitsereignisse mit periodischer Aktualisierung, ohne direkten Chat-Stream oder KI-/Prozessabbruch. Der [Abgleich](../specifications/fallkommunikation.md#abgleich-mit-bestehenden-dokumenten) ist damit fachlich abgeschlossen; die SSR-Entscheidung bleibt gültig.
