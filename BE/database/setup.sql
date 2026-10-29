-- Mathio LMS / SQL Server 2016+ (database compatibility level 130 or higher).
-- Open in SSMS, connect to your LOCAL development instance and execute the entire file.
-- Change ALL occurrences of math11_tutor_db if using another database name.
-- Creates missing objects, preserves existing data, and does not use a migration ledger.
USE [master];
GO
IF DB_ID(N'math11_tutor_db') IS NULL
    EXEC(N'CREATE DATABASE [math11_tutor_db]');
GO
USE [math11_tutor_db];
GO
IF DB_NAME() <> N'math11_tutor_db'
    THROW 50000, 'Wrong database. Select math11_tutor_db before running setup.', 1;
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRY
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.roles','U') IS NULL CREATE TABLE dbo.roles (id BIGINT IDENTITY PRIMARY KEY, code NVARCHAR(20) NOT NULL UNIQUE, CONSTRAINT ck_role CHECK(code IN ('ADMIN','TEACHER','STUDENT')));

IF OBJECT_ID(N'dbo.users','U') IS NULL CREATE TABLE dbo.users (id BIGINT IDENTITY PRIMARY KEY, role NVARCHAR(20) NOT NULL REFERENCES dbo.roles(code), email NVARCHAR(254) NOT NULL UNIQUE, name NVARCHAR(150) NOT NULL, password_hash VARCHAR(60) NOT NULL, active BIT NOT NULL DEFAULT 1, deleted BIT NOT NULL DEFAULT 0, session_version INT NOT NULL DEFAULT 1, created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME());

IF OBJECT_ID(N'dbo.user_profiles','U') IS NULL CREATE TABLE dbo.user_profiles (id BIGINT IDENTITY PRIMARY KEY, user_id BIGINT NOT NULL UNIQUE REFERENCES dbo.users(id), phone NVARCHAR(30), birth_date DATE);

IF OBJECT_ID(N'dbo.subjects','U') IS NULL CREATE TABLE dbo.subjects (id BIGINT IDENTITY PRIMARY KEY, title NVARCHAR(200) NOT NULL UNIQUE);

IF OBJECT_ID(N'dbo.chapters','U') IS NULL CREATE TABLE dbo.chapters (id BIGINT IDENTITY PRIMARY KEY, subject_id BIGINT NOT NULL REFERENCES dbo.subjects(id), title NVARCHAR(200) NOT NULL UNIQUE, position INT NOT NULL);

IF OBJECT_ID(N'dbo.topics','U') IS NULL CREATE TABLE dbo.topics (id BIGINT IDENTITY PRIMARY KEY, chapter_id BIGINT NOT NULL REFERENCES dbo.chapters(id), title NVARCHAR(200) NOT NULL, UNIQUE(chapter_id,title));

IF OBJECT_ID(N'dbo.courses','U') IS NULL CREATE TABLE dbo.courses (id BIGINT IDENTITY PRIMARY KEY, title NVARCHAR(200) NOT NULL, description NVARCHAR(2000) NOT NULL DEFAULT '', teacher_id BIGINT NOT NULL REFERENCES dbo.users(id), published BIT NOT NULL DEFAULT 0, deleted BIT NOT NULL DEFAULT 0, created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME());

IF OBJECT_ID(N'dbo.course_enrollments','U') IS NULL CREATE TABLE dbo.course_enrollments (id BIGINT IDENTITY PRIMARY KEY, course_id BIGINT NOT NULL REFERENCES dbo.courses(id), student_id BIGINT NOT NULL REFERENCES dbo.users(id), enrolled_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(), UNIQUE(course_id,student_id));

