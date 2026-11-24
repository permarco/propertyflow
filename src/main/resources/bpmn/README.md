# Ausführbare PropertyFlow-Prozessmodelle

Diese acht BPMN-Dateien sind die verbindlichen Quellen der deployten Prozesse.
Maven nimmt sie unter `BOOT-INF/classes/bpmn/` in das Anwendungs-JAR auf.
`dev/deploy-models.ps1` deployt dieselben Quelldateien unverändert; es ergänzt
keine Ausführungseigenschaften. Integrationstests laden die Ressourcen aus dem Klassenpfad.

Die fachliche Dokumentation, Versionsgeschichte und Diagrammregeln liegen unter
[docs/architecture/bpmn](../../../../docs/architecture/bpmn/README.md).
Modelle im Camunda Modeler direkt aus diesem Ressourcenordner öffnen.
Die gemeinsame Gesprächsauswertung ist in [gespraech-auswerten.bpmn](gespraech-auswerten.bpmn)
modelliert. Die automatische Abklärung, die Mieternachrichtenverarbeitung und
die Mieterantwortprüfung rufen sie auf.

Die Dateinamen bleiben die Einstiegspunkte im Repository. Sichtbare Namen und
XML-IDs folgen dem Namensschema in den
[Camunda-Richtlinien](../../../../docs/specifications/CAMUNDA_GUIDELINES.md#32-namen-und-ids-in-bpmn-modellen).
Ausführung und Testdeployments verwenden die aktuellen Prozess- und Element-IDs.
`isExecutable=true` ist keine fachliche Produktionsfreigabe: Automatische Abschlüsse
bleiben serverseitig gesperrt, die Modellevaluation bleibt zurückgestellt.

Deployment ist ein ausdrücklicher Schritt des Devstarts. Ein gewöhnlicher Backendstart
deployt nicht ungefragt neue Prozessversionen. Laufende Instanzen werden nicht migriert.
