package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mathio.lms.DAO.Database;
import com.mathio.lms.DAO.QuestionDAO;
import com.mathio.lms.DAO.ReportingDAO;
import com.mathio.lms.model.Actor;
import com.mathio.lms.model.AiService;
import com.mathio.lms.model.ApiException;
import com.mathio.lms.model.EssayService;
import com.mathio.lms.model.ExamService;
import com.mathio.lms.model.ImageStorage;
import com.mathio.lms.model.Json;

import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

class QuizAndImageTest {
    Connection c;
    Database db;
    MockedStatic<Database> sql;

    @BeforeEach
    void setup() throws Exception {
        c = mock(Connection.class);
        DataSource source = mock(DataSource.class);
        when(source.getConnection()).thenReturn(c);
        db = new Database(source);
        sql = mockStatic(Database.class);
    }

    @AfterEach
    void close() {
        sql.close();
    }

    @Test
    void quizGradesSavedOptionsOnServerAndDuplicateSubmitCannotAwardTwice() throws Exception {
        Map<String, String> answers = new HashMap<>();
        Map<String, Object> result = new HashMap<>();
        String[] status = {"IN_PROGRESS"};
        int[] resultWrites = {0};
        ObjectNode first = ServiceTest.question(), second = ServiceTest.question();
        second.put("id", 2);
        second.put("title", "Second");
        second.put("answer", "1");
        List<Map<String, Object>> snapshots =
                List.of(
                        Map.of("question_id", 1L, "snapshot", Json.stringify(first)),
                        Map.of("question_id", 2L, "snapshot", Json.stringify(second)));
        sql.when(() -> Database.rows(any(), anyString(), any(Object[].class)))
                .thenAnswer(
                        inv -> {
                            String query =
                                    ((String) inv.getArgument(1)).replaceAll("\\s+", " ").trim();
                            if (query.contains("FROM exam_questions")) return snapshots;
                            if (query.contains("FROM exam_answers"))
                                return answers.entrySet().stream()
                                        .map(
                                                e ->
                                                        Map.<String, Object>of(
                                                                "question_id",
                                                                Long.parseLong(e.getKey()),
                                                                "answer",
                                                                e.getValue()))
                                        .toList();
                            return List.of();
                        });
        sql.when(() -> Database.one(any(), anyString(), any(Object[].class)))
                .thenAnswer(
                        inv -> {
                            String query =
                                    ((String) inv.getArgument(1)).replaceAll("\\s+", " ").trim();
                            Object[] args = (Object[]) inv.getRawArguments()[2];
                            if (query.contains("SELECT a.*"))
                                return Map.of(
                                        "status",
                                        status[0],
                                        "exam_id",
                                        9L,
                                        "duration_seconds",
                                        1800);
                            if (query.contains("DATEDIFF")) return Map.of("seconds", 60);
                            if (query.contains("FROM exam_attempts"))
                                return Map.of(
                                        "id",
                                        5L,
                                        "examId",
                                        9L,
                                        "studentId",
                                        1L,
                                        "status",
                                        status[0],
                                        "startedAt",
                                        "2026-01-01T00:00:00Z",
                                        "elapsedSeconds",
                                        60);
                            if (query.contains("FROM exam_answers")) {
                                String value = answers.get(args[1].toString());
                                return value == null ? null : Map.of("answer", value);
                            }
                            if (query.contains("FROM exam_results"))
                                return result.isEmpty() ? null : result;
                            if (query.contains("FROM exams"))
                                return Map.of(
                                        "title",
                                        "Real quiz",
                                        "durationSeconds",
                                        1800,
                                        "results_published",
                                        true);
                            return null;
                        });
        sql.when(() -> Database.update(any(), anyString(), any(Object[].class)))
                .thenAnswer(
                        inv -> {
                            String query =
                                    ((String) inv.getArgument(1)).replaceAll("\\s+", " ").trim();
                            Object[] args = (Object[]) inv.getRawArguments()[2];
                            if (query.startsWith("UPDATE exam_answers SET answer"))
                                answers.put(args[2].toString(), args[0].toString());
                            if (query.startsWith("UPDATE exam_attempts SET status"))
                                status[0] = "SUBMITTED";
                            if (query.startsWith("INSERT exam_results")) {
                                resultWrites[0]++;
                                result.put("score", args[1]);
                                result.put("correctCount", args[2]);
                                result.put("totalCount", args[3]);
                                result.put("review", args[4]);
                            }
                            return 1;
                        });
        ExamService service = new ExamService(db, new QuestionDAO(db));
        Actor a = new Actor(1, "STUDENT", 1);
        JsonNode open = (JsonNode) service.getAttemptById(a, 5);
        assertFalse(open.path("questions").path(0).has("answer"));
        assertFalse(open.has("result"));
        assertThrows(
                ApiException.class,
                () ->
                        service.saveAnswers(
                                a, 5, Json.parse("{\"answers\":{\"999\":\"2\"}}"), false));
        JsonNode graded =
                (JsonNode)
                        service.saveAnswers(
                                a,
                                5,
                                Json.parse(
                                        "{\"answers\":{\"1\":\"2\",\"2\":\"2\"},\"score\":10,\"correctCount\":2}"),
                                true);
        assertEquals(5, graded.path("result").path("score").asDouble());
        assertEquals(1, graded.path("result").path("correctCount").asInt());
        assertEquals("1", graded.path("result").path("review").path(1).path("answer").asText());
        service.saveAnswers(a, 5, Json.parse("{\"answers\":{}}"), true);
        assertEquals(1, resultWrites[0]);
    }

