# Fallkommunikation – fachliche und sicherheitsrelevante Regeln

**Status:** Zentrale Spezifikation der im Projekt festgelegten Kommunikationsregeln; technische Umsetzung noch offen.
**Stand:** 09.10.2026
**Geltungsbereich:** Mieteransicht, Backoffice, Backend-Services, Persistenz, Camunda-Worker, E-Mail und LLM-/RAG-Integration, soweit sie Fallkommunikation verarbeiten.

## Zweck und Verbindlichkeit

Dieses Dokument ist die gemeinsame fachliche Quelle für interne und externe Nachrichten, deren Sichtbarkeit und die zulässige Grundlage LLM-generierter Kommunikation. Die Regeln gelten unabhängig von einer einzelnen Benutzeroberfläche. Änderungen werden hier gepflegt; Screen-Spezifikationen beschreiben die daraus folgende Darstellung und Bedienung und verweisen auf die Regel-IDs.

Die Spezifikation wird mit der Projektdokumentation versioniert und ist für alle Projektbeteiligten und Entwicklungswerkzeuge im Repository auffindbar. Der gemeinsame Zugriff auf die Spezifikation erweitert keine Zugriffsrechte auf interne Fallinhalte.

Die Architekturgrundlagen bleiben in [ADR-001](../architecture/adr/ADR-001-grundarchitektur.md), [ADR-002](../architecture/adr/ADR-002-camunda8-workflow-orchestrierung.md) und [ADR-003](../architecture/adr/ADR-003-praesentationsschicht.md) dokumentiert. Dieses Dokument beschreibt fachliche Regeln und ihre Durchsetzung; ein ADR dokumentiert Architekturentscheidungen und deren Begründung. Es werden keine bestehenden ADRs stillschweigend geändert.

## KOM-01: Interne und externe Nachrichten

Jede Nachricht besitzt eine eindeutige **Sichtbarkeit**, unabhängig von ihrer Absenderrolle. Ein Mitarbeiter kann sowohl ein internes Memo als auch eine externe Antwort zum gleichen Fall erfassen. **Bestätigt am 10.10.2026:** Auch das System darf interne Memos über autorisierte PropertyFlow-Anwendungsdienste speichern. Sie bleiben ausschliesslich intern, erzeugen keine Mieterbenachrichtigung und unterliegen KOM-02 sowie der Nachrichtensperre nach KOM-04. Absender System, Zeitpunkt und Sichtbarkeit bleiben nachvollziehbar.

**Bestätigt am 10.10.2026:** Im Mitarbeiterverlauf wird neben Absender und Zeitpunkt die Herkunft System (Camunda-/KI-Verarbeitung) oder Mensch erkennbar geführt. Menschliche Absenderrollen unterscheiden Mieter und Immobilienverwaltung. Interne Memos bleiben zusätzlich als «Intern» gekennzeichnet; Herkunft und Sichtbarkeit sind getrennte Merkmale. Eine vom Mitarbeiter geprüfte und gesendete Nachricht wird als «Mensch» gekennzeichnet, auch wenn sie zuvor mit KI formuliert wurde; ein zusätzlicher Hinweis «mit KI formuliert» wird nicht angezeigt. Ein ergänzendes Icon bleibt eine optionale Gestaltungsentscheidung. Die Sichtbarkeit wird dauerhaft mit der Nachricht gespeichert; die konkreten technischen Bezeichnungen können beispielsweise `INTERNAL` und `EXTERNAL` lauten.

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

