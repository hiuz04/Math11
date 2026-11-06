-- Apply manually to the intended Math11 database after backup. No links are inferred.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH(N'dbo.questions', N'course_id') IS NULL OR COL_LENGTH(N'dbo.exams', N'course_id') IS NULL
    THROW 51000, 'Select the Math11 database with V002 applied.', 1;

IF OBJECT_ID(N'dbo.lesson_practice_questions', N'U') IS NULL
    CREATE TABLE dbo.lesson_practice_questions (
        lesson_id BIGINT NOT NULL REFERENCES dbo.lessons(id),
        question_id BIGINT NOT NULL REFERENCES dbo.questions(id),
        CONSTRAINT pk_lesson_practice_questions PRIMARY KEY (lesson_id, question_id)
    );

IF OBJECT_ID(N'dbo.lesson_practice_exams', N'U') IS NULL
    CREATE TABLE dbo.lesson_practice_exams (
        lesson_id BIGINT NOT NULL REFERENCES dbo.lessons(id),
        exam_id BIGINT NOT NULL REFERENCES dbo.exams(id),
        CONSTRAINT pk_lesson_practice_exams PRIMARY KEY (lesson_id, exam_id)
    );

IF NOT EXISTS (SELECT 1 FROM dbo.schema_versions WHERE version=5)
    INSERT dbo.schema_versions(version) VALUES(5);
COMMIT;
