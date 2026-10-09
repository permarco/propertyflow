# Screen 01 – Mieter-Fallansicht

**Projekt:** PropertyFlow – KI-gestützte Triage und Bearbeitung von Mieteranliegen  
**Status:** Fachliche Spezifikation und technischer Wireframe für Block 2 (Entwurf zur Umsetzung)  
**Vorgesehener Repository-Pfad:** `docs/frontend/screen-01-mieter-fallansicht.md`  
**Zielgruppe:** Mieterinnen und Mieter  
**Darstellung:** Server-Side Rendering (Thymeleaf) mit gezieltem Vanilla JavaScript  
**Workflow-Orchestrierung:** Camunda 8

## 1. Zweck und Abgrenzung

Diese Seite ist der **einzige Einstieg für Mieterinnen und Mieter** in die Erfassung und Kommunikation zu einem Anliegen. Es gibt **kein Mieterportal, kein Benutzerkonto und keinen Login**. Ein neuer Fall wird zunächst über ein Formular erfasst. Danach bleibt der Benutzer auf derselben funktionalen Seite und sieht den Fall sowie seine fortlaufende Kommunikation. Für den späteren erneuten Zugriff erhält er per E-Mail einen persönlichen Fall-Link.

Jeder Fall wird beim **ersten erfolgreichen Absenden** dauerhaft angelegt und eine zugehörige **Camunda-8-Prozessinstanz** wird gestartet. Camunda steuert die weiteren Prozessschritte: KI-Abklärung, Rückfragen, Wartezustände, Übergabe an das Backoffice sowie den Abschluss. Die Seite bildet den aktuellen fachlichen Status und die Kommunikation ab.

**Nicht Teil dieses Bildschirms:** Mieterregistrierung, Fallübersicht über mehrere Fälle, Bearbeitung fremder Fälle, interne Backoffice-Aufgaben oder direkte Bedienung von Camunda durch den Browser.

### Render-Strategie (ein Satz für die FFHS-Abgabe)

**SSR mit gezielter clientseitiger Interaktivität:** Thymeleaf rendert Erfassungsformular, Falldaten und Verlauf serverseitig; Vanilla JavaScript ergänzt nur die dynamische Aktualisierung und das abbrechbare Streaming der KI-Antwort, weil dafür kein vollständiges CSR-Frontend benötigt wird.

Diese Entscheidung folgt `docs/architecture/adr/ADR-003-praesentationsschicht.md` und `docs/project-context.md`.

## 2. Eine Seite mit drei Zuständen

| Zustand | Aufruf / Auslöser | Sichtbarer Inhalt | Zulässige Aktion |
|---|---|---|---|
| **Neuer Fall** | Neue Erfassungsseite, noch keine Case-ID | Formular mit Pflichtfeldern | Einmalig einen neuen Fall absenden |
| **Aktiver Fall** | Nach erfolgreichem Erstabsenden oder über persönlichen Fall-Link | Case-ID, Status, Betreff, aufklappbares ursprüngliches Anliegen, chronologische Nachrichten, Eingabe für neue Nachricht | Weitere Nachrichten jederzeit hinzufügen, solange der Fall aktiv ist |
| **Abgeschlossener Fall** | Persönlicher Fall-Link, nachdem Camunda den Fall abgeschlossen hat | Case-ID, Abschlussstatus, ursprüngliches Anliegen und gesamte bisherige Historie | Ausschliesslich lesen; keine neuen Nachrichten durch irgendeinen Absender |

**Direkter Übergang:** Nach dem erfolgreichen Erstabsenden gelangt der Mieter **ohne erneute Anmeldung und ohne zuerst die E-Mail öffnen zu müssen** unmittelbar zur aktiven Fallansicht. Der persönliche Link wird zusätzlich per E-Mail zugestellt. Die konkrete technische Umsetzung der sicheren Übergabe (z. B. HTTP-Redirect mit einmalig etabliertem Zugriffskontext) wird im Service-Block festgelegt.

**Unveränderlichkeit:** Sobald ein Fall angelegt ist, sind die beim Erstellen erfassten Ursprungsdaten für den Mieter **nicht mehr bearbeitbar**. Ergänzungen werden ausschliesslich als neue Nachrichten protokolliert; ältere Nachrichten werden nicht überschrieben.

