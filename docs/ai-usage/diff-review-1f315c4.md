# Diff-Review: Gemeinsame E-Mail-Prüfung (Commit `1f315c4`)

**Commit:** `1f315c4` «feat: Fallzugriff absichern und Camunda-Verträge präzisieren», 14.11.2026

**Umfang:** 21 Dateien, +281/−100 Zeilen: 8 Java-Dateien (5 Produktiv-, 3 Testdateien) in den Modulen `access` und `caseprocessing` sowie 13 Dokumente

**KI-Werkzeug:** Codex

**Status des Reviews:** abgeschlossen am 25.11.2026. KI-Beitrag und Bewertung stammen vom Entwickler; die Befunde B1–B7 wurden im Review mit Claude Code erarbeitet.

Der Diff lässt sich nachvollziehen mit:

```powershell
git show 1f315c4 --stat
git show 1f315c4 -- src/
```

## 1. Rahmen des KI-Vorschlags

Massgebend war der [Projekt-Kontext](../project-context.md) in **Version 0.1**,
der beim Commit galt. Die heutige Version ist 0.3. Für diese Änderung gelten
folgende Leitplanken aus v0.1:

| Abschnitt in v0.1 | Leitplanke | Bezug zur Änderung |
| --- | --- | --- |
| 2. Entwicklungsrandbedingungen | Kleine, fokussierte Änderungen und automatisierte Tests werden bevorzugt | Befund B5: Der Commit umfasst zwei Themen |
| 5. Bausteinsicht | Module nach fachlicher Verantwortung; keine globalen technischen Root-Packages | Befund B3: `caseprocessing` verwendet `access.contract` |
| 6.9 Fallzugriff und Sicherheit | Ein Mieterkonto zeigt alle Fälle mit derselben aktuellen Kontaktadresse wie die verifizierte Konto-Adresse | Kern der Änderung: «dieselbe» muss exakt und sicher verglichen werden |
| 7. Testbarkeit | Regeln automatisiert prüfen | Neue Grenz- und Angriffstests |

Die Regel «neue Kommentare und Javadoc auf Deutsch» in
[copilot-instructions.md](../../.github/copilot-instructions.md) entstand erst am
17.11.2026 mit Commit `05d5d83`. Für diesen Commit galt sie noch nicht.

## 2. Ausgangslage vor dem Commit

Fallanlage und Konto prüften E-Mail-Adressen mit zwei verschiedenen Regeln:

| Stelle | Prüfung vor `1f315c4` | Schwachstelle |
| --- | --- | --- |
| `DemoAccounts.normalizeEmail` (Konto, Login) | Zuerst `toLowerCase(Locale.ROOT)`, danach ASCII-Regex | Das Kelvin-Zeichen `K` (U+212A) wird zu `k` und besteht danach die ASCII-Prüfung. `Konto@example.test` wird so zur ASCII-Adresse `konto@example.test` vereinheitlicht |
| `DemoCases.create` (Gastformular) | Einfache Regex `[^\s@]+@[^\s@]+\.[^\s@]+`, keine Vereinheitlichung | Nicht-ASCII-Adressen wie `mıeter@example.test` mit punktlosem `ı` werden gespeichert |
| `DemoCases.tenantCases`, `ownedCase` (Kontozugriff) | `equalsIgnoreCase` | `"mıeter@…".equalsIgnoreCase("mieter@…")` ergibt `true`. Das Konto `mieter@…` sieht damit einen fremden Fall |

Mit Java 23 am 25.11.2026 reproduziert (Hilfsprogramm, nicht im Repository):

```text
Alt Konto: konto@example.test (ASCII-Regex danach erfuellt)
Alt Fall equalsIgnoreCase: true
Alt Fall-Regex akzeptiert: true
```

## 3. Review-Befunde

Diese Befunde dienen als Grundlage für die Bewertung je Datei in Abschnitt 4.

**B1 – Kern der Korrektur ist richtig.** `EmailAddress.normalize` prüft die
ASCII-Syntax vor `toLowerCase`. Die Regex verwendet `CASE_INSENSITIVE` ohne
`UNICODE_CASE`; dadurch erkennt sie nur ASCII-Buchstaben ohne Rücksicht auf
Gross- und Kleinschreibung, und `K` wird nicht als `k` behandelt. Erst die
geprüfte Adresse wird in Kleinbuchstaben umgewandelt. Beide Angriffsadressen
werden abgewiesen.
Beleg: `DemoAccountsTest.accountAddressRejectsUnicodeLookalikes`, heute
[EmailAddressTest](../../src/test/java/com/pertegato/propertyflow/access/contract/EmailAddressTest.java).

**B2 – Fallzugriff vergleicht exakt.** `DemoCases.matchesVerifiedEmail`
vereinheitlicht die gespeicherte Fall-Adresse mit derselben Regel und vergleicht
sie mit `equals`. Historische Adressen, die die Prüfung nicht bestehen, ergeben
`false` und gewähren keinen Kontozugriff. Der Fall-Link mit Token bleibt nutzbar.
Beleg: `StaffAccessWebTest.verifiedTenantCannotListReadOrWriteLegacyCaseWithDotlessI`
prüft Liste, Detail, Schreibversuch und Token-Link.

**B3 – Modulabhängigkeit.** `caseprocessing.demo.DemoCases` importiert
`access.contract.EmailAddress`. Das Modul `access` steht in v0.1 nicht in der
Modulliste. Die Abhängigkeit läuft über ein `contract`-Paket und nicht über
interne Klassen. Sie besteht heute weiter in `CaseInputValidation` und
`CaseAccessAdministrationService`.

**B4 – Verhaltensänderung für Gäste.** Das Gastformular prüft jetzt strenger:
höchstens 64 Zeichen vor dem `@`, keine führenden, abschliessenden oder doppelten
Punkte, gültige Domain-Bezeichner. Die bisher akzeptierte Grenzadresse
`"a".repeat(249) + "@b.ch"` wird abgewiesen. `DemoCasesBoundaryTest` wurde
entsprechend angepasst. Gäste sehen zudem einen anderen Fehlertext: statt «Bitte
eine gültige E-Mail-Adresse eingeben.» erscheint «Eine gültige E-Mail-Adresse ist
erforderlich.».

**B5 – Zwei Themen in einem Commit.** Neben der E-Mail-Prüfung schärft der Commit
die Camunda-Verträge in 11 Dokumenten: synchrone Korrelation, Abschluss von User
Tasks, dauerhaft abgelehnte Statusmail und Instanzzuordnung. Das widerspricht der
Leitplanke «kleine, fokussierte Änderungen».

**B6 – Testaufbau über Reflection.** Der Zugriffstest setzt die historische
Adresse über `ReflectionTestUtils` in ein privates Feld. Das ist fragil. Der
heutige Nachfolger
[AccountLifecyclePostgresTest](../../src/test/java/com/pertegato/propertyflow/caseprocessing/service/AccountLifecyclePostgresTest.java)
(Zeile 91) setzt sie per SQL-Update in der Datenbank. Beim Nachfolgetest hält
die [Evaluationsnotiz vom 16.11.2026](../evaluation/konten-und-zugriffe-2026-11-16.md)
fest: Ein erster Versuch speiste die Unicode-Adresse über das Formular ein und
wurde von der Validierung korrekt abgewiesen. Erst ein gespeicherter historischer
Datensatz prüft den Zugriffsschutz. Dasselbe Muster erklärt den Reflection-Aufbau
in diesem Commit.

**B7 – Nachverfolgung.** Die Demo-Klassen sind mit dem Profil `frontend-demo`
entfernt worden. `EmailAddress` ist seit diesem Commit unverändert und wird von
neun Klassen des regulären Backends verwendet, darunter Konto- und
Einmalcode-Dienste, Login-Controller, `CaseInputValidation` und
`CaseAccessAdministrationService`. Die Testabsichten sind übernommen
([Demo-Bestandsliste](../architecture/demo-bestandsliste.md)).

## 4. Bewertung je Datei

Legende für die Spalte «Bewertung»: **übernommen**, **korrigiert** oder **verworfen**.
Als Beleg dient ein Diff-Ausschnitt oder ein Test.

### 4.1 Code und Tests

