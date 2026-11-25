# Diff-Review: BPMN-Umbau (Commit `ce01094`, PR #46)

**Commit:** `ce01094` «BPMN Cleanup (#46)», 25.11.2026, Squash des Branches `refactor/refactor-bpmn`

**Umfang:** 207 Dateien, +12'078/−9'978 Zeilen in BPMN-Modellen, Java (`caseprocessing`, `access`, `intake`, `recommendation`, `triage`), Migration, Templates, Tests und Dokumentation

**KI-Werkzeug:** Codex

**Status des Reviews:** abgeschlossen am 25.11.2026. Vorgaben, KI-Beitrag und
Bewertung stammen vom Entwickler; die Befunde B1–B5 wurden im Review mit Claude
Code erarbeitet.

Zum Unterschied gegenüber dem [Review von `1f315c4`](diff-review-1f315c4.md):
Dort wurde ein KI-Vorschlag unverändert übernommen. Hier hat der Entwickler den
Umbau mit eigenen Vorgaben gesteuert. Die BPMN-Dateien und die neuen
Konstantenklassen werden einzeln bewertet. Die übrigen Dateien sind nach Bereichen
gruppiert, weil sie die Vorgaben mechanisch nachziehen.

```powershell
git show ce01094 --stat
git show ce01094 -- src/main/resources/bpmn
```

## 1. Rahmen

Massgebend waren beim Commit:

| Quelle | Inhalt für diesen Umbau |
| --- | --- |
| [Projekt-Kontext](../project-context.md), damals v0.1 | Die BPMN-Modelle unter `src/main/resources/bpmn` sind die ausführbaren Quellen; `docs/architecture/bpmn` beschreibt sie. Kleine, fokussierte Änderungen und Tests werden bevorzugt |
| [CAMUNDA_GUIDELINES](../specifications/CAMUNDA_GUIDELINES.md) | Wird in diesem Commit um Namensschema (3.2), Konstantenklassen (3.3), Jobtypen und Fehlercodes (3.4) sowie Variablennamen (4.1) erweitert |
| [BPMN-README](../architecture/bpmn/README.md) | Farbkonvention seit 17.11.2026 (#44); in diesem Commit um Rosa für LLM-Schritte ergänzt |

## 2. Vorgaben des Entwicklers

Laut Entwickler gab er Codex folgende Vorgaben. Die Spalte «Nachweis im Diff»
zeigt, wo sie sichtbar sind.

| Vorgabe | Nachweis im Diff |
| --- | --- |
| V1 – Prozess neu zeichnen | Alle acht Modelle neu angeordnet und benannt. Im Hauptprozess `propertyflow-fallprozess-entwurf.bpmn` bleibt die Zahl der Jobs (31) und Call Activities (4) gleich: Die Neuzeichnung ändert Darstellung und Namen, nicht den Umfang des Ablaufs |
| V2 – Konstanten einführen | Neun neue Klassen im Paket `caseprocessing.contract.bpmn`: eine pro Modell und `CommonBpmnConstants`. Regeln in CAMUNDA_GUIDELINES 3.3, 3.4 und 4.1. Worker, Engine-Abfragen und Tests verwenden die Konstanten; Bindungstests gleichen sie mit den Modellen ab |
| V3 – Farben | Farbkonvention nach Aufgabenart. Neu in diesem Commit: Rosa (`#fce7f3`) für Service Tasks, die ein LLM aufrufen können |
| V4 – Eigenes BPMN für das LLM | Neues Modell `gespraech-auswerten.bpmn` mit den drei LLM-Schritten «Gespraech analysieren», «Vorgehen empfehlen» und «Fallabschluss beurteilen». Automatische Abklärung (1 Aufruf), Mieterantwortprüfung (1) und Mieternachrichtenverarbeitung (2) rufen es auf, statt die Schritte selbst zu enthalten |
| V5 – Lombok-Regeln | Neuer Abschnitt «Lombok für Konstruktoren und Datenklassen» in `JAVA_GUIDELINES.md` |
| V6 – Testkopien der Modelle löschen | 24 Testmodelle unter `src/test/resources/workflow` gelöscht; Tests laden die kanonischen Modelle (B3) |

Alle sechs Vorgaben stammen vom Entwickler. Codex hat sie umgesetzt; den
neu gezeichneten Hauptprozess lieferte Codex direkt in der heutigen Form, ohne
Nachbesserung.

## 3. Review-Befunde

**B1 – LLM-Schritte nur noch an einer Stelle.** Vor dem Umbau enthielten drei
Modelle eigene Jobs für die Gesprächsauswertung. Danach ist die Zahl der Jobs dort
gesunken (Abklärung 12→11, Mieterantwort 5→4, Mieternachricht 9→7), und alle rufen
`Gespraech_auswerten_PROCESS` auf. Eine Änderung an der LLM-Auswertung betrifft
damit ein Modell statt drei.

**B2 – Neue IDs sind ein Vertragsbruch für laufende Daten.** Prozess-IDs wie
`PropertyFlow_Case_Draft` heissen jetzt `Fall_bearbeiten_PROCESS`, User-Task-IDs
wie `SReview` jetzt `Fall_pruefen_und_Vorgehen_entscheiden_USERTASK`. Gespeicherte
Werte werden mit der neuen Migration `V12__staff_task_identifiers.sql` umgeschrieben,
ohne bestehende Migrationen zu ändern. Laufende Camunda-Instanzen alter Versionen
werden nicht migriert; vor der Produktion gelten nur die aktuellen IDs
(CAMUNDA_GUIDELINES 3.2).

**B3 – Testkopien der Modelle entfernt.** 24 Testmodelle unter
`src/test/resources/workflow` sind gelöscht. Die Tests laden jetzt die kanonischen
Modelle aus `src/main/resources/bpmn`. Neu ist unter anderem
`ProcessVariablesContractTest`; `ReviewWorkflowBindingTest` prüft die Konstanten
gegen die Modelle. Tests und Produktion können nicht mehr auseinanderlaufen.

**B4 – Sehr grosser Commit.** 207 Dateien in einem Squash. Neben dem BPMN-Umbau
enthält er Lombok-Regeln in JAVA_GUIDELINES, eine Migration und Anpassungen in
UC-012 und UC-027. Die Lombok-Regeln (V5) waren eine bewusste Vorgabe, gehören
thematisch aber nicht zum BPMN-Umbau. Das widerspricht der Leitplanke «kleine,
fokussierte Änderungen» und erschwert das Review einzelner Dateien.

**B5 – Farben als reine Darstellung.** Die Farbkonvention ändert keine Logik.
Sie steht in den BPMN-DI-Attributen (`bioc:fill`, `color:background-color`) und ist
in BPMN-README und CAMUNDA_GUIDELINES dokumentiert. In älteren Modellen stehen
noch Farbwerte ausserhalb der Konvention, etwa `#ffcdd2` und `#bbdefb`.

## 4. Bewertung

Legende für die Spalte «Bewertung»: **übernommen**, **korrigiert** oder **verworfen**.
Eine Zeile mit eigener Vorgabe, die Codex umgesetzt hat, gilt als «übernommen»;
in der Spalte «KI-Beitrag» steht dann «Umsetzung der Vorgabe V…». «Korrigiert»
gilt, wenn ein Codex-Ergebnis nachträglich geändert wurde.

### 4.1 BPMN-Modelle (einzeln)

| Datei | Änderung laut Diff | Vorgabe / Befund | KI-Beitrag | Bewertung | Begründung |
| --- | --- | --- | --- | --- | --- |
| `propertyflow-fallprozess-entwurf.bpmn` | Hauptprozess neu gezeichnet; Prozess-ID `Fall_bearbeiten_PROCESS`; IDs nach Namensschema; Farben | V1, V3, B2 | Umsetzung der Vorgaben V1 und V3; Codex zeichnete den Prozess direkt in der heutigen Form | übernommen | Die Neuzeichnung macht den Hauptprozess lesbar und benennt die Elemente nach dem Namensschema; Umfang an Jobs und Call Activities bleibt gleich (V1). Beleg: `CanonicalModelsCamundaTest`, `ReviewWorkflowBindingTest` |
| `automatische-abklaerung.bpmn` | Neu gezeichnet; LLM-Schritte durch Aufruf von `gespraech-auswerten` ersetzt | V1, V3, V4, B1 | Umsetzung der Vorgaben V1, V3 und V4 | übernommen | Die LLM-Schritte liegen nicht mehr doppelt im Modell (B1). Beleg: Call Activity auf `Gespraech_auswerten_PROCESS` |
| `gespraech-auswerten.bpmn` (neu) | Eigenes Modell mit drei LLM-Schritten, rosa gefärbt | V4, V3, B1 | Umsetzung der Vorgaben V4 und V3; Modell und Rosa-Färbung waren Idee des Entwicklers | übernommen | Ein eigenes Modell trennt die LLM-Auswertung von der Fallsteuerung; Rosa macht LLM-Schritte im Diagramm sofort erkennbar (B1). Beleg: `ConversationEvaluationCamundaTest` |
| `mieterantwort-pruefen.bpmn` | Neu gezeichnet; ruft `gespraech-auswerten` auf | V1, V4, B1 | Umsetzung der Vorgaben V1 und V4 | übernommen | Wiederverwendung der gemeinsamen Gesprächsauswertung (B1). Beleg: `TenantReplyCamundaTest` |
| `mieternachricht-verarbeiten.bpmn` | Neu gezeichnet; ruft `gespraech-auswerten` zweimal auf | V1, V4, B1 | Umsetzung der Vorgaben V1 und V4 | übernommen | Beide Auswertungsstellen verwenden dasselbe Teilmodell (B1). Beleg: `TenantMessageCamundaTest` |
| `mitarbeiterbearbeitung.bpmn` | Neu gezeichnet; User-Task-IDs nach Namensschema | V1, V3, B2 | Umsetzung der Vorgaben V1 und V3 | übernommen | Einheitliche Namen; die neuen User-Task-IDs werden mit V12 in gespeicherten Entscheidungen nachgeführt (B2). Beleg: `StaffDecisionCamundaTest` |
| `wiedervorlage.bpmn` | Neu gezeichnet; Prozess-ID `Wiedervorlage_verwalten_PROCESS` | V1, V3 | Umsetzung der Vorgaben V1 und V3 | übernommen | Einheitliche Darstellung und Namen. Beleg: `CaseWaitCamundaTest` |
| `fallbenachrichtigung-versenden.bpmn` | Neu gezeichnet; Prozess-ID `Fallbenachrichtigung_versenden_PROCESS` | V1, V3 | Umsetzung der Vorgaben V1 und V3 | übernommen | Einheitliche Darstellung und Namen. Beleg: `CaseMailCamundaTest` |
| `bpmn/README.md` | Acht statt sieben Modelle; Verweis auf Namensschema | V4 | Nachführung durch Codex | übernommen | Beschreibt das neue achte Modell und verweist auf das Namensschema |

### 4.2 Konstantenklassen (einzeln)

| Datei | Inhalt | Vorgabe | KI-Beitrag | Bewertung | Begründung |
| --- | --- | --- | --- | --- | --- |
| `CommonBpmnConstants.java` | Prozessübergreifende Variablennamen, Fehlercodes | V2 | Umsetzung der Vorgabe V2 (Idee des Entwicklers) | übernommen | IDs, Jobtypen, Fehlercodes und Variablennamen stehen an einer Stelle statt verstreut in Workern und Tests. Beleg: `ReviewWorkflowBindingTest` gleicht jede Konstante mit ID und Typ im Modell ab; `ProcessVariablesContractTest` |
| `CaseProcessBpmnConstants.java` | Hauptprozess | V2 | Umsetzung der Vorgabe V2 | übernommen | Wie oben |
| `AutomaticClarificationBpmnConstants.java` | Automatische Abklärung | V2 | Umsetzung der Vorgabe V2 | übernommen | Wie oben |
| `ConversationEvaluationBpmnConstants.java` | Gesprächsauswertung, Jobtypen der LLM-Schritte | V2, V4 | Umsetzung der Vorgabe V2 | übernommen | Wie oben |
| `TenantReplyBpmnConstants.java` | Mieterantwortprüfung | V2 | Umsetzung der Vorgabe V2 | übernommen | Wie oben |
| `TenantMessageBpmnConstants.java` | Mieternachrichtenverarbeitung | V2 | Umsetzung der Vorgabe V2 | übernommen | Wie oben |
| `StaffProcessingBpmnConstants.java` | Mitarbeiterbearbeitung | V2 | Umsetzung der Vorgabe V2 | übernommen | Wie oben |
| `FollowUpBpmnConstants.java` | Wiedervorlage | V2 | Umsetzung der Vorgabe V2 | übernommen | Wie oben |
| `CaseNotificationBpmnConstants.java` | Fallbenachrichtigung | V2 | Umsetzung der Vorgabe V2 | übernommen | Wie oben |

### 4.3 Übrige Dateien (gruppiert)

| Bereich | Dateien | Änderung | Befund | KI-Beitrag | Bewertung | Begründung |
| --- | --- | --- | --- | --- | --- | --- |
| Richtlinien | `CAMUNDA_GUIDELINES.md`, `JAVA_GUIDELINES.md` | Namensschema, Konstantenregeln, Farben; zusätzlich Lombok-Regeln | V2, V3, B4 | CAMUNDA-Teil: Umsetzung von V2 und V3; Lombok-Teil: Umsetzung von V5 | übernommen | Die Regeln halten die Vorgaben verbindlich fest, damit künftige KI-Änderungen sie einhalten. Die Lombok-Regeln gehören thematisch nicht zum BPMN-Umbau (B4) |
| BPMN-Doku | `docs/architecture/bpmn/` (3 Dateien) | Farbkonvention mit Rosa, Teilprozess-Verträge, Laufzeitbeschreibung | V3, V4 | Nachführung durch Codex | übernommen | Dokumentiert die Farbkonvention mit Rosa und die neuen Teilprozess-Verträge |
| Fachdoku | `fallkommunikation.md`, `UC-012`, `UC-027`, `use_cases/README.md`, `ansicht-03-mitarbeiter-falldetail.md`, `datenbankmigrationen.md` | Nachführung an neue Abläufe und IDs | B4 | Nachführung durch Codex | übernommen | Use Cases und Spezifikationen bleiben mit den Modellen konsistent |
| Workflow-Worker | `caseprocessing/workflow/` (36 Dateien) | Jobtypen, IDs und Variablen über Konstanten | V2 | Nachführung durch Codex | übernommen | Worker verwenden Jobtypen und Variablen aus den Konstanten statt eigener Strings (V2). Beleg: Bindungstests |
| Persistenz und Dienste | `caseprocessing/persistence/` (33), `caseprocessing/service/` (14), `caseprocessing/controller/` (8) | Neue IDs, Konstanten | V2, B2 | Nachführung durch Codex | übernommen | Neue IDs und Konstanten durchgängig verwendet (B2) |
| Andere Module | `access` (9), `intake` (2), `recommendation` (5), `triage` (2) | Nachführung, eine Konfiguration in `triage` entfernt | B4 | Nachführung durch Codex | übernommen | Folgeänderungen ohne eigene Fachlogik |
| Migration | `V12__staff_task_identifiers.sql`, `updates/README.md` | Gespeicherte User-Task-IDs auf neue Namen umgeschrieben | B2 | Von Codex erstellt | übernommen | Neue Migration statt Änderung bestehender Migrationen, wie es die Projektregeln verlangen (B2). Beleg: `BackendMigrationPostgresTest` |
| Oberfläche und Entwicklung | `templates/` (2), `dev/` (3), `pom.xml` | Deployment der acht Modelle, Anzeige | – | Nachführung durch Codex | übernommen | Devstart deployt die acht Modelle |
| Tests | `src/test/java` (33), `src/test/browser` (3), `src/test/README.md` | Tests auf kanonische Modelle und Konstanten umgestellt; neue Vertragstests | B3 | Umstellung durch Codex | übernommen | Tests prüfen die echten Modelle und die Konstanten (B3) |
| Gelöschte Testmodelle | `src/test/resources/workflow/` (24 Dateien) | Entfernt | B3 | Umsetzung der Vorgabe V6 (Idee des Entwicklers) | übernommen | Ohne Testkopien können Test und Produktion nicht mehr auseinanderlaufen (B3) |

## 5. Ergebnis

**Gesamturteil.** Der Umbau folgt sechs Vorgaben des Entwicklers: Neuzeichnung,
Konstanten, Farben, eigenes LLM-Modell, Lombok-Regeln und das Löschen der
Testkopien. Codex hat sie umgesetzt und alle Folgeänderungen nachgezogen; die
Ergebnisse wurden ohne Nachbesserung übernommen. Die Neuzeichnung lieferte Codex
direkt in der heutigen Form. Bindungs- und Workflowtests gegen die echten Modelle
belegen, dass Modelle, Konstanten und Worker zusammenpassen (B3).

**Rolle der KI.** Anders als beim [Review von `1f315c4`](diff-review-1f315c4.md)
kamen die Architekturentscheidungen hier vom Entwickler. Codex war ausführendes
Werkzeug: Es hat die Vorgaben über 207 Dateien konsistent umgesetzt, was von Hand
deutlich länger gedauert hätte.

**Was das Review zusätzlich aufdeckt.**

- Der Commit ist mit 207 Dateien zu gross und enthält mit den Lombok-Regeln ein
  fremdes Thema (B4).
- Einzelne Farbwerte in älteren Modellen weichen von der Konvention ab (B5).
- Die neuen IDs gelten nur für neue Prozessinstanzen; vor der Produktion dürfen
  keine Instanzen alter Versionen mehr laufen (B2).

**Was künftig anders gemacht wird.**

1. Vorgaben als Teilschritte umsetzen lassen und je Schritt committen, etwa
   Konstanten, Neuzeichnung und LLM-Modell getrennt.
2. Themenfremde Regeln wie Lombok in einem eigenen Commit festhalten.
3. Die Farbkonvention mit einer automatischen Prüfung absichern, damit
   abweichende Farbwerte auffallen.
