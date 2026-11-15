# Fallkommunikation – fachliche und sicherheitsrelevante Regeln

**Status:** Zentrale Spezifikation der im Projekt festgelegten Kommunikationsregeln; technische Umsetzung noch offen.
**Stand:** 15.11.2026
**Geltungsbereich:** Mieteransicht, Backoffice, Backend-Services, Persistenz, Camunda-Worker, E-Mail und LLM-/RAG-Integration, soweit sie Fallkommunikation verarbeiten.

## Zweck und Verbindlichkeit

Dieses Dokument ist die gemeinsame fachliche Quelle für interne und externe Nachrichten, deren Sichtbarkeit und die zulässige Grundlage LLM-generierter Kommunikation. Die Regeln gelten unabhängig von einer einzelnen Benutzeroberfläche. Änderungen werden hier gepflegt; Screen-Spezifikationen beschreiben die daraus folgende Darstellung und Bedienung und verweisen auf die Regel-IDs.

Die Spezifikation wird mit der Projektdokumentation versioniert und ist für alle Projektbeteiligten und Entwicklungswerkzeuge im Repository auffindbar. Der gemeinsame Zugriff auf die Spezifikation erweitert keine Zugriffsrechte auf interne Fallinhalte.

Die Architekturgrundlagen bleiben in [ADR-001](../architecture/adr/ADR-001-grundarchitektur.md), [ADR-002](../architecture/adr/ADR-002-camunda8-workflow-orchestrierung.md) und [ADR-003](../architecture/adr/ADR-003-praesentationsschicht.md) dokumentiert. Dieses Dokument beschreibt fachliche Regeln und ihre Durchsetzung; ein ADR dokumentiert Architekturentscheidungen und deren Begründung. Es werden keine bestehenden ADRs stillschweigend geändert.

## KOM-01: Interne und externe Nachrichten

Jede Nachricht besitzt eine eindeutige **Sichtbarkeit**, unabhängig von ihrer Absenderrolle. Ein Mitarbeiter kann sowohl ein internes Memo als auch eine externe Antwort zum gleichen Fall erfassen. **Bestätigt am 10.10.2026:** Auch das System darf interne Memos über autorisierte PropertyFlow-Anwendungsdienste speichern. Sie bleiben ausschliesslich intern, erzeugen keine Mieterbenachrichtigung und unterliegen KOM-02 sowie der Nachrichtensperre nach KOM-04. Absender System, Zeitpunkt und Sichtbarkeit bleiben nachvollziehbar.

