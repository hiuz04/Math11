# API contract

Base URL phía browser: `/api`. URL trực tiếp của WAR mặc định: `http://localhost:8080/Math11/api`. JSON dùng UTF-8; thời gian UTC ISO-8601, UI định dạng theo locale.

## Session và transport

- `GET /auth/csrf` tạo session tiền đăng nhập, trả `{csrfToken}`. Gửi cookie và `X-CSRF-Token` với mọi POST/PUT/DELETE, kể cả login.
- Login đổi session ID và CSRF token; lấy token mới từ response. Cookie HttpOnly, SameSite=Lax, Secure khi `APP_ENV=production`.
- Mutation JSON có body dùng `Content-Type: application/json` (cho phép `charset=UTF-8`). POST `/essays`, `/ai/essays/evaluate-retry`, `/ai/chat` còn nhận multipart theo mục Upload bên dưới. Request không có body, gồm DELETE/logout, không bắt buộc Content-Type. Body DELETE nếu có vẫn được parse/kiểm tra. Origin phải nằm trong `APP_ORIGIN`; dùng proxy cùng origin, không bật CORS `*`.
- Frontend gửi `Idempotency-Key` ngẫu nhiên mỗi thao tác. Gửi lại **cùng key/cùng route/body** sẽ nhận lại status/body đã lưu, trong cùng transaction với thay đổi nghiệp vụ đối với mutation ngắn. Tác vụ Gemini dùng lease riêng, gọi HTTP ngoài JDBC; kết quả đánh giá/Tutor/chat và response được ghi cùng transaction cuối. Cùng key nhưng route/payload khác trả 409. Request đang xử lý trả 409; cùng key được retry sau lỗi hoặc khi lease hết hạn.
- Backend kiểm tra principal/session version ở mọi API được bảo vệ; role trong query/body không quyết định quyền.
- Lỗi: `{error:{code:"HTTP_403",message:"...",requestId:"..."}}`; không có stack trace/key/hash mật khẩu. Status gồm 400/401/403/404/405/409/413/429/500/502/503. DELETE thành công trả 204.

## Auth, user, profile

| Method/path | Input → output / quyền |
| --- | --- |
| GET `/auth/csrf` | Public → `{csrfToken}` |
| POST `/auth/login` | `{email,password}` → `{user,csrfToken}`; BCrypt, giới hạn số lần |
| GET `/auth/me` | `{user,csrfToken}`; 401 khi hết phiên |
| POST `/auth/logout` | `{}` → 204, invalidate session |
| POST `/auth/change-password` | `{oldPassword,newPassword}` → 204, thu hồi tất cả phiên cũ |
| GET `/profile` | Hồ sơ của principal |
| POST `/profile` | `{name,phone,birthDate}`; birthDate dạng `YYYY-MM-DD` hoặc rỗng |
| GET `/users` | Admin: mọi tài khoản chưa xóa; Teacher: Student trong khóa học được phân công |
| POST `/users` | Admin; `{name,email,role,password,active?}` → user 201 |
| POST/PUT `/users/{id}` | Admin; `{name,email,role,active}` → user; thay role/active làm hết phiên cũ |
| DELETE `/users/{id}` | Admin, soft delete; không tự xóa tài khoản đang dùng |

`user` gồm `id,name,email,role,createdAt?,phone?,birthDate?`; danh sách admin có `active`. Role là `STUDENT/TEACHER/ADMIN`, React ánh xạ sang nhãn tiếng Việt. Không trả `password_hash`. Tạo/đổi mật khẩu tối thiểu 10 ký tự, tối đa 72 bytes UTF-8.

## Catalog, bài giảng, tiến độ

