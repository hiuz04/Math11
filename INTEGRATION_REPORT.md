# Mathio — báo cáo tích hợp frontend/backend

Cập nhật: 28/10/2026. Trạng thái tổng thể: **PARTIAL**. Frontend và backend đều có trong workspace; đã sửa code thực tế và đối chiếu servlet mappings. Chưa chứng minh toàn bộ nghiệp vụ trên SQL Server/Gemini thật với cấu hình mới. Browser API tests dùng HTTP fixtures, không được xem là kiểm thử database/provider thật.

## A. Changes Summary

| File/nhóm file | Mục đích |
| --- | --- |
| FE/src/App.tsx; services/routeState.ts | Guard role tập trung, không fallback vào quản lý; URL lưu ID, F5/back/forward; session restore từ backend |
| FE/src/services/api.ts; authService.ts; serviceMode.ts | Cookie/CSRF, multipart boundary, lỗi HTTP tiếng Việt, AbortSignal, mutation deduplication và retry chủ động cùng Idempotency-Key |
| FE/src/services/storage.ts; useResource.ts; lmsService.ts | Chặn demo storage trong real mode; chống response cũ; metadata thật, cancellation cho catalog |
| FE/src/services/quizApiService.ts; types/lms.ts | Student/Teacher/Review DTO riêng; start/resume/save/submit; deadline theo server; essay text và ảnh multipart trong exam |
| FE/src/pages/student/ApiQuizPage.tsx; ApiQuizResultPage.tsx | F5 khôi phục attempt; auto submit một lần; khóa sau hạn; retry offline; chờ công bố/chấm; review tự luận |
| FE/src/pages/student/ExercisesPage.tsx | ID ổn định, questionCount/duration/status/bestScore thật, bốn bộ lọc, loading/error/empty |
| FE/src/services/imageService.ts; essayService.ts; aiTutorService.ts; aiService.ts | Multipart cho essay/retry/chat; File + blob preview ở real mode, demo vẫn riêng |
| FE/src/components/essay/EssayUpload.tsx; pages/student/EssayPracticePage.tsx; EssayTutorPage.tsx; AITutorPage.tsx; components/ai/AiDrawer.tsx | Giữ ảnh khi đổi input mode; quản lý vòng đời preview; gửi bytes thay blob URL |
| FE/src/services/questionValidation.ts; questionService.ts; pages/teacher/QuestionGeneratorPage.tsx; components/teacher/GeneratedQuestionEditor.tsx | Tạo lại đúng một câu, giữ câu khác/metadata; kiểm tra MCQ/essay, chống trùng |
| FE/src/pages/teacher/QuestionBankPage.tsx; CreateLessonPage.tsx | ID câu mới âm theo contract; bỏ draft/chapter demo trong real mode |
| FE/src/components/teacher/CourseChapterSelect.tsx; pages/teacher/ExamGeneratorPage.tsx | Chọn course/chapter thật trong dropdown hiện có; lưu và tạo AI giữ courseId, yêu cầu chọn khi có nhiều course |
| FE/src/pages/teacher/ApiStudentDetailPage.tsx | Mở chi tiết bài thi/tự luận từ lịch sử học sinh theo quyền server |
| FE/src/pages/student/LessonPage.tsx; CoursesPage.tsx; CourseDetailPage.tsx; ProgressPage.tsx | Báo lỗi lưu tiến độ nền, ID chương đúng, bỏ nhãn/highlight demo |
| FE/src/pages/management/ManagementDashboard.tsx; ApiDataManagementPage.tsx | Nhãn tháng từ analytics; bộ lọc hoạt động |
| FE/src/pages/admin/UserManagementPage.tsx; pages/management/ApiDataManagementPage.tsx | Chống refresh cũ ghi đè sau mutation; ghi danh từ danh sách học sinh/khóa học thực |
| FE/src/services/searchService.ts; pages/shared/SearchResultsPage.tsx; components/layout/Topbar.tsx | Tìm kiếm qua backend, phân trang, khôi phục từ URL và mở đúng ID |
| FE/src/services/notificationService.ts; App.tsx; Sidebar/Topbar/NotificationsPage | Dùng chung thông báo và bộ đếm; trạng thái đọc chỉ cập nhật sau server xác nhận |
| FE/package.json; tests/services.test.ts; tests/browser-api/api.spec.ts; tests/browser/additional.spec.ts | Chế độ test explicit; các ca hồi quy và hợp đồng |
| BE/src/main/java/com/mathio/lms/DAO/CatalogDAO.java | Sửa SQL phân trang thiếu OFFSET/FETCH so với bind parameters |
| BE/src/main/java/com/mathio/lms/DAO/ExamDAO.java; model/ExamService.java | Deadline/serverTime SQL; tính hạn chính xác; DTO công khai |
| BE/src/main/java/com/mathio/lms/DAO/QuestionDAO.java; model/StudentQuestionDTO.java; RichText.java | Allowlist công khai, metadata bài tập thật, validate nội dung sạch và chống bulk trùng; giữ công thức |
| BE/src/main/java/com/mathio/lms/control/MultipartContent.java; ApiController.java; SecurityFilter.java; EssayController.java; AiController.java; ExamController.java | Multipart giới hạn tại endpoint cho phép, bao gồm exam essay; validate bytes; session hết hạn trả 401 |
| BE/src/main/java/com/mathio/lms/control/ReportingController.java; DAO/ReportingDAO.java; model/AiService.java | Search SQL theo quyền, escape wildcard và phân trang; kiểm tra course trước gọi Gemini |
| BE/src/main/java/com/mathio/lms/DAO/DBContext.java; src/main/webapp/META-INF/context.xml | Bỏ credentials mặc định và đường dẫn .env máy cũ |
| BE/src/test/java/com/mathio/lms/FrontendIntegrationRegressionTest.java; QuizAndImageTest.java; ApiContractTest.java; HttpSecurityTest.java | DTO không lộ grading, SQL binding, expiry và HTTP multipart thật |
| BE/scripts/InspectDatabase.java; BE/API.md; BE/README.md | Công cụ read-only và tài liệu hợp đồng/chạy |

