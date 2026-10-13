# Screen 03 – Mitarbeiter-Falldetailansicht

**Projekt:** PropertyFlow – FFHS CAS AISE\
**Status:** Fachliche Bedienung und Desktop-Gestaltung bestätigt; responsive Darstellung bestätigt; Prozess- und technische Verträge separat\
**Stand:** 10.10.2026\
**Zielgruppe:** Mitarbeitende mit einheitlichen Adminrechten\
**Darstellung:** Eigene SSR-Seite mit Thymeleaf und gezieltem Vanilla JavaScript

## 1. Zweck und Verbindlichkeit

Die Ansicht beantwortet: **Was ist in diesem Fall passiert, welche Prüfung ist erforderlich und was kann ich als Mitarbeiter als Nächstes tun?** Sie wird über die Case-ID aus [UI2 – Mitarbeiter-Fallübersicht](ansicht-02-mitarbeiter-falluebersicht.md) oder über eine berechtigte direkte Referenz geöffnet. Ein Aufruf legt keinen Fall an und löst keine Analyse oder Freigabe aus.

**Bestätigt am 09.10.2026:** Oben stehen Falltitel, Bearbeitungsstatus und Dringlichkeit. Darunter steht **der Verlauf links; Falldaten und Aktionen stehen rechts**. Die Ansicht ist die dritte eigenständige Seite neben [UI1 – Mieter-Fallansicht](ansicht-01-mieter-fallansicht.md) und UI2.

Dieses Dokument fasst die am 09./10.10.2026 bestätigten Entscheidungen zusammen. Fachliche Regeln werden in den zentralen Quellen gepflegt; UI3 beschreibt ihre Bedienung. Die verbleibenden Prozess- und Technikfragen sind in Abschnitt 8 abgegrenzt. Die Spezifikation ist kein Nachweis implementierter Funktionen oder ausgeführter Anwendungstests.

| Quelle | Verantwortung |
|---|---|
| [Fallverwaltung](../specifications/fallverwaltung.md) | Identität, Ursprungsdaten, Status, Abschluss und menschliche Entscheidungsgrenzen, insbesondere FALL-04 bis FALL-11 |
| [Fallkommunikation](../specifications/fallkommunikation.md) | Interne Memos, externe Veröffentlichung, erlaubte LLM-Kommunikationsgrundlage und Nachrichtensperre |
| [Dringlichkeitsbewertung](../specifications/dringlichkeitsbewertung.md) | Stufen und Farben, geschützte manuelle Einstufung, Bewertung und Historie |
| [Fallzugriff und Sicherheit](../specifications/fallzugriff-und-sicherheit.md) | Mitarbeiterbereich ohne Anmeldung und einheitliche Adminrechte; ZUG-04 |
| [Benachrichtigungen und Zustellung](../specifications/benachrichtigungen-und-zustellung.md) | Mailauslöser und Trennung von Veröffentlichung und Zustellung |
| [UC-003](../use_cases/UC-003-mieteranliegen-details-anzeigen.md), [UC-004](../use_cases/UC-004-ki-analyse-durchfuehren.md) | Detailanzeige und KI-Formulierung einer externen Nachricht |
| [ADR-003](../architecture/adr/ADR-003-praesentationsschicht.md) | SSR, gezieltes JavaScript sowie Streaming und Abbruch der KI-Nachrichtenformulierung |

## 2. Bestätigte Grundanordnung

| Bereich | Inhalt / Einordnung |
|---|---|
| **Kopf über beide Spalten** | Links Case-ID und ursprünglicher Betreff als Falltitel; rechts daneben letzter bestätigter Status, wirksame Dringlichkeit mit Farbe, Text und Quelle sowie Wiedervorlage als Datum oder «Keine Wiedervorlage». Keine zusätzliche Metadatenzeile unter dem Titel. |
| **Links: Verlauf** | Separat scrollbar; Fallkommunikation und nachvollziehbare Änderungen, externe Nachrichten, interne Memos und fachliche Ereignisse klar unterscheidbar |
| **Rechts: Falldaten und Aktionen** | Bleibt beim Scrollen des linken Verlaufs fix stehen; Ursprungsdaten, Zuordnung, relevante Zeitpunkte, Prüfbedarf und zulässige Mitarbeiteraktionen |
| **Automatische Verarbeitung** | Analyse und Dringlichkeitsbewertung laufen im Camunda-Prozess. Systemnachrichten erscheinen rechtsbündig innerhalb des linken Verlaufsbereichs; die wirksame Dringlichkeit steht im Kopf und ist im geöffneten Änderungsformular vorausgewählt. Menschliche Abklärungen werden über Memos dokumentiert. |
| **KI-Formulierungshilfe** | Erzeugt aus einer kurzen externen Mitarbeitereingabe einen Nachrichtenvorschlag mit Streaming und Abbruch. Button neben der gemeinsamen Eingabe zusammen mit «Intern»; Stream darunter, bewusste Übernahme nach vollständiger Ausgabe. |

**Bestätigt am 10.10.2026 – Scrollverhalten auf breiten Bildschirmen:** Nur der Verlauf in der linken Hälfte ist separat scrollbar. «Nachricht verfassen» mit Eingabe, Aktionen und KI-Vorschlag steht als eigener fester Bereich darunter ausserhalb dieses Scrollbereichs. Das Scrollen des Verlaufs verschiebt weder diesen Eingabebereich noch die rechte Hälfte mit Falldaten und Aktionen oder den gemeinsamen Kopf. Die Arbeitsfläche nutzt die verfügbare Bildschirmhöhe bis auf einen schmalen unteren Aussenabstand; zusätzliche Höhe kommt insbesondere dem Verlauf zugute. Linker Eingabebereich und rechter Arbeitsbereich reichen bis zur gemeinsamen unteren Kante.

**Ebenfalls bestätigt:** Wenn der rechte Inhalt die verfügbare Bildschirmhöhe überschreitet, ist die rechte Hälfte ebenfalls unabhängig scrollbar. Scrollen in einer Hälfte verschiebt die andere Hälfte und den gemeinsamen Kopf nicht. Auf schmalen Bildschirmen gilt die bestätigte Reihenfolge Kopf → Falldaten und Aktionen → Verlauf mit Nachrichteneingabe; die Seite wird als Ganzes gescrollt.

Die hier beschriebenen Bedienelemente und die nachfolgenden Gestaltungsentscheidungen sind bestätigt. Schriftwahl, Schriftgrössen, Abstände und Farbcodes sind im Abschnitt «Bestätigte Schriftgrössen, Abstände und Farben» festgelegt. Weitere komponentenspezifische Masse und Kontrastnachweise werden bei der Umsetzung konkretisiert. Auf schmalen Bildschirmen gilt Kopf → Falldaten und Aktionen → Verlauf mit Nachrichteneingabe; die Seite wird als Ganzes gescrollt.

**Bestätigt am 10.10.2026 – Vergrössern der Nachrichtentextbox:** Auf breiten Bildschirmen lässt sich die Textbox durch Ziehen nach unten ausschliesslich in der Höhe vergrössern; seitliches Vergrössern ist ausgeschlossen. Der unten verankerte Eingabebereich wächst dabei nach oben und verkleinert den Verlauf darüber. Der Senden-Button bleibt sichtbar. Der Verlauf behält mindestens 200 CSS-Pixel Höhe. Sobald diese Grenze erreicht ist, lässt sich die Textbox nicht weiter vergrössern; längerer Text scrollt innerhalb der Textbox. Beim Verkleinern erhält der Verlauf den Platz zurück. Nachrichten und ungesendeter Text bleiben erhalten. Kopf und rechte Spalte bleiben unverändert. Aktionen und der sichtbare KI-Vorschlag werden bei der verfügbaren Höhe mitberücksichtigt.

Unter 1200 px wächst die Textbox automatisch mit dem Inhalt von mindestens 96 px bis maximal 240 px; danach scrollt ihr Inhalt. Es gilt gemeinsames Seitenscrollen gemäss Abschnitt «Bestätigte responsive Darstellung»; die Desktop-Mindesthöhe des Verlaufs gilt dort nicht.

### 2.1 Gemeinsame Gestaltung und Änderungskarten

**Konsolidierter Gestaltungsstand am 10.10.2026:** Helle Oberfläche mit weissen Karten, dunkler gut lesbarer Schrift, dezenten Rahmen, abgerundeten Ecken und Blau als gemeinsame Aktionsfarbe, passend zu den JPGs (bestätigt am 10.10.2026). Diese gemeinsame Gestaltungsbasis ist in UI1, UI2 und UI3 bestätigt; ihre jeweiligen Abläufe bleiben massgeblich. Dringlichkeitsfarben folgen DRING-04: Kritisch rot, Hoch orange, Normal gelb, Niedrig grün, noch nicht bewertet blau. Status und Prüfbedarf sind davon visuell unterscheidbar. Eine zusätzliche Farblegende «Gestaltungsbasis» gehört nicht zur Anwendungsoberfläche.

Geschlossene Karten für Status, Dringlichkeit und Wiedervorlage zeigen nur ihre Bezeichnung und die Änderungsaktion. Sie wiederholen die aktuellen Werte aus dem Kopf nicht. Beim Aufklappen ist der gespeicherte Status bzw. die gespeicherte Dringlichkeit vorausgewählt; bei Wiedervorlage ist ein vorhandener Termin in der Eingabe erkennbar. Auswahländerungen bleiben bis zum erfolgreichen Speichern ungespeichert; der Kopf zeigt weiterhin den bestätigten Stand. «Abbrechen» schliesst die Bearbeitung ohne Übernahme. Die optionale interne Begründung folgt Abschnitt 3. Die Karte «Abschluss» zeigt den Button ohne zusätzlichen Hinweis «Abschluss erfordert Bestätigung»; die Bestätigung beim Auslösen bleibt bestehen.

Das [bestätigte JPG der Mitarbeiter-Falldetailansicht](design/ansicht-03-mitarbeiter-falldetail.jpg) illustriert die Desktop-Gestaltung. Verbindliche Entscheidungen stehen in dieser Screen-Spezifikation und den referenzierten Fachspezifikationen; das JPG ist keine eigenständige Anforderungsquelle. Die festgelegten Gestaltungswerte gelten für die Umsetzung; weitere komponentenspezifische Masse werden dort konkretisiert.

### Bestätigte responsive Darstellung

**Bestätigt am 10.10.2026:** Massgeblich ist die verfügbare Browserbreite in CSS-Pixeln.

| Browserbreite | Darstellung |
|---|---|
| Ab 1200 px | Desktop gemäss JPGs |
| 768–1199 px | Kompakte Ansicht mit umgebrochenem Kopf |
| Unter 768 px | Mobile Ansicht mit einer Spalte |

Unter 1200 px scrollt die ganze Seite; Kopf und Eingabebereiche sind nicht feststehend. Die Desktop-Regel mit mindestens 200 px Verlauf gilt dort nicht. Es gibt kein horizontales Scrollen; lange Wörter und Referenzen dürfen umbrechen. Bedienelemente bleiben mindestens 44 px hoch. Unter 768 px gelten 16 px Seitenrand, 24 px Seitentitel und weiterhin 16 px Nachrichtentext. Die Bildschirmtastatur darf keine Aktionen dauerhaft verdecken; diese bleiben durch Seitenscrollen erreichbar.

Unter 1200 px wechseln die zwei Spalten zu dieser Reihenfolge: Fallkopf → Falldaten und Aktionen → Verlauf → Nachricht verfassen → KI-Vorschlag. Status, Dringlichkeit und Wiedervorlage stehen unter dem Titel und dürfen umbrechen. Änderungskarten sind beim Öffnen der Ansicht zunächst geschlossen; beim Aufklappen erscheint der gespeicherte Wert vorausgewählt.

Auf Mobil erhalten Nachrichtenblasen beider Seiten dieselbe Breite von maximal 92 % der verfügbaren Verlaufsbreite: Mieter links, Verwaltung/System rechts. Buttons und Toggles umbrechen in mehrere Zeilen, statt kleiner zu werden. Rollen und Zeitangaben bleiben innerhalb der Blasen.

Nachrichtentextboxen wachsen unter 1200 px automatisch mit dem Inhalt von mindestens 96 px bis maximal 240 px Höhe; danach scrollt der Text innerhalb des Felds. Die manuelle Desktop-Vergrösserung gilt ab 1200 px. Die Regeln für abgeschlossene Fälle und gesperrte Eingaben bleiben bestehen.

