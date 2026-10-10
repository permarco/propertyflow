# ADR-004: Tokenbasierter Mieterzugriff ohne Benutzerkonto

## Status

Akzeptiert – dokumentiert die am 09.10.2026 vereinbarte Zugangsentscheidung. Die technische Implementierung und die noch offenen Sicherheitsparameter sind damit nicht als fertig bestätigt.

**Fachlich konkretisiert am 10.10.2026:** Der persönliche Fall-Link hat keine zeitliche Ablauffrist. Diese bestätigte Festlegung ersetzt den bisherigen offenen Laufzeitvorschlag; das direkte Tokenmodell und die getrennten Regeln für einen bewussten Widerruf bleiben bestehen.

## Kontext

Mieterinnen und Mieter sollen neue Anliegen erfassen und einen bestehenden Fall wiederholt aufrufen können. Ein Mieterportal mit Benutzerkonto und Anmeldung ist für diesen Umfang nicht vorgesehen. Der persönliche Falllink wird per E-Mail zugestellt und in späteren Benachrichtigungen wiederverwendet.

Die Case-ID wird als lesbare, unveränderliche Fallreferenz benötigt. Sie soll keinen Zugriff gewähren. Ein separater geheimer Zugang muss dem Fall zugeordnet, geprüft und bei Bedarf gesperrt werden können, ohne die Fallidentität zu ändern.

PropertyFlow besitzt die fachlichen Daten und verantwortet die Autorisierung gemäss [ADR-001](ADR-001-grundarchitektur.md). Die Ansicht wird mit Thymeleaf serverseitig gerendert gemäss [ADR-003](ADR-003-praesentationsschicht.md). Camunda 8 orchestriert die Hintergrundverarbeitung gemäss [ADR-002](ADR-002-camunda8-workflow-orchestrierung.md); der Browser greift nicht direkt auf Camunda zu.

Die detaillierten Regeln sind in [Fallzugriff und Sicherheit](../../specifications/fallzugriff-und-sicherheit.md) beschrieben. Dieser ADR dokumentiert ihre Architekturgrundlage, Alternativen und Konsequenzen.

## Entscheidung

1. **Zugang ohne Benutzerkonto:** Ein kryptografisch zufälliger, nicht aus der Case-ID ableitbarer Token vermittelt die begrenzte Mieterberechtigung für genau einen Fall. Im vereinbarten Modell gibt es pro Fall genau einen gültigen Mieterzugang.
2. **Fallidentität und Berechtigung getrennt:** Die Case-ID identifiziert den Fall und erscheint im Inhalt der Ansicht. Nur der gültige geheime Token berechtigt zum Zugriff; Case-ID, Kontaktadresse oder Absende-ID allein genügen nicht.
3. **Direkte, wiederverwendbare Tokenadresse:** Der persönliche Link lautet `/mieter/fall/zugang/<geheimer-token>`. Ein gültiger GET rendert den Fall direkt unter derselben URL. Es gibt keinen Austausch gegen eine berechtigende Sitzung und keine GET-Weiterleitung auf eine tokenfreie Adresse. Jeder Lese- und Schreibaufruf prüft Token, Fallzuordnung und geltende Rechte erneut. Technische Cookies können dem Formularschutz dienen, ersetzen aber die Tokenprüfung nicht.
4. **Ein gleichbleibender Link ohne zeitliche Ablauffrist:** Der Token läuft weder durch Zeitablauf noch durch Inaktivität ab. Öffnen und gewöhnliche E-Mail-Benachrichtigungen verbrauchen oder ersetzen ihn nicht; eine Verlängerung ist nicht erforderlich. Ein bewusster Ersatz erzeugt einen neuen unabhängigen Token und sperrt den alten. Die Case-ID und der Verlauf bleiben erhalten.
5. **Zwei Speicherformen desselben Tokens:** Ein Hash dient zur Zugriffsprüfung. Eine separat geschützte, verschlüsselte Tokenkopie ermöglicht der berechtigten Versandfunktion, denselben Link in späteren E-Mails wiederherzustellen. Der Verschlüsselungsschlüssel wird ausserhalb der Datenbank und des Git-Repositorys verwaltet. Nur die Versandfunktion erhält die Möglichkeit zur Entschlüsselung; Fallbearbeitung, Camunda und KI erhalten sie nicht.
6. **Besitz vermittelt Berechtigung, keine Identität:** Jeder Besitzer eines gültigen Links erhält dieselben begrenzten Mieterrechte. Linkweitergabe ist faktische Weitergabe dieser Rechte. Sie erzeugt keine separate Empfängeridentität oder zusätzliche E-Mail-Abonnierung.