| Method/path | Input → output / quyền |
| --- | --- |
| GET `/courses` | Course[] trong phạm vi được phép; Student chỉ khóa đã publish và ghi danh |
| POST `/courses` | Admin; `{title,description?,teacherId,published}` → `{id}`, 201 |
| POST/PUT `/courses/{id}` | Admin; cùng metadata → `{id}` |
| DELETE `/courses/{id}` | Admin; soft delete |
| POST `/courses/{id}/enrollments` | Admin/giáo viên phụ trách; `{studentId}` → `{saved:true}` |
| GET `/chapters?courseId={id}` | `{id,title,position,lessonCount,exerciseCount,progress}[]` |
| GET `/topics` | `{id,chapterId,title}[]` |
| GET `/lessons?chapterId={id}` | Metadata bài được phép, completion của principal |
| GET `/lessons/{id}` | Metadata + `content,objectives,videoUrl,contentType,courseId,chapterId,completed,elapsedSeconds` |
| POST `/lessons` | Staff; `{title,courseId?,chapterId,description?,objectives?,content,videoUrl?,contentType?,published}` → `{id}`, 201 |
| POST/PUT `/lessons/{id}` | Staff trong phạm vi course; cùng body → `{id}` |
| DELETE `/lessons/{id}` | Staff phụ trách/admin; soft delete |
| POST `/lessons/{id}/progress` | Student ghi danh; `{completed,elapsedSeconds}` → `{saved:true}` |
| GET `/exercises` | Đề ESSAY và bài kiểm tra MCQ đang xuất bản, không có đáp án kín |

Rich HTML được sanitize theo allowlist phía server; giữ heading/list/format và thuộc tính công thức TipTap. URL video chỉ nhận HTTPS YouTube. `contentType` thuộc `TEXT/VIDEO/YOUTUBE`. Progress không bị lùi khi nhận request cũ. Bản nháp hoặc bài thuộc course không cho phép bị chặn khi đọc chi tiết.

## Câu hỏi, đề thi, quiz

| Method/path | Input → output / quyền |
| --- | --- |
| GET `/questions` | Question[]; Student chỉ câu đã publish trong course đã publish và đã ghi danh; không nhận `answer`. Staff theo quyền câu/course |
| GET `/questions/{id}` | Question trong phạm vi, không lộ answer cho Student |
| POST `/questions/bulk` | Staff; `{questions:Question[]}` (1–100) → Question[] đã lưu |
| DELETE `/questions/{id}` | Staff sở hữu/admin; soft delete |
| POST `/ai/questions/generate` | Staff; GenerationRequest → Question[]; gọi Gemini thật |
| POST `/ai/exams/generate` | Staff; `{chapter,topic,matrix}` → Exam chưa lưu |
| GET `/exams` | Exam[] được phép; Student không nhận đáp án |
| GET `/exams/{id}` | Exam + question snapshot |
| POST `/exams` | Staff; Exam → Exam đã lưu, 201; kiểm tra từng ô matrix |
| DELETE `/exams/{id}` | Staff sở hữu/admin; soft delete |
| POST `/exams/{id}/attempts` | Student; `{}` → QuizAttempt; tái dùng attempt đang mở, 201 |
| GET `/quiz-attempts` | `{id,examId,status,startedAt}[]` của principal |
| GET `/quiz-attempts/{id}` | Chủ bài/admin/giáo viên được phân công; QuizAttempt |
| POST `/quiz-attempts/{id}/answers` | Chủ bài; `{answers:{questionId:optionText}}` → QuizAttempt |
| POST `/quiz-attempts/{id}/submit` | Chủ bài; cùng body → QuizAttempt; đề có ESSAY chuyển PENDING_GRADING, chưa có điểm cuối |
| POST `/quiz-attempts/{id}/grade` | Teacher có quyền đề/course hoặc Admin; `{grades:[{questionId,score,feedback}]}` → QuizAttempt |
| POST `/exams/{id}/publish-results` | Teacher có quyền đề/course hoặc Admin; `{}` → `{published:true}` |
| GET `/exam-files/{id}` | Binary ảnh tự luận trong attempt, kiểm tra owner/quyền đề |

