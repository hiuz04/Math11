# Bộ dữ liệu kiểm thử mở rộng

Đã thêm trực tiếp vào `math11_tutor_db` ngày 28/10/2026. Nội dung mới có tiền tố `[TEST]`; tài khoản mới dùng miền `mathio.test`.

## Tài khoản đăng nhập

Mật khẩu chung cho **24 tài khoản mới**: `TestMathio2026!`.
Mật khẩu các tài khoản demo có sẵn không thay đổi.

| Tài khoản | Vai trò / tình huống |
|---|---|
| `qa.admin@mathio.test` | Quản trị viên, quản lý toàn bộ dữ liệu |
| `qa.teacher01@mathio.test` | Giáo viên lớp 11A1 và 11A2 |
| `qa.teacher02@mathio.test` | Giáo viên lớp 11B1 và 11B2 |
| `qa.teacher03@mathio.test` | Giáo viên lớp 11C1 và lớp nháp 11C2 |
| `qa.student01@mathio.test` đến `qa.student06@mathio.test` | Học sinh 11A1, 11A2 và lớp Demo mở rộng |
| `qa.student07@mathio.test` đến `qa.student12@mathio.test` | Học sinh 11B1 và 11B2 |
| `qa.student13@mathio.test` đến `qa.student18@mathio.test` | Học sinh 11C1, có đăng ký 11C2 nhưng khóa học này chưa xuất bản |
| `qa.student19@mathio.test` | Chưa đăng ký khóa học: danh sách khóa học/câu hỏi/đề thi trống |
| `qa.student20@mathio.test` | Tài khoản bị khóa: đăng nhập phải bị từ chối |

`teacher@demo.vn` được giao lớp `[TEST] Toán 11 - Lớp 11 Demo mở rộng`.
`student@demo.vn` được đăng ký cả 6 khóa học mới đã xuất bản, có tiến độ, bài thi và bài tự luận mẫu.

## Dữ liệu đã thêm

| Loại | Số lượng |
|---|---:|
| Tài khoản | 24: 1 admin, 3 giáo viên, 20 học sinh |
| Khóa học | 7: 6 xuất bản, 1 nháp |
| Bài giảng | 70: 56 xuất bản, 14 nháp |
| Câu hỏi | 168: 126 trắc nghiệm, 42 tự luận; 140 xuất bản, 28 nháp |
| Đề thi | 21: 7 trắc nghiệm, 7 kết hợp tự luận, 7 nháp |
| Lượt làm bài thi | 84 |
| Bài đang làm | 18 |
| Bài trắc nghiệm đã nộp | 30 |
| Bài thi đang chờ giáo viên chấm | 18 |
| Bài thi kết hợp đã chấm | 18 |
| Bài nộp tự luận độc lập | 42: 18 đã nộp/chưa chấm, 24 hoàn thành |
| Tiến độ bài giảng | 210: 138 hoàn thành, 72 chưa hoàn thành |

Mỗi khóa học có 10 bài giảng, 24 câu hỏi và 3 đề thi. Có cả đề đã công bố kết quả và đề chưa công bố kết quả. Bài thi đã nộp có thời điểm trong 1–5 ngày trước để thử lịch sử và thống kê. Bài đang làm có thời hạn 60 phút tính từ lúc tạo dữ liệu; sau thời hạn sẽ dùng để thử tình huống hết giờ.

## Các luồng có thể thử

1. Đăng nhập học sinh 01, xem khóa học, bài giảng, tiến độ, luyện tập và đề thi; làm một lượt thi mới hoặc nộp tự luận mới.
2. Đăng nhập giáo viên 01, xem bài tự luận và bài thi chờ chấm của lớp A; thử chấm, xem lịch sử điểm và công bố kết quả.
3. So sánh học sinh 01 và 07 để kiểm tra danh sách lớp và nội dung tách biệt. Giáo viên 01 không được truy cập lớp B.
4. Dùng tài khoản 19 để thử màn hình chưa có dữ liệu; tài khoản 20 để thử đăng nhập khi bị khóa.
5. Dùng admin để quản lý tài khoản, xem nội dung nháp, bật/tắt xuất bản và thay đổi phân công trên các bản ghi `[TEST]`.
6. Câu hỏi tự luận và bài nộp đã sẵn sàng để thử chức năng AI khi có cấu hình Gemini hợp lệ. Không tạo kết quả AI giả và không gọi dịch vụ AI trong quá trình seed.

## Kiểm tra đã thực hiện

- Đăng nhập thành công cả 23 tài khoản mới đang hoạt động; tài khoản bị khóa nhận lỗi 401.
- Kiểm tra phân quyền khác lớp, giáo viên khác lớp, khóa học/đề nháp, học sinh chưa đăng ký và ẩn đáp án của học sinh.
- Đọc danh sách/chi tiết bài thi và bài nộp bằng service thật cho học sinh, giáo viên và admin.
- Chạy `validate-schema.sql`: không có lỗi schema, FK, constraint, ma trận, snapshot hoặc lựa chọn trắc nghiệm. Cảnh báo cũ vẫn còn: đề thi ID 1 chưa được gán khóa học; bộ seed không sửa đề cũ này.

## Chạy lại và sao lưu

Script: `scripts/SeedTestData.java`. Chạy từ thư mục `BE`:

```powershell
& 'C:\Program Files\Java\jdk-17\bin\java.exe' -cp 'build/web/WEB-INF/classes;lib/*' scripts/SeedTestData.java --apply
```

Script kiểm tra đúng DB, tạo backup COPY_ONLY/CHECKSUM và chạy RESTORE VERIFYONLY trước khi ghi. Tất cả thao tác thêm dữ liệu nằm trong một transaction. Marker `QA_SEED_20261028_V1` trong `audit_logs` ngăn chạy lặp tạo dữ liệu trùng.

Danh sách ID/tài khoản/khóa học và đường dẫn backup chính xác nằm trong `test-data-manifest.json` cùng thư mục.

Bản backup ngay trước lần seed thành công: `backups/math11_pre_test_seed_9169d11f-f815-418e-881c-acec1b4eb92a.bak`.
