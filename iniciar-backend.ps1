$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not (Test-Path '.config-local.json')) { throw 'Execute primeiro .\configurar-windows.ps1' }
$config = Get-Content '.config-local.json' -Raw | ConvertFrom-Json
foreach ($property in $config.PSObject.Properties) { [Environment]::SetEnvironmentVariable($property.Name, [string]$property.Value, 'Process') }
if (Get-Command mvn -ErrorAction SilentlyContinue) { mvn spring-boot:run }
elseif (Test-Path '.\mvnw.cmd') { & '.\mvnw.cmd' spring-boot:run }
else { throw 'Maven nao encontrado. Instale Maven e Java 21, abra um novo PowerShell e tente novamente.' }
