# Benachrichtigungen und Zustellung

**Status:** Zentrale Spezifikation der vereinbarten E-Mail-Benachrichtigungen; technische Zustellung und Betriebsparameter noch zu konkretisieren.  
**Stand:** 09.10.2026  
**Geltungsbereich:** Fallanlage, externe Fallkommunikation, sichtbare Status- und Dringlichkeitsänderungen sowie Fallabschluss; PropertyFlow-Services, Versandfunktion und Camunda-Integration.

## Zweck und fachliche Grundlage

Der Mieter erhält eine E-Mail, wenn sich der für ihn sichtbare Fallverlauf ändert. Die E-Mail informiert kurz über die Änderung und führt mit dem persönlichen Link zur Fallansicht. Der vollständige Nachrichtentext bleibt dort. Dies gilt auch für eigene Nachrichten des Mieters und für den Fallabschluss.

Dieses Dokument definiert Auslöser, Mailinhalt und die erforderliche Zuverlässigkeit. Die Architektur des wiederverwendbaren Mieterlinks und seiner Speicherung begründet [ADR-004](../architecture/adr/ADR-004-tokenbasierter-mieterzugriff.md). [Fallverwaltung](fallverwaltung.md) definiert Annahme und Lebenszyklus, [Fallkommunikation](fallkommunikation.md) Sichtbarkeit und Veröffentlichung, [Fallzugriff und Sicherheit](fallzugriff-und-sicherheit.md) den persönlichen Link und seine geschützte Speicherung. Die [Mieteransicht](../frontend/ansicht-01-mieter-fallansicht.md) bildet diese Regeln ab.

PropertyFlow bleibt Eigentümer der fachlichen Daten. Camunda 8 orchestriert die Hintergrundverarbeitung gemäss [ADR-002](../architecture/adr/ADR-002-camunda8-workflow-orchestrierung.md); Browser und Mailversand schaffen keine direkte KI-Chatsitzung. Die [Modulgrenzen](../architecture/module-structure.md) bleiben bestehen. Dieses Dokument bestätigt keine implementierte Zustellung oder ausgeführten Tests.

## BEN-01: Auslöser und Ausschlüsse

Für jedes dauerhaft gespeicherte, für den Mieter sichtbare Änderungsereignis wird eine E-Mail-Benachrichtigung veranlasst. Auslöser ist die fachliche Änderung, nicht das Öffnen der Ansicht oder ein technischer Worker-Aufruf.

