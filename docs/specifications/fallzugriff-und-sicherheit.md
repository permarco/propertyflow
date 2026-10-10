# Fallzugriff und Sicherheit – persönlicher Mieterlink

**Status:** Zentrale Spezifikation des vereinbarten direkten Tokenzugriffs; technische Umsetzung und Sicherheitsparameter im Review.  
**Stand:** 10.10.2026

**Geltungsbereich:** Mieterzugriff, tokenbasierte Backend-Autorisierung, getrennte Mitarbeiter-Adminrechte, Speicherung von Zugriffsnachweisen und Versand persönlicher Falllinks.

## Zweck und Verbindlichkeit

Mieterinnen und Mieter öffnen ihren Fall über einen persönlichen Link ohne Benutzerkonto und ohne Anmeldeformular. **Der persönliche E-Mail-Link enthält als einzigen variablen Zugangswert einen geheimen Token.** PropertyFlow ermittelt den zugehörigen Fall serverseitig und rendert die Ansicht direkt unter `/mieter/fall/zugang/<geheimer-token>`. Der Benutzer bleibt auf dieser URL. Die lesbare Case-ID bleibt die unveränderliche Referenz des Falls und wird im Inhalt der Fallansicht angezeigt.

Die Architekturentscheidung mit Begründung, Alternativen und Konsequenzen ist in [ADR-004: Tokenbasierter Mieterzugriff](../architecture/adr/ADR-004-tokenbasierter-mieterzugriff.md) festgehalten. Dieses Dokument bleibt die gemeinsame Quelle für die detaillierten Zugriffsregeln und Prüfkriterien. [Screen 01](../frontend/ansicht-01-mieter-fallansicht.md) beschreibt deren Darstellung und Bedienung. [Fallverwaltung](fallverwaltung.md) definiert Fallidentität und Lebenszyklus; [Fallkommunikation](fallkommunikation.md) definiert sichtbare Inhalte, LLM-Datengrundlage und Nachrichtensperren. Die Architektur aus [ADR-001](../architecture/adr/ADR-001-grundarchitektur.md), [ADR-002](../architecture/adr/ADR-002-camunda8-workflow-orchestrierung.md) und [ADR-003](../architecture/adr/ADR-003-praesentationsschicht.md) bleibt bestehen: PropertyFlow autorisiert den Zugriff, Thymeleaf rendert die Ansicht und Camunda 8 orchestriert die Hintergrundverarbeitung.

Die fachliche Trennung von Case-ID und geheimem Zugang, die direkt angezeigte Token-URL sowie die Speicherung desselben Tokens als Hash und verschlüsselte Versandkopie sind vereinbart. **Bestätigt am 10.10.2026:** Der persönliche Fall-Link hat keine zeitliche Ablauffrist. Der frühere Laufzeitvorschlag entfällt. Weitere technische Schutzparameter bleiben ausdrücklich als Review-Vorschläge ausgewiesen. Die Spezifikation bestätigt keine implementierten Sicherheitsmechanismen oder ausgeführten Tests.

## ZUG-01: Case-ID und geheimer Token

| Begriff | Aufgabe | Umgang |
|---|---|---|
| Case-ID | Identifiziert dauerhaft einen Fall, beispielsweise `REQ-2026-001` | Wird in der Ansicht angezeigt und kann als Referenz gegenüber der Verwaltung verwendet werden. Gewährt allein keinen Zugriff. |
| Zugriffstoken | Berechtigt seinen Besitzer zu den zulässigen Mieteraktionen für genau einen Fall | Kryptografisch zufällig, geheim, nicht aus der Case-ID oder Kontaktangaben ableitbar. Ohne zeitliche Ablauffrist; bewusster Widerruf oder Ersatz bleiben getrennte Vorgänge. |
| Zugriffsdatensatz | Ordnet den Token-Prüfnachweis dem Fall und seinen erlaubten Aktionen zu | Wird bei jedem Zugriff auf Gültigkeit und Widerruf geprüft. Eine separate Sitzung ist für die Fallberechtigung nicht erforderlich. |

