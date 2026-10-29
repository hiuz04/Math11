package com.mathio.lms.DAO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** Truy vấn SQL Server cho module Exam; dùng Connection của transaction đang xử lý. */
public class ExamDAO {
    public List<Map<String, Object>> getAttemptHistory(Connection connection, long studentId)
            throws SQLException {
        String sql =
                """
                    SELECT id,
                           exam_id AS examId,
                           status,
                           started_at AS startedAt
                    FROM exam_attempts
                    WHERE student_id=?
                    ORDER BY id DESC
                """;
        return Database.rows(connection, sql, studentId);
    }

    public Map<String, Object> getExamAuthor(Connection connection, long examId)
            throws SQLException {
        String sql =
                """
                    SELECT author_id
                    FROM exams WITH(UPDLOCK,HOLDLOCK)
                    WHERE id=? AND deleted=0
                """
                        .strip();
        return Database.one(connection, sql, examId);
    }

    public Map<String, Object> getFirstAttempt(Connection connection, long examId)
            throws SQLException {
        String sql =
                """
                    SELECT TOP (1) id
                    FROM exam_attempts
                    WHERE exam_id=?
                """
                        .strip();
        return Database.one(connection, sql, examId);
    }

    public int updateExam(
            Connection connection,
            String title,
            String chapter,
            String topic,
            String matrix,
            long examId)
            throws SQLException {
        String sql =
                """
                    UPDATE exams
                    SET title=?,chapter=?,topic=?,matrix=?
                    WHERE id=?
                """
                        .strip();
        return Database.update(connection, sql, title, chapter, topic, matrix, examId);
    }

    public int deleteExamQuestions(Connection connection, long examId) throws SQLException {
        String sql =
                """
                    DELETE
                    FROM exam_questions
                    WHERE exam_id=?
                """
                        .strip();
        return Database.update(connection, sql, examId);
    }

    public long createExam(
            Connection connection,
            long authorId,
            String title,
            String chapter,
            String topic,
            String matrix,
            boolean published,
            int durationSeconds)
            throws SQLException {
        String sql =
                """
                    INSERT exams(author_id,title,chapter,topic,matrix,published,duration_seconds)
                    VALUES(?,?,?,?,?,?,?)
                """
                        .strip();
        return Database.insert(
                connection,
                sql,
                authorId,
                title,
                chapter,
                topic,
                matrix,
                published,
                durationSeconds);
    }

    public int createExamQuestion(
            Connection connection, long examId, long questionId, int position, String snapshot)
            throws SQLException {
        String sql =
                """
                    INSERT exam_questions(exam_id,question_id,position,snapshot)
                    VALUES(?,?,?,?)
                """
                        .strip();
        return Database.update(connection, sql, examId, questionId, position, snapshot);
    }

    public Map<String, Object> getExamById(Connection connection, long examId, boolean student)
            throws SQLException {
        String sql =
                """
                    SELECT id,
                           author_id AS authorId,
                           title,
                           chapter,
                           topic,
                           matrix,
                           duration_seconds AS durationSeconds,
                           course_id AS courseId, published, results_published AS resultsPublished,
                           created_at AS createdAt
                    FROM exams
                    WHERE id=? AND deleted=0 AND (?=0 OR published=1)
                """
                        .strip();
        return Database.one(connection, sql, examId, student);
    }

    public List<Map<String, Object>> getQuestionSnapshots(Connection connection, Object examId)
            throws SQLException {
        String sql =
                """
                    SELECT snapshot
                    FROM exam_questions
                    WHERE exam_id=?
                    ORDER BY position
                """
                        .strip();
        return Database.rows(connection, sql, examId);
    }

    public List<Map<String, Object>> getVisibleExamIds(
            Connection connection, boolean admin, long userId, boolean student)
            throws SQLException {
        String sql =
                """
                    SELECT id
                    FROM exams
                    WHERE deleted=0 AND (?=1 OR author_id=? OR ?=1 AND published=1)
                """
                        .strip();
        return Database.rows(connection, sql, admin, userId, student);
    }

    public int deleteExam(Connection connection, long examId) throws SQLException {
        String sql =
                """
                    UPDATE exams
                    SET deleted=1
                    WHERE id=?
                """
                        .strip();
        return Database.update(connection, sql, examId);
    }

    public Map<String, Object> getOpenAttemptForUpdate(
            Connection connection, long studentId, long examId) throws SQLException {
        String sql =
                """
                    SELECT id
                    FROM exam_attempts WITH(UPDLOCK,HOLDLOCK)
                    WHERE student_id=? AND exam_id=? AND status='IN_PROGRESS'
                """
                        .strip();
        return Database.one(connection, sql, studentId, examId);
    }

    public long createAttempt(Connection connection, long studentId, long examId)
            throws SQLException {
        String sql =
                """
                    INSERT exam_attempts(student_id,exam_id)
                    VALUES(?,?)
                """
                        .strip();
        return Database.insert(connection, sql, studentId, examId);
    }

    public Map<String, Object> getAttemptById(Connection connection, long attemptId)
            throws SQLException {
        String sql =
                """
                    SELECT id,
                           exam_id AS examId,
                           student_id AS studentId,
                           status,
                           started_at AS startedAt,
                           DATEADD(SECOND,(SELECT duration_seconds FROM exams WHERE id=exam_id),started_at) AS deadlineAt,
                           SYSUTCDATETIME() AS serverTime,
                           elapsed_seconds AS elapsedSeconds
                    FROM exam_attempts
                    WHERE id=?
                """
                        .strip();
        return Database.one(connection, sql, attemptId);
    }