    @Test
    void expiredAttemptRejectsSavingAndGradesOnlyPreviouslyPersistedAnswers() throws Exception {
        var snapshot = ServiceTest.question();
        sql.when(() -> Database.rows(any(), anyString(), any(Object[].class))).thenAnswer(inv -> {
            String query = inv.getArgument(1);
            return query.contains("FROM exam_questions") ? List.of(Map.of("question_id", 1L, "snapshot", Json.stringify(snapshot))) : List.of();
        });
        sql.when(() -> Database.one(any(), anyString(), any(Object[].class))).thenAnswer(inv -> {
            String query = inv.getArgument(1);
            if (query.contains("SELECT a.*")) return Map.of("status", "IN_PROGRESS", "exam_id", 9L, "duration_seconds", 60);
            if (query.contains("DATEDIFF")) return Map.of("seconds", 90);
            if (query.contains("FROM exam_attempts")) return Map.of("id", 5L, "examId", 9L, "studentId", 1L, "status", "SUBMITTED", "startedAt", "2026-01-01T00:00:00Z", "elapsedSeconds", 90);
            if (query.contains("FROM exams")) return Map.of("title", "Expired quiz", "durationSeconds", 60);
            return null;
        });
        ExamService service = new ExamService(db, new QuestionDAO(db));
        Actor student = new Actor(1, "STUDENT", 1);
        var input = Json.parse("{\"answers\":{\"1\":\"2\"},\"score\":10}");
        var error = assertThrows(ApiException.class, () -> service.saveAnswers(student, 5, input, false));
        assertEquals(409, error.status);
        service.saveAnswers(student, 5, input, true);
        sql.verify(() -> Database.update(eq(c), contains("UPDATE exam_answers SET answer"), any(Object[].class)), never());
        sql.verify(() -> Database.update(eq(c), contains("INSERT exam_results"), any(Object[].class)));
    }

    @Test
    void retryComputesHashAndRejectsOriginalEvenWhenClientLies() throws Exception {
        String data = SecurityTest.png(), hash = ImageStorage.decode(data).hash();
        ObjectNode s = Json.object();
        s.put("status", "RETRY_REQUIRED");
        s.put("studentId", 1);
        s.put("questionId", 1);
        s.put("id", 5);
        s.put("startedAt", "2026-01-01T00:00:00Z");
        s.set("retrySubmissions", Json.array());
        sql.when(() -> Database.one(any(), anyString(), any(Object[].class)))
                .thenAnswer(
                        inv -> {
                            String query =
                                    ((String) inv.getArgument(1)).replaceAll("\\s+", " ").trim();
                            Object[] args = (Object[]) inv.getRawArguments()[2];
                            if (query.contains("FROM essay_submissions"))
                                return Map.of("student_id", 1L, "state_json", Json.stringify(s));
                            if (query.contains("FROM submission_files")) {
                                assertEquals(hash, args[1]);
                                return Map.of("id", 1L);
                            }
                            return null;
                        });
        AiService ai = mock(AiService.class);
        EssayService service =
                new EssayService(db, mock(QuestionDAO.class), ai, new ImageStorage());
        ObjectNode request = Json.object();
        request.set(
                "content",
                Json.object().put("image", data).put("imageHash", "forged-different-client-hash"));
        assertEquals(
                409,
                assertThrows(
                                ApiException.class,
                                () -> service.retryEssay(new Actor(1, "STUDENT", 1), 5, request))
                        .status);
        verifyNoInteractions(ai);
        verify(c).rollback();
    }

    @Test
    void historyOnlyReadsOwnedRecordsAndParsesSafeDetails() throws Exception {
        sql.when(() -> Database.rows(any(), anyString(), any(Object[].class)))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "id",
                                        7L,
                                        "title",
                                        "Quiz",
                                        "score",
                                        5.0,
                                        "detail",
                                        "{\"prompt\":\"Question\",\"answer\":\"2\",\"feedback\":\"Saved\"}")));
        ReportingDAO reporting = new ReportingDAO(db);
        JsonNode history = (JsonNode) reporting.getLearningHistory(new Actor(1, "STUDENT", 1), 0);
        assertEquals("7", history.path(0).path("id").asText());
        assertEquals("Saved", history.path(0).path("detail").path("feedback").asText());
        assertThrows(
                ApiException.class,
                () -> reporting.getLearningHistory(new Actor(1, "STUDENT", 1), 2));
    }
}
