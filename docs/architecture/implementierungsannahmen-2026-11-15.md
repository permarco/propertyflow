# Implementierungsannahmen vom 15.11.2026

Arbeitsauftrag: selbständig weiter implementieren, keine beantwortungspflichtigen
Rückfragen stellen. Verbindliche Spezifikationen und akzeptierte ADRs haben Vorrang.
Diese Datei dokumentiert technische Ausgestaltungen; sie erteilt keine zusätzlichen
fachlichen Freigaben. Sie wird während der Umsetzung fortgeschrieben.

## Laufzeit und Datenhaltung

- PostgreSQL bleibt die dauerhafte Fachablage. Camunda 8.9 führt den Prozess und
  veranlasst Statusänderungen; der Backend-Prozess setzt keine eigenen fachlichen Timer.
- Bestehende Fallidentität und persönlicher Link bleiben bei Wiedereröffnung
  erhalten. Eine neue Bearbeitung erhält eine neue führende Prozessinstanz; der
  tatsächlich beendete Vorgänger und alle bisherigen Zuordnungen bleiben historisiert.
- Prozesse verwenden eine festgeschriebene Deployment-Definition und Business-ID.
  Automatisches Ausweichen auf eine unbekannte neueste Definition ist nicht vorgesehen.
- Native Aufgabenentscheidungen werden an konkreten Task-Key, Fallversion und
  Prüfgrundstand gebunden. Bei unbekanntem Erfolg gibt es keine automatische zweite
  Entscheidung. Lesende Statusabgleiche sind weiterhin zulässig.
- Terminänderungen sind eigene dauerhafte Aufträge an den aktuellen Wiedervorlage-
  Aufruf. Zwischen fachlichem Commit und bestätigter Engine-Übernahme der neuen
  Terminversion werden Kontakt-/Prüfmarker kurz zurückgehalten. Vor dem Commit
  haben neue Nachrichten und Prüfgründe weiterhin Vorrang und können den Auftrag
  ungültig machen. Unverändertes Datum erzeugt keine neue Terminversion/Mailpflicht.
- Eine Rückfrage aus «In Bearbeitung» beendet die konkrete native Aufgabe mit
  einem dauerhaft referenzierten Frageauftrag. Erst dessen geprüfte Veröffentlichung
  erlaubt den Reply-Aufruf. Ein veralteter Auftrag veröffentlicht nichts und führt
  zurück zur Prüfung. Die Veröffentlichung allein ändert den Status noch nicht.
- Jeder Reply-Aufruf besitzt eine eigene dauerhafte Kennung. Die Zustellung wird
  erst nach Initialisierung der Engine-Variablen freigegeben. Teilantworten verbrauchen
  ihren Ergebnisstand genau einmal; vor dem Eintritt gespeicherte Auswertungen werden
  nachgeliefert. Es gibt keine Antwortfrist und keine Erinnerung durch diesen Pfad.
- Zusätzliche Mitarbeiterrückfragen im Mieterwarten bleiben Nebenaktionen derselben
  Kindinstanz. Neue Antwortbedarfe machen laufende Auswertungen veraltet; nach einer
  bestätigten Fortsetzung dürfen alte Nebenaufträge nicht mehr veröffentlichen.
- «Antwortbedarf aufheben» verwendet einen eigenen Camunda-Nachrichteneingang und
  den vorhandenen dauerhaften Mitarbeiterauftragsvertrag. Aufhebung, Audit und eine
  gegebenenfalls erforderliche Fortsetzung werden gemeinsam gespeichert. Die
  Fortsetzung erhält eine eigene Kennung; nach dem Commit sind weitere Nebenaufträge
  des alten Reply-Aufrufs gesperrt. Ein neuer Prüfgrund wird am neuen Mitarbeitereintritt
  berücksichtigt. Der Vorgang ruft kein Modell auf und erzeugt keine Abschlussbehauptung.

## Zentrale Auswertung und Empfehlung

- Eine zentrale Auswertung umfasst den veröffentlichten externen Verlauf, offene
  Rückfragen, Dringlichkeit und Empfehlung. Gleiche Fall-/Analyseversionen teilen
  dieselbe Auswertung. Interne Memos, interne Begründungen, persönliche Links und
  Kontaktadressen gehören nicht in diesen Kommunikationskontext.
- Modellantworten werden strikt auf Schema, bekannte IDs, Reihenfolge und wörtliche
  Belegstellen geprüft. Diese Prüfung ersetzt keine semantische Modellevaluation.
- Für den noch nicht konkretisierten Wissensbestand aus UC-018 wird zunächst ein
  lokales Verzeichnis versionierter, ausdrücklich freigegebener JSON-Quellen
  unterstützt. Die erste Suche ist lexikalisch und benötigt keinen Vektordienst.
  Es gibt keinen automatischen Import privater Altfälle und keine Websuche nach
  vermeintlich verbindlichen Regeln.
- Ohne passende freigegebene Fachquellen bzw. ohne konfiguriertes Modell bleibt
  die Fallannahme möglich; die Auswertung führt nachvollziehbar zur menschlichen
  Prüfung. Es werden keine Fachregeln oder Quellen als freigegeben erfunden.
- Automatische Veröffentlichung und Systemabschluss benötigen weiterhin die
  konkreten Freigaben aus KOM-05/KOM-06 und FALL-07/FALL-10. Ein formal gültiges
  Modellresultat allein ist keine solche Freigabe.

## Prüfung und Betrieb

- Integrationsprüfungen verwenden ausschliesslich die isolierte lokale Testdatenbank
  auf Port 5434 und Testengine auf Port 8085. Entwicklungs-/Produktionsfälle bleiben
  unberührt. Test-Fixtures sind keine produktiven Ersatzprozesse.
- Kein Commit, Push oder produktives Deployment wird aus diesem Arbeitsauftrag
  abgeleitet. Die Implementierung und ihre Prüfberichte bleiben im Arbeitsverzeichnis.
- Sechs Originalmodelle bleiben bis zur vollständigen Bindung und Prüfung nicht
  ausführbar. Teilnachweise werden ausdrücklich von einer Gesamtabnahme unterschieden.


## Abschluss während Wiedervorlage (16.11.2026)

