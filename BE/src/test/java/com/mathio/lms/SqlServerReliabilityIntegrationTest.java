package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mathio.lms.DAO.*;
import com.mathio.lms.control.Application;
import com.mathio.lms.model.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.mindrot.jbcrypt.BCrypt;

import java.nio.file.Path;
import java.util.*;

/** Real SQL Server coverage for V002. All database writes deliberately roll back. */
@EnabledIfEnvironmentVariable(named = "RUN_SQL_INTEGRATION", matches = "true")
class SqlServerReliabilityIntegrationTest {
    @TempDir Path storage;

    static class Rollback extends RuntimeException {}

    @Test
    void mixedGradingImageStorageAndCrossCoursePermissionsUseRealSql() {
        String previous = System.getProperty("STORAGE_DIR");
        System.setProperty("STORAGE_DIR", storage.toString());
        try (Database db = Database.configured()) {
            assertTrue(
                    db.read(c -> Database.one(c, "SELECT DB_NAME() AS name").get("name").toString())
                            .endsWith("_test"),
                    "Refusing writes outside *_test");
            assertNotNull(
                    db.read(
                            c ->
                                    Database.one(
                                            c,
                                            "SELECT version FROM schema_versions WHERE version=2")),
                    "Apply V002 manually first");
            String suffix = UUID.randomUUID().toString();
            Application app = new Application(db);
            assertThrows(
                    Rollback.class,
                    () ->
                            db.transaction(
                                    c -> {
                                        String hash =
                                                BCrypt.hashpw("Integration123!", BCrypt.gensalt(4));
                                        long adminId =
                                                Database.insert(
                                                        c,
                                                        "INSERT"
                                                            + " users(role,email,name,password_hash)"
                                                            + " VALUES('ADMIN',?,?,?)",
                                                        "admin-" + suffix + "@test.vn",
                                                        "Test admin",
                                                        hash);
                                        Actor admin = new Actor(adminId, "ADMIN", 1);
                                        Actor t1 = user(app, admin, "TEACHER", "a-" + suffix),
                                                t2 = user(app, admin, "TEACHER", "b-" + suffix),
                                                student =
                                                        user(app, admin, "STUDENT", "s-" + suffix),
                                                outsider =
                                                        user(app, admin, "STUDENT", "o-" + suffix);
                                        long subject =
                                                Database.insert(
                                                        c,
                                                        "INSERT subjects(title) VALUES(?)",
                                                        "Subject " + suffix);
                                        String chapter = "Chapter " + suffix,
                                                topic = "Topic " + suffix;
                                        long chapterId =
                                                Database.insert(
                                                        c,
                                                        "INSERT chapters(subject_id,title,position)"
                                                                + " VALUES(?,?,1)",
                                                        subject,
                                                        chapter);
                                        Database.insert(
                                                c,
                                                "INSERT topics(chapter_id,title) VALUES(?,?)",
                                                chapterId,
                                                topic);
                                        long a = course(app, admin, t1, student, "A " + suffix),
                                                b = course(app, admin, t2, student, "B " + suffix);
                                        ObjectNode q = ServiceTest.question();
                                        q.remove("id");
                                        q.put("chapter", chapter);
                                        q.put("topic", topic);
                                        q.put("courseId", b);
                                        q.put("published", false);
                                        JsonNode hidden =
                                                Json.MAPPER
                                                        .valueToTree(
                                                                app.questionDAO.saveQuestions(
                                                                        t2,
                                                                        Json.object()
                                                                                .set(
                                                                                        "questions",
                                                                                        Json.array()
                                                                                                .add(
                                                                                                        q))))
                                                        .path(0);
                                        long question = hidden.path("id").asLong();
                                        assertEquals(
                                                404,
                                                assertThrows(
                                                                ApiException.class,
                                                                () ->
                                                                        app.questionDAO
                                                                                .getQuestionById(
                                                                                        student,
                                                                                        question))
                                                        .status);
                                        ObjectNode visible = (ObjectNode) hidden.deepCopy();
                                        visible.put("published", true);
                                        app.questionDAO.saveQuestions(
                                                t2,
                                                Json.object()
                                                        .set(
                                                                "questions",
                                                                Json.array().add(visible)));
                                        assertFalse(
                                                Json.MAPPER
                                                        .valueToTree(
                                                                app.questionDAO.getQuestionById(
                                                                        student, question))
                                                        .has("answer"));
                                        assertEquals(
                                                404,
                                                assertThrows(
                                                                ApiException.class,
                                                                () ->
                                                                        app.questionDAO
                                                                                .getQuestionById(
                                                                                        outsider,
                                                                                        question))
                                                        .status);
                                        ObjectNode essay = q.deepCopy();
                                        essay.put("type", "ESSAY");
                                        essay.put("title", "Essay " + suffix);
                                        essay.put("published", true);
                                        essay.remove("options");
                                        JsonNode eq =
                                                Json.MAPPER
                                                        .valueToTree(
                                                                app.questionDAO.saveQuestions(
                                                                        t2,
                                                                        Json.object()
                                                                                .set(
                                                                                        "questions",
                                                                                        Json.array()
                                                                                                .add(
                                                                                                        essay))))
                                                        .path(0);
                                        ObjectNode exam =
                                                Json.object()
                                                        .put("title", "Mixed " + suffix)
                                                        .put("chapter", chapter)
                                                        .put("topic", topic)
                                                        .put("courseId", b)
                                                        .put("published", true)
                                                        .put("resultsPublished", false);
                                        exam.set(
                                                "matrix",
                                                Json.parse(
                                                        "{\"KNOWLEDGE\":{\"MCQ\":1,\"ESSAY\":1},\"UNDERSTANDING\":{\"MCQ\":0,\"ESSAY\":0},\"APPLICATION\":{\"MCQ\":0,\"ESSAY\":0}}"));
                                        exam.set("questions", Json.array().add(visible).add(eq));
                                        long examId =
                                                Json.MAPPER
                                                        .valueToTree(
                                                                app.examService.saveExam(t2, exam))
                                                        .path("id")
                                                        .asLong();
                                        JsonNode attempt =
                                                Json.MAPPER.valueToTree(
                                                        app.examService.startAttempt(
                                                                student, examId));
                                        long id = attempt.path("id").asLong();
                                        assertEquals(
                                                id,
                                                Json.MAPPER
                                                        .valueToTree(
                                                                app.examService.startAttempt(
                                                                        student, examId))
                                                        .path("id")
                                                        .asLong());
                                        ObjectNode answers =
                                                Json.object().put(Long.toString(question), "2");
                                        ObjectNode essayAnswers = Json.object();
                                        essayAnswers.set(
                                                eq.path("id").asText(),
                                                Json.object()
                                                        .put("text", "Bài làm tự luận")
                                                        .put("image", SecurityTest.png()));
                                        ObjectNode body = Json.object();
                                        body.set("answers", answers);
                                        body.set("essayAnswers", essayAnswers);
                                        JsonNode pending =
                                                Json.MAPPER.valueToTree(
                                                        app.examService.saveAnswers(
                                                                student, id, body, true));
                                        assertEquals(
                                                "PENDING_GRADING", pending.path("status").asText());
                                        assertFalse(pending.has("result"));
                                        long examFile =
                                                pending.path("essayAnswers")
                                                        .path(eq.path("id").asText())
                                                        .path("content")
                                                        .path("fileId")
                                                        .asLong();
                                        assertArrayEquals(
                                                ImageStorage.decode(SecurityTest.png()).bytes(),
                                                (byte[])
                                                        app.examService
                                                                .getFile(student, examFile)
                                                                .get("bytes"));
                                        assertEquals(
                                                403,
                                                assertThrows(
                                                                ApiException.class,
                                                                () ->
                                                                        app.examService
                                                                                .getAttemptById(
                                                                                        t1, id))
                                                        .status);
                                        assertEquals(
                                                403,
                                                assertThrows(
                                                                ApiException.class,
                                                                () ->
                                                                        app.examService.getFile(
                                                                                t1, examFile))
                                                        .status);
                                        ObjectNode grades =
                                                Json.object()
                                                        .set(
                                                                "grades",
                                                                Json.array()
                                                                        .add(
                                                                                Json.object()
                                                                                        .put(
                                                                                                "questionId",
                                                                                                eq.path(
                                                                                                                "id")
                                                                                                        .asLong())
                                                                                        .put(
                                                                                                "score",
                                                                                                8)
                                                                                        .put(
                                                                                                "feedback",
                                                                                                "Đã kiểm"
                                                                                                    + " tra")));
                                        assertEquals(
                                                403,
                                                assertThrows(
                                                                ApiException.class,
                                                                () ->
                                                                        app.examService
                                                                                .gradeAttempt(
                                                                                        t1, id,
                                                                                        grades))
                                                        .status);
                                        JsonNode finalGrade =
                                                Json.MAPPER.valueToTree(
                                                        app.examService.gradeAttempt(
                                                                t2, id, grades));
                                        assertEquals(
                                                9,
                                                finalGrade.path("result").path("score").asDouble());
                                        assertFalse(
                                                Json.MAPPER
                                                        .valueToTree(
                                                                app.examService.getAttemptById(
                                                                        student, id))
                                                        .has("result"));
                                        app.examService.publishResults(t2, examId);
                                        assertEquals(
                                                9,
                                                Json.MAPPER
                                                        .valueToTree(
                                                                app.examService.getAttemptById(
                                                                        student, id))
                                                        .path("result")
                                                        .path("score")
                                                        .asDouble());
                                        JsonNode submission =
                                                Json.MAPPER.valueToTree(
                                                        app.essayService.submitEssay(
                                                                student,
                                                                Json.object()
                                                                        .put(
                                                                                "questionId",
                                                                                eq.path("id")
                                                                                        .asLong())
                                                                        .set(
                                                                                "content",
                                                                                Json.object()
                                                                                        .put(
                                                                                                "text",
                                                                                                "Bài nộp")
                                                                                        .put(
                                                                                                "image",
                                                                                                SecurityTest
                                                                                                        .png()))));
                                        long sid = submission.path("id").asLong(),
                                                file =
                                                        submission
                                                                .path("originalSubmission")
                                                                .path("fileId")
                                                                .asLong();
                                        assertEquals(
                                                403,
                                                assertThrows(
                                                                ApiException.class,
                                                                () ->
                                                                        app.essayService
                                                                                .getEssayById(
                                                                                        t1, sid))
                                                        .status);
                                        assertEquals(
                                                403,
                                                assertThrows(
                                                                ApiException.class,
                                                                () ->
                                                                        app.essayService.getFile(
                                                                                t1, file))
                                                        .status);
                                        assertNotNull(app.essayService.getFile(t2, file));
                                        assertEquals(
                                                0,
                                                Json.MAPPER
                                                        .valueToTree(
                                                                app.reportingDAO.getLearningHistory(
                                                                        t1, student.getId()))
                                                        .size(),
                                                "Teacher A must not receive course B history");
                                        app.essayService.gradeEssay(
                                                t2,
                                                sid,
                                                Json.object()
                                                        .put("score", 7)
                                                        .put("feedback", "Chấm thủ công"));
                                        assertEquals(
                                                7,
                                                Json.MAPPER
                                                        .valueToTree(
                                                                app.essayService.getEssayById(
                                                                        student, sid))
                                                        .path("finalResult")
                                                        .path("score")
                                                        .asDouble());
                                        assertNotNull(
                                                Database.one(
                                                        c,
                                                        "SELECT id FROM exam_grade_audit WHERE"
                                                                + " attempt_id=?",
                                                        id));
                                        throw new Rollback();
                                    }));
            assertNull(
                    db.read(
                            c ->
                                    Database.one(
                                            c,
                                            "SELECT id FROM users WHERE email=?",
                                            "admin-" + suffix + "@test.vn")));
        } finally {
            if (previous == null) System.clearProperty("STORAGE_DIR");
            else System.setProperty("STORAGE_DIR", previous);
        }
    }

    static Actor user(Application app, Actor admin, String role, String email) {
        JsonNode u =
                Json.MAPPER.valueToTree(
                        app.userDAO.saveUser(
                                admin,
                                0,
                                Json.object()
                                        .put("name", email)
                                        .put("email", email + "@test.vn")
                                        .put("role", role)
                                        .put("password", "Integration123!")));
        return new Actor(u.path("id").asLong(), role, 1);
    }

    static long course(Application app, Actor admin, Actor teacher, Actor student, String title) {
        long id =
                Json.MAPPER
                        .valueToTree(
                                app.catalogDAO.saveCourse(
                                        admin,
                                        0,
                                        Json.object()
                                                .put("title", title)
                                                .put("teacherId", teacher.getId())
                                                .put("published", true)))
                        .path("id")
                        .asLong();
        app.catalogDAO.enrollStudent(teacher, id, Json.object().put("studentId", student.getId()));
        return id;
    }
}
