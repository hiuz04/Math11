# Math11 local workflow fixes — 02/11/2026

## Trạng thái

Đã sửa code thực tế và kiểm thử service, HTTP fixtures, JDBC fixtures và Tomcat/WAR. Chưa xác minh end-to-end trên SQL Server/Gemini thật, vì không có cấu hình test được cung cấp. Không chạy setup.sql, seed, migration hoặc ghi vào database người dùng. Không thay stack, CSS, thiết kế Sidebar/Topbar, Tiptap/KaTeX hoặc cấu hình deployment.

## Các lỗi đã xác nhận và sửa

1. Teacher chỉ xem được bài làm: thêm form chấm ESSAY trong modal hiện có, điểm 0–10 và feedback từng câu, xác nhận, khóa request đồng thời, loading/error/success sau phản hồi server. Chỉ gửi câu ESSAY, không gửi điểm MCQ.
2. Bài mixed PENDING_GRADING chưa có trong learning history: bổ sung danh sách attempt/submission theo phạm vi backend, hiển thị trạng thái, mở bài từ danh sách, cập nhật trạng thái sau chấm, mở lại/F5 đọc điểm thật đã lưu. Staff đọc rubric và điểm MCQ do server chấm; Student vẫn nhận DTO công khai.
3. Tự luận độc lập chưa có UI chấm: dùng cùng form với API essays riêng, giữ bài gốc/ảnh/feedback AI, lưu score/feedback và metadata người chấm có sẵn. Backend chặn chấm lại bằng key mới sau khi đã có gradedBy; idempotency replay vẫn hoạt động.
4. API công bố thiếu kiểm tra bài chờ chấm và có thể bị bypass qua saveExam: thêm kiểm tra trong transaction, giữ phân quyền, ngăn công bố khi còn PENDING_GRADING, công bố lại không thêm audit/update; resultsPublished là metadata read-only khi lưu đề.
5. Đề mới mặc định published=true và database cũ mặc định results_published=1: mặc định nháp và INSERT tường minh results_published=0. Giữ nguyên trạng thái dữ liệu cũ. Thêm Lưu nháp/Xuất bản, trạng thái kết quả và nút Công bố kết quả; mở lại đề đã lưu để sửa. Backend vẫn cấm sửa đề đã có attempt.
6. Thiếu thời lượng trên form: thêm phút 1–240, lưu giây; server kiểm tra integer, overflow và khoảng 60–14400. Xuất bản cần course hợp lệ. Có thể sửa tên đề; câu hỏi vẫn được validate theo matrix/catalog hiện có. Câu hỏi độc lập có checkbox xuất bản trong form cũ.
7. Nhập tự luận chỉ lưu onBlur và F5 có thể mất nội dung: thêm debounce, lưu chủ động, nháp sessionStorage theo userId+attemptId. Nháp văn bản/MCQ không chứa File/blob. Ảnh chưa upload được đánh dấu và cảnh báo chọn lại, kể cả refresh tiếp. Nháp bị xóa sau submit được xác nhận hoặc khi hết hạn/không còn hợp lệ.
8. Hai tab có thể ghi đè: server phát revision opaque từ câu trả lời đã persist, kiểm tra dưới khóa attempt trước khi ghi. Client gửi revision trong JSON/multipart, giữ nội dung khi lỗi và khóa đồng bộ khi 409; người dùng chủ động tải dữ liệu server. Không tự gán revision mới cho nháp cũ sau xung đột. Deadline/idempotency/submit một lần vẫn do server quyết định.
9. Management chỉ tải trang đầu và phân trang giả: users/students/courses/lessons/questions có request offset/limit thực, lọc SQL trước phân trang, nút trước/sau, loading, generation guard, reset trang khi đổi filter, lưu filter/page trong URL. Exam list có trang thực. Lookup/dropdown và history đọc các trang backend để không cắt ở 100 bản ghi. Array contract giữ nguyên, không bịa total.
10. UNKNOWN được ghi correct=false ở public step và có thể lặp mãi: giữ correct=null, không cấp điểm/next-step; sau 3 UNKNOWN chuyển NEEDS_REVISION để nộp lời giải rõ hơn/nhờ giáo viên. Giữ feedback AI gốc, private plan, lịch sử retry và kiểm tra ảnh mới hiện có.
11. Chapter/topic thiếu CRUD: thêm Admin form trong màn hình bài giảng hiện có và route trên LessonController/CatalogDAO hiện có. Kiểm tra chapter/topic, trùng tên, tham chiếu/FK, ngăn đổi tên/move làm sai exam snapshot và ngăn xóa catalog đang dùng. Không tự thay danh mục.

## API

Không thêm controller/service trùng chức năng. Chi tiết request/response được cập nhật tại [BE/API.md](BE/API.md).

- Mới: POST /api/chapters, /api/chapters/{id}, /api/topics, /api/topics/{id}; DELETE /api/chapters/{id}, /api/topics/{id}. Admin only.
- Chỉnh: POST /api/exams (default draft, duration/course validation, read-only result metadata); POST /api/exams/{id}/publish-results (pending check, repeat-safe).
- Chỉnh: GET quiz-attempts summaries/detail (studentId/title, staff rubric/MCQ scores, revision); POST quiz-attempts/{id}/answers và submit (optional revision để tương thích client cũ).
- Chỉnh: POST essays/{id}/grade (manual-grade freeze); Tutor check-step (UNKNOWN fallback).
- Chỉnh: GET users/courses/lessons/questions thêm filter SQL, giữ offset/limit và response array.
- Frontend nối API grade quiz, grade essay và publish-results hiện có; giữ session, CSRF, Idempotency-Key, upload private và phân quyền server.

## Database

Migration tùy chọn [V003__topic_order.sql](BE/database/migrations/V003__topic_order.sql) chỉ thêm topics.position và version 3; không thay hoặc seed dữ liệu cũ. Chưa chạy migration. App vẫn CRUD trên schema cũ; đặt thứ tự topic khác 0 yêu cầu V003 và trả 409 rõ ràng nếu thiếu. Hướng dẫn backup/apply/rollback: [README-MIGRATIONS.md](BE/database/README-MIGRATIONS.md). Core grading/draft/revision không cần migration mới ngoài V002 đã có của dự án.

## Kiểm thử thực sự đã chạy

| Kiểm tra | Kết quả |
| --- | --- |
| node node_modules/typescript/bin/tsc --noEmit (FE) | PASS |
| node --import tsx --env-file=.env.test --test tests/services.test.ts (FE) | PASS 18/18 |
| node node_modules/@playwright/test/cli.js test --config playwright.api.config.ts (FE) | PASS 28/28; HTTP fixtures, không SQL/Gemini thật |
| node node_modules/vite/bin/vite.js build (FE) | PASS; còn warning chunk >500 kB |
| Maven -o -B -Dmaven.repo.local=.m2 verify, JDK 17 (BE) | PASS: 219 ca, 214 pass, 5 skipped opt-in |
| RUN_WAR_DEPLOYMENT=true; Maven -o -B -Dmaven.repo.local=.m2 -Dtest=WarDeploymentTest test | PASS 2/2; WAR thật trên Tomcat 10.1 tạm, kiểm tra cả catalog mappings, không ghi SQL |

Dùng executable Node trực tiếp tương đương các npm scripts vì npm không có trong PATH. Dependencies từ lockfile hiện có; không thay dependency manifest/lockfile. JDK/Maven portable trong .tools được ignore. Logs/build/test output không nằm trong commit.

Các ca mới bao phủ chấm mixed/độc lập và F5, validation score/MCQ, publication chờ chấm, draft/private defaults, revision stale write, filter SQL binding, phân trang qua bản ghi 100/125, URL restore, nháp offline và conflict, UNKNOWN lặp, catalog scope/referenced delete và CRUD UI. Các lỗi fixture/nhãn dropdown đã được sửa trước lượt xác nhận cuối; không nới validation để test pass.

