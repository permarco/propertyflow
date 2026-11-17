# Annahmen und Umsetzung: Konten und Fallzugänge

## Auftrag und Grenzen

Der Benutzer hat am 16.11.2026 die selbständige Vervollständigung des manuellen
Betriebs, die Behebung der gemeldeten Mitarbeiteranmeldung und Tests beauftragt.
Die Modellevaluation bleibt zurückgestellt. `AutomaticClosureRelease.approved`
bleibt immer `false`. Bestehende Entwicklungsdaten und Link-Schlüssel bleiben erhalten.

## Konkretisierung offener technischer Details

- Mitarbeiter melden sich weiter unter `/anmelden` an. Mieterkonten verwenden
  `/mieter/anmelden`, die Registrierung `/registrieren`. Die Anmeldeseiten verlinken
  beide Zugänge. Im lokalen Devprofil zeigt die Mitarbeiteranmeldung die tatsächlich
  eingerichtete Adresse und den Mailpit-Link; ausserhalb des Devprofils nicht.
- Die erste HTTP-Diagnose der gemeldeten Anmeldung war erfolgreich: Codeanforderung
  HTTP 200, Codeprüfung HTTP 303 zur Fallübersicht. Eine konkrete technische Ursache
  des Benutzerfehlers war damit nicht reproduziert. Die bisher fehlende Zugangshilfe
  wird verbessert; unbekannte Adressen erhalten weiterhin eine neutrale Rückmeldung.
- Mitarbeiter- und Mieterkonten bleiben getrennte Rollenidentitäten. Dieselbe
  Postfachadresse darf ein freiwilliges Mieterkonto besitzen; Registrierung verleiht
  niemals Mitarbeiterrechte. Ein erfolgreicher Rollenwechsel im Browser entfernt
  die andere Rollenidentität und erneuert Sitzung und CSRF-Wert.
- Mieterkonten werden erst nach erfolgreicher E-Mail-Codeprüfung gespeichert.
  Gesperrte Konten werden durch eine erneute Registrierung nicht aktiviert.
  Die existierenden Codegrenzen gelten auch hier: zehn Minuten, fünf Fehlversuche,
  eine Minute zwischen Anforderungen, fünf Anforderungen je Stunde und Adresse.
  Codes liegen nur als HMAC im Arbeitsspeicher und verlieren bei Neustart ihre
  Gültigkeit. Konten und Fälle bleiben in PostgreSQL erhalten; erneute Anmeldung ist
  nach einem Backendneustart vorgesehen.
- Die Fallmenge wird bei jedem Zugriff anhand der exakt normalisierten aktuellen
  Kontaktadresse bestimmt. Keine nachträgliche Fallverknüpfung und keine Ableitung
  der Rechte aus einer Formularadresse. Neue Kontoeinreichungen verwenden die
  serverseitig verifizierte Adresse und eine kontogebundene Absendeidentität.
- Die Kontoverwaltung kann Mitarbeiterkonten mit der Mitarbeiterrolle anlegen,
  sperren und ihnen die Mitarbeiterrolle entziehen. Bestehende Sitzungen werden
  durch die erneute Rechteprüfung beim nächsten Aufruf unwirksam. Eine gemeinsame
  Datenbanksperre schützt das letzte aktive Verwaltungskonto auch bei Parallelität.
- Das ursprüngliche Verwaltungskonto erhält das Zusatzrecht «Fallzugänge verwalten».
  Die reguläre Kontoanlage vergibt gemäss HTTP-Vertrag keine zusätzlichen
  Verwaltungsrechte. Weitere Rechtevergaben benötigen einen eigenen administrativen
  Bereitstellungsweg; dieser Ausbau ist hier nicht enthalten.
