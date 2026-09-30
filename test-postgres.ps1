$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    if (-not (Test-Path -LiteralPath '.env')) { & .\setup.ps1 }
    foreach ($line in Get-Content -LiteralPath '.env') {
        if ($line -match '^(DB_PASSWORD|JWT_SECRET)=(.+)$') { [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process') }
    }
    & docker compose --profile test up -d --wait db-test
    if ($LASTEXITCODE -ne 0) { throw 'Nao foi possivel iniciar o banco de testes.' }
    & .\mvnw.cmd -B -ntp test '-Dpg.tests=true' '-Dpg.url=jdbc:postgresql://localhost:55433/agendapro_test' "-Dpg.password=$env:DB_PASSWORD"
    if ($LASTEXITCODE -ne 0) { throw 'Testes falharam.' }
} finally {
    & docker compose --profile test stop db-test
    Pop-Location
}
