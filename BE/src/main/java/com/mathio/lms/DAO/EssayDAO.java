package com.mathio.lms.DAO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** Truy vấn SQL Server cho module Essay; dùng Connection của transaction đang xử lý. */
public class EssayDAO {
    public int updateSubmission(
            Connection connection, String state, String status, long submissionId)
            throws SQLException {
        String sql =
                """
                    UPDATE essay_submissions
                    SET state_json=?,status=?,revision=revision+1,updated_at=SYSUTCDATETIME()
                    WHERE id=?
                """
                        .strip();
        return Database.update(connection, sql, state, status, submissionId);
    }

    public Map<String, Object> getDuplicateFile(
            Connection connection, long submissionId, String sha256) throws SQLException {
        String sql =
                """
                    SELECT id
                    FROM submission_files
                    WHERE submission_id=? AND sha256=?
                """
                        .strip();
        return Database.one(connection, sql, submissionId, sha256);
    }

    public long createFile(
            Connection connection,
            long submissionId,
            long ownerId,
            String storageKey,
            String sha256,
            String mime,
            int sizeBytes)
            throws SQLException {
        String sql =
                """
    INSERT submission_files(submission_id,owner_id,storage_key,sha256,mime,size_bytes)
    VALUES(?,?,?,?,?,?)
"""
                        .strip();
        return Database.insert(
                connection, sql, submissionId, ownerId, storageKey, sha256, mime, sizeBytes);
    }

    public Map<String, Object> getFileContent(Connection connection, long fileId)
            throws SQLException {
        String sql =
                """
                    SELECT storage_key,
                           mime
                    FROM submission_files
                    WHERE id=?
                """
                        .strip();
        return Database.one(connection, sql, fileId);
    }

    public long createSubmission(
            Connection connection, long studentId, long questionId, String state)
            throws SQLException {
        String sql =
                """
                    INSERT essay_submissions(student_id,question_id,state_json)
                    VALUES(?,?,?)
                """
                        .strip();
        return Database.insert(connection, sql, studentId, questionId, state);
    }

    public List<Map<String, Object>> getStudentSubmissions(Connection connection, long studentId)
            throws SQLException {
        String sql =
                """
                    SELECT id,
                           student_id,
                           state_json
                    FROM essay_submissions
                    WHERE student_id=?
                    ORDER BY id DESC
                """
                        .strip();
        return Database.rows(connection, sql, studentId);
    }

    public int createEvaluation(Connection connection, long submissionId, String result)
            throws SQLException {
        String sql =
                """
                    INSERT essay_evaluations(submission_id,result_json)
                    VALUES(?,?)
                """
                        .strip();
        return Database.update(connection, sql, submissionId, result);
    }

    public long createTutorSession(Connection connection, long submissionId, int totalSteps)
            throws SQLException {
        String sql =
                """
                    INSERT tutor_sessions(submission_id,total_steps)
                    VALUES(?,?)
                """
                        .strip();
        return Database.insert(connection, sql, submissionId, totalSteps);
    }

    public long createTutorStep(
            Connection connection,
            long sessionId,
            int stepNumber,
            String instruction,
            String privateAnswer)
            throws SQLException {
        String sql =
                """
                    INSERT tutor_steps(session_id,step_number,instruction,private_answer)
                    VALUES(?,?,?,?)
                """
                        .strip();
        return Database.insert(connection, sql, sessionId, stepNumber, instruction, privateAnswer);
    }

    public int updatePrivatePlan(Connection connection, String plan, long submissionId)
            throws SQLException {
        String sql =
                """
                    UPDATE essay_submissions
                    SET private_plan=?
                    WHERE id=?
                """
                        .strip();
        return Database.update(connection, sql, plan, submissionId);
    }

    public int createStepAttempt(
            Connection connection, long stepId, String answer, Boolean correct, String verification)
            throws SQLException {
        String sql =
                """
                    INSERT tutor_step_attempts(step_id,answer,correct,verification)
                    VALUES(?,?,?,?)
                """
                        .strip();
        return Database.update(connection, sql, stepId, answer, correct, verification);
    }

    public int updateStepCorrectness(Connection connection, boolean correct, long stepId)
            throws SQLException {
        String sql =
                """
                    UPDATE tutor_steps
                    SET correct=?
                    WHERE id=?
                """
                        .strip();
        return Database.update(connection, sql, correct, stepId);
    }

    public int completeTutorSession(Connection connection, long submissionId) throws SQLException {
        String sql =
                """
                    UPDATE tutor_sessions
                    SET completed=1
                    WHERE submission_id=?
                """
                        .strip();
        return Database.update(connection, sql, submissionId);
    }

    public Map<String, Object> getTutorStep(
            Connection connection, long submissionId, int stepNumber) throws SQLException {
        String sql =
                """
                    SELECT ts.id
                    FROM tutor_steps ts
                    JOIN tutor_sessions s ON s.id=ts.session_id
                    WHERE s.submission_id=? AND ts.step_number=?
                """
                        .strip();
        return Database.one(connection, sql, submissionId, stepNumber);
    }

    public int updateCurrentStep(Connection connection, int currentStep, long submissionId)
            throws SQLException {
        String sql =
                """
                    UPDATE tutor_sessions
                    SET current_step=?
                    WHERE submission_id=?
                """
                        .strip();
        return Database.update(connection, sql, currentStep, submissionId);
    }

    public Map<String, Object> getCompletedTutorSession(Connection connection, long submissionId)
            throws SQLException {
        String sql =
                """
                    SELECT id
                    FROM tutor_sessions
                    WHERE submission_id=? AND completed=1
                """
                        .strip();
        return Database.one(connection, sql, submissionId);
    }

    public int createRetry(Connection connection, long submissionId, long fileId, String result)
            throws SQLException {
        String sql =
                """
                    INSERT essay_retry_submissions(submission_id,file_id,result_json)
                    VALUES(?,?,?)
                """
                        .strip();
        return Database.update(connection, sql, submissionId, fileId, result);
    }

    public int createHistory(
            Connection connection,
            long studentId,
            String title,
            String type,
            String category,
            int attempt,
            Object score,
            String status,
            int elapsedSeconds,
            long submissionId)
            throws SQLException {
        String sql =
                """
    INSERT learning_history(student_id,title,type,category,attempt,score,status,elapsed_seconds,submission_id)
    VALUES(?,?,?,?,?,?,?,?,?)
"""
                        .strip();
        return Database.update(
                connection,
                sql,
                studentId,
                title,
                type,
                category,
                attempt,
                score,
                status,
                elapsedSeconds,
                submissionId);
    }

    public Map<String, Object> getFileById(Connection connection, long fileId) throws SQLException {
        String sql =
                """
                    SELECT owner_id, submission_id,
                           storage_key,
                           mime
                    FROM submission_files
                    WHERE id=?
                """
                        .strip();
        return Database.one(connection, sql, fileId);
    }

    public Map<String, Object> getSubmission(Connection connection, long submissionId, boolean lock)
            throws SQLException {
        String sql =
                lock
                        ? """
                              SELECT id,
                                     student_id,
                                     question_id,
                                     status,
                                     state_json,
                                     private_plan, reference_json,
                                     revision
                              FROM essay_submissions WITH(UPDLOCK,HOLDLOCK)
                              WHERE id=?
                          """
                        : """
                              SELECT id,
                                     student_id,
                                     question_id,
                                     status,
                                     state_json,
                                     private_plan, reference_json,
                                     revision
                              FROM essay_submissions
                              WHERE id=?
                          """;
        return Database.one(connection, sql, submissionId);
    }
}