- Die Fallzugangsverwaltung liegt separat unter `/mitarbeiter/fallzugaenge`, nicht
  als fachliche Aktion im Falldetail. Als Konkretisierung des noch offenen
  Prüfverfahrens dokumentiert die berechtigte Person zuerst eine unabhängige
  Identitätsprüfung desselben Mieters (z. B. Mietvertragsabgleich und Rückruf über
  eine bekannte Nummer). Zusätzlich muss ein einmaliger Code aus dem Zielpostfach
  bestätigt werden. Der Code allein belegt keine Mieteridentität.
- Der serverseitige Prüfnachweis ist an Mitarbeiter, Fall, Fallversion und Zieladresse
  gebunden, gilt 15 Minuten und erlaubt fünf Fehlversuche. Höchstens fünf Anforderungen
  je Stunde und Mitarbeiter oder Fall. Noch unbestätigte Codes müssen nach Neustart neu angefordert
  werden. Bereits verifizierte Nachweise behalten ihre verbleibende Gültigkeit. Die Identitätsbegründung bleibt im geschützten Prüfnachweis. Das getrennte
  Aktionsaudit enthält nur Mitarbeiter, Fall, Handlung, Zeitpunkt und Prüfreferenz.
- Linkersatz, gegebenenfalls Adresskorrektur, Verbrauch des Nachweises und Versandauftrag
  werden gemeinsam gespeichert. Identische Wiederholung verbraucht keinen weiteren
  Link. Die ursprüngliche Kontaktadresse bleibt separat erhalten. Kein Fallstatus
  wird durch diesen administrativen Vorgang geändert; Camunda ist dafür nicht nötig.
- Ein eigener persistenter Versandauftrag stellt Ersatzlinks auch bei Camunda-Ausfall
  zu. Er lädt die aktuelle Kontaktadresse und den aktuell gültigen Link vor dem Versand.
  Ein unbekannter SMTP-Ausgang oder Prozessabbruch führt zu `UNCERTAIN`, keinem blinden
  Neuversand. Eine Betriebsoberfläche zur Klärung solcher Versandzustände ist weiterhin
  ein separater Ausbau.
- Migrationen V1–V5 bleiben unverändert. V6 ergänzt Konten und Berechtigungen, V7 die
  geprüfte Fallzugangsverwaltung und V8 deren getrenntes Audit und Befehlsverträge. Es gibt keinen Reset und keine Änderung alter Prüfsummen.

## Unveränderte Grenzen

OAuth/MCP-Delegation, E-Mail-Eingang und KI-Formulierung sind nicht Teil dieses
Ausbaus. Der persönliche Fall-Link bleibt unabhängig vom Konto gültig, bis er
ausdrücklich ersetzt wird. Die KI-Fallback-Spezifikation und die Camunda-Führung
fachlicher Aktionen bleiben verbindlich. Der automatische Abschluss bleibt gesperrt.

Die [Umsetzungsmatrix](../use_cases/umsetzungsstand-2026-11-16.md) trennt vorhandenen
Code, konkrete Nachweise und verbleibende Lücken.

## HTTP-Verträge

Die dokumentierten Befehlsrouten für Kontoanlage, Zugriffsentzug, Linkersatz und
Adresskorrektur bleiben erhalten. Zugriffsentzug verwendet `action`, `operation`
und `csrf`; Wiederholungen derselben Aktion erzeugen keinen weiteren Auditdatensatz.
Zusätzliche Formularfelder wie eine frei gewählte Verwaltungsrolle werden abgewiesen.
Die Befehlsrouten für Linkersatz und Adresskorrektur liefern HTTP 204 ohne Token.

Die Browserführung ergänzt `/pruefung` zur Anforderung und
`/pruefung/{proofId}/bestaetigen` zur Codeprüfung. `/anwenden` verbindet die Codeprüfung
und den anschliessenden Änderungsbefehl für die HTML-Bestätigungsseite. Der tatsächliche
Änderungsdienst verlangt bereits verifizierte, serverseitig gebundene Nachweise;
eine erratene Referenz oder ein noch unbestätigter Codeauftrag genügt nicht.
