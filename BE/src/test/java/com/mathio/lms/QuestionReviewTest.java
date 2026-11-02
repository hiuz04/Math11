package com.mathio.lms;
import com.mathio.lms.DAO.QuestionDAO;
import com.mathio.lms.model.Json;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class QuestionReviewTest {
 @Test void reviewBindsContentButNotTemporaryId() {
  var q=Json.object();
  q.put("title","Câu hỏi");q.put("content","2 + 2 = ?");q.put("chapter","Chương 1");q.put("topic","Toán");
  q.put("type","MCQ");q.put("difficulty","EASY");q.put("cognitiveLevel","KNOWLEDGE");q.put("answer","4");
  q.set("options",Json.array().add("3").add("4"));
  String original=QuestionDAO.reviewHash(q);
  q.put("id",42);q.put("published",true);
  assertEquals(original,QuestionDAO.reviewHash(q));
  q.put("content","3 + 1 = ?");assertNotEquals(original,QuestionDAO.reviewHash(q));
 }
}
