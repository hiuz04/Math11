function Get-MavenCommand {
  $command = Get-Command mvn.cmd -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($command) { return $command.Source }

  foreach ($mavenRoot in @($env:MAVEN_HOME, $env:M2_HOME)) {
    if ($mavenRoot) {
      $candidate = Join-Path $mavenRoot 'bin\mvn.cmd'
      if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
    }
  }

  # NetBeans bundles Maven even when it has not been added to PATH.
  if ($env:ProgramFiles) {
    $candidate = Join-Path $env:ProgramFiles 'NetBeans-17\netbeans\java\maven\bin\mvn.cmd'
    if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
  }
  throw 'Maven was not found. Add its bin directory to PATH or set MAVEN_HOME to the Maven installation directory.'
}
