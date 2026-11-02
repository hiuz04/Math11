# Math11 — chạy local với profile dev

Yêu cầu: JDK 17, Node.js 22.12+ hoặc 24+, Maven và Tomcat 10.1, SQL Server có database `math11_tutor_db` với schema V002. Script tự tìm Maven trong PATH, `MAVEN_HOME` hoặc NetBeans 17.

## Cấu hình lần đầu

Chạy từ thư mục gốc repository:

```powershell
Copy-Item BE/.env.dev.example BE/.env.dev
```

Điền `DB_USER`, `DB_PASSWORD` trong `BE/.env.dev`; sửa `DB_URL` nếu SQL Server dùng host, port hoặc database khác. File này được Git bỏ qua. Gemini là tùy chọn; điền `GEMINI_API_KEY` để dùng AI và `APP_ENCRYPTION_KEY` nếu lưu khóa qua trang quản trị.

Profile backend được chọn bằng `-Profile dev`. Khi chạy Tomcat, script đọc riêng `BE/.env.dev`, đặt `APP_ENV=development` và lưu ảnh trong `BE/private-storage/dev`. Build/test với profile dev không nạp cấu hình runtime vào các test fixture. Profile mặc định vẫn đọc `BE/.env`. Có thể chỉ định file khác bằng `-EnvFile <path>`.

Nếu chưa có database local, thiết lập theo `BE/database/setup.sql`; database cũ xem `BE/database/README-MIGRATIONS.md`. Các lệnh chạy ứng dụng không tự tạo, seed hay migrate database.

## Backend

Trong terminal PowerShell tại thư mục gốc:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
./BE/scripts/run.ps1 -Profile dev -Action package
./BE/scripts/run.ps1 -Profile dev -Action tomcat -TomcatHome 'C:\Program Files\Apache Software Foundation\Tomcat 10.1'
```

Có thể dùng `CATALINA_HOME` thay cho `-TomcatHome`. Script deploy `target/Math11.war` vào Tomcat được chọn và chạy foreground; dừng bằng Ctrl+C. Dừng Tomcat trong NetBeans trước nếu instance đó đang chạy. Tomcat trên máy hiện dùng cổng `9999`; nếu cổng khác, sửa proxy frontend tương ứng.

Kiểm tra API: `http://localhost:9999/Math11/api/auth/csrf` phải trả JSON.

## Frontend

Mở terminal thứ hai:

```powershell
Set-Location FE
npm ci
npm run dev:local
```

Truy cập **http://localhost:8443**. Lệnh dùng Vite mode `dev`, nạp `FE/.env.dev`, gọi API thật qua proxy `/api` → `http://localhost:9999/Math11/api` và giữ session cookie qua proxy.

Nếu cần đổi cấu hình riêng trên máy, tạo `FE/.env.dev.local` (Git bỏ qua), ví dụ:

```dotenv
BACKEND_URL=http://localhost:8080
PORT=8443
```

Biến môi trường trong terminal có độ ưu tiên cao hơn các file env. Sau khi sửa env, khởi động lại server. Nếu đổi cổng frontend, sửa `APP_ORIGIN` trong `BE/.env.dev` cho khớp và khởi động lại backend.

## Kiểm tra

```powershell
./BE/scripts/run.ps1 -Profile dev -Action verify
```

Thêm `-Offline` vào lệnh package/verify khi Maven cache đã có đủ dependency. SQL/Gemini integration vẫn tắt trong profile; chỉ bật kiểm thử SQL với database riêng có tên kết thúc `_test` theo tài liệu backend.
