# PropertyFlow - MCP-Werkzeuge

**Status:** Die Werkzeuge sind im Backend implementiert. Der Nachweis mit
einem echten MCP-Client ist am 25.11.2026 mit Claude Code erbracht; ChatGPT
bleibt möglich, braucht aber eine öffentliche HTTPS-Adresse.

Diese Spezifikation definiert die MCP-Werkzeuge für Mieter und Bewirtschaftung
im regulären PropertyFlow-Backend mit PostgreSQL. Web und MCP verwenden dieselben
fachlichen Dienste. Das Zugriffsmodell und die Begründung für die Bestätigung
wichtiger Schreibaktionen stehen in [ADR-005](../architecture/adr/ADR-005-zugriffsmodell.md)
und [ADR-006](../architecture/adr/ADR-006-mcp-mieterwerkzeuge-und-nachrichtenfreigabe.md).

## Sicherheitskonzept für Agent und Werkzeug

Der Agent handelt mit den delegierten Rechten des angemeldeten Benutzers.
Der MCP-Adapter übergibt die verifizierte Identität und die angeforderte Aktion
an den fachlichen Anwendungsdienst. **Der Dienst erzwingt die Berechtigungen
und Freigaben bei jedem Aufruf.** Toolbeschreibung, Eingabeschema und
Bestätigungsdialog im Chat unterstützen den Ablauf.

