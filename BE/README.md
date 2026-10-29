# Math11 backend — NetBeans, Java 17, Tomcat 10.1

Backend đã tách khỏi frontend tại `D:\PRJ\ProjectPRJ\BE`. Dự án giữ cấu hình **NetBeans Web Application (Ant)** hiện có, đồng thời hỗ trợ Maven và JUnit 5.

## Mở và chạy trong NetBeans

1. Nếu Math11 đang mở, đóng project rồi dùng **File → Open Project** chọn `D:\PRJ\ProjectPRJ\BE` để nạp lại source roots và thư viện.
2. Trong **Properties → Libraries**, chọn **JDK 17**. Trong **Properties → Run**, chọn **Apache Tomcat 10.1**, context path **`/Math11`**. Tomcat hiện đăng ký trên máy dùng cổng **9999**. Dự án tắt Compile on Save và Deploy on Save để Ant biên dịch trước khi triển khai, tránh dùng class cũ từ cache NetBeans. Nếu project đang mở khi đổi cấu hình, đóng rồi mở lại project.
3. Kiểm tra kết nối SQL Server trong file **`D:\PRJ\ProjectPRJ\BE\.env`**. File cấu hình cũ được chuyển sang nguyên vẹn. Nếu chưa có database, mở `database/setup.sql` trong SSMS và Execute; tùy chọn chạy `database/seed-demo.sql` để tạo dữ liệu demo.
4. Nhấn **Clean and Build Project**, rồi **Run Project (F6)**. Ant tạo **`dist/Math11.war`**. Không cần khởi động Tomcat bằng script khi đã chạy bằng NetBeans.
5. Kiểm tra `http://localhost:9999/Math11/api/auth/csrf`. API hoạt động sẽ trả JSON có `csrfToken`. Đây là backend API; giao diện React chạy riêng ở frontend.

`src/main/webapp/META-INF/context.xml` giữ cookie SameSite=Lax. Cấu hình Tomcat/NetBeans JVM option `-DMATHIO_ENV_FILE=D:/PRJ/ProjectPRJ/BE/.env` hoặc biến môi trường MATHIO_ENV_FILE để đọc file cấu hình ngoài WAR. Đặt context `/Math11` trong cấu hình deploy. Listener đọc file trước khi khởi tạo database. Thứ tự ưu tiên: JVM system property → biến môi trường → `.env` → giá trị mặc định. Đường dẫn `STORAGE_DIR` tương đối trong `.env` được tính từ thư mục chứa file, nên `./private-storage` trở thành `D:\PRJ\ProjectPRJ\BE\private-storage`. Khi đổi cấu hình, Stop rồi Run lại project.

`.env`, database SQL và ảnh riêng tư không được đóng gói vào WAR. Các thư viện runtime đầy đủ ở `lib/`; không còn tham chiếu JDBC/JSTL cũ tại `D:\PRJ\sqljdbc42`.

## Kết nối frontend

Frontend vẫn ở `D:\PRJ\ProjectPRJ\FE`. Cấu hình Vite:

```dotenv
VITE_USE_MOCK_API=false
VITE_API_BASE_URL=/api
BACKEND_URL=http://localhost:9999
BACKEND_CONTEXT=/Math11
```

Nếu Tomcat dùng cổng khác, thay `BACKEND_URL`. Frontend mặc định mở ở `http://localhost:8443`; Vite proxy `/api` tới `/Math11/api` và rewrite cookie path. Khởi động lại Vite sau khi đổi env. `APP_ORIGIN` trong backend `.env` phải khớp origin frontend.

## Cấu trúc

