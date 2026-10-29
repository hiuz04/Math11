package com.mathio.lms.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mathio.lms.DAO.AiJobDAO;
import com.mathio.lms.DAO.Database;
import com.mathio.lms.DAO.EssayDAO;
import com.mathio.lms.DAO.QuestionDAO;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Điều phối từng bước Tutor, chỉ mở bước kế tiếp sau khi học sinh trả lời đúng. */
public class TutorService {
    private final Database database;
    private final EssayDAO essayDAO = new EssayDAO();
    private final QuestionDAO questionDAO;
    private final AiService aiService;
    private final EssayService essayService;

    public TutorService(
            Database database,
            QuestionDAO questionDAO,
            AiService aiService,
            EssayService essayService) {
        this.database = database;
        this.questionDAO = questionDAO;
        this.aiService = aiService;
        this.essayService = essayService;
    }

    private static ObjectNode publicStep(JsonNode p, int i, long stepId) {
        ObjectNode step = Json.object();
        step.put("id", stepId);
        step.put("stepNumber", i + 1);
        step.put("instruction", p.path("instruction").asText());
        if (p.has("formula")) step.put("formula", p.path("formula").asText());
        step.put("correct", false);
        step.set("attempts", Json.array());
        return step;
    }

    public Object startTutor(Actor currentUser, long id) {
        ApiException.require(currentUser.isStudent(), 403, "Chỉ học sinh dùng Tutor.");
        record Work(AiJobDAO.Job job, ObjectNode state, JsonNode question, String image) {}
        Work work =
                database.transaction(
                        connection -> {
                            var row =
                                    essayService.getSubmissionRow(
                                            connection, currentUser, id, true);
                            ObjectNode state = essayService.getSubmissionState(row);
                            ApiException.require(
                                    state.path("status").asText().equals("NEEDS_TUTOR"),
                                    409,
                                    "Bài làm chưa cần Tutor.");
                            if (state.path("aiSteps").size() > 0)
                                return new Work(null, state, null, null);
                            var job =
                                    essayService.jobs.reserve(
                                            connection,
                                            id,
                                            "TUTOR",
                                            EssayService.revision(row),
                                            "tutor",
                                            state.path("originalSubmission"));
                            return new Work(
                                    job,
                                    state,
                                    essayService.getPrivateReference(connection, row, state),
                                    essayService.getImageData(
                                            connection, state.path("originalSubmission")));
                        });
        if (work.job() == null) return work.state();
        try {
            JsonNode result =
                    aiService.generateResponse(
                            currentUser,
                            "stepTutor",
                            "Create a private server-side plan for this incorrect solution. Return"
                                + " JSON {steps:[{instruction,formula?,expectedAnswer,hint}]}, 1-12"
                                + " steps. expectedAnswer must be a short exact rational/algebraic"
                                + " value, equation or finite set. Instructions, formula and hints"
                                + " must not include any current or future answers. Ask student to"
                                + " work. Problem: "
                                    + Json.stringify(work.question())
                                    + "\nStudent text: "
                                    + work.state().path("originalSubmission").path("text").asText()
                                    + "\nFeedback: "
                                    + work.state().path("originalFeedback").asText(),
                            work.image());
            JsonNode plan = validatePlan(result);
            return database.transaction(
                    connection -> {
                        var row = essayService.getSubmissionRow(connection, currentUser, id, true);
                        essayService.jobs.validate(
                                connection, work.job(), EssayService.revision(row));
                        ObjectNode state = essayService.getSubmissionState(row);
                        long session = essayDAO.createTutorSession(connection, id, plan.size());
                        long first = 0;
                        int i = 0;
                        for (var p : plan) {
                            long step =
                                    essayDAO.createTutorStep(
                                            connection,
                                            session,
                                            ++i,
                                            p.path("instruction").asText(),
                                            p.path("expectedAnswer").asText());
                            if (i == 1) first = step;
                        }
                        essayDAO.updatePrivatePlan(connection, Json.stringify(plan), id);
                        state.put("totalSteps", plan.size());
                        ((ArrayNode) state.path("aiSteps")).add(publicStep(plan.path(0), 0, first));
                        essayService.updateSubmissionState(connection, id, state);
                        essayService.createHistory(
                                connection,
                                currentUser,
                                state,
                                "AI_TUTOR",
                                "Đang hướng dẫn",
                                null,
                                1);
                        essayService.jobs.finish(connection, work.job(), true);
                        IdempotencyService.completeInTransaction(connection, state);
                        return state;
                    });
        } catch (RuntimeException e) {
            try {
                database.transaction(
                        c -> {
                            essayService.jobs.finish(c, work.job(), false);
                            return null;
                        });
            } catch (RuntimeException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
    }

    private static JsonNode validatePlan(JsonNode result) {
        try {
            JsonNode plan = result.path("steps");
            ApiException.require(
                    plan.isArray() && plan.size() > 0 && plan.size() <= 12,
                    502,
                    "Kế hoạch Tutor không hợp lệ.");
            for (var p : plan) {
                String expected = Json.text(p, "expectedAnswer", 200);
                ApiException.require(
                        MathAnswer.compare(expected, expected) == MathAnswer.Comparison.EQUIVALENT,
                        502,
                        "Đáp án Tutor ngoài phạm vi xác minh chính xác.");
                Json.text(p, "instruction", 4000);
                Json.text(p, "hint", 4000);
                ApiException.require(
                        !p.has("formula")
                                || p.path("formula").isTextual()
                                        && p.path("formula").asText().length() <= 4000,
                        502,
                        "Công thức Tutor không hợp lệ.");
            }
            for (var p : plan)
                for (var secret : plan) {
                    String expected =
                            secret.path("expectedAnswer")
                                    .asText()
                                    .toLowerCase(Locale.ROOT)
                                    .replaceAll("\\s+", "");
                    for (String k : List.of("instruction", "hint", "formula"))
                        ApiException.require(
                                !p.path(k)
                                        .asText()
                                        .toLowerCase(Locale.ROOT)
                                        .replaceAll("\\s+", "")
                                        .contains(expected),
                                502,
                                "Tutor tiết lộ đáp án trong nội dung công khai.");
                }
            return plan;
        } catch (ApiException e) {
            if (e.status == 400) throw new ApiException(502, "Kế hoạch Tutor không đúng cấu trúc.");
            throw e;
        }
    }

    public Object checkTutorStep(Actor currentUser, long id, JsonNode input) {
        ApiException.require(currentUser.isStudent(), 403, "Chỉ học sinh dùng Tutor.");
        String answer = Json.text(input, "answer", 1000);
        return database.transaction(
                connection -> {
                    var row = essayService.getSubmissionRow(connection, currentUser, id, true);
                    ObjectNode state = essayService.getSubmissionState(row);
                    int i = state.path("currentStep").asInt();
                    ApiException.require(
                            input.path("stepNumber").isIntegralNumber()
                                    && input.path("stepNumber").asInt() == i + 1,
                            409,
                            "Bước trả lời đã thay đổi. Hãy tải lại bài làm.");
                    ApiException.require(
                            state.path("status").asText().equals("NEEDS_TUTOR")
                                    && state.path("aiSteps").size() > i,
                            409,
                            "Bước chưa mở.");
                    ObjectNode step = (ObjectNode) state.path("aiSteps").path(i);
                    ApiException.require(
                            !step.path("correct").asBoolean(),
                            409,
                            "Bước đã hoàn thành; hãy bấm Tiếp tục.");
                    JsonNode p = Json.parse(row.get("private_plan").toString()).path(i);
                    MathAnswer.Comparison comparison =
                            MathAnswer.compare(answer, p.path("expectedAnswer").asText());
                    boolean correct = comparison == MathAnswer.Comparison.EQUIVALENT;
                    ObjectNode attempt = Json.object();
                    attempt.put("answer", answer);
                    if (comparison == MathAnswer.Comparison.UNKNOWN) attempt.putNull("correct");
                    else attempt.put("correct", correct);
                    attempt.put("verification", comparison.name());
                    if (comparison == MathAnswer.Comparison.UNKNOWN)
                        attempt.put("diagnostic", "UNABLE_TO_VERIFY");
                    attempt.put("date", Instant.now().toString());
                    String hint =
                            comparison == MathAnswer.Comparison.UNKNOWN
                                    ? "Chưa thể xác minh chính xác. Hãy nhập phân số, biểu thức đại"
                                            + " số hoặc tập nghiệm trong phạm vi hỗ trợ."
                                    : p.path("hint").asText();
                    if (!correct) attempt.put("hint", hint);
                    step.put("studentAnswer", answer);
                    step.put("correct", correct);
                    step.put("verification", comparison.name());
                    if (!correct) step.put("hint", hint);
                    else step.remove("hint");
                    ((ArrayNode) step.path("attempts")).add(attempt);
                    ObjectNode all = attempt.deepCopy();
                    all.put("stepId", step.path("id").asLong());
                    ((ArrayNode) state.path("studentStepAnswers")).add(all);
                    essayDAO.createStepAttempt(
                            connection,
                            step.path("id").asLong(),
                            answer,
                            comparison == MathAnswer.Comparison.UNKNOWN ? null : correct,
                            comparison.name());
                    essayDAO.updateStepCorrectness(connection, correct, step.path("id").asLong());
                    essayService.updateSubmissionState(connection, id, state);
                    return state;
                });
    }

    public Object nextTutorStep(Actor currentUser, long id, JsonNode input) {
        ApiException.require(currentUser.isStudent(), 403, "Chỉ học sinh dùng Tutor.");
        return database.transaction(
                connection -> {
                    var row = essayService.getSubmissionRow(connection, currentUser, id, true);
                    ObjectNode state = essayService.getSubmissionState(row);
                    int i = state.path("currentStep").asInt();
                    ApiException.require(
                            input.path("stepNumber").isIntegralNumber()
                                    && input.path("stepNumber").asInt() == i + 1
                                    && state.path("status").asText().equals("NEEDS_TUTOR")
                                    && state.path("aiSteps").path(i).path("correct").asBoolean(),
                            409,
                            "Hãy trả lời đúng bước hiện tại trước khi tiếp tục.");
                    JsonNode plan = Json.parse(row.get("private_plan").toString());
                    if (i + 1 == plan.size()) {
                        state.put("status", "RETRY_REQUIRED");
                        essayDAO.completeTutorSession(connection, id);
                    } else {
                        state.put("currentStep", i + 1);
                        var step = essayDAO.getTutorStep(connection, id, i + 2);
                        ((ArrayNode) state.path("aiSteps"))
                                .add(
                                        publicStep(
                                                plan.path(i + 1),
                                                i + 1,
                                                ((Number) step.get("id")).longValue()));
                        essayDAO.updateCurrentStep(connection, i + 1, id);
                    }
                    essayService.updateSubmissionState(connection, id, state);
                    return state;
                });
    }
}
