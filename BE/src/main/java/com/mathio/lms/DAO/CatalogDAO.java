package com.mathio.lms.DAO;

import com.fasterxml.jackson.databind.JsonNode;
import com.mathio.lms.model.Actor;
import com.mathio.lms.model.ApiException;
import com.mathio.lms.model.Json;
import com.mathio.lms.model.LessonView;
import com.mathio.lms.model.RichText;

import java.util.Map;
import java.util.Set;

public class CatalogDAO {
    private final Database database;

    public CatalogDAO(Database database) {
        this.database = database;
    }

    public Object getCourses(Actor currentUser) {
        return getCourses(currentUser, 0, 100);
    }

    public Object getCourses(Actor currentUser, int offset, int limit) { return getCourses(currentUser, offset, limit, "", ""); }

    public Object getCourses(Actor currentUser, int offset, int limit, String query, String published) {
        return database.read(
                connection ->
                        Database.rows(
                                connection,
                                """
    SELECT c.id,
           c.title,
           c.description,
           c.teacher_id AS teacherId,
           c.published,
           c.created_at AS createdAt
    FROM courses c
    WHERE c.deleted=0 AND (?=1 OR c.teacher_id=? OR c.published=1 AND EXISTS(SELECT 1
    FROM course_enrollments e
    WHERE e.course_id=c.id AND e.student_id=?))
    AND (?='' OR CHARINDEX(?,c.title+N' '+COALESCE(c.description,N''))>0)
    AND (?='' OR c.published=CASE WHEN ?='true' THEN 1 ELSE 0 END)
    ORDER BY c.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
""",
                                currentUser.isAdmin(),
                                currentUser.getId(),
                                currentUser.getId(),
                                query, query, published, published, offset,
                                limit));
    }

    public Object getChapters(Actor actor, long course) {
        return database.read(
                c -> {
                    if (course > 0) AccessDAO.validateCourseAccess(c, actor, course);
                    String sql =
                            """
SELECT ch.id,ch.title,ch.position,
    (SELECT COUNT(*) FROM lessons l JOIN courses co ON co.id=l.course_id
     WHERE l.chapter_id=ch.id AND l.deleted=0 AND co.deleted=0
       AND (?=0 OR l.course_id=?)
       AND (?=1 OR co.teacher_id=? OR (l.published=1 AND co.published=1 AND EXISTS(
           SELECT 1 FROM course_enrollments n WHERE n.course_id=co.id AND n.student_id=?)))) AS lessonCount,
    (SELECT COUNT(*) FROM questions q
     WHERE q.chapter_id=ch.id AND q.deleted=0 AND (?=0 OR q.course_id=?) AND (%s)) AS exerciseCount,
    COALESCE((SELECT AVG(CAST(COALESCE(p.completed,0) AS FLOAT))*100
     FROM lessons l JOIN courses co ON co.id=l.course_id
     LEFT JOIN lesson_progress p ON p.lesson_id=l.id AND p.student_id=?
     WHERE l.chapter_id=ch.id AND l.deleted=0 AND l.published=1 AND co.deleted=0 AND co.published=1
       AND (?=0 OR l.course_id=?) AND EXISTS(
           SELECT 1 FROM course_enrollments n WHERE n.course_id=co.id AND n.student_id=?)),0) AS progress
FROM chapters ch ORDER BY ch.position
"""
                                    .formatted(AccessDAO.questionScope(actor, "q"));
                    return Database.rows(
                            c,
                            sql,
                            course,
                            course,
                            actor.isAdmin(),
                            actor.getId(),
                            actor.getId(),
                            course,
                            course,
                            actor.getId(),
                            course,
                            course,
                            actor.getId());
                });
    }

