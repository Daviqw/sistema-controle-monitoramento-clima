$ErrorActionPreference = 'Stop'
if (Test-Path (Join-Path $PSScriptRoot '.config-local.json')) {
 $config = Get-Content (Join-Path $PSScriptRoot '.config-local.json') -Raw | ConvertFrom-Json
 foreach ($name in @('FRONTEND_ORIGIN','FRONTEND_SHARED_KEY')) { [Environment]::SetEnvironmentVariable($name,[string]$config.$name,'Process') }
}
# Limita os processos paralelos do Angular em computadores com pouca memoria.
$env:NG_BUILD_MAX_WORKERS = '1'
Set-Location (Join-Path $PSScriptRoot 'frontend')
if (-not (Get-Command npm -ErrorAction SilentlyContinue)) { throw 'Node.js/npm nao encontrado.' }
if (-not (Test-Path 'node_modules')) { npm ci; if ($LASTEXITCODE -ne 0) { throw 'npm ci falhou.' } }
npm start
if ($LASTEXITCODE -ne 0) { throw 'Frontend encerrou com erro. Copie a mensagem do terminal.' }
