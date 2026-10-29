import com.mathio.lms.DAO.*;
import com.mathio.lms.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.mindrot.jbcrypt.BCrypt;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Explicit, transactional local test-data seed. Existing content/passwords are preserved. */
public class SeedTestData {
    static final String MARKER = "QA_SEED_20261028_V1";
    static final String PASSWORD = "TestMathio2026!";
    static final String[] LEVELS = {"KNOWLEDGE","UNDERSTANDING","APPLICATION"};
    static final String[] DIFFICULTY = {"EASY","MEDIUM","HARD"};
    static final String[] TOPICS = {"Hàm số lượng giác","Cấp số cộng","Cấp số nhân","Giới hạn của dãy số","Đường thẳng và mặt phẳng trong không gian","Quan hệ song song trong không gian"};
    static final String[] NAMES = {"Nguyễn An","Trần Bình","Lê Chi","Phạm Dũng","Hoàng Hà","Vũ Linh","Đặng Nam","Bùi Ngọc","Đỗ Phúc","Hồ Quỳnh","Ngô Sơn","Dương Thảo","Lý Trang","Mai Tú","Đinh Vân","Cao Việt","Tạ Yến","Võ Khánh","Học sinh chưa đăng ký","Học sinh bị khóa"};
    static final List<Map<String,Object>> accounts = new ArrayList<>();
    static final Map<String,Object> report = new LinkedHashMap<>();
    static long id(Object o) { return Json.MAPPER.valueToTree(o).path("id").asLong(); }
    static void require(boolean ok,String message) { if(!ok) throw new IllegalStateException(message); }
    static Actor user(Connection c,String role,String email,String name,String hash,boolean active) throws Exception {
        require(Database.one(c,"SELECT id FROM users WHERE email=?",email)==null,"Test email already exists: "+email);
        long id=Database.insert(c,"INSERT users(role,email,name,password_hash,active) VALUES(?,?,?,?,?)",role,email,name,hash,active);
        Database.update(c,"INSERT user_profiles(user_id,phone,birth_date) VALUES(?,?,?)",id,"",role.equals("STUDENT")?"2009-05-15":"1988-06-20");
        accounts.add(Map.of("id",id,"email",email,"name",name,"role",role,"active",active));
        return new Actor(id,role,1);
    }
    static ObjectNode question(int course,int index,Map<String,Object> topic) {
        int kind=index%6,a=course+index+2,d=2+index%4,n=5+index%5;
        int answer; String title,text,solution;
        switch(kind) {
            case 0 -> { answer=a+d; title="Giá trị lớn nhất của hàm lượng giác"; text="Tìm giá trị lớn nhất của y = "+a+" sin x + "+d+"."; solution="Vì -1 ≤ sin x ≤ 1 và "+a+" > 0, giá trị lớn nhất là "+a+" + "+d+" = "+answer+"."; }
            case 1 -> { answer=a+(n-1)*d; title="Số hạng của cấp số cộng"; text="Cấp số cộng có u₁ = "+a+", công sai d = "+d+". Tính u"+n+"."; solution="uₙ = u₁ + (n-1)d, nên u"+n+" = "+a+" + "+(n-1)+" × "+d+" = "+answer+"."; }
            case 2 -> { answer=a*(1<<(n-1)); title="Số hạng của cấp số nhân"; text="Cấp số nhân có u₁ = "+a+", công bội q = 2. Tính u"+n+"."; solution="uₙ = u₁ × q^(n-1), nên u"+n+" = "+a+" × 2^"+(n-1)+" = "+answer+"."; }
            case 3 -> { answer=a; title="Giới hạn của dãy số"; text="Tính giới hạn của ("+a+"n + "+d+")/n khi n tiến tới dương vô cùng."; solution="Chia cho n được "+a+" + "+d+"/n. Vì 1/n tiến tới 0, giới hạn là "+answer+"."; }
            case 4 -> { answer=n*(n-1)/2; title="Đếm đường thẳng trong mặt phẳng"; text="Cho "+n+" điểm trong một mặt phẳng, không có ba điểm thẳng hàng. Có bao nhiêu đường thẳng đi qua các cặp điểm?"; solution="Mỗi cặp điểm xác định một đường thẳng và không trùng nhau. C("+n+",2) = "+n+" × "+(n-1)+" / 2 = "+answer+"."; }
            default -> { answer=a+d; title="Đường trung bình và quan hệ song song"; text="Trong tam giác ABC, M và N lần lượt là trung điểm AB và AC. Biết BC = "+(2*(a+d))+" cm. Tính MN (cm)."; solution="Theo định lý đường trung bình, MN song song BC và MN = BC/2 = "+answer+" cm."; }
        }
        boolean essay=index>=18;
        ObjectNode q=Json.object().put("title",String.format("[TEST] K%02d - %s %02d",course,title,index+1))
            .put("content","<p>"+text+(essay?" Trình bày các bước giải.":"")+"</p>")
            .put("chapter",topic.get("chapter").toString()).put("topic",topic.get("title").toString())
            .put("type",essay?"ESSAY":"MCQ").put("difficulty",DIFFICULTY[(index/6)%3])
            .put("cognitiveLevel",LEVELS[(index/6)%3]).put("answer",essay?solution:Integer.toString(answer))
            .put("published",index<20);
        if(!essay) {
            ArrayNode options=Json.array();
            List<String> values=new ArrayList<>(List.of(""+(answer-1),""+answer,""+(answer+1),""+(answer+2)));
            Collections.rotate(values,index%4); values.forEach(options::add); q.set("options",options);
        }
        return q;
    }
    static long exam(Database db,Connection c,Actor teacher,long course,int k,int kind,List<ObjectNode> questions) throws Exception {
        ArrayNode selected=Json.array();
        if(kind==1) { for(int i=6;i<10;i++) selected.add(questions.get(i)); selected.add(questions.get(18)); selected.add(questions.get(19)); }
        else for(int i=kind==2?10:0;i<(kind==2?16:8);i++) selected.add(questions.get(i));
        ObjectNode matrix=Json.object();
        for(String l:LEVELS) matrix.set(l,Json.object().put("MCQ",0).put("ESSAY",0));
        for(JsonNode q:selected) { ObjectNode cell=(ObjectNode)matrix.path(q.path("cognitiveLevel").asText()); String type=q.path("type").asText(); cell.put(type,cell.path(type).asInt()+1); }
        ExamService.validateMatrix(matrix,selected);
        long exam=Database.insert(c,"INSERT exams(author_id,title,chapter,topic,matrix,published,duration_seconds,course_id,results_published) VALUES(?,?,?,?,?,?,?,?,?)",
            teacher.getId(),String.format("[TEST] K%02d - %s",k,kind==0?"Trắc nghiệm tổng hợp":kind==1?"Kiểm tra kết hợp tự luận":"Đề nháp chưa xuất bản"),"Toán 11","Ôn tập",Json.stringify(matrix),kind!=2,3600,course,kind==0||k%2==1);
        int pos=0;
        for(JsonNode q:selected) Database.update(c,"INSERT exam_questions(exam_id,question_id,position,snapshot) VALUES(?,?,?,?)",exam,q.path("id").asLong(),pos++,Json.stringify(q));
        return exam;
    }
    static void attempt(Database db,Connection c,ExamService exams,Actor student,Actor teacher,long exam,int variant,boolean mixed) throws Exception {
        long attempt=id(exams.startAttempt(student,exam));
        var snapshots=Database.rows(c,"SELECT question_id,snapshot FROM exam_questions WHERE exam_id=? ORDER BY position",exam);
        ObjectNode input=Json.object(),answers=Json.object(),essays=Json.object(); ArrayNode grades=Json.array();
        int i=0;
        for(var row:snapshots) {
            JsonNode q=Json.parse(row.get("snapshot").toString()); String key=row.get("question_id").toString();
            if(q.path("type").asText().equals("MCQ")) {
                String answer=q.path("answer").asText();
                if((i+variant)%4==0) for(JsonNode option:q.path("options")) if(!option.asText().equals(answer)) { answer=option.asText(); break; }
                if(variant!=0||i<2) answers.put(key,answer);
            } else {
                essays.set(key,Json.object().put("text",variant%2==0?q.path("answer").asText():"Em áp dụng công thức đã học nhưng chưa hoàn thành bước tính cuối."));
                grades.add(Json.object().put("questionId",q.path("id").asLong()).put("score",variant%2==0?9:6).put("feedback","[TEST] Giáo viên chấm mẫu: kiểm tra công thức, cách trình bày và kết quả."));
            }
            i++;
        }
        input.set("answers",answers); input.set("essayAnswers",essays);
        exams.saveAnswers(student,attempt,input,variant>0);
        if(mixed&&variant>=3) exams.gradeAttempt(teacher,attempt,Json.object().set("grades",grades));
        if(variant>0) {
            int days=variant%7,seconds=300+variant*90;
            Database.update(c,"UPDATE exam_attempts SET started_at=DATEADD(SECOND,-?,DATEADD(DAY,-?,SYSUTCDATETIME())),submitted_at=DATEADD(DAY,-?,SYSUTCDATETIME()),elapsed_seconds=? WHERE id=?",seconds,days,days,seconds,attempt);
            Database.update(c,"UPDATE learning_history SET created_at=DATEADD(DAY,-?,SYSUTCDATETIME()),elapsed_seconds=? WHERE attempt_id=?",days,seconds,attempt);
        }
    }
    public static void main(String[] args) throws Exception {
        require(args.length==1&&args[0].equals("--apply"),"Usage: SeedTestData.java --apply");
        Environment.load(Path.of(".env")); require(!Environment.production(),"Local development seed only");
        try(Database db=Database.configured()) {
            String name=db.read(c->Database.one(c,"SELECT DB_NAME() AS name").get("name").toString());
            require(name.equals("math11_tutor_db"),"Unexpected database");
            if(db.read(c->Database.one(c,"SELECT id FROM audit_logs WHERE action=?",MARKER))!=null) { System.out.println("This test seed is already applied; no changes made."); return; }
            require(db.read(c->Database.one(c,"SELECT version FROM schema_versions WHERE version=2"))!=null,"V002 required");
            Path folder=Path.of("database/backups").toAbsolutePath(); Files.createDirectories(folder);
            Path backup=folder.resolve("math11_pre_test_seed_"+UUID.randomUUID()+".bak");
            db.read(c->{
                try(Statement s=c.createStatement()) {
                    s.setQueryTimeout(120); String disk="N'"+backup.toString().replace("'","''")+"'";
                    s.execute("BACKUP DATABASE [math11_tutor_db] TO DISK="+disk+" WITH COPY_ONLY,CHECKSUM");
                    while(s.getMoreResults()||s.getUpdateCount()!=-1) {}
                    s.execute("RESTORE VERIFYONLY FROM DISK="+disk+" WITH CHECKSUM");
                    while(s.getMoreResults()||s.getUpdateCount()!=-1) {}
                } return null;
            });
            System.out.println("Verified backup: "+backup);
            report.put("backup",backup.toString());
            String hash=BCrypt.hashpw(PASSWORD,BCrypt.gensalt(12));
            db.transaction(c->{
                require(Database.one(c,"SELECT version FROM schema_versions WITH(UPDLOCK,HOLDLOCK) WHERE version=2")!=null,"V002 required");
                require(Database.one(c,"SELECT id FROM audit_logs WHERE action=?",MARKER)==null,"Seed already applied");
                require(Database.one(c,"SELECT id FROM users WHERE email LIKE 'qa.%@mathio.test'")==null,"QA email namespace already used; no existing data changed");
                Actor admin=user(c,"ADMIN","qa.admin@mathio.test","[TEST] Quản trị kiểm thử",hash,true);
                Actor[] teachers=new Actor[3],students=new Actor[20];
                String[] teacherNames={"Trần Thanh Hương","Lê Quốc Bảo","Phạm Thu Hạnh"};
                for(int i=0;i<3;i++) teachers[i]=user(c,"TEACHER",String.format("qa.teacher%02d@mathio.test",i+1),"[TEST] "+teacherNames[i],hash,true);
                for(int i=0;i<20;i++) students[i]=user(c,"STUDENT",String.format("qa.student%02d@mathio.test",i+1),"[TEST] "+NAMES[i],hash,i!=19);
                var oldTeacher=Database.one(c,"SELECT id,session_version FROM users WHERE email='teacher@demo.vn' AND active=1 AND deleted=0");
                var oldStudent=Database.one(c,"SELECT id,session_version FROM users WHERE email='student@demo.vn' AND active=1 AND deleted=0");
                require(oldTeacher!=null&&oldStudent!=null,"Expected original demo accounts");
                Actor demoTeacher=new Actor(((Number)oldTeacher.get("id")).longValue(),"TEACHER",((Number)oldTeacher.get("session_version")).intValue());
                Actor demoStudent=new Actor(((Number)oldStudent.get("id")).longValue(),"STUDENT",((Number)oldStudent.get("session_version")).intValue());
                var topics=Database.rows(c,"SELECT t.id,t.title,t.chapter_id,ch.title AS chapter FROM topics t JOIN chapters ch ON ch.id=t.chapter_id ORDER BY t.id");
                Map<String,Map<String,Object>> topicMap=new HashMap<>(); for(var t:topics)topicMap.put(t.get("title").toString(),t);
                for(String t:TOPICS)require(topicMap.containsKey(t),"Missing topic "+t);
                var chapters=Database.rows(c,"SELECT id,title FROM chapters ORDER BY position"); require(chapters.size()>=5,"Five chapters required");
                CatalogDAO catalog=new CatalogDAO(db); QuestionDAO questions=new QuestionDAO(db); ExamService exams=new ExamService(db,questions);
                EssayService essayService=new EssayService(db,questions,null,new ImageStorage());
                List<Map<String,Object>> courses=new ArrayList<>();
                for(int k=1;k<=7;k++) {
                    Actor teacher=k==7?demoTeacher:teachers[(k-1)/2]; boolean published=k!=6;
                    long course=Database.insert(c,"INSERT courses(title,description,teacher_id,published) VALUES(?,?,?,?)",String.format("[TEST] Toán 11 - Lớp %s",new String[]{"11A1","11A2","11B1","11B2","11C1","11C2 - Nháp","11 Demo mở rộng"}[k-1]),"Dữ liệu kiểm thử: bài học, luyện tập, thi trắc nghiệm, tự luận và phân quyền theo lớp.",teacher.getId(),published);
                    courses.add(Map.of("id",course,"teacherId",teacher.getId(),"published",published));
                    int group=k==7?0:((k-1)/2)*6;
                    for(int j=0;j<6;j++)Database.update(c,"INSERT course_enrollments(course_id,student_id) VALUES(?,?)",course,students[group+j].getId());
                    if(published)Database.update(c,"INSERT course_enrollments(course_id,student_id) VALUES(?,?)",course,demoStudent.getId());
                    List<Long> lessons=new ArrayList<>();
                    for(int j=0;j<10;j++) {
                        var chapter=chapters.get(j%5); boolean lessonPublished=j<8;
                        String[] examples={
                            "Hàm sin và cos có tập giá trị [-1;1], chu kỳ 2π. Với y = 3 sin x + 2, giá trị lớn nhất là 5 và nhỏ nhất là -1. Hãy tìm tập giá trị của y = 2 cos x - 1.",
                            "Cấp số cộng có uₙ = u₁ + (n-1)d. Nếu u₁ = "+(k+j+1)+", d = 3 thì u₁₀ = "+(k+j+28)+". Hãy tự tính u₂₀ và tổng 20 số hạng đầu.",
                            "Ta có (3n+2)/n = 3 + 2/n. Vì lim 1/n = 0 nên giới hạn bằng 3. Hãy tính lim (5n-7)/n và giải thích các bước.",
                            "Qua ba điểm không thẳng hàng xác định duy nhất một mặt phẳng. Nếu hai điểm phân biệt của đường thẳng nằm trên mặt phẳng thì cả đường thẳng nằm trên mặt phẳng. Hãy áp dụng để tìm giao tuyến hai mặt phẳng của hình chóp S.ABCD.",
                            "Trong tam giác ABC, M và N là trung điểm AB và AC thì MN song song BC và MN = BC/2. Nếu BC = 12 cm thì MN = 6 cm. Hãy chứng minh bằng định lý đường trung bình."
                        };
                        String text="<h2>"+chapter.get("title")+"</h2><p>Mục tiêu: nhận biết khái niệm, giải thích công thức và vận dụng qua ví dụ.</p><h3>Kiến thức và ví dụ</h3><p>"+examples[j%5]+"</p>";
                        String type=j==6?"YOUTUBE":"TEXT";
                        long lesson=id(catalog.saveLesson(teacher,0,Json.object().put("courseId",course).put("chapterId",((Number)chapter.get("id")).longValue())
                            .put("title",String.format("[TEST] K%02d - %s - %s",k,chapter.get("title"),j<5?"Lý thuyết":"Luyện tập"))
                            .put("description","Bài học kiểm thử có nội dung văn bản, công thức và bài tập vận dụng.").put("objectives","Hiểu kiến thức và trình bày lời giải rõ ràng.")
                            .put("content",text).put("contentType",type).put("videoUrl",type.equals("YOUTUBE")?"https://www.youtube.com/watch?v=NybHckSEQBI":"").put("published",lessonPublished)));
                        lessons.add(lesson);
                    }
                    List<ObjectNode> refs=new ArrayList<>();
                    for(int j=0;j<24;j++) {
                        ObjectNode q=question(k,j,topicMap.get(TOPICS[j%6]));q.put("courseId",course);
                        long qid=questions.saveQuestion(c,teacher,q);refs.add(questions.getQuestionReference(c,qid));
                    }
                    long mcq=exam(db,c,teacher,course,k,0,refs),mixed=exam(db,c,teacher,course,k,1,refs);exam(db,c,teacher,course,k,2,refs);
                    if(published) {
                        for(int j=0;j<7;j++) {
                            Actor student=j==6?demoStudent:students[group+j];
                            for(int l=0;l<5;l++)catalog.updateProgress(student,lessons.get(l),Json.object().put("completed",(j+l)%3!=0).put("elapsedSeconds",(j+l+1)*120));
                            attempt(db,c,exams,student,teacher,mcq,j%6,false);
                            attempt(db,c,exams,student,teacher,mixed,j==6?2:j,true);
                            ObjectNode submission=Json.object().put("questionId",refs.get(18).path("id").asLong());
                            submission.set("content",Json.object().put("text",j%2==0?refs.get(18).path("answer").asText():"Em xác định sin x nằm trong [-1;1], sau đó thay giá trị lớn nhất vào biểu thức."));
                            long essay=id(essayService.submitEssay(student,submission));
                            if(j%2==0)essayService.gradeEssay(teacher,essay,Json.object().put("score",j==0?9.5:7).put("feedback","[TEST] Bài đã được giáo viên chấm thủ công. Cần ghi rõ bước áp dụng công thức."));
                            Database.update(c,"INSERT notifications(user_id,content,is_read) VALUES(?,?,?)",student.getId(),"[TEST] Khóa học #"+course+" có bài tập và đề thi mới. Hãy xem mục Luyện tập và Kiểm tra.",j%2==0);
                        }
                    }
                    System.out.println("Prepared course "+k+"/7");
                }
                report.put("courses",courses);
                Database.update(c,"INSERT audit_logs(actor_id,action) VALUES(?,?)",admin.getId(),MARKER);
                require(((Number)Database.one(c,"SELECT COUNT(*) AS n FROM users WHERE email LIKE 'qa.%@mathio.test'").get("n")).intValue()==24,"Account count mismatch");
                require(((Number)Database.one(c,"SELECT COUNT(*) AS n FROM questions WHERE title LIKE '[[]TEST] %'").get("n")).intValue()==168,"Question count mismatch");
                require(Database.one(c,"SELECT TOP(1) q.id FROM questions q WHERE q.title LIKE '[[]TEST] %' AND q.course_id IS NULL")==null,"Unassigned test question");
                return null;
            });
            report.put("accounts",accounts);report.put("newAccountPassword",PASSWORD);
            Files.writeString(Path.of("database/test-data-manifest.json"),Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(report));
            System.out.println("COMMITTED. Created 24 accounts, 7 courses, 70 lessons, 168 questions, 21 exams, 84 attempts and 42 essay submissions.");
            System.out.println("New-account password: "+PASSWORD);
            System.out.println("Manifest: database/test-data-manifest.json");
        }
    }
}
