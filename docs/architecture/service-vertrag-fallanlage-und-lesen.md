# Gemeinsamer Service-Vertrag: Fallanlage und Fall lesen

Stand: 14.11.2026 · Version: 0.1 · Ausgangspunkt: `dbbe652` plus aktueller Arbeitsbaum.

**Zweck:** Gemeinsame Implementierungsgrundlage für zwei Chats. Chat A implementiert HTTP-/MCP-Adapter, Chat B Services und Persistenz. Die Java-Vertragsdateien sind seit 14.11.2026 angelegt und gezielt getestet (Abschnitt 11). Fachservices und Persistenz sind damit noch nicht implementiert. In getrennten Worktrees wird genau dieser Vertragsstand übernommen; im selben Arbeitsverzeichnis sind die Dateien bereits für beide Chats sichtbar.

## 1. Umfang und verbindliche Ausgangslage

**Sofort startbereit: Stufe 1 – Gastanlage und Lesen über Fall-Link.** Die Konto- und MCP-Verträge bilden Stufe 2. Ihre Aktivierung und End-to-End-Abnahme hängen von einem gemeinsamen Access-Vertrag ab. Bis dahin werden dafür keine regulären Routen oder Ersatzidentitäten freigeschaltet.

Der Gesamtumfang dieses Dokuments ist:

- Anlage über das bestehende Webformular als Gast oder verifizierter Mieter.
- Dauerhafte Speicherung und idempotente Wiederholung.
- Lesen genau eines Falls über persönlichen Token, Mieterkonto oder Mitarbeiterkonto.
- Lesen des reduzierten Fallstands über das vorhandene MCP-Werkzeug.

Nicht enthalten: Falllisten/Suche, neue Nachrichten, Mitarbeiter-Schreibaktionen, vollständiger OAuth-Aufbau, KI-Anbindung und ausführbares BPMN. Diese folgen als eigene Abschnitte. Der Startauftrag wird jetzt dauerhaft gespeichert; er wird bis zur abgestimmten Camunda-Implementierung nicht abgearbeitet. Niemand setzt deshalb eigenständig „In Bearbeitung“ oder „In Abklärung“.

