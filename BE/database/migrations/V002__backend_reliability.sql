-- V002: run manually in SSMS, after setup.sql. No data is deleted.
-- Select the intended math11_tutor_db or a dedicated *_test database first.
IF DB_NAME() <> N'math11_tutor_db' AND RIGHT(DB_NAME(),5) <> N'_test'
    THROW 51000, 'Select the intended Math11 database; edit this guard for a custom name.', 1;
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRY
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.schema_versions',N'U') IS NULL
    CREATE TABLE dbo.schema_versions(version INT NOT NULL PRIMARY KEY, applied_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME());
IF EXISTS(SELECT 1 FROM dbo.schema_versions WHERE version=2)
BEGIN
    COMMIT; PRINT N'V002 already applied.'; RETURN;
END;
IF OBJECT_ID(N'dbo.questions',N'U') IS NULL THROW 51001, 'Run setup.sql first.', 1;
IF COL_LENGTH(N'dbo.questions',N'course_id') IS NULL
    EXEC(N'ALTER TABLE dbo.questions ADD course_id BIGINT NULL CONSTRAINT fk_questions_course REFERENCES dbo.courses(id);');
IF COL_LENGTH(N'dbo.questions',N'published') IS NULL
    EXEC(N'ALTER TABLE dbo.questions ADD published BIT NOT NULL CONSTRAINT df_questions_published DEFAULT 0;');
IF COL_LENGTH(N'dbo.exams',N'course_id') IS NULL
    EXEC(N'ALTER TABLE dbo.exams ADD course_id BIGINT NULL CONSTRAINT fk_exams_course REFERENCES dbo.courses(id);');
IF COL_LENGTH(N'dbo.exams',N'results_published') IS NULL
    EXEC(N'ALTER TABLE dbo.exams ADD results_published BIT NOT NULL CONSTRAINT df_exam_results_published DEFAULT 1;');
IF COL_LENGTH(N'dbo.essay_submissions',N'course_id') IS NULL
    EXEC(N'ALTER TABLE dbo.essay_submissions ADD course_id BIGINT NULL CONSTRAINT fk_essays_course REFERENCES dbo.courses(id);');
IF COL_LENGTH(N'dbo.essay_submissions',N'reference_json') IS NULL
    EXEC(N'ALTER TABLE dbo.essay_submissions ADD reference_json NVARCHAR(MAX) NULL CONSTRAINT ck_essay_reference_json CHECK(reference_json IS NULL OR ISJSON(reference_json)=1);');
IF COL_LENGTH(N'dbo.idempotency_keys',N'status') IS NULL
    EXEC(N'ALTER TABLE dbo.idempotency_keys ADD status VARCHAR(20) NOT NULL CONSTRAINT df_idempotency_status DEFAULT ''COMPLETED'' CONSTRAINT ck_idempotency_status CHECK(status IN(''PROCESSING'',''COMPLETED'',''FAILED'')), lease_token VARCHAR(36) NULL, lease_until DATETIME2 NULL;');
ALTER TABLE dbo.tutor_step_attempts ALTER COLUMN correct BIT NULL;
IF COL_LENGTH(N'dbo.tutor_step_attempts',N'verification') IS NULL
    EXEC(N'ALTER TABLE dbo.tutor_step_attempts ADD verification VARCHAR(20) NOT NULL CONSTRAINT df_step_verification DEFAULT ''VERIFIED'' CONSTRAINT ck_step_verification CHECK(verification IN(''VERIFIED'',''EQUIVALENT'',''NOT_EQUIVALENT'',''UNKNOWN''));');
-- Replace only the old status CHECK, preserving all attempts and unrelated constraints.
DECLARE @DropChecks NVARCHAR(MAX)=N'';
SELECT @DropChecks=@DropChecks+N'ALTER TABLE dbo.exam_attempts DROP CONSTRAINT '+QUOTENAME(name)+N';'
FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.exam_attempts') AND definition LIKE N'%status%';
EXEC sys.sp_executesql @DropChecks;
ALTER TABLE dbo.exam_attempts WITH CHECK ADD CONSTRAINT ck_attempt_status_v2
    CHECK(status IN('IN_PROGRESS','SUBMITTED','PENDING_GRADING','GRADED','PUBLISHED'));

