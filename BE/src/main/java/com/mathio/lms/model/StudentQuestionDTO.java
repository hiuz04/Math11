package com.mathio.lms.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/** Allowlist: new author/grading fields can never leak through a stored snapshot. */
public final class StudentQuestionDTO {
    public static ObjectNode from(JsonNode question) {
        ObjectNode out = Json.object();
        for (String field : List.of("id", "title", "content", "chapter", "topic", "type",
                "difficulty", "cognitiveLevel", "options", "courseId", "published"))
            if (question.has(field)) out.set(field, question.get(field).deepCopy());
        return out;
    }
    private StudentQuestionDTO() {}
}