- Die bestehende Freigabe aus FALL-11 Übergang 22 wird mit einem eigenen,
  dauerhaft gespeicherten Abschlussauftrag zur konkreten Terminversion umgesetzt.
  Es wird keine nicht vorhandene native Benutzeraufgabe erfunden. Der WClose-Empfang
  korreliert an die aktuelle Wiedervorlage; WCloseTask prüft Berechtigung und Fallstand.
- Der Auftrag bleibt während der Rückgabe über die tatsächlich beendete Mitarbeiterinstanz
  in Bearbeitung. Erst der gemeinsame Abschlussdienst schreibt «Abgeschlossen» und
  beendet Termin sowie offene Antwortbedarfe. Die Herkunft bleibt explizit gespeichert
  (native Mitarbeiterentscheidung oder Wiedervorlage-Abschlussauftrag, Migration V30).
- Eine neue Nachricht, ein neuer Prüfgrund oder Rollenverlust zwischen Vorbereitung
  und Abschluss führt zur aktuellen Mitarbeiterprüfung. Terminaufhebung, abgelehnter
  Auftrag und neue Prüfzuordnung werden gemeinsam gespeichert. Bereits unterwegs
  befindliche Unterbrechungen dürfen keinen blockierenden Abschlussauftrag hinterlassen.
- Ein unbekanntes Korrelationsergebnis wird nicht automatisch erneut gesendet.
  Wiederholungen derselben Operation lesen ihren gespeicherten Stand. Die Oberfläche
  verlangt eine ausdrückliche Abschlussbestätigung und übernimmt den Status erst nach
  bestätigter Rückmeldung aus dem Backend.


## Katalogfragen im initialen Automatikpfad (16.11.2026)

- Die bestätigte KOM-05-Fassung vom 14.11.2026 wird als `KOM-05-2026-11-14`
  versioniert. Veröffentlichbar sind ausschliesslich die vier dort wortgleich
  freigegebenen Texte. Für mehrere ausgewählte Fragen entsteht je eine Nachricht
  mit eigenem Antwortbedarf, damit Teilantworten unabhängig ausgewertet werden können.
- Der Entscheidungsnachweis bindet Auswahl, Regelgrundlage und Veröffentlichung an
  die zentrale Auswertung, führende Instanz und konkrete automatische Kindinstanz.
  RAG-/Modelltexte werden nicht als Nachricht übernommen. Eine erfolgreiche frühere
  Veröffentlichung wird auch nach verloren gegangener Worker-Bestätigung nicht dupliziert.
- Die bestehende Nachrichtentabelle wird aus Kompatibilitätsgründen weiterverwendet.
  Eine explizite Herkunftsbedingung unterscheidet Mitarbeiterauftrag und Systembatch;
  Systemnachrichten erhalten keinen erfundenen Mitarbeiter als Urheber. Beide verwenden
  denselben öffentlichen Verlauf, Antwortbedarfs- und Benachrichtigungsvertrag (V31).
- Bereits gespeicherte Katalog-IDs sowie identische manuelle Fragetexte sperren eine
  erneute Veröffentlichung dauerhaft, auch bei beantwortetem oder aufgehobenem Bedarf.
  Relevanz, bekannte Angaben und sinngleiche freie Formulierungen bleiben semantische
  Aufgaben der Empfehlung und ihrer Evaluation; exakte Prüfungen beweisen sie nicht.
- Im initialen Automatikpfad sind Veröffentlichung und anschliessendes Mieterwarten
  verbunden. Neue zusätzliche Katalogvorschläge aus einer späteren Reply-Auswertung
  benötigen noch die gesonderte QUESTION-Wiedereintrittsanbindung und führen bis dahin
  nachvollziehbar zur Prüfung. Bereits bekannte Vorschläge allein tun dies nicht.
- Ein aufgehobener letzter Antwortbedarf darf auch zwischen Automatikende und
  Reply-Registrierung nicht verloren gehen. Dauerhafte Marker adressieren hierfür
  den konkreten automatischen Aufruf beziehungsweise vorbereiteten Reply-Eintritt.
  Ein späterer manueller Frageauftrag besitzt eine andere Referenz.
- Benachrichtigungen verlangen nur dann ausdrücklich eine Antwort, wenn der eigene
  Antwortbedarf beim Laden der Versanddaten noch offen ist. Ein inzwischen aufgehobener
  Bedarf oder abgeschlossener Fall wird nicht durch einen verspäteten Mailauftrag reaktiviert.


## Weitere Katalogfragerunden aus Reply (16.11.2026, V32)

- Ein gültiger QUESTION-Entscheid aus dem tatsächlich beendeten Reply-Kind erlaubt
  einen neuen Automatikaufruf. Er erteilt keine dauerhafte Veröffentlichungsfreigabe.
  Das neue Kind verwendet die gemeinsame aktuelle Auswertung; bei unverändertem
  Datenstand wird das vorhandene Ergebnis wiederverwendet, bei Änderungen neu geprüft.
- Die bisherigen Eintrittsdatensätze bleiben für Herkunftsnachweise erhalten. Ein
  eindeutiger aktueller Eintritt je führendem Durchlauf ersetzt die frühere Annahme
  genau eines Automatikaufrufs pro Durchlauf. Alte Aufrufe sind für weitere Wirkungen gesperrt.
- QUESTION behält den Status «Wartet auf Mieterantwort» bei. Es entsteht weder eine
  zweite Statusänderung noch eine zweite Statusmail allein durch den Aufrufwechsel.
  Neue Katalogfragen erhalten weiterhin einzelne Nachricht-/Antwort-/Mailpflichten.
- Prüfbedarf und vollständige Beantwortung haben Vorrang vor weiteren Fragen.
  Neue notwendige Katalogfragen sind nur bei verbleibendem Antwortbedarf zulässig.
  Bereits bekannte oder aufgehobene Fragen werden nicht erneut veröffentlicht.
- Pflichtprüfbedarf und manuelles Aufheben des letzten Bedarfs können auch das neue
  Automatik-Kind unterbrechen. Der unveränderte Wartestatus allein bestimmt deshalb
  nicht mehr den Empfangsbereich; der aktuelle registrierte Call wird mitgeprüft.
