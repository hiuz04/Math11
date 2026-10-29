package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mathio.lms.DAO.AccessDAO;
import com.mathio.lms.DAO.Database;
import com.mathio.lms.DAO.QuestionDAO;
import com.mathio.lms.DAO.UserDAO;
import com.mathio.lms.model.Actor;
import com.mathio.lms.model.AiService;
import com.mathio.lms.model.ApiException;
import com.mathio.lms.model.AuthService;
import com.mathio.lms.model.EssayService;
import com.mathio.lms.model.ExamService;
import com.mathio.lms.model.IdempotencyService;
import com.mathio.lms.model.ImageStorage;
import com.mathio.lms.model.Json;
import com.mathio.lms.model.StoredResponse;
import com.mathio.lms.model.TutorService;

import org.junit.jupiter.api.*;
import org.mindrot.jbcrypt.BCrypt;
import org.mockito.MockedStatic;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

class ServiceTest {
    Connection c;
    Database db;
    MockedStatic<Database> sql;

    @BeforeEach
    void setup() throws Exception {
        c = mock(Connection.class);
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenReturn(c);
        db = new Database(ds);
        sql = mockStatic(Database.class);
        sql.when(() -> Database.update(any(), anyString(), any(Object[].class))).thenReturn(1);
    }

    @AfterEach
    void close() {
        sql.close();
    }

    @Test
    void authenticationUsesDatabaseBcryptAndNeverReturnsHash() throws Exception {
        String hash = BCrypt.hashpw("Password123!", BCrypt.gensalt(4));
        sql.when(() -> Database.one(any(), anyString(), any(Object[].class)))
                .thenReturn(
                        Map.of(
                                "id",
                                1L,
                                "role",
                                "STUDENT",
                                "password_hash",
                                hash,
                                "active",
                                true,
                                "session_version",
                                1));
        AuthService auth = new AuthService(db);
        Actor actor =
                auth.login(
                        Json.parse(
                                "{\"email\":\"student@test.vn\",\"password\":\"Password123!\",\"role\":\"ADMIN\"}"));
        assertEquals("STUDENT", actor.getRole());
        assertEquals(
                401,
                assertThrows(
                                ApiException.class,
                                () ->
                                        auth.login(
                                                Json.parse(
                                                        "{\"email\":\"student@test.vn\",\"password\":\"wrong\"}")))
                        .status);
        assertFalse(Json.stringify(actor).contains(hash));
    }

    @Test
    void inactiveAndMissingAccountFailAuthentication() throws Exception {
        sql.when(() -> Database.one(any(), anyString(), any(Object[].class))).thenReturn(null);
        assertEquals(
                401,
                assertThrows(
                                ApiException.class,
                                () ->
                                        new AuthService(db)
                                                .login(
                                                        Json.parse(
                                                                "{\"email\":\"nobody@test.vn\",\"password\":\"Password123!\"}")))
                        .status);
    }

    @Test
    void staleSessionsAreRevokedAfterRoleOrPasswordChanges() throws Exception {
        sql.when(() -> Database.one(any(), anyString(), any(Object[].class)))
                .thenReturn(Map.of("role", "STUDENT", "session_version", 2));
        assertEquals(
                401,
                assertThrows(
                                ApiException.class,
                                () ->
                                        new AuthService(db)
                                                .validateSession(new Actor(1, "STUDENT", 1)))
                        .status);
    }

    @Test
    void studentCannotReadAnotherStudentsData() throws Exception {
        sql.when(() -> Database.one(any(), anyString(), any(Object[].class))).thenReturn(null);
        assertEquals(
                403,
                assertThrows(
                                ApiException.class,
                                () ->
                                        AccessDAO.validateStudentAccess(
                                                c, new Actor(1, "STUDENT", 1), 2))
                        .status);
        assertEquals(
                403,
                assertThrows(
                                ApiException.class,
                                () ->
                                        AccessDAO.validateStudentAccess(
                                                c, new Actor(3, "TEACHER", 1), 2))
                        .status);
        assertDoesNotThrow(() -> AccessDAO.validateStudentAccess(c, new Actor(1, "STUDENT", 1), 1));
    }

