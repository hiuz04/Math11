package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mathio.lms.DAO.*;
import com.mathio.lms.model.*;

import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import javax.sql.DataSource;

/** Concurrency fixtures exercise service/transaction boundaries, not SQL Server's lock engine. */
class AiConcurrencyTest {
    static class Fixture {
        final ReentrantLock lock = new ReentrantLock();
        final AtomicInteger borrowed = new AtomicInteger(), calls = new AtomicInteger();
        final Map<String, Object> row = new HashMap<>(), job = new HashMap<>();
        final Actor student = new Actor(1, "STUDENT", 1);
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final Database db;
        final AiService ai = mock(AiService.class);
        final QuestionDAO questions = mock(QuestionDAO.class);
        final EssayService essays;
        final TutorService tutor;

        Fixture(String status) throws Exception {
            DataSource ds = mock(DataSource.class);
            when(ds.getConnection())
                    .thenAnswer(
                            inv -> {
                                borrowed.incrementAndGet();
                                Connection c = mock(Connection.class);
                                doAnswer(
                                                x -> {
                                                    boolean auto = x.getArgument(0);
                                                    if (!auto) lock.lock();
                                                    return null;
                                                })
                                        .when(c)
                                        .setAutoCommit(anyBoolean());
                                doAnswer(
                                                x -> {
                                                    lock.unlock();
                                                    return null;
                                                })
                                        .when(c)
                                        .commit();
                                doAnswer(
                                                x -> {
                                                    lock.unlock();
                                                    return null;
                                                })
                                        .when(c)
                                        .rollback();
                                doAnswer(
                                                x -> {
                                                    borrowed.decrementAndGet();
                                                    return null;
                                                })
                                        .when(c)
                                        .close();
                                return c;
                            });
            db = new Database(ds);
            ObjectNode state =
                    Json.object()
                            .put("id", 5)
                            .put("studentId", 1)
                            .put("questionId", 1)
                            .put("status", status)
                            .put("startedAt", "2026-01-01T00:00:00Z")
                            .put("currentStep", 0);
            state.set("question", ServiceTest.question());
            state.set("originalSubmission", Json.object().put("text", "wrong"));
            state.set("aiSteps", Json.array());
            state.set("retrySubmissions", Json.array());
            state.set("studentStepAnswers", Json.array());
            row.put("student_id", 1L);
            row.put("question_id", 1L);
            row.put("revision", 0L);
            row.put("state_json", Json.stringify(state));
            when(questions.getQuestionReference(any(), eq(1L))).thenReturn(ServiceTest.question());
            essays = new EssayService(db, questions, ai, new ImageStorage());
            tutor = new TutorService(db, questions, ai, essays);
            when(ai.generateResponse(any(), anyString(), anyString(), nullable(String.class)))
                    .thenAnswer(
                            inv -> {
                                db.requireNoTransaction();
                                assertEquals(
                                        0,
                                        borrowed.get(),
                                        "No JDBC connection may be held at the HTTP boundary");
                                calls.incrementAndGet();
                                entered.countDown();
                                assertTrue(release.await(10, TimeUnit.SECONDS));
                                return inv.getArgument(1).equals("stepTutor")
                                        ? Json.parse(
                                                "{\"steps\":[{\"instruction\":\"Rút gọn biểu"
                                                    + " thức.\",\"expectedAnswer\":\"x+1\",\"hint\":\"Gom"
                                                    + " các hạng tử.\"}]}")
                                        : Json.parse(
                                                "{\"correct\":false,\"confident\":true,\"feedback\":\"Kiểm"
                                                    + " tra lại phép tính.\",\"score\":3}");
                            });
        }

        <T> T invoke(Supplier<T> action) {
            try (MockedStatic<Database> sql = mockStatic(Database.class)) {
                sql.when(() -> Database.one(any(), anyString(), any(Object[].class)))
                        .thenAnswer(
                                inv -> {
                                    String query = inv.getArgument(1);
                                    if (query.contains("FROM essay_submissions")) return row;
                                    if (query.contains("FROM essay_ai_jobs"))
                                        return job.isEmpty() ? null : new HashMap<>(job);
                                    return null;
                                });
                sql.when(() -> Database.insert(any(), anyString(), any(Object[].class)))
                        .thenReturn(10L);
                sql.when(() -> Database.update(any(), anyString(), any(Object[].class)))
                        .thenAnswer(
                                inv -> {
                                    String q =
                                            ((String) inv.getArgument(1))
                                                    .replaceAll("\\s+", " ")
                                                    .trim();
                                    Object[] args = (Object[]) inv.getRawArguments()[2];
                                    if (q.startsWith("INSERT essay_ai_jobs")) {
                                        job.put("kind", args[1]);
                                        job.put("token", args[2]);
                                        job.put("status", "PROCESSING");
                                        job.put("live", 1);
                                        job.put("fingerprint", args[4]);
                                        job.put("content_json", args[5]);
                                    }
                                    if (q.startsWith("UPDATE essay_ai_jobs SET kind")) {
                                        job.put("kind", args[0]);
                                        job.put("token", args[1]);
                                        job.put("status", "PROCESSING");
                                        job.put("live", 1);
                                        job.put("fingerprint", args[3]);
                                        job.put("content_json", args[4]);
                                    }
                                    if (q.startsWith("UPDATE essay_ai_jobs SET status")
                                            && Objects.equals(args[2], job.get("token")))
                                        job.put("status", args[0]);
                                    if (q.startsWith("UPDATE essay_submissions SET state_json")) {
                                        row.put("state_json", args[0]);
                                        row.put("status", args[1]);
                                        row.put(
                                                "revision",
                                                ((Number) row.get("revision")).longValue() + 1);
                                    }
                                    if (q.startsWith("UPDATE essay_submissions SET private_plan"))
                                        row.put("private_plan", args[0]);
                                    return 1;
                                });
                return action.get();
            }
        }
    }

    @Test
    void twentyConcurrentEvaluationsHaveOneHttpCallAndNoBorrowedConnection() throws Exception {
        concurrent(false);
    }

    @Test
    void twentyConcurrentTutorStartsCreateOnePlanAndNoBorrowedConnection() throws Exception {
        concurrent(true);
    }

    private void concurrent(boolean tutor) throws Exception {
        Fixture f = new Fixture(tutor ? "NEEDS_TUTOR" : "SUBMITTED");
        ExecutorService pool = Executors.newFixedThreadPool(20);
        Supplier<Object> action =
                () ->
                        tutor
                                ? f.tutor.startTutor(f.student, 5)
                                : f.essays.evaluateEssay(f.student, 5);
        try {
            Future<Object> first = pool.submit(() -> f.invoke(action));
            assertTrue(f.entered.await(5, TimeUnit.SECONDS));
            List<Future<Integer>> others = new ArrayList<>();
            for (int i = 0; i < 19; i++)
                others.add(
                        pool.submit(
                                () ->
                                        f.invoke(
                                                () ->
                                                        assertThrows(
                                                                        ApiException.class,
                                                                        action::get)
                                                                .status)));
            for (var other : others) assertEquals(409, other.get(5, TimeUnit.SECONDS));
            f.release.countDown();
            assertNotNull(first.get(5, TimeUnit.SECONDS));
            assertEquals(1, f.calls.get());
            assertNotNull(f.invoke(action));
            assertEquals(1, f.calls.get());
            assertEquals(0, f.borrowed.get());
        } finally {
            f.release.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void expiredLeaseReplacesTokenAndRejectsStaleResult() throws Exception {
        Fixture f = new Fixture("SUBMITTED");
        f.job.putAll(
                Map.of("kind", "EVALUATE", "token", "stale", "status", "PROCESSING", "live", 0));
        f.invoke(
                () ->
                        f.db.transaction(
                                c -> {
                                    AiJobDAO jobs = new AiJobDAO();
                                    var fresh =
                                            jobs.reserve(
                                                    c, 5, "EVALUATE", 0, "original", Json.object());
                                    assertNotEquals("stale", fresh.token());
                                    assertEquals(
                                            409,
                                            assertThrows(
                                                            ApiException.class,
                                                            () ->
                                                                    jobs.validate(
                                                                            c,
                                                                            new AiJobDAO.Job(
                                                                                    5,
                                                                                    "EVALUATE",
                                                                                    "stale",
                                                                                    0,
                                                                                    "original",
                                                                                    Json.object()),
                                                                            0))
                                                    .status);
                                    assertDoesNotThrow(() -> jobs.validate(c, fresh, 0));
                                    return null;
                                }));
    }

    @Test
    void geminiFailureLeavesRetryableReservationAndNeverCompletesSubmission() throws Exception {
        Fixture f = new Fixture("SUBMITTED");
        doThrow(new ApiException(502, "Invalid JSON"))
                .when(f.ai)
                .generateResponse(any(), anyString(), anyString(), nullable(String.class));
        assertEquals(
                502,
                f.invoke(
                                () ->
                                        assertThrows(
                                                ApiException.class,
                                                () -> f.essays.evaluateEssay(f.student, 5)))
                        .status);
        assertEquals("FAILED", f.job.get("status"));
        assertEquals(
                "SUBMITTED",
                Json.parse(f.row.get("state_json").toString()).path("status").asText());
    }

    @Test
    void unknownMathIsRecordedAsUnverifiedAndCannotUnlockNextStep() throws Exception {
        Fixture f = new Fixture("NEEDS_TUTOR");
        f.release.countDown();
        f.invoke(() -> f.tutor.startTutor(f.student, 5));
        var state =
                Json.MAPPER.valueToTree(
                        f.invoke(
                                () ->
                                        f.tutor.checkTutorStep(
                                                f.student,
                                                5,
                                                Json.object()
                                                        .put("stepNumber", 1)
                                                        .put("answer", "sin(x)"))));
        var attempt = state.path("aiSteps").path(0).path("attempts").path(0);
        assertTrue(attempt.path("correct").isNull());
        assertEquals("UNKNOWN", attempt.path("verification").asText());
        assertEquals(
                409,
                f.invoke(
                        () ->
                                assertThrows(
                                                ApiException.class,
                                                () ->
                                                        f.tutor.nextTutorStep(
                                                                f.student,
                                                                5,
                                                                Json.object().put("stepNumber", 1)))
                                        .status));
    }

    @Test
    void referencesAreFrozenAtSubmissionAndUncertainEvaluationHasNoScore() throws Exception {
        Fixture f = new Fixture("SUBMITTED");
        f.row.put(
                "reference_json", Json.stringify(Json.object().put("answer", "private-original")));
        doAnswer(
                        inv -> {
                            f.db.requireNoTransaction();
                            assertTrue(((String) inv.getArgument(2)).contains("private-original"));
                            return Json.parse(
                                    "{\"correct\":true,\"confident\":false,\"feedback\":\"Ảnh chưa"
                                            + " đủ rõ\",\"score\":10}");
                        })
                .when(f.ai)
                .generateResponse(any(), anyString(), anyString(), nullable(String.class));
        var state = Json.MAPPER.valueToTree(f.invoke(() -> f.essays.evaluateEssay(f.student, 5)));
        assertEquals("NEEDS_REVISION", state.path("status").asText());
        assertFalse(state.path("finalResult").has("score"));
        assertFalse(state.path("finalResult").path("correct").asBoolean());
    }
}
