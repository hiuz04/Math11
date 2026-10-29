package com.mathio.lms.DAO;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mathio.lms.model.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

public class ReportingDAO {
    private final Database database;

    public ReportingDAO(Database database) {
        this.database = database;
    }

    /** Search only public metadata, using the same course/question/exam ACL as detail endpoints. */
    public Object getSearch(Actor actor, String query, int offset, int limit) {
        String value = query == null ? "" : query.strip();
        ApiException.require(value.length() <= 200 && offset >= 0 && offset <= 1_000_000
                && limit >= 1 && limit <= 100, 400, "Tham số tìm kiếm không hợp lệ.");
        if (value.isEmpty()) return List.of();
        String term = "%" + value.replace("!", "!!").replace("%", "!%")
                .replace("_", "!_").replace("[", "![") + "%";
        String lessonScope = actor.isStudent()
                ? "l.published=1 AND co.published=1 AND EXISTS(SELECT 1 FROM course_enrollments n WHERE n.course_id=co.id AND n.student_id=" + actor.getId() + ")"
                : AccessDAO.courseScope(actor, "co");
        String matchLesson = "(l.title COLLATE Latin1_General_100_CI_AI LIKE ? ESCAPE '!' OR ch.title COLLATE Latin1_General_100_CI_AI LIKE ? ESCAPE '!')";
        String matchQuestion = "(q.title COLLATE Latin1_General_100_CI_AI LIKE ? ESCAPE '!' OR ch.title COLLATE Latin1_General_100_CI_AI LIKE ? ESCAPE '!')";
        String sql = "SELECT l.id,l.title,ch.title AS chapter,l.course_id AS courseId,l.chapter_id AS chapterId,N'lesson' AS kind"
                + " FROM lessons l JOIN courses co ON co.id=l.course_id JOIN chapters ch ON ch.id=l.chapter_id"
                + " WHERE l.deleted=0 AND co.deleted=0 AND (" + lessonScope + ") AND " + matchLesson
                + " UNION ALL SELECT q.id,q.title,ch.title AS chapter,q.course_id AS courseId,q.chapter_id AS chapterId,N'"
                + (actor.isStudent() ? "essay" : "question") + "' AS kind FROM questions q JOIN chapters ch ON ch.id=q.chapter_id"
                + " WHERE q.deleted=0 AND (" + AccessDAO.questionScope(actor, "q") + ")"
                + (actor.isStudent() ? " AND q.type='ESSAY'" : "") + " AND " + matchQuestion;
        List<Object> args = new ArrayList<>(List.of(term, term, term, term));
        if (actor.isStudent()) {
            sql += " UNION ALL SELECT e.id,e.title,e.chapter,e.course_id AS courseId,CAST(NULL AS BIGINT) AS chapterId,N'quiz' AS kind"
                    + " FROM exams e WHERE e.deleted=0 AND (" + AccessDAO.examScope(actor, "e") + ")"
                    + " AND (e.title COLLATE Latin1_General_100_CI_AI LIKE ? ESCAPE '!' OR e.chapter COLLATE Latin1_General_100_CI_AI LIKE ? ESCAPE '!')";
            args.add(term); args.add(term);
        }
        sql += " ORDER BY title,id,kind OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        args.add(offset); args.add(limit);
        String statement = sql;
        return database.read(c -> Database.rows(c, statement, args.toArray()));
    }

    public Object getLearningHistory(Actor actor, long student) {
        return getLearningHistory(actor, student, 0, 100);
    }

    public Object getLearningHistory(Actor actor, long student, int offset, int limit) {
        long owner = student == 0 ? actor.getId() : student;
        return database.read(
                c -> {
                    AccessDAO.validateStudentAccess(c, actor, owner);
                    ArrayNode out = Json.array();
                    for (var row :
                            Database.rows(
                                    c,
                                    "SELECT"
                                        + " h.id,h.title,h.type,h.category,h.attempt,h.score,h.status,h.created_at"
                                        + " AS date,h.elapsed_seconds AS"
                                        + " elapsedSeconds,h.submission_id AS"
                                        + " submissionId,h.attempt_id AS attemptId,h.detail FROM"
                                        + " learning_history h WHERE h.student_id=? AND ("
                                            + AccessDAO.historyScope(actor, "h")
                                            + ") ORDER BY h.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS"
                                            + " ONLY",
                                    owner,
                                    offset,
                                    limit)) {
                        ObjectNode r = Json.MAPPER.valueToTree(row);
                        r.put("id", row.get("id").toString());
                        if (row.get("detail") != null)
                            r.set("detail", Json.parse(row.get("detail").toString()));
                        for (String key : List.of("detail", "submissionId", "attemptId", "score"))
                            if (row.get(key) == null) r.remove(key);
                        out.add(r);
                    }
                    return out;
                });
    }

