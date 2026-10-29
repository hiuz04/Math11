package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mathio.lms.DAO.*;
import com.mathio.lms.model.*;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.sql.Connection;
import java.util.Map;

import javax.sql.DataSource;

class ObjectAuthorizationTest {
    @Test
    void hiddenQuestionIsRejectedEvenWhenCallerRequestsTheAnswer() throws Exception {
        Connection c = mock(Connection.class);
        try (MockedStatic<Database> sql = mockStatic(Database.class)) {
            sql.when(() -> Database.one(any(), anyString(), any(Object[].class))).thenReturn(null);
            var dao = new QuestionDAO(mock(Database.class));
            assertEquals(
                    404,
                    assertThrows(
                                    ApiException.class,
                                    () ->
                                            dao.getQuestionById(
                                                    c, new Actor(1, "STUDENT", 1), 999, true))
                            .status);
            sql.verify(
                    () ->
                            Database.one(
                                    eq(c),
                                    argThat(
                                            q ->
                                                    q.contains("q.published=1")
                                                            && q.contains("scope_n.student_id=1")
                                                            && q.contains(
                                                                    "scope_c.id=q.course_id")),
                                    eq(999L)));
        }
    }

    @Test
    void teacherWithTheStudentInAnotherCourseCannotReadAttemptSubmissionOrImage() throws Exception {
        Connection c = mock(Connection.class);
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenReturn(c);
        Database db = new Database(ds);
        Actor teacher = new Actor(2, "TEACHER", 1);
        try (MockedStatic<Database> sql = mockStatic(Database.class)) {
            sql.when(() -> Database.one(any(), anyString(), any(Object[].class)))
                    .thenAnswer(
                            inv -> {
                                String q = inv.getArgument(1);
                                if (q.contains("FROM course_enrollments e"))
                                    return Map.of("id", 1L); // broad student relationship exists
                                if (q.contains("FROM exam_attempts"))
                                    return Map.of("id", 9L, "studentId", 3L, "examId", 99L);
                                if (q.contains("FROM essay_submissions WITH"))
                                    return Map.of("id", 8L, "student_id", 3L, "question_id", 7L);
                                if (q.contains("FROM essay_submissions\n"))
                                    return Map.of("id", 8L, "student_id", 3L, "question_id", 7L);
                                if (q.contains("FROM submission_files"))
                                    return Map.of(
                                            "owner_id",
                                            3L,
                                            "submission_id",
                                            8L,
                                            "storage_key",
                                            "unreachable",
                                            "mime",
                                            "image/png");
                                return null; // resource does not belong to this teacher/course
                            });
            assertDoesNotThrow(() -> AccessDAO.validateStudentAccess(c, teacher, 3));
            assertEquals(
                    403,
                    assertThrows(
                                    ApiException.class,
                                    () ->
                                            new ExamService(db, new QuestionDAO(db))
                                                    .getAttemptById(teacher, 9))
                            .status);
            EssayService essays =
                    new EssayService(
                            db,
                            new QuestionDAO(db),
                            mock(AiService.class),
                            mock(ImageStorage.class));
            assertEquals(
                    403,
                    assertThrows(ApiException.class, () -> essays.getEssayById(teacher, 8)).status);
            assertEquals(
                    403, assertThrows(ApiException.class, () -> essays.getFile(teacher, 6)).status);
        }
    }

    @Test
    void reportingFiltersTheResourceInsteadOfAllWorkByAnEnrolledStudent() {
        Actor teacher = new Actor(2, "TEACHER", 1);
        String scope = AccessDAO.historyScope(teacher, "h");
        assertTrue(scope.contains("scope_a.id=h.attempt_id"));
        assertTrue(scope.contains("scope_s.id=h.submission_id"));
        assertTrue(scope.contains("scope_c.id=scope_e.course_id"));
        assertTrue(scope.contains("scope_c.id=scope_s.course_id"));
        assertFalse(scope.contains("course_enrollments"));
    }

    @Test
    void studentsCannotGradeOrPublishScores() {
        Actor student = new Actor(1, "STUDENT", 1);
        Database db = mock(Database.class);
        ExamService exams = new ExamService(db, mock(QuestionDAO.class));
        assertEquals(
                403,
                assertThrows(
                                ApiException.class,
                                () -> exams.gradeAttempt(student, 1, Json.object()))
                        .status);
        assertEquals(
                403,
                assertThrows(ApiException.class, () -> exams.publishResults(student, 1)).status);
        assertEquals(
                403,
                assertThrows(
                                ApiException.class,
                                () ->
                                        new EssayService(
                                                        db,
                                                        mock(QuestionDAO.class),
                                                        mock(AiService.class),
                                                        mock(ImageStorage.class))
                                                .gradeEssay(student, 1, Json.object()))
                        .status);
        verifyNoInteractions(db);
    }
}