| Ereignis | E-Mail | Zeitpunkt |
|---|---|---|
| Neues Anliegen angenommen | Ja, Bestätigung mit persönlichem Link | Nach dauerhafter Annahme nach FALL-02. Die Ursprungsmeldung gehört zu dieser Bestätigung und erzeugt keine zusätzliche Nachrichteneingangsmail. |
| Weitere Nachricht des Mieters gespeichert | Ja, auch wenn er sie selbst abgeschickt hat | Nach dauerhafter Speicherung. |
| Externe KI-/Systemnachricht veröffentlicht | Ja | Erst wenn die vollständige Nachricht gespeichert und für den Mieter veröffentlicht ist. |
| Externe Mitarbeiternachricht veröffentlicht | Ja | Nach Speicherung und ausdrücklicher Veröffentlichung. |
| Für den Mieter sichtbarer fachlicher Status geändert | Ja | Nach dauerhaftem Statuswechsel in PropertyFlow. Unveränderte Statuswerte erzeugen kein Ereignis. |
| Mieteröffentliche Dringlichkeit erstmals bewertet oder geändert | Ja | Nach gespeicherter Stufenänderung mit Historieneintrag gemäss [DRING-07](dringlichkeitsbewertung.md#dring-07-änderungshistorie-und-mietertransparenz). Ein unverändertes Bewertungsergebnis erzeugt keine Änderungsmail. |
| Fall fachlich abgeschlossen | Ja, ausdrücklich als Abschlussinformation | Nach gespeichertem Abschluss. Der Abschluss ist ein eigener Typ der Statusbenachrichtigung und erzeugt nicht zusätzlich eine zweite generische Statusmail. |
| Internes Memo angelegt oder geändert | Nein | Auch keine Hinweise oder Metadaten dazu versenden. |
| Externer Entwurf oder KI-Teilantwort | Nein | Eine spätere Veröffentlichung ist dagegen ein Auslöser. |
| Technischer Camunda-Schritt, Wiederholung oder Incident | Nein | Nur ein daraus resultierendes sichtbares fachliches Ereignis kann auslösen. |
| Fallansicht geöffnet oder neu geladen | Nein | GET verändert keine fachlichen Daten. |

Die Benachrichtigung ist unabhängig davon, ob der Mieter die Ansicht bereits geöffnet hat. Ein geöffnetes Browserfenster ersetzt die vereinbarte E-Mail nicht. Jede weitere eigene Nachricht wird bestätigt. Eine zusätzliche Empfängerliste oder ein separater Zugang für Personen, an die der Mieter den Link weiterleitet, ist nicht vorgesehen.

## BEN-02: Empfänger, Inhalt und persönlicher Link

Empfänger ist die dem Fall zugeordnete Kontaktadresse. Eine technisch beliebig übermittelte Empfängeradresse darf diese Zuordnung nicht überschreiben. Kontaktprüfung und geprüfte Adressänderungen folgen ZUG-02 und ZUG-05; die ursprüngliche Formulareingabe gilt nicht allein als Identitätsnachweis.

Die E-Mail enthält:

- Eine kurze, zum Ereignis passende Information, beispielsweise «Ihre Nachricht wurde gespeichert», «Eine neue Nachricht liegt vor» oder «Ihr Fall wurde abgeschlossen».
- Die lesbare Case-ID als Referenz.
- Den persönlichen Link zur Fallansicht, solange der zugehörige Zugang gültig ist.
- Den Hinweis: «Dieser Link ermöglicht Zugriff auf Ihren Fall. Bitte behandeln Sie ihn vertraulich.»

Betreff und Text enthalten keine vollständigen Nachrichten, internen Memos, sensiblen Freitextbetreffe oder technischen Fehlerdetails. Für die kurzen Benachrichtigungstexte genügen feste Vorlagen; eine zusätzliche LLM-Generierung ist dafür nicht erforderlich. Die Inhaltsgrenzen nach KOM-01 und KOM-02 gelten auch hier.

Jede normale E-Mail verwendet denselben gültigen Link `/mieter/fall/zugang/<geheimer-token>`. PropertyFlow zeigt den Fall direkt unter dieser Adresse. Der Link hat keine zeitliche Ablauffrist. Mailversand, Öffnen und Wiederholung erzeugen keinen neuen Token; eine Verlängerung entfällt. Hash, verschlüsselte Versandkopie und getrennte Schlüsselverwaltung sind in [ZUG-06](fallzugriff-und-sicherheit.md#speicherung-und-wiederverwendung-für-e-mails) geregelt.

Wer den Link weiterleitet, gibt die bestehenden Mieterrechte weiter. Der zusätzliche Besitzer wird dadurch weder als Person identifiziert noch automatisch zum Benachrichtigungsempfänger.

**Bestätigt am 10.10.2026 – Antwortbedarf:** Die Benachrichtigung zu einer externen Mitarbeiternachricht unterscheidet ausdrücklich eine Information von einer erforderlichen Mieterantwort. Bei gesetztem Antwortbedarf muss bereits in der E-Mail klar ersichtlich sein, dass der Mieter handeln und über seine Fallansicht antworten soll; der vollständige Auftrag steht in der veröffentlichten Nachricht. Bei einer reinen Information wird keine erforderliche Antwort behauptet. Eine ältere offene Rückfrage wird durch eine neue Informationsnachricht nicht aufgehoben. Kurze feste Vorlagen, Case-ID, persönlicher Link und die bisherigen Inhaltsgrenzen bleiben gültig; vollständige Nachrichten werden nicht in die E-Mail kopiert. Die konkrete Formulierung von Betreff und Hinweis bleibt noch zu besprechen.

## BEN-03: Speicherung und zuverlässige Übergabe

Die gespeicherte fachliche Änderung ist die Grundlage der Benachrichtigung. Vor dauerhafter Speicherung wird keine Erfolgsmail gesendet. Die Änderung und die Pflicht zur Benachrichtigung müssen so koordiniert werden, dass ein Neustart zwischen Speicherung und Versand keinen Auftrag dauerhaft verlieren lässt.

Ein Versandauftrag referenziert mindestens den Fall, das eindeutige Änderungsereignis, den Benachrichtigungstyp und seinen Bearbeitungszustand. Pro Ereignis und vorgesehenem Empfänger wird nur ein logischer Auftrag erzeugt. Wiederholte HTTP-Anfragen, Worker-Ausführungen oder Ereignisübernahmen erzeugen weder eine zweite fachliche Änderung noch einen zusätzlichen Auftrag für dasselbe Ereignis.

Ein transaktionales Outbox-Verfahren ist ein möglicher Umsetzungsvorschlag, keine bereits getroffene Architekturentscheidung. Datenbankschema, Schnittstellen und die konkrete atomare Übergabe werden im Service-/Persistenzdesign festgelegt.

Der Versand erfolgt nachgelagert. Ein Mailausfall nimmt weder die Fallannahme noch eine gespeicherte Nachricht oder einen Statuswechsel zurück. Die Ansicht liest weiterhin den fachlichen Stand aus PropertyFlow. Ein Mailauftrag startet keinen zweiten führenden Fallprozess.

## BEN-04: Wiederholungen und Zustellnachweis

| Zustand beziehungsweise Situation | Erforderliches Verhalten |
|---|---|
| Auftrag gespeichert, noch nicht versendet | Auftrag bleibt nach Neustart auffindbar und ausführbar. |
| Vorübergehender Fehler bei Maildienst oder Netzwerk | Auftrag mit demselben Ereignis und gültigen Zugang erneut versuchen; keine neuen Falldaten oder Tokens erzeugen. |
| Maildienst bestätigt die Annahme | Als an den Maildienst übergeben vermerken. Dies beweist weder Posteingangszustellung noch Lesen. |
| Übermittlungsausgang unklar, etwa bei Timeout nach Übergabe | Unsicheren Ausgang festhalten; soweit unterstützt mit derselben Versandkennung abgleichen oder idempotent wiederholen. Eine doppelte E-Mail kann bei fehlender Dienstunterstützung nicht vollständig ausgeschlossen werden. |
| Dauerhafter Fehler, Rückläufer oder Wiederholungsgrenze erreicht | Für berechtigte interne Bearbeitung sichtbar machen; Auftrag nicht stillschweigend als erfolgreich markieren oder verlieren. |
| Gültiger Zugang oder Entschlüsselung fehlt | Versand mit persönlichem Link blockieren und zur Klärung sichtbar machen; keinen widerrufenen oder ersetzten Link als gültig senden und keinen Token automatisch ersetzen. |

Die fachliche Vorgabe «immer eine E-Mail» bedeutet: Jeder vereinbarte Auslöser erzeugt eine nachvollziehbare Versandpflicht. Eine tatsächliche Zustellung in den Posteingang kann PropertyFlow nicht allein garantieren. Versandannahme, gegebenenfalls vom Maildienst bestätigte Zustellung und Rückläufer werden getrennt von Fallstatus und Nachrichtenveröffentlichung geführt. Tracking zur Lesebestätigung ist nicht vorgesehen.

Wiederholungen eines alten Auftrags dürfen keinen neueren Fallstand überschreiben. Die E-Mail beschreibt ihr auslösendes Ereignis; die Fallansicht zeigt beim Öffnen den aktuellen Stand. Technische Versand- und Empfangsverzögerungen können die Reihenfolge der E-Mails beeinflussen. Keine Zusammenfassung oder Drosselung darf vereinbarte Ereignisse ohne Benachrichtigung verwerfen.

Konkrete Wiederholungsabstände, Grenzen und interne Zuständigkeiten bleiben offen. Bei einem nicht mehr gültigen Zugang bleibt die Versandpflicht sichtbar; Ersatz und Identitätsprüfung folgen ZUG-05. Ob bis zum Ersatz eine neutrale E-Mail ohne Falllink versendet werden soll, ist noch zu entscheiden. Eine solche Mail wird hier nicht als vollständiger Ersatz der vereinbarten Linkbenachrichtigung festgelegt.

**Abgleich mit FALL-05:** Ein klärungsbedürftiger E-Mail-Versand führt bei aktivem Fall zu «Mitarbeiterprüfung erforderlich» und zum intern gespeicherten Hinweis «E-Mail-Versand klären» am Fall. Die Mitarbeiterliste zeigt den Bearbeitungsstatus, aber keine Versand- oder sonstigen Problemhinweise; ihre interne Darstellung wird bei Screen 03 konkretisiert. Automatische Wiederholungen sind davon nicht ausgenommen; ein noch ausstehender Versuch ohne festgestellten Fehler erzeugt dagegen keinen Problemhinweis. Ein erst nach Abschluss festgestelltes Versandproblem bleibt ein interner Hinweis am abgeschlossenen Fall. Der Versandzustand bleibt technisch getrennt; er wird nicht selbst als Bearbeitungsstatus angezeigt. Wiederholungen bei unverändertem fachlichem Status erzeugen weder einen erneuten Statuswechsel noch weitere Statusmailaufträge.

## BEN-05: Fallabschluss und Sicherheit

Der gespeicherte fachliche Abschluss löst auch dann eine Abschlussmail aus, wenn keine separate Abschlussnachricht erstellt wurde. **Für UI3 bestätigt am 10.10.2026:** Eine zusätzliche externe Abschlussmitteilung ist optional und wird vor dem Abschluss über das bestehende Textfeld gesendet, wie in FALL-07 geregelt. Ihre Veröffentlichung und der fachliche Abschluss bleiben unterschiedliche Ereignisse mit den jeweils geltenden Benachrichtigungspflichten. Das spätere Versenden der E-Mail erzeugt keinen neuen Historieneintrag und umgeht die Nachrichtensperre nach KOM-04 nicht.

Dies gilt gleichermassen für einen Mitarbeiterabschluss und für die vereinbarten Systemabschlüsse nach FALL-07. Ein Abschluss wegen mieterseitiger Eigenverantwortung darf in der Information nicht als vom System bestätigte Reparatur dargestellt werden. Die Abschlussmail bestätigt den fachlichen Abschluss; der zugrunde liegende Fall bleibt lesbar.

Ein gültiger Link öffnet den abgeschlossenen Fall lesend. Der Abschluss widerruft den Token nicht. Die Abschlussmail behauptet deshalb weder Schreibrechte noch einen erneuten Prozessstart. Da mit dem Abschluss noch offener Mieterantwortbedarf gemäss KOM-04 endet, fordert die Abschlussmail keine Antwort auf bisherige Rückfragen an.

Versandaufträge und Fehlerdaten unterliegen den Token- und Datenschutzregeln aus ZUG-06. Der Token wird nur durch die berechtigte Versandfunktion entschlüsselt. Er gehört weder in normale Protokolle noch in Camunda-Variablen oder KI-Kontexte. Interne Memos und deren Metadaten dürfen keine Benachrichtigung beeinflussen oder darin erscheinen.

## Prüfkriterien

Die folgenden Kriterien sind Anforderungen an spätere Tests, keine bereits erbrachten Nachweise.

- **BEN-AK-01:** Fallannahme erzeugt einen Bestätigungsauftrag. Eine sichere Einreichungswiederholung erzeugt weder einen neuen Fall noch eine zweite logische Bestätigung.
- **BEN-AK-02:** Eine eigene weitere Mieter-Nachricht sowie jede veröffentlichte externe Mitarbeiter- oder KI-Nachricht erzeugen je eine Benachrichtigungspflicht. Speicherung, Veröffentlichung und Ereigniswiederholungen werden getrennt geprüft.
- **BEN-AK-03:** Interne Memos, externe Entwürfe, KI-Teilantworten, GET-Aufrufe und rein technische Workflow-Schritte erzeugen keine Mieter-E-Mail und keine Hinweise auf interne Inhalte.
- **BEN-AK-04:** Ein sichtbarer fachlicher Statuswechsel und der Fallabschluss erzeugen Benachrichtigungen. Dies gilt für Mitarbeiterabschlüsse und die beiden vereinbarten Systemabschlussgründe aus FALL-07. Der Abschluss erzeugt keine zusätzliche generische Statusmail und benötigt keinen nachträglichen Nachrichteneintrag. Fehlender Verwaltungsbedarf wird nicht als bestätigte Reparatur ausgegeben.
- **BEN-AK-05:** Mails enthalten kurze Vorlageninformation, Case-ID, gültigen persönlichen Link und Vertraulichkeitshinweis; vollständige Nachrichten, sensible Freitexte und interne Inhalte fehlen. Wiederholungen verwenden denselben gültigen Token.
- **BEN-AK-06:** Ein Neustart nach fachlicher Speicherung verliert keine Versandpflicht. Vorübergehende Fehler sind wiederholbar; dauerhafte oder unklare Ergebnisse bleiben nachvollziehbar. Mailausfälle nehmen weder Fallannahme noch Nachrichten oder andere bestätigte Änderungen zurück. Bei klärungsbedürftigem Versand gelten Status und Hinweis nach FALL-05; abgeschlossene Fälle werden nicht wiedereröffnet. Unveränderte Statuswerte erzeugen auch bei Versandwiederholungen keine neuen Statusmailaufträge.
- **BEN-AK-07:** Widerrufene oder ersetzte Tokens werden vor Versand geprüft und nicht als gültige Links versendet. Ein unverändert gültiger Token bleibt auch lange nach Ausstellung für spätere Benachrichtigungen nutzbar; eine zeitliche Ablaufprüfung entfällt. Fehlende Schlüssel und Entschlüsselungsfehler führen zu sichtbarer Klärung, niemals zu automatischem Tokenersatz oder einer falschen Erfolgsmarkierung.
- **BEN-AK-08:** Die Annahme durch den Maildienst wird nicht als bestätigte Zustellung oder Lesen ausgegeben. Ein Timeout nach möglicher Annahme prüft den Umgang mit potenziellen Doppelzustellungen.
- **BEN-AK-09:** Die erste wirksame Dringlichkeitseinstufung und jede spätere wirksame Stufenänderung erzeugen je einen logischen Mailauftrag. Unveränderte Neubewertungen, Wiederholungen desselben Ereignisses und getrennte Systembewertungen bei geschützter manueller Einstufung erzeugen keine zusätzliche Dringlichkeitsmail.

## Offene Umsetzungsentscheidungen und Abschluss des Spezifikationsstands

- Konkretes Übergabeverfahren, Ereignis-/Versandkennungen, Maildienst und dessen Idempotenz- beziehungsweise Rückläuferunterstützung.
- Wiederholungsabstände und -grenzen, interne Fehlerzuständigkeit, Aufbewahrung und Bereinigung von Versandnachweisen.
- Empfängerbindung bei geprüften Adressänderungen und Behandlung bereits wartender Aufträge.
- Umgang mit Benachrichtigungen bei einem bewusst gesperrten Zugang.
- Konkrete Versandvorlagen und Übergangsereignisse für die vereinbarten Statuswerte gemäss FALL-05. Eine externe Abschlussmitteilung und der Abschluss sind unterschiedliche Ereignisse; ob beide in einer gemeinsamen E-Mail bestätigt werden dürfen, bleibt offen. Bis dahin wird keine ihrer Benachrichtigungspflichten verworfen.

Die vereinbarten Benachrichtigungsregeln gelten für Screen 01 und die interne Versandbearbeitung. Screen 02 zeigt bei aktivem Prüfbedarf den fachlichen Status nach FALL-05, aber keine Versandhinweise. Die offenen Punkte betreffen die Konkretisierung beziehungsweise Umsetzung und werden nicht als bereits beschlossen dargestellt. Der [Geltungsbereich von Streaming und Abbruch](fallkommunikation.md#abgleich-mit-bestehenden-dokumenten) ist geklärt: Diese Funktionen gehören zum Mitarbeiterdetail gemäss ADR-003 und UC-004. Die Mieteransicht bleibt eine asynchrone Fallansicht. Gemeinsame JPG-Entwürfe folgen erst nach den drei Screen-Spezifikationen samt technischen Wireframes.