HTTP 303 nach erfolgreicher Fallanlage oder Nachrichtenspeicherung bleibt für Post/Redirect/Get zulässig. Er führt zur persönlichen Tokenansicht und ist von einer Weiterleitung beim Öffnen des Links zu unterscheiden.

## Begründung

Der persönliche Link erfüllt den wiederholten Fallzugriff ohne Registrierung, Passwortverwaltung oder Portalnavigation. Die Trennung von Case-ID und Token erlaubt eine stabile Fallreferenz und einen unabhängig widerrufbaren Zugang.

Die direkte Tokenadresse entspricht dem vereinbarten Bedienmodell. Sie vermeidet eine zusätzliche berechtigende Fallsitzung, verlangt dafür aber den Schutz des geheimen URL-Pfads bei jedem Aufruf.

Ein Hash allein kann den ursprünglichen Token nicht für spätere E-Mails wiederherstellen. Die verschlüsselte Versandkopie ermöglicht denselben Link, ohne den Token dauerhaft im Klartext abzulegen oder bei jeder Benachrichtigung zu rotieren. Die separate Schlüsselverwaltung schützt gegen die Rekonstruktion der Links aus einer Datenbankkopie allein; sie schützt nicht allein gegen einen kompromittierten Anwendungsserver mit Zugriff auf Schlüssel und Versandkopien.

## Betrachtete Alternativen

| Alternative | Bewertung |
|---|---|
| Mieterportal mit Konto und Anmeldung | Könnte personenbezogene Zugriffe und getrennte Empfängerberechtigungen unterstützen, führt aber Registrierung, Kontoverwaltung und zusätzliche Bedienung ein. Für den vereinbarten Umfang verworfen. |
| Fallzugriff allein über eine geheime Case-ID | Vermischt dauerhafte Fallreferenz und widerrufbare Berechtigung. Die lesbare Case-ID soll ohne Weitergabe des Zugangs verwendet werden können. Verworfen. |
| Case-ID und separater Token gemeinsam in der URL | Technisch möglich, aber die zusätzliche Fall-ID ist für die Zuordnung nicht erforderlich. Die vereinbarte Route enthält nur den Token als variablen Zugangswert. |
| Token gegen eine Fallsitzung tauschen und auf eine tokenfreie URL weiterleiten | Würde die Geheimnisexposition in der sichtbaren Adresse reduzieren, benötigt jedoch Sitzungsautorisierung und widerspricht der vereinbarten dauerhaft sichtbaren Tokenadresse. Verworfen. |
| Einmaliger oder bei jeder Benachrichtigung wechselnder Token | Ältere E-Mails und Lesezeichen würden keinen dauerhaft wiederverwendbaren Zugang bieten. Für normale Zugriffe und Benachrichtigungen verworfen; bewusster Ersatz bleibt möglich. |
| Nur Hash speichern | Genügt zur Prüfung, ermöglicht aber keinen erneuten Versand desselben Tokens. Für die vereinbarten wiederkehrenden Linkmails unzureichend. |
| Token dauerhaft im Klartext speichern | Ermöglicht erneuten Versand, legt aber lesbare Zugangsgeheimnisse in der Datenbank ab. Verworfen zugunsten der verschlüsselten Versandkopie. |

## Konsequenzen