- Dieser Abschnitt ersetzt die vorangehende vorläufige Beschränkung zusätzlicher
  Katalogvorschläge auf Mitarbeiterprüfung. Individuelle Abschlussnachrichten und
  Systemabschluss bleiben getrennte, noch nicht freigegebene Umsetzungswege.


## Direkte Mitarbeiterübernahme aus Abklärung (16.11.2026, V33)

- «Bearbeitung übernehmen» verwendet einen eigenen lokalen Camunda-Nachrichteneingang,
  den bestehenden dauerhaften Mitarbeiterauftragsvertrag und eine explizite Bestätigung
  in der Oberfläche. Die aktuelle Rolle, Fallversion, führende Instanz und Automatikreferenz
  werden vor Speicherung erneut geprüft. Unklare UI-Antworten werden nur lesend abgeglichen.
- Der Übernahmecommit speichert den Zielstatus, internen Akteurnachweis, Mailpflicht und
  Mitarbeiterfortsetzung zusammen und sperrt alte automatische Wirkungen. Ohne Pflichtprüfung
  entsteht SWork, andernfalls SReview. Offene Fragen und manuelle Dringlichkeit bleiben erhalten.
- Eine bereits vorbereitete oder beanspruchte Prüfungsfortsetzung desselben automatischen
  Aufrufs wird übernommen; es entsteht keine zweite Mitarbeiterfortsetzung. Ein inzwischen
  eingetretener Prüfgrund kann vor der Kindregistrierung denselben Eintritt auf REVIEW ändern.
- Der bestätigte Entscheid wird dauerhaft an den konkreten automatischen Aufruf zugestellt.
  Zusätzliche Übergabe-Randereignisse an AutomaticResult und Reply verhindern eine Lücke
  nach Automatikende beziehungsweise vor der Registrierung der Mieterwartephase.
- Neue Prüfmarker werden bei der Mitarbeiterregistrierung nicht pauschal geleert. So bleibt
  ein Prüfgrund erhalten, der während der Registrierung an die Kindinstanz zugestellt wurde.
- Die Übernahme gilt hier für «In Abklärung». Weitere direkte Aktionen während der
  Mieterwartephase und direkte Wiedervorlagen werden durch diesen Abschnitt nicht freigegeben.


## Manuelle Rückfrage während Abklärung (16.11.2026, V34)

- Der vorhandene Nachrichtenauftrag erlaubt nun Antwortbedarf aus «In Abklärung».
  Nachricht, Antwortbedarf, Zielstatus, Statusmail und Sperre der alten Automatik
  werden in derselben Transaktion gespeichert. Die spätere Reply-Registrierung
  erzeugt keine zweite Statusänderung oder Mailpflicht.
- Vor Veröffentlichung vorhandener Pflichtprüfbedarf verhindert die Rückfrage.
  Nach Veröffentlichung eingetretener Prüfbedarf erhält die Frage und führt
  denselben Hauptpfad zur Mitarbeiterprüfung. Ein Abbruch macht keine Nachricht rückgängig.
- Ein eigener Herkunftsnachweis erlaubt das tatsächlich beendete oder unterbrochene
  Automatik-Kind als Quelle. Alte reguläre Automatik-Eintritte dürfen den manuell
  vorbereiteten Mieterwarte-Eintritt nicht verdrängen.
- Aufheben des letzten Bedarfs funktioniert auch vor der Reply-Registrierung.
  Noch nicht zugestellte Prüfgründe und kritische Dringlichkeit haben Vorrang;
  vor der Mitarbeiterregistrierung neu entstandener Bedarf aktualisiert denselben Eintritt.

## Direkter Mitarbeiterabschluss während Abklärung (16.11.2026, V35)

- Die ausdrückliche UI-Bestätigung bindet Fallversion und Prüfgrundstand. Der
  serverseitige Auftrag enthält Mitarbeiter, führenden Durchlauf und Operationskennung.
  Neue Prüfgründe ohne zugeordnete Instanz gehören bereits zur Entscheidungsgrundlage;
  ihre spätere technische Zustellung darf die fachliche Kenntnis nicht vorwegnehmen.
- Der bestehende Abschlussdienst und dieselbe Abschlussroutine speichern auch diesen
  Eingang. Es gibt keine erfundene native Mitarbeiteraufgabe. Der Auftrag darf nur aus
  «In Abklärung» wirken. Änderungen bis zum Commit führen zur Ablehnung ohne Abschluss.
- Nach Commit sind alte fachliche Wirkungen gesperrt. Der Camunda-Worker liefert den
  gespeicherten Erfolg bei Wiederholung erneut; erst dessen Eskalation beendet den
  gesamten ActiveCase-Bereich. Ein alter Auftrag gehört ausschliesslich zum alten Durchlauf.
- Offene Antwortbedarfe erhalten CASE_CLOSED. Bereits veröffentlichte Nachrichten und
  frühere Mailpflichten bleiben bestehen; der Abschluss erzeugt keine eigene Mail.
- Unklarer HTTP-Ausgang wird ausschliesslich lesend abgeglichen. Ein aktuellerer
  Fall-/Prüfgrundstand wird niemals stillschweigend als neue Zustimmung übernommen.


## Abgleich überholter Mitarbeiterfortsetzungen (16.11.2026)

- Ein früher bereits beanspruchter, inzwischen abgelöster Eintritt darf beim Retry
  nicht nochmals START erhalten. Vor eigener Wiederholung, Registrierung und
  Prüfstatusänderung muss die aktuelle Eigentümerschaft feststehen.
- SUPERSEDED und EXISTING benötigen einen tatsächlich aktiven Mitarbeiter-Kindprozess
  und dessen aktiven Staff-Call mit passendem Elternprozess und Tenant. Eine reine
  Datenbankreservierung oder ein noch unbestätigter Mitarbeitereintritt genügt nicht.
- Vor Ende des redundanten Pfads wird der Eigentümer erneut unter Fallsperre geprüft.
  Noch nicht fachlich berücksichtigte Prüfgründe des alten Eintritts werden dauerhaft
  dem aktuellen Eintritt zugewiesen und über den vorhandenen Prüfbedarfseingang zugestellt.
  Bereits berücksichtigte Gründe behalten ihren historischen Entscheidungsbezug.
- Ist noch kein bestätigter Mitarbeiter-Nachfolger vorhanden oder hat der Fall bereits
  abgeschlossen, wird kein normales Ende freigegeben. Der Job bleibt technisch zu klären;
  Abschluss und andere Prozessphasen benötigen ihre jeweiligen eigenen Nachweise.


## Direkter Mitarbeiterabschluss während Mieterwarten (16.11.2026, V36)

- Der explizite Auftrag wird an genau den aktuellen registrierten Reply-Call gebunden.
  Die Annahme reserviert diesen intern als CLOSING; dies ist kein neuer fachlicher Status.
  «Wartet auf Mieterantwort» und offener Antwortbedarf bleiben bis zum Abschlusscommit erhalten.
- Reservierung, Sperre weiterer Reply-Wirkungen und dauerhafte Zustellpflicht liegen
  in derselben Transaktion. Neue Rückfragen, Antwortaufhebungen, Auswertungsentscheidungen
  oder Reply-Registrierungen können diesen Call danach nicht mehr verdrängen.
  Mieternachrichten und Falldatenergänzungen bleiben möglich und entwerten gegebenenfalls
  die frühere Zustimmung. Ein Ausfall zwischen Reservierung und Markerzustellung wird nachgeholt.
- Der Marker vergleicht Call und Eintritt. Erst die tatsächliche Unterbrechung von Reply
  führt zum gemeinsamen Close-Worker. Er erwartet bei dieser Herkunft TERMINATED statt
  eines regulär beendeten Mitarbeiter-Kindes und liest den dauerhaften Mitarbeiterauftrag.
- Vor Abschluss werden Rolle, führender Durchlauf, Version und Prüfgrundstand erneut
  geprüft. Später Konflikt erzeugt eine aktuelle Mitarbeiterprüfung, erhält die offenen
  Fragen und speichert keinen Abschluss. Früh abgelehnte Aufträge lassen den Wartepfad bestehen.
- Abklärung und Mieterwartephase verwenden denselben Bestätigungsdialog mit getrennten
  serverseitigen Eingängen. Die UI zeigt die Reservierung als ausstehenden Auftrag,
  niemals als bereits gespeicherten Abschluss. Unklare Antworten werden nur lesend abgeglichen.

## Direkte Wiedervorlage (16.11.2026, V37)

- Der Auftrag speichert die ursprüngliche Datumseingabe, den einmalig berechneten
  absoluten Zeitpunkt, den bestätigten Prüfgrundstand und die optionale interne
  Begründung. Eine verspätete Verarbeitung berechnet keine neue Frist.
- Termin, Statusereignis, Mailpflicht, Berücksichtigung bekannter Prüfgründe und
  Sperre des abgelösten Automatik-/Reply-Aufrufs werden gemeinsam gespeichert.
  Die zusätzliche Berücksichtigungsreferenz trennt diese Entscheidung von späteren
  Prüfgründen. Sie wird in allen bestehenden Abfragen zu offenen Gründen berücksichtigt.
- Vor Registrierung des Mitarbeiterkindes darf die Terminversion bereits bestehen.
  Der noch fehlende Kindbezug ist kein zweiter Timer: erst der verifizierte Eintritt
  bindet Mitarbeiter- und Wiedervorlage-Kind. Ein früher Kontakt oder neuer zwingender
  Grund hebt den Termin auf und führt direkt zur Prüfung.
- Während der Übergabe sind Terminänderungen und Rückfragen erst verfügbar, wenn
  ihr konkreter Prozessadressat registriert ist. Für einen direkten Auftrag aus
  Mieterwarten wird derzeit ein registrierter WAITING-Reply-Aufruf verlangt.
- Bestehende Rückfragen bleiben offen. Die bekannte Mitarbeiterentscheidung gilt
  ausschliesslich für das Warten und ist keine Freigabe für Kosten oder Abschluss.
- Der neue UI-Eingang verwendet Datum oder Kalendertage, interne Begründung,
  ausdrückliche Bestätigung und ausschliesslich lesenden Abgleich unklarer Antworten.
  Die aktuellen Laufzeitnachweise werden im Prüfbericht getrennt aufgeführt.

## Fortsetzungsabgleich mit Reply und Automatik (16.11.2026)

- Ein alter Mitarbeiter-Claim kann inzwischen einem aktuellen Reply oder einem
  neuen Automatikaufruf gegenüberstehen. Ein gespeicherter Nachfolger allein genügt
  nicht: der Worker prüft dessen tatsächlich aktives Kind, aktive Call Activity,
  Elternbezug und Tenant. Der Dienst prüft denselben Eigentümer nochmals unter Fallsperre.
- Noch nicht berücksichtigte Gründe gehen in den dauerhaften Prozesseingang zurück.
  Der reguläre Dispatcher erreicht damit den aktuellen Empfänger; eine weitere
  Mitarbeiteraufgabe wird durch den alten Pfad nicht erzeugt. Bereits berücksichtigte
  Gründe bleiben berücksichtigt. Wiederholte Übergabe erzeugt keinen zweiten Grund.
- Ein zwischen Prüfung und Übergabe geänderter Eigentümer führt zu technischem Retry.
  Ein bereits reservierter Reply-Abschluss (CLOSING) wird nicht als aktiver Empfänger
  freigegeben. Abschluss und dessen Konfliktpfad behalten die Führung.

## Vollständige BPMN-Quellen im Integrationstest (16.11.2026)

- Die vollständigen sieben Quelldateien werden testweise gemeinsam bereitgestellt.
  Nur in der Testkopie werden die Ausführbarkeitsflags gesetzt und die vier noch
  offenen automatischen Abschlussaufgaben mit unerfüllten Test-Jobtypen versehen.
  Diese Aufgaben werden in den geprüften Mitarbeiterpfaden nicht ausgeführt.
- Die Prüfung ersetzt weder die Implementierung des automatischen Abschlusses noch
  die semantische Modellevaluation. Die Quelldateien behalten ihre bisherigen Flags.