Không sửa CSS, màu/font/spacing/breakpoint, Sidebar/Topbar layout hoặc giao diện Tiptap/KaTeX.

## B. Fixed Bugs

| Bug | Root cause | File chính | Result |
| --- | --- | --- | --- |
| Upload mất boundary | Mutation luôn bị gán JSON Content-Type | api.ts, MultipartContent.java | Browser tự đặt multipart boundary; service + Tomcat HTTP tests |
| Trang quản lý sai role | if/else và fallback mở | App.tsx, routeState.ts | Guard trước mount, URL không vượt quyền |
| F5 mất trang/ID | Chỉ giữ React state | routeState.ts | Hash URL phục hồi ID sau session restore |
| Session mutation hết hạn trả 403 | CSRF chạy trước xác thực | SecurityFilter.java | 401 cho hết phiên; 403 không logout phiên hợp lệ |
| Snapshot có thể lộ trường grading mới | Chỉ remove answer | StudentQuestionDTO.java, ExamService.java | Allowlist không copy answer/explanation/rubric/score/privatePlan |
| Resume vẫn tạo attempt | Tìm open rồi tiếp tục POST | quizApiService.ts | GET attempt mở, concurrent start dùng chung promise |
| Timer không auto-submit / submit trùng | Không có expiry effect/lock | ApiQuizPage.tsx | Một auto-submit; retry cùng key sau lỗi mạng; khóa input |
| Đồng hồ lệch backend | Client clock + DATEDIFF biên giây | ExamDAO.java, quizApiService.ts | SQL deadline/serverTime, bù lệch, server quyết định |
| 0 câu/thời gian giả/nhầm bài trùng tên | Thiếu metadata, tìm bằng title | QuestionDAO.java, ExercisesPage.tsx | COUNT thật, duration từ exams, kind+id |
| Tạo lại làm mất cả bộ | Gọi generate toàn bộ | QuestionGeneratorPage.tsx | count=1, thay đúng ID, giữ câu khác |
| Tạo câu mới 404 | ID Date.now dương bị coi là bản ghi cập nhật | QuestionBankPage.tsx | ID tạm âm đúng backend contract |
| Lưu câu rỗng/trùng | Validation thiếu/sanitize sau kiểm tra | questionValidation.ts, QuestionDAO.java | Validation client/server; math-only hợp lệ |
| Catalog có thể 500 | SQL thiếu placeholders phân trang | CatalogDAO.java | OFFSET/FETCH khớp bind, regression test |
| Ảnh mất khi đổi tab / chat gửi blob URL | Preview sở hữu sai component, API nhận string | Essay pages, aiService.ts | Page giữ File/preview; transport gửi bytes |
| Real mode dùng demo storage | Mode bật mock ngầm | serviceMode.ts, storage.ts | Mock chỉ opt-in; real mode chặn local demo storage |
| Kết nối SQL dựa tài khoản hardcode | Fallback trong DBContext | DBContext.java | Chỉ nhận cấu hình riêng |
| Phản hồi cũ có thể cập nhật phiên mới | Request/body/CSRF hoàn thành sau đổi phiên | api.ts, authService.ts | Generation guard trước/sau fetch/parse; CSRF single-flight theo phiên, AbortError được giữ |
| StrictMode gửi GET trùng | Effect gọi load trước cleanup | useResource.ts, SearchResultsPage.tsx | Bỏ lượt đã hủy trước khi gửi; test đếm request |
| Lesson completed=0 bị hiểu sai | Java wire DTO dùng integer | lmsService.ts | Chuẩn hóa 0/1 thành boolean và từ chối giá trị sai |
| Khó lưu khi có nhiều course | Form chỉ chọn chương, thiếu courseId | CourseChapterSelect.tsx | Chọn trong optgroup, giữ layout/form row hiện có |
| Search/notification chưa đồng bộ | Topbar chưa gọi search; nhiều nguồn thông báo | SearchResultsPage.tsx, App.tsx | Search paging theo quyền; một GET thông báo ban đầu, read cập nhật cùng bộ đếm |

## C. API Integration Report

Đường dẫn tính từ `/api`. Mapping được đối chiếu controller thật. Chi tiết DTO đầy đủ: `BE/API.md`. PARTIAL nghĩa là đã nối code/hợp đồng nhưng chưa đạt đủ Definition of Done trên SQL/provider thật.

| Feature / frontend service | Method / endpoint / servlet | Request → response | Mapping | Status / Test |
| --- | --- | --- | --- | --- |
| CSRF / api | GET auth/csrf / AuthController | — → {csrfToken} | Matched | COMPLETED: Tomcat + WAR + browser |
| Login/restore/logout/password / authService | POST auth/login; GET auth/me; POST auth/logout, auth/change-password / AuthController | Credentials/passwords → session/204 | Matched | PARTIAL: transport/session/BCrypt tests |
| Courses / lmsService | GET courses / CourseController | paging? → CourseRecord[] | SQL mismatch fixed | PARTIAL: JDBC/controller tests |
| Chapters/topics / lmsService | GET chapters?courseId; GET topics / LessonController | courseId? → catalog[] | Matched | PARTIAL: curriculum fixture |
| Lessons/progress / lmsService,lessonService | GET/POST lessons; GET/POST lessons/{id}; POST .../progress / LessonController | metadata+HTML / progress → detail/{id}/{saved} | SQL mismatch fixed | PARTIAL: lesson save/reload, backend sanitation |
| Exercises / lmsService | GET exercises / QuestionController | — → ExerciseRecord[] | Missing metadata fixed | PARTIAL: filters/duplicate titles fixture |
| Student question / essayService | GET questions/{id} / QuestionController | — → StudentQuestionDTO | Hardened | PARTIAL: allowlist test |
| Questions / questionService | GET questions; POST questions/bulk; DELETE questions/{id} / QuestionController | TeacherQuestionDTO[] → persisted questions/204 | New-ID mismatch fixed | PARTIAL: create/filter/validation fixture |
| Exams / questionService | GET/POST exams; GET/DELETE exams/{id} / ExamController | Exam/matrix → Exam/204 | Matched | PARTIAL: matrix/backend tests |
| Start/resume / quizApiService | POST exams/{id}/attempts; GET quiz-attempts/{id} / ExamController | {} / — → QuizAttempt | Duplicate start fixed | PARTIAL: start/resume/F5 tests |
| Answers/submit/result / quizApiService | POST quiz-attempts/{id}/answers, submit; GET quiz-attempts/{id} / ExamController | JSON answers/essayAnswers hoặc multipart payload+image, no score → QuizAttempt/allowed review | Matched | PARTIAL: expiry/offline/dedup/text/image HTTP tests |
| Essay/images / essayService | POST essays; GET essays, essays/{id}, files/{id} / EssayController | JSON text or multipart payload+image → EssaySubmission/binary | Multipart added | PARTIAL: browser encoding + HTTP Tomcat + ACL tests |
| Evaluation/Tutor/retry / aiTutorService | POST ai/essays/evaluate, evaluate-retry; ai/tutor/start, check-step, next-step / AiController | submissionId/stepNumber/answer or multipart → unlocked EssaySubmission | Matched | PARTIAL: gating/math/image/history tests; live AI pending |
| History / historyService | GET learning-history / ReportingController | studentId? (staff scope) → HistoryEntry[] | Matched | PARTIAL: history/ownership tests |
| Client history writes | POST learning-history / ReportingController | client entry → 403 | Intentionally denied | COMPLETED: server owns scores/history |
| Dashboard/analytics/students / dashboardService,lmsService | GET dashboard, analytics, students/{id}/statistics / ReportingController | — → scoped statistics | Matched | PARTIAL: no mock fallback/scope tests |
| Users / userService | GET/POST users; POST users/{id}; DELETE users/{id} / UserController | user/password/role/active → UserRecord/204 | Matched | PARTIAL: browser create/filter/lock/delete |
| Course admin/enrollment / management page | POST courses, courses/{id}, courses/{id}/enrollments; DELETE courses/{id} / CourseController | metadata/{studentId} → {id}/{saved}/204 | Matched | PARTIAL: backend role/controller + teacher enrollment browser test |
| AI generation / questionService | POST ai/questions/generate, ai/exams/generate / AiController | GenerationRequest/matrix → draft questions/exam | Matched | PARTIAL: single regeneration + matrix fixtures |
| Gemini/prompts / adminConfigService | GET/POST admin/config/gemini, prompts; POST .../gemini/test / ConfigurationController | config/key?/Prompts → masked config/status | Matched | PARTIAL; live connection BLOCKED by config |
| Chat / aiService | POST ai/chat; GET ai/conversations, ai/conversations/{id} / AiController | message/image/conversationId or multipart → reply/history | Multipart added | PARTIAL: disabled-provider fixture; live pending |
| Profile/notifications / shared pages | GET/POST profile / AuthController; GET notifications, POST .../{id}/read / ReportingController | profile/{} → profile/scoped records | Matched | PARTIAL: server contracts + browser count/read synchronization |
| Global search / searchService | GET search?q=&offset=&limit= / ReportingController | bounded query → SearchRecord[] metadata only | Added | PARTIAL: SQL binding/scope tests + browser pagination/F5/exact-ID/error |

