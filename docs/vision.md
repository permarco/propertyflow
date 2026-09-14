# PropertyFlow – Vision

KI-gestützte Triage und Bearbeitung von Mieteranliegen

## Overview

Immobilienverwaltungen erhalten regelmässig Mieteranliegen zu Schäden,
technischen Problemen oder anderen Fragen rund um das Mietobjekt.
Diese Meldungen liegen häufig als unstrukturierter Freitext vor und müssen
durch die Immobilienbewirtschaftung manuell beurteilt, priorisiert und der
richtigen Zuständigkeit zugeordnet werden.

Für die Beurteilung müssen teilweise zusätzliche Informationen wie interne
Richtlinien, relevante Regelwerke oder Erkenntnisse aus früheren Fällen
herangezogen werden. Dies verursacht manuellen Abklärungsaufwand und kann
insbesondere bei dringenden oder unklaren Anliegen zu Verzögerungen führen.

PropertyFlow unterstützt diesen Prozess durch die strukturierte Erfassung von
Mieteranliegen, eine KI-gestützte Analyse und Dringlichkeitsbewertung sowie
durch Zuständigkeits- und Handlungsempfehlungen auf Basis einer Wissensbasis.
Die abschliessende fachliche Beurteilung und Entscheidung verbleibt bei der
Immobilienbewirtschaftung.

## Vision

PropertyFlow unterstützt Immobilienverwaltungen dabei, Mieteranliegen
schneller, nachvollziehbarer und mit der richtigen Zuständigkeit zu bearbeiten.

## Stakeholders

| Stakeholder | Kerninteresse |
|---|---|
| Mieterinnen und Mieter | Anliegen einfach melden und rasch eine verständliche Rückmeldung zum weiteren Vorgehen erhalten. |
| Immobilienbewirtschaftung | Anliegen effizient beurteilen, Dringlichkeiten erkennen, Zuständigkeiten klären und die weitere Bearbeitung koordinieren. |
| Hauswart / Facility Management | Ausreichend abgeklärte und relevante Aufträge mit den für die Bearbeitung notwendigen Informationen erhalten. |

## Core Capabilities

### 1. Erfassung und Strukturierung von Mieteranliegen

Mieteranliegen werden über ein Webformular oder per E-Mail erfasst
und als bearbeitbarer Fall in PropertyFlow gespeichert.

**KI-Nutzen:** Die KI analysiert die unstrukturierte Beschreibung und
extrahiert daraus für die weitere Bearbeitung relevante Informationen wie
Problemart, betroffenes Objekt und weitere Angaben.

### 2. Dringlichkeitsbewertung

PropertyFlow beurteilt eingehende Mieteranliegen hinsichtlich ihrer
Dringlichkeit und unterstützt damit die Priorisierung der weiteren Bearbeitung.

**KI-Nutzen:** Die KI bewertet den Inhalt der Meldung semantisch und kann
dadurch insbesondere Situationen erkennen, die eine schnelle oder sofortige
Bearbeitung erfordern.

### 3. Zuständigkeits- und Handlungsempfehlung anhand einer Wissensbasis

PropertyFlow sucht zu einem Mieteranliegen relevante Informationen aus einer
Wissensbasis, beispielsweise interne Richtlinien, relevante Regelwerke und
vergleichbare frühere Fälle, und unterstützt damit die Beurteilung der
Zuständigkeit und des weiteren Vorgehens.

**KI-Nutzen:** Mittels semantischer Suche beziehungsweise RAG werden relevante
Informationen zum konkreten Anliegen gefunden und von der KI zu einer
begründeten Zuständigkeits- und Handlungsempfehlung zusammengeführt.

### 4. Prüfung und Bearbeitung durch die Immobilienbewirtschaftung

Die Immobilienbewirtschaftung kann die von PropertyFlow aufbereiteten
Informationen und Empfehlungen prüfen, korrigieren oder ablehnen.
PropertyFlow steuert den Bearbeitungsprozess eines Mieteranliegens über
mehrere Bearbeitungsschritte hinweg und hält den aktuellen Zustand
auch bei länger dauernden Warte- und Freigabephasen fest.

**KI-Nutzen:** Die KI reduziert den manuellen Abklärungsaufwand, indem sie
strukturierte Informationen, eine Dringlichkeitsbewertung und eine begründete
Handlungsempfehlung bereitstellt; die endgültige fachliche Entscheidung
verbleibt beim Menschen.

## Optional Extension

### KI-gestützte Rückfragen

Wenn für eine zuverlässige Beurteilung eines Mieteranliegens wichtige
Informationen fehlen, kann PropertyFlow gezielte Rückfragen an die
Mieterin oder den Mieter formulieren.

Diese Funktion gehört nicht zum verpflichtenden MVP und wird nur umgesetzt,
wenn der Projektfortschritt und der verfügbare Umfang dies zulassen.
