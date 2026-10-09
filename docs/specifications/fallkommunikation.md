# Fallkommunikation – fachliche und sicherheitsrelevante Regeln

**Status:** Zentrale Spezifikation der im Projekt festgelegten Kommunikationsregeln; technische Umsetzung noch offen.
**Stand:** 09.10.2026
**Geltungsbereich:** Mieteransicht, Backoffice, Backend-Services, Persistenz, Camunda-Worker, E-Mail und LLM-/RAG-Integration, soweit sie Fallkommunikation verarbeiten.

## Zweck und Verbindlichkeit

Dieses Dokument ist die gemeinsame fachliche Quelle für interne und externe Nachrichten, deren Sichtbarkeit und die zulässige Grundlage LLM-generierter Kommunikation. Die Regeln gelten unabhängig von einer einzelnen Benutzeroberfläche. Änderungen werden hier gepflegt; Screen-Spezifikationen beschreiben die daraus folgende Darstellung und Bedienung und verweisen auf die Regel-IDs.

Die Spezifikation wird mit der Projektdokumentation versioniert und ist für alle Projektbeteiligten und Entwicklungswerkzeuge im Repository auffindbar. Der gemeinsame Zugriff auf die Spezifikation erweitert keine Zugriffsrechte auf interne Fallinhalte.

Die Architekturgrundlagen bleiben in [ADR-001](../architecture/adr/ADR-001-grundarchitektur.md), [ADR-002](../architecture/adr/ADR-002-camunda8-workflow-orchestrierung.md) und [ADR-003](../architecture/adr/ADR-003-praesentationsschicht.md) dokumentiert. Dieses Dokument beschreibt fachliche Regeln und ihre Durchsetzung; ein ADR dokumentiert Architekturentscheidungen und deren Begründung. Es werden keine bestehenden ADRs stillschweigend geändert.

## KOM-01: Interne und externe Nachrichten

Jede Nachricht besitzt eine eindeutige **Sichtbarkeit**, unabhängig von ihrer Absenderrolle. Ein Mitarbeiter kann sowohl ein internes Memo als auch eine externe Antwort zum gleichen Fall erfassen. Die Sichtbarkeit wird dauerhaft mit der Nachricht gespeichert; die konkreten technischen Bezeichnungen können beispielsweise `INTERNAL` und `EXTERNAL` lauten.

| Sichtbarkeit | Zweck und Beispiel | Wer darf die Nachricht sehen? |
|---|---|---|
| **Intern** | Fallbezogenes Memo eines Mitarbeiters, z. B. «Vor einer Beauftragung bitte den Sachverhalt intern mit der Bewirtschaftung klären.» | Ausschliesslich berechtigte interne Mitarbeitende in der Backoffice-Ansicht; niemals der Mieter. |
| **Extern** | Mieteranliegen, Ergänzung des Mieters oder veröffentlichte Antwort an den Mieter, z. B. «Bitte teilen Sie uns mit, seit wann die Störung besteht.» | Der für diesen Fall berechtigte Mieter sowie berechtigte interne Mitarbeitende. |

**Extern bedeutet fallbezogen sichtbar, nicht öffentlich zugänglich.** Ein externer Entwurf wird erst nach ausdrücklicher Veröffentlichung für den Mieter sichtbar. Sichtbarkeit und Veröffentlichungszustand sind getrennte Merkmale.

Für alle Komponenten und Ausgabekanäle der Fallkommunikation gelten folgende Regeln:

- Die dem Mieter zugängliche Historie enthält ausschliesslich externe, veröffentlichte Nachrichten des autorisierten Falls. Interne Memos erscheinen weder als Inhalt noch als Platzhalter oder Hinweis «Interne Nachricht vorhanden».
- Nachrichten aus dem Mieterformular einschliesslich der Ursprungsmeldung werden serverseitig als externe Kommunikation eingeordnet. Der Mieter erhält keine Auswahl «intern/extern» und kann die Sichtbarkeit nicht über manipulierte Requests verändern.
- Interne Memos werden ausschliesslich über eine dafür berechtigte Backoffice-Funktion erfasst. Ein Mitarbeiter muss eine Antwort an den Mieter ausdrücklich als extern veröffentlichen; eine interne Notiz wird nicht automatisch veröffentlicht. Bei fehlender oder unbekannter Klassifikation erfolgt keine Ausgabe an den Mieter.
- Ein internes Memo bleibt intern. Soll ein berechtigter Mitarbeiter eine Information daraus dem Mieter mitteilen, erstellt er eine separate, manuell formulierte und bewusst geprüfte externe Nachricht. Das erlaubt keine memo-basierte LLM-Generierung; auch abgeleitete Inhalte bleiben gemäss [KOM-02](#kom-02-llm-kommunikation-ohne-interne-memos) als LLM-Grundlage ausgeschlossen. Der Zugriff auf interne Notizen wird durch den persönlichen Fall-Link nicht erweitert.
- Die Filterung erfolgt vor der Ausgabe serverseitig und gilt für HTML, ergänzende Datenantworten, einzelne Nachrichtenabrufe und E-Mail-Benachrichtigungen. Reines Ausblenden im Browser genügt nicht. Auch eine bekannte oder erratene Nachrichten-ID gewährt dem Mieter keinen Zugriff auf ein internes Memo.
- Mieterbezogene Nachrichtenzahlen, Seitennavigation, Vorschauen und der Zeitpunkt der letzten öffentlichen Aktualisierung berücksichtigen ausschliesslich die sichtbare externe Kommunikation. Ein internes Memo löst keine Mieterbenachrichtigung aus.
- **Verbindlicher Ausschluss:** LLM-generierte Kommunikationsnachrichten dürfen niemals auf internen Memos basieren. Interne Memos und daraus abgeleitete Inhalte werden bereits vor der Generierung aus sämtlichen Eingaben und abrufbaren Quellen des Kommunikations-LLM ausgeschlossen. Eine nachträgliche Prüfung oder Freigabe der Antwort ersetzt diesen Ausschluss nicht; Details siehe [KOM-02](#kom-02-llm-kommunikation-ohne-interne-memos).

Für beide Sichtbarkeiten gilt die [Nachrichtensperre nach Fallabschluss (KOM-04)](#kom-04-nachrichtensperre-nach-fallabschluss).

## KOM-02: LLM-Kommunikation ohne interne Memos

**Interne Memos sind keine zulässige Grundlage für die Generierung von Kommunikationsnachrichten durch ein LLM.** Diese Regel gilt für Antworten, Rückfragen und Kommunikationsentwürfe, einschliesslich später manuell freigegebener Entwürfe.

- Der zuständige PropertyFlow-Service stellt vor jedem Kommunikations-LLM-Aufruf einen zweckgebundenen Kontext aus ausdrücklich zulässigen Quellen zusammen, beispielsweise Mieterangaben, geeigneter externer Fallkommunikation und dafür freigegebenen Sachinformationen.
- Interne Memos dürfen weder direkt im Prompt oder Gesprächsverlauf noch indirekt über RAG/Retrieval, Tool-Ergebnisse, Speicher/Caches oder Camunda-Variablen in diesen Kontext gelangen.
- Der Ausschluss umfasst auch Memo-Zusammenfassungen, Paraphrasen, extrahierte Fakten und sonstige daraus abgeleitete Inhalte. Die Kennzeichnung einer daraus erstellten Nachricht als «extern» hebt den Ausschluss für die LLM-Generierung nicht auf.
- Sichtbarkeit für den Mieter und Eignung als LLM-Eingabe sind getrennte Prüfungen. Datenherkunft und zulässige Verwendung müssen über Verarbeitungsschritte hinweg nachvollziehbar bleiben. Bei unklarer Herkunft wird der Inhalt nicht in den Generierungskontext aufgenommen.
- Ein Prompt wie «Interne Memos nicht erwähnen» genügt nicht. Der Ausschluss wird serverseitig vor dem LLM-Aufruf und bei allen vom Kommunikations-LLM abrufbaren Quellen durchgesetzt, auch bei Wiederholungen eines Camunda-Jobs.
- Auch ein bereits mit internen Memos befüllter LLM-Sitzungs- oder Gesprächskontext darf nicht zur Kommunikationsgenerierung weiterverwendet werden. Der Kommunikationsschritt erhält einen getrennten, zulässigen Kontext.

Die Anforderung schützt die Grundlage der Generierung, nicht nur den sichtbaren Wortlaut der fertigen Nachricht. Menschliche Veröffentlichungskontrollen bleiben zusätzlich bestehen.

## KOM-03: Asynchrone Fallkommunikation

KI-gestützte Verarbeitungsschritte der Fallkommunikation werden durch Camunda 8 orchestriert und über PropertyFlow-Worker/Services ausgeführt. Der Mieter übermittelt Informationen zum Fall; das Backend speichert sie und führt sie gemäss dem aktuellen BPMN-Schritt der weiteren Verarbeitung zu. Eine Nachricht eröffnet weder einen neuen Fall noch zwangsläufig einen neuen Prozessschritt.

Der Mieter hat keine direkte LLM-Chatsitzung, keinen Chat-Stream und kein Recht, KI-Schritte oder den Camunda-Prozess abzubrechen. Diese Grenze gilt auch bei direkten Backend-Aufrufen mit Mieterberechtigung. Neuladen oder Schliessen der Seite beeinflusst die Hintergrundverarbeitung nicht.

An den Mieter werden ausschliesslich vollständige, gespeicherte und veröffentlichte externe Nachrichten ausgegeben. KI-Teilantworten und interne Zwischenergebnisse gehören nicht zur externen Historie. Wiederholungen und KI-Fehler werden durch die Camunda-/Backend-Integration behandelt; gespeicherte Nachrichten bleiben erhalten. Vor Kommunikationsgenerierung gilt KOM-02, vor Ausgabe KOM-01.

Die konkrete Aktualisierung der Darstellung beschreibt [Screen 01](../frontend/ansicht-01-mieter-fallansicht.md). Die in [Vision](../vision.md) optional vorgesehenen KI-Rückfragen bleiben hinsichtlich ihres produktiven Umfangs separat zu entscheiden. Sobald sie eingesetzt werden, gelten diese Regeln ebenfalls.

## KOM-04: Nachrichtensperre nach Fallabschluss

Die Voraussetzungen des fachlichen Abschlusses und die Zeit danach sind in [FALL-07](fallverwaltung.md#fall-07-abschluss-und-zeit-danach) definiert. Dieser Abschnitt regelt die daraus folgende Nachrichtensperre.

Nach fachlichem Abschluss darf kein Absender neue Nachrichten zum Fall erzeugen: weder Mieter noch Mitarbeitende noch System-/KI-Komponenten. Dies gilt für interne Memos und externe Nachrichten. Eine Ausnahme für interne Memos nach Abschluss ist nicht vorgesehen. Der berechtigte lesende Zugriff auf bestehende Nachrichten bleibt gemäss KOM-01 möglich.

Die Prüfung wird serverseitig bei jeder Schreiboperation durchgesetzt, einschliesslich direkter HTTP-Aufrufe, verspäteter KI-Ergebnisse und wiederholter Worker-Ausführungen. Abschluss und Prüfen/Speichern einer Nachricht müssen konsistent synchronisiert werden; ein konkurrierender Request darf die Sperre nicht umgehen. Die konkrete Synchronisation wird im Service-/Persistenzdesign festgelegt.

Die fachliche und technische Statusverantwortung sowie ihre Synchronisierung sind zentral in [FALL-06](fallverwaltung.md#fall-06-fachlicher-status-und-technischer-workflow) beschrieben.

E-Mail-Auslöser, kurzer Mailinhalt und zuverlässige Zustellung werden zentral in [Benachrichtigungen und Zustellung](benachrichtigungen-und-zustellung.md) geregelt. Auch eigene gespeicherte Mieter-Nachrichten und der fachliche Abschluss werden per E-Mail bestätigt; interne Memos und Entwürfe bleiben ausgeschlossen.

## Verantwortlichkeiten und Umsetzung

| Bestandteil | Pflicht |
|---|---|
| PropertyFlow-Anwendungsdienste | Fallberechtigung, Sichtbarkeit, Veröffentlichung und Abschlussregel bei jedem relevanten Zugriff durchsetzen. |
| Persistenz und Leseverträge | Sichtbarkeit und Veröffentlichungszustand erhalten; Mieteransichten nur mit zulässigen Daten versorgen. |
| Camunda-Worker | Fachliche Services verwenden; die Regeln auch bei Wiederholungen und verspäteten Ergebnissen einhalten. |
| Kommunikations-LLM-/RAG-Adapter | Zulässigen Generierungskontext nach KOM-02 herstellen und alle nachgeladenen Quellen ebenso prüfen. |
| Backoffice | Internes Memo und externe Veröffentlichung unterscheiden; berechtigte menschliche Entscheidungen unterstützen. |
| Mieteransicht und E-Mail an Mieter | Ausschliesslich autorisierte externe Kommunikation ausgeben; keine internen Inhalte oder Hinweise darauf transportieren. |

Konkrete DTOs, Datenbankschemata, APIs, Herkunftskennzeichnungen und technische Autorisierung werden in den jeweiligen Implementierungsverträgen festgelegt. Diese Spezifikation bestätigt keine bereits vorhandene Implementierung. Die [Modulgrenzen](../architecture/module-structure.md) und die [Evaluations- und Sicherheitsbasis](../evaluation/evaluationsgrundlage.md) gelten ergänzend.

## Prüfkriterien

Die Kriterien beschreiben Anforderungen an spätere Tests. Sie sind kein Testbericht. KOM-AK-01 bis KOM-AK-05 übernehmen die bisherigen Screen-Kriterien AK-20 bis AK-24 in derselben Reihenfolge.

### KOM-AK-01

Ein Fall mit einer Mieter-Nachricht, einem internen Mitarbeitermemo und einer veröffentlichten externen Antwort zeigt dem Mieter genau die beiden externen Nachrichten. Memo, Metadaten und Platzhalter fehlen auch im HTML und in Datenantworten.

### KOM-AK-02

Der Mieter kann ein internes Memo auch mit bekannter Nachrichten-ID oder manipuliertem Request weder lesen noch veröffentlichen. Das Mieterformular setzt die externe Sichtbarkeit serverseitig.

### KOM-AK-03

Das Speichern eines internen Memos erzeugt weder eine Mieter-E-Mail noch eine Änderung mieteröffentlicher Nachrichtenzahlen oder Aktualisierungszeitpunkte. Externe Entwürfe bleiben bis zur Veröffentlichung verborgen.

### KOM-AK-04

Ein Testfall mit einem ausschliesslich im internen Memo vorhandenen Marker weist nach, dass dieser weder im initialen Kommunikations-LLM-Request noch in nachgeladenen Retrieval-/Tool-Ergebnissen, Zusammenfassungen oder wiederverwendetem Gesprächskontext enthalten ist. Eine unauffällige fertige Antwort allein genügt nicht als Nachweis.

### KOM-AK-05

Hinzufügen oder Ändern eines internen Memos verändert bei ansonsten gleichen zulässigen Quellen den fachlichen Eingabekontext der Kommunikationsgenerierung nicht. Memo-abgeleitete und hinsichtlich ihrer Herkunft unklare Inhalte werden auch bei Camunda-Wiederholungen ausgeschlossen; getestet wird der Eingabekontext, nicht die wortgleiche Ausgabe eines LLM.

### KOM-AK-06

Eine Mieterberechtigung ermöglicht keinen KI-/Prozessabbruch, auch nicht über direkte Backend-Aufrufe. Ein Test prüft, dass Seitenwechsel oder Verbindungsabbruch die asynchrone Verarbeitung nicht beendet.

### KOM-AK-07

Nach Abschluss werden interne und externe Nachrichtenschreiboperationen aller Absender abgelehnt. Ein gezielter Konkurrenztest sowie ein verspätetes Worker-Ergebnis prüfen die Synchronisierung von Abschluss und Nachrichtenspeicherung.

## Abgleich mit bestehenden Dokumenten

Die globalen Streaming-/Abbruchvorgaben aus ADR-003, Projekt-Kontext und UC-004 sind bei deren nächster fachlicher Überarbeitung hinsichtlich ihres Geltungsbereichs abzugleichen. Für die Mieter-Fallkommunikation gilt die ausdrücklich festgelegte Regel KOM-03. Eine gegebenenfalls erforderliche Streaming-Demonstration ist separat zu verorten.

Fallidentität, Annahme und Lebenszyklus werden zentral in der [Fallverwaltung](fallverwaltung.md) gepflegt. Direkter persönlicher Tokenzugriff und Berechtigungen werden zentral in [Fallzugriff und Sicherheit](fallzugriff-und-sicherheit.md) gepflegt. Screen-spezifische Felder, Routenübersicht, Wireframes und Bedienungsregeln bleiben in [Screen 01](../frontend/ansicht-01-mieter-fallansicht.md). Erweiterungen der grundlegenden Kommunikationsregeln werden hier vorgenommen und von den betroffenen Screens und Use-Cases referenziert.
