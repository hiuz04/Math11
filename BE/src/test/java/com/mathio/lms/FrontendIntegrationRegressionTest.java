package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.mathio.lms.DAO.*;
import com.mathio.lms.model.*;
import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class FrontendIntegrationRegressionTest {
    @Test
    void studentDtoNeverCopiesGradingOrUnexpectedSnapshotFields() {
        var source = Json.parse("{\"id\":1,\"title\":\"Question\",\"content\":\"1+1\",\"options\":[\"1\",\"2\"],\"answer\":\"2\",\"explanation\":\"secret\",\"score\":10,\"rubric\":{},\"privatePlan\":[]}");
        var student = StudentQuestionDTO.from(source);
        assertEquals(4, student.size());
        assertTrue(student.has("options"));
        for (String field : List.of("answer", "explanation", "score", "rubric", "privatePlan")) assertFalse(student.has(field));
        assertTrue(source.has("answer"));
    }

    @Test
    void emptySanitizedQuestionsAreRejectedAndTiptapMathIsPreserved() {
        var question = ServiceTest.question();
        question.put("content", "<p>&nbsp;</p>");
        assertThrows(ApiException.class, () -> QuestionDAO.validateQuestion(question));
        question.put("content", "<span data-type='inline-math' data-latex='x^2'></span>");
        assertTrue(QuestionDAO.validateQuestion(question).path("content").asText().contains("data-latex"));
        question.put("answer", "<script>secret()</script>");
        assertThrows(ApiException.class, () -> QuestionDAO.validateQuestion(question));
    }

    @Test
    void questionGenerationChecksCourseOwnershipBeforeCallingGemini() throws Exception {
        Connection c = mock(Connection.class);
        DataSource source = mock(DataSource.class);
        when(source.getConnection()).thenReturn(c);
        GeminiClient client = mock(GeminiClient.class);
        try (var db = new Database(source); var sql = mockStatic(Database.class)) {
            sql.when(() -> Database.one(any(), anyString(), any(Object[].class)))
                    .thenReturn(java.util.Map.of("teacher_id", 99L, "published", true));
            var service = new AiService(db, mock(ConfigurationService.class), client);
            var input = Json.parse("{\"courseId\":77,\"chapter\":\"Chương\",\"topic\":\"Chủ đề\",\"questionType\":\"MCQ\",\"cognitiveLevel\":\"KNOWLEDGE\",\"difficulty\":\"EASY\",\"count\":1}");
            var error = assertThrows(ApiException.class, () -> service.generateQuestions(new Actor(2, "TEACHER", 1), input));
            assertEquals(403, error.status);
            verifyNoInteractions(client);
        }
    }

    @Test
    void catalogPaginationBindsExactlyTheSqlPlaceholders() throws Exception {
        Connection c = mock(Connection.class);
        DataSource source = mock(DataSource.class);
        when(source.getConnection()).thenReturn(c);
        try (var db = new Database(source); var sql = mockStatic(Database.class)) {
            sql.when(() -> Database.rows(any(), anyString(), any(Object[].class))).thenAnswer(inv -> {
                String query = inv.getArgument(1);
                Object[] args = (Object[]) inv.getRawArguments()[2];
                assertEquals(query.chars().filter(ch -> ch == '?').count(), args.length, query);
                assertTrue(query.contains("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"));
                assertEquals(3, args[args.length - 2]);
                assertEquals(7, args[args.length - 1]);
                return List.of();
            });
            var catalog = new CatalogDAO(db);
            Actor student = new Actor(1, "STUDENT", 1);
            catalog.getCourses(student, 3, 7);
            catalog.getLessons(student, 0, 3, 7);
        }
    }

    @Test
    void searchUsesScopedMetadataAndBoundLiteralSearchWithPagination() throws Exception {
        Connection c = mock(Connection.class);
        DataSource source = mock(DataSource.class);
        when(source.getConnection()).thenReturn(c);
        try (var db = new Database(source); var sql = mockStatic(Database.class)) {
            String[] expectedRole = {"STUDENT"};
            sql.when(() -> Database.rows(any(), anyString(), any(Object[].class))).thenAnswer(inv -> {
                String query = inv.getArgument(1);
                Object[] args = (Object[]) inv.getRawArguments()[2];
                assertEquals(query.chars().filter(ch -> ch == '?').count(), args.length);
                assertFalse(query.contains("q.answer"));
                assertFalse(query.contains("password"));
                assertEquals("%a!%!_![b!!%", args[0]);
                assertEquals(40, args[args.length - 2]);
                assertEquals(21, args[args.length - 1]);
                if (expectedRole[0].equals("STUDENT")) {
                    assertTrue(query.contains("l.published=1"));
                    assertTrue(query.contains("q.published=1"));
                    assertTrue(query.contains("e.published=1"));
                    assertTrue(query.contains("student_id=17"));
                    assertTrue(query.contains("q.type='ESSAY'"));
                } else if (expectedRole[0].equals("TEACHER")) {
                    assertTrue(query.contains("co.teacher_id=3"));
                    assertTrue(query.contains("q.author_id=3"));
                    assertFalse(query.contains("N'quiz'"));
                }
                return List.of();
            });
            var reporting = new ReportingDAO(db);
            reporting.getSearch(new Actor(17,"STUDENT",1), "a%_[b!", 40, 21);
            expectedRole[0] = "TEACHER";
            reporting.getSearch(new Actor(3,"TEACHER",1), "a%_[b!", 40, 21);
            expectedRole[0] = "ADMIN";
            reporting.getSearch(new Actor(1,"ADMIN",1), "a%_[b!", 40, 21);
            assertThrows(ApiException.class, () -> reporting.getSearch(new Actor(17,"STUDENT",1), "x".repeat(201),0,21));
        }
    }
}