| Regel | Durchsetzung in PropertyFlow | Nachweis |
| --- | --- | --- |
| Autorisierung | Der Dienst prüft die aktuelle Rolle, die Delegation für die konkrete Aktion und die Berechtigung am Fall. Die Fallzuordnung für Mieter stammt aus den aktuellen gespeicherten Daten. | Ohne Token, mit fremder Case-ID, nach Rollenentzug sowie nach Widerruf und Adresswechsel direkt aufrufen. |
| Minimale Rechte | Lesen und Schreiben sind getrennte Rechte. Eine Mitarbeiteraktion erfordert die Mitarbeiterrolle und die zugehörige Delegation. Der Dienst liefert nur die vereinbarten Datenfelder. Das Schreibrecht erlaubt das Vorbereiten eines Vorgangs. | Ein reines Lesetoken gegen ein Schreibwerkzeug verwenden. Mitarbeiterwerkzeuge mit einem Mietertoken aufrufen und die Rückgabefelder prüfen. |
| Eingabeprüfung | Der Adapter prüft das Eingabeschema. Der Dienst prüft die fachlichen Daten wie Case-ID, Nachrichtentext, Länge, Aktion und aktuellen Fallzustand. Identität, Rolle und Autor kommen aus dem Dienst. Nachrichten und Modelltexte werden als Daten behandelt. | Ungültige Eingaben, zusätzliche Rollenfelder und Nachrichten mit eingebetteten Anweisungen testen. Keine dieser Angaben darf Rechte erweitern oder eine Freigabe erzeugen. |
| Menschliche Freigabe | Für wichtige Schreibaktionen öffnet MCP Elicitation die PropertyFlow-Seite. Der Dienst prüft beim Bestätigen das angemeldete Konto und die an Aktion, Ziel, Inhalt und gegebenenfalls Fallstand gebundene Freigabe. Für fachlich kritische Aktionen ist eine berechtigte Mitarbeiterentscheidung gemäss [FALL-10](fallverwaltung.md#fall-10-automatisierung-und-menschliche-freigabe) erforderlich. | Direktaufruf ohne Bestätigung, falsches Konto, geänderten Text, Ablauf und Wiederholung prüfen. Die falsche Freigabe darf keine Änderung auslösen. |
| Audit | PropertyFlow protokolliert versuchte Schreibaktionen, einschliesslich Ablehnungen, und das Ergebnis einer Bestätigung. Der Eintrag enthält Zeitpunkt, Kanal `mcp`, Aktion, Konto und Client, soweit verifiziert, Zielreferenz, Vorgangsreferenz soweit vorhanden sowie Ergebnis oder Ablehnungsgrund. Nachrichtentexte, Tokens und Einmalcodes bleiben aus dem Protokoll. | Erfolgreichen Versand und Ablehnung anhand der Vorgangsreferenz nachvollziehen. Das Audit auf vertrauliche Daten prüfen. |

### Speicherung, Zugriff und Aufbewahrung des MCP-Audits

PropertyFlow speichert das MCP-Audit dauerhaft in PostgreSQL. Eine eigene
Tabelle `pf_mcp_audit` wird per additiver Flyway-Migration angelegt. Die
vorhandenen Konto- und Fallzugangsprotokolle behalten ihre eigenen Regeln.

- Einträge werden angehängt. Ereignisse für Vorbereitung, Bestätigung,
  Ablehnung und fachliches Ergebnis lassen sich über dieselbe Vorgangsreferenz
  verbinden. Bei fehlendem oder ungültigem Token bleiben nicht verifizierte
  Konto-, Client- und Fallangaben aus dem Eintrag. Wiederholte HTTP-Aufrufe
  können eigene Audit-Einträge erzeugen. Die fachliche Änderung bleibt einmalig.
- Eine erfolgreiche Speicherung oder Veröffentlichung erhält einen dauerhaften
  Audit-Nachweis. Wo der Fachdienst die Änderung lokal in PostgreSQL speichert,
  wird der zugehörige Nachweis in derselben Transaktion festgehalten. Ausstehende
  oder unklare Prozessbestätigungen werden als solche erfasst. Bei einem
  fehlgeschlagenen Schreibversuch wird die Ablehnung unabhängig von einem
  Rollback der Fachänderung protokolliert.
- Nur ausdrücklich zur Audit-Einsicht berechtigte Betriebs- oder
  Sicherheitsverantwortliche erhalten Lesezugriff. Die Mitarbeiterrolle allein
  gewährt ihn nicht. Das Protokoll ist über die sechs MCP-Werkzeuge nicht abrufbar.
- Der Startwert für die Aufbewahrung beträgt **90 Tage** ab Ereigniszeitpunkt.
  Der Betrieb kann die Frist konfigurieren. Ein täglicher Bereinigungslauf
  löscht abgelaufene MCP-Audit-Einträge. Die Bereinigung wirkt ausschliesslich
  auf diese Tabelle. Die Frist ist ein Projektstartwert und wird vor Einsatz
  mit realen Mieterdaten durch die verantwortliche Stelle überprüft.

## Anmeldung und Fallrechte

Mieter und Mitarbeitende melden sich mit ihrem persönlichen PropertyFlow-Konto
an und erteilen dem MCP-Client die gewünschten Rechte. Der Client erhält ein
eigenes OAuth-Zugriffstoken. PropertyFlow prüft bei jedem Aufruf das Token, die
aktuelle Kontorolle und die delegierte Aktion. Ohne gültige Delegation sind
auch die Werkzeugliste und die Lesefunktionen gesperrt. Ein persönlicher
Fall-Link gilt ausschliesslich für den Browserzugriff auf seinen Fall.

| Konto | Fallzugriff über MCP |
| --- | --- |
| Mieter | Nur Fälle, deren aktuelle Kontaktadresse mit der verifizierten E-Mail-Adresse des Mieterkontos übereinstimmt. Das gilt beim Auflisten, beim Abruf per Case-ID und beim Senden einer Ergänzung. |
| Aktive Mitarbeiterrolle | Leserechte für alle Fälle, einschliesslich abgeschlossener Fälle. Die Übersicht startet mit aktiven Fällen. Die delegierte Aktion und die je Werkzeug freigegebenen Datenfelder bestimmen, was der Client abrufen kann. Schreibaktionen folgen den jeweiligen Fach- und Freigaberegeln. |

Eine geänderte Fall-Kontaktadresse, ein Rechteentzug oder ein Widerruf der
Delegation wirkt beim nächsten Aufruf. Massgeblich ist
[ZUG-04](fallzugriff-und-sicherheit.md#zug-04-rechte-und-vertrauensgrenzen).

### Anmeldung und Delegation im Browser

1. Die Person wählt in ChatGPT das PropertyFlow-Plugin und startet die Verbindung.
2. ChatGPT leitet zur PropertyFlow-Anmeldung weiter. Eine gültige PropertyFlow-Sitzung
   im selben Browserprofil kann wiederverwendet werden. Sonst meldet sich die Person
   wie in der Web-App per E-Mail-Einmalcode an.
3. PropertyFlow zeigt den Client und die angefragten Rechte an. Die Person kann
   Leserechte erteilen, Schreibrechte separat freigeben oder den Vorgang abbrechen.
4. Nach der Freigabe erhält der registrierte Client ein OAuth-Token für die
   PropertyFlow-MCP-Ressource. OAuth Authorization Code mit PKCE und die
   Client-Registrierung richten sich nach der für den Zielclient verwendeten
   [MCP-Autorisierungsspezifikation](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization). PropertyFlow prüft Zielressource,
   Gültigkeit und aktuelle Delegation.
5. Die Person kann die Delegation in PropertyFlow einsehen und widerrufen.
   Die lokale Web-Abmeldung beendet die Browsersitzung. Die MCP-Delegation
   hat einen eigenen Widerruf. Für die Bestätigung einer Schreibaktion im
   Browser ist weiterhin eine Sitzung desselben berechtigten Kontos erforderlich.

### Verbundene Apps

Im PropertyFlow-Konto steht Mietern und Mitarbeitenden die Seite «Verbundene
Apps» zur Verfügung. Sie zeigt die eigenen aktiven Verbindungen mit Clientname
und erteilten Rechten in verständlicher Sprache, etwa «Fälle lesen» und
«Nachrichten vorbereiten». «Zugriff widerrufen» beendet die gewählte
Verbindung. PropertyFlow weist den nächsten Werkzeugaufruf und jede weitere
Token-Erneuerung für diese Delegation ab. Offene Bestätigungsvorgänge dieser
Verbindung können anschliessend keine Nachricht mehr auslösen.

| OAuth-Berechtigung | Vergabe und Wirkung |
| --- | --- |
| `tenant:cases:read` | Mieterkonto. Eigene Fälle auflisten und Fallstand abrufen. |
| `tenant:messages:write` | Mieterkonto. Eine Ergänzung im eigenen Fall zur Bestätigung vorbereiten. |
| `staff:cases:read` | Aktive Mitarbeiterrolle. Mitarbeiterübersicht und Fallverlauf abrufen. |
| `staff:messages:write` | Aktive Mitarbeiterrolle. Externe Information oder Rückfrage zur Bestätigung vorbereiten. |

Vorgeschlagene Startwerte für den ersten Betrieb sind 15 Minuten für ein
Zugriffstoken, höchstens 8 Stunden für die Delegation mit Token-Erneuerung
und 10 Minuten für einen einzelnen Bestätigungsvorgang. Die Werte werden
im Integrationstest mit dem Zielclient geprüft. Ein Widerruf sperrt weitere
Aufrufe und Token-Erneuerungen.

## Gemeinsame Bestätigung für wichtige MCP-Schreibaktionen

Für wichtige Schreibaktionen, die ein MCP-Client im Auftrag eines Benutzers
an PropertyFlow übergibt, gilt derselbe Bestätigungsablauf für Mieter und
Mitarbeitende. Dazu zählen das Senden oder Veröffentlichen einer Nachricht,
fachlich wirksame Änderungen am Fall wie Abschluss oder Wiedereröffnung
sowie Änderungen an Fallzugängen oder Kontorechten, sofern die jeweilige
Aktion künftig über MCP angeboten wird. Bei jedem Schreibwerkzeug legt der
fachliche Dienst verbindlich fest, ob die Aktion eine Bestätigung braucht.
Reine Leseaufrufe benötigen keine Bestätigung pro Aufruf.

1. Der Dienst prüft Rolle, delegierte Aktion, Berechtigung am Ziel und Eingaben.
   Er speichert einen befristeten Bestätigungsvorgang für das Konto, den Client
   und seine Delegation, die konkrete Aktion, das Zielobjekt und die vorgelegten
   Daten. Falls die Aktion an einen bestimmten Fallstand oder eine offene
   Prozessaufgabe gebunden ist, gehören auch diese Angaben zum Vorgang.
2. MCP Elicitation im URL-Modus führt zur Bestätigungsseite von PropertyFlow.
   Sie zeigt der angemeldeten Person die Aktion, das Ziel und die wesentlichen
   Folgen. Das Öffnen der Seite führt die Aktion nicht aus.
3. Beim ausdrücklichen Bestätigen prüft der Dienst den CSRF-Nachweis und erneut
   Identität, Rechte, Delegation und fachliche Voraussetzungen. Die Bestätigung
   gilt einmalig für die hinterlegten Daten. Bei Widerruf, Ablauf, geändertem
   relevantem Zustand oder fehlender Bestätigung bleibt die Aktion ohne Wirkung.
   Wiederholungen dürfen keine zusätzliche fachliche Wirkung auslösen.
4. Das Audit erfasst Konto, Client, Kanal, Aktion, Zielreferenz, Zeitpunkt und
   Ergebnis. Zugangsdaten und vertrauliche Inhalte bleiben aus dem Audit.

Das delegierte Schreibrecht erlaubt dem Client, die Aktion vorzubereiten.
Die Bestätigung gilt für genau einen Vorgang. Für fachlich kritische Aktionen
bleiben die Anforderungen an eine berechtigte Mitarbeiterentscheidung nach
[FALL-10](./fallverwaltung.md#fall-10-automatisierung-und-menschliche-freigabe)
und [UC-027](../use_cases/UC-027-kritische-fallentscheidung-treffen.md)
massgeblich. Die Bestätigung eines Mieters erfüllt diese Anforderungen nicht.

## Erste Werkzeuge für Mieter

| Werkzeug | Delegiertes Recht | Fachlicher Vertrag |
| --- | --- | --- |
| `eigene_faelle_auflisten` | Lesen | Listet die eigenen Fälle mit Case-ID, Titel und Status. Der Dienst ermittelt die Fallmenge anhand der aktuell verifizierten Konto-Adresse und der aktuellen Kontaktadresse der Fälle. |
| `fallstand_abrufen` | Lesen | Nimmt eine Case-ID entgegen. Liefert für einen eigenen Fall Case-ID, Bearbeitungsstatus und wirksame Dringlichkeit. Fremde und unbekannte Fälle ergeben dieselbe neutrale Rückmeldung. |
| `fall_kommentar_senden` | Schreiben | Nimmt Case-ID und Nachrichtentext entgegen und leitet die Bestätigung ein. Nach der Bestätigung speichert der Dienst die Ergänzung als reguläre Mieternachricht gemäss [UC-009](../use_cases/UC-009-mieteranliegen-ergaenzen.md). |

## Ablauf für eine Mieternachricht

Jede Mieternachricht über MCP verwendet den [gemeinsamen Bestätigungsablauf](#gemeinsame-bestätigung-für-wichtige-mcp-schreibaktionen).

1. Der Agent findet den Fall und übergibt Case-ID und Nachrichtentext an
   `fall_kommentar_senden`.
2. PropertyFlow prüft das Konto, die Client-Delegation, das Schreibrecht,
   die Fallberechtigung und die Eingabe. Es erstellt einen befristeten Vorgang,
   der an Konto, Delegation, Fall und den zur Freigabe vorgelegten Text gebunden
   ist. Das Werkzeug fordert per MCP Elicitation im URL-Modus zur Bestätigung
   auf einer PropertyFlow-Seite auf.
3. Der Mieter öffnet die Seite in seiner angemeldeten Browsersitzung. Dort sieht
   er den Fall und den vollständigen Text. Das Öffnen der Seite hat keine
   fachliche Wirkung. Der Mieter bestätigt den Versand ausdrücklich mit
   **«Nachricht senden»**.
4. Beim Absenden prüft PropertyFlow den CSRF-Nachweis und erneut das aktuelle
   Konto, die gebundene Delegation, das Schreibrecht, die Fallzuordnung und den
   Fallstatus. Nur eine gültige Bestätigung für den hinterlegten Text löst den
   bestehenden Nachrichtendienst aus. Pro Vorgang wirkt eine stabile Absende-ID
   als Schutz vor doppelter Speicherung.
5. Abbruch, Ablauf oder eine verweigerte Prüfung lassen den Fall unverändert.
   Das Audit hält bei Schreibversuchen Konto, Client, Kanal, Aktion,
   Fallreferenz, Zeitpunkt und Ergebnis fest. Nachrichtentexte, Zugangstokens
   und Einmalcodes bleiben aus dem Audit.

## Erste Werkzeuge für die Bewirtschaftung

PropertyFlow prüft bei jedem Aufruf die aktuelle Mitarbeiterrolle und die für
das Werkzeug delegierte Berechtigung. Die Werkzeuge nutzen die vorhandenen
fachlichen Dienste der Mitarbeiteroberfläche. Für die erste Ausbaustufe wird
der Detailabruf auf den mit dem Mieter geteilten Nachrichtenverlauf begrenzt.

| Werkzeug | Delegiertes Recht | Fachlicher Vertrag |
| --- | --- | --- |
| `mitarbeiter_faelle_auflisten` | Mitarbeiterfälle lesen | Listet standardmässig aktive Fälle und unterstützt Filter nach Status und Dringlichkeit. Die Ausgabe enthält Case-ID, Titel, Status, Dringlichkeit und Zeitpunkt des letzten Mieterkontakts. Weitere Treffer sind seitenweise abrufbar. Grundlage ist [UC-002](../use_cases/UC-002-mieteranliegen-anzeigen.md). |
| `mitarbeiter_fall_abrufen` | Mitarbeiterfälle lesen | Liest Fallstand und veröffentlichten Nachrichtenverlauf für eine Case-ID. Der Agent kann daraus eine Zusammenfassung für die Bewirtschaftung erstellen. Grundlage ist [UC-003](../use_cases/UC-003-mieteranliegen-details-anzeigen.md). |
| `mitarbeiter_fallnachricht_veroeffentlichen` | Externe Fallnachrichten veröffentlichen | Nimmt Case-ID, Nachrichtentext und die Auswahl «Information» oder «Rückfrage mit Antwortbedarf» entgegen. Das Werkzeug bereitet die Bestätigung vor. Die Veröffentlichung verwendet den bestehenden Ablauf gemäss [UC-010](../use_cases/UC-010-externe-fallnachricht-veroeffentlichen.md). |

Für die Veröffentlichung zeigt die Bestätigungsseite Fall, vollständigen
Wortlaut und Antwortbedarf an. Beim Klick auf **«Nachricht senden»** prüft
PropertyFlow zusätzlich, ob der aktuelle Camunda-Fallprozess die Aktion erlaubt.
Der Ablauf meldet die Veröffentlichung erst, wenn die Nachricht dauerhaft
gespeichert und für den Mieter abrufbar ist. Die E-Mail folgt nachgelagert.

## Weitere MCP-Werkzeuge

| Werkzeug | Berechtigung | Eingabe und Wirkung |
| --- | --- | --- |
| `falldetails_abrufen` | `tenant:cases:read` oder `staff:cases:read` | `caseId`. Liefert Titel, ursprüngliche Problembeschreibung, Status und Dringlichkeit. |
| `fallhistorie_abrufen` | `tenant:cases:read` oder `staff:cases:read` | `caseId`, optional `messageCursor`. Liefert veröffentlichte Kommunikation seitenweise. |
| `meldung_erstellen` | `tenant:cases:create` | `title`, `unit`, `description`. Legt nach Bestätigung eine dem Mieterkonto zugeordnete Meldung an. Die Quittung enthält die Case-ID. |
| `kommentar_hinzufuegen` | `tenant:messages:write` oder `staff:messages:write` | `caseId` und `text`. Sendet nach Bestätigung eine Mieternachricht bzw. veröffentlicht eine Information der Bewirtschaftung. |
| `fallstatus_aendern` | `staff:cases:transition` | `caseId` und `action` (`fortsetzen`, `abschliessen` oder `wiedereroeffnen`). Verwendet nach Bestätigung den jeweiligen Camunda-Ablauf. `submitted` bezeichnet einen ausstehenden Statuswechsel, `applied` dessen bestätigte Ausführung. |

Bei Detail und Verlauf gelten die aktuellen Fallrechte. Vertrauliche
Zugangslinks und E-Mail-Adressen werden in Freitexten ausgeblendet.
Die Fallanlage nutzt den regulären Intake und bleibt bei einem
vorübergehenden Camunda-Ausfall möglich. Bei Statusänderungen prüft der
Anwendungsdienst die bestätigte Fallversion und, wo erforderlich, die
konkrete Camunda-Aufgabe. Eine noch ausstehende Bearbeitung verändert den
angezeigten Fallstatus nicht.

## Technischer Werkzeugvertrag

`tools/list` zeigt nur Werkzeuge, die zur aktuellen Rolle und Delegation
passen. PropertyFlow prüft dieselben Rechte bei `tools/call` erneut. Ein
Leseaufruf liefert strukturierte Daten. Die folgende Tabelle benennt die
Felder für das JSON-Schema. Mit `?` markierte Eingaben sind optional.

| Werkzeug | Eingabe | Strukturierte Ausgabe bei Erfolg |
| --- | --- | --- |
| `eigene_faelle_auflisten` | `state?`, `cursor?` | `items` mit `caseId`, `title`, `status` sowie optional `nextCursor` |
| `fallstand_abrufen` | `caseId` | `caseId`, `status`, `urgency` |
| `fall_kommentar_senden` | `caseId`, `text` | Bestätigungsanforderung. Nach Bestätigung eine Quittung mit `caseId` und `outcome` (`sent` oder `submitted`) sowie `messageId`, sobald die Nachricht gespeichert ist. |
| `mitarbeiter_faelle_auflisten` | `state?`, `status?`, `urgency?`, `cursor?` | `items` mit `caseId`, `title`, `status`, `urgency`, `lastTenantContactAt` sowie optional `nextCursor` |
| `mitarbeiter_fall_abrufen` | `caseId`, `messageCursor?` | `caseId`, `title`, `status`, `urgency` und `messages` mit `sentAt`, `authorRole`, `text`, `answerRequired` sowie optional `nextMessageCursor` |
| `mitarbeiter_fallnachricht_veroeffentlichen` | `caseId`, `text`, `kind` | Bestätigungsanforderung. Nach Bestätigung eine Quittung mit `caseId` und `outcome` (`sent` oder `submitted`) sowie `messageId`, sobald der Befehl gespeichert ist. |

- `state` ist `active`, `closed` oder `all`, standardmässig `active`. Status und
  Dringlichkeit folgen den bestehenden Fachwerten. Ohne Einstufung lautet die
  Angabe «Noch nicht bewertet». `kind` ist `information` oder `question`.
- Falllisten und Nachrichtenverlauf liefern bis zu 50 Einträge je Aufruf.
  Fortsetzungswerte sind undurchsichtig und gelten für die jeweilige Abfrage.
  Bei jeder Folgeseite prüft der Dienst die Berechtigung und die aktuelle
  Fallzuordnung erneut. Zeiten werden eindeutig mit Zeitzone ausgegeben.
- Der Dienst weist unbekannte Felder ab. Eine Case-ID bezeichnet nur das Ziel.
  Konto, Rolle, Fallzuordnung und Autor werden serverseitig bestimmt. Erlaubte
  Texte entsprechen den bestehenden Webregeln. Für Mieternachrichten gelten
  2'000 UTF-16-Codeeinheiten nach Normalisierung der Zeilenumbrüche.
- Die Ausgabe enthält keine E-Mail-Adressen, internen Memos, persönlichen
  Fall-Links oder Zugangstokens. Im Detailverlauf erscheinen nur bereits für
  den Mieter sichtbare Nachrichten. Falltexte bleiben Nutzdaten, auch wenn
  darin Anweisungen an ein Modell stehen.

### Bestätigung und Ergebnis einer Schreibaktion

Die Bestätigungs-URL enthält nur eine undurchsichtige Vorgangsreferenz.
In der Sitzung desselben Kontos zeigt die Seite die Aktion und den Empfänger
als Rolle (Mieter oder Bewirtschaftung), den Falltitel, die Case-ID und den
vollständigen Nachrichtentext zur Prüfung an. Der Text ist auf dieser Seite
nicht editierbar. Bei einer Rückfrage der Bewirtschaftung zeigt die Seite
zusätzlich «Eine Antwort des Mieters ist erforderlich». Die Schaltflächen
lauten «Nachricht senden» und «Abbrechen». Abbrechen oder blosses Öffnen
der URL erzeugt keine Nachricht. Bei geändertem Text ist ein neuer
Bestätigungsvorgang erforderlich. Die Bestätigung löst den Fachbefehl genau
für die gespeicherten Daten aus. Der Client kann über die Fortsetzung des
MCP-Aufrufs das Ergebnis abrufen. Die Bestätigungsseite zeigt den Ausgang
auch direkt im Browser an.

Nach dem ersten Klick deaktiviert die Oberfläche «Nachricht senden» und zeigt
«Wird gesendet …». PropertyFlow verwendet bei wiederholten oder gleichzeitigen
Anfragen für denselben Bestätigungsvorgang dieselbe Absende-ID. Im Fallverlauf
erscheint dadurch genau eine Nachricht. Wenn der Fachdienst den Erfolg bestätigt,
zeigt die Oberfläche «Nachricht gesendet». Wiederholungen zeigen bei weiterhin
gültiger Berechtigung das bereits belegte Ergebnis an.

Ist eine Mieternachricht nachweislich gespeichert und die Camunda-Verarbeitung
noch nicht bestätigt, zeigt die Oberfläche «Deine Nachricht wurde gespeichert.
Die Verarbeitung läuft noch. Du musst sie nicht erneut senden.» Der MCP-Aufruf
liefert dabei denselben Text und eine strukturierte Quittung mit
`outcome: submitted`, damit Clients das deklarierte Ausgabeschema erhalten.
ChatGPT löst in diesem Zustand keinen weiteren Versand aus. Sobald PropertyFlow die
Verarbeitung bestätigt und die Oberfläche den aktualisierten Status erhält,
wechselt die Anzeige zu «Nachricht gesendet».

| Situation | Erwartetes Verhalten |
| --- | --- |
| Token fehlt, ist ungültig oder widerrufen | Der MCP-Endpunkt verweigert den Zugriff mit HTTP `401`. |
| Gültiges Token ohne erforderliche Rolle oder Delegation | Das Werkzeug liefert eine Ablehnung ohne Falldaten. |
| Fremder oder unbekannter Fall aus Sicht eines Mieters | Identische neutrale Meldung. Sie verrät nicht, ob der Fall existiert. |
| Fall geschlossen, Zustand veraltet, Bestätigung abgelaufen oder widerrufen | Der Dienst verweigert eine neue Änderung. |
| Camunda vor Ausführung nicht verfügbar | Der Dienst nimmt keine neue Schreibaktion an. Fallabrufe bleiben bei verfügbarer Datenbank möglich. |
| Mieternachricht nachweislich gespeichert, Camunda-Bestätigung offen | Die Oberfläche zeigt die Zwischenmeldung. Eine allfällige technische Wiederholung verwendet denselben Vorgang und dieselbe Absende-ID gemäss UC-009. |
| Bestätigte Aktion mit derselben ID erneut übermittelt | Kein zweiter Eintrag. Einen bereits belegten Erfolg kann der Dienst bei weiterhin gültigem Zugriff erneut anzeigen. |

## Unterstützung im MCP-Client

### Bestätigung direkt im Chat als bevorzugte Bedienung

Für ChatGPT soll zuerst eine interaktive Karte im Chat erprobt werden. Sie
zeigt den Fall und den vollständigen Wortlaut sowie die Schaltflächen
«Nachricht senden» und «Abbrechen». Der Benutzer kann alternativ im Chat
eine Korrektur formulieren. Daraus entsteht ein neuer Entwurf, der erneut
zur Bestätigung angezeigt wird. Alternativ kann der Client ein Textfeld für
die Korrektur im Bestätigungsdialog anbieten. Freitext ändert oder ergänzt
den Entwurf. Der Versand folgt erst der ausdrücklichen Bestätigung des
angezeigten Wortlauts.

Der Chat-Dialog ist ein Prüfauftrag. PropertyFlow muss den Klick der
aktuellen Person und dem konkreten Vorgang zuordnen können und einen
direkten Modellaufruf ohne Klick abweisen. Eine reine Tool-Sichtbarkeit im
Client oder ein Parameter wie `confirmed: true` genügt hierfür nicht.
Bis dieser Nachweis erbracht ist, gilt die Bestätigung auf der
PropertyFlow-Seite gemäss ADR-006.
Siehe [OpenAI-Dokumentation zu interaktiven Plugin-Oberflächen](https://developers.openai.com/plugins/reference).

Der gesamte Ablauf für Mieter und Mitarbeitende ist im Browser nutzbar.
Anmeldung, Erteilen und Widerrufen der Delegation sowie die Bestätigung
einer Schreibaktion erfolgen über Weboberflächen. Die Bestätigung der
konkreten Aktion findet auf der PropertyFlow-Seite statt. Der Nutzer kann
den Ablauf ohne lokal installierten MCP-Client und ohne manuelles Kopieren
von Zugriffstokens abschliessen.

**Entschieden am 25.11.2026:** Der Nachweis mit einem echten MCP-Client
erfolgt mit Claude Code. Es ruft den MCP-Server vom eigenen Rechner aus auf und
braucht deshalb weder Tunnel noch öffentliche Adresse. ChatGPT ruft Werkzeuge
dagegen über die Server von OpenAI auf, auch aus der Desktop-App. Einrichtung
und Sicherheitsabwägung zur Callback-Adresse siehe
[Dev-Anleitung](../../dev/README.md#claude-code-als-lokaler-mcp-client).

Ein Client, der alle angebotenen Scopes gleichzeitig anfordert, erhält über
`propertyflow.mcp.oauth.role` (`tenant` oder `staff`) nur die Scopes einer
Rolle angeboten. Ohne Angabe bleiben alle Scopes angeboten. Ergebnis des Laufs
siehe [Abgabe Block 3, Abschnitt 3](../abgabe/block-3-services.md#3-mcp-werkzeug).

**Ursprünglich geplanter Zielclient war ChatGPT im Browser.** PropertyFlow wird als eigenes
MCP-Plugin angebunden. ChatGPT erreicht den MCP-Endpunkt des regulären Backends
über HTTPS oder einen unterstützten sicheren Tunnel. Der erste Nachweis nutzt
synthetische Fälle und getrennte Testkonten für Mieter und Bewirtschaftung.
Für den Schreibnachweis braucht es einen ChatGPT-Workspace mit freigeschalteten
MCP-Schreibrechten. OpenAI dokumentiert diese Unterstützung für Business,
Enterprise und Edu sowie Lesezugriff für Pro.
Siehe [OpenAI-Dokumentation zur MCP-Anbindung](https://help.openai.com/en/articles/12584461-connecting-custom-mcp-servers-in-chatgpt).

### Erster Nachweis für Block 3

Zuerst wird `fallstand_abrufen` mit einem verifizierten Mieterkonto, OAuth
und synthetischen Fällen aus PostgreSQL erprobt. Das reguläre Backend enthält
den Fallabruf und einen integrierten Spring Authorization Server. Der Zugriff
ist standardmässig deaktiviert. Die erste Variante kennt einen vorregistrierten
öffentlichen Client und verwendet Authorization Code mit PKCE sowie ein
15 Minuten gültiges und widerrufbares Zugriffstoken. Die Browseranmeldung
verwendet die bestehenden Konten. Die Einwilligung lässt sich unter
«Verbundene Apps» löschen. Nach Ablauf des Zugriffstokens ist eine neue
Autorisierung nötig. Einrichtung siehe [Dev-Anleitung](../../dev/README.md#mcp-fallstand-mit-oauth).

Der PropertyFlow-Endpunkt
wird in ChatGPT als benutzerdefinierter MCP-Server registriert. ChatGPT liest
die Werkzeugdefinition über MCP ein. Die erste Verbindung verwendet die
vorhandene ChatGPT-Oberfläche ohne zusätzliche interaktive Karte.

Der erste Meilenstein ist erreicht, wenn sich der Mieter im Browser anmelden,
ChatGPT das Leserecht erteilen und einen eigenen Fall abrufen kann. Derselbe
Client muss beim Abruf eines fremden Falls dieselbe Rückmeldung erhalten wie
beim Abruf eines unbekannten Falls. Nach einem Widerruf wird der nächste
Aufruf abgewiesen. Der Nachweis für Schreibwerkzeuge umfasst zusätzlich
die Bestätigung im Browser und die Fortsetzung des MCP-Aufrufs.

Vor der Umsetzung ist der Zielclient gegen seine tatsächlich unterstützte
Protokollrevision und den URL-Modus zu prüfen. Der bisherige Test verwendet
`2025-11-25`. In der Revision `2026-07-28` kann der Server den zusätzlichen
Schritt über `input_required` anfordern. Für einen Client ohne Unterstützung
des Bestätigungsablaufs bleibt der Schreibvorgang gesperrt. Für ChatGPT ist
der URL-Modus ein eigener Abnahmepunkt. Eine ChatGPT-interne Bestätigung oder
ein vom Modell gesendeter Bestätigungswert reicht für die Freigabe nicht aus.
Siehe die [Elicitation-Spezifikation 2025-11-25](https://modelcontextprotocol.io/specification/2025-11-25/client/elicitation)
und die [Elicitation-Spezifikation 2026-07-28](https://modelcontextprotocol.io/specification/2026-07-28/client/elicitation).

## Geplante Prüfungen

| Prüfung | Erwartetes Ergebnis |
| --- | --- |
| Im Chat wird ein Entwurf bestätigt, korrigiert oder per direktem Modellaufruf ohne Benutzerklick zu senden versucht. | Der Klick bestätigt nur den angezeigten Entwurf. Korrektur und direkter Modellaufruf speichern keine Nachricht. |
| Nutzer verbindet den MCP-Client erstmals, ruft einen Fall ab und bestätigt eine Nachricht. | Anmeldung, Delegation und Bestätigung gelingen im Browser ohne manuelle Tokenübergabe. |
| ChatGPT zeigt die URL-Bestätigung an und verarbeitet den Abschluss. Dieselbe Nachricht wird mit einem Client ohne URL-Modus vorbereitet. | Der erste Lauf belegt die vollständige Browser-Kette. Im zweiten Lauf bleibt der Versand gesperrt. |
| Client fragt nur Lesezugriff an oder wechselt beim Öffnen der Bestätigungsseite das PropertyFlow-Konto. | Der Client erhält kein Schreibrecht beziehungsweise der andere Benutzer kann weder den Entwurf lesen noch ihn bestätigen. |
| Client ruft die Werkzeugliste oder ein Werkzeug ohne gültige Delegation auf. | Der Dienst weist den Aufruf ab. |
| Agent listet mehrere eigene Fälle und ruft einen davon ab. | Nur berechtigte Fälle und die vereinbarten Felder sind sichtbar. Ein fremder und ein unbekannter Fall liefern dieselbe Ablehnung. |
| Die Kontaktadresse eines Falls wird geändert. | Das bisher berechtigte Mieterkonto verliert beim nächsten Aufruf den Zugriff. |
| Nutzer widerruft ChatGPT unter «Verbundene Apps», während eine Bestätigung offen ist. | Die Verbindung ist nicht mehr aktiv. Werkzeugaufruf, Token-Erneuerung und Bestätigung der vorbereiteten Nachricht werden abgewiesen. |
| Agent verfügt nur über Leserecht oder sendet geänderte Daten zu einem bestehenden Bestätigungsvorgang. | Es wird keine Nachricht gespeichert. |
| Mieter öffnet die Seite und bricht ab oder lässt die Bestätigung ablaufen. | Es wird keine Nachricht gespeichert. |
| Fall wird vor der Bestätigung abgeschlossen, wechselt die Kontaktadresse oder die Delegation wird widerrufen. | Der Dienst weist die Bestätigung ab. |
| Mieter bestätigt die angezeigte Nachricht und wiederholt denselben Aufruf. | Genau eine Mieternachricht wird gespeichert. Der Versand ist im Audit nachvollziehbar. |
| Die Camunda-Bestätigung bleibt nach Speicherung der Mieternachricht zunächst aus und trifft später ein. | Die Oberfläche zeigt zuerst die vereinbarte Zwischenmeldung und nach dem bestätigten Ergebnis «Nachricht gesendet». Im Fallverlauf bleibt genau eine Nachricht. |
| Mitarbeiter filtert nach dringenden offenen Fällen und ruft einen Treffer ab. | Liste und Detail liefern die vereinbarten Felder. Interne Memos fehlen im MCP-Ergebnis. Ein Mieterkonto erhält keinen Zugriff auf die Mitarbeiterwerkzeuge. |
| Ein berechtigter Mitarbeiter ruft einen abgeschlossenen Fall mit fremder Kontaktadresse ab. Danach wird seine Mitarbeiterrolle entzogen. | Der erste Aufruf liefert die vereinbarten Felder. Der nächste Aufruf wird trotz bestehender Delegation abgewiesen. |
| Mitarbeiter bestätigt eine Rückfrage und sendet denselben Bestätigungsvorgang erneut. | Genau eine veröffentlichte Nachricht mit Antwortbedarf ist im Mieterfall sichtbar. |
| Agent versucht eine kritische Aktion mit einer Freigabe für eine andere Aktion oder vom falschen Konto. | Der Dienst verweigert die Aktion. Die Ablehnung bleibt im Audit nachvollziehbar. |
| Berechtigte Bestätigung und abgewiesener Schreibversuch werden geprüft. | Audit-Ereignisse zeigen die jeweiligen Ergebnisse und enthalten keine Texte oder Zugangsgeheimnisse. |
| Anwendung nach einem protokollierten Schreibversuch neu starten und die Audit-Einsicht mit und ohne explizites Audit-Recht prüfen. | Der Eintrag bleibt erhalten. Die Mitarbeiterrolle und ein MCP-Token gewähren keine Audit-Einsicht. |
| Bereinigung mit Einträgen auf beiden Seiten der konfigurierten Frist ausführen. | Nur abgelaufene MCP-Audit-Einträge werden gelöscht. Die anderen Audit-Tabellen behalten ihre Einträge. |
| Mitarbeiter verwendet die Bestätigung für eine andere Aktion oder einen veralteten Fallstand. Alternativ ist der Fallprozess nicht erreichbar. | Der Dienst weist die Veröffentlichung ab und protokolliert das Ergebnis. |

Der geplante Abgabenachweis zeigt den Ablauf auf beiden Seiten. Der Mieter
findet einen eigenen Fall und bestätigt seine Ergänzung. Die Bewirtschaftung
findet den Fall in ihrer Übersicht, liest den Verlauf und bestätigt eine
Rückfrage. Ein abgewiesener Schreibversuch belegt die serverseitige Prüfung.
