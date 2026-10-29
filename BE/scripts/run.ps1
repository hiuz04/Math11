param(
  [ValidateSet('verify','package','tomcat')][string]$Action = 'verify',
  [string]$EnvFile = '',
  [string]$TomcatHome = ''
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'maven.ps1')
$backendRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (-not $EnvFile) { $EnvFile = Join-Path $backendRoot '.env' }
if (Test-Path -LiteralPath $EnvFile) {
  foreach ($line in Get-Content -LiteralPath $EnvFile -Encoding UTF8) {
    if ($line -match '^\s*([A-Z][A-Z0-9_]*)=(.*)$') {
      $settingName = $Matches[1]
      $settingValue = $Matches[2].Trim()
      if (($settingValue.StartsWith('"') -and $settingValue.EndsWith('"')) -or ($settingValue.StartsWith("'") -and $settingValue.EndsWith("'"))) { $settingValue = $settingValue.Substring(1,$settingValue.Length-2) }
      [Environment]::SetEnvironmentVariable($settingName,$settingValue,'Process')
    }
  }
}
Push-Location $backendRoot
try {
  if ($Action -in @('verify','package')) {
    $mavenCommand = Get-MavenCommand
    & $mavenCommand -B '-Dmaven.repo.local=.m2' clean $Action
  }
  else {
    if (-not $TomcatHome) { throw 'Pass -TomcatHome with the path of a Tomcat 10.1 ZIP installation.' }
    $tomcatRoot = [IO.Path]::GetFullPath($TomcatHome)
    $catalinaScript = Join-Path $tomcatRoot 'bin\catalina.bat'
    if (-not (Test-Path -LiteralPath $catalinaScript)) { throw 'Tomcat installation not found.' }
    if (-not (Test-Path -LiteralPath 'target\Math11.war')) { throw 'Build the WAR before running Tomcat.' }
    if (-not $env:STORAGE_DIR) { $env:STORAGE_DIR = Join-Path $backendRoot 'private-storage' }
    Copy-Item -LiteralPath 'target\Math11.war' -Destination (Join-Path $tomcatRoot 'webapps\Math11.war') -Force
    $env:CATALINA_HOME = $tomcatRoot
    $env:CATALINA_BASE = $tomcatRoot
    & $catalinaScript run
  }
  if ($LASTEXITCODE -ne 0) { throw "Command failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }
