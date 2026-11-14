# Fallzugriff und Sicherheit

**Status:** Zentrale Spezifikation des persönlichen Fall-Links, der Kontozugriffe und der MCP-Autorisierung. Technische Umsetzung und Sicherheitsparameter im Review.\
**Stand:** 12.11.2026

**Geltungsbereich:** Mieterzugriff über Fall-Link oder Konto, Mitarbeiteranmeldung und Kontoverwaltung, delegierte MCP-Zugriffe sowie Schutz und Versand persönlicher Fall-Links.

## Zweck und Verbindlichkeit

Mieterinnen und Mieter können Anliegen ohne Konto einreichen und über einen persönlichen Link auf den jeweiligen Fall zugreifen. **Der persönliche E-Mail-Link enthält als einzigen variablen Zugangswert einen geheimen Token.** PropertyFlow ermittelt den zugehörigen Fall serverseitig und rendert die Ansicht direkt unter `/mieter/fall/zugang/<geheimer-token>`. Der Benutzer bleibt auf dieser URL. Die lesbare Case-ID bleibt die unveränderliche Referenz des Falls und wird im Inhalt der Fallansicht angezeigt. Der zusätzliche Zugang über ein freiwilliges Mieterkonto und die Regeln für Mitarbeiterkonten und MCP stehen in [ZUG-04](#zug-04-rechte-und-vertrauensgrenzen).

Die Architekturentscheidungen sind in [ADR-004: Persönlicher Fall-Link](../architecture/adr/ADR-004-tokenbasierter-mieterzugriff.md) und [ADR-005: Persönliche Konten und fallbezogener Zugriff über Web und MCP](../architecture/adr/ADR-005-zugriffsmodell.md) dokumentiert. Diese Spezifikation enthält die detaillierten Zugriffsregeln und Prüfkriterien. [Screen 01](../frontend/ansicht-01-mieter-fallansicht.md) beschreibt die Bedienung des Linkzugriffs. [Fallverwaltung](fallverwaltung.md) definiert Fallidentität und Lebenszyklus. [Fallkommunikation](fallkommunikation.md) definiert sichtbare Inhalte, LLM-Datengrundlage und Nachrichtensperren. PropertyFlow autorisiert den Zugriff. Camunda 8 führt die Fallbearbeitung gemäss [FALL-06](fallverwaltung.md#fall-06-fachlicher-status-und-technischer-workflow).

Die fachliche Trennung von Case-ID und geheimem Zugang, die direkt angezeigte Token-URL sowie die Speicherung desselben Tokens als Hash und verschlüsselte Versandkopie sind vereinbart. **Bestätigt am 10.10.2026:** Der persönliche Fall-Link hat keine zeitliche Ablauffrist. Der frühere Laufzeitvorschlag entfällt. Weitere technische Schutzparameter bleiben ausdrücklich als Review-Vorschläge ausgewiesen. Die Spezifikation bestätigt keine implementierten Sicherheitsmechanismen oder ausgeführten Tests.

## ZUG-01: Case-ID und geheimer Token

| Begriff | Aufgabe | Umgang |
|---|---|---|
| Case-ID | Identifiziert dauerhaft einen Fall, beispielsweise `REQ-2026-001` | Wird in der Ansicht angezeigt und kann als Referenz gegenüber der Verwaltung verwendet werden. Gewährt allein keinen Zugriff. |
| Zugriffstoken | Berechtigt seinen Besitzer zu den zulässigen Mieteraktionen für genau einen Fall | Kryptografisch zufällig, geheim, nicht aus der Case-ID oder Kontaktangaben ableitbar. Ohne zeitliche Ablauffrist. Ein administrativer Ersatz sperrt den bisherigen Token. |
| Zugriffsdatensatz | Ordnet den Token-Prüfnachweis dem Fall und seinen erlaubten Aktionen zu | Wird bei jedem Zugriff auf Gültigkeit und Widerruf geprüft. Eine separate Sitzung ist für die Fallberechtigung nicht erforderlich. |

Die «geheime Zahl» im Link übernimmt damit die Rolle des Tokens. Sie muss ausreichend lang und zufällig sein; eine fortlaufende Nummer oder kurze PIN erfüllt diese Anforderung nicht. Ob die Darstellung Ziffern oder URL-taugliche Buchstaben und Ziffern verwendet, ist eine technische Formatentscheidung.

Pro Fall gibt es im Linkmodell genau einen gültigen Fall-Link-Token. Hash und verschlüsselte Versandkopie sind zwei Speicherformen desselben Tokens. Berechtigungen über ein Mieterkonto richten sich nach ZUG-04.

Der Token ist ein zufälliger, serverseitig zugeordneter Schlüssel. Er muss weder die Case-ID noch personenbezogene Daten enthalten. Ein Tokenwechsel ändert weder Case-ID noch Fallhistorie oder Camunda-Prozesszuordnung.

**Besitz vermittelt Zugang, bestätigt aber keine Identität.** Wer den vollständigen gültigen Link erhält, kann die damit erlaubten Mieteraktionen ausführen. Das Weiterleiten eines Links gibt diesen Zugang faktisch weiter: Der Empfänger kann den externen veröffentlichten Verlauf lesen und bei einem aktiven Fall Nachrichten senden. Interne Memos bleiben unzugänglich. Ohne zusätzliche Identitätsprüfung kann PropertyFlow den ursprünglichen Mieter nicht vom Empfänger des weitergeleiteten Links unterscheiden. Eine über diesen Zugang gesendete Nachricht ist dem Fallzugang zugeordnet; die persönliche Identität des Verfassers ist damit nicht nachgewiesen. Die Kenntnis einer Case-ID, E-Mail-Adresse oder Wohnungsreferenz allein genügt niemals.

## ZUG-02: Ausgabe und Erstzugriff

1. Ein neues Anliegen wird nach [FALL-02](fallverwaltung.md#fall-02-erfolgreiche-annahme) dauerhaft angenommen. `GET /mieter/fall` erzeugt weder einen Fall noch eine Berechtigung auf einen bestehenden Fall.
2. PropertyFlow erzeugt einen sicheren Token ohne Ablaufdatum und ordnet seinen Prüfnachweis genau dem angenommenen Fall zu. Ein Zugriffsdatensatz führt mindestens Fallzuordnung, Ausstellungszeitpunkt und Widerrufszustand; das konkrete Datenbankschema bleibt offen.
3. Nach bestätigter Annahme führt die Antwort auf das Erstabsenden per HTTP 303 zur persönlichen Token-URL. Dort sieht die Mieterin oder der Mieter unmittelbar die aktive Fallansicht mit Case-ID, ohne zuerst die E-Mail zu öffnen. Der GET auf diese URL prüft den Token direkt; es folgt keine weitere Weiterleitung auf eine Case-ID- oder sitzungsbasierte URL.
4. Der persönliche Link wird zusätzlich an die bei der Erfassung angegebene E-Mail-Adresse versendet. Mailzustellung und Camunda-/KI-Verarbeitung sind keine Voraussetzung für den fachlichen Annahmeerfolg.

Eine syntaktisch gültige E-Mail-Adresse bestätigt weder die Identität noch das Mietverhältnis. Die Kontaktbestätigung vor produktivem Einsatz bleibt zu konkretisieren. Die Erfassung darf aufgrund ungeprüfter Angaben keine bereits bestehenden Fälle oder fremden Stammdaten zugänglich machen.

Die sichere Wiederholung einer Einreichung folgt [FALL-03](fallverwaltung.md#fall-03-wiederholung-und-doppelte-einreichung). Eine Case-ID oder Absende-ID allein darf dabei keine neue Zugriffsberechtigung auf einen bestehenden Fall auslösen. Die technische Bindung einer Wiederholung an den ursprünglichen Einreichungskontext ist im Servicevertrag festzulegen.

Scheitert nach dauerhafter Annahme die Übergabe des persönlichen Links an den Browser, bleibt der Fall bestehen. Eine sichere Wiederaufnahme darf keinen zweiten Fall erzeugen; ohne gültigen Token werden keine weiteren Falldaten ausgeliefert.

## ZUG-03: Direkter Linkaufruf und Tokenprüfung

1. Der Browser öffnet `/mieter/fall/zugang/<geheimer-token>`.
2. PropertyFlow prüft den Token serverseitig, ermittelt über seinen Prüfnachweis den zugehörigen Fall und kontrolliert Gültigkeit sowie Widerruf. Eine vom Aufrufer zusätzlich übermittelte Case-ID darf diese Zuordnung nicht überschreiben.
3. Bei gültigem Zugang rendert PropertyFlow den aktiven oder abgeschlossenen Fall direkt per Thymeleaf. **Die sichtbare URL bleibt unverändert.** Der Token wird weder aus der URL entfernt noch gegen eine sitzungsbasierte Fallberechtigung ausgetauscht.
4. Auch bei Neuladen, weiteren Datenabrufen und Schreibaktionen über die Fall-Link-Route wird der mitgesendete Token erneut geprüft. Eine Sitzung oder ein Cookie ersetzt diese Prüfung auf der Link-Route nicht. Schreiben erfordert zusätzlich einen aktiven Fall, die erlaubte Aktion und die geltenden Schreibschutzprüfungen.

Der persönliche E-Mail-Link ist zugleich die Browseradresse der Fallansicht und ohne zeitliche Ablauffrist **wiederverwendbar**, bis die Verwaltung ihn gegebenenfalls ersetzt. Er kann erneut geöffnet oder als Lesezeichen gespeichert werden. Ein Linkaufruf verbraucht den Token nicht und verändert keine fachlichen Falldaten. Eine Verlängerung ist nicht erforderlich. E-Mail-Linkscanner dürfen dadurch weder Fälle noch Nachrichten, Freigaben oder Prozessaktionen auslösen.

Ein Token für Fall A berechtigt nicht zu Fall B. Jede Anfrage wird dem Fall des geprüften Tokens zugeordnet. Bei mehreren geöffneten Tabs darf das Öffnen eines zweiten Links Nachrichten aus einem alten Tab niemals in einen anderen Fall umleiten. Eine Übersicht über mehrere Fälle setzt ein verifiziertes Mieterkonto nach ZUG-04 voraus.

Die sichtbare Adresse enthält den Zugangsschlüssel. Kopieren oder Weitergeben der vollständigen Adresse vermittelt dieselbe Berechtigung wie der E-Mail-Link. Token können dadurch auch in Browserhistorie und Mailarchiven verbleiben. Die technischen Schutzregeln aus ZUG-06 gelten deshalb für die gesamte Fallansicht und ihre Aufrufe.

## ZUG-04: Rechte und Vertrauensgrenzen

Die Matrix gilt für das Zielmodell aus [ADR-005](../architecture/adr/ADR-005-zugriffsmodell.md) und für direkte Aufrufe der PropertyFlow-Schnittstellen. Für persönliche Konten gelten die [Rollen und Zusatzrechte](#rollen-und-zusatzrechte).

| Aufrufer und Zugangsnachweis | Lesen | Schreiben und weitere Aktionen |
| --- | --- | --- |
| Ohne Konto und ohne Fall-Link | Öffentliches Erfassungsformular. | Neues Anliegen einreichen. |
| Browser mit gültigem persönlichem Fall-Link | Genau den dem Token zugeordneten Fall mit veröffentlichtem Verlauf, wirksamer Dringlichkeit und freigegebener Stufenhistorie nach DRING-07. | Eine externe Nachricht im zugeordneten aktiven Fall senden. |
| Angemeldeter Mieter mit verifizierter E-Mail-Adresse | Alle Fälle mit derselben aktuellen Kontaktadresse und deren mieteröffentliche Daten. | Neues Anliegen einreichen und in einem zugehörigen aktiven Fall eine externe Nachricht senden. |
| Angemeldeter Mitarbeiter mit aktiver Mitarbeiterrolle | Alle Fälle im Mitarbeiterbereich mit veröffentlichtem Verlauf und internen Memos. Die Volltextsuche umfasst diese freigegebenen Inhalte. | Im Fallprozess vorgesehene Fachaktionen anstossen, etwa die Dringlichkeit einstufen, ein Memo erfassen, eine Nachricht veröffentlichen oder den Abschluss bestätigen. Camunda veranlasst die Statuswechsel gemäss FALL-06. |
| Angemeldete Person mit aktiver Mitarbeiterrolle und zusätzlichem Kontoverwaltungsrecht | Geschützte Kontoverwaltung. Fallzugriff richtet sich nach der Mitarbeiterrolle. | Mitarbeiterkonten anlegen und die Mitarbeiterrolle gemäss UC-007 vergeben. Mitarbeiterkonten sperren oder die Mitarbeiterrolle gemäss UC-029 entziehen. |
| Angemeldete Person mit aktiver Mitarbeiterrolle und dem Zusatzrecht «Fallzugänge verwalten» | Geschützte Fallzugangsverwaltung für berechtigte Fälle. | Nach serverseitig hinterlegter Prüfung einen Fall-Link ersetzen oder die Fall-Kontaktadresse desselben Mieters korrigieren (UC-024, UC-025). |
| MCP-Client mit gültigem OAuth-Zugriffstoken eines angemeldeten Benutzers | Freigegebene Tools und Daten für Fälle, auf die das Konto aktuell Zugriff hat. | Ausdrücklich delegierte fachliche Aktionen innerhalb der Kontorechte und der Regeln für den konkreten Fall. |
| Camunda-Integration und Hintergrunddienste mit technischer Identität | Nur die für die jeweilige Aufgabe erforderlichen Daten. | Definierte Systemaktionen über autorisierte PropertyFlow-Dienste. Die Worker speichern die von Camunda veranlassten Statuswechsel. Die Entscheidung zur Wiedereröffnung bleibt bei Mitarbeitenden gemäss FALL-07. |

### Rollen und Zusatzrechte

| Rolle oder Zusatzrecht | Wie das Konto sie erhält | Fachlicher Umfang |
| --- | --- | --- |
| **Mieter** | Durch freiwillige Registrierung und Verifikation der E-Mail-Adresse gemäss [UC-005](../use_cases/UC-005-userkonto-eroeffnen.md). | Die Person kann neue Anliegen einreichen, Fälle mit passender aktueller Kontaktadresse und deren mieteröffentliche Daten einsehen und diese Fälle in der aktiven Phase mit externen Nachrichten ergänzen. |
| **Mitarbeiter** | Eine berechtigte Person der Verwaltung legt das Konto mit der geschäftlichen E-Mail-Adresse an und weist ihm die Mitarbeiterrolle gemäss [UC-007](../use_cases/UC-007-mitarbeiterkonto-anlegen.md) zu. | Die Person kann im Mitarbeiterbereich Fälle und interne Memos einsehen und suchen sowie die im Fallprozess zulässigen Bearbeitungsaktionen anstossen. Für kritische Handlungen gilt [FALL-10](fallverwaltung.md#fall-10-automatisierung-und-menschliche-freigabe). |
| **Mitarbeiterkonten verwalten** | Das erste Verwaltungskonto erhält das Zusatzrecht zur Mitarbeiterrolle bei der Servereinrichtung gemäss [UC-007](../use_cases/UC-007-mitarbeiterkonto-anlegen.md). | Die Person kann in der geschützten Kontoverwaltung Mitarbeiterkonten anlegen, die Mitarbeiterrolle vergeben, Mitarbeiterkonten sperren oder die Mitarbeiterrolle entziehen. Der Fallzugriff richtet sich nach der Mitarbeiterrolle. |
| **Fallzugänge verwalten** | Separates Zusatzrecht für dafür zuständige Mitarbeitende. Die technische Rechtevergabe ist bei der Umsetzung festzulegen. | Fall-Link ersetzen und die Fall-Kontaktadresse nach der Prüfung gemäss ZUG-05 korrigieren. |

Der Entzug des Mitarbeiterzugriffs setzt eine aktive Mitarbeiterrolle und
das Zusatzrecht «Mitarbeiterkonten verwalten» voraus. Nach der Aktion muss
mindestens ein aktives Konto mit Mitarbeiterrolle und Kontoverwaltungsrecht
verbleiben. Diese Prüfung gilt auch bei parallelen Aktionen. Den Ablauf und
die Wirkung auf bestehende Sitzungen und Delegationen beschreibt
[UC-029](../use_cases/UC-029-mitarbeiterzugriff-entziehen.md).

Der persönliche Fall-Link gewährt Zugriff auf den zugeordneten Fall gemäss
[ZUG-03](#zug-03-direkter-linkaufruf-und-tokenprüfung). Der MCP-Client handelt
im Rahmen der [delegierten Kontorechte](#gemeinsame-anmeldung-und-mcp-delegation).
Camunda und Hintergrunddienste handeln mit einer technischen Identität. Für
diese Zugriffe gelten die jeweiligen Zeilen der Zugriffsmatrix.

### Mieterzugriff

Die Einreichung und der persönliche Fall-Link bleiben ohne Konto nutzbar. Ein freiwilliges Mieterkonto wird nach der E-Mail-Verifikation gemäss [UC-005](../use_cases/UC-005-userkonto-eroeffnen.md) aktiviert. PropertyFlow ermittelt die Fallmenge anhand der verifizierten Konto-Adresse und der aktuellen Fall-Kontaktadresse. Das gilt auch für frühere Einreichungen ohne Anmeldung. Der Dienst prüft diese Zuordnung beim Listenabruf und bei jedem Detail- oder Schreibzugriff im Web und über MCP. Nach einer geprüften Änderung der Fall-Kontaktadresse gilt die neue Zuordnung ab dem nächsten Zugriff.

**Gemeinsame E-Mail-Regel.** Bei Fallanlage und Konto verwendet PropertyFlow dieselbe serverseitige Adressprüfung. Die Prüfung entfernt äussere Leerzeichen und weist Adressen mit Zeichen ausserhalb von ASCII vor der Umwandlung in Kleinbuchstaben ab. Für die geprüfte Adresse gelten die bisherige Kontosyntax mit einer Domain aus mehreren Bezeichnern und höchstens 254 Zeichen insgesamt. Der Teil vor dem `@` hat höchstens 64 Zeichen und enthält keine führenden, abschliessenden oder aufeinanderfolgenden Punkte.

Für den Kontozugriff müssen die nach dieser Regel vereinheitlichte Fall-Kontaktadresse und die verifizierte Konto-Adresse exakt übereinstimmen. Eine bereits gespeicherte Fall-Kontaktadresse, die diese Prüfung nicht besteht, gewährt keinen kontobasierten Zugriff. Für den persönlichen Fall-Link gilt die Tokenprüfung nach [ZUG-03](#zug-03-direkter-linkaufruf-und-tokenprüfung).

Die Mieterrechte umfassen veröffentlichte Falldaten und externe Nachrichten. Der Zugriff auf interne Memos und deren Metadaten ist dem Mitarbeiterbereich vorbehalten. Fachaktionen wie eine Dringlichkeitseinstufung, Freigabe, Abschluss oder Wiedereröffnung richten sich nach den jeweiligen Mitarbeiter- und Systemrechten im Fallprozess. Ein abgeschlossener Fall bleibt bei gültiger Berechtigung lesbar. Für Nachrichten gilt die Schreibsperre nach [KOM-04](fallkommunikation.md#kom-04-nachrichtensperre-nach-fallabschluss).

**Lokaler Implementierungsstand (13.11.2026):** Selbstregistrierung, E-Mail-Codeprüfung,
«Meine Fälle» sowie die Prüfung bei Detail- und Nachrichtenzugriffen sind im Profil
`frontend-demo` umgesetzt. Die Konto-Adresse stammt aus der Sitzung; Formularwerte
ändern keine Zuordnung oder Rolle. Für die Codeprüfung gelten die [gemeinsamen
Einmalcode-Regeln](#e-mail-einmalcode-für-registrierung-und-anmeldung).
Konten und Fälle liegen im Arbeitsspeicher. Dauerhafte Speicherung, persönliche
Audit-Zuordnung und OAuth-/MCP-Delegation sind damit noch nicht umgesetzt.
Die [Testanleitung](../../src/test/README.md) beschreibt die Prüfungen mit Mailpit.

### Mitarbeiteranmeldung und Kontoverwaltung

UI2 und UI3 setzen für Lesen und Schreiben ein aktives, persönlich angemeldetes Konto mit Mitarbeiterrolle voraus. Die Anmeldung per E-Mail-Einmalcode beschreibt [UC-006](../use_cases/UC-006-mitarbeiter-anmelden.md). Für Kontoanlage und Rollenvergabe gelten die [Rollen und Zusatzrechte](#rollen-und-zusatzrechte).

Die fachlichen Fallrechte sind für die Mitarbeiterrolle einheitlich. Vor Prozessstart und bei unerreichbarem Camunda können Mitarbeitende Fälle einsehen. Für Änderungen im Fallprozess muss Camunda die konkrete Fachaktion erlauben. PropertyFlow setzt diese Grenze auch bei direkten Web- und MCP-Aufrufen durch. Für die Prüfung und Freigaben gelten [FALL-06](fallverwaltung.md#fall-06-fachlicher-status-und-technischer-workflow), [FALL-07](fallverwaltung.md#fall-07-abschluss-und-zeit-danach) und [FALL-10](fallverwaltung.md#fall-10-automatisierung-und-menschliche-freigabe). PropertyFlow ordnet Änderungen und Freigaben der persönlichen Benutzer-ID zu. Fehlende oder abgelaufene Anmeldungen sperren den Mitarbeiterzugriff bis zur erneuten Anmeldung. Bei einem gesperrten Konto oder entzogener Mitarbeiterrolle weist der Dienst den nächsten Zugriff zurück.

### E-Mail-Einmalcode für Registrierung und Anmeldung

Bei der freiwilligen Mieterregistrierung dient der Code als Nachweis für die
angegebene E-Mail-Adresse. Für die Anmeldung von Mietern und Mitarbeitenden
verwendet PropertyFlow die im berechtigten Konto hinterlegte Adresse.

- Die öffentliche Rückmeldung auf eine Code-Anforderung bleibt neutral. Sie
  legt weder den Kontostatus noch zugehörige Fälle offen.
- PropertyFlow prüft die Zuordnung des Codes zur E-Mail-Adresse und zum
  vorgesehenen Konto oder Registrierungsvorgang serverseitig. Ein Code ist
  zeitlich begrenzt und einmal verwendbar. Ein ungültiger, abgelaufener oder
  bereits verbrauchter Code eröffnet keine Sitzung und aktiviert kein Konto.
- Eine Neuregistrierung aktiviert das Mieterkonto erst nach erfolgreicher
  Verifikation. Bei der Anmeldung muss das bestehende Konto bei der Prüfung
  weiterhin für den angeforderten Zugang berechtigt sein.
- Anforderungs- und Versuchslimits begrenzen Wiederholungen. Ein neuer Code kann
  im Rahmen der Anforderungslimits angefordert werden.

**Lokale Frontend-Demo (13.11.2026):** Der Code gilt zehn Minuten ab Anforderung.
Nach fünf Fehlversuchen ist er gesperrt. Zwischen zwei Anforderungen für
dieselbe E-Mail-Adresse liegt mindestens eine Minute. Innerhalb einer gleitenden
Stunde sind höchstens fünf Anforderungen je Adresse möglich.
Registrierung und Anmeldung teilen die Anforderungslimits. Produktive
Laufzeiten, Limits und die technische Behandlung von Versandfehlern sind im
Anmeldevertrag zu konkretisieren.

### Gemeinsame Anmeldung und MCP-Delegation

Web und MCP nutzen für Mieter und Mitarbeitende denselben Anmeldedienst. Eine gültige Anmeldung im selben Browserprofil kann für beide Zugänge verwendet werden. Der MCP-Client erhält nach der Freigabe durch die Person ein eigenes OAuth-Zugriffstoken für den MCP-Endpunkt über HTTP. Der persönliche Fall-Link gilt als Browserzugang für seinen Fall.

Web und MCP verwenden dieselben fachlichen Anwendungsdienste. Bei MCP prüft PropertyFlow das Zugriffstoken, das aktuelle Konto und seine Rolle, die Delegation an den Client, die Berechtigung am konkreten Fall und den fachlichen Zustand. Für kritische Aktionen prüft der Dienst die vorgesehene menschliche Freigabe für genau diese Handlung. Ein Widerruf der Delegation sperrt den betroffenen MCP-Zugriff bei der nächsten Anfrage. Bei Schreibversuchen protokolliert PropertyFlow Benutzer-ID, Kanal, gegebenenfalls MCP-Client, Aktion, Fallreferenz, Zeitpunkt und Ergebnis.

Kontaktadresse, Fallzuordnung, Absenderrolle, Status, Sichtbarkeit, Veröffentlichungskennzeichen und Zeitstempel werden serverseitig bestimmt beziehungsweise geprüft. Tool-Argumente, manipulierte Pfade, Formularfelder und Modelltexte erweitern keine Berechtigungen. PropertyFlow verantwortet die Autorisierung. Der Browser greift ausschliesslich über PropertyFlow auf Fallfunktionen zu. Zugriffstokens, Einmalcodes und Sitzungsschlüssel gehören weder in Audit-Protokolle noch in Camunda-Prozessvariablen, LLM-Prompts, RAG-Indizes oder Tool-Kontexte.

### Web-Abmeldung

- Die Web-Abmeldung erfolgt über eine CSRF-geschützte Schreibaktion. Ein GET
  oder ein fehlender beziehungsweise ungültiger CSRF-Nachweis löst keine
  Abmeldung aus.
- PropertyFlow beendet die aktuelle lokale Browsersitzung serverseitig.
  Weitere kontobasierte Zugriffe mit dieser Sitzung erfordern eine erneute
  Anmeldung. Das gilt auch für Aufrufe aus bereits geöffneten Tabs.
  Ungesendete Eingaben werden bei der Abmeldung nicht gespeichert.
- Persönliche Fall-Links behalten ihre Gültigkeit gemäss [ZUG-05](#zug-05-dauerhafte-gültigkeit-und-direkter-ersatz).
  Bereits erteilte MCP-Delegationen unterliegen weiterhin dem eigenen
  [Widerruf](#gemeinsame-anmeldung-und-mcp-delegation).

Die genaue Logout-Route und ein übergreifender Logout am gemeinsamen
Anmeldedienst bleiben im technischen Zugriffsvertrag zu konkretisieren.

<a id="zug-05-dauerhafte-gültigkeit-widerruf-und-ersatz"></a>

## ZUG-05: Dauerhafte Gültigkeit und direkter Ersatz

- **Bestätigt am 10.10.2026:** Der persönliche Fall-Link läuft zeitlich nicht ab. Weder Inaktivität noch eine feste Dauer seit Ausstellung machen ihn ungültig. Auch der Fallabschluss setzt keine Ablauffrist. Eine Verlängerungs- oder Erneuerungsaktion aufgrund des Alters des Links ist nicht vorgesehen; die vorgeschlagene UI3-Aktion «Fall-Link ersetzen» wird nicht eingeführt.
- **Vereinbart am 13.11.2026:** PropertyFlow aktiviert beim administrativen Ersatz einen neuen unabhängigen Token und sperrt den bisherigen in derselben Datenbanktransaktion. Nach dem Commit gilt der neue Link für denselben Fall. Weitere Lese- und Schreibaufrufe mit dem alten Token werden auch aus bereits geöffneten Tabs abgewiesen. Scheitert der Ersatz, gilt der bisherige Link weiter. Case-ID, Historie und Bearbeitungsstand bleiben erhalten.
- Der Zielvertrag für den administrativen Linkersatz und die Adresskorrektur verlangt das Zusatzrecht «Fallzugänge verwalten». Die Aktionen laufen in der separaten Fallzugangsverwaltung ausserhalb von UI3 und sind auch bei einem Camunda-Ausfall möglich. [UC-024](../use_cases/UC-024-fall-link-widerrufen-oder-ersetzen.md) · [UC-025](../use_cases/UC-025-fall-kontaktadresse-aendern.md)
- PropertyFlow verlangt für jede Aktion einen serverseitig hinterlegten Prüfnachweis für Identität und bestätigte Adresse. Er ist an Fall, handelnde Person, geprüften Fallstand und Aktion gebunden, befristet und einmal verwendbar. Das konkrete Prüfverfahren und die Bedienung bleiben festzulegen.
- Bei geprüfter Adresskorrektur desselben Mieters speichert PropertyFlow die neue aktuelle Adresse und ersetzt den bisherigen Link gemeinsam. Die neue Adresse gilt beim nächsten kontobasierten Abruf im Web und über MCP. Nach beiden Aktionen geht der gültige Link nachgelagert an die bestätigte Adresse. Der Versand liest die aktuell bestätigte Adresse und den aktuell gültigen Link aus PropertyFlow.
- Automatischer öffentlicher Linkersatz ist eine optionale Erweiterung. Er muss neutral antworten, darf weder Fallbestand noch Kontaktzuordnung offenlegen und benötigt Missbrauchsschutz.
- Normales Öffnen und normale Benachrichtigungen rotieren den Token nicht. Auch nach dem Fallabschluss bleibt der Fall über den gültigen Link lesbar. Die fachliche Abschlusssperre verhindert weitere Schreibzugriffe.
- Die Aufbewahrung der Falldaten folgt eigenen Regeln. Der Ersatz betrifft ausschliesslich den persönlichen Zugang. Eine berechtigende Fallsitzung ist im direkten Tokenmodell nicht vorgesehen.

## ZUG-06: Schutz von Token und Falldaten

Tokens werden über eine kryptografisch sichere Zufallsquelle erzeugt und über HTTPS übertragen. Für die Verifikation wird ein sicherer Token-Hash gespeichert; der Hash ermöglicht die serverseitige Zuordnung zum Zugriffsdatensatz. Die konkrete Mindestentropie und Umsetzung werden im Review unten festgehalten. Klartexttokens gehören nicht in gewöhnliche Falltabellen.

### Speicherung und Wiederverwendung für E-Mails

Der persönliche Link bleibt bei normalen Benachrichtigungen gleich. Damit PropertyFlow ihn später erneut versenden kann, wird neben dem Hash eine **verschlüsselte Kopie desselben Tokens** in einem separat geschützten Speicherbereich aufbewahrt. Aus dem Hash lässt sich der ursprüngliche Token nicht zurückgewinnen.

| Speicherform | Zweck | Zugriff |
|---|---|---|
| Token-Hash mit Fallzuordnung, Gültigkeit und Widerrufszustand | Token aus einer eingehenden Anfrage prüfen und den Fall bestimmen | Zugriffsprüfung; keine Entschlüsselung erforderlich. |
| Verschlüsselte Tokenkopie | Den identischen persönlichen Link für spätere E-Mails wiederherstellen | Ausschliesslich die dafür berechtigte Versandfunktion. |
| Verschlüsselungsschlüssel | Die Versandkopie verschlüsseln und für den Versand entschlüsseln | Separat verwaltetes Anwendungsgeheimnis ausserhalb der Datenbank und des Git-Repositorys. |

**Ablauf:**

1. Bei der Fallanlage erzeugt PropertyFlow den Token einmal und speichert Hash sowie verschlüsselte Versandkopie mit derselben Fallzuordnung. Der Hash dient auch weiterhin zur Prüfung jedes Browserzugriffs.
2. Ein Benachrichtigungsauftrag referenziert den Fall. Die Versandfunktion ermittelt den zugehörigen gültigen Zugriffsdatensatz beim Versand.
3. Die Versandfunktion prüft den aktuellen Zugang, entschlüsselt dessen Versandkopie und setzt den Link `/mieter/fall/zugang/<geheimer-token>` zusammen.
4. Der lesbare Token wird nur vorübergehend für die Linkbildung und Mailübergabe verwendet. Er darf nicht in reguläre Protokolle, Fehlerberichte oder ungeschützte Warteschlangen geschrieben werden. Erledigte Versandaufträge werden gemäss der noch festzulegenden Aufbewahrungsfrist bereinigt; die geschützte Tokenkopie bleibt für weitere Benachrichtigungen während der Zugangsgültigkeit verfügbar.
5. Jede normale Benachrichtigung und jede Wiederholung verwendet denselben gültigen Token ohne zeitliche Ablauffrist. Weder Versand noch Öffnen einer E-Mail erzeugt einen zusätzlichen Zugang; eine Verlängerung entfällt.

Nur die Versandfunktion erhält die Möglichkeit zur Entschlüsselung. Fallbearbeitung, Browser, Camunda und KI benötigen weder die Versandkopie noch den Schlüssel. Der Schlüssel wird über eine geschützte Betriebskonfiguration beziehungsweise Geheimnisverwaltung bereitgestellt; konkrete Verfahren und technische Berechtigungen werden bei der Umsetzung festgelegt. Eine Datenbankkopie allein soll nicht ausreichen, um gültige Falllinks zu rekonstruieren. Ein kompromittierter Anwendungsserver mit Zugriff auf Schlüssel und Versandkopien ist durch diese Trennung allein nicht abgesichert.

**Ersatz und Fehler:** Beim direkten Ersatz nach ZUG-05 speichert PropertyFlow den neuen Hash und die neue verschlüsselte Versandkopie mit der Aktivierung des neuen Zugangs. Verzögerte Aufträge verwenden den beim Versand aktuell gültigen Link. Die Sperre des alten Links gilt auch für bereits verschickte E-Mails.

Fehlen Schlüssel oder Versandkopie oder schlägt die Entschlüsselung fehl, bleibt der Versandauftrag als fehlgeschlagen beziehungsweise erneut ausführbar erkennbar. Die Fallannahme, gespeicherte Nachrichten und vorhandenen Zugriffsrechte bleiben bestehen. Fehler enthalten keinen Token oder Schlüssel. Wiederholungen und die sichtbare Klärung folgen [BEN-04](benachrichtigungen-und-zustellung.md#ben-04-wiederholungen-und-zustellnachweis).

Verschlüsselte Kopien nicht mehr gültiger Tokens werden nicht für weitere Benachrichtigungen genutzt und gemäss einer noch festzulegenden Löschregel entfernt. Verschlüsselungsverfahren, Schlüsselrotation, geschützte Sicherungen und Löschfristen bleiben Umsetzungsentscheidungen. Ein Wechsel des Verschlüsselungsschlüssels ist kein Wechsel des persönlichen Zugriffstokens.

Die E-Mail weist auf die Wirkung der Linkweitergabe hin: **«Dieser Link ermöglicht Zugriff auf Ihren Fall. Bitte behandeln Sie ihn vertraulich.»** Ein Ersatz sperrt den alten Link für alle Besitzer; bereits gelesene oder kopierte Inhalte können dadurch nicht zurückgeholt werden.

Soweit technische Cookies eingesetzt werden, verwenden sie `Secure`, `HttpOnly` und `SameSite=Lax`. Sie können beispielsweise dem Formularschutz dienen, ersetzen jedoch niemals die Tokenprüfung. Schreibende Browseraufrufe benötigen CSRF-Schutz; der Zugriffstoken wird nicht als CSRF-Nachweis wiederverwendet. Rate Limits schützen Neuanlage, Tokenprüfung, Nachrichtensenden und gegebenenfalls Linkersatz; konkrete Grenzwerte bleiben festzulegen.

Für Zugangs- und Fallantworten einschliesslich Weiterleitungen gelten `Referrer-Policy: no-referrer` und `Cache-Control: no-store`. Es werden keine Drittanbieterressourcen oder Trackingdienste eingebunden. Tokens dürfen nicht in Anwendungs-, Webserver-, Proxy-, Zugriffs- oder Fehlerlogs, Analytics oder Traces gelangen. **Da der Token in der dauerhaft sichtbaren Falladresse und in Schreibpfaden steht, muss auch der URL-Pfad vor der Protokollierung redigiert werden.** Nur Queryparameter zu filtern genügt nicht.

Freitext und Tokens werden nicht in Local Storage gespeichert. Fehlerantworten enthalten weder Stacktraces noch Secrets oder interne Inhalte. Eine nicht sensitive Fehlerreferenz kann die Abklärung ermöglichen.

## ZUG-07: Geplanter HTTP-Vertrag

Die folgenden Routen konkretisieren das vereinbarte direkte Tokenmodell für Spring Boot und Thymeleaf. Sie beschreiben keine bereits vorhandenen Controller. Die GET-Fallansicht bleibt unter derselben Token-URL wie der persönliche E-Mail-Link.

**Persönlicher E-Mail-Link, mit Beispieldomain und Platzhalter:**

`https://propertyflow.example/mieter/fall/zugang/<geheimer-token>`

| Methode und Route | Vertrag |
|---|---|
| `GET /mieter/fall` | Neues Formular. Kein Fallzugriff und keine fachliche Schreibaktion. |
| `GET /mieter/fall/zugang/{token}` | Token prüfen, Fall serverseitig ermitteln und unmittelbar rendern; keine Weiterleitung auf eine andere Falladresse. |
| `POST /mieter/fall` | Anliegen nach FALL-02 annehmen, Token ausgeben und per HTTP 303 auf `/mieter/fall/zugang/{token}` weiterleiten. |
| `POST /mieter/fall/zugang/{token}/nachrichten` | Token, daraus abgeleitete Fallberechtigung, CSRF und aktiven Status prüfen; Nachricht speichern und per HTTP 303 zur gleichen Token-Fallansicht zurückkehren. |

Ein gültiger GET auf den persönlichen Link liefert die Fallansicht direkt aus. Es gibt keinen Wechsel auf eine tokenfreie oder Case-ID-basierte Falladresse. HTTP 303 wird nach erfolgreichen POSTs für Post/Redirect/Get verwendet: Nach Fallanlage führt er erstmals zur Token-Fallansicht, nach einer Nachricht zurück zu genau dieser Ansicht. Das ist keine Weiterleitung beim Öffnen des Falllinks.

Die lesbare Case-ID bleibt im Inhalt von Screen 01 sichtbar und kopierbar. Das Kopieren der Case-ID allein gibt keine Zugriffsberechtigung weiter. Die vollständige Tokenadresse hingegen ist der persönliche Zugangsschlüssel. Die früheren Vorschläge mit Case-ID plus Tokenparameter sowie mit einer Weiterleitung auf eine tokenfreie Sitzungsadresse sind durch dieses direkte Modell ersetzt.

## ZUG-08: Fehlerverhalten

| Situation | Verbindliches Verhalten |
|---|---|
| Token fehlt, ist falsch, widerrufen oder ersetzt | Neutrale Ablehnung ohne Falldaten und ohne Bestätigung einer Fall- oder Kontaktzuordnung. Kein stiller Wechsel in eine Neuanlage. |
| Token, zusätzliche Case-ID oder Nachrichten-ID wurde verändert | Fall ausschliesslich aus dem gültigen Token bestimmen; Objektberechtigung prüfen und unberechtigten Zugriff ohne Datenoffenlegung ablehnen. |
| Linkersatz bei geöffneter Seite | Weitere Lese- und Schreibaufrufe mit dem alten Token sperren. Ungesendeten Text nicht automatisch speichern; bereits ausgelieferte oder kopierte Inhalte lassen sich dadurch nicht zurückholen. |
| CSRF-Prüfung schlägt fehl | Schreibaktion ohne fachlichen Seiteneffekt ablehnen. |
| Fall wurde während der Eingabe geschlossen | Nachricht gemäss KOM-04 zurückweisen; berechtigtes Lesen bleibt möglich. |
| Web-Anmeldung fehlt oder ist abgelaufen | Geschützte Inhalte und Aktionen sperren und zur Anmeldung führen. Die abgewiesene Schreibaktion wird nach der Anmeldung nicht automatisch erneut ausgelöst. |
| Gültige Web-Anmeldung ohne erforderliche Rolle oder Fallberechtigung | Zugriff ohne Offenlegung geschützter Inhalte oder Schreibwirkung verweigern. |
| MCP-Zugriffstoken ungültig oder Delegation, Rollenrecht oder Fallberechtigung fehlt | Aufruf serverseitig ablehnen. Der Client erhält keine geschützten Daten und die Aktion hat keine fachliche Wirkung. |
| Rate Limit erreicht | Ablehnen und eine verständliche Warteinformation geben, gegebenenfalls mit `Retry-After`. |
| Technischer Fehler oder unklarer Absendeausgang | Keinen falschen Erfolg bestätigen; keine Berechtigung umgehen. Bereits angenommene Fälle bleiben gemäss FALL-03/FALL-09 bestehen. |

Die konkreten Mietertexte und das Verhalten noch sichtbarer Eingaben werden in [Screen 01, Abschnitt 6](../frontend/ansicht-01-mieter-fallansicht.md#6-persönlicher-fall-link-und-datenschutz) beschrieben. Eingabeerhalt darf keine fehlende Berechtigung umgehen und ist nach einem vollständigen Browser- oder Netzwerkausfall nicht garantiert.

## Review-Vorschläge und offene Entscheidungen

Die bisher in Screen 01 diskutierten Werte werden hier weitergeführt und sind **noch keine beschlossenen Sicherheitsparameter**:

| Punkt | Bisheriger Vorschlag beziehungsweise offene Entscheidung |
|---|---|
| Tokenstärke | Mindestens 256 Bit kryptografische Zufallsentropie; URL-taugliche Darstellung und konkrete Hash-Verifikation im Service-Design festlegen. |
| Routen | Direkte Fallansicht unter `/mieter/fall/zugang/{token}` gemäss ZUG-07; weitere Backend-Verträge werden bei der Umsetzung konkretisiert. |
| Kontaktbestätigung und Linkersatz | Verfahren zur Identitäts- und Adressprüfung, Rechtevergabe und Oberfläche konkretisieren. Der Zielvertrag definiert Prüfnachweis, Berechtigungsprüfung und Audit. |
| Erstzugriff und Wiederholung | Sichere Bindung an den Einreichungskontext sowie Koordination von Annahme, Zugriffsdatensatz und Versandauftrag festlegen. |
| Umsetzung der Token-Aufbewahrung | Hash für die Prüfung und separat verschlüsselte Kopie desselben Tokens für den Versand sind vereinbart. Verschlüsselungsverfahren, Schlüsselbereitstellung und -rotation, technische Entschlüsselungsrechte, Sicherungen und Löschfristen konkretisieren. |
| Schutzparameter und Betrieb | Rate-Limit-Werte, Hash-Verfahren, Aufbewahrungsfristen, Formularschutz und technische Durchsetzung der Log-Redaktion konkretisieren. |

Der persönliche Fall-Link ist nach ZUG-05 zeitlich unbegrenzt gültig. PropertyFlow prüft bei jeder Anfrage, ob der Token dem Fall zugeordnet und weiterhin aktiv ist und ob er die angefragte Aktion erlaubt. Die Laufzeiten für Kontositzungen, Einmalcodes und MCP-Zugriffstokens werden separat festgelegt.

Für Block 2 sind echte Tokenausgabe und echter E-Mail-Versand noch nicht erforderlich. Die Mock-Ansicht muss ihre Grenzen erkennen lassen und darf keinen produktiven Zugriffsschutz vortäuschen. Die tatsächliche Autorisierung und Integration folgen im Service-Block, Persistenz und Aufbewahrung im Persistenz-Block. Eine Überarbeitung akzeptierter Architekturentscheidungen benötigt weiterhin einen eigenen ADR-Entscheid.

## Prüfkriterien

Diese Kriterien sind Anforderungen an spätere Tests und Reviews, keine bereits erbrachten Sicherheitsnachweise.

### ZUG-AK-01

Der persönliche E-Mail-Link enthält als variablen Zugangswert ausschliesslich einen Token. Der Server ermittelt daraus den richtigen Fall. Die sichtbare Case-ID bleibt bei erneutem Öffnen und Tokenersatz unverändert; Case-ID, Kontaktangaben und Absende-ID allein erlauben keinen Fallzugriff.

### ZUG-AK-02

Erzeugung und Speicherung werden gegen die beschlossenen Sicherheitsparameter geprüft: kryptografische Zufallsquelle, ausreichende Entropie, eindeutige Fallbindung und sicherer Prüfnachweis. Gewöhnliche Falltabellen und Sitzungen enthalten keinen Klartexttoken; geschützte Versandaufträge werden nach Abschluss bereinigt.

### ZUG-AK-03

Nach erfolgreicher Annahme öffnet der berechtigte einreichende Browser den Fall ohne vorherigen E-Mail-Aufruf. Eine technische Wiederholung erzeugt weder einen zweiten Fall noch aufgrund einer fremden Case-ID oder Absende-ID unberechtigten Zugang. Mail- oder Prozessausfälle heben die bestätigte Annahme nicht auf.

### ZUG-AK-04

Ein gültiger Link liefert die Fallansicht direkt unter derselben Token-URL aus, ohne Redirect und ohne erforderliche berechtigende Sitzung. Wiederholtes Öffnen, Neuladen und ein Aufruf ohne bestehende Cookies prüfen den Token erneut. Auch nach längerer Inaktivität öffnet der aktuelle Link die Fallansicht. Das Öffnen verbraucht den Token nicht und erzeugt keine fachlichen Änderungen, auch beim Abruf durch einen Linkscanner.

### ZUG-AK-05

Mit einer Berechtigung für Fall A können weder Fall B noch dessen Nachrichten gelesen oder beschrieben werden. Manipulierte Pfade und Formularwerte sowie mehrere offene Tabs werden geprüft; eine Nachricht darf niemals durch einen Kontextwechsel im falschen Fall landen.

### ZUG-AK-06

Ein erfolgreicher Ersatz aktiviert den neuen Link und sperrt gleichzeitig alle weiteren Zugriffe mit dem alten Token, auch aus bereits geöffneten Tabs oder mit vorhandenen Cookies. Der neue Link öffnet denselben Fall. Bei einem fehlgeschlagenen Commit bleibt der bisherige Link gültig. Verzögerte Versandaufträge verwenden den jeweils aktuellen Link. Der Zugriff auf einen gültigen Tokenlink hängt nicht von einer Fallsitzung ab.

### ZUG-AK-07

Mieterzugriff umfasst nur die Rechte nach ZUG-04. Interne Nachrichten, Backoffice-Funktionen und KI-/Prozessabbruch sind auch über direkte Backend-Aufrufe unzugänglich. Ein abgeschlossener Fall bleibt bei gültiger Berechtigung lesbar; Schreibzugriffe folgen KOM-04.

### ZUG-AK-08

Tokenwerte fehlen in regulären Logs einschliesslich URL-Pfaden, Traces, Fehlerberichten und Drittanbieterrequests. Cache-/Referrer-Regeln gelten auch für Weiterleitungen und Fehlerantworten. Tokens und Sitzungsschlüssel fehlen in Camunda-Variablen, KI-Kontexten und Local Storage.

### ZUG-AK-09

Schreibzugriffe mit fehlendem oder falschem CSRF-Nachweis werden ohne fachlichen Seiteneffekt abgelehnt. Die festgelegten Missbrauchsgrenzen und neutralen Fehlerantworten werden geprüft; unbekannte und nicht mehr gültige Links offenbaren keine Fall- oder Kontaktzuordnung.

### ZUG-AK-10

Ein bewusster administrativer Linkersatz erfordert den vorgesehenen geprüften Ablauf. Unbestätigte Adressänderungen oder blosse Kenntnis einer Case-ID dürfen keinen Ersatzlink an einen anderen Empfänger auslösen. Ersatz verändert weder den fachlichen Fallstatus noch seine Aufbewahrung. Es gibt keinen Ersatz aufgrund eines zeitlichen Ablaufs und keine UI3-Aktion «Fall-Link ersetzen».

### ZUG-AK-11

Fallanlage erzeugt genau einen gültigen Mieterzugang ohne Ablaufdatum. Hash und entschlüsselte Versandkopie gehören zum selben Token und Fall. Mehrere Benachrichtigungen und Versandwiederholungen enthalten denselben gültigen Link, ohne zusätzliche Tokens zu erzeugen. Auch lange nach Ausstellung benötigt der unverändert gültige Link keine Verlängerung.

### ZUG-AK-12

Nur die berechtigte Versandfunktion kann die Versandkopie entschlüsseln. Der Schlüssel liegt weder in der Datenbank noch im Repository. Fallbearbeitung und KI erhalten keine Entschlüsselungsrechte; Tokenkopie und Schlüssel fehlen in Camunda-Variablen, LLM-Eingaben und regulären Protokollen. Fehlende Schlüssel oder Entschlüsselungsfehler verhindern den Linkversand, ohne gespeicherte Falldaten zu verlieren oder Tokens automatisch zu ersetzen.

### ZUG-AK-13

Ein weitergeleiteter gültiger Link vermittelt dieselben Mieterrechte, ohne die Identität des Empfängers nachzuweisen. Interne Inhalte bleiben verborgen. Der direkte Ersatz sperrt den alten Zugang für alle Besitzer. Bereits ausgegebene Inhalte können nicht zurückgeholt werden.

### ZUG-AK-14

UI2 und UI3 geben Fall- und Memodaten nur an angemeldete Konten mit aktiver Mitarbeiterrolle aus. Zwei entsprechend berechtigte Mitarbeitende erhalten bei gleicher Such- und Filterauswahl dieselben Fälle und Trefferzahlen. Fehlende oder abgelaufene Anmeldung sowie ein Mieterkonto oder eine entzogene Rolle verhindern auch bei direkten Lese- und Schreibaufrufen den Mitarbeiterzugriff. Eine erfolgreiche Änderung ist der persönlichen Mitarbeiter-ID zugeordnet. Fachliche Schreibsperren und der Vorrang manueller Dringlichkeit gelten auch für Mitarbeitende.

Ein Fall mit ausstehendem Prozessstart und ein bereits gestarteter Fall bei unerreichbarem Camunda bleiben für berechtigte Mitarbeitende lesbar. In beiden Situationen weist PropertyFlow auch direkte Schreibaufrufe für die Fallbearbeitung ab. Aufrufe mit frei gewähltem Zielstatus weist der Dienst unabhängig vom Prozessstand ab.

### ZUG-AK-15

Ein verifiziertes Mieterkonto erhält im Web und über einen entsprechend berechtigten MCP-Client die Fälle mit passender aktueller Kontaktadresse. Die Fallübersicht berücksichtigt frühere Einreichungen ohne Konto. Eine fremde Case-ID oder eine im Aufruf übermittelte andere Adresse gewährt keinen Zugriff. Nach einem geprüften Adresswechsel wirkt die neue Zuordnung bei der nächsten Anfrage.

Für einen bestehenden Testfall mit `mıeter@example.test` (punktloses `ı`) prüft ein Konto mit verifiziertem `mieter@example.test` die Fallliste, den Direktabruf und das Ergänzen. Die Liste enthält den Fall nicht. Der Direktabruf liefert keine Falldaten und der Schreibaufruf erzeugt keinen Verlaufseintrag.

### ZUG-AK-16

Die geschützte Kontoverwaltung erlaubt eine Kontoanlage nur mit dem zusätzlichen Kontoverwaltungsrecht. Eine Mitarbeiterrolle allein genügt dafür nicht. Die erfolgreich vergebene Rolle ist zusammen mit der handelnden Benutzer-ID protokolliert. Für das erste Verwaltungskonto gelten die Prüfungen zur Servereinrichtung nach UC-007.

### ZUG-AK-17

Ein MCP-Aufruf mit gültigem Zugriffstoken ist nur mit delegierter Aktion, aktueller Kontoberechtigung und zulässigem Fallzustand erfolgreich. Ein widerrufener Zugriff, ein fremder Fall oder eine fehlende Freigabe für eine kritische Aktion wird ohne fachliche Wirkung abgelehnt. Das Audit ordnet den Schreibversuch Benutzer und Client zu.
