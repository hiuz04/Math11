package com.mathio.lms.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mathio.lms.DAO.AccessDAO;
import com.mathio.lms.DAO.Database;
import com.mathio.lms.DAO.ExamDAO;
import com.mathio.lms.DAO.QuestionDAO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExamService {
    private final Database database;
    private final ExamDAO examDAO = new ExamDAO();
    private final QuestionDAO questionDAO;
    private final ImageStorage storage = new ImageStorage();

    public ExamService(Database database, QuestionDAO question) {
        this.database = database;
        this.questionDAO = question;
    }

    public static void validateMatrix(JsonNode matrix, JsonNode questions) {
        int total = 0;
        for (String level : List.of("KNOWLEDGE", "UNDERSTANDING", "APPLICATION"))
            for (String type : List.of("MCQ", "ESSAY")) {
                var cell = matrix.path(level).path(type);
                ApiException.require(
                        cell.isIntegralNumber() && cell.asInt() >= 0 && cell.asInt() <= 100,
                        400,
                        "Ma trận không hợp lệ.");
                int count = 0;
                for (var question : questions)
                    if (question.path("type").asText().equals(type)
                            && question.path("cognitiveLevel").asText().equals(level)) count++;
                ApiException.require(count == cell.asInt(), 400, "Số câu không khớp ma trận.");
                total += count;
            }
        ApiException.require(
                total > 0 && total <= 100 && total == questions.size(),
                400,
                "Tổng số câu không hợp lệ.");
    }

    public Object saveExam(Actor currentUser, JsonNode input) {
        currentUser.requireStaff();
        validateMatrix(input.path("matrix"), input.path("questions"));
        return database.transaction(
                connection -> {
                    long id = input.path("id").asLong();
                    var old = id > 0 ? examDAO.getExamAuthor(connection, id) : null;
                    ApiException.require(
                            id <= 0 || old != null, 404, "Không tìm thấy đề thi cần sửa.");
                    if (old != null) {
                        AccessDAO.validateAuthorAccess(currentUser, old.get("author_id"));
                        ApiException.require(
                                examDAO.getFirstAttempt(connection, id) == null,
                                409,
                                "Không thể sửa đề đã có bài làm.");
                        examDAO.updateExam(
                                connection,
                                Json.text(input, "title", 200),
                                Json.text(input, "chapter", 200),
                                Json.text(input, "topic", 200),
                                Json.stringify(input.path("matrix")),
                                id);
                        examDAO.deleteExamQuestions(connection, id);
                    } else
                        id =
                                examDAO.createExam(
                                        connection,
                                        currentUser.getId(),
                                        Json.text(input, "title", 200),
                                        Json.text(input, "chapter", 200),
                                        Json.text(input, "topic", 200),
                                        Json.stringify(input.path("matrix")),
                                        input.path("published").asBoolean(true),
                                        Math.max(
                                                60,
                                                Math.min(
                                                        14400,
                                                        input.path("durationSeconds")
                                                                .asInt(1800))));
                    Long course =
                            input.has("courseId") || old == null
                                    ? AccessDAO.resolveCourse(connection, currentUser, input)
                                    : null;
                    if (course != null)
                        Database.update(
                                connection, "UPDATE exams SET course_id=? WHERE id=?", course, id);
                    if (input.has("published"))
                        Database.update(
                                connection,
                                "UPDATE exams SET published=? WHERE id=?",
                                input.path("published").asBoolean(),
                                id);
                    if (input.has("resultsPublished"))
                        Database.update(
                                connection,
                                "UPDATE exams SET results_published=? WHERE id=?",
                                input.path("resultsPublished").asBoolean(),
                                id);
                    if (input.has("durationSeconds")) {
                        ApiException.require(
                                input.path("durationSeconds").isIntegralNumber()
                                        && input.path("durationSeconds").asInt() >= 60
                                        && input.path("durationSeconds").asInt() <= 14400,
                                400,
                                "Thời gian đề thi không hợp lệ.");
                        Database.update(
                                connection,
                                "UPDATE exams SET duration_seconds=? WHERE id=?",
                                input.path("durationSeconds").asInt(),
                                id);
                    }
                    int i = 0;
                    Set<Long> ids = new HashSet<>();
                    for (var question : input.path("questions")) {
                        long questionId =
                                questionDAO.saveQuestion(connection, currentUser, question);
                        ApiException.require(
                                ids.add(questionId), 400, "Câu hỏi bị trùng trong đề.");
                        ObjectNode snapshot =
                                questionDAO.getQuestionById(
                                        connection, currentUser, questionId, true);
                        examDAO.createExamQuestion(
                                connection, id, questionId, i++, Json.stringify(snapshot));
                    }
                    AccessDAO.audit(connection, currentUser, "EXAM_SAVED", id);
                    return getExamById(connection, currentUser, id);
                });
    }

    private ObjectNode getExamById(Connection connection, Actor currentUser, long id)
            throws SQLException {
        var row = examDAO.getExamById(connection, id, currentUser.isStudent());
        ApiException.require(row != null, 404, "Không tìm thấy đề thi.");
        AccessDAO.validateExamAccess(connection, currentUser, id);
        ObjectNode e = Json.MAPPER.valueToTree(row);
        e.set("matrix", Json.parse(row.get("matrix").toString()));
        e.remove("authorId");
        ArrayNode questionList = Json.array();
        for (var snapshotRow : examDAO.getQuestionSnapshots(connection, id)) {
            ObjectNode question = (ObjectNode) Json.parse(snapshotRow.get("snapshot").toString());
            if (currentUser.isStudent()) question = StudentQuestionDTO.from(question);
            questionList.add(question);
        }
        e.set("questions", questionList);
        return e;
    }

    public Object getExamById(Actor currentUser, long id) {
        return database.read(connection -> getExamById(connection, currentUser, id));
    }

    public Object getExams(Actor actor) {
        return getExams(actor, 0, 100);
    }

    public Object getExams(Actor actor, int offset, int limit) {
        return database.read(
                c -> {
                    var rows =
                            Database.rows(
                                    c,
                                    "SELECT e.id,e.title,e.chapter,e.topic,e.matrix,e.course_id AS"
                                        + " courseId,e.duration_seconds AS"
                                        + " durationSeconds,e.published,e.results_published AS"
                                        + " resultsPublished,e.created_at AS createdAt FROM exams e"
                                        + " WHERE e.deleted=0 AND ("
                                            + AccessDAO.examScope(actor, "e")
                                            + ") ORDER BY e.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS"
                                            + " ONLY",
                                    offset,
                                    limit);
                    ArrayNode out = Json.array();
                    if (rows.isEmpty()) return out;
                    var snapshots =
                            Database.rows(
                                    c,
                                    "SELECT exam_id,snapshot FROM exam_questions WHERE exam_id IN ("
                                            + String.join(
                                                    ",",
                                                    java.util.Collections.nCopies(rows.size(), "?"))
                                            + ") ORDER BY exam_id,position",
                                    rows.stream().map(r -> r.get("id")).toArray());
                    for (var row : rows) {
                        ObjectNode exam = Json.MAPPER.valueToTree(row);
                        exam.set("matrix", Json.parse(row.get("matrix").toString()));
                        ArrayNode list = Json.array();
                        for (var snapshot : snapshots)
                            if (snapshot.get("exam_id").equals(row.get("id"))) {
                                ObjectNode q =
                                        (ObjectNode)
                                                Json.parse(snapshot.get("snapshot").toString());
                                if (actor.isStudent()) q = StudentQuestionDTO.from(q);
                                list.add(q);
                            }
                        exam.set("questions", list);
                        out.add(exam);
                    }
                    return out;
                });
    }

    public void deleteExam(Actor currentUser, long id) {
        currentUser.requireStaff();
        database.transaction(
                connection -> {
                    getExamById(connection, currentUser, id);
                    examDAO.deleteExam(connection, id);
                    AccessDAO.audit(connection, currentUser, "EXAM_DELETED", id);
                    return null;
                });
    }

    public Object startAttempt(Actor currentUser, long id) {
        ApiException.require(currentUser.isStudent(), 403, "Chỉ học sinh làm bài.");
        return database.transaction(
                connection -> {
                    // Serialize exam edits and attempt creation on the parent row.
                    ApiException.require(
                            examDAO.getExamAuthor(connection, id) != null,
                            404,
                            "Không tìm thấy đề thi.");
                    ObjectNode e = getExamById(connection, currentUser, id);
                    ApiException.require(
                            e.path("questions").size() > 0, 409, "Đề thi chưa có câu hỏi.");
                    var open = examDAO.getOpenAttemptForUpdate(connection, currentUser.getId(), id);
                    long attempt =
                            open == null
                                    ? examDAO.createAttempt(connection, currentUser.getId(), id)
                                    : ((Number) open.get("id")).longValue();
                    if (open == null)
                        AccessDAO.audit(connection, currentUser, "EXAM_ATTEMPT_STARTED", attempt);
                    return getAttemptById(connection, currentUser, attempt);
                });
    }

    private ObjectNode getAttemptById(Connection connection, Actor currentUser, long id)
            throws SQLException {
        var row = examDAO.getAttemptById(connection, id);
        ApiException.require(row != null, 404, "Không tìm thấy bài làm.");
        if (currentUser.isStudent())
            ApiException.require(
                    ((Number) row.get("studentId")).longValue() == currentUser.getId(),
                    403,
                    "Bài làm ngoài phạm vi.");
        else
            AccessDAO.validateExamAccess(
                    connection, currentUser, ((Number) row.get("examId")).longValue());
        ObjectNode out = Json.MAPPER.valueToTree(row);
        var exam = examDAO.getExamSummary(connection, row.get("examId"));
        out.set("exam", Json.MAPPER.valueToTree(exam));
        if ("IN_PROGRESS".equals(row.get("status"))) {
            long seconds = getElapsedSeconds(connection, id);
            out.put("elapsedSeconds", Math.max(0, seconds));
            out.put("expired", seconds >= ((Number) exam.get("durationSeconds")).longValue());
        }
        ArrayNode questionList = Json.array();
        for (var snapshotRow : examDAO.getQuestionSnapshots(connection, row.get("examId"))) {
            ObjectNode question = (ObjectNode) Json.parse(snapshotRow.get("snapshot").toString());
            questionList.add(StudentQuestionDTO.from(question));
        }
        out.set("questions", questionList);
        ObjectNode answers = Json.object();
        for (var answerRow : examDAO.getAttemptAnswers(connection, id))
            answers.put(
                    answerRow.get("question_id").toString(), answerRow.get("answer").toString());
        out.set("answers", answers);
        var result = examDAO.getAttemptResult(connection, id);
        var publication =
                Database.one(
                        connection,
                        "SELECT results_published FROM exams WHERE id=?",
                        row.get("examId"));
        if (result != null
                && (!currentUser.isStudent()
                        || publication != null
                                && Boolean.TRUE.equals(publication.get("results_published")))) {
            ObjectNode node = Json.MAPPER.valueToTree(result);
            node.set("review", Json.parse(result.get("review").toString()));
            out.set("result", node);
        }
        ObjectNode essayAnswers = Json.object();
        for (var answer :
                Database.rows(
                        connection,
                        "SELECT question_id,content_json,score,feedback,graded_at AS"
                                + " gradedAt,graded_by AS gradedBy FROM exam_essay_answers WHERE"
                                + " attempt_id=?",
                        id)) {
            ObjectNode item = Json.object();
            item.set("content", Json.parse(answer.get("content_json").toString()));
            if (!currentUser.isStudent() || out.has("result"))
                for (String k : List.of("score", "feedback", "gradedAt", "gradedBy"))
                    if (answer.get(k) != null) item.set(k, Json.MAPPER.valueToTree(answer.get(k)));
            essayAnswers.set(answer.get("question_id").toString(), item);
        }
        out.set("essayAnswers", essayAnswers);
        return out;
    }

    public Object getAttemptById(Actor currentUser, long id) {
        return database.read(connection -> getAttemptById(connection, currentUser, id));
    }

    public Object saveAnswers(Actor currentUser, long id, JsonNode input, boolean submit) {
        ApiException.require(currentUser.isStudent(), 403, "Chỉ học sinh nộp bài.");
        List<String> newFiles = new java.util.ArrayList<>();
        try {
            return database.transaction(
                    connection -> {
                        var row = examDAO.getAttemptForUpdate(connection, id, currentUser.getId());
                        ApiException.require(row != null, 404, "Không tìm thấy bài làm.");
                        if (!row.get("status").equals("IN_PROGRESS")) {
                            ApiException.require(submit, 409, "Bài đã nộp.");
                            return getAttemptById(connection, currentUser, id);
                        }
                        long elapsed = getElapsedSeconds(connection, id);
                        boolean expired =
                                elapsed >= ((Number) row.get("duration_seconds")).longValue();
                        ApiException.require(submit || !expired, 409, "Đã hết thời gian làm bài.");
                        var questionList = examDAO.getExamQuestions(connection, row.get("exam_id"));
                        JsonNode incoming = input.path("answers");
                        ApiException.require(incoming.isObject(), 400, "Đáp án không hợp lệ.");
                        Set<String> valid = new HashSet<>();
                        questionList.forEach(
                                question -> valid.add(question.get("question_id").toString()));
                        JsonNode essayInput = input.path("essayAnswers");
                        ApiException.require(
                                essayInput.isMissingNode() || essayInput.isObject(),
                                400,
                                "Bài tự luận không hợp lệ.");
                        var essayFields = essayInput.fieldNames();
                        while (essayFields.hasNext())
                            ApiException.require(
                                    valid.contains(essayFields.next()),
                                    400,
                                    "Câu hỏi ngoài đề thi.");
                        var fields = incoming.fieldNames();
                        while (fields.hasNext())
                            ApiException.require(
                                    valid.contains(fields.next()), 400, "Câu hỏi ngoài đề thi.");
                        if (!expired)
                            for (var question : questionList) {
                                String key = question.get("question_id").toString();
                                if (!incoming.has(key)) continue;
                                ApiException.require(
                                        incoming.path(key).isTextual(),
                                        400,
                                        "Đáp án MCQ phải là chuỗi.");
                                String value = incoming.path(key).asText();
                                JsonNode snap = Json.parse(question.get("snapshot").toString());
                                ApiException.require(
                                        snap.path("type").asText().equals("MCQ"),
                                        400,
                                        "Dùng essayAnswers để lưu câu tự luận.");
                                ApiException.require(
                                        Json.MAPPER
                                                .convertValue(snap.path("options"), List.class)
                                                .contains(value),
                                        400,
                                        "Lựa chọn không hợp lệ.");
                                if (examDAO.updateAnswer(
                                                connection, value, id, question.get("question_id"))
                                        == 0)
                                    examDAO.createAnswer(
                                            connection, id, question.get("question_id"), value);
                            }
                        if (!expired)
                            saveEssayAnswers(
                                    connection,
                                    currentUser,
                                    id,
                                    questionList,
                                    essayInput,
                                    newFiles);
                        if (submit) {
                            boolean mixed =
                                    questionList.stream()
                                            .anyMatch(
                                                    q ->
                                                            Json.parse(q.get("snapshot").toString())
                                                                    .path("type")
                                                                    .asText()
                                                                    .equals("ESSAY"));
                            if (mixed) {
                                for (var question : questionList) {
                                    JsonNode snapshot =
                                            Json.parse(question.get("snapshot").toString());
                                    if (!snapshot.path("type").asText().equals("MCQ")) continue;
                                    var answer =
                                            examDAO.getAnswer(
                                                    connection, id, question.get("question_id"));
                                    boolean ok =
                                            answer != null
                                                    && answer.get("answer")
                                                            .toString()
                                                            .equals(
                                                                    snapshot.path("answer")
                                                                            .asText());
                                    examDAO.updateAnswerCorrectness(
                                            connection, ok, id, question.get("question_id"));
                                }
                                Database.update(
                                        connection,
                                        "UPDATE exam_attempts SET"
                                            + " status='PENDING_GRADING',submitted_at=SYSUTCDATETIME(),elapsed_seconds=?"
                                            + " WHERE id=?",
                                        elapsed,
                                        id);
                                AccessDAO.audit(
                                        connection,
                                        currentUser,
                                        "EXAM_SUBMITTED_PENDING_GRADING",
                                        id);
                                return getAttemptById(connection, currentUser, id);
                            }
                            int correct = 0;
                            ArrayNode review = Json.array();
                            for (var question : questionList) {
                                ObjectNode snap =
                                        (ObjectNode)
                                                Json.parse(question.get("snapshot").toString());
                                var ans =
                                        examDAO.getAnswer(
                                                connection, id, question.get("question_id"));
                                String value = ans == null ? "" : ans.get("answer").toString();
                                boolean ok = value.equals(snap.path("answer").asText());
                                if (ok) correct++;
                                snap.put("studentAnswer", value);
                                snap.put("correct", ok);
                                review.add(snap);
                                examDAO.updateAnswerCorrectness(
                                        connection, ok, id, question.get("question_id"));
                            }
                            double score =
                                    Math.round(correct * 1000.0 / questionList.size()) / 100.0;
                            examDAO.submitAttempt(connection, elapsed, id);
                            examDAO.createResult(
                                    connection,
                                    id,
                                    score,
                                    correct,
                                    questionList.size(),
                                    Json.stringify(review));
                            examDAO.createHistory(
                                    connection,
                                    currentUser.getId(),
                                    examDAO.getExamTitle(connection, row.get("exam_id"))
                                            .get("title"),
                                    score,
                                    elapsed,
                                    Json.stringify(
                                            Map.of(
                                                    "prompt",
                                                    "Đề thi #" + row.get("exam_id"),
                                                    "answer",
                                                    "",
                                                    "feedback",
                                                    "Chấm điểm trên máy chủ")),
                                    id);
                        }
                        if (submit)
                            AccessDAO.audit(connection, currentUser, "EXAM_ATTEMPT_SUBMITTED", id);
                        if (submit) examDAO.notifySubmission(connection, currentUser.getId());
                        return getAttemptById(connection, currentUser, id);
                    });
        } catch (RuntimeException e) {
            newFiles.forEach(storage::delete);
            throw e;
        }
    }

    public Object getAttemptHistory(Actor currentUser) {
        return getAttemptHistory(currentUser, 0, 100);
    }

    public Object getAttemptHistory(Actor currentUser, int offset, int limit) {
        return database.read(
                c ->
                        Database.rows(
                                c,
                                "SELECT a.id,a.exam_id AS examId,a.status,a.started_at AS startedAt"
                                    + " FROM exam_attempts a JOIN exams e ON e.id=a.exam_id WHERE "
                                        + (currentUser.isStudent()
                                                ? "a.student_id=" + currentUser.getId()
                                                : AccessDAO.examScope(currentUser, "e"))
                                        + " ORDER BY a.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS"
                                        + " ONLY",
                                offset,
                                limit));
    }

    private void saveEssayAnswers(
            Connection c,
            Actor actor,
            long attempt,
            List<Map<String, Object>> questions,
            JsonNode incoming,
            List<String> files)
            throws SQLException {
        for (var row : questions) {
            String key = row.get("question_id").toString();
            if (!incoming.has(key)) continue;
            JsonNode snap = Json.parse(row.get("snapshot").toString());
            ApiException.require(
                    snap.path("type").asText().equals("ESSAY"),
                    400,
                    "essayAnswers chỉ nhận câu tự luận.");
            JsonNode input = incoming.path(key);
            if (input.has("content")) input = input.path("content");
            ApiException.require(input.isObject(), 400, "Nội dung bài tự luận không hợp lệ.");
            String text = input.path("text").asText("");
            ApiException.require(text.length() <= 100000, 400, "Bài làm quá dài.");
            var old =
                    Database.one(
                            c,
                            "SELECT content_json FROM exam_essay_answers WHERE attempt_id=? AND"
                                    + " question_id=?",
                            attempt,
                            row.get("question_id"));
            ObjectNode content =
                    old == null
                            ? Json.object()
                            : (ObjectNode) Json.parse(old.get("content_json").toString());
            content.put("text", text);
            if (input.has("image") && input.path("image").isNull())
                content.remove(List.of("image", "imageHash", "fileId", "imageName"));
            if (input.hasNonNull("image")) {
                String data = input.path("image").asText();
                if (data.startsWith("data:")) {
                    var image = ImageStorage.decode(data);
                    String storageKey = storage.store(image);
                    files.add(storageKey);
                    long file =
                            Database.insert(
                                    c,
                                    "INSERT"
                                        + " exam_answer_files(attempt_id,question_id,owner_id,storage_key,sha256,mime,size_bytes)"
                                        + " VALUES(?,?,?,?,?,?,?)",
                                    attempt,
                                    row.get("question_id"),
                                    actor.getId(),
                                    storageKey,
                                    image.hash(),
                                    image.mime(),
                                    image.bytes().length);
                    content.put("image", "/api/exam-files/" + file);
                    content.put("imageHash", image.hash());
                    content.put("fileId", file);
                    String displayName = input.path("imageName").asText("Ảnh bài làm").strip();
                    content.put("imageName", displayName.substring(0, Math.min(200, displayName.length())));
                } else
                    ApiException.require(
                            data.equals(content.path("image").asText()) && !data.isBlank(),
                            400,
                            "Ảnh phải được upload mới hoặc thuộc câu trả lời này.");
            }
            String value = Json.stringify(content);
            if (Database.update(
                            c,
                            "UPDATE exam_essay_answers SET content_json=? WHERE attempt_id=? AND"
                                    + " question_id=?",
                            value,
                            attempt,
                            row.get("question_id"))
                    == 0)
                Database.update(
                        c,
                        "INSERT exam_essay_answers(attempt_id,question_id,content_json)"
                                + " VALUES(?,?,?)",
                        attempt,
                        row.get("question_id"),
                        value);
        }
    }

    public Map<String, Object> getFile(Actor actor, long id) {
        return database.read(
                c -> {
                    var file =
                            Database.one(
                                    c,
                                    "SELECT attempt_id,storage_key,mime FROM exam_answer_files"
                                            + " WHERE id=?",
                                    id);
                    ApiException.require(file != null, 404, "Không tìm thấy ảnh.");
                    var attempt =
                            examDAO.getAttemptById(
                                    c, ((Number) file.get("attempt_id")).longValue());
                    ApiException.require(attempt != null, 404, "Không tìm thấy bài làm.");
                    if (actor.isStudent())
                        ApiException.require(
                                ((Number) attempt.get("studentId")).longValue() == actor.getId(),
                                403,
                                "Ảnh ngoài phạm vi.");
                    else
                        AccessDAO.validateExamAccess(
                                c, actor, ((Number) attempt.get("examId")).longValue());
                    return Map.of(
                            "bytes",
                            storage.read(file.get("storage_key").toString()),
                            "mime",
                            file.get("mime"));
                });
    }

    public Object gradeAttempt(Actor actor, long id, JsonNode input) {
        actor.requireStaff();
        return database.transaction(
                c -> {
                    var row =
                            Database.one(
                                    c,
                                    "SELECT * FROM exam_attempts WITH(UPDLOCK,HOLDLOCK) WHERE id=?",
                                    id);
                    ApiException.require(row != null, 404, "Không tìm thấy bài làm.");
                    AccessDAO.validateExamAccess(
                            c, actor, ((Number) row.get("exam_id")).longValue());
                    ApiException.require(
                            "PENDING_GRADING".equals(row.get("status")),
                            409,
                            "Bài chưa chờ chấm hoặc đã chấm xong.");
                    JsonNode grades = input.path("grades");
                    ApiException.require(
                            grades.isArray() && grades.size() > 0 && grades.size() <= 100,
                            400,
                            "Danh sách chấm không hợp lệ.");
                    var questions = examDAO.getExamQuestions(c, row.get("exam_id"));
                    Set<Long> seen = new HashSet<>();
                    for (var grade : grades) {
                        long question = Json.id(grade, "questionId");
                        ApiException.require(
                                seen.add(question)
                                        && questions.stream()
                                                .anyMatch(
                                                        q ->
                                                                ((Number) q.get("question_id"))
                                                                                        .longValue()
                                                                                == question
                                                                        && Json.parse(
                                                                                        q.get(
                                                                                                        "snapshot")
                                                                                                .toString())
                                                                                .path("type")
                                                                                .asText()
                                                                                .equals("ESSAY")),
                                400,
                                "Câu tự luận ngoài đề hoặc trùng.");
                        ApiException.require(
                                grade.path("score").isNumber()
                                        && Double.isFinite(grade.path("score").asDouble())
                                        && grade.path("score").asDouble() >= 0
                                        && grade.path("score").asDouble() <= 10,
                                400,
                                "Điểm phải từ 0 đến 10.");
                        String feedback = Json.text(grade, "feedback", 10000);
                        if (Database.one(
                                        c,
                                        "SELECT id FROM exam_essay_answers WHERE attempt_id=? AND"
                                                + " question_id=?",
                                        id,
                                        question)
                                == null)
                            Database.update(
                                    c,
                                    "INSERT exam_essay_answers(attempt_id,question_id,content_json)"
                                            + " VALUES(?,?,N'{}')",
                                    id,
                                    question);
                        Database.update(
                                c,
                                "INSERT"
                                    + " exam_grade_audit(attempt_id,question_id,actor_id,previous_score,score,feedback)"
                                    + " SELECT attempt_id,question_id,?,score,?,? FROM"
                                    + " exam_essay_answers WHERE attempt_id=? AND question_id=?",
                                actor.getId(),
                                grade.path("score").asDouble(),
                                feedback,
                                id,
                                question);
                        Database.update(
                                c,
                                "UPDATE exam_essay_answers SET"
                                    + " score=?,feedback=?,graded_by=?,graded_at=SYSUTCDATETIME()"
                                    + " WHERE attempt_id=? AND question_id=?",
                                grade.path("score").asDouble(),
                                feedback,
                                actor.getId(),
                                id,
                                question);
                        AccessDAO.audit(c, actor, "EXAM_ESSAY_GRADED", id);
                    }
                    finalizeMixed(c, id, row, questions);
                    return getAttemptById(c, actor, id);
                });
    }

    private void finalizeMixed(
            Connection c, long id, Map<String, Object> attempt, List<Map<String, Object>> questions)
            throws SQLException {
        int correct = 0;
        double earned = 0;
        ArrayNode review = Json.array();
        for (var row : questions) {
            ObjectNode q = (ObjectNode) Json.parse(row.get("snapshot").toString());
            if (q.path("type").asText().equals("ESSAY")) {
                var answer =
                        Database.one(
                                c,
                                "SELECT content_json,score,feedback FROM exam_essay_answers WHERE"
                                        + " attempt_id=? AND question_id=?",
                                id,
                                row.get("question_id"));
                if (answer == null || answer.get("score") == null) return;
                double score = ((Number) answer.get("score")).doubleValue();
                earned += score;
                q.set("studentAnswer", Json.parse(answer.get("content_json").toString()));
                q.put("score", score);
                q.put("feedback", answer.get("feedback").toString());
            } else {
                var answer = examDAO.getAnswer(c, id, row.get("question_id"));
                String value = answer == null ? "" : answer.get("answer").toString();
                boolean ok = value.equals(q.path("answer").asText());
                if (ok) {
                    correct++;
                    earned += 10;
                }
                q.put("studentAnswer", value);
                q.put("correct", ok);
                q.put("score", ok ? 10 : 0);
                examDAO.updateAnswerCorrectness(c, ok, id, row.get("question_id"));
            }
            q.put("weight", 1);
            q.put("maxScore", 10);
            review.add(q);
        }
        double score = Math.round(earned * 100 / questions.size()) / 100.0;
        examDAO.createResult(c, id, score, correct, questions.size(), Json.stringify(review));
        Database.update(c, "UPDATE exam_attempts SET status='GRADED' WHERE id=?", id);
        examDAO.createHistory(
                c,
                ((Number) attempt.get("student_id")).longValue(),
                examDAO.getExamTitle(c, attempt.get("exam_id")).get("title"),
                score,
                ((Number) attempt.get("elapsed_seconds")).longValue(),
                Json.stringify(
                        Map.of(
                                "gradingRule",
                                "EQUAL_QUESTION_WEIGHT",
                                "questionCount",
                                questions.size())),
                id);
        examDAO.notifySubmission(c, ((Number) attempt.get("student_id")).longValue());
    }

    public Object publishResults(Actor actor, long examId) {
        actor.requireStaff();
        return database.transaction(
                c -> {
                    AccessDAO.validateExamAccess(c, actor, examId);
                    Database.update(c, "UPDATE exams SET results_published=1 WHERE id=?", examId);
                    AccessDAO.audit(c, actor, "EXAM_RESULTS_PUBLISHED", examId);
                    return Map.of("published", true);
                });
    }

    private long getElapsedSeconds(Connection connection, long id) throws SQLException {
        return ((Number) examDAO.getElapsedSeconds(connection, id).get("seconds")).longValue();
    }
}