IF OBJECT_ID(N'dbo.lessons','U') IS NULL CREATE TABLE dbo.lessons (id BIGINT IDENTITY PRIMARY KEY, course_id BIGINT NOT NULL REFERENCES dbo.courses(id), chapter_id BIGINT NOT NULL REFERENCES dbo.chapters(id), author_id BIGINT NOT NULL REFERENCES dbo.users(id), title NVARCHAR(200) NOT NULL, description NVARCHAR(2000) NOT NULL DEFAULT '', objectives NVARCHAR(3000) NOT NULL DEFAULT '', content NVARCHAR(MAX) NOT NULL, content_type NVARCHAR(20) NOT NULL CONSTRAINT df_lesson_content_type DEFAULT N'TEXT' CONSTRAINT ck_lesson_content_type CHECK(content_type IN(N'TEXT',N'VIDEO',N'YOUTUBE')), video_url NVARCHAR(1000) NOT NULL DEFAULT '', duration_seconds INT NOT NULL DEFAULT 1200 CHECK(duration_seconds>=0), published BIT NOT NULL DEFAULT 0, deleted BIT NOT NULL DEFAULT 0, updated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(), created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME());

IF OBJECT_ID(N'dbo.lesson_progress','U') IS NULL CREATE TABLE dbo.lesson_progress (id BIGINT IDENTITY PRIMARY KEY, student_id BIGINT NOT NULL REFERENCES dbo.users(id), lesson_id BIGINT NOT NULL REFERENCES dbo.lessons(id), completed BIT NOT NULL DEFAULT 0, elapsed_seconds INT NOT NULL DEFAULT 0 CHECK(elapsed_seconds>=0), updated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(), UNIQUE(student_id,lesson_id));

IF OBJECT_ID(N'dbo.questions','U') IS NULL CREATE TABLE dbo.questions (id BIGINT IDENTITY PRIMARY KEY, author_id BIGINT NOT NULL REFERENCES dbo.users(id), chapter_id BIGINT NOT NULL REFERENCES dbo.chapters(id), topic_id BIGINT NOT NULL REFERENCES dbo.topics(id), title NVARCHAR(200) NOT NULL, content NVARCHAR(MAX) NOT NULL, type NVARCHAR(10) NOT NULL CHECK(type IN('MCQ','ESSAY')), difficulty NVARCHAR(10) NOT NULL CHECK(difficulty IN('EASY','MEDIUM','HARD')), cognitive_level NVARCHAR(20) NOT NULL CHECK(cognitive_level IN('KNOWLEDGE','UNDERSTANDING','APPLICATION')), answer NVARCHAR(MAX) NOT NULL, deleted BIT NOT NULL DEFAULT 0, created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME());

IF OBJECT_ID(N'dbo.question_options','U') IS NULL CREATE TABLE dbo.question_options (id BIGINT IDENTITY PRIMARY KEY, question_id BIGINT NOT NULL REFERENCES dbo.questions(id), position INT NOT NULL, content NVARCHAR(4000) NOT NULL, UNIQUE(question_id,position));

IF OBJECT_ID(N'dbo.exams','U') IS NULL CREATE TABLE dbo.exams (id BIGINT IDENTITY PRIMARY KEY, author_id BIGINT NOT NULL REFERENCES dbo.users(id), title NVARCHAR(200) NOT NULL, chapter NVARCHAR(200) NOT NULL, topic NVARCHAR(200) NOT NULL, matrix NVARCHAR(MAX) NOT NULL CHECK(ISJSON(matrix)=1), published BIT NOT NULL DEFAULT 1, deleted BIT NOT NULL DEFAULT 0, duration_seconds INT NOT NULL DEFAULT 1800 CHECK(duration_seconds>0), created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME());

IF OBJECT_ID(N'dbo.exam_questions','U') IS NULL CREATE TABLE dbo.exam_questions (id BIGINT IDENTITY PRIMARY KEY, exam_id BIGINT NOT NULL REFERENCES dbo.exams(id), question_id BIGINT NOT NULL REFERENCES dbo.questions(id), position INT NOT NULL, snapshot NVARCHAR(MAX) NOT NULL CHECK(ISJSON(snapshot)=1), UNIQUE(exam_id,position), UNIQUE(exam_id,question_id));