CREATE TABLE dbo.essay_ai_jobs (
    submission_id BIGINT NOT NULL PRIMARY KEY REFERENCES dbo.essay_submissions(id),
    kind VARCHAR(20) NOT NULL CHECK(kind IN('EVALUATE','TUTOR','RETRY')),
    token VARCHAR(36) NOT NULL, status VARCHAR(20) NOT NULL CHECK(status IN('PROCESSING','COMPLETED','FAILED')),
    revision BIGINT NOT NULL, fingerprint VARCHAR(64) NOT NULL,
    content_json NVARCHAR(MAX) NOT NULL CHECK(ISJSON(content_json)=1),
    lease_until DATETIME2 NOT NULL, updated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
);
CREATE TABLE dbo.essay_ai_job_attempts (
    id BIGINT IDENTITY PRIMARY KEY, submission_id BIGINT NOT NULL REFERENCES dbo.essay_submissions(id),
    kind VARCHAR(20) NOT NULL CHECK(kind IN('EVALUATE','TUTOR','RETRY')),
    token VARCHAR(36) NOT NULL UNIQUE, revision BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK(status IN('PROCESSING','COMPLETED','FAILED','EXPIRED')),
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(), completed_at DATETIME2 NULL
);
CREATE INDEX ix_ai_job_attempts_submission ON dbo.essay_ai_job_attempts(submission_id,id DESC);
CREATE TABLE dbo.exam_essay_answers (
    id BIGINT IDENTITY PRIMARY KEY, attempt_id BIGINT NOT NULL REFERENCES dbo.exam_attempts(id),
    question_id BIGINT NOT NULL REFERENCES dbo.questions(id),
    content_json NVARCHAR(MAX) NOT NULL CHECK(ISJSON(content_json)=1),
    score DECIMAL(4,2) NULL CHECK(score BETWEEN 0 AND 10), feedback NVARCHAR(MAX) NULL,
    graded_by BIGINT NULL REFERENCES dbo.users(id), graded_at DATETIME2 NULL,
    CONSTRAINT ux_exam_essay_answer UNIQUE(attempt_id,question_id)
);
CREATE TABLE dbo.exam_answer_files (
    id BIGINT IDENTITY PRIMARY KEY, attempt_id BIGINT NOT NULL REFERENCES dbo.exam_attempts(id),
    question_id BIGINT NOT NULL REFERENCES dbo.questions(id), owner_id BIGINT NOT NULL REFERENCES dbo.users(id),
    storage_key NVARCHAR(100) NOT NULL UNIQUE, sha256 CHAR(64) NOT NULL,
    mime NVARCHAR(30) NOT NULL CHECK(mime IN('image/jpeg','image/png','image/webp')),
    size_bytes INT NOT NULL CHECK(size_bytes BETWEEN 1 AND 8388608),
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
);
CREATE TABLE dbo.exam_grade_audit (
    id BIGINT IDENTITY PRIMARY KEY, attempt_id BIGINT NOT NULL REFERENCES dbo.exam_attempts(id),
    question_id BIGINT NOT NULL REFERENCES dbo.questions(id), actor_id BIGINT NOT NULL REFERENCES dbo.users(id),
    previous_score DECIMAL(4,2) NULL, score DECIMAL(4,2) NOT NULL CHECK(score BETWEEN 0 AND 10),
    feedback NVARCHAR(MAX) NOT NULL, created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
);
EXEC(N'CREATE INDEX ix_questions_visibility ON dbo.questions(course_id,published,deleted,id) INCLUDE(author_id,type);');
EXEC(N'CREATE INDEX ix_exams_visibility ON dbo.exams(course_id,published,deleted,id) INCLUDE(author_id);');
EXEC(N'CREATE INDEX ix_essays_course ON dbo.essay_submissions(course_id,student_id,id) INCLUDE(question_id,status);');
EXEC(N'CREATE INDEX ix_questions_author ON dbo.questions(author_id,deleted,id) INCLUDE(course_id,published,type);');
EXEC(N'CREATE INDEX ix_exams_author ON dbo.exams(author_id,deleted,id) INCLUDE(course_id,published);');
CREATE INDEX ix_exam_files_attempt ON dbo.exam_answer_files(attempt_id,question_id);
CREATE INDEX ix_grade_audit_attempt ON dbo.exam_grade_audit(attempt_id,question_id,created_at);
CREATE INDEX ix_messages_conversation ON dbo.ai_messages(conversation_id,id);
CREATE INDEX ix_audit_date ON dbo.audit_logs(created_at DESC);
INSERT dbo.schema_versions(version) VALUES(2);
COMMIT;
PRINT N'V002 applied. Assign legacy questions/exams to courses before student publication. Run validate-schema.sql.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK;
    THROW;
END CATCH;
GO
