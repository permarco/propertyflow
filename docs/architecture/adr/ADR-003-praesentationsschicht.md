# ADR-002: Präsentationsschicht – SSR mit gezielter clientseitiger Interaktivität

## Status

Accepted

## Kontext

PropertyFlow stellt zwei wesentliche Benutzeroberflächen bereit:

1. Mieterinnen und Mieter erfassen ein Anliegen über ein Formular.
2. Immobilienbewirtschafter bearbeiten eingegangene Anliegen in einem Cockpit mit
   Listen-, Detail- und Bearbeitungsansichten.

Die Benutzerinteraktion ist überwiegend formular- und workfloworientiert. Eine
vollständige Single-Page-Application mit umfangreichem clientseitigem State
ist für die Kernfunktionen nicht erforderlich.

Einzelne Funktionen benötigen jedoch dynamisches Verhalten im Browser.
Insbesondere soll die KI-Analyse eines Mieteranliegens ihren Zustand
(`waiting`, `streaming`, `completed`, `aborted`, `error`) anzeigen können und
eine laufende Ausgabe muss durch den Benutzer abgebrochen werden können.

Für die Präsentationsschicht sind insbesondere folgende Qualitätsziele relevant:

- geringe technische Komplexität im Frontend,
- nachvollziehbare und wartbare Implementierung,
- kurze Ladezeiten und begrenzte JavaScript-Menge,
- barrierefreie Bedienbarkeit,
- sichere Darstellung von Benutzer- und KI-generierten Inhalten,
- Unterstützung gezielter interaktiver Funktionen wie KI-Streaming.

## Entscheidung

PropertyFlow verwendet Server-Side Rendering (SSR) als primäre
Rendering-Strategie.

Formulare, Listen, Detailansichten und normale Bearbeitungsabläufe werden
serverseitig gerendert. Clientseitiges JavaScript wird gezielt dort ergänzt,
wo ein fachlicher oder qualitativer Nutzen besteht.

Insbesondere wird die Darstellung der KI-Analyse clientseitig erweitert, um:

- den aktuellen Verarbeitungsstatus anzuzeigen,
- die KI-Antwort schrittweise darzustellen,
- eine laufende Anfrage wirksam abbrechen zu können,
- Fehlerzustände darzustellen,
- die Ausgabe sicher als Text zu behandeln.

Damit wird bewusst ein hybrider Ansatz aus SSR und gezielter
clientseitiger Interaktivität gewählt, ohne die gesamte Anwendung als
CSR-/SPA-Anwendung umzusetzen.

## Entscheidungsgründe

### Geringe bis mittlere Interaktivität

Die Kernabläufe von PropertyFlow bestehen hauptsächlich aus dem Erfassen,
Anzeigen und Bearbeiten von Mieteranliegen. Dafür ist eine vollständig
clientseitig gerenderte Anwendung nicht erforderlich.

### Reduzierte Frontend-Komplexität

SSR reduziert den Bedarf an clientseitigem State Management, Routing und
umfangreicher JavaScript-Infrastruktur.

### Performance

Die wesentlichen Inhalte können bereits als HTML ausgeliefert werden.
Zusätzliches JavaScript wird nur für Funktionen geladen bzw. eingesetzt,
die tatsächlich Client-Interaktivität benötigen.

### Wartbarkeit

Die Präsentationslogik bleibt möglichst einfach. Interaktive Funktionen
werden auf klar abgegrenzte Bereiche beschränkt.

### KI-Streaming

Die KI-Analyse benötigt dynamisches Verhalten. Diese Anforderung rechtfertigt
gezielte clientseitige Logik, jedoch nicht die Umstellung der gesamten
Anwendung auf CSR.

## Betrachtete Alternativen

### Alternative 1: Vollständiges CSR / Single-Page-Application

Eine SPA, beispielsweise mit Angular, würde eine hohe Flexibilität für
dynamische Benutzeroberflächen bieten.

**Vorteile**
- hohe Interaktivität,
- dynamische Aktualisierung ohne vollständige Seitenwechsel,
- gute Unterstützung komplexer Client-Zustände.

**Nachteile**
- zusätzliche Frontend-Komplexität,
- mehr JavaScript im Browser,
- zusätzliches State- und API-Management,
- für die derzeitigen PropertyFlow-Anforderungen nur begrenzt notwendig.

Diese Variante wurde verworfen, da ihre zusätzliche Komplexität für die
überwiegend formular- und workfloworientierte Anwendung derzeit keinen
ausreichenden Nutzen bietet.

### Alternative 2: Ausschliessliches SSR ohne Client-JavaScript

Alle Interaktionen könnten über klassische HTTP-Requests und vollständige
Seitenantworten abgewickelt werden.

**Vorteile**
- sehr geringe Client-Komplexität,
- minimale JavaScript-Abhängigkeit,
- einfache technische Struktur.

**Nachteile**
- keine geeignete Benutzererfahrung für Streaming,
- laufende KI-Anfragen könnten nicht komfortabel abgebrochen werden,
- Statusänderungen während der Verarbeitung wären nur eingeschränkt
  darstellbar.

Diese Variante wurde verworfen, weil die KI-Integration gezielte
clientseitige Interaktivität benötigt.

## Konsequenzen

### Positive Konsequenzen

- geringe Grundkomplexität der Präsentationsschicht,
- weniger clientseitiger State,
- JavaScript wird gezielt statt flächendeckend eingesetzt,
- Streaming und Abbruch der KI-Analyse bleiben möglich,
- SSR und interaktive KI-Funktionen können kombiniert werden.

### Negative Konsequenzen

- die Anwendung verwendet zwei Rendering- bzw. Interaktionsmechanismen,
- für interaktive Bereiche muss zusätzlicher JavaScript-Code gepflegt und
  getestet werden,
- die Grenze zwischen serverseitiger und clientseitiger Verantwortung muss
  klar definiert bleiben.

## Leitplanken

- SSR ist der Standard; clientseitige Logik benötigt einen konkreten
  fachlichen oder qualitativen Grund.
- Benutzer- und KI-generierte Inhalte werden standardmässig als Text
  ausgegeben und nicht ungeprüft als HTML interpretiert.
- Interaktive Funktionen müssen per Tastatur bedienbar sein.
- KI-Aufrufe müssen einen sichtbaren Status, einen wirksamen Abbruch und
  einen definierten Fehlerpfad besitzen.
- Generierter Frontend-Code wird durch automatisierte Tests und manuelle
  Qualitätsprüfung verifiziert.

## Folgen für die weitere Umsetzung

Die Entscheidung wird neu bewertet, falls spätere Anforderungen deutlich mehr
clientseitige Interaktivität erfordern, beispielsweise komplexe
Echtzeit-Dashboards oder umfangreichen lokalen Anwendungszustand.
