#requires -Version 7.0
param([switch]$ApplicationOnly)
$ErrorActionPreference = 'Stop'
$repository = Split-Path $PSScriptRoot -Parent
Set-Location $repository
$processFile = Join-Path $repository '.propertyflow-local/dev-process.json'
if (Test-Path -LiteralPath $processFile) {
    $saved = Get-Content $processFile -Raw | ConvertFrom-Json
    $app = Get-Process -Id $saved.id -ErrorAction SilentlyContinue
    # PID-Wiederverwendung darf keinen fremden Prozess beenden.
    if ($app -and $app.ProcessName -eq 'java' -and $app.StartTime.ToUniversalTime().Ticks -eq $saved.startedTicks) {
        Stop-Process -Id $app.Id
        Wait-Process -Id $app.Id -Timeout 30 -ErrorAction SilentlyContinue
    } elseif ($app) { throw 'Die Prozesskennung gehört nicht mehr zum gespeicherten Devstart. Es wird kein fremder Prozess beendet.' }
    Remove-Item -LiteralPath $processFile
}
if (-not $ApplicationOnly) {
    $env:PROPERTYFLOW_DATABASE_PASSWORD = 'unused-for-stop'
    $env:PROPERTYFLOW_CAMUNDA_DATABASE_PASSWORD = 'unused-for-stop'
    & docker compose -f dev/compose.yaml -f dev/compose.runtime.yaml stop
    if ($LASTEXITCODE -ne 0) { throw 'Die Laufzeitdienste konnten nicht gestoppt werden.' }
    & docker compose -f dev/compose.backend.yaml stop
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL konnte nicht gestoppt werden.' }
}
Write-Host 'Entwicklungsdienste gestoppt; Container und persistente Daten bleiben erhalten.'
