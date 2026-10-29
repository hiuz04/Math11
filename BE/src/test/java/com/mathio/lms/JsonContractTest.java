package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mathio.lms.DAO.CatalogDAO;
import com.mathio.lms.DAO.Database;
import com.mathio.lms.DAO.QuestionDAO;
import com.mathio.lms.model.Actor;
import com.mathio.lms.model.GeminiSettings;
import com.mathio.lms.model.Json;
import com.mathio.lms.model.StoredResponse;
import com.mathio.lms.model.UserAccount;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.sql.Connection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

class JsonContractTest {
    @Test
    void pojosPreserveSessionAndIdempotencyJsonAndKeepSecretsPrivate() {
        assertEquals(
                Json.parse("{\"id\":1,\"role\":\"STUDENT\",\"version\":2}"),
                Json.parse(Json.stringify(new Actor(1, "STUDENT", 2))));
        assertEquals(
                Json.parse("{\"status\":201,\"body\":\"saved\"}"),
                Json.parse(Json.stringify(new StoredResponse(201, "saved"))));
        UserAccount account = new UserAccount(1, "STUDENT", "private-hash", true, 1);
        assertFalse(Json.stringify(account).contains("private-hash"));
        GeminiSettings settings = new GeminiSettings("model", 0.2, 4096, true, "private-key");
        assertFalse(Json.stringify(settings).contains("private-key"));
        assertEquals(
                Json.parse(
                        "{\"model\":\"model\",\"temperature\":0.2,\"maxOutputTokens\":4096,\"enabled\":true,\"hasApiKey\":true}"),
                Json.parse(Json.stringify(settings.publicView())));
    }

    @Test
    void lessonDetailHasExactlyTheExistingFieldsIncludingNullableValues() throws Exception {
        Connection connection = mock(Connection.class);
        DataSource source = mock(DataSource.class);
        when(source.getConnection()).thenReturn(connection);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 7L);
        row.put("title", "Bài giảng");
        row.put("description", "");
        row.put("objectives", "");
        row.put("content", "<p>uₙ</p>");
        row.put("videoUrl", null);
        row.put("contentType", "TEXT");
        row.put("courseId", 2L);
        row.put("chapterId", 3L);
        row.put("published", true);
        row.put("authorId", 4L);
        row.put("completed", 1);
        row.put("elapsedSeconds", 60);
        try (MockedStatic<Database> sql = mockStatic(Database.class)) {
            sql.when(() -> Database.one(any(), anyString(), any(Object[].class))).thenReturn(row);
            Object lesson =
                    new CatalogDAO(new Database(source)).getLessonById(new Actor(1, "ADMIN", 1), 7);
            assertEquals(Json.parse(Json.stringify(row)), Json.parse(Json.stringify(lesson)));
        }
    }

    @Test
    void questionKeepsOptionsShapeAndHidesAnswerOnlyForStudentResponses() throws Exception {
        Map<String, Object> row =
                new HashMap<>(Json.MAPPER.convertValue(ServiceTest.question(), Map.class));
        row.remove("options");
        row.put("authorId", 9L);
        row.put("published", true);
        row.put("courseId", 2L);
        try (MockedStatic<Database> sql = mockStatic(Database.class)) {
            sql.when(() -> Database.one(any(), anyString(), any(Object[].class))).thenReturn(row);
            sql.when(() -> Database.rows(any(), anyString(), any(Object[].class)))
                    .thenReturn(List.of());
            QuestionDAO dao = new QuestionDAO(mock(Database.class));
            var staff =
                    dao.getQuestionById(
                            mock(Connection.class), new Actor(9, "TEACHER", 1), 1, true);
            var student =
                    dao.getQuestionById(
                            mock(Connection.class), new Actor(1, "STUDENT", 1), 1, false);
            row.remove("authorId");
            assertEquals(Json.parse(Json.stringify(row)), Json.parse(Json.stringify(staff)));
            row.remove("answer");
            assertEquals(Json.parse(Json.stringify(row)), Json.parse(Json.stringify(student)));
            assertFalse(student.has("options"));
        }
    }
}
