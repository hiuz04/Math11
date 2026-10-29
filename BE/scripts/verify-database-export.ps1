param(
    [string]$SqlPath = (Join-Path $PSScriptRoot '..\database\backups\math11_tutor_db_full_20261029.sql'),
    [string]$ServerInstance = 'localhost,1433'
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'export-database.ps1') -FunctionsOnly -ServerInstance $ServerInstance
$fullPath = [IO.Path]::GetFullPath($SqlPath)
$manifest = Get-Content -LiteralPath ($fullPath + '.manifest.json') -Raw | ConvertFrom-Json
$sql = [IO.File]::ReadAllText($fullPath, [Text.Encoding]::UTF8)
if ((Get-FileHash -LiteralPath $fullPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $manifest.ScriptSHA256) { throw 'Export SQL checksum mismatch.' }
$testDatabase = 'math11_export_verify_' + [Guid]::NewGuid().ToString('N')
$originalDeclaration = 'DECLARE @TargetDatabase sysname = ' + (Quote-Text $manifest.Database) + ';'
$testDeclaration = 'DECLARE @TargetDatabase sysname = ' + (Quote-Text $testDatabase) + ';'
if (-not $sql.Contains($originalDeclaration)) { throw 'Target declaration was not found.' }
$testSql = $sql.Replace($originalDeclaration, $testDeclaration)
$connection = New-Object System.Data.SqlClient.SqlConnection "Server=$ServerInstance;Database=master;Integrated Security=True;Encrypt=True;TrustServerCertificate=True;Connection Timeout=10"
$sourceConnection = New-Object System.Data.SqlClient.SqlConnection "Server=$ServerInstance;Database=$($manifest.Database);Integrated Security=True;Encrypt=True;TrustServerCertificate=True;Connection Timeout=10"
$restored = $false
$report = [ordered]@{ TestDatabase=$testDatabase; VerifiedAt=[DateTimeOffset]::Now.ToString('O'); Tables=@(); TotalRows=0; SchemaMatches=$false; IntegrityCheck=$false; ExistingDatabaseGuard=$false; TemporaryDatabaseRemoved=$false }

function Get-CatalogHash($Connection, $Parts) {
    $queries = @(
        "SELECT s.name AS schema_name,t.name AS table_name,c.column_id,c.name,ty.name AS type_name,c.max_length,c.precision,c.scale,c.collation_name,c.is_nullable,c.is_identity,c.is_computed,c.is_rowguidcol,cc.definition AS computed_definition,cc.is_persisted,CONVERT(varchar(100),ic.seed_value) AS identity_seed,CONVERT(varchar(100),ic.increment_value) AS identity_increment,CONVERT(varchar(100),ic.last_value) AS identity_last FROM sys.tables t JOIN sys.schemas s ON s.schema_id=t.schema_id JOIN sys.columns c ON c.object_id=t.object_id JOIN sys.types ty ON ty.user_type_id=c.user_type_id LEFT JOIN sys.computed_columns cc ON cc.object_id=c.object_id AND cc.column_id=c.column_id LEFT JOIN sys.identity_columns ic ON ic.object_id=c.object_id AND ic.column_id=c.column_id WHERE t.is_ms_shipped=0 ORDER BY s.name,t.name,c.column_id",
        "SELECT s.name AS schema_name,t.name AS table_name,d.name,c.name AS column_name,d.definition FROM sys.default_constraints d JOIN sys.tables t ON t.object_id=d.parent_object_id JOIN sys.schemas s ON s.schema_id=t.schema_id JOIN sys.columns c ON c.object_id=t.object_id AND c.column_id=d.parent_column_id ORDER BY s.name,t.name,d.name",
        "SELECT s.name AS schema_name,t.name AS table_name,c.name,c.definition,c.is_disabled,c.is_not_trusted,c.is_not_for_replication FROM sys.check_constraints c JOIN sys.tables t ON t.object_id=c.parent_object_id JOIN sys.schemas s ON s.schema_id=t.schema_id ORDER BY s.name,t.name,c.name",
        "SELECT s.name AS schema_name,t.name AS table_name,i.name,i.type_desc,i.is_unique,i.is_primary_key,i.is_unique_constraint,i.is_disabled,i.has_filter,i.filter_definition,i.fill_factor,i.is_padded,i.ignore_dup_key,i.allow_row_locks,i.allow_page_locks,ic.index_column_id,c.name AS column_name,ic.key_ordinal,ic.is_descending_key,ic.is_included_column FROM sys.indexes i JOIN sys.tables t ON t.object_id=i.object_id JOIN sys.schemas s ON s.schema_id=t.schema_id JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE t.is_ms_shipped=0 ORDER BY s.name,t.name,i.name,ic.index_column_id",
        "SELECT s.name AS schema_name,t.name AS table_name,f.name,cs.name AS column_name,rs.name AS referenced_schema,rt.name AS referenced_table,rc.name AS referenced_column,fc.constraint_column_id,f.delete_referential_action_desc,f.update_referential_action_desc,f.is_disabled,f.is_not_trusted,f.is_not_for_replication FROM sys.foreign_keys f JOIN sys.tables t ON t.object_id=f.parent_object_id JOIN sys.schemas s ON s.schema_id=t.schema_id JOIN sys.foreign_key_columns fc ON fc.constraint_object_id=f.object_id JOIN sys.columns cs ON cs.object_id=t.object_id AND cs.column_id=fc.parent_column_id JOIN sys.tables rt ON rt.object_id=f.referenced_object_id JOIN sys.schemas rs ON rs.schema_id=rt.schema_id JOIN sys.columns rc ON rc.object_id=rt.object_id AND rc.column_id=fc.referenced_column_id ORDER BY s.name,t.name,f.name,fc.constraint_column_id",
        "SELECT e.class_desc,COALESCE(s.name,'') AS schema_name,COALESCE(t.name,'') AS table_name,COALESCE(c.name,'') AS column_name,e.name,CONVERT(nvarchar(max),e.value) AS value FROM sys.extended_properties e LEFT JOIN sys.tables t ON e.major_id=t.object_id LEFT JOIN sys.schemas s ON t.schema_id=s.schema_id LEFT JOIN sys.columns c ON c.object_id=e.major_id AND c.column_id=e.minor_id ORDER BY e.class_desc,s.name,t.name,c.name,e.name"
    )
    $builder = New-Object Text.StringBuilder
    foreach ($query in $queries) {
        $command=$Connection.CreateCommand()
        $command.CommandText="SELECT CONVERT(nvarchar(max),($query FOR XML PATH('row'),ROOT('metadata'),TYPE))"
        $part=[string]$command.ExecuteScalar()
        [void]$builder.Append($part)
        [void]$Parts.Add($part)
        $command.Dispose()
    }
    $hasher=[Security.Cryptography.SHA256]::Create()
    try { ([BitConverter]::ToString($hasher.ComputeHash([Text.Encoding]::UTF8.GetBytes($builder.ToString())))).Replace('-','').ToLowerInvariant() } finally { $hasher.Dispose() }
}

try {
    $connection.Open()
    $command=$connection.CreateCommand()
    $command.CommandTimeout=180
    $command.CommandText=$testSql
    [void]$command.ExecuteNonQuery()
    $restored=$true
    $connection.ChangeDatabase($testDatabase)
    foreach ($table in $manifest.Tables) {
        $digest=Get-TableDigest $connection $null $table.QualifiedName $table.OrderBy
        if ($digest.Count -ne $table.RowCount -or $digest.SHA256 -ne $table.SHA256) { throw "Restored data mismatch: $($table.QualifiedName)" }
        $report.TotalRows += $digest.Count
        $report.Tables += [ordered]@{ Table=$table.QualifiedName; RowCount=$digest.Count; SHA256=$digest.SHA256; Matches=$true }
    }
    $sourceConnection.Open()
    $sourceParts=New-Object 'Collections.Generic.List[string]'
    $restoredParts=New-Object 'Collections.Generic.List[string]'
    $report.SourceCatalogSHA256=Get-CatalogHash $sourceConnection $sourceParts
    $report.RestoredCatalogSHA256=Get-CatalogHash $connection $restoredParts
    if ($report.SourceCatalogSHA256 -ne $report.RestoredCatalogSHA256) {
        $report.CatalogDifferences=@()
        for ($i=0; $i -lt $sourceParts.Count; $i++) {
            if ($sourceParts[$i] -eq $restoredParts[$i]) { continue }
            $sourceXml=[xml]$sourceParts[$i]
            $restoredXml=[xml]$restoredParts[$i]
            $sourceRows=@($sourceXml.metadata.row | ForEach-Object { $_.OuterXml })
            $restoredRows=@($restoredXml.metadata.row | ForEach-Object { $_.OuterXml })
            $report.CatalogDifferences+=@{Category=$i;Differences=@(Compare-Object $sourceRows $restoredRows | Select-Object InputObject,SideIndicator)}
        }
        throw 'Restored database schema/identity metadata differs from source.'
    }
    $report.SchemaMatches=$true
    $command=$connection.CreateCommand()
    $command.CommandTimeout=120
    $command.CommandText='DBCC CHECKDB (' + (Quote-Text $testDatabase) + ') WITH NO_INFOMSGS, ALL_ERRORMSGS; DBCC CHECKCONSTRAINTS WITH ALL_CONSTRAINTS;'
    $reader=$command.ExecuteReader()
    do { if ($reader.HasRows) { throw 'Integrity checks returned errors.' } } while ($reader.NextResult())
    $reader.Dispose()
    $report.IntegrityCheck=$true
    $connection.ChangeDatabase('master')
    $command=$connection.CreateCommand()
    $command.CommandTimeout=180
    $command.CommandText=$testSql
    try { [void]$command.ExecuteNonQuery(); throw 'Existing database guard failed.' }
    catch [System.Data.SqlClient.SqlException] { if ($_.Exception.Number -ne 51001) { throw }; $report.ExistingDatabaseGuard=$true }
    Write-Output "Verified $($manifest.Tables.Count) tables and $($report.TotalRows) rows; data SHA-256 and catalog metadata match."
} finally {
    $sourceConnection.Dispose()
    if ($connection.State -eq 'Open') {
        $connection.ChangeDatabase('master')
        # Drop only this uniquely named, temporary verification database.
        if ($testDatabase -notmatch '^math11_export_verify_[0-9a-f]{32}$') { throw 'Unsafe verification database name.' }
        $cleanup=$connection.CreateCommand()
        $cleanup.CommandText='IF DB_ID(' + (Quote-Text $testDatabase) + ') IS NOT NULL BEGIN ALTER DATABASE ' + (Quote-Name $testDatabase) + ' SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE ' + (Quote-Name $testDatabase) + '; END;'
        [void]$cleanup.ExecuteNonQuery()
        $report.TemporaryDatabaseRemoved=$true
    }
    $connection.Dispose()
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath ($fullPath + '.verification.json') -Encoding UTF8
}
