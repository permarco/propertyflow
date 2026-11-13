# Prüffälle für den PropertyFlow-Fallprozess

**Stand** · 13.11.2026. Die folgenden Prüffälle sind für spätere Integrationstests vorgesehen.

Die drei Fälle konkretisieren die Prozessausgänge aus [ADR-002](../architecture/adr/ADR-002-camunda8-workflow-orchestrierung.md#beispiel--zuständigkeit-und-vorgehen-empfehlen) und [UC-018](../use_cases/UC-018-zustaendigkeit-und-vorgehen-empfehlen.md). Die Fachregeln und Freigaben in den Beispielen sind kontrollierte Testdaten. Die produktiven Regeln für die automatische Veröffentlichung werden im Kommunikationsvertrag konkretisiert.

## Gemeinsamer Testaufbau

- Ein aktiver Fall ist mit seiner Case-ID gespeichert und einem laufenden Camunda-Fallprozess zugeordnet. Zu Beginn gibt es für die Meldung noch keinen Antwort- oder Abschlussnachweis.
- Der Test steuert die Wissensquellen und die Ausgabe am Modellport. Camunda und die PropertyFlow-Dienste verarbeiten den Auftrag. Geprüft werden der gespeicherte Fallstand, sichtbare Nachrichten und die Fortsetzung des Prozesses. Für den Nachrichtentext zählen die fachlichen Aussagen.
- Als Wiederholungsprüfung wird derselbe fachliche Folgeauftrag erneut zugestellt. Nachricht, Antwortbedarf, Abschluss und zugehörige logische Benachrichtigungsaufträge dürfen dadurch nicht doppelt entstehen.

Die [Evaluationsgrundlage](evaluationsgrundlage.md#bewertungsprinzip) beschreibt die fachliche Beurteilung von KI-Ausgaben. Die hier vorgegebenen Modellausgaben prüfen den Prozess und seine Prüfregeln. Ein Lauf mit einem echten Modell kann zusätzlich zeigen, ob es die Zuständigkeit und fehlende Angaben korrekt erkennt.

## PF-01 · Private Lampe

> Meine Lampe zu Hause funktioniert nicht mehr.

**Testdaten** · Die Fallangaben bestätigen, dass es sich um die private Lampe des Mieters handelt. Eine freigegebene Testregel weist den Austausch dem Mieter zu. Es gibt keinen weiteren Bearbeitungsbedarf der Verwaltung und keine ausstehende menschliche Entscheidung. Die Testregeln erlauben die automatische Veröffentlichung der Antwort.

**Modellausgabe** · Die private Lampe fällt in die Zuständigkeit des Mieters. Das Modell schlägt eine direkte Antwort und den Abschluss vor.

**Erwarteter Ausgang** · PropertyFlow veröffentlicht die Antwort im Fallverlauf und schliesst den Fall mit dem Grund «Kein Bearbeitungsbedarf der Verwaltung» ab.

**Prüfkriterien**

- PropertyFlow prüft die Einordnung anhand der Fallangaben und der hinterlegten Regel.
- Die sichtbare Antwort erklärt die Zuständigkeit des Mieters. Sie enthält keine Behauptung, die Lampe sei repariert worden.
- Die Antwort wird vor dem Abschluss gespeichert und veröffentlicht. Sie bleibt unter derselben Case-ID lesbar.
- PropertyFlow speichert den Status «Abgeschlossen», die Abschlussquelle «System» und die geprüfte Fachregel als Grundlage. Der Camunda-Fallprozess durchläuft den Abschlusszweig.
- Der Abschluss erzeugt genau einen logischen Abschlussmailauftrag. Eine Wiederholung erzeugt keine weitere Antwort und keinen zusätzlichen Abschlussnachweis oder Abschlussmailauftrag.

[FALL-07 · Abschluss](../specifications/fallverwaltung.md#fall-07-abschluss-und-zeit-danach) · [KOM-04 · Nachrichtensperre](../specifications/fallkommunikation.md#kom-04-nachrichtensperre-nach-fallabschluss) · [BEN-05 · Fallabschluss und Sicherheit](../specifications/benachrichtigungen-und-zustellung.md#ben-05-fallabschluss-und-sicherheit)

## PF-02 · Lüftungsfilter

> Der Lüftungsfilter im Badezimmer muss ausgetauscht werden.

**Testdaten** · Die freigegebene Testregel weist diesen Filterwechsel dem Mieter zu. Die Verwaltung kann beim Organisieren eines Ersatzfilters helfen. Die Testregeln erlauben eine unverbindliche Rückfrage mit Kostenhinweis. Es liegt kein vorrangiger Prüfbedarf vor.

**Modellausgabe** · «Sollen wir für Sie einen Ersatzfilter organisieren? Die Kosten für den Filter würden Sie tragen.»

**Erwarteter Ausgang** · PropertyFlow veröffentlicht die Rückfrage mit Antwortbedarf. Der Fall bleibt aktiv und erhält den Status «Wartet auf Mieterantwort».

**Prüfkriterien**

- Die sichtbare Rückfrage bietet Hilfe bei der Organisation an und weist darauf hin, dass der Mieter die Filterkosten tragen würde.
- PropertyFlow speichert den offenen Antwortbedarf an der veröffentlichten Nachricht. Camunda wartet im bestehenden Fallprozess auf die Mieterantwort.
- Die Rückfrage erzeugt keinen Abschlussnachweis und keinen Abschlussmailauftrag. Es wird keine Bestellung und keine Verrechnung ausgelöst.
- Eine Wiederholung des Auftrags erzeugt weder eine zweite Rückfrage noch einen zweiten Antwortbedarf oder einen zusätzlichen Benachrichtigungsauftrag für dieselbe Rückfrage.

Bei einer Zusage klärt die Immobilienbewirtschaftung die Kosten und entscheidet über eine allfällige Bestellung, die sie ausserhalb von PropertyFlow organisiert.

[FALL-05 · Fachlicher Lebenszyklus](../specifications/fallverwaltung.md#fall-05-fachlicher-lebenszyklus) · [FALL-10 · Menschliche Freigabe](../specifications/fallverwaltung.md#fall-10-automatisierung-und-menschliche-freigabe) · [KOM-01 · Antwortbedarf](../specifications/fallkommunikation.md#antwortbedarf-einer-externen-mitarbeiternachricht)

## PF-03 · Unklare Lampe

> Die Lampe funktioniert nicht mehr.

**Testdaten** · Die Fallangaben lassen offen, ob es sich um eine private Lampe oder eine Leuchte im Treppenhaus handelt. Aus den freigegebenen Quellen lässt sich für diese Meldung keine eindeutige Zuständigkeit ableiten.

**Modellausgabe** · Das Modell behauptet dennoch, die Lampe sei privat, und schlägt den Systemabschluss vor.

**Erwarteter Ausgang** · PropertyFlow weist die vorgeschlagene automatische Aktion zurück. Der Fall bleibt aktiv und erhält den Status «Mitarbeiterprüfung erforderlich».

**Prüfkriterien**

- Der vorgeschlagene Abschluss wird bei der serverseitigen Prüfung abgewiesen, weil die Fallangaben die Zuordnung nicht belegen.
- Der fehlende Nachweis zur Zuständigkeit ist im Mitarbeiterdetail als Prüfgrund nachvollziehbar. Camunda führt den Fall zur fachlichen Prüfung weiter.
- PropertyFlow veröffentlicht keine automatische Zuständigkeitsauskunft und erzeugt keinen Abschlussnachweis oder Abschlussmailauftrag. Der Fall bleibt manuell bearbeitbar.
- Auch bei Wiederholung desselben Auftrags bleibt die unbelegte Zuordnung ohne Abschlusswirkung.

[FALL-05 · Fachlicher Lebenszyklus](../specifications/fallverwaltung.md#fall-05-fachlicher-lebenszyklus) · [FALL-07 · Abschluss](../specifications/fallverwaltung.md#fall-07-abschluss-und-zeit-danach) · [FALL-10 · Menschliche Freigabe](../specifications/fallverwaltung.md#fall-10-automatisierung-und-menschliche-freigabe)
