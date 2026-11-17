# Initialschema nach Modulen

| Modul | Datei | Inhalt |
|---|---|---|
| access | `access/V1__initial_schema.sql` | Mitarbeiterkonten und Einrichtung |
| caseprocessing | `caseprocessing/V2__initial_schema.sql` | Fallpersistenz einschliesslich Analyseergebnissen, Empfehlungen, Kommunikation und Prozessaufträgen |
| intake | `intake/V3__initial_schema.sql` | Aktuell keine eigenen Tabellen |
| triage | `triage/V4__initial_schema.sql` | Aktuell keine eigenen Tabellen |
| recommendation | `recommendation/V5__initial_schema.sql` | Aktuell keine eigenen Tabellen |

Flyway lädt diese Unterordner rekursiv. Die Versionsnummern sind gemeinsam
eindeutig. Weitere Änderungen beginnen mit V6 im Ordner `updates`.

Nach dem freigegebenen Hard Reset existiert nur noch dieser Migrationsweg.
Legacy-Skripte und automatische Historienumschaltung sind entfernt.

Siehe [Datenbankmigrationen nach Modulen](../../../../../docs/architecture/datenbankmigrationen.md).
