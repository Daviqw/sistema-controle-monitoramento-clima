$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
Write-Host 'CONFIGURACAO LOCAL' -ForegroundColor Cyan
Write-Host 'Crie antes no PostgreSQL um banco chamado clima (pgAdmin ou psql).'
$dbUser = Read-Host 'Usuario PostgreSQL [postgres]'
if ([string]::IsNullOrWhiteSpace($dbUser)) { $dbUser = 'postgres' }
$dbPassword = Read-Host 'Senha do PostgreSQL (entrada visivel)'
$adminEmail = Read-Host 'Email do administrador [admin@clima.local]'
if ([string]::IsNullOrWhiteSpace($adminEmail)) { $adminEmail = 'admin@clima.local' }
$adminPassword = Read-Host 'Senha inicial do administrador (minimo 10 caracteres; entrada visivel)'
if ($adminPassword.Length -lt 10) { throw 'Use uma senha de administrador com pelo menos 10 caracteres.' }
$secretBytes = New-Object byte[] 48
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($secretBytes)
$jwt = [Convert]::ToBase64String($secretBytes)
$config = @{
 DB_URL='jdbc:postgresql://localhost:5432/clima'; DB_USER=$dbUser; DB_PASSWORD=$dbPassword
 ADMIN_INITIAL_EMAIL=$adminEmail; ADMIN_INITIAL_PASSWORD=$adminPassword
 JWT_SECRET=$jwt; FRONTEND_ORIGIN='http://localhost:4200'; FRONTEND_SHARED_KEY='dev-only-frontend-key'
 SPRING_PROFILES_ACTIVE='dev'; SERVER_PORT='8080'
}
$config | ConvertTo-Json | Set-Content -Encoding UTF8 '.config-local.json'
Write-Host 'Configuracao salva em .config-local.json (arquivo com senhas; nao compartilhe).' -ForegroundColor Green
Write-Host 'Agora execute .\iniciar-backend.ps1'

