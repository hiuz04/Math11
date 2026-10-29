package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mathio.lms.DAO.*;
import com.mathio.lms.model.*;

import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.sql.Connection;
import java.util.*;

import javax.sql.DataSource;

class MixedExamTest {
    Connection c;
    Database db;
    MockedStatic<Database> sql;
    ExamService exams;
    Actor student = new Actor(1, "STUDENT", 1), teacher = new Actor(2, "TEACHER", 1);
    String status = "IN_PROGRESS";
    boolean published = false, teacherAllowed = true;
    long elapsed = 60;
    int resultWrites, historyWrites, auditWrites;
    Map<String, String> mcq = new HashMap<>();
    Map<Long, Map<String, Object>> essay = new LinkedHashMap<>();
    Map<String, Object> result;
    List<Map<String, Object>> questions = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        c = mock(Connection.class);
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenReturn(c);
        db = new Database(ds);
        exams = new ExamService(db, new QuestionDAO(db));
        ObjectNode initial = ServiceTest.question();
        questions.add(Map.of("question_id", 1L, "snapshot", Json.stringify(initial)));
        for (long id : new long[] {2, 3}) {
            ObjectNode q = ServiceTest.question();
            q.put("id", id);
            q.put("type", "ESSAY");
            q.remove("options");
            questions.add(Map.of("question_id", id, "snapshot", Json.stringify(q)));
        }
        sql = mockStatic(Database.class);
        sql.when(() -> Database.one(any(), anyString(), any(Object[].class)))
                .thenAnswer(
                        inv -> {
                            String query =
                                    ((String) inv.getArgument(1)).replaceAll("\\s+", " ").trim();
                            Object[] args = (Object[]) inv.getRawArguments()[2];
                            if (query.contains("SELECT e.id FROM exams e"))
                                return teacherAllowed ? Map.of("id", 9L) : null;
                            if (query.startsWith("SELECT a.*"))
                                return Map.of(
                                        "status", status, "exam_id", 9L, "duration_seconds", 1800);
                            if (query.contains("DATEDIFF")) return Map.of("seconds", elapsed);
                            if (query.contains("FROM exam_attempts"))
                                return Map.of(
                                        "id",
                                        5L,
                                        "examId",
                                        9L,
                                        "studentId",
                                        1L,
                                        "status",
                                        status,
                                        "startedAt",
                                        "2026-01-01T00:00:00Z",
                                        "elapsedSeconds",
                                        elapsed,
                                        "exam_id",
                                        9L,
                                        "student_id",
                                        1L,
                                        "elapsed_seconds",
                                        elapsed);
                            if (query.contains("FROM exam_essay_answers"))
                                return essay.get(((Number) args[1]).longValue());
                            if (query.contains("FROM exam_answers")) {
                                String value = mcq.get(args[1].toString());
                                return value == null ? null : Map.of("answer", value);
                            }
                            if (query.contains("FROM exam_results")) return result;
                            if (query.startsWith("SELECT author_id"))
                                return Map.of("author_id", 2L);
                            if (query.contains("FROM exams"))
                                return Map.of(
                                        "id",
                                        9L,
                                        "authorId",
                                        2L,
                                        "title",
                                        "Mixed",
                                        "matrix",
                                        "{}",
                                        "durationSeconds",
                                        1800,
                                        "results_published",
                                        published);
                            return null;
                        });
        sql.when(() -> Database.rows(any(), anyString(), any(Object[].class)))
                .thenAnswer(
                        inv -> {
                            String query = inv.getArgument(1);
                            if (query.contains("FROM exam_questions")) return questions;
                            if (query.contains("FROM exam_answers"))
                                return mcq.entrySet().stream()
                                        .map(
                                                e ->
                                                        Map.<String, Object>of(
                                                                "question_id",
                                                                Long.parseLong(e.getKey()),
                                                                "answer",
                                                                e.getValue()))
                                        .toList();
                            if (query.contains("FROM exam_essay_answers"))
                                return essay.entrySet().stream()
                                        .map(
                                                e -> {
                                                    var r = new HashMap<>(e.getValue());
                                                    r.put("question_id", e.getKey());
                                                    return r;
                                                })
                                        .toList();
                            return List.of();
                        });
        sql.when(() -> Database.insert(any(), anyString(), any(Object[].class))).thenReturn(5L);
        sql.when(() -> Database.update(any(), anyString(), any(Object[].class)))
                .thenAnswer(
                        inv -> {
                            String q = ((String) inv.getArgument(1)).replaceAll("\\s+", " ").trim();
                            Object[] a = (Object[]) inv.getRawArguments()[2];
                            if (q.startsWith("UPDATE exam_answers SET answer"))
                                mcq.put(a[2].toString(), a[0].toString());
                            if (q.startsWith("UPDATE exam_essay_answers SET content_json")) {
                                var old = essay.get(((Number) a[2]).longValue());
                                if (old == null) return 0;
                                old.put("content_json", a[0]);
                            }
                            if (q.startsWith("INSERT exam_essay_answers")) {
                                long id = ((Number) a[1]).longValue();
                                var r = new HashMap<String, Object>();
                                r.put("id", id);
                                r.put("content_json", a.length > 2 ? a[2] : "{}");
                                essay.put(id, r);
                            }
                            if (q.startsWith("UPDATE exam_essay_answers SET score")) {
                                var r = essay.get(((Number) a[4]).longValue());
                                r.put("score", a[0]);
                                r.put("feedback", a[1]);
                                r.put("gradedBy", a[2]);
                                r.put("gradedAt", "2026-01-01T00:00:00Z");
                            }
                            if (q.startsWith("UPDATE exam_attempts SET status"))
                                status =
                                        q.contains("PENDING_GRADING")
                                                ? "PENDING_GRADING"
                                                : q.contains("GRADED") ? "GRADED" : "SUBMITTED";
                            if (q.startsWith("UPDATE exams SET results_published"))
                                published = true;
                            if (q.startsWith("INSERT exam_results")) {
                                resultWrites++;
                                result =
                                        Map.of(
                                                "score",
                                                a[1],
                                                "correctCount",
                                                a[2],
                                                "totalCount",
                                                a[3],
                                                "review",
                                                a[4]);
                            }
                            if (q.startsWith("INSERT learning_history")) historyWrites++;
                            if (q.startsWith("INSERT exam_grade_audit")) auditWrites++;
                            return 1;
                        });
    }

    @AfterEach
    void close() {
        sql.close();
    }

    @Test
    void mixedAttemptPersistsBothKindsWaitsForAllGradesThenPublishesOneResult() {
        JsonNode open = Json.MAPPER.valueToTree(exams.startAttempt(student, 9));
        assertEquals(3, open.path("questions").size());
        assertFalse(open.path("questions").path(1).has("answer"));
        JsonNode pending =
                Json.MAPPER.valueToTree(
                        exams.saveAnswers(
                                student,
                                5,
                                Json.parse(
                                        "{\"answers\":{\"1\":\"2\"},\"essayAnswers\":{\"2\":{\"text\":\"Bài"
                                            + " làm 2\"},\"3\":{\"content\":{\"text\":\"Bài làm"
                                            + " 3\"}}},\"score\":10}"),
                                true));
        assertEquals("PENDING_GRADING", pending.path("status").asText());
        assertFalse(pending.has("result"));
        assertEquals(
                "Bài làm 2",
                pending.path("essayAnswers").path("2").path("content").path("text").asText());
        exams.saveAnswers(student, 5, Json.parse("{\"answers\":{\"1\":\"1\"}}"), true);
        assertEquals("2", mcq.get("1"));
        assertEquals(0, resultWrites);
        exams.gradeAttempt(
                teacher,
                5,
                Json.parse(
                        "{\"grades\":[{\"questionId\":2,\"score\":8,\"feedback\":\"Thiếu"
                                + " bước\"}]}"));
        assertEquals("PENDING_GRADING", status);
        assertEquals(0, resultWrites);
        JsonNode finalStaff =
                Json.MAPPER.valueToTree(
                        exams.gradeAttempt(
                                teacher,
                                5,
                                Json.parse(
                                        "{\"grades\":[{\"questionId\":3,\"score\":6,\"feedback\":\"Cần"
                                            + " giải thích\"}]}")));
        assertEquals("GRADED", status);
        assertEquals(8, finalStaff.path("result").path("score").asDouble());
        assertEquals(1, resultWrites);
        assertEquals(1, historyWrites);
        assertEquals(2, auditWrites);
        JsonNode hidden = Json.MAPPER.valueToTree(exams.getAttemptById(student, 5));
        assertFalse(hidden.has("result"));
        assertFalse(hidden.path("essayAnswers").path("2").has("score"));
        exams.publishResults(teacher, 9);
        JsonNode shown = Json.MAPPER.valueToTree(exams.getAttemptById(student, 5));
        assertEquals(8, shown.path("result").path("score").asDouble());
        assertEquals(
                409,
                assertThrows(
                                ApiException.class,
                                () -> exams.gradeAttempt(teacher, 5, Json.parse("{\"grades\":[]}")))
                        .status);
    }

    @Test
    void essayOnlyExamIsRunnableAndUsesTheSameTenPointRule() {
        questions.remove(0);
        questions.remove(1);
        exams.startAttempt(student, 9);
        exams.saveAnswers(
                student,
                5,
                Json.parse("{\"answers\":{},\"essayAnswers\":{\"2\":{\"text\":\"solution\"}}}"),
                true);
        JsonNode graded =
                Json.MAPPER.valueToTree(
                        exams.gradeAttempt(
                                teacher,
                                5,
                                Json.parse(
                                        "{\"grades\":[{\"questionId\":2,\"score\":7,\"feedback\":\"Đã"
                                            + " chấm\"}]}")));
        assertEquals(7, graded.path("result").path("score").asDouble());
    }

    @Test
    void expiredSubmissionDoesNotAcceptLateAnswersAndWrongTeacherCannotGrade() {
        elapsed = 1800;
        exams.saveAnswers(
                student,
                5,
                Json.parse(
                        "{\"answers\":{\"1\":\"2\"},\"essayAnswers\":{\"2\":{\"text\":\"late\"}}}"),
                true);
        assertTrue(mcq.isEmpty());
        assertTrue(essay.isEmpty());
        teacherAllowed = false;
        assertEquals(
                403,
                assertThrows(
                                ApiException.class,
                                () ->
                                        exams.gradeAttempt(
                                                teacher,
                                                5,
                                                Json.parse(
                                                        "{\"grades\":[{\"questionId\":2,\"score\":10,\"feedback\":\"forged\"}]}")))
                        .status);
        assertEquals(0, auditWrites);
    }
}
