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
abgewiesen. Die abschliessende Mail bleibt im Hauptprozess nach dem erfolgreichen
fachlichen Commit. Interne Übergaben dürfen Abschluss oder Mitarbeiterentscheidungen
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
  kein Wiederaufleben und genau ein logischer Abschlussmailauftrag.

Diese Laufzeitprüfungen stehen aus. XML-Schema, Modeler-Import und Modellverknüpfungen
belegen nur die strukturelle Aufteilung. Zeile 4 der fachlichen Fortsetzungstabelle
bleibt der nächste Besprechungspunkt.

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
