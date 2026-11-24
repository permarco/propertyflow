#requires -Version 7.0
param([string]$CamundaUrl = 'http://127.0.0.1:8080')
$ErrorActionPreference = 'Stop'
$repository = Split-Path $PSScriptRoot -Parent
$files = @('propertyflow-fallprozess-entwurf', 'automatische-abklaerung', 'gespraech-auswerten', 'mitarbeiterbearbeitung',
    'mieterantwort-pruefen', 'mieternachricht-verarbeiten', 'wiedervorlage', 'fallbenachrichtigung-versenden')
$multipart = [System.Net.Http.MultipartFormDataContent]::new()
$client = [System.Net.Http.HttpClient]::new()
try {
    foreach ($name in $files) {
        # Ausführbare Anwendungsressourcen unverändert deployen; keine Laufzeit-Umschreibung.
        $settings = [System.Xml.XmlReaderSettings]::new()
        $settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
        $settings.XmlResolver = $null
        $modelPath = Join-Path $repository "src/main/resources/bpmn/$name.bpmn"
        $reader = [System.Xml.XmlReader]::Create($modelPath, $settings)
        try {
            $document = [System.Xml.XmlDocument]::new()
            $document.PreserveWhitespace = $true
            $document.XmlResolver = $null
            $document.Load($reader)
        } finally { $reader.Dispose() }
        foreach ($process in $document.GetElementsByTagName('process', 'http://www.omg.org/spec/BPMN/20100524/MODEL')) {
            if ($process.GetAttribute('isExecutable') -ne 'true') { throw "Nicht ausführbare BPMN-Ressource: $name" }
        }
        $content = [System.Net.Http.StringContent]::new([System.IO.File]::ReadAllText($modelPath), [System.Text.Encoding]::UTF8, 'application/xml')
        $multipart.Add($content, 'resources', "$name.bpmn")
    }
    $response = $client.PostAsync("$CamundaUrl/v2/deployments", $multipart).GetAwaiter().GetResult()
    $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) { throw "BPMN-Deployment fehlgeschlagen: $($response.StatusCode) $body" }
    $deployment = $body | ConvertFrom-Json
    $processes = @($deployment.deployments | ForEach-Object { $_.processDefinition })
    $caseKey = ($processes | Where-Object processDefinitionId -eq 'Fall_bearbeiten_PROCESS').processDefinitionKey
    $mailKey = ($processes | Where-Object processDefinitionId -eq 'Fallbenachrichtigung_versenden_PROCESS').processDefinitionKey
    if (-not $caseKey -or -not $mailKey) { throw 'Die Fall- oder Maildefinition fehlt in der Deployment-Antwort.' }
    @("propertyflow.workflow.definition-key=$caseKey", "propertyflow.case-mail.definition-key=$mailKey") |
        Set-Content (Join-Path $repository '.propertyflow-local/runtime.properties') -Encoding utf8
    Write-Host "$($files.Count) BPMN-Modelle bereit: Fall $caseKey, Mail $mailKey."
} finally {
    $multipart.Dispose()
    $client.Dispose()
}