| Datei | Änderung laut Diff | Befund | KI-Beitrag | Bewertung | Begründung |
| --- | --- | --- | --- | --- | --- |
| `access/contract/EmailAddress.java` (neu) | Gemeinsame Prüfung: ASCII-Regex vor `toLowerCase`, Grenzen 254 Zeichen insgesamt und 64 vor dem `@`, Punktregeln | B1, B3, B7 | Codex schlug vor, die bisher getrennten Prüfungen in einer gemeinsamen Klasse zusammenzuführen und die ASCII-Syntax vor der Umwandlung in Kleinbuchstaben zu prüfen | übernommen | Der Vorschlag schliesst die Lücke mit `ı` und `K` an einer zentralen Stelle (B1). Beleg: `DemoAccountsTest.accountAddressRejectsUnicodeLookalikes`, heute `EmailAddressTest.rejectsMalformedAddressesAndUnicodeLookalikes` |
| `access/demo/DemoAccounts.java` | `normalizeEmail` und Regex entfernt; Aufrufe auf `EmailAddress.normalize` umgestellt | B1 | Folgeänderung aus dem Codex-Vorschlag: Aufrufe auf die gemeinsame Klasse umgestellt | übernommen | Mechanische Umstellung ohne eigene Logik; die alte, unsichere Reihenfolge entfällt. Beleg: Diff entfernt `normalizeEmail` vollständig |
| `access/service/StaffLogin.java` | `requestCode` und `verifyCode` verwenden `EmailAddress.normalize` | B1 | Folgeänderung aus dem Codex-Vorschlag | übernommen | Code-Anforderung und Code-Prüfung verwenden dieselbe Regel wie die Fallanlage |
| `access/web/StaffLoginController.java` | Zieladresse über `EmailAddress.normalize` | B1 | Folgeänderung aus dem Codex-Vorschlag | übernommen | Der Controller verwendet dieselbe Regel wie der Dienst; keine abweichende Prüfung im Web-Adapter |
| `caseprocessing/demo/DemoCases.java` | Gastadresse wird vereinheitlicht; `equalsIgnoreCase` durch exakten Vergleich `matchesVerifiedEmail` ersetzt | B2, B3, B4 | Codex schlug den exakten Vergleich nach gemeinsamer Vereinheitlichung und die Prüfung der Gastadresse mit `EmailAddress` vor | übernommen | Der exakte Vergleich schliesst den fremden Fallzugriff über `ı` (B2). Beleg: `StaffAccessWebTest.verifiedTenantCannotListReadOrWriteLegacyCaseWithDotlessI`. Die strengere Gastprüfung und der geänderte Fehlertext (B4) waren beim Übernehmen nicht bewusst und wurden erst in diesem Review erkannt. Sie werden akzeptiert: Gast und Konto sollen nach ZUG-04 dieselbe Regel verwenden, und realistische Mieteradressen sind nicht betroffen |
| `DemoAccountsTest.java` | Parametrisierter Test mit `ı` und `K` | B1 | Codex schlug den Test mit beiden Angriffszeichen vor | übernommen | Deckt beide reproduzierten Lücken der Kontoprüfung ab (Abschnitt 2) |
| `StaffAccessWebTest.java` | Direkte HTTP-Einreichung mit Unicode-Adresse ergibt 400, eine korrigierte Wiederholung ist möglich; historischer Fall mit `ı` bleibt für Liste, Detail und Schreiben gesperrt | B2, B6 | Codex schlug beide Tests einschliesslich des Testaufbaus über `ReflectionTestUtils` vor; wie der Aufbau entstand, ist nicht mehr nachvollziehbar | übernommen | Die Tests prüfen den eigentlichen Angriff über HTTP: Liste, Detail, Schreibversuch und weiterhin gültigen Token-Link. Der Reflection-Aufbau ist fragil (B6); der Nachfolgetest `AccountLifecyclePostgresTest` setzt die historische Adresse per SQL |
| `DemoCasesBoundaryTest.java` | Grenzfälle auf 64 Zeichen vor dem `@` und Domain-Bezeichner umgestellt | B4 | Codex passte die Grenzwerte an die neue Regel an | übernommen | Die Anpassung macht die Verhaltensänderung B4 sichtbar; sie wurde im Review akzeptiert (siehe `DemoCases.java`) |

### 4.2 Dokumentation zur E-Mail-Regel

| Datei | Änderung laut Diff | Befund | KI-Beitrag | Bewertung | Begründung |
| --- | --- | --- | --- | --- | --- |
| `specifications/fallzugriff-und-sicherheit.md` | «Gemeinsame E-Mail-Regel» unter ZUG-04; Abnahmekriterium ZUG-AK-15 für `ı` | B1, B2 | Text von Codex formuliert | übernommen | Die Spezifikation hält die Regel als Quelle fest, auf die Fallanlage, Konto und Screens verweisen. ZUG-AK-15 entspricht dem Test `verifiedTenantCannotListReadOrWriteLegacyCaseWithDotlessI` |
| `specifications/fallverwaltung.md` | Gleiche Prüfung für eingegebene Kontaktadressen; Abnahmekriterium FALL-AK-01 für `ı`; ausserdem Status «Mitarbeiterprüfung» bei abgelehnter Statusmail | B4, B5 | Text von Codex formuliert, einschliesslich des Camunda-Anteils | übernommen | Die E-Mail-Regel für Gastadressen entspricht dem Code in `DemoCases`; das Abnahmekriterium entspricht `directHttpSubmissionRejectsUnicodeEmailAndAllowsCorrectedRetry`. Der Camunda-Anteil gehört thematisch zu 4.3 (B5) |
| `use_cases/UC-008-eigenen-fall-einsehen.md` | UC-008-AK-03: Fall mit `ı` erscheint weder in der Liste noch im Direktabruf | B2 | Text von Codex formuliert | übernommen | Das Abnahmekriterium ist aus Sicht des Use Case formuliert und entspricht dem Zugriffstest |
| `frontend/ansicht-01-mieter-fallansicht.md` | E-Mail-Grenze verweist auf ZUG-04; ausserdem synchrone Korrelation beim Ergänzen | B4, B5 | Text von Codex formuliert, einschliesslich des Camunda-Anteils | übernommen | Die Screen-Spezifikation verweist auf die zentrale Regel statt sie zu wiederholen. Der Camunda-Anteil gehört thematisch zu 4.3 (B5) |

