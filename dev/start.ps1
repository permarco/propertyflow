#requires -Version 7.0
param([switch]$SkipBuild, [switch]$InfrastructureOnly, [switch]$SkipSeed)
$ErrorActionPreference = 'Stop'
$repository = Split-Path $PSScriptRoot -Parent
Set-Location $repository
$local = Join-Path $repository '.propertyflow-local'
New-Item -ItemType Directory -Path $local -Force | Out-Null

function Read-LocalProperties([string]$Path) {
    $values = @{}
    if (Test-Path -LiteralPath $Path) {
        foreach ($line in Get-Content -LiteralPath $Path -Encoding utf8) {
            if ($line -match '^([^#!][^=]*)=(.*)$') { $values[$Matches[1].Trim()] = $Matches[2] }
        }
    }
    return $values
}

function Invoke-Compose([string[]]$Arguments) {
    & docker compose @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose konnte die Entwicklungsdienste nicht starten.' }
}

function Initialize-Examples {
    if (-not $SkipSeed) {
        & node dev/seed.mjs
        if ($LASTEXITCODE -ne 0) { throw 'Beispielanlage unvollständig. Anwendung läuft; Einzelheiten stehen in der Seed-Ausgabe.' }
    }
}

$backendFile = Join-Path $local 'backend.properties'
$backend = Read-LocalProperties $backendFile
if (-not (Test-Path -LiteralPath $backendFile)) {
    $password = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
    $key = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    @('spring.datasource.url=jdbc:postgresql://127.0.0.1:5433/propertyflow',
        'spring.datasource.username=propertyflow', "spring.datasource.password=$password",
        "propertyflow.links.encryption-key=$key") | Set-Content $backendFile -Encoding utf8
    $backend = Read-LocalProperties $backendFile
}
if (-not $backend['spring.datasource.password'] -or -not $backend['propertyflow.links.encryption-key']) {
    throw 'Bestehende backend.properties ist unvollständig. Passwort und stabilen Link-Schlüssel erhalten und ergänzen.'
}
$env:PROPERTYFLOW_DATABASE_PASSWORD = $backend['spring.datasource.password']
$runtimeSecret = Join-Path $local 'camunda-database-password'
if (-not (Test-Path -LiteralPath $runtimeSecret)) {
    [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24)) |
        Set-Content $runtimeSecret -Encoding utf8 -NoNewline
}
$env:PROPERTYFLOW_CAMUNDA_DATABASE_PASSWORD = Get-Content $runtimeSecret -Raw
& docker volume create propertyflow-dev_mailpit-data | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Das dauerhafte Mailpit-Volume konnte nicht bereitgestellt werden.' }
Invoke-Compose @('-f', 'dev/compose.backend.yaml', 'up', '-d', '--wait', '--wait-timeout', '120')
Invoke-Compose @('-f', 'dev/compose.yaml', '-f', 'dev/compose.runtime.yaml', 'up', '-d', '--wait', '--wait-timeout', '360')
& "$PSScriptRoot/deploy-models.ps1"
if ($InfrastructureOnly) { return }

$processFile = Join-Path $local 'dev-process.json'
if (Test-Path -LiteralPath $processFile) {
    $previous = Get-Content $processFile -Raw | ConvertFrom-Json
    $running = Get-Process -Id $previous.id -ErrorAction SilentlyContinue
    if ($running -and $running.StartTime.ToUniversalTime().Ticks -eq $previous.startedTicks) {
        Write-Host 'PropertyFlow läuft bereits. Für einen Neubau zuerst dev/stop.ps1 -ApplicationOnly ausführen.'
        Initialize-Examples
        return
    }
}
if (Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue) {
    throw 'Port 8081 ist belegt. Den bisherigen PropertyFlow-Start in der IDE beenden; fremde Prozesse werden nicht gestoppt.'
}
if (-not $SkipBuild) {
    & ./mvnw.cmd package -DskipTests '-Dmaven.jar.forceCreation=true'
    if ($LASTEXITCODE -ne 0) { throw 'Der Paketbau ist fehlgeschlagen.' }
}
$jar = Join-Path $repository 'target/propertyflow-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Das Anwendungspaket fehlt. Ohne -SkipBuild starten.' }

# Die bestehende Ersteinrichtung wird nur ergänzt, niemals überschrieben.
$staffFile = Join-Path $local 'staff-bootstrap.properties'
if (-not (Test-Path -LiteralPath $staffFile)) {
    @('propertyflow.staff.bootstrap.name=Testverwaltung', 'propertyflow.staff.bootstrap.email=verwaltung@example.test') |
        Set-Content $staffFile -Encoding utf8
}
$staff = Read-LocalProperties $staffFile
$env:PROPERTYFLOW_STAFF_BOOTSTRAP_NAME = $staff['propertyflow.staff.bootstrap.name']
$env:PROPERTYFLOW_STAFF_BOOTSTRAP_EMAIL = $staff['propertyflow.staff.bootstrap.email']
$env:SPRING_MAIL_HOST = '127.0.0.1'
$env:SPRING_MAIL_PORT = '1025'
$env:PROPERTYFLOW_SMTP_AUTH = 'false'
$env:PROPERTYFLOW_SMTP_STARTTLS = 'false'
& java -jar $jar --spring.profiles.active=propertyflow-backend,staff-bootstrap --spring.main.web-application-type=none `
    --propertyflow.workflow.enabled=false --propertyflow.case-mail.enabled=false --camunda.client.enabled=false
if ($LASTEXITCODE -ne 0) { throw 'Die Kontoeinrichtung ist fehlgeschlagen.' }
$app = Start-Process java -ArgumentList @('-jar', ('"' + $jar + '"'),
    '--spring.profiles.active=propertyflow-backend,propertyflow-dev',
    '--propertyflow.workflow.enabled=true', '--propertyflow.case-mail.enabled=true',
    '--server.address=127.0.0.1', '--server.port=8081') -WorkingDirectory $repository -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $local 'application.log') -RedirectStandardError (Join-Path $local 'application-error.log') -PassThru
@{ id = $app.Id; startedTicks = $app.StartTime.ToUniversalTime().Ticks } |
    ConvertTo-Json | Set-Content $processFile -Encoding utf8
$ready = $false
for ($attempt = 0; $attempt -lt 90; $attempt++) {
    if ($app.HasExited) { throw 'PropertyFlow wurde beendet. Siehe .propertyflow-local/application*.log.' }
    try {
        $health = Invoke-RestMethod 'http://127.0.0.1:8081/actuator/health' -TimeoutSec 3
        if ($health.status -eq 'UP') { $ready = $true; break }
    } catch { }
    Start-Sleep -Seconds 2
}
if (-not $ready) { throw 'PropertyFlow ist noch nicht bereit. Siehe .propertyflow-local/application.log.' }
Write-Host 'PropertyFlow: http://127.0.0.1:8081/mieter/fall | Mitarbeiter: http://127.0.0.1:8081/anmelden'
Write-Host 'Camunda Operate: http://127.0.0.1:8080/operate (demo / demo) | Mailpit: http://127.0.0.1:8025'
Write-Host 'Mitarbeiteradresse: .propertyflow-local/staff-bootstrap.properties; Einmalcode in Mailpit.'
Initialize-Examples
