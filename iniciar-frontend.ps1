param([switch]$Recompilar, [switch]$Desenvolvimento)
$ErrorActionPreference = 'Stop'
if (Test-Path (Join-Path $PSScriptRoot '.config-local.json')) {
    $config = Get-Content (Join-Path $PSScriptRoot '.config-local.json') -Raw | ConvertFrom-Json
    foreach ($name in @('FRONTEND_ORIGIN','FRONTEND_SHARED_KEY','SERVER_PORT')) { [Environment]::SetEnvironmentVariable($name,[string]$config.$name,'Process') }
}
$env:NG_BUILD_MAX_WORKERS = '1'
. "$PSScriptRoot\scripts\cache-build.ps1"
Set-Location (Join-Path $PSScriptRoot 'frontend')
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw 'Node.js nao encontrado.' }
if (-not (Test-Path 'node_modules')) {
    & npm.cmd ci
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao instalar dependencias.' }
}
if ($Desenvolvimento) {
    & npm.cmd start
    if ($LASTEXITCODE -ne 0) { throw 'Frontend encerrou com erro.' }
    exit
}
$fingerprint = Get-BuildFingerprint @('src','package.json','package-lock.json','angular.json','tsconfig.json','tsconfig.app.json')
$stamp = 'dist/.build-fingerprint'
if ($Recompilar -or -not (Test-Path 'dist/clima-frontend/browser/index.html') -or -not (Test-Path $stamp) -or (Get-Content $stamp -Raw).Trim() -ne $fingerprint) {
    Write-Host 'Preparando interface. Nas proximas aberturas esta etapa sera reutilizada.'
    & node '--max-old-space-size=1024' './node_modules/@angular/cli/bin/ng.js' build --configuration production
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar a interface.' }
    Set-Content -LiteralPath $stamp -Value $fingerprint
}
& node '--max-old-space-size=96' "$PSScriptRoot\scripts\servir-frontend.cjs"
if ($LASTEXITCODE -ne 0) { throw 'Frontend encerrou com erro. Confira a mensagem acima.' }
