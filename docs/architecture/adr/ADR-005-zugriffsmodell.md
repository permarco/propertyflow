# ADR-005: Persönliche Konten und fallbezogener Zugriff über Web und MCP

## Status

**Vorgeschlagen.** Die fachliche Richtung und die automatische Fallzuordnung anhand der verifizierten E-Mail-Adresse sind am 12.11.2026 abgestimmt. Die technischen Anmeldeverfahren sind noch offen.

## Kontext

Mitarbeitende sollen sich vor der Fallbearbeitung persönlich anmelden. Mieter können weiterhin ohne Konto ein Anliegen einreichen und über ihren persönlichen Fall-Link antworten. Legt ein Mieter später freiwillig ein Konto an und verifiziert dieselbe E-Mail-Adresse, erhält er Zugriff auf alle Fälle, bei denen diese Adresse als aktuelle Kontaktadresse hinterlegt ist. Dieselbe Fallmenge gilt im Web und über MCP, jeweils mit den für den Mieter zulässigen Aktionen.

Der bisherige Verzicht auf Mitarbeiteranmeldung in [ZUG-04](../../specifications/fallzugriff-und-sicherheit.md#zug-04-rechte-und-vertrauensgrenzen) wird damit revidiert. Dieser Entwurf ergänzt den fallgebundenen Zugang aus [ADR-004](ADR-004-tokenbasierter-mieterzugriff.md). Eine bei der Einreichung angegebene E-Mail-Adresse ist zunächst eine Kontaktadresse. Ihre Verifikation belegt Zugriff auf das Postfach zum Prüfzeitpunkt und ermöglicht PropertyFlow, die zugehörigen Fälle für das Mieterkonto zu bestimmen.

## Akteure und Rechte

| Akteur und Zugangsweg | Lesen | Schreiben |
| --- | --- | --- |
| Mieter ohne Konto und ohne Fall-Link | Öffentliche Erfassung. | Neues Anliegen einreichen. Die Fallannahme wartet weder auf eine E-Mail-Verifikation noch auf E-Mail-Zustellung, Camunda oder KI. |
| Inhaber eines gültigen persönlichen Fall-Links | Mieteransicht des zugeordneten Falls mit veröffentlichtem Verlauf und freigegebenen Falldaten. | Externe Nachricht in diesem Fall senden, solange er aktiv ist. |
| Angemeldeter Mieter mit verifizierter E-Mail-Adresse | Alle Fälle mit passender aktueller Kontaktadresse und dieselben freigegebenen Falldaten wie in der Mieteransicht. | Neues Anliegen einreichen und in einem aktiven Fall mit passender Kontaktadresse eine externe Nachricht senden. |
| Angemeldeter Mitarbeiter | Für die Mitarbeiterrolle freigegebene Fälle und interne Informationen. | Autorisierte Bearbeitungsaktionen gemäss Fachregeln, beispielsweise Status ändern oder eine externe Antwort veröffentlichen. Kritische Aktionen erfordern die vorgesehene menschliche Freigabe. |
| MCP-Client im Auftrag eines angemeldeten Benutzers | Nur freigegebene Werkzeuge, Daten und Fälle innerhalb der delegierten Rechte. | Nur ausdrücklich delegierte fachliche Aktionen im Rahmen der Rechte des Benutzers am Fall. |
| Camunda-Integration und Hintergrunddienste | Für die jeweilige Aufgabe erforderliche Daten. | Definierte technische oder fachlich geprüfte Systemaktionen unter einer eigenen technischen Identität. |

Der persönliche Fall-Link vermittelt seine begrenzten Rechte im Browser. Der MCP-Zugang verwendet ein persönliches Konto mit eigener Delegation. Mieteraktionen bleiben auch im Konto und über MCP auf den externen Fallverlauf und zulässige Nachrichten beschränkt. Interne Memos, direkte Änderungen von Status oder Dringlichkeit sowie Freigaben bleiben der dafür autorisierten Bearbeitung vorbehalten.

## Entscheidungsentwurf

### Mitarbeiteridentität

Der Mitarbeiterbereich setzt für Lesen und Schreiben eine persönliche Anmeldung voraus. PropertyFlow ordnet jedem Mitarbeiter eine stabile interne Benutzer-ID und die vergebene Mitarbeiterrolle zu. Eine E-Mail-Verifikation allein verleiht keine Mitarbeiterrolle. Das konkrete Anmeldeverfahren und die Kontobereitstellung werden im nächsten Entwurfsschritt festgelegt.

### Freiwilliges Mieterkonto und Fallzuordnung

Der Ablauf für die Kontoeröffnung ist in [UC-005](../../use_cases/UC-005-userkonto-eroeffnen.md) beschrieben.

PropertyFlow führt ein Mieterkonto unter einer stabilen internen Benutzer-ID und prüft den Zugriff auf die angegebene E-Mail-Adresse vor der Aktivierung. **Vorschlag für den Nachweis:** ein kurz gültiger, einmal nutzbarer Code per E-Mail. Diese Verifikationsmail gehört zur Kontoanmeldung. Die [Fallbenachrichtigungen](../../specifications/benachrichtigungen-und-zustellung.md) verwenden weiterhin den persönlichen Fall-Link für den Browserzugriff.

**Automatische Fallzuordnung:** PropertyFlow bestimmt die Fälle des Mieterkontos anhand der Übereinstimmung der verifizierten Konto-Adresse mit der jeweils aktuellen Fall-Kontaktadresse. Das gilt für bereits bestehende und für künftige Fälle, unabhängig davon, ob sie mit oder ohne Anmeldung eingereicht wurden. Nach der Verifikation stehen die passenden Fälle sofort im Konto zur Verfügung.

**Neue Fälle aus dem Mieterkonto:** PropertyFlow übernimmt die verifizierte Kontaktadresse aus dem Konto. Bei Einreichungen ohne Anmeldung verwendet PropertyFlow die eingegebene Kontaktadresse und stellt die Annahme wie bisher im Browser bereit.

Bei Listen-, Detail- und Schreibzugriffen prüft der Dienst die aktuelle Kontaktadresse des Falls gegen die serverseitig verifizierte Adresse des angemeldeten Kontos. Eine E-Mail-Adresse aus Formular- oder Tool-Argumenten bestimmt keine Berechtigung. Nach einer geprüften Änderung der Fall-Kontaktadresse gemäss [ZUG-05](../../specifications/fallzugriff-und-sicherheit.md#zug-05-dauerhafte-gültigkeit-widerruf-und-ersatz) gilt die neue Adresse ab dem nächsten Zugriff. Eine neue Konto-Adresse zählt erst nach ihrer Verifikation. Fall-Link und MCP-Delegation unterliegen ihren eigenen Widerrufsregeln.

### Autorisierung über MCP

**Gemeinsame Anmeldung für Mieter und Mitarbeitende:** Web-Anwendung und MCP-Client im LLM-Chat nutzen denselben Anmeldedienst und denselben OAuth-Ablauf für die MCP-Freigabe. Bei einer gültigen Sitzung dieses Dienstes im selben Browserprofil genügt eine Anmeldung für beide Zugänge. Die Web-Anwendung verwendet eine Browsersitzung. Der MCP-Client erhält nach der Freigabe ein eigenes OAuth-Zugriffstoken für den MCP-Endpunkt über HTTP. PropertyFlow berücksichtigt die jeweilige Kontorolle und die an den Client delegierten Rechte. Der Ablauf ist in [UC-005](../../use_cases/UC-005-userkonto-eroeffnen.md#flussdiagramm--anmeldung-für-web-und-mcp) dargestellt.

Web und MCP rufen dieselben fachlichen Anwendungsdienste auf. Bei jedem MCP-Aufruf bestimmt PropertyFlow serverseitig den Benutzer und den delegierten Client. Für Mieterkonten gilt dieselbe E-Mail-basierte Fallzuordnung wie im Web. Der Anwendungsdienst prüft Eingabe, freigegebene Aktion, Fallberechtigung und fachlichen Zustand. Für eine kritische Aktion prüft er die menschliche Freigabe für genau diese Handlung gemäss [FALL-10](../../specifications/fallverwaltung.md#fall-10-automatisierung-und-menschliche-freigabe).

Das Audit erfasst bei Schreibversuchen den Benutzer, den Kanal und bei MCP den Client, die Aktion, die Fallreferenz, den Zeitpunkt und das Ergebnis. Persönliche Fall-Links, Anmeldecodes und Zugriffsgeheimnisse gelangen weder ins Audit noch in Modellkontexte oder Camunda-Variablen.

## Preis und Alternativen

Mitarbeiterkonten ermöglichen die persönliche Zuordnung von Änderungen. Kontobereitstellung und Sperrung kommen als Betriebsaufgaben hinzu. Der freiwillige Kontozugang hält den Einstieg für Mieter niedrigschwellig. PropertyFlow muss dafür zwei Zugangswege je Fall prüfen und deren Widerruf nachvollziehbar behandeln.

Die automatische Fallzuordnung erspart die manuelle Verknüpfung jedes einzelnen Falls. Ihr Preis ist die Bindung an die gepflegte Kontaktadresse. Bei einem gemeinsam genutzten oder später neu vergebenen Postfach kann ein Konto nach Verifikation alle noch mit dieser Adresse geführten Fälle sehen. Korrekturen der Fall-Kontaktadresse wirken sich daher auch auf den Kontozugriff aus.

## Geplante Prüfungen

| Prüfung | Erwartetes Ergebnis |
| --- | --- |
| Ohne Anmeldung einreichen, während Mail und Camunda ausfallen. | Ein dauerhaft angenommener Fall wird im Browser bestätigt. |
| Ohne Mitarbeiteranmeldung direkt einen Mitarbeiter-Endpunkt für eine Änderung aufrufen. | Der Dienst lehnt den Zugriff ab. Falldaten bleiben unverändert. |
| Einen unverifizierten Anmeldeversuch, einen abgelaufenen Code und die Wiederholung eines bereits genutzten Codes testen. | Es entsteht keine authentifizierte Sitzung. |
| Einen gültigen Fall-Link für Fall A im Browser verwenden und damit anschliessend einen MCP-Aufruf oder Zugriff auf Fall B versuchen. | Der Browserzugriff gilt ausschliesslich für Fall A. Der MCP-Aufruf wird abgelehnt. |
| Zwei Fälle ohne Anmeldung mit derselben Kontaktadresse einreichen, einen dritten mit einer anderen Adresse. Danach ein Konto für die erste Adresse anlegen und verifizieren. | Beide früheren Fälle erscheinen automatisch im Konto. Der dritte Fall erscheint weder in der Liste noch als abrufbares Detail. |
| Einen weiteren Fall mit derselben Kontaktadresse einreichen und anschliessend die Liste im Mieterkonto und über MCP abrufen. | Beide Kanäle liefern dieselbe Fallmenge und ausschliesslich mieteröffentliche Daten. |
| Die Fall-Kontaktadresse über den vorgesehenen geprüften Ablauf ändern. | Das bisherige Konto verliert bei der nächsten Anfrage den Zugriff auf den Fall. Das zur neuen Adresse passende verifizierte Konto kann den Fall abrufen. |
| Über MCP eine Nachricht im eigenen aktiven Fall schreiben und die Anfrage mit fremder Case-ID, entzogenem Recht oder abgeschlossenem Fall wiederholen. | Nur der berechtigte Aufruf speichert eine Nachricht. Das Audit weist den Benutzer und den MCP-Client aus. |
| Eine als kritisch eingestufte MCP-Aktion ohne Freigabe und mit Freigabe für eine andere Aktion aufrufen. | Der Dienst lehnt beide Aufrufe ohne fachliche Änderung ab. |
| Einen Fall-Link ersetzen und die MCP-Delegation getrennt widerrufen. | Der bisherige Link und die widerrufene Delegation sind ab der nächsten Anfrage gesperrt. |

## Nächster Entscheid

Anmeldeverfahren und Kontobereitstellung für Mitarbeiter, den Ablauf für die E-Mail-Verifikation der Mieterkonten und die Berechtigungsdelegation für MCP festlegen. Anschliessend die Zugriffsregeln in ZUG-04 nachführen und die Schnittstellen-IDs für C4 festlegen.
