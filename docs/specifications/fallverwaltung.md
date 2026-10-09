# Fallverwaltung – Fallanlage und Lebenszyklus

**Status:** Zentrale Spezifikation der vereinbarten fachlichen Regeln und Bearbeitungsstatus; Prozessübergänge und technische Verträge noch zu konkretisieren.

**Stand:** 09.10.2026

**Geltungsbereich:** Erfassung, Backoffice, fachliche Services, Persistenz und Camunda-Integration von PropertyFlow.

## Zweck und Abgrenzung

Dieses Dokument definiert, wann ein Fall entsteht, wie er identifiziert und fortgeführt wird und was sein fachlicher Abschluss bedeutet. Es ist die gemeinsame Quelle für diese Regeln. Screen-Spezifikationen beschreiben ihre Darstellung; Use-Cases beschreiben die Nutzung aus Sicht der jeweiligen Akteure.

Die Anforderungen werden aus [Vision](../vision.md), [Projekt-Kontext](../project-context.md), [ADR-001](../architecture/adr/ADR-001-grundarchitektur.md), [ADR-002](../architecture/adr/ADR-002-camunda8-workflow-orchestrierung.md), den bestehenden Use-Cases und der bisherigen [Screen-01-Spezifikation](../frontend/ansicht-01-mieter-fallansicht.md) zusammengeführt. Akzeptierte Architekturentscheidungen bleiben gültig. Neue Konkretisierungen sind ausdrücklich als Review-Vorschlag gekennzeichnet.

| Zentrale Quelle | Verantwortung |
|---|---|
| Dieses Dokument | Fallidentität, Annahme, Ursprungsdaten, Zuordnung, fachlicher Lebenszyklus und Beziehung zum Workflow |
| [Fallkommunikation](fallkommunikation.md) | Interne/externe Nachrichten, Veröffentlichung, Ausschluss interner Memos aus LLM-Kommunikation und Nachrichtensperre nach Abschluss |
| [Dringlichkeitsbewertung](dringlichkeitsbewertung.md) | Stufen, Farben, Erst-/Neubewertung, Vorrang manueller Einstufungen und mieteröffentliche Änderungshistorie |
| [Fallzugriff und Sicherheit](fallzugriff-und-sicherheit.md) | Direkter persönlicher Tokenzugriff, Berechtigungen, Widerruf und Ersatz |
| [Benachrichtigungen und Zustellung](benachrichtigungen-und-zustellung.md) | E-Mail-Auslöser, Versandpflicht, Wiederholungen und Zustellfehler |
| [Screen 01](../frontend/ansicht-01-mieter-fallansicht.md) | Felddarstellung, Navigation, Routenübersicht, Rückmeldungen und Wireframes |
| [Screen 02](../frontend/ansicht-02-mitarbeiter-falluebersicht.md) | Mitarbeiterliste, Suche, Filter, Sortierung, Seitennavigation und Aktualisierung |
| [Architektur und Modulgrenzen](../architecture/module-structure.md) | Technische Zuständigkeiten und öffentliche Integrationsverträge |

Die konkrete zuverlässige Übergabe an Camunda und Mail, ein mögliches Outbox-Verfahren und konkrete Datenbankschemata werden separat entschieden. Dieses Dokument legt die erforderlichen fachlichen Ergebnisse fest und bestätigt keine bereits vorhandene Umsetzung.

## FALL-01: Fallidentität und Erfassungskanal

Ein Fall ist das dauerhaft angenommene Mieteranliegen mit seiner fachlichen Identität und dem zugehörigen Bearbeitungsverlauf. Das Öffnen eines Formulars und noch nicht übermittelte Eingaben erzeugen keinen Fall. Eine dauerhafte Entwurfsverwaltung ist im aktuellen Umfang nicht vorgesehen.