- XML-Dokumentation muss vor Erweiterungen stehen; Prozessartefakte nach den
  Flusselementen. Die Korrektur erhält die Diagrammdaten. Erstanlage verwendet
  ausdrücklich INITIAL. Der bestehende S13-Pfad verlangt ausdrücklich REVIEW;
  unbekannte Entscheidungen erhalten keinen Default. Die Anwendung bietet keine
  zusätzliche REVIEW-Aktion an: neue Pflichtgründe verwenden das vorhandene
  aufrufgebundene Randereignis. SExternalWait ist eine native Camunda-User-Task.

### Ergänzter Abschlussprüfpfad (16.11.2026)

Die Abschlussgrundprüfung ist als eigener Vertrag im Empfehlungsmodul vorbereitet.
Sie erteilt selbst keine Status- oder Veröffentlichungsfreigabe. Allgemeine
RAG-Freigabe wird ausdrücklich von der zusätzlichen Freigabe einer Abschlussregel
getrennt. Regeln benötigen aktuell einen ausdrücklichen Geltungszeitraum, mindestens
eine Voraussetzung und mindestens einen Ausschlussumstand; fehlende Angaben werden
nicht positiv ergänzt. Die lokale Modellrevision qwen3.5:4b wird ausschliesslich
mit synthetischen Fällen evaluiert. Ein positives Prüfergebnis allein aktiviert
keine automatische Falländerung. Der Evaluationsbestand und die Einschränkungen
stehen in docs/evaluation/automatischer-abschluss.md.

### Gemeinsame Abschlussbewertung und sichere Fortsetzung (16.11.2026)

- Der Abschlussvorschlag wird innerhalb derselben versionierten zentralen Auswertung
  ermittelt und gespeichert. Dafür genügen deren bestehende JSON-Persistenz und
  Versuchssperre; eine weitere Tabelle oder eine unabhängige Abschlussanalyse entfällt.
  Frühere Auswertungen besitzen keinen Abschlussvorschlag und bleiben unverändert gültig.
- Die Versuchssperre beträgt nun vier Minuten: drei auf höchstens eine Minute
  begrenzte Modellaufrufe benötigen zusätzlich Zeit für Einplanung und Speicherung.
  Externe Aufrufe halten weiterhin keine Datenbanktransaktion offen.
- Aufgrund des fehlgeschlagenen echten Modellnachweises wird aus keinem positiven
  Vorschlag eine automatische Wirkung freigegeben. Der vorhandene menschliche
  Prüfpfad übernimmt den Fall; alte Mitarbeiterentscheidungen werden nicht verdrängt.
  Ein isolierter Fehler der Abschlussbewertung verwirft keine gültigen Antworten
  aus der zentralen Analyse, verlangt aber menschliche Prüfung.
- Promptfassung closure-ground-v2 präzisiert die bereits spezifizierten Grenzen:
  alle Teilprobleme berücksichtigen und unveränderte zusammenhängende Belegstellen
  mit der tatsächlichen Nachrichten-ID liefern. Sie ist keine neue fachliche Regel
  und keine Freigabe. Der frühere Modellbericht bleibt als fehlgeschlagener Nachweis erhalten.

### Explizite Zusammenführungen der Prozessalternativen (16.11.2026)

- Die drei veralteten Terminaufträge kehren über das bestehende zusammenführende
  XOR zur nächsten Ereigniswartephase zurück. Ihre Linien laufen ausserhalb des
  dargestellten Nebenprozesses; alle bestehenden Knotenpositionen bleiben erhalten.
- Vor ClearAnswerContinue und AutomaticInterruptionPrepare bündeln zwei eigene
  XOR-Gateways die alternativen Unterbrechungswege. Worker, Auftragsreferenzen,
  Bedingungen und bestehende Zielaufgaben ändern sich dadurch nicht. Die elf
  Diagramme des Hauptprozesses bleiben erhalten. Eine automatische Strukturprüfung
  kontrolliert die getrennte Zusammenführung für alle sieben Originalmodelle.

### Anzeige der Abschlussbewertung

- Die bereits vorhandene Empfehlungsanzeige der Mitarbeiteransicht erhält die
  Abschlussbewertung als zusätzlichen ausdrücklich zu prüfenden Hinweis mit
  genannten Belegstellen. Das ist keine neue Abschlussaktion oder Freigabe.
  Der bestehende Hinweis auf einen früheren Fallstand gilt für die gesamte
  Empfehlung; technische Modell-/Prüfkennungen bleiben im internen Nachweis.


### Technische Abschlussimplementierung bei weiterhin gesperrter Freigabe (16.11.2026, V38)

- Der Benutzerauftrag trennt Implementierung und fachliche Betriebsfreigabe ausdrücklich.
  Alle vier Abschlussaufgaben werden deshalb angebunden, obwohl die Modellevaluation
  nicht bestanden ist. Die Anwendung gibt mit AutomaticClosureRelease weiterhin
  ausnahmslos false zurück. Kein Konfigurationsschalter und kein LLM-Feld kann dies aufheben.
- Eine spätere Freigabe erfordert einen geprüften Nachweis für Modellrevision,
  Modelloptionen, Bewertungs-/Mitteilungsprompts, Validatoren und Regelbestand.
  Synthetische Infrastrukturtests sind ausdrücklich kein solcher Nachweis.
- Die Inhaltsprüfung der individuellen Mitteilung verwendet zusätzlich zur formalen
  Prüfung einen zweiten begrenzten Modellaufruf im selben Worker. Dies ist eine
  technische Umsetzung der vereinbarten Inhaltsprüfung, keine neue Mitarbeiterfreigabe
  oder BPMN-Aufgabe. Die fachliche Evaluation dieses Prüfers steht noch aus.
- Beide Kommunikationsaufrufe erhalten ausschliesslich öffentliche Nachrichten,
  die belegte Abschlussgrundlage und eine gegebenenfalls aktuell gültige Fachregel.
  Es gibt keine feste Antwortvorlage. Interne Memos und deren Ableitungen bleiben ausgeschlossen.
- Regelkennung und Version bezeichnen eine unveränderliche fachliche Fassung.
  Inhaltliche Änderungen einer freigegebenen Regel müssen eine neue Version erhalten.
  Die aktuelle Gültigkeit und Vollständigkeit der Belege werden vor den Wirkungen erneut geprüft.
