# Use-Case-Umsetzung im integrierten Backend

Stand: 16.11.2026. Diese Übersicht ersetzt die historischen Implementierungsangaben
in der ursprünglichen Use-Case-Liste. Die fachlichen Anforderungen der einzelnen
Use Cases bleiben verbindlich. «Umgesetzt» bedeutet nicht automatisch vollständig
produktiv abgenommen. Die Modellevaluation ist zurückgestellt.
MCP-Anforderungen und MCP-Fehler sind auf ausdrücklichen Benutzerwunsch aus diesem
Arbeitslauf ausgeklammert und werden separat bearbeitet.

## Zuordnung von Oberfläche, Java, Speicherung und Prozess

| UC | Funktion | Integrierte Umsetzung und Persistenz | Camunda / Nachweis / Grenze |
|---|---|---|---|
| 001 | Anliegen einreichen | Gast- und Kontoeinreichung; `PersistentCaseService`; Fall, Zugang, Absendeidentität und Startauftrag in PostgreSQL | Echter Fallstart; Gast- und Kontobrowserprüfung |
| 002 | Mitarbeiterübersicht | `StaffCaseController`, `StaffCaseService`; Datenbankabfragen mit Filtern | Lesen unabhängig von Engineverfügbarkeit; bestehende HTTP-Tests |
| 003 | Mitarbeiterdetail | Persistente Falldaten und interner/externer Verlauf | Operate-Link auf führende Instanz; Dev-Browserprüfung |
| 004 | Mit KI formulieren | Neu: integrierte SSE-Formulierung, Spring-AI-Adapter, bewusste Übernahme; keine Entwurfspersistenz | Mitarbeiter-/Prozessprüfung, sitzungsgebundener Abbruch, Timeout und manueller Ersatzbetrieb technisch geprüft; Modellqualität nicht evaluiert |
| 005 | Mieterkonto eröffnen | Neu: `TenantLoginController`, `TenantOtpService`, `TenantAccountService`, `pf_tenant_account` | Codeprüfung, eigene/fremde Fälle und Browserablauf geprüft |
| 006 | Mitarbeiter anmelden | `RegularStaffLoginController`, `StaffOtpService`, `pf_staff_account` | Echte Mailpit-Anmeldung geprüft; lokale Adresse auf Dev-Anmeldeseite sichtbar |
| 007 | Mitarbeiterkonto anlegen | Neu: Kontoverwaltung, `AccountAdministrationService`, Konto und `pf_account_audit` | Nur Mitarbeiterrolle; keine Vergabe weiterer Verwaltungsrechte über diese Aktion; Browser-/DB-Prüfung |
| 008 | Eigenen Fall einsehen | Persönlicher Link und neu persistente Kontoübersicht/Detail | Aktuelle Kontaktadresse entscheidet bei jeder Anfrage; Fremdzugriff geprüft |
| 009 | Fall ergänzen | Token- und Kontonachrichten über `TenantMessageService`, `pf_tenant_message` | Bestehender Nachrichtenprozess; Konto-Autor wird gespeichert; Browser-/Camunda-Prüfung |
| 010 | Externe Nachricht | `StaffMessageService`, Nachrichten-, Befehls- und Versandtabellen | Prozessgebundene Veröffentlichung; bestehender Devdialog |
| 011 | Internes Memo | `CaseDataService`, `pf_internal_memo`; nicht im Mieterprojektionsmodell | Mitarbeiter-/Prozessrechte; bestehende technische Tests |
| 012 | Fall steuern | Mitarbeiteraktionen, Dringlichkeit und Zuordnung mit persistenten Aufträgen | Camunda-Freigaben und führende Instanz; technische Tests und Devzustände |
| 013 | Wiedervorlage | `FollowUpCommandService`, Warte- und Befehlstabellen | Timer-/Nachrichtenpfade implementiert; Integrationstests, Devbeispiel |
| 014 | Abschliessen | `CaseClosureService`; manueller Abschluss und separate individuelle Nachricht | Manuell nutzbar; automatischer Abschluss technisch vorhanden, fest gesperrt |
| 015 | Wiedereröffnen | `CaseReopenService`, persistenter Wiedereröffnungsauftrag | Neue führende Instanz; Integrationstest und Devbeispiel |
| 016 | Dringlichkeit bewerten | Analyseadapter und `CaseAnalysisService`; fallbezogene Ergebnisse gespeichert | Worker vorhanden; Dev ohne KI führt zur Mitarbeiterprüfung; keine neue Modellevaluation |
| 017 | Antwort auswerten | `ConversationEvaluationService`, `TenantReplyService`; Auswertungen und Antwortbedarf gespeichert | Technische PostgreSQL-/Camunda-Prüfungen; keine Aussage über vollständige Modellqualität |
| 018 | Empfehlung/RAG | `RecommendationService`, freigegebene Dateiquellen; Ergebnisse beim Fall gespeichert | Technisch angebunden; fachlich freigegebener produktiver Wissensbestand und Gesamtabnahme offen |
| 019 | Benachrichtigen | `CaseMailService`, persistente Aufträge und Zustellzustände; SMTP an Mailpit | Eigener Mailprozess; zusätzliche administrative Ersatzlink-Outbox ohne Camunda; nicht jede Fehlerphase neu geprüft |
| 020 | E-Mail-Eingang | Nicht implementiert | Sichere Eingangskanal-Spezifikation und Umsetzung offen; Mailpit ist kein implementierter Eingangskanal |
| 021 | KI-Rückfrage | Freigegebener Fragenkatalog und automatische Fragerunden implementiert | Freie interaktive Vorschläge nicht vollständig umgesetzt; technische Katalogtests vorhanden |
| 022 | Mieter anmelden | Neu: `/mieter/anmelden`, dauerhaftes Konto, E-Mail-Einmalcode | Neuanmeldung nach Neustart; keine dauerhaften Sitzungscookies als Identitätsersatz |
| 023 | MCP delegieren | Demo vorhanden; produktive OAuth-/Delegationspersistenz nicht implementiert | Keine MCP-Gesamtabnahme; bleibt offen |
| 024 | Fall-Link ersetzen | Neu: separate Fallzugangsverwaltung, serverseitige Prüfung, `CaseAccessAdministrationService` | Atomarer Ersatz, Audit, Outbox, Wiederholungsschutz; unabhängig von Camunda |
| 025 | Kontaktadresse ändern | Neu: bestätigte Zieladresse, gleichzeitiger Linkersatz, ursprüngliche Adresse erhalten | Kontozugriff wechselt ab nächster Anfrage; Browser-/DB-Prüfung |
| 026 | Abmelden | Gemeinsamer Logout für Mitarbeiter- und Mietersitzung | CSRF-Prüfung, Sitzung invalidieren; Browserprüfung |
| 027 | Kritisch entscheiden | Menschliche Prüfgründe und prozessgebundene Mitarbeiterentscheidungen vorhanden | Vollständige Abdeckung des eigenständigen Zielvertrags für alle kritischen Handlungen noch nicht nachgewiesen |
| 028 | Antwortbedarf aufheben | Mitarbeiteraktion mit persistentem Auftrag und Prozessprüfung | Technische Umsetzung vorhanden; nicht separat im neuen Kontenbrowserlauf geprüft |
| 029 | Mitarbeiterzugriff entziehen | Neu: Kontosperre/Rollenentzug, persönliches Audit, Absende-ID und letzter-Administrator-Schutz | Bereits offene Websitzung verliert Rechte; Parallelität geprüft. MCP bleibt separat offen |

## Datenbankstand

54 Anwendungstabellen nach V1–V8, zusätzlich Flyways Schematabelle:
fünf unveränderte Modulinitialskripte sowie additive Migrationen für Konten,
Fallzugangsprüfung und administrative Befehle. `intake`, `triage` und
`recommendation` benötigen weiterhin keine eigenen Fachtabellen. Fallbezogene
Ergebnisse gehören zu `caseprocessing`, Konten zu `access`.

## Nachweise und verbleibende Arbeit

- [Devbetrieb und Camunda](../evaluation/devbetrieb-2026-11-16.md)
- [Konten- und Zugriffsprüfung](../evaluation/konten-und-zugriffe-2026-11-16.md)
- [Annahmen und technische Konkretisierung](../architecture/konten-und-zugangsverwaltung-2026-11-16.md)

[KI-Formulierung: Annahmen](../architecture/ki-formulierung-2026-11-16.md) ·
[Technische Prüfung](../evaluation/ki-formulierung-2026-11-16.md)

Die lesende Betriebsübersicht `/mitarbeiter/betrieb` zeigt auffällige persistente
Aufträge. Autorisierung, Projektion und Datenbankfehler sind geprüft. Sie ersetzt
keine auditierte Klärung oder erneute Ausführung. Alle sieben ausführbaren BPMN
liegen nun unter `src/main/resources/bpmn` und werden mit dem JAR ausgeliefert.
Ein eigener GitHub-Job prüft den integrierten Betrieb mit PostgreSQL, Camunda und
Mailpit; lokale Nachweise und Grenzen stehen im oben verlinkten Prüfbericht und
in [Betrieb, BPMN und CI](../architecture/betriebsuebersicht-und-ci-2026-11-16.md).

Weiter offen: produktiver MCP/OAuth-Zugang, E-Mail-Eingang, auditierte Klärung/Wiederaufnahme ungewisser Aufträge und
vollständige Ausfall-/Parallelitätsmatrix aller KI- und Prozessphasen.
Der automatische Abschluss bleibt bis zur bestandenen Evaluation und Freigabe gesperrt.