Die «geheime Zahl» im Link übernimmt damit die Rolle des Tokens. Sie muss ausreichend lang und zufällig sein; eine fortlaufende Nummer oder kurze PIN erfüllt diese Anforderung nicht. Ob die Darstellung Ziffern oder URL-taugliche Buchstaben und Ziffern verwendet, ist eine technische Formatentscheidung.

Pro Fall gibt es im vereinbarten Mieterzugang genau einen gültigen Zugriffstoken. Hash und verschlüsselte Versandkopie sind zwei Speicherformen desselben Tokens, keine zusätzlichen Zugangstokens. Ein separater Zugang pro Empfänger ist nicht vorgesehen.

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
4. Auch bei Neuladen, weiteren Datenabrufen und Schreibaktionen wird der mitgesendete Token erneut geprüft. Eine technische Sitzung oder ein Cookie allein gewährt keinen Fallzugriff. Schreiben erfordert zusätzlich einen aktiven Fall, die erlaubte Aktion und die geltenden Schreibschutzprüfungen.

Der persönliche E-Mail-Link ist zugleich die Browseradresse der Fallansicht und ohne zeitliche Ablauffrist **wiederverwendbar**, solange er nicht bewusst widerrufen oder ersetzt wird. Er kann erneut geöffnet oder als Lesezeichen gespeichert werden. Ein Linkaufruf verbraucht den Token nicht und verändert keine fachlichen Falldaten. Eine Verlängerung ist nicht erforderlich. E-Mail-Linkscanner dürfen dadurch weder Fälle noch Nachrichten, Freigaben oder Prozessaktionen auslösen.

Ein Token für Fall A berechtigt nicht zu Fall B. Jede Anfrage wird dem Fall des geprüften Tokens zugeordnet; Formularwerte dürfen diese Zuordnung nicht ersetzen. Bei mehreren geöffneten Tabs darf das Öffnen eines zweiten Links Nachrichten aus einem alten Tab niemals in einen anderen Fall umleiten. Das ergibt keine Fallübersicht oder ein Mieterportal.

Die sichtbare Adresse enthält den Zugangsschlüssel. Kopieren oder Weitergeben der vollständigen Adresse vermittelt dieselbe Berechtigung wie der E-Mail-Link. Token können dadurch auch in Browserhistorie und Mailarchiven verbleiben. Die technischen Schutzregeln aus ZUG-06 gelten deshalb für die gesamte Fallansicht und ihre Aufrufe.

## ZUG-04: Rechte und Vertrauensgrenzen

| Aufrufer | Zulässige Aktionen |
|---|---|
| Ohne Fallberechtigung | Neues Anliegen einreichen; keine bestehenden Fälle lesen oder verändern. |
| Mit gültiger Mieterberechtigung | Genau den zugeordneten Fall, seine externen veröffentlichten Nachrichten sowie aktuelle Dringlichkeit und freigegebene Stufenhistorie nach DRING-07 lesen; bei aktivem Fall weitere externe Nachrichten senden. |
| Mit gültiger Mieterberechtigung bei abgeschlossenem Fall | Den zugeordneten Fall weiterhin lesen; die Schreibsperre gemäss KOM-04 gilt. |
| Immobilienverwaltung | Alle Mitarbeitenden haben dieselben Adminrechte im PropertyFlow-Backoffice, einschliesslich manueller Anpassung des Bearbeitungsstatus in UI3 nach FALL-06, fachlichem Abschluss und bewusster Wiedereröffnung nach FALL-07. Ein Mieterlink vermittelt keine internen Rechte. |
| Systemkomponenten | Fallabschluss über autorisierte PropertyFlow-Anwendungsdienste im Camunda-gesteuerten Ablauf, wenn ein vereinbarter Systemabschlussgrund nach FALL-07 geprüft vorliegt; keine Berechtigung zur Wiedereröffnung. |

