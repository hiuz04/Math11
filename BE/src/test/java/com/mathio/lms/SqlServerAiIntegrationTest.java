package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.mathio.lms.DAO.*;
import com.mathio.lms.model.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.mindrot.jbcrypt.BCrypt;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real SQL/leases/locks and real image storage; Gemini is an explicitly mocked HTTP boundary. */
@EnabledIfEnvironmentVariable(named = "RUN_SQL_INTEGRATION", matches = "true")
class SqlServerAiIntegrationTest {
    @TempDir Path storage;

    @Test
    void committedAiPhasesRecoverLeasesAndPersistTutorRetryWithoutHoldingJdbc() throws Exception {
        String old = System.getProperty("STORAGE_DIR");
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
                                            "SELECT version FROM schema_versions WHERE"
                                                    + " version=2")));
            long[] ids = new long[6];
            String suffix = UUID.randomUUID().toString();
            ExecutorService pool = Executors.newFixedThreadPool(20);
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            try {
                db.transaction(
                        c -> {
                            String hash = BCrypt.hashpw("Integration123!", BCrypt.gensalt(4));
                            ids[0] =
                                    Database.insert(
                                            c,
                                            "INSERT users(role,email,name,password_hash)"
                                                    + " VALUES('TEACHER',?,?,?)",
                                            "ai-teacher-" + suffix + "@test.vn",
                                            "Teacher fixture",
                                            hash);
                            ids[1] =
                                    Database.insert(
                                            c,
                                            "INSERT users(role,email,name,password_hash)"
                                                    + " VALUES('STUDENT',?,?,?)",
                                            "ai-student-" + suffix + "@test.vn",
                                            "Student fixture",
                                            hash);
                            ids[2] =
                                    Database.insert(
                                            c, "INSERT subjects(title) VALUES(?)", "AI " + suffix);
                            ids[3] =
                                    Database.insert(
                                            c,
                                            "INSERT chapters(subject_id,title,position)"
                                                    + " VALUES(?,?,1)",
                                            ids[2],
                                            "AI chapter " + suffix);
                            Database.insert(
                                    c,
                                    "INSERT topics(chapter_id,title) VALUES(?,?)",
                                    ids[3],
                                    "AI topic " + suffix);
                            ids[4] =
                                    Database.insert(
                                            c,
                                            "INSERT courses(title,teacher_id,published)"
                                                    + " VALUES(?,?,1)",
                                            "AI " + suffix,
                                            ids[0]);
                            Database.insert(
                                    c,
                                    "INSERT course_enrollments(course_id,student_id) VALUES(?,?)",
                                    ids[4],
                                    ids[1]);
                            ids[5] =
                                    Database.insert(
                                            c,
                                            "INSERT"
                                                + " questions(author_id,chapter_id,topic_id,title,content,type,difficulty,cognitive_level,answer,course_id,published)"
                                                + " SELECT ?,?,id,N'Rút gọn',N'Rút gọn"
                                                + " 1+x',N'ESSAY',N'EASY',N'KNOWLEDGE',N'x+1',?,1"
                                                + " FROM topics WHERE chapter_id=?",
                                            ids[0],
                                            ids[3],
                                            ids[4],
                                            ids[3]);
                            return null;
                        });
                Actor student = new Actor(ids[1], "STUDENT", 1);
                QuestionDAO questions = new QuestionDAO(db);
                ConfigurationService config = mock(ConfigurationService.class);
                GeminiClient client = mock(GeminiClient.class);
                AtomicInteger calls = new AtomicInteger();
                when(config.getSettings())
                        .thenReturn(
                                new GeminiSettings("test-fixture", 0, 512, true, "fixture-only"));
                when(config.getPromptTemplates())
                        .thenReturn(
                                Map.of(
                                        "system",
                                        "system",
                                        "essayEvaluation",
                                        "essayEvaluation",
                                        "stepTutor",
                                        "stepTutor",
                                        "retryEvaluation",
                                        "retryEvaluation"));
                when(client.generate(any(), anyString(), anyString(), nullable(String.class)))
                        .thenAnswer(
                                inv -> {
                                    db.requireNoTransaction();
                                    String system = inv.getArgument(1);
                                    calls.incrementAndGet();
                                    if (system.contains("stepTutor"))
                                        return Json.parse(
                                                "{\"steps\":[{\"instruction\":\"Gom các hạng"
                                                        + " tử.\",\"hint\":\"Áp dụng giao"
                                                        + " hoán.\",\"expectedAnswer\":\"x+1\"}]}");
                                    if (system.contains("retryEvaluation"))
                                        return Json.parse(
                                                "{\"correct\":true,\"confident\":true,\"feedback\":\"Đã"
                                                    + " hiểu cách làm\",\"score\":10}");
                                    entered.countDown();
                                    assertTrue(release.await(15, TimeUnit.SECONDS));
                                    return Json.parse(
                                            "{\"correct\":false,\"confident\":true,\"feedback\":\"Kiểm"
                                                + " tra các hạng tử\",\"score\":3}");
                                });
                AiService ai = new AiService(db, config, client);
                EssayService essays = new EssayService(db, questions, ai, new ImageStorage());
                TutorService tutor = new TutorService(db, questions, ai, essays);
                long sid =
                        Json.MAPPER
                                .valueToTree(
                                        essays.submitEssay(
                                                student,
                                                Json.object()
                                                        .put("questionId", ids[5])
                                                        .set(
                                                                "content",
                                                                Json.object()
                                                                        .put("text", "wrong")
                                                                        .put(
                                                                                "image",
                                                                                SecurityTest
                                                                                        .png()))))
                                .path("id")
                                .asLong();
                Future<StoredResponse> first =
                        pool.submit(
                                () ->
                                        new IdempotencyService(db)
                                                .execute(
                                                        student,
                                                        "sql-ai-request-123456",
                                                        "POST /ai/essays/evaluate",
                                                        "{}",
                                                        () ->
                                                                new StoredResponse(
                                                                        200,
                                                                        Json.stringify(
                                                                                essays
                                                                                        .evaluateEssay(
                                                                                                student,
                                                                                                sid)))));
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                List<Future<Integer>> others = new ArrayList<>();
                for (int i = 0; i < 19; i++)
                    others.add(
                            pool.submit(
                                    () ->
                                            assertThrows(
                                                            ApiException.class,
                                                            () ->
                                                                    essays.evaluateEssay(
                                                                            student, sid))
                                                    .status));
                for (var future : others) assertEquals(409, future.get(10, TimeUnit.SECONDS));
                release.countDown();
                assertEquals(200, first.get(10, TimeUnit.SECONDS).getStatus());
                assertEquals(1, calls.get());
                new IdempotencyService(db)
                        .execute(
                                student,
                                "sql-ai-request-123456",
                                "POST /ai/essays/evaluate",
                                "{}",
                                () -> {
                                    fail("must replay committed response");
                                    return null;
                                });
                db.transaction(
                        c -> {
                            Database.update(
                                    c,
                                    "UPDATE essay_ai_jobs SET"
                                        + " kind='TUTOR',token='stale',status='PROCESSING',lease_until=DATEADD(SECOND,-1,SYSUTCDATETIME())"
                                        + " WHERE submission_id=?",
                                    sid);
                            return null;
                        });
                JsonNode plan = Json.MAPPER.valueToTree(tutor.startTutor(student, sid));
                assertEquals(1, plan.path("aiSteps").size());
                assertFalse(Json.stringify(plan).contains("expectedAnswer"));
                assertEquals(
                        409,
                        assertThrows(
                                        ApiException.class,
                                        () ->
                                                tutor.nextTutorStep(
                                                        student,
                                                        sid,
                                                        Json.object().put("stepNumber", 1)))
                                .status);
                JsonNode checked =
                        Json.MAPPER.valueToTree(
                                tutor.checkTutorStep(
                                        student,
                                        sid,
                                        Json.object().put("stepNumber", 1).put("answer", "1+x")));
                assertTrue(checked.path("aiSteps").path(0).path("correct").asBoolean());
                tutor.nextTutorStep(student, sid, Json.object().put("stepNumber", 1));
                assertEquals(
                        409,
                        assertThrows(
                                        ApiException.class,
                                        () ->
                                                essays.retryEssay(
                                                        student,
                                                        sid,
                                                        Json.object()
                                                                .set(
                                                                        "content",
                                                                        Json.object()
                                                                                .put(
                                                                                        "image",
                                                                                        SecurityTest
                                                                                                .png())
                                                                                .put(
                                                                                        "imageHash",
                                                                                        "forged"))))
                                .status);
                var image =
                        new java.awt.image.BufferedImage(
                                2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
                image.setRGB(0, 0, 0x00ff00);
                var bytes = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(image, "png", bytes);
                String newImage =
                        "data:image/png;base64,"
                                + Base64.getEncoder().encodeToString(bytes.toByteArray());
                JsonNode completed =
                        Json.MAPPER.valueToTree(
                                essays.retryEssay(
                                        student,
                                        sid,
                                        Json.object()
                                                .set(
                                                        "content",
                                                        Json.object()
                                                                .put("text", "1+x")
                                                                .put("image", newImage))));
                assertEquals("COMPLETED", completed.path("status").asText());
                assertEquals(1, completed.path("retrySubmissions").size());
                assertEquals(3, calls.get());
                assertEquals(
                        1,
                        ((Number)
                                        db.read(
                                                        c ->
                                                                Database.one(
                                                                        c,
                                                                        "SELECT COUNT(*) AS n FROM"
                                                                            + " tutor_sessions"
                                                                            + " WHERE"
                                                                            + " submission_id=?",
                                                                        sid))
                                                .get("n"))
                                .intValue());
            } finally {
                release.countDown();
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
                if (ids[1] > 0)
                    db.transaction(
                            c -> {
                                for (String table :
                                        List.of(
                                                "idempotency_keys",
                                                "ai_request_logs",
                                                "audit_logs"))
                                    Database.update(
                                            c,
                                            "DELETE FROM " + table + " WHERE actor_id IN (?,?)",
                                            ids[0],
                                            ids[1]);
                                Database.update(
                                        c,
                                        "DELETE FROM learning_history WHERE student_id=?",
                                        ids[1]);
                                Database.update(
                                        c,
                                        "DELETE a FROM tutor_step_attempts a JOIN tutor_steps t ON"
                                                + " t.id=a.step_id JOIN tutor_sessions s ON"
                                                + " s.id=t.session_id JOIN essay_submissions e ON"
                                                + " e.id=s.submission_id WHERE e.student_id=?",
                                        ids[1]);
                                Database.update(
                                        c,
                                        "DELETE t FROM tutor_steps t JOIN tutor_sessions s ON"
                                                + " s.id=t.session_id JOIN essay_submissions e ON"
                                                + " e.id=s.submission_id WHERE e.student_id=?",
                                        ids[1]);
                                for (String table :
                                        List.of(
                                                "tutor_sessions",
                                                "essay_retry_submissions",
                                                "essay_evaluations",
                                                "essay_ai_jobs",
                                                "essay_ai_job_attempts",
                                                "submission_files"))
                                    Database.update(
                                            c,
                                            "DELETE t FROM "
                                                    + table
                                                    + " t JOIN essay_submissions e ON"
                                                    + " e.id=t.submission_id WHERE e.student_id=?",
                                            ids[1]);
                                Database.update(
                                        c,
                                        "DELETE FROM essay_submissions WHERE student_id=?",
                                        ids[1]);
                                Database.update(c, "DELETE FROM questions WHERE id=?", ids[5]);
                                Database.update(
                                        c,
                                        "DELETE FROM course_enrollments WHERE course_id=?",
                                        ids[4]);
                                Database.update(c, "DELETE FROM courses WHERE id=?", ids[4]);
                                Database.update(c, "DELETE FROM topics WHERE chapter_id=?", ids[3]);
                                Database.update(c, "DELETE FROM chapters WHERE id=?", ids[3]);
                                Database.update(c, "DELETE FROM subjects WHERE id=?", ids[2]);
                                Database.update(
                                        c, "DELETE FROM users WHERE id IN (?,?)", ids[0], ids[1]);
                                return null;
                            });
            }
        } finally {
            if (old == null) System.clearProperty("STORAGE_DIR");
            else System.setProperty("STORAGE_DIR", old);
        }
    }
}