    private static long count(Connection c, String sql, Object... args) throws SQLException {
        return ((Number) Database.one(c, sql, args).get("n")).longValue();
    }

    private static Map<String, Object> stat(String icon, Object value, String label, String tone) {
        return Map.of(
                "icon",
                icon,
                "value",
                String.valueOf(value),
                "label",
                label,
                "tone",
                tone,
                "note",
                "Dữ liệu đã lưu",
                "unit",
                "");
    }

    public Object getDashboard(Actor actor) {
        if (actor.isStudent()) {
            var s = getStudentStatistics(actor, actor.getId());
            return List.of(
                    stat("check", s.get("completedLessons"), "Bài học hoàn thành", "green"),
                    stat("file", s.get("completedExercises"), "Bài tập đã làm", "purple"),
                    stat("award", s.get("averageScore"), "Điểm trung bình", "orange"),
                    stat("clock", s.get("elapsedHours"), "Giờ học", "blue"));
        }
        return database.read(
                c -> {
                    long students =
                            count(
                                    c,
                                    "SELECT COUNT(*) AS n FROM users u WHERE u.role='STUDENT' AND"
                                        + " u.deleted=0 AND (?=1 OR EXISTS(SELECT 1 FROM"
                                        + " course_enrollments n JOIN courses c ON c.id=n.course_id"
                                        + " WHERE n.student_id=u.id AND c.deleted=0 AND"
                                        + " c.teacher_id=?))",
                                    actor.isAdmin(),
                                    actor.getId());
                    long teachers =
                            count(
                                    c,
                                    "SELECT COUNT(*) AS n FROM users WHERE role='TEACHER' AND"
                                            + " deleted=0");
                    long lessons =
                            count(
                                    c,
                                    "SELECT COUNT(*) AS n FROM lessons l JOIN courses c ON"
                                            + " c.id=l.course_id WHERE l.deleted=0 AND ("
                                            + AccessDAO.courseScope(actor, "c")
                                            + ")");
                    long questions =
                            count(
                                    c,
                                    "SELECT COUNT(*) AS n FROM questions q WHERE q.deleted=0 AND ("
                                            + AccessDAO.questionScope(actor, "q")
                                            + ")");
                    long exams =
                            count(
                                    c,
                                    "SELECT COUNT(*) AS n FROM exams e WHERE e.deleted=0 AND ("
                                            + AccessDAO.examScope(actor, "e")
                                            + ")");
                    return actor.isAdmin()
                            ? List.of(
                                    stat("users", students, "Học sinh", "blue"),
                                    stat("user", teachers, "Giáo viên", "purple"),
                                    stat("book", lessons, "Bài giảng", "orange"),
                                    stat("file", questions, "Câu hỏi", "green"))
                            : List.of(
                                    stat("users", students, "Học sinh", "blue"),
                                    stat("book", lessons, "Bài giảng", "purple"),
                                    stat("file", questions, "Câu hỏi", "orange"),
                                    stat("target", exams, "Đề thi", "green"));
                });
    }