    @Test
    void userCrudRequiresAdminAndPreventsSelfDeletion() {
        UserDAO users = new UserDAO(db);
        assertThrows(
                ApiException.class,
                () -> users.saveUser(new Actor(1, "TEACHER", 1), 0, Json.object()));
        assertThrows(ApiException.class, () -> users.deleteUser(new Actor(1, "ADMIN", 1), 1));
        assertThrows(ApiException.class, () -> users.getUsers(new Actor(1, "STUDENT", 1)));
    }

    @Test
    void createUserCreatesProfileAndHashesPassword() throws Exception {
        sql.when(() -> Database.one(any(), anyString(), any(Object[].class)))
                .thenAnswer(
                        inv ->
                                ((String) inv.getArgument(1)).contains("WHERE email")
                                        ? null
                                        : Map.of(
                                                "id",
                                                8L,
                                                "name",
                                                "Test",
                                                "email",
                                                "test@test.vn",
                                                "role",
                                                "STUDENT",
                                                "active",
                                                true));
        sql.when(() -> Database.insert(any(), anyString(), any(Object[].class)))
                .thenAnswer(
                        inv -> {
                            Object[] args = (Object[]) inv.getRawArguments()[2];
                            assertTrue(BCrypt.checkpw("Password123!", args[3].toString()));
                            return 8L;
                        });
        Object result =
                new UserDAO(db)
                        .saveUser(
                                new Actor(1, "ADMIN", 1),
                                0,
                                Json.parse(
                                        "{\"name\":\"Test\",\"email\":\"test@test.vn\",\"role\":\"STUDENT\",\"password\":\"Password123!\"}"));
        assertFalse(Json.stringify(result).contains("Password123!"));
        sql.verify(
                () ->
                        Database.update(
                                eq(c),
                                argThat(
                                        query ->
                                                query.replaceAll("\\s+", " ")
                                                        .trim()
                                                        .equals(
                                                                "INSERT user_profiles(user_id)"
                                                                        + " VALUES(?)")),
                                eq(8L)));
        verify(c).commit();
    }

    static ObjectNode question() {
        return (ObjectNode)
                Json.parse(
                        "{\"id\":1,\"title\":\"Test\",\"content\":\"Tính 1+1\",\"chapter\":\"Chương"
                            + " 1\",\"topic\":\"Toán\",\"type\":\"MCQ\",\"difficulty\":\"EASY\",\"cognitiveLevel\":\"KNOWLEDGE\",\"options\":[\"1\",\"2\"],\"answer\":\"2\"}");
    }

    @Test
    void questionValidationRejectsMetadataAndMissingCorrectOption() {
        ObjectNode q = question();
        assertDoesNotThrow(() -> QuestionDAO.validateQuestion(q));
        q.put("answer", "3");
        assertThrows(ApiException.class, () -> QuestionDAO.validateQuestion(q));
        q.put("answer", "2");
        q.put("cognitiveLevel", "CLIENT_ADMIN");
        assertThrows(ApiException.class, () -> QuestionDAO.validateQuestion(q));
    }

    @Test
    void examMatrixChecksEachCell() {
        ObjectNode q = question();
        ArrayNode questions = Json.array().add(q);
        var matrix =
                Json.parse(
                        "{\"KNOWLEDGE\":{\"MCQ\":1,\"ESSAY\":0},\"UNDERSTANDING\":{\"MCQ\":0,\"ESSAY\":0},\"APPLICATION\":{\"MCQ\":0,\"ESSAY\":0}}");
        assertDoesNotThrow(() -> ExamService.validateMatrix(matrix, questions));
        q.put("cognitiveLevel", "APPLICATION");
        assertThrows(ApiException.class, () -> ExamService.validateMatrix(matrix, questions));
    }

    @Test
    void questionAnswersAreOmittedForStudents() throws Exception {
        Map<String, Object> row = new HashMap<>(Json.MAPPER.convertValue(question(), Map.class));
        row.put("authorId", 2L);
        sql.when(() -> Database.one(any(), anyString(), any(Object[].class))).thenReturn(row);
        sql.when(() -> Database.rows(any(), anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("content", "1"), Map.of("content", "2")));
        ObjectNode q = new QuestionDAO(db).getQuestionById(c, new Actor(1, "STUDENT", 1), 1, false);
        assertFalse(q.has("answer"));
        assertFalse(q.has("authorId"));
        assertEquals(2, q.path("options").size());
    }

