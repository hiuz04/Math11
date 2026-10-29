package com.mathio.lms.control;

import com.fasterxml.jackson.databind.JsonNode;
import com.mathio.lms.model.Actor;
import com.mathio.lms.model.ApiException;
import com.mathio.lms.model.Json;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Nhận request và trả JSON cho Ai; giữ nguyên đường dẫn API. */
@WebServlet(
        name = "AiController",
        urlPatterns = {"/api/ai/*"})
@jakarta.servlet.annotation.MultipartConfig(maxFileSize = 8 * 1024 * 1024, maxRequestSize = 12_000_000, fileSizeThreshold = 65536)
public class AiController extends ApiController {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        handleRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        handleRequest(request, response);
    }

    @Override
    protected void handleRequest(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String path = getPath(request);
        String method = request.getMethod();
        if (path.equals("/ai/essays/evaluate") && method.equals("POST")) {
            evaluateEssay(request, response);
            return;
        }
        if (path.equals("/ai/tutor/start") && method.equals("POST")) {
            startTutor(request, response);
            return;
        }
        if (path.equals("/ai/tutor/check-step") && method.equals("POST")) {
            checkTutorStep(request, response);
            return;
        }
        if (path.equals("/ai/tutor/next-step") && method.equals("POST")) {
            showNextTutorStep(request, response);
            return;
        }
        if (path.equals("/ai/essays/evaluate-retry") && method.equals("POST")) {
            evaluateRetry(request, response);
            return;
        }
        if (path.equals("/ai/questions/generate") && method.equals("POST")) {
            generateQuestions(request, response);
            return;
        }
        if (path.equals("/ai/exams/generate") && method.equals("POST")) {
            generateExam(request, response);
            return;
        }
        if (path.equals("/ai/chat") && method.equals("POST")) {
            chat(request, response);
            return;
        }
        if (path.equals("/ai/conversations") && method.equals("GET")) {
            showConversations(request, response);
            return;
        }
        if (path.matches("/ai/conversations/\\d+") && method.equals("GET")) {
            showMessages(request, response);
            return;
        }
        throw new ApiException(404, "API không tồn tại.");
    }

    private void evaluateEssay(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        JsonNode input = requestBody(request);
        Object result = app.essayService.evaluateEssay(currentUser, Json.id(input, "submissionId"));
        writeJson(response, result);
    }

    private void startTutor(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        JsonNode input = requestBody(request);
        Object result = app.tutorService.startTutor(currentUser, Json.id(input, "submissionId"));
        writeJson(response, result);
    }

    private void checkTutorStep(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        JsonNode input = requestBody(request);
        Object result =
                app.tutorService.checkTutorStep(currentUser, Json.id(input, "submissionId"), input);
        writeJson(response, result);
    }

    private void showNextTutorStep(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        JsonNode input = requestBody(request);
        Object result =
                app.tutorService.nextTutorStep(currentUser, Json.id(input, "submissionId"), input);
        writeJson(response, result);
    }

    private void evaluateRetry(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        JsonNode input = requestBody(request);
        Object result =
                app.essayService.retryEssay(currentUser, Json.id(input, "submissionId"), input);
        writeJson(response, result);
    }

    private void generateQuestions(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        JsonNode input = requestBody(request);
        Object result = app.aiService.generateQuestions(currentUser, input);
        writeJson(response, result);
    }

    private void generateExam(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        JsonNode input = requestBody(request);
        Object result = app.aiService.generateExam(currentUser, input);
        writeJson(response, result);
    }

    private void chat(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        JsonNode input = requestBody(request);
        Object result = app.aiService.chat(currentUser, input);
        writeJson(response, result);
    }

    private void showConversations(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        Object result = app.aiService.getConversations(currentUser);
        writeJson(response, result);
    }

    private void showMessages(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Application app = application(request);
        Actor currentUser = currentUser(request);
        String path = getPath(request);
        Object result = app.aiService.getMessages(currentUser, number(path.substring(18)));
        writeJson(response, result);
    }
}