**Prüfkriterium für die Umsetzung:** Übergänge bei 767/768 px und 1199/1200 px sowie schmale Ansichten mit langen Inhalten prüfen. Alle Angaben und Aktionen bleiben ohne horizontales Scrollen erreichbar; Reihenfolge, Umbrüche und Scrollverhalten entsprechen diesem Abschnitt. Mit geöffneter Bildschirmtastatur und langem Eingabetext die automatische Textboxhöhe von 96–240 px, internes Textscrollen und Erreichbarkeit der Aktionen prüfen.

#### JPG-Entwürfe für Mobile und Tablet

**Erstellt am 10.10.2026 zur visuellen Abstimmung:** Die folgenden Entwürfe ergänzen die bestätigten Desktop-JPGs. Sie zeigen durchgehende Seitenansichten einschliesslich der durch Seitenscrollen erreichbaren Bereiche. Mobile orientiert sich an einer Browserbreite von 390 CSS-Pixeln, Tablet an 834 CSS-Pixeln; die Bildauflösung ist keine CSS-Massvorgabe. Die bestätigten Responsive-Regeln und Gestaltungswerte bleiben verbindlich. Die Bilder verwenden fiktive Beispieldaten und ausgewählte Verlaufsstände.

| Zustand | Mobile | Tablet |
|---|---|---|
| Falldetail | [JPG](design/ansicht-03-mitarbeiter-falldetail-mobile.jpg) | [JPG](design/ansicht-03-mitarbeiter-falldetail-tablet.jpg) |

### Bestätigte Schriftgrössen, Abstände und Farben

**Bestätigt am 10.10.2026:** Die folgenden Werte gelten gemeinsam für UI1, UI2 und UI3. Sie konkretisieren die JPG-Gestaltung für die Umsetzung; die JPGs sind keine pixelgenauen Farbreferenzen. Grössen sind in CSS-Pixeln angegeben. Die Schriftfamilie ist Inter mit der Systemschrift des Geräts als Fallback; halbfett entspricht Schriftgewicht 600, normal 400.

| Textelement | Grösse / Gewicht |
|---|---|
| Seiten- und Falltitel | 30 px / 600; auf kleinen Bildschirmen 24 px |
| Kartenüberschrift | 20 px / 600 |
| Nachrichtentext, Formulare und Tabellen | 16 px / 400 |
| Buttons und Feldbeschriftungen | 14 px / 600 |
| Zeitangaben und ergänzende Informationen | 14 px / 400 |

Nachrichtentexte erhalten einen Zeilenabstand von 1,5.

| Abstand / Form | Wert |
|---|---|
| Seitenrand Desktop / Mobil | 24 px / 16 px |
| Abstand zwischen Karten | 16 px |
| Innenabstand Karten | 20 px |
| Innenabstand Nachrichtenblasen | 16 px |
| Abstand zwischen Nachrichten | 12 px |
| Abstand Feldlabel zum Feld | 8 px |
| Mindesthöhe Buttons und Eingabefelder | 44 px |
| Eckenradius Karten | 10 px |
| Eckenradius Felder und Buttons | 6 px |
| Rahmenstärke | 1 px |

| Grundfarbe | Farbcode |
|---|---|
| Seitenhintergrund | `#F2F8FC` |
| Karten und Eingabefelder | `#FFFFFF` |
| Haupttext | `#172033` |
| Ergänzende Texte und Zeitangaben | `#52627A` |
| Kartenrahmen | `#DCE5EF` |
| Eingabefeldrahmen | `#8292A8` |
| Aktionen und Links | `#006FBB` |
| Button bei Hover | `#005A99` |
| Text auf primären Buttons | `#FFFFFF` |
| Neutrale Statusanzeige | Hintergrund `#E9EEF5`, Text `#42536B` |

| Dringlichkeit | Hintergrund | Text |
|---|---|---|
| Kritisch | `#FEE2E2` | `#B91C1C` |
| Hoch | `#FFEDD5` | `#9A3412` |
| Normal | `#FEF3C7` | `#854D0E` |
| Niedrig | `#DCFCE7` | `#166534` |
| Noch nicht bewertet | `#DBEAFE` | `#1D4ED8` |

Die Farbfamilien folgen DRING-04; «Noch nicht bewertet» bleibt ein Bewertungszustand. Die responsive Darstellung ist im entsprechenden Abschnitt bestätigt. Die Kontrastprüfung an den tatsächlich umgesetzten Komponenten steht noch aus.

Nachrichtenblasen: Mieter und Immobilienverwaltung `#EEF2F7`, Systemnachrichten `#E5F5F3`. Rollen beziehungsweise die vereinbarten Icons und die interne Kennzeichnung bleiben zusätzlich sichtbar.

## 3. Fachlich verbindliche Inhalte

### Fall und Zugriffsrechte

Alle Mitarbeitenden besitzen dieselben Adminrechte und können alle Fälle sowie deren interne Memos einsehen. **Korrigiert am 10.10.2026:** Es gibt keine Mitarbeiteranmeldung, keine Mitarbeiterkonten und keine Abmeldeaktion. UI3 führt keinen Ablauf für eine abgelaufene Anmeldung oder erneute Anmeldung ein. Jede Lese- und Schreibaktion wahrt die serverseitige Trennung zum Mieterzugang nach ZUG-04. Deren technische Absicherung sowie die Quelle einer individuellen Mitarbeiteridentität für die verlangten Änderungsnachweise bleiben dort ausdrücklich offen. Der geheime Mieterzugangstoken wird nicht als Falldatum angezeigt.

**Bestätigt am 10.10.2026:** Der persönliche Fall-Link des Mieters hat keine zeitliche Ablauffrist. UI3 enthält keine Aktion zur Verlängerung oder zum Ersetzen dieses Links. Die Zugangsregeln werden zentral in ZUG-05 geführt.

Ursprünglicher Betreff, Beschreibung, Objekt-/Wohnungsreferenz und Kontaktadresse bleiben als ursprüngliche Angaben erkennbar. Eine bestätigte Objektzuordnung ersetzt die ursprüngliche Angabe nicht unbemerkt. Die Detailseite zeigt den zuletzt gespeicherten fachlichen Stand aus PropertyFlow, nicht einen direkt aus Camunda gelesenen Browserzustand.

«Letzte Aktion des Mieters» folgt FALL-08: letzte gespeicherte Mieter-Nachricht, andernfalls ursprünglicher Eingang. Interne Memos, Verwaltungsnachrichten, Lesezugriffe und Systemschritte verändern diesen Wert nicht.

### Objekt und Wohnung zuordnen

**Bestätigt am 10.10.2026:** Auf Höhe von «Bestätigte Zuordnung» steht für aktive Fälle rechts «Ändern» mit Aufklapppfeil. Darunter öffnet sich eine abgegrenzte Bearbeitungsbox über die gesamte innere Breite der Falldaten-Karte, über Beschriftungs- und Wertespalte hinweg. Das Textfeld bietet vorhandene Zuordnungen als Vorschläge und erlaubt eine neue Zuordnung als Freitext (1–200 Zeichen nach Entfernen äusserer Leerzeichen). Solange keine bestätigte Zuordnung vorliegt, ist das Feld mit der ursprünglichen Mieterangabe vorbelegt; bei späteren Änderungen mit der aktuellen bestätigten Zuordnung. Die Vorbelegung allein bestätigt nichts. Erst «Zuordnung bestätigen» speichert und bestätigt die Eingabe in einem Schritt; der Button ersetzt «Speichern», ein zusätzlicher Button entfällt; «Abbrechen» verwirft sie und schliesst die Box. Die Aktion dient sowohl der ersten bestätigten Zuordnung als auch einer Korrektur. Die ursprüngliche Mieterangabe bleibt als solche lesbar; die gespeicherte Zuordnung wird separat dargestellt. Auswahl allein speichert nichts.