    @Test
    void tutorGatesEveryStepAndKeepsAnswersPrivate() throws Exception {
        Actor student = new Actor(1, "STUDENT", 1);
        QuestionDAO questions = mock(QuestionDAO.class);
        ObjectNode q = question();
        q.put("type", "ESSAY");
        when(questions.getQuestionById(any(), any(), anyLong(), anyBoolean())).thenReturn(q);
        when(questions.getQuestionReference(any(), anyLong())).thenReturn(q);
        AiService ai = mock(AiService.class);
        when(ai.generateResponse(any(), eq("essayEvaluation"), anyString(), nullable(String.class)))
                .thenReturn(
                        Json.parse(
                                "{\"correct\":false,\"confident\":true,\"feedback\":\"Kiểm tra phép"
                                        + " tính.\",\"score\":4}"));
        when(ai.generateResponse(any(), eq("stepTutor"), anyString(), nullable(String.class)))
                .thenReturn(
                        Json.parse(
                                "{\"steps\":[{\"instruction\":\"Thay dữ kiện vào công"
                                    + " thức.\",\"expectedAnswer\":\"2+(10-1)*3\",\"hint\":\"Kiểm"
                                    + " tra công sai.\"},{\"instruction\":\"Tính số hạng cần"
                                    + " tìm.\",\"expectedAnswer\":\"29\",\"hint\":\"Cộng số hạng"
                                    + " đầu với phần tăng.\"}]}"));
        Map<String, Object> record =
                new HashMap<>(Map.of("student_id", 1L, "question_id", 1L, "state_json", "{}"));
        long[] ids = {10};
        boolean[] finished = {false};
        Map<String, Object> job = new HashMap<>();
        sql.when(() -> Database.insert(any(), anyString(), any(Object[].class)))
                .thenAnswer(inv -> ids[0]++);
        sql.when(() -> Database.one(any(), anyString(), any(Object[].class)))
                .thenAnswer(
                        inv -> {
                            String query =
                                    ((String) inv.getArgument(1)).replaceAll("\\s+", " ").trim();
                            if (query.contains("FROM essay_submissions")) return record;
                            if (query.contains("FROM essay_ai_jobs"))
                                return job.isEmpty() ? null : job;
                            if (query.contains("FROM tutor_steps")) return Map.of("id", 13L);
                            if (query.contains("FROM tutor_sessions"))
                                return finished[0] ? Map.of("id", 11L) : null;
                            return null;
                        });
        sql.when(() -> Database.update(any(), anyString(), any(Object[].class)))
                .thenAnswer(
                        inv -> {
                            String query =
                                    ((String) inv.getArgument(1)).replaceAll("\\s+", " ").trim();
                            Object[] args = (Object[]) inv.getRawArguments()[2];
                            if (query.startsWith("INSERT essay_ai_jobs")) {
                                job.put("token", args[2]);
                                job.put("status", "PROCESSING");
                                job.put("live", 1);
                            }
                            if (query.startsWith("UPDATE essay_ai_jobs SET kind")) {
                                job.put("token", args[1]);
                                job.put("status", "PROCESSING");
                                job.put("live", 1);
                            }
                            if (query.startsWith("UPDATE essay_ai_jobs SET status"))
                                job.put("status", args[0]);
                            if (query.startsWith("UPDATE essay_submissions SET state_json")) {
                                record.put("state_json", args[0]);
                                record.put("status", args[1]);
                            }
                            if (query.startsWith("UPDATE essay_submissions SET private_plan"))
                                record.put("private_plan", args[0]);
                            if (query.startsWith("UPDATE tutor_sessions SET completed"))
                                finished[0] = true;
                            return 1;
                        });
        EssayService service = new EssayService(db, questions, ai, new ImageStorage());
        TutorService tutor = new TutorService(db, questions, ai, service);
        ObjectNode s =
                (ObjectNode)
                        service.submitEssay(
                                student,
                                Json.parse(
                                        "{\"questionId\":1,\"content\":{\"text\":\"30\"},\"score\":10,\"status\":\"COMPLETED\"}"));
        long id = s.path("id").asLong();
        assertEquals("SUBMITTED", s.path("status").asText());
        s = (ObjectNode) service.evaluateEssay(student, id);
        assertEquals("NEEDS_TUTOR", s.path("status").asText());
        s = (ObjectNode) tutor.startTutor(student, id);
        assertEquals(1, s.path("aiSteps").size());
        assertFalse(Json.stringify(s).contains("expectedAnswer"));
        assertFalse(Json.stringify(s).contains("private_plan"));
        assertThrows(
                ApiException.class,
                () -> tutor.nextTutorStep(student, id, Json.parse("{\"stepNumber\":1}")));
        assertThrows(
                ApiException.class,
                () ->
                        service.retryEssay(
                                student, id, Json.parse("{\"content\":{\"image\":\"invalid\"}}")));
        s =
                (ObjectNode)
                        tutor.checkTutorStep(
                                student,
                                id,
                                Json.parse(
                                        "{\"stepNumber\":1,\"answer\":\"30\",\"correct\":true}"));
        assertFalse(s.path("aiSteps").path(0).path("correct").asBoolean());
        s =
                (ObjectNode)
                        tutor.checkTutorStep(
                                student,
                                id,
                                Json.parse("{\"stepNumber\":1,\"answer\":\"2+(10-1)*3\"}"));
        assertTrue(s.path("aiSteps").path(0).path("correct").asBoolean());
        assertEquals(0, s.path("currentStep").asInt());
        assertEquals(2, s.path("studentStepAnswers").size());
        s = (ObjectNode) tutor.nextTutorStep(student, id, Json.parse("{\"stepNumber\":1}"));
        assertEquals(1, s.path("currentStep").asInt());
        assertThrows(
                ApiException.class,
                () -> tutor.nextTutorStep(student, id, Json.parse("{\"stepNumber\":1}")));
        tutor.checkTutorStep(student, id, Json.parse("{\"stepNumber\":2,\"answer\":\"29\"}"));
        s = (ObjectNode) tutor.nextTutorStep(student, id, Json.parse("{\"stepNumber\":2}"));
        assertEquals("RETRY_REQUIRED", s.path("status").asText());
        assertThrows(
                ApiException.class,
                () ->
                        service.retryEssay(
                                student, id, Json.parse("{\"content\":{\"text\":\"29\"}}")));
    }