- Die drei Minuten gültige Generierungsreservierung deckt zwei auf je höchstens
  eine Minute begrenzte Modellaufrufe und Commit-Zeit ab. Eine erneuerte Versuchs-ID
  macht ein verspätetes Ergebnis wirkungslos. Externe Aufrufe halten keine Fallsperre.
- Ein Abschlussversuch referenziert eine Auswertung. Die Veröffentlichung besitzt
  davon unabhängig einen stabilen Schlüssel aus Fall, führendem Durchlauf, Grund,
  Regelreferenz und sortierten Belegen. Dieselbe Grundlage nach erneuter Auswertung
  verursacht keine zweite Nachricht/Mailpflicht. Geänderte Belege bilden eine neue Grundlage.
- Die eigene Veröffentlichung erhöht Fall- und Analyseversion; diese erwartete Änderung
  wird separat erfasst. Eine weitere Änderung verlangt aktuelle Auswertung. Bereits
  veröffentlichte Texte bleiben erhalten und werden bei einem Konflikt nicht zurückgenommen.
- CLOSE aus der Mieterantwortprüfung erzeugt wie QUESTION einen neuen aktuellen
  Automatik-Eintritt. Status und Fallidentität bleiben erhalten. Die gespeicherte
  Entscheidung erlaubt nur diesen Einstieg, keine spätere Veröffentlichung ohne erneute Prüfung.
- V1–V37 bleiben unverändert. V38 ergänzt die Herkunfts-/Versionsnachweise und
  erlaubt Systemabschlüsse im gemeinsamen Abschlussspeicher mit leerer Mitarbeiter-ID.
  Die beiden Abschlussquellen teilen dieselbe Bereinigung und Nachrichtensperre.
- Die früher in diesem Dokument beschriebenen vier Test-Platzhalter sind seit V38
  entfernt. Die vollständigen Modelle werden mit echten Bindungen geprüft; ihre
  unveränderten produktiven Ausführbarkeitsflags erteilen weiterhin keine B10-Freigabe.

- Angenommene, noch nicht verarbeitete Mitarbeiteraufträge für Falldaten oder externe
  Nachrichten haben auch vor dem fachlichen Commit Vorrang. AutoAnswer/AutoClose
  warten auf ihre Verarbeitung. Eine blosse Reservierung erzeugt keinen neuen
  Prüfgrund; nach Verarbeitung gelten wieder aktuelle Version und Status.


## Ergänzung vom 16.11.2026: DB-Initialskripte pro Modul

- Die nachträgliche Präzisierung «eine Datei für recommendation, eine für triage usw.»
  ersetzt den Wunsch nach einer einzigen gemeinsamen Initialdatei. Es gibt deshalb
  eine Initialdatei je bestehendem Modul mit backendweit eindeutigen Versionen V1–V5.
- Die Aufteilung folgt der aktuellen Persistenzverantwortung: `access` besitzt zwei
  Tabellen, `caseprocessing` 47. Die drei übrigen Module haben derzeit keine eigenen
  SQL-Tabellen; ihre Dateien erläutern diesen Stand ohne künstliche Tabellen.
- «Bestehende Entwicklungsdaten erhalten» umfasst auch die originale Flyway-Historie
  und ihre Prüfsummen. Die bisherigen 38 Dateien bleiben bytegleich im Legacy-Pfad.
  Die Auswahl geschieht beim Backend-Start anhand der ursprünglichen V1-Historie.
- Nur die ausdrücklich freigegebene tmpfs-Testdatenbank auf Port 5434 wird neu erstellt.
  Entwicklungsdatenbanken und persistente Volumes werden nicht verändert.
- Weitere Änderungen beginnen gemeinsam mit V39, damit beide Historien ohne
  Versionskonflikt fortgesetzt werden können. Es werden keine angewendeten
  Migrationen nachträglich umgeschrieben oder automatisch repariert.
- Diese Strukturänderung erteilt keine Freigabe für automatische Abschlüsse.
  Die bestehende Evaluationssperre bleibt unverändert aktiv.

Details: [Datenbankmigrationen nach Modulen](datenbankmigrationen.md).

### Folgeauftrag: Umstellung der bestehenden Entwicklungsdatenbank

- Die ausdrückliche Freigabe umfasst eine einmalige Änderung der Flyway-Historie,
  sofern alle Fachdaten erhalten bleiben. Dies erlaubt keine automatische Reparatur
  beim Anwendungsstart. Das separate Wartungswerkzeug sichert und restauriert den
  Bestand und probt die Umstellung an der Kopie vor der Änderung am Original.
- Eine vorhandene H2-Demo wird nicht als PostgreSQL-Backend-Datenbank interpretiert.
  Ein lokaler PostgreSQL-Dienst ohne zuordenbare Verbindung und Zugangsdaten ist
  ebenfalls keine ausreichende Grundlage für eine Änderung seiner Datenbanken.
- Das konkrete Backend-Entwicklungsziel war am 16.11.2026 hier nicht zugänglich:
  Container/Port 5433 und Backend-Konfiguration fehlen; Port 5432 verlangt hier
  nicht hinterlegte Zugangsdaten. Deshalb wurde keine reale Historie umgestellt.
- Die beauftragte Entfernung der Legacy-Skripte folgt erst nach der tatsächlichen
  Bestandsumstellung. Erfolgreiche synthetische Tests reichen dafür nicht aus.
  Bis dahin bleibt die bestehende Kompatibilität unverändert erhalten.

Werkzeug und Voraussetzungen: [Bestandshistorie umstellen](../../dev/database/README.md).


### Verbindliche Korrektur vom 16.11.2026: Hard Reset ohne Sicherung

- Die neue ausdrückliche Benutzerentscheidung verwirft den bisherigen Datenerhalt:
  Es handelt sich um nicht mehr benötigte Testdaten; eine Sicherung ist nicht gewünscht.
- Deshalb entfallen die 38 Legacy-Skripte, die automatische Legacy-Auswahl sowie
  das vorbereitete Historienumstellungswerkzeug einschliesslich Manifest und Tests.
  Die vorstehenden Annahmen zur Aufbewahrung dieser Kette sind damit überholt.
