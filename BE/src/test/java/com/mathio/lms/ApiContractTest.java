package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mathio.lms.DAO.CatalogDAO;
import com.mathio.lms.DAO.QuestionDAO;
import com.mathio.lms.DAO.ReportingDAO;
import com.mathio.lms.DAO.UserDAO;
import com.mathio.lms.control.AiController;
import com.mathio.lms.control.ApiServlet;
import com.mathio.lms.control.Application;
import com.mathio.lms.control.AuthController;
import com.mathio.lms.control.ConfigurationController;
import com.mathio.lms.control.CourseController;
import com.mathio.lms.control.EssayController;
import com.mathio.lms.control.ExamController;
import com.mathio.lms.control.LessonController;
import com.mathio.lms.control.QuestionController;
import com.mathio.lms.control.ReportingController;
import com.mathio.lms.control.UserController;
import com.mathio.lms.model.Actor;
import com.mathio.lms.model.AiService;
import com.mathio.lms.model.ApiException;
import com.mathio.lms.model.AuthService;
import com.mathio.lms.model.ConfigurationService;
import com.mathio.lms.model.EssayService;
import com.mathio.lms.model.ExamService;
import com.mathio.lms.model.Json;
import com.mathio.lms.model.TutorService;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Kiểm tra endpoint/method/status với mapping thật, không phụ thuộc SQL Server/Gemini. */
class ApiContractTest {
    static final List<Class<? extends HttpServlet>> CONTROLLERS =
            List.of(
                    ApiServlet.class,
                    AuthController.class,
                    UserController.class,
                    CourseController.class,
                    LessonController.class,
                    QuestionController.class,
                    ExamController.class,
                    EssayController.class,
                    AiController.class,
                    ConfigurationController.class,
                    ReportingController.class);

    static void registerControllers(Context context) throws Exception {
        for (Class<? extends HttpServlet> type : CONTROLLERS) {
            WebServlet mapping = type.getAnnotation(WebServlet.class);
            String name = type.getSimpleName();
            var wrapper = Tomcat.addServlet(context, name, type.getDeclaredConstructor().newInstance());
            var multipart = type.getAnnotation(jakarta.servlet.annotation.MultipartConfig.class);
            if (multipart != null) wrapper.setMultipartConfigElement(new jakarta.servlet.MultipartConfigElement(multipart));
            for (String path : mapping.urlPatterns()) {
                context.addServletMappingDecoded(path, name);
            }
        }
    }

