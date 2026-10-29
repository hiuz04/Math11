# Math11 backend — NetBeans, Java 17, Tomcat 10.1

Backend đã tách khỏi frontend tại `D:\PRJ\Math11`. Dự án giữ cấu hình **NetBeans Web Application (Ant)** hiện có, đồng thời hỗ trợ Maven và JUnit 5.

## Mở và chạy trong NetBeans

1. Nếu Math11 đang mở, đóng project rồi dùng **File → Open Project** chọn `D:\PRJ\Math11` để nạp lại source roots và thư viện.
2. Trong **Properties → Libraries**, chọn **JDK 17**. Trong **Properties → Run**, chọn **Apache Tomcat 10.1**, context path **`/Math11`**. Tomcat hiện đăng ký trên máy dùng cổng **9999**. Dự án tắt Compile on Save và Deploy on Save để Ant biên dịch trước khi triển khai, tránh dùng class cũ từ cache NetBeans. Nếu project đang mở khi đổi cấu hình, đóng rồi mở lại project.
3. Kiểm tra kết nối SQL Server trong file **`D:\PRJ\Math11\.env`**. File cấu hình cũ được chuyển sang nguyên vẹn. Nếu chưa có database, mở `database/setup.sql` trong SSMS và Execute; tùy chọn chạy `database/seed-demo.sql` để tạo dữ liệu demo.
4. Nhấn **Clean and Build Project**, rồi **Run Project (F6)**. Ant tạo **`dist/Math11.war`**. Không cần khởi động Tomcat bằng script khi đã chạy bằng NetBeans.
5. Kiểm tra `http://localhost:9999/Math11/api/auth/csrf`. API hoạt động sẽ trả JSON có `csrfToken`. Đây là backend API; giao diện React chạy riêng ở frontend.

`src/main/webapp/META-INF/context.xml` khai báo context `/Math11` và file cấu hình ngoài WAR `D:/PRJ/Math11/.env`. Listener đọc file trước khi khởi tạo database. Thứ tự ưu tiên: JVM system property → biến môi trường → `.env` → giá trị mặc định. Đường dẫn `STORAGE_DIR` tương đối trong `.env` được tính từ thư mục chứa file, nên `./private-storage` trở thành `D:\PRJ\Math11\private-storage`. Khi đổi cấu hình, Stop rồi Run lại project.

`.env`, database SQL và ảnh riêng tư không được đóng gói vào WAR. Các thư viện runtime đầy đủ ở `lib/`; không còn tham chiếu JDBC/JSTL cũ tại `D:\PRJ\sqljdbc42`.

## Kết nối frontend

Frontend vẫn ở `D:\PRJ\AI-Powered Math LMS UI Design`. Cấu hình Vite:

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
Set-Location 'D:\PRJ\Math11'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\run.ps1 -Action verify
```

Build offline bằng Maven đi kèm NetBeans:

```powershell
& 'C:\Program Files\NetBeans-17\netbeans\java\maven\bin\mvn.cmd' -o -B '-Dmaven.repo.local=.m2' clean verify
```

SQL integration chỉ chạy khi đặt `RUN_SQL_INTEGRATION=true` và có database đã thiết lập. Build thành công không xác nhận SQL Server hoặc Gemini đã được cấu hình và kết nối thành công. API chi tiết xem `API.md`.

## Nâng cấp backend 25/10/2026

Với database đã tồn tại, trước khi chạy WAR mới cần backup và tự áp dụng `database/migrations/V002__backend_reliability.sql` theo `database/README-MIGRATIONS.md`. Không có migration tự động. Các thay đổi mixed exam, AI lease và phân quyền course được mô tả trong `README.md` và `API.md`. Cách chạy kiểm thử được hướng dẫn ở phần Kiểm thử phía trên.