    public Object saveCatalog(Actor actor, boolean topic, long catalogId, JsonNode input, boolean delete) {
        actor.requireAdmin();
        return database.transaction(c -> {
            long id = catalogId;
            String table = topic ? "topics" : "chapters";
            var old = id > 0 ? Database.one(c, "SELECT * FROM " + table + " WITH(UPDLOCK,HOLDLOCK) WHERE id=?", id) : null;
            ApiException.require(id == 0 || old != null, 404, "Không tìm thấy danh mục.");
            if (delete) {
                ApiException.require(id > 0, 400, "Hãy chọn danh mục.");
                String reference = topic
                    ? "SELECT TOP (1) id FROM questions WHERE topic_id=? UNION ALL SELECT TOP (1) id FROM exams WHERE topic=?"
                    : "SELECT TOP (1) id FROM topics WHERE chapter_id=? UNION ALL SELECT TOP (1) id FROM lessons WHERE chapter_id=? UNION ALL SELECT TOP (1) id FROM questions WHERE chapter_id=? UNION ALL SELECT TOP (1) id FROM exams WHERE chapter=?";
                var refs = topic ? Database.rows(c, reference, id, old.get("title")) : Database.rows(c, reference, id, id, id, old.get("title"));
                ApiException.require(refs.isEmpty(),409,"Danh mục đang được sử dụng; không thể xóa.");
                Database.update(c,"DELETE FROM " + table + " WHERE id=?",id);
            } else {
                String title = Json.text(input,"title",200);
                if (old != null && !title.equals(old.get("title"))) {
                    ApiException.require(Database.rows(c, "SELECT TOP (1) id FROM exams WHERE " + (topic ? "topic" : "chapter") + "=?", old.get("title")).isEmpty(),409,"Danh mục đã gắn với đề thi; không thể đổi tên làm sai snapshot.");
                }
                if (topic) {
                    long chapter = Json.id(input,"chapterId");
                    ApiException.require(Database.one(c,"SELECT id FROM chapters WITH(UPDLOCK,HOLDLOCK) WHERE id=?",chapter)!=null,400,"Chương không hợp lệ.");
                    ApiException.require(old==null || ((Number)old.get("chapter_id")).longValue()==chapter || Database.rows(c,"SELECT TOP (1) id FROM questions WHERE topic_id=? UNION ALL SELECT TOP (1) id FROM exams WHERE topic=?",id,old.get("title")).isEmpty(),409,"Chủ đề đang được sử dụng; không thể chuyển chương.");
                    ApiException.require(Database.one(c,"SELECT id FROM topics WHERE chapter_id=? AND title=? AND id<>?",chapter,title,id)==null,409,"Tên chủ đề đã tồn tại.");
                    if(id==0) id=Database.insert(c,"INSERT topics(chapter_id,title) VALUES(?,?)",chapter,title);
                    else Database.update(c,"UPDATE topics SET chapter_id=?,title=? WHERE id=?",chapter,title,id);
                    if (input.has("position")) {
                        ApiException.require(input.path("position").isIntegralNumber() && input.path("position").canConvertToInt() && input.path("position").asInt()>=0,400,"Thứ tự chủ đề không hợp lệ.");
                        var column=Database.one(c,"SELECT COL_LENGTH('dbo.topics','position') AS length");
                        boolean supported=column!=null && column.get("length")!=null;
                        ApiException.require(supported || input.path("position").asInt()==0,409,"Cần áp dụng migration V003 trước khi đặt thứ tự chủ đề.");
                        if(supported) Database.update(c,"UPDATE topics SET position=? WHERE id=?",input.path("position").asInt(),id);
                    }
                } else {
                    ApiException.require(input.path("position").isIntegralNumber() && input.path("position").canConvertToInt() && input.path("position").asInt()>=0,400,"Thứ tự chương không hợp lệ.");
                    ApiException.require(Database.one(c,"SELECT id FROM chapters WHERE title=? AND id<>?",title,id)==null,409,"Tên chương đã tồn tại.");
                    if(id==0) {
                        var subject=Database.rows(c,"SELECT TOP (2) id FROM subjects ORDER BY id");
                        ApiException.require(subject.size()==1,409,"Cần đúng một môn học để tạo chương.");
                        id=Database.insert(c,"INSERT chapters(subject_id,title,position) VALUES(?,?,?)",subject.get(0).get("id"),title,input.path("position").asInt());
                    } else Database.update(c,"UPDATE chapters SET title=?,position=? WHERE id=?",title,input.path("position").asInt(),id);
                }
            }
            AccessDAO.audit(c,actor,delete?"CATALOG_DELETED":"CATALOG_SAVED",id);
            return Map.of("id",id,"saved",true);
        });
    }

    public Object getTopics() {
        return database.read(c -> {
            var column=Database.one(c,"SELECT COL_LENGTH('dbo.topics','position') AS length");
            boolean ordered=column!=null && column.get("length")!=null;
            return Database.rows(c,"SELECT id,chapter_id AS chapterId,title,"+(ordered?"position":"0 AS position")+" FROM topics ORDER BY "+(ordered?"position,id":"id"));
        });
    }

    public Object getLessons(Actor currentUser, long chapter) {
        return getLessons(currentUser, chapter, 0, 100);
    }