## 3. Zustand A – Neuer Fall

### 3.1 Eingabefelder

| Feld | Inhalt und Zweck | Pflicht | Validierung / Verhalten |
|---|---|---|---|
| **Betreff** | Kurze, aussagekräftige Bezeichnung des Anliegens, z. B. «Heizung funktioniert nicht». | Ja | Nicht leer oder nur Leerzeichen; Maximallänge als Implementierungsregel festlegen (Vorschlag: 150 Zeichen). |
| **Beschreibung** | Freitext mit Fehlerbild, Beobachtungen, Zeitpunkt und gegebenenfalls Auswirkungen, z. B. «Seit gestern sind alle Heizkörper kalt». | Ja | Nicht leer oder nur Leerzeichen; serverseitige Längenbegrenzung (Vorschlag: 5'000 Zeichen); als **Text**, niemals ungeprüftes HTML behandeln. |
| **Objekt-/Wohnungsreferenz** | Identifikation der betroffenen Liegenschaft und Wohnung, z. B. «Musterstrasse 12, Wohnung 4». | Ja | Nicht leer; für Block 2 Freitext mit Mock-Wert, spätere Prüfung/Zuordnung durch Backend. |
| **E-Mail-Adresse** | Kontaktadresse des Mieters für die Zustellung des persönlichen Fall-Links und spätere Benachrichtigungen. | Ja | Erforderlich; syntaktisch gültige E-Mail; serverseitig prüfen; keine Offenlegung anderer Fälle über diese Adresse. |

**Absendeaktion:** Schaltfläche **«Anliegen absenden»**. Sie ist während des laufenden Absendens gegen wiederholtes Anklicken gesperrt und der Fortschritt wird sichtbar angezeigt. Dies ergänzt, ersetzt aber **nicht** den serverseitigen Schutz gegen Doppelanlage.

### 3.2 Ablauf beim ersten Absenden

1. Mieter füllt die vier Felder aus und klickt auf **«Anliegen absenden»**.
2. Der Server validiert alle Angaben erneut und zeigt bei Fehlern feldbezogene Rückmeldungen; bereits eingegebene Werte bleiben erhalten.
3. PropertyFlow nimmt das Anliegen mit eindeutiger fachlicher **Case-ID** an und speichert die Ursprungsdaten dauerhaft.
4. Für diesen Fall wird genau **eine zugehörige Camunda-8-Prozessinstanz** gestartet (doppelte Requests und Wiederholungen werden idempotent behandelt).
5. PropertyFlow erstellt einen persönlichen, nicht erratbaren Zugriffsnachweis und veranlasst die E-Mail mit dem Fall-Link.
6. Die Anwendung zeigt **auf derselben funktionalen Seite** die aktive Fallansicht; ursprüngliche Felder sind fortan schreibgeschützt.
7. Der Camunda-Prozess steuert die initiale KI-Analyse und gegebenenfalls eine erste Rückfrage. Der Dialog kann unmittelbar in der Fallansicht erscheinen.

**Zuverlässigkeit:** Zwischen Datenbankspeicherung und Camunda-Prozessstart besteht keine gemeinsame lokale Transaktion. Der Backend-Entwurf muss fehlgeschlagene Prozessstarts erkennen und zuverlässig wiederholen, ohne eine zweite fachliche Fallanlage oder zweite aktive Prozessinstanz zu erzeugen. Die genaue technische Strategie wird in Block 3/4 festgelegt; der Frontend-Mock muss sie nicht implementieren.

### 3.3 Ergebnis und Fehlermeldungen

- **Erfolg:** Fallansicht mit Case-ID und klar erkennbarem aktuellem Status.
- **Feldfehler:** Betroffenes Feld markieren und verständlichen Hinweis anzeigen; Eingaben beibehalten.
- **Technischer Fehler vor erfolgreicher Annahme:** Keine falsche Erfolgsmeldung; sicheren erneuten Versuch ermöglichen.
- **KI nicht erreichbar:** Erfasster Fall bleibt bestehen und kann weiterbearbeitet werden. Der Mieter erhält einen verständlichen Status statt einer verlorenen Eingabe.
- **E-Mail-Zustellung verzögert/fehlgeschlagen:** Der Fall darf dadurch nicht verloren gehen; Zustand/Fehler wird im Backend nachvollziehbar behandelt.

## 4. Zustand B – Aktiver Fall

### 4.1 Kopfbereich

| Element | Inhalt / Verhalten |
|---|---|
| **Case-ID** | Lesbare Referenz, z. B. `REQ-2026-001`; dient der Wiedererkennung, **nicht** allein als Zugriffsberechtigung. |
| **Betreff** | Ursprünglicher Betreff, schreibgeschützt. |
| **Fallstatus** | Fachlich verständlicher, vom Camunda-Ablauf abgeleiteter Status, z. B. «In Abklärung», «Wartet auf Rückmeldung», «Beim Backoffice». |
| **Ursprüngliches Anliegen anzeigen** | Standardmässig **eingeklappter** Bereich; beim Aufklappen werden alle vier ursprünglichen Felder **schreibgeschützt** angezeigt. |

Der aufklappbare Bereich wird vorzugsweise mit den nativen HTML-Elementen `<details>` und `<summary>` umgesetzt; hierfür ist kein JavaScript erforderlich. Es gibt **keine Bearbeiten-Funktion** für diese Daten.

### 4.2 Kommunikationshistorie

Die Historie enthält **alle für den Mieter sichtbaren Nachrichten** dieses Falls in chronologischer Reihenfolge und bleibt über spätere Aufrufe erhalten.

Jeder Nachrichteneintrag zeigt mindestens:

- **Absenderrolle:** «Mieter», «KI-Assistent» beziehungsweise «System», oder «Immobilienverwaltung».
- **Zeitpunkt:** Erstellungszeit beziehungsweise Versandzeit in einer verständlichen Darstellung.
- **Inhalt:** Die unveränderte inhaltliche Aussage als sicher ausgegebener Text.

**Absender und Ausrichtung im Wireframe:** Nachrichten des Mieters rechts; Nachrichten von System/KI und Mitarbeitenden links. Der Absender wird zusätzlich als Text bezeichnet, nicht nur über Farbe oder Ausrichtung kenntlich gemacht.

Automatische Antworten können durch Camunda-Service-Tasks und PropertyFlow-Worker ausgelöst werden; manuelle Antworten entstehen durch Backoffice-Mitarbeitende. Beide werden im gleichen Verlauf angezeigt. Interne Notizen, technische Prozessvariablen, Modell-Prompts und vertrauliche Backoffice-Daten gehören **nicht** in die Mieteransicht.

**Ursprungsbeschreibung im Verlauf:** Die ursprüngliche Beschreibung erscheint als erste Mieter-Nachricht oder als eindeutig referenzierter Ersteintrag. Es darf dabei keine fachlich doppelte Nachricht entstehen. Die genaue Datenmodellierung wird später festgelegt.

### 4.3 Neue Nachricht hinzufügen

| Element | Zweck / Verhalten |
|---|---|
| **Nachricht (Mehrzeilentext)** | Mieter ergänzt jederzeit während eines **aktiven** Falls weitere Informationen oder beantwortet eine Rückfrage. |
| **Nachricht senden** | Prüft die Eingabe und sendet sie dem bestehenden Fall zu; **kein neuer Fall und keine neue Prozessinstanz**. |
| **Status-/Fehlerhinweis** | Zeigt an, ob die Nachricht versendet wird, angenommen wurde oder ein Fehler aufgetreten ist. |

Validierung: Eine Nachricht darf nicht leer oder nur aus Leerzeichen bestehen; Maximallänge festlegen (Vorschlag: 5'000 Zeichen); sicher als Text anzeigen. Eine gesendete Nachricht ist nicht nachträglich editierbar. Wiederholte Sendeversuche dürfen dieselbe Nachricht nicht mehrfach speichern.

### 4.4 Ablauf beim Senden einer weiteren Nachricht

1. Mieter öffnet den Fall über den persönlichen Link oder arbeitet nach der Erfassung direkt weiter.
2. PropertyFlow prüft bei jedem Zugriff die Berechtigung anhand des Zugriffsnachweises.
3. Mieter schreibt eine neue Nachricht und klickt auf **«Nachricht senden»**.
4. Der Server prüft erneut Zugriffsberechtigung, Eingabevalidität, Idempotenz und ob der Fall **noch aktiv** ist.
5. Die Nachricht wird dem bestehenden Fall zugeordnet und chronologisch gespeichert.
6. Die Information wird dem Camunda-Prozess entsprechend dem **aktuellen BPMN-Schritt** zugeführt (z. B. Message-Korrelation bei einer wartenden Rückfrage oder vorgesehene weitere Verarbeitung). Eine Nachricht löst **nicht** zwangsläufig jedes Mal einen neuen Prozessschritt aus.
7. Der Mieter sieht die übernommene Nachricht; eine spätere KI- oder Backoffice-Antwort wird im gleichen Verlauf ergänzt.

### 4.5 KI-Antworten und Streaming (Block 2)

Für die Vorgabe des Frontend-Blocks wird **eine simulierte KI-Antwort** nach und nach im Nachrichtenverlauf ausgegeben. Für Block 2 genügt ein lokaler Mock ohne echtes LLM und ohne Camunda-Laufzeit-Anbindung.

Die clientseitige Komponente unterscheidet mindestens folgende Zustände:

| Zustand | Darstellung |
|---|---|
| `idle` | Kein Stream aktiv. |
| `waiting` | Verarbeitung läuft, aber noch kein Text empfangen. |
| `streaming` | Neue Textteile erscheinen fortlaufend. |
| `completed` | Vollständige Antwort erfolgreich empfangen. |
| `aborted` | Ausgabe wurde wirksam abgebrochen; **keine** späteren Textteile dürfen mehr erscheinen. |
| `error` | Verständliche Fehlermeldung; keine falsche Darstellung als erfolgreich abgeschlossen. |

Die Ausgabe erfolgt über sichere Textmechanismen, z. B. `textContent`, **nicht** mit ungeprüftem `innerHTML`. Der Abbruch muss den laufenden Mock-Stream beziehungsweise die laufende Anfrage tatsächlich beenden (nicht nur den Button oder das Statuslabel ändern). Ein gezielt simulierbarer Fehlerpfad ist für Tests vorzusehen.

**Fachliche Abgrenzung:** Der Abbruch einer laufenden **Darstellung/Anfrage** ist nicht automatisch der Abschluss oder Abbruch der Camunda-Prozessinstanz. Dies muss im späteren echten Integrationsvertrag ausdrücklich getrennt werden. Nur vollständig angenommene Systemantworten zählen als verbindliche Nachrichten der Historie; unvollständige Streaming-Ausgaben dürfen nicht versehentlich als abgeschlossene fachliche Antwort gespeichert werden.

## 5. Zustand C – Abgeschlossener Fall

Sobald der zugehörige Camunda-8-Prozess den fachlich vorgesehenen **Abschluss** erreicht hat, zeigt PropertyFlow den Status **«Abgeschlossen»**.

- Betreff, Case-ID, ursprüngliche Angaben und die vollständige Mieter-sichtbare Nachrichtenhistorie bleiben einsehbar.
- Der Bereich «Ursprüngliches Anliegen anzeigen» bleibt **standardmässig eingeklappt** und jederzeit aufklappbar.
- **Kein Absender** darf weitere Nachrichten erzeugen: weder Mieter noch System/KI noch Backoffice-Mitarbeitende.
- Der Texteingabebereich und der Senden-Button werden durch folgenden Hinweis ersetzt:

> Dieser Fall ist abgeschlossen. Es können keine weiteren Nachrichten hinzugefügt werden.

**Verbindliche Serverregel:** Die Sperre darf nicht nur von einer deaktivierten Oberfläche abhängen. Das Backend muss alle Nachrichtenschreiboperationen auch bei direktem HTTP-Aufruf, verspäteten KI-Antworten, konkurrierenden Requests und wiederholten Camunda-Worker-Ausführungen verlässlich zurückweisen. Der Übergang zum Abschluss und das Prüfen/Speichern einer Nachricht benötigen eine konsistente Synchronisationsregel, damit keine Nachricht «zwischen» Statusprüfung und Abschluss unzulässig angenommen wird.

**Prozessstatus:** Camunda besitzt den technischen Workflow-State; PropertyFlow besitzt und präsentiert die fachlichen Falldaten und den für die Benutzer bestimmten fachlichen Status. Der sichtbare Abschlussstatus muss mit dem massgeblichen Camunda-Prozessabschluss synchronisiert sein, ohne einen unabhängigen zweiten Workflow einzuführen.

## 6. Persönlicher Fall-Link und Datenschutz

**Beispiel einer fachlichen URL-Form (nicht als produktive Domain zu verstehen):**

`https://propertyflow.example/case/REQ-2026-001?token=<zufaelliger-zugriffsnachweis>`

- Die Case-ID ist eine Referenz, **kein Passwort**. Die Case-ID allein berechtigt zu keinem Zugriff.
- Der persönliche Zugriffsnachweis muss kryptografisch zufällig, ausreichend stark, nicht erratbar und serverseitig überprüfbar sein.
- Zugriff auf Falldaten und Nachrichtensenden erfordern die **serverseitige** Prüfung des gültigen Tokens und der Fallzuordnung.
- Zugriffstokens dürfen nicht im Klartext in Anwendungs- oder Webserver-Logs, Tracking-Diensten, Analytics, Fehlerberichten oder Referrer-Daten an Dritte gelangen. HTTPS, eine restriktive Referrer-Policy, geschützte Token-Speicherung und geeignete Cache-Regeln sind vorzusehen.
- Ein solcher Link ist wie ein Schlüssel zu behandeln: Wer ihn besitzt, könnte sonst Zugriff erhalten. Regeln zur Gültigkeit, Wiederherstellung und zum Widerruf werden im Sicherheitskonzept festgelegt.
- Der Link wird an die beim Erstellen angegebene E-Mail-Adresse gesendet und kann bei späterer Kommunikation erneut zugestellt werden.
- Nachrichten für fremde Case-IDs dürfen niemals durch Ändern der URL sichtbar werden.
- Bei geschlossenem Fall bleibt der Zugriff lesend möglich, sofern der persönliche Zugriffsnachweis gültig ist.

**Block-2-Grenze:** Die Sicherheit muss bereits im technischen Konzept dokumentiert sein. Eine echte Token-Ausgabe und ein echter E-Mail-Versand sind für die Frontend-Implementierung mit Mock-Daten noch nicht erforderlich. Die Mock-Ansicht darf aber keinen produktiven Sicherheitsmechanismus vortäuschen.

## 7. Camunda-8-Verantwortung und Integration

| Bestandteil | Verantwortung |
|---|---|
| **Thymeleaf-Seite + Vanilla JavaScript** | Formular, Nachrichtenverlauf, aufklappbare Ursprungsdaten, sichtbarer Status und Streaming-Darstellung |
| **PropertyFlow-Backend (Spring Boot)** | HTTP-Validierung, Autorisierung per persönlichem Zugriff, Fallanlage, Nachrichtenannahme, Anwendungslogik und Integrationsadapter |
| **PropertyFlow-Datenhaltung (PostgreSQL)** | Persistente fachliche Falldaten, Nachrichten, Absender, Zeitpunkte und fachlich relevante Historie |
| **Camunda 8** | Technische Prozessinstanz, Prozessposition, Message-Catch-/Wartezustände, Service Tasks, User Tasks, Gateways, Timer und Prozessabschluss |
| **KI-/RAG-Services** | Fachlich begrenzte Analysen, Rückfragen und Antworten; Aufruf über kontrollierte Worker/Services |
| **Backoffice** | Manuelle Prüfung, Kommunikation und erlaubte Entscheidungen in Camunda-gesteuerten Prozessschritten |

Der Browser ruft **nicht direkt Camunda** auf. Die Fachlogik bleibt im PropertyFlow-Backend. Camunda verwaltet technische Prozessvariablen möglichst referenzbasiert und nicht als vollständige Kopie sensibler Mieter- und Kommunikationsdaten.

**Regel zur KI-Autonomie:** Camunda kann eine automatische Antwort oder einen automatischen Abschluss nur entlang ausdrücklich modellierter und serverseitig geprüfter Geschäftsregeln ausführen. Für risikoreiche oder kritische Entscheidungen bleiben die in den bestehenden ADRs und Guardrails vorgesehenen menschlichen Prüfungen erhalten. Eine allgemeine Erlaubnis, jeden Fall durch die KI autonom zu schliessen, ist **nicht** beschlossen.

## 8. Technische Wireframes (Markdown / ASCII)

### 8.1 Neuer Fall – noch keine Case-ID

```text
┌──────────────────────────────────────────────────────────┐
│ PropertyFlow                                             │
├──────────────────────────────────────────────────────────┤
│ Neues Mieteranliegen                                     │
│                                                          │
│ Betreff *                                                │
│ ┌──────────────────────────────────────────────────────┐ │
│ │ Heizung funktioniert nicht                          │ │
│ └──────────────────────────────────────────────────────┘ │
│                                                          │
│ Objekt / Wohnung *                                       │
│ ┌──────────────────────────────────────────────────────┐ │
│ │ Musterstrasse 12, Wohnung 4                         │ │
│ └──────────────────────────────────────────────────────┘ │
│                                                          │
│ E-Mail-Adresse *                                         │
│ ┌──────────────────────────────────────────────────────┐ │
│ │ mieter@example.ch                                   │ │
│ └──────────────────────────────────────────────────────┘ │
│                                                          │
│ Beschreibung *                                           │
│ ┌──────────────────────────────────────────────────────┐ │
│ │ Seit gestern funktionieren alle Heizkörper nicht.  │ │
│ │                                                      │ │
│ └──────────────────────────────────────────────────────┘ │
│                                                          │
│ * Pflichtfelder                            [Absenden]    │
└──────────────────────────────────────────────────────────┘
```

**Aktion:** «Absenden» legt den Fall an, startet dessen Camunda-Prozess und öffnet unmittelbar die aktive Fallansicht.

### 8.2 Aktiver Fall – Kommunikation

```text
┌──────────────────────────────────────────────────────────┐
│ PropertyFlow                                             │
├──────────────────────────────────────────────────────────┤
│ REQ-2026-001                          [In Abklärung]     │
│ Heizung funktioniert nicht                               │
│                                                          │
│ ▸ Ursprüngliches Anliegen anzeigen                       │
│   (standardmässig eingeklappt; schreibgeschützt)         │
├──────────────────────────────────────────────────────────┤
│ Kommunikationsverlauf                                    │
│                                                          │
│                         ┌─────────────────────────────┐  │
│                         │ Mieter · 09:12              │  │
│                         │ Meine Heizung funktioniert │  │
│                         │ seit gestern nicht mehr.   │  │
│                         └─────────────────────────────┘  │
│                                                          │
│ ┌────────────────────────────────────┐                   │
│ │ KI-Assistent · 09:13               │                   │
│ │ Sind alle Heizkörper betroffen?    │                   │
│ └────────────────────────────────────┘                   │
│                                                          │
│                         ┌─────────────────────────────┐  │
│                         │ Mieter · 09:15              │  │
│                         │ Ja, sämtliche Heizkörper.   │  │
│                         └─────────────────────────────┘  │
│                                                          │
│ ┌───────────────────────────────────────────────┐        │
│ │ Immobilienverwaltung · 10:30                  │        │
│ │ Wir haben den Hauswart informiert.            │        │
│ └───────────────────────────────────────────────┘        │
│                                                          │
│ [KI antwortet …] / [Fehler] / [Abbrechen]                │
│ (nur wenn gerade eine KI-Streaming-Antwort läuft)        │
├──────────────────────────────────────────────────────────┤
│ Weitere Informationen hinzufügen                         │
│ ┌──────────────────────────────────────────────────────┐ │
│ │ Neue Nachricht schreiben ...                        │ │
│ │                                                      │ │
│ └──────────────────────────────────────────────────────┘ │
│                                         [Nachricht senden]│
└──────────────────────────────────────────────────────────┘
```

**Aktion:** Eine neue Nachricht ergänzt den bestehenden Fall, ohne die ursprünglichen Felder zu verändern oder einen neuen Camunda-Prozess zu eröffnen.

**Aufgeklappter Bereich (Ausschnitt):**

```text
│ ▾ Ursprüngliches Anliegen ausblenden                     │
│   Betreff:       Heizung funktioniert nicht              │
│   Objekt/Wohnung: Musterstrasse 12, Wohnung 4            │
│   E-Mail:        mieter@example.ch                       │
│   Beschreibung:  Seit gestern funktionieren ...          │
│   [Alle Werte nur lesen – keine Bearbeiten-Schaltfläche]│
```

### 8.3 Abgeschlossener Fall – nur lesbar

```text
┌──────────────────────────────────────────────────────────┐
│ PropertyFlow                                             │
├──────────────────────────────────────────────────────────┤
│ REQ-2026-001                          [Abgeschlossen]    │
│ Heizung funktioniert nicht                               │
│                                                          │
│ ▸ Ursprüngliches Anliegen anzeigen                       │
│   (standardmässig eingeklappt; schreibgeschützt)         │
├──────────────────────────────────────────────────────────┤
│ Kommunikationsverlauf                                    │
│                                                          │
│   ... alle bisherigen Nachrichten chronologisch ...     │
│                                                          │
│ ┌───────────────────────────────────────────────┐        │
│ │ Immobilienverwaltung · 16:45                  │        │
│ │ Der Fall wurde erfolgreich abgeschlossen.     │        │
│ └───────────────────────────────────────────────┘        │
├──────────────────────────────────────────────────────────┤
│ Dieser Fall ist abgeschlossen. Es können keine           │
│ weiteren Nachrichten hinzugefügt werden.                │
│                                                          │
│ [Keine Nachrichteneingabe]  [Kein Senden-Button]          │
└──────────────────────────────────────────────────────────┘
```

**Aktion:** Es ist ausschliesslich Lesen zulässig. Zusätzliche Nachrichten müssen auch serverseitig verhindert werden.

## 9. Qualitäts- und Bedienungsregeln für diesen Screen

| Aspekt | Prüfkriterium / Nachweisvorschlag für Block 2 |
|---|---|
| **Barrierefreiheit** | Alle Eingaben haben sichtbare Labels; Fehlermeldungen sind dem Feld zugeordnet; ausklappbarer Bereich und Aktionen sind per Tastatur nutzbar; mindestens ein reproduzierbarer automatisierter A11y-Check (z. B. axe-core) plus Tastaturprüfung. |
| **Sicherheit** | KI- und Benutzernachrichten werden als Text ausgegeben. Test mit HTML-/Script-Payload bestätigt, dass keine Ausführung stattfindet; kein ungeprüftes `innerHTML`. |
| **Performance** | Definiertes Frontend-Performance-Budget und reproduzierbare Messung mit Lighthouse; Zielwert und tatsächliches Ergebnis werden nach der Implementierung dokumentiert. |
| **Streaming** | Statusübergänge überprüfbar; nach Abbruch erscheinen auch nach weiterer Wartezeit keine neuen Textteile; Fehlerzustand ist testbar. |
| **Mutation / Unit-Test** | Mindestens ein KI-generierter Komponententest wird durch gezielte Mutation nachweislich rot und nach Wiederherstellung wieder grün. |

**Ergebnisse sind noch offen:** Dies ist eine Spezifikation, kein bereits ausgeführter Test- oder Messbericht. Messwerte werden erst nach der Implementierung ergänzt.

## 10. Akzeptanzkriterien

- **AK-01:** Ohne Case-ID sieht der Mieter das Neuerfassungsformular mit Betreff, Beschreibung, Objekt-/Wohnungsreferenz und E-Mail-Adresse.
- **AK-02:** Ungültige Pflichtfelder verhindern die Annahme und führen zu verständlichen, feldbezogenen Fehlermeldungen ohne Verlust der eingegebenen Werte.
- **AK-03:** Nach erfolgreichem Erstabsenden existiert eine fachliche Case-ID und genau eine zugehörige Camunda-Prozessinstanz; erneutes Senden verursacht keine Duplikate.
- **AK-04:** Nach dem Erstabsenden wird auf derselben funktionalen Seite unmittelbar die aktive Fallansicht angezeigt; der persönliche Fall-Link wird per E-Mail versendet.
- **AK-05:** Beim bestehenden Fall ist «Ursprüngliches Anliegen anzeigen» zunächst eingeklappt und zeigt nach dem Aufklappen die vier ursprünglichen Angaben ausschliesslich lesbar.
- **AK-06:** Die gesamte für den Mieter bestimmte Kommunikation erscheint chronologisch, mit erkennbarer Absenderrolle und Zeitstempel.
- **AK-07:** Bei aktivem Fall kann der Mieter jederzeit zusätzliche Nachrichten an denselben Fall senden; Ursprungstext und Case-ID ändern sich dabei nicht.
- **AK-08:** KI-/Systemantworten und manuelle Backoffice-Antworten erscheinen im selben Verlauf, ohne interne Bearbeitungsnotizen offenzulegen.
- **AK-09:** Die simulierte Streaming-Komponente zeigt `waiting`, `streaming`, `completed`, `aborted` und `error`; ein Abbruch verhindert nachweislich weitere Textteile.
- **AK-10:** HTML-/Script-Payloads in Mieter- und KI-Ausgaben werden ohne Skriptausführung als Text dargestellt.
- **AK-11:** Nach fachlichem Abschluss wird der gesamte Fall nur lesbar angezeigt; eine Nachricht kann weder im Browser noch über einen direkten Backend-Aufruf durch irgendeine Rolle gespeichert werden.
- **AK-12:** Ein Fall kann nicht anhand einer bloss erratenen Case-ID gelesen oder beschrieben werden; ein gültiger persönlicher Zugriffsnachweis ist erforderlich.
- **AK-13:** Im Frontend ist die Camunda-Steuerung nur über PropertyFlow-Backend-Verträge erreichbar; es gibt keine direkten Camunda-Aufrufe aus dem Browser.

## 11. Umsetzung im FFHS-Block 2 / spätere Blöcke

**Block 2 – mit statischen Testdaten:**

- Thymeleaf-Vorlage für die drei Zustände derselben funktionalen Mieter-Fallansicht.
- Mock-Falldaten und Mock-Nachrichten, inklusive abgeschlossenem Fall.
- Ein- und Ausklappen der ursprünglichen Angaben mit nativem HTML.
- Eine Master-Detail-Ansicht wird **separat im Backoffice-Dashboard mit Falldetail** umgesetzt; Screen 01 ist selbst keine solche Ansicht.
- Simulierte, abbrechbare KI-Streaming-Antwort mit Status, Fehlerpfad und sicherer Ausgabe.
- Generierte und überprüfte Tests sowie A11y-, Sicherheits- und Performance-Nachweise.

**Block 3 – Services:** API-Verträge, tatsächliche Camunda-8-Integration, Prozessstart, Nachrichtenkorrelation, KI-/Worker-Integration, Autorisierungs- und Idempotenzmechanismen.

**Block 4 – Persistenz:** Fachliches Datenmodell für Anliegen, Nachrichten, Zugriffsnachweise und Auditdaten; dauerhafte Historie und zuverlässige Koordination von Prozessstart sowie Abschluss-/Nachrichtenschreiboperationen.

## 12. Referenzen und noch zu konkretisierende Punkte

**Bestehende Projektdokumente im Repository:**

- `docs/project-context.md` – verbindlicher KI-/Projektkontext.
- `docs/architecture/adr/ADR-001-grundarchitektur.md` – modularer Monolith.
- `docs/architecture/adr/ADR-002-camunda8-workflow-orchestration.md` – Camunda 8 und Zuständigkeiten.
- `docs/architecture/adr/ADR-003-praesentationsschicht.md` – SSR mit Thymeleaf und gezieltem JavaScript.
- `docs/use_cases/UC-001-submit-tenant-request.md` – ursprüngliche Anliegen-Erfassung.
- `docs/use_cases/UC-002-view-tenant-requests.md` – Backoffice-Übersicht.
- `docs/use_cases/UC-003-view-tenant-request-details.md` – Backoffice-Detail.
- `docs/use_cases/UC-004-run-ai-analysis.md` – bisherige KI-Analyse mit Streaming und Abbruch.

**Durch diese Screen-Spezifikation präzisiert beziehungsweise erweitert:** durchgängige Mieter-Kommunikationsansicht, direkter KI-Dialog nach Ersterfassung, persönlicher Fall-Link, gesperrte Ursprungsdaten, vollständige Mieter-sichtbare Historie, Nachrichtensperre nach Camunda-Abschluss. Bestehende Use-Cases und ADRs müssen bei der Implementierung **konsistent** ergänzt werden, statt konkurrierende Regeln einzuführen.

**Für einen späteren technischen Entscheid offen:** exakte Feldlängen, öffentliches URL-/Token-Format, Ablauf/Widerruf des Tokens, Zustell- und Wiederholungsstrategie für E-Mail/Prozessstart, konkretes BPMN-Nachrichtenmodell, genaue Statusnamen, Streaming-Transport und numerisches Performance-Budget. Die oben genannten Werte und Umsetzungsdetails sind Vorschläge, **keine bereits beschlossenen Projektentscheidungen**.