    @TestFactory
    Stream<DynamicTest> existingEndpointsKeepTheirContracts() {
        String cases =
                """
GET|/auth/csrf|200|
POST|/auth/login|200|authService.login,authService.getProfile
GET|/auth/me|200|authService.getProfile
POST|/auth/logout|204|
POST|/auth/change-password|204|authService.changePassword
GET|/profile|200|authService.getProfile
POST|/profile|200|authService.updateProfile,authService.getProfile
PUT|/profile|200|authService.updateProfile,authService.getProfile
GET|/users|200|userDAO.getUsers
POST|/users|201|userDAO.saveUser
POST|/users/7|200|userDAO.saveUser
PUT|/users/7|200|userDAO.saveUser
DELETE|/users/7|204|userDAO.deleteUser
GET|/courses|200|catalogDAO.getCourses
GET|/search|200|reportingDAO.getSearch
POST|/courses|201|catalogDAO.saveCourse
POST|/courses/7|200|catalogDAO.saveCourse
PUT|/courses/7|200|catalogDAO.saveCourse
DELETE|/courses/7|204|catalogDAO.deleteCourse
POST|/courses/7/enrollments|200|catalogDAO.enrollStudent
GET|/chapters|200|catalogDAO.getChapters
GET|/topics|200|catalogDAO.getTopics
GET|/lessons|200|catalogDAO.getLessons
POST|/lessons|201|catalogDAO.saveLesson
GET|/lessons/7|200|catalogDAO.getLessonById
POST|/lessons/7|200|catalogDAO.saveLesson
PUT|/lessons/7|200|catalogDAO.saveLesson
DELETE|/lessons/7|204|catalogDAO.deleteLesson
POST|/lessons/7/progress|200|catalogDAO.updateProgress
GET|/questions|200|questionDAO.getQuestions
POST|/questions/bulk|200|questionDAO.saveQuestions
GET|/questions/7|200|questionDAO.getQuestionById
DELETE|/questions/7|204|questionDAO.deleteQuestion
GET|/exercises|200|questionDAO.getExercises
GET|/exams|200|examService.getExams
POST|/exams|201|examService.saveExam
GET|/exams/7|200|examService.getExamById
DELETE|/exams/7|204|examService.deleteExam
POST|/exams/7/attempts|201|examService.startAttempt
GET|/quiz-attempts|200|examService.getAttemptHistory
GET|/quiz-attempts/7|200|examService.getAttemptById
POST|/quiz-attempts/7/answers|200|examService.saveAnswers
POST|/quiz-attempts/7/submit|200|examService.saveAnswers
POST|/quiz-attempts/7/grade|200|examService.gradeAttempt
POST|/exams/7/publish-results|200|examService.publishResults
POST|/essays/7/grade|200|essayService.gradeEssay
GET|/exam-files/7|200|examService.getFile
GET|/essays|200|essayService.getEssays
POST|/essays|201|essayService.submitEssay
GET|/essays/7|200|essayService.getEssayById
GET|/files/7|200|essayService.getFile
POST|/ai/essays/evaluate|200|essayService.evaluateEssay
POST|/ai/tutor/start|200|tutorService.startTutor
POST|/ai/tutor/check-step|200|tutorService.checkTutorStep
POST|/ai/tutor/next-step|200|tutorService.nextTutorStep
POST|/ai/essays/evaluate-retry|200|essayService.retryEssay
POST|/ai/questions/generate|200|aiService.generateQuestions
POST|/ai/exams/generate|200|aiService.generateExam
POST|/ai/chat|200|aiService.chat
GET|/ai/conversations|200|aiService.getConversations
GET|/ai/conversations/7|200|aiService.getMessages
GET|/admin/config/gemini|200|configurationService.getConfiguration
POST|/admin/config/gemini|200|configurationService.saveConfiguration
POST|/admin/config/gemini/test|200|aiService.testConnection
GET|/admin/config/prompts|200|configurationService.getPrompts
POST|/admin/config/prompts|200|configurationService.savePrompts,configurationService.getPrompts
GET|/dashboard|200|reportingDAO.getDashboard
GET|/analytics|200|reportingDAO.getAnalytics
GET|/students/7/statistics|200|reportingDAO.getStudentStatistics
GET|/learning-history|200|reportingDAO.getLearningHistory
GET|/notifications|200|reportingDAO.getNotifications
POST|/notifications/7/read|200|reportingDAO.markNotificationRead
GET|/admin/audit-logs|200|reportingDAO.getAuditLogs
GET|/users/7|405|
PUT|/users|405|
DELETE|/profile|405|
POST|/questions|404|
PUT|/questions/7|405|
POST|/exams/7|405|
POST|/essays/7|409|
DELETE|/essays/7|405|
POST|/learning-history|403|
PATCH|/learning-history|403|
HEAD|/lessons|405|
GET|/lessons/0|400|
GET|/lessons/999999999999999999999999999|400|
GET|/not-found|404|
POST|/ai/not-found|404|
GET|/admin/config/not-found|404|
""";
        return cases.lines().map(line -> DynamicTest.dynamicTest(line, () -> runCase(line)));
    }

