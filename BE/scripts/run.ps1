param(
  [ValidateSet('verify','package','tomcat')][string]$Action = 'verify',
  [string]$EnvFile = '',
  [string]$TomcatHome = '',
  [ValidateSet('default','dev')][string]$Profile = 'default',
  [switch]$Offline
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'maven.ps1')
$backendRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (-not $EnvFile) {
  $configName = if ($Profile -eq 'dev') { '.env.dev' } else { '.env' }
  $EnvFile = Join-Path $backendRoot $configName
}
$EnvFile = [IO.Path]::GetFullPath($EnvFile)
if ($Profile -eq 'dev' -and -not (Test-Path -LiteralPath $EnvFile -PathType Leaf)) {
  throw 'Dev configuration not found. Copy BE/.env.dev.example to BE/.env.dev and configure DB_USER and DB_PASSWORD.'
}
# Dev builds/tests use their own fixtures; runtime settings are only for Tomcat.
$loadRuntimeConfig = $Profile -eq 'default' -or $Action -eq 'tomcat'
if ($loadRuntimeConfig -and (Test-Path -LiteralPath $EnvFile)) {
  foreach ($line in Get-Content -LiteralPath $EnvFile -Encoding UTF8) {
    if ($line -match '^\s*([A-Z][A-Z0-9_]*)=(.*)$') {
      $settingName = $Matches[1]
      $settingValue = $Matches[2].Trim()
      if ($settingValue.Length -ge 2 -and (($settingValue.StartsWith('"') -and $settingValue.EndsWith('"')) -or ($settingValue.StartsWith("'") -and $settingValue.EndsWith("'")))) { $settingValue = $settingValue.Substring(1,$settingValue.Length-2) }
      [Environment]::SetEnvironmentVariable($settingName,$settingValue,'Process')
    }
  }
  # Let the servlet listener use this same file, including when Tomcat changes cwd.
  $env:MATHIO_ENV_FILE = $EnvFile
  if ($env:STORAGE_DIR -and -not [IO.Path]::IsPathRooted($env:STORAGE_DIR)) {
    $env:STORAGE_DIR = [IO.Path]::GetFullPath((Join-Path (Split-Path -Parent $EnvFile) $env:STORAGE_DIR))
  }
}
if ($Profile -eq 'dev' -and $Action -eq 'tomcat') { $env:APP_ENV = 'development' }
Push-Location $backendRoot
try {
  if ($Action -in @('verify','package')) {
    $mavenCommand = Get-MavenCommand
    $mavenArguments = @('-B', '-Dmaven.repo.local=.m2')
    if ($Offline) { $mavenArguments += '-o' }
    & $mavenCommand @mavenArguments clean $Action
  }
  else {
    if (-not $TomcatHome) { $TomcatHome = $env:CATALINA_HOME }
    if (-not $TomcatHome) { throw 'Pass -TomcatHome with the path of a Tomcat 10.1 ZIP installation, or set CATALINA_HOME.' }
    $tomcatRoot = [IO.Path]::GetFullPath($TomcatHome)
    $catalinaScript = Join-Path $tomcatRoot 'bin\catalina.bat'
    if (-not (Test-Path -LiteralPath $catalinaScript)) { throw 'Tomcat installation not found.' }
    if (-not (Test-Path -LiteralPath 'target\Math11.war')) { throw 'Build the WAR before running Tomcat.' }
    if ($Profile -eq 'dev') {
      foreach ($requiredSetting in @('DB_URL','DB_PASSWORD')) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($requiredSetting,'Process'))) {
          throw "Configure $requiredSetting in $EnvFile before starting Tomcat."
        }
      }
      if (-not $env:DB_USER -and -not $env:DB_USERNAME) { throw "Configure DB_USER in $EnvFile before starting Tomcat." }
    }
    if (-not $env:STORAGE_DIR) { $env:STORAGE_DIR = Join-Path $backendRoot 'private-storage' }
    Copy-Item -LiteralPath 'target\Math11.war' -Destination (Join-Path $tomcatRoot 'webapps\Math11.war') -Force
    $env:CATALINA_HOME = $tomcatRoot
    $env:CATALINA_BASE = $tomcatRoot
    & $catalinaScript run
  }
  if ($LASTEXITCODE -ne 0) { throw "Command failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }
