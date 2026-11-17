# Konten und Fallzugänge – technischer Nachweis vom 16.11.2026

## Umfang

Integriertes PostgreSQL-Backend, reale Camunda-Engine und lokales Mailpit.
Geprüft werden Mitarbeiteranmeldung, persistente Mieterkonten, manuelle
Mieterkommunikation, Kontoverwaltung und administrative Fallzugänge.
Die Modellevaluation wurde nicht wieder aufgenommen. Automatische Abschlüsse
bleiben durch `AutomaticClosureRelease.approved(...) == false` gesperrt.

## Ausgeführte Prüfungen

| Prüfung | Ergebnis |
|---|---|
| Erste Diagnose der gemeldeten Mitarbeiteranmeldung | Konfiguriertes Konto: Codeanforderung HTTP 200, Codeprüfung HTTP 303 zur Mitarbeiterübersicht. Konkreter Benutzerfehler nicht reproduziert; fehlende Dev-Zugangshilfe ergänzt. |
| Java-Regression mit PostgreSQL und Camunda | 130 Tests bestanden, 0 Fehler, 0 übersprungen; `target/account-regression-final.log` |
| Abschliessender Paketbau nach letzten UI-/Anmeldekorrekturen | Weitere 53 gezielte Tests bestanden, 0 Fehler, 0 übersprungen; `target/account-package-final.log` |
| Kontenbrowserprüfung nach Vertragskorrekturen | Bestanden, rund 21 Sekunden, keine JavaScript-Fehler |
| Persistente Mieterregistrierung | Code aktiviert Konto; frühere eigene Gastfälle erscheinen, fremder Fall wird abgewiesen |
| Einreichung und Nachricht aus Mieterkonto | Tatsächliche HTTP-Annahme, Speicherung und Verarbeitung durch Camunda bestanden |
| Mitarbeiteranlage und Sperrung | Neues Mitarbeiterkonto kann sich anmelden; nach Sperrung wird die bereits offene Sitzung abgewiesen |
| Verwaltungsrechte und CSRF | Gewöhnlicher Mitarbeiter, zusätzliche Rollenfelder und falscher CSRF-Wert werden abgewiesen |
| Letztes Verwaltungskonto | Entzug wird auch bei konkurrierenden Verwaltungsaktionen verhindert |
| Geprüfter Adresswechsel | Codeprüfung, atomarer Linkersatz, neue Kontozuordnung und tatsächlicher Ersatzlinkversand bestanden |
| Unbestätigter, falscher, abgelaufener oder ausgeschöpfter Prüfnachweis | Keine Änderung; bisheriger Zugang bleibt gültig |
| Wiederholung administrativer Befehle | Identische Wiederholung ohne weitere Änderung; widersprüchliche Wiederholung abgewiesen |
| Erzwungener Datenbankfehler beim Ersatzlink-Versandauftrag | Gesamte Änderung zurückgerollt; ursprüngliche Adresse und Link bleiben gültig |
| Berechtigungsentzug nach Kontaktprüfung | Nachweis erlaubt anschliessend keine Änderung mehr |
| Bestehender Dialog ohne KI und Operate-Verknüpfung | Erneut bestanden, rund 54 Sekunden; tatsächliche Prozessposition sichtbar |
| Migrationen | Neuaufbau und erneute Validierung von V1–V8 mit 54 Anwendungstabellen bestanden; V1–V5 unverändert |

Die 130 Tests enthalten die betroffenen bestehenden Prüfungen für Gastannahme,
Mitarbeiterzugriff, Mieternachrichten und Antwortverarbeitung sowie die vollständigen
kanonischen BPMN-Modelle. Die technischen Abschlussprüfungen verwenden ihre
vorhandenen Testadapter und schalten keine produktive Freigabe frei.

Bei einem ersten neuen Test wurde eine nicht zulässige Unicode-Adresse über das
öffentliche Erfassungsformular eingespeist. Die Eingabevalidierung wies sie korrekt
ab. Der Test verwendet jetzt einen ausdrücklich gespeicherten historischen
Datensatz und prüft, dass eine ähnlich aussehende ASCII-Kontoadresse dadurch
keinen Zugriff erhält. Ein früher Compilefehler im Testaufbau wurde ebenfalls
korrigiert; die oben genannten Läufe sind anschliessend erfolgreich ausgeführt.

## Neustartprüfung

Vor dem vollständigen Stop wurden 20 Fälle, 4 Mieterkonten, 3 Mitarbeiterkonten,
6 Mieter- und 7 Mitarbeiternachrichten sowie 113 Mails erfasst. Fallinhalte,
Konten, aktuelle Prozesszuordnungen, Zugänge und Ersatzlink-Versandaufträge werden
über zusätzliche Prüfsummen verglichen. Nach dem vollständigen Stop und Neustart
von Backend, beiden Dev-PostgreSQL-Diensten, Camunda und Mailpit waren sämtliche
Werte und Prüfsummen identisch. Backend-Health: `UP`; Camunda: **0 Incidents**.

Danach wurden beide bereits bestehenden Mieterkonten im Browser erneut per Code
angemeldet. Die ursprünglichen Fälle und die geänderte Kontaktzuordnung blieben
korrekt erhalten; das vorherige Konto konnte den übertragenen Fall weiterhin nicht
lesen. Dieser separate Wiederanlauftest bestand in rund fünf Sekunden. Der erste,
Daten erzeugende Test derselben Datei wurde bei diesem Lauf über seinen Opt-in-Schalter
bewusst nicht erneut ausgeführt. Die beiden neuen Anmeldecodes erzeugen erwartungsgemäss
zusätzliche Mails nach dem abgeschlossenen Datenvergleich.

## Nachweise und Wiederholung

- [Konten-Browsertest](../../src/test/browser/dev-accounts.test.mjs)
- [Bestehender Devdialog und Camunda](../../src/test/browser/dev-runtime.test.mjs)
- [Konten-/Zugriffs-Integrationstest](../../src/test/java/com/pertegato/propertyflow/caseprocessing/service/AccountLifecyclePostgresTest.java)
- [Mieter-Codeprüfung](../../src/test/java/com/pertegato/propertyflow/access/service/TenantOtpServiceTest.java)
- [Migrationsprüfung](../../src/test/java/com/pertegato/propertyflow/config/BackendMigrationPostgresTest.java)
- Lokale Logs: `target/account-regression-final.log`, `target/account-tests.log`,
  `target/dev-verification/accounts-browser.log`, `runtime-browser-final.log`,
  `accounts-restart-browser.log` und `target/account-package-final.log`.
- Lokale Ergebnisdateien: `target/dev-verification/accounts-result.json`,
  `browser-result.json`, `accounts-before-restart.json`, `accounts-after-restart.json`,
  `account-restart-result.json`.
- Screenshots: `tenant-account.png`, `access-administration.png`, `camunda-case.png`
  unter `target/dev-verification/`.

Die PostgreSQL-Tests laufen ausschliesslich gegen `propertyflow_test` auf Port 5434,
Camunda-Tests gegen Port 8085. Die Browserprüfungen erzeugen synthetische Daten im
Devstack; sie löschen oder ersetzen keine bestehenden Geschäftsfälle.

## Grenzen

Das ist eine gezielte technische Integrationsprüfung, keine vollständige
Produktions- oder Modellevaluation. MCP/OAuth, E-Mail-Eingang, interaktive
KI-Formulierung und die Betriebsoberfläche für ungewisse Aufträge bleiben offen.
Die vollständigen Ausfallkriterien aller KI-/RAG-Anbieter und aller konkurrierenden
Prozessphasen sind durch diese Prüfung nicht vollständig abgedeckt.

[Aktuelle Umsetzungsmatrix](../use_cases/umsetzungsstand-2026-11-16.md) ·
[Annahmen und technische Details](../architecture/konten-und-zugangsverwaltung-2026-11-16.md) ·
[Start und Bedienung](../../dev/README.md)