IF OBJECT_ID(N'dbo.exam_attempts','U') IS NULL CREATE TABLE dbo.exam_attempts (id BIGINT IDENTITY PRIMARY KEY, exam_id BIGINT NOT NULL REFERENCES dbo.exams(id), student_id BIGINT NOT NULL REFERENCES dbo.users(id), status NVARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS' CHECK(status IN('IN_PROGRESS','SUBMITTED')), started_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(), submitted_at DATETIME2, elapsed_seconds INT NOT NULL DEFAULT 0);

IF OBJECT_ID(N'dbo.exam_answers','U') IS NULL CREATE TABLE dbo.exam_answers (id BIGINT IDENTITY PRIMARY KEY, attempt_id BIGINT NOT NULL REFERENCES dbo.exam_attempts(id), question_id BIGINT NOT NULL REFERENCES dbo.questions(id), answer NVARCHAR(4000) NOT NULL, correct BIT, UNIQUE(attempt_id,question_id));

IF OBJECT_ID(N'dbo.exam_results','U') IS NULL CREATE TABLE dbo.exam_results (id BIGINT IDENTITY PRIMARY KEY, attempt_id BIGINT NOT NULL UNIQUE REFERENCES dbo.exam_attempts(id), score DECIMAL(4,2) NOT NULL CHECK(score BETWEEN 0 AND 10), correct_count INT NOT NULL, total_count INT NOT NULL, review NVARCHAR(MAX) NOT NULL CHECK(ISJSON(review)=1));

IF OBJECT_ID(N'dbo.essay_submissions','U') IS NULL CREATE TABLE dbo.essay_submissions (id BIGINT IDENTITY PRIMARY KEY, student_id BIGINT NOT NULL REFERENCES dbo.users(id), question_id BIGINT NOT NULL REFERENCES dbo.questions(id), status NVARCHAR(30) NOT NULL DEFAULT 'SUBMITTED' CHECK(status IN('SUBMITTED','CORRECT','NEEDS_TUTOR','RETRY_REQUIRED','COMPLETED','NEEDS_REVISION')), state_json NVARCHAR(MAX) NOT NULL CHECK(ISJSON(state_json)=1), private_plan NVARCHAR(MAX), revision INT NOT NULL DEFAULT 0, created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(), updated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME());

IF OBJECT_ID(N'dbo.submission_files','U') IS NULL CREATE TABLE dbo.submission_files (id BIGINT IDENTITY PRIMARY KEY, submission_id BIGINT NOT NULL REFERENCES dbo.essay_submissions(id), owner_id BIGINT NOT NULL REFERENCES dbo.users(id), storage_key NVARCHAR(100) NOT NULL UNIQUE, sha256 CHAR(64) NOT NULL, mime NVARCHAR(30) NOT NULL, size_bytes INT NOT NULL CHECK(size_bytes BETWEEN 1 AND 8388608), created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(), UNIQUE(submission_id,sha256));

IF OBJECT_ID(N'dbo.essay_evaluations','U') IS NULL CREATE TABLE dbo.essay_evaluations (id BIGINT IDENTITY PRIMARY KEY, submission_id BIGINT NOT NULL REFERENCES dbo.essay_submissions(id), result_json NVARCHAR(MAX) NOT NULL CHECK(ISJSON(result_json)=1), created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME());

IF OBJECT_ID(N'dbo.tutor_sessions','U') IS NULL CREATE TABLE dbo.tutor_sessions (id BIGINT IDENTITY PRIMARY KEY, submission_id BIGINT NOT NULL UNIQUE REFERENCES dbo.essay_submissions(id), current_step INT NOT NULL DEFAULT 0, total_steps INT NOT NULL CHECK(total_steps>0), completed BIT NOT NULL DEFAULT 0);

IF OBJECT_ID(N'dbo.tutor_steps','U') IS NULL CREATE TABLE dbo.tutor_steps (id BIGINT IDENTITY PRIMARY KEY, session_id BIGINT NOT NULL REFERENCES dbo.tutor_sessions(id), step_number INT NOT NULL, instruction NVARCHAR(4000) NOT NULL, private_answer NVARCHAR(4000) NOT NULL, correct BIT NOT NULL DEFAULT 0, UNIQUE(session_id,step_number));

IF OBJECT_ID(N'dbo.tutor_step_attempts','U') IS NULL CREATE TABLE dbo.tutor_step_attempts (id BIGINT IDENTITY PRIMARY KEY, step_id BIGINT NOT NULL REFERENCES dbo.tutor_steps(id), answer NVARCHAR(4000) NOT NULL, correct BIT NOT NULL, created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME());

IF OBJECT_ID(N'dbo.essay_retry_submissions','U') IS NULL CREATE TABLE dbo.essay_retry_submissions (id BIGINT IDENTITY PRIMARY KEY, submission_id BIGINT NOT NULL REFERENCES dbo.essay_submissions(id), file_id BIGINT NOT NULL UNIQUE REFERENCES dbo.submission_files(id), result_json NVARCHAR(MAX) NOT NULL CHECK(ISJSON(result_json)=1), created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME());

IF OBJECT_ID(N'dbo.ai_conversations','U') IS NULL CREATE TABLE dbo.ai_conversations (id BIGINT IDENTITY PRIMARY KEY, owner_id BIGINT NOT NULL REFERENCES dbo.users(id), created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME());

IF OBJECT_ID(N'dbo.ai_messages','U') IS NULL CREATE TABLE dbo.ai_messages (id BIGINT IDENTITY PRIMARY KEY, conversation_id BIGINT NOT NULL REFERENCES dbo.ai_conversations(id), role NVARCHAR(20) NOT NULL CHECK(role IN('user','model')), content NVARCHAR(MAX) NOT NULL, created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME());

IF OBJECT_ID(N'dbo.gemini_settings','U') IS NULL CREATE TABLE dbo.gemini_settings (id BIGINT IDENTITY PRIMARY KEY, singleton BIT NOT NULL UNIQUE DEFAULT 1 CHECK(singleton=1), model NVARCHAR(100) NOT NULL, temperature FLOAT NOT NULL CHECK(temperature BETWEEN 0 AND 2), max_output_tokens INT NOT NULL CHECK(max_output_tokens BETWEEN 1 AND 65536), enabled BIT NOT NULL DEFAULT 0, encrypted_api_key NVARCHAR(2000));

IF OBJECT_ID(N'dbo.prompt_templates','U') IS NULL CREATE TABLE dbo.prompt_templates (id BIGINT IDENTITY PRIMARY KEY, name NVARCHAR(30) NOT NULL UNIQUE, content NVARCHAR(MAX) NOT NULL);

IF OBJECT_ID(N'dbo.ai_request_logs','U') IS NULL CREATE TABLE dbo.ai_request_logs (id BIGINT IDENTITY PRIMARY KEY, actor_id BIGINT REFERENCES dbo.users(id), purpose NVARCHAR(50) NOT NULL, model NVARCHAR(100) NOT NULL, success BIT NOT NULL, duration_ms BIGINT NOT NULL, created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME());

IF OBJECT_ID(N'dbo.learning_history','U') IS NULL CREATE TABLE dbo.learning_history (id BIGINT IDENTITY PRIMARY KEY, student_id BIGINT NOT NULL REFERENCES dbo.users(id), title NVARCHAR(200) NOT NULL, type NVARCHAR(20) NOT NULL CHECK(type IN('MCQ','ESSAY','AI_TUTOR')), category NVARCHAR(20) NOT NULL, attempt INT NOT NULL DEFAULT 1, score DECIMAL(4,2) CHECK(score BETWEEN 0 AND 10), status NVARCHAR(50) NOT NULL, elapsed_seconds INT NOT NULL DEFAULT 0, submission_id BIGINT REFERENCES dbo.essay_submissions(id), attempt_id BIGINT NULL REFERENCES dbo.exam_attempts(id), detail NVARCHAR(MAX), created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME());

IF OBJECT_ID(N'dbo.notifications','U') IS NULL CREATE TABLE dbo.notifications (id BIGINT IDENTITY PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES dbo.users(id), content NVARCHAR(1000) NOT NULL, is_read BIT NOT NULL DEFAULT 0, created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME());

IF OBJECT_ID(N'dbo.audit_logs','U') IS NULL CREATE TABLE dbo.audit_logs (id BIGINT IDENTITY PRIMARY KEY, actor_id BIGINT REFERENCES dbo.users(id), action NVARCHAR(100) NOT NULL, resource_id BIGINT, created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME());

IF OBJECT_ID(N'dbo.idempotency_keys','U') IS NULL CREATE TABLE dbo.idempotency_keys (id BIGINT IDENTITY PRIMARY KEY, actor_id BIGINT NOT NULL REFERENCES dbo.users(id), request_key NVARCHAR(100) NOT NULL, route NVARCHAR(300) NOT NULL, fingerprint CHAR(64) NOT NULL, response_json NVARCHAR(MAX) NOT NULL, created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(), UNIQUE(actor_id,request_key));

-- Support databases that were partially prepared with the previous project scripts.
-- Dynamic SQL also supports columns added earlier in this same batch.
IF COL_LENGTH(N'dbo.lessons', N'content_type') IS NULL
    EXEC(N'ALTER TABLE dbo.lessons ADD content_type NVARCHAR(20) NOT NULL CONSTRAINT df_lesson_content_type DEFAULT N''TEXT'' CONSTRAINT ck_lesson_content_type CHECK(content_type IN(N''TEXT'',N''VIDEO'',N''YOUTUBE''));');
IF COL_LENGTH(N'dbo.learning_history', N'attempt_id') IS NULL
    EXEC(N'ALTER TABLE dbo.learning_history ADD attempt_id BIGINT NULL REFERENCES dbo.exam_attempts(id);');

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.questions') AND name=N'ix_questions_filter')
    EXEC(N'CREATE INDEX ix_questions_filter ON dbo.questions(chapter_id,topic_id,type,cognitive_level) INCLUDE(deleted);');

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.learning_history') AND name=N'ix_history_student')
    EXEC(N'CREATE INDEX ix_history_student ON dbo.learning_history(student_id,created_at DESC);');

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.essay_submissions') AND name=N'ix_essays_owner')
    EXEC(N'CREATE INDEX ix_essays_owner ON dbo.essay_submissions(student_id,updated_at DESC);');

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.exam_attempts') AND name=N'ix_attempts_student')
    EXEC(N'CREATE INDEX ix_attempts_student ON dbo.exam_attempts(student_id,started_at DESC);');

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.course_enrollments') AND name=N'ix_enrollments_student')
    EXEC(N'CREATE INDEX ix_enrollments_student ON dbo.course_enrollments(student_id,course_id);');

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.lessons') AND name=N'ix_lessons_course')
    EXEC(N'CREATE INDEX ix_lessons_course ON dbo.lessons(course_id,chapter_id,published,deleted);');

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.ai_request_logs') AND name=N'ix_ai_logs_date')
    EXEC(N'CREATE INDEX ix_ai_logs_date ON dbo.ai_request_logs(created_at);');

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.exam_attempts') AND name=N'ux_attempt_open')
    EXEC(N'CREATE UNIQUE INDEX ux_attempt_open ON dbo.exam_attempts(student_id,exam_id) WHERE status=''IN_PROGRESS'';');

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.learning_history') AND name=N'ix_history_attempt')
    EXEC(N'CREATE INDEX ix_history_attempt ON dbo.learning_history(attempt_id) WHERE attempt_id IS NOT NULL;');

INSERT dbo.roles(code)
SELECT code FROM (VALUES(N'ADMIN'),(N'TEACHER'),(N'STUDENT')) r(code)
WHERE NOT EXISTS (SELECT 1 FROM dbo.roles x WHERE x.code=r.code);
COMMIT TRANSACTION;
PRINT N'Mathio schema ready. Optional: run seed-demo.sql for local demo data.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO

-- Current additive schema version (also available as a standalone upgrade).
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