    @Test
    void idempotencyReplaysResultAndRejectsChangedPayload() throws Exception {
        Map<String, Object> saved = new HashMap<>();
        sql.when(() -> Database.one(any(), anyString(), any(Object[].class)))
                .thenAnswer(inv -> saved.isEmpty() ? null : saved);
        sql.when(() -> Database.update(any(), anyString(), any(Object[].class)))
                .thenAnswer(
                        inv -> {
                            if (((String) inv.getArgument(1)).startsWith("INSERT audit_logs"))
                                return 1;
                            Object[] args = (Object[]) inv.getRawArguments()[2];
                            saved.put("route", args[2]);
                            saved.put("fingerprint", args[3]);
                            saved.put("response_json", args[4]);
                            return 1;
                        });
        var service = new IdempotencyService(db);
        Actor a = new Actor(1, "STUDENT", 1);
        int[] calls = {0};
        var first =
                service.execute(
                        a,
                        "unique-request-key-123",
                        "POST /essays",
                        "body",
                        () -> {
                            calls[0]++;
                            return new StoredResponse(201, "{\"id\":5}");
                        });
        var repeated =
                service.execute(
                        a,
                        "unique-request-key-123",
                        "POST /essays",
                        "body",
                        () -> {
                            fail("must not execute duplicate");
                            return null;
                        });
        assertEquals(first, repeated);
        assertEquals(1, calls[0]);
        assertThrows(
                ApiException.class,
                () ->
                        service.execute(
                                a,
                                "unique-request-key-123",
                                "POST /essays",
                                "changed",
                                () -> null));
    }
}