Für beide Sichtbarkeiten gilt die [Nachrichtensperre nach Fallabschluss (KOM-04)](#kom-04-nachrichtensperre-nach-fallabschluss).

**Mitarbeiter-Volltextsuche:** Alle Mitarbeitenden haben dieselben Adminrechte nach ZUG-04. Die Suche in [Screen 02](../frontend/ansicht-02-mitarbeiter-falluebersicht.md) umfasst gespeicherte veröffentlichte externe Nachrichten und sämtliche gespeicherten internen Memos. Externe Entwürfe, ungespeicherte Eingaben und KI-Teilantworten sind ausgeschlossen. «Versendet» meint die Veröffentlichung im Fallverlauf, unabhängig vom E-Mail-Zustellstatus. Die Mieteransicht bietet diese Suche nicht an. Auch ein interner Suchindex darf dem Kommunikations-LLM keine Memos oder Ableitungen daraus bereitstellen; KOM-02 gilt weiterhin.

### Antwortbedarf einer externen Mitarbeiternachricht

**Bestätigt am 10.10.2026:** Der Mitarbeiter legt beim Senden einer externen Nachricht ausdrücklich fest, ob eine Antwort des Mieters erforderlich ist. Diese Angabe wird mit der veröffentlichten Nachricht gespeichert; eine reine Information und eine Rückfrage sind dadurch unterscheidbar. Die KI-Formulierung entscheidet diese Angabe nicht selbst. Die Auswahl allein speichert nichts; Speicherung, Veröffentlichung und fachliche Übernahme des Antwortbedarfs erfolgen im vorgesehenen PropertyFlow-/Camunda-Ablauf.

Beispiele: «Der Techniker ist informiert und wird Sie für einen Termin kontaktieren» verlangt keine Antwort an PropertyFlow. «Bitte bestätigen Sie, ob die Fehlermeldung auch nach Ausstecken und Wiedereinstecken bestehen bleibt» verlangt eine Mieterantwort. Der Antwortbedarf wird beim Mieter im Fallverlauf und in der zugehörigen Benachrichtigungs-E-Mail eindeutig angezeigt. Interne Memos erzeugen keinen Mieterantwortbedarf.

Eine reine Informationsnachricht hebt keinen bereits offenen Antwortbedarf auf. Der Eingang irgendeiner Mieternachricht erledigt eine Rückfrage nicht automatisch; die inhaltliche Klärung und Prozessfortsetzung folgen FALL-05 und KOM-03. **Bedienung in UI3 bestätigt:** Checkbox «Mieterantwort erforderlich» neben dem Toggle «Intern», beim Öffnen standardmässig ausgeschaltet und nur für externe Nachrichten verfügbar. Interne Memos dürfen keinen Mieterantwortbedarf setzen; dies wird serverseitig geprüft.

## KOM-02: LLM-Kommunikation ohne interne Memos

**Präzisiert und bestätigt am 10.10.2026:** Interne Memos werden niemals automatisch als Kontext für die Generierung von Kommunikationsnachrichten durch ein LLM verwendet. Ein Mitarbeiter darf bewusst eine kurze Sachanweisung für die externe Nachricht eingeben, auch wenn dieselbe Information bereits in einem internen Memo steht. Er entscheidet, welche Sachinformationen er zur Formulierung übergibt, und prüft den erzeugten Text vor dem Senden. Die Kontextgrenze gilt für Antworten, Rückfragen und Kommunikationsentwürfe, einschliesslich später manuell freigegebener Entwürfe.

- Der zuständige PropertyFlow-Service stellt vor jedem Kommunikations-LLM-Aufruf einen zweckgebundenen Kontext aus ausdrücklich zulässigen Quellen zusammen, beispielsweise Mieterangaben, geeigneter externer Fallkommunikation, dafür freigegebenen Sachinformationen und der bewusst für diesen Formulierungsversuch übergebenen Mitarbeitereingabe.
- Das System darf Mitarbeiter- und Systemmemos weder direkt als Prompt- oder Gesprächsinhalte noch indirekt über RAG/Retrieval, Tool-Ergebnisse, Speicher/Caches, Suchindizes oder Camunda-Variablen in diesen Kontext übernehmen.
- Der Ausschluss umfasst auch vom System aus Memos erzeugte Zusammenfassungen, Paraphrasen, extrahierte Fakten und sonstige Ableitungen. Eine Umbenennung oder Kennzeichnung solcher Daten als «extern» oder «Mitarbeitereingabe» macht sie nicht zulässig. Die erlaubte kurze Sachanweisung stammt aus einer bewussten Mitarbeitereingabe für den Formulierungsversuch; das System darf sie nicht selbst aus Memos erstellen oder mit Memo-Inhalten anreichern.
- Sichtbarkeit für den Mieter und Eignung als LLM-Eingabe sind getrennte Prüfungen. Die Herkunft systemseitig bereitgestellter Inhalte und ihre zulässige Verwendung bleiben über Verarbeitungsschritte nachvollziehbar. Bei unklarer Herkunft wird solcher zusätzlicher Kontext ausgeschlossen. Für frei formulierte Mitarbeitereingaben wird keine technische Erkennung verlangt oder behauptet, ob der Mitarbeiter denselben Sachverhalt aus einem Memo kennt. Die bestätigte Grenze betrifft die bewusste Eingabe einerseits und die systemseitige Bereitstellung von Kontext andererseits.
- Ein Prompt wie «Interne Memos nicht erwähnen» genügt nicht. Der Ausschluss systemseitiger Memo-Quellen wird vor dem LLM-Aufruf und bei allen vom Kommunikations-LLM abrufbaren Quellen durchgesetzt, auch bei Wiederholungen eines Camunda-Jobs. Eine spätere menschliche Veröffentlichungskontrolle ersetzt diese Prüfung nicht.
- Ein LLM-Sitzungs- oder Gesprächskontext, der für andere Aufgaben mit internen Memos befüllt wurde, darf nicht zur Kommunikationsgenerierung weiterverwendet werden. Der Kommunikationsschritt erhält einen getrennten, zulässigen Kontext. Bewusst eingegebene Sachanweisungen eröffnen keinen Zugriff auf die zugrunde liegenden Memos.

**Bestätigtes Beispiel:** Ein internes Memo enthält den Technikertermin am Dienstag, eine Kostenfreigabe und eine interne Vorgabe zur Offertprüfung. Der Mitarbeiter gibt für die externe Formulierung nur «Techniker kommt am Dienstag. Bitte Zugang zur Wohnung ermöglichen.» ein. Diese Sachanweisung ist zulässig. Die ausschliesslich im Memo vorhandene Kostenfreigabe und Offertvorgabe werden nicht in den KI-Kontext übernommen. Das Memo bleibt intern; erst die vom Mitarbeiter geprüfte und gesendete externe Nachricht wird veröffentlicht. Kurzeingabe und ungesendeter KI-Vorschlag bleiben ohne Entwurfsspeicherung.

## KOM-03: Asynchrone Fallkommunikation

KI-gestützte Verarbeitungsschritte der Fallkommunikation werden durch Camunda 8 orchestriert und über PropertyFlow-Worker/Services ausgeführt. Der Mieter übermittelt Informationen zum Fall; das Backend speichert sie und führt sie gemäss dem aktuellen BPMN-Schritt der weiteren Verarbeitung zu. Jede dauerhaft gespeicherte Mieter-Nachricht veranlasst die Dringlichkeitsbewertung nach DRING-05 unter Wahrung manueller Einstufungen nach DRING-06. Sie eröffnet weder einen neuen Fall noch einen zweiten führenden Prozess. Der blosse Nachrichteneingang erledigt keine Rückfrage; dafür ist die nachfolgend definierte inhaltliche Prüfung erforderlich. Die konkrete BPMN-Korrelation bleibt im Integrationsvertrag festzulegen.

**Bestätigt am 10.10.2026:** Eine laufende Wiedervorlage verzögert die Verarbeitung neuer Mieternachrichten nicht. Sie werden parallel zum Camunda-Timer verarbeitet. Neue Erkenntnisse können nach [FALL-11](fallverwaltung.md#fall-11-wiedervorlage-und-parallele-nachrichtenverarbeitung) eine frühere Mitarbeiterprüfung auslösen; ohne neuen früheren Handlungsbedarf bleibt der Termin bestehen. Der blosse Nachrichteneingang beendet oder verlängert die Wartezeit nicht.

Der Mieter hat keine direkte LLM-Chatsitzung, keinen Chat-Stream und kein Recht, KI-Schritte oder den Camunda-Prozess abzubrechen. Diese Grenze gilt auch bei direkten Backend-Aufrufen mit Mieterberechtigung. Neuladen oder Schliessen der Seite beeinflusst die Hintergrundverarbeitung nicht.

An den Mieter werden als Kommunikationsnachrichten ausschliesslich vollständige, gespeicherte und veröffentlichte externe Nachrichten ausgegeben. KI-Teilantworten und interne Zwischenergebnisse gehören nicht zur externen Historie. Wiederholungen und KI-Fehler werden durch die Camunda-/Backend-Integration behandelt; gespeicherte Nachrichten bleiben erhalten. Vor Kommunikationsgenerierung gilt KOM-02, vor Ausgabe KOM-01.

Die konkrete Aktualisierung der Darstellung beschreibt [Screen 01](../frontend/ansicht-01-mieter-fallansicht.md). Die in [Vision](../vision.md) optional vorgesehenen KI-Rückfragen bleiben hinsichtlich ihres produktiven Umfangs separat zu entscheiden. Sobald sie eingesetzt werden, gelten diese Regeln ebenfalls.

### Inhaltliche Prüfung offener Rückfragen

**Bestätigt am 10.10.2026:** Camunda veranlasst bei neuen Mieternachrichten die inhaltliche Prüfung der offenen Rückfragen über die zuständigen PropertyFlow-Services. Eine eindeutig beantwortete Rückfrage wird als beantwortet erledigt. Unvollständige, unklare oder themenfremde Antworten lassen die jeweilige Rückfrage offen. Bei mehreren offenen Rückfragen wird jede einzeln beurteilt; die Beantwortung einer Frage erledigt keine andere Frage ohne passende inhaltliche Grundlage.

Die Prüfung berücksichtigt den Zusammenhang der veröffentlichten Rückfragen und der gespeicherten Mieternachrichten. Eine Nachricht kann mehrere Fragen beantworten, wenn die Antwort für jede dieser Fragen eindeutig ist. Bleibt die Zuordnung oder Vollständigkeit unklar, wird keine Erledigung behauptet. Eine fehlgeschlagene Auswertung gilt ebenfalls nicht als erfolgreiche Beantwortung.

Die ursprüngliche Kennzeichnung «Mieterantwort erforderlich» an der veröffentlichten Nachricht und der aktuelle Erledigungsstand der zugehörigen Rückfrage bleiben unterscheidbar. Die Veröffentlichung und ihr Text werden nicht nachträglich überschrieben. Eine bestätigte Beantwortung wird in PropertyFlow mit Bezug zur Rückfrage, zur zugrunde liegenden Mieterantwort und zum Verarbeitungszeitpunkt nachvollziehbar gespeichert. UI1 und UI3 zeigen den aktuellen Antwortbedarf; eine erledigte Rückfrage fordert nicht weiter zur Antwort auf. Wiederholte Verarbeitung derselben Erkenntnisse erzeugt keine zusätzliche Erledigung.

Die inhaltliche Prüfung läuft auch während einer Wiedervorlage parallel zur Wartezeit gemäss FALL-11. Eine beantwortete Rückfrage bewirkt für sich weder den Fallabschluss noch das Löschen einer Wiedervorlage. **Bestätigt am 10.10.2026:** Sind alle zuvor offenen Rückfragen beantwortet, folgt nach der automatischen Verarbeitung «Mitarbeiterprüfung erforderlich», sofern kein zulässiger automatischer Abschluss erfolgt und keine Wiedervorlage läuft. Die zentrale Statusregel samt Ausnahmen und Wiederholungen ist in [FALL-05](fallverwaltung.md#status-nach-beantwortung-aller-rückfragen) festgelegt. Eine laufende Wiedervorlage bleibt bestehen und kann bei neuen Erkenntnissen nach FALL-11 vorgezogen werden. Beim Fallabschluss endet noch offener Antwortbedarf gemäss KOM-04. Wird eine Rückfrage während eines aktiven Falls gegenstandslos, kann ein Mitarbeiter ihren Antwortbedarf gezielt aufheben, wie nachfolgend beschrieben.

### Antwortbedarf durch Mitarbeiter aufheben

**Bestätigt am 10.10.2026:** Ein Mitarbeiter kann in UI3 bei einer veröffentlichten externen Rückfrage mit noch offenem Antwortbedarf die Aktion «Antwortbedarf aufheben» wählen. Die Aktion beendet ausschliesslich den Antwortbedarf der gewählten Nachricht. Andere Nachrichten mit offenem Antwortbedarf bleiben unberührt. Die ursprüngliche Nachricht und ihre ursprüngliche Kennzeichnung bleiben erhalten; ohne passende Antwort wird die Rückfrage nicht als inhaltlich beantwortet ausgegeben. UI1 und UI3 zeigen den gespeicherten aktuellen Antwortbedarf und fordern für die betreffende Nachricht keine weitere Antwort mehr an. Es entsteht kein zusätzlicher Fall- oder Rückfragestatus.

Der Fall bleibt aktiv und bearbeitbar. Die Aufhebung ist kein Fallabschluss und behauptet keine Behebung des gemeldeten Problems. Beispielsweise kann eine Rückfrage entfallen, weil der Techniker die Ursache inzwischen kennt, während die Behebung noch aussteht. Der Abschluss erfolgt separat nach FALL-07. **Ebenfalls bestätigt:** Entfällt durch die Aktion der letzte offene Antwortbedarf, folgt «In Bearbeitung», sofern keine laufende Wiedervorlage und kein vorrangiger aktueller Mitarbeiterprüfbedarf bestehen. Die zentrale Statusregel und diese Ausnahmen sind in [FALL-05](fallverwaltung.md#status-nach-manuellem-aufheben-des-antwortbedarfs) festgelegt.

Die Aktion wird serverseitig autorisiert und gegen den aktuellen Fall- und Antwortbedarf geprüft. PropertyFlow speichert die Aufhebung mit Bezug zur Nachricht, Zeitpunkt und handelnder Mitarbeiteridentität; Camunda berücksichtigt den aktuellen Antwortbedarf. Mieter können die Aktion nicht ausführen. Nach Fallabschluss steht sie nicht mehr zur Verfügung; dann gilt KOM-04. Wiederholungen erzeugen keine zusätzliche Aufhebung und verspätete Auswertungen setzen den aufgehobenen Antwortbedarf nicht wieder. Eine nicht bestätigte oder fehlgeschlagene Speicherung wird nicht als Erfolg dargestellt. Ungesendeter Text, die Auswahl «Intern» und «Mieterantwort erforderlich» sowie die laufende KI-Formulierung bleiben erhalten.

## KOM-04: Nachrichtensperre nach Fallabschluss

Die Voraussetzungen des fachlichen Abschlusses und die Zeit danach sind in [FALL-07](fallverwaltung.md#fall-07-abschluss-und-zeit-danach) definiert. Dieser Abschnitt regelt die daraus folgende Nachrichtensperre.

Nach fachlichem Abschluss darf kein Absender neue Nachrichten zum Fall erzeugen: weder Mieter noch Mitarbeitende noch System-/KI-Komponenten. Dies gilt für interne Memos und externe Nachrichten. Eine Ausnahme für interne Memos während des abgeschlossenen Zustands ist nicht vorgesehen. Ausschliesslich Mitarbeitende können den Fall nach FALL-07 in UI3 bewusst wiedereröffnen. Erst nach bestätigter Rückkehr in die aktive Phase sind neue interne Memos und externe Nachrichten wieder zulässig; die Wiedereröffnung selbst ist keine Kommunikationsnachricht. Der berechtigte lesende Zugriff auf bestehende Nachrichten bleibt gemäss KOM-01 möglich.

**Bestätigt am 10.10.2026 – offener Antwortbedarf beim Abschluss:** Mit dem erfolgreichen fachlichen Fallabschluss endet auch der noch offene Antwortbedarf zu bisherigen Rückfragen. UI1 und UI3 fordern danach keine Mieterantwort mehr an. Der Fallstatus lautet «Abgeschlossen»; dafür wird kein zusätzlicher Fall- oder Rückfragestatus eingeführt. Die Beschreibung «nicht mehr erforderlich» bezeichnet lediglich diese Folge des Abschlusses. Die veröffentlichten Rückfragen und ihre ursprüngliche Kennzeichnung bleiben im Verlauf erhalten; unbeantwortete Fragen werden dadurch nicht als inhaltlich beantwortet ausgegeben. Ein abgebrochener oder fehlgeschlagener Abschluss beendet den Antwortbedarf nicht.

Die Prüfung wird serverseitig bei jeder Schreiboperation durchgesetzt, einschliesslich direkter HTTP-Aufrufe, verspäteter KI-Ergebnisse und wiederholter Worker-Ausführungen. Abschluss und Prüfen/Speichern einer Nachricht müssen konsistent synchronisiert werden; ein konkurrierender Request darf die Sperre nicht umgehen. Die konkrete Synchronisation wird im Service-/Persistenzdesign festgelegt.

Nach einer Wiedereröffnung dürfen verspätete automatische Ergebnisse aus der vorherigen Bearbeitung ebenfalls keine neue Nachricht oder andere Falländerung erzeugen. Massgeblich ist die [fachliche Grenze aus FALL-07](fallverwaltung.md#verspätete-automatische-ergebnisse-nach-wiedereröffnung). Bereits gespeicherte Nachrichten bleiben im Verlauf erhalten. Die technische Zuordnung solcher Ergebnisse wird später konkretisiert.

Die fachliche und technische Statusverantwortung sowie ihre Synchronisierung sind zentral in [FALL-06](fallverwaltung.md#fall-06-fachlicher-status-und-technischer-workflow) beschrieben.

E-Mail-Auslöser, kurzer Mailinhalt und zuverlässige Zustellung werden zentral in [Benachrichtigungen und Zustellung](benachrichtigungen-und-zustellung.md) geregelt. Auch eigene gespeicherte Mieter-Nachrichten und der fachliche Abschluss werden per E-Mail bestätigt; interne Memos und Entwürfe bleiben ausgeschlossen.

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

Eine neue Mieternachricht wird inhaltlich gegen jede offene Rückfrage geprüft. Eine eindeutig beantwortete Frage wird erledigt; eine unvollständig, unklar oder themenfremd beantwortete Frage bleibt offen. Bei zwei offenen Fragen erledigt die Antwort auf nur eine davon ausschliesslich diese Frage; eine Nachricht darf beide erledigen, wenn sie beide eindeutig beantwortet. Antwortbedarf und Erledigungsstand bleiben in UI1 und UI3 konsistent; der veröffentlichte Nachrichtentext bleibt erhalten. Fehlgeschlagene Auswertungen und Wiederholungen erzeugen keine falsche oder doppelte Erledigung. Die Prüfung findet auch während einer Wiedervorlage statt und schliesst den Fall nicht allein wegen der Beantwortung automatisch ab.

Sind alle zuvor offenen Rückfragen beantwortet und ist die automatische Verarbeitung abgeschlossen, erhält der aktive Fall ohne laufende Wiedervorlage «Mitarbeiterprüfung erforderlich». Ein zulässiger automatischer Abschluss nach FALL-07/FALL-10 führt stattdessen zu «Abgeschlossen». Eine laufende Wiedervorlage bleibt bestehen, soweit keine neuen Erkenntnisse eine frühere oder sofortige Prüfung nach FALL-11 erfordern. Eine Teilantwort oder eine reine Informationsnachricht ohne zuvor offene Rückfragen löst den Übergang wegen vollständiger Beantwortung nicht aus. Wiederholte Auswertungen bereits berücksichtigter Antworten nehmen eine anschliessende bewusste Mitarbeiterentscheidung nicht zurück. Die angezeigten Fallstatus in UI1–UI3 entsprechen dem gespeicherten Ergebnis.

### KOM-AK-10

Ein Mitarbeiter hebt in UI3 gezielt den Antwortbedarf einer veröffentlichten Rückfrage auf. UI1 und UI3 zeigen danach für diese Nachricht keinen offenen Antwortbedarf; der offene Antwortbedarf einer zweiten Nachricht bleibt unverändert. Nachrichtentext und ursprüngliche Kennzeichnung bleiben erhalten, ohne eine erfolgte Beantwortung zu behaupten. Entfällt der letzte offene Antwortbedarf, folgt «In Bearbeitung»; eine laufende Wiedervorlage und vorrangiger aktueller Mitarbeiterprüfbedarf bleiben nach FALL-05/FALL-11 wirksam. Verbleibender Antwortbedarf löst durch die Aktion allein keinen Statuswechsel aus. Fall und ausstehende Bearbeitung bleiben aktiv. Es entsteht kein zusätzlicher Fall- oder Rückfragestatus. Die Aufhebung ist mit Nachricht, Mitarbeiter und Zeitpunkt nachvollziehbar. Mieteraufrufe und Änderungen an abgeschlossenen Fällen werden abgelehnt. Wiederholte Aufrufe, verspätete Auswertungen sowie ein konkurrierender Fallabschluss oder neu hinzugekommener Antwortbedarf erzeugen keine falsche oder doppelte Änderung. Ein abgelehnter Vorgang speichert weder eine Aufhebung noch deren Statusfolge. Bei Fehlschlag wird kein Erfolg vorgetäuscht; Mitarbeitereingabe und aktiver KI-Stream bleiben erhalten.

## Abgleich mit bestehenden Dokumenten

**Geltungsbereich geklärt (09.10.2026):** Streaming und Abbruch gemäss [ADR-003](../architecture/adr/ADR-003-praesentationsschicht.md) und [UC-004](../use_cases/UC-004-ki-analyse-durchfuehren.md) gehören zur Mitarbeiteransicht im Backoffice. **Präzisiert am 10.10.2026:** Die interaktive Mitarbeiteranfrage dient der Formulierung einer externen Nachricht aus einer kurzen fachlichen Mitarbeitereingabe, nicht dem Start einer Fallanalyse. Fallanalyse und Dringlichkeitsbewertung laufen automatisch im Camunda-Fallprozess. Das System speichert Analyseinformationen als Nachrichten mit expliziter Sichtbarkeit; interne Systemmemos bleiben intern. Der Formulierungsvorschlag wird als ungespeicherter Stream angezeigt, vom Mitarbeiter geprüft und gegebenenfalls bearbeitet. Erst «Nachricht senden» persistiert und veröffentlicht die externe Nachricht. KOM-02 schliesst die systemseitige Übernahme von Mitarbeiter- und Systemmemos sowie deren Ableitungen aus; bewusst eingegebene kurze Sachanweisungen des Mitarbeiters sind zulässig. Für den Mieter gilt KOM-03: kein direkter Chat-Stream und kein KI-/Prozessabbruch; periodische Leseabrufe zeigen gespeicherte Ergebnisse. Ein Mitarbeiterabbruch einer KI-Anfrage schliesst den Fall nicht und ist kein pauschaler Abbruch des führenden Camunda-Fallprozesses. Dieser fachliche Abgleich ist abgeschlossen; technische Streaming- und Abbruchverträge der Mitarbeiteransicht werden bei ihrer Umsetzung konkretisiert.

Fallidentität, Annahme und Lebenszyklus werden zentral in der [Fallverwaltung](fallverwaltung.md) gepflegt. Direkter persönlicher Tokenzugriff und Berechtigungen werden zentral in [Fallzugriff und Sicherheit](fallzugriff-und-sicherheit.md) gepflegt. Screen-spezifische Felder, Routenübersicht, Wireframes und Bedienungsregeln bleiben in [Screen 01](../frontend/ansicht-01-mieter-fallansicht.md), [Screen 02](../frontend/ansicht-02-mitarbeiter-falluebersicht.md) und [Screen 03](../frontend/ansicht-03-mitarbeiter-falldetail.md). Erweiterungen der grundlegenden Kommunikationsregeln werden hier vorgenommen und von den betroffenen Screens und Use-Cases referenziert.