- Der gültige Zugang erlaubt ausschliesslich das Lesen des zugeordneten externen, veröffentlichten Fallverlaufs und bei aktivem Fall das Senden externer Nachrichten. Interne Memos, Backoffice-Aktionen und KI-/Prozessabbruch bleiben ausgeschlossen.
- Ein abgeschlossener Fall bleibt bei gültigem Token lesbar. Der Fallabschluss sperrt Nachrichten, widerruft aber den Zugang nicht automatisch und setzt keine Ablauffrist. Aufbewahrung und Löschung von Falldaten werden unabhängig von der zeitlich unbegrenzten Linkgültigkeit geregelt.
- Bei weitergegebenem Link kann PropertyFlow den ursprünglichen Mieter nicht vom Empfänger unterscheiden. Die Identität des Verfassers einer Nachricht ist damit nicht nachgewiesen. Ein Ersatz sperrt den alten Link für alle Besitzer; bereits gelesene Inhalte lassen sich nicht zurückholen.
- Die Browseradresse, Lesezeichen und E-Mails enthalten ein Zugangsgeheimnis. HTTPS, Log-Redaktion auch des URL-Pfads, Cache-/Referrer-Schutz, Ausschluss von Drittanbieterressourcen und CSRF-Schutz für Schreibaktionen sind erforderlich. Der Zugriffstoken ersetzt keinen CSRF-Nachweis.
- Schlüsselverwaltung, Entschlüsselungsrechte und Bereinigung der Versandkopien verursachen zusätzlichen Betriebsaufwand. Hash und verschlüsselte Kopie müssen demselben Zugang zugeordnet bleiben. Ein Wechsel des Verschlüsselungsschlüssels ist kein Wechsel des Zugriffstokens.
- Die Versandfunktion prüft den Zugang vor dem Versand. Widerrufene oder ersetzte Tokens werden nicht als gültige Links versendet; eine zeitliche Ablaufprüfung entfällt. Ein Versandfehler erzeugt keinen Ersatztoken und nimmt keine fachliche Speicherung zurück.
- Tokens, verschlüsselte Versandkopien und Schlüssel gehören nicht in Camunda-Variablen, LLM-Eingaben oder RAG-Kontexte. Normale Benachrichtigungen verwenden kurze Vorlagen und den persönlichen Link.

## Abgrenzung und offene Konkretisierungen

Die fehlende zeitliche Ablauffrist ist bestätigt. Dieser ADR legt kein konkretes Tokenformat, Hash-/Verschlüsselungsverfahren, keine Schlüsselrotation, Rate Limits oder Löschfristen fest. Die hierzu in der Zugangs-Spezifikation genannten Werte bleiben Review-Vorschläge. Kontaktbestätigung, Identitätsprüfung bei einem administrativen Linkersatz und die Behandlung wartender Mails bei ungültigem Zugang werden vor produktivem Einsatz konkretisiert. Eine UI3-Aktion «Fall-Link ersetzen» ist nicht vorgesehen.

Die Spezifikationen bleiben die Quellen für detaillierte Regeln und Prüfkriterien:

- [Fallzugriff und Sicherheit](../../specifications/fallzugriff-und-sicherheit.md): ZUG-01 bis ZUG-08, HTTP-Vertrag und Sicherheitsprüfkriterien.
- [Benachrichtigungen und Zustellung](../../specifications/benachrichtigungen-und-zustellung.md): Auslöser, Empfänger, Versandpflicht und Fehlerbehandlung.
- [Fallkommunikation](../../specifications/fallkommunikation.md): Sichtbarkeit, zulässige LLM-Grundlage und Nachrichtensperre.
- [Fallverwaltung](../../specifications/fallverwaltung.md): Fallidentität und Lebenszyklus.
- [Mieter-Fallansicht](../../frontend/ansicht-01-mieter-fallansicht.md): Darstellung und Bedienung.

Dieser ADR ergänzt ADR-001 bis ADR-003. Die grundlegende Präsentationsarchitektur bleibt in ADR-003 beschrieben. Dieser ADR regelt die Zugangsarchitektur; die Zuordnung interaktiver Funktionen zu einzelnen Ansichten bleibt den Use-Cases und UI-Spezifikationen vorbehalten. Künftige Änderungen an der hier festgelegten Zugangsarchitektur benötigen einen neuen oder ersetzenden ADR; fachliche und technische Detailregeln werden in den verknüpften Spezifikationen gepflegt.
