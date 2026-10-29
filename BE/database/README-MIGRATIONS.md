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

Tạo `.env.test` riêng, copy các key từ `.env.example`, đặt DB_NAME=math11_tutor_db_test và credentials kiểm thử hợp lệ. Nếu dùng DB_URL, databaseName trong URL phải trỏ đúng `_test`. Đặt RUN_SQL_INTEGRATION=true; giữ RUN_GEMINI_INTEGRATION=false. Chạy:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\run.ps1 -Action verify -EnvFile .env.test
```

Ba test SQL thực thi DAO/service thật. Test gốc và test mixed/security rollback toàn bộ dữ liệu. Test AI cần commit các phase để kiểm chứng lease/idempotency; nó chỉ tạo tài nguyên UUID trong `_test` và dọn đúng các ID đó trong finally. Gemini trong test SQL AI là fixture được khai báo rõ; ảnh là file thật trong thư mục tạm. Nếu tiến trình bị kill, dữ liệu UUID kiểm thử có thể còn lại: chỉ dọn sau khi xác minh đúng database/ID kiểm thử.

## Rollback

Ưu tiên sửa tiến tới với schema V002 được giữ nguyên. Không có script DROP cột/bảng tự động vì sẽ làm mất bài tự luận, điểm và lịch sử mới. Nếu phải quay lại WAR cũ, trước hết phải xử lý mọi PENDING_GRADING/GRADED và AI reservation; WAR cũ không hiểu đề mixed. Nếu cần restore backup, dừng ứng dụng, lưu/export mọi phát sinh sau backup và private storage, thử restore sang database mới trước, đối chiếu rồi mới quyết định chuyển connection. Restore ghi đè database chính là thao tác riêng do DBA xác nhận; không được tự chạy trong pipeline này.

## Giới hạn kiểm chứng

ScriptDom parse được cú pháp không chứng minh quyền ALTER, dữ liệu cũ, lock/race, query runtime hay rollback trên SQL Server. Chỉ đánh dấu SQL integration PASS khi các test thực sự kết nối `_test` đã chuẩn bị và chạy thành công.