Die Aktion folgt [FALL-04](../specifications/fallverwaltung.md#fall-04-ursprungsdaten-und-zuordnung). Der Server prüft Berechtigung, die Länge von 1–200 Zeichen nach Entfernen äusserer Leerzeichen und zwischenzeitliche Änderungen. Nur bei strukturierten Stammdatenreferenzen wird zusätzlich deren Gültigkeit geprüft; Freitext ist ohne solche Referenz zulässig. Bei einem Konflikt wird der aktuelle Stand neu geladen und «konnte nicht erfolgreich gespeichert werden» angezeigt. Mitarbeitereingabe, KI-Ausgabe und laufender Stream bleiben erhalten. Nach bestätigtem Speichern zeigen UI3 und die bestehende UI2-Spalte «Objekt/Wohnung» dieselbe Zuordnung. Ein gewünschter manueller Statuswechsel bleibt eine separate Aktion. Kontaktadresse und persönlicher Fall-Link werden nicht geändert.

### Kommunikation und Verlauf

Veröffentlichte externe Nachrichten und gespeicherte interne Memos sind für Mitarbeitende lesbar.

**Bestätigt am 10.10.2026:** Auch das System darf interne Memos speichern; Absender, Zeitpunkt und interne Sichtbarkeit bleiben nachvollziehbar. Während des abgeschlossenen Zustands gilt die Nachrichtensperre auch für Systemmemos. Absender, Zeitpunkt und Sichtbarkeit müssen eindeutig unterscheidbar sein. Interne Memos bleiben ausschliesslich intern; ihre Speicherung veröffentlicht keine Nachricht an den Mieter.

**Bestätigt am 10.10.2026:** Es gibt keine Aktion «Entwurf speichern» und keine automatische Entwurfsspeicherung. Externe Nachrichten werden erst mit bewusster Veröffentlichung persistiert und dem Mieter angezeigt (FALL-01, KOM-01/KOM-03). Ungesendete externe Texte bleiben ungespeicherte Eingaben und erscheinen nicht als versendete Nachrichten. Veröffentlichungsstand und E-Mail-Zustellstand sind getrennte Angaben. Eine fehlgeschlagene Zustellung entfernt keine veröffentlichte Nachricht.

**Bestätigt am 10.10.2026 – Verlauf:** Ein gemeinsamer Verlauf links, chronologisch mit ältesten Einträgen oben und neuesten unten. Mieterblasen stehen links und enthalten linksbündigen Text. Verwaltungs- und Systemblasen sowie Status- und Dringlichkeitsereignisse stehen rechts; auch ihre Inhalte einschliesslich umgebrochener Zeilen sind rechtsbündig. Alle Sprech- und Ereignisblasen sind gleich breit, orientiert an der ersten Mieterblase des Entwurfs (etwa drei Viertel der nutzbaren Verlaufsbreite); nur ihre Höhe hängt vom Inhalt ab. Ereignisse bleiben durch ihre Gestaltung von Kommunikationsnachrichten unterscheidbar. Absender, Herkunft, Zeitpunkt und interne Kennzeichnung liegen vollständig innerhalb der jeweiligen Blase.

**Bestätigte Icon-Darstellung:** Ein Personen-Icon ersetzt das Wort «Mensch»; «Mieter» bzw. «Immobilienverwaltung» bleibt als Rolle sichtbar. Ein Zahnrad ersetzt «System» in Nachrichten- und Ereigniskopfzeilen. Ein Schloss kennzeichnet interne Nachrichten, ohne zusätzliches Wort «Intern» in der Blase. Herkunft und Sichtbarkeit bleiben getrennte Merkmale; ein internes Systemmemo trägt Zahnrad und Schloss. Hover und Tastaturfokus zeigen beim Personen-Icon den verlässlich verfügbaren Namen, andernfalls die Rolle; beim Zahnrad «System» und beim Schloss «Interne Nachricht». Die Icons erhalten zugängliche Beschriftungen. Die Ermittlung individueller Mitarbeiteridentitäten bleibt nach ZUG-04 zu konkretisieren. Eine vom Mitarbeiter geprüfte und gesendete Nachricht trägt weiterhin menschliche Herkunft, auch nach KI-Formulierung, ohne Zusatz «mit KI formuliert». Ungesendete externe Texte bleiben ausschliesslich im gemeinsamen Eingabefeld; es gibt keine gespeicherten externen Entwürfe im Verlauf.

**Bestätigt am 09.10.2026 – gemeinsame Eingabe:** Externe Antworten und interne Memos werden in einem gemeinsamen Textfeld erfasst. Ein deutlich beschrifteter Toggle «Intern» bestimmt die Sichtbarkeit: eingeschaltet = internes Memo, ausgeschaltet = externe Antwort. Die Auswahl allein speichert oder veröffentlicht keinen Inhalt. Das Speichern eines internen Memos veröffentlicht keine externe Nachricht und löst keine Mieterbenachrichtigung aus (KOM-01, BEN-01). Eine externe Antwort benötigt weiterhin die bewusste Veröffentlichung. Ein gespeichertes internes Memo bleibt intern; der Toggle erlaubt keine nachträgliche Umwandlung dieses Memos in eine externe Nachricht.

**Bestätigter Anfangszustand:** Beim Öffnen ist «Intern» ausgeschaltet; die Eingabe ist zunächst eine externe Antwort. Auch in diesem Zustand wird nichts automatisch veröffentlicht.

**Bestätigtes Umschaltverhalten:** Beim Umschalten von «Intern» bleibt der bereits eingegebene Text erhalten. Beim Einschalten von «Intern» wird «Mieterantwort erforderlich» ausgeschaltet und für interne Memos gesperrt. Beim Zurückschalten auf extern bleibt «Mieterantwort erforderlich» ausgeschaltet und kann erneut bewusst aktiviert werden. Ein sichtbarer Hinweis und der Aktionsbutton passen sich an die gewählte Sichtbarkeit an. Umschalten allein speichert oder veröffentlicht weiterhin nichts und verändert keine bereits gespeicherte Nachricht. Entwurfsspeicherung ist ausgeschlossen.

**Bestätigt am 10.10.2026 – externe Aktion:** Bei ausgeschaltetem Toggle «Intern» heisst der Aktionsbutton «Nachricht senden». Die Aktion speichert und veröffentlicht die externe Nachricht nach erfolgreicher serverseitiger Prüfung; erst nach bestätigter Speicherung erscheint sie im Verlauf und für den Mieter. Sie veranlasst die E-Mail-Benachrichtigung nach BEN-01, ohne auf deren Zustellung zu warten.

**Bestätigt am 10.10.2026 – interne Aktion:** Bei eingeschaltetem Toggle «Intern» heisst der Aktionsbutton «Memo speichern». Nach erfolgreicher serverseitiger Prüfung wird das Memo dauerhaft intern gespeichert und im Mitarbeiterverlauf angezeigt. Es erscheint nicht beim Mieter und löst keine Mieterbenachrichtigung aus. Die Nachrichtensperre gilt während des abgeschlossenen Zustands auch für diese Aktion.

Wirksame Dringlichkeitsänderungen bleiben mit altem/neuem Wert, Zeitpunkt und Quelle nachvollziehbar. Der Mieter sieht die freigegebene Historie mit «System» oder «Immobilienverwaltung» in UI1. Interne Mitarbeiteridentitäten, Prüfgründe und Freigaben werden dadurch nicht mieteröffentlich.

### Leere Eingabe

**Bestätigt am 10.10.2026:** Bei leerem oder ausschliesslich aus Leerzeichen bestehendem Text sind «Nachricht senden», «Memo speichern» und «Mit KI formulieren» deaktiviert. Die Prüfung wird auch serverseitig durchgesetzt: Eine leere Eingabe erzeugt weder eine Nachricht noch ein Memo und startet keinen KI-Formulierungsversuch. Die weiteren bestätigten Voraussetzungen für die jeweilige Aktion gelten weiterhin.

### Rückmeldung nach Senden oder Memo-Speicherung

**Bestätigt am 10.10.2026 – laufende Speicherung:** Während «Nachricht senden» beziehungsweise «Memo speichern» ausgeführt wird, sind das gemeinsame Textfeld, «Intern», «Mieterantwort erforderlich», «Mit KI formulieren», «Vorschlag übernehmen» und die Sende-/Speicheraktion deaktiviert. Der Mitarbeiter kann die Eingabe währenddessen weder bearbeiten noch durch einen KI-Vorschlag ersetzen. Die Sperre beginnt mit dem Auslösen der Speicherung und gilt bis zum Ende des Speichervorgangs. Doppelklicks und technische Wiederholungen derselben Speicheroperation dürfen auch serverseitig keinen zweiten Nachrichteneintrag und keinen zusätzlichen logischen Benachrichtigungsauftrag erzeugen. Nach einem Fehler ist ein sicherer erneuter Versuch möglich; Text und Auswahl bleiben erhalten und werden gemäss den geltenden Fall- und KI-Zuständen wieder bedienbar.

Ein bereits laufender KI-Stream wird durch die Speicherung nicht abgebrochen. Auch sein Abschluss, Abbruch oder Fehler ersetzt die Eingabe nicht und hebt die Sperre nicht vorzeitig auf. Die Bearbeitbarkeit während der KI-Formulierung nach Abschnitt 4 gilt nur ausserhalb einer laufenden Nachrichten- oder Memo-Speicherung.

**Bestätigt am 10.10.2026:** Nach bestätigtem erfolgreichem «Nachricht senden» beziehungsweise «Memo speichern» erscheint der gespeicherte Eintrag links im Verlauf mit Absender, Zeitpunkt, Herkunft und Sichtbarkeit. Erst dann wird das gemeinsame Eingabefeld geleert.

**Korrigierte Entscheidung am 10.10.2026:** Nach bestätigtem erfolgreichem Senden oder Memo-Speichern werden «Intern» und «Mieterantwort erforderlich» wieder ausgeschaltet. Zusammen mit dem geleerten Textfeld beginnt die nächste Eingabe damit als externe Nachricht ohne erforderliche Mieterantwort. Die Bedienelemente werden gemäss den geltenden Fall- und KI-Zuständen wieder verfügbar. Bei fehlgeschlagener oder nicht bestätigter Speicherung bleiben Text und gewählte Einstellungen erhalten; ein blosses Auslösen der Aktion setzt nichts zurück. Es wird weder ein Erfolg noch ein gespeicherter Verlaufseintrag vorgetäuscht. Die E-Mail-Zustellung ist keine Voraussetzung für die bestätigte Veröffentlichung einer externen Nachricht. Eine fehlgeschlagene E-Mail-Zustellung nimmt die veröffentlichte Nachricht nicht zurück.

### Mieterantwort erforderlich

**Bestätigt am 10.10.2026:** Zusätzlich zum Text legt der Mitarbeiter bei einer externen Nachricht ausdrücklich fest, ob eine Mieterantwort erforderlich ist. Die Auswahl wird erst mit «Nachricht senden» zusammen mit der veröffentlichten Nachricht gespeichert. Eine entsprechend markierte Rückfrage führt im vorgesehenen Ablauf zu «Wartet auf Mieterantwort»; vorrangiger offener Prüfbedarf nach FALL-05/FALL-10 bleibt als «Mitarbeiterprüfung erforderlich» erhalten. Eine reine Informationsnachricht setzt keinen neuen Wartestatus und hebt eine ältere offene Rückfrage nicht auf.

Der Mieter erkennt den Antwortbedarf in der Fallansicht und in der Benachrichtigungs-E-Mail. Ein internes Memo fordert keine Mieterantwort an. Die KI-Formulierungshilfe formuliert den Text, entscheidet aber weder Antwortbedarf noch Status selbst.

**Bestätigt am 10.10.2026:** Neue Mieternachrichten werden im Camunda-Prozess inhaltlich gegen die offenen Rückfragen geprüft. Jede Rückfrage wird einzeln beurteilt: eindeutig beantwortet = erledigt; unvollständig, unklar oder themenfremd beantwortet = weiterhin offen. UI3 zeigt den gespeicherten aktuellen Antwortbedarf gemäss [KOM-03](../specifications/fallkommunikation.md#inhaltliche-prüfung-offener-rückfragen). Die ursprüngliche externe Nachricht bleibt erhalten. Die Prüfung erfolgt auch während einer Wiedervorlage; der Nachrichteneingang allein erledigt keine Frage.

**Bestätigt am 10.10.2026 – weitere Bearbeitung:** Sind alle zuvor offenen Rückfragen beantwortet, erhält der weiterhin aktive Fall nach automatischer Verarbeitung «Mitarbeiterprüfung erforderlich», sofern kein zulässiger automatischer Abschluss erfolgt und keine Wiedervorlage läuft. Der Mitarbeiter entscheidet über das weitere Vorgehen. Eine laufende Wiedervorlage bleibt bestehen und kann bei neuen Erkenntnissen nach FALL-11 vorgezogen werden. Massgeblich ist der gespeicherte Folgestatus gemäss [FALL-05](../specifications/fallverwaltung.md#status-nach-beantwortung-aller-rückfragen).

**Bestätigt am 10.10.2026 – Bedienung:** Neben dem Toggle «Intern» steht der gleich gestaltete Toggle «Mieterantwort erforderlich». Beide sind beim Öffnen ausgeschaltet. Der Antwortbedarf ist nur für externe Nachrichten verfügbar; bei eingeschaltetem «Intern» kann kein Mieterantwortbedarf gesetzt oder mit dem Memo gespeichert werden. Umschalten allein speichert nichts; der Antwortbedarf wird erst mit «Nachricht senden» wirksam.

### Antwortbedarf aufheben

**Bestätigt am 10.10.2026:** Bei einer veröffentlichten externen Rückfrage mit noch offenem Antwortbedarf steht dem Mitarbeiter im Verlauf die Aktion «Antwortbedarf aufheben» zur Verfügung. Nach bestätigter Speicherung verlangt diese Nachricht keine weitere Mieterantwort; andere offene Rückfragen bleiben bestehen. Die Nachricht bleibt im Verlauf erhalten und wird dadurch nicht als beantwortet ausgegeben. Der Fall bleibt aktiv, beispielsweise solange der Techniker den Fehler noch behebt. Die Aktion schliesst den Fall nicht und führt keinen zusätzlichen Status ein.

Es gelten [KOM-03](../specifications/fallkommunikation.md#antwortbedarf-durch-mitarbeiter-aufheben) und die [Statusregel aus FALL-05](../specifications/fallverwaltung.md#status-nach-manuellem-aufheben-des-antwortbedarfs): Entfällt der letzte offene Antwortbedarf, folgt «In Bearbeitung», sofern keine laufende Wiedervorlage und kein vorrangiger aktueller Mitarbeiterprüfbedarf bestehen. Verbleiben andere offene Rückfragen, löst die Aktion allein keinen Statuswechsel aus. Bei bereits aufgehobenem Antwortbedarf oder abgeschlossenem Fall steht die Aktion nicht zur Verfügung. Ungesendeter Text, «Intern», «Mieterantwort erforderlich» und laufender KI-Stream bleiben erhalten; das Zurücksetzen nach erfolgreichem Senden oder Memo-Speichern gilt nicht für diese Aktion. Fehler dürfen keinen Erfolg vortäuschen.

### Dringlichkeit ändern

Ein Mitarbeiter darf die Dringlichkeit im Detail manuell ändern. Die vier Stufen und Farben folgen DRING-01/DRING-04. «Noch nicht bewertet» bezeichnet fehlende Bewertung und ist keine frei wählbare fünfte Stufe.

Eine manuelle Einstufung darf das System nicht überschreiben, auch nicht nach neuen Mieter-Nachrichten oder durch verspätete Analyseergebnisse. Getrennte Systembewertungen können intern nachvollziehbar bleiben; sie ersetzen die wirksame manuelle Stufe nicht. Wirksame Änderungen werden gemäss DRING-07 historisiert.

**Bestätigt am 10.10.2026 – Bedienung:** Rechts im Bereich Dringlichkeit öffnet «Dringlichkeit ändern» die Auswahl der vier Stufen «Kritisch», «Hoch», «Normal» und «Niedrig». Erst die bewusste Aktion «Speichern» übernimmt die ausgewählte Stufe nach erfolgreicher serverseitiger Prüfung als manuelle Einstufung. Die Auswahl allein verändert keinen Fallwert. Manuelle Einstufungen bleiben vor Systemüberschreibung geschützt; wirksame Änderungen werden mit Historie und Mieterbenachrichtigung gemäss DRING-07 und BEN-01 übernommen. «Noch nicht bewertet» ist nicht auswählbar.

**Bestätigt am 10.10.2026:** Eine kurze Begründung ist optional und bleibt intern; sie wird bei «Speichern» mit der manuellen Anpassung nachvollziehbar festgehalten und weder dem Mieter noch in der E-Mail angezeigt. Sie ist auch als Grundlage der KI-Kommunikationsformulierung ausgeschlossen. Weitere konkrete Rückmeldungen werden im technischen Bedienungsvertrag ausgearbeitet; das Konfliktverhalten ist in Abschnitt 6 bestätigt. Eine Funktion zum Aufheben des manuellen Vorrangs wird hier nicht eingeführt.

### Prüfbedarf und menschliche Entscheidung

Bei aktiven Fällen führen offene Objektzuordnung, fehlgeschlagene KI-Analyse und klärungsbedürftiger E-Mail-Versand nach FALL-05 zu «Mitarbeiterprüfung erforderlich». Die internen Gründe gehören in dieses Detail; UI2 zeigt sie nicht.

[FALL-10](../specifications/fallverwaltung.md#fall-10-automatisierung-und-menschliche-freigabe) verlangt zusätzlich eine menschliche Entscheidung bei:

- Kosten oder verbindlicher externer Beauftragung, beispielsweise einem Technikeraufgebot;
- sehr dringlichen oder schwerwiegenden Fällen;
- starken oder eskalierten Mieterbeschwerden.

Bis zur Entscheidung bleibt der aktive Fall in «Mitarbeiterprüfung erforderlich». Das System darf Vorschläge vorbereiten; verbindliche Entscheidungen und ein Abschluss mit noch offenem menschlichem Prüfbedarf erfordern den Mitarbeiter. Eine menschliche Entscheidung bezieht sich auf das konkret geprüfte Vorgehen; sie ist keine pauschale Erledigung anderer Prüfgründe oder Abschlussberechtigung. PropertyFlow führt keine externen Aufträge aus.

Eine starke Beschwerde ist nicht allein wegen ihres Tons «Kritisch». Dringlichkeit, Bearbeitungsstatus und Prüfgrund bleiben unterscheidbar.

**Bestätigte Abgrenzung am 10.10.2026:** UI3 dient der fachlichen Fallbearbeitung und Kommunikation zwischen Verwaltung und Mieter. PropertyFlow ist nicht an ein externes Beauftragungssystem angeschlossen und sendet keine Techniker- oder Dienstleisteraufträge. Die Verwaltung organisiert diese ausserhalb des Systems und kann den Mieter anschliessend darüber informieren. Ein Auftragsfreigabeformular mit automatischer Ausführung wird nicht eingeführt. Der zuvor vorgeschlagene Button «Handlung prüfen» für eine Beauftragung ist damit verworfen.

**Bestätigt am 10.10.2026:** Mitarbeitende dokumentieren fachliche Prüfungen, Abklärungen und ausserhalb von PropertyFlow organisierte Massnahmen über die bestehenden internen Memos. Dafür wird keine separate Aktion «Prüfung erledigt» und kein zusätzliches Prüfbestätigungsformular eingeführt. Offene fachliche Prüfgründe bleiben rechts sichtbar. Das Speichern eines Memos ändert für sich weder den Bearbeitungsstatus noch erledigt es automatisch einen Prüfgrund; der Mitarbeiter kann den Bearbeitungsstatus separat manuell anpassen. Die menschlichen Entscheidungsgrenzen nach FALL-10 bleiben gültig. Die bestätigte manuelle Statusänderung ist im folgenden Abschnitt beschrieben.

### Bearbeitungsstatus manuell ändern

**Bestätigt am 10.10.2026:** Mitarbeitende können den fachlichen Bearbeitungsstatus in UI3 von Hand anpassen. Interne Memos dienen der Dokumentation; ihre Speicherung allein löst keinen Statuswechsel aus. Die Statusänderung wird serverseitig autorisiert und fachlich geprüft, in PropertyFlow gespeichert und mit Zeitpunkt sowie handelnder Mitarbeiteridentität nachvollziehbar geführt. Sichtbare Statuswechsel lösen die Benachrichtigung nach BEN-01 aus. Die Fortsetzung wird mit dem führenden Camunda-Fallprozess abgestimmt. Abschluss und Wiedereröffnung bleiben den dafür geltenden Fachregeln unterstellt; eine beliebige Auswahl umgeht weder offenen Prüfbedarf noch die Nachrichtensperre. **Bedienung bestätigt am 10.10.2026:** Rechts öffnet «Status ändern» die Statusauswahl. Erst «Speichern» übernimmt den gewählten Status nach erfolgreicher serverseitiger Prüfung. Die Auswahl allein ändert den Status nicht.

**Bestätigt am 10.10.2026 – direkte Weiterbearbeitung:** Nach Prüfung kann der Mitarbeiter über «Status ändern» → «In Bearbeitung» → «Speichern» direkt aus «Mitarbeiterprüfung erforderlich» in die Bearbeitung wechseln, auch ohne Wiedervorlage. Dieser bestätigte Wechsel ist die Mitarbeiterentscheidung gemäss [FALL-06](../specifications/fallverwaltung.md#direkter-übergang-in-die-bearbeitung). Bereits berücksichtigte Prüfgründe lösen keinen sofortigen Rückwechsel aus; neue Erkenntnisse können erneut eine Prüfung erfordern. Mitarbeiteridentität, Zeitpunkt und geprüfter Fallstand bleiben nachvollziehbar. Es gibt keine zusätzliche Aktion «Prüfung erledigt»; Memos dienen weiterhin der Dokumentation. Die Konfliktprüfung sowie der Erhalt von Nachrichteneingabe und KI-Stream gelten weiterhin.

**Bestätigt am 10.10.2026:** Abschluss und Wiedereröffnung erfolgen über separate Buttons «Fall abschliessen» und «Fall wiedereröffnen», nicht über die normale Statusauswahl. «Status ändern» dient den aktiven Bearbeitungsstatus. Weitere Übergänge über die hier bestätigten Abläufe hinaus bleiben zu konkretisieren.

**Ergänzt am 10.10.2026 – optionale interne Begründung:** Die aufgeklappte Statusänderung enthält «Begründung (optional, intern)». Die Änderung eines aktiven Bearbeitungsstatus ist mit oder ohne Begründung möglich. Eine eingegebene Begründung wird nur zusammen mit der erfolgreich bestätigten Änderung gespeichert und intern dieser Änderung zugeordnet; sie erzeugt kein separates Memo. Sie erscheint weder beim Mieter noch in E-Mails und wird nicht in die KI-Kommunikationsformulierung übernommen (FALL-06, KOM-01/KOM-02). Abbrechen, Fehler oder Konflikte speichern weder Änderung noch Begründung. Abschluss und Wiedereröffnung behalten die unter FALL-07 vereinbarte separate Bedienung.

### Wiedervorlage

**Bestätigt am 10.10.2026:** Im rechten Bereich kann der Mitarbeiter für einen aktiven Fall eine optionale Wiedervorlage als Anzahl Tage, beispielsweise «in 3 Tagen», oder als festes Datum setzen. Der bestimmte Termin wird am Fall gespeichert und angezeigt; UI2 erhält die zusätzliche sortierbare Datumsspalte. Setzen führt zum neuen Status «Wartet auf Wiedervorlage». Fachlicher Ablauf und Nachvollziehbarkeit folgen [FALL-11](../specifications/fallverwaltung.md#fall-11-wiedervorlage-und-parallele-nachrichtenverarbeitung); Camunda führt die Wartezeit als Timer im bestehenden Fallprozess.

Der Mitarbeiter kann das Datum während des aktiven Falls jederzeit ändern oder löschen. Rechts stehen dafür «Wiedervorlage ändern» mit Terminwahl und «Speichern» sowie «Wiedervorlage löschen» zur Verfügung. Eine Änderung übernimmt den neuen Termin in den Camunda-Timer und setzt «Wartet auf Wiedervorlage». Löschen entfernt den aktuellen Termin, beendet die Wartezeit und setzt «In Bearbeitung»; die Historie bleibt erhalten. Änderungen werden erst nach erfolgreicher serverseitiger Prüfung wirksam. Die Konfliktprüfung und der Erhalt von Nachrichteneingabe und KI-Stream gelten auch beim Ändern und Löschen der Wiedervorlage. «Wartet auf Wiedervorlage» kann über die allgemeine Statusauswahl nicht ohne gespeicherten Termin gesetzt werden.

Neue Mieternachrichten erscheinen weiterhin links und werden parallel zur Wartezeit automatisch verarbeitet. Ihr Eingang allein beendet das Warten nicht. Neue Erkenntnisse können eine frühere, bei unmittelbarem Handlungsbedarf sofortige Wiedervorlage auslösen; andernfalls bleibt der gesetzte Termin bestehen. Beim wirksamen Wiedervorlagetermin erhält der aktive Fall «Mitarbeiterprüfung erforderlich». Eine manuelle Terminänderung wird im Timer berücksichtigt; beim Abschluss entfällt die offene Wiedervorlage.

Die Aktualisierung von Nachrichten, Status, Dringlichkeit und Wiedervorlagedatum folgt weiterhin Abschnitt 6 und erhält Mitarbeitereingabe und KI-Stream. Änderungen am Wiedervorlagedatum werden im Popup mit altem und neuem Wert angezeigt; ein gelöschter Termin wird als «Keine Wiedervorlage» erkennbar. Tagesberechnung, Uhrzeit bei festem Datum und Darstellungsdetails des Aktualisierungs-Popups werden noch konkretisiert; die Desktop-Grundanordnung ist bestätigt.

**Ergänzt am 10.10.2026 – optionale interne Begründung:** Bei Setzen, Ändern und Löschen steht «Begründung (optional, intern)» im Wiedervorlage-Bereich zur Verfügung. «Speichern» übernimmt sie mit der Terminsetzung oder -änderung; «Wiedervorlage löschen» übernimmt sie mit der Löschung. Jede Aktion bleibt ohne Begründung möglich. Die Begründung wird intern der erfolgreich bestätigten Aktion zugeordnet, auch nach Entfernung des Termins, und erzeugt kein separates Memo. Sie bleibt von Mieteransicht, E-Mail und KI-Kommunikationsformulierung ausgeschlossen (FALL-11, KOM-01/KOM-02). Abbrechen, Fehler oder Konflikte speichern weder Aktion noch Begründung.

### Abschluss und abgeschlossene Fälle

Mitarbeitende können den fachlichen Abschluss bestätigen. Das System kann ausserhalb verpflichtender menschlicher Entscheidungen die vereinbarten Abschlussgründe aus FALL-07 anwenden. Ein späterer Mieterhinweis «behoben» umgeht keinen offenen Prüfbedarf nach FALL-10.

Nach Abschluss bleiben die gespeicherten Fallinhalte bei gültiger Berechtigung lesbar. Neue Nachrichten und interne Memos sind für alle Absender gesperrt; auch verspätete KI-Ergebnisse umgehen diese Sperre nicht. Ein Versandproblem nach Abschluss eröffnet den Fall nicht wieder.

**Bestätigt am 10.10.2026:** Mit dem erfolgreichen Abschluss endet noch offener Mieterantwortbedarf gemäss KOM-04. Die bisherigen Rückfragen bleiben im Verlauf erhalten, ohne unbeantwortete Fragen als beantwortet darzustellen. Der bestehende Status «Abgeschlossen» ist massgeblich; dafür wird kein zusätzlicher Fall- oder Rückfragestatus eingeführt. UI1 und UI3 verlangen danach keine Antwort mehr. Ein abgebrochener oder fehlgeschlagener Abschluss lässt den bisherigen Antwortbedarf bestehen.

**Bestätigte Aktion:** Bei aktivem Fall steht rechts der separate Button «Fall abschliessen» zur Verfügung; der Abschluss unterliegt den serverseitigen Voraussetzungen aus FALL-07/FALL-10.

**Bestätigt am 10.10.2026 – Abschlussbestätigung:** «Fall abschliessen» öffnet die Bestätigung «Fall wirklich abschliessen? Danach sind keine neuen Nachrichten oder internen Memos möglich.» mit «Abschliessen» und «Abbrechen». Erst «Abschliessen» fordert den fachlichen Abschluss an; Berechtigung, aktueller Fallstand und Abschlussvoraussetzungen werden serverseitig geprüft. «Abbrechen» schliesst die Bestätigung ohne Änderung am Fall. Nach bestätigtem Abschluss gilt die Nachrichtensperre bis zu einer bewussten Wiedereröffnung.

**Bestätigt am 10.10.2026 – optionale Abschlussnachricht:** Eine zusätzliche externe Abschlussnachricht ist optional. Der Mitarbeiter sendet sie vor dem Abschluss über das bestehende gemeinsame Textfeld mit «Nachricht senden». Der Abschlussdialog enthält kein zusätzliches Nachrichtenfeld. Der fachliche Abschluss veranlasst unabhängig davon die automatische Abschluss-E-Mail nach BEN-05; sie erzeugt keine nachträgliche Kommunikationsnachricht. Eine bereits gesendete Abschlussnachricht bleibt auch dann veröffentlicht, wenn der anschliessende Abschluss abgebrochen wird oder scheitert.

**Bestätigt am 10.10.2026 – interner Abschlussnachweis:** Beim erfolgreichen Mitarbeiterabschluss speichert PropertyFlow intern den Vermerk «Vom Mitarbeiter geprüft: keine weitere Bearbeitung erforderlich.» mit Mitarbeiteridentität, Zeitpunkt und Abschlussquelle. Die bewusste Bestätigung ist die auslösende Grundlage nach FALL-07. Dafür gibt es kein zusätzliches Begründungsfeld und kein Pflichtmemo. Der Vermerk ist Teil des strukturierten Abschlussnachweises und keine neue Kommunikationsnachricht. Bei Bedarf dokumentiert der Mitarbeiter eine ausführlichere Erklärung vor dem Abschluss als freiwilliges internes Memo über «Intern» und «Memo speichern». Bei einem Systemabschluss wird weiterhin dessen tatsächlich geprüfter fachlicher Grund gespeichert.

**Bestätigt am 09.10.2026:** Nur Mitarbeitende können einen abgeschlossenen Fall in dieser Ansicht bewusst wiedereröffnen. Case-ID und bisheriger Verlauf bleiben erhalten; die Änderung bleibt mit Zeitpunkt und Mitarbeiteridentität nachvollziehbar. Mieter und System besitzen diese Berechtigung nicht. Nach bestätigter Wiedereröffnung gelten wieder die Regeln für aktive Fälle, einschliesslich der Möglichkeit, Nachrichten und interne Memos zu speichern.

**Bestätigte Aktion:** Bei abgeschlossenem Fall steht rechts der separate Button «Fall wiedereröffnen» zur Verfügung.

**Bestätigt am 10.10.2026 – Wiedereröffnungsablauf:** «Fall wiedereröffnen» öffnet die Bestätigung «Fall wiedereröffnen?» mit «Wiedereröffnen» und «Abbrechen». Erst «Wiedereröffnen» fordert die Wiedereröffnung unter serverseitiger Prüfung an; «Abbrechen» bewirkt keine Falländerung. Nach bestätigter Wiedereröffnung erhält der Fall «In Bearbeitung». Falls offener Prüfbedarf nach FALL-05/FALL-10 besteht, erhält er stattdessen «Mitarbeiterprüfung erforderlich». Case-ID und Historie bleiben erhalten; neue Nachrichten und Memos sind erst nach bestätigter Wiedereröffnung wieder zulässig.

**Bestätigt am 10.10.2026 – verspätete Verarbeitung:** Automatische Ergebnisse aus der Bearbeitung vor dem letzten Abschluss dürfen den wiedereröffneten Fall nicht verändern, insbesondere nicht erneut abschliessen oder alte automatische Nachrichten nachtragen. UI3 zeigt den aktuellen gespeicherten Fallstand und den erhaltenen Verlauf gemäss [FALL-07](../specifications/fallverwaltung.md#verspätete-automatische-ergebnisse-nach-wiedereröffnung). Hierfür entsteht keine zusätzliche Mitarbeiteraktion. Die technische Prozess- und LLM-Ausarbeitung folgt später.

**Bestätigt am 10.10.2026 – optionale Begründung:** Nach bestätigter Wiedereröffnung kann der Mitarbeiter bei Bedarf eine Begründung als internes Memo über die bestehende Eingabe erfassen. Der Wiedereröffnungsdialog enthält kein zusätzliches Begründungsfeld; ein Begründungsmemo ist keine Pflicht. Zeitpunkt und handelnde Mitarbeiteridentität der Wiedereröffnung bleiben unabhängig davon nachvollziehbar.

**Bestätigt am 10.10.2026:** Im abgeschlossenen Zustand bleibt die Dringlichkeit ausschliesslich lesbar. Eine manuelle Dringlichkeitsänderung erfordert zuerst die bestätigte Wiedereröffnung. Auch automatische Bewertungen dürfen im abgeschlossenen Zustand die wirksame Dringlichkeit nicht ändern; die Sperre wird serverseitig durchgesetzt.

## 4. Automatische Verarbeitung und KI-Formulierungshilfe

**Bestätigt am 10.10.2026:** Fallanalyse, weitere automatische Bearbeitung und Dringlichkeitsbewertung erfolgen im Camunda-Fallprozess. Ein Fall kommt im vorgesehenen Ablauf bereits analysiert und bearbeitet zum Mitarbeiter. Analyseausfälle verhindern die manuelle Bearbeitung nicht; FALL-05 bleibt gültig. UI3 enthält keine manuell startbare Fallanalyse.

Das System speichert das Analyseergebnis als Nachricht mit Absender und Herkunft «System». Die Sichtbarkeit wird gemäss KOM-01 bestimmt: Eine externe Systemnachricht wird bei Veröffentlichung persistiert und dem Mieter angezeigt; ein internes Systemmemo bleibt intern. Eine pauschale Veröffentlichung sämtlicher Analyseinformationen ist damit nicht festgelegt. Die Dringlichkeitsbewertung bleibt ein eigener fachlicher Wert mit Historie; manuelle Einstufungen bleiben geschützt.

Der Mitarbeiter kann die KI eine freundliche externe Nachricht formulieren lassen. Er gibt beispielsweise «Wir haben den Techniker aufgeboten» ein; die KI erzeugt daraus einen Vorschlag. Diese Hilfe löst keine Beauftragung aus und ersetzt keine Entscheidung nach FALL-10.

Die Ausgabe folgt UC-004: bereit, wartet, Streaming, vollständig, abgebrochen oder fehlgeschlagen.

**Bestätigt am 10.10.2026:** In derselben Fallansicht läuft höchstens ein Formulierungsversuch gleichzeitig. Während Warten und Streaming ist «Mit KI formulieren» deaktiviert. Für einen neuen Vorschlag beendet der Mitarbeiter zuerst den laufenden Versuch mit «Abbrechen» oder wartet, bis er abgeschlossen ist. Nach Abschluss, Abbruch oder Fehler kann bei externer Auswahl erneut formuliert werden. Während Warten und Streaming kann der Mitarbeiter den Formulierungsversuch wirksam abbrechen. Der Abbruch betrifft nicht automatisch den Camunda-Fallprozess.

Der vollständige Vorschlag wird vom Mitarbeiter geprüft und kann bearbeitet werden. Erst «Nachricht senden» speichert und veröffentlicht ihn. Weder Kurzeingabe noch Teilantwort noch vollständiger ungesendeter Vorschlag werden als Nachrichtenentwurf persistiert oder im Fallverlauf angezeigt.

**Bestätigter Fehlertext am 10.10.2026:** «Die Nachricht konnte nicht formuliert werden.» Die aktuelle Eingabe bleibt erhalten; der Mitarbeiter kann erneut formulieren lassen oder manuell bearbeiten und senden, sofern der Fall dies erlaubt. Ein fehlgeschlagener oder unvollständiger KI-Text wird nicht als vollständiger Vorschlag übernommen. Ein Formulierungsfehler ist vom Fehler der automatischen Fallanalyse zu unterscheiden.

**Verbindliche Kommunikationsgrenze, präzisiert am 10.10.2026:** Das System übernimmt nach KOM-02 keine Mitarbeiter- oder Systemmemos und keine automatisch daraus erzeugten Zusammenfassungen oder Fakten in den KI-Kontext. Der Mitarbeiter darf bewusst eine kurze Sachanweisung zur externen Formulierung eingeben, auch wenn dieselbe Information in einem Memo steht. Beispielsweise ist «Techniker kommt am Dienstag. Bitte Zugang zur Wohnung ermöglichen.» zulässig; nur im Memo vorhandene Kostenfreigaben werden nicht nachgeladen. Der Mitarbeiter prüft den Vorschlag vor dem Senden. Seine Eingabe erlaubt keinen automatischen Zugriff auf das Memo.

**Bestätigt am 10.10.2026 – Position und Beschriftung:** Der Button «Mit KI formulieren» steht direkt neben dem gemeinsamen Eingabefeld, zusammen mit dem Toggle «Intern». Er startet die Formulierung einer externen Nachricht nach UC-004. Der Bereich darunter trägt nur die Überschrift «KI-Vorschlag», ohne Zusatz «ungespeichert». Der Generierungszustand bleibt erkennbar, beispielsweise «Vollständig». Der Vorschlag bleibt fachlich ungespeichert; Übernahme und Veröffentlichung sind getrennte bewusste Aktionen. Nach «Vorschlag übernehmen» steht der Text im bearbeitbaren Nachrichtenfeld; der KI-Vorschlagsbereich verschwindet und der bisherige Vorschlag wird geleert. Beim erneuten Start von «Mit KI formulieren» erscheint der Bereich wieder mit dem neuen Generierungsstatus. Die Übernahme sendet keine Nachricht. Der KI-Vorschlagsbereich ist beim Öffnen der Detailansicht verborgen und erscheint erst nach Klick auf «Mit KI formulieren». «Vorschlag löschen» steht direkt rechts neben «Vorschlag übernehmen» und verwirft den vorhandenen KI-Vorschlag und blendet dessen Bereich aus, ohne den Text im Nachrichtenfeld zu verändern. Während Generierung oder Speicherung ist der Löschbutton gesperrt; ein abgebrochener oder fehlerhafter Teilvorschlag kann ebenfalls gelöscht werden.

**Bestätigtes Verhalten bei interner Auswahl:** Bei eingeschaltetem «Intern» ist «Mit KI formulieren» deaktiviert. Die Funktion dient ausschliesslich externen Nachrichten. Das Abschalten des Toggles liest keine Memos ein und startet keinen KI-Aufruf. Erst der bewusste Formulierungsaufruf verwendet die vom Mitarbeiter ausgewählte externe Sachanweisung und den nach KOM-02 zulässigen Kontext; gespeicherte Memos bleiben intern.

**Bestätigt am 10.10.2026 – Stream und Übernahme:** Der KI-Vorschlag erscheint zunächst als Stream unter dem gemeinsamen Eingabefeld. Die Mitarbeitereingabe bleibt während der Formulierung erhalten und bearbeitbar, sofern keine Nachrichten- oder Memo-Speicherung läuft. Während einer solchen Speicherung gilt die Sperre aus «Rückmeldung nach Senden oder Memo-Speicherung» auch für das Übernehmen eines vollständigen Vorschlags.

**Bestätigt am 10.10.2026:** Ausserhalb einer laufenden Nachrichten- oder Memo-Speicherung kann der Mitarbeiter seinen Text während Warten und Streaming weiter bearbeiten. Erst «Vorschlag übernehmen» ersetzt den zu diesem Zeitpunkt aktuellen Text im Eingabefeld durch den vollständigen KI-Vorschlag; es erfolgt keine automatische Ersetzung. Änderungen während des Streams verändern die beim Start verwendete Eingabe des laufenden KI-Versuchs nicht. Erst nach vollständiger Ausgabe kann der Mitarbeiter mit «Vorschlag übernehmen» den Vorschlag ins Eingabefeld übernehmen, prüfen, bearbeiten und anschliessend mit «Nachricht senden» veröffentlichen. Während Warten und Streaming steht «Abbrechen» zur Verfügung; der Abbruch beendet den Formulierungsversuch wirksam, danach werden keine weiteren Antwortteile verarbeitet oder angezeigt. Ein abgebrochener Teiltext ist kein vollständiger übernehmbarer Vorschlag. Abbruch und Übernahme speichern oder veröffentlichen keine Nachricht und brechen nicht den Camunda-Fallprozess ab. **Weiterer bestätigter Randfall am 10.10.2026:** Schaltet der Mitarbeiter während einer laufenden KI-Formulierung auf «Intern», läuft der bereits gestartete Stream weiter. «Mit KI formulieren» und «Vorschlag übernehmen» sind bei interner Auswahl deaktiviert; «Abbrechen» bleibt während Warten und Streaming verfügbar. Nach dem Zurückschalten auf extern kann ein vollständiger Vorschlag wieder übernommen werden, sofern keine Speicherung läuft. Der laufende Versuch behält seine beim Start geprüfte externe Eingabe und seinen zulässigen Kommunikationskontext; Umschalten oder spätere interne Texte werden nicht nachträglich in den KI-Kontext aufgenommen.

## 5. Technischer Wireframe der bestätigten Anordnung

Die Skizze zeigt die bestätigte Anordnung; Beispieldaten sind fiktiv. `[Person]`, `[Zahnrad]` und `[Schloss]` stehen hier für die in Abschnitt 3 festgelegten Icons. Gleich breite Blasen enthalten alle Metadaten; Text und Metadaten folgen der jeweiligen Ausrichtung. Details zu Farben und Karten stehen in Abschnitt 2.1.

~~~text
PropertyFlow · Mitarbeiterbereich                                     [Zur Fallübersicht]
REQ-2026-001 · Heizung funktioniert nicht     Status | Dringlichkeit | Wiedervorlage
                                            bestätigt | Hoch · System | kein Termin

+-------------------------------------------------------+-----------------------------------------+
| VERLAUF · nur dieser Bereich scrollt                  | FALLDATEN UND AKTIONEN                  |
|                                                       | unabhängig scrollbar bei Überlänge      |
| +--------------------------------------+              | Ursprungsdaten · bestätigte Zuordnung   |
| | [Person] Mieter                      |              | [Zuordnung ändern] · Kontakt · Zeiten   |
| | Zeitpunkt · Nachricht                |              |                                         |
| +--------------------------------------+              | PRÜFBEDARF · konkrete interne Gründe    |
|                                                       |                                         |
|             +--------------------------------------+  | STATUS                 [Status ändern]  |
|             |                            [Zahnrad] |  | WIEDERVORLAGE   [Wiedervorlage ändern]  |
|             |                Zeitpunkt · Nachricht |  | DRINGLICHKEIT  [Dringlichkeit ändern]   |
|             +--------------------------------------+  |                                         |
|                                                       | Aufgeklappt: gespeicherte Auswahl bzw.  |
|             +--------------------------------------+  | Termin vorausgewählt; Begründung        |
|             |                            [Zahnrad] |  | optional, intern; Speichern / Abbrechen |
|             |             Zeitpunkt · Stufenwechsel | |                                         |
|             +--------------------------------------+  | Bei Wiedervorlage zusätzlich Löschen    |
|                                                       |                                         |
|             +--------------------------------------+  |                                         |
|             |        [Person] Verwaltung [Schloss] |  |                                         |
|             |                     Zeitpunkt · Memo |  |                                         |
|             +--------------------------------------+  |                                         |
| ────────────────────────────────────────────────────  |                                         |
| FESTER EINGABEBEREICH                                 |                                         |
| Nachricht verfassen · Textfeld                        |                                         |
| [Mit KI formulieren]                                  |                                         |
| Toggle Intern · Toggle Mieterantwort erforderlich     |                                         |
| [Nachricht senden / Memo speichern]                   |                                         |
| KI-Vorschlag · Generierungszustand                    |                                         |
| [Abbrechen] während Warten/Streaming                  |                                         |
| [Vorschlag übernehmen] wenn vollständig               | ABSCHLUSS          [Fall abschliessen]  |
+-------------------------------------------------------+-----------------------------------------+
~~~

Bei abgeschlossenem Fall steht statt «Fall abschliessen» die Aktion «Fall wiedereröffnen». Die Werte der geschlossenen Änderungskarten stehen nur im Kopf. In der rechten Spalte ist höchstens eine Bearbeitungsbox geöffnet (Zuordnung, Status, Wiedervorlage, Dringlichkeit sowie Abschluss/Wiedereröffnung). Öffnen einer Box schliesst die anderen, ohne deren Eingaben zu speichern; blosses Schliessen setzt Eingaben nicht zurück. «Abbrechen» verwirft weiterhin die Eingabe der jeweiligen Box. Die Aktionsbeschriftungen «Status ändern», «Wiedervorlage ändern» und «Dringlichkeit ändern» sind rechtsbündig an einer gemeinsamen Textkante ausgerichtet; die Aufklapppfeile stehen mit einheitlichem Abstand rechts daneben. Bei mehrzeiligen Beschriftungen bleibt die Rechtsausrichtung erhalten. Der KI-Vorschlag liegt im festen Eingabebereich und bleibt bis zur bewussten Veröffentlichung ungespeichert, ohne diesen Zusatz in der Überschrift.

Die alternativen Aktionen werden zustandsabhängig angezeigt: «Nachricht senden» extern, «Memo speichern» intern. KI-Formulieren und Übernahme sind intern deaktiviert. Während der Nachrichten- oder Memo-Speicherung sind Textfeld, beide Auswahlfelder, KI-Start, Vorschlagsübernahme und Sende-/Speicheraktion deaktiviert. Im abgeschlossenen Zustand sind Nachrichtenschreiben, Memo-Speichern und Dringlichkeitsänderung gesperrt; der vorhandene Verlauf bleibt lesbar. Ein bereits laufender Stream wird durch eine Aktualisierung oder die Speicherung nicht beendet.

Bei neuen Daten erscheint ein Popup mit Hinweis auf neue Einträge bzw. altem/neuem Status oder Dringlichkeitswert und «OK». Es löscht keine Texte und beendet keinen Stream.

Auf schmalen Bildschirmen: Kopf → Falldaten und Aktionen → Verlauf mit Eingabe und KI-Vorschlag; gemeinsames Seitenscrollen.

## 6. Bestätigte Bedienung und Randfälle

Alle folgenden Bedienungsregeln sind am 10.10.2026 bestätigt.

| Thema | Regel |
|---|---|
| Rückkehr zu UI2 | «Zur Fallübersicht» erhält Suche, Filter, Sortierung und zuletzt gewählte Seite. Für entfallene Seiten gelten die UI2-Regeln. |
| Aktualisierung | Alle 20 Sekunden den gespeicherten Verlauf, Status, die wirksame Dringlichkeit, den Wiedervorlagetermin und die bestätigte Objekt-/Wohnungszuordnung nachführen. Neue Nachrichten und Memos erscheinen links ohne doppelte Einträge. |
| Neue Daten | Popup bei neuen Nachrichten/Memos und bei Status-/Dringlichkeits- oder Zuordnungsänderungen. Es zeigt den Hinweis auf neue Einträge und bei Wertänderungen alten/neuen Wert; bei mehreren Änderungen alle betroffenen Werte. «OK» schliesst den Hinweis, ohne eine fachliche Änderung freizugeben. |
| Erhaltungsregel | Datenabgleich und Popup erhalten Mitarbeitereingabe, KI-Teiltext, vollständigen Vorschlag, übernommenen Text und Leseposition. Der laufende Stream wird weder abgebrochen noch neu gestartet. Fokus bleibt beim Datenabgleich erhalten; Popup-Fokusführung wird technisch ausgearbeitet. |
| Konflikt beim Speichern | Vor manueller Status-/Dringlichkeits- oder Zuordnungsänderung sowie Setzen, Ändern oder Löschen der Wiedervorlage serverseitig prüfen, ob Einstellungen seit Beginn der Bearbeitung geändert wurden. Bei Konflikt nichts überschreiben, aktuellen Einstellungsstand neu laden und «konnte nicht erfolgreich gespeichert werden» anzeigen. Die Erhaltungsregel gilt weiterhin. Prüfung und Speicherung müssen konsistent gegen gleichzeitige Änderungen abgesichert sein. |
| Erstabruf fehlgeschlagen | «Die Fallansicht konnte nicht geladen werden.» mit «Erneut versuchen» und «Zur Fallübersicht». Erneut versuchen liest nur; es entsteht kein Fall und keine Nachricht. |
| Aktualisierung fehlgeschlagen | Letzten bestätigten Fallstand erhalten und «Aktualisierung fehlgeschlagen» mit «Erneut versuchen» anzeigen. Den alten Stand nicht als erfolgreich aktualisiert ausgeben. Eingabe und KI-Stream bleiben erhalten. Eigenständige KI-Verbindungsfehler folgen UC-004. |
| Fall nicht verfügbar | «Dieser Fall ist nicht verfügbar.» mit «Zur Fallübersicht», ohne Falldaten. Technische Ladefehler folgen der separaten Erstabruf-Regel. |
| Ungesendeter Text | Vor bewusstem Verlassen/Neuladen vor Textverlust warnen, soweit der Browser dies unterstützt. Abbrechen des Seitenwechsels erhält Text. Keine automatische Speicherung oder Entwurfsablage. Leere bzw. erfolgreich gespeicherte/gesendete Eingaben lösen keine Warnung aus. |
| Abschluss während Eingabe/Stream | Texte und laufenden Stream erhalten. Nachrichten- und Memo-Speicherung sowie Dringlichkeitsänderungen im abgeschlossenen Zustand serverseitig sperren. Eine gespeicherte Ausgabe oder ein Stream umgeht diese Sperre nicht. |
| Suchtreffer aus UI2 | Normale Detailansicht ohne Sprung zur Fundstelle und ohne Hervorhebung. Keine zusätzliche globale Volltextsuche. |
| Schmaler Bildschirm | Kopf → Falldaten und Aktionen → Verlauf mit Eingabe; die Seite als Ganzes scrollen. Funktionen bleiben per Tastatur erreichbar. |

## 7. Prüfkriterien für die spätere Umsetzung

Diese Kriterien bilden die bestätigten Fachregeln und Bedienungsentscheidungen ab. Sie beschreiben spätere Prüfungen und sind kein Testbericht.

- **FD-AK-01:** Ein berechtigter Aufruf zeigt genau den ausgewählten Fall als eigene Seite mit Kopf oben, Verlauf links und Falldaten/Aktionen rechts auf breiten Bildschirmen.
- **FD-AK-02:** Alle Mitarbeitenden haben denselben fachlichen Zugriff; UI3 verlangt keine Anmeldung und enthält keine Anmelde-, Abmelde- oder Sitzungserneuerungsaktion. Fremde oder nicht berechtigte Aufrufer erhalten keine Fallinhalte. Die technische Abgrenzung und der Nachweis einer individuellen Mitarbeiteridentität für Änderungen folgen der noch auszuarbeitenden Grundlage in ZUG-04; eine nicht vorhandene Anmeldung wird dafür nicht vorausgesetzt.
- **FD-AK-03:** Gespeicherte interne Memos, ungespeicherte externe Eingaben und veröffentlichte externe Nachrichten sind unterscheidbar. Externe Eingaben werden erst bei Veröffentlichung persistiert; eine Entwurfsspeicherung ist ausgeschlossen. Nur ausdrücklich veröffentlichte externe Inhalte gelangen in die Mieterkommunikation; KOM-02 wird bereits vor einer LLM-Generierung durchgesetzt.
- **FD-AK-04:** Manuelle Dringlichkeitsänderungen bleiben vor Systemüberschreibung geschützt. Wirksame Änderungen erscheinen konsistent in aktueller Stufe und Historie; UI1 erhält nur die freigegebenen Angaben.
- **FD-AK-05:** Prüfgründe nach FALL-05/FALL-10 sind im Detail nachvollziehbar. Der nach Prüfung bewusst gespeicherte Wechsel von «Mitarbeiterprüfung erforderlich» auf «In Bearbeitung» gilt auch ohne Wiedervorlage als Mitarbeiterentscheidung. Bereits berücksichtigte Gründe lösen keinen sofortigen Rückwechsel aus; neue Erkenntnisse können erneut eine Prüfung auslösen. Ein Memo allein ersetzt die Entscheidung nicht. Keine Aktion und kein Systemabschluss umgeht eine erforderliche menschliche Entscheidung; Freigaben gelten nur für die geprüfte Handlung. Die Zuordnung zum geprüften Fallstand, Konfliktprüfung und Erhaltung von Eingabe und KI-Stream werden geprüft.
- **FD-AK-06:** Automatische Analyse und Dringlichkeitsbewertung laufen im Camunda-Prozess. Die interaktive Formulierungshilfe mit Streaming, wirksamem Abbruch, Fehlerverhalten und sicherer Textausgabe folgt UC-004; ungesendete Vorschläge werden nicht persistiert. Gespeicherte Fallinhalte und manuelle Bearbeitung bleiben erhalten.
- **FD-AK-07:** Ein zwischenzeitlicher Abschluss sperrt neue Nachrichten und Memos serverseitig auch bei veralteter Anzeige oder verspäteten Ergebnissen. Lesen bleibt bei gültiger Berechtigung möglich.

- **FD-AK-08:** Nur Mitarbeitende können den abgeschlossenen Fall in UI3 bewusst wiedereröffnen. Der bestehende Verlauf und die Case-ID bleiben erhalten. Erst nach bestätigter Wiedereröffnung sind neue Nachrichten und Memos zulässig; Mieter- und Systemberechtigungen erlauben keine Wiedereröffnung. Verspätete automatische Ergebnisse aus der Bearbeitung vor dem letzten Abschluss erzeugen auch nach Wiedereröffnung keine Falländerung oder neue Nachricht; die fachliche Grenze folgt FALL-AK-16 und KOM-AK-07.

- **FD-AK-09:** Ein gemeinsames Textfeld mit Toggle «Intern» unterscheidet interne Memos und externe Antworten. Der Toggle ist beim Öffnen ausgeschaltet. Beim Umschalten bleibt der eingegebene Text erhalten; Hinweis und Aktionsbutton passen sich an. Umschalten allein speichert oder veröffentlicht nichts. Gespeicherte Memos bleiben intern; externe Antworten werden erst nach bewusster Veröffentlichung für den Mieter sichtbar.

- **FD-AK-10:** Auf breiten Bildschirmen kann ausschliesslich der linke Verlauf gescrollt werden, ohne den festen Eingabebereich darunter mit Aktionen und KI-Vorschlag, die rechte Hälfte oder den gemeinsamen Kopf zu verschieben. Beide Arbeitsbereiche nutzen die verfügbare Höhe bis zum unteren Aussenabstand. Bei überlangem rechten Inhalt ist die rechte Hälfte unabhängig scrollbar. Auf schmalen Bildschirmen stehen Kopf, Falldaten und Aktionen sowie Verlauf mit Nachrichteneingabe in dieser Reihenfolge untereinander; die Seite wird als Ganzes gescrollt.

- **FD-AK-11:** Ungesendeter externer Text und ungespeicherte interne Eingaben lösen beim bewussten Verlassen oder Neuladen eine Textverlustwarnung aus, soweit der Browser dies unterstützt. Abbrechen des Seitenwechsels erhält die Eingabe. Die Warnung speichert oder veröffentlicht nichts; leere oder erfolgreich gespeicherte/gesendete Eingaben lösen sie nicht aus.

- **FD-AK-12:** Periodische Leseabrufe alle 20 Sekunden führen gespeicherten Verlauf, Status und Dringlichkeit nach. Neue gespeicherte Mieter- und Systemnachrichten erscheinen links im Verlauf, mit erkennbarer Absenderrolle und Sichtbarkeit. Neue gespeicherte Nachrichten oder Memos sowie erkannte Änderungen von Status oder wirksamer Dringlichkeit zeigen ein Popup mit dem Button «OK» zum Schliessen. Status- und Dringlichkeitsänderungen zeigen den alten und neuen Wert; neue Einträge erhalten einen Hinweis auf neue Daten im Verlauf. Bei gleichzeitiger Änderung beider Werte werden beide angezeigt; «OK» bestätigt keine neue fachliche Änderung. Mitarbeitereingabe und KI-Ausgabe bleiben erhalten; der aktive Stream wird weder abgebrochen noch neu gestartet. Ein währenddessen bestätigter Abschluss sperrt weiterhin das Senden/Speichern serverseitig, ohne die vorhandenen Texte zu löschen.

- **FD-AK-13:** Vor dem Speichern einer manuellen Status-/Dringlichkeitsänderung prüft das System auf zwischenzeitlich geänderte Einstellungen. Bei Konflikt wird nichts überschrieben; der aktuelle Einstellungsstand wird neu geladen und «konnte nicht erfolgreich gespeichert werden» angezeigt. Mitarbeitereingabe, KI-Ausgabe und aktiver Stream bleiben erhalten. Eine Änderung zwischen Prüfung und Speicherung darf die Konfliktprüfung nicht umgehen.

- **FD-AK-14:** Externe und interne Eingabe teilen ein Textfeld. Während einer absichtlich verzögerten Nachrichten- oder Memo-Speicherung lassen sich Text, «Intern» und «Mieterantwort erforderlich» nicht ändern; KI-Start, Vorschlagsübernahme und Sende-/Speicheraktion sind ebenfalls deaktiviert. Ein gleichzeitig abgeschlossener KI-Vorschlag ersetzt den Text nicht und hebt die Sperre nicht auf. Nach bestätigtem Senden/Speichern wird das Textfeld geleert und beide Auswahlfelder werden ausgeschaltet; bei Fehler bleiben Text und Auswahl erhalten. Nach Ende des Speichervorgangs gelten wieder die Bedieneinschränkungen des aktuellen Fall- und KI-Zustands. Die Freigabe wartet nicht auf den E-Mail-Versand. Leere Texte werden abgewiesen; Wiederholungen erzeugen keinen zweiten Eintrag.
- **FD-AK-15:** «Mieterantwort erforderlich» ist beim Öffnen ausgeschaltet und nur extern wirksam. Senden einer Rückfrage übernimmt Antwortbedarf und den passenden Wartestatus, ohne vorrangigen Prüfbedarf zu umgehen. Fallansicht und E-Mail machen den Antwortbedarf erkennbar; reine Informationen erledigen keine ältere Rückfrage. «Antwortbedarf aufheben» beendet gezielt den Antwortbedarf einer veröffentlichten Rückfrage gemäss KOM-AK-10, ohne die Nachricht zu löschen, den Fall abzuschliessen oder Eingabe und KI-Stream zurückzusetzen.
- **FD-AK-16:** «Mit KI formulieren» steht neben der Eingabe und «Intern». Der Stream erscheint darunter, erhält die ausserhalb einer Nachrichten- oder Memo-Speicherung weiterhin bearbeitbare Eingabe und ist wirksam abbrechbar. Eine Speicherung beendet den Stream nicht; FD-AK-14 sperrt dabei Eingabe, KI-Start und Übernahme. Nur ein vollständiger Vorschlag ist extern und ausserhalb einer laufenden Speicherung mit «Vorschlag übernehmen» übernehmbar; Übernahme ersetzt den aktuellen Eingabetext, speichert aber nichts. Intern und während eines laufenden Versuchs ist der Generierungsbutton deaktiviert. Umschalten auf intern beendet den bestehenden Stream nicht, sperrt aber die Übernahme. Die Start-Eingabe des laufenden Versuchs wird durch spätere Änderungen nicht verändert.
- **FD-AK-17:** Status und Dringlichkeit werden rechts über Änderungsauswahl und «Speichern» angepasst. Abschluss und Wiedereröffnung verwenden separate Buttons mit den vereinbarten Bestätigungen. Wiedereröffnung erhält Case-ID und Historie und führt zu «In Bearbeitung» bzw. bei offenem Prüfbedarf «Mitarbeiterprüfung erforderlich». Abschlussbegründung ist optional als Memo davor, Wiedereröffnungsbegründung optional als Memo danach; die Dialoge enthalten keine zusätzlichen Begründungsfelder. Der erfolgreiche Mitarbeiterabschluss speichert den internen festen Abschlussvermerk samt Mitarbeiter, Zeitpunkt und Quelle nach FALL-AK-12 auch ohne Memo. Er erzeugt dadurch keine zusätzliche Kommunikationsnachricht; ein Systemabschluss behält seinen tatsächlich geprüften Grund.
- **FD-AK-18:** Verlaufseinträge zeigen Absender, Zeitpunkt, Herkunft System/Mensch und Sichtbarkeit. Mitarbeiterseitig geprüfte und gesendete KI-Formulierungen tragen das Personen-Icon ohne KI-Zusatz. Systemmemos bleiben intern. Das System übernimmt keine Memos oder daraus abgeleiteten Inhalte in den Kommunikations-LLM-Kontext. Bewusst eingegebene kurze Sachanweisungen sind nach KOM-AK-04/05 auch dann zulässig, wenn dieselbe Information in einem Memo steht; weitere Memo-Inhalte werden nicht nachgeladen. PropertyFlow führt keine externen Beauftragungen aus.

- **FD-AK-19:** Wiedervorlage in Tagen oder als Datum und der gespeicherte Termin sind in UI3 verfügbar. Der Mitarbeiter kann den Termin im aktiven Fall ändern oder löschen. Setzen und Ändern führen zu «Wartet auf Wiedervorlage», Löschen zu «In Bearbeitung» ohne aktuellen Termin; Historie und Mitarbeiteridentität bleiben nachvollziehbar. Während der Camunda-Timer wartet, erscheinen neue gespeicherte Mieternachrichten weiterhin im Verlauf und werden automatisch verarbeitet. Ohne neuen früheren Handlungsbedarf bleibt der Termin bestehen; entsprechende neue Erkenntnisse ziehen die Wiedervorlage vor. Am wirksamen Termin wird «Mitarbeiterprüfung erforderlich» angezeigt. Terminänderungen werden mit altem/neuem Wert nachgeführt; Änderungen und Löschen sind gegen konkurrierende Einstellungsänderungen abgesichert. Eingabe und Stream bleiben erhalten; Abschluss und Timerkoordination folgen FALL-11.

- **FD-AK-20:** Auf Höhe von «Bestätigte Zuordnung» öffnet «Ändern» mit Pfeil eine Bearbeitungsbox für Auswahlvorschläge oder Freitext. Solange keine bestätigte Zuordnung vorliegt, ist das Feld mit der ursprünglichen Mieterangabe vorbelegt; bei späteren Änderungen mit der aktuellen bestätigten Zuordnung. Die Vorbelegung allein bestätigt nichts. Auch die unveränderte ursprüngliche Angabe lässt sich bestätigen. Erst «Zuordnung bestätigen» übernimmt die serverseitig geprüfte Zuordnung, ohne weiteren Bestätigungsschritt. Auswahl und Freitext sind gleichwertig; beide werden nach Entfernen äusserer Leerzeichen auf 1–200 Zeichen geprüft. Die ursprüngliche Mieterangabe bleibt erkennbar; bestätigte Zuordnung, Mitarbeiter und Zeitpunkt sind nach FALL-AK-04 nachvollziehbar. UI2 und UI3 zeigen nach Aktualisierung denselben gespeicherten Stand. Fehler, ungültige Eingaben oder strukturierte Referenzen und konkurrierende Änderungen überschreiben keine gültige Zuordnung. Eingabe und aktiver KI-Stream bleiben erhalten. Ein gewünschter manueller Statuswechsel bleibt separat; Kontaktadresse und persönlicher Fall-Link bleiben unverändert.

- **FD-AK-21:** Manuelle aktive Statusänderung sowie Setzen, Ändern und Löschen der Wiedervorlage bieten «Begründung (optional, intern)» an. Jede Aktion wird mit und ohne Begründung geprüft. Nur die erfolgreich bestätigte Aktion speichert eine eingegebene Begründung mit Bezug zur Änderung, Mitarbeiter und Zeitpunkt; beim Löschen bleibt dieser Nachweis trotz entferntem Termin erhalten. Abbrechen, Fehler und Konflikte erzeugen weder Änderung noch gespeicherte Begründung; Wiederholungen erzeugen keinen doppelten Nachweis. Es entsteht kein separates Memo. Die Begründung ist nur intern lesbar und fehlt in Mieter-HTML, Datenantworten, E-Mails und Kommunikations-LLM-Kontext einschliesslich Ableitungen (KOM-AK-11). Abschluss und Wiedereröffnung behalten ihre bisherigen separaten Abläufe.

- **FD-AK-22:** Status, Dringlichkeit und Wiedervorlage stehen auf breiten Bildschirmen rechts neben dem Falltitel. Geschlossene Änderungskarten wiederholen die Werte nicht. Öffnen zeigt den gespeicherten Wert vorausgewählt; ungespeicherte Auswahl verändert den Kopf nicht. Abbrechen übernimmt nichts. Mieterblasen sind links angeordnet und linksbündig, Verwaltungs-/Systemblasen und Ereignisse rechts angeordnet und rechtsbündig. Alle Blasen sind gleich breit; Quelle, Rolle, Zeitpunkt und interne Kennzeichnung liegen vollständig darin. Personen-, Zahnrad- und Schloss-Icons, Rollen und Tooltips folgen Abschnitt 3 und sind per Tastatur sowie mit zugänglicher Beschriftung verständlich. Beide Eingabe-Toggles haben denselben Stil. Farblegende, Abschluss-Hilfstext und der Überschriftszusatz «ungespeichert» fehlen; fachliche Bestätigung und Veröffentlichung bleiben erforderlich.

- **FD-AK-23:** Bei Vergrösserung der Nachrichtentextbox auf breiten Bildschirmen bleibt ihre Breite konstant; der Eingabebereich wächst nach oben, der Verlauf wird bis auf mindestens 200 CSS-Pixel verkleinert und der Senden-Button bleibt sichtbar. An der Grenze scrollt nur der längere Text innerhalb der Textbox. Verkleinern gibt dem Verlauf Platz zurück, ohne Nachrichten oder Eingabetext zu verlieren. Auf kleinen Bildschirmen wächst die Textbox automatisch bis zur vorgesehenen Maximalhöhe und scrollt danach intern. Kopf und rechte Spalte bleiben unverändert; Aktionen und sichtbarer KI-Vorschlag werden bei der Höhenbegrenzung berücksichtigt.

## 8. Abgleich und verbleibende Konkretisierungen

Die grundlegende fachliche Bedienung von UI3 ist bestätigt. UI1 und UI2 behalten ihre bestätigten Funktionen; gemeinsame Kommunikations-, Status-, Zugriffs- und Dringlichkeitsregeln wurden mit den zentralen Spezifikationen sowie UC-003/UC-004 abgeglichen.

Folgende Punkte gehören zur anschliessenden Prozess- und technischen Ausarbeitung; sie sind keine erneute Abstimmung der bestätigten Bedienung:

- Weitere Übergänge zwischen aktiven Bearbeitungsstatus und Synchronisierung mit Camunda. Der direkte manuelle Wechsel nach Prüfung auf «In Bearbeitung» sowie die Wiedervorlage sind fachlich bestätigt; ihre technische Zuordnung zum geprüften Fallstand und die Unterscheidung neuer von bereits berücksichtigten Prüfgründen werden umgesetzt. Ein Memo allein bestätigt die Mitarbeiterentscheidung nicht.
- Wiedervorlage nach FALL-11: technische Zuordnung zum geprüften Fallstand, Tagesberechnung und Uhrzeit sowie Koordination konkurrierender Änderungen. Der zusätzliche Status, die sortierbare UI2-Spalte, Änderung und Löschung in UI3, Camunda-Timer und parallele Nachrichtenverarbeitung sind vereinbart.
- Umsetzung der bestätigten inhaltlichen Einzelprüfung offener Rückfragen und der Mitarbeiteraktion «Antwortbedarf aufheben» gemäss KOM-03, der Folgestatus nach vollständiger Beantwortung beziehungsweise manueller Aufhebung gemäss FALL-05 und des Endes offenen Antwortbedarfs beim erfolgreichen Fallabschluss gemäss KOM-04. Die zugehörigen fachlichen Regeln sind bestätigt; technische Koordination und Nachweise folgen bei der Umsetzung.
- Konfliktprüfung und atomare Speicherung, technische Versionierung, sicherer Umgang mit unklarem Speicherergebnis sowie Wiederholungen ohne Mehrfachwirkung.
- Zuordnung nach FALL-04: Stammdatenzugriff für Vorschläge und technische Umsetzung der bestätigten Zuordnungsänderung samt Konfliktprüfung und Nachvollziehbarkeit.
- Technischer Nachweis der Trennung bewusster Mitarbeitereingaben von systemseitig bereitgestelltem KI-Kontext nach KOM-02, einschliesslich Ausschluss automatischer Memo-Übernahme und systemseitiger Memo-Ableitungen; Streaming-/Abbruchvertrag und Koordination konkurrierender Aktionen.
- E-Mail-Vorlagen mit klar erkennbarem Antwortbedarf; die bereits zentral offene Behandlung der getrennten Benachrichtigungspflichten von Abschlussnachricht und Abschluss.
- Darstellungsdetails wie Popup-Verhalten bei mehreren Aktualisierungen, Fokusführung, genaue Scrollhöhe sowie konkrete Grafikdateien der festgelegten Icons. Das Verhalten bei manueller Textbox-Vergrösserung ist in Abschnitt 2 bestätigt; unter 1200 px gilt die bestätigte automatische Textboxhöhe von 96–240 px.

Die Erstposition des Verlaufs beim Öffnen bleibt gesondert zu konkretisieren. Das Zurücksetzen von «Mieterantwort erforderlich» beim Einschalten von «Intern» ist in [Abschnitt 3](#3-fachlich-verbindliche-inhalte) bestätigt. Die bestehenden Regeln zu Textbestand, externem Antwortbedarf und Zurücksetzen nach erfolgreicher Speicherung gelten verbindlich.

JPG-Entwürfe und eine lokale Frontend-Demo liegen vor. Produktive Service-, Speicher- und Integrationsverträge bleiben separat auszuarbeiten.

## 9. Ergänzungen zur Darstellung vom 12.10.2026

### Farbkreise im Verlauf

Gemäss [DRING-04](../specifications/dringlichkeitsbewertung.md#dring-04-farbkennzeichnung) und [DRING-07](../specifications/dringlichkeitsbewertung.md#dring-07-änderungshistorie-und-mietertransparenz): Dringlichkeitsänderungen im Verlauf zeigen wie in der JPG-Vorlage einen farbigen Kreis für die alte Stufe, einen Pfeil und einen Kreis für die neue Stufe. Blau = noch nicht bewertet, Rot = kritisch, Orange = hoch, Gelb = normal, Grün = niedrig. Der ausgeschriebene Änderungstext bleibt erhalten; die Kreise sind ergänzend und für Screenreader dekorativ. Die gemeinsame Verlaufsdarstellung gilt auch für freigegebene Dringlichkeitsereignisse in UI1.

### Kompakter Kopf und Speicherrückmeldung
 Case-ID und Falltitel stehen nebeneinander auf gemeinsamer Grundlinie; lange Titel dürfen umbrechen. Erfolgreiche Speicherrückmeldungen erscheinen als platzsparendes Toast-Popup unten rechts und verschwinden nach fünf Sekunden. Laufende Speicherung und Fehler werden ebenfalls dort angezeigt; Fehler bleiben bis zur nächsten Rückmeldung sichtbar. Die Rückmeldung ist per Live-Region zugänglich und belegt keinen Platz zwischen Kopf und Verlauf.

## Implementierungs- und Qualitätsnachweise

Die lokale Demo und ihre Startanleitung stehen in der [README](../../README.md#frontend-demo-ohne-datenbank). Das [Block-2-Prüfprotokoll](../ai-usage/block2-oberflaechen-pruefung.md#ergänzung-browser-qualitätsnachweis-und-präsentationsentscheidung) enthält ausgeführte Browser-, Streaming-, A11y- und Performance-Prüfungen einschliesslich Grenzen. Fachliche und produktive Prüfziele bleiben in der [Evaluationsgrundlage](../evaluation/evaluationsgrundlage.md) massgebend; die Demo-Nachweise ersetzen keine Produktivfreigabe.


### Berücksichtigte Prüfgründe

Gemäss [FALL-06](../specifications/fallverwaltung.md#fall-06-fachlicher-status-und-technischer-workflow) zeigt die Box «Prüfbedarf» nur offene Gründe. Nach dem Wechsel auf «In Bearbeitung» sowie nach Setzen, Ändern oder Löschen einer Wiedervorlage zeigt das zugehörige Änderungsereignis intern «Berücksichtigter Prüfgrund: …». Die optionale interne Begründung bleibt eine separate Angabe am selben Ereignis. In der Mieteransicht fehlen beide internen Angaben.

### Historische Rückfragen

Nach [KOM: historischem und aktuellem Antwortbedarf](../specifications/fallkommunikation.md#historischer-und-aktueller-antwortbedarf--präzisierung-vom-13102026) bleiben ehemalige Rückfragen als «Ursprünglich: Mieterantwort erforderlich» erkennbar. Daneben stehen Beendigungsgrund und Zeitpunkt. Nur aktuell offene Rückfragen zeigen die aktive Antwortaufforderung; Wiedereröffnung aktiviert ehemalige Rückfragen nicht erneut.

### Gelöschte Wiedervorlage im Verlauf

Das interne Löschereignis zeigt den bisherigen Termin und «keine Wiedervorlage» gemäss [FALL: Nachweis gelöschter Wiedervorlagen](../specifications/fallverwaltung.md#nachweis-gelöschter-wiedervorlagen--präzisierung-vom-13102026). Zeitpunkt und optionale interne Begründung bleiben am Ereignis sichtbar.

### Unveränderte Dringlichkeit speichern

Nach [DRING: unveränderter manueller Einstufung](../specifications/dringlichkeitsbewertung.md#unveränderte-manuelle-einstufung--präzisierung-vom-13102026) erzeugt erneutes Speichern derselben manuellen Stufe keinen Eintrag. Eine gleichbleibende Systembewertung kann manuell bestätigt werden: Der Kopf zeigt danach «Immobilienverwaltung» als Quelle; der Verlauf dokumentiert die Bestätigung ausschliesslich intern, ohne Stufenwechsel-Kreise.

### Korrektur F06 – Rückkehr zur Fallübersicht

Bestätigt und umgesetzt am 13.10.2026: Falllinks führen Suche, Dringlichkeitsfilter, Einblendung abgeschlossener Fälle, Sortierung, Richtung und die tatsächlich angezeigte Seite als Rückkehrziel mit. «Zur Fallübersicht» verwendet dieses Ziel auch nach gespeicherten Detailaktionen; der Ablauf funktioniert ohne JavaScript. Direkt geöffnete Details verwenden die Standardübersicht. Der Server akzeptiert ausschliesslich Rückkehrziele auf dem lokalen Listenpfad. HTTP-/Rendering-Tests prüfen Kontextübernahme, Speichern, Direktaufruf und das Zurückweisen externer/fremder Ziele.

### Korrektur F09 – Aktualisierungsfehler und verweigerter Zugriff

Am 13.10.2026 bestätigt: Bei fehlgeschlagenem Leseabruf bleiben letzter Stand und Eingaben erhalten; «Erneut versuchen» startet einen erneuten Abruf ohne parallele Anfrage. Nach erfolgreicher Detailaktualisierung verschwindet die Lesefehlermeldung. Bei ausdrücklicher Lesezugriffsverweigerung (HTTP 401/403) werden Falldaten ausgeblendet und weitere automatische/manuelle AJAX-Abrufe sowie Speichern gesperrt. Ungesendeter Nachrichtentext bleibt lesbar und kopierbar, ohne Veröffentlichung. Ein bewusster Seiten-Neuaufruf prüft den Zugriff erneut. Ein POST-403 allein wird nicht als Lesezugriffsverlust gewertet, da er auch einen ungültigen CSRF-Nachweis bedeuten kann. Testdateien des parallel arbeitenden Agenten bleiben unverändert.

### Korrektur F10 – Abbrechen nach zwischenzeitlicher Aktualisierung

Am 13.10.2026 bestätigt: Ein offener Editor behält seinen ursprünglichen Fallstand für die Konfliktprüfung. Eine zwischenzeitlich erfolgreich geladene Sidebar wird als aktueller Stand vorgemerkt. «Abbrechen» verwirft die Eingaben dieses Editors und ersetzt ihn durch den neuesten geladenen Stand, einschliesslich Version und vorausgewählten Werten. Andere eingeklappte Entwürfe werden nicht mitverworfen. Bei inzwischen abgeschlossenem Fall wird die Sidebar auf die zulässigen Abschlussaktionen umgestellt. Nachrichtentext, KI-Vorschlag und laufender Stream bleiben erhalten. Ohne neu geladenen Stand gilt weiterhin die bisherige Versionsprüfung. Testdateien des parallel arbeitenden Agenten bleiben unangetastet; Syntaxprüfung und Build-Ressourcenaktualisierung wurden ausgeführt.