    public Object getLessons(Actor currentUser, long chapter, int offset, int limit) { return getLessons(currentUser, chapter, offset, limit, "", "", "", 0); }

    public Object getLessons(Actor currentUser, long chapter, int offset, int limit, String query, String published, String chapterTitle, long course) {
        return database.read(
                connection ->
                        Database.rows(
                                connection,
                                """
    SELECT l.id,
           l.title,
           l.description,
           l.course_id AS courseId,
           l.chapter_id AS chapterId,
           ch.title AS chapter,
           l.duration_seconds AS durationSeconds,
           l.published,
           l.updated_at AS updatedAt,
           COALESCE(p.completed,0) AS completed
    FROM lessons l
    JOIN courses co ON co.id=l.course_id AND co.deleted=0
    JOIN chapters ch ON ch.id=l.chapter_id
    LEFT JOIN lesson_progress p ON p.lesson_id=l.id AND p.student_id=?
    WHERE l.deleted=0 AND (?=0 OR l.chapter_id=?) AND (?=1 OR EXISTS(SELECT 1
    FROM courses owned
    WHERE owned.id=l.course_id AND owned.teacher_id=? AND owned.deleted=0) OR l.published=1 AND EXISTS(SELECT 1
    FROM course_enrollments e
    JOIN courses co ON co.id=e.course_id AND co.deleted=0 AND co.published=1
    WHERE e.course_id=l.course_id AND e.student_id=?))
    AND (?='' OR CHARINDEX(?,l.title+N' '+ch.title)>0)
    AND (?='' OR l.published=CASE WHEN ?='true' THEN 1 ELSE 0 END)
    AND (?='' OR ch.title=?) AND (?=0 OR l.course_id=?)
    ORDER BY l.id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
""",
                                currentUser.getId(),
                                chapter,
                                chapter,
                                currentUser.isAdmin(),
                                currentUser.getId(),
                                currentUser.getId(),
                                query, query, published, published, chapterTitle, chapterTitle, course, course, offset,
                                limit));
    }

    public Object getLessonById(Actor currentUser, long id) {
        return database.read(
                connection -> {
                    var l =
                            Database.one(
                                    connection,
                                    """
    SELECT l.id,
           l.title,
           l.description,
           l.objectives,
           l.content,
           l.video_url AS videoUrl,
           l.content_type AS contentType,
           l.course_id AS courseId,
           l.chapter_id AS chapterId,
           l.published,
           l.author_id AS authorId,
           COALESCE(p.completed,0) AS completed,
           COALESCE(p.elapsed_seconds,0) AS elapsedSeconds
    FROM lessons l
    LEFT JOIN lesson_progress p ON p.lesson_id=l.id AND p.student_id=?
    WHERE l.id=? AND l.deleted=0
""",
                                    currentUser.getId(),
                                    id);
                    ApiException.require(l != null, 404, "Không tìm thấy bài giảng.");
                    AccessDAO.validateCourseAccess(
                            connection, currentUser, ((Number) l.get("courseId")).longValue());
                    ApiException.require(
                            !currentUser.isStudent() || Boolean.TRUE.equals(l.get("published")),
                            404,
                            "Bài giảng chưa xuất bản.");
                    return mapLessonView(l);
                });
    }

