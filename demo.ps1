param([ValidateRange(1024,65535)][int]$Port = 8080)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $jarPath = Join-Path $PSScriptRoot 'agendapro.jar'
    if (-not (Test-Path -LiteralPath $jarPath)) {
        & .\mvnw.cmd -B -ntp package
        if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar.' }
        $jarPath = Join-Path $PSScriptRoot 'target/agendapro-1.0.0.jar'
    }
    Write-Host "Abra http://localhost:$Port . Empresa: studio-demo; e-mail: lisa@example.com; senha: Demo-AgendaPro-2026!"
    Write-Host 'Modo demo: os dados sao temporarios e desaparecem ao encerrar. Ctrl+C para parar.'
    & java -jar $jarPath --spring.profiles.active=demo "--server.port=$Port"
    if ($LASTEXITCODE -ne 0) { throw 'A aplicacao foi encerrada com erro.' }
} finally { Pop-Location }
