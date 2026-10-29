package com.mathio.lms.DAO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mathio.lms.model.Actor;
import com.mathio.lms.model.ApiException;
import com.mathio.lms.model.Json;
import com.mathio.lms.model.Question;
import com.mathio.lms.model.RichText;
import com.mathio.lms.model.StudentQuestionDTO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class QuestionDAO {
    private final Database database;

    public QuestionDAO(Database database) {
        this.database = database;
    }

    public static ObjectNode validateQuestion(JsonNode input) {
        ObjectNode question = Json.object();
        for (String k :
                List.of(
                        "title",
                        "content",
                        "chapter",
                        "topic",
                        "type",
                        "difficulty",
                        "cognitiveLevel",
                        "answer"))
            question.put(
                    k,
                    Json.text(input, k, k.equals("content") || k.equals("answer") ? 100000 : 200));
        ApiException.require(
                Set.of("MCQ", "ESSAY").contains(question.path("type").asText())
                        && Set.of("EASY", "MEDIUM", "HARD")
                                .contains(question.path("difficulty").asText())
                        && Set.of("KNOWLEDGE", "UNDERSTANDING", "APPLICATION")
                                .contains(question.path("cognitiveLevel").asText()),
                400,
                "Metadata câu hỏi không hợp lệ.");
        question.put("content", RichText.clean(question.path("content").asText()));
        question.put("answer", RichText.clean(question.path("answer").asText()));
        ApiException.require(RichText.hasContent(question.path("content").asText())
                && RichText.hasContent(question.path("answer").asText()), 400,
                "Nội dung và hướng dẫn chấm không được rỗng.");
        if (question.path("type").asText().equals("MCQ")) {
            var options = input.path("options");
            ApiException.require(
                    options.isArray() && options.size() >= 2 && options.size() <= 8,
                    400,
                    "Câu trắc nghiệm phải có 2–8 lựa chọn.");
            ArrayNode clean = Json.array();
            for (JsonNode o : options) {
                ApiException.require(
                        o.isTextual() && !o.asText().isBlank() && o.asText().length() <= 4000,
                        400,
                        "Lựa chọn không hợp lệ.");
                clean.add(RichText.clean(o.asText()));
            }
            Set<String> optionValues = new HashSet<>();
            for (JsonNode option : clean) {
                optionValues.add(option.asText());
            }
            ApiException.require(
                    optionValues.size() == clean.size()
                            && optionValues.contains(question.path("answer").asText()),
                    400,
                    "Đáp án phải khớp một lựa chọn duy nhất.");
            question.set("options", clean);
        }
        return question;
    }

    public ObjectNode getQuestionById(
            Connection connection, Actor currentUser, long id, boolean answer) throws SQLException {
        ApiException.require(
                Database.one(
                                connection,
                                "SELECT q.id FROM questions q WHERE q.id=? AND q.deleted=0 AND ("
                                        + AccessDAO.questionScope(currentUser, "q")
                                        + ")",
                                id)
                        != null,
                404,
                "Không tìm thấy câu hỏi được phép.");
        ObjectNode question = getQuestionReference(connection, id);
        if (currentUser.isStudent() || !answer) question = StudentQuestionDTO.from(question);
        return question;
    }

    /** Server-only reference. Call only after authorizing the owning submission/exam. */
    public ObjectNode getQuestionReference(Connection connection, long id) throws SQLException {
        var row =
                Database.one(
                        connection,
                        """
                            SELECT q.id,
                                   q.author_id AS authorId,
                                   q.title,
                                   q.content,
                                   c.title AS chapter,
                                   t.title AS topic,
                                   q.type,
                                   q.difficulty,
                                   q.cognitive_level AS cognitiveLevel,
                                   q.answer, q.course_id AS courseId, q.published
                            FROM questions q
                            JOIN chapters c ON c.id=q.chapter_id
                            JOIN topics t ON t.id=q.topic_id
                            WHERE q.id=? AND q.deleted=0
                        """,
                        id);
        ApiException.require(row != null, 404, "Không tìm thấy câu hỏi.");
        ObjectNode question = Json.MAPPER.valueToTree(mapQuestion(row));
        question.remove("authorId");
        if (row.get("courseId") != null)
            question.set("courseId", Json.MAPPER.valueToTree(row.get("courseId")));
        question.put("published", Boolean.TRUE.equals(row.get("published")));
        var options =
                Database.rows(
                        connection,
                        """
                            SELECT content
                            FROM question_options
                            WHERE question_id=?
                            ORDER BY position
                        """,
                        id);
        if (!options.isEmpty()) {
            ArrayNode optionList = Json.array();
            options.forEach(option -> optionList.add(option.get("content").toString()));
            question.set("options", optionList);
        }
        return question;
    }

    public Object getQuestionById(Actor currentUser, long id) {
        return database.read(c -> getQuestionById(c, currentUser, id, !currentUser.isStudent()));
    }

    public Object getQuestions(Actor currentUser) {
        return getQuestions(currentUser, 0, 100);
    }

    public Object getQuestions(Actor currentUser, int offset, int limit) {
        return database.read(
                c -> {
                    ArrayNode out = Json.array();
                    // Two batched queries replace one options query per question.
                    var rows =
                            Database.rows(
                                    c,
                                    "SELECT q.id, q.title, q.content, ch.title AS chapter, t.title"
                                        + " AS topic, q.type, q.difficulty, q.cognitive_level AS"
                                        + " cognitiveLevel, q.answer, q.course_id AS courseId,"
                                        + " q.published FROM questions q JOIN chapters ch ON"
                                        + " ch.id=q.chapter_id JOIN topics t ON t.id=q.topic_id"
                                        + " WHERE q.deleted=0 AND ("
                                            + AccessDAO.questionScope(currentUser, "q")
                                            + ") ORDER BY q.id OFFSET ? ROWS FETCH NEXT ? ROWS"
                                            + " ONLY",
                                    offset,
                                    limit);
                    if (rows.isEmpty()) return out;
                    var options =
                            Database.rows(
                                    c,
                                    "SELECT o.question_id,o.content FROM question_options o WHERE"
                                            + " o.question_id IN ("
                                            + String.join(
                                                    ",",
                                                    java.util.Collections.nCopies(rows.size(), "?"))
                                            + ") ORDER BY o.question_id,o.position",
                                    rows.stream().map(r -> r.get("id")).toArray());
                    for (var row : rows) {
                        ObjectNode q = Json.MAPPER.valueToTree(mapQuestion(row));
                        if (row.get("courseId") != null)
                            q.set("courseId", Json.MAPPER.valueToTree(row.get("courseId")));
                        q.put("published", Boolean.TRUE.equals(row.get("published")));
                        if (currentUser.isStudent()) q = StudentQuestionDTO.from(q);
                        ArrayNode choices = Json.array();
                        options.stream()
                                .filter(o -> o.get("question_id").equals(row.get("id")))
                                .forEach(o -> choices.add(o.get("content").toString()));
                        if (!choices.isEmpty()) q.set("options", choices);
                        out.add(q);
                    }
                    return out;
                });
    }

    public long saveQuestion(Connection connection, Actor currentUser, JsonNode input)
            throws SQLException {
        currentUser.requireStaff();
        ObjectNode question = validateQuestion(input);
        var topic =
                Database.one(
                        connection,
                        """
                            SELECT t.id,
                                   t.chapter_id
                            FROM topics t
                            JOIN chapters ch ON ch.id=t.chapter_id
                            WHERE ch.title=? AND t.title=?
                        """,
                        question.path("chapter").asText(),
                        question.path("topic").asText());
        ApiException.require(topic != null, 400, "Chương/chủ đề không hợp lệ.");
        long id = input.path("id").asLong();
        var old =
                id > 0
                        ? Database.one(
                                connection,
                                """
                                    SELECT author_id
                                    FROM questions
                                    WHERE id=? AND deleted=0
                                """,
                                id)
                        : null;
        ApiException.require(id <= 0 || old != null, 404, "Không tìm thấy câu hỏi cần sửa.");
        if (old == null)
            id =
                    Database.insert(
                            connection,
                            """
    INSERT questions(author_id,chapter_id,topic_id,title,content,type,difficulty,cognitive_level,answer)
    VALUES(?,?,?,?,?,?,?,?,?)
""",
                            currentUser.getId(),
                            topic.get("chapter_id"),
                            topic.get("id"),
                            question.path("title").asText(),
                            question.path("content").asText(),
                            question.path("type").asText(),
                            question.path("difficulty").asText(),
                            question.path("cognitiveLevel").asText(),
                            question.path("answer").asText());
        else {
            AccessDAO.validateAuthorAccess(currentUser, old.get("author_id"));
            Database.update(
                    connection,
                    """
    UPDATE questions
    SET chapter_id=?,topic_id=?,title=?,content=?,type=?,difficulty=?,cognitive_level=?,answer=?
    WHERE id=?
""",
                    topic.get("chapter_id"),
                    topic.get("id"),
                    question.path("title").asText(),
                    question.path("content").asText(),
                    question.path("type").asText(),
                    question.path("difficulty").asText(),
                    question.path("cognitiveLevel").asText(),
                    question.path("answer").asText(),
                    id);
            Database.update(
                    connection,
                    """
                        DELETE
                        FROM question_options
                        WHERE question_id=?
                    """,
                    id);
        }
        if (old != null) {
            ApiException.require(
                    Database.one(
                                    connection,
                                    "SELECT q.id FROM questions q WHERE q.id=? AND ("
                                            + AccessDAO.questionScope(currentUser, "q")
                                            + ")",
                                    id)
                            != null,
                    403,
                    "Câu hỏi ngoài phạm vi quản lý.");
        }
        Long course =
                input.has("courseId") || old == null
                        ? AccessDAO.resolveCourse(connection, currentUser, input)
                        : null;
        if (course != null)
            Database.update(connection, "UPDATE questions SET course_id=? WHERE id=?", course, id);
        if (input.has("published")) {
            ApiException.require(
                    input.path("published").isBoolean(), 400, "published phải là boolean.");
            Database.update(
                    connection,
                    "UPDATE questions SET published=? WHERE id=?",
                    input.path("published").asBoolean(),
                    id);
        }
        AccessDAO.audit(
                connection, currentUser, old == null ? "QUESTION_CREATED" : "QUESTION_UPDATED", id);
        int position = 0;
        for (var o : question.path("options"))
            Database.update(
                    connection,
                    """
                        INSERT question_options(question_id,position,content)
                        VALUES(?,?,?)
                    """,
                    id,
                    position++,
                    o.asText());
        return id;
    }

    public Object saveQuestions(Actor currentUser, JsonNode input) {
        currentUser.requireStaff();
        var list = input.path("questions");
        ApiException.require(
                list.isArray() && list.size() > 0 && list.size() <= 100,
                400,
                "Danh sách câu hỏi không hợp lệ.");
        return database.transaction(
                connection -> {
                    ArrayNode result = Json.array();
                    Set<String> contents = new HashSet<>();
                    Set<Long> ids = new HashSet<>();
                    for (var question : list) {
                        ApiException.require(contents.add(question.path("type").asText() + ":" + RichText.clean(question.path("content").asText()).trim()), 400, "Danh sách có câu hỏi trùng nội dung.");
                        long suppliedId = question.path("id").asLong();
                        ApiException.require(suppliedId <= 0 || ids.add(suppliedId), 400, "Danh sách có ID câu hỏi trùng.");
                        result.add(
                                getQuestionById(
                                        connection,
                                        currentUser,
                                        saveQuestion(connection, currentUser, question),
                                        true));
                    }
                    return result;
                });
    }

    public void deleteQuestion(Actor currentUser, long id) {
        currentUser.requireStaff();
        database.transaction(
                connection -> {
                    var row =
                            Database.one(
                                    connection,
                                    """
                                        SELECT author_id
                                        FROM questions
                                        WHERE id=? AND deleted=0
                                    """,
                                    id);
                    ApiException.require(row != null, 404, "Không tìm thấy câu hỏi.");
                    AccessDAO.validateAuthorAccess(currentUser, row.get("author_id"));
                    Database.update(
                            connection,
                            """
                                UPDATE questions
                                SET deleted=1
                                WHERE id=?
                            """,
                            id);
                    AccessDAO.audit(connection, currentUser, "QUESTION_DELETED", id);
                    return null;
                });
    }

    public Object getExercises(Actor currentUser) {
        return database.read(
                c -> {
                    ArrayNode out = Json.array();
                    var essayStats = Database.rows(c,
                            "SELECT question_id, MAX(TRY_CONVERT(float,JSON_VALUE(state_json,'$.finalResult.score'))) AS bestScore,"
                            + " MAX(CASE WHEN status IN ('SUBMITTED','NEEDS_TUTOR','RETRY_REQUIRED','NEEDS_REVISION') THEN 1 ELSE 0 END) AS inProgress"
                            + " FROM essay_submissions WHERE student_id=? GROUP BY question_id", currentUser.getId());
                    for (var q : (ArrayNode) getQuestions(currentUser)) {
                        if (q.path("type").asText().equals("ESSAY")) {
                            ((ObjectNode) q).remove("answer");
                            ((ObjectNode) q).put("kind", "essay");
                            ((ObjectNode) q).put("questionCount", 1);
                            ((ObjectNode) q).putNull("durationSeconds");
                            ((ObjectNode) q).put("status", "NOT_STARTED");
                            for (var stats : essayStats) if (stats.get("question_id").toString().equals(q.path("id").asText())) {
                                ((ObjectNode) q).put("status", ((Number) stats.get("inProgress")).intValue() == 1 ? "IN_PROGRESS" : "COMPLETED");
                                if (stats.get("bestScore") != null) ((ObjectNode) q).set("bestScore", Json.MAPPER.valueToTree(stats.get("bestScore")));
                            }
                            out.add(q);
                        }
                    }
                    for (var row :
                            Database.rows(
                                    c,
                                    "SELECT TOP (100) e.id,e.title,e.chapter,e.duration_seconds AS durationSeconds,"
                                        + " (SELECT COUNT(*) FROM exam_questions eq WHERE eq.exam_id=e.id) AS questionCount,"
                                        + " (SELECT CASE MAX(CASE JSON_VALUE(eq.snapshot,'$.difficulty') WHEN 'HARD' THEN 3 WHEN 'MEDIUM' THEN 2 WHEN 'EASY' THEN 1 END) WHEN 3 THEN 'HARD' WHEN 2 THEN 'MEDIUM' WHEN 1 THEN 'EASY' END FROM exam_questions eq WHERE eq.exam_id=e.id) AS difficulty,"
                                        + " (SELECT MAX(r.score) FROM exam_attempts a JOIN exam_results r ON r.attempt_id=a.id WHERE a.exam_id=e.id AND a.student_id=? AND e.results_published=1) AS bestScore,"
                                        + " CASE WHEN EXISTS(SELECT 1 FROM exam_attempts a WHERE a.exam_id=e.id AND a.student_id=? AND a.status='IN_PROGRESS') THEN 'IN_PROGRESS'"
                                        + " WHEN EXISTS(SELECT 1 FROM exam_attempts a WHERE a.exam_id=e.id AND a.student_id=?) THEN 'COMPLETED' ELSE 'NOT_STARTED' END AS status"
                                        + " FROM exams e WHERE e.deleted=0 AND ("
                                            + AccessDAO.examScope(currentUser, "e")
                                            + ") ORDER BY e.id DESC", currentUser.getId(), currentUser.getId(), currentUser.getId())) {
                        ObjectNode e = Json.MAPPER.valueToTree(row);
                        e.put("kind", "quiz");
                        out.add(e);
                    }
                    return out;
                });
    }

    private Question mapQuestion(Map<String, Object> row) {
        Question result = new Question();
        result.setId(row.get("id") == null ? null : ((Number) row.get("id")).longValue());
        result.setTitle((String) row.get("title"));
        result.setContent((String) row.get("content"));
        result.setChapter((String) row.get("chapter"));
        result.setTopic((String) row.get("topic"));
        result.setType((String) row.get("type"));
        result.setDifficulty((String) row.get("difficulty"));
        result.setCognitiveLevel((String) row.get("cognitiveLevel"));
        result.setAnswer((String) row.get("answer"));
        return result;
    }
}
