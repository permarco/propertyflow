# Fallverwaltung – Fallanlage und Lebenszyklus

**Status:** Zentrale Spezifikation der vereinbarten fachlichen Regeln und Bearbeitungsstatus; Prozessübergänge und technische Verträge noch zu konkretisieren.

**Stand:** 14.11.2026

**Geltungsbereich:** Erfassung, Backoffice, fachliche Services, Persistenz und Camunda-Integration von PropertyFlow.

## Zweck und Abgrenzung

Dieses Dokument definiert, wann ein Fall entsteht, wie er identifiziert und fortgeführt wird und was sein fachlicher Abschluss bedeutet. Es ist die gemeinsame Quelle für diese Regeln. Screen-Spezifikationen beschreiben ihre Darstellung; Use-Cases beschreiben die Nutzung aus Sicht der jeweiligen Akteure.

Die Anforderungen werden aus [Vision](../vision.md), [Projekt-Kontext](../project-context.md), [ADR-001](../architecture/adr/ADR-001-grundarchitektur.md), [ADR-002](../architecture/adr/ADR-002-camunda8-workflow-orchestrierung.md), den bestehenden Use-Cases und der bisherigen [Screen-01-Spezifikation](../frontend/ansicht-01-mieter-fallansicht.md) zusammengeführt. Akzeptierte Architekturentscheidungen bleiben gültig. Neue Konkretisierungen sind ausdrücklich als Review-Vorschlag gekennzeichnet.

| Zentrale Quelle | Verantwortung |
|---|---|
| Dieses Dokument | Fallidentität, Annahme, Ursprungsdaten, Zuordnung, fachlicher Lebenszyklus und Beziehung zum Workflow |
| [Fallkommunikation](fallkommunikation.md) | Interne/externe Nachrichten, Veröffentlichung, Ausschluss interner Memos aus LLM-Kommunikation und Nachrichtensperre nach Abschluss |
| [Dringlichkeitsbewertung](dringlichkeitsbewertung.md) | Stufen, Farben, Erst-/Neubewertung, Vorrang manueller Einstufungen und mieteröffentliche Änderungshistorie |
| [Fallzugriff und Sicherheit](fallzugriff-und-sicherheit.md) | Direkter persönlicher Tokenzugriff, Berechtigungen und Linkersatz |
| [Benachrichtigungen und Zustellung](benachrichtigungen-und-zustellung.md) | E-Mail-Auslöser, Versandpflicht, Wiederholungen und Zustellfehler |
| [Screen 01](../frontend/ansicht-01-mieter-fallansicht.md) | Felddarstellung, Navigation, Routenübersicht, Rückmeldungen und Wireframes |
| [Screen 02](../frontend/ansicht-02-mitarbeiter-falluebersicht.md) | Mitarbeiterliste, Suche, Filter, Sortierung, Seitennavigation und Aktualisierung |
| [Architektur und Modulgrenzen](../architecture/module-structure.md) | Technische Zuständigkeiten und öffentliche Integrationsverträge |

Die konkrete zuverlässige Übergabe an Camunda und Mail, ein mögliches Outbox-Verfahren und konkrete Datenbankschemata werden separat entschieden. Dieses Dokument legt die erforderlichen fachlichen Ergebnisse fest und bestätigt keine bereits vorhandene Umsetzung.

## FALL-01: Fallidentität und Erfassungskanal

Ein Fall ist das dauerhaft angenommene Mieteranliegen mit seiner fachlichen Identität und dem zugehörigen Bearbeitungsverlauf. Das Öffnen eines Formulars und noch nicht übermittelte Eingaben erzeugen keinen Fall. Eine dauerhafte Entwurfsverwaltung ist im aktuellen Umfang nicht vorgesehen. Dies gilt auch für externe Antworten im Mitarbeiterdetail: Kein Entwurfsspeichern; externe Nachrichten werden erst bei Veröffentlichung persistiert gemäss KOM-01. Interne Memos werden separat bewusst intern gespeichert.

