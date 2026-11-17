# Ausführbare PropertyFlow-Prozessmodelle

Diese sieben BPMN-Dateien sind die verbindlichen Quellen der deployten Prozesse.
Maven nimmt sie unter `BOOT-INF/classes/bpmn/` in das Anwendungs-JAR auf.
`dev/deploy-models.ps1` deployt dieselben Quelldateien unverändert; es ergänzt
keine Ausführungseigenschaften. Integrationstests laden die Ressourcen aus dem Klassenpfad.

Die fachliche Dokumentation, Versionsgeschichte und Diagrammregeln liegen unter
[docs/architecture/bpmn](../../../../docs/architecture/bpmn/README.md).
Modelle im Camunda Modeler direkt aus diesem Ressourcenordner öffnen.

Prozess-IDs, Dateinamen und historische Anzeigenamen mit `Draft`/`Entwurf` bleiben
zur Kompatibilität erhalten. Sie bedeuten nicht, dass die Ressourcen unausführbar sind.
`isExecutable=true` ist keine fachliche Produktionsfreigabe: Automatische Abschlüsse
bleiben serverseitig gesperrt, die Modellevaluation bleibt zurückgestellt.

Deployment ist ein ausdrücklicher Schritt des Devstarts. Ein gewöhnlicher Backendstart
deployt nicht ungefragt neue Prozessversionen. Laufende Instanzen werden nicht migriert.
