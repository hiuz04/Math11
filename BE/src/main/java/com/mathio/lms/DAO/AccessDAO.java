package com.mathio.lms.DAO;

import com.mathio.lms.model.Actor;
import com.mathio.lms.model.ApiException;

import java.sql.Connection;
import java.sql.SQLException;

public final class AccessDAO {
    /** Aliases are internal constants; user ids are server-authenticated longs. */
    public static String courseScope(Actor actor, String alias) {
        return actor.isAdmin()
                ? "1=1"
                : alias + ".deleted=0 AND " + alias + ".teacher_id=" + actor.getId();
    }

    public static String examScope(Actor actor, String alias) {
        if (actor.isAdmin()) return "1=1";
        if (actor.isStudent())
            return alias
                    + ".deleted=0 AND "
                    + alias
                    + ".published=1 AND EXISTS(SELECT 1 FROM courses scope_c JOIN"
                    + " course_enrollments scope_n ON scope_n.course_id=scope_c.id WHERE"
                    + " scope_c.id="
                    + alias
                    + ".course_id AND scope_c.deleted=0 AND scope_c.published=1 AND"
                    + " scope_n.student_id="
                    + actor.getId()
                    + ")";
        return "("
                + alias
                + ".author_id="
                + actor.getId()
                + " OR EXISTS(SELECT 1 FROM courses scope_c WHERE scope_c.id="
                + alias
                + ".course_id AND "
                + courseScope(actor, "scope_c")
                + "))";
    }

    public static String questionScope(Actor actor, String alias) {
        if (actor.isAdmin()) return "1=1";
        if (actor.isStudent())
            return alias
                    + ".published=1 AND EXISTS(SELECT 1 FROM courses scope_c JOIN"
                    + " course_enrollments scope_n ON scope_n.course_id=scope_c.id WHERE"
                    + " scope_c.id="
                    + alias
                    + ".course_id AND scope_c.published=1 AND scope_c.deleted=0 AND"
                    + " scope_n.student_id="
                    + actor.getId()
                    + ")";
        return "("
                + alias
                + ".author_id="
                + actor.getId()
                + " OR EXISTS(SELECT 1 FROM courses scope_c WHERE scope_c.id="
                + alias
                + ".course_id AND "
                + courseScope(actor, "scope_c")
                + "))";
    }

    public static String essayScope(Actor actor, String alias) {
        if (actor.isAdmin()) return "1=1";
        if (actor.isStudent()) return alias + ".student_id=" + actor.getId();
        return "EXISTS(SELECT 1 FROM questions scope_q WHERE scope_q.id="
                + alias
                + ".question_id AND (scope_q.author_id="
                + actor.getId()
                + " OR EXISTS(SELECT 1 FROM courses scope_c WHERE scope_c.id="
                + alias
                + ".course_id AND "
                + courseScope(actor, "scope_c")
                + ")))";
    }

    public static String historyScope(Actor actor, String alias) {
        if (actor.isAdmin()) return "1=1";
        if (actor.isStudent())
            return alias
                    + ".student_id="
                    + actor.getId()
                    + " AND ("
                    + alias
                    + ".attempt_id IS NULL OR EXISTS(SELECT 1 FROM exam_attempts publication_a JOIN"
                    + " exams publication_e ON publication_e.id=publication_a.exam_id WHERE"
                    + " publication_a.id="
                    + alias
                    + ".attempt_id AND publication_e.results_published=1))";
        return "(EXISTS(SELECT 1 FROM exam_attempts scope_a JOIN exams scope_e ON"
                + " scope_e.id=scope_a.exam_id WHERE scope_a.id="
                + alias
                + ".attempt_id AND "
                + examScope(actor, "scope_e")
                + ") OR EXISTS(SELECT 1 FROM essay_submissions scope_s WHERE scope_s.id="
                + alias
                + ".submission_id AND "
                + essayScope(actor, "scope_s")
                + "))";
    }

    public static void validateExamAccess(Connection c, Actor actor, long exam)
            throws SQLException {
        ApiException.require(
                Database.one(
                                c,
                                "SELECT e.id FROM exams e WHERE e.id=? AND ("
                                        + examScope(actor, "e")
                                        + ")",
                                exam)
                        != null,
                403,
                "Đề thi ngoài phạm vi được phép.");
    }

    public static void validateEssayAccess(Connection c, Actor actor, long submission)
            throws SQLException {
        ApiException.require(
                Database.one(
                                c,
                                "SELECT s.id FROM essay_submissions s WHERE s.id=? AND ("
                                        + essayScope(actor, "s")
                                        + ")",
                                submission)
                        != null,
                403,
                "Bài tự luận ngoài phạm vi được phép.");
    }

    public static Long resolveCourse(
            Connection c, Actor actor, com.fasterxml.jackson.databind.JsonNode input)
            throws SQLException {
        if (input.hasNonNull("courseId")) {
            long id = com.mathio.lms.model.Json.id(input, "courseId");
            validateCourseAccess(c, actor, id);
            return id;
        }
        var courses =
                Database.rows(
                        c,
                        "SELECT TOP (2) c.id FROM courses c WHERE c.deleted=0 AND ("
                                + courseScope(actor, "c")
                                + ") ORDER BY c.id");
        return courses.size() == 1 ? ((Number) courses.get(0).get("id")).longValue() : null;
    }

    public static void audit(Connection c, Actor actor, String action, long id)
            throws SQLException {
        Database.update(
                c,
                "INSERT audit_logs(actor_id,action,resource_id) VALUES(?,?,?)",
                actor.getId(),
                action,
                id);
    }

    public static void validateCourseAccess(Connection connection, Actor currentUser, long id)
            throws SQLException {
        var row =
                Database.one(
                        connection,
                        """
                            SELECT teacher_id,
                                   published
                            FROM courses
                            WHERE id=? AND deleted=0
                        """,
                        id);
        ApiException.require(row != null, 404, "Không tìm thấy khóa học.");
        if (currentUser.isAdmin()) return;
        if (currentUser.isStudent())
            ApiException.require(
                    Boolean.TRUE.equals(row.get("published"))
                            && Database.one(
                                            connection,
                                            """
                                                SELECT id
                                                FROM course_enrollments
                                                WHERE course_id=? AND student_id=?
                                            """,
                                            id,
                                            currentUser.getId())
                                    != null,
                    403,
                    "Bạn chưa được ghi danh khóa học.");
        else
            ApiException.require(
                    ((Number) row.get("teacher_id")).longValue() == currentUser.getId(),
                    403,
                    "Khóa học ngoài phạm vi quản lý.");
    }

    public static void validateStudentAccess(Connection connection, Actor currentUser, long student)
            throws SQLException {
        if (currentUser.isAdmin() || currentUser.isStudent() && currentUser.getId() == student)
            return;
        ApiException.require(
                !currentUser.isStudent()
                        && Database.one(
                                        connection,
                                        """
                                            SELECT TOP (1) e.id
                                            FROM course_enrollments e
                                            JOIN courses c ON c.id=e.course_id
                                            WHERE e.student_id=? AND c.teacher_id=? AND c.deleted=0
                                        """,
                                        student,
                                        currentUser.getId())
                                != null,
                403,
                "Học sinh ngoài phạm vi quản lý.");
    }

    public static void validateAuthorAccess(Actor currentUser, Object id) {
        currentUser.requireStaff();
        ApiException.require(
                currentUser.isAdmin() || ((Number) id).longValue() == currentUser.getId(),
                403,
                "Nội dung ngoài phạm vi quản lý.");
    }

    private AccessDAO() {}
}
