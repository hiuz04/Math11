param(
    [string]$ServerInstance = 'localhost,1433',
    [string]$DatabaseName = 'math11_tutor_db',
    [string]$OutputPath = (Join-Path $PSScriptRoot '..\database\backups\math11_tutor_db_full_20261029.sql'),
    [switch]$FunctionsOnly
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Quote-Name([string]$Name) { '[' + $Name.Replace(']', ']]') + ']' }
function Quote-Text([string]$Value) { "N'" + $Value.Replace("'", "''") + "'" }
function Get-TableDigest($Connection, $Transaction, [string]$QualifiedName, [string]$OrderBy) {
    $command = $Connection.CreateCommand()
    $command.Transaction = $Transaction
    $command.CommandTimeout = 120
    $command.CommandText = "SELECT * FROM $QualifiedName ORDER BY $OrderBy"
    $reader = $command.ExecuteReader()
    $hash = [Security.Cryptography.SHA256]::Create()
    $stream = New-Object Security.Cryptography.CryptoStream ([IO.Stream]::Null), $hash, ([Security.Cryptography.CryptoStreamMode]::Write)
    $writer = New-Object IO.BinaryWriter $stream, ([Text.Encoding]::UTF8)
    [long]$count = 0
    try {
        while ($reader.Read()) {
            $count++
            for ($i = 0; $i -lt $reader.FieldCount; $i++) {
                $value = $reader.GetValue($i)
                if ($value -is [DBNull]) { $writer.Write([byte]0); continue }
                $writer.Write([byte]1)
                if ($value -is [byte[]]) { $valueText = [Convert]::ToBase64String($value) }
                elseif ($value -is [DateTime]) { $valueText = $value.ToString('O', [Globalization.CultureInfo]::InvariantCulture) }
                elseif ($value -is [double] -or $value -is [single]) { $valueText = $value.ToString('R', [Globalization.CultureInfo]::InvariantCulture) }
                elseif ($value -is [IFormattable]) { $valueText = $value.ToString($null, [Globalization.CultureInfo]::InvariantCulture) }
                else { $valueText = [string]$value }
                $writer.Write([string]$valueText)
            }
        }
        $writer.Flush()
        $stream.FlushFinalBlock()
        return [pscustomobject]@{ Count = $count; SHA256 = ([BitConverter]::ToString($hash.Hash)).Replace('-', '').ToLowerInvariant() }
    } finally { $reader.Dispose(); $command.Dispose(); $writer.Dispose(); $stream.Dispose(); $hash.Dispose() }
}

if ($FunctionsOnly) { return }
# Load the installed .NET Framework SMO from the GAC without initializing the SQLPS provider.
foreach ($assembly in @('ConnectionInfo', 'Management.Sdk.Sfc', 'Smo', 'SmoExtended', 'SqlEnum')) {
    [void][Reflection.Assembly]::Load("Microsoft.SqlServer.$assembly, Version=16.0.0.0, Culture=neutral, PublicKeyToken=89845dcd8080cc91")
}

$server = New-Object Microsoft.SqlServer.Management.Smo.Server $ServerInstance
$server.ConnectionContext.ConnectionString = "Server=$ServerInstance;Database=master;Integrated Security=True;Encrypt=True;TrustServerCertificate=True;Connection Timeout=10"
$server.ConnectionContext.StatementTimeout = 120
$lockConnection = New-Object System.Data.SqlClient.SqlConnection "Server=$ServerInstance;Database=$DatabaseName;Integrated Security=True;Encrypt=True;TrustServerCertificate=True;Connection Timeout=10"
$lockTransaction = $null
$writer = $null
$fullPath = [IO.Path]::GetFullPath($OutputPath)
$partialPath = $fullPath + '.partial'
if (Test-Path -LiteralPath $fullPath) { throw "Output already exists: $fullPath" }
[void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($fullPath))
$manifest = [ordered]@{ Database = $DatabaseName; SourceServer = $ServerInstance; ExportedAt = [DateTimeOffset]::Now.ToString('O'); Tables = @() }
try {
    $server.ConnectionContext.Connect()
    $db = $server.Databases[$DatabaseName]
    if ($null -eq $db) { throw "Database not found: $DatabaseName" }
    $tables = @($db.Tables | Where-Object { -not $_.IsSystemObject } | Sort-Object Schema,Name)
    # This exporter deliberately refuses unsupported object types instead of silently omitting them.
    $unsupported = $db.ExecuteWithResults("SELECT name FROM sys.objects WHERE is_ms_shipped=0 AND type IN ('V','P','PC','FN','IF','TF','FS','FT','SO','TT','TR','TA') UNION ALL SELECT name FROM sys.types WHERE is_user_defined=1").Tables[0]
    if ($unsupported.Rows.Count -gt 0) { throw 'Additional database object types require extending this exporter.' }
    if (@($tables | Where-Object { $_.Schema -ne 'dbo' -or $_.Triggers.Count -gt 0 }).Count -gt 0) { throw 'Custom schemas or triggers require extending this exporter.' }
    $manifest.ServerVersion = $server.VersionString
    $manifest.Collation = $db.Collation
    $lockConnection.Open()
    $lockTransaction = $lockConnection.BeginTransaction([Data.IsolationLevel]::Serializable)
    foreach ($table in $tables) {
        $qualifiedName = (Quote-Name $table.Schema) + '.' + (Quote-Name $table.Name)
        $command = $lockConnection.CreateCommand()
        $command.Transaction = $lockTransaction
        $command.CommandTimeout = 30
        $command.CommandText = "SELECT COUNT_BIG(*) FROM $qualifiedName WITH (TABLOCK,HOLDLOCK)"
        [void]$command.ExecuteScalar()
        $command.Dispose()
    }
    $writer = New-Object IO.StreamWriter $partialPath, $false, (New-Object Text.UTF8Encoding $true)
    $writer.WriteLine(@"
-- Full database export from the live local SQL Server. UTF-8 with BOM.
-- Export time: $($manifest.ExportedAt)
-- Source: SQL Server $($server.VersionString); $($tables.Count) tables.
-- Run the ENTIRE file in SSMS against SQL Server 2022 or newer.
-- Change ONLY @TargetDatabase below to restore under another database name.
-- The target database MUST NOT already exist. Existing databases are never overwritten.
-- SQL logins and files outside the database are not included.
-- No SQLCMD mode is required. There are intentionally no GO batch separators.
USE [master];
SET NOCOUNT ON;
SET XACT_ABORT ON;
DECLARE @TargetDatabase sysname = $(Quote-Text $DatabaseName);
IF @TargetDatabase IS NULL OR LEN(@TargetDatabase) = 0
    THROW 51000, 'Target database name must not be empty.', 1;
IF DB_ID(@TargetDatabase) IS NOT NULL
    THROW 51001, 'Target database already exists. Choose a new @TargetDatabase name.', 1;
DECLARE @CreateDatabase nvarchar(max) = N'CREATE DATABASE ' + QUOTENAME(@TargetDatabase) + N' COLLATE $($db.Collation)';
EXEC sys.sp_executesql @CreateDatabase;
DECLARE @ExecuteInTarget nvarchar(776) = QUOTENAME(@TargetDatabase) + N'.sys.sp_executesql';
BEGIN TRY
    BEGIN TRANSACTION;
"@)
    function Write-TargetBatch([string]$Sql) {
        if (-not [string]::IsNullOrWhiteSpace($Sql)) {
            $writer.WriteLine('    EXEC @ExecuteInTarget ' + (Quote-Text $Sql) + ';')
        }
    }
    $schemaOptions = New-Object Microsoft.SqlServer.Management.Smo.ScriptingOptions
    $schemaOptions.ScriptSchema = $true
    $schemaOptions.ScriptData = $false
    $schemaOptions.DriPrimaryKey = $true
    $schemaOptions.DriUniqueKeys = $true
    $schemaOptions.DriDefaults = $true
    $schemaOptions.DriChecks = $true
    $schemaOptions.DriIncludeSystemNames = $true
    $schemaOptions.Indexes = $true
    $schemaOptions.Triggers = $false
    $schemaOptions.ExtendedProperties = $true
    $schemaOptions.IncludeDatabaseContext = $false
    $schemaOptions.NoFileGroup = $true
    $schemaOptions.ScriptBatchTerminator = $false
    $schemaOptions.TargetServerVersion = [Microsoft.SqlServer.Management.Smo.SqlServerVersion]::Version160
    $writer.WriteLine('-- Tables, primary/unique keys, defaults, checks and indexes.')
    foreach ($table in $tables) {
        foreach ($batch in $table.Script($schemaOptions)) { Write-TargetBatch $batch }
    }
    $scripter = New-Object Microsoft.SqlServer.Management.Smo.Scripter $server
    $scripter.Options.ScriptSchema = $false
    $scripter.Options.ScriptData = $true
    $scripter.Options.IncludeDatabaseContext = $false
    $scripter.Options.ScriptBatchTerminator = $false
    $scripter.Options.TargetServerVersion = [Microsoft.SqlServer.Management.Smo.SqlServerVersion]::Version160
    foreach ($table in $tables) {
        $qualifiedName = (Quote-Name $table.Schema) + '.' + (Quote-Name $table.Name)
        $primaryKey = @($table.Indexes | Where-Object { $_.IndexKeyType.ToString() -eq 'DriPrimaryKey' })
        if ($primaryKey.Count -ne 1) { throw "No deterministic primary key: $qualifiedName" }
        $orderBy = (@($primaryKey[0].IndexedColumns | ForEach-Object { Quote-Name $_.Name }) -join ',')
        $digest = Get-TableDigest $lockConnection $lockTransaction $qualifiedName $orderBy
        $writer.WriteLine("-- Data: $qualifiedName ($($digest.Count) rows)")
        # IDENTITY_INSERT is scoped to this dynamic execution; keep ON, INSERTs and OFF together.
        $dataBatch = New-Object Text.StringBuilder
        foreach ($batch in $scripter.EnumScript([Microsoft.SqlServer.Management.Sdk.Sfc.Urn[]]@($table.Urn))) { [void]$dataBatch.AppendLine($batch) }
        Write-TargetBatch $dataBatch.ToString()
        $identityColumn = @($table.Columns | Where-Object { $_.Identity })
        $identityLastValue = $null
        if ($identityColumn.Count -gt 0) {
            $command = $lockConnection.CreateCommand()
            $command.Transaction = $lockTransaction
            $command.CommandText = 'SELECT CONVERT(varchar(100),last_value) FROM sys.identity_columns WHERE object_id=OBJECT_ID(@name)'
            [void]$command.Parameters.AddWithValue('@name', $qualifiedName)
            $last = $command.ExecuteScalar()
            $command.Dispose()
            if ($null -ne $last -and $last -isnot [DBNull]) {
                $identityLastValue = [string]$last
                if ($identityLastValue -notmatch '^-?\d+$') { throw 'Unexpected identity value.' }
                Write-TargetBatch ("DBCC CHECKIDENT (" + (Quote-Text $qualifiedName) + ", RESEED, $identityLastValue) WITH NO_INFOMSGS;")
            }
        }
        Write-TargetBatch ("IF (SELECT COUNT_BIG(*) FROM $qualifiedName) <> $($digest.Count) THROW 51002, 'Row count mismatch for $qualifiedName', 1;")
        $manifest.Tables += [ordered]@{ Schema=$table.Schema; Name=$table.Name; QualifiedName=$qualifiedName; OrderBy=$orderBy; RowCount=$digest.Count; SHA256=$digest.SHA256; IdentityLastValue=$identityLastValue }
        Write-Output "$qualifiedName : $($digest.Count) rows"
    }
    $writer.WriteLine('-- Foreign keys are added after all data has been inserted.')
    $fkOptions = New-Object Microsoft.SqlServer.Management.Smo.ScriptingOptions
    $fkOptions.DriForeignKeys = $true
    $fkOptions.DriIncludeSystemNames = $true
    $fkOptions.SchemaQualify = $true
    foreach ($table in $tables) {
        foreach ($foreignKey in $table.ForeignKeys) {
            foreach ($batch in $foreignKey.Script($fkOptions)) { Write-TargetBatch $batch }
        }
    }
    $writer.WriteLine(@"
    COMMIT TRANSACTION;
    PRINT N'Database restored successfully: ' + QUOTENAME(@TargetDatabase);
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    -- A failed restore leaves the newly created database for inspection.
    -- Choose another name or remove that failed target before retrying.
    THROW;
END CATCH;
"@)
    $writer.Dispose()
    $writer = $null
    $lockTransaction.Commit()
    $lockTransaction.Dispose()
    $lockTransaction = $null
    Move-Item -LiteralPath $partialPath -Destination $fullPath
    $manifest.ScriptSHA256 = (Get-FileHash -LiteralPath $fullPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath ($fullPath + '.manifest.json') -Encoding UTF8
    Write-Output "Export complete: $fullPath"
} finally {
    if ($null -ne $writer) { $writer.Dispose() }
    if ($null -ne $lockTransaction) { try { $lockTransaction.Rollback() } catch { Write-Warning $_.Exception.Message }; $lockTransaction.Dispose() }
    $lockConnection.Dispose()
    $server.ConnectionContext.Disconnect()
}