Question giữ `title,content,chapter,topic,type,difficulty,cognitiveLevel,options?,answer?`. `type=MCQ/ESSAY`, `difficulty=EASY/MEDIUM/HARD`, `cognitiveLevel=KNOWLEDGE/UNDERSTANDING/APPLICATION`. MCQ answer phải khớp một lựa chọn; ESSAY cần hướng dẫn chấm. Chương/chủ đề đối chiếu DB. ID của câu/đề vừa sinh là ID tạm âm; SQL Server cấp ID thật khi lưu.

GenerationRequest: `{chapter,topic,questionType,cognitiveLevel,count,difficulty,instructions?}`. Server kiểm tra JSON Gemini, đúng count, metadata, không trùng nội dung. Matrix có sáu ô số nguyên không âm; tổng 1–100:

```json
{
  "KNOWLEDGE": {"MCQ": 5, "ESSAY": 1},
  "UNDERSTANDING": {"MCQ": 4, "ESSAY": 1},
  "APPLICATION": {"MCQ": 3, "ESSAY": 2}
}
```

Exam gồm `id,title,chapter,topic,matrix,questions,createdAt`; question snapshot cố định cho các attempt. Đề đã có bài làm không được chỉnh. Runner hỗ trợ MCQ, ESSAY hoặc hỗn hợp trong cùng attempt; API tự luận/Tutor độc lập vẫn được giữ. `courseId,published,resultsPublished,durationSeconds` là metadata bổ sung. Câu hỏi/đề cũ chưa được gán course không được hiển thị cho Student. Khi tạo mới mà không gửi courseId, server chỉ tự chọn khi staff có đúng một course; trường hợp khác để draft chưa gán. Nên gửi courseId rõ ràng.

QuizAttempt gồm `id,examId,status,startedAt,deadlineAt,serverTime,elapsedSeconds,expired?,exam:{title,durationSeconds},questions,answers,essayAnswers,result?`. Chưa nộp không có answer chuẩn/result. Sau khi nộp: `result={score,correctCount,totalCount,review}`; review chứa đáp án, bài làm và đúng/sai. Score/time/status do server quyết định; gửi score/correctCount từ client không ảnh hưởng. Nộp lại attempt đã kết thúc không chấm/ghi điểm thêm. MCQ giữ status SUBMITTED tương thích cũ; đề có ESSAY dùng IN_PROGRESS → PENDING_GRADING → GRADED. `resultsPublished=false` ẩn cả result, điểm tự luận và history điểm khỏi Student tới khi công bố. Staff có quyền vẫn xem/chấm được.

## Tự luận, ảnh, Tutor

| Method/path | Input → output |
| --- | --- |
| POST `/essays` | Student; `{questionId,content,startedAt?}` → EssaySubmission, 201 |
| GET `/essays` | Student: bài của mình; staff: bài theo quyền câu/course |
| GET `/essays/{id}` | Chủ bài/admin/Teacher trong phạm vi; đầy đủ lịch sử được phép |
| POST `/essays/{id}` | 409: bài đã nộp; client không sửa score/status/Tutor |
| POST `/essays/{id}/grade` | Teacher có quyền câu/course hoặc Admin; `{score,feedback}` → EssaySubmission, lưu gradedBy/gradedAt/audit |
| POST `/ai/essays/evaluate` | `{submissionId}` → EssaySubmission |
| POST `/ai/tutor/start` | `{submissionId}` → EssaySubmission, chỉ các bước đã mở |
| POST `/ai/tutor/check-step` | `{submissionId,stepNumber,answer}` → EssaySubmission |
| POST `/ai/tutor/next-step` | `{submissionId,stepNumber}` → EssaySubmission |
| POST `/ai/essays/evaluate-retry` | `{submissionId,content}` → EssaySubmission |
| GET `/files/{id}` | Binary ảnh, có kiểm tra quyền; không phải URL public |

SubmissionContent: `{text?,image?,imageName?,imageHash?}`. Frontend real API gửi ảnh bằng multipart theo mục upload bên dưới; JSON data URL JPEG/PNG/WEBP còn được chấp nhận để tương thích client cũ, tối đa 8 MiB bytes sau decode. `imageHash` client chỉ phục vụ UX: server giải mã ảnh, xác minh format/dimensions, tính SHA-256 và lưu file UUID trong private storage. Response dùng URL `/api/files/{id}`, hash thật và `fileId`; tên file không được dùng làm đường dẫn.

EssaySubmission giữ các field TypeScript: `id,studentId,questionId,question,originalSubmission,originalFeedback,status,aiSteps,studentStepAnswers,totalSteps,currentStep,retrySubmissions,retrySubmission?,finalResult?,startedAt,updatedAt,elapsedSeconds`. Response không có private plan/expectedAnswer hoặc bước tương lai.

Luồng: `SUBMITTED → CORRECT` hoặc `NEEDS_TUTOR → RETRY_REQUIRED → COMPLETED/NEEDS_REVISION`. Đánh giá không chắc chắn có thể chuyển `NEEDS_REVISION` với `diagnostic=UNABLE_TO_EVALUATE`, không score và cho nộp ảnh rõ hơn. Các bước Tutor có `instruction,formula?,correct,hint?,attempts`; không có đáp án chuẩn.

`stepNumber` bắt đầu từ 1, phải khớp bước đang mở. Sai ghi attempt và trả hint; đúng chỉ đánh dấu bước, **không tự chuyển**. Next chỉ chạy khi bước hiện tại đúng. Sau bước cuối mới mở retry; cần ảnh mới, không trùng bất kỳ SHA-256 đã nộp của bài. Không thể dùng body để đổi owner/status/score hay đánh dấu correct. Các thay đổi khóa hàng và lưu trong transaction ngắn. Đánh giá, retry và tạo plan dùng reservation PROCESSING, revision và lease 300 giây; không giữ connection trong lúc HTTP. Request đồng thời cùng bài nhận 409. Sau restart, request mới có thể lấy lại lease hết hạn; token cũ không được ghi đè. Lease Idempotency-Key là 900 giây, được replay ngay khi hoàn thành; lỗi trước khi hoàn thành cho phép thử lại. Retry sau lỗi AI dùng lại ảnh đã lưu của cùng payload, tránh bắt upload ảnh khác chỉ vì upstream lỗi.

Gemini evaluation phải trả `{correct:boolean,confident:boolean,feedback:string,score?:number}`. Chưa tin cậy không gán điểm. Kế hoạch Tutor được lưu phía server; so khớp dùng số hữu tỉ chính xác và đa thức/phân thức đa biến, không dùng thử vài điểm ngẫu nhiên. Hỗ trợ số nguyên/thập phân/phân số, + − × ÷, ngoặc, lũy thừa nguyên giới hạn, biến chữ đơn, LaTeX fraction/power, tập nghiệm hữu hạn và phương trình bậc 1–2 có nghiệm hữu tỉ. Bảo toàn điều kiện xác định: x/x so với 1 là UNKNOWN, không tự nhận đúng. Hàm lượng giác, căn vô tỉ, nghiệm tham số và biểu thức vượt giới hạn trả UNKNOWN. Attempt bước có `verification=EQUIVALENT/NOT_EQUIVALENT/UNKNOWN`, `correct=null` và `diagnostic=UNABLE_TO_VERIFY` khi không xác minh được; không mở bước tiếp theo. `aiOperations` trong GET essay detail liệt kê lịch sử reservation đã hoàn thành/lỗi/hết hạn, không chứa token hay private reference.

## AI chat và quản trị

| Method/path | Input → output / quyền |
| --- | --- |
| POST `/ai/chat` | `{message,image?,conversationId?}` → `{reply,conversationId}`; ghi conversation/messages/history |
| GET `/ai/conversations` | Danh sách hội thoại của principal |
| GET `/ai/conversations/{id}` | Messages của đúng owner |
| GET `/admin/config/gemini` | Admin; `{model,temperature,maxOutputTokens,enabled,hasApiKey}` |
| POST `/admin/config/gemini` | Admin; cùng tham số + `apiKey?`; AES-GCM khi lưu key vào DB |
| POST `/admin/config/gemini/test` | Admin; tham số/key tạm hoặc đã lưu → `{ok,message}` khi gọi được Gemini thật |
| GET `/admin/config/prompts` | Admin; `{system,essayEvaluation,stepTutor,retryEvaluation}` |
| POST `/admin/config/prompts` | Admin; bốn prompt không rỗng |
| GET `/admin/audit-logs` | Admin; 200 hoạt động gần nhất, chỉ metadata |

## Dashboard, lịch sử, thông báo

| Method/path | Output / quyền |
| --- | --- |
| GET `/dashboard` | DashboardStatistic[]; role lấy từ session, bỏ qua role client |
| GET `/analytics` | Student: tiến độ, score series, activities, continueLesson; Staff: trend/distribution/activities trong phạm vi |
| GET `/students/{id}/statistics` | Staff được phân công/admin; thống kê học sinh |
| GET `/learning-history?studentId={id}` | HistoryEntry[]; mặc định chính mình, Teacher bị giới hạn assignment |
| POST `/learning-history` | 403; lịch sử và điểm phải được tạo từ nghiệp vụ server |
| GET `/notifications` | Thông báo của principal |
| POST `/notifications/{id}/read` | `{}` → `{saved:true}`, kiểm tra owner |

HistoryEntry có `id` string, `title,type,category,attempt,score?,status,date,elapsedSeconds,submissionId?,attemptId?,detail?`. `attemptId` mở lại kết quả quiz; `submissionId` mở chi tiết tự luận/Tutor. Teacher/Admin không thể tùy ý ghi điểm bằng endpoint history.

## Bổ sung contract 25/10/2026: đề hỗn hợp

Lưu đáp án với cùng endpoint cũ, thêm `essayAnswers` (không gửi score/status):

```json
{
  "answers": {"101":"Nội dung lựa chọn"},
  "essayAnswers": {
    "102": {"text":"Bài làm", "image":"data:image/png;base64,..."}
  }
}
```

`answers` là object, dùng `{}` cho đề chỉ ESSAY. `essayAnswers[id]` nhận trực tiếp SubmissionContent hoặc `{content:SubmissionContent}`. Có thể lưu từng phần rồi reload qua GET attempt. Ảnh response là `/api/exam-files/{id}`; chỉ chấp nhận data URL mới hoặc URL đã thuộc chính câu trả lời đó. Không nhận fileId/hash/URL của bài khác. Đến hạn, submit chốt các đáp án đã lưu; bỏ qua thay đổi gửi muộn.

MCQ được chấm bằng snapshot server khi nộp. Tự luận trong exam dùng **manual grading**, không dùng luồng Tutor độc lập để thay đổi điểm exam. Giáo viên được chấm từng phần; bài chưa đủ điểm vẫn PENDING_GRADING. Mỗi câu có trọng số 1 cố định trong mã server, điểm câu ESSAY từ 0–10 và MCQ đúng=10/sai=0. Điểm tổng = tổng điểm câu / số câu, làm tròn 2 chữ số. Review lưu `weight=1,maxScore=10,score` từng câu, audit lưu điểm cũ/mới, feedback, giáo viên và thời gian. Không nhận trọng số/điểm từ request học sinh. Kết quả được đóng băng sau khi GRADED; chấm lại trả 409.

Teacher không được đọc bài chỉ vì có chung học sinh ở course khác. Attempt/ảnh exam kiểm tra exam author hoặc course teacher; essay/ảnh/Tutor kiểm tra question author hoặc course teacher đã ghi trên submission. History, statistics và analytics áp dụng cùng phạm vi tài nguyên. Chat riêng của học sinh không xuất hiện trong báo cáo Teacher.

## Phân trang và giới hạn tải

`GET /questions,/exams,/quiz-attempts,/essays,/users,/courses,/lessons,/learning-history` hỗ trợ `offset=0&limit=100`, limit 1–100, offset 0–1000000. Response giữ kiểu array. Request cũ không có tham số nhận tối đa 100 bản ghi; dùng trang tiếp theo khi đủ limit. Audit/notifications giới hạn 200, hội thoại 100, messages 200. Curriculum chapters/topics nhỏ giữ danh sách hiện có.

Gemini chỉ nhận tối đa 8 HTTP call đồng thời trong một JVM; yêu cầu vượt tải trả 503, không xếp hàng giữ JDBC. Timeout mỗi call 45 giây, tối đa 2 lần cho 429/5xx, không có mock fallback. DB pool mặc định 10, có `DB_POOL_SIZE`; query timeout mặc định 30 giây (`DB_QUERY_TIMEOUT_SECONDS`). Không bảo đảm 20 cuộc gọi AI đều thành công đồng thời: giới hạn tải là chủ động và được kiểm thử.


## Cập nhật tích hợp frontend 26/10/2026

### Upload multipart

POST `/essays`, `/ai/essays/evaluate-retry`, `/ai/chat`, `/quiz-attempts/{id}/answers` nhận đúng hai part:

- `payload`: chuỗi JSON UTF-8, tối đa 200.000 bytes. Với essay: `{questionId,startedAt?,content:{text?,imageName?,imageHash?}}`. Với retry: `{submissionId,content:{...}}`. Với chat: `{message,conversationId?,content:{imageName?,imageHash?}}`.
- `image`: một file JPEG/PNG/WEBP, từ 1 byte đến 8 MiB. Browser tự đặt Content-Type/boundary. CSRF/cookie/Idempotency-Key vẫn bắt buộc như JSON.

Với ảnh tự luận trong đề, `payload` là `{questionId,content:{text?,imageName?,imageHash?},answers:{...},essayAnswers:{...}}`. Adapter đặt ảnh vào `essayAnswers[questionId]`; service kiểm tra câu thuộc đúng attempt và có type ESSAY. Text/đáp án trắc nghiệm khác được giữ lại. JSON không chứa File hoặc blob URL. Response trả tham chiếu ảnh riêng tư `/api/exam-files/{id}`; đọc ảnh phải có quyền trên attempt. Gửi `{text:"...",image:null}` trong essayAnswers của câu đó để bỏ ảnh đã lưu. Lưu sau deadline trả 409; nộp hết hạn chỉ chốt nội dung đã persist.

Servlet kiểm tra tổng dung lượng, số/tên part, định dạng thực, khả năng decode, kích thước pixel. Filename không quyết định đường dẫn. Adapter multipart chuyển dữ liệu vào service hiện có; service tự tính hash, lưu UUID trong private storage và ghi metadata/owner/submission trong transaction. Không có upload public rời rạc. GET `/files/{id}` vẫn kiểm tra quyền. Không nhận multipart tại endpoint khác.

### DTO công khai và bài thi

`StudentQuestionDTO` dùng allowlist: `id,title,content,chapter,topic,type,difficulty,cognitiveLevel,options?,courseId?,published?`. Không chuyển tiếp field lạ từ snapshot, answer, explanation, rubric hoặc score. Teacher giữ DTO tác giả; review chỉ xuất hiện theo quyền công bố kết quả. Client không được tự tạo history/score bằng POST `/learning-history` hoặc sửa EssaySubmission.

`deadlineAt` và `serverTime` là UTC ISO 8601 từ SQL Server. Deadline bằng started_at + duration_seconds. Kiểm tra hạn dùng DATEDIFF_BIG(MILLISECOND)/1000 để tránh sai lệch biên giây. GET trả `expired=true` khi hết giờ; POST answers sau hạn trả 409, POST submit chấm các câu đã persist và bỏ câu đến muộn. Nộp trùng không tạo thêm kết quả. Frontend bù lệch đồng hồ từ serverTime, tự nộp một lần, khóa nhập liệu, và cho retry với cùng key sau lỗi mạng.

### Metadata bài tập

GET `/exercises` trả `{id,title,chapter,kind,difficulty?,questionCount,durationSeconds,status,bestScore?}` cùng metadata câu hỏi tự luận. `kind=essay|quiz`; khóa duy nhất ở UI là kind + id. `status=NOT_STARTED|IN_PROGRESS|COMPLETED`. questionCount tính từ exam_questions, durationSeconds lấy từ exams; essay không có thời hạn quy định trả null. Difficulty của đề là mức cao nhất trong các snapshot. Best score của đề chỉ hiện khi results_published=1; không suy ra hoặc tạo điểm mặc định. Danh sách hiện giới hạn 100 câu hỏi/100 đề theo DAO hiện có.

### Phân trang và cấu hình

Courses và lessons đã có OFFSET/FETCH khớp các tham số JDBC. Không còn tài khoản SQL mặc định trong source. Cấu hình đủ DB_URL, DB_USER và DB_PASSWORD; DB_USERNAME được hỗ trợ khi chưa đặt DB_USER. Tomcat đọc biến môi trường/JVM properties; dùng MATHIO_ENV_FILE để trỏ file .env bên ngoài WAR. Không còn đường dẫn máy cũ trong META-INF/context.xml.

## Cập nhật tích hợp 28/10/2026

### Chọn khóa học khi tạo nội dung

Request tạo/sửa câu hỏi, bài giảng và đề giữ `courseId` thực tế. Request AI `/ai/questions/generate` và `/ai/exams/generate` nhận thêm `courseId?`; nếu có, backend kiểm tra quyền khóa học trước khi gọi provider. Khi lưu, backend chỉ tự suy ra khóa học nếu tác giả có đúng một khóa học hợp lệ. Frontend dùng optgroup trong dropdown chương hiện có để chọn khóa học/chương, không dùng ID mặc định. Teacher không thể gán nội dung vào course ngoài quyền bằng cách sửa body.

### Tìm kiếm toàn cục

`GET /search?q={text}&offset=0&limit=21` → `SearchRecord[]`, mỗi phần tử có `{id,title,chapter,courseId,chapterId,kind}`. `kind=lesson|essay|quiz|question`; student chỉ nhận bài giảng/câu tự luận/đề đã xuất bản trong khóa đã ghi danh. Staff nhận bài giảng và câu hỏi thuộc phạm vi quản lý. Không có answer, explanation hay grading data trong search response.

`q` tối đa 200 ký tự; offset 0–1000000, limit 1–100. Query dùng PreparedStatement và escape `%`, `_`, `[` để tìm literal, collation không phân biệt dấu/chữ hoa. Thứ tự ổn định theo title, id, kind. Frontend hiển thị 20 kết quả/trang, lấy thêm một kết quả để xác định nút trang sau; từ khóa và trang được lưu trong hash URL. Backend không trả tổng số kết quả, không suy ra số tổng giả.

### Tiến độ và thông báo

Lesson wire DTO hiện có `completed=0|1`; frontend chuẩn hóa thành boolean, không hiểu 0 là đã hoàn thành. GET `/notifications` dùng chung cho Sidebar, Topbar và trang thông báo. POST `/notifications/{id}/read` cập nhật trạng thái/bộ đếm khi server xác nhận, không gửi lại với thông báo đã đọc.