Es gelten der vorhandene [HTTP-Vertrag](openapi/fallannahme.yaml), [SS-004](schnittstellenentwurf-block-3.md#ss-004--mcp-werkzeug-fallstand-abrufen), [FALL-02/03](../specifications/fallverwaltung.md), [ZUG](../specifications/fallzugriff-und-sicherheit.md) und [ADR-004](adr/ADR-004-tokenbasierter-mieterzugriff.md). Insbesondere bleibt die Gastanlage zulässig; ein Login wird nicht neu erzwungen. Die Originalangabe `unit` ist Freitext/Referenz aus dem Formular, keine neue Pflicht zu einer existierenden Fremdschlüssel-ID.

## 2. Gemeinsame Java-Schnittstellen

Die folgenden Signaturen sind die Zielsignaturen für diesen Abschnitt. Alle Typen werden in einzelnen Dateien angelegt. Keine Spring-, Servlet-, JPA-, Camunda- oder MCP-SDK-Typen in diesen Schnittstellen. Öffentliche DTOs sind keine Entities.

```java
// intake/contract/CaseSubmission.java
public interface CaseSubmission {
    CaseAccepted submit(SubmissionContext context, CreateCaseCommand command);
}

// caseprocessing/contract/CaseCreation.java
public interface CaseCreation {
    CaseAccepted create(SubmissionContext context, CreateCaseCommand command);
}

// caseprocessing/contract/CaseReading.java
public interface CaseReading {
    TenantCaseView readByToken(String token);
}

// caseprocessing/contract/AccountCaseReading.java – Stufe 2
public interface AccountCaseReading {
    TenantCaseView readForTenant(WebAccountContext context, String caseId);

    StaffCaseView readForStaff(WebAccountContext context, String caseId);
}

// caseprocessing/contract/McpCaseReading.java – Stufe 2
public interface McpCaseReading {
    CaseStatusView readStatusForMcp(McpAccountContext context, String caseId);
}
```

`CaseSubmission` liegt in `com.pertegato.propertyflow.intake.contract`. `CaseCreation`, `CaseReading`, `AccountCaseReading`, `McpCaseReading`, Commands, Results, Views, Enums und `CaseServiceException` liegen in `com.pertegato.propertyflow.caseprocessing.contract`. Die drei Kontexttypen liegen in `com.pertegato.propertyflow.access.contract`.

Der HTTP-Anlagecontroller ruft `CaseSubmission` auf. Dessen Implementierung validiert/normalisiert und ruft `CaseCreation` synchron auf. `CaseCreation` bleibt selbst gegen ungültige Eingaben und falsche Berechtigungen abgesichert. Es besitzt die Transaktion und garantiert den Commit vor Rückgabe an Intake. Token-Lesecontroller rufen `CaseReading` auf; Konto- und MCP-Adapter verwenden später die getrennten Leseinterfaces. Stufe 1 benötigt damit keine Fake-Implementierungen für noch nicht angeschlossene Lesewege.

## 3. Identität: Daten aus dem Server, nicht aus dem Formular

```java
public sealed interface SubmissionContext {
    record Guest(UUID sessionScope) implements SubmissionContext {}
    record Tenant(UUID accountId) implements SubmissionContext {}
}

public record WebAccountContext(UUID accountId) {}

public record McpAccountContext(
    UUID accountId,
    UUID delegationId,
    String clientId
) {}
```

Die Kurzschreibweise der Records erläutert die Signaturen; bei der Umsetzung gelten die Formatierungsregeln des Repositorys.

**Vertrauensgrenze:** Kontexte entstehen ausschliesslich innerhalb des Servers nach Prüfung der aktuellen Sitzung bzw. des MCP-Zugriffstokens. Sie dürfen niemals als HTTP-/Tool-Eingabe deserialisiert werden. Die Records sind kein Authentifizierungsmechanismus und keine Berechtigung allein durch Kenntnis einer UUID. Rollen und E-Mail-Adressen werden bewusst nicht als vertrauenswürdige Kontextfelder übergeben.

- Chat A übernimmt die Identität aus dem geprüften Access-Adapter und prüft bei POST den CSRF-Nachweis.
- Chat B prüft bei jedem Service-Aufruf erneut das aktive Konto und die aktuelle Rolle; bei Mietern auch die verifizierte Adresse. Ein gefälschter oder inzwischen gesperrter Kontobezug ist kein zulässiger Aufruf.
- Für MCP muss die fachliche Prüfung zusätzlich die aktuelle Delegation, ihren Benutzer, Client, Ablauf/Widerruf und das delegierte Leserecht über den Access-Dienst prüfen. Ein gültiges Konto ohne passende Delegation genügt nicht. Die kryptografische/technische Tokenprüfung bleibt beim dafür zuständigen Access-Adapter; sie wird nicht durch Toolargumente ersetzt.
- Ein Gastkontext enthält eine zufällige serverseitige Scope-ID, die mit der ursprünglichen Gastsitzung verbunden bleibt. Keine E-Mail als Scope, keine vom Browser frei vorgegebene Scope-ID. Ende/Verlust dieser Sitzung beendet den Gast-Retry-Kontext. Keine neue Sitzung stillschweigend mit dem alten Kontext verbinden.
- Der Fall-Link ist eine eigene begrenzte Berechtigung. Er wird bei `readByToken` geprüft und berechtigt weder zum MCP-Zugang noch zum Mitarbeiterabruf.

### Access-Gate vor Stufe 2

`CurrentAccount` und `StaffLogin` hängen heute an `DemoAccounts`; `DemoMcpGrants` besitzt noch kein reguläres Client-/Delegationsmodell. Die Kontext-Records lösen diese Abhängigkeit nicht allein.

Vor Stufe 2 vereinbaren beide Chats einen eigenen kleinen Access-Vertrag: Auflösen der Sitzung, Abruf aktueller Konten/Rollen sowie Prüfung von MCP-Token und Delegation einschliesslich Clientbindung. Chat B besitzt Access-Service/Persistenz und die Entkopplung von `StaffLogin`; Chat A besitzt Login-/Session-/MCP-Webadapter. Bis zu diesem gemeinsamen Vertragsstand bleiben die konto- und MCP-bezogenen Tests ausstehend. Keiner baut unabhängig eine neue Identitätsbrücke.

Stufe 1 läuft ausdrücklich als separater Gast-/Token-Zugang ohne angeschlossenen Kontologin. Die bestehende Demo bleibt getrennt. Eine aktive Mietersitzung darf nicht stillschweigend ignoriert oder zum Gast herabgestuft werden. Ein noch nicht unterstützter `Tenant`-Kontext wird sicher mit `UNAUTHENTICATED` abgelehnt, statt seine Gastfelder zu verwenden. Die Konto-Leseinterfaces werden erst nach echter Anbindung registriert.

**Formularabbildung ab Stufe 2:** Bei gültigem verifiziertem Mieterkonto immer `Tenant` verwenden, auch wenn `accountSubmission=false` manipuliert wurde. Bei `accountSubmission=true` ohne gültiges Mieterkonto HTTP 401 und kein Anlageaufruf. Sonst ist `Guest` erlaubt, mit erforderlicher Gastadresse. Eine Mitarbeitersitzung wird für das öffentliche Gastformular nicht zum Mieterkonto umgedeutet; dieser Gastweg verwendet den Sitzungs-Scope. Das präzisiert die derzeitige Demo, deren `submissionScope` noch jedes Konto als Kontoscope behandelt.

## 4. Fallanlage: Eingaben und Ergebnis

```java
public record CreateCaseCommand(
    String operationId,
    String title,
    String unit,
    String description,
    String guestEmail
) {}

public record CaseAccepted(
    String caseId,
    String personalAccessToken,
    boolean replayed
) {}
```

Alle Strings im Command sind zunächst untrusted. `guestEmail` darf im Kontoweg null sein und wird dort vollständig ignoriert; der Dienst liest die aktuelle verifizierte Konto-Adresse. Bei Gästen ist eine gültige Adresse erforderlich.

`CaseAccepted` wird nur nach bestätigtem Commit oder nach erneutem berechtigtem Abruf einer bereits gespeicherten identischen Annahme geliefert. Die Case-ID ist eine undurchsichtige fachliche Zeichenfolge; der Controller erfindet kein Nummernformat. `personalAccessToken` ist ausschliesslich zur bestehenden persönlichen Weiterleitung bestimmt, nicht zur allgemeinen JSON-/MCP-Ausgabe. `replayed` ist ein internes Ergebnismerkmal, kein anderer HTTP-Erfolgsstatus. Ein aktueller Fallstatus wird absichtlich nicht mitgeliefert: Ein Retry kann erfolgen, nachdem der Prozess bereits fortgeschritten ist.

**Geheimnisse:** Command und Ergebnis dürfen nicht vollständig protokolliert werden. Für `CaseAccepted` ein redigiertes `toString()` vorsehen. Token niemals in Exceptiontexte, Traces, SQL-Parameterlogs, Testberichte oder Camunda-Variablen übernehmen. Hash und verschlüsselte Versandkopie gemäss ADR-004 speichern; ein identischer gültiger Retry darf nicht einen neuen Token erzeugen.

### Validierung und Vergleich

| Feld | Regel |
| --- | --- |
| `operationId` | Inhalt, höchstens 80 UTF-16-Codeeinheiten; kein UUID-Zwang, keine Umformatierung |
| `title` | Nicht leer/blank, höchstens 150 UTF-16-Codeeinheiten |
| `unit` | Nicht leer/blank, höchstens 200 UTF-16-Codeeinheiten; Ursprungsangabe bewahren |
| `description` | CRLF und CR zu LF; danach höchstens 2’000 UTF-16-Codeeinheiten vor äusserem Trimmen; Inhalt erforderlich |
| `guestEmail` | Bei Gast vorhandene `EmailAddress`-Regeln; bei Kontozugang serverseitige Konto-Adresse |

Bestehende Helfer `CaseDescription` und `EmailAddress` verwenden. Für Idempotenz werden Titel und Unit vor äusserem Trimmen, normalisierte Beschreibung vor Trimmen und die geprüfte Kontaktadresse verglichen. Form-/Darstellungsfelder wie `csrf` gehören nicht zum fachlichen Inhalt. Validierungsfehler dürfen keinen erfolgreichen Wiederholungseintrag verbrauchen.

### Atomarer Erfolg und Wiederholung

Case Processing speichert Fall mit „Eingegangen“, ursprüngliche Mitteilung bzw. eindeutige Referenz, Zugang, Wiederholungszuordnung, Startauftrag und erforderlichen Annahme-Benachrichtigungsauftrag konsistent in einer lokalen Transaktion. Externe Aufrufe an Camunda, Modell und SMTP gehören nicht in diese Transaktion.

Eindeutiger Wiederholungsschlüssel: Typ des Einreichungskontexts + serverseitige Konto-/Gastsitzungsreferenz + `operationId`. Gleicher Schlüssel und identischer Inhalt: derselbe Fall, kein zweiter Start-/Mailauftrag. Abweichender Inhalt: `IDEMPOTENCY_CONFLICT`. Auch zeitgleich eintreffende Requests müssen diese Garantie erfüllen; Java-`synchronized` allein reicht nicht. Identischer Retry nach weiteren Falländerungen setzt den Fall nicht auf „Eingegangen“ zurück.

Nach Speicherung ist die Annahme unabhängig von Camunda-/Modell-/Mail-Erreichbarkeit. Bei unklarem Commit-Ausgang keine Erfolgsmeldung erfinden; Retry mit gleichem Schlüssel klärt den Zustand. Die Wiederholungszuordnung referenziert den ursprünglich ausgegebenen Zugang beziehungsweise dessen Version. Sie darf niemals einfach den aktuellsten Ersatzlink entschlüsseln und herausgeben.

**HTTP-Randfall – zwischen Chat A und Chat B am 14.11.2026 abgestimmt:** Der aktuelle Vertrag verspricht dieselbe Weiterleitung nur bei weiterhin gültigem Link. Für widerrufenen/ersetzten ursprünglichen Link oder nicht mehr zulässigen Zugriff dieses Retry-Kontexts definieren wir `REPLAY_ACCESS_UNAVAILABLE`: kein neuer Fall, kein Ersatzlink und keine Herausgabe eines neuen Geheimnisses. Festgelegte HTTP-Abbildung: 409 mit neutralem Hinweis „Die Einreichung wurde bereits angenommen; der bisherige Zugang ist nicht mehr verfügbar.“ Chat A ergänzt diesen zusätzlichen 409-Grund koordiniert in OpenAPI, bevor dieser Randfall als abgenommen gilt. Dies ist eine neue Konkretisierung, nicht bereits nachgewiesenes Demo-Verhalten. Keine Umgehung des Widerrufs durch Zugriff auf die verschlüsselte Versandkopie.

## 5. Leseergebnisse

Getrennte Projektionen verhindern, dass der Controller interne Felder nachträglich aus einer umfassenden Entity entfernen muss.

```java
public record CaseStatusView(
    String caseId,
    CaseStatus status,
    Optional<Urgency> urgency
) {}

public record TenantCaseView(
    CaseStatusView state,
    String title,
    String description,
    String originalUnit,
    Optional<String> assignedUnit,
    String maskedContact,
    Instant receivedAt,
    Instant lastTenantContactAt,
    long version,
    Optional<UrgencySource> urgencySource,
    List<PublicMessage> messages,
    List<UrgencyChange> urgencyHistory
) {}

public record PublicMessage(
    String id,
    PublicAuthor author,
    String text,
    Instant occurredAt
) {}

public record UrgencyChange(
    String id,
    Optional<Urgency> previous,
    Urgency current,
    UrgencySource source,
    Instant occurredAt
) {}

public record StaffCaseView(
    TenantCaseView publicView,
    String contactEmail,
    Optional<LocalDate> followUpDate,
    List<String> reviewReasons,
    List<InternalMemo> internalMemos
) {}

public record InternalMemo(
    String id,
    String text,
    Instant occurredAt
) {}
```

Dieser Abschnitt implementiert lesende Detailseiten, nicht den vollständigen UI3-Aktionsvertrag. `PublicMessage` enthält ausschliesslich gespeicherte veröffentlichte externe Nachrichten; keine automatisch aus internen Statusereignissen generierten Texte. Dringlichkeitsänderungen bleiben als eigene strukturierte Historie vorhanden. Für die Ansicht können beide Listen nach Zeitpunkt zusammengeführt werden. Bei gleichem Zeitpunkt liefert der Service eine stabile Reihenfolge innerhalb der Listen; die Ansicht verwendet Typ/ID als stabilen Tie-Breaker.

Alle Listen sind unveränderliche Snapshots, niemals Lazy-JPA-Collections. Zeiten sind `Instant` in UTC; Anzeige in Europe/Zurich liegt beim Adapter. `assignedUnit`, `followUpDate` und Bewertung dürfen fachlich fehlen; optionale Werte sind `Optional.empty()`, niemals null. `reviewReasons`/Listen sind leer statt null. `maskedContact` enthält bereits eine freigegebene maskierte Darstellung, keine Rohadresse zum Maskieren im Browser. Die ursprüngliche Beschreibung darf im Datenmodell einmal gespeichert und im Verlauf referenziert werden; keine doppelte externe Nachricht erzeugen.

`InternalMemo` bildet nur ausdrücklich interne Memos ab. Mitarbeiteridentitäten/Audit, Aufgaben, Bearbeitungsrechte und interne Statusereignisse werden nicht durch diese reduzierte Projektion ersetzt; zusätzliche Ansichtsverträge kommen mit den jeweiligen Aktionen. Bis dahin erscheinen im regulären Profil keine unimplementierten Schreibaktionen.

### Enum-Werte und Darstellung

| `CaseStatus` | Deutscher HTTP-/MCP-Anzeigewert |
| --- | --- |
| `RECEIVED` | Eingegangen |
| `UNDER_CLARIFICATION` | In Abklärung |
| `WAITING_FOR_TENANT` | Wartet auf Mieterantwort |
| `STAFF_REVIEW_REQUIRED` | Mitarbeiterprüfung erforderlich |
| `IN_PROGRESS` | In Bearbeitung |
| `WAITING_FOR_EXTERNAL` | Wartet auf externe Rückmeldung |
| `WAITING_FOR_FOLLOW_UP` | Wartet auf Wiedervorlage |
| `CLOSED` | Abgeschlossen |

`Urgency`: `CRITICAL`, `HIGH`, `NORMAL`, `LOW` → Kritisch, Hoch, Normal, Niedrig. Leeres `Optional<Urgency>` bedeutet „Noch nicht bewertet“, keine fünfte Dringlichkeitsstufe. `UrgencySource`: `SYSTEM`, `STAFF` → System, Immobilienverwaltung; ohne wirksame Bewertung bleibt die Quelle leer. `PublicAuthor`: `TENANT`, `STAFF`, `SYSTEM` → Mieter, Immobilienverwaltung, System. Chat B bildet intern vorhandene Triage-Enums auf diese Contract-Werte ab; keine zweite fachliche Bewertungslogik.

### Autorisierung pro Methode

| Methode | Dienstprüfung | Ergebnis |
| --- | --- | --- |
| `readByToken` | Token gültig, nicht ersetzt/widerrufen; Fall wird nur aus Token ermittelt | Mieterprojektion; keine Sitzung nötig |
| `readForTenant` | Aktives verifiziertes Mieterkonto; aktuelle Konto-Adresse entspricht aktueller Fall-Kontaktadresse | Mieterprojektion |
| `readForStaff` | Aktives Konto mit Mitarbeiterrolle | Mitarbeiterprojektion |
| `readStatusForMcp` | Aktives Mieterkonto, passende gültige Delegation für Client und Leserecht, aktuelle Adresszuordnung | Genau Case-ID, Status und wirksame Dringlichkeit |

Für Token, fremden Fall und nicht vorhandenen Fall wird kein ungeschützter Rohdatenabruf vorgeschaltet. Fremde und gültig formatierte unbekannte Case-ID sind nach aussen nicht unterscheidbar. Ein Konto erhält seine früheren Gastfälle über die verifizierte Adresszuordnung, nicht allein über eine beim Anlegen gespeicherte Benutzer-ID.

Case-IDs werden nie getrimmt oder in Gross-/Kleinschreibung verändert. Web verwendet die vorhandene undurchsichtige nichtleere ID; eine unbekannte oder leere ID ist neutral nicht verfügbar. MCP behält seine vorhandene Argumentgrenze `^[A-Za-z0-9_-]{1,80}$`. Chat B erzeugt neue Case-IDs, die in diese Grenze passen; ein sichtbares Nummernformat wird dadurch nicht festgelegt.

Alle Abrufe funktionieren auch bei „Eingegangen“, „Abgeschlossen“ und Camunda-Ausfall. Sie starten keinen Prozess, erhöhen keine Fallversion und öffnen keinen Fall wieder. Sicherheits-Audit ist zulässig und für MCP erforderlich; es enthält keine Falltexte, Kontaktadressen oder Geheimnisse.

Der MCP-Adapter serialisiert exakt `caseId`, `status`, `urgency` mit den vereinbarten deutschen Werten. Er serialisiert nicht automatisch das gesamte Java-Record oder seine Enum-Namen.

## 6. Fehlervertrag und Protokollabbildung

```java
public enum CaseErrorCode {
    INVALID_INPUT,
    UNAUTHENTICATED,
    FORBIDDEN,
    CASE_NOT_AVAILABLE,
    IDEMPOTENCY_CONFLICT,
    REPLAY_ACCESS_UNAVAILABLE,
    STORAGE_UNAVAILABLE,
    INTERNAL_FAILURE
}
```

`CaseServiceException extends RuntimeException` hat den öffentlichen Konstruktor `(CaseErrorCode code, Map<String, String> fieldErrors)` und die Accessors `code()` und `fieldErrors()`. Field-Error-Werte sind stabile Codes wie `REQUIRED`, `TOO_LONG`, `INVALID`; Schlüssel entsprechen `title`, `unit`, `description`, `email`, `operation`. Keine abgelehnten Werte oder SQL-/Providertexte in öffentlichen Fehlermeldungen. Implementierungen dürfen unerwartete technische Ursachen intern korrelierbar protokollieren, ohne sensible Parameter.

| Fall | HTTP-Anlage | HTTP-Lesen / MCP |
| --- | --- | --- |
| `INVALID_INPUT` | 400, Formular mit Eingaben und gleicher Operation | Web: malformed Case-ID/Token neutral 404; MCP: ungültige Argumente als Toolfehler ohne Fallzugriff |
| `UNAUTHENTICATED` | 401 im Kontoformular | Konto 401; Mitarbeiter-Navigation gemäss OpenAPI zur Anmeldung, Hintergrundabruf 401; MCP-Transport weist fehlendes/ungültiges Token vor Toolaufruf ab |
| `FORBIDDEN` | Fehlender CSRF: Adapter liefert 403 ohne Service-Aufruf | Falsche Rolle 403; MCP fehlendes delegiertes Recht gemäss Transport-/Toolgrenze ablehnen |
| `CASE_NOT_AVAILABLE` | Nicht regulärer Anlageausgang | 404 bzw. neutrales MCP-`isError`; keine Unterscheidung fremd/unbekannt |
| `IDEMPOTENCY_CONFLICT` | 409 | Kein Fehler eines reinen Leseaufrufs |
| `REPLAY_ACCESS_UNAVAILABLE` | 409 gemäss Abschnitt 4 | Kein Ersatzlink |
| `STORAGE_UNAVAILABLE` | 503, Annahme nicht bestätigen | Gemäss bestehendem Lesevertrag 500 bzw. neutraler MCP-Toolfehler, nicht als 404 verschleiern |
| `INTERNAL_FAILURE` | 500 | 500 / neutraler MCP-Toolfehler |

Rate Limits und CSRF sind zusätzliche Adapter-/Access-Prüfungen. 429 bleibt gemäss OpenAPI erhalten; Schwellen sind konfigurierbar und separat festzulegen, nicht willkürlich in den Fallservice einbauen. Unbekannte Exceptions fängt die zentrale Adapter-Fehlerbehandlung neutral ab.

Anlageerfolg: 303 mit `Location: /mieter/fall/zugang/{token}`, `Cache-Control: no-store`, `Referrer-Policy: no-referrer`. Token-Lesen bleibt direkt unter dieser URL. Keine neue REST-Route und kein 201-JSON-Ersatz. Schreibformulare für Nachrichten/Aktionen gehören nicht zu diesem ersten Leseabschnitt.

## 7. Tests als gemeinsame Abnahme

### Chat A – Adaptertests mit Testimplementierung der Schnittstellen

Stufe 1: A1, Gastteil von A3 und Token-/HTML-Teil von A4. Konto-/MCP-Prüfungen erst nach dem Access-Gate.

A1. Formularfelder korrekt an Command übergeben; CSRF/Sitzung prüfen, keine Konto-ID/E-Mail-Identität aus versteckten Feldern übernehmen.

A2. Gast, gültiger Mieter, manipulierte `accountSubmission`/E-Mail und abgelaufene Anmeldung getrennt prüfen. Konto-E-Mail darf vom mitgesendeten Gastwert nicht beeinflusst werden.

A3. Service-Erfolg wird zu 303 mit korrekten Schutzheadern; jedes dokumentierte Fehlerergebnis wird richtig abgebildet und behält bei Korrektur dieselbe Operation.

A4. Token-/Konto-/Mitarbeiter-Detailroute ruft nur die passende Lesemethode auf. HTML escaped Texte; interne Daten fehlen vollständig in Mieterantworten.

A5. MCP gibt genau drei Felder aus; unbekannter und fremder Fall ergeben dieselbe neutrale Rückmeldung; Token-/Identitätsprüfung läuft auch bei direktem HTTP-Aufruf.

### Chat B – Fachservice-/PostgreSQL-Tests ohne Controller

Stufe 1: B1/B2/B4 und Gast-/Tokenanteile von B5/B6. Konto-/Delegationsprüfungen erst nach dem Access-Gate.

B1. Anlage persistiert Fall, ursprüngliche Mitteilung, Zugang und Folgeaufträge atomar. Fehler mitten in der Transaktion hinterlässt keinen Teilbestand.

B2. Identischer Retry, geänderter Inhalt, parallele identische Requests und gleicher Schlüssel in verschiedenen Kontexten. Je berechtigter identischer Anlage höchstens ein Fall und ein logischer Folgeauftrag pro Typ.

B3. Aktuelle Konto-Adresse serverseitig laden, fremde/gesperrte Identität und geänderte Adresse prüfen; gültiger Fall-Link vermittelt keine Kontorechte.

B4. Tokenzugriff bei Widerruf/Ersatz abweisen; Retry darf keinen neu vergebenen Link preisgeben. Testberichte/Logs enthalten keine Geheimnisse.

B5. Fall-/Kontozuordnung übersteht Neustart; Gast-Retry bleibt an die ursprüngliche gültige Sitzung gebunden. Datenbanktest mit PostgreSQL, nicht nur H2.

B6. Alle Lesemethoden bei Camunda-Ausfall und in Eingegangen/Abgeschlossen; kein Statuswechsel, keine erhöhte Version. Interne Memos in Tenant-DTOs ausgeschlossen.

B7. MCP-Zugriff prüft Konto und Delegation erneut, einschliesslich Widerruf und Clientzuordnung; Tests direkt am Service, zusätzlich zum Adaptertest.

### Gemeinsame Tests nach Zusammenführung

J0. Erste Stufe: Gastformular, PostgreSQL-Commit, Token-Lesen, Neustart und Retry im noch gültigen Gastkontext. Dies ist die erste gemeinsame Abnahme, noch kein Konto-/MCP-Nachweis. J2/J4 vollständig erst nach dem Access-Gate.

J1. Reales Formular → Intake → Case Processing → PostgreSQL → 303 → Token-Detailseite.

J2. Erreichbare DB, ausgeschaltetes Camunda/Modell: zehn Gäste und zehn verifizierte Mieter über zehn Minuten; 20 erfolgreiche 303. Nach App-Neustart je ein Fall und ein offener Startauftrag. Konten erneut anmelden, wenn Sitzungen nicht persistent sind.

J3. Verlorene HTTP-Antwort und Retry mit gleichem Kontext/Operation; geänderter Inhalt ergibt 409, kein zweiter Fall.

J4. Zwei echte Konten: jedes sieht nur eigene Fälle; fremder und unbekannter Fall liefern denselben Fehler. MCP-Wiretest mit demselben Szenario, fehlendem Token und Widerruf.

J5. Relevante vorhandene Browser-/Unit-Regressionen; SMTP-End-to-End nutzt isoliertes Test-Mailpit. Neue reguläre Tests erzeugen ihre Daten selbst, statt `REQ-2026-001` vorauszusetzen.

Die Abnahmetests A1–J5 sind noch auszuführen. Die bereits ausgeführten Java-Vertragstests aus Abschnitt 11 prüfen ausschliesslich die Datentypen. Test-Doubles sind nur für isolierte Adaptertests; sie gelten nicht als Persistenz-/Sicherheitsnachweis des vollständigen Ablaufs.

## 8. Startprotokoll für parallele Arbeit

1. Beide Chats lesen diesen identischen Stand und verwenden getrennte Worktrees/Branches.
2. Chat B erstellt ausschliesslich die oben vereinbarten Java-Contracts als ersten kleinen Commit. Beide Chats gleichen Methodensignaturen, DTO-Felder und Fehler ab; Chat A übernimmt den Commit. Git-Schreibaktionen erfolgen nur im jeweils autorisierten Rahmen. Ohne Commit kann derselbe Satz Contract-Dateien explizit übernommen werden; keine unabhängigen Varianten pflegen.
3. Chat A darf vorher Adaptertests und Mapping vorbereiten; produktive Imports erst gegen den gemeinsamen Stand finalisieren.
4. Danach parallele Implementierung. Chat A besitzt HTTP-/MCP-Adapter, Templates und Protokolltests; Chat B besitzt Contracts, Services, Entities, Repositories, Flyway, reguläres Profil und Compose. Änderungen an `pom.xml` koordiniert ausschliesslich Chat B.
5. Vertragsanpassungen zuerst als konkrete Änderung abstimmen, dann auf beiden Branches übernehmen. Keine stillen Signaturänderungen.
6. Früh zusammenführen, sobald die Gastanlage und Token-Leseseite funktionieren. Danach das Access-Gate schliessen und Konto-/MCP-Zugang integrieren. Beide Agenten melden ausdrücklich offene Abhängigkeiten statt einen Stub als fertiges Backend auszugeben.

Der aktuelle MCP-Zwischenstand liegt teilweise gestagt/uncommittet im ursprünglichen Arbeitsbaum. Er erscheint nicht automatisch im anderen Worktree. Vor Beginn gemeinsam sichern/übertragen und einen Eigentümer bestimmen. Keine fremden Änderungen pauschal committen oder verwerfen.

### Abgestimmtes Laufzeitprofil

Das reguläre Profil für Stufe 1 heisst `propertyflow-backend`. Neue Adapter und Backend-Beans verwenden die Profilbedingung `propertyflow-backend & !frontend-demo`, damit bei versehentlicher gemeinsamer Aktivierung keine konkurrierenden Demo-/Produktivrouten entstehen. Dies legt den Profilnamen fest, implementiert aber noch keine Beans oder Konfiguration. Chat A besitzt Controller/Templates und die OpenAPI-Ergänzung für 409; Chat B ändert diese Dateien nicht.

## 9. Kopierauftrag für Chat A – Controller und Adapter

> Implementiere im eigenen Worktree die HTTP- und MCP-Adapter für den ersten Abschnitt aus `docs/architecture/service-vertrag-fallanlage-und-lesen.md`. Lies zuerst AGENTS.md und die Projektregeln. Verwende exakt die von Chat B bereitgestellten Java-Contracts; erfinde keine abweichenden Signaturen. Du besitzt die Anlage-/Lesecontroller, Identity-Mapping aus geprüfter Sitzung bzw. MCP-Authentifizierung, Templates/Ansichtsmodelle und Adaptertests. Halte bestehende URLs, Formularfelder, Statuscodes und Schutzheader ein. Übernimm die abgestimmte 409-Ergänzung für widerrufenen Retry-Zugang und führe OpenAPI gezielt nach. Implementiere jetzt ausschliesslich Stufe 1: Gastanlage und Token-Lesen. Konto-/Mitarbeiter-Lesen und MCP erst nach dem gemeinsamen Access-Gate; keine eigene Identitätsbrücke erfinden. Fachregeln und endgültige Zugriffsprüfung bleiben in den Services. Nutze für isolierte Tests kontrollierte Testimplementierungen, aber keine Fake-Produktivservices. Erzeuge keine Entities, Repositories, Migrationen oder BPMN-Logik; ändere pom.xml nur über Koordination mit Chat B. Bestehende Demo nicht löschen und nicht dieselben Routen gleichzeitig im regulären Profil registrieren. Nachrichten, Listen, Schreibaktionen und OAuth-Neuentwicklung gehören nicht zu diesem Abschnitt. Führe die für Stufe 1 bezeichneten Adaptertests aus und bereite J0/J1/J3 vor. Konto-/MCP-Tests als ausstehend kennzeichnen. Berichte fertige Adapter, Testnachweise und offene Abhängigkeiten. Bestehende fremde Änderungen bewahren; nicht pushen oder pauschal committen.

## 10. Kopierauftrag für Chat B – Services und Persistenz

> Implementiere im eigenen Worktree das Backend für den ersten Abschnitt aus `docs/architecture/service-vertrag-fallanlage-und-lesen.md`, abgestimmt mit `docs/architecture/backend-umsetzungsplan.md`. Lies zuerst AGENTS.md und die Projektregeln. Erstelle zuerst die vereinbarten Java-Interfaces, Kontexttypen, DTOs, Enums und Exceptions und stelle denselben Stand Chat A bereit; ändere diese danach nur koordiniert. Implementiere jetzt Stufe 1: Intake-Fassade, Gastanlage und Token-Lesen mit service-seitiger Berechtigungsprüfung. Die übrigen Leseinterfaces erst nach dem Access-Gate implementieren und registrieren. Du besitzt Services, Access-Anbindung, Entities, Repositories, Flyway-Migrationen, PostgreSQL-Konfiguration, reguläres Profil und die nötigen Build-Abhängigkeiten. Persistiere Fall, Wiederholungszuordnung, Token-Hash/verschlüsselte Versandkopie und erforderliche Folgeaufträge atomar. Verwende vorhandene Validierungsbausteine. Prüfe die für Stufe 1 bezeichneten Backendtests mit PostgreSQL; keine zusätzliche In-Memory-Produktivlösung. Beginne mit Gastanlage/Token-Lesen und stelle diese früh für J1 bereit. Den Access-Vertrag für Stufe 2 zuerst mit Chat A abstimmen; solange reguläre Authentifizierung fehlt, keinen unsicheren Ersatz als produktiv ausgeben. Der Camunda-Startauftrag bleibt zunächst offen. Implementiere noch keinen eigenen BPMN-Ablauf und entscheide keinen ersten aktiven Status; das folgt nach gemeinsamer Prozessfreigabe. Ändere Controller, Templates und MCP-Transport nicht parallel zu Chat A. Keine Demo-Dateien löschen, keine neuen Microservices. Berichte Migrationen, Tests und noch offene Integrationen. Bestehende fremde Änderungen bewahren; nicht pushen oder pauschal committen.

## 11. Implementierter Vertragsstand und Übergabe an Chat A

**Stand 14.11.2026: bereit für Adapterentwicklung der Stufe 1.** Angelegt sind 22 öffentliche Java-Vertragsdateien; keine Service-Beans, Controller, Entities, Migrationen oder Camunda-Worker. Die Signaturen aus diesem Dokument sind unverändert. Die Stufe-2-Interfaces sind nur Typdefinitionen, keine aktivierten Zugänge.

| Verzeichnis unter `src/main/java/com/pertegato/propertyflow/` | Neu angelegte Dateien |
| --- | --- |
| `access/contract/` | `SubmissionContext.java`, `WebAccountContext.java`, `McpAccountContext.java` |
| `intake/contract/` | `CaseSubmission.java` |
| `caseprocessing/contract/` | `CaseCreation.java`, `CaseReading.java`, `AccountCaseReading.java`, `McpCaseReading.java`, `CreateCaseCommand.java`, `CaseAccepted.java`, `CaseStatusView.java`, `TenantCaseView.java`, `StaffCaseView.java`, `PublicMessage.java`, `UrgencyChange.java`, `InternalMemo.java`, `CaseStatus.java`, `Urgency.java`, `UrgencySource.java`, `PublicAuthor.java`, `CaseErrorCode.java`, `CaseServiceException.java` |

Bestehende `EmailAddress.java` und `CaseDescription.java` wurden nicht verändert. Command-Felder bleiben untrusted und dürfen zur späteren Service-Validierung null enthalten. Ergebnisse/Kontexte verlangen nicht-null Pflichtreferenzen. Optionale Ergebniswerte verwenden nicht-null `Optional`. Listen und Fehlerfelder sind defensive, unveränderliche Kopien. `CreateCaseCommand.toString()` und der Tokenanteil von `CaseAccepted.toString()` sind redigiert; dies ersetzt keine sichere Protokollierung an den Adaptern.

**Ausgeführt:** `./mvnw.cmd -B -Dtest=CaseContractTest test` mit lokalem JDK 25, Compilerziel Java 23. Produktions- und Testquellen wurden kompiliert. Sechs Vertragstests erfolgreich, keine Fehler und keine übersprungenen Tests. Geprüft wurden Token-/Eingaberedaktion, null Gastadresse im Kontocommand, unveränderliche Mieter-/Mitarbeiterlisten sowie Fehlerfeld-Snapshots und neutrale Exceptionmeldungen. Es wurde nicht die gesamte Testsuite ausgeführt.

Testdatei: [CaseContractTest.java](../../src/test/java/com/pertegato/propertyflow/caseprocessing/contract/CaseContractTest.java).

Chat A kann `CaseSubmission` und `CaseReading` jetzt in seine Controller injizieren und für Adaptertests ersetzen. Keine produktiven Fake-Beans als Ersatz für die noch folgende Persistenz registrieren. Ein vollständiger Start des regulären Profils wird erst mit den Implementierungen aus Chat B möglich.

Die Dateien sind noch nicht committet. Ein anderer Worktree erhält sie nicht automatisch. Nur die oben aufgeführten neuen Dateien, den Vertragstest und dieses Dokument gemeinsam übernehmen; keine vollständigen Verzeichnisse mit fremden Änderungen überschreiben. Wenn beide Chats denselben Arbeitsbaum verwenden, entfällt das Kopieren; Dateibesitz und Maven-/Build-Läufe müssen trotzdem koordiniert werden.

## Quellen und Nachweise zum Ausgangspunkt

- [Backend-Umsetzungsplan](backend-umsetzungsplan.md)
- [Modulvertrag Fallanlage](module-structure.md#fallanlage--intake-und-case-processing)
- [OpenAPI](openapi/fallannahme.yaml)
- [Fallverwaltung](../specifications/fallverwaltung.md)
- [Fallzugriff](../specifications/fallzugriff-und-sicherheit.md)
- [MCP Guidelines](../specifications/MCP_GUIDELINES.md)
- [SS-004](schnittstellenentwurf-block-3.md#ss-004--mcp-werkzeug-fallstand-abrufen)
- [Aktuelles Identity-Mapping](../../src/main/java/com/pertegato/propertyflow/access/web/CurrentAccount.java)
- [Demo-Fallmodell](../../src/main/java/com/pertegato/propertyflow/caseprocessing/demo/DemoCases.java)
- [MCP-Werkzeug](../../src/main/java/com/pertegato/propertyflow/caseprocessing/mcp/FallstandTool.java)