    public Object saveLesson(Actor currentUser, long id, JsonNode input) {
        currentUser.requireStaff();
        return database.transaction(
                connection -> {
                    long course = input.path("courseId").asLong();
                    if (course == 0) {
                        var first =
                                Database.one(
                                        connection,
                                        """
                                            SELECT TOP (1) id
                                            FROM courses
                                            WHERE deleted=0 AND (?=1 OR teacher_id=?)
                                            ORDER BY id
                                        """,
                                        currentUser.isAdmin(),
                                        currentUser.getId());
                        ApiException.require(first != null, 400, "Hãy tạo khóa học trước.");
                        course = ((Number) first.get("id")).longValue();
                    }
                    AccessDAO.validateCourseAccess(connection, currentUser, course);
                    String contentType = input.path("contentType").asText("TEXT");
                    ApiException.require(
                            Set.of("TEXT", "VIDEO", "YOUTUBE").contains(contentType),
                            400,
                            "Invalid content type");
                    long chapter = input.path("chapterId").asLong(1);
                    String title = Json.text(input, "title", 200),
                            content = RichText.clean(Json.text(input, "content", 200000));
                    String video = input.path("videoUrl").asText("");
                    ApiException.require(
                            video.isBlank()
                                    || video.matches(
                                            "https://(www\\.)?(youtube\\.com|youtu\\.be)/[^\\s]*"),
                            400,
                            "Video URL không hợp lệ.");
                    long next = id;
                    if (id == 0)
                        next =
                                Database.insert(
                                        connection,
                                        """
    INSERT lessons(course_id,chapter_id,author_id,title,description,objectives,content,video_url,content_type,published)
    VALUES(?,?,?,?,?,?,?,?,?,?)
""",
                                        course,
                                        chapter,
                                        currentUser.getId(),
                                        title,
                                        input.path("description").asText(""),
                                        input.path("objectives").asText(""),
                                        content,
                                        video,
                                        contentType,
                                        input.path("published").asBoolean());
                    else {
                        var old =
                                Database.one(
                                        connection,
                                        """
                                            SELECT author_id,
                                                   course_id
                                            FROM lessons
                                            WHERE id=? AND deleted=0
                                        """,
                                        id);
                        ApiException.require(old != null, 404, "Không tìm thấy bài giảng.");
                        AccessDAO.validateCourseAccess(
                                connection,
                                currentUser,
                                ((Number) old.get("course_id")).longValue());
                        Database.update(
                                connection,
                                """
    UPDATE lessons
    SET course_id=?,chapter_id=?,title=?,description=?,objectives=?,content=?,video_url=?,content_type=?,published=?,updated_at=SYSUTCDATETIME()
    WHERE id=?
""",
                                course,
                                chapter,
                                title,
                                input.path("description").asText(""),
                                input.path("objectives").asText(""),
                                content,
                                video,
                                contentType,
                                input.path("published").asBoolean(),
                                id);
                    }
                    if (input.path("published").asBoolean())
                        Database.update(
                                connection,
                                """
                                    INSERT notifications(user_id,content) SELECT student_id,?
                                    FROM course_enrollments
                                    WHERE course_id=?
                                """,
                                "Bài giảng đã xuất bản/cập nhật: " + title,
                                course);
                    AccessDAO.audit(
                            connection,
                            currentUser,
                            id == 0 ? "LESSON_CREATED" : "LESSON_UPDATED",
                            next);
                    return Map.of("id", next);
                });
    }

    public void deleteLesson(Actor currentUser, long id) {
        currentUser.requireStaff();
        database.transaction(
                connection -> {
                    var l =
                            Database.one(
                                    connection,
                                    """
                                        SELECT author_id,
                                               course_id
                                        FROM lessons
                                        WHERE id=? AND deleted=0
                                    """,
                                    id);
                    ApiException.require(l != null, 404, "Không tìm thấy bài giảng.");
                    AccessDAO.validateCourseAccess(
                            connection, currentUser, ((Number) l.get("course_id")).longValue());
                    Database.update(
                            connection,
                            """
                                UPDATE lessons
                                SET deleted=1
                                WHERE id=?
                            """,
                            id);
                    AccessDAO.audit(connection, currentUser, "LESSON_DELETED", id);
                    return null;
                });
    }

    public Object updateProgress(Actor currentUser, long id, JsonNode input) {
        ApiException.require(currentUser.isStudent(), 403, "Chỉ học sinh ghi tiến độ.");
        int seconds = input.path("elapsedSeconds").asInt();
        ApiException.require(seconds >= 0 && seconds <= 86400, 400, "Thời gian không hợp lệ.");
        return database.transaction(
                connection -> {
                    getLessonById(currentUser, id);
                    var old =
                            Database.one(
                                    connection,
                                    """
                                        SELECT id
                                        FROM lesson_progress WITH(UPDLOCK,HOLDLOCK)
                                        WHERE student_id=? AND lesson_id=?
                                    """,
                                    currentUser.getId(),
                                    id);
                    if (old == null)
                        Database.update(
                                connection,
                                """
    INSERT lesson_progress(student_id,lesson_id,completed,elapsed_seconds)
    VALUES(?,?,?,?)
""",
                                currentUser.getId(),
                                id,
                                input.path("completed").asBoolean(),
                                seconds);
                    else
                        Database.update(
                                connection,
                                """
    UPDATE lesson_progress
    SET completed=CASE WHEN completed=1 THEN 1 ELSE ? END,elapsed_seconds=CASE WHEN elapsed_seconds>? THEN elapsed_seconds ELSE ? END,updated_at=SYSUTCDATETIME()
    WHERE student_id=? AND lesson_id=?
""",
                                input.path("completed").asBoolean(),
                                seconds,
                                seconds,
                                currentUser.getId(),
                                id);
                    return Map.of("saved", true);
                });
    }

