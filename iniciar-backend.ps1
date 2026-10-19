param([switch]$Recompilar)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not (Test-Path '.config-local.json')) { throw 'Execute primeiro .\configurar-windows.ps1' }
$config = Get-Content '.config-local.json' -Raw | ConvertFrom-Json
foreach ($property in $config.PSObject.Properties) { [Environment]::SetEnvironmentVariable($property.Name, [string]$property.Value, 'Process') }
. "$PSScriptRoot\scripts\cache-build.ps1"
$fingerprint = Get-BuildFingerprint @('pom.xml', 'src/main')
$jar = 'target/sistema-controle-clima-1.0.0-SNAPSHOT.jar'
$stamp = 'target/.build-fingerprint'
if ($Recompilar -or -not (Test-Path $jar) -or -not (Test-Path $stamp) -or (Get-Content $stamp -Raw).Trim() -ne $fingerprint) {
    Write-Host 'Preparando backend. Nas proximas aberturas esta etapa sera reutilizada.'
    $previousOpts = $env:MAVEN_OPTS
    try {
        $env:MAVEN_OPTS = "$previousOpts -Xms64m -Xmx384m -XX:+UseSerialGC"
        if (Get-Command mvn.cmd -ErrorAction SilentlyContinue) { & mvn.cmd '-Dmaven.test.skip=true' clean package }
        elseif (Test-Path '.\mvnw.cmd') { & '.\mvnw.cmd' '-Dmaven.test.skip=true' clean package }
        else { throw 'Maven nao encontrado. Instale Maven e Java 21 ou superior.' }
        if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar o backend.' }
        Set-Content -LiteralPath $stamp -Value $fingerprint
    } finally { $env:MAVEN_OPTS = $previousOpts }
}
& java '-Xms64m' '-Xmx384m' '-XX:+UseSerialGC' '-XX:ReservedCodeCacheSize=64m' '-XX:ActiveProcessorCount=2' -jar $jar
if ($LASTEXITCODE -ne 0) { throw 'Backend encerrou com erro. Confira a mensagem acima.' }