```text
Math11/
  build.xml, nbproject/       Project NetBeans Ant, cấu hình Tomcat cũ
  src/main/java/com/mathio/lms/
    model/                   POJO, DTO, xử lý nghiệp vụ, AI và dữ liệu
    control/                 Servlet Controller, Filter và listener
    DAO/                     JDBC, SQL Server, transaction và DBContext
  src/main/webapp/            web.xml và cấu hình Tomcat
  src/test/java/             JUnit 5, Mockito và kiểm thử HTTP
  lib/                       10 JAR runtime dùng cho NetBeans Ant
  database/                  setup.sql, seed-demo.sql, seed/curriculum.json
  scripts/                   Build/verify và chạy Tomcat ngoài IDE
  pom.xml                    Maven build, cùng source với Ant
  .m2/                       Maven cache đã chuyển, hỗ trợ build offline
  .env                       Cấu hình riêng trên máy, không commit
  dist/Math11.war            Kết quả build NetBeans Ant
  target/Math11.war          Kết quả build Maven
```

## Kiểm thử

**Test Project** trong NetBeans gọi Maven Surefire để chạy JUnit 5. Hoặc dùng:

```powershell
Set-Location 'D:\PRJ\ProjectPRJ\BE'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\run.ps1 -Action verify
```

Build offline bằng Maven đi kèm NetBeans:

```powershell
& 'C:\Program Files\NetBeans-17\netbeans\java\maven\bin\mvn.cmd' -o -B '-Dmaven.repo.local=.m2' clean verify
```

SQL integration chỉ chạy khi đặt `RUN_SQL_INTEGRATION=true` và có database đã thiết lập. Build thành công không xác nhận SQL Server hoặc Gemini đã được cấu hình và kết nối thành công. API chi tiết xem `API.md`.

## Cập nhật backend 25/10/2026

Backend hiện hỗ trợ đề MCQ/ESSAY/hỗn hợp trong một attempt, lưu ảnh riêng, giáo viên chấm từng câu, công bố kết quả; giữ các endpoint MCQ cũ. `API.md` mô tả trường `essayAnswers`, `resultsPublished`, phân trang và endpoints chấm. Student chỉ đọc câu hỏi standalone đã publish trong course được ghi danh. Teacher xem bài làm/history theo exam/question/course, không theo quan hệ chung với học sinh.

**Trước khi dùng WAR mới với database cũ:** backup SQL + private storage, rồi tự chạy `database/migrations/V002__backend_reliability.sql` trong SSMS theo `database/README-MIGRATIONS.md`. Không có migration/seed tự động. Setup cho database mới đã gồm V002. Gán course và publish câu hỏi cũ sau khi duyệt; dữ liệu chưa gán không hiển thị cho Student.

Đánh giá/Tutor/retry dùng transaction ngắn → HTTP Gemini ngoài JDBC → transaction ngắn kiểm tra token/revision. Có lease bền vững, lịch sử tác vụ, idempotency và giới hạn 8 cuộc gọi AI/JVM. Bộ kiểm tra Toán dùng số hữu tỉ/đa thức chính xác; biểu thức ngoài phạm vi trả UNKNOWN, không tự đánh dấu sai hoặc mở bước tiếp theo.

Các kiểm thử SQL thật và Gemini thật là opt-in, tách khỏi fixture. Chạy kiểm thử tích hợp với cấu hình hiện tại để xác nhận các luồng dữ liệu; kiểm tra kết nối bằng `DBContext.main` chỉ xác nhận truy cập SQL Server.

Kiểm tra WAR sau khi package (không cần SQL/Gemini thật):

```powershell
$env:RUN_WAR_DEPLOYMENT='true'
& 'C:\Program Files\NetBeans-17\netbeans\java\maven\bin\mvn.cmd' -o -B '-Dmaven.repo.local=.m2' '-Dtest=WarDeploymentTest' test
Remove-Item Env:RUN_WAR_DEPLOYMENT
```

Smoke test deploy WAR thật lên embedded Tomcat 10.1 với cấu hình tạm, kiểm tra annotation mapping/listener, context `/Math11`, CSRF, cookie và chặn chưa đăng nhập. Nó không xác nhận truy vấn SQL hay chấm Gemini hoạt động.

Không còn mặc định tài khoản/mật khẩu SQL trong source. Cần điền credentials riêng; `.env.example` không chứa secrets. Xem `../INTEGRATION_REPORT.md` để biết trạng thái kiểm thử và các giới hạn frontend còn lại.