Die Case-ID wird serverseitig vergeben, ist eindeutig und bleibt über den Lebenszyklus unverändert. Ein Beispiel wie `REQ-2026-001` legt noch kein verbindliches Nummernformat fest. Die Case-ID ist weder ein Zugriffstoken noch die technische Camunda-Prozessinstanz-ID. Der persönliche Mieterlink enthält ausschliesslich einen geheimen Token; seine serverseitige Fallzuordnung und die unveränderte Case-ID bei Tokenersatz regelt [ZUG-01](fallzugriff-und-sicherheit.md#zug-01-case-id-und-geheimer-token).

Die [Vision](../vision.md) sieht Webformular und E-Mail als Eingangskanäle vor. Für jeden Kanal gelten dieselben Regeln zur dauerhaften Annahme und Fallidentität. Die Feldbelegung und sichere Zuordnung eingehender E-Mails sind separat zu spezifizieren. Eine E-Mail ist nicht automatisch ein neuer Fall; eine sicher zugeordnete Ergänzung gehört zum bestehenden Fall gemäss [KOM-03](fallkommunikation.md#kom-03-asynchrone-fallkommunikation).

Die Aktion «Neues Anliegen melden» eröffnet die Erfassung. Erst eine neue erfolgreich angenommene Einreichung erzeugt eine neue Case-ID. Das Ergänzen eines bestehenden Falls erzeugt keine neue Case-ID und keinen zweiten führenden Bearbeitungsprozess.

## FALL-02: Erfolgreiche Annahme

**Ein Fall gilt erst nach erfolgreicher dauerhafter Speicherung als angenommen.** Weder eine Browserbestätigung noch ein gestarteter LLM-Aufruf ersetzt diesen Zeitpunkt.

Für die Web-Erfassung sind Betreff, Beschreibung, Objekt-/Wohnungsreferenz und E-Mail-Adresse vorgesehen. Konkrete Feldlängen bleiben Review-Vorschläge der [Screen-Spezifikation](../frontend/ansicht-01-mieter-fallansicht.md#31-eingabefelder); deren Darstellung und Fehlertexte bleiben dort. Der Server prüft die geltenden Eingaberegeln unabhängig vom Browser.

Die Annahme umfasst fachlich:

1. Eingabe und zulässige Einreichung prüfen.
2. Case-ID vergeben und Ursprungsdaten dauerhaft speichern.
3. Die ursprüngliche Beschreibung als erste externe Mitteilung eindeutig in die Historie aufnehmen oder darauf referenzieren; keine fachlich doppelte Nachricht anlegen.
4. Den initialen fachlichen Stand als eingegangen und aktiv festhalten.
5. Die nachgelagerte Prozessübergabe und erforderliche Bestätigungsbenachrichtigung zuverlässig veranlassen.
6. Erst nach bestätigter Speicherung den Erfolg mit der Case-ID zurückmelden.

Die für eine angenommene Meldung erforderlichen Daten und Folgeaufträge müssen nach einem Neustart rekonstruierbar sein. Ihre technische Transaktionsgrenze wird im Integrationsdesign festgelegt; eine gemeinsame lokale Transaktion mit Camunda oder dem Maildienst wird nicht vorausgesetzt.

Die Erfassung wartet nicht auf KI/RAG, erfolgreiche E-Mail-Zustellung, einen erreichbaren Camunda-Server oder eine menschliche Entscheidung. Ein bereits gespeicherter Fall bleibt bei einem Ausfall dieser Komponenten erhalten und für die manuelle Bearbeitung zugänglich.

## FALL-03: Wiederholung und doppelte Einreichung

Die technische Wiederholung derselben Einreichung darf keinen zweiten Fall erzeugen. Der bereits beschriebene Absende-ID-Vertrag bleibt Grundlage: gleiche Einreichung und gleiche serverseitig geprüfte Absende-ID liefern dasselbe Ergebnis und dieselbe Case-ID zurück; abweichender Inhalt mit derselben Absende-ID wird als Konflikt behandelt. Der technische Vertrag und seine Aufbewahrungsdauer sind noch festzulegen.

Bei unklarem Request-Ausgang, etwa nach einem Netzwerkabbruch, kann die Speicherung bereits erfolgt sein. Eine Wiederholung darf deshalb keine neue fachliche Identität erzeugen. Auch Prozessstart-Wiederholungen müssen demselben Fall zugeordnet und gegen Mehrfachwirkung abgesichert werden.

Das ist von zwei bewusst getrennten Einreichungen mit ähnlichem Inhalt zu unterscheiden: Eine automatische fachliche Zusammenführung solcher Fälle ist nicht beschlossen. Ähnliche Texte allein rechtfertigen weder Zusammenführen noch Verwerfen eines Anliegens.

## FALL-04: Ursprungsdaten und Zuordnung

Die bei der Annahme gespeicherten Ursprungsangaben sind für den Mieter unveränderlich. Er ergänzt oder korrigiert seine Meldung über eine neue Nachricht. Die Ursprungseingabe und frühere Nachrichten werden dadurch nicht überschrieben.

Die eingegebene Objekt-/Wohnungsreferenz ist von einer später bestätigten Zuordnung zu Mieterstammdaten zu unterscheiden. Eine unklare Zuordnung verhindert die Annahme nicht. Sie bleibt intern als offen erkennbar und kann durch die dafür zuständige Fachbearbeitung geklärt werden. Dabei dürfen keine fremden Stammdaten in der öffentlichen Erfassung offengelegt werden.

Eine syntaktisch gültige E-Mail und eine angegebene Adresse bestätigen weder die persönliche Identität noch das Mietverhältnis. Identitätsprüfung, Empfängerwechsel und Zugriffsberechtigungen werden in [Fallzugriff und Sicherheit](fallzugriff-und-sicherheit.md) geführt. Ein Ablauf oder Widerruf des Falllinks verändert den fachlichen Fallstatus nicht.

**Review-Vorschlag zur Nachvollziehbarkeit:** Bestätigte oder korrigierte Zuordnungen werden getrennt von den ursprünglichen Freitextangaben geführt. Berechtigung und Protokollierung solcher Backoffice-Änderungen sind im Servicevertrag zu konkretisieren.

## FALL-05: Fachlicher Lebenszyklus

Der gemeinsame Grundzustand unterscheidet **noch keinen Fall**, **aktiven Fall** und **abgeschlossenen Fall**. «Neuer Fall» bezeichnet im UI das Erfassungsformular und ist kein bereits persistierter Fallstatus.

| Phase | Eintritt | Bedeutung und zulässige Fortsetzung |
|---|---|---|
| Noch kein Fall | Formular geöffnet oder Annahme gescheitert | Eingaben können korrigiert und eingereicht werden. Kein bearbeitbarer Fall wurde bestätigt. |
| Aktiv | Annahme nach FALL-02 erfolgreich | Der Fall existiert, auch wenn der Prozessstart noch aussteht. Zuordnung, Prüfung, Bearbeitung und Kommunikation erfolgen unter den jeweiligen Berechtigungen. |
| Abgeschlossen | Fachlich vorgesehener Abschluss nach FALL-07 konsistent übernommen | Berechtigtes Lesen bleibt möglich; die Nachrichtensperre aus KOM-04 gilt. Eine neue Meldung ist ein separater Fall. |

```mermaid
flowchart LR
    N["Noch kein Fall"] -->|"Erfolgreiche dauerhafte Annahme"| A["Aktiver Fall"]
    N -->|"Validierungsfehler: Eingabe korrigieren"| N
    A -->|"Pruefung, Bearbeitung, Rueckfrage, Ergaenzung"| A
    A -->|"Fachlicher Abschluss gemaess Prozess"| C["Abgeschlossener Fall"]
```

Das Diagramm zeigt den fachlichen Grundvertrag. Es ersetzt weder BPMN noch schreibt es eine zweite Workflow-Engine in PropertyFlow vor.

### Fachliche Bearbeitungsstatus – vereinbart

**Vereinbart am 09.10.2026:** Die folgenden sieben Bezeichnungen und Bedeutungen bilden die fachlichen Bearbeitungsstatus. Technische Enum-Werte, konkrete Übergangsbedingungen und das Mapping zum BPMN-Modell werden separat festgelegt.

| Bearbeitungsstatus | Bedeutung |
|---|---|
| Eingegangen | Dauerhaft angenommen; weitere Bearbeitung kann noch ausstehen. |
| In Abklärung | Automatische Analyse oder fachliche Abklärung läuft. |
| Wartet auf Mieterantwort | Eine Rückfrage wurde veröffentlicht; benötigte Informationen fehlen noch. |
| Mitarbeiterprüfung erforderlich | Menschliche Prüfung ist nötig, etwa nach einem Analysefehler oder weil der Fall trotz Rückfragen unklar bleibt. |
| In Bearbeitung | Das weitere Vorgehen wurde festgelegt; die Umsetzung läuft. |
| Wartet auf externe Rückmeldung | Informationen oder eine Rückmeldung beispielsweise von Hauswart oder Fachstelle stehen aus. |
| Abgeschlossen | Der fachliche Abschluss wurde bestätigt; neue Nachrichten sind gemäss KOM-04 gesperrt. |

**Vereinbarte Konkretisierung am 09.10.2026:** Bei einem aktiven Fall führen **offene Objektzuordnung**, **fehlgeschlagene KI-Analyse** und **klärungsbedürftiger E-Mail-Versand** einheitlich zum Bearbeitungsstatus **«Mitarbeiterprüfung erforderlich»**. Der jeweilige Grund bleibt zusätzlich intern am Fall gespeichert und für die Bearbeitung nachvollziehbar: «Zuordnung offen», «KI-Analyse fehlgeschlagen» bzw. «E-Mail-Versand klären». Screen 02 zeigt den Bearbeitungsstatus, aber keine Problemhinweise. Die konkrete interne Darstellung der Gründe wird bei Screen 03 festgelegt. Mehrere Gründe können gleichzeitig vorliegen. Ein automatischer Wiederholungsversuch hebt diese Zuordnung nicht auf. Diese Vereinbarung ersetzt die frühere Ausnahme für Analysefehler während automatischer Wiederholungen. Der Übergang wird im vorgesehenen Camunda-/PropertyFlow-Ablauf vorgenommen, nicht allein aus einem Browserfehler abgeleitet.

Eine veröffentlichte Rückfrage mit ausstehender Mieterantwort ist fachliches Warten und für sich kein technischer Fehler. Nach Behebung der Hinweise muss die fachlich passende Fortsetzung im Prozess erfolgen; der konkrete Übergang bleibt festzulegen. Ein noch ausstehender Analyse- oder Versandversuch ohne festgestellten Fehler ist nicht automatisch ein Problemhinweis.

**Abschlussgrenze:** Ein Versandproblem, das erst nach fachlichem Abschluss festgestellt wird, bleibt als Hinweis am Fall für die interne Bearbeitung nachvollziehbar; es wird nicht in Screen 02 angezeigt. Der Fall bleibt «Abgeschlossen»; die Problembehandlung eröffnet ihn nicht wieder und umgeht die Nachrichtensperre nicht. Der aktive Status «Mitarbeiterprüfung erforderlich» wird nicht zur stillschweigenden Wiedereröffnung verwendet.

Die ersten sechs Bearbeitungsstatus gehören zur aktiven Phase; «Abgeschlossen» gehört zur abgeschlossenen Phase. Eine zusätzliche Nachricht ist während eines aktiven Falls zulässig, auch wenn der Workflow gerade nicht auf eine Mieterantwort wartet. Jede gespeicherte Mieter-Nachricht veranlasst die Neubewertung gemäss DRING-05. Welche fachliche Fortsetzung sie auslöst, bestimmt der modellierte Prozess. Der blosse Eingang einer Nachricht beantwortet nicht automatisch jede offene fachliche Rückfrage.

Die Zuordnung ist keine feste lineare Reihenfolge. Der Workflow kann Abklärungen und Rückfragen wiederholen. Solche Schleifen behalten die Case-ID bei und öffnen keine neue Fallanlage.

## FALL-06: Fachlicher Status und technischer Workflow

| Information | Führende Verantwortung | Abgrenzung |
|---|---|---|
| Case-ID, Ursprungsdaten, Objektzuordnung, fachlicher Status und Verlauf | PropertyFlow | Grundlage der fachlichen Fallansichten |
| Prozessinstanz, Prozessposition, aktive Aufgaben, Timer, Jobs und Incidents | Camunda 8 | Technischer Workflow-State gemäss ADR-002 |
| E-Mail-Auftrag und Versand-/Zustellinformation | PropertyFlow und Mailintegration | Benachrichtigungszustand; kein Fallstatus |
| Analyseergebnis oder technischer Analysefehler | Zuständiger PropertyFlow-Service | Unterstützende Information; keine eigenständige fachliche Abschlussentscheidung |

Für einen angenommenen Fall wird genau ein führender Camunda-Bearbeitungsprozess zuverlässig gestartet und dem Fall zugeordnet. Unmittelbar nach Annahme kann dieser Start noch ausstehen. Ein technischer Retry darf keine zweite unabhängige Bearbeitung desselben Falls eröffnen. Eine mögliche interne BPMN-Zerlegung wird dadurch nicht vorweggenommen.

Fachliche Statusänderungen erfolgen durch autorisierte PropertyFlow-Anwendungsdienste im vorgesehenen Prozessablauf. Der Browser kann Statuswerte nicht verbindlich vorgeben. PropertyFlow speichert den fachlichen Status als fachliche Sicht auf den Ablauf; es führt daneben keinen konkurrierenden Workflow ein.

Technische Incidents und Wiederholungen schliessen den Fall nicht. Für offene Objektzuordnung, fehlgeschlagene KI-Analyse und klärungsbedürftigen E-Mail-Versand gilt bei aktiven Fällen die vereinbarte Übernahme von «Mitarbeiterprüfung erforderlich» gemäss FALL-05. Dieser fachliche Statuswechsel wird in PropertyFlow gespeichert; eine reine technische Fehlermeldung im Browser ersetzt ihn nicht. Die Ansichten lesen den zuletzt bestätigten fachlichen Status. Die Verwaltung muss den Fall trotz ausgefallener Automatisierung manuell weiterbearbeiten können; die technische Synchronisierung ist im Integrationsvertrag abzusichern.

Ein technischer Abbruch oder ein beliebiges BPMN-Endereignis ist nicht ohne fachliche Zuordnung mit einem erfolgreichen Fallabschluss gleichzusetzen. Massgeblich ist der im Fachprozess ausdrücklich vorgesehene Abschluss.

## FALL-07: Abschluss und Zeit danach

Ein Fall wechselt erst nach dem fachlich vorgesehenen Abschluss im Camunda-Ablauf und der konsistenten Übernahme in PropertyFlow in die abgeschlossene Phase. Eine abgeschlossene KI-Analyse, ein Browserabbruch oder eine verschickte E-Mail genügt dafür nicht.

**Vereinbart am 09.10.2026:** Ein Fall kann durch einen separat authentifizierten Mitarbeiter oder durch das System abgeschlossen werden. «Fachlich erledigt» bedeutet, dass für die Verwaltung kein weiterer Bearbeitungsbedarf besteht. Dies kann eine behobene Störung oder ein eindeutig der Eigenverantwortung des Mieters zugeordnetes Anliegen sein; ein Abschluss behauptet deshalb nicht in jedem Fall eine ausgeführte Reparatur.

| Abschluss durch | Fachliche Voraussetzung / Beispiel |
|---|---|
| **Mitarbeiter** | Ein Mitarbeiter mit der einheitlichen Adminrolle bestätigt den fachlichen Abschluss. Er beurteilt, dass keine weitere Bearbeitung des Falls erforderlich ist. |
| **System – Behebung bestätigt** | Eine gespeicherte Mieter-Nachricht bestätigt eindeutig, dass das Problem behoben ist und keine weitere Hilfe benötigt wird, z. B. «Fehler behoben, keine weitere Hilfe nötig». Das System prüft diese Aussage im Zusammenhang des aktuellen Falls. |
| **System – kein Bearbeitungsbedarf der Verwaltung** | Die fachliche Prüfung ergibt eindeutig, dass das Anliegen gemäss den hinterlegten Fachregeln vom Mieter selbst zu erledigen ist. Beispiel: ein Glühbirnenwechsel, soweit der konkrete Austausch nach diesen Regeln in die Eigenverantwortung des Mieters fällt. |

Für die beiden beschriebenen Systemabschlüsse ist keine zusätzliche Einzelfreigabe durch einen Mitarbeiter vorgesehen, sofern keine bestehende verpflichtende menschliche Prüfung greift. Das System führt den Abschluss im vorgesehenen Camunda-/PropertyFlow-Ablauf aus. Die Einordnung wird vor der Übernahme serverseitig validiert; ein isoliertes Stichwort wie «behoben» oder «Glühbirne» genügt nicht. Die fachliche Regel und der aktuelle Fallinhalt sind massgeblich. Bei unklarer oder widersprüchlicher Einordnung wird nicht automatisch abgeschlossen; nötige menschliche Prüfung wird als «Mitarbeiterprüfung erforderlich» geführt.

Die Beispiele sind bestätigte Abschlussgründe, kein abschliessender Katalog aller künftig möglichen Automatisierungsregeln. Weitere automatische Gründe sind gesondert fachlich festzulegen. Die Einstufung «kein konkreter Fall» bezeichnet hier fehlenden weiteren Bearbeitungsbedarf der Verwaltung: Ein bereits angenommenes Anliegen behält Case-ID, Ursprungsdaten und Historie und erhält den Status «Abgeschlossen». Es wird weder verworfen noch gelöscht.

Bestehende menschliche Prüf- und Freigabegrenzen aus [ADR-002](../architecture/adr/ADR-002-camunda8-workflow-orchestrierung.md) und der [Sicherheitsbasis](../evaluation/evaluationsgrundlage.md) bleiben verbindlich, insbesondere bei kritischen fachlichen Aktionen und externen verbindlichen oder kostenwirksamen Aufträgen. Ein Analysefehler, eine offene Zuordnung oder ein Versandproblem ist für sich kein Abschlussgrund. Die Dringlichkeitsstufe allein begründet ebenfalls keinen Abschluss.

Der Abschluss bleibt mit Zeitpunkt, Abschlussquelle (Mitarbeiter oder System), fachlichem Grund und auslösender Grundlage nachvollziehbar. Bei Mitarbeitern wird die handelnde Identität, beim System die relevante Nachricht bzw. Fachregel und Prozessreferenz intern zugeordnet. Ein durch die Mieternachricht ausgelöster Abschluss hat die Quelle «System»; der Mieter erhält dadurch keine direkte Berechtigung zur Statusänderung. Eine wiederholte Verarbeitung desselben Abschlusses erzeugt keinen zweiten fachlichen Abschluss oder zusätzlichen logischen Abschlussmailauftrag.

Die Folgen für interne und externe Nachrichten sind zentral in [KOM-04](fallkommunikation.md#kom-04-nachrichtensperre-nach-fallabschluss) festgelegt: Nach Abschluss dürfen keine neuen Nachrichten mehr entstehen. Die dort verlangte Synchronisierung muss gleichzeitig mit dem Abschluss greifen, auch bei konkurrierenden Requests und verspäteten Worker-Ergebnissen.

**Zu konkretisierende Abschlussreihenfolge:** Falls eine Abschlussmitteilung vorgesehen ist, muss sie vor Eintritt der Schreibsperre oder als fachlich konsistenter Teil des Abschlusses gespeichert werden. Ein nachträglicher Worker darf die Sperre nicht für eine neue Nachricht umgehen. Der Versand einer bereits gespeicherten externen Mitteilung ist davon als separater Folgeauftrag zu unterscheiden.

Der abgeschlossene Fall bleibt mit gültiger Berechtigung lesbar. Screen 01 bietet keine Wiedereröffnung. Ein erneutes Anliegen wird separat angelegt. Eine globale Wiedereröffnungs-, Zusammenführungs- oder Stornierungsfunktion ist noch nicht spezifiziert und wird durch dieses Dokument nicht eingeführt.

Abschluss ist weder Löschung noch Ablauf einer Zugriffsberechtigung. Archivierung, Aufbewahrung und besondere Löschprozesse benötigen eigene Festlegungen.

## FALL-08: Nachvollziehbarkeit

Case-ID, Eingangszeitpunkt und die gespeicherten Ursprungsdaten müssen die ursprüngliche Annahme eindeutig erkennbar machen. Die Nachrichtenhistorie folgt [KOM-01](fallkommunikation.md#kom-01-interne-und-externe-nachrichten); interne Änderungen werden dadurch nicht automatisch mieteröffentlich.

**Review-Vorschlag für den Statusverlauf:** Für einen fachlichen Statuswechsel werden vorheriger und neuer Status, Serverzeitpunkt, auslösender Akteur bzw. Prozessschritt und eine geeignete Referenz nachvollziehbar festgehalten. Fachliche Änderungen durch Wiederholungen dürfen nicht mehrfach wirksam werden. Datenminimierung und die bestehenden Auditregeln gelten weiterhin.

Zeitpunkte werden in UTC gespeichert; ihre Darstellung ist Sache der jeweiligen Oberfläche. Interne Aktualisierungen und der Zeitpunkt der letzten mieteröffentlichen Änderung bleiben unterscheidbar.

**Vereinbartes Zeitmodell für «Letzte Aktion des Mieters»:** Massgeblich ist der serverseitige Speicherzeitpunkt der letzten angenommenen Mieter-Nachricht; ohne weitere Nachricht gilt der Zeitpunkt der ursprünglichen Einreichung. Ein wiederholter Request für dieselbe Nachricht erzeugt keinen neuen Kontaktzeitpunkt. Lesen, Polling, interne Memos sowie Nachrichten oder Verarbeitungsschritte von Verwaltung und System verändern diesen Wert nicht. Die [Mitarbeiterliste](../frontend/ansicht-02-mitarbeiter-falluebersicht.md) verwendet ihn für Anzeige und Sortierung. Er ist vom Eingang des Falls und von der letzten mieteröffentlichen Änderung zu unterscheiden.

Für wirksame Dringlichkeitsänderungen gilt die bereits vereinbarte dauerhafte Historie nach [DRING-07](dringlichkeitsbewertung.md#dring-07-änderungshistorie-und-mietertransparenz); der obige Review-Vorschlag zum Statusverlauf stellt diese Regel nicht wieder zur Entscheidung.

## FALL-09: Fehler und Grenzfälle

| Ereignis | Fachliches Ergebnis |
|---|---|
| Ungültige Eingabe | Keine erfolgreiche Annahme; Eingabe korrigieren. |
| Fehler vor bestätigtem Commit | Kein Erfolg bestätigen; keine unbestätigte Fallanlage als angenommen darstellen. |
| Antwort geht nach Commit verloren | Fall kann bereits existieren; Wiederholung muss sein vorhandenes Ergebnis liefern. |
| Camunda vorübergehend nicht erreichbar | Angenommener Fall bleibt aktiv; Prozessstart wird zuverlässig nachgeholt. |
| KI-Analyse fehlgeschlagen oder Objektzuordnung offen | Aktiver Fall erhält «Mitarbeiterprüfung erforderlich» und den jeweiligen Hinweis nach FALL-05; gespeicherte Daten und manuelle Bearbeitung bleiben erhalten. |
| RAG oder andere externe Komponente fällt aus | Fall bleibt gespeichert; soweit dadurch eine fehlgeschlagene KI-Analyse oder offene Zuordnung vorliegt, gilt FALL-05. Weitere technische Zustände sind im Integrationsvertrag zuzuordnen. |
| E-Mail-Versand ist klärungsbedürftig | Aktiver Fall erhält «Mitarbeiterprüfung erforderlich» und den Versandhinweis; abgeschlossener Fall bleibt abgeschlossen. Fall, Nachrichten und Case-ID bleiben bestehen. |
| Zusätzliche Nachricht bei aktivem Fall | Bestehenden Fall ergänzen; keinen neuen Fall und keinen zweiten führenden Prozess anlegen. |
| Nachricht trifft während oder nach Abschluss ein | Konsistente Durchsetzung der Nachrichtensperre gemäss KOM-04. |
| Falllink abgelaufen oder widerrufen | Zugriff entfällt; fachlicher Fallstatus ändert sich dadurch nicht. |
| Ähnliche neue Meldung | Keine automatische Zusammenführung ohne gesondert festgelegte Regel. |

## Prüfkriterien

Diese Kriterien dienen der späteren Umsetzung und sind keine bereits ausgeführten Testnachweise. Die Prüfung fachlicher Services muss gemäss Projekt-Kontext ohne reale externe Systeme möglich sein.

### FALL-AK-01

Das Öffnen der Erfassung und eine abgewiesene Eingabe erzeugen keinen angenommenen Fall. Erst nach dauerhaftem Speichern wird die Case-ID mit Erfolg zurückgegeben; Ursprungsmeldung und fachlicher Eingang sind eindeutig nachvollziehbar.

### FALL-AK-02

Wiederholung derselben angenommenen Einreichung liefert dieselbe Case-ID. Doppelklick und verlorene HTTP-Antwort erzeugen weder einen zweiten Fall noch eine zweite Ursprungsnachricht. Abweichender Inhalt mit derselben Absende-ID wird als Konflikt behandelt.

### FALL-AK-03

Ein Ausfall von Camunda, Mail oder KI nach Annahme verliert den Fall nicht. Der Prozessstart kann ausstehen und wird wiederholt, ohne eine zweite unabhängige Bearbeitung zu erzeugen; manuelle Bearbeitung bleibt zugänglich.

### FALL-AK-04

Eine unklare Objektzuordnung verhindert die Annahme nicht. Ursprüngliche Freitextangaben bleiben bei späterer Zuordnung nachvollziehbar. Eine syntaktisch gültige Kontaktadresse wird nicht als bestätigtes Mietverhältnis behandelt.

### FALL-AK-05

Eine Ergänzung zu einem aktiven Fall behält Case-ID und Ursprungsdaten bei. Sie legt keinen neuen Fall an und startet keinen zweiten führenden Bearbeitungsprozess. Der aktuelle BPMN-Schritt bestimmt die weitere Verarbeitung.

### FALL-AK-06

Technische Retries, Incidents, KI-Ergebnisse und Mailzustände bewirken für sich allein keinen fachlichen Abschluss. Eine abgelaufene Zugriffsberechtigung ändert den Fallstatus ebenfalls nicht.

### FALL-AK-07

Der fachlich vorgesehene Abschluss wird in PropertyFlow konsistent übernommen. Die Nachrichtensperre wird nach [KOM-AK-07](fallkommunikation.md#kom-ak-07) auch bei gleichzeitigen Schreibversuchen geprüft. Eine gegebenenfalls gespeicherte Abschlussmitteilung umgeht diese Sperre nicht.

### FALL-AK-08

Mieteransicht und Backoffice lesen den fachlichen Status aus derselben PropertyFlow-Grundlage.
Ein vom Browser eingesandter Statuswert kann den Fall nicht eigenmächtig schliessen oder wiedereröffnen.

### FALL-AK-09

Ein abgeschlossener Fall bleibt bei gültiger Berechtigung lesbar. 
Eine neue erfolgreiche Einreichung erzeugt einen separaten Fall; 
Screen 01 öffnet den alten Fall dabei nicht wieder.

### FALL-AK-10

Jeder der drei Gründe aus FALL-05 führt bei aktivem Fall zu «Mitarbeiterprüfung erforderlich» mit passendem intern gespeichertem Hinweis, auch während automatischer Wiederholungen und bei mehreren gleichzeitigen Gründen. Ein bloss ausstehender Versuch ohne Fehler erzeugt keinen Problemhinweis. Ein nach Abschluss festgestelltes Versandproblem eröffnet den Fall nicht wieder.

### FALL-AK-11

«Letzte Aktion des Mieters» folgt FALL-08. Neue angenommene Mieter-Nachrichten aktualisieren den Wert; Wiederholungen derselben Nachricht, reine Lesezugriffe und interne bzw. System-/Verwaltungsaktionen tun dies nicht.

### FALL-AK-12

Ein authentifizierter Mitarbeiter kann den fachlichen Abschluss nach FALL-07 bestätigen. Das System kann einen Fall ohne zusätzliche Einzelfreigabe abschliessen, wenn eine gespeicherte Mieter-Nachricht im aktuellen Fallzusammenhang eindeutig die Behebung und fehlenden weiteren Hilfebedarf bestätigt oder wenn eine hinterlegte Fachregel eindeutig keinen weiteren Bearbeitungsbedarf der Verwaltung ergibt. Bestehende verpflichtende menschliche Prüfungen bleiben wirksam. Beide Abschlussquellen führen zum selben Status «Abgeschlossen», erhalten Fall und Historie, sperren neue Nachrichten und veranlassen die Abschlussbenachrichtigung. Quelle, Grund und auslösende Grundlage bleiben nachvollziehbar; Wiederholungen duplizieren den Abschluss nicht.

### FALL-AK-13

Eine widersprüchliche Nachricht wie «Die erste Störung ist behoben, aber es läuft weiterhin Wasser aus» genügt nicht für «behoben, keine weitere Hilfe nötig». Das Wort «Glühbirne» ohne gesicherte Zuordnung zur Eigenverantwortung des Mieters, eine niedrige Dringlichkeit oder ein technischer Fehler genügt ebenfalls nicht zum Systemabschluss. Bei unklarer fachlicher Einordnung erfolgt Mitarbeiterprüfung. Der angenommene Fall wird bei fehlendem Verwaltungsbedarf abgeschlossen und nicht verworfen.

## Offene Entscheidungen und Weiterentwicklung

- Konkrete Case-ID-Darstellung und Beziehung zur technischen Prozessreferenz.
- Konkrete Übergangsbedingungen, technische Statuswerte und Mapping der vereinbarten Bearbeitungsstatus aus FALL-05 zum BPMN-Modell.
- Technische Nachweise und Validierung der vereinbarten Abschlussgründe aus FALL-07, konkrete Fachregeln für mieterseitige Eigenverantwortung, gegebenenfalls weitere automatische Abschlussgründe und Reihenfolge einer Abschlussmitteilung. Die Berechtigung von Mitarbeitern und System sowie die beiden beschriebenen Systemgründe sind bereits vereinbart.
- Technische Koordination von Annahme, Prozessstart, Statusübernahme und Nachrichtensperre; Absende-ID-/Retry-Verträge und ihre Aufbewahrungsdauer.
- Nachvollziehbarkeit und Berechtigungen bei Backoffice-Korrekturen und Stammdatenzuordnungen.
- E-Mail-Eingangsvertrag sowie spätere Regelungen für Archivierung, Löschung, mögliche Wiedereröffnung oder Zusammenführung.

Änderungen an diesen fachlichen Regeln werden hier gepflegt. Kommunikationsregeln bleiben in `fallkommunikation.md`, Darstellung und Bedienung in den Screen-Spezifikationen. Bei Umsetzung eines bislang offenen Punktes werden die betroffenen Use-Cases und gegebenenfalls Architekturentscheidungen gezielt fortgeschrieben.