Die Mieterberechtigung erlaubt weder interne Memos noch deren Metadaten, keine Prioritätsänderung, Beauftragung, fachliche Freigabe, Schliessung oder Wiedereröffnung und keinen KI-/Prozessabbruch. Das gilt auch bei direkten Backend-Aufrufen. Die inhaltlichen Grenzen werden in [KOM-01](fallkommunikation.md#kom-01-interne-und-externe-nachrichten), [KOM-02](fallkommunikation.md#kom-02-llm-kommunikation-ohne-interne-memos), [KOM-03](fallkommunikation.md#kom-03-asynchrone-fallkommunikation) und [KOM-04](fallkommunikation.md#kom-04-nachrichtensperre-nach-fallabschluss) gepflegt.

**Vereinbart am 09.10.2026 – einheitliche Mitarbeiterrechte:** Alle Mitarbeitenden haben dieselbe Adminrolle und Zugriff auf alle Fälle sowie deren interne Memos und externe Kommunikation im PropertyFlow-Backoffice. Es gibt keine unterschiedlichen Mitarbeiterrollen oder fallbezogenen Lesebeschränkungen zwischen Mitarbeitenden. Die Volltextsuche in der Mitarbeiter-Fallübersicht umfasst damit auch sämtliche internen Memos.

**Korrigiert und bestätigt am 10.10.2026 – keine Mitarbeiteranmeldung:** Für Mitarbeitende gibt es in PropertyFlow keine Anmeldung, keine Mitarbeiterkonten und keine Abmeldefunktion. UI2 und UI3 setzen keinen Anmeldeschritt voraus. Die bisher in den Dokumenten geforderte separate Anmeldung entfällt. Es gibt entsprechend keinen UI-Ablauf für eine abgelaufene Mitarbeiteranmeldung oder die Wiederherstellung einer Eingabe nach erneuter Anmeldung.

Adminrechte beziehen sich auf die vorgesehenen PropertyFlow-Backoffice-Funktionen; sie setzen fachliche Regeln wie die Nachrichtensperre nach Abschluss, Veröffentlichungsprüfung und den Schutz manueller Dringlichkeitseinstufungen nicht ausser Kraft. Die Berechtigung zum Mitarbeiterabschluss und die geprüften Systemabschlussgründe sind in [FALL-07](fallverwaltung.md#fall-07-abschluss-und-zeit-danach) vereinbart. Weitere Funktionen werden durch die einheitliche Rolle nicht eingeführt. Die serverseitige Trennung von Mieter- und Mitarbeiterfunktionen bleibt erforderlich; ein Mieterlink vermittelt keine Mitarbeiterrechte. Eine Mieter-Nachricht kann einen Systemabschluss begründen, vermittelt aber keine direkte Berechtigung zum Setzen des Fallstatus.

Die konkrete technische Absicherung des Mitarbeiterbereichs ist noch nicht festgelegt. Auch die in den Fachregeln verlangte Zuordnung von Änderungen zu einer bestimmten Mitarbeiterperson erhält durch diese Festlegung keine technische Identitätsquelle; deren Ermittlung bleibt im Service-/Betriebskonzept zu klären. Die Absenderrolle «Mitarbeiter» beziehungsweise Herkunft «Mensch» allein weist keine individuelle Identität nach. Daraus darf weder eine bereits vorhandene Personenidentifikation noch eine zusätzliche Login-Funktion abgeleitet werden.

Kontaktadresse, Fallzuordnung, Absenderrolle, Status, Sichtbarkeit, Veröffentlichungskennzeichen und Zeitstempel werden serverseitig bestimmt beziehungsweise geprüft. Manipulierte Pfade, Formularfelder oder Nachrichten-IDs erweitern keine Rechte. Mietertexte und LLM-Ausgaben sind nicht vertrauenswürdig; enthaltene Anweisungen verändern keine Systemregeln oder Berechtigungen.

PropertyFlow verantwortet die Autorisierung. Der Browser spricht Camunda nicht direkt an. Zugriffstokens und Sitzungsschlüssel gehören weder in Camunda-Prozessvariablen noch in LLM-Prompts, RAG-Indizes oder Tool-Kontexte.

## ZUG-05: Dauerhafte Gültigkeit, Widerruf und Ersatz

- **Bestätigt am 10.10.2026:** Der persönliche Fall-Link läuft zeitlich nicht ab. Weder Inaktivität noch eine feste Dauer seit Ausstellung machen ihn ungültig. Auch der Fallabschluss setzt keine Ablauffrist. Eine Verlängerungs- oder Erneuerungsaktion aufgrund des Alters des Links ist nicht vorgesehen; die vorgeschlagene UI3-Aktion «Fall-Link ersetzen» wird nicht eingeführt.
- Ein bewusster Widerruf entzieht den Zugriff bei jeder weiteren Anfrage mit diesem Token, auch wenn die Seite bereits geöffnet ist. Browsercookies oder technische Sitzungen dürfen das nicht umgehen. Diese bestehende Regel ist von einem zeitlichen Ablauf unabhängig.
- Ein Ersatzlink verwendet einen neuen unabhängigen Token. Der ersetzte Token verliert seine Berechtigung für alle weiteren Lese- und Schreibaufrufe. Case-ID, Historie und Bearbeitungsstand bleiben erhalten.
- Ein erforderlicher bewusster Ersatz eines verlorenen oder kompromittierten Links erfolgt über die Verwaltung nach geeigneter Identitätsprüfung an die bestätigte Kontaktadresse. Der sichere administrative Ablauf bleibt zu konkretisieren; daraus entsteht keine zusätzliche UI3-Aktion. Ein Adresswechsel benötigt einen separaten geprüften Ablauf.
- Automatischer öffentlicher Linkersatz ist eine optionale Erweiterung. Er muss neutral antworten, darf weder Fallbestand noch Kontaktzuordnung offenlegen und benötigt Missbrauchsschutz.
- Normales Öffnen und normale Benachrichtigungen rotieren den Token nicht. Der Abschluss eines Falls ist ebenfalls kein Tokenwiderruf: Lesen bleibt bei gültigem Zugang möglich, Schreiben wird durch die fachliche Abschlusssperre verhindert.
- Linkgültigkeit und Aufbewahrung von Falldaten sind unterschiedliche Regeln. Die fehlende Ablauffrist legt keine Aufbewahrungs- oder Löschfrist für Falldaten fest. Widerruf und Ersatz schliessen oder löschen keinen Fall. Eine berechtigende Fallsitzung ist im direkten Tokenmodell nicht vorgesehen.

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
2. Ein Benachrichtigungsauftrag referenziert den Fall und den zugehörigen Zugriffsdatensatz. Er benötigt keinen dauerhaft gespeicherten Klartexttoken.
3. Vor dem Versand prüft die Versandfunktion Gültigkeit und Widerruf des Zugangs. Nur für einen gültigen Zugang entschlüsselt sie die Versandkopie und setzt den Link `/mieter/fall/zugang/<geheimer-token>` zusammen.
4. Der lesbare Token wird nur vorübergehend für die Linkbildung und Mailübergabe verwendet. Er darf nicht in reguläre Protokolle, Fehlerberichte oder ungeschützte Warteschlangen geschrieben werden. Erledigte Versandaufträge werden gemäss der noch festzulegenden Aufbewahrungsfrist bereinigt; die geschützte Tokenkopie bleibt für weitere Benachrichtigungen während der Zugangsgültigkeit verfügbar.
5. Jede normale Benachrichtigung und jede Wiederholung verwendet denselben gültigen Token ohne zeitliche Ablauffrist. Weder Versand noch Öffnen einer E-Mail erzeugt einen zusätzlichen Zugang; eine Verlängerung entfällt.

Nur die Versandfunktion erhält die Möglichkeit zur Entschlüsselung. Fallbearbeitung, Browser, Camunda und KI benötigen weder die Versandkopie noch den Schlüssel. Der Schlüssel wird über eine geschützte Betriebskonfiguration beziehungsweise Geheimnisverwaltung bereitgestellt; konkrete Verfahren und technische Berechtigungen werden bei der Umsetzung festgelegt. Eine Datenbankkopie allein soll nicht ausreichen, um gültige Falllinks zu rekonstruieren. Ein kompromittierter Anwendungsserver mit Zugriff auf Schlüssel und Versandkopien ist durch diese Trennung allein nicht abgesichert.

**Widerruf, Ersatz und Fehler:** Ein widerrufener oder ersetzter Token wird nicht mehr als gültiger Falllink versendet. Eine zeitliche Ablaufprüfung entfällt. Ein bewusster Ersatz erfolgt nach ZUG-05: neuer unabhängiger Token, neuer Hash und neue verschlüsselte Kopie; der alte Zugang wird gesperrt. Verzögerte Aufträge prüfen den aktuellen Zugang vor dem Versand und dürfen keinen alten Zugang reaktivieren. Bereits verschickte E-Mails lassen sich nicht zurückholen; ihr gesperrter Link gewährt jedoch keinen weiteren Zugriff.

Fehlen Schlüssel oder Versandkopie oder schlägt die Entschlüsselung fehl, wird kein gültiger Link erfunden und kein neuer Token automatisch erzeugt. Der Versandauftrag bleibt als fehlgeschlagen beziehungsweise erneut ausführbar erkennbar; die Fallannahme, gespeicherte Nachrichten und vorhandenen Zugriffsrechte bleiben bestehen. Fehler enthalten keinen Token oder Schlüssel. Wiederholungen und die sichtbare Klärung bei ungültigem Zugang folgen [BEN-04](benachrichtigungen-und-zustellung.md#ben-04-wiederholungen-und-zustellnachweis); der dort als offen gekennzeichnete Versand einer neutralen Mail ohne Link ist noch nicht beschlossen.

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
| Widerruf oder Ersatz bei geöffneter Seite | Weitere Lese- und Schreibaufrufe mit dem alten Token sperren. Ungesendeten Text nicht automatisch speichern; bereits ausgelieferte oder kopierte Inhalte lassen sich dadurch nicht zurückholen. |
| CSRF-Prüfung schlägt fehl | Schreibaktion ohne fachlichen Seiteneffekt ablehnen. |
| Fall wurde während der Eingabe geschlossen | Nachricht gemäss KOM-04 zurückweisen; berechtigtes Lesen bleibt möglich. |
| Rate Limit erreicht | Ablehnen und eine verständliche Warteinformation geben, gegebenenfalls mit `Retry-After`. |
| Technischer Fehler oder unklarer Absendeausgang | Keinen falschen Erfolg bestätigen; keine Berechtigung umgehen. Bereits angenommene Fälle bleiben gemäss FALL-03/FALL-09 bestehen. |

Die konkreten Mietertexte und das Verhalten noch sichtbarer Eingaben werden in [Screen 01, Abschnitt 6](../frontend/ansicht-01-mieter-fallansicht.md#6-persönlicher-fall-link-und-datenschutz) beschrieben. Eingabeerhalt darf keine fehlende Berechtigung umgehen und ist nach einem vollständigen Browser- oder Netzwerkausfall nicht garantiert.

## Review-Vorschläge und offene Entscheidungen

Die bisher in Screen 01 diskutierten Werte werden hier weitergeführt und sind **noch keine beschlossenen Sicherheitsparameter**:

| Punkt | Bisheriger Vorschlag beziehungsweise offene Entscheidung |
|---|---|
| Tokenstärke | Mindestens 256 Bit kryptografische Zufallsentropie; URL-taugliche Darstellung und konkrete Hash-Verifikation im Service-Design festlegen. |
| Routen | Direkte Fallansicht unter `/mieter/fall/zugang/{token}` gemäss ZUG-07; weitere Backend-Verträge werden bei der Umsetzung konkretisiert. |
| Kontaktbestätigung und Linkersatz | Konkrete Identitätsprüfung, Adresswechsel, sichere Durchführung durch die einheitliche Mitarbeiter-Adminrolle und Nachvollziehbarkeit festlegen. |
| Erstzugriff und Wiederholung | Sichere Bindung an den Einreichungskontext sowie Koordination von Annahme, Zugriffsdatensatz und Versandauftrag festlegen. |
| Umsetzung der Token-Aufbewahrung | Hash für die Prüfung und separat verschlüsselte Kopie desselben Tokens für den Versand sind vereinbart. Verschlüsselungsverfahren, Schlüsselbereitstellung und -rotation, technische Entschlüsselungsrechte, Sicherungen und Löschfristen konkretisieren. |
| Schutzparameter und Betrieb | Rate-Limit-Werte, Hash-Verfahren, Aufbewahrungsfristen, Formularschutz und technische Durchsetzung der Log-Redaktion konkretisieren. |

Die dauerhafte zeitliche Gültigkeit ist nach ZUG-05 entschieden und kein offener Review-Parameter. Der frühere Sitzungs-Vorschlag mit 30 Minuten Inaktivität und acht Stunden absoluter Laufzeit ist durch den direkten Tokenzugriff ersetzt und gilt nicht mehr als Zugangsregel. Tokenprüfung, Fallbindung, geltende Rechte und ein allfälliger bewusster Widerruf oder Ersatz bestimmen die Fallberechtigung bei jeder Anfrage.

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

Ein gültiger Link liefert die Fallansicht direkt unter derselben Token-URL aus, ohne Redirect und ohne erforderliche berechtigende Sitzung. Wiederholtes Öffnen, Neuladen und ein Aufruf ohne bestehende Cookies prüfen den Token erneut. Auch nach beliebigem zeitlichem Abstand oder längerer Inaktivität bleibt ein nicht widerrufener oder ersetzter Link gültig; eine zeitliche Ablauffrist wird nicht ausgewertet. Das Öffnen verbraucht den Token nicht und erzeugt keine fachlichen Änderungen, auch beim Abruf durch einen Linkscanner.

### ZUG-AK-05

Mit einer Berechtigung für Fall A können weder Fall B noch dessen Nachrichten gelesen oder beschrieben werden. Manipulierte Pfade und Formularwerte sowie mehrere offene Tabs werden geprüft; eine Nachricht darf niemals durch einen Kontextwechsel im falschen Fall landen.

### ZUG-AK-06

Bewusster Widerruf und Ersatz sperren alle weiteren Zugriffe mit dem alten Token, auch aus bereits geöffneten Tabs oder mit vorhandenen Cookies. Ein gültiger Ersatzlink öffnet denselben Fall. Verzögerte Versandaufträge aktivieren den alten Zugang nicht wieder. Zeitablauf allein sperrt keinen Link. Der Zugriff auf einen gültigen Tokenlink hängt nicht von einer Fallsitzung ab.

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

Ein weitergeleiteter gültiger Link vermittelt dieselben Mieterrechte, ohne die Identität des Empfängers nachzuweisen. Interne Inhalte bleiben verborgen. Ein Widerruf beziehungsweise Ersatz sperrt den alten Zugang für alle Besitzer und für nachfolgende Versandversuche. Bereits ausgegebene Inhalte können nicht zurückgeholt werden.

### ZUG-AK-14

Alle Mitarbeitenden haben dieselben Fall- und Memorechte. Zwei Mitarbeitende erhalten bei gleicher Such-/Filterauswahl dieselben Fälle und Trefferzahlen. UI2 und UI3 verlangen keine Anmeldung und enthalten keine Anmelde-, Abmelde- oder Sitzungserneuerungsaktion. Mieter und Aufrufer ohne Mitarbeiterberechtigung können die Mitarbeiterliste, ihre Suche und ergänzende Datenabrufe nicht nutzen; der technische Nachweis dieser Abgrenzung folgt dem noch auszuarbeitenden Schutzkonzept aus ZUG-04. Eine individuelle Mitarbeiteridentität darf nicht aus einer nicht vorhandenen Anmeldung behauptet werden. Fachliche Schreibsperren und der Vorrang manueller Dringlichkeit gelten auch für die Adminrolle.
