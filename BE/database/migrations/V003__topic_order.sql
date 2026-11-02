-- Optional additive migration. Run manually after backup in the intended database.
-- Never run setup.sql on an existing database. No existing rows are reordered.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.topics',N'U') IS NULL THROW 51000, 'Select the existing Math11 database.', 1;
IF COL_LENGTH(N'dbo.topics',N'position') IS NULL
    ALTER TABLE dbo.topics ADD position INT NOT NULL CONSTRAINT df_topics_position DEFAULT 0;
IF OBJECT_ID(N'dbo.schema_versions',N'U') IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.schema_versions WHERE version=3)
    INSERT dbo.schema_versions(version) VALUES(3);
COMMIT;
-- Rollback only after backup and stopping catalog writes:
-- ALTER TABLE dbo.topics DROP CONSTRAINT df_topics_position;
-- ALTER TABLE dbo.topics DROP COLUMN position;
-- DELETE dbo.schema_versions WHERE version=3;