## Luồng đã nối và giới hạn xác minh

Admin quản lý user/course/enrollment và catalog; Teacher chọn đúng học sinh, xem bài, chấm 2 loại tự luận, tạo/lưu nháp/sửa/xuất bản đề và công bố kết quả; Student lưu/resume/nộp bài và đọc kết quả theo quyền. Luồng Tutor giữ gating và có lối xử lý UNKNOWN an toàn. Kiểm thử hiện có về session/CSRF/ownership/deadline/upload/idempotency vẫn qua.

Chưa xác minh live: SQL persistence/transaction/races/FK/migration trên SQL Server thật; Gemini evaluation/generation/timeout bằng key thật; toàn bộ chu trình Admin → Teacher → Student trên cùng database thật. Ba SqlServer integration tests và Gemini live test vẫn opt-in. Kết quả JDBC/HTTP fixtures không chứng minh các mục này. V003 cần người vận hành backup và áp dụng thủ công trước khi dùng topic ordering. Không push remote.

## Files thay đổi

- `BE/src/main/java/com/mathio/lms/DAO/CatalogDAO.java`
- `BE/src/main/java/com/mathio/lms/DAO/ExamDAO.java`
- `BE/src/main/java/com/mathio/lms/DAO/QuestionDAO.java`
- `BE/src/main/java/com/mathio/lms/DAO/UserDAO.java`
- `BE/src/main/java/com/mathio/lms/control/CourseController.java`
- `BE/src/main/java/com/mathio/lms/control/LessonController.java`
- `BE/src/main/java/com/mathio/lms/control/QuestionController.java`
- `BE/src/main/java/com/mathio/lms/control/UserController.java`
- `BE/src/main/java/com/mathio/lms/model/EssayService.java`
- `BE/src/main/java/com/mathio/lms/model/ExamService.java`
- `BE/src/main/java/com/mathio/lms/model/TutorService.java`
- `BE/src/test/java/com/mathio/lms/AiConcurrencyTest.java`
- `BE/src/test/java/com/mathio/lms/MixedExamTest.java`
- `BE/src/test/java/com/mathio/lms/WarDeploymentTest.java`
- `BE/src/test/java/com/mathio/lms/WorkflowValidationTest.java`
- `FE/src/components/essay/TeacherGradingForm.tsx`
- `FE/src/components/teacher/CatalogManagement.tsx`
- `FE/src/pages/admin/UserManagementPage.tsx`
- `FE/src/pages/management/ApiDataManagementPage.tsx`
- `FE/src/pages/student/ApiQuizPage.tsx`
- `FE/src/pages/student/EssayTutorPage.tsx`
- `FE/src/pages/teacher/ApiStudentDetailPage.tsx`
- `FE/src/pages/teacher/ExamGeneratorPage.tsx`
- `FE/src/pages/teacher/QuestionBankPage.tsx`
- `FE/src/services/essayService.ts`
- `FE/src/services/historyService.ts`
- `FE/src/services/lmsService.ts`
- `FE/src/services/questionService.ts`
- `FE/src/services/quizApiService.ts`
- `FE/src/services/routeState.ts`
- `FE/src/services/userService.ts`
- `FE/src/services/listRoute.ts`
- `FE/src/services/pagination.ts`
- `FE/src/services/quizDraft.ts`
- `FE/src/types/lms.ts`
- `FE/tests/browser-api/api.spec.ts`
- `FE/tests/services.test.ts`
- `BE/database/migrations/V003__topic_order.sql`
- `BE/database/README-MIGRATIONS.md`
- `BE/API.md`
- `.gitignore`
- `WORKFLOW_FIX_REPORT.md`

Commit: xem hash trong câu trả lời cuối hoặc git log -1; message theo yêu cầu: fix: complete local LMS business workflows and grading.
