SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('question_reviews','U') IS NULL
CREATE TABLE question_reviews (
 reviewer_id BIGINT NOT NULL REFERENCES users(id),
 content_hash VARCHAR(64) NOT NULL,
 reviewed_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
 CONSTRAINT PK_question_reviews PRIMARY KEY(reviewer_id,content_hash)
);
COMMIT;