    public Map<String, Object> getStudentStatistics(Actor actor, long id) {
        return database.read(
                c -> {
                    AccessDAO.validateStudentAccess(c, actor, id);
                    String lessonScope =
                            actor.isStudent() || actor.isAdmin()
                                    ? "1=1"
                                    : AccessDAO.courseScope(actor, "co");
                    String attempts = actor.isStudent() ? "1=1" : AccessDAO.examScope(actor, "e");
                    String essays = AccessDAO.essayScope(actor, "s");
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put(
                            "completedLessons",
                            count(
                                    c,
                                    "SELECT COUNT(*) AS n FROM lesson_progress p JOIN lessons l ON"
                                        + " l.id=p.lesson_id JOIN courses co ON co.id=l.course_id"
                                        + " WHERE p.student_id=? AND p.completed=1 AND l.deleted=0"
                                        + " AND ("
                                            + lessonScope
                                            + ")",
                                    id));
                    data.put(
                            "completedExercises",
                            count(
                                    c,
                                    "SELECT (SELECT COUNT(*) FROM exam_attempts a JOIN exams e ON"
                                            + " e.id=a.exam_id WHERE a.student_id=? AND"
                                            + " a.status<>'IN_PROGRESS' AND ("
                                            + attempts
                                            + "))+(SELECT COUNT(*) FROM essay_submissions s WHERE"
                                            + " s.student_id=? AND ("
                                            + essays
                                            + ")) AS n",
                                    id,
                                    id));
                    String history =
                            " FROM learning_history h WHERE h.student_id=? AND ("
                                    + AccessDAO.historyScope(actor, "h")
                                    + ")";
                    data.put(
                            "averageScore",
                            Database.one(
                                            c,
                                            "SELECT COALESCE(AVG(score),0) AS score FROM (SELECT"
                                                    + " h.score,ROW_NUMBER() OVER(PARTITION BY"
                                                    + " h.submission_id,h.attempt_id ORDER BY h.id"
                                                    + " DESC) AS position"
                                                    + history
                                                    + " AND h.score IS NOT NULL) latest WHERE"
                                                    + " position=1",
                                            id)
                                    .get("score"));
                    data.put(
                            "elapsedHours",
                            Database.one(
                                            c,
                                            "SELECT ROUND(((SELECT"
                                                + " COALESCE(SUM(CAST(p.elapsed_seconds AS"
                                                + " BIGINT)),0) FROM lesson_progress p JOIN lessons"
                                                + " l ON l.id=p.lesson_id JOIN courses co ON"
                                                + " co.id=l.course_id WHERE p.student_id=? AND ("
                                                    + lessonScope
                                                    + "))+(SELECT"
                                                    + " COALESCE(SUM(CAST(a.elapsed_seconds AS"
                                                    + " BIGINT)),0) FROM exam_attempts a JOIN exams"
                                                    + " e ON e.id=a.exam_id WHERE a.student_id=?"
                                                    + " AND ("
                                                    + attempts
                                                    + "))+(SELECT"
                                                    + " COALESCE(SUM(TRY_CONVERT(BIGINT,JSON_VALUE(s.state_json,'$.elapsedSeconds'))),0)"
                                                    + " FROM essay_submissions s WHERE"
                                                    + " s.student_id=? AND ("
                                                    + essays
                                                    + ")))/3600.0,2) AS hours",
                                            id,
                                            id,
                                            id)
                                    .get("hours"));
                    data.put(
                            "chapters",
                            Database.rows(
                                    c,
                                    "SELECT ch.title,COUNT(l.id) AS totalLessons,SUM(CASE WHEN"
                                        + " p.completed=1 THEN 1 ELSE 0 END) AS"
                                        + " completedLessons,COALESCE(AVG(CAST(COALESCE(p.completed,0)"
                                        + " AS FLOAT))*100,0) AS progress FROM chapters ch LEFT"
                                        + " JOIN lessons l ON l.chapter_id=ch.id AND l.deleted=0"
                                        + " AND l.published=1 AND EXISTS(SELECT 1 FROM courses co"
                                        + " JOIN course_enrollments n ON n.course_id=co.id WHERE"
                                        + " co.id=l.course_id AND co.deleted=0 AND co.published=1"
                                        + " AND n.student_id=? AND ("
                                            + lessonScope
                                            + ")) LEFT JOIN lesson_progress p ON p.lesson_id=l.id"
                                            + " AND p.student_id=? GROUP BY ch.id,ch.title ORDER BY"
                                            + " ch.id",
                                    id,
                                    id));
                    data.put(
                            "activities",
                            Database.rows(
                                    c,
                                    "SELECT TOP (10) h.title,h.type,h.score,h.status,h.created_at"
                                            + " AS date"
                                            + history
                                            + " ORDER BY h.id DESC",
                                    id));
                    data.put(
                            "scores",
                            Database.rows(
                                    c,
                                    "SELECT TOP (12) h.score,h.created_at AS date"
                                            + history
                                            + " AND h.score IS NOT NULL ORDER BY h.id DESC",
                                    id));
                    data.put(
                            "continueLesson",
                            Database.one(
                                    c,
                                    "SELECT TOP (1) l.id,l.title,l.chapter_id AS chapterId FROM"
                                        + " lessons l JOIN courses co ON co.id=l.course_id LEFT"
                                        + " JOIN lesson_progress p ON p.lesson_id=l.id AND"
                                        + " p.student_id=? WHERE l.published=1 AND l.deleted=0 AND"
                                        + " co.deleted=0 AND co.published=1 AND"
                                        + " COALESCE(p.completed,0)=0 AND ("
                                            + lessonScope
                                            + ") AND EXISTS(SELECT 1 FROM course_enrollments n"
                                            + " WHERE n.course_id=co.id AND n.student_id=?) ORDER"
                                            + " BY CASE WHEN p.id IS NOT NULL THEN 0 ELSE 1"
                                            + " END,l.id",
                                    id,
                                    id));
                    var user =
                            Database.one(c, "SELECT name FROM users WHERE id=? AND deleted=0", id);
                    ApiException.require(user != null, 404, "Không tìm thấy học sinh.");
                    data.put("name", user.get("name"));
                    return data;
                });
    }

