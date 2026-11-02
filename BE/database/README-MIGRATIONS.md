# SQL Server: nâng cấp V002 thủ công

Không có migration/seed tự chạy khi backend khởi động. Kết nối database thật trong phiên review ngày 25/10/2026 bị từ chối đăng nhập (SQL error 18456); các script chưa được chạy ở phiên này.

1. Dừng ghi dữ liệu trong thời gian nâng cấp, ghi lại phiên bản WAR và cấu hình đang dùng. Trong SSMS, **Tasks → Back Up**, chọn Full, Copy-only, Checksum, đường dẫn backup trên máy chủ SQL và tên tệp mới. Backup thêm private storage cùng thời điểm, vì database chỉ lưu khóa file UUID. Kiểm tra backup bằng Restore Verifyonly và thử restore sang database khác trước khi sửa database chính.
2. Database đã tồn tại: chọn đúng database trong SSMS, đọc rồi chạy `migrations/V002__backend_reliability.sql`. Script nằm trong một transaction, có XACT_ABORT và rollback khi có lỗi; ghi version 2 khi hoàn thành. Script không xóa hàng dữ liệu, không DROP database. Nó thay CHECK của trạng thái attempt, thêm bảng/cột/index; các cấu trúc cũ khác vẫn được giữ.
3. Database mới: chạy `setup.sql`; script đã bao gồm chính V002. Sau đó tùy chọn chạy `seed-demo.sql` cho môi trường demo. Không chạy seed bằng tài khoản demo công khai trong production.
4. Chạy `validate-schema.sql` và xem **mọi result set/DBCC message**, không chỉ dòng PRINT cuối. Script kiểm tra bảng/cột/kiểu dữ liệu, JSON text, FK/constraint, index, option MCQ, snapshot, matrix, ownership ảnh và dữ liệu công bố thiếu course. Các cảnh báo cấu trúc hoặc seed không được xem là PASS ứng dụng.
5. Gán `questions.course_id` và `exams.course_id` của dữ liệu cũ theo phân công thật. Questions cũ mặc định `published=0`; chỉ bật sau khi duyệt. Không suy ra quyền từ việc giáo viên từng dạy cùng học sinh ở course khác. API tạo mới tự chọn course chỉ khi có đúng một course trong quyền; hãy truyền courseId rõ ràng.
6. Essay mới lưu course và private reference tại lúc nộp. Với bài cũ, course có thể NULL; chỉ author/admin được đọc nếu không có course. Nếu cần course teacher đọc bài cũ, gán `essay_submissions.course_id` theo hồ sơ phân công thực tế. Không thể phục hồi chắc chắn reference cũ nếu question đã sửa trước V002; cần giáo viên kiểm tra/chấm thủ công các bài đó.
7. Deploy WAR mới, kiểm tra CSRF/login/logout, các ca student/teacher chéo course, quiz, mixed grading và Tutor trên database kiểm thử trước khi mở lại ghi dữ liệu.

## Database kiểm thử

Tạo riêng `math11_tutor_db_test`. Trong bản sao của setup/seed mở trong SSMS, thay **tất cả** tên `math11_tutor_db` thành `math11_tutor_db_test`, kiểm tra USE/guard rồi tự Execute. V002 riêng chấp nhận tên kết thúc `_test`. Không dùng database production với cờ test.

Tạo `.env.test` riêng, copy các key từ `.env.example`, đặt DB_URL với databaseName=math11_tutor_db_test cùng DB_USER và DB_PASSWORD kiểm thử hợp lệ. databaseName trong URL phải trỏ đúng `_test`. Đặt RUN_SQL_INTEGRATION=true; giữ RUN_GEMINI_INTEGRATION=false. Chạy:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\run.ps1 -Action verify -EnvFile .env.test
```

Ba test SQL thực thi DAO/service thật. Test gốc và test mixed/security rollback toàn bộ dữ liệu. Test AI cần commit các phase để kiểm chứng lease/idempotency; nó chỉ tạo tài nguyên UUID trong `_test` và dọn đúng các ID đó trong finally. Gemini trong test SQL AI là fixture được khai báo rõ; ảnh là file thật trong thư mục tạm. Nếu tiến trình bị kill, dữ liệu UUID kiểm thử có thể còn lại: chỉ dọn sau khi xác minh đúng database/ID kiểm thử.

## Rollback

Ưu tiên sửa tiến tới với schema V002 được giữ nguyên. Không có script DROP cột/bảng tự động vì sẽ làm mất bài tự luận, điểm và lịch sử mới. Nếu phải quay lại WAR cũ, trước hết phải xử lý mọi PENDING_GRADING/GRADED và AI reservation; WAR cũ không hiểu đề mixed. Nếu cần restore backup, dừng ứng dụng, lưu/export mọi phát sinh sau backup và private storage, thử restore sang database mới trước, đối chiếu rồi mới quyết định chuyển connection. Restore ghi đè database chính là thao tác riêng do DBA xác nhận; không được tự chạy trong pipeline này.

## Giới hạn kiểm chứng

ScriptDom parse được cú pháp không chứng minh quyền ALTER, dữ liệu cũ, lock/race, query runtime hay rollback trên SQL Server. Chỉ đánh dấu SQL integration PASS khi các test thực sự kết nối `_test` đã chuẩn bị và chạy thành công.

## V003 — optional topic ordering (02/11/2026)

File: `migrations/V003__topic_order.sql`. This adds only `topics.position` (INT, default 0) and records schema version 3. Existing titles, identifiers, relationships and order by ID are preserved. The application supports an unmigrated schema; setting a nonzero topic position then returns a clear 409.

Before applying manually in SSMS:

1. Confirm the intended database name and existing V002 schema. Inspect the script; do not run setup.sql on an existing database.
2. Take a full database backup to a separately managed backup location and confirm that it can be restored. Record the existing schema version and topic list/order.
3. Stop catalog writes, select that database explicitly, and execute V003. Verify the column, default constraint and version row, then test topic create/edit/reorder on a dedicated test database first.

Rollback requires a backup and a pause in catalog writes. The commented rollback statements at the end of V003 remove only its default constraint, column and version row. Export any new topic ordering first: removing the column loses that ordering. Existing catalog data and relationships remain. Use the older application compatibility path (position 0) after rollback. This task did not execute the migration or any SQL against a user database.

## V004 — question review
Run migrations/V004__question_reviews.sql manually on the selected local SQL Server database before using question review or publishing exams. Adds question_reviews keyed by reviewer and validated content hash; no existing data is deleted. Each question must be explicitly accepted before publication; editing content invalidates its prior review. Existing published exams remain readable. This migration has not been executed in this session.