Die Case-ID wird serverseitig vergeben, ist eindeutig und bleibt über den Lebenszyklus unverändert. Ein Beispiel wie `REQ-2026-001` legt noch kein verbindliches Nummernformat fest. Die Case-ID ist weder ein Zugriffstoken noch die technische Camunda-Prozessinstanz-ID. Der persönliche Mieterlink enthält ausschliesslich einen geheimen Token; seine serverseitige Fallzuordnung und die unveränderte Case-ID bei Tokenersatz regelt [ZUG-01](fallzugriff-und-sicherheit.md#zug-01-case-id-und-geheimer-token).

Die [Vision](../vision.md) sieht Webformular und E-Mail als Eingangskanäle vor. Für jeden Kanal gelten dieselben Regeln zur dauerhaften Annahme und Fallidentität. Die Feldbelegung und sichere Zuordnung eingehender E-Mails sind separat zu spezifizieren. Eine E-Mail ist nicht automatisch ein neuer Fall; eine sicher zugeordnete Ergänzung gehört zum bestehenden Fall gemäss [KOM-03](fallkommunikation.md#kom-03-asynchrone-fallkommunikation).

Die Aktion «Neues Anliegen melden» eröffnet die Erfassung. Erst eine neue erfolgreich angenommene Einreichung erzeugt eine neue Case-ID. Das Ergänzen eines bestehenden Falls erzeugt keine neue Case-ID und keinen zweiten führenden Bearbeitungsprozess.

## FALL-02: Erfolgreiche Annahme

**Ein Fall gilt erst nach erfolgreicher dauerhafter Speicherung als angenommen.** Weder eine Browserbestätigung noch ein gestarteter LLM-Aufruf ersetzt diesen Zeitpunkt.

Für die Web-Erfassung sind Betreff, Beschreibung und Objekt-/Wohnungsreferenz vorgesehen. Ohne Anmeldung wird zusätzlich eine E-Mail-Adresse eingegeben. Bei Einreichung aus einem angemeldeten, verifizierten Mieterkonto übernimmt der Dienst die verifizierte Konto-Adresse serverseitig als Fall-Kontaktadresse; sie ist kein Eingabefeld und darf nicht durch mitgesendete Werte überschrieben werden. Die Kontaktadresse bleibt für Fallzuordnung und Benachrichtigungen erforderlich. Die [Screen-Spezifikation](../frontend/ansicht-01-mieter-fallansicht.md#31-eingabefelder) definiert die Darstellung und Fehlertexte und führt die Review-Vorschläge zu den übrigen Feldlängen. Der Server prüft die geltenden Eingaberegeln unabhängig vom Browser.

Für eingegebene Kontaktadressen gilt dieselbe serverseitige E-Mail-Prüfung wie für Konto-Adressen gemäss [ZUG-04](fallzugriff-und-sicherheit.md#mieterzugriff). PropertyFlow speichert die geprüfte, vereinheitlichte Adresse und weist ungültige Adressen vor der Fallanlage ab.

Die Beschreibung eines neuen Anliegens muss einen Inhalt haben und darf höchstens **2’000 UTF-16-Codeeinheiten** umfassen. Vor der Längenprüfung vereinheitlicht der Server CRLF und einzelne CR zu LF. Die Grenze gilt vor dem Entfernen äusserer Leerzeichen. Für die dauerhafte Speicherung des vollständigen angenommenen Beschreibungstexts ist in PostgreSQL der Spaltentyp `TEXT` vorgesehen. Die Fallannahme setzt die Längengrenze durch.

Die Annahme umfasst fachlich:

1. Eingabe und zulässige Einreichung prüfen.
2. Case-ID vergeben und Ursprungsdaten dauerhaft speichern.
3. Die ursprüngliche Beschreibung als erste externe Mitteilung eindeutig in die Historie aufnehmen oder darauf referenzieren; keine fachlich doppelte Nachricht anlegen.
4. Den initialen fachlichen Status «Eingegangen» festhalten. Der gespeicherte Fall ist noch nicht aktiv; die aktive Phase beginnt erst nach erfolgreicher Registrierung in Camunda gemäss FALL-05/FALL-06.
5. Die nachgelagerte Prozessübergabe und erforderliche Bestätigungsbenachrichtigung zuverlässig veranlassen.
6. Erst nach bestätigter Speicherung den Erfolg mit der Case-ID zurückmelden.

Die für eine angenommene Meldung erforderlichen Daten und Folgeaufträge müssen nach einem Neustart rekonstruierbar sein. Ihre technische Transaktionsgrenze wird im Integrationsdesign festgelegt; eine gemeinsame lokale Transaktion mit Camunda oder dem Maildienst wird nicht vorausgesetzt.

Die Erfassung wartet nicht auf KI/RAG, erfolgreiche E-Mail-Zustellung, einen erreichbaren Camunda-Server oder eine menschliche Entscheidung. Ein gespeicherter Fall bleibt bei einem Ausfall erhalten und für Berechtigte lesbar. Die Fallbearbeitung richtet sich nach FALL-06.

## FALL-03: Wiederholung und doppelte Einreichung

Die technische Wiederholung derselben Einreichung darf keinen zweiten Fall erzeugen. Der bereits beschriebene Absende-ID-Vertrag bleibt Grundlage: gleiche Einreichung und gleiche serverseitig geprüfte Absende-ID liefern dasselbe Ergebnis und dieselbe Case-ID zurück; abweichender Inhalt mit derselben Absende-ID wird als Konflikt behandelt. Der technische Vertrag und seine Aufbewahrungsdauer sind noch festzulegen.

Bei unklarem Request-Ausgang, etwa nach einem Netzwerkabbruch, kann die Speicherung bereits erfolgt sein. Eine Wiederholung darf deshalb keine neue fachliche Identität erzeugen. Auch Prozessstart-Wiederholungen müssen demselben Fall zugeordnet und gegen Mehrfachwirkung abgesichert werden.

Das ist von zwei bewusst getrennten Einreichungen mit ähnlichem Inhalt zu unterscheiden: Eine automatische fachliche Zusammenführung solcher Fälle ist nicht beschlossen. Ähnliche Texte allein rechtfertigen weder Zusammenführen noch Verwerfen eines Anliegens.

## FALL-04: Ursprungsdaten und Zuordnung

Die bei der Annahme gespeicherten Ursprungsangaben sind für den Mieter unveränderlich. Er ergänzt oder korrigiert seine Meldung über eine neue Nachricht. Die Ursprungseingabe und frühere Nachrichten werden dadurch nicht überschrieben.

Die eingegebene Objekt-/Wohnungsreferenz ist von einer später vom Mitarbeiter bestätigten Fallzuordnung zu unterscheiden. Eine unklare Zuordnung verhindert die Annahme nicht. Sie bleibt intern als offen erkennbar und kann durch die dafür zuständige Fachbearbeitung geklärt werden. Dabei dürfen keine fremden Stammdaten in der öffentlichen Erfassung offengelegt werden.

Eine syntaktisch gültige E-Mail und eine angegebene Adresse bestätigen weder die persönliche Identität noch das Mietverhältnis. Identitätsprüfung, Empfängerwechsel und Zugriffsberechtigungen werden in [Fallzugriff und Sicherheit](fallzugriff-und-sicherheit.md) geführt. Der persönliche Fall-Link hat keine zeitliche Ablauffrist. Der fachliche Fallstatus bleibt beim Ersatz erhalten.

**Bestätigt am 10.10.2026 – Zuordnung durch Mitarbeiter:** Rechts bei Objekt/Wohnung in UI3 steht für die Bearbeitung aktiver Fälle «Zuordnung ändern» zur Verfügung. Der Mitarbeiter wählt einen vorhandenen Zuordnungsvorschlag oder gibt die Zuordnung frei ein und speichert mit «Zuordnung bestätigen». Solange keine bestätigte Zuordnung vorliegt, ist das Feld mit der ursprünglichen Mieterangabe vorbelegt; bei späteren Änderungen mit der aktuellen bestätigten Zuordnung. Die Vorbelegung allein bestätigt nichts. Damit kann er eine offene Zuordnung klären oder eine bestehende Zuordnung korrigieren. Die Auswahl allein verändert keine gespeicherten Daten. Auswahl und Freitext sind gleichwertige Eingabewege für dieselbe bestätigte Zuordnung; die ursprüngliche Mieterangabe bleibt unverändert nachvollziehbar. Es gibt keinen zusätzlichen Bestätigungsschritt nach dem Speichern und keinen zweiten Speicherbutton.

PropertyFlow prüft Berechtigung und Eingabelänge serverseitig. Wird eine strukturierte Stammdatenreferenz übermittelt, wird zusätzlich die gültige Zusammengehörigkeit von Objekt und Wohnung geprüft; eine Freitextzuordnung erfordert keine bestehende Stammdatenreferenz. Die bestätigte Zuordnung wird getrennt von den Ursprungsangaben mit vorherigem und neuem Wert, Zeitpunkt und handelndem Mitarbeiter gespeichert. UI3 zeigt den gespeicherten Stand; UI2 übernimmt ihn in die bestehende Spalte «Objekt/Wohnung». Eine erfolglose Speicherung wird nicht als erfolgreiche Zuordnung dargestellt. Die Konfliktprüfung aus FALL-06 gilt auch für die Zuordnungsänderung; eine zwischenzeitliche Änderung darf nicht unbemerkt überschrieben werden. Nachrichteneingabe und laufende KI-Formulierung bleiben erhalten.

Die Zuordnung klärt den entsprechenden Hinweis «Zuordnung offen». Weitere Prüfgründe bleiben bestehen. Camunda bestimmt die fachliche Fortsetzung nach FALL-06. Die Änderung bestätigt keine persönliche Identität und ändert weder Kontaktadresse noch Empfänger oder Mieterzugang. Hierfür gelten die eigenständigen Regeln aus [Fallzugriff und Sicherheit](fallzugriff-und-sicherheit.md). Die Aktion erlaubt vorhandene Zuordnungen als Vorschläge oder eine vom Mitarbeiter frei formulierte Zuordnung (1–200 Zeichen nach Entfernen äusserer Leerzeichen). Freitext wird als Fallzuordnung gespeichert und erzeugt keine neuen Stammdatensätze. Auswahlgestaltung, Datenzugriff und technische Speicherung werden im Service-/UI-Vertrag konkretisiert.

## FALL-05: Fachlicher Lebenszyklus

Der gemeinsame Grundzustand unterscheidet **noch keinen Fall**, **eingegangenen, noch nicht aktiven Fall**, **aktiven Fall** und **abgeschlossenen Fall**. «Neuer Fall» bezeichnet im UI das Erfassungsformular und ist kein bereits persistierter Fallstatus.

**Vereinbart am 13.11.2026:** «Eingegangen» bedeutet dauerhaft in PropertyFlow gespeichert, aber noch nicht erfolgreich in Camunda registriert. Erst die bestätigte Registrierung des zugehörigen führenden Bearbeitungsprozesses eröffnet die aktive Phase. Eine lediglich veranlasste oder unbestätigte Prozessübergabe genügt nicht. «Aktiver Fall» ist eine Phase, kein zusätzlicher Bearbeitungsstatus.

| Phase | Eintritt | Bedeutung und zulässige Fortsetzung |
|---|---|---|
| Noch kein Fall | Formular geöffnet oder Annahme gescheitert | Eingaben können korrigiert und eingereicht werden. Kein bearbeitbarer Fall wurde bestätigt. |
| Eingegangen, noch nicht aktiv | Annahme nach FALL-02 erfolgreich | Der Fall ist gespeichert und für Berechtigte lesbar; die Registrierung in Camunda steht noch aus. |
| Aktiv | Registrierung des führenden Bearbeitungsprozesses in Camunda bestätigt | Camunda führt die Bearbeitung. Für die Fallbearbeitung durch Mitarbeitende gilt FALL-06. |
| Abgeschlossen | Fachlich vorgesehener Abschluss nach FALL-07 konsistent übernommen | Berechtigtes Lesen bleibt möglich; die Nachrichtensperre aus KOM-04 gilt. Eine neue Meldung ist ein separater Fall. |

```mermaid
%%{init: {"flowchart":{"curve":"basis","nodeSpacing":42,"rankSpacing":55},"layout":"elk","theme": "base", "themeVariables": {"darkMode": true, "background": "#0f172a", "primaryColor": "#1e3a5f", "primaryTextColor": "#f1f5f9", "primaryBorderColor": "#60a5fa", "lineColor": "#94a3b8", "textColor": "#f1f5f9", "edgeLabelBackground": "#1e293b", "clusterBkg": "#1e293b", "clusterBorder": "#64748b"}}}%%
flowchart LR
    E["Eingegangen<br/>Gespeichert, noch nicht in Camunda registriert"]
    subgraph Aktiv["Aktiver Fall – in Camunda registriert"]
        direction LR
        Start(("Prozessstart"))
        A["In Abklärung"]
        M["Wartet auf<br/>Mieterantwort"]
        X["Wartet auf<br/>externe Rückmeldung"]
        W["Wartet auf<br/>Wiedervorlage"]
        B["In Bearbeitung"]
        P["Mitarbeiterprüfung<br/>erforderlich"]
    end
    Z["Abgeschlossen"]
    E -->|"1"| Start
    Start -->|"2"| A
    A -->|"4"| M
    M -->|"9"| B
    W -->|"17"| B
    B -->|"6"| P
    A -->|"3"| P
    P -->|"5"| B
    B -->|"7"| M
    M -->|"8"| P
    M -->|"10"| M
    B -->|"11"| X
    X -->|"12"| B
    X -->|"25"| W
    X -->|"26"| Z
    X -->|"27"| P
    P -->|"13"| W
    B -->|"14"| W
    W -->|"15"| W
    W -->|"16"| P
    A -->|"18"| Z
    M -->|"19"| Z
    P -->|"20"| Z
    B -->|"21"| Z
    W -->|"22"| Z
    Z -->|"23"| B
    Z -->|"24"| P
    %% Unsichtbare Verbindungen richten die Wartezustände aus.
    A ~~~ X
    A ~~~ W
    X ~~~ B
    M ~~~ B
    W ~~~ B
    classDef processing fill:#1e3a5f,stroke:#60a5fa,color:#f1f5f9
    classDef waiting fill:#3b2f63,stroke:#a78bfa,color:#f5f3ff
    classDef review fill:#713f12,stroke:#fbbf24,color:#fffbeb
    class A,B,Start processing
    class M,X,W waiting
    class P review
    style E fill:#164e63,stroke:#22d3ee,color:#ecfeff,stroke-width:2px
    style Z fill:#14532d,stroke:#4ade80,color:#f0fdf4,stroke-width:2px
    style Aktiv fill:#1e293b,stroke:#64748b,color:#f1f5f9
```

«Eingegangen» und «Abgeschlossen» liegen ausserhalb der aktiven Phase. «Prozessstart» ist ein Verbindungspunkt und kein zusätzlicher Bearbeitungsstatus.

Die Pfeilnummern verweisen auf die Übergänge in der Tabelle. Die Anmerkungen in eckigen Klammern gelten wie angegeben.

| Pfeil | Von | Nach | Übergang |
|---:|---|---|---|
| 1 | Eingegangen | Prozessstart | Erfolgreich in Camunda registriert |
| 2 | Prozessstart | In Abklärung | Erster Bearbeitungsschritt · Entwurf |
| 3 | In Abklärung | Mitarbeiterprüfung erforderlich | Menschliche Prüfung nötig |
| 4 | In Abklärung | Wartet auf Mieterantwort | Freigegebene Rückfrage [1] |
| 5 | Mitarbeiterprüfung erforderlich | In Bearbeitung | Bearbeitung fortsetzen |
| 6 | In Bearbeitung | Mitarbeiterprüfung erforderlich | Neuer Prüfbedarf, z. B. Statusmail dauerhaft abgelehnt |
| 7 | In Bearbeitung | Wartet auf Mieterantwort | Rückfrage veröffentlicht [1] |
| 8 | Wartet auf Mieterantwort | Mitarbeiterprüfung erforderlich | Alle Antworten verarbeitet [2] |
| 9 | Wartet auf Mieterantwort | In Bearbeitung | Letzten Antwortbedarf aufgehoben [1] |
| 10 | Wartet auf Mieterantwort | Wartet auf Mieterantwort | Antwortbedarf bleibt offen |
| 11 | In Bearbeitung | Wartet auf externe Rückmeldung | Mitarbeiter wählt externe Wartephase [4] |
| 12 | Wartet auf externe Rückmeldung | In Bearbeitung | Mitarbeiter setzt Bearbeitung fort [4] |
| 13 | Mitarbeiterprüfung erforderlich | Wartet auf Wiedervorlage | Termin setzen |
| 14 | In Bearbeitung | Wartet auf Wiedervorlage | Termin setzen |
| 15 | Wartet auf Wiedervorlage | Wartet auf Wiedervorlage | Termin ändern oder Rückfrage veröffentlichen |
| 16 | Wartet auf Wiedervorlage | Mitarbeiterprüfung erforderlich | Termin erreicht oder Mieternachricht gespeichert |
| 17 | Wartet auf Wiedervorlage | In Bearbeitung | Termin löschen |
| 18 | In Abklärung | Abgeschlossen | Geprüfter Systemabschluss [3] |
| 19 | Wartet auf Mieterantwort | Abgeschlossen | Geprüfter Systemabschluss [3] |
| 20 | Mitarbeiterprüfung erforderlich | Abgeschlossen | Mitarbeiter bestätigt Abschluss [3] |
| 21 | In Bearbeitung | Abgeschlossen | Zulässiger Abschluss [3] |
| 22 | Wartet auf Wiedervorlage | Abgeschlossen | Zulässiger Abschluss [3] |
| 23 | Abgeschlossen | In Bearbeitung | Wiedereröffnung ohne Prüfbedarf |
| 24 | Abgeschlossen | Mitarbeiterprüfung erforderlich | Wiedereröffnung mit Prüfbedarf |
| 25 | Wartet auf externe Rückmeldung | Wartet auf Wiedervorlage | Mitarbeiter setzt Termin [4] |
| 26 | Wartet auf externe Rückmeldung | Abgeschlossen | Mitarbeiter bestätigt Abschluss [3][4] |
| 27 | Wartet auf externe Rückmeldung | Mitarbeiterprüfung erforderlich | Neuer zwingender Prüfbedarf [4] |

- **[1]** Keine laufende Wiedervorlage und kein vorrangiger Mitarbeiterprüfbedarf; automatische Rückfragen benötigen die serverseitig geprüften Ausführungsbedingungen nach [KOM-05](fallkommunikation.md#kom-05-automatische-rueckfragen). Für zulässige Katalogrückfragen ist keine zusätzliche menschliche Einzelfreigabe vorgesehen.
- **[2]** Kein zulässiger automatischer Abschluss und keine laufende Wiedervorlage.
- **[4]** Externe Wartephase gemäss FALL-12; Nachricht oder Memo allein beendet sie nicht.
- **[3]** Abschluss gemäss FALL-07/FALL-10 unter Beachtung verpflichtender menschlicher Entscheidungen.

Mit **Entwurf** bezeichnete Übergänge sind noch zu konkretisieren. Insbesondere legt die Registrierung allein noch keinen ersten aktiven Bearbeitungsstatus fest; der dargestellte Einstieg in «In Abklärung» ist ein Vorschlag. Das Diagramm ist kein vollständiger Übergangskatalog: Neue Prüfgründe können auch aus weiteren aktiven Status zur Mitarbeiterprüfung führen; Wiedervorlage und Abschluss sind bei entsprechender Prozessfreigabe ebenfalls aus weiteren aktiven Status möglich. Die nachfolgenden Fachregeln bleiben massgebend. Das Diagramm ersetzt weder BPMN noch schreibt es eine zweite Workflow-Engine in PropertyFlow vor.

### Fachliche Bearbeitungsstatus – vereinbart

**Vereinbart am 09.10.2026, ergänzt am 10.10.2026:** Die folgenden acht Bezeichnungen und Bedeutungen bilden die fachlichen Bearbeitungsstatus. «Wartet auf Wiedervorlage» wurde mit FALL-11 ergänzt. Technische Enum-Werte, weitere Übergangsbedingungen und das Mapping zum BPMN-Modell werden separat festgelegt.

| Bearbeitungsstatus | Bedeutung |
|---|---|
| Eingegangen | Dauerhaft in PropertyFlow gespeichert, aber noch nicht erfolgreich in Camunda registriert; noch kein aktiver Fall. |
| In Abklärung | Automatische Analyse oder fachliche Abklärung läuft. |
| Wartet auf Mieterantwort | Eine Rückfrage wurde veröffentlicht; benötigte Informationen fehlen noch. |
| Mitarbeiterprüfung erforderlich | Menschliche Prüfung ist nötig, etwa nach einem Analysefehler, bei klärungsbedürftigem E-Mail-Versand, bei verbleibender Unklarheit oder wegen einer verpflichtenden menschlichen Entscheidung nach FALL-10. |
| In Bearbeitung | Das weitere Vorgehen wurde festgelegt; die Umsetzung läuft. |
| Wartet auf externe Rückmeldung | Informationen oder eine Rückmeldung beispielsweise von Hauswart oder Fachstelle stehen aus. |
| Wartet auf Wiedervorlage | Ein Mitarbeiter hat die erneute Prüfung terminiert. Camunda wartet bis zum Termin oder bis zur nächsten gespeicherten Mieternachricht gemäss FALL-11. |
| Abgeschlossen | Der fachliche Abschluss wurde bestätigt; neue Nachrichten sind gemäss KOM-04 gesperrt. |

**Vereinbarte Konkretisierung am 09.10.2026:** Bei einem aktiven Fall führen **offene Objektzuordnung**, **fehlgeschlagene KI-Analyse** und **klärungsbedürftiger E-Mail-Versand** einheitlich zum Bearbeitungsstatus **«Mitarbeiterprüfung erforderlich»**. Der jeweilige Grund bleibt zusätzlich intern am Fall gespeichert und für die Bearbeitung nachvollziehbar: «Zuordnung offen», «KI-Analyse fehlgeschlagen» bzw. «E-Mail-Versand klären». Screen 02 zeigt den Bearbeitungsstatus, aber keine Problemhinweise. Die konkrete interne Darstellung der Gründe wird bei Screen 03 festgelegt. Mehrere Gründe können gleichzeitig vorliegen. Ein automatischer Wiederholungsversuch hebt diese Zuordnung nicht auf. Diese Vereinbarung ersetzt die frühere Ausnahme für Analysefehler während automatischer Wiederholungen. Der Übergang wird im vorgesehenen Camunda-/PropertyFlow-Ablauf vorgenommen, nicht allein aus einem Browserfehler abgeleitet.

Die Zuordnung zu «Mitarbeiterprüfung erforderlich» beschreibt den Eintritt in die erforderliche Prüfung. Bestätigt ein Mitarbeiter nach Prüfung «Bearbeitung fortsetzen», veranlasst Camunda nach FALL-06 den Wechsel zu «In Bearbeitung». Entscheidet er über eine Wiedervorlage oder deren Löschung, gelten die bestätigten Statusübergänge aus FALL-11. Bereits berücksichtigte Gründe erzwingen nach diesen Entscheidungen ohne neue Erkenntnisse keinen sofortigen Rückwechsel; sie bleiben intern nachvollziehbar und gelten dadurch nicht pauschal als fachlich erledigt.

Eine veröffentlichte Rückfrage mit ausstehender Mieterantwort ist fachliches Warten und für sich kein technischer Fehler. Nach Behebung der Hinweise muss die fachlich passende Fortsetzung im Prozess erfolgen; der konkrete Übergang bleibt festzulegen. Ein noch ausstehender Analyse- oder Versandversuch ohne festgestellten Fehler ist nicht automatisch ein Problemhinweis.

**Abschlussgrenze:** Ein Versandproblem, das erst nach fachlichem Abschluss festgestellt wird, bleibt als Hinweis am Fall für die interne Bearbeitung nachvollziehbar; es wird nicht in Screen 02 angezeigt. Der Fall bleibt «Abgeschlossen»; die Problembehandlung eröffnet ihn nicht wieder und umgeht die Nachrichtensperre nicht. Der aktive Status «Mitarbeiterprüfung erforderlich» wird nicht zur stillschweigenden Wiedereröffnung verwendet.

«Eingegangen» liegt vor der aktiven Phase. Die sechs Bearbeitungsstatus von «In Abklärung» bis «Wartet auf Wiedervorlage» gehören zur aktiven Phase; «Abgeschlossen» gehört zur abgeschlossenen Phase. Eine zusätzliche Nachricht ist während eines aktiven Falls zulässig, auch wenn der Workflow gerade nicht auf eine Mieterantwort wartet. Jede gespeicherte Mieter-Nachricht veranlasst die Neubewertung gemäss DRING-05. Welche fachliche Fortsetzung sie auslöst, bestimmt der modellierte Prozess. Der blosse Eingang einer Nachricht beantwortet nicht automatisch jede offene fachliche Rückfrage.

**Bestätigt am 14.11.2026:** Mieternachrichten sind in jedem der sechs aktiven Bearbeitungsstatus zulässig, insbesondere während «Mitarbeiterprüfung erforderlich». Eine offene Rückfrage ist keine Voraussetzung. Der laufende Fallprozess muss dafür während der gesamten aktiven Bearbeitung einen Nachrichtenempfang bereitstellen; der Empfang darf nicht auf den sichtbaren Warteschritt «Wartet auf Mieterantwort» beschränkt sein. Zugriff, laufender Prozess, Erreichbarkeit von Camunda und die Nachrichtensperre nach Abschluss gelten weiterhin gemäss FALL-06/KOM-04. Für neue Nachrichten während einer Mitarbeiterprüfung gilt der nachfolgend festgelegte [Vorrang der Mitarbeiterprüfung](#vorrang-einer-laufenden-mitarbeiterprüfung).

Die Zuordnung ist keine feste lineare Reihenfolge. Der Workflow kann Abklärungen und Rückfragen wiederholen. Solche Schleifen behalten die Case-ID bei und öffnen keine neue Fallanlage.

**Mitarbeiter-Rückfragen:** Beim Veröffentlichen einer externen Nachricht mit ausdrücklich gesetztem Mieterantwortbedarf veranlasst Camunda «Wartet auf Mieterantwort», sofern keine Wiedervorlage läuft und kein vorrangiger Prüfbedarf nach FALL-05/FALL-10 «Mitarbeiterprüfung erforderlich» verlangt. Die veröffentlichte Rückfrage bleibt auch in diesen Fällen als offen nachvollziehbar. Der Wartestatus wird nicht allein durch die Auswahl im Browser oder die KI-Formulierung gesetzt.

**Vereinbart am 13.11.2026:** Veröffentlicht ein Mitarbeiter während einer laufenden Wiedervorlage eine Rückfrage, bleibt der Fall auf «Wartet auf Wiedervorlage» und der Termin gilt weiter. Geht die Mieterantwort vor dem Termin ein, hebt Camunda die Wiedervorlage nach Speicherung der Antwort auf und veranlasst sofort «Mitarbeiterprüfung erforderlich». Ohne vorherige Mieternachricht löst der wirksame Termin die Mitarbeiterprüfung aus. [FALL-11](#fall-11-wiedervorlage) · [UC-010](../use_cases/UC-010-externe-fallnachricht-veroeffentlichen.md)

Eine reine Informationsnachricht setzt keinen neuen Mieter-Wartestatus, entfernt keinen bestehenden Antwortbedarf und löst für sich keinen beliebigen Statuswechsel aus. **Bestätigt am 10.10.2026:** Camunda veranlasst die inhaltliche Prüfung neuer Mieternachrichten gegen jede offene Rückfrage. Eindeutig beantwortete Fragen werden einzeln erledigt; unvollständige, unklare oder themenfremde Antworten lassen die betroffenen Fragen offen. Die Regeln und Prüfkriterien sind zentral in [KOM-03](fallkommunikation.md#inhaltliche-prüfung-offener-rückfragen) geführt. Beim erfolgreichen Fallabschluss endet noch offener Antwortbedarf gemäss KOM-04. Wird eine Rückfrage während eines aktiven Falls gegenstandslos, kann ein Mitarbeiter ihren Antwortbedarf über «Antwortbedarf aufheben» gemäss KOM-03 gezielt beenden.

### Ausbleibende Mieterantwort

**Version 1, bestätigt am 15.11.2026:** Keine automatischen Erinnerungen,
Antwortfristen oder zeitgesteuerten Eskalationen wegen Schweigens. Ohne anderen
fachlichen Auslöser bleibt «Wartet auf Mieterantwort» samt offenem Antwortbedarf
bestehen. Schweigen löst keinen Abschluss aus. Manuelles Nachfragen, bewusstes
Aufheben des Antwortbedarfs und Mitarbeiterabschluss folgen den bestehenden
Regeln. Eine ausdrücklich gesetzte Wiedervorlage nach FALL-11 bleibt wirksam.
Details und geplante Prüffälle: [KOM-03](fallkommunikation.md#ausbleibende-mieterantwort-in-version-1)
und KOM-AK-28.

### Status nach Beantwortung aller Rückfragen

**Bestätigt am 10.10.2026:** Ergibt die inhaltliche Prüfung, dass alle zuvor offenen Rückfragen beantwortet sind, verarbeitet Camunda die Antworten im vorgesehenen Fallprozess weiter. Erfolgt dabei kein zulässiger automatischer Abschluss nach FALL-07/FALL-10 und läuft keine Wiedervorlage, erhält der weiterhin aktive Fall nach der automatischen Verarbeitung den Status «Mitarbeiterprüfung erforderlich». Der Mitarbeiter entscheidet anschliessend über das weitere Vorgehen.

Eine bereits laufende Mitarbeiterprüfung bleibt dabei bis zur bewussten Mitarbeiterentscheidung bestehen; aus diesem Status ist kein Systemabschluss zulässig. [FALL-06](#vorrang-einer-laufenden-mitarbeiterprüfung)

Bei laufender Wiedervorlage löst bereits die gespeicherte Mieternachricht die sofortige Vorlage zur Mitarbeiterprüfung nach FALL-11 aus. Ein erfolgreich abgeschlossener Fall bleibt «Abgeschlossen». Sind noch Rückfragen offen oder wurden sie nicht zuverlässig als beantwortet erkannt, greift der Übergang wegen vollständiger Beantwortung nicht; andere Prüfgründe und Statusregeln bleiben wirksam. Eine reine Informationsnachricht ohne zuvor offene Rückfragen löst diesen Übergang ebenfalls nicht aus.

Die Statusübernahme wird mit dem aktuellen Fallstand und Camunda abgestimmt. Eine wiederholte Verarbeitung derselben bereits berücksichtigten Antworten darf eine anschliessende bewusste Mitarbeiterentscheidung nach FALL-06/FALL-11 nicht zurücknehmen. Die Nachweise für inhaltliche Prüfung und diese Statusfortsetzung werden gemeinsam in KOM-AK-09 geführt.

### Status nach manuellem Aufheben des Antwortbedarfs

**Prozessfortsetzung bestätigt am 15.11.2026:** Der geprüfte Mitarbeiterauftrag
weckt den aktuellen Mieterwartepfad ohne Mieternachricht oder neue KI-Analyse.
Nach Aufhebung des letzten Antwortbedarfs folgt direkt die Mitarbeiterbearbeitung,
sofern keine vorrangige Prüfung oder Wiedervorlage besteht. Teilaufhebung lässt
den Wartepfad bestehen. Mitarbeiterprüfung und Wiedervorlage werden durch die
Nebenaktion nicht beendet. Die eigenständige externe Wartephase nach FALL-12
bleibt ebenfalls bestehen; der direkte Wechsel betrifft den Mieterwartepfad.
Einzelheiten und geplante Konfliktprüfungen stehen in
[KOM-03](fallkommunikation.md#prozesseingang-für-antwortbedarf-aufheben) und KOM-AK-29.


**Bestätigt am 10.10.2026:** Hebt ein Mitarbeiter den letzten offenen Mieterantwortbedarf über die UI3-Aktion «Antwortbedarf aufheben» auf, wechselt der aktive Fall auf «In Bearbeitung», sofern keine laufende Wiedervorlage, externe Wartephase oder vorrangiger aktueller Mitarbeiterprüfbedarf bestehen. Eine laufende Wiedervorlage bleibt gemäss FALL-11 wirksam; vorrangiger Mitarbeiterprüfbedarf behält «Mitarbeiterprüfung erforderlich». Bereits durch eine bewusste Mitarbeiterentscheidung berücksichtigte Prüfgründe werden dabei nach FALL-06/FALL-11 nicht erneut als neuer Prüfbedarf behandelt.

Besteht nach der Aufhebung weiterer Mieterantwortbedarf, löst die Aktion allein keinen Statuswechsel aus. Die Aufhebung schliesst den Fall nicht ab, bestätigt keine Behebung des Problems und gibt keine weiteren Massnahmen frei. Fallabschluss und Beantwortung durch den Mieter bleiben eigene Vorgänge. PropertyFlow prüft und speichert Antwortbedarf und einen daraus folgenden Statuswechsel konsistent auf dem aktuellen Fallstand und stimmt die Änderung mit Camunda ab. Es entsteht kein zusätzlicher Status. Die Kommunikationsregeln und Prüfkriterien stehen in KOM-03/KOM-AK-10.

## FALL-06: Fachlicher Status und technischer Workflow

| Information | Führende Verantwortung | Abgrenzung |
|---|---|---|
| Case-ID, Ursprungsdaten, Objektzuordnung, gespeicherter fachlicher Status und Verlauf | PropertyFlow | Führender Datenspeicher für die Fallansichten |
| Prozessinstanz, Prozessposition, aktive Aufgaben, Timer, Jobs, Incidents und Entscheidung über Statuswechsel | Camunda 8 | Führende Instanz für den Ablauf gemäss ADR-002 |
| E-Mail-Auftrag und Versand-/Zustellinformation | PropertyFlow und Mailintegration | Benachrichtigungszustand; kein Fallstatus |
| Analyseergebnis oder technischer Analysefehler | Zuständiger PropertyFlow-Service | Unterstützende Information; keine eigenständige fachliche Abschlussentscheidung |

Für einen angenommenen Fall wird genau ein führender Camunda-Bearbeitungsprozess zuverlässig gestartet und dem Fall zugeordnet. Unmittelbar nach Annahme kann dieser Start noch ausstehen; der Fall bleibt dann «Eingegangen» und noch nicht aktiv. Mit Registrierung ist hier die bestätigte Anlage und Zuordnung dieses führenden Prozesses gemeint. Erst danach beginnt die aktive Phase. Ein technischer Retry darf keine zweite unabhängige Bearbeitung desselben Falls eröffnen. Eine mögliche interne BPMN-Zerlegung wird dadurch nicht vorweggenommen.

**Vereinbart am 13.11.2026:** PropertyFlow speichert bei der Fallanlage den initialen Status «Eingegangen». Ab dann veranlasst ausschliesslich der führende Camunda-Prozess fachliche Statuswechsel. Mitarbeitende lösen fachliche Aktionen aus. PropertyFlow prüft ihre Berechtigung und übergibt die Aktion an Camunda. Ein Job Worker ruft den zuständigen PropertyFlow-Anwendungsdienst für den durch Camunda veranlassten Übergang auf. Der Dienst prüft den aktuellen Fallstand und speichert den bestätigten Status.

Vor dem Prozessstart und bei nicht verfügbarem Camunda bleiben Fälle für berechtigte Mitarbeitende lesbar. PropertyFlow sperrt fachliche Bearbeitungsaktionen serverseitig, auch bei direktem Zugriff über Web oder MCP. Der zuletzt gespeicherte Status bleibt sichtbar. Eine ungeklärte Antwort von Camunda gilt nicht als Bestätigung einer Aktion.

Ergänzungen zu bestehenden Fällen setzen einen laufenden Fallprozess und erreichbares Camunda voraus. Bis dahin bleibt der ungesendete Text im Mieterformular. Die Fallannahme startet Camunda weiterhin asynchron. [UC-009](../use_cases/UC-009-mieteranliegen-ergaenzen.md) · [SS-003](../architecture/schnittstellenentwurf-block-3.md#ss-003--camunda-integrationsvertrag)

Der Prozess gibt die jeweilige Mitarbeiteraktion an der dafür vorgesehenen Stelle frei. Eine Aktion darf keinen Fallstand aus einer bereits abgeschlossenen Aufgabe überschreiben. Während der Mitarbeiterprüfung wartet Camunda auf die fachliche Entscheidung. Verspätete automatische Ergebnisse aus einem früheren Prozessschritt dürfen den Fall in dieser Phase nicht ändern. Beim Fortsetzen prüft PropertyFlow den aktuellen Fallstand und die Zuordnung zur passenden Prozessaufgabe. Bei einem Konflikt wird die Aktion nicht als erfolgreich angezeigt. Eingaben und laufende Formulierung bleiben erhalten.

### Vorrang einer laufenden Mitarbeiterprüfung

**Bestätigt am 14.11.2026:** Sobald ein Fall **«Mitarbeiterprüfung erforderlich»** erreicht hat, darf das System diesen Bearbeitungsstatus nicht selbstständig verlassen. Nur eine bewusste, berechtigte Mitarbeiterentscheidung zur passenden aktuellen Aufgabe kann die Fortsetzung, eine Wiedervorlage oder den Abschluss veranlassen. Camunda und der PropertyFlow-Dienst führen diese Entscheidung nach Prüfung aus. Das gilt unabhängig davon, ob der Prüfbedarf ursprünglich durch das System oder durch eine menschliche Entscheidung entstanden ist. Eine automatische Ausnahme anhand dieser Herkunft ist für den aktuellen Umfang ausdrücklich nicht vorgesehen.

- Neue Mieternachrichten werden weiterhin angenommen, gespeichert und derselben laufenden Prüfung zugeordnet. Sie erzeugen keine zweite Mitarbeiteraufgabe und keinen neuen Fallprozess.
- Die aktuelle Nachricht darf nach den bestehenden Regeln die Neubewertung der Dringlichkeit, die Einzelprüfung offener Rückfragen und eine aktualisierte Empfehlung auslösen. Zulässige aktuelle Ergebnisse dürfen gespeichert werden; manuelle Dringlichkeit und bewusste Mitarbeiterentscheidungen bleiben geschützt. Die Auswertung ergänzt die Entscheidungsgrundlage, schliesst aber weder die Mitarbeiteraufgabe noch den Fall.
- **«Alles klar, der Fall ist abgeschlossen»** beziehungsweise eine eindeutige Behebungsbestätigung des Mieters ändert diesen Status nicht. Das gilt auch dann, wenn die neue Analyse keinen weiteren Verwaltungsbedarf erkennt, alle Rückfragen beantwortet sind oder der ursprüngliche systemseitige Prüfgrund inzwischen entfallen scheint. Der Mitarbeiter kann noch eigene Abklärungen oder Kontrollen offen haben.
- Die Sperre gilt für jeden automatischen Weg: Statusfortsetzung, Systemabschluss, Timer, Retry und verspätetes Ergebnis. Auch ein bereits vorbereiteter automatischer Abschluss darf eine inzwischen begonnene Mitarbeiterprüfung nicht übergehen. Die Prüfung erfolgt am aktuellen Fall- und Aufgabenstand unmittelbar vor einer wirksamen Änderung.
- Nach einer bestätigten Mitarbeiterentscheidung folgt der freigegebene Prozessweg. Alte Auswertungen dürfen diese Entscheidung nicht zurücknehmen. Eine spätere automatische Aktion benötigt erneut die aktuellen Ausführungsbedingungen; «Bearbeitung fortsetzen» ist keine pauschale Abschlussfreigabe.

Die folgenden Fehler-, Freigabe- und Abschlussregeln gelten ergänzend; sie erlauben keine automatische Ausnahme von diesem Statusschutz. Der Nachrichteneingang und seine Auswertung werden in [KOM-03](fallkommunikation.md#kom-03-asynchrone-fallkommunikation) beschrieben.

Bei verfügbarer Prozesssteuerung führen die Fehlerfälle nach FALL-05 über Camunda zu «Mitarbeiterprüfung erforderlich». Das betrifft offene Objektzuordnungen, fehlgeschlagene KI-Analysen und klärungsbedürftigen E-Mail-Versand. Die fachlichen Abschluss-, Wiedereröffnungs- und Freigaberegeln nach FALL-07 und FALL-10 gelten dabei weiterhin.

Ein technischer Abbruch oder ein beliebiges BPMN-Endereignis ist nicht ohne fachliche Zuordnung mit einem erfolgreichen Fallabschluss gleichzusetzen. Massgeblich ist der im Fachprozess ausdrücklich vorgesehene Abschluss.

**Präzisiert am 13.10.2026 – berücksichtigte Prüfgründe:** Beim bewussten Wechsel auf «In Bearbeitung» wird der bisher offene Prüfgrund dem Statusereignis als interner, berücksichtigter Prüfgrund zugeordnet. Er verschwindet aus der Box «Prüfbedarf», bleibt aber mit Zeitpunkt und Änderungsquelle im internen Verlauf erhalten, unabhängig von einer optionalen Mitarbeiterbegründung. Ohne neue Erkenntnisse löst dieser berücksichtigte Grund keine erneute Prüfung aus. Mieter erhalten weder den Grund noch dessen interne Kennzeichnung. Die Umsetzung der Demo ist im [F01-Nachweis](../ai-usage/block2-oberflaechen-pruefung.md#korrektur-f01--berücksichtigte-prüfgründe) dokumentiert.

### Optionale interne Begründung der Bearbeitungsentscheidung

Bei «Bearbeitung fortsetzen» kann der Mitarbeiter eine optionale interne Begründung eingeben. PropertyFlow hält sie bei der bestätigten Aktion zusammen mit der Mitarbeiteridentität und dem Zeitpunkt im internen Änderungsnachweis fest. Mieteransicht, E-Mail und Kommunikations-LLM erhalten sie gemäss KOM-01/KOM-02 nicht.

Abbrechen, eine fehlgeschlagene Prüfung oder ein Konflikt speichern keine bestätigte Änderung samt Begründung. Wiederholungen derselben erfolgreichen Aktion erzeugen keinen doppelten Änderungsnachweis.

### Direkter Übergang in die Bearbeitung

**Vereinbart am 13.11.2026:** Wartet der Camunda-Prozess bei «Mitarbeiterprüfung erforderlich» auf eine Mitarbeiterentscheidung, kann ein berechtigter Mitarbeiter nach Prüfung des Falls «Bearbeitung fortsetzen» bestätigen. Damit gibt er an, dass das weitere Vorgehen feststeht und die Verwaltung es umsetzt. Camunda veranlasst aus der passenden Mitarbeiteraufgabe den Übergang zu «In Bearbeitung». Ein internes Memo dient der Dokumentation.

Die Entscheidung wird mit Mitarbeiteridentität, Zeitpunkt und Bezug zum geprüften Fallstand einschliesslich der bereits berücksichtigten Prüfgründe nachvollziehbar gespeichert und der bestätigten Prozessaufgabe zugeordnet. Dieselben Gründe oder wiederholte Auswertungen derselben Erkenntnisse setzen den Fall nicht sofort wieder auf «Mitarbeiterprüfung erforderlich». Neue Erkenntnisse oder neu entstandene Prüfgründe können erneut eine Prüfung auslösen. Die bestehende Konfliktprüfung verhindert, dass eine Entscheidung auf veraltetem Einstellungsstand eine zwischenzeitliche Änderung überschreibt.

Die Entscheidung bestätigt die Übernahme der weiteren Bearbeitung. Bestehende Hinweise und ihre Historie bleiben nachvollziehbar; technische Probleme gelten dadurch nicht als behoben. Die konkrete Bearbeitungsentscheidung ist keine pauschale Freigabe weiterer Kosten, Folgehandlungen oder eines späteren Systemabschlusses nach FALL-10 und erledigt keine offene Mieterfrage. Die serverseitige Autorisierung sowie die Regeln zu Abschluss und Wiedereröffnung bleiben wirksam.

## FALL-07: Abschluss und Zeit danach

Ein Fall wechselt erst nach dem fachlich vorgesehenen Abschluss im Camunda-Ablauf und der konsistenten Übernahme in PropertyFlow in die abgeschlossene Phase. Eine abgeschlossene KI-Analyse, ein Browserabbruch oder eine verschickte E-Mail genügt dafür nicht.

**Vereinbart am 09.10.2026:** Ein Fall kann durch einen Mitarbeiter oder durch das System abgeschlossen werden. «Fachlich erledigt» bedeutet, dass für die Verwaltung kein weiterer Bearbeitungsbedarf besteht. Dies kann eine behobene Störung oder ein eindeutig der Eigenverantwortung des Mieters zugeordnetes Anliegen sein; ein Abschluss behauptet deshalb nicht in jedem Fall eine ausgeführte Reparatur.

| Abschluss durch | Fachliche Voraussetzung / Beispiel |
|---|---|
| **Mitarbeiter** | Eine persönlich angemeldete Person mit Mitarbeiterrolle bestätigt den fachlichen Abschluss. Sie beurteilt, dass keine weitere Bearbeitung des Falls erforderlich ist. |
| **System – Behebung bestätigt** | Eine gespeicherte Mieter-Nachricht bestätigt eindeutig, dass das Problem behoben ist und keine weitere Hilfe benötigt wird, z. B. «Fehler behoben, keine weitere Hilfe nötig». Das System prüft diese Aussage im Zusammenhang des aktuellen Falls. |
| **System – kein Bearbeitungsbedarf der Verwaltung** | Die fachliche Prüfung ergibt eindeutig, dass das Anliegen gemäss den hinterlegten Fachregeln vom Mieter selbst zu erledigen ist. Beispiel: ein Glühbirnenwechsel, soweit der konkrete Austausch nach diesen Regeln in die Eigenverantwortung des Mieters fällt. |

Für die beiden beschriebenen Systemabschlüsse ist keine zusätzliche Einzelfreigabe durch einen Mitarbeiter vorgesehen, sofern weder eine bestehende verpflichtende menschliche Prüfung noch der Status «Mitarbeiterprüfung erforderlich» greift. Eine bereits laufende Mitarbeiterprüfung kann ausschliesslich durch eine bewusste Mitarbeiterentscheidung verlassen werden, auch bei ursprünglich systemseitig ausgelöstem Prüfbedarf. [FALL-06](#vorrang-einer-laufenden-mitarbeiterprüfung)

Das System führt einen zulässigen Abschluss im vorgesehenen Camunda-/PropertyFlow-Ablauf aus. Die Einordnung und der Statusschutz werden unmittelbar vor der Übernahme serverseitig validiert; ein isoliertes Stichwort wie «behoben» oder «Glühbirne» genügt nicht. Die fachliche Regel und der aktuelle Fallinhalt sind massgeblich. Bei unklarer oder widersprüchlicher Einordnung wird nicht automatisch abgeschlossen; nötige menschliche Prüfung wird als «Mitarbeiterprüfung erforderlich» geführt.

Für den aktuellen automatischen Umfang sind ausschliesslich die beiden beschriebenen Abschlussgründe freigegeben, sofern die folgenden Voraussetzungen erfüllt sind. Weitere automatische Gründe sind gesondert fachlich festzulegen. Die Einstufung «kein konkreter Fall» bezeichnet hier fehlenden weiteren Bearbeitungsbedarf der Verwaltung: Ein bereits angenommenes Anliegen behält Case-ID, Ursprungsdaten und Historie und erhält den Status «Abgeschlossen». Es wird weder verworfen noch gelöscht.

Die verpflichtenden menschlichen Entscheidungen sind in [FALL-10](#fall-10-automatisierung-und-menschliche-freigabe) konkretisiert: Kosten bzw. verbindliche externe Beauftragung, sehr dringliche oder schwerwiegende Fälle sowie starke oder eskalierte Mieterbeschwerden. Sie entsprechen den Prüfgrenzen aus [ADR-002](../architecture/adr/ADR-002-camunda8-workflow-orchestrierung.md) und der [Sicherheitsbasis](../evaluation/evaluationsgrundlage.md). Ein offener menschlicher Entscheidungsbedarf darf nicht durch einen Systemabschluss umgangen werden. Ein Analysefehler, eine offene Zuordnung oder ein Versandproblem ist für sich kein Abschlussgrund. Die Dringlichkeitsstufe allein begründet ebenfalls keinen Abschluss.

Der Abschluss bleibt mit Zeitpunkt, Abschlussquelle (Mitarbeiter oder System), fachlichem Grund und auslösender Grundlage nachvollziehbar. Bei Mitarbeitern wird die handelnde Identität, beim System die relevante Nachricht bzw. Fachregel und Prozessreferenz intern zugeordnet. Ein durch die Mieternachricht ausgelöster Abschluss hat die Quelle «System»; der Mieter erhält dadurch keine direkte Berechtigung zur Statusänderung. Eine wiederholte Verarbeitung desselben Abschlusses erzeugt keinen zweiten fachlichen Abschluss oder zusätzlichen logischen Abschlussmailauftrag.

**Bestätigt am 10.10.2026 – Abschlussvermerk bei Mitarbeiterabschluss:** Beim erfolgreichen Mitarbeiterabschluss speichert PropertyFlow intern den festen Vermerk «Vom Mitarbeiter geprüft: keine weitere Bearbeitung erforderlich.» zusammen mit dem handelnden Mitarbeiter, dem Zeitpunkt und der Abschlussquelle «Mitarbeiter». Die bewusste Abschlussbestätigung ist die auslösende Grundlage. Damit ist der fachliche Grund auch ohne separates Memo nachvollziehbar. Der Vermerk gehört zum strukturierten Abschlussnachweis; er ist keine zusätzliche Nachricht oder Memo-Speicherung und wird nicht als Nachricht an den Mieter veröffentlicht. Ein zusätzliches Begründungsfeld entfällt. Eine ausführlichere Erklärung kann der Mitarbeiter weiterhin freiwillig vor dem Abschluss als internes Memo speichern.

Bei einem automatischen Abschluss wird weiterhin der tatsächlich geprüfte Systemgrund einschliesslich seiner Grundlage gespeichert, beispielsweise die eindeutige Behebungsbestätigung des Mieters oder die einschlägige Fachregel. Der feste Mitarbeitervermerk ersetzt diesen Nachweis nicht. Ein abgebrochener oder fehlgeschlagener Abschluss erzeugt keinen erfolgreichen Abschlussnachweis; die geltenden Abschlussvoraussetzungen und Freigabegrenzen bleiben wirksam.

Die Folgen für interne und externe Nachrichten sind zentral in [KOM-04](fallkommunikation.md#kom-04-nachrichtensperre-nach-fallabschluss) festgelegt: Nach Abschluss dürfen keine neuen Nachrichten mehr entstehen. **Bestätigt am 10.10.2026:** Mit dem erfolgreichen Abschluss endet auch noch offener Mieterantwortbedarf. Bisherige Rückfragen bleiben im Verlauf erhalten, ohne unbeantwortete Fragen als beantwortet auszugeben. Dies ist eine Folge des Status «Abgeschlossen» und führt keinen zusätzlichen Status ein. Die dort verlangte Synchronisierung muss gleichzeitig mit dem Abschluss greifen, auch bei konkurrierenden Requests und verspäteten Worker-Ergebnissen.

**UI3-Abschlussbestätigung vereinbart am 10.10.2026:** Der separate Button «Fall abschliessen» öffnet «Fall wirklich abschliessen?» mit «Abschliessen» und «Abbrechen». Erst die Bestätigung fordert den Abschluss unter Prüfung der geltenden Voraussetzungen an. Abbrechen bewirkt keine fachliche Änderung. **Ebenfalls vereinbart:** Eine zusätzliche interne Abschlussbegründung kann bei Bedarf vor dem Abschluss als Memo gespeichert werden. Ein separates Memo ist keine Pflicht; der Dialog enthält kein zusätzliches Begründungsfeld. Der strukturierte Abschlussnachweis nach FALL-07 bleibt erforderlich und wird dadurch nicht ersetzt.

**Abschlussreihenfolge in UI3 vereinbart am 10.10.2026:** Eine zusätzliche externe Abschlussmitteilung ist optional und wird vor dem Abschluss über das bestehende Textfeld mit «Nachricht senden» gespeichert und veröffentlicht. Der Abschlussdialog enthält kein separates Nachrichtenfeld. Der Abschluss selbst erzeugt keine Nachricht und keine E-Mail nach BEN-05. Das spätere Abbrechen oder Scheitern des Abschlusses nimmt eine bereits veröffentlichte Mitteilung nicht zurück. Ein nachträglicher Worker darf die Sperre nicht für eine neue Nachricht umgehen. Der Versand einer bereits gespeicherten externen Mitteilung ist davon als separater Folgeauftrag zu unterscheiden.

Der abgeschlossene Fall bleibt mit gültiger Berechtigung lesbar. Screen 01 bietet keine Wiedereröffnung. Ein erneutes Anliegen wird separat angelegt. **Vereinbart am 09.10.2026:** Die fachliche Entscheidung zur Wiedereröffnung liegt ausschliesslich bei Mitarbeitenden. Alle Mitarbeitenden haben die Berechtigung, die Aktion in UI3 anzufordern. Camunda veranlasst die Wiedereröffnung auf Grundlage der geprüften Mitarbeiteraktion. Der Fall behält Case-ID, Ursprungsdaten und Historie einschliesslich des bisherigen Abschlusses. Die Wiedereröffnung führt ihn in die aktive Phase zurück und bleibt mit Zeitpunkt und handelnder Mitarbeiteridentität nachvollziehbar. Erst nach bestätigter Wiedereröffnung sind neue Nachrichten und interne Memos wieder zulässig. Die bisherigen Fachregeln einschliesslich FALL-10 und des manuellen Dringlichkeitsvorrangs gelten weiterhin.

**Wiedereröffnungsablauf vereinbart am 10.10.2026:** Der separate Button «Fall wiedereröffnen» öffnet «Fall wiedereröffnen?» mit «Wiedereröffnen» und «Abbrechen». Mit der Bestätigung fordert der Mitarbeiter die Wiedereröffnung an. Camunda veranlasst nach der Prüfung den Zielstatus «In Bearbeitung» oder bei offenem Prüfbedarf nach FALL-05/FALL-10 «Mitarbeiterprüfung erforderlich». Bis zur bestätigten Übernahme bleibt der Fall abgeschlossen. **Ebenfalls vereinbart:** Eine zusätzliche Begründung wird bei Bedarf nach bestätigter Wiedereröffnung als internes Memo erfasst. Sie ist optional. Der Wiedereröffnungsdialog enthält kein zusätzliches Begründungsfeld. Der Nachweis von Zeitpunkt und Mitarbeiteridentität bleibt unabhängig davon erforderlich. Bei beendeter Camunda-Instanz startet die bestätigte Wiedereröffnung eine neue Prozessinstanz für denselben Fall, wie nachfolgend festgelegt. Eine Zusammenführungs- oder Stornierungsfunktion ist weiterhin nicht spezifiziert.

**Mieteransicht nach Wiedereröffnung, bestätigt am 10.10.2026:** Die sichtbare Mieteransicht liest auch bei abgeschlossenem Fall alle 20 Sekunden den gespeicherten Stand. Erkennt sie die bestätigte Wiedereröffnung, zeigt sie ohne manuelles Neuladen den aktuellen aktiven Status und die Nachrichteneingabe wieder an. Darstellung, Pausen, Fehlerbehandlung und Prüfkriterien stehen in [UI1, Abschnitt 4.5.1](../frontend/ansicht-01-mieter-fallansicht.md#451-automatische-aktualisierung-von-nachrichten-status-und-dringlichkeit). Diese Leseabrufe sind keine Wiedereröffnungsaktion; die serverseitige Nachrichtensperre gilt bis zur bestätigten Wiedereröffnung.

Bei abgeschlossenem Fall ist die wirksame Dringlichkeit nur lesbar gemäss DRING-08. Eine Änderung setzt die bestätigte mitarbeiterseitige Wiedereröffnung voraus. Abschluss ist weder Löschung noch Widerruf einer Zugriffsberechtigung; der persönliche Fall-Link bleibt ohne zeitliche Ablauffrist nutzbar. Archivierung, Aufbewahrung und besondere Löschprozesse benötigen eigene Festlegungen.

### Regeln für den automatischen Fallabschluss

**Bestätigt am 15.11.2026:** Ein Systemabschluss benötigt einen konkret belegten Abschlussgrund und die Freigabe am aktuellen Fallstand. Das LLM schlägt den Grund mit Belegstellen vor. Der Dienst prüft die zulässige Ausführung, den Bezug der Belege zum Fall und gegebenenfalls die Fachregel. Eine Empfehlung oder ein hoher vom Modell genannter Sicherheitswert ist für sich keine Abschlussfreigabe. Die folgenden Beschränkungen betreffen Systemabschlüsse; für Mitarbeiterabschlüsse gelten die eigenen Regeln dieses Abschnitts.

#### Gemeinsame Voraussetzungen

Alle Voraussetzungen müssen gleichzeitig erfüllt sein:

1. **Zulässiger Bearbeitungsstatus:** Der führende Fallprozess gibt den Systemabschluss frei, und der gespeicherte Status ist **«In Abklärung»** oder **«Wartet auf Mieterantwort»**. In allen anderen Status ist ein automatischer Abschluss ausgeschlossen. Insbesondere werden laufende Mitarbeiterprüfung, Mitarbeiterbearbeitung, Wiedervorlage und externe Wartephase nicht automatisch beendet. Eine frühere Entscheidung «Bearbeitung fortsetzen» ist keine pauschale Abschlussfreigabe.
2. **Kein offener menschlicher Entscheidungsbedarf:** Die Grenzen aus FALL-06/FALL-10 bleiben wirksam, insbesondere bei Kosten oder verbindlicher Beauftragung, schwerwiegenden beziehungsweise sehr dringlichen Fällen und starken oder eskalierten Beschwerden. Eine Behebungsbestätigung hebt einen solchen Prüfbedarf nicht auf.
3. **Keine ungeklärte Grundlage:** Offene Objektzuordnung, fehlgeschlagene Analyse und klärungsbedürftiger Versand schliessen den Systemabschluss aus. Eine niedrige Dringlichkeit, ein technischer Fehler oder das Ausbleiben einer Mieterantwort ist kein Abschlussgrund.
4. **Aktueller Nachrichten- und Auswertungsstand:** Alle für die Entscheidung relevanten gespeicherten Mieternachrichten sind berücksichtigt. Eine neu eingegangene, noch nicht ausgewertete Nachricht verhindert den automatischen Abschluss auf alter Grundlage. Ein veraltetes Ergebnis wird verworfen beziehungsweise auf aktueller Grundlage neu beurteilt; sein Alter oder eine Wiederholung allein erzeugt keinen neuen Mitarbeiterprüfgrund.

Status, Prozessfreigabe, offene Prüfgründe und Entscheidungsgrundlage werden unmittelbar vor einer automatischen Veröffentlichung und nochmals bei der Abschlussspeicherung gegen den aktuellen Fallstand geprüft. Prüfung und Speicherung müssen konkurrierende Änderungen gemeinsam absichern. Wird die vorgeschlagene Aktion inzwischen von einer Mitarbeiterentscheidung oder einem anderen Prozessschritt überholt, darf sie diesen nicht verdrängen. Ein bereits erfolgreich gespeicherter identischer Abschluss bleibt bei Wiederholung idempotent.

#### Abschlussgrund «Problem behoben»

Alle folgenden Bedingungen müssen belegt sein:

1. **Tatsächlich eingetretene Behebung:** Eine gespeicherte Mieternachricht bestätigt im Zusammenhang des aktuellen Falls eindeutig, dass das gemeldete Problem nicht mehr besteht. Vermutungen, vorläufige Besserung, eine angekündigte Reparatur oder eine erst geplante Handlung genügen nicht.
2. **Kein weiterer Hilfebedarf:** Aus der Nachricht ergibt sich ausdrücklich oder im Fallzusammenhang eindeutig, dass keine weitere Hilfe benötigt wird. Eine bestimmte Formulierung ist nicht vorgeschrieben. «Danke», Schweigen oder die blosse Aufforderung «Bitte schliessen» belegen für sich keine Behebung.
3. **Gesamter offener Sachverhalt erledigt:** Bei mehreren Teilproblemen muss die Bestätigung alle noch offenen Anliegen abdecken. Verbleibende Störungen, Folgeschäden, zusätzliche Forderungen oder sonstiger Bearbeitungsbedarf verhindern den automatischen Abschluss.
4. **Kein aktueller Widerspruch:** Die Einordnung berücksichtigt den Nachrichtenverlauf. Eine positive Einzelaussage darf keine gleichzeitig oder später gemeldete Einschränkung überdecken.

| Mieternachricht | Einordnung des Abschlussgrundes |
|---|---|
| «Die Heizung funktioniert wieder. Sie müssen nichts mehr unternehmen.» | Behebung und fehlender Hilfebedarf sind belegt, sofern dies den gesamten offenen Sachverhalt betrifft. Die gemeinsamen Voraussetzungen bleiben zu prüfen. |
| «Im Moment funktioniert sie wieder. Ich beobachte es noch.» | Keine eindeutige Behebung bestätigt; kein automatischer Abschluss. |
| «Das Tropfen hat aufgehört, aber die Decke ist noch beschädigt.» | Weiterer Bearbeitungsbedarf; kein automatischer Abschluss. |
| «Der Techniker kommt morgen.» | Massnahme angekündigt, Behebung nicht bestätigt; kein automatischer Abschluss. |

Ist das ganze Anliegen eindeutig erledigt, müssen dadurch gegenstandslos gewordene Rückfragen nicht künstlich beantwortet werden. Ihr offener Antwortbedarf endet erst mit dem erfolgreichen Abschluss gemäss KOM-04; die Fragen werden ohne passende Antwort nicht als beantwortet ausgegeben. Eine weiterhin relevante offene Frage darf dagegen nicht durch den Abschluss übergangen werden.

#### Abschlussgrund «Kein Bearbeitungsbedarf der Verwaltung»

Alle folgenden Bedingungen müssen belegt sein:

1. **Ausdrücklich freigegebene Fachregel:** Eine hinterlegte Regel mit Kennung, Version und festgelegtem Geltungsbereich erlaubt für diesen Sachverhalt einen automatischen Abschluss. Allgemeines Modellwissen, ein beliebiger gefundener Text oder lediglich eine thematisch ähnliche Quelle genügt nicht. Die Regel muss für den konkreten Fall gültig und anwendbar sein.
2. **Voraussetzungen durch Fallangaben belegt:** Alle von der Regel verlangten Angaben sind vorhanden und nachvollziehbar belegt, beispielsweise Gegenstand, Ort und Art der Störung. Fehlende Angaben dürfen weder als erfüllt noch als Beleg für das Fehlen einer Ausnahme behandelt werden.
3. **Keine Ausnahme und kein Widerspruch:** Abweichende Umstände, zusätzliche Schäden, widersprüchliche Regeln oder ein begründeter Einwand des Mieters gegen die Zuordnung verhindern die automatische Anwendung und führen zur Mitarbeiterprüfung. Ungeklärte Ausschlussumstände gelten nicht als ausgeräumt.
4. **Gesamtes Anliegen abgedeckt:** Die Regel deckt den vollständigen noch offenen Sachverhalt ab. Bleibt ein Teilproblem in der Verantwortung der Verwaltung oder ungeklärt, darf der Fall nicht automatisch abgeschlossen werden.

Beispiel zur Regelanwendung: Falls eine ausdrücklich freigegebene Fachregel einen bestimmten Leuchtmittelwechsel der Eigenverantwortung zuordnet, müssen die Angaben genau zu dieser Regel passen. «Licht defekt» allein reicht nicht. Eine beschädigte Fassung oder ein anderer Defekt darf nicht ohne passende Regel gleichgesetzt werden. Dieses Beispiel gibt selbst keine Zuständigkeitsregel frei. Solange keine geeignete freigegebene Regel vorliegt, ist dieser Systemabschlussgrund nicht anwendbar. Der Abschluss dokumentiert fehlenden Verwaltungsbedarf und behauptet keine ausgeführte Reparatur.

#### Prüfung, Nachweis und Fortsetzung

Das LLM liefert den vorgeschlagenen Grund, zugehörige Nachrichten beziehungsweise Belegstellen und bei regelbasiertem Abschluss die Regelreferenz. Der Dienst prüft vorhandene und zum Fall gehörende Quellen, Regelkennung und -version, Geltungsbereich, notwendige Voraussetzungen sowie die aktuellen Ausführungsbedingungen. Eine formal gültige Ausgabe beweist keine semantische Richtigkeit. Die inhaltliche Erkennung und Regelzuordnung benötigen eine Evaluation mit positiven, negativen und mehrdeutigen Fällen, bevor sie automatisch wirksam werden. Für die Generierung von Kommunikation gilt weiterhin KOM-02.

Beim erfolgreichen Systemabschluss bleiben Grund, Belegbezug, geprüfter Fallstand, Prozessreferenz und gegebenenfalls Regelkennung/-version nachvollziehbar. Bereits bekannte Belege oder erfolgreiche Wiederholungen erzeugen keine zweite fachliche Wirkung und keinen zusätzlichen logischen Abschlussmailauftrag.

Ohne belegten Grund erfolgt kein automatischer Abschluss. Unklare oder widersprüchliche fachliche Einordnung benötigt Mitarbeiterprüfung. Für gezielt erfragbare fehlende Sachangaben bleiben die bestehenden Katalogrückfrageregeln aus KOM-05 anwendbar; die vollständige Beantwortung früherer Rückfragen folgt weiterhin FALL-05. Ein anderer aktueller Status, eine doppelte Verarbeitung oder ein bloss veraltetes Ergebnis darf nicht pauschal eine zusätzliche Mitarbeiteraufgabe erzeugen. Die technische Koordination der Fortsetzung bleibt vor der ausführbaren Umsetzung festzulegen.

Der automatische Abschluss erzeugt eine individuelle LLM-Nachricht nach [KOM-06](fallkommunikation.md#kom-06-automatische-antworten-vor-fallabschluss). Der Worker prüft Inhalt und Aktualität, speichert die Nachricht und beauftragt zuverlässig den Versand. Danach wird der Abschluss erneut geprüft und gespeichert; keine zusätzliche Abschlussmail. Mitarbeiter dürfen ohne Nachricht abschliessen und können bei Bedarf vorher selbst senden. Technische Fehler verwenden Job-Retries/Incidents; inhaltlich unzulässige LLM-Nachrichten führen zur Mitarbeiterprüfung.

### Mitarbeiterabschluss während laufender KI-Auswertung

**Bestätigt am 15.11.2026:** Eine laufende KI-Auswertung allein blockiert keinen bewussten Mitarbeiterabschluss. Der Mitarbeiter beurteilt den aktuellen Fallstand einschliesslich bereits gespeicherter neuer Mieternachrichten und bestätigt, dass keine weitere Bearbeitung nötig ist. Ein veralteter Entscheidungsstand blockiert den Abschluss hingegen. Die Entscheidung bleibt an die konkrete aktuelle Mitarbeiteraufgabe und die geprüfte fachliche Grundlage gebunden; ein bloss ausstehender Modellaufruf ist kein neuer Prüfgrund.

PropertyFlow prüft diese Grundlage bei der Annahme der Mitarbeiteraktion und nochmals unmittelbar bei der fachlichen Abschlussspeicherung. Prüfung und Speicherung müssen konkurrierende fachliche Änderungen gemeinsam absichern. Eine neue Nachricht oder eine inzwischen wirksam gespeicherte, relevante Auswertung darf nicht unbemerkt durch eine ältere Abschlussentscheidung übergangen werden. Rein technische Job- oder Retry-Zustände gelten für sich nicht als geänderte fachliche Entscheidungsgrundlage.

- **Konflikt vor Abschluss der Mitarbeiteraufgabe:** Die veraltete Aktion wird abgewiesen. Die bestehende Aufgabe bleibt offen; der Mitarbeiter muss den aktualisierten Fallstand prüfen und bewusst erneut entscheiden.
- **Konflikt nach Abschluss der Mitarbeiteraufgabe, aber vor Speicherung von «Abgeschlossen»:** Camunda unterbricht den Abschlussweg und führt zurück zu «Mitarbeiterprüfung erforderlich». Die alte Abschlussfreigabe gilt nicht weiter. Für den aktuellen Fallstand wird genau eine neue Mitarbeiteraufgabe mit neuem Aufgabenschlüssel bereitgestellt; die bereits erledigte Aufgabe wird nicht reaktiviert. Wiederholte Konfliktmeldungen dürfen keine zusätzlichen Aufgaben erzeugen. Interner Hinweis: «Fallstand seit Abschlussbestätigung geändert».
- **Folgen des Konflikts:** Der Fall bleibt aktiv. Es entstehen weder ein erfolgreicher Abschlussnachweis noch ein Abschlussmailauftrag. Der durchgängige Mieternachrichtenempfang bleibt bestehen. Bereits gespeicherte Nachrichten und eine zuvor veröffentlichte Abschlussmitteilung bleiben erhalten. Dieser Konflikt ist keine Wiedereröffnung, weil der fachliche Abschluss noch nicht stattgefunden hat.
- **Technischer Speicherfehler:** Er ist von einem festgestellten fachlichen Konflikt zu unterscheiden. Ein technischer Retry verwendet dieselbe Entscheidung und prüft deren Gültigkeit erneut; er bestätigt keinen Abschluss und erzeugt nicht allein eine neue Mitarbeiterprüfung. Ein bereits erfolgreich gespeicherter identischer Abschluss wird bei Wiederholung idempotent bestätigt und darf nicht als Konflikt zurück in die aktive Bearbeitung führen.

Nach erfolgreicher Abschlussspeicherung endet der aktive Prozessbereich samt laufenden Nachrichtenauswertungen und Empfang; es wird keine zusätzliche Abschlussbenachrichtigung veranlasst. Ein bereits gestarteter externer Modellaufruf kann trotzdem verspätet antworten. Vor einer Ergebnisübernahme prüft der Dienst deshalb erneut den aktuellen Status und den Bezug zur Bearbeitung. Das alte Ergebnis darf weder Status, Dringlichkeit, Antwortbedarf oder Kommunikation ändern noch eine Mitarbeiterprüfung oder Wiedereröffnung auslösen. Diese Wirkung muss auch bei gleichzeitigem Abschluss und Ergebniszugriff abgesichert sein.

Der fachliche Rückweg ist im [BPMN-Entwurf](../architecture/bpmn/propertyflow-fallprozess-entwurf.bpmn) vorgesehen. Technische Versionsbindung, Transaktionsschutz und Wiederholungskoordination sind vor der ausführbaren Umsetzung zu konkretisieren.

### Neuer Prozessdurchlauf bei Wiedereröffnung

**Bestätigt am 15.11.2026:** Wiedereröffnung startet eine neue Instanz des
Fallprozesses für denselben bestehenden Fall. Die bisherige Instanz bleibt
beendet und historisch zugeordnet; sie wird nicht reaktiviert. Case-ID,
Ursprungsdaten, Nachrichten und bisheriger Abschlussnachweis bleiben erhalten.
Keine neue Fallanlage, Annahmebestätigung oder automatische Erstprüfung.

Die berechtigte Mitarbeiterbestätigung erzeugt einen eigenen nachvollziehbaren
Wiedereröffnungsauftrag mit Bezug zum abgeschlossenen Bearbeitungsdurchlauf.
Die neue Instanz prüft den Auftrag und den aktuellen Fallstand. Nur ein weiterhin
abgeschlossener Fall mit beendeter Vorgängerinstanz darf übernommen werden.
Aktuelle führende Instanz, neuer Bearbeitungsdurchlauf, Zielstatus, Mitarbeiter-
und Zeitnachweis sowie Auftragserledigung werden konsistent gespeichert.
Bis zur erfolgreichen Speicherung bleibt «Abgeschlossen» mit Nachrichtensperre.
Der neue Nachrichteneingang muss vor fachlicher Aktivierung empfangsbereit sein;
öffentliche Korrelation verwendet erst danach die neue serverseitige Zuordnung.

Ohne aktuellen Prüfbedarf geht es direkt zu «In Bearbeitung» und zur Aufgabe
«Weiteres Vorgehen umsetzen». Bei aktuellem Prüfbedarf folgt «Mitarbeiterprüfung
erforderlich» und «Fall prüfen und Vorgehen entscheiden». Der Eintritt ist
serverseitig aus der geprüften Wiedereröffnung abzuleiten, kein frei gewählter
Client-Status. Ein inzwischen neu auftretender Prüfbedarf bleibt vorrangig.
Alte Wiedervorlagen, beendeter Antwortbedarf und erledigte User Tasks werden nicht
reaktiviert. Fachliche Historie und manuelle Dringlichkeit bleiben erhalten.

Wiederholungen desselben Auftrags dürfen nur einen neuen führenden Durchlauf
wirksam machen. Eine doppelt gestartete oder fachlich überholte Instanz darf die
gewählte neue Zuordnung nicht überschreiben. Sie endet ohne fachliche Wirkung;
keine weitere Mitarbeiteraufgabe, keine Nachricht und kein fingierter Abschluss.
Technische Fehler folgen Retries/Incidents. Alte Aufträge, Timer, KI-Ergebnisse
und User-Task-Aktionen werden auch nach Wiedereröffnung anhand ihres Durchlaufs
zurückgewiesen. Ein neuer aktiver Status allein macht alte Arbeit nicht gültig.

Diese Entscheidung präzisiert die Ausnahme zur unveränderlichen Erststart-
Zuordnung aus ADR-002: Austausch ausschliesslich über den geprüften
Wiedereröffnungsauftrag, niemals durch einen beliebigen zweiten Start. Exakte
Transaktions-/Startschnittstellen und Engine-Abstimmung stehen vor Ausführung
noch aus. Die bestehende Statusbenachrichtigung für Wiedereröffnung folgt BEN-01;
es entsteht keine erneute Fallannahmebestätigung.

### Verspätete automatische Ergebnisse nach Wiedereröffnung

**Bestätigt am 10.10.2026:** Verspätete automatische Ergebnisse aus der Bearbeitung vor dem letzten Fallabschluss dürfen nach einer Wiedereröffnung keine Änderungen am aktuellen Fall bewirken. Das gilt insbesondere für einen alten Abschlussvorschlag, Status- oder Dringlichkeitsänderungen, automatische Nachrichten und frühere Timerereignisse. Der wiedereröffnete Fall wird anhand seines aktuellen Fallstands weiterbearbeitet. Case-ID und bereits gespeicherter Verlauf bleiben erhalten; historische Einträge werden nicht gelöscht.

Diese Regel beschreibt die fachlich erforderliche Wirkung. Die technische Zuordnung von Ergebnissen zur aktuellen Bearbeitung, die Camunda-Prozessfortsetzung und die konkrete LLM-Logik werden später ausgearbeitet. Die bestätigte UI3-Bedienung zur Wiedereröffnung und zum Erhalt laufender Formulierungs-Streams bleibt bestehen; ein erhaltener Vorschlag bewirkt weiterhin keine automatische Veröffentlichung oder Falländerung.

Automatischer Abschluss nach veröffentlichter Antwort: Der bestätigte Rückweg
bei `AUTO_CLOSURE_STALE`, Erhalt der Antwort und Wiederholungsschutz stehen in
[KOM-06-Ergänzung](fallkommunikation.md#veralteter-automatischer-abschluss-nach-veröffentlichter-antwort).
KOM-AK-26 beschreibt die späteren Prüffälle; der Mitarbeiterkonflikt bleibt getrennt.

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
| Camunda beim Prozessstart vorübergehend nicht erreichbar | Angenommener Fall bleibt gespeichert, noch nicht aktiv und auf «Eingegangen». PropertyFlow holt die Registrierung des führenden Prozesses zuverlässig nach. Erst deren Bestätigung eröffnet die aktive Phase. |
| KI-Analyse fehlgeschlagen oder Objektzuordnung offen | Camunda veranlasst für den aktiven Fall «Mitarbeiterprüfung erforderlich» mit dem jeweiligen Hinweis nach FALL-05. Gespeicherte Daten bleiben erhalten. Die Mitarbeiterprüfung läuft bei verfügbarem Camunda über die zugehörige Prozessaufgabe. |
| RAG oder andere externe Komponente fällt aus | Fall bleibt gespeichert; soweit dadurch eine fehlgeschlagene KI-Analyse oder offene Zuordnung vorliegt, gilt FALL-05. Weitere technische Zustände sind im Integrationsvertrag zuzuordnen. |
| E-Mail-Versand ist klärungsbedürftig | Aktiver Fall erhält «Mitarbeiterprüfung erforderlich» und den Versandhinweis; abgeschlossener Fall bleibt abgeschlossen. Fall, Nachrichten und Case-ID bleiben bestehen. |
| Zusätzliche Nachricht bei aktivem Fall | Bestehenden Fall ergänzen; keinen neuen Fall und keinen zweiten führenden Prozess anlegen. |
| Nachricht trifft während oder nach Abschluss ein | Konsistente Durchsetzung der Nachrichtensperre gemäss KOM-04. |
| Fall-Link ersetzt | Der neue Link öffnet denselben Fall mit dem aktuellen fachlichen Status. Der alte Link ist ab dem Ersatz gesperrt. |
| Ähnliche neue Meldung | Keine automatische Zusammenführung ohne gesondert festgelegte Regel. |

## FALL-10: Automatisierung und menschliche Freigabe

**Umfang präzisiert am 10.10.2026:** PropertyFlow konzentriert sich auf Fallbearbeitung und Kommunikation zwischen Immobilienverwaltung und Mieter. Es gibt keine Anbindung zur automatischen externen Beauftragung und keine Aktion, die einen Auftrag an einen Techniker oder Dienstleister sendet. Die Verwaltung entscheidet und organisiert solche Massnahmen ausserhalb von PropertyFlow. Die nachfolgenden menschlichen Entscheidungsgrenzen bleiben gültig; sie führen zu fachlichem Prüfbedarf, nicht zu einer externen Auftragsausführung durch PropertyFlow. Eine Nachricht wie «Techniker aufgeboten» informiert über eine tatsächlich ausserhalb organisierte Massnahme. Eine Nachricht oder ihre KI-Formulierung ersetzt diese nicht. **Bestätigt am 10.10.2026:** Mitarbeitende nutzen interne Memos zur Dokumentation ihrer Prüfungen, Abklärungen und ausserhalb organisierten Massnahmen. Eine separate UI3-Aktion «Prüfung erledigt» ist nicht vorgesehen. Das Speichern eines Memos bewirkt allein keine Statusänderung und keine automatische Erledigung eines Prüfgrundes. Mitarbeitende übermitteln fachliche Entscheidungen nach FALL-06 an den Fallprozess.

**Vereinbart am 09.10.2026:** Das System darf fachliche Schritte innerhalb der ausdrücklich vereinbarten und serverseitig geprüften Regeln automatisch ausführen. Eine endgültige Entscheidung erfordert einen Mitarbeiter, sobald mindestens einer der folgenden Gründe vorliegt:

| Grund | Verbindliche Grenze |
|---|---|
| **Kosten oder verbindliche externe Beauftragung** | Die Entscheidung über eine kostenwirksame Massnahme oder einen verbindlichen Auftrag, beispielsweise ein Technikeraufgebot, liegt beim Mitarbeiter. Beauftragung und Ausführung werden ausserhalb von PropertyFlow organisiert; PropertyFlow löst keine solche Beauftragung aus. Ein unbekannter Preis erlaubt keine automatische Entscheidung. |
| **Sehr dringlicher oder schwerwiegender Fall** | Bei sehr hoher Dringlichkeit oder schwerwiegenden Auswirkungen, etwa unmittelbarer Gefahr oder drohendem erheblichem Folgeschaden, trifft ein Mitarbeiter die endgültige fachliche Entscheidung über das Vorgehen und den Abschluss. |
| **Starke oder eskalierte Mieterbeschwerde** | Wenn sich der Mieter stark beschwert oder der Fall fachlich eskaliert, sind das weitere Vorgehen und der Abschluss durch einen Mitarbeiter zu entscheiden; dies gilt auch ohne hohe technische Dringlichkeit. |

Solange die notwendige menschliche Entscheidung aussteht, erhält ein aktiver Fall **«Mitarbeiterprüfung erforderlich»**. Der konkrete Prüfgrund bleibt intern nachvollziehbar und wird im Mitarbeiterdetail dargestellt. Screen 02 zeigt weiterhin ausschliesslich den Bearbeitungsstatus und keine Problemhinweise. Die Mieteransicht erhält keine internen Prüfgründe oder Freigaben.

Das System darf Daten erfassen, analysieren und Vorschläge vorbereiten. Es darf die erforderliche menschliche Entscheidung nicht selbst freigeben. Eine spätere Nachricht wie «behoben, keine weitere Hilfe nötig» hebt einen noch offenen Prüfbedarf nicht automatisch auf. Die Systemabschlussregeln aus FALL-07 gelten nur, wenn keine verpflichtende menschliche Entscheidung mehr aussteht.

Unabhängig von den vorstehenden FALL-10-Gründen schützt [FALL-06](#vorrang-einer-laufenden-mitarbeiterprüfung) jeden Fall im Status «Mitarbeiterprüfung erforderlich». Die Herkunft des Prüfbedarfs gibt dem System kein Recht, diesen Status selbstständig zu verlassen.

Eine Freigabe bezieht sich auf die konkret geprüfte Handlung. Sie ist keine pauschale Erlaubnis für weitere Kosten, weitere Aufträge oder einen späteren Fallabschluss. Entscheid, handelnder Mitarbeiter, Zeitpunkt, Bezug zur Handlung und fachlicher Grund bleiben intern nachvollziehbar. Die konkrete Bedienung wird in [Screen 03](../frontend/ansicht-03-mitarbeiter-falldetail.md) ausgearbeitet.

**Dringlichkeit und Prüfbedarf bleiben getrennt:** Eine starke Beschwerde führt nicht allein wegen ihres Tons zur Stufe «Kritisch». Umgekehrt beseitigt eine manuell niedrig gesetzte Dringlichkeit keinen anderweitig bestehenden menschlichen Prüfbedarf. Der Schutz manueller Einstufungen nach DRING-06 bleibt bestehen; das System darf sie auch in diesen Fällen nicht überschreiben.

Ausserhalb dieser Grenzen bleiben die ausdrücklich vereinbarten automatischen Schritte und Systemabschlüsse zulässig. Konkrete Erkennungsregeln und Grenzfallbeispiele, Prozessübergänge und die technische Durchsetzung werden im Service-/Prozessentwurf ergänzt. Diese Konkretisierung eröffnet die vereinbarten menschlichen Entscheidungsgrenzen nicht erneut und führt keine unbestätigten Kostenschwellen ein.

## FALL-11: Wiedervorlage

**Vereinbart am 10.10.2026:** Ein Mitarbeiter kann für einen aktiven Fall in UI3 eine optionale Wiedervorlage setzen: als Anzahl Tage, beispielsweise «in 3 Tagen», oder als festes Datum. Der daraus bestimmte Termin wird am Fall in PropertyFlow gespeichert und in UI3 angezeigt. UI2 zeigt das Wiedervorlagedatum als zusätzliche, auf- und absteigend sortierbare Spalte. Setzen einer Wiedervorlage führt zum achten Bearbeitungsstatus «Wartet auf Wiedervorlage».

Camunda verwaltet die Wartezeit als Timer im bestehenden führenden Fallprozess gemäss ADR-002. Beim Erreichen des wirksamen Wiedervorlagetermins endet die Wartezeit. Camunda veranlasst für den aktiven Fall «Mitarbeiterprüfung erforderlich». Der Mitarbeiter kann den Termin in UI3 ändern oder löschen, wenn der Fallprozess die Aktion zulässt. Eine Änderung setzt die Wartezeit auf den neuen Termin und führt zu «Wartet auf Wiedervorlage». Löschen entfernt den aktuellen Termin, beendet die zugehörige Wartezeit und setzt «In Bearbeitung». Die bisherige Termin- und Statushistorie bleibt nachvollziehbar. Beim Fallabschluss entfällt die offene Wiedervorlage; ein später eintreffendes Timerereignis darf den Fall weder wiedereröffnen noch seinen Abschlussstatus ändern.

| Aktion oder Ereignis | Folge für Termin und Status |
|---|---|
| Mitarbeiter setzt oder ändert die Wiedervorlage | Termin speichern, Camunda-Timer entsprechend führen; «Wartet auf Wiedervorlage». |
| Mitarbeiter löscht die Wiedervorlage | Aktuellen Termin entfernen, Timer beenden; «In Bearbeitung». |
| Mitarbeiter veröffentlicht eine Rückfrage während laufender Wiedervorlage | Rückfrage als offen veröffentlichen. Termin und Camunda-Timer gelten weiter. Der Fall bleibt auf «Wartet auf Wiedervorlage». [FALL-05](#fall-05-fachlicher-lebenszyklus) · [UC-010](../use_cases/UC-010-externe-fallnachricht-veroeffentlichen.md) |
| Wirksamer Termin erreicht | Den Fall zur Mitarbeiterprüfung vorlegen. |
| Neue Mieternachricht gespeichert | Wiedervorlage und Timer aufheben, Termin aus dem aktuellen Fallstand entfernen und den Fall sofort zur Mitarbeiterprüfung vorlegen. |
| Fall wird abgeschlossen | Offene Wiedervorlage beenden; «Abgeschlossen». |

«Wartet auf Wiedervorlage» setzt einen gespeicherten Termin voraus. Camunda veranlasst den Wartezustand über die fachliche Aktion zum Setzen der Wiedervorlage. Die Aktion führt Datum und Status gemeinsam nach. Konflikte mit zwischenzeitlichen Termin-, Status- oder Dringlichkeitsänderungen werden wie die Einstellungsänderungen aus FALL-06 geprüft; dies gilt auch beim Löschen. Ein alter Timer darf nach Änderung, Löschung oder Aufhebung keinen Statuswechsel mehr auslösen.

**Vereinbart am 13.11.2026:** Jede neu gespeicherte Mieternachricht hebt eine laufende Wiedervorlage auf. PropertyFlow übergibt den Nachrichteneingang an den bestehenden Camunda-Fallprozess. Camunda beendet die Wartezeit und veranlasst sofort «Mitarbeiterprüfung erforderlich». PropertyFlow entfernt den aktuellen Termin und speichert den Status gemeinsam. Die Nachricht ist im Fallverlauf verfügbar. Die Vorlage erfolgt unabhängig vom Inhalt der Nachricht und vom Ergebnis der automatischen Auswertung. Die weitere Auswertung nach DRING-05 und KOM-03 unterliegt der Bearbeitungsfreigabe nach FALL-06. Ein verspätetes Ergebnis darf die Mitarbeiterentscheidung weder vorwegnehmen noch den Fall während der Prüfung ändern.

Die Wiedervorlage dokumentiert die bewusste Mitarbeiterentscheidung, den aktuellen Fallstand bis zum Termin zurückzustellen; Löschen dokumentiert die Wiederaufnahme der Bearbeitung. Die bisherigen Prüfgründe bleiben intern nachvollziehbar. **Präzisiert am 13.10.2026:** Setzen, Ändern und Löschen der Wiedervorlage übernehmen die zu diesem Zeitpunkt offenen Prüfgründe als berücksichtigt in das interne Änderungsereignis. Sie verschwinden aus der Box «Prüfbedarf» und blockieren ohne neue Erkenntnisse keine spätere Statusfortsetzung. Offene Mieterfragen bleiben unverändert. Fehler, Versionskonflikte und wiederholte erfolgreiche Aufrufe erzeugen keinen zusätzlichen Berücksichtigungsnachweis. Bereits berücksichtigte Erkenntnisse setzen den Fall nach diesen Mitarbeiteraktionen nicht sofort wieder auf «Mitarbeiterprüfung erforderlich». Warten oder Wiederaufnehmen ist keine pauschale Freigabe von Kosten, Folgehandlungen oder Systemabschluss nach FALL-10 und erledigt keine offene Mieterfrage. Der manuelle Dringlichkeitsvorrang bleibt geschützt.

**Vereinbart am 10.10.2026 – optionale interne Begründung:** Beim manuellen Setzen, Ändern und Löschen der Wiedervorlage kann der Mitarbeiter eine Begründung angeben. Alle drei Aktionen bleiben ohne Begründung möglich. Eine eingegebene Begründung wird zusammen mit der erfolgreich bestätigten Aktion und deren Mitarbeiteridentität und Zeitpunkt gespeichert. Sie bleibt auch nach Löschung des aktuellen Termins der betreffenden Änderung zugeordnet. Für gemeinsame Speicherung, Fehler, Konflikte, Wiederholungen und ausschliesslich interne Verwendung gelten dieselben Regeln wie für [die Bearbeitungsentscheidung](#optionale-interne-begründung-der-bearbeitungsentscheidung). Es entsteht kein separates Memo; Mieteransicht, E-Mail und Kommunikations-LLM erhalten die Begründung nicht.

Terminsetzung, Terminänderung, Terminlöschung und automatische Aufhebung bleiben mit Zeitpunkt, Quelle und auslösender Grundlage nachvollziehbar. Treffen eine während der Wartezeit gespeicherte Mieternachricht und der Timerablauf zusammen, endet die Wiedervorlage. Camunda legt den Fall dafür nur einmal zur Mitarbeiterprüfung vor. Wird der Timer zuerst verarbeitet, gehört die Nachricht zur bereits ausgelösten Prüfung. PropertyFlow prüft dazu den aktuellen Fallstand. Wiederholte Nachrichtenereignisse oder überholte Timer dürfen weder eine zweite Mitarbeiteraufgabe auslösen noch eine später bewusst gesetzte Wiedervorlage aufheben. Bei konkurrierender Terminänderung, Löschung oder Abschluss bleiben die Regeln für den aktuellen Fallstand massgeblich.

### Terminberechnung: Kalendertage und Schweizer Zeit

**Bestätigt am 15.11.2026:** «In N Tagen» bedeutet: zum aktuellen lokalen Datum
in **Europe/Zurich** N Kalendertage addieren und am Zieldatum **08:00 Uhr** verwenden.
Ein fest gewähltes Datum wird ebenfalls um **08:00 Uhr Europe/Zurich** fällig.
Wochenenden und Feiertage verschieben den Termin nicht. Sommer- und Winterzeit
richten sich nach der Zeitzone am Zieldatum; keine feste UTC-Abweichung und keine
Berechnung als N mal 24 Stunden.

Beim erstmaligen Annehmen einer Setz- oder Änderungsaktion muss der berechnete
Zeitpunkt strikt in der Zukunft liegen. Vergangene Termine und ein bereits
erreichter Zeitpunkt werden abgelehnt; der Mitarbeiter muss einen zukünftigen
Termin wählen. Heute ist als festes Datum nur vor 08:00 Uhr zulässig. Eine
abgelehnte Änderung verändert weder den bestehenden Termin noch den Status oder
Timer. Massgeblich ist die serverseitige Zeitprüfung, nicht die Browseruhr.

Der geprüfte absolute Zeitpunkt wird an die bestätigte Aktion und Terminversion
gebunden und gespeichert. Wiederholungen desselben erfolgreich angenommenen
Auftrags berechnen «in N Tagen» nicht neu. Wird ein gültig angenommener Termin
wegen verzögerter Verarbeitung oder eines Ausfalls erst nach Fälligkeit vom
Prozess verarbeitet, ist das keine rückwirkend ungültige Eingabe: die aktuelle
Wiedervorlage wird bei Wiederaufnahme ohne weitere Tagesverschiebung fällig.
Versions-, Abschluss- und Konkurrenzschutz nach FALL-11 bleiben massgeblich.

Der BPMN-Timer verwendet den gespeicherten absoluten Zeitpunkt `followUpDueAt`
als ISO-8601-Zeitpunkt mit Offset oder UTC, nicht als relative Wartezeit. Der
zukünftige Dienst liefert ihn vor Timeraktivierung in der Wiedervorlage-Instanz;
bei bestätigter Änderung ersetzt er ihn versionsgebunden. Technische Umsetzung
der parallelen Verarbeitung bleibt offen.

 Die Timerentscheidung ändert die Verantwortung aus FALL-06 nicht: PropertyFlow hält die fachlichen Falldaten; Camunda führt den technischen Wartezustand.

### Nachricht, Timer und neuere Terminentscheidung

**Bestätigt am 15.11.2026 – Fortsetzungstabelle, Zeile 5:**

| Situation | Verbindliche Fortsetzung |
|---|---|
| Nachricht während Wiedervorlage gespeichert | Betroffene Wiedervorlage samt Timer beenden, aktuellen Termin entfernen und sofort Mitarbeiterprüfung veranlassen; nicht auf KI warten. |
| KI-Auswertung später fertig | Aktuelle Grundlage an derselben Mitarbeiteraufgabe ergänzen. Kein automatischer Abschluss und keine Aufhebung der Prüfung. |
| Timer und Nachricht gleichzeitig | Genau eine logische Mitarbeiterprüfung für die betroffene Wiedervorlage; Wiederholungen erzeugen keine zusätzliche Aufgabe. |
| Mitarbeiter setzt inzwischen bewusst einen neuen Termin | Verspäteter Kontakt oder Timer zur alten Wiedervorlage darf weder den neuen Termin aufheben noch dessen Wartevorgang abbrechen. |
| Fall inzwischen abgeschlossen | Keine neue Aufgabe, Statusänderung oder Wiederaufnahme; alte Ergebnisse bleiben auch nach späterer Wiedereröffnung unwirksam. |

Kontakt und Timer sind an Fall, führende Bearbeitung, konkreten Wiedervorlage-Aufruf
und Terminversion zu binden. Die bei Nachrichtenspeicherung betroffene Version
muss nachvollziehbar bleiben; sie darf bei verzögerter Verarbeitung nicht einfach
auf den inzwischen aktuellen Termin umgebogen werden. Eine bewusste neuere
Terminentscheidung muss den aktuellen Fallstand einschliesslich bereits
vorliegender Nachrichten berücksichtigen. Ein tatsächlich erst danach gespeicherter
neuer Kontakt kann dagegen die neue Wiedervorlage aufheben.

Die Dienste hinter `NWake`, `SStopFollowUp`, `WClearTimer`, `WUpdate` und
`SReviewStatus` müssen konkurrierende Änderungen versionsgeprüft koordinieren.
Die Kontaktbereitstellung an die aktuelle Mitarbeiterinstanz erfolgt vor dem
LLM-Aufruf. Timer- und Kontaktweg verwenden dieselbe nachvollziehbare Erledigung
und Prüfzuordnung der betroffenen Wiedervorlage. Bestehende Prüfung wird ergänzt,
keine zweite Aufgabe erzeugt. Manuelle Dringlichkeit bleibt erhalten.

Die Schutzprüfung muss bereits vor dem unterbrechenden Randereignis gelten:
Eine Prüfung erst nach Abbruch des Calls reicht nicht zum Schutz eines neuen
Timers. Terminänderung und Kontaktzustellung sind deshalb auch über die
Instanzgrenze konsistent zu koordinieren. Bereits erfolgreiche Wiederholung
bleibt ohne zusätzliche Wirkung; technische Zustellfehler benötigen nachholbare
Bereitstellung. Konkretes Synchronisations- und Engine-Verfahren bleibt vor
Ausführung zu implementieren. Die fachliche Regel ist damit festgelegt, der
BPMN-Entwurf allein ist kein Nachweis dieser Nebenläufigkeitsgarantien.

## FALL-12: Warten auf externe Rückmeldung

**Bestätigt am 15.11.2026:** Aus «In Bearbeitung» kann ein Mitarbeiter bewusst
«Auf externe Rückmeldung warten» wählen. Camunda veranlasst den Status
«Wartet auf externe Rückmeldung». Aus offener Mitarbeiterprüfung führt der Weg
zuerst über «Bearbeitung fortsetzen» nach «In Bearbeitung»; die externe Wartephase
umgeht keine menschliche Entscheidung.

Beispiel: Ein Handwerker wurde ausserhalb von PropertyFlow kontaktiert und seine
Rückmeldung steht aus. Die Aktion löst weder einen externen Auftrag noch eine
inhaltliche Nachricht aus. Bestehende Statusbenachrichtigungen nach BEN-01 bleiben
unberührt. Ein internes Memo zum Anlass oder später zum Ergebnis ist optional,
separat erfassbar und kein Pflichtfeld. Es beendet die Wartephase nicht.

| Auslöser während der externen Wartephase | Fortsetzung |
|---|---|
| Mitarbeiter wählt «Bearbeitung fortsetzen» | «In Bearbeitung»; weitere Mitarbeiterbearbeitung. |
| Mitarbeiter setzt eine Wiedervorlage | «Wartet auf Wiedervorlage»; bestehende Terminregeln nach FALL-11. |
| Mitarbeiter bestätigt Abschluss | «Abgeschlossen» nach FALL-07/FALL-10; ohne Nachrichtenpflicht. |
| Neuer zwingender Prüfbedarf | «Mitarbeiterprüfung erforderlich»; bewusste Mitarbeiterentscheidung nötig. |
| Externe Rückmeldung oder internes Memo allein | Keine Statusänderung; Mitarbeiter entscheidet über die Fortsetzung. |
| Neue Mieternachricht ohne neuen zwingenden Prüfbedarf | Nachricht verarbeiten; externe Wartephase bleibt bestehen. |

Externe Kommunikation erfolgt weiterhin ausserhalb von PropertyFlow. Es gibt
keinen automatischen Handwerker-Eingang. Mieternachrichten werden über den
bestehenden zentralen Eingang verarbeitet. Ein neuer zwingender Prüfbedarf wird
serverseitig anhand der geltenden Fachregeln festgestellt; die LLM-Empfehlung
allein autorisiert keinen Übergang. Keine automatische Rückkehr zu «In Abklärung»,
keine automatische Rückfrage und kein Systemabschluss aus dieser Wartephase.

Mitarbeiteraktionen und Prüfbedarf sind an die aktuelle Wartephase, Fallversion,
Aufgabe und führende Prozessinstanz zu binden. Gleichzeitige Aktionen dürfen nur
eine wirksame Fortsetzung erzeugen; alte Ereignisse dürfen nach Abschluss oder
Wiedereröffnung keine Aufgabe reaktivieren. Technische Umsetzung bleibt offen.

## Prüfkriterien

Diese Kriterien dienen der späteren Umsetzung und sind keine bereits ausgeführten Testnachweise. Die Prüfung fachlicher Services muss gemäss Projekt-Kontext ohne reale externe Systeme möglich sein.

### FALL-AK-01

Das Öffnen der Erfassung und eine abgewiesene Eingabe erzeugen keinen angenommenen Fall. Erst nach dauerhaftem Speichern wird die Case-ID mit Erfolg zurückgegeben; Ursprungsmeldung und fachlicher Eingang sind eindeutig nachvollziehbar.

Eine Beschreibung mit genau 2’000 UTF-16-Codeeinheiten nach Zeilenumbruchnormalisierung wird angenommen und vollständig gespeichert. Eine Beschreibung mit 2’001 Codeeinheiten wird auch bei direkter Übermittlung an den Server abgewiesen.

Bei direkter Übermittlung der Kontaktadresse `mıeter@example.test` mit punktlosem `ı` weist PropertyFlow die Eingabe ab. Es wird kein Fall angelegt.

### FALL-AK-02

Wiederholung derselben angenommenen Einreichung liefert dieselbe Case-ID. Doppelklick und verlorene HTTP-Antwort erzeugen weder einen zweiten Fall noch eine zweite Ursprungsnachricht. Abweichender Inhalt mit derselben Absende-ID wird als Konflikt behandelt.

### FALL-AK-03

Bei einem Ausfall von Camunda, Mail oder KI bleibt der angenommene Fall gespeichert. PropertyFlow wiederholt einen ausstehenden Prozessstart, ohne eine zweite unabhängige Bearbeitung zu erzeugen.

Vor bestätigter Registrierung in Camunda bleibt der Fall «Eingegangen» und noch nicht aktiv. Eine nur versendete Startanforderung oder unklare Camunda-Antwort aktiviert ihn nicht. Erst die bestätigte Registrierung des zugeordneten führenden Prozesses eröffnet die aktive Phase; Wiederholungen erzeugen keine zweite Registrierung. Ein späterer Camunda-Ausfall setzt einen bereits aktiven Fall nicht auf «Eingegangen» zurück.

Bei unerreichbarem Camunda zeigt die persönliche Fallansicht nach erfolgreicher Einreichung die Case-ID, die gespeicherten Angaben und «Eingegangen». Mitarbeitende können den zuletzt gespeicherten Stand lesen. Bearbeitungsaufrufe werden sowohl vor dem Start als auch bei Camunda-Ausfall während eines laufenden Falls serverseitig abgewiesen. Nach dem Wiederanlauf kann der Prozess einen zulässigen Statuswechsel veranlassen.

### FALL-AK-04

Eine unklare Objektzuordnung verhindert die Annahme nicht. Ursprüngliche Freitextangaben bleiben bei späterer Zuordnung nachvollziehbar. Eine syntaktisch gültige Kontaktadresse wird nicht als bestätigtes Mietverhältnis behandelt.

Ein Mitarbeiter kann in UI3 über «Ändern» einen Zuordnungsvorschlag auswählen oder Freitext eingeben und mit «Zuordnung bestätigen» bewusst speichern. Solange keine bestätigte Zuordnung vorliegt, ist das Feld mit der ursprünglichen Mieterangabe vorbelegt; bei späteren Änderungen mit der aktuellen bestätigten Zuordnung. Die Vorbelegung allein bestätigt nichts. Eine unveränderte ursprüngliche Mieterangabe kann direkt bestätigt werden. Beide Eingabewege erfüllen nach Entfernen äusserer Leerzeichen die Grenze von 1–200 Zeichen; innere Leerzeichen bleiben erhalten. Die Bestätigung oder Korrektur bleibt getrennt von der ursprünglichen Mieterangabe mit altem/neuem Wert, Mitarbeiter und Zeitpunkt nachvollziehbar. UI2 und UI3 zeigen die gespeicherte Zuordnung konsistent. Leere oder überlange Zuordnungen, ungültige strukturierte Objekt-/Wohnungsreferenzen und nicht berechtigte Schreibversuche werden abgewiesen. Zulässiger Freitext wird auch ohne Stammdatenreferenz akzeptiert. Abbruch vor dem Speichern, Speicherfehler und Konflikte überschreiben keine bestehende Zuordnung. Eingabe und laufender KI-Stream bleiben erhalten. Kontaktadresse, Empfänger, Mieterzugang und ursprünglicher Mieterkontakt werden durch die Zuordnung nicht geändert; andere Prüfgründe werden nicht erledigt.

### FALL-AK-05

Eine Ergänzung zu einem aktiven Fall behält Case-ID und Ursprungsdaten bei. Sie legt keinen neuen Fall an und startet keinen zweiten führenden Bearbeitungsprozess. Der aktuelle BPMN-Schritt bestimmt die weitere Verarbeitung.

### FALL-AK-06

Technische Retries, Incidents, KI-Ergebnisse und Mailzustände bewirken für sich allein keinen fachlichen Abschluss. Beim administrativen Ersatz des persönlichen Links bleibt der Fallstatus erhalten. Der Link gilt zeitlich unbegrenzt.

### FALL-AK-07

Der fachlich vorgesehene Abschluss wird in PropertyFlow konsistent übernommen. Die Nachrichtensperre wird nach [KOM-AK-07](fallkommunikation.md#kom-ak-07) auch bei gleichzeitigen Schreibversuchen geprüft. Eine gegebenenfalls gespeicherte Abschlussmitteilung umgeht diese Sperre nicht.

### FALL-AK-08

Mieteransicht und Backoffice lesen den fachlichen Status aus derselben PropertyFlow-Grundlage.
Direkte Statusänderungen über Web und MCP werden serverseitig abgewiesen. Nur der im zugehörigen Camunda-Schritt veranlasste Übergang ändert nach fachlicher Prüfung den gespeicherten Status.

### FALL-AK-09

Ein abgeschlossener Fall bleibt bei gültiger Berechtigung lesbar. 
Eine neue erfolgreiche Einreichung erzeugt einen separaten Fall; 
Screen 01 öffnet den alten Fall dabei nicht wieder.

### FALL-AK-10

Jeder der drei Gründe aus FALL-05 führt beim Eintritt in die erforderliche Prüfung eines aktiven Falls zu «Mitarbeiterprüfung erforderlich» mit passendem intern gespeichertem Hinweis, auch während automatischer Wiederholungen und bei mehreren gleichzeitigen Gründen. Eine anschliessende bewusste Mitarbeiterentscheidung zum direkten Wechsel auf «In Bearbeitung» folgt FALL-06, eine Entscheidung zur Wiedervorlage oder deren Löschung FALL-11. Derselbe bereits berücksichtigte Grund löst ohne neue Erkenntnisse keinen sofortigen Rückwechsel aus. Ein bloss ausstehender Versuch ohne Fehler erzeugt keinen Problemhinweis. Ein nach Abschluss festgestelltes Versandproblem eröffnet den Fall nicht wieder.

### FALL-AK-11

«Letzte Aktion des Mieters» folgt FALL-08. Neue angenommene Mieter-Nachrichten aktualisieren den Wert; Wiederholungen derselben Nachricht, reine Lesezugriffe und interne bzw. System-/Verwaltungsaktionen tun dies nicht.

### FALL-AK-12

Ein Mitarbeiter kann den fachlichen Abschluss nach FALL-07 bestätigen. Das System kann einen Fall ohne zusätzliche Einzelfreigabe abschliessen, wenn eine gespeicherte Mieter-Nachricht im aktuellen Fallzusammenhang eindeutig die Behebung und fehlenden weiteren Hilfebedarf bestätigt oder wenn eine hinterlegte Fachregel eindeutig keinen weiteren Bearbeitungsbedarf der Verwaltung ergibt. Bestehende verpflichtende menschliche Prüfungen bleiben wirksam. Beide Abschlussquellen führen zum selben Status «Abgeschlossen», erhalten Fall und Historie, sperren neue Nachrichten; der Abschlussstatus erzeugt keine Benachrichtigung. Quelle, Grund und auslösende Grundlage bleiben nachvollziehbar; Wiederholungen duplizieren den Abschluss nicht.

Ein Mitarbeiterabschluss ist ohne zusätzliches Begründungsfeld und ohne Pflichtmemo möglich. Der erfolgreiche Abschluss speichert intern «Vom Mitarbeiter geprüft: keine weitere Bearbeitung erforderlich.» mit Mitarbeiter, Zeitpunkt und Abschlussquelle; die bewusste Bestätigung ist die Grundlage. Der Vermerk erzeugt keine zusätzliche Nachricht oder Memo-Speicherung. Bei einem Systemabschluss werden der tatsächlich geprüfte Systemgrund und dessen Grundlage gespeichert. Ein freiwilliges Memo bleibt eine separate, vor dem Abschluss gespeicherte Dokumentation. Abbrechen oder ein fehlgeschlagener Abschluss erzeugen keinen erfolgreichen Abschlussnachweis.

### FALL-AK-13

Eine widersprüchliche Nachricht wie «Die erste Störung ist behoben, aber es läuft weiterhin Wasser aus» genügt nicht für «behoben, keine weitere Hilfe nötig». Das Wort «Glühbirne» ohne gesicherte Zuordnung zur Eigenverantwortung des Mieters, eine niedrige Dringlichkeit oder ein technischer Fehler genügt ebenfalls nicht zum Systemabschluss. Bei unklarer fachlicher Einordnung erfolgt Mitarbeiterprüfung. Der angenommene Fall wird bei fehlendem Verwaltungsbedarf abgeschlossen und nicht verworfen.

### FALL-AK-14

Kostenwirksame Massnahmen, verbindliche externe Aufträge wie ein Technikeraufgebot, sehr dringliche oder schwerwiegende Fälle sowie starke oder eskalierte Mieterbeschwerden lösen die menschliche Entscheidung nach FALL-10 aus. Ein aktiver Fall erhält bis zur Entscheidung «Mitarbeiterprüfung erforderlich» mit internem Grund. Ohne die erforderliche Mitarbeiterentscheidung erfolgen weder verbindliche Auslösung noch Systemabschluss. Ein unbekannter Preis und eine spätere Nachricht «behoben» umgehen den offenen Prüfbedarf nicht.

### FALL-AK-15

Eine starke Beschwerde kann Mitarbeiterprüfung erfordern, obwohl keine akute technische Gefahr vorliegt. Sie wird nicht allein aufgrund ihres Tons als «Kritisch» eingestuft. Eine manuelle Dringlichkeit bleibt geschützt; sie hebt einen unabhängigen Prüfbedarf nicht auf. Freigaben sind mit handelndem Mitarbeiter, Zeitpunkt, Grund und konkreter Handlung nachvollziehbar und gelten nicht pauschal für Folgehandlungen. Ohne verpflichtende menschliche Prüfung bleiben die vereinbarten Systemabschlüsse aus FALL-07 möglich.

### FALL-AK-16

Nur Mitarbeitende können die Wiedereröffnung über die vorgesehene UI3-Aktion anfordern. Versuche ohne berechtigte Mitarbeiterentscheidung werden abgelehnt. Camunda veranlasst die Rückkehr in die aktive Phase. Case-ID und bestehende Historie bleiben erhalten. Zeitpunkt und Mitarbeiteridentität sind nachvollziehbar. Bis zur bestätigten Wiedereröffnung bleibt die Nachrichtensperre wirksam. Danach gelten die Regeln für aktive Fälle.

Nach Abschluss und anschliessender Wiedereröffnung darf ein verspätetes automatisches Ergebnis aus der vorherigen Bearbeitung den Fall weder erneut abschliessen noch dessen Status, Dringlichkeit oder Kommunikation ändern. Auch ein altes Timerereignis bleibt unwirksam. Die Prüfung berücksichtigt den wiedereröffneten aktuellen Fallstand; bestehende historische Einträge bleiben erhalten. Die konkrete technische Durchsetzung wird im Integrationsvertrag festgelegt.

### FALL-AK-17

Ein berechtigter Mitarbeiter kann eine Wiedervorlage in Tagen oder als Datum setzen und später in UI3 ändern oder löschen. Setzen und Ändern führen zu «Wartet auf Wiedervorlage»; der gespeicherte Termin ist in UI3 und in der sortierbaren UI2-Spalte sichtbar. Löschen entfernt den aktuellen Termin, beendet die Wartezeit und führt zu «In Bearbeitung». Camunda führt den Timer im bestehenden Fallprozess. Am wirksamen Termin erhält der aktive Fall «Mitarbeiterprüfung erforderlich». Ein überholter, gelöschter oder wiederholter Timer löst keine zusätzliche Wiedervorlage aus. Nach Abschluss kann ein Timer den Fall nicht wiedereröffnen. Einstellungsänderungen werden gegen konkurrierende Änderungen geprüft; Historie und Mitarbeiteridentität bleiben nachvollziehbar.

### FALL-AK-18

Bei laufender Wiedervorlage eine Mieternachricht speichern und das Ergebnis der automatischen Auswertung zurückhalten. Camunda beendet die Wartezeit. Der Fall erhält ohne aktuellen Termin «Mitarbeiterprüfung erforderlich», und die Nachricht ist bereits im Verlauf sichtbar. Beim konkurrierenden Timerablauf und bei Wiederholung desselben Nachrichtenereignisses entsteht genau eine Mitarbeiteraufgabe für diese Wiedervorlage. Die Case-ID, der führende Fallprozess und eine bereits manuell gesetzte Dringlichkeit bleiben erhalten.

### FALL-AK-19

Bei der passenden Mitarbeiteraufgabe bestätigt der Mitarbeiter «Bearbeitung fortsetzen». Camunda veranlasst «In Bearbeitung». Die bestätigte Entscheidung ist mit Mitarbeiteridentität, Zeitpunkt, geprüftem Fallstand und den berücksichtigten Prüfgründen nachvollziehbar. Ein internes Memo oder eine verspätete Auswertung derselben Erkenntnisse ändert den Status nicht. Gleichzeitige oder wiederholte Bestätigungen derselben Aufgabe lösen höchstens einen wirksamen Übergang aus. Ein neuer Prüfgrund kann eine neue Mitarbeiteraufgabe auslösen.

### FALL-AK-20

«Bearbeitung fortsetzen» sowie Setzen, Ändern und Löschen der Wiedervorlage sind jeweils mit und ohne interne Begründung möglich. Eine eingegebene Begründung wird nur zusammen mit der erfolgreich bestätigten Aktion gespeichert und bleibt mit Änderung, Mitarbeiter und Zeitpunkt intern nachvollziehbar. Nach Terminlöschung bleibt die zugehörige Begründung erhalten. Abbrechen, Fehler und Konflikte speichern weder Aktion noch Begründung; wiederholte Aufrufe erzeugen keinen doppelten Nachweis. Die Begründung erzeugt keine separate Kommunikationsnachricht oder Memo-Speicherung und fehlt in sämtlichen Mieterausgaben, E-Mails und im Kommunikations-LLM-Kontext gemäss KOM-AK-11. Bestehende Status-, Timer- und menschliche Entscheidungsregeln gelten auch bei leerer Begründung.

### FALL-AK-21: Mitarbeiterprüfung bleibt unter menschlicher Kontrolle

Die folgenden Fälle werden jeweils mit ursprünglich systemseitigem Prüfbedarf und mit Prüfbedarf aus einer menschlichen Entscheidung geprüft:

1. Bei «Mitarbeiterprüfung erforderlich» eine neue Mieternachricht speichern. Neubewertung und gegebenenfalls Einzelprüfung offener Fragen finden statt; dieselbe Mitarbeiteraufgabe und der Status bleiben bestehen.
2. Der Mieter schreibt «Alles klar, der Fall ist abgeschlossen». Auch bei eindeutig erkannter Behebung oder fehlendem Verwaltungsbedarf bleiben Fall und Mitarbeiteraufgabe offen. Dasselbe gilt nach Beantwortung aller Rückfragen oder scheinbarem Wegfall des bisherigen Prüfgrundes.
3. Einen automatischen Abschluss vorbereiten, dann den Fall vor dessen Speicherung zur Mitarbeiterprüfung übergeben. Das verspätete Ergebnis darf weder den Status ändern noch die Aufgabe beenden. Wiederholungen und alte Timer umgehen den Schutz ebenfalls nicht.
4. Ein berechtigter Mitarbeiter bestätigt am aktuellen Fallstand Fortsetzung, Wiedervorlage oder Abschluss. Nur die bestätigte Aktion veranlasst den jeweiligen Übergang; Abbruch, veraltete Aufgabe oder Konflikt lassen die aktuelle Prüfung bestehen. Ein späteres Ergebnis darf die bestätigte Entscheidung nicht zurücknehmen.

KOM-AK-17 prüft den durchgängigen Nachrichtenempfang in allen aktiven Status. DRING-AK-09 und DRING-AK-10 prüfen die zulässige Neubewertung bei geschützter Mitarbeiterentscheidung.

### FALL-AK-22: Mitarbeiterabschluss bei laufender Auswertung und veraltetem Fallstand

1. Neue Mieternachricht speichern und deren KI-Auswertung anhalten. Ein Mitarbeiter bestätigt den Abschluss am aktuellen Fallstand einschliesslich dieser Nachricht. Der Abschluss gelingt ohne Warten auf das Modell; Nachricht und Verlauf bleiben erhalten, es entsteht kein Abschlussmailauftrag.
2. Dieselbe Abschlussaktion aus einem Stand vor der neuen Nachricht senden. Ablehnung vor dem User-Task-Abschluss: dieselbe Aufgabe bleibt offen, kein erfolgreicher Abschlussnachweis und kein Abschlussmailauftrag.
3. Nach bestätigtem User-Task-Abschluss, aber vor fachlichem Abschluss eine weitere Nachricht oder relevante Auswertung speichern. Der Abschluss wird nicht gespeichert; der Prozess erreicht erneut Mitarbeiterprüfung mit genau einer neuen Aufgabe und neuem Aufgabenschlüssel. Die alte Abschlussfreigabe und alte Browseraktionen dürfen sie nicht abschliessen. Die neue Aufgabe verlangt eine bewusste Entscheidung am aktualisierten Stand.
4. Konfliktmeldung beziehungsweise deren Verarbeitung wiederholen. Keine weitere Mitarbeiteraufgabe, kein zweiter Konfliktnachweis für denselben Vorgang, kein Abschlussmailauftrag. Empfang neuer Mieternachrichten bleibt möglich.
5. Abschluss und KI-Ergebnis gleichzeitig speichern lassen: Wird zuerst eine relevante Auswertung wirksam, muss die ältere Mitarbeiterentscheidung erneut geprüft werden; wird zuerst der Abschluss wirksam, darf das alte Ergebnis nichts mehr am Fall ändern. Dasselbe gilt für verspätete Ergebnisse nach Wiedereröffnung gemäss FALL-AK-16.
6. Vorübergehenden Speicherfehler und verlorene Job-Abschlussbestätigung getrennt prüfen. Der technische Fehler allein löst keinen fachlichen Konfliktpfad aus. Ein bereits gespeicherter identischer Abschluss wird beim Retry bestätigt, ohne erneute Mitarbeiterprüfung, Wiedereröffnung oder zusätzlichen logischen Mailauftrag.

Diese Prüffälle beschreiben Anforderungen an die spätere Backend-/Camunda-Integration, keine bereits ausgeführten Laufzeittests.

### FALL-AK-23: Gemeinsame Voraussetzungen des Systemabschlusses

Beide Gründe jeweils in «In Abklärung» und «Wartet auf Mieterantwort» mit vollständigen Belegen prüfen. Dieselben Vorschläge in jedem anderen Bearbeitungsstatus dürfen keinen automatischen Abschluss auslösen. Zusätzlich offene Mitarbeiterentscheidung nach FALL-10, offene Objektzuordnung, Analysefehler und klärungsbedürftigen Versand jeweils einzeln prüfen: keine Freigabe. Eine frühere Bearbeitungsentscheidung oder niedrige manuelle Dringlichkeit umgeht diese Grenzen nicht. Bestehende Mitarbeiteraufgaben und Wartephasen werden nicht dupliziert oder verdrängt.

### FALL-AK-24: Belegte Behebung des gesamten Anliegens

Die vier Beispiele zum Abschlussgrund «Problem behoben» sowie «Danke», Schweigen, «Bitte schliessen» und eine mehrdeutige Bestätigung gegen einen versionierten Evaluationsbestand prüfen. Nur eine eindeutige Behebung des gesamten offenen Sachverhalts ohne weiteren Hilfebedarf kann den Grund erfüllen. Bei zwei Teilproblemen genügt die Behebung eines Problems nicht. Spätere widersprechende Nachrichten verhindern den Abschluss auf alter Grundlage. Bei eindeutig erledigtem Gesamtanliegen dürfen gegenstandslos gewordene Rückfragen den erlaubten Abschluss nicht allein blockieren; ihr Antwortbedarf endet durch Abschluss, ohne eine inhaltliche Beantwortung zu erfinden.

### FALL-AK-25: Freigegebene und vollständig anwendbare Zuständigkeitsregel

Eine ausdrücklich für Systemabschluss freigegebene Testregel mit Kennung, Version und Geltungsbereich gegen passende sowie abweichende Fälle prüfen. Fehlende Voraussetzungen, unbekannte oder nicht mehr gültige Regelreferenz, anderer Geltungsbereich, widersprüchliche Quellen, eine Ausnahme, ein begründeter Mietereinwand und ein nicht abgedecktes Teilproblem erlauben keinen automatischen Abschluss. «Licht defekt» ohne weitere Angaben und eine beschädigte Fassung dürfen nicht allein wegen des Stichworts einer Leuchtmittelregel zugeordnet werden. Ohne geeignete freigegebene Regel ist der Grund nicht anwendbar. Erfolgreiche Anwendung hält Regel und Belege fest und behauptet keine Reparatur.

### FALL-AK-26: Aktualität, Nachweis und wiederholte Ausführung

Zwischen Empfehlung, automatischer Veröffentlichung und Abschlussspeicherung jeweils eine neue Mieternachricht, eine Mitarbeiterentscheidung oder eine Änderung der gültigen Regelgrundlage einspielen. Kein veralteter Vorschlag darf dadurch wirksam werden. Eine noch nicht ausgewertete neue Nachricht blockiert den Systemabschluss auf alter Grundlage; im Gegensatz dazu gelten für bewusste Mitarbeiterabschlüsse FALL-AK-22 und die dortige Aktualitätsprüfung. Doppelte und bereits erfolgreich verarbeitete Ergebnisse erzeugen weder zusätzlichen Abschluss noch Mailauftrag oder einen künstlichen Prüfgrund. Fremde, unbekannte oder unpassende Belegreferenzen und ein hoher Modell-Sicherheitswert ohne Beleg ersetzen keine gültige Grundlage. Nach erfolgreichem Abschluss gespeicherter Grund, Belege und gegebenenfalls Regelversion sind nachvollziehbar. Laufzeit- und Evaluationsergebnisse sind bei Umsetzung gesondert nachzuweisen; diese Prüfkriterien sind noch keine ausgeführten Tests.

### FALL-AK-27: Versionsgebundene Wiedervorlage bei Mieterkontakt

Nachricht bei blockiertem LLM speichern: Termin und Timer enden, genau eine
Mitarbeiterprüfung beginnt vor dem KI-Ergebnis. Späteres Ergebnis ergänzt
höchstens dieselbe Aufgabe. Timer und Kontakt in beiden Reihenfolgen sowie
wiederholt zustellen: eine logische Prüfung, keine doppelte Aufgabe.

Terminversion A durch eine bewusste Entscheidung am aktuellen Fallstand durch B
ersetzen; verzögerten Kontakt/Timer für A zustellen: B und sein Timer bleiben
bestehen. Kontaktzustellung und Terminänderung auch unmittelbar am Call-Abbruch
konkurrieren lassen; kein Abbruch von B durch A. Ein neuer Kontakt nach B hebt B
hingegen nach derselben Regel auf. Abschluss vor und während der Verarbeitung
sowie späteres Eintreffen nach Wiedereröffnung prüfen: keine Reaktivierung.
Dies sind künftige Integrationsprüfungen, keine bereits ausgeführten Laufzeittests.

### FALL-AK-28: Wiedereröffnung mit neuem Prozessdurchlauf

Berechtigte Wiedereröffnung jeweils mit und ohne aktuellen Prüfbedarf prüfen:
neue führende Instanz, gleiche Case-ID und erhaltene Historie; direkter Einstieg
in Mitarbeiterprüfung beziehungsweise Bearbeitung ohne Erstklassifikation.
Vor Commit bleibt der Fall geschlossen; danach ist der neue Nachrichteneingang
verfügbar. Doppelte Bestätigung, verlorene Startantwort und doppelte Instanzstarts
bewirken nur einen neuen aktiven Durchlauf. Unberechtigter Auftrag, aktiver Fall
oder nicht beendete Vorgängerinstanz darf keine Zuordnung überschreiben.

Alte Timer, Ergebnisse, User-Task-Aktionen und Antwortbedarfe bleiben unwirksam.
Abbruch/technischer Fehler erzeugt keine erfolgreiche Wiedereröffnung; wiederholter
Commit bleibt idempotent. Eingang einer neuen Nachricht direkt nach Aktivierung
verarbeitet nur die neue Instanz. Dies sind spätere Integrationsprüfungen,
keine bereits ausgeführten Laufzeittests.

### FALL-AK-29: Externe Wartephase

Eintritt nur durch berechtigte Mitarbeiteraktion aus «In Bearbeitung» prüfen;
keine Umgehung einer offenen Mitarbeiterprüfung. Kein externer Auftrag und keine
inhaltliche Nachricht durch den Eintritt, optionales Memo ohne Statuswirkung.
Fortsetzen, Wiedervorlage und Abschluss jeweils mit aktuellem Fallstand prüfen.
Neue Mieternachricht ohne zwingenden Prüfbedarf lässt die Wartephase bestehen;
neuer zwingender Prüfbedarf führt genau einmal zur Mitarbeiterprüfung.
Memo und externe Rückmeldung allein beenden keine Aufgabe. Systemabschluss und
automatische Rückkehr in Abklärung bleiben gesperrt. Gleichzeitige Mitarbeiter-
und Prüfaktionen sowie alte Ereignisse nach Abschluss/Wiedereröffnung prüfen.
Dies sind geplante Integrationsprüfungen, keine ausgeführten Laufzeittests.

### FALL-AK-30: Berechnung und Fälligkeit der Wiedervorlage

«In 3 Tagen» und festes Datum mit identischem Zieldatum ergeben denselben
08:00-Uhr-Termin in Europe/Zurich. Monats-/Jahreswechsel, Wochenende, Feiertag
sowie Wechsel zwischen Sommer- und Winterzeit prüfen. Keine Verschiebung auf
Arbeitstage und keine Berechnung als 72 Stunden. Heute vor 08:00 Uhr ist zulässig,
ab 08:00 Uhr nicht; vergangene Termine werden ohne Status-/Terminänderung abgelehnt.
Eine andere Browserzeitzone darf das Ergebnis nicht verändern.
Wiederholung nach Mitternacht behält den bereits angenommenen Zeitpunkt.
Verzögerte Aktivierung eines gültig angenommenen, inzwischen fälligen Termins
führt ohne neuen Aufschub zur versionsgeprüften Mitarbeiterprüfung; gelöschte,
ersetzte oder nach Fallabschluss verspätete Timer bleiben wirkungslos.
Dies sind geplante Integrationsprüfungen, keine ausgeführten Laufzeittests.

## Offene Entscheidungen und Weiterentwicklung

- Konkrete Case-ID-Darstellung und Beziehung zur technischen Prozessreferenz.
- Technische Umsetzung der bestätigten Terminberechnung (Kalendertage, 08:00 Uhr Europe/Zurich), Zuordnung der bewussten Warteentscheidung zum geprüften Fallstand sowie Koordination von Timer, Nachrichteneingang, Terminänderung, Löschung und Abschluss nach FALL-11.
- Konkrete Übergangsbedingungen, technische Statuswerte und Mapping der vereinbarten Bearbeitungsstatus aus FALL-05 zum BPMN-Modell; Erkennungsregeln, Grenzfallbeispiele und technische Durchsetzung der menschlichen Entscheidungsgrenzen aus FALL-10.
- Technische Durchsetzung und Evaluation der bestätigten Systemabschlussregeln aus FALL-07, konkreter freigegebener Katalog für mieterseitige Eigenverantwortung sowie Veröffentlichungsregeln und Fehlerbehandlung bei automatischer Antwort vor Abschluss. Die beiden Gründe, ihre Nachweise und die Beschränkung auf «In Abklärung» beziehungsweise «Wartet auf Mieterantwort» sind bestätigt. Weitere automatische Gründe benötigen eine gesonderte fachliche Entscheidung.
- Technische Koordination von Annahme, Prozessstart, Übergabe der Mitarbeiterentscheidung, Statusübernahme und Nachrichtensperre. Dazu gehören Aufgabenzuordnung, Verhalten bei unklarer Camunda-Antwort, Wiederholungsschutz und Aufbewahrungsdauer.
- Technische Umsetzung der bestätigten UI3-Zuordnungsänderung nach FALL-04, einschliesslich Auswahlvorschlägen und Freitext, Berechtigungsprüfung, Konflikten und nachvollziehbarer Speicherung; weitere Backoffice-Korrekturen sind gesondert festzulegen.
- E-Mail-Eingangsvertrag sowie spätere Regelungen für Archivierung, Löschung oder Zusammenführung; technische Umsetzung des bestätigten neuen Prozessdurchlaufs bei mitarbeiterseitiger Wiedereröffnung; Bedienung, Zielstatus und optionale Begründung als Memo sind unter FALL-07 vereinbart.

Änderungen an diesen fachlichen Regeln werden hier gepflegt. Kommunikationsregeln bleiben in [fallkommunikation.md](fallkommunikation.md), Darstellung und Bedienung in den Screen-Spezifikationen. Bei Umsetzung eines bislang offenen Punktes werden die betroffenen Use-Cases und gegebenenfalls Architekturentscheidungen gezielt fortgeschrieben.

### Nachweis gelöschter Wiedervorlagen – Präzisierung vom 13.10.2026

Das interne Löschereignis enthält den vorherigen Termin und den neuen Zustand, beispielsweise «Wiedervorlage gelöscht: bisher 20.10.2026 → keine Wiedervorlage», mit Änderungszeitpunkt und optionaler interner Begründung. Dies gilt auch, wenn kein älteres Setzereignis vorhanden ist. Eine Wiederholung derselben erfolgreichen Aktion erzeugt keinen weiteren Eintrag; ohne vorhandenen Termin erzeugt eine neue Löschaktion keinen fingierten Löschvorgang.