- Zurückgesetzt werden die eindeutig zugeordneten tmpfs-Testcontainer für PostgreSQL
  (5434), Camunda (8085/26505) und Mailpit (1027/8027). PostgreSQL und Prozesszustände
  werden gemeinsam erneuert; alte Testinstanzen sollen nicht auf gelöschte Fälle zeigen.
- Das bisher fehlende Entwicklungs-Backend wird auf dem vorgesehenen Port 5433
  mit eigener leerer Datenbank `propertyflow` und privater lokaler Konfiguration
  eingerichtet. Zugangsdaten und Link-Schlüssel werden neu erzeugt und nicht versioniert.
- Der nicht zuordenbare PostgreSQL-Dienst auf 5432 und die getrennte Frontend-Demo
  werden nicht als Ziel geraten. Ihre Daten gehören nicht zur identifizierten Backend-Testumgebung.
- Es gibt nur noch die fünf Modulinitialdateien. Neue Änderungen beginnen mit V6;
  die alte Reservierung bis V38 entfällt. Es gibt keinen automatischen Reset im Backend.
- Frühere, ausschliesslich synthetische Sicherungen der Umstellungsprobe werden nach
  Prüfung ihres Testdatenbanknamens entfernt. Für diesen Reset wird keine Sicherung erstellt.
- Die Sperre automatischer Abschlüsse bleibt unverändert bestehen.


## Ergänzung vom 16.11.2026: fachliche Abschluss-Evaluation

- Der ursprüngliche 24-Fälle-Bestand bleibt unverändert. Ergänzende Fallverläufe,
  synthetische Regeln und Kommunikationsentwürfe erhalten eigene versionierte Dateien.
  Sie sind Entwicklungsregressionen, keine unabhängige statistische Validierungsmenge.
- Die Regel `EVAL-PRIVATE-TIMER/fixture-v1` ist ausschliesslich eine Testannahme zur
  Prüfung von Voraussetzungen und Ausnahmen. Sie wird nicht als produktive Quelle
  gespeichert und begründet keine tatsächliche Zuständigkeitsentscheidung.
- Bei kontrollierten Kommunikationsentwürfen ersetzt die Testvorgabe nur die
  Generierung; die Inhaltsprüfung erfolgt über das echte lokale Modell und den
  unveränderten produktiven Kommunikationsdienst. Getrennte Fälle prüfen beide
  realen Modellaufrufe einschliesslich individueller Generierung.
- Technische Ausfälle, ungültiges JSON und falsche Entwurfsdigests gelten nicht als
  erfolgreiche fachliche Ablehnung. Formale Textgrenzen werden separat ausgewiesen.
- Freie Begründungen der Abschlussbewertung sind keine bestätigten Fakten. An die
  Kommunikationsschritte gehen nur öffentliche Nachrichten, Abschlussgrund,
  Regelreferenz und exakte Belege; keine freie Bewertungsbegründung.
- Auch vollständig grüne kleine synthetische Bestände schalten die Anwendung nicht
  selbständig frei. Produktive Regeln und eine dokumentierte Freigabe der gesamten
  tatsächlich verwendeten Modellkonfiguration bleiben eigenständige Voraussetzungen.


### Mehrere Belege je Abschlusskriterium

FALL-07 verlangt vollständige Belege, aber nicht genau einen Beleg pro Kriterium.
Bei mehreren Teilproblemen können getrennte Mieternachrichten dasselbe Kriterium
belegen. Die bisherige Beschränkung auf genau vier Einträge bei RESOLVED war eine
unnötige technische Einschränkung. Ab `closure-ground-validation-v3` bleiben alle
Pflichtkriterien erforderlich; bis zu 64 unterschiedliche Originalbelege sind
zulässig. Identische Belegtripel, unbekannte Kriterien, fehlende Pflichtkriterien
und nicht vorhandene Originalzitate bleiben ungültig. Bei NONE/REVIEW bleibt die
Belegliste leer. Die Versionsänderung verhindert die Übernahme alter Bewertungen
als aktuell geprüfte Ergebnisse. Die Veröffentlichungsbasis sortiert Belege nach
Kriterium, Nachrichten-ID und Zitat, damit ihre Reihenfolge keinen Zweitversand erzeugt.


### Getrennte Modellaufträge und gebundene Ausgabeschemata

- Ohne freigegebene Regel wird nur die vollständige Behebung geprüft. Mit Regeln
  bleiben tatsächliche Behebung und regelgestützter fehlender Verwaltungsbedarf
  alternative Gründe; nicht anwendbare Regeln verhindern keine belegte Behebung.
- Generierung und Inhaltsprüfung erhalten unterschiedliche Systemanweisungen.
  Die serverseitige Operation bestimmt die Rolle, nicht Text in einer Mieternachricht.
- Für die OpenAI-kompatible Kommunikationsanbindung wird ein natives JSON-Schema
  verwendet. Grund beziehungsweise Entwurfsdigest werden darin auf die serverseitig
  bekannten Werte beschränkt. approved bleibt ein frei zu bewertender Boolean.
  Das Schema bestätigt keine Fakten und erteilt keine Abschlussfreigabe.
- Die Dienstprüfung von Text, Abschlussgrund, Boolean und exaktem Digest bleibt
  unverändert erforderlich. Fehlende Schema-Unterstützung darf nicht zu einer
  stillschweigend weniger strengen Prüfung führen. Neue Adapteroptionen benötigen
  einen eigenen Modellnachweis; die bisherigen Berichte werden nicht umgewertet.


### Reduzierter Kommunikationskontext und getrennte Reasoning-Profile

- Regelkennung und Version bleiben für die serverseitige exakte Auswahl und das
  Audit erforderlich. Die Formulierung benötigt nur den fachlichen Geltungsbereich,
  Voraussetzungen und Ausnahmen. Ab Kommunikationsfassung v5 werden die technischen
  Regelmetadaten deshalb keinem der beiden Kommunikationsaufrufe übergeben.
- Fehlende Namen oder Daten werden ausgelassen, nicht durch Platzhalter ergänzt.
  Eine Abschlussmitteilung muss ohne nachträgliches Ausfüllen versandfertig sein.