### 4.3 Dokumentation zu Camunda-Verträgen (Thema B5)

| Datei | Änderung laut Diff | KI-Beitrag | Bewertung | Begründung |
| --- | --- | --- | --- | --- |
| `architecture/adr/ADR-002-camunda8-workflow-orchestrierung.md` | Synchrone Nachrichtenkorrelation präzisiert; neue Regel «Synchroner Abschluss von User Tasks» | Text von Codex formuliert, in derselben Sitzung wie die E-Mail-Prüfung | übernommen | Inhaltlich als Präzisierung der Camunda-Verträge geprüft und übernommen. Die Änderung gehört thematisch nicht zur E-Mail-Prüfung und hätte als eigener Commit erfolgen sollen (B5) |
| `architecture/schnittstellenentwurf-block-3.md` | SS-003 um User-Task-Abschluss und Korrelation ergänzt; dauerhaft abgelehnte Statusmail | Wie oben: Codex, dieselbe Sitzung | übernommen | Wie oben (B5) |
| `specifications/benachrichtigungen-und-zustellung.md` | Dauerhafter Versandfehler, verspätete Rückläufer offen, «immer eine E-Mail» präzisiert | Wie oben: Codex, dieselbe Sitzung | übernommen | Wie oben (B5) |
| `specifications/dringlichkeitsbewertung.md` | Eingaben der Neubewertung: Case-ID und auslösende Nachricht | Wie oben: Codex, dieselbe Sitzung | übernommen | Wie oben (B5) |
| `use_cases/UC-001-mieteranliegen-einreichen.md` | Ablauf bis zur bestätigten Instanzzuordnung; Fall bis dahin nur lesbar | Wie oben: Codex, dieselbe Sitzung | übernommen | Wie oben (B5) |
| `use_cases/UC-009-mieteranliegen-ergaenzen.md` | Synchrone Korrelation der Mieterergänzung | Wie oben: Codex, dieselbe Sitzung | übernommen | Wie oben (B5) |
| `use_cases/UC-012-fall-fachlich-steuern.md` | User-Task-Abschluss bei Mitarbeiteraktionen | Wie oben: Codex, dieselbe Sitzung | übernommen | Wie oben (B5) |
| `use_cases/UC-016-dringlichkeit-automatisch-bewerten.md` | Ablauf der Neubewertung | Wie oben: Codex, dieselbe Sitzung | übernommen | Wie oben (B5) |
| `use_cases/UC-019-fallbenachrichtigung-zustellen.md` | Abgelehnte Statusmail führt zur Mitarbeiterprüfung | Wie oben: Codex, dieselbe Sitzung | übernommen | Wie oben (B5) |

## 5. Ergebnis

**Gesamturteil.** Codex hat die Lücke beim Fallzugriff richtig erkannt und an
einer zentralen Stelle behoben. Alle 21 Dateien wurden unverändert übernommen.
Das Review findet keinen Fehler im Code; die Sicherheitswirkung ist durch Tests
belegt (B1, B2). Die Klasse `EmailAddress` ist bis heute unverändert im regulären
Backend im Einsatz (B7).

**Was das Review zusätzlich aufdeckt.** Zwei Punkte waren beim Übernehmen nicht
bewusst:

- Die strengere Prüfung der Gastadressen und der geänderte Fehlertext (B4). Sie
  werden nachträglich akzeptiert, weil Gast und Konto dieselbe Regel verwenden
  sollen.
- Die Vermischung zweier Themen in einem Commit (B5). Eine Codex-Sitzung hat
  E-Mail-Prüfung und Camunda-Verträge bearbeitet; beides wurde zusammen
  committet. Der Commit-Titel nennt zwar beide Themen, die Änderungen sind aber
  schwerer einzeln zu prüfen und zurückzunehmen.

**Was künftig anders gemacht wird.**

1. Pro Thema eine eigene KI-Sitzung oder mindestens ein eigener Commit.
2. Bei KI-Vorschlägen zur Eingabeprüfung gezielt auf geänderte Grenzwerte achten.
   Angepasste bestehende Tests wie `DemoCasesBoundaryTest` sind ein Hinweis auf
   eine Verhaltensänderung und müssen bewusst bestätigt werden.
3. Testaufbauten über Reflection hinterfragen und Testdaten über die Datenbank
   oder eine Testschnittstelle setzen, wie im Nachfolgetest (B6).