    private void runCase(String line) throws Exception {
        String[] fields = line.split("\\|", -1);
        String method = fields[0], path = fields[1];
        int expectedStatus = Integer.parseInt(fields[2]);
        Actor actor = new Actor(3, "ADMIN", 1);
        Answer<Object> savedResponse =
                invocation -> {
                    Class<?> type = invocation.getMethod().getReturnType();
                    if (type == void.class) return null;
                    if (type == Actor.class) return actor;
                    if (type == ArrayNode.class) return Json.array().add("preserved");
                    if (type == ObjectNode.class) return Json.object().put("value", "preserved");
                    return Map.of("value", "preserved");
                };
        Application app = new Application();
        app.authService = mock(AuthService.class, savedResponse);
        app.userDAO = mock(UserDAO.class, savedResponse);
        app.catalogDAO = mock(CatalogDAO.class, savedResponse);
        app.questionDAO = mock(QuestionDAO.class, savedResponse);
        app.examService = mock(ExamService.class, savedResponse);
        app.essayService = mock(EssayService.class, savedResponse);
        app.tutorService = mock(TutorService.class, savedResponse);
        app.aiService = mock(AiService.class, savedResponse);
        app.configurationService = mock(ConfigurationService.class, savedResponse);
        app.reportingDAO = mock(ReportingDAO.class, savedResponse);
        doReturn(Map.of("bytes", new byte[] {1, 2, 3}, "mime", "image/png"))
                .when(app.essayService)
                .getFile(actor, 7);

        doReturn(Map.of("bytes", new byte[] {1, 2, 3}, "mime", "image/png"))
                .when(app.examService)
                .getFile(actor, 7);
        HttpServletRequest request =
                request(
                        app,
                        actor,
                        method,
                        path,
                        "{\"submissionId\":7,\"role\":\"STUDENT\",\"userId\":999}");
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter output = new StringWriter();
        ByteArrayOutputStream binary = new ByteArrayOutputStream();
        int[] status = {200};
        doAnswer(
                        inv -> {
                            status[0] = inv.getArgument(0);
                            return null;
                        })
                .when(response)
                .setStatus(anyInt());
        when(response.getStatus()).thenAnswer(inv -> status[0]);
        when(response.getWriter()).thenReturn(new PrintWriter(output));
        when(response.getOutputStream())
                .thenReturn(
                        new ServletOutputStream() {
                            public void write(int value) {
                                binary.write(value);
                            }

                            public boolean isReady() {
                                return true;
                            }

                            public void setWriteListener(WriteListener listener) {}
                        });

        try {
            controller(path).service(request, response);
        } catch (ApiException e) {
            status[0] = e.status;
            assertTrue(expectedStatus >= 400, e.getMessage());
        }
        assertEquals(expectedStatus, status[0]);
        List<String> actual = new ArrayList<>();
        for (String field :
                List.of(
                        "authService",
                        "userDAO",
                        "catalogDAO",
                        "questionDAO",
                        "examService",
                        "essayService",
                        "tutorService",
                        "aiService",
                        "configurationService",
                        "reportingDAO")) {
            Object mock = Application.class.getField(field).get(app);
            mockingDetails(mock)
                    .getInvocations()
                    .forEach(
                            inv -> {
                                actual.add(field + "." + inv.getMethod().getName());
                                if (inv.getArguments().length > 0
                                        && inv.getArgument(0) instanceof Actor) {
                                    assertSame(actor, inv.getArgument(0));
                                }
                            });
        }
        assertEquals(fields[3].isEmpty() ? List.of() : Arrays.asList(fields[3].split(",")), actual);
        if (status[0] == 204) assertEquals("", output.toString());
        if (path.equals("/files/7") || path.equals("/exam-files/7"))
            assertArrayEquals(new byte[] {1, 2, 3}, binary.toByteArray());
        else if (status[0] < 400 && status[0] != 204) assertNotNull(Json.parse(output.toString()));
    }

    static HttpServlet controller(String path) throws Exception {
        String endpoint = "/api" + path;
        Class<? extends HttpServlet> selected = null;
        int priority = -1;
        for (Class<? extends HttpServlet> type : CONTROLLERS) {
            for (String pattern : type.getAnnotation(WebServlet.class).urlPatterns()) {
                boolean prefix = pattern.endsWith("/*");
                String root = prefix ? pattern.substring(0, pattern.length() - 2) : pattern;
                boolean match =
                        prefix
                                ? endpoint.equals(root) || endpoint.startsWith(root + "/")
                                : endpoint.equals(root);
                int rank = prefix ? root.length() : 10000 + root.length();
                if (match && rank > priority) {
                    selected = type;
                    priority = rank;
                }
            }
        }
        assertNotNull(selected, endpoint);
        return selected.getDeclaredConstructor().newInstance();
    }

    static HttpServletRequest request(
            Application app, Actor actor, String method, String path, String body)
            throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentType()).thenReturn("application/json");
        ServletContext context = mock(ServletContext.class);
        HttpSession session = mock(HttpSession.class);
        when(context.getAttribute(Application.class.getName())).thenReturn(app);
        when(request.getServletContext()).thenReturn(context);
        when(request.getContextPath()).thenReturn("/mathio");
        when(request.getRequestURI()).thenReturn("/mathio/api" + path);
        when(request.getServletPath()).thenReturn("/api");
        when(request.getPathInfo()).thenReturn(path);
        when(request.getMethod()).thenReturn(method);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getSession()).thenReturn(session);
        when(request.getSession(true)).thenReturn(session);
        when(session.getAttribute("csrf")).thenReturn("csrf");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("actor", actor);
        when(request.getAttribute(anyString()))
                .thenAnswer(inv -> attributes.get(inv.getArgument(0)));
        doAnswer(
                        inv -> {
                            attributes.put(inv.getArgument(0), inv.getArgument(1));
                            return null;
                        })
                .when(request)
                .setAttribute(anyString(), any());
        ByteArrayInputStream input =
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        when(request.getInputStream())
                .thenReturn(
                        new ServletInputStream() {
                            public int read() {
                                return input.read();
                            }

                            public boolean isFinished() {
                                return input.available() == 0;
                            }

                            public boolean isReady() {
                                return true;
                            }

                            public void setReadListener(ReadListener listener) {}
                        });
        return request;
    }
}
