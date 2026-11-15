# Verträge der ausgelagerten BPMN-Teilprozesse

Stand 15.11.2026 · Fachentwurf 0.10 · nicht ausführbar, keine Backend-Implementierung.

Die Aufteilung in eigene Dateien ist bestätigt. Die folgenden technischen
Übergaben konkretisieren den Entwurf; sie sind noch kein implementierter
Java-/HTTP-Vertrag. Die Fachregeln aus KOM-03 bis KOM-06 und FALL-05 bis FALL-11
bleiben bestehen. Der Hauptprozess behält allein die führende Fallzuordnung.

## Dateien und Aufrufe

| Datei | Prozess-ID | Aufrufstelle |
|---|---|---|
| [propertyflow-fallprozess-entwurf.bpmn](propertyflow-fallprozess-entwurf.bpmn) | `PropertyFlow_Case_Draft` | Asynchroner Fallstart |
| [mieterantwort-pruefen.bpmn](mieterantwort-pruefen.bpmn) | `PropertyFlow_TenantReply_Draft` | `Reply` im Hauptprozess |
| [mitarbeiterbearbeitung.bpmn](mitarbeiterbearbeitung.bpmn) | `PropertyFlow_Staff_Draft` | `Staff` im Hauptprozess |
| [wiedervorlage.bpmn](wiedervorlage.bpmn) | `PropertyFlow_FollowUp_Draft` | `FollowUp` im Mitarbeiterprozess |
| [mieternachricht-verarbeiten.bpmn](mieternachricht-verarbeiten.bpmn) | `PropertyFlow_TenantMessage_Draft` | `ProcessTenantMessage` im Ereignis-Teilprozess `TenantInbox` |

`ActiveCase` und der Ereignis-Teilprozess `TenantInbox` bleiben eingebettet im
Hauptprozess. Die Nebenaktion für Mitarbeiterrückfragen während Wiedervorlage
bleibt eingebettet in der Wiedervorlage. Es gibt weiterhin genau einen externen
Empfänger für gespeicherte Mieternachrichten und einen führenden Fallprozess.
Call Activities erzeugen zusätzliche **untergeordnete** Instanzen, keine neuen Fälle.

