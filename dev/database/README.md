# Datenbank nach dem Hard Reset

Am 16.11.2026 wurde der Neuaufbau ohne Sicherung ausdrücklich freigegeben.
Die Legacy-Migrationen und das vorher vorbereitete Werkzeug zur verlustfreien
Historienumstellung werden nicht mehr benötigt und sind entfernt.

- Entwicklungs-Backend: PostgreSQL auf `127.0.0.1:5433`, Datenbank `propertyflow`.
- Integrationstests: PostgreSQL auf `127.0.0.1:5434`, Datenbank `propertyflow_test`.
- Die Testprozesse laufen auf der getrennten Camunda-Instanz auf Port 8085.
- Beide Datenbanken verwenden die fünf Modulinitialdateien und additive Updates V6–V8.
- Weitere Änderungen beginnen mit V9. Es gibt keine Legacy-Umschaltung oder
  automatische Umschreibung einer bestehenden Flyway-Historie.

Die lokale Verbindung und der Link-Schlüssel stehen in
`.propertyflow-local/backend.properties` und werden nicht in Git aufgenommen.
Der vorher nicht zuordenbare PostgreSQL-Dienst auf Port 5432 wurde nicht verändert.
Die separate Frontend-Demo bleibt unabhängig vom Backend.

Siehe [Datenbankmigrationen nach Modulen](../../docs/architecture/datenbankmigrationen.md)
und [Entwicklungsstart](../README.md#starten-und-stoppen).
