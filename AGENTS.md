# AGENTS.md

## Überblick

Dieses Repository enthält das Projekt PropertyFlow, eine Spring Boot-Anwendung für die Verwaltung und Analyse von Immobilienbeständen, Mietverhältnissen und operativen Prozessen.

## Richtlinien

- Bevorzugt kleine, gezielte Änderungen mit klaren Commit-Scopes.
- Dokumentation und Architekturentscheidungen gehören in den `docs/`-Bereich.
- Neue Features sollten in verständlichen, fachlich benannten Modulen und Packages entstehen.
- Tests sind für fachlich relevante Wartungs- und Betriebslogik erforderlich.

## Typische Arbeitsabläufe

- Build: `./mvnw clean test`
- Starten: `./mvnw spring-boot:run`
- Docker/Compose: `docker compose up`

## Struktur

- `src/main/`: Anwendungscode
- `src/test/`: Tests
- `docs/`: Projektkontext, Fachkonzeption und Architekturentscheidungen

## Dokumentationspflichten

Dokumente, die Architekturentscheidungen, Systemgrenzen oder fachliche Zielsetzungen betreffen, müssen im `docs/`-Baum ergänzt werden.