    public Map<String, Object> getExamSummary(Connection connection, Object examId)
            throws SQLException {
        String sql =
                """
                    SELECT title,
                           duration_seconds AS durationSeconds
                    FROM exams
                    WHERE id=?
                """
                        .strip();
        return Database.one(connection, sql, examId);
    }

    public List<Map<String, Object>> getAttemptAnswers(Connection connection, long attemptId)
            throws SQLException {
        String sql =
                """
                    SELECT question_id,
                           answer
                    FROM exam_answers
                    WHERE attempt_id=?
                """
                        .strip();
        return Database.rows(connection, sql, attemptId);
    }

    public Map<String, Object> getAttemptResult(Connection connection, long attemptId)
            throws SQLException {
        String sql =
                """
                    SELECT score,
                           correct_count AS correctCount,
                           total_count AS totalCount,
                           review
                    FROM exam_results
                    WHERE attempt_id=?
                """
                        .strip();
        return Database.one(connection, sql, attemptId);
    }

    public Map<String, Object> getAttemptForUpdate(
            Connection connection, long attemptId, long studentId) throws SQLException {
        String sql =
                """
                    SELECT a.*,
                           e.duration_seconds
                    FROM exam_attempts a WITH(UPDLOCK,HOLDLOCK)
                    JOIN exams e ON e.id=a.exam_id
                    WHERE a.id=? AND a.student_id=?
                """
                        .strip();
        return Database.one(connection, sql, attemptId, studentId);
    }

    public List<Map<String, Object>> getExamQuestions(Connection connection, Object examId)
            throws SQLException {
        String sql =
                """
                    SELECT question_id,
                           snapshot
                    FROM exam_questions
                    WHERE exam_id=?
                    ORDER BY position
                """
                        .strip();
        return Database.rows(connection, sql, examId);
    }

    public int updateAnswer(Connection connection, String answer, long attemptId, Object questionId)
            throws SQLException {
        String sql =
                """
                    UPDATE exam_answers
                    SET answer=?
                    WHERE attempt_id=? AND question_id=?
                """
                        .strip();
        return Database.update(connection, sql, answer, attemptId, questionId);
    }

    public int createAnswer(Connection connection, long attemptId, Object questionId, String answer)
            throws SQLException {
        String sql =
                """
                    INSERT exam_answers(attempt_id,question_id,answer)
                    VALUES(?,?,?)
                """
                        .strip();
        return Database.update(connection, sql, attemptId, questionId, answer);
    }

    public Map<String, Object> getAnswer(Connection connection, long attemptId, Object questionId)
            throws SQLException {
        String sql =
                """
                    SELECT answer
                    FROM exam_answers
                    WHERE attempt_id=? AND question_id=?
                """
                        .strip();
        return Database.one(connection, sql, attemptId, questionId);
    }

    public int updateAnswerCorrectness(
            Connection connection, boolean correct, long attemptId, Object questionId)
            throws SQLException {
        String sql =
                """
                    UPDATE exam_answers
                    SET correct=?
                    WHERE attempt_id=? AND question_id=?
                """
                        .strip();
        return Database.update(connection, sql, correct, attemptId, questionId);
    }

    public int submitAttempt(Connection connection, long elapsedSeconds, long attemptId)
            throws SQLException {
        String sql =
                """
                    UPDATE exam_attempts
                    SET status='SUBMITTED',submitted_at=SYSUTCDATETIME(),elapsed_seconds=?
                    WHERE id=?
                """
                        .strip();
        return Database.update(connection, sql, elapsedSeconds, attemptId);
    }

    public int createResult(
            Connection connection,
            long attemptId,
            double score,
            int correctCount,
            int totalCount,
            String review)
            throws SQLException {
        String sql =
                """
                    INSERT exam_results(attempt_id,score,correct_count,total_count,review)
                    VALUES(?,?,?,?,?)
                """
                        .strip();
        return Database.update(connection, sql, attemptId, score, correctCount, totalCount, review);
    }

    public int createHistory(
            Connection connection,
            long studentId,
            Object title,
            double score,
            long elapsedSeconds,
            String detail,
            long attemptId)
            throws SQLException {
        String sql =
                """
    INSERT learning_history(student_id,title,type,category,score,status,elapsed_seconds,detail,attempt_id)
    VALUES(?,?,'MCQ',N'Kiểm tra',?,N'Hoàn thành',?,?,?)
"""
                        .strip();
        return Database.update(
                connection, sql, studentId, title, score, elapsedSeconds, detail, attemptId);
    }

    public Map<String, Object> getExamTitle(Connection connection, Object examId)
            throws SQLException {
        String sql =
                """
                    SELECT title
                    FROM exams
                    WHERE id=?
                """
                        .strip();
        return Database.one(connection, sql, examId);
    }

    public int notifySubmission(Connection connection, long studentId) throws SQLException {
        String sql =
                """
                    INSERT notifications(user_id,content)
                    VALUES(?,N'Bài kiểm tra đã được chấm. Xem lịch sử học tập để xem điểm.')
                """
                        .strip();
        return Database.update(connection, sql, studentId);
    }

    public Map<String, Object> getElapsedSeconds(Connection connection, long attemptId)
            throws SQLException {
        String sql =
                """
                    SELECT DATEDIFF_BIG(MILLISECOND,started_at,SYSUTCDATETIME()) / 1000 AS seconds
                    FROM exam_attempts
                    WHERE id=?
                """
                        .strip();
        return Database.one(connection, sql, attemptId);
    }
}
