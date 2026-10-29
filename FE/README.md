# Frontend Math11

## Chạy local

Copy `.env.example` thành `.env.local`, giữ `VITE_API_URL=/api` và `VITE_USE_MOCK_API=false`, rồi chạy `npm install` và `npm run dev`. Vite proxy `/api` đến `BACKEND_URL` với context `BACKEND_CONTEXT` (mặc định `/Math11`).

## Deploy Vercel

- Root Directory: `FE`.
- Framework Preset: Vite.
- Build Command: `npm run build`; Output Directory: `dist`.
- Đặt `VITE_USE_MOCK_API=false` và `VITE_API_URL=https://math11.onrender.com/api` trong Environment Variables. Cấu hình riêng cho Preview nếu sử dụng môi trường đó.
- Redeploy sau khi đổi biến môi trường vì Vite nhúng giá trị vào JavaScript lúc build.

Client đọc `VITE_API_URL` trước, sau đó mới dùng tên cũ `VITE_API_BASE_URL`; mặc định local là `/api`. URL cấu hình phải chứa đầy đủ context API: Render chạy `ROOT.war` dùng `/api`, WAR chạy dưới `/Math11` dùng `/Math11/api`. Client không tự thêm `/api`. Tất cả request dùng `credentials: "include"`; mutation gửi CSRF token cùng Idempotency-Key.

`vercel.json` chuyển các đường dẫn SPA về `index.html` để refresh không bị 404. Ứng dụng hiện điều hướng bằng hash (`#/dashboard`, `#/courses`); file rewrite cũng chuẩn bị cho các đường dẫn SPA khi cần.

## Backend tương ứng

Frontend chính thức: `https://math11-khaki.vercel.app`. Trên Render, đặt `APP_ENV=production`, `APP_ORIGIN=https://math11-khaki.vercel.app` (không thêm path hoặc dấu `/` cuối), cùng `DB_URL`, `DB_USER`, `DB_PASSWORD`. Nhiều origin được phân cách bằng dấu phẩy; chỉ khai báo các domain tin cậy, không điền `*`. Backend phải deploy bản code CORS mới này.

Cookie session production dùng `HttpOnly; Secure; SameSite=None` cho request Vercel → Render. Trình duyệt chặn cookie bên thứ ba vẫn có thể ngăn đăng nhập giữa hai domain; khi đó cần frontend/backend chung site hoặc proxy API cùng origin.

Kiểm tra backend bằng `GET https://math11.onrender.com/api/auth/csrf`; endpoint này phải trả JSON có `csrfToken`. Với header `Origin` là domain Vercel thực tế, response phải có `Access-Control-Allow-Origin` trùng domain đó và `Access-Control-Allow-Credentials: true`. Preflight OPTIONS hợp lệ trả 204 trước kiểm tra session.

## Kiểm tra trước khi push

```sh
npm run typecheck
npm run test:services
npm run build
```