    public Object getAnalytics(Actor actor) {
        if (actor.isStudent()) return getStudentStatistics(actor, actor.getId());
        return database.read(
                c -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    String history =
                            " FROM learning_history h WHERE ("
                                    + AccessDAO.historyScope(actor, "h")
                                    + ")";
                    out.put(
                            "activities",
                            Database.rows(
                                    c,
                                    "SELECT TOP (12) h.title,h.status,h.created_at AS date,u.name"
                                            + " FROM learning_history h JOIN users u ON"
                                            + " u.id=h.student_id WHERE ("
                                            + AccessDAO.historyScope(actor, "h")
                                            + ") ORDER BY h.id DESC"));
                    out.put(
                            "trend",
                            Database.rows(
                                    c,
                                    "SELECT CONVERT(char(7),h.created_at,126) AS month,AVG(h.score)"
                                            + " AS value"
                                            + history
                                            + " AND h.score IS NOT NULL GROUP BY"
                                            + " CONVERT(char(7),h.created_at,126) ORDER BY month"));
                    String label =
                            "CASE WHEN h.score>=9 THEN N'Xuất sắc' WHEN h.score>=7 THEN N'Khá' ELSE"
                                    + " N'Cần cải thiện' END";
                    out.put(
                            "distribution",
                            Database.rows(
                                    c,
                                    "SELECT "
                                            + label
                                            + " AS label,COUNT(*) AS count"
                                            + history
                                            + " AND h.score IS NOT NULL GROUP BY "
                                            + label));
                    if (actor.isAdmin()) {
                        out.put("aiUsage", count(c, "SELECT COUNT(*) AS n FROM ai_request_logs"));
                        out.put(
                                "activities",
                                Database.rows(
                                        c,
                                        "SELECT TOP (12) a.action AS title,N'Đã ghi nhận' AS"
                                            + " status,a.created_at AS date,u.name FROM audit_logs"
                                            + " a LEFT JOIN users u ON u.id=a.actor_id ORDER BY"
                                            + " a.id DESC"));
                    }
                    return out;
                });
    }

    public Object getNotifications(Actor actor) {
        return database.read(
                c ->
                        Database.rows(
                                c,
                                "SELECT TOP (200) id,content,is_read AS isRead,created_at AS"
                                    + " createdAt FROM notifications WHERE user_id=? ORDER BY id"
                                    + " DESC",
                                actor.getId()));
    }

    public void markNotificationRead(Actor actor, long id) {
        database.transaction(
                c -> {
                    ApiException.require(
                            Database.update(
                                            c,
                                            "UPDATE notifications SET is_read=1 WHERE id=? AND"
                                                    + " user_id=?",
                                            id,
                                            actor.getId())
                                    == 1,
                            404,
                            "Không tìm thấy thông báo.");
                    return null;
                });
    }

    public Object getAuditLogs(Actor actor) {
        actor.requireAdmin();
        return database.read(
                c ->
                        Database.rows(
                                c,
                                "SELECT TOP (200) id,actor_id AS actorId,action,resource_id AS"
                                    + " resourceId,created_at AS createdAt FROM audit_logs ORDER BY"
                                    + " id DESC"));
    }
}
