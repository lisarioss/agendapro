$ErrorActionPreference = 'Stop'
$envFile = Join-Path $PSScriptRoot '.env'
if (Test-Path -LiteralPath $envFile) { Write-Host 'O arquivo .env ja existe; foi preservado.'; exit 0 }
function New-Secret {
    $bytes = New-Object byte[] 48
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    [Convert]::ToBase64String($bytes)
}
$dbSecret = New-Secret
$jwtSecret = New-Secret
"DB_PASSWORD=$dbSecret`nJWT_SECRET=$jwtSecret`n" | Set-Content -LiteralPath $envFile -Encoding ASCII
Write-Host 'Arquivo .env criado com segredos aleatorios. Nao o publique no Git.'