    public Object saveCourse(Actor currentUser, long id, JsonNode input) {
        currentUser.requireAdmin();
        return database.transaction(
                connection -> {
                    String title = Json.text(input, "title", 200);
                    long teacher = Json.id(input, "teacherId");
                    ApiException.require(
                            Database.one(
                                            connection,
                                            """
                                                SELECT id
                                                FROM users
                                                WHERE id=? AND role='TEACHER' AND deleted=0
                                            """,
                                            teacher)
                                    != null,
                            400,
                            "Giáo viên không hợp lệ.");
                    if (id == 0) {
                        long created =
                                Database.insert(
                                        connection,
                                        """
                                            INSERT courses(title,description,teacher_id,published)
                                            VALUES(?,?,?,?)
                                        """,
                                        title,
                                        input.path("description").asText(""),
                                        teacher,
                                        input.path("published").asBoolean());
                        AccessDAO.audit(connection, currentUser, "COURSE_CREATED", created);
                        return Map.of("id", created);
                    }
                    ApiException.require(
                            Database.update(
                                            connection,
                                            """
                                                UPDATE courses
                                                SET title=?,description=?,teacher_id=?,published=?
                                                WHERE id=? AND deleted=0
                                            """,
                                            title,
                                            input.path("description").asText(""),
                                            teacher,
                                            input.path("published").asBoolean(),
                                            id)
                                    == 1,
                            404,
                            "Không tìm thấy khóa học.");
                    AccessDAO.audit(connection, currentUser, "COURSE_UPDATED", id);
                    return Map.of("id", id);
                });
    }

    public void deleteCourse(Actor currentUser, long id) {
        currentUser.requireAdmin();
        database.transaction(
                connection -> {
                    ApiException.require(
                            Database.update(
                                            connection,
                                            """
                                                UPDATE courses
                                                SET deleted=1
                                                WHERE id=? AND deleted=0
                                            """,
                                            id)
                                    == 1,
                            404,
                            "Không tìm thấy khóa học.");
                    AccessDAO.audit(connection, currentUser, "COURSE_DELETED", id);
                    return null;
                });
    }

    public void enrollStudent(Actor currentUser, long id, JsonNode input) {
        currentUser.requireStaff();
        database.transaction(
                connection -> {
                    AccessDAO.validateCourseAccess(connection, currentUser, id);
                    long student = Json.id(input, "studentId");
                    ApiException.require(
                            Database.one(
                                            connection,
                                            """
    SELECT id
    FROM users
    WHERE id=? AND role='STUDENT' AND active=1 AND deleted=0
""",
                                            student)
                                    != null,
                            400,
                            "Học sinh không hợp lệ.");
                    if (Database.one(
                                    connection,
                                    """
                                        SELECT id
                                        FROM course_enrollments WITH(UPDLOCK,HOLDLOCK)
                                        WHERE course_id=? AND student_id=?
                                    """,
                                    id,
                                    student)
                            == null)
                        Database.update(
                                connection,
                                """
                                    INSERT course_enrollments(course_id,student_id)
                                    VALUES(?,?)
                                """,
                                id,
                                student);
                    AccessDAO.audit(connection, currentUser, "STUDENT_ENROLLED", id);
                    return null;
                });
    }

    private LessonView mapLessonView(Map<String, Object> row) {
        LessonView result = new LessonView();
        result.setId(row.get("id") == null ? null : ((Number) row.get("id")).longValue());
        result.setTitle((String) row.get("title"));
        result.setDescription((String) row.get("description"));
        result.setObjectives((String) row.get("objectives"));
        result.setContent((String) row.get("content"));
        result.setVideoUrl((String) row.get("videoUrl"));
        result.setContentType((String) row.get("contentType"));
        result.setCourseId(
                row.get("courseId") == null ? null : ((Number) row.get("courseId")).longValue());
        result.setChapterId(
                row.get("chapterId") == null ? null : ((Number) row.get("chapterId")).longValue());
        result.setPublished((Boolean) row.get("published"));
        result.setAuthorId(
                row.get("authorId") == null ? null : ((Number) row.get("authorId")).longValue());
        result.setCompleted(
                row.get("completed") == null ? null : ((Number) row.get("completed")).intValue());
        result.setElapsedSeconds(
                row.get("elapsedSeconds") == null
                        ? null
                        : ((Number) row.get("elapsedSeconds")).intValue());
        return result;
    }
}