Backend có grade/publish-results cho đề mixed và grade essay; chưa có màn hình giáo viên tương ứng, không báo COMPLETED chỉ vì endpoint tồn tại.

## D. Database Changes

Không tạo/thay database, không chạy seed/migration trên database người dùng. Lần sửa này không cần migration mới: metadata lấy từ bảng hiện có. Database cũ vẫn cần migration có sẵn `BE/database/migrations/V002__backend_reliability.sql` nếu chưa có version 2; thực hiện theo `BE/database/README-MIGRATIONS.md`, backup và kiểm tra trước. Không dùng setup.sql thay thế schema đang chạy. Test SQL bắt buộc database riêng kết thúc `_test`.

## E. Testing Results

| Command | Kết quả xác nhận |
| --- | --- |
| npm ci --cache .npm-cache --no-audit --no-fund | PASS; lần sandbox Windows EPERM được chạy lại với quyền đã duyệt |
| npm run typecheck | PASS, chạy lại 28/10 |
| npm run build | PASS, chạy lại 28/10; warning chunk >500 kB và Vite config loader; CSS hash vẫn index-CUlGzZeP.css |
| npm run test:services | PASS 12/12, chạy lại 28/10 |
| LMS_TEST_PORT=8564; npm run test:e2e | PASS 8/8 (28/10), responsive 1440/768/390; log FE/demo-e2e-final.log |
| LMS_TEST_PORT=8565; npm run test:e2e:api | PASS 22/22 (28/10) — HTTP fixtures, không SQL/Gemini thật; log FE/api-e2e-final.log |
| Maven -o -B -Dmaven.repo.local=.m2 verify, Java 17 | PASS 197 ca: 192 pass, 5 skipped opt-in; WAR tạo thành công (27/10) |
| RUN_WAR_DEPLOYMENT=true, Maven -o -B -Dmaven.repo.local=.m2 -Dtest=WarDeploymentTest test | PASS 1/1 (28/10) trên Tomcat 10.1 tạm; mapping search và multipart của cả ba servlet trong WAR đúng; không write SQL; log BE/war-deployment.log |
| DBContext .env, Java 17 | Check read-only ban đầu qua fallback cũ thành công; sau khi bỏ fallback, cấu hình credentials rỗng gây login failure; không coi integration đã pass |
| scripts/InspectDatabase.java | BLOCKED do credentials SQL trống, không write |
| Ba SqlServer*IntegrationTest | SKIPPED, chưa bật RUN_SQL_INTEGRATION/cấu hình *_test |
| GeminiLiveIntegrationTest | SKIPPED; chưa gọi provider thật |

Các kiểm thử Tomcat/browser dùng loopback ngoài sandbox qua quyền thực thi được duyệt và port riêng. Không đổi firewall/server người dùng. Các lỗi test fixture/locator hoặc test cũ thiếu hướng dẫn chấm đã sửa, không nới validation để test pass.

Ca WAR mở rộng ban đầu fail vì đọc multipart config trước khi Tomcat lazy-load servlet. Đã đối chiếu StandardWrapper của Tomcat cài sẵn, sửa test gọi load rồi kiểm tra annotation/giới hạn thật; chạy lại pass. Không bỏ assertion hoặc thay cấu hình multipart giả để qua test. Service tests mới xác nhận CSRF đang chờ của phiên cũ không thay token phiên mới, body JSON đang đọc bị bỏ khi đổi phiên và cancellation giữ AbortError thay vì báo lỗi server.

## F. Remaining Issues

1. `BE/.env` vẫn trống DB_USERNAME, DB_PASSWORD, DB_URL, GEMINI_API_KEY, APP_ENCRYPTION_KEY tại lần kiểm tra 28/10; process cũng không có các biến này. Người dùng sẽ cấu hình; không gửi secrets vào chat. DB_URL không bắt buộc nếu host/name và cơ chế xác thực riêng đã đủ. Cần SQL test database `_test` có V002 và key thử nghiệm để xác minh persistence/ownership/AI thật.
2. Chưa smoke-test WAR mới trên Tomcat người dùng qua Vite proxy với SQL/Gemini thật. WAR smoke hiện cô lập, dùng cấu hình giả có chủ đích.
3. Backend hỗ trợ chấm/công bố đề mixed và chấm essay; UI teacher hiện xem được chi tiết bài làm, chưa có các thao tác chấm/công bố đó. Không tự xuất bản câu/đề hoặc kết quả khi người dùng chỉ lưu bản nháp.
4. Câu/đề mới cần courseId rõ ràng nếu staff có nhiều course; dropdown chương hiện đã hỗ trợ chọn course. Các draft chưa chọn bị chặn trước lưu; không dùng ID giả để mở quyền.
5. Backend mặc định tối đa 100 records, chưa có server pagination đầy đủ trên mọi trang danh sách cũ. Search Topbar đã phân trang server và mở được tài nguyên theo ID ngoài trang đầu. Các chỉ số phân tích chưa có DTO trả về vẫn hiển thị rỗng/gợi ý chung, không tạo điểm giả.
6. Câu chưa được server xác nhận khi mất mạng chỉ giữ trong page đang mở; F5/đóng trước retry không khôi phục phần chưa persist. UI báo lỗi lưu; không dùng localStorage thay database ở real mode.

## G. Running Instructions

### Backend

- Điền BE/.env từ .env.example, dùng credentials riêng; xác minh schema V002. Không bật cờ SQL test trên database chính.
- Trong BE/: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run.ps1 -Action verify`. Có cache thì Maven offline: `mvn -o -B -Dmaven.repo.local=.m2 verify`.
- Deploy `BE/target/Math11.war` lên Tomcat 10.1/JDK 17/context `/Math11`. NetBeans VM option: `-DMATHIO_ENV_FILE=D:/PRJ/ProjectPRJ/BE/.env`. Standalone: `scripts/run.ps1 -Action tomcat -TomcatHome <path>` tải env vào process.
- GET `http://localhost:<cổng-thực-tế>/Math11/api/auth/csrf` phải trả JSON. APP_ORIGIN khớp React origin.

### Frontend

```dotenv
VITE_USE_MOCK_API=false
VITE_API_BASE_URL=/api
BACKEND_URL=http://localhost:8080
BACKEND_CONTEXT=/Math11
```

Đặt trong FE/.env.local, thay 8080 bằng cổng Tomcat thực tế. BACKEND_URL chỉ origin; context chỉ khai báo một lần. Trong FE/: `npm ci`, `npm run dev` (8443). Restart Vite sau thay env. Proxy /api → /Math11/api và rewrite cookie path `/`.

Route `/#/quiz?examId=...&attemptId=...`/`/#/essay-result?submissionId=...` không cần Servlet rewrite cho F5. Production: phục vụ dist + proxy `/api` đến WAR trên cùng HTTPS origin; rewrite cookie Path từ /Math11 sang /, giữ HttpOnly/Secure/SameSite; APP_ENV=production, APP_ORIGIN chính xác và SQL certificate validation. Không dùng Vite dev server/CORS wildcard cho production.

Browser test: `$env:LMS_TEST_PORT='8505'; npm run test:e2e:api`. Tên api chỉ frontend real adapter với HTTP fixture; các test SQL/Gemini opt-in là nhóm khác.

### Kiểm thử SQL/Gemini khi đã có cấu hình

Chuẩn bị database SQL riêng có tên kết thúc `_test`, schema và V002 theo `BE/database/README-MIGRATIONS.md`. Không trỏ bài test write vào database chính. Trong process Maven, cung cấp cấu hình riêng cho database đó và `RUN_SQL_INTEGRATION=true`; chạy `mvn -o -B -Dmaven.repo.local=.m2 "-Dtest=SqlServer*IntegrationTest" test`. Các test SQL AI dùng provider fixture có khai báo; chưa thay thế live Gemini test.

Với Gemini key thử nghiệm đã có ở môi trường backend, đặt `RUN_GEMINI_INTEGRATION=true`, chạy `mvn -o -B -Dmaven.repo.local=.m2 -Dtest=GeminiLiveIntegrationTest test`. Lệnh này gọi provider thật và có thể phát sinh phí; không đưa key vào dòng lệnh/chat/bundle. Sau đó mới chạy các luồng student/teacher/admin qua Vite proxy với session và database thật để nâng trạng thái PARTIAL lên COMPLETED.