Das Speichern eines Mitarbeitermemos und das Veröffentlichen einer Mitarbeiternachricht setzen die passende Aktion im laufenden Camunda-Fallprozess voraus. PropertyFlow prüft diese Voraussetzung und die Berechtigung serverseitig. Vor Prozessstart und bei unerreichbarem Camunda bleibt der gespeicherte Verlauf für berechtigte Mitarbeitende lesbar. Für Änderungen und Statuswechsel gilt [FALL-06](fallverwaltung.md#fall-06-fachlicher-status-und-technischer-workflow).

**Bestätigt am 10.10.2026, Darstellung konkretisiert:** Im Mitarbeiterverlauf bleiben Absender, Zeitpunkt, Herkunft System (Camunda-/KI-Verarbeitung) bzw. Mensch und interne Sichtbarkeit erkennbar. UI3 stellt menschliche Herkunft mit einem Personen-Icon und der Rolle «Mieter» bzw. «Immobilienverwaltung», Systemherkunft mit einem Zahnrad und interne Sichtbarkeit zusätzlich mit einem Schloss dar. Die Wörter «Mensch», «System» und «Intern» entfallen in diesen Blasenkopfzeilen zugunsten verständlicher Tooltips und zugänglicher Beschriftungen gemäss UI3; Herkunft und Sichtbarkeit bleiben getrennte gespeicherte Merkmale. Ein internes Systemmemo trägt Zahnrad und Schloss. Eine vom Mitarbeiter geprüfte und gesendete Nachricht behält menschliche Herkunft und das Personen-Icon, auch nach KI-Formulierung, ohne Zusatz «mit KI formuliert». Die Sichtbarkeit wird dauerhaft mit der Nachricht gespeichert; technische Bezeichnungen können beispielsweise `INTERNAL` und `EXTERNAL` lauten. Diese UI3-Darstellung erweitert keine Mieterrechte.

| Sichtbarkeit | Zweck und Beispiel | Wer darf die Nachricht sehen? |
|---|---|---|
| **Intern** | Fallbezogenes Memo eines Mitarbeiters oder des Systems, z. B. «Vor einer Beauftragung bitte den Sachverhalt intern mit der Bewirtschaftung klären.» | Ausschliesslich berechtigte interne Mitarbeitende in der Backoffice-Ansicht; niemals der Mieter. |
| **Extern** | Mieteranliegen, Ergänzung des Mieters oder veröffentlichte Antwort an den Mieter, z. B. «Bitte teilen Sie uns mit, seit wann die Störung besteht.» | Der für diesen Fall berechtigte Mieter sowie berechtigte interne Mitarbeitende. |

**Extern bedeutet fallbezogen sichtbar, nicht öffentlich zugänglich.** **Präzisiert am 10.10.2026:** Externe Nachrichten werden erst mit ihrer Veröffentlichung dauerhaft als Fallkommunikation gespeichert und für den Mieter sichtbar. Externe Entwürfe und ungesendete Eingaben werden nicht persistiert; es gibt weder «Entwurf speichern» noch automatisches Entwurfsspeichern. Vorbereitete externe Texte sind bis zur Veröffentlichung ausschliesslich ungespeicherte Arbeitsstände. Gespeicherte interne Memos bleiben davon getrennt: Sie werden bewusst intern gespeichert und niemals dem Mieter angezeigt. Sichtbarkeit und Veröffentlichungszustand sind getrennte Merkmale.

Für alle Komponenten und Ausgabekanäle der Fallkommunikation gelten folgende Regeln:

- Der Nachrichtenteil der dem Mieter zugänglichen Historie enthält ausschliesslich externe, veröffentlichte Nachrichten des autorisierten Falls. Ergänzend erscheinen die freigegebenen strukturierten Dringlichkeitsereignisse gemäss [DRING-07](dringlichkeitsbewertung.md#dring-07-änderungshistorie-und-mietertransparenz). Sie sind keine Kommunikationsnachrichten und geben keine interne Audit-Historie frei. Interne Memos erscheinen weder als Inhalt noch als Platzhalter oder Hinweis «Interne Nachricht vorhanden».
- Nachrichten aus dem Mieterformular einschliesslich der Ursprungsmeldung werden serverseitig als externe Kommunikation eingeordnet. Der Mieter erhält keine Auswahl «intern/extern» und kann die Sichtbarkeit nicht über manipulierte Requests verändern.
- Interne Memos werden ausschliesslich über eine dafür berechtigte Backoffice-Funktion oder durch autorisierte Systemkomponenten erfasst. Ein Mitarbeiter muss eine Antwort an den Mieter ausdrücklich als extern veröffentlichen; eine interne Notiz wird nicht automatisch veröffentlicht. Bei fehlender oder unbekannter Klassifikation erfolgt keine Ausgabe an den Mieter.
- Ein internes Memo bleibt intern. Soll ein berechtigter Mitarbeiter eine Information daraus dem Mieter mitteilen, wählt er die für die externe Nachricht bestimmten Sachinformationen bewusst aus und erstellt eine separate Nachricht. Er kann sie selbst formulieren oder seine kurze Sachanweisung gemäss [KOM-02](#kom-02-llm-kommunikation-ohne-interne-memos) mit KI formulieren lassen. Vor dem Senden prüft er den Text. Das System übernimmt dafür keine internen Memos automatisch; der Zugriff auf interne Notizen wird durch den persönlichen Fall-Link nicht erweitert.
- Die Filterung erfolgt vor der Ausgabe serverseitig und gilt für HTML, ergänzende Datenantworten, einzelne Nachrichtenabrufe und E-Mail-Benachrichtigungen. Reines Ausblenden im Browser genügt nicht. Auch eine bekannte oder erratene Nachrichten-ID gewährt dem Mieter keinen Zugriff auf ein internes Memo.
- Mieterbezogene Nachrichtenzahlen, Seitennavigation, Vorschauen und der Zeitpunkt der letzten öffentlichen Aktualisierung berücksichtigen ausschliesslich die sichtbare externe Kommunikation. Ein internes Memo löst keine Mieterbenachrichtigung aus.
- **Verbindliche Kontextgrenze:** Das System darf interne Memos und automatisch daraus abgeleitete Inhalte nicht in den Kontext der Kommunikationsgenerierung aufnehmen oder dem Kommunikations-LLM als abrufbare Quellen bereitstellen. Bewusst eingegebene kurze Sachanweisungen des Mitarbeiters sind gemäss [KOM-02](#kom-02-llm-kommunikation-ohne-interne-memos) zulässig, auch wenn dieselbe Information in einem Memo steht. Die spätere Prüfung oder Veröffentlichung einer Antwort erlaubt keinen automatischen Memo-Zugriff.

**Bestätigt am 10.10.2026 – Mitarbeiterdokumentation:** Interne Memos dienen auch der Dokumentation fachlicher Prüfungen, Abklärungen und ausserhalb von PropertyFlow organisierter Massnahmen. Dafür wird kein separates Prüfbestätigungsformular eingeführt. Ein gespeichertes Memo bewirkt für sich weder eine Statusänderung noch eine automatische Erledigung offenen Prüfbedarfs. Memo-Inhalte werden nicht automatisch für die KI-Kommunikationsformulierung verwendet; bewusst eingegebene Sachanweisungen des Mitarbeiters folgen KOM-02.

**Interne Änderungsbegründungen:** Optionale Begründungen für «Bearbeitung fortsetzen» (FALL-06), das Setzen, Ändern und Löschen einer Wiedervorlage (FALL-11) sowie manuelle Dringlichkeitsänderungen (DRING-06) gehören ausschliesslich zum internen Änderungsnachweis. Sie sind keine externen Nachrichten und erzeugen kein separates Memo. Sie fehlen serverseitig in allen Mieterausgaben, einschliesslich HTML, Datenantworten, Vorschauen und E-Mails. Die Begründung selbst löst keine Mieterbenachrichtigung aus. Eine nach BEN-01 vorgesehene Benachrichtigung über die fachliche Änderung bleibt davon unabhängig und enthält die Begründung nicht.

Für beide Sichtbarkeiten gilt die [Nachrichtensperre nach Fallabschluss (KOM-04)](#kom-04-nachrichtensperre-nach-fallabschluss).

**Mitarbeiter-Volltextsuche:** Persönlich angemeldete Mitarbeitende haben mit der Mitarbeiterrolle dieselben fachlichen Suchrechte nach ZUG-04. Die Suche in [Screen 02](../frontend/ansicht-02-mitarbeiter-falluebersicht.md) umfasst gespeicherte veröffentlichte externe Nachrichten und sämtliche gespeicherten internen Memos. Externe Entwürfe, ungespeicherte Eingaben und KI-Teilantworten sind ausgeschlossen. «Versendet» meint die Veröffentlichung im Fallverlauf, unabhängig vom E-Mail-Zustellstatus. Die Mieteransicht bietet diese Suche nicht an. Auch ein interner Suchindex darf dem Kommunikations-LLM keine Memos oder Ableitungen daraus bereitstellen; KOM-02 gilt weiterhin.

### Antwortbedarf einer externen Mitarbeiternachricht

**Bestätigt am 10.10.2026:** Der Mitarbeiter legt beim Senden einer externen Nachricht ausdrücklich fest, ob eine Antwort des Mieters erforderlich ist. Diese Angabe wird mit der veröffentlichten Nachricht gespeichert; eine reine Information und eine Rückfrage sind dadurch unterscheidbar. Die KI-Formulierung entscheidet diese Angabe nicht selbst. Die Auswahl allein speichert nichts; Speicherung, Veröffentlichung und fachliche Übernahme des Antwortbedarfs erfolgen im vorgesehenen PropertyFlow-/Camunda-Ablauf.

Beispiele: «Der Techniker ist informiert und wird Sie für einen Termin kontaktieren» verlangt keine Antwort an PropertyFlow. «Bitte bestätigen Sie, ob die Fehlermeldung auch nach Ausstecken und Wiedereinstecken bestehen bleibt» verlangt eine Mieterantwort. Der Antwortbedarf wird beim Mieter im Fallverlauf und in der zugehörigen Benachrichtigungs-E-Mail eindeutig angezeigt. Interne Memos erzeugen keinen Mieterantwortbedarf.

Eine reine Informationsnachricht hebt keinen bereits offenen Antwortbedarf auf. Der Eingang irgendeiner Mieternachricht erledigt eine Rückfrage nicht automatisch; die inhaltliche Klärung und Prozessfortsetzung folgen FALL-05 und KOM-03. **Bedienung in UI3 bestätigt:** Toggle «Mieterantwort erforderlich» im selben Stil neben dem Toggle «Intern», beim Öffnen standardmässig ausgeschaltet und nur für externe Nachrichten verfügbar. Interne Memos dürfen keinen Mieterantwortbedarf setzen; dies wird serverseitig geprüft.

### Mitarbeiternachricht als Nebenaktion während offener Prüfung

**Bestätigt am 15.11.2026:** Während «Mitarbeiterprüfung erforderlich» kann der
Mitarbeiter über das bestehende UI eine externe Nachricht senden. Dies ist eine
Nebenaktion: dieselbe User Task «Fall prüfen und Vorgehen entscheiden» bleibt
offen, ihr Schlüssel und der Status bleiben erhalten. Senden erledigt weder
Prüfgründe noch eine Mitarbeiterentscheidung und erzeugt keine neue Aufgabe.

Mit «Mieterantwort erforderlich» wird zusätzlich Antwortbedarf an der Nachricht
geführt, ohne auf «Wartet auf Mieterantwort» umzuschalten. Ohne diese Auswahl
entsteht kein neuer Antwortbedarf; bestehender Antwortbedarf bleibt erhalten.
Eine spätere Mieterantwort wird zentral empfangen und ausgewertet und ergänzt
dieselbe Prüfung. Erst eine bewusste Mitarbeiterentscheidung gibt die weitere
Bearbeitung, Wiedervorlage oder den Abschluss frei.

Im Mitarbeiter-BPMN bildet ein nicht unterbrechender Ereignis-Teilprozess
`SMessageSide` diese Nebenaktion ab. Sein internes Sendeereignis gehört zu einem
berechtigten Mitarbeiterauftrag, nicht zum öffentlichen Mieternachrichteneingang.
Weil der Empfang im Mitarbeiterprozess liegt, muss der Dienst ausdrücklich auf
die aktuelle offene Prüfaufgabe, externe Warteaufgabe nach FALL-12 oder – ausschliesslich für Information ohne neuen Antwortbedarf – Bearbeitungsaufgabe SWork begrenzen. Bei SWork bleibt eine Rückfrage mit Antwortbedarf im bestehenden SQuestion-Fortsetzungsweg. Zu prüfen sind Mitarbeiterberechtigung, Fall,
führende Bearbeitung, Kindinstanz und User-Task-Zuordnung sowie den aktuellen
Fallstand vor Veröffentlichung prüfen. Kein blosser Client-Prozessschlüssel als
Autorisierung. Text, Absender und Antwortbedarf stammen aus dem bestätigten
Sendeauftrag; das Modell entscheidet diese Angaben nicht selbst.

Der Worker speichert die externe Nachricht, gegebenenfalls Antwortbedarf und
zuverlässigen Versandauftrag mit Wiederholungsschutz. Pro Ereignis bleibt die
Auftragsreferenz lokal; keine überschreibbare globale letzte Nachricht und keine
Änderung der Entscheidungs-/Rückgabevariablen des Mitarbeiter-Hauptpfads. Die
Nebenaktion enthält keine zusätzliche LLM-Erzeugung; die vorhandene freiwillige
Formulierungshilfe im UI bleibt unverändert. Interne Memos folgen ihrem eigenen Weg.

Gleichzeitige Mitarbeiterentscheidung und Veröffentlichung werden gegen denselben
aktuellen Stand geprüft. Bereits gespeicherte Nachricht bleibt erhalten. Ist die
zugehörige Prüfaufgabe vor Veröffentlichung beendet oder der Fall abgeschlossen,
wird dieser alte Nebenauftrag nicht nachträglich veröffentlicht und reaktiviert
keine Aufgabe. Ablehnung wird als solche nachvollziehbar, nicht als Sendeerfolg.
Eine erfolgreiche Wiederholung liefert nur den bestehenden Nachweis. Technische
Fehler folgen Job-Retries/Incidents; neue fachliche Wirkung erfordert weiterhin
eine gültige Aufgabe. Ein normales Ende des Nebenpfads beendet nur diesen Auftrag.
Die technische Korrelation, Rückmeldung ans UI und Synchronisation sind noch zu
implementieren; keine neue öffentliche Route oder geänderte HTTP-Semantik festgelegt.

## KOM-02: LLM-Kommunikation ohne interne Memos

**Präzisiert und bestätigt am 10.10.2026:** Interne Memos werden niemals automatisch als Kontext für die Generierung von Kommunikationsnachrichten durch ein LLM verwendet. Ein Mitarbeiter darf bewusst eine kurze Sachanweisung für die externe Nachricht eingeben, auch wenn dieselbe Information bereits in einem internen Memo steht. Er entscheidet, welche Sachinformationen er zur Formulierung übergibt, und prüft den erzeugten Text vor dem Senden. Die Kontextgrenze gilt für Antworten, Rückfragen und Kommunikationsentwürfe, einschliesslich später manuell freigegebener Entwürfe.

- Der zuständige PropertyFlow-Service stellt vor jedem Kommunikations-LLM-Aufruf einen zweckgebundenen Kontext aus ausdrücklich zulässigen Quellen zusammen, beispielsweise Mieterangaben, geeigneter externer Fallkommunikation, dafür freigegebenen Sachinformationen und der bewusst für diesen Formulierungsversuch übergebenen Mitarbeitereingabe.
- Das System darf Mitarbeiter- und Systemmemos weder direkt als Prompt- oder Gesprächsinhalte noch indirekt über RAG/Retrieval, Tool-Ergebnisse, Speicher/Caches, Suchindizes oder Camunda-Variablen in diesen Kontext übernehmen.
- Der Ausschluss umfasst auch vom System aus Memos erzeugte Zusammenfassungen, Paraphrasen, extrahierte Fakten und sonstige Ableitungen. Eine Umbenennung oder Kennzeichnung solcher Daten als «extern» oder «Mitarbeitereingabe» macht sie nicht zulässig. Die erlaubte kurze Sachanweisung stammt aus einer bewussten Mitarbeitereingabe für den Formulierungsversuch; das System darf sie nicht selbst aus Memos erstellen oder mit Memo-Inhalten anreichern.
- Sichtbarkeit für den Mieter und Eignung als LLM-Eingabe sind getrennte Prüfungen. Die Herkunft systemseitig bereitgestellter Inhalte und ihre zulässige Verwendung bleiben über Verarbeitungsschritte nachvollziehbar. Bei unklarer Herkunft wird solcher zusätzlicher Kontext ausgeschlossen. Für frei formulierte Mitarbeitereingaben wird keine technische Erkennung verlangt oder behauptet, ob der Mitarbeiter denselben Sachverhalt aus einem Memo kennt. Die bestätigte Grenze betrifft die bewusste Eingabe einerseits und die systemseitige Bereitstellung von Kontext andererseits.
- Ein Prompt wie «Interne Memos nicht erwähnen» genügt nicht. Der Ausschluss systemseitiger Memo-Quellen wird vor dem LLM-Aufruf und bei allen vom Kommunikations-LLM abrufbaren Quellen durchgesetzt, auch bei Wiederholungen eines Camunda-Jobs. Eine spätere menschliche Veröffentlichungskontrolle ersetzt diese Prüfung nicht.
- Ein LLM-Sitzungs- oder Gesprächskontext, der für andere Aufgaben mit internen Memos befüllt wurde, darf nicht zur Kommunikationsgenerierung weiterverwendet werden. Der Kommunikationsschritt erhält einen getrennten, zulässigen Kontext. Bewusst eingegebene Sachanweisungen eröffnen keinen Zugriff auf die zugrunde liegenden Memos.

**Bestätigtes Beispiel:** Ein internes Memo enthält den Technikertermin am Dienstag, eine Kostenfreigabe und eine interne Vorgabe zur Offertprüfung. Der Mitarbeiter gibt für die externe Formulierung nur «Techniker kommt am Dienstag. Bitte Zugang zur Wohnung ermöglichen.» ein. Diese Sachanweisung ist zulässig. Die ausschliesslich im Memo vorhandene Kostenfreigabe und Offertvorgabe werden nicht in den KI-Kontext übernommen. Das Memo bleibt intern; erst die vom Mitarbeiter geprüfte und gesendete externe Nachricht wird veröffentlicht. Kurzeingabe und ungesendeter KI-Vorschlag bleiben ohne Entwurfsspeicherung.

**Ergänzt am 10.10.2026 – interne Änderungsbegründungen:** Die vorstehenden Ausschlüsse gelten ebenso für Begründungen zu Status-, Wiedervorlage- und Dringlichkeitsänderungen. Das System übernimmt weder die Begründungen selbst noch daraus erzeugte Zusammenfassungen oder Fakten in Prompts, Retrieval-/Tool-Ergebnisse oder wiederverwendeten Kommunikationskontext. Die Eingabe in ein Begründungsfeld gilt nicht als Sachanweisung für die externe KI-Formulierung. Eine gesonderte, bewusst für die externe Formulierung eingegebene Sachanweisung folgt weiterhin der oben vereinbarten Abgrenzung; sie erlaubt keinen automatischen Zugriff auf gespeicherte Begründungen.

## KOM-03: Asynchrone Fallkommunikation

KI-gestützte Verarbeitungsschritte der Fallkommunikation werden durch Camunda 8 orchestriert und über PropertyFlow-Worker/Services ausgeführt. Der Mieter übermittelt Informationen zum Fall; das Backend speichert sie und führt sie gemäss dem aktuellen BPMN-Schritt der weiteren Verarbeitung zu. Jede dauerhaft gespeicherte Mieter-Nachricht veranlasst die Dringlichkeitsbewertung nach DRING-05 unter Wahrung manueller Einstufungen nach DRING-06. Sie eröffnet weder einen neuen Fall noch einen zweiten führenden Prozess. Der blosse Nachrichteneingang erledigt keine Rückfrage; dafür ist die nachfolgend definierte inhaltliche Prüfung erforderlich. Die konkrete BPMN-Korrelation bleibt im Integrationsvertrag festzulegen.

**Bestätigt am 14.11.2026 – Nachrichtenempfang in allen aktiven Status:** Ein berechtigter Mieter kann jederzeit während der aktiven Bearbeitung eine Nachricht schicken, auch ohne offene Rückfrage und während automatischer Analyse, Mitarbeiterprüfung, Umsetzung oder Wartephasen. Der laufende Camunda-Fallprozess muss einen durchgängigen Eingang für Mieternachrichten bereitstellen. Der Empfang darf keine Lücke allein durch einen Wechsel zwischen diesen Prozessschritten haben. Die technische Modellierung und eindeutige Zuordnung der Ereignisse werden vor Ausführung konkretisiert; mehrere Empfangspfade oder technische Wiederholungen dürfen keine doppelte fachliche Wirkung desselben Ereignisses erzeugen. Die bestehenden Voraussetzungen zu Zugriff, laufendem Prozess, Camunda-Erreichbarkeit und Nachrichtensperre nach Abschluss bleiben gültig.

**Während «Mitarbeiterprüfung erforderlich»:** Eine neue Nachricht ergänzt dieselbe Prüfung. Die aktuelle Analyse, Dringlichkeitsbewertung und Prüfung offener Rückfragen dürfen nach ihren Fachregeln weiterlaufen und ihre zulässigen Ergebnisse speichern. Der Bearbeitungsstatus bleibt jedoch unverändert, bis ein Mitarbeiter bewusst entscheidet. Auch «alles erledigt» hebt die Prüfung nicht auf; weder eine neue noch eine verspätete automatische Auswertung darf die Aufgabe beenden oder den Fall abschliessen. Die Herkunft des Prüfbedarfs (System oder Mensch) ändert daran nichts. [FALL-06 · Vorrang einer laufenden Mitarbeiterprüfung](fallverwaltung.md#vorrang-einer-laufenden-mitarbeiterprüfung)

Bei laufender Wiedervorlage hebt eine neu gespeicherte Mieternachricht den Termin auf. Camunda legt den Fall sofort zur Mitarbeiterprüfung vor. Die automatische Auswertung entscheidet über die inhaltliche Beantwortung offener Rückfragen. Für die Vorlage genügt der gespeicherte Nachrichteneingang. [FALL-11](fallverwaltung.md#fall-11-wiedervorlage)

Der Mieter hat keine direkte LLM-Chatsitzung, keinen Chat-Stream und kein Recht, KI-Schritte oder den Camunda-Prozess abzubrechen. Diese Grenze gilt auch bei direkten Backend-Aufrufen mit Mieterberechtigung. Neuladen oder Schliessen der Seite beeinflusst die Hintergrundverarbeitung nicht.

An den Mieter werden als Kommunikationsnachrichten ausschliesslich vollständige, gespeicherte und veröffentlichte externe Nachrichten ausgegeben. KI-Teilantworten und interne Zwischenergebnisse gehören nicht zur externen Historie. Wiederholungen und KI-Fehler werden durch die Camunda-/Backend-Integration behandelt; gespeicherte Nachrichten bleiben erhalten. Vor Kommunikationsgenerierung gilt KOM-02, vor Ausgabe KOM-01.

Die konkrete Aktualisierung der Darstellung beschreibt [Screen 01](../frontend/ansicht-01-mieter-fallansicht.md). Für die in [Vision](../vision.md) optional vorgesehenen KI-Rückfragen sind die Veröffentlichungsregeln in [KOM-05](#kom-05-automatische-rueckfragen) festgelegt. Die technische Umsetzung steht aus; die Einordnung als optionale Erweiterung bleibt bestehen.

### Mieternachricht während Wiedervorlage

**Bestätigt am 15.11.2026 – Fortsetzungstabelle, Zeile 5:** Kontakt hebt die
betroffene Wiedervorlage vor der KI-Auswertung auf. Spätere Auswertung ergänzt
dieselbe Mitarbeiterprüfung. Gleichzeitiger Timer erzeugt keine zweite Aufgabe;
ein alter Kontakt hebt keine bewusst neu gesetzte Wiedervorlage auf. Nach
Abschluss keine Wiederaufnahme. Versionsbindung und Konkurrenzregeln sind
zentral in [FALL-11](fallverwaltung.md#nachricht-timer-und-neuere-terminentscheidung)
festgelegt; spätere Prüffälle stehen in FALL-AK-27.

### Worker-Regeln bei Mitarbeiterprüfung und veralteter Auswertung

**Bestätigt am 15.11.2026 – Fortsetzungstabelle, Zeile 4:** Die drei Ausgänge
bei «Aktuelle Bearbeitung» wählen den Bearbeitungskontext. Die folgenden
Prüfungen erfolgen im Dienst hinter den Service Tasks, nicht durch ein weiteres LLM.

- **NKeepReview – dieselbe Mitarbeiteraufgabe ergänzen:** Nachricht, Fall,
  führende Instanz und aktuelle Aufgabe über die verifizierte Kindzuordnung prüfen.
  Nur die bestehende offene Aufgabe ergänzen; keine zweite Aufgabe erzeugen,
  keine erledigte Aufgabe reaktivieren und keine zwischenzeitliche Entscheidung
  zurücknehmen. «Problem behoben» ist lediglich eine Empfehlung. Ist die Aufgabe
  inzwischen erledigt, bleibt ihre Entscheidung bestehen; die weitere Auswertung
  muss den aktuellen Bearbeitungskontext verwenden.
- **NApply – Auswertung geschützt bereitstellen:** Unmittelbar vor fachlicher
  Speicherung Fall-/Bearbeitungsstand, relevante Nachrichten, Aufgabenstand,
  führenden Prozess und manuelle Dringlichkeit prüfen. Prüfung und Speicherung
  gegen konkurrierende Änderungen absichern. Mitarbeiterprüfung wird weder
  automatisch beendet noch ihr Status geändert. Versionierte Bereitstellung an
  den richtigen Instanzbereich ist ausdrücklich und wiederholbar durchzuführen.
- **NFailure – Analysefehler:** Fehlergrundlage bereitstellen; vorhandene
  Dringlichkeit erhalten und eine bestehende Mitarbeiterprüfung ergänzen.
  Keine zweite Aufgabe allein wegen dieses Fehlers. Auch Fehlerergebnisse
  durchlaufen vor ihrer Wirkung die Aktualitätsprüfung in NApply.

Ist das noch nicht wirksam gespeicherte Ergebnis veraltet, meldet NApply den
fachlichen BPMN-Fehler `TENANT_EVALUATION_STALE`. Die alte Grundlage wird nicht
angewandt und löst kein Bereitschaftssignal aus. Der Rückweg führt zu
**«Aktuelle Grundlage übernehmen / abwarten»**. Dieser Dienst prüft zunächst,
ob die Verarbeitung noch zur aktiven Bearbeitung gehört. Bei Abschluss, alter
Prozesszuordnung oder bereits wirksam verarbeitetem Ergebnis endet dieser
Nachrichtenaufruf ohne neue Wirkung. Eine spätere Wiedereröffnung macht alte
Ergebnisse nicht wieder gültig.

Andernfalls verwendet der Dienst eine passende aktuelle zentrale Auswertung,
wartet auf eine bereits laufende oder veranlasst die fehlende über dieselbe
zentrale Verarbeitung. Die Koordination darf weder auf das eigene verworfene
Ergebnis warten noch parallele unabhängige Analysen derselben Grundlage starten.
Anschliessend geht es erneut zu NApply; weitere Änderungen können erneut den
Rückweg auslösen. Der Rückweg wiederholt nicht die Eingangswirkungen NKeepReview
oder NWake und hebt insbesondere keine inzwischen neu gesetzte Wiedervorlage auf.
Er setzt keinen Status zurück und erteilt keine Veröffentlichungs-/Abschlussfreigabe.

Ein tatsächlicher Analysefehler bleibt vom Versionskonflikt getrennt. Ein rein
technischer Speicher-/Bereitstellungsfehler verwendet technische Wiederholung,
nicht `TENANT_EVALUATION_STALE`. Bereits erfolgreiche Wiederholungen bleiben
idempotent; fehlende interne Zustellung wird versionsgeprüft nachgeliefert.
Worker, Synchronisation und Warteverfahren sind noch nicht implementiert.
Prüfnachweis: KOM-AK-25.

### Neue Mieternachricht während automatischer Analyse

**Bestätigt am 15.11.2026 – Fortsetzungstabelle, Zeile 1:** Geht während der automatischen Analyse oder Empfehlung im Status «In Abklärung» eine neue Mieternachricht ein, gehört sie zur aktuellen Entscheidungsgrundlage. Eine Auswertung, die diese relevante gespeicherte Nachricht noch nicht berücksichtigt, darf keine Rückfrage, Antwort oder Abschlussaktion auslösen und keine aktuelle Einstufung überschreiben. Die Nachricht bleibt gespeichert; ein veraltetes Ergebnis allein erzeugt keine Mitarbeiterprüfung.

Der Hauptprozess übernimmt eine aktuelle Auswertung, die alle relevanten gespeicherten Nachrichten umfasst, und führt damit erneut «Ausführungsbedingungen und Abschlussgründe prüfen» aus. Liegt diese Auswertung bereits vor, wird sie verwendet. Läuft sie im zentralen Nachrichteneingang, wird ihr Ergebnis abgewartet. Fehlt eine passende Auswertung, wird ihre Erstellung über dieselbe zentrale Auswertungsverarbeitung veranlasst. Es entsteht weder ein zweiter unabhängiger Entscheidungsweg noch ein weiterer externer Nachrichtenempfänger. Eine weitere Nachricht während des Wartens muss ebenfalls berücksichtigt werden; ein nur inzwischen fertig gewordenes älteres Ergebnis genügt nicht.

Der Status bleibt dabei zunächst «In Abklärung». Eine inzwischen getroffene Mitarbeiterentscheidung, verpflichtender aktueller Prüfbedarf und die Abschlusssperre behalten Vorrang; der Rückweg darf keine andere Bearbeitung zurücksetzen. Ein tatsächlicher Analysefehler folgt seinem Fehlerweg und wird nicht als bloss veraltetes Ergebnis behandelt. Ein bereits laufender Modellaufruf muss für diese fachliche Regel nicht technisch abgebrochen werden; sein veraltetes Ergebnis darf nicht wirksam werden.

Im BPMN führt das an der Ausführungsprüfung erkannte veraltete Analyseergebnis über den Rückweg «Aktuelle Auswertung übernehmen / abwarten» erneut zur Prüfung. Der zusätzliche Fehlerschlüssel ist ein fachlicher Platzhalter des nicht ausführbaren Entwurfs; Versionsbindung, Bereitstellung und Warteverfahren werden vor Umsetzung konkretisiert. Diese Regel betrifft Zeile 1; für eine erst nach der Prüfung, aber vor Veröffentlichung eingehende Nachricht gilt der folgende Abschnitt.

### Neue Mieternachricht vor automatischer Veröffentlichung

**Bestätigt am 15.11.2026 – Fortsetzungstabelle, Zeile 2:** Nach erfolgreicher Ausführungsprüfung prüft der Veröffentlichungsdienst den aktuellen Nachrichten- und Fallstand nochmals gemeinsam mit der Speicherung. Dies gilt für automatische Katalogrückfragen nach KOM-05 und automatische Antworten nach KOM-06. Die Prüfung und Speicherung werden mit konkurrierenden Nachrichteneingängen konsistent synchronisiert; zwischen letzter Prüfung und Veröffentlichung darf keine neue relevante gespeicherte Nachricht unberücksichtigt bleiben.

Ist die Grundlage durch eine inzwischen gespeicherte Mieternachricht überholt, wird die vorbereitete Nachricht nicht veröffentlicht. Es entstehen weder eine externe Nachricht noch neuer Antwortbedarf oder ein zugehöriger Benachrichtigungsauftrag. Bereits bestehende Nachrichten, Rückfragen und Versandaufträge bleiben erhalten. Eine veraltete Grundlage allein erzeugt keine Mitarbeiterprüfung.

Der Prozess verwendet denselben Rückweg zur aktuellen zentralen Auswertung wie in Zeile 1: vorhandenes aktuelles Ergebnis übernehmen, laufende Auswertung abwarten oder fehlende Auswertung über dieselbe Verarbeitung veranlassen. Danach werden die Ausführungsbedingungen erneut geprüft, ohne alte Veröffentlichungs- oder Abschlussfreigabe zu übernehmen. Der Rückweg setzt keinen neuen Status; insbesondere wird «Wartet auf Mieterantwort» nicht allein deshalb zu «In Abklärung». Bisherige Rückfragen und deren aktueller Antwortbedarf bleiben Teil der Prüfung. Sind alle zuvor offenen Fragen beantwortet, gilt weiterhin FALL-05: Mitarbeiterprüfung, sofern kein zulässiger Systemabschluss erfolgt und keine vorrangige Wiedervorlage besteht. Dieser Übergang darf durch eine neue Frage nicht umgangen werden. Mitarbeiterentscheidungen, Pflichtprüfbedarf und Abschlusssperre behalten Vorrang.

**Reihenfolge der Speicherung:** Wird die Mieternachricht zuerst wirksam gespeichert, ist die alte Veröffentlichung zu unterdrücken. Wird die Veröffentlichung zuerst erfolgreich gespeichert, bleibt sie samt ihrer Benachrichtigungspflicht bestehen; die danach gespeicherte Mieternachricht wird gegen den dann aktuellen Verlauf und bei einer Rückfrage gegen deren Antwortbedarf ausgewertet. Kein rückwirkendes Löschen, kein Zurücknehmen der Veröffentlichung und keine doppelte Nachricht bei Wiederholung. Bei einer bereits veröffentlichten automatischen Antwort bleibt die erneute Abschlussprüfung aus KOM-06 erforderlich.

Im BPMN führen unterbrechende Fehler-Randereignisse an «Katalogrückfrage veröffentlichen» und «LLM-Nachricht erstellen und versenden» zum gemeinsamen Schritt «Aktuelle Auswertung übernehmen / abwarten» zurück. `PUBLICATION_BASIS_STALE` ist ein fachlicher Platzhalter für die vor Speicherung unterdrückte Veröffentlichung. Ein technischer Speicherfehler oder die Wiederholung einer bereits erfolgreichen Veröffentlichung löst diesen fachlichen Rückweg nicht allein aus. Ein neuer Mieterkontakt löst ihn nicht direkt aus; der Dienst muss den Konflikt feststellen. Technische Transaktions- und Versionsverträge bleiben vor der ausführbaren Umsetzung festzulegen.

### Längengrenze für Mieternachrichten

Eine neue Ergänzung oder Antwort eines Mieters zu einem bestehenden Fall darf höchstens **2’000 UTF-16-Codeeinheiten** umfassen. Formular und Backend prüfen dieselbe Grenze nach Vereinheitlichung von CRLF und einzelnem CR zu LF. Die Grenze gilt vor dem Entfernen äusserer Leerzeichen. Bei Überschreitung weist PropertyFlow die Nachricht ohne Speicherung ab. Der vollständige eingegebene Text bleibt mit einem Hinweis zur maximalen Länge im Formular. [UC-009](../use_cases/UC-009-mieteranliegen-ergaenzen.md) · [Screen 01](../frontend/ansicht-01-mieter-fallansicht.md#43-neue-nachricht-hinzufügen)

### Inhaltliche Prüfung offener Rückfragen

**Bestätigt am 10.10.2026:** Camunda veranlasst bei neuen Mieternachrichten die inhaltliche Prüfung der offenen Rückfragen über die zuständigen PropertyFlow-Services. Eine eindeutig beantwortete Rückfrage wird als beantwortet erledigt. Unvollständige, unklare oder themenfremde Antworten lassen die jeweilige Rückfrage offen. Bei mehreren offenen Rückfragen wird jede einzeln beurteilt; die Beantwortung einer Frage erledigt keine andere Frage ohne passende inhaltliche Grundlage.

Die Prüfung berücksichtigt den Zusammenhang der veröffentlichten Rückfragen und der gespeicherten Mieternachrichten. Eine Nachricht kann mehrere Fragen beantworten, wenn die Antwort für jede dieser Fragen eindeutig ist. Bleibt die Zuordnung oder Vollständigkeit unklar, wird keine Erledigung behauptet. Eine fehlgeschlagene Auswertung gilt ebenfalls nicht als erfolgreiche Beantwortung.

Die ursprüngliche Kennzeichnung «Mieterantwort erforderlich» an der veröffentlichten Nachricht und der aktuelle Erledigungsstand der zugehörigen Rückfrage bleiben unterscheidbar. Die Veröffentlichung und ihr Text werden nicht nachträglich überschrieben. Eine bestätigte Beantwortung wird in PropertyFlow mit Bezug zur Rückfrage, zur zugrunde liegenden Mieterantwort und zum Verarbeitungszeitpunkt nachvollziehbar gespeichert. UI1 und UI3 zeigen den aktuellen Antwortbedarf; eine erledigte Rückfrage fordert nicht weiter zur Antwort auf. Wiederholte Verarbeitung derselben Erkenntnisse erzeugt keine zusätzliche Erledigung.

Bei einer laufenden Wiedervorlage hat die sofortige Vorlage nach FALL-11 Vorrang. Die inhaltliche Prüfung der Nachricht folgt den Freigaben des aktuellen Prozessschritts nach FALL-06. Für den Folgestatus nach Beantwortung aller Rückfragen gelten die Regeln und Ausnahmen in [FALL-05](fallverwaltung.md#status-nach-beantwortung-aller-rückfragen). Beim Fallabschluss endet noch offener Antwortbedarf gemäss KOM-04. Wird eine Rückfrage während eines aktiven Falls gegenstandslos, kann ein Mitarbeiter ihren Antwortbedarf gezielt aufheben, wie nachfolgend beschrieben.

### Fortsetzung nach einer Mieterantwort im Wartestatus

**Bestätigt am 15.11.2026 – Fortsetzungstabelle, Zeile 3:** Bei «Wartet auf Mieterantwort» wird eine neue Mieternachricht zentral ausgewertet. Währenddessen bleibt dieser Status zunächst bestehen; der Nachrichteneingang oder die Auswertung allein setzt ihn nicht auf «In Abklärung». Jede offene Frage wird einzeln gegen den aktuellen Verlauf geprüft. Eindeutige Teilantworten erledigen nur die jeweils beantwortete Frage. Die bereits vereinbarten Regeln aus FALL-05, KOM-05 und KOM-06 werden in folgender Reihenfolge angewendet:

| Priorität | Ergebnis am aktuellen Fallstand | Fortsetzung |
|---|---|---|
| 1 | Verpflichtender menschlicher Prüfbedarf oder festgestellter Analysefehler | Mitarbeiterprüfung. Eine bereits laufende Prüfung bleibt dieselbe Aufgabe. Ein positiver Abschlussvorschlag übergeht keinen Prüfbedarf. |
| 2 | Vollständig belegter, zulässiger Systemabschluss mit anschliessender individueller LLM-Nachricht | Freigegebene Antwort nach KOM-06 veröffentlichen, danach den Abschluss nochmals prüfen. Die Auswertung selbst schliesst den Fall nicht ab. |
| 3 | Alle zuvor offenen Fragen beantwortet, aber kein zulässiger Systemabschluss | Mitarbeiterprüfung gemäss FALL-05. Keine neue Frage stellen, um diesen Übergang zu umgehen. |
| 4 | Fragen bleiben offen und eine weitere notwendige Katalogfrage ist nach KOM-05 zulässig | Nur die neue notwendige Frage veröffentlichen. Bereits offene, beantwortete oder bewusst aufgehobene Fragen nicht wiederholen. Danach weiter auf Antworten warten. |
| 5 | Fragen bleiben offen und keine weitere Frage ist erforderlich | Weiter auf Antworten warten, ohne neue Nachricht, zusätzlichen Antwortbedarf oder Benachrichtigungsauftrag. Eine Teilantwort allein erzeugt keinen Prüfbedarf. |

Unklare fachliche Einordnung, inhaltlich unzulässige Abschlussnachricht und notwendige Fragen ausserhalb des Katalogs folgen weiterhin den Prüfregeln aus FALL-07/KOM-05/KOM-06; sie werden nicht stillschweigend als «keine weitere Frage erforderlich» behandelt. Ist bei eindeutig erledigtem Gesamtanliegen ein Systemabschluss zulässig, dürfen gegenstandslose Rückfragen gemäss FALL-07 bis zum erfolgreichen Abschluss offen bleiben; sie werden nicht als beantwortet erfunden. Eine reine Informationsnachricht ohne zuvor offene Rückfragen löst den Übergang wegen vollständiger Beantwortung nicht aus.

Die Reihenfolge setzt eine aktuelle, zum Fall und zur Bearbeitung passende Auswertung voraus. Ein überholtes oder bereits verarbeitetes Ergebnis ist kein fachlicher Ausgang zur Mitarbeiterprüfung. Wird während der Antwortprüfung eine weitere relevante Nachricht gespeichert, wird die aktuelle zentrale Auswertung übernommen beziehungsweise abgewartet und danach erneut geprüft. Fehlt sie, wird ihre Erstellung über dieselbe zentrale Verarbeitung veranlasst. Keine zweite unabhängige Analyse und keine weitere Nachrichtenkorrelation. «Weiter warten» verwendet einen bereits entschiedenen Ergebnisstand nicht nochmals als neue Antwort; eine schon verfügbare neuere Auswertung darf aber nicht verloren gehen. Verbrauch und Ergebnisstand müssen deshalb nachvollziehbar gebunden sein.

Auch nach dem Rückweg einer unterdrückten Veröffentlichung aus Zeile 2 gelten diese Ausgänge. Bei verbliebenen offenen Fragen ohne weitere zulässige Aktion führt der Hauptprozess zurück zum Antwortwarten und nicht allein deshalb zum Default-Ausgang Mitarbeiterprüfung. Der Rückweg erhält den bisherigen Wartestatus und Rückfragenkontext. Eine inzwischen getroffene Mitarbeiterentscheidung, vorrangige Wiedervorlage oder ein gespeicherter Abschluss wird nicht zurückgenommen. Die technische Zuordnung und Synchronisierung bleiben vor Ausführung festzulegen.

Beispiel: Offen sind «Seit wann besteht die Störung?» und «In welchem Raum tritt sie auf?». Die Antwort «Seit gestern» erledigt nur die erste Frage. Ohne zusätzlichen Prüfbedarf, zulässigen Abschluss oder weitere notwendige Katalogfrage bleibt «Wartet auf Mieterantwort» bestehen. Die Raumfrage wird nicht nochmals gesendet. Beantwortet der Mieter später auch diese Frage, folgt ohne zulässigen Systemabschluss die Mitarbeiterprüfung.

### Ausbleibende Mieterantwort in Version 1

**Bestätigt am 15.11.2026:** Version 1 kennt keine automatischen Erinnerungen,
keine Antwortfristen und keine zeitgesteuerte Eskalation wegen einer ausbleibenden
Mieterantwort. Ohne sonstigen fachlichen Auslöser bleibt der Fall auf
«Wartet auf Mieterantwort»; offener Antwortbedarf bleibt bestehen.
Schweigen ist weder eine Antwort noch ein Beleg für die Behebung und löst keinen
Fallabschluss aus. Das gilt auch für unbeantwortete Restfragen nach Teilantworten.

Der Mitarbeiter kann selbst nachfragen oder nicht mehr benötigten Antwortbedarf
bewusst aufheben. Dafür gelten die bestehenden Fortsetzungsregeln; ein zulässiger
Mitarbeiterabschluss bleibt möglich. Neue zwingende Prüfgründe und andere bereits
vereinbarte Mitarbeiteraktionen werden durch das Warten nicht blockiert.
Der ausdrücklich gesetzte Wiedervorlagetermin nach FALL-11 bleibt davon getrennt:
Sein Timer gilt weiterhin und ist keine automatische Mieterantwortfrist.

### Antwortbedarf durch Mitarbeiter aufheben

**Bestätigt am 10.10.2026:** Ein Mitarbeiter kann in UI3 bei einer veröffentlichten externen Rückfrage mit noch offenem Antwortbedarf die Aktion «Antwortbedarf aufheben» wählen. Die Aktion beendet ausschliesslich den Antwortbedarf der gewählten Nachricht. Andere Nachrichten mit offenem Antwortbedarf bleiben unberührt. Die ursprüngliche Nachricht und ihre ursprüngliche Kennzeichnung bleiben erhalten; ohne passende Antwort wird die Rückfrage nicht als inhaltlich beantwortet ausgegeben. UI1 und UI3 zeigen den gespeicherten aktuellen Antwortbedarf und fordern für die betreffende Nachricht keine weitere Antwort mehr an. Es entsteht kein zusätzlicher Fall- oder Rückfragestatus.

Der Fall bleibt aktiv und bearbeitbar. Die Aufhebung ist kein Fallabschluss und behauptet keine Behebung des gemeldeten Problems. Beispielsweise kann eine Rückfrage entfallen, weil der Techniker die Ursache inzwischen kennt, während die Behebung noch aussteht. Der Abschluss erfolgt separat nach FALL-07. **Ebenfalls bestätigt:** Entfällt durch die Aktion der letzte offene Antwortbedarf, folgt «In Bearbeitung», sofern keine laufende Wiedervorlage, externe Wartephase oder vorrangiger aktueller Mitarbeiterprüfbedarf bestehen. Die zentrale Statusregel und diese Ausnahmen sind in [FALL-05](fallverwaltung.md#status-nach-manuellem-aufheben-des-antwortbedarfs) festgelegt.

PropertyFlow prüft serverseitig die Berechtigung, den aktuellen Fall- und Antwortbedarf sowie die Zulässigkeit der Aktion im Fallprozess. Camunda veranlasst die Aufhebung und bestimmt einen allfälligen Statuswechsel nach [FALL-06](fallverwaltung.md#fall-06-fachlicher-status-und-technischer-workflow). PropertyFlow speichert die Aufhebung mit Bezug zur Nachricht, Zeitpunkt und handelnder Mitarbeiteridentität. Mieter können die Aktion nicht ausführen. Nach Fallabschluss steht sie nicht mehr zur Verfügung. Dann gilt KOM-04. Wiederholungen erzeugen keine zusätzliche Aufhebung und verspätete Auswertungen setzen den aufgehobenen Antwortbedarf nicht wieder. Eine nicht bestätigte oder fehlgeschlagene Speicherung wird nicht als Erfolg dargestellt. Ungesendeter Text, die Auswahl «Intern» und «Mieterantwort erforderlich» sowie die laufende KI-Formulierung bleiben erhalten.

### Prozesseingang für «Antwortbedarf aufheben»

**Bestätigt am 15.11.2026:** Auslöser ist die bewusste Mitarbeiteraktion an einer
veröffentlichten Rückfrage mit offenem Antwortbedarf. Der Dienst prüft Berechtigung
und aktuellen Fallstand; Camunda verarbeitet den Auftrag unabhängig von einer
neuen Mieternachricht oder KI-Auswertung. Die ursprüngliche Nachricht bleibt
unverändert im Verlauf und wird nicht als beantwortet markiert.

| Stand nach bestätigter Aufhebung | Fortsetzung |
|---|---|
| Weitere Rückfragen offen | Weiter warten; kein Statuswechsel durch die Aufhebung. |
| Letzter Antwortbedarf im Mieterwartepfad aufgehoben, kein vorrangiger Prüfbedarf | Direkt «In Bearbeitung» und weitere Mitarbeiterbearbeitung; keine erneute KI-Analyse. |
| Mitarbeiterprüfung aktiv oder vorrangiger aktueller Prüfbedarf | Bestehende Prüfung erhalten beziehungsweise notwendige Prüfung veranlassen; keine automatische Aufhebung des Prüfbedarfs. |
| Wiedervorlage läuft | Wiedervorlage und Timer bleiben bestehen. |

Die Aktion schliesst keinen Fall ab. Ein späteres KI-Ergebnis darf aufgehobenen
Antwortbedarf nicht reaktivieren. Wiederholungen erzeugen weder eine zweite
Aufhebung noch eine zweite Mitarbeiteraufgabe. Veraltete Aufträge nach Abschluss,
Wiedereröffnung oder Wechsel des Warteaufrufs dürfen keine neue Bearbeitung
unterbrechen. Entsteht vor der Fortsetzung neuer Antwortbedarf, wird der aktuelle
Stand geprüft und gegebenenfalls weiter gewartet statt eine alte Freigabe zu verwenden.
Der bestehende Mitarbeitereingang gilt auch ausserhalb des Mieterwartepfads als
Nebenaktion; er beendet keine externe Wartephase nach FALL-12.

### Manuelles Nachfragen während «Wartet auf Mieterantwort»

**Bestätigt am 15.11.2026 – B02.1:** Der Mitarbeiter verwendet die bestehende
Nachrichtenfunktion im Falldetail. Er schreibt den Text und setzt für eine
Rückfrage «Mieterantwort erforderlich». Es gibt keinen zusätzlichen Nachfragen-Button.

Der Dienst prüft Berechtigung, aktuellen Fallstand und den laufenden Mieterwarteaufruf.
Nachricht, optionaler Antwortbedarf und Mailpflicht werden konsistent gespeichert.
Camunda beauftragt den Mail-Worker über den bestehenden Versandprozess; die
Mieterwartephase wartet nicht auf SMTP. Der Sendeweg ist eine nicht unterbrechende
Nebenaktion: Status und bestehender Reply-Aufruf bleiben erhalten, es entsteht
kein zweiter Warteprozess und keine User Task wird abgeschlossen.

- Mit Antwortbedarf entsteht eine zusätzliche offene Rückfrage. Bestehende Fragen
  werden weder erledigt noch ersetzt. Ohne Antwortbedarf entsteht nur eine
  Informationsnachricht; bestehende Antwortbedarfe bleiben unverändert.
- Eine spätere Mieterantwort wird über den bestehenden zentralen Eingang ausgewertet.
  Der Antwortpfad prüft, welche offenen Fragen beantwortet sind. Läuft bereits eine
  Auswertung, muss sie neuen gespeicherten Antwortbedarf vor Fortsetzung berücksichtigen;
  eine alte Freigabe darf ihn nicht übergehen. Senden allein erzeugt keine Mieterantwort.
- Wurde der Fall inzwischen weitergeführt oder abgeschlossen, wird ein alter,
  unveröffentlichter Sendeauftrag ohne Wirkung abgelehnt; der Text bleibt für den
  Mitarbeiter erhalten. Auch andere relevante Versionskonflikte verlangen Prüfung
  des aktuellen Stands. Keine stillschweigende Umleitung in eine andere Phase.
- Eine Wiederholung desselben erfolgreichen Auftrags erzeugt weder eine doppelte
  Nachricht noch eine zweite Mailpflicht. Bereits gespeicherte Nachrichten und
  Versandpflichten bleiben bei späterer Fortsetzung bestehen.
- Version 1 erkennt nicht automatisch inhaltlich gleiche Rückfragen. Bewusst erneut
  gesendeter Text mit neuer Operationskennung ist eine neue Nachricht; mit gesetztem
  Antwortbedarf eine zusätzliche offene Rückfrage. Technische Wiederholung verwendet
  dagegen dieselbe Operationskennung und bleibt ohne doppelte Wirkung.

Im BPMN bildet `RStaffMessageSide` diese Nebenaktion innerhalb der Mieterantwort-
Kindinstanz ab. Ihre eigenen Auftragsdaten dürfen keine Hauptpfad-Ergebnisse
überschreiben. Technische Fehler folgen Retry/Incident. Autorisierung und
zuverlässige Korrelation mit dem tatsächlichen aktuellen Call sind noch umzusetzen.

## KOM-04: Nachrichtensperre nach Fallabschluss

**UI1-Präzisierung vom 13.10.2026:** Bei serverseitig bestätigt erkanntem Abschluss löscht die Mieteransicht ungesendeten Text und blendet die Eingabe aus; nach Wiedereröffnung erscheint sie leer. Netzwerkfehler allein lösen diese Löschung nicht aus. Gespeicherte Nachrichten bleiben erhalten. Die Mitarbeiteransicht UI3 behält dagegen ihre bisherigen Eingabe-/KI-Erhaltungsregeln. Details und Prüfkriterien stehen in [UI1](../frontend/ansicht-01-mieter-fallansicht.md#ungesendeter-mietertext-bei-abschluss).

Die Voraussetzungen des fachlichen Abschlusses und die Zeit danach sind in [FALL-07](fallverwaltung.md#fall-07-abschluss-und-zeit-danach) definiert. Dieser Abschnitt regelt die daraus folgende Nachrichtensperre.

Nach fachlichem Abschluss darf kein Absender neue Nachrichten zum Fall erzeugen: weder Mieter noch Mitarbeitende noch System-/KI-Komponenten. Dies gilt für interne Memos und externe Nachrichten. Eine Ausnahme für interne Memos während des abgeschlossenen Zustands ist nicht vorgesehen. Ausschliesslich Mitarbeitende können den Fall nach FALL-07 in UI3 bewusst wiedereröffnen. Erst nach bestätigter Rückkehr in die aktive Phase sind neue interne Memos und externe Nachrichten wieder zulässig; die Wiedereröffnung selbst ist keine Kommunikationsnachricht. Der berechtigte lesende Zugriff auf bestehende Nachrichten bleibt gemäss KOM-01 möglich.

**Bestätigt am 10.10.2026 – offener Antwortbedarf beim Abschluss:** Mit dem erfolgreichen fachlichen Fallabschluss endet auch der noch offene Antwortbedarf zu bisherigen Rückfragen. UI1 und UI3 fordern danach keine Mieterantwort mehr an. Der Fallstatus lautet «Abgeschlossen»; dafür wird kein zusätzlicher Fall- oder Rückfragestatus eingeführt. Die Beschreibung «nicht mehr erforderlich» bezeichnet lediglich diese Folge des Abschlusses. Die veröffentlichten Rückfragen und ihre ursprüngliche Kennzeichnung bleiben im Verlauf erhalten; unbeantwortete Fragen werden dadurch nicht als inhaltlich beantwortet ausgegeben. Ein abgebrochener oder fehlgeschlagener Abschluss beendet den Antwortbedarf nicht.

Die Prüfung wird serverseitig bei jeder Schreiboperation durchgesetzt, einschliesslich direkter HTTP-Aufrufe, verspäteter KI-Ergebnisse und wiederholter Worker-Ausführungen. Abschluss und Prüfen/Speichern einer Nachricht müssen konsistent synchronisiert werden; ein konkurrierender Request darf die Sperre nicht umgehen. Die konkrete Synchronisation wird im Service-/Persistenzdesign festgelegt.

Nach einer Wiedereröffnung dürfen verspätete automatische Ergebnisse aus der vorherigen Bearbeitung ebenfalls keine neue Nachricht oder andere Falländerung erzeugen. Massgeblich ist die [fachliche Grenze aus FALL-07](fallverwaltung.md#verspätete-automatische-ergebnisse-nach-wiedereröffnung). Bereits gespeicherte Nachrichten bleiben im Verlauf erhalten. Die technische Zuordnung solcher Ergebnisse wird später konkretisiert.

Die fachliche und technische Statusverantwortung sowie ihre Synchronisierung sind zentral in [FALL-06](fallverwaltung.md#fall-06-fachlicher-status-und-technischer-workflow) beschrieben.

E-Mail-Auslöser, kurzer Mailinhalt und zuverlässige Zustellung werden zentral in [Benachrichtigungen und Zustellung](benachrichtigungen-und-zustellung.md) geregelt. Auch eigene gespeicherte Mieter-Nachrichten und der fachliche Abschluss werden per E-Mail bestätigt; interne Memos und Entwürfe bleiben ausgeschlossen.

<a id="kom-05-automatische-rueckfragen"></a>

## KOM-05: Automatische Rückfragen

**Bestätigt am 14.11.2026:** PropertyFlow darf im laufenden Camunda-Fallprozess fehlende, für die weitere Beurteilung notwendige Sachangaben automatisch erfragen. Dafür gilt der folgende begrenzte Fragenkatalog. Diese Freigabe erlaubt weder individuelle Handlungsempfehlungen noch Kostenentscheidungen, externe Beauftragungen oder einen Fallabschluss. Die Regeln sind fachlich vereinbart, aber noch nicht implementiert.

### Freigegebener Fragenkatalog

| ID | Veröffentlichbarer Text | Zweck |
|---|---|---|
| RQ-001 | Seit wann besteht die Störung? | Fehlenden Beginn der gemeldeten Störung klären. |
| RQ-002 | In welchem Raum tritt sie auf? | Fehlenden Ort innerhalb des bereits zugeordneten Objekts klären. |
| RQ-003 | Tritt die Störung ständig oder zeitweise auf? | Fehlende Angaben zum zeitlichen Auftreten klären. |
| RQ-004 | Welche Fehlermeldung wird angezeigt? | Eine für die Beurteilung relevante, noch unbekannte angezeigte Fehlermeldung erfragen. |

Das LLM schlägt passende Frage-IDs vor. Der Dienst übernimmt ausschliesslich den zur freigegebenen Katalogfassung gehörenden Text und setzt den Antwortbedarf. Freie LLM-Ergänzungen werden nicht automatisch veröffentlicht. Eine notwendige Frage ausserhalb dieses Katalogs benötigt Mitarbeiterprüfung. RQ-002 ersetzt insbesondere keine offene Objektzuordnung.

### Voraussetzungen für automatische Veröffentlichung

Alle folgenden Bedingungen müssen unmittelbar vor dem Speichern der Nachricht erfüllt sein:

1. **Zulässiger Prozessschritt:** Der Fall ist aktiv; eine automatische Abklärung oder Antwortauswertung ist freigegeben. Während Mitarbeiterbearbeitung oder Wiedervorlage erfolgt keine parallele automatische Veröffentlichung. Zulässige manuelle Kommunikation bleibt davon unberührt.
2. **Kein offener Prüfgrund:** Es steht keine menschliche Entscheidung nach FALL-10 aus. Offene Objektzuordnung, festgestellter Analysefehler oder klärungsbedürftiger E-Mail-Versand führen nach FALL-05 zur Mitarbeiterprüfung. Bereits berücksichtigte Gründe lösen ohne neue Erkenntnisse keine erneute Prüfung aus; sie erlauben aber keine pauschalen Folgehandlungen. Ein noch ausstehender Versand allein gilt nicht als Versandfehler.
3. **Notwendige fehlende Angabe:** Die Frage passt zum konkreten Anliegen. Bekannte Angaben und bisherige Antworten werden berücksichtigt. Es werden nicht vorsorglich alle Katalogfragen gestellt.
4. **Begrenzter Inhalt:** Die Frage-ID und Katalogfassung sind freigegeben. Die veröffentlichte Nachricht enthält ausschliesslich die ausgewählten Katalogtexte, keine Diagnose, Kostenzusage, Beauftragung oder Handlungsanweisung.
5. **Kein wiederholter Antwortbedarf:** Bereits offene, beantwortete oder durch bekannte Angaben erledigte Fragen werden nicht erneut gestellt. Bewusst aufgehobener Antwortbedarf wird nicht durch eine erneute oder sinngleiche Frage reaktiviert.
6. **Aktueller Fallstand und Idempotenz:** Dienst und Persistenz prüfen aktuelle Fallversion, Prozessfreigabe und Antwortbedarf konsistent mit der Veröffentlichung. Eine inzwischen gespeicherte Antwort, Mitarbeiterentscheidung oder ein Abschluss darf nicht übergangen werden. Wiederholungen desselben Auftrags erzeugen keine zweite Nachricht und keinen zusätzlichen Antwortbedarf.

Erst die erfolgreiche Veröffentlichung erzeugt Antwortbedarf. Die Nachricht besitzt Systemherkunft und externe Sichtbarkeit. Camunda veranlasst den zulässigen Folgestatus nach FALL-05/FALL-06. Die E-Mail-Benachrichtigung folgt [BEN-01](benachrichtigungen-und-zustellung.md#ben-01-auslöser-und-ausschlüsse); die Veröffentlichung wartet nicht auf SMTP-Zustellung.

### Mitarbeiterprüfung oder Verarbeitung ohne neue Nachricht

Die Unterscheidung verhindert, dass jede unterdrückte Veröffentlichung eine neue Mitarbeiteraufgabe erzeugt:

| Situation | Folge |
|---|---|
| Offene menschliche Entscheidung zu Kosten oder verbindlichem Auftrag, sehr dringlicher/schwerwiegender Fall oder starke/eskalierte Beschwerde | Mitarbeiterprüfung gemäss FALL-10; keine automatische Rückfrage. |
| Offene Objektzuordnung, festgestellter Analysefehler oder klärungsbedürftiger Versand | Mitarbeiterprüfung gemäss FALL-05; keine automatische Rückfrage. |
| Widersprüche oder fehlende verlässliche Grundlage verhindern eine zuverlässige Fragewahl | Mitarbeiterprüfung. |
| Notwendige Frage ausserhalb des Katalogs, unbekannte Frage-ID oder individuell zu formulierende Anweisung | Mitarbeiterprüfung; kein Ersatz durch ungeprüften freien LLM-Text. |
| Ergebnis bezieht sich auf einen überholten Fallstand | Nicht veröffentlichen; bei weiter bestehendem Bedarf am aktuellen Fallstand neu beurteilen. Der Versionskonflikt allein ist kein fachlicher Prüfgrund. |
| Dieselbe Frage ist bereits offen | Keine Wiederholung; weiter auf die bestehende Rückfrage warten, sofern kein vorrangiger Prozessschritt greift. |
| Angabe ist bereits bekannt/beantwortet oder Antwortbedarf wurde bewusst aufgehoben | Keine neue Rückfrage und keine Reaktivierung; gemäss aktuellem Fall- und Prozessstand fortfahren. |
| Fall inzwischen abgeschlossen oder Ergebnis stammt aus einer Bearbeitung vor dem letzten Abschluss | Verwerfen; keine Nachricht und keine Wiedereröffnung durch dieses Ergebnis (KOM-04). |

Teilantworten erledigen nach KOM-03 nur die jeweils eindeutig beantworteten Fragen; andere bleiben offen. Blosser Antwortausstand oder eine Teilantwort erzeugt für sich keinen neuen Prüfgrund. Eine starre Höchstzahl automatischer Fragerunden ist nicht vereinbart. Sind **alle zuvor offenen Rückfragen beantwortet**, greift jedoch der vereinbarte Übergang zur Mitarbeiterprüfung, sofern kein zulässiger Systemabschluss erfolgt und keine laufende Wiedervorlage vorrangig ist. Diese Regel darf nicht durch das Stellen neuer Fragen umgangen werden. [FALL-05](fallverwaltung.md#status-nach-beantwortung-aller-rückfragen)

### Prüfung, Kontext und Nachvollziehbarkeit

Der Service Task **«Vorgehen bestimmen»** liefert die Empfehlung. **«Ausführungsbedingungen und Abschlussgründe prüfen»** setzt die festen Regeln zu Prozessfreigabe, aktuellem Fallstand, Katalog, Prüfbedarf und Wiederholung durch. Ob eine Angabe in freiem Mietertext bereits enthalten ist, eine Frage fachlich relevant ist oder ein Widerspruch vorliegt, bleibt eine inhaltliche Beurteilung. Ein gültiges Ausgabeformat oder eine hohe Selbsteinschätzung des LLM beweist deren Richtigkeit nicht. Die Serviceprüfung garantiert deshalb keine vollständige semantische Korrektheit. Ein zweiter LLM-Aufruf ist hierfür nicht vorgeschrieben; Grenzfälle sind mit einem versionierten Evaluationsbestand zu prüfen, unzuverlässige Ergebnisse führen zur Mitarbeiterprüfung.

Für die Frageauswahl gilt KOM-02 vollständig: keine internen Memos, internen Änderungsbegründungen oder daraus abgeleiteten Inhalte im Kommunikations-LLM. Auch eine anschliessende menschliche Prüfung erlaubt keine Ausnahme. Serverseitige Freigabeprüfungen dürfen den internen Prüfbedarf berücksichtigen, ohne ihn als Kommunikationskontext an das LLM zu übergeben.

Der interne Entscheidungsnachweis hält Fall- und Verarbeitungsbezug, geprüften Fallstand, Frage-IDs und Katalogfassung, angewandte Regeln, Zeitpunkt und Ergebnis fest: veröffentlicht, zur Mitarbeiterprüfung gegeben oder ohne neue Nachricht verworfen/zurückgestellt. Bei Veröffentlichung besteht ein Bezug zur gespeicherten Nachricht. Modell-/Prompt-Fassung und zulässige Quellen bleiben für die Empfehlung nachvollziehbar. Dies erlaubt weder das Speichern externer Entwürfe noch eine pauschale Protokollierung interner Inhalte oder vollständiger Prompts. Konkrete Verträge und Synchronisierung werden bei der Umsetzung festgelegt.

<a id="kom-06-automatische-antworten-vor-fallabschluss"></a>

## KOM-06: Automatische Antworten vor Fallabschluss

**Vereinfacht und bestätigt am 15.11.2026:** Bei fachlich zulässigem Systemabschluss
formuliert das LLM eine individuelle fallbezogene Nachricht. Feste Antwortvorlagen
und ein gesonderter Schritt zur Vorlagenfreigabe entfallen. Die beiden zulässigen
Abschlussgründe und menschlichen Entscheidungsgrenzen aus FALL-07/FALL-10 bleiben.

### Ablauf und Prüfung im Worker

1. Zulässigen automatischen Abschluss am aktuellen Fallstand feststellen.
2. Individuelle LLM-Nachricht erzeugen und im selben Worker prüfen. Keine unbelegten
   Aussagen oder internen Inhalte; Kontextgrenzen aus KOM-02 gelten weiter.
   Die Nachricht darf den Abschluss mitteilen. Kein zusätzlicher Prüf- oder
   Freigabedialog beim Mitarbeiter und kein Prozessentwurf im UI-Antwortfeld.
3. Nachricht mit Systemherkunft extern im Fallverlauf speichern und ihren
   E-Mail-Versand zuverlässig beauftragen. Dies gilt als «verschickt» für den
   Prozess; weder SMTP-Annahme noch Postfachzustellung abwarten. Kein neuer Antwortbedarf.
4. Aktuellen Fallstand nochmals prüfen und «Abgeschlossen» speichern.
   Danach keine zusätzliche Abschlussmail oder generische Statusmail für diesen Übergang.

Die LLM-Erzeugung läuft hinter dem Modell-Port. Hohe Modellkonfidenz oder gültiges
Schema ersetzen keine Inhaltsprüfung durch den Dienst. Kein zweites LLM ist
vorgeschrieben. Der Dienst prüft zudem Fall-/Prozessbezug, zulässigen Status,
Abschlussgrund, aktuelle Nachrichten und den Vorrang menschlicher Entscheidungen.
Mitarbeiterprüfung, Mitarbeiterbearbeitung und Wiedervorlage werden nicht übergangen.
Bei fehlender gültiger Fachregel ist kein automatischer Abschluss wegen fehlenden
Verwaltungsbedarfs zulässig; ein eigener freigegebener Erklärungstext ist nicht nötig.

### Fehler, Aktualität und Wiederholungen

Technische LLM-, Speicher- oder Bereitstellungsfehler werden als fehlgeschlagene
Camunda-Jobs mit konfigurierten Retries behandelt; ausgeschöpfte Versuche führen
zum Incident. Kein zusätzlicher fachlicher Fehlerpfad allein wegen technischer Fehler.
Eine inhaltlich unzulässige generierte Nachricht wird nicht veröffentlicht und
führt ohne automatischen Abschluss zur Mitarbeiterprüfung. Eine bestehende
Prüfung wird ergänzt, nicht dupliziert.

Veraltete Grundlagen vor Veröffentlichung führen über `PUBLICATION_BASIS_STALE`
zur aktuellen Auswertung und erneuten Prüfung. Trifft nach Veröffentlichung vor
Abschluss eine neue relevante Nachricht ein, gilt der Rückweg
`AUTO_CLOSURE_STALE`. Bereits gespeicherte Nachricht und Versandauftrag bleiben
erhalten und werden nicht erneut erzeugt. Jeder Abschlussversuch prüft Aktualität
und Mitarbeiterentscheidungen erneut. Bereits erfolgreicher identischer Abschluss
bleibt Erfolg. Offener Antwortbedarf endet erst mit erfolgreichem Abschluss.
Nach Abschluss oder aus einer früheren Bearbeitung keine späte neue Wirkung.

Versand im Hintergrund folgt BEN-03/BEN-04. Ein verzögerter Mailversand nimmt
einen gespeicherten Abschluss nicht zurück. Die zuverlässige Beauftragung und
Wiederholungsschutz müssen vor Ausführung technisch umgesetzt werden.

### Mitarbeiterabschluss und Nachvollziehbarkeit

Der direkte Mitarbeiterabschluss ist auch während «Wartet auf Mieterantwort»
möglich, ohne vorher den Antwortbedarf aufzuheben. Erst die erfolgreiche
Abschlussspeicherung beendet den offenen Bedarf; ein abgelehnter Auftrag oder
Konflikt tut dies nicht. Mitarbeitereingang und Konfliktpfade stehen in
[FALL-07](fallverwaltung.md#direkter-mitarbeiterabschluss-während-warten-auf-mieterantwort).


Mitarbeiter dürfen ohne Nachricht abschliessen. Bei Bedarf senden sie vorher
über das bestehende UI eine Nachricht, optional mit der bestehenden KI-
Formulierungshilfe. Kein zusätzliches Kommentarfeld und keine automatische
Nachricht oder Abschlussmail beim Mitarbeiterabschluss. Interner Abschlussnachweis,
Nachrichtensperre und Beendigung offener Wiedervorlagen bleiben bestehen.

Fall, Bearbeitung, geprüfte Grundlage, Abschlussgrund und Belege sowie gespeicherte
Nachricht und stabiler Veröffentlichungsauftrag bleiben nachvollziehbar. Keine
Vorlagenkennung oder vorgeschriebene Textfassung; bei regelgestütztem Abschluss
weiterhin Regelkennung/-version. Keine vollständigen Prompts oder internen Memos
pauschal protokollieren. Dies ist Spezifikation, keine Backend-Implementierung.

## Verantwortlichkeiten und Umsetzung

| Bestandteil | Pflicht |
|---|---|
| PropertyFlow-Anwendungsdienste | Fallberechtigung, Sichtbarkeit, Veröffentlichung und Abschlussregel bei jedem relevanten Zugriff durchsetzen. |
| Persistenz und Leseverträge | Sichtbarkeit und Veröffentlichungszustand erhalten; Mieteransichten nur mit zulässigen Daten versorgen. |
| Camunda-Worker | Fachliche Services verwenden; die Regeln auch bei Wiederholungen und verspäteten Ergebnissen einhalten. |
| Kommunikations-LLM-/RAG-Adapter | Zulässigen Generierungskontext nach KOM-02 herstellen und alle nachgeladenen Quellen ebenso prüfen. |
| Backoffice | Internes Memo und externe Veröffentlichung unterscheiden; berechtigte menschliche Entscheidungen unterstützen. |
| Mieteransicht und E-Mail an Mieter | Ausschliesslich autorisierte externe Kommunikation ausgeben; keine internen Inhalte oder Hinweise darauf transportieren. |

Konkrete DTOs, Datenbankschemata, APIs, Herkunftskennzeichnungen und technische Autorisierung werden in den jeweiligen Implementierungsverträgen festgelegt. Diese Spezifikation bestätigt keine bereits vorhandene Implementierung. Die [Modulgrenzen](../architecture/module-structure.md) und die [Evaluations- und Sicherheitsbasis](../evaluation/evaluationsgrundlage.md) gelten ergänzend.

## Prüfkriterien

Die Kriterien beschreiben Anforderungen an spätere Tests. Sie sind kein Testbericht. KOM-AK-01 bis KOM-AK-05 übernehmen die bisherigen Screen-Kriterien AK-20 bis AK-24 in derselben Reihenfolge.

### KOM-AK-01

Ein Fall mit einer Mieter-Nachricht, einem internen Mitarbeitermemo und einer veröffentlichten externen Antwort zeigt dem Mieter genau die beiden externen Nachrichten. Memo, Metadaten und Platzhalter fehlen auch im HTML und in Datenantworten.

### KOM-AK-02

Der Mieter kann ein internes Memo auch mit bekannter Nachrichten-ID oder manipuliertem Request weder lesen noch veröffentlichen. Das Mieterformular setzt die externe Sichtbarkeit serverseitig.

### KOM-AK-03

Das Speichern eines internen Memos erzeugt weder eine Mieter-E-Mail noch eine Änderung mieteröffentlicher Nachrichtenzahlen oder Aktualisierungszeitpunkte. Externe Entwürfe werden nicht persistiert. Erst die bewusste Veröffentlichung speichert die externe Nachricht und macht sie für den Mieter sichtbar.

### KOM-AK-04

Ein Testfall mit einem ausschliesslich im internen Memo vorhandenen und nicht vom Mitarbeiter zur Formulierung eingegebenen Marker weist nach, dass dieser weder im initialen Kommunikations-LLM-Request noch in nachgeladenen Retrieval-/Tool-Ergebnissen, Zusammenfassungen oder wiederverwendetem Gesprächskontext enthalten ist. Eine unauffällige fertige Antwort allein genügt nicht als Nachweis. Die Prüfung umfasst Mitarbeiter- und Systemmemos sowie indirekte Bereitstellung aus Suchindizes und Camunda-Variablen.

### KOM-AK-05

Hinzufügen oder Ändern eines internen Memos verändert bei ansonsten gleichen zulässigen Quellen einschliesslich unveränderter Mitarbeitereingabe den fachlichen Eingabekontext der Kommunikationsgenerierung nicht. Systemseitige Memo-Ableitungen und zusätzlicher Kontext unklarer Herkunft bleiben auch bei Camunda-Wiederholungen ausgeschlossen; getestet wird der Eingabekontext, nicht die wortgleiche Ausgabe eines LLM.

Als positiver Gegenfall ist die bewusst eingegebene Sachanweisung «Techniker kommt am Dienstag. Bitte Zugang zur Wohnung ermöglichen.» zulässig, obwohl der Termin auch in einem Memo steht. Nur im Memo vorhandene Kostenfreigaben oder Offertvorgaben fehlen im Request und in nachgeladenem Kontext. Ein automatischer Memo-Abruf oder eine serverseitig erzeugte Memo-Zusammenfassung darf sich nicht als Mitarbeitereingabe ausgeben. Der Mitarbeiter kann den Vorschlag prüfen und bearbeiten; erst «Nachricht senden» veröffentlicht ihn. Eine Änderung des Toggles allein liest keine Memos ein und startet keine Generierung.

### KOM-AK-06

Eine Mieterberechtigung ermöglicht keinen KI-/Prozessabbruch, auch nicht über direkte Backend-Aufrufe. Ein Test prüft, dass Seitenwechsel oder Verbindungsabbruch die asynchrone Verarbeitung nicht beendet.

### KOM-AK-07

Nach Abschluss werden interne und externe Nachrichtenschreiboperationen aller Absender abgelehnt, einschliesslich einer Mieternachricht aus einer noch geöffneten veralteten Ansicht oder über einen direkten Backend-Aufruf. Ein gezielter Konkurrenztest sowie ein verspätetes Worker-Ergebnis prüfen die Synchronisierung von Abschluss und Nachrichtenspeicherung. Mit erfolgreichem Abschluss endet jeder noch offene Mieterantwortbedarf; UI1 und UI3 fordern keine Antwort mehr an. Veröffentlichte Rückfragen bleiben erhalten und gelten ohne passende Antwort nicht als inhaltlich beantwortet. Es entsteht kein zusätzlicher Fall- oder Rückfragestatus. Ein abgebrochener oder fehlgeschlagener Abschluss lässt den bisherigen Antwortbedarf bestehen.

Auch nach einer bestätigten Wiedereröffnung erzeugt ein verspätetes automatisches Ergebnis aus der Bearbeitung vor dem letzten Abschluss keine neue Nachricht. Die vorhandene Historie bleibt erhalten; neu autorisierte Kommunikation zum aktuellen Fallstand bleibt möglich. FALL-AK-16 prüft die übergreifenden Folgen für den wiedereröffneten Fall.

### KOM-AK-08

Ein ausschliesslich in einem gespeicherten internen Memo vorhandener Suchbegriff liefert den Fall für jeden Mitarbeiter. Ein Begriff, der ausschliesslich in einem externen Entwurf oder einer KI-Teilantwort vorkommt, liefert keinen Suchtreffer. Der Suchindex erweitert weder den Mieterzugriff noch den zulässigen Kommunikations-LLM-Kontext. Die übrigen Such-/Filterregeln werden in Screen 02 geprüft.

### KOM-AK-09

Eine neue Mieternachricht wird inhaltlich gegen jede offene Rückfrage geprüft. Eine eindeutig beantwortete Frage wird erledigt; eine unvollständig, unklar oder themenfremd beantwortete Frage bleibt offen. Bei zwei offenen Fragen erledigt die Antwort auf nur eine davon ausschliesslich diese Frage; eine Nachricht darf beide erledigen, wenn sie beide eindeutig beantwortet. Antwortbedarf und Erledigungsstand bleiben in UI1 und UI3 konsistent; der veröffentlichte Nachrichtentext bleibt erhalten. Fehlgeschlagene Auswertungen und Wiederholungen erzeugen keine falsche oder doppelte Erledigung. Bei laufender Wiedervorlage erhält der Mitarbeiter den Fall bereits beim Nachrichteneingang nach FALL-11 zur Prüfung.

Sind alle zuvor offenen Rückfragen beantwortet und ist die automatische Verarbeitung abgeschlossen, erhält der aktive Fall ohne laufende Wiedervorlage «Mitarbeiterprüfung erforderlich». Ein zulässiger automatischer Abschluss nach FALL-07/FALL-10 führt stattdessen zu «Abgeschlossen», sofern noch keine Mitarbeiterprüfung läuft. Eine bereits bestehende Mitarbeiterprüfung bleibt nach FALL-06 bis zur bewussten Mitarbeiterentscheidung erhalten, unabhängig von ihrer Herkunft. Eine während der Wiedervorlage neu gespeicherte Nachricht führt nach FALL-11 unabhängig vom Ergebnis der Auswertung zur Mitarbeiterprüfung. Eine Teilantwort oder eine reine Informationsnachricht ohne zuvor offene Rückfragen löst den Übergang wegen vollständiger Beantwortung nicht aus. Wiederholte Auswertungen bereits berücksichtigter Antworten nehmen eine anschliessende bewusste Mitarbeiterentscheidung nicht zurück. Die angezeigten Fallstatus in UI1–UI3 entsprechen dem gespeicherten Ergebnis.

### KOM-AK-10

Ein Mitarbeiter hebt in UI3 gezielt den Antwortbedarf einer veröffentlichten Rückfrage auf. UI1 und UI3 zeigen danach für diese Nachricht keinen offenen Antwortbedarf; der offene Antwortbedarf einer zweiten Nachricht bleibt unverändert. Nachrichtentext und ursprüngliche Kennzeichnung bleiben erhalten, ohne eine erfolgte Beantwortung zu behaupten. Entfällt der letzte offene Antwortbedarf, folgt «In Bearbeitung»; eine laufende Wiedervorlage und vorrangiger aktueller Mitarbeiterprüfbedarf bleiben nach FALL-05/FALL-11 wirksam. Verbleibender Antwortbedarf löst durch die Aktion allein keinen Statuswechsel aus. Fall und ausstehende Bearbeitung bleiben aktiv. Es entsteht kein zusätzlicher Fall- oder Rückfragestatus. Die Aufhebung ist mit Nachricht, Mitarbeiter und Zeitpunkt nachvollziehbar. Mieteraufrufe und Änderungen an abgeschlossenen Fällen werden abgelehnt. Wiederholte Aufrufe, verspätete Auswertungen sowie ein konkurrierender Fallabschluss oder neu hinzugekommener Antwortbedarf erzeugen keine falsche oder doppelte Änderung. Ein abgelehnter Vorgang speichert weder eine Aufhebung noch deren Statusfolge. Bei Fehlschlag wird kein Erfolg vorgetäuscht; Mitarbeitereingabe und aktiver KI-Stream bleiben erhalten.

### KOM-AK-11: Interne Änderungsbegründungen

Ein ausschliesslich in einer internen Begründung vorhandener Marker wird bei «Bearbeitung fortsetzen», Setzen, Ändern und Löschen der Wiedervorlage sowie manueller Dringlichkeitsänderung geprüft. Er ist für berechtigte Mitarbeitende dem Änderungsnachweis zugeordnet, fehlt aber in Mieter-HTML, Datenantworten, Vorschauen und E-Mails. Der Ausschluss umfasst den initialen Kommunikations-LLM-Request, nachgeladene Quellen, Zusammenfassungen und wiederverwendeten Kontext. Eine unauffällige fertige LLM-Antwort allein genügt nicht als Nachweis. Es entsteht keine separate Kommunikationsnachricht oder Memo-Speicherung. Eine Benachrichtigung über die fachliche Änderung enthält die Begründung nicht. KOM-AK-04/05 gelten für diese Quellen entsprechend.

### KOM-AK-12 · Längengrenze für Mieternachrichten

Eine Mieternachricht mit genau 2’000 UTF-16-Codeeinheiten nach der Zeilenumbruchnormalisierung wird bei gültigem Fallzugriff und verfügbarem Fallprozess angenommen. Eine Nachricht mit 2’001 Codeeinheiten wird auch bei direkter Übermittlung an das Backend ohne neuen Verlaufseintrag abgewiesen. Im Formular bleibt der vollständige Text editierbar und der Hinweis nennt die Grenze.

### KOM-AK-13: Katalog und Veröffentlichungsfreigabe

Bei einem zugeordneten Fall in automatischer Abklärung fehlen Beginn und betroffener Raum der Störung. Liegt kein Prüfgrund vor, werden nur die passenden RQ-001/RQ-002-Texte mit Systemherkunft, externer Sichtbarkeit und Antwortbedarf veröffentlicht. Bereits bekannte Angaben werden nicht erneut erfragt. Unbekannte IDs, nicht freigegebene Katalogfassungen und freie LLM-Zusätze werden nicht veröffentlicht. Eine notwendige Frage ausserhalb des Katalogs wird zur Mitarbeiterprüfung gegeben. Die fachliche Auswahl wird zusätzlich an positiven, negativen und widersprüchlichen Beispielen evaluiert; ein Schema-Test allein genügt nicht.

### KOM-AK-14: Vorrang menschlicher Entscheidungen

Offene Objektzuordnung, festgestellter Analysefehler, klärungsbedürftiger Versand sowie jeder FALL-10-Prüfgrund verhindern automatische Veröffentlichung und führen zur Mitarbeiterprüfung. Ein noch ausstehender Versand allein löst diesen Fehlerpfad nicht aus. Laufende Mitarbeiterbearbeitung und Wiedervorlage werden nicht durch automatische Rückfragen verdrängt; zulässige manuelle Rückfragen bleiben möglich. Eine bekannte niedrige Dringlichkeit hebt andere Prüfgründe nicht auf. RQ-002 darf eine fehlende Objektzuordnung nicht ersetzen.

### KOM-AK-15: Wiederholungen und konkurrierende Änderungen

Ein wiederholter oder parallel ausgeführter Veröffentlichungsauftrag erzeugt höchstens eine Nachricht mit einmaligem Antwortbedarf. Eine zwischen Auswahl und Veröffentlichung gespeicherte Antwort, Mitarbeiterentscheidung oder ein Abschluss wird berücksichtigt. Ein überholtes Ergebnis erzeugt weder eine Nachricht noch allein deshalb Mitarbeiterprüfung. Bereits offene, beantwortete oder bewusst aufgehobene Fragen werden nicht wiederholt bzw. reaktiviert. Nach Wiedereröffnung bleibt ein Ergebnis aus der vorherigen Bearbeitung unwirksam. Bei gescheiterter Speicherung entsteht kein Antwortbedarf.

### KOM-AK-16: Antwortauswertung und Kontextgrenze

Eine Teilantwort erledigt nur die beantwortete Frage; andere bleiben offen, ohne allein dadurch Mitarbeiterprüfung auszulösen. Nach Beantwortung aller zuvor offenen Rückfragen führt der Fall ohne zulässigen Systemabschluss und ohne laufende Wiedervorlage zur Mitarbeiterprüfung; neue Katalogfragen umgehen diesen Übergang nicht. KOM-AK-04/05 prüfen auch bei der Auswahl von Frage-IDs den Ausschluss interner Inhalte und Ableitungen. Ein interner Prüfgrund kann die serverseitige Veröffentlichung sperren, ohne in den Kommunikations-LLM-Request zu gelangen. Der Entscheidungsnachweis unterscheidet Veröffentlichung, Prüfbedarf und Verarbeitung ohne neue Nachricht.

### KOM-AK-17: Durchgängiger Mieternachrichteneingang

In jedem der sechs aktiven Bearbeitungsstatus eine neue Mieternachricht mit gültigem Zugriff, laufendem Prozess und erreichbarem Camunda übermitteln, jeweils auch ohne offene Rückfrage. Die Nachricht wird genau einmal gespeichert und an den bestehenden Fallprozess übergeben. Auch während eines Wechsels zwischen Analyse, Mitarbeiteraufgabe und Wartephase darf keine fehlende Empfangsbereitschaft allein wegen der Prozessposition entstehen. Wiederholungen erzeugen weder doppelte fachliche Wirkung derselben Nachricht noch eine zusätzliche Mitarbeiteraufgabe. Bei «Mitarbeiterprüfung erforderlich» ergänzen Nachricht und zulässige Auswertung dieselbe Aufgabe; der Status bleibt auch bei einer Behebungsbestätigung unverändert (FALL-AK-21). Bei Wiedervorlage gilt weiterhin die sofortige Aufhebung und Vorlage nach FALL-11. Fehlender Zugriff, noch nicht gestarteter Prozess, nicht erreichbares Camunda und abgeschlossener Fall behalten ihre bisherigen Fehler- beziehungsweise Sperrregeln.

### KOM-AK-18: Individuelle LLM-Nachricht und Fehler

Beide zulässigen Abschlussgründe mit individuellen Nachrichten prüfen. Keine feste
Textvorlage erforderlich. Unbelegte Aussagen oder interne Inhalte verhindern
Veröffentlichung und automatischen Abschluss und führen zur Mitarbeiterprüfung.
Technischer Modellfehler verwendet Job-Retries/Incident statt Inhaltsfehlerpfad.
Die Nachricht darf den Abschluss mitteilen. Kein automatischer Entwurf im Mitarbeiter-UI.

### KOM-AK-19: Veröffentlichungsschutz und Kontext

Nur bei zulässigem Systemabschluss und aktueller Grundlage veröffentlichen.
Mitarbeiterentscheidungen und manuelle Einstufungen schützen, KOM-02 einhalten.
Neue Nachricht während Generierung verhindert Veröffentlichung des veralteten Textes.
Nachricht und zuverlässiger Versandauftrag müssen vor Abschluss gespeichert sein;
ein langsamer Mailserver blockiert den Abschluss nicht.

### KOM-AK-20: Nachricht gespeichert, Abschluss ausgeblieben

Nach Veröffentlichung neue Nachricht oder technische Speicherstörung auslösen.
Bei neuer Grundlage erneut prüfen; bei technischer Störung Job-Retry. Bereits
gespeicherte Nachricht und Versandauftrag bleiben einmalig. Ohne Commit kein
Abschluss. Auch nach erfolgreichem Abschluss kein zusätzlicher Abschlussmailauftrag.

### KOM-AK-21: Wiederholungen und Mitarbeiterabschluss

Parallele Wiederholungen erzeugen eine Nachricht und einen logischen Versandauftrag.
Erfolgreicher identischer Abschluss bleibt Erfolg; verspätete Ergebnisse nach
Abschluss/Wiedereröffnung bleiben unwirksam. Mitarbeiterabschluss ohne vorherige
Nachricht ist möglich und erzeugt weder Nachricht noch Mail, auch keine generische
Statusmail. Eine freiwillig vorher gesendete Mitarbeiternachricht bleibt unabhängig.

### KOM-AK-22: Neue Nachricht während automatischer Analyse

Während Dringlichkeitsbewertung beziehungsweise Empfehlung eine neue Mieternachricht speichern. Das alte Ergebnis darf keine Veröffentlichung, Abschlussaktion oder Überschreibung aktueller Einstufungen bewirken. Eine bereits fertige aktuelle Auswertung wird übernommen, eine laufende abgewartet und eine fehlende über die zentrale Verarbeitung veranlasst; keine doppelte unabhängige Analyse derselben Grundlage. Vorhandene und neue relevante Nachrichten gehen gemeinsam in die anschliessende Ausführungsprüfung ein. Eine weitere Nachricht während des Wartens verhindert die Verwendung des erneut überholten Ergebnisses. Ohne vorrangige andere Entscheidung bleibt «In Abklärung» bestehen; das Alter des Ergebnisses allein erzeugt keine Mitarbeiteraufgabe. Zwischenzeitliche Mitarbeiterentscheidung, Abschluss und tatsächlichen Analysefehler gesondert prüfen. Beispiel: «Heizung funktioniert nicht» und während der Analyse «Jetzt funktioniert sie wieder» werden gemeinsam geprüft; daraus folgt noch keine pauschale Abschlussfreigabe.

### KOM-AK-23: Nachricht zwischen Prüfung und Veröffentlichung

Für Katalogrückfrage und automatische Antwort jeweils eine neue Mieternachricht nach Ausführungsprüfung, aber vor Veröffentlichung speichern. Die alte Vorlage wird nicht gespeichert; kein neuer Antwortbedarf und kein Benachrichtigungsauftrag entstehen. Der Rückweg übernimmt eine aktuelle zentrale Auswertung und prüft erneut, ohne Statuswechsel allein durch den Rückweg. Beispiel: Vorbereitete Frage «Seit wann besteht die Störung?» und neue Nachricht «Seit gestern Abend»; die dadurch erledigte Frage wird nicht mehr gestellt. Bestehende Rückfragen und die Regel nach vollständiger Beantwortung bleiben wirksam. Mitarbeiterentscheidung und Abschluss während des Rückwegs dürfen nicht übergangen werden.

Den konkurrierenden Ablauf in beiden Speicherreihenfolgen prüfen: Mieternachricht zuerst unterdrückt die überholte Veröffentlichung; Veröffentlichung zuerst bleibt einmalig gespeichert und die Mieternachricht wird danach gegen den aktuellen Verlauf ausgewertet. Bereits bestehende Nachrichten, Antwortbedarf und Mailaufträge werden nicht zurückgenommen. Wiederholung nach erfolgreicher Veröffentlichung erzeugt keine zweite Nachricht und keinen zusätzlichen Mailauftrag; ein technischer Speicherfehler allein ist kein fachlicher Versionskonflikt. Prüfkriterien beschreiben spätere Integrationsnachweise, keine bereits implementierte Synchronisation.

### KOM-AK-24: Fortsetzungen nach Mieterantwort

Alle fünf Prioritäten aus «Fortsetzung nach einer Mieterantwort im Wartestatus» mit aktuellen Auswertungen prüfen. Pflichtprüfbedarf oder Analysefehler schlägt einen gleichzeitig vorgeschlagenen Systemabschluss. Ein zulässiger Abschluss benötigt KOM-06 und eine erneute Abschlussprüfung. Vollständige Beantwortung ohne zulässigen Abschluss führt zur Mitarbeiterprüfung, auch wenn das Modell eine weitere Frage vorschlägt. Eine Teilantwort ohne weiteren Grund bleibt im Wartestatus; nur die eindeutig beantwortete Frage endet. Eine zusätzliche notwendige zulässige Katalogfrage wird einmalig veröffentlicht; vorhandene Fragen werden nicht wiederholt. Unzuverlässige Einordnung oder notwendige Frage ausserhalb des Katalogs nicht als blosses Weiterwarten behandeln.

Das Beispiel mit Beginn und Raum prüfen, einschliesslich zuerst fertiger Auswertung vor Eintritt in den Warteschritt, erneuter Zustellung und weiterer Nachricht während der Antwortprüfung. Nur aktueller Ergebnisstand entscheidet, kein zweiter Analysepfad und kein wiederholter Verbrauch eines bereits entschiedenen Ergebnisses. Rückkehr aus Zeile 2 mit verbliebenen offenen Fragen ohne weitere Aktion führt ebenfalls zum Antwortwarten. Ein überholtes Ergebnis allein erzeugt keine Mitarbeiterprüfung. Zwischenzeitliche Mitarbeiterentscheidung, Wiedervorlage und Abschluss bleiben geschützt. Dies sind Anforderungen an spätere Tests, keine bereits ausgeführten Laufzeitnachweise.

### KOM-AK-25: Worker-Schutz und veraltete Nachrichtenauswertung

Während offener Mitarbeiterprüfung zwei Nachrichten und vertauscht eintreffende
KI-Ergebnisse prüfen: dieselbe Aufgabe bleibt offen, nur die aktuelle Grundlage
wird ergänzt. «Alles behoben» beendet weder Aufgabe noch Fall. Eine inzwischen
erledigte Aufgabe wird nicht reaktiviert. Manuelle Dringlichkeit bleibt erhalten.
Fehler ergänzen eine bestehende Prüfung, ohne eine zweite Aufgabe zu erzeugen.

Am Bereitstellungs-Worker eine neue Nachricht oder Mitarbeiterentscheidung vor
dem Commit auslösen: keine veraltete fachliche Änderung und kein Bereitschaftssignal.
Der BPMN-Rückweg übernimmt eine fertige aktuelle Auswertung, wartet auf eine
laufende oder veranlasst eine fehlende über dieselbe zentrale Verarbeitung.
Kein Warten auf das eigene verworfene Ergebnis und keine doppelte unabhängige
Analyse derselben Grundlage. Eine weitere Änderung erzwingt erneut die Prüfung.
Abschluss, Wiedereröffnung mit neuer Bearbeitung und bereits wirksam verarbeitete
Ergebnisse führen zum Ende ohne neue Wirkung. Technische Speicherfehler nutzen
Retry statt fachlichem Konfliktpfad; erfolgreiche Wiederholungen bleiben idempotent.
Dies sind Anforderungen an spätere Laufzeittests, keine implementierten Nachweise.

### Veralteter automatischer Abschluss nach veröffentlichter Antwort

**Bestätigt am 15.11.2026:** Zwischen automatischer Antwort und Abschluss prüft
`Close` die aktuelle Grundlage nochmals. Ist sie weiterhin gültig, wird der Fall
abgeschlossen; danach entsteht keine zusätzliche Abschlussbenachrichtigung.

Macht eine neue relevante Mieternachricht die noch nicht wirksam gespeicherte
Systemabschlussgrundlage veraltet, meldet der Dienst ausdrücklich
`AUTO_CLOSURE_STALE`. Der unterbrechende Rückweg führt über `CurrentAnalysis`
(aktuelle zentrale Auswertung übernehmen/abwarten) erneut zu `Validate`
(Ausführungsbedingungen und Abschlussgründe prüfen). Kein direkter Rücksprung
zum Abschluss und keine automatische Mitarbeiterprüfung allein wegen des Konflikts.
Alle relevanten Nachrichten und die bereits veröffentlichte Antwort gehen in
die neue Prüfung ein. Eine zwischenzeitliche Mitarbeiterentscheidung bleibt
vorrangig; der Rückweg reaktiviert keine alte Aufgabe, setzt keinen Status zurück
und nimmt keinen gespeicherten Abschluss zurück.

Die veröffentlichte Antwort und ihr logischer Benachrichtigungsauftrag bleiben
unverändert erhalten. Ihr Veröffentlichungsbezug muss auch über den Rückweg
und Teilprozessaufrufe erhalten beziehungsweise aus PropertyFlow geladen werden.
Wird nach erneuter fachlicher Prüfung derselbe Antwortauftrag wieder erreicht,
erkennt `AutoAnswer` ihn als bereits veröffentlicht und speichert weder dieselbe
Antwort noch deren Mailauftrag erneut. Das ersetzt weder die aktuelle Inhalts-/
Ausführungsprüfung noch die erneute Prüfung in `Close`; ein altes Ergebnis ist
keine dauerhafte Freigabe. Eine sachlich andere Antwort ist kein Retry und
unterliegt erneut allen Regeln aus KOM-06. Die konkrete Zuordnung der Aufträge
und Ergebnisvariablen bleibt vor ausführbarer Umsetzung festzulegen.

Ein rein technischer Speicherfehler verwendet technischen Retry statt
`AUTO_CLOSURE_STALE`: beim erneuten Versuch Aktualität prüfen, Antwort nicht
nochmals senden. Bereits erfolgreich gespeicherter identischer Abschluss wird
idempotent als Erfolg behandelt, ohne zweiten Abschluss oder Abschlussmailauftrag.
`STAFF_CLOSURE_STALE` bleibt ausschliesslich für veraltete Mitarbeiterabschlüsse.
Ohne erfolgreichen Abschluss bleiben Fall und noch offener Antwortbedarf aktiv;
der normale Weg zum terminierenden Ende wird nicht genommen.

### KOM-AK-26: Konflikt nach veröffentlichter automatischer Antwort

Antwort erfolgreich speichern, dann vor Abschluss eine neue Nachricht speichern:
keine Abschlussspeicherung, Rückweg über aktuelle Auswertung und erneute Prüfung.
Die vorhandene Antwort bleibt genau einmal erhalten. Bei erneut zulässigem
identischem Antwortauftrag keine zweite Antwort und kein zweiter Antwortmailauftrag;
aktuelle Abschlussbedingungen trotzdem prüfen. Weitere Nachricht während des
Rückwegs, inzwischen getroffene Mitarbeiterentscheidung und zwischenzeitlichen
Abschluss gesondert prüfen: keine Rücknahme oder Wiederaufnahme. Technischen
Speicherfehler getrennt auslösen: Retry ohne künstliche Mitarbeiterprüfung.
Erfolgreichen identischen Abschluss wiederholen: ein Abschluss und kein zusätzlicher
Abschlussmailauftrag. Ein tatsächlich anderer Antwortauftrag benötigt eine neue
fachliche Freigabe. Dies sind spätere Laufzeitprüfungen, keine implementierten Tests.

### KOM-AK-27: Mitarbeiternachricht während offener Prüfung

Normale Nachricht und Nachricht mit Antwortbedarf jeweils während offener Prüfung
senden: eine externe Nachricht und ein logischer Versandauftrag, unveränderte
User-Task-ID und Status «Mitarbeiterprüfung erforderlich». Nur beim gesetzten
Toggle entsteht neuer Antwortbedarf. Wiederholungen duplizieren nichts; zwei
verschiedene Aufträge behalten getrennte Inhalte und Kennungen. Mieterantwort
und KI-Auswertung ergänzen dieselbe Aufgabe, ohne sie zu erledigen.

Fremden Fall, unberechtigten Mitarbeiter, falsche Kind-/Aufgabenzuordnung und
bereits erledigte Prüfaufgabe ablehnen. Senden gegen Entscheidung/Abschluss in
beiden Speicherreihenfolgen prüfen: gespeicherte Nachricht bleibt, veralteter
noch unveröffentlichter Auftrag bleibt ohne Wirkung. Technischer Fehler lässt
Prüfung offen; Wiederholung prüft erneut. Kein Überschreiben der Hauptpfad-
Entscheidung durch Nebenpfadvariablen. Diese Kriterien sind spätere
Integrationstests, keine bereits ausgeführten Laufzeitnachweise.

### KOM-AK-28: Keine Antwortfrist oder automatische Erinnerung

Zeitablauf allein bei vollständig ausbleibender Antwort und nach Teilantwort
prüfen: keine Erinnerung, Eskalation, Antwortbedarfsaufhebung oder Schliessung;
Status und verbleibender Antwortbedarf bleiben unverändert. Späte Mieterantworten
werden bei weiterhin aktivem Fall nach den normalen Regeln verarbeitet.
Manuelles Nachfragen, bewusstes Aufheben des Antwortbedarfs und zulässiger
Mitarbeiterabschluss bleiben möglich. Eine separat gesetzte Wiedervorlage wird
weiterhin zum vereinbarten Termin fällig. Dies sind geplante Integrationsprüfungen,
keine ausgeführten Laufzeittests.

### KOM-AK-29: Mitarbeitergesteuerte Aufhebung im Prozess

Ohne neue Mieternachricht und ohne KI-Ergebnis eine offene Rückfrage aufheben:
bei verbleibenden Fragen weiter warten; bei letzter Frage direkt Mitarbeiter-
bearbeitung ohne neue Analyse. Prüfung und Wiedervorlage jeweils unverändert
fortführen. Nachricht bleibt erhalten, nicht als beantwortet markieren, kein
Fallabschluss. Doppelte Aufträge, neue Fragen während Fortsetzung, alte
KI-Ergebnisse und verspätete Aktionen nach Abschluss oder Wiedereröffnung prüfen:
keine Reaktivierung, doppelte Aufgabe oder Überschreibung aktueller Entscheidungen.
Dies sind geplante Integrationsprüfungen, keine ausgeführten Laufzeittests.

## Abgleich mit bestehenden Dokumenten

**Geltungsbereich geklärt (09.10.2026):** Streaming und Abbruch gemäss [ADR-003](../architecture/adr/ADR-003-praesentationsschicht.md) und [UC-004](../use_cases/UC-004-ki-analyse-durchfuehren.md) gehören zur Mitarbeiteransicht im Backoffice. **Präzisiert am 10.10.2026:** Die interaktive Mitarbeiteranfrage dient der Formulierung einer externen Nachricht aus einer kurzen fachlichen Mitarbeitereingabe, nicht dem Start einer Fallanalyse. Fallanalyse und Dringlichkeitsbewertung laufen automatisch im Camunda-Fallprozess. Das System speichert Analyseinformationen als Nachrichten mit expliziter Sichtbarkeit; interne Systemmemos bleiben intern. Der Formulierungsvorschlag wird als ungespeicherter Stream angezeigt, vom Mitarbeiter geprüft und gegebenenfalls bearbeitet. Erst «Nachricht senden» persistiert und veröffentlicht die externe Nachricht. KOM-02 schliesst die systemseitige Übernahme von Mitarbeiter- und Systemmemos sowie deren Ableitungen aus; bewusst eingegebene kurze Sachanweisungen des Mitarbeiters sind zulässig. Für den Mieter gilt KOM-03: kein direkter Chat-Stream und kein KI-/Prozessabbruch; periodische Leseabrufe zeigen gespeicherte Ergebnisse. Ein Mitarbeiterabbruch einer KI-Anfrage schliesst den Fall nicht und ist kein pauschaler Abbruch des führenden Camunda-Fallprozesses. Dieser fachliche Abgleich ist abgeschlossen; technische Streaming- und Abbruchverträge der Mitarbeiteransicht werden bei ihrer Umsetzung konkretisiert.

Fallidentität, Annahme und Lebenszyklus werden zentral in der [Fallverwaltung](fallverwaltung.md) gepflegt. Direkter persönlicher Tokenzugriff und Berechtigungen werden zentral in [Fallzugriff und Sicherheit](fallzugriff-und-sicherheit.md) gepflegt. Screen-spezifische Felder, Routenübersicht, Wireframes und Bedienungsregeln bleiben in [Screen 01](../frontend/ansicht-01-mieter-fallansicht.md), [Screen 02](../frontend/ansicht-02-mitarbeiter-falluebersicht.md) und [Screen 03](../frontend/ansicht-03-mitarbeiter-falldetail.md). Erweiterungen der grundlegenden Kommunikationsregeln werden hier vorgenommen und von den betroffenen Screens und Use-Cases referenziert.

### Historischer und aktueller Antwortbedarf – Präzisierung vom 13.10.2026

Die ursprüngliche Kennzeichnung «Mieterantwort erforderlich» bleibt unveränderlich erhalten. Der aktuelle Antwortbedarf wird separat geführt. Manuelles Aufheben hält Ursache, Zeitpunkt und Bezug zur betroffenen Nachricht fest; Fallabschluss beendet nur noch offene Rückfragen. Bereits beendete Rückfragen behalten ihre bisherige Ursache und ihren Zeitpunkt. Wiedereröffnung reaktiviert keinen alten Antwortbedarf. Die Verlaufsanzeige in UI1 und UI3 unterscheidet «Antwortbedarf manuell aufgehoben» von «Antwortbedarf durch Fallabschluss beendet», jeweils mit Zeitpunkt und historischer Kennzeichnung. Keiner dieser Vermerke behauptet eine inhaltliche Beantwortung. Die automatische inhaltliche Beantwortungsprüfung bleibt eine separate spätere Integration.

### Wiederholung einer Nachricht mit abweichendem Inhalt – Präzisierung vom 13.10.2026

Bei Nachrichten bindet die erfolgreiche fallgebundene Absende-ID den ursprünglichen Anfrageinhalt: Text, interne/externe Auswahl, Antwortbedarf und Absenderrolle. Identische Wiederholungen erzeugen keine weitere Nachricht und bestätigen die frühere Speicherung, auch nach zwischenzeitlichem Abschluss. Dieselbe ID mit abweichendem Inhalt wird mit Konflikt zurückgewiesen; der geänderte Inhalt wird nicht gespeichert und nicht als erfolgreich übernommen ausgegeben. Im aktiven Fall bleiben Text und Auswahl im Browser erhalten. Die bestätigte UI1-Ausnahme bei erkanntem Abschluss bleibt gültig. Der Benutzer prüft den Verlauf vor einem neuen Sendevorgang; es erfolgt kein automatischer Neuversand mit neuer ID. Verglichen wird der übermittelte Text einschliesslich Leerzeichen, nicht nur der getrimmte Speicherwert. Fehlgeschlagene Erstversuche reservieren die ID nicht.

### Mitarbeiterrückfrage während der automatischen Abklärung

Für eine bewusst veröffentlichte Mitarbeiterrückfrage mit «Mieterantwort
 erforderlich» im Status «In Abklärung» gilt zentral
[FALL-06: Mitarbeiterrückfrage während In Abklärung](fallverwaltung.md#mitarbeiterrückfrage-während-in-abklärung)
mit Übergang 29 und den geplanten Prüfungen FALL-AK-35. Sie löst die automatische
Abklärung kontrolliert ab und eröffnet «Wartet auf Mieterantwort». Eine reine
Information tut dies nicht. Mitarbeitertext braucht keine automatische Katalogfreigabe.
Die bereits festgelegten Regeln für Fragen während Mitarbeiterprüfung oder
Wiedervorlage bleiben bestehen.

### Mitarbeiterrückfrage während externer Wartephase

**Bestätigt am 15.11.2026:** Der nicht unterbrechende Mitarbeiter-Sendeweg gilt
auch während «Wartet auf externe Rückmeldung». Eine Rückfrage speichert den
bestätigten Antwortbedarf, beendet aber weder Status noch Warteaufgabe.
Die verbindlichen Status-, Fortsetzungs- und Konfliktregeln stehen zentral in
[FALL-12](fallverwaltung.md#fall-12-warten-auf-externe-rückmeldung);
geplante Prüffälle: FALL-AK-39. `SMessageSide` prüft dafür die aktuelle
`SExternalWait`-Zuordnung statt einer offenen `SReview`-Aufgabe.

### KOM-AK-30: Manuelles Nachfragen im bestehenden Mieterwartepfad

Geplante Prüfungen; keine ausgeführten Laufzeitnachweise:

- Rückfrage senden: Nachricht, neuer Antwortbedarf und Mailpflicht einmal speichern;
  Status und derselbe Reply-Aufruf bleiben bestehen, alte Fragen bleiben offen.
- Information ohne Antwortbedarf senden: kein neuer Bedarf, alte Fragen unverändert.
- Mieterantwort danach: bestehender Auswertungsweg berücksichtigt alle offenen Fragen.
  Neue Mitarbeiterrückfrage während Auswertung verhindert Fortsetzung auf alter Grundlage.
- Konkurrierende Fortsetzung/Abschluss oder veraltete Fallversion: unveröffentlichten
  Auftrag ablehnen, Mitarbeitereingabe erhalten, keine Nachricht/Mailpflicht speichern.
- Erfolgreichen Auftrag wiederholen, auch nach Fortsetzung: keine doppelte Nachricht
  oder Mailpflicht. Bewusst neuer Auftrag mit gleichem Text ist dagegen eine neue Nachricht.
- Unberechtigter, falscher oder alter Call-/Durchlaufbezug wird abgewiesen; keine
  User-Task-Completion und keine Wiederbelebung eines beendeten Warteaufrufs.
- Technischen Fehler wiederholen und bereits gespeicherten Versand zuverlässig
  nachholen; Nebenaktion überschreibt keine outcome-/decisionRef-Variablen.

### Reine Informationsnachrichten über bestehende Sendewege (0.33)

**Bestätigt am 15.11.2026 – B02.2:** Die bestehende Nachrichtenfunktion veröffentlicht
externe Informationen ohne neuen Antwortbedarf als Nebenaktion in allen aktiven
Status. Kein neuer Button oder zusätzlicher grosser Teilprozess. Die zentrale
Freigabe «Daten ergänzen» in FALL-06 bleibt massgebend.

| Phase | Anbindung | Wirkung |
|---|---|---|
| In Abklärung | StaffQuestionSide: nach Speicherung zwischen Information und Rückfrage unterscheiden | Information endet ohne Automatik-Ablösung; nur Rückfrage mit Antwortbedarf verwendet den bestehenden Übergang 29. |
| In Bearbeitung | SMessageSide für die aktuelle SWork-Aufgabe erweitern | Nur Information ohne neuen Antwortbedarf über diesen Nebenweg; dieselbe Aufgabe bleibt offen. Rückfrage bleibt bewusste Fortsetzung über SQuestion. |
| Wartet auf Wiedervorlage | WQuestionSide für Information und Rückfrage verwenden | Termin, Terminversion, Timer und Wartephase bleiben bestehen. |
| Mitarbeiterprüfung / externe Wartephase | Bestehendes SMessageSide | Bestehende Freigaben unverändert. |
| Wartet auf Mieterantwort | RStaffMessageSide gemäss KOM-AK-30 | Bestehender Warteaufruf und Antwortbedarfe bleiben erhalten. |

Berechtigung, bestätigter Fallstand und aktueller Prozess-/Call-/Aufgabenbezug
werden vor Veröffentlichung serverseitig geprüft. Nachricht und Mailpflicht
werden gemeinsam gespeichert; Camunda beauftragt den Versand über den bestehenden
Mailprozess. Es wird kein neuer Antwortbedarf angelegt, kein alter Bedarf erledigt
und keine User Task oder Wartephase durch Informationsversand beendet.

Ein veralteter unveröffentlichter Auftrag wird ohne Wirkung abgelehnt; Eingabe
bleibt erhalten. Erfolgreiche Wiederholung erzeugt keine zweite Nachricht oder
Mailpflicht. Bereits gespeicherte Nachrichten und Versandpflichten bleiben bei
späterer Fortsetzung bestehen. Technische Fehler folgen Retry/Incident.

Während der Abklärung darf ein altes KI-Ergebnis eine relevante neue Information
nicht ignorieren. Der bestehende Aktualitätsschutz prüft die Grundlage vor Wirkung;
das Senden allein unterbricht die Automatik nicht. Neuer zwingender Prüfbedarf
wird weiterhin über seinen eigenen Eingang verarbeitet. Eine Information ist
keine automatische Freigabe von Prüfung, Kosten, Auftrag oder Abschluss.

### KOM-AK-31: Statusneutraler Informationsversand

Geplante Laufzeitprüfungen, keine ausgeführten Nachweise:

- In Abklärung Information senden: Nachricht/Mailpflicht gespeichert, kein neuer
  Antwortbedarf, kein Ablösemarker, Automatik und Status bleiben bestehen.
  Rückfrage mit Antwortbedarf verwendet weiterhin den geschützten Übergang 29.
- Relevante Information während KI-Auswertung speichern: alte Grundlage darf
  keine nachfolgende Veröffentlichung oder Abschlussentscheidung autorisieren.
- In Bearbeitung Information über SMessageSide senden: gleiche offene SWork-Aufgabe.
  Rückfrage mit Antwortbedarf über diesen Nebenweg ablehnen; bestehender SQuestion-
  Fortsetzungsweg bleibt zulässig und darf nicht statusneutral umgangen werden.
- Während Wiedervorlage Information senden: Termin, Terminversion und Timer unverändert;
  bestehende Rückfragen bleiben offen. Rückfrage mit Bedarf weiterhin ohne Timeränderung.
- Berechtigungen, Phasen-/Versionskonflikte und konkurrierenden Abschluss/Timer prüfen:
  unveröffentlichte alte Aufträge ohne Wirkung ablehnen und Eingabe erhalten.
- Erfolgreichen Auftrag wiederholen: keine zweite Nachricht/Mailpflicht. Bereits
  gespeicherten Versand nach Phasenwechsel zuverlässig fortsetzen. Keine Änderung
  der Hauptpfadvariablen und kein neuer Warteprozess durch die Nebenaktion.