- Die lokale Reasoning-Einstellung `low` wird als eigene Evaluationskonfiguration
  untersucht. Das 60-Sekunden-Limit und die übrigen Optionen bleiben gleich.
  Ergebnisse mit `none` qualifizieren `low` nicht und umgekehrt; der Backend-Standard
  wird nicht geändert. Keine Modellantwort darf eine fehlende Fachfreigabe ersetzen.


### Quellenbezogene Prüfbegründung vor dem Mitteilungsurteil

Die Kommunikationsfassung v6 fordert bei der separaten Inhaltsprüfung zuerst eine
kurze fallbezogene `explanation`, danach `approved` und den gebundenen `digest`.
Schema v2 erhält diese Reihenfolge als Modellhinweis. Die technische Hypothese ist,
dass die Quelle vor dem Boolean-Urteil ausgewertet werden soll; ein Qualitätsgewinn
wird erst durch den anschliessenden Modelllauf beurteilt. Die Begründung bleibt
unvertrauenswürdige Modellausgabe und ist kein eigener Wahrheitsnachweis. Sie wird
nicht in die öffentliche Mitteilung übernommen. Leere, typfalsche, überlange oder
fehlende Begründungen gelten als Formatfehler; `approved` bleibt frei, und alle
bisherigen Quellen-, Digest- und Freigabegrenzen bleiben bestehen. Historische
Zweifeld-Antworten werden nicht nachträglich nach diesem neuen Vertrag bewertet.

Die Formulierung wird in derselben Kommunikationsfassung auf ein bis drei direkt
adressierte Sätze zum belegten aktuellen Stand begrenzt. Diese Stilannahme dient
einer kurzen individuellen Nachricht und ist keine feste Textvorlage. Eine
Mieterbestätigung belegt den Zustand, aber ohne zusätzliche Quelle weder den
Reparaturakteur noch Reparaturdetails. Die beiden Änderungen werden gemeinsam
evaluiert; ihre Einzelwirkungen sind damit nicht isoliert nachgewiesen.


### Grundbezogene Modellaufträge und tatsächliche Absatzzeichen

- Die Begründungen aus Kommunikationsfassung v6 machen eine Verwechslung der beiden
  Abschlussgründe sichtbar. Ab v7 wählt deshalb der bereits serverseitig vorgegebene
  Grund die passende Generierungs- und Prüfanweisung. RESOLVED-Aufträge enthalten
  keine Zuständigkeitsregelprüfung; TENANT_RESPONSIBILITY-Aufträge erklären und prüfen
  ausschliesslich den belegten fehlenden Verwaltungsbedarf. Beide behalten gemeinsame
  Quellen- und Ausgabegrenzen. Mieternachrichten können die Auswahl nicht ändern.
- Die Rohtextdarstellung legte zunächst doppelt maskierte Zeilenumbrüche nahe.
  Die anschliessende Zeichenprüfung weist jedoch LF-Zeichen (Codepoint 10) und keine
  Backslashes in den fünf generierten Texten nach. Die vorläufig vorbereitete
  Zusatzsperre wurde vor jedem Testlauf entfernt. Es liegt hier kein Formatfehler vor.
  Echte Absätze bleiben erlaubt und gehen unverändert in den Entwurfsdigest ein.
  Bestand v5 ergänzt einen kontrollierten zulässigen Text mit echten Absätzen.


### Fortsetzung mit stärkerem lokalem Modellkandidaten

Nach der erneuten Fortsetzungsanweisung wird der offizielle Ollama-Kandidat
`qwen3.5:9b` als zusätzliche lokale Testabhängigkeit geladen. Er wird zunächst
mit den unveränderten 13 Regelfällen, identischen Prompts, Reasoning `none` und
demselben 60-Sekunden-Limit geprüft. Dies ändert weder die produktive Modellauswahl
noch die feste Abschlusssperre. Es werden ausschliesslich synthetische Testdaten
lokal verarbeitet. Ein bestandener Teilbestand ersetzt keine vollständige Evaluation.

## Konsolidierter Devbetrieb und manueller KI-Ersatzbetrieb – 16.11.2026

- Die neue Anforderung zum manuellen Betrieb bei KI-Ausfall ist eine ausdrückliche
  Benutzerentscheidung, keine Annahme. Sie gilt systemweit gemäss
  [KI-Tools](../specifications/ki-tools.md). Modellevaluation und automatische
  Abschlussfreigabe bleiben zurückgestellt bzw. gesperrt.
- Die lokale produktionsnahe Umgebung verwendet das bestehende PostgreSQL-Projekt
  `propertyflow-backend` unverändert. Camunda erhält getrennte dauerhafte Volumes und
  eine eigene PostgreSQL-Datenbank im Projekt `propertyflow-dev`. Testprojekte bleiben
  getrennt. Es gibt keinen Datenbankreset und keine Änderung bestehender Schlüssel.
- Für den lokalen Betrieb genügen Operate und Tasklist aus der vorhandenen
  Camunda-Version 8.9.19. Web Modeler, Connector-Runtime und öffentliche Bereitstellung
  sind nicht erforderlich. Alle veröffentlichten Ports bleiben an Loopback gebunden.
- Die sieben kanonischen BPMN-Quellen werden gemeinsam deployt. Ausschliesslich die
  Laufzeitkopien erhalten `isExecutable=true`, analog zum vorhandenen Integrationstest.
  Eine fachliche Freigabe automatischer KI-Abschlüsse folgt daraus nicht.
- Die KI bleibt standardmässig deaktiviert. Die Beispielsfälle entstehen über echte
  HTTP-Befehle und Camunda-Worker; ihre Zustände werden nicht durch SQL vorgetäuscht.
- Mitarbeiter authentifizieren sich mit ihrem persistenten Konto und Mailpit-Einmalcode.
  Der durchgängig integrierte Mieterzugang verwendet zunächst die spezifizierten
  persönlichen Fall-Links. Die bislang nur in der Frontend-Demo vorhandene freiwillige
  Mieterkonto-Registrierung ist damit noch nicht als PostgreSQL-Funktion abgenommen.
- Eine manuelle individuelle Abschlussmitteilung wird im vorhandenen Nachrichtenfeld
  bewusst gesendet, anschliessend bestätigt der Mitarbeiter den Abschluss separat.
  Die automatische Generierung und Veröffentlichung bleibt gesperrt.
