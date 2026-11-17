# Gemeinsamer Zugriffsvertrag für Mitarbeiter-Login, Fallübersicht und Falldetail

Stand 14.11.2026 · Stufe 2, erster Abschnitt · Regulärer Web-Einstieg mit PostgreSQL und SMTP.

Mitarbeitende melden sich mit einem E-Mail-Einmalcode an und öffnen ein gespeichertes Falldetail. Das erste Verwaltungskonto wird gemäss UC-007 eingerichtet. Für dieses Inkrement genügt ein auf diese Weise bereitgestelltes aktives Mitarbeiterkonto. Die reguläre Mieterregistrierung, die Mitarbeiter-Kontoverwaltung im Browser, MCP-Delegation und Falländerungen folgen separat. Anlegen und Lesen von Gastfällen aus Stufe 1 bleiben über ihre bestehenden Routen verfügbar. Quellen sind [UC-006](../use_cases/UC-006-mitarbeiter-anmelden.md), [UC-007](../use_cases/UC-007-mitarbeiterkonto-anlegen.md), [UC-029](../use_cases/UC-029-mitarbeiterzugriff-entziehen.md), [ZUG-04](../specifications/fallzugriff-und-sicherheit.md#zug-04-rechte-und-vertrauensgrenzen), [UI4](../frontend/ansicht-04-login.md) und die [OpenAPI](openapi/propertyflow-web.yaml).

## Java-Verträge

Der Anmeldevertrag gehört zu `com.pertegato.propertyflow.access.contract`.

```java
public interface StaffAuthentication {
    void requestCode(UUID loginFlowId, String email);

    Optional<WebAccountContext> verifyCode(UUID loginFlowId, String code);
}

public interface StaffAccountAccess {
    StaffAccessDecision checkStaffAccess(WebAccountContext context);
}

public enum StaffAccessDecision {
    ALLOWED,
    UNAUTHENTICATED,
    FORBIDDEN
}
```

| Aufruf | Eingabe und Ergebnis |
| --- | --- |
| `requestCode` | Der Aufrufer ist der Web-Adapter mit geprüfter CSRF- und Browsersitzung. Er übergibt eine neue zufällige, serverseitig erzeugte Flow-ID und die E-Mail-Adresse. Der Dienst wendet `EmailAddress` an und reserviert die Anfrage. Die erfolgreiche Rückkehr bedeutet eine neutral behandelte Anforderung. Versand und seine Fehler erscheinen im öffentlichen Ergebnis unabhängig vom Kontostatus. |
| Fehler bei `requestCode` | Eine typisierte `StaffAuthenticationException` mit `code()` liefert `INVALID_EMAIL` oder `RATE_LIMITED`. Der Adapter bildet sie auf HTTP 400 bzw. 429 ab. Bei einer abgewiesenen Anforderung bleibt die bisherige Challenge in der Sitzung unverändert. |
| `verifyCode` | Der Adapter verwendet ausschliesslich die in der Sitzung gespeicherte Flow-ID. Der Dienst prüft sechs ASCII-Ziffern, Zuordnung, Gültigkeit und verbleibende Versuche. Bei erfolgreicher atomarer Einmalverwendung und weiterhin aktivem Mitarbeiterkonto liefert er den bestehenden `WebAccountContext`. Fehlender, ungültiger, abgelaufener oder verbrauchter Code sowie eine inzwischen fehlende Mitarbeiterberechtigung ergeben `Optional.empty()`. |
| `AccountCaseReading.readForStaff(WebAccountContext, String caseId)` | Bestehende Signatur. Der Fallservice prüft im Access-Dienst die aktuelle Identität und Mitarbeiterrolle, bevor er Falldaten liest. Das Ergebnis ist die bestehende `StaffCaseView` mit Fallstand, für Mitarbeitende sichtbaren Nachrichten und internen Memos. Der Aufruf verändert den Fall nicht und funktioniert bei ausgeschaltetem Camunda. |
| `StaffCaseListing.listForStaff(WebAccountContext, StaffCaseQuery)` | Derselbe Fallservice prüft die aktuellen Mitarbeiterrechte. Er liefert eine `StaffCasePage` mit Fallübersichten, Trefferzahl und Seitennavigation. Die Abfrage unterstützt Suche, Filter und Sortierung. |

Der Access-Port gibt seine eigene Zugriffsentscheidung zurück. Case Processing
übersetzt die Ablehnung in den bestehenden `CaseErrorCode`. So bleiben
Implementierung und Kontodaten bei Access und der Fall-Lesedienst behält
seine Signatur und Fehler.

| Zustand | Ergebnis am Fall-Lesedienst |
| --- | --- |
| Aktives Konto mit Mitarbeiterrolle | Fallprüfung und Lesen sind zulässig. Eine separate Rollenkopie in der Sitzung ist dafür nicht erforderlich. |
| Konto ist gesperrt oder hat keine Mitarbeiterrolle mehr | `FORBIDDEN`, Zugriff sofort abweisen, auch bei bestehender Browsersitzung. |
| Die Kontoreferenz ist unbekannt | `UNAUTHENTICATED`. Der Web-Adapter verwirft den veralteten Anmeldekontext. |
| Fall fehlt oder ist fachlich nicht verfügbar | `CASE_NOT_AVAILABLE`, nach der Mitarbeiterprüfung. Der Fall-Link ist kein Anmeldenachweis. |

## Einmalcode und Browsersitzung

- Der Web-Adapter speichert die Flow-ID und die für erneutes Anfordern benötigte geprüfte Adresse in der Voranmeldesitzung. Er liest weder Flow-ID noch Konto-ID, Rolle oder Rückkehrziel als Vertrauensnachweis aus Formularfeldern. Der Code selbst erscheint weder im Sitzungszustand noch in Antworten oder Logs.
- Der Access-Dienst bindet jede Challenge an die konkrete Flow-ID, die normalisierte Adresse, den Mitarbeiter-Anmeldezweck und die bei Ausstellung ermittelte Konto-ID. Browser B kann die Anforderung aus Browser A auch mit derselben E-Mail-Adresse weder prüfen noch verbrauchen. Fehlversuche und Verbrauch sind pro Challenge atomar. Mehrfaches gleichzeitiges Prüfen ergibt höchstens eine erfolgreiche Einmalverwendung.
- Beim Anfordern in derselben Sitzung ersetzt eine angenommene Anforderung die Flow-ID. Das gilt auch dann, wenn die öffentliche neutrale Rückmeldung keinen Versand auslöst. Der Web-Adapter koordiniert Anfordern, Prüfen und Abschliessen der Anmeldung einschliesslich Sitzungsrotation innerhalb derselben Sitzung. Eine bereits ersetzte Flow-ID darf keine Sitzung anmelden. Nach erfolgreicher Prüfung entfernt der Adapter Flow-ID und Anforderungsadresse. Bei fehlender oder verlorener Voranmeldesitzung wird neu angefordert.
- Der Versand läuft entkoppelt von der HTTP-Antwort. Für syntaktisch gültige Eingaben ohne erreichtes Limit bleiben Status und dargestellter Hinweis bei aktiver, unbekannter, gesperrter oder reiner Mieteradresse sowie bei SMTP-Fehler gleich. Der Dienst sendet einen verwendbaren Code nur an ein zum Zeitpunkt der Ausgabe zulässiges Mitarbeiterkonto. Ein verspäteter Versandfehler darf nur seine eigene Challenge betreffen. Anforderungslimits gelten auch für unbekannte Adressen. Die Codeprüfung prüft den aktuellen Kontostand erneut.
- Standardmässig gilt der Code zehn Minuten mit höchstens fünf Fehlversuchen je Challenge. Pro normalisierter Adresse gilt mindestens eine Minute zwischen Anforderungen und höchstens fünf Anforderungen pro Stunde. Die Grenzwerte sind konfigurierbar.
- Beim Login rotiert der Web-Adapter Sitzungskennung und CSRF-Wert. Die Sitzung führt die verifizierte Konto-ID. Logout invalidiert die Browsersitzung. Konten und Rollen überstehen einen Anwendungsneustart; Sitzung und noch ungenutzter Einmalcode können nach Neustart neu angefordert werden. Für HTTPS gelten `Secure`, `HttpOnly`, `SameSite=Lax` und Cookie-only Tracking gemäss ZUG-06.

## HTTP und UI4

| Endpunkt | Abbildung |
| --- | --- |
| `GET /anmelden` | 200 mit Adresseingabe und Sitzungs-CSRF. Das Formular verwendet im regulären Profil `access/staff-login`. Die bestehende UI4-Gestaltung und Formularfeldnamen bleiben. |
| `POST /anmelden/code` | 200 mit der neutralen Codeansicht. 400 für fehlende oder ungültige Adresse, 403 bei fehlender Sitzung bzw. ungültigem CSRF, 429 bei Anforderungslimit. Fehlt `email` beim erneuten Anfordern, gilt die zuletzt in derselben Sitzung hinterlegte Adresse. |
| `POST /anmelden/pruefen` | 303 nach erfolgreicher Prüfung und Rotation zur vorgemerkten erlaubten Mitarbeiterseite. 400 bei fehlender Codeanforderung oder unbrauchbarem Code, 403 bei fehlender Sitzung oder ungültigem CSRF. Fehlertexte enthalten keine Hinweise auf Kontoaktivität, Versandstatus oder den eingegebenen Code. |
| `GET /mitarbeiter/faelle` | Reguläre Fallübersicht und Standardziel nach der Anmeldung. 200 für berechtigte Mitarbeitende, 400 bei ungültigen Such- oder Filterangaben. Für 303, 401 und 403 gelten dieselben Regeln wie beim Falldetail. |
| `GET /mitarbeiter/faelle/{id}` | 200 nur für berechtigte Mitarbeitende. Bei fehlender Anmeldung speichert die normale Seitennavigation die lokale Zielseite und leitet per 303 zu `/anmelden`. Ein Hintergrundabruf (`X-PropertyFlow-Request: partial`) erhält 401 ohne Weiterleitung. Ein gesperrtes Konto oder fehlende Mitarbeiterrechte ergeben 403 mit `X-PropertyFlow-Access: denied`. Fehlender Fall ergibt 404 erst nach der Rechteprüfung. |
| `POST /abmelden` | 303 auf `/mieter/fall` nach CSRF-Prüfung und Invalidierung der Sitzung. Fehlende Sitzung oder ungültiges CSRF ergeben 403. |

Für geschützte Ansichten, Anmeldung und Weiterleitungen gelten `Cache-Control: no-store` und `Referrer-Policy: no-referrer`. Der Adapter übernimmt die kontextrelativen Weiterleitungen und die Beschränkung auf lokale Mitarbeiterpfade aus den vorhandenen Web-Helfern. Die beim Aufruf auf dem Server vorgemerkte Route hat Vorrang. `returnTo` im Falldetail betrifft die Rückkehr zur Fallübersicht. Es erlaubt kein frei wählbares Login-Ziel. Nach einem abgewiesenen Abruf sendet der Browser keine Schreibaktion erneut.

Das Falldetail zeigt in diesem Inkrement die gespeicherten Fallstammdaten und die ursprüngliche Mietermeldung. Falländerungen und die Kontoverwaltung im Browser folgen separat. Fallaktionen setzen die Freigabe des zuständigen Camunda-Schritts voraus.

Bei einem vorübergehend nicht verfügbaren Konto- oder Falldienst gilt synchron HTTP 503 (`SERVICE_UNAVAILABLE` oder `STORAGE_UNAVAILABLE`). Unerwartete Fehler ergeben HTTP 500. Beide Antworten zeigen einen neutralen HTML-Hinweis. Bei einem verzögerten SMTP-Fehler gilt weiterhin die neutrale Antwort 200 der Codeanforderung.

## Aufgaben und Abnahme

| Worker A | Worker B |
| --- | --- |
| HTTP-Abbildung, UI4, CSRF, Bindung der Flow-ID an die Browsersitzung, sichere Weiterleitung, Logout und lesendes Falldetail. | Java-Ports und reguläre Access-Implementierung, dauerhaftes Konto und Rollen, Einmalcode und SMTP, erneute Rechteprüfung im Fall-Lesedienst. |
| Adaptertests mit kontrolliertem Access-Dienst und `AccountCaseReading`, anschliessend Browserprüfung mit isoliertem Mailpit. | Dienst- und PostgreSQL-Tests für Kontoanlage, Login nach Neustart, Rollenentzug, Challenge-Verbrauch und neutrale Fehler- und Versandbehandlung. |

| Prüfung | Nachweis |
| --- | --- |
| UI4 mit E-Mail- und Codeansicht, Fehlermeldung und kontextrelativen Formularzielen | [Rendering-Test mit Thymeleaf](../../src/test/java/com/pertegato/propertyflow/access/web/StaffLoginViewTest.java), ergänzt durch bestehende Tests zur Demo-Registrierung. |
| Erfolg, 400 für unbrauchbaren Code, 403 für ungültiges CSRF, 429 für Anforderungslimits | HTTP-Adaptertest mit kontrollierten Serviceergebnissen. Beim CSRF-Fehler bleibt der Access-Dienst unaufgerufen. Code oder Flow-ID aus dem Request können keinen fremden Vorgang auswählen. |
| Seitennavigation aus UI3, Login und Rückkehr, 401 für einen Hintergrundabruf vor der Anmeldung, 403 nach Rollenentzug, 404 bei fehlendem Fall | Adaptertest mit leerer, berechtigter und abgewiesener Sitzung. Fallabruf verwendet ausschliesslich den serverseitigen Kontokontext. Geschützte Antworten enthalten keine Falldaten für unberechtigte Aufrufer. |
| Dieselbe Adresse in zwei Sitzungen, abgelaufener Code, fünf Fehlversuche, neuer Code, gleichzeitige Prüfung, fehlgeschlagener SMTP-Versand | Diensttest mit kontrollierter Uhr und Versandport. Codes aus einer anderen Sitzung bleiben wirkungslos. Der öffentliche Anforderungshinweis ist für alle Kontozustände und Versandausgänge gleich. |
| Neuanforderung konkurriert mit der Sitzungseröffnung, verzögerter SMTP-Fehler fällt nach Neuanforderung an, oder die Rolle wird zwischen Ausgabe und Prüfung entzogen | Adaptertest koordiniert Sitzungseröffnung und Wechsel der Flow-ID. Diensttest prüft, dass der alte SMTP-Fehler die neue Challenge intakt lässt und der Rollenentzug die Anmeldung verhindert. |
| Kontodienst oder Falldatenbank beim synchronen Aufruf nicht verfügbar | Adaptertests prüfen HTTP 503 und HTTP 500 mit neutralem HTML-Hinweis. |
| Echtes Mitarbeiterkonto in PostgreSQL, OTP aus Test-Mailpit, geschützte Fallübersicht, Falldetail und Logout | [Browsertest](../../src/test/browser/stage2/staff-login.test.mjs) für die laufende reguläre Anwendung. Die Einrichtung und der Rechteentzug werden separat im [PostgreSQL-Test](../../src/test/java/com/pertegato/propertyflow/access/service/StaffAccountPostgresTest.java) geprüft. |

## Anschluss an MCP

Der Web-Anmeldedienst soll später für den regulären OAuth-Einwilligungsablauf wiederverwendet werden. Der MCP-Client erhält ein für den MCP-Endpunkt bestimmtes Delegationstoken. Browsersitzung und Fall-Link sind eigenständige Zugänge. Signatur/Introspektion, Audience, Clientbindung, Scope und Delegationswiderruf bleiben Teil des späteren MCP-Vertrags. Der Dienst prüft bei jedem Zugriff die aktuelle Berechtigung. [ADR-005](adr/ADR-005-zugriffsmodell.md#autorisierung-über-mcp) · [MCP Guidelines](../specifications/MCP_GUIDELINES.md#6-oauth-identität-und-objektbezogene-autorisierung)
