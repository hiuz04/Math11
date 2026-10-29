package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mathio.lms.DAO.Database;
import com.mathio.lms.control.Application;
import com.mathio.lms.model.Actor;
import com.mathio.lms.model.ApiException;
import com.mathio.lms.model.Json;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mindrot.jbcrypt.BCrypt;

import java.util.List;
import java.util.UUID;

/** Opt-in integration test against SQL Server prepared with setup.sql; every write rolls back. */
@EnabledIfEnvironmentVariable(named = "RUN_SQL_INTEGRATION", matches = "true")
class SqlServerIntegrationTest {
    private static class Rollback extends RuntimeException {}

    @Test
    void sqlServerPersistsAndScopesTheEntireLmsTransaction() {
        try (Database db = Database.configured()) {
            String name =
                    db.read(
                            c ->
                                    Database.one(c, "SELECT DB_NAME() AS name")
                                            .get("name")
                                            .toString());
            assertTrue(
                    name.endsWith("_test"),
                    "Refusing integration writes outside a database ending in _test");
            Application app = new Application(db);
            String suffix = UUID.randomUUID().toString();
            String email = "student-" + suffix + "@example.vn";
            assertThrows(
                    Rollback.class,
                    () ->
                            db.transaction(
                                    c -> {
                                        for (String role : List.of("ADMIN", "TEACHER", "STUDENT"))
                                            if (Database.one(
                                                            c,
                                                            "SELECT id FROM roles WHERE code=?",
                                                            role)
                                                    == null)
                                                Database.update(
                                                        c, "INSERT roles(code) VALUES(?)", role);
                                        long adminId =
                                                Database.insert(
                                                        c,
                                                        "INSERT"
                                                            + " users(role,email,name,password_hash)"
                                                            + " VALUES('ADMIN',?,?,?)",
                                                        "admin-" + suffix + "@example.vn",
                                                        "Integration admin",
                                                        BCrypt.hashpw(
                                                                "Integration123!",
                                                                BCrypt.gensalt(4)));
                                        Actor admin = new Actor(adminId, "ADMIN", 1);
                                        JsonNode teacher =
                                                Json.MAPPER.valueToTree(
                                                        app.userDAO.saveUser(
                                                                admin,
                                                                0,
                                                                Json.parse(
                                                                        "{\"name\":\"Giáo viên kiểm"
                                                                            + " thử\",\"email\":\"teacher-"
                                                                                + suffix
                                                                                + "@example.vn\",\"role\":\"TEACHER\",\"password\":\"Integration123!\"}")));
                                        long teacherId = teacher.path("id").asLong();
                                        Actor instructor = new Actor(teacherId, "TEACHER", 1);
                                        JsonNode user =
                                                Json.MAPPER.valueToTree(
                                                        app.userDAO.saveUser(
                                                                admin,
                                                                0,
                                                                Json.parse(
                                                                        "{\"name\":\"Học sinh kiểm"
                                                                            + " thử\",\"email\":\""
                                                                                + email
                                                                                + "\",\"role\":\"STUDENT\",\"password\":\"Integration123!\"}")));
                                        Actor student =
                                                new Actor(user.path("id").asLong(), "STUDENT", 1);
                                        assertEquals(
                                                student,
                                                app.authService.login(
                                                        Json.object()
                                                                .put("email", email)
                                                                .put(
                                                                        "password",
                                                                        "Integration123!")));
                                        long subject =
                                                Database.insert(
                                                        c,
                                                        "INSERT subjects(title) VALUES(?)",
                                                        "Subject " + suffix);
                                        long chapter =
                                                Database.insert(
                                                        c,
                                                        "INSERT chapters(subject_id,title,position)"
                                                                + " VALUES(?,?,1)",
                                                        subject,
                                                        "Chapter " + suffix);
                                        Database.insert(
                                                c,
                                                "INSERT topics(chapter_id,title) VALUES(?,?)",
                                                chapter,
                                                "Topic " + suffix);
                                        long course =
                                                Json.MAPPER
                                                        .valueToTree(
                                                                app.catalogDAO.saveCourse(
                                                                        admin,
                                                                        0,
                                                                        Json.object()
                                                                                .put(
                                                                                        "title",
                                                                                        "Course "
                                                                                                + suffix)
                                                                                .put(
                                                                                        "teacherId",
                                                                                        teacherId)
                                                                                .put(
                                                                                        "published",
                                                                                        true)))
                                                        .path("id")
                                                        .asLong();
                                        app.catalogDAO.enrollStudent(
                                                instructor,
                                                course,
                                                Json.object().put("studentId", student.getId()));
                                        long lesson =
                                                Json.MAPPER
                                                        .valueToTree(
                                                                app.catalogDAO.saveLesson(
                                                                        instructor,
                                                                        0,
                                                                        Json.object()
                                                                                .put(
                                                                                        "title",
                                                                                        "Bài giảng"
                                                                                            + " tiếng"
                                                                                            + " Việt")
                                                                                .put(
                                                                                        "courseId",
                                                                                        course)
                                                                                .put(
                                                                                        "chapterId",
                                                                                        chapter)
                                                                                .put(
                                                                                        "content",
                                                                                        "<h2>Cấp số"
                                                                                            + " cộng</h2><p>uₙ"
                                                                                            + " = u₁"
                                                                                            + " + (n−1)d</p><script>alert(1)</script>")
                                                                                .put(
                                                                                        "published",
                                                                                        true)))
                                                        .path("id")
                                                        .asLong();
                                        assertFalse(
                                                Json.stringify(
                                                                app.catalogDAO.getLessonById(
                                                                        student, lesson))
                                                        .contains("script"));
                                        app.catalogDAO.updateProgress(
                                                student,
                                                lesson,
                                                Json.object()
                                                        .put("completed", true)
                                                        .put("elapsedSeconds", 60));
                                        ObjectNode q = ServiceTest.question();
                                        q.remove("id");
                                        q.put("chapter", "Chapter " + suffix);
                                        q.put("topic", "Topic " + suffix);
                                        JsonNode bank =
                                                Json.MAPPER.valueToTree(
                                                        app.questionDAO.saveQuestions(
                                                                instructor,
                                                                Json.object()
                                                                        .set(
                                                                                "questions",
                                                                                Json.array()
                                                                                        .add(q))));
                                        JsonNode matrix =
                                                Json.parse(
                                                        "{\"KNOWLEDGE\":{\"MCQ\":1,\"ESSAY\":0},\"UNDERSTANDING\":{\"MCQ\":0,\"ESSAY\":0},\"APPLICATION\":{\"MCQ\":0,\"ESSAY\":0}}");
                                        ObjectNode exam =
                                                Json.object()
                                                        .put("title", "Exam " + suffix)
                                                        .put("chapter", "Chapter " + suffix)
                                                        .put("topic", "Topic " + suffix);
                                        exam.set("matrix", matrix);
                                        exam.set("questions", bank);
                                        long examId =
                                                Json.MAPPER
                                                        .valueToTree(
                                                                app.examService.saveExam(
                                                                        instructor, exam))
                                                        .path("id")
                                                        .asLong();
                                        JsonNode attempt =
                                                Json.MAPPER.valueToTree(
                                                        app.examService.startAttempt(
                                                                student, examId));
                                        assertFalse(
                                                attempt.path("questions").path(0).has("answer"));
                                        ObjectNode answers =
                                                Json.object()
                                                        .put(bank.path(0).path("id").asText(), "2");
                                        JsonNode result =
                                                Json.MAPPER.valueToTree(
                                                        app.examService.saveAnswers(
                                                                student,
                                                                attempt.path("id").asLong(),
                                                                Json.object()
                                                                        .set("answers", answers),
                                                                true));
                                        assertEquals(
                                                10, result.path("result").path("score").asDouble());
                                        assertEquals(
                                                1,
                                                Json.MAPPER
                                                        .valueToTree(
                                                                app.reportingDAO.getLearningHistory(
                                                                        student, 0))
                                                        .size());
                                        assertEquals(
                                                1,
                                                ((Number)
                                                                app.reportingDAO
                                                                        .getStudentStatistics(
                                                                                instructor,
                                                                                student.getId())
                                                                        .get("completedLessons"))
                                                        .intValue());
                                        assertThrows(
                                                ApiException.class,
                                                () ->
                                                        app.reportingDAO.getLearningHistory(
                                                                new Actor(adminId, "STUDENT", 1),
                                                                student.getId()));
                                        throw new Rollback();
                                    }));
            assertNull(db.read(c -> Database.one(c, "SELECT id FROM users WHERE email=?", email)));
        }
    }
}