Die Calls verwenden statische Prozess-IDs und `bindingType="deployment"`.
Eine spätere ausführbare Fassung muss die fünf Dateien zusammen versioniert
bereitstellen. Jetzt sind alle Prozesse `isExecutable="false"`; kein Deployment.
Die Calls kopieren nur explizite Eingaben und geben nur explizite Ergebnisse zurück.
[Camunda: Call Activities](https://docs.camunda.io/docs/8.9/components/modeler/bpmn/call-activities/)

## Eingaben und Ergebnisse

Die Variablennamen in den BPMN-Mappings sind ein gemeinsamer technischer Entwurf.
Die ausführbaren Worker, Ergebniszuweisungen und Gateway-Bedingungen fehlen noch.

- `caseId`: Fallreferenz, kein Zugriffsbeweis.
- `leadingProcessInstanceKey`: unveränderter Schlüssel der führenden Instanz.
  Im Hauptprozess stammt er aus `processInstanceKey`; bei verschachtelten Calls
  wird er unverändert weitergegeben. Der eigene Schlüssel der Kindinstanz kommt
  aus vertrauenswürdigen Engine-Metadaten und überschreibt niemals die Fallzuordnung.
- `entryContextRef`: Referenz auf den geprüften Eintrittsauftrag, einschliesslich
  Fallstand, Herkunft des Aufrufs und gegebenenfalls Mitarbeiterentscheidung oder
  Wiedervorlage. Der aufrufende Dienst muss sie vor dem Call bereitstellen.
  Keine vollständigen Falltexte, Tokens oder internen Memos in den Variablen.

Die aufrufende Instanz stellt dafür `replyEntryContextRef` vor `Reply`,
`staffEntryContextRef` vor `Staff` beziehungsweise `followUpEntryContextRef`
vor `FollowUp` bereit. Das jeweilige Input-Mapping benennt diese Referenz im
Kindprozess in `entryContextRef` um. Auch bei erneutem Aufruf ist eine aktuelle
Eintrittsgrundlage erforderlich.

Die drei fachlichen Entscheidungsprozesse liefern `outcome` und `decisionRef`. Die Referenz bezeichnet
die nachvollziehbar gespeicherte Entscheidungsgrundlage samt Versionen und
Aufrufzuordnung; sie ist keine dauerhafte Freigabe späterer Aktionen.

| Kindprozess | Erlaubtes `outcome` | Erzeugendes Ende | Zielvariablen beim Aufrufer |
|---|---|---|---|
| Mieterantwort | `CLOSE`, `REVIEW`, `QUESTION`, `WAIT` | `RClose`, `RReview`, `RQuestion`, `RWait` | `replyOutcome`, `replyDecisionRef` |
| Mitarbeiterbearbeitung | `CLOSE`, `WAIT_FOR_REPLY` | `SCloseEnd`, `SReplyEnd` | `staffOutcome`, `staffDecisionRef` |
| Wiedervorlage | `REVIEW`, `CONTINUE`, `CLOSE` | `WReviewEnd`, `WProgressEnd`, `WCloseEnd` | `followUpOutcome`, `followUpDecisionRef` |

Das normale Ende eines Kindprozesses schliesst den Fall nicht ab. Der letzte
entscheidende Dienst muss Ergebnis und Referenz passend zum Ende setzen; das
ist noch nicht implementiert. Der Aufrufer prüft Rückgabe, Aufrufzuordnung und
aktuellen Fallstand. Fehlende/unbekannte Werte sind technische Vertragsfehler,
keine implizite Abschlussfreigabe und kein ungeprüfter Default-Mitarbeiterpfad.
Status, Dringlichkeit und Nachrichtenzähler werden nicht pauschal zurückkopiert.

## Laufende Aktualisierungen über Instanzgrenzen

Call-Eingaben sind keine live gemeinsam genutzten Variablen. Insbesondere
weckt eine Änderung im Hauptprozess nicht automatisch `RMessage` im Kindprozess
oder `SContactDuringFollowUp` im Mitarbeiterprozess.

Für die Umsetzung ist eine dauerhaft nachvollziehbare Zuordnung nötig:
führende Instanz → Call-Aufruf → aktuelle Kindinstanz → relevanter Empfangsbereich.
Sie wird aus Engine-Metadaten geprüft, nicht aus frei übergebenen Benutzerwerten.
`RStatus`, `SReviewStatus` und `WSave` prüfen beim Eintritt ihren Instanzbezug und
stellen den aktuellen Stand für ihren Bereich bereit, bevor darauf gewartet wird.
Das begründet keine zweite führende Fallzuordnung.

| Anlass | Quelle | Zuständiger Zielbereich / Wirkung |
|---|---|---|
| Gespeicherte Mieternachricht | HTTP/MCP → Hauptprozess `TenantInbox` | Unveränderter externer Eingang; öffentliche Bestätigung wartet auf dessen Korrelation, nicht auf die LLM-Auswertung. |
| Aktuelle Auswertung fertig | `NApply` / zentrale Verarbeitung | Ergebnis dauerhaft sichern; Bereitschaft für die aktuelle Antwort-Kindinstanz herstellen. `tenantEvaluationVersion` und Verbrauch sind lokal zu dieser Kindinstanz zu führen. Fallinhalt aus PropertyFlow lesen. |
| Nachricht während Mitarbeiterprüfung | Zentrale Verarbeitung | Dieselbe aktuelle Aufgabe erhält die neue Grundlage; keine automatische Erledigung oder neue Aufgabe. Der Mitarbeiterprozess liest den aktuellen Fallstand. |
| Kontakt während Wiedervorlage | `NWake`, vor dem LLM-Aufruf | Versionsgebundenen Kontakt in die aktuelle **Mitarbeiterinstanz** bringen, in der die Call Activity `FollowUp` und ihr Randereignis liegen. Nicht nur im Hauptprozess setzen. |
| Wiedervorlage gesetzt/geändert | `WSave` / `WUpdate` | Aktuelle Wiedervorlage-Version auch im zuständigen Mitarbeiterbereich bereitstellen; gespeicherte Kontakte gegen denselben Stand prüfen. |
| Mitarbeiteraktionen zur Wiedervorlage | Autorisierter Dienst | Engine-Ziel der aktuellen Wiedervorlage-Kindinstanz serverseitig ermitteln. Die bestehenden Änderungs-/Lösch-/Abschluss-/Rückfragenereignisse sind keine Mieternachrichten. |

Bereitstellung bedeutet hier eine ausdrücklich noch zu implementierende interne,
versionierte und wiederholbare Übergabe an den richtigen Scope. Transport und
Engine-API werden im technischen Integrationsvertrag konkretisiert; es wird
keine neue öffentliche Schnittstelle und kein weiterer externer Mieterempfang eingeführt.

Nachrichten vor der Registrierung eines Kindes gehen nicht verloren: Quelle
und aktueller Stand bleiben gespeichert; Registrierung und Nachlieferung müssen
ohne Lücke zusammenwirken. Ein bloss einmaliger Snapshot genügt nicht.
Verbrauch ist pro Aufruf nachvollziehbar; ein neues Kind darf weder alte
Ergebnisse erneut entscheiden noch einen noch offenen neuen Eingang verlieren.
Verspätete Zustellung an eine beendete Instanz wird wirkungslos oder anhand der
aktuellen Zuordnung neu zugestellt; sie reaktiviert keine alte Aufgabe.
Ein Versand-/Bereitstellungsfehler darf nicht als erfolgreicher Empfang gelten.

## Abbruch, Rechte und Gleichzeitigkeit

`SContactDuringFollowUp` bleibt an der Call Activity `FollowUp` im Mitarbeiterprozess.
Bei passendem Kontakt unterbricht es den Call und die Kindinstanz einschliesslich
Timer/Nebenaktion. Der Randereignispfad darf keine erst beim normalen Call-Ende
erwartete Rückgabe voraussetzen; aktuelle Wiedervorlagedaten liegen in PropertyFlow.
Eine neuere bewusste Terminentscheidung darf ein alter Kontakt nicht aufheben.

Bei fachlichem Abschluss endet `ActiveCase` mit seinen aktiven Calls und ihrem
Nachrichtenempfang. Späte Worker-/LLM-Ergebnisse werden zusätzlich serverseitig
abgewiesen. Die automatische Nachricht samt Versandauftrag entsteht vor dem fachlichen
Abschluss. Der Abschluss selbst erzeugt keine Mail. Interne Übergaben dürfen Abschluss oder Mitarbeiterentscheidungen
nicht rückgängig machen.

Die bisherige Beschreibung in ADR-002/SS-003 bindet Aufgaben an den führenden
Prozess. Vor ausführbarer Umsetzung ist diese Bindung für Calls zu präzisieren:
Der Dienst muss die autorisierte aktuelle Aufgabe über ihre verifizierte
Eltern-Kind-Zuordnung demselben Fall zuordnen. Weder blosse Gleichheit mit dem
Root-Schlüssel noch ein beliebig mitgesendeter Kindschlüssel genügt. Die bestehende
Zugriffsprüfung wird hierdurch nicht gelockert. Hauptprozess und HTTP-Verträge
werden nicht stillschweigend umgestellt.

## Nachweise vor Ausführung

- Alle Calls lösen genau das zugehörige Prozessmodell derselben Bereitstellung auf.
- Nur erlaubte Eingaben/Rückgaben passieren die Grenze; alte Kindvariablen
  überschreiben keine neue Mitarbeiterentscheidung oder Fallversion.
- Nachricht vor Kindstart, während Registrierung, während Warten und nach
  Kindende prüfen; keine verlorene Aktualisierung und keine doppelte Wirkung.
- Antwortauswertung aktualisiert den richtigen Kindbereich; früh fertiges
  Ergebnis wird erkannt, verbrauchtes Ergebnis löst keine Schleife aus.
- Kontakt beendet die richtige Wiedervorlage samt Timer vor KI-Ende; verspäteter
  Kontakt hebt keinen neueren Termin auf. Abbruch liefert keine normalen Outputs.
- Mitarbeiter-Task nur im aktuellen, autorisierten Aufrufbaum freigeben;
  fremde, alte oder bereits abgeschlossene Kindinstanzen zurückweisen.
- Abschluss während laufender Kindarbeit und interner Nachlieferung prüfen;
  kein Wiederaufleben und keine zusätzliche Abschlussmail.

Diese Laufzeitprüfungen stehen aus. XML-Schema, Modeler-Import und Modellverknüpfungen
belegen nur die strukturelle Aufteilung. Zeile 4 der fachlichen Fortsetzungstabelle
ist in KOM-03 bestätigt; KOM-AK-25 beschreibt die ausstehenden Laufzeitprüfungen.

## Nachrichtenverarbeitung als eigener Aufruf (Stand 0.10)

`TenantInbox` bleibt nicht unterbrechend im aktiven Hauptprozess eingebettet:
`NStart` (einziger externer Empfang) → `ProcessTenantMessage` → `NReceptionEnd`.
Jeder Eingang startet einen eigenen Aufruf der Verarbeitung. Der Kindprozess
beginnt mit einem gewöhnlichen Start, ohne weitere Nachrichten-Subscription.
Das Ende des Aufrufs beendet nur diesen Eingang; der Empfang bleibt aktiv.

Explizite Inputs sind `caseId`, `leadingProcessInstanceKey` aus dem führenden
`processInstanceKey` und `tenantMessageId`. Die Nachrichten-ID muss je Empfang
lokal aus der korrelierten, bereits gespeicherten Nachricht bereitgestellt werden.
Diese technische Eingangszuordnung ist noch zu implementieren; keine globale
überschreibbare letzte Nachrichten-ID. `NIdentify` prüft Nachricht, Fall und
verifizierte Aufrufzuordnung; Wiederholungen bleiben fachlich idempotent.

Es gibt **keine Outputs** (`propagateAllChildVariables=false`): Weder Empfehlung
noch Status werden beim Call-Ende in den Hauptprozess kopiert. `NApply` sichert
die aktuelle Grundlage und stellt sie ausdrücklich im benötigten Haupt- oder
Kindbereich bereit. `NWake` liefert den versionsgebundenen Kontakt vor dem
LLM-Aufruf in die aktuelle Mitarbeiterinstanz. Die Regeln zur lückenlosen
Nachlieferung und zum Vorrang menschlicher Entscheidungen gelten unverändert.
Auch diese Übergaben sind noch keine implementierte Backend-Funktion.

Die HTTP-Empfangsbestätigung wartet weiterhin nicht auf diese Auswertung.
Beim Abschluss beendet `ActiveCase` auch laufende Nachrichten-Kindaufrufe;
späte externe Ergebnisse benötigen weiterhin die serverseitige Abschlussprüfung.
Vor Ausführung zusätzlich prüfen: zwei gleichzeitige Nachrichten behalten ihre
jeweilige ID, Wiederholungen erzeugen keine zweite Wirkung, Abbruch verhindert
späte Änderungen und ein Verarbeitungsfehler deaktiviert nicht den Empfang.

## Vereinfachter Abschluss (Stand 0.13)

Die fachlichen Kindprozess-Ergebnisse bleiben unverändert. `CLOSE` auf dem
Mieterantwortweg führt im Hauptprozess zuerst zur individuellen LLM-Nachricht
mit Worker-Prüfung und zuverlässigem Versandauftrag. Der bestätigte
Mitarbeiterabschluss führt direkt zu `Close`, ohne Nachricht. Kein abschliessender
Mail-Task und keine zweite Abschlussbenachrichtigung. Technische Fehler folgen
Job-Retries/Incidents; unzulässiger Nachrichteninhalt führt vor Veröffentlichung
zur Mitarbeiterprüfung. Aktuelle Grundlage und Wiederholungsschutz bleiben Pflicht.

## Mitarbeiternachricht während Prüfung (Stand 0.14)

`Message_StaffReviewSend` startet `SMessageSide` nicht unterbrechend in der
aktuellen Mitarbeiterinstanz. Nur autorisierte Aufträge zur aktuellen offenen
`SReview`-Aufgabe sind zulässig. Die tatsächliche Engine-Korrelation und
Rückmeldung an den sendenden HTTP-Aufruf sind noch festzulegen; dies ändert
keinen öffentlichen HTTP-Vertrag. Keine zusätzliche externe Mieter-Subscription.

Die Auftragsreferenz bleibt pro Nebenaktion lokal. Der Worker lädt den bestätigten
Text und Antwortbedarf über den zuständigen Dienst, prüft erneut den aktuellen
Aufgabenstand und speichert Nachricht/Versandauftrag idempotent. Er schreibt keine
Hauptpfadvariablen `outcome` oder `decisionRef` und beendet keine User Task.
Parallele Sendungen überschreiben einander nicht. Beendete Prüfaufgabe oder
Abschluss verhindert neue Veröffentlichung aus einem alten Auftrag; schon
veröffentlichte Nachricht bleibt bestehen. Laufende Nebenaktionen müssen bei
Fortsetzung ohne alte fachliche Wirkung aufgelöst werden; ein Incident kann die
technische Kindinstanzbeendigung verzögern. Fachliche Regeln: KOM-01, Tests: KOM-AK-27.

## Wiedereröffnung: neuer führender Durchlauf (0.15)

Der autorisierte Wiedereröffnungsauftrag gehört zum bestehenden Fall und referenziert
seinen abgeschlossenen Durchlauf. Er startet eine neue Hauptinstanz; erst deren
geprüfter Zuordnungs-Worker ersetzt die führende Zuordnung gemäss FALL-07 und der
Ergänzung in ADR-002. Ein anderer laufender oder bereits erfolgreich zugeordneter
Durchlauf darf dabei nicht überschrieben werden. Doppelte Starts erzeugen keine
zweite aktive Bearbeitung; technische Start- und Deduplizierungsverträge sind vor
Ausführung zu konkretisieren.

Für alle neuen Calls gilt der neue `leadingProcessInstanceKey`; innerhalb eines
Calls bleibt dieser unveränderlich. Alte Kindinstanzen, Timer und Ergebnisse
werden nicht übernommen. `entryContextRef` im Mitarbeiterprozess referenziert den
serverseitig geprüften Wiedereröffnungsentscheid und den aktuellen Prüfbedarf.
Es ist keine frei wählbare Status- oder Berechtigungsvariable.

`SEntryCheck` registriert und prüft den Kind-/Call-Bezug vor **beiden** Eintrittswegen.
Ohne Prüfbedarf darf der erfolgreiche Wiedereröffnungseintritt direkt `SWork`
verwenden. Reguläre Eintritte und Wiedereröffnung mit Prüfbedarf führen über
`SReviewStatus` zu `SReview`. Neue Prüfgründe haben Vorrang. Der direkte Weg erzeugt
keine erfundene frühere Mitarbeiterentscheidung und hebt keine Prüfgründe auf.
Rückgaben und die bestehende Nachrichten-Nebenaktion bleiben unverändert.

## Externe Wartephase (0.16)

`SExternalStatus` und `SExternalWait` gehören zur bestehenden Mitarbeiterinstanz;
keine zusätzliche führende Instanz und kein neuer öffentlicher Nachrichteneingang.
Die bewusste Mitarbeiteraktion wird gegen aktuelle Aufgabe und Fallstand geprüft.
`SExternalDecision` verwendet die vorhandenen Fortsetzungs-, Wiedervorlage- und
Abschlusswege; die bisherigen Call-Ein-/Ausgaben bleiben bestehen.

Der zentrale Nachrichtenauswerter stellt einen serverseitig festgestellten neuen
zwingenden Prüfbedarf zur aktuellen externen Wartephase ausdrücklich für die
Mitarbeiterinstanz bereit. `SExternalReview` unterbricht nur diese passende Aufgabe.
Die Bedingung mit `activeExternalWaitRef`, `reviewWaitRef` und
`externalWaitReviewRequired` ist ein fachlicher Platzhalter, kein implementierter
Variablenvertrag. Bereits vor Aufgabenaktivierung entstandene Gründe müssen beim
Eintritt erkannt werden. Keine stillschweigend gemeinsam genutzten Elternvariablen.
Markerübertragung und Abbruch sind vor Ausführung mit Mitarbeiterentscheidung,
Abschluss und Wiedereröffnung zu koordinieren; ein nachträglicher Worker-Check
allein schützt nicht vor dem Abbruch einer neueren Aufgabe. Wiederholungen dürfen
keine zweite Mitarbeiterprüfung erzeugen. Normale Mieternachrichten ergänzen nur
den Fallstand; sie sind keine externe Rückmeldung und kein Abbruchsignal.

## Wiedervorlagetermin (0.17)

`followUpDueAt` ist der gespeicherte absolute Termin der aktuellen Wiedervorlage-
Version, als ISO-8601-Zeitpunkt mit Offset oder UTC. `WSave` beziehungsweise
`WUpdate` muss ihn vor Aktivierung von `WEvents`/`WTimer` ausdrücklich in der
Wiedervorlage-Instanz bereitstellen. Kein neues Call-Eingabefeld: der Dienst lädt
den bestätigten Auftrag über den bestehenden Kontext. Berechnung und Annahmeprüfung
folgen FALL-11; N Kalendertage, 08:00 Uhr Europe/Zurich, keine relative 24h-Dauer.
Bereits angenommene Termine werden bei Wiederholung nicht neu berechnet und bei
verzögerter Verarbeitung nicht nachträglich als ungültige Eingabe behandelt.
Eine abgelehnte neue Terminänderung darf die laufende Wartephase nicht beenden;
sie muss vor Bestätigung/Korrelation der Änderung geprüft werden. Technische
Koordination und Worker sind weiterhin nicht implementiert.

## Mitarbeiterauftrag «Antwortbedarf aufheben» (0.19)

`ClearAnswerSide` empfängt ausschliesslich serverseitig autorisierte Aufträge zur
aktuellen führenden Instanz. Der Auftrag enthält Referenzen auf Rückfrage und
geprüften Fallstand; keine frei wählbare Statusfreigabe. Nebenaktionsdaten bleiben
lokal. Der Dienst speichert Aufhebung und Fortsetzungsnachweis idempotent und
stellt den aktuellen Marker zuverlässig bereit. Teilaufhebung benötigt keinen
Call-Abbruch. Laufende Mitarbeiterprüfung, Wiedervorlage oder externe Wartephase
werden nicht unterbrochen.

`clearedAnswerCallRef` und `activeReplyCallRef` sind fachliche Platzhalter für die
Bindung an den aktuellen Mieterwarte-Call. Markerübergabe und Unterbrechung müssen
vor ausführbarer Umsetzung gegen neue Fragen, Abschluss und Call-Wechsel abgesichert
werden; eine nachträgliche Prüfung allein reicht nicht. Keine normalen Outputs
vom abgebrochenen `Reply` erwarten. `ClearAnswerContinue` lädt stattdessen den
persistierten Entscheid, prüft den aktuellen Stand und bereitet je nach Ergebnis
`staffEntryContextRef` oder `replyEntryContextRef` vor. Ein inzwischen überholter
Auftrag darf keine andere aktive Bearbeitung verdrängen.

`SEntryCheck` akzeptiert den geprüften Aufhebungsentscheid ohne Prüfbedarf als
weiteren Direkteintritt zu `SWork`, neben der Wiedereröffnung. Kein durch den
Client wählbarer Direkteintritt; neuer Prüfbedarf bleibt vorrangig. Vorhandene
Call-Rückgabewerte bleiben unverändert. Technische Realisierung noch offen.

## Direkter Mitarbeiterabschluss aus `Reply` (0.20)

`StaffCloseReplySide` empfängt einen serverseitig autorisierten Mitarbeiterauftrag
zur aktuellen führenden Instanz. `StaffCloseReplyCheck` prüft Fallstand, Status,
Identität und den aktuellen `Reply`-Call. Keine Mitarbeiter-User-Task voraussetzen,
wo das Modell auf eine Mieterantwort wartet. Frühe Ablehnung liefert keinen
Unterbrechungsmarker. Technische Fehler bleiben Jobfehler.

Der persistierte Entscheid trägt Quelle Mitarbeiter, Auftragsschlüssel und
Fall-/Call-Bezug. `StaffCloseReplySignal` stellt dessen Referenz zuverlässig bereit.
`staffCloseReplyCallRef` und `activeReplyCallRef` sind fachliche Platzhalter;
Markerbereitstellung und Call-Abbruch müssen vor Ausführung gegen konkurrierende
Aufhebung, neue Fragen, automatische Veröffentlichung und Call-Wechsel abgesichert
werden. Ein nachträglicher Worker-Check allein genügt nicht. Keine neue Wirkung
für alte Generationen oder bereits erfolgreich abgeschlossene identische Aufträge.

Nach Unterbrechung lädt `Close` den dauerhaften Mitarbeiterentscheid, keine Outputs
des abgebrochenen `Reply`. Der normale Reply-Ausgang `CLOSE` bleibt unverändert ein
Systemabschlussvorschlag und wird nicht für diesen Mitarbeiterauftrag verwendet.
Später fachlicher Konflikt: `STAFF_CLOSURE_STALE` → `CloseConflict` → aktuelle
Mitarbeiterprüfung. Erst erfolgreicher Commit beendet offenen Antwortbedarf und
den aktiven Ausführungsbaum. Worker und technische Übergaben sind noch offen.

## Unabhängiger Prüfbedarf (0.21)

`ReviewNeedSide` ist ein interner Empfang im aktiven Hauptprozess. Eingangsdaten
referenzieren Quelle, fachlichen Grund, Erkenntnisversion und Bearbeitungsdurchlauf.
Nebenaktionsdaten bleiben lokal; keine ungeprüfte globale Statusvariable. Grund
und Zustellung müssen dauerhaft nachvollziehbar sowie idempotent sein.

Bei offenem Review die aktuelle Mitarbeiter-Kindinstanz und dieselbe Aufgabe
gezielt ergänzen. Im Mieterwartepfad bindet `reviewNeedReplyCallRef` den geprüften
Grund an `activeReplyCallRef`; diese Namen sind fachliche Platzhalter. Übertragung,
Call-Abbruch und Eintritt müssen lückenlos mit Abschluss, Aufhebung und Wechsel
der aktuellen Aufgabe koordiniert werden. Ein nachträglicher Worker-Check allein
schützt nicht vor dem Abbruch einer neueren Instanz. `ReviewNeedPrepare` lädt den
persistierten Prüfentscheid statt Outputs des abgebrochenen Calls und stellt den
geprüften `staffEntryContextRef` bereit. Gleichzeitige Gründe müssen dieselbe
logische Prüfung verwenden, niemals parallele Staff-Calls erzeugen.

In anderen aktiven Phasen erfolgt Übergabe an die bestehende Phasensteuerung;
fehlende Übergabe ist keine erfolgreich verworfene Meldung. Bereits berücksichtigte
Gründe ohne neue Erkenntnis erhalten keine neue Prüfzuordnung. Nach Fallabschluss
führt die meldende Funktion nur einen internen Hinweis nach, ohne Korrelation an
eine beendete Instanz oder neuen Prozessstart. Alte Durchläufe dürfen nach
Wiedereröffnung keine aktuelle Prüfung auslösen. Technische Umsetzung bleibt offen.

## Prüfbedarf in Bearbeitung und Wiedervorlage (0.22)

`ReviewNeedOther` im Hauptprozess liefert den geprüften Grund ausdrücklich in
die aktuelle Mitarbeiter-Kindinstanz. `pendingWorkReviewRef` ist an
`activeWorkTaskRef` gebunden; `pendingReviewFollowUpVersion` an
`activeFollowUpVersion`. Diese Bedingungen sind fachliche Platzhalter, keine
implementierten Variablenverträge. Quelle, Erkenntnisversion und aktueller
Aufgaben-/Call-Bezug sind serverseitig zu prüfen; keine live gemeinsam genutzten
Elternvariablen und keine Korrelation anhand beliebiger Client-Schlüssel.

`SWorkReviewNeeded` beendet nur die aktuelle Bearbeitungsaufgabe.
`SFollowUpReviewNeeded` beendet den aktuellen Wiedervorlage-Call samt Timer und
Nebenaktionen. `SStopFollowUp` lädt den persistierten Prüfentscheid und entfernt
den betroffenen Termin ohne normale Call-Outputs. Timer-, Kontakt- und Prüfweg
müssen dieselbe logische Prüfzuordnung verwenden. Vor Ausführung sind Marker,
Aufgaben-/Terminversion, Abbruch und Speicherung gegen neuere Entscheidungen
atomar beziehungsweise zuverlässig zu koordinieren; ein nachträglicher Check
allein verhindert keinen falschen Abbruch. Alte Meldungen und bereits
berücksichtigte Gründe ohne neue Erkenntnis dürfen keine neuere Aufgabe stören.
Bestehende Call-Eingaben und Rückgaben bleiben unverändert.

## Eigenständiger Versandprozess (0.23)

`PropertyFlow_Mail_Draft` in `fallbenachrichtigung-versenden.bpmn` wird je
persistiertem Versandauftrag unabhängig gestartet. Einziger Eingabebezug ist
`mailOrderRef`; keine Eltern-Call-Bindung und keine Rückgaben an den Fallprozess.
Er ersetzt oder registriert niemals dessen führenden Prozessschlüssel.
`MailLoad` prüft Auftrag und liefert `mailRoute` (CHANGE, MESSAGE oder SKIP).
Beide Send-Tasks verwenden `propertyflow.mail.send` und laden Versanddaten
innerhalb des Workers über PropertyFlow. Auftragsprüfung und Fehlerdokumentation
haben die Entwurfs-Jobtypen `propertyflow.mail.prepare` und
`propertyflow.mail.record-problem`. Ein fachlicher Fehler
`MAIL_CLARIFICATION_REQUIRED` führt zur Problemablage; rein technische Fehler
bleiben Jobfehler. Die Jobs sind modelliert, aber nicht implementiert.

Der Starter koordiniert Startversuche aus der dauerhaften Mailpflicht; parallele
Starts und verlorene Startantwort dürfen keinen doppelten logischen Versand
erzeugen. SMTP-Ungewissheit folgt BEN-04. Ereignisgeneration und Empfängerbezug
bleiben nachvollziehbar; keine pauschale Prüfung auf «Fall noch aktiv», da eine
bereits veröffentlichte Nachricht auch nach Abschluss benachrichtigt werden muss.
Ein neuer Prüfgrund wird nur bei zulässiger aktueller Fallzuordnung wirksam.
Der Versandprozess muss nicht mit dem Fallprozess als synchroner Call bereitgestellt
werden; die bisherigen fünf Call-Modelle behalten ihre Deployment-Bindung.

## Mitarbeiterrückfrage in der Abklärung (0.24)

`StaffQuestionSide` bleibt im Hauptprozess. Ein erfolgreicher, idempotenter
Veröffentlichungsauftrag aus «In Abklärung» entzieht dem bisherigen automatischen
Abklärungslauf die Schreibfreigabe. Der durchlaufgebundene Marker
`staffQuestionTakeoverReady` unterbricht genau dessen aktuellen automatischen
Schritt, einschliesslich eines Übergangs zwischen Jobaktivierungen. Er darf in
späteren Bearbeitungsphasen nicht weiterwirken. `StaffQuestionContinue` lädt den
aktuellen gespeicherten Stand, konsumiert den Übergang und setzt entweder
`replyEntryContextRef` für `Reply` oder bei zwingendem Prüfbedarf
`staffEntryContextRef` für `Staff`. Keine Rückgaben abgebrochener Jobs verwenden.

Der zentrale Mieternachrichteneingang bleibt aktiv. Bereits gespeicherte
Rückfrage und Mailpflicht werden nicht wiederholt. Publikationssperre, zuverlässige
Übergabe nach Commit, Markerlebensdauer und genau eine Fortsetzung sind vor
Ausführbarkeit technisch nachzuweisen; ein BPMN-Abbruch allein sperrt keinen
bereits laufenden externen Worker. Fachliche Quelle: FALL-06, Übergang 29.

## Direkter Mitarbeiterabschluss aus Abklärung (0.25)

`StaffCloseClarificationSide` empfängt intern den Mitarbeiterauftrag im Hauptprozess.
`StaffCloseClarificationApply` verwendet denselben fachlichen Mitarbeiter-Abschlussvertrag
wie `Close`; keine zweite fachliche Abschlusslogik. Der Auftrag ist an Mitarbeiter,
Fall, führenden Durchlauf, bestätigten fachlichen Fallstand und Idempotenzschlüssel
gebunden. Ein fachlicher Konflikt beendet nur die Nebenaktion ohne Abschluss;
der Mitarbeiter muss den aktuellen Stand erneut bestätigen.

Erst nach erfolgreichem Commit folgt die Eskalation
`STAFF_CLARIFICATION_CLOSURE_COMMITTED`. Das unterbrechende Randereignis
`StaffClosureCommitted` am `ActiveCase` beendet sämtliche darin laufenden
Aktivitäten und führt zum bestehenden Prozessende. Der abgeschlossene Datenstand
sperrt alte Worker schon vor dieser BPMN-Unterbrechung. Eigenständige Mailprozesse
für frühere Nachrichten werden nicht abgebrochen; der Abschluss erzeugt keine neue Mail.

Keine Rückgaben abgebrochener Kindprozesse verwenden. Wiederholungen müssen den
bereits gespeicherten Erfolg erkennen und die Prozessbeendigung zuverlässig
nachholen; alte Aufträge dürfen nach Wiedereröffnung keinen neuen Durchlauf
beenden. Diese Garantien sind noch nicht implementiert oder zur Laufzeit getestet.

## Direkte Wiedervorlage aus Abklärung (0.26)

`StaffFollowSide` prüft und speichert den Auftrag aus «In Abklärung» am aktuellen
Fallstand. Termin, Terminversion, berücksichtigte Prüfgründe und Status werden
konsistent gesichert; alte automatische Schreibfreigaben erlöschen.
`staffFollowTakeoverReady` ist an den übernommenen Abklärungslauf gebunden und
muss auch Aktivitätswechsel abdecken. `StaffFollowContinue` konsumiert den Marker
und setzt `staffEntryContextRef` mit Bezug auf den gespeicherten Terminauftrag.

`SEntryCheck` prüft diesen Kontext erneut. Ohne inzwischen eingegangenen Kontakt
oder neuen zwingenden Prüfbedarf folgt `S_EntryFollowUp` direkt zu `FollowUp`;
`followUpEntryContextRef` verweist auf denselben Auftrag. Andernfalls wird die
betroffene Wiedervorlage aufgehoben und zur Mitarbeiterprüfung verzweigt.
Die bestehende Call-Hierarchie bleibt erhalten: Hauptprozess → Mitarbeiterprozess
→ Wiedervorlage. Kein zusätzlicher führender Fallprozess und kein zweiter Timer.

`WSave` übernimmt beim Direkteinstieg den bereits gespeicherten Termin und seine
Version idempotent. Keine Neuberechnung und keine neue Mailpflicht. Vor Aktivierung
des Calls eingetroffene Kontakte und neue Prüfgründe müssen zuverlässig nachgeliefert
werden; bestehende Randereignisse reagieren auch auf bereits bereitstehende Marker.
Ein inzwischen aufgehobener Termin darf nicht wiederhergestellt werden. Ein während
der Übergabe fällig gewordener, ursprünglich gültiger Termin folgt sofort dem
Timerpfad. Die lückenlose Übergabe und Synchronisierung mit konkurrierenden Aktionen
sind Implementierungsanforderungen, noch kein ausgeführter Laufzeitnachweis.
