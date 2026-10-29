# Math11: tích hợp Frontend–Backend và Vercel

## Kiến trúc và nguyên nhân đã xác minh

Browser gọi `/api/*` cùng domain frontend. Local dùng Vite proxy; production dùng Vercel rewrite đến Render. `ROOT.war` có context `/`, nhưng servlet vẫn nằm dưới `/api`. Local WAR `/Math11` được Vite bổ sung qua `BACKEND_CONTEXT`. Không đổi router, UI hoặc backend source trong bản sửa này.

Quan sát HTTP production ngày 29/10/2026, trước deploy bản sửa:

| Request | Kết quả thực tế | Nguyên nhân/kết luận |
| --- | --- | --- |
| Render GET `/auth/csrf` | 404, text/html | Thiếu `/api`, không khớp mapping `/api/auth/*`. |
| Render GET `/api/auth/csrf`, không có Origin | 200 JSON có csrfToken, Cache-Control: no-store | Endpoint đúng ở context gốc. |
| Render GET `/api/auth/me`, chưa đăng nhập | 401 JSON | Session check đúng, endpoint không bị mất. |
| Vercel GET `/api/auth/csrf`, `/api/auth/me` | 200 text/html, X-Vercel-Cache: HIT; SHA-256 của body giống hệt `/` | `vercel.json` thiếu tuyến API trước SPA fallback, API nhận nhầm index.html. Không suy luận lỗi chỉ từ status 304. |
| Render GET CSRF, POST login/logout và OPTIONS, Origin `https://math11-khaki.vercel.app` | 403 JSON: Origin không được phép; không có CORS allow headers | Origin production chưa được phép trong cấu hình đang chạy. |
| Render OPTIONS, Origin `http://localhost:8443` | 204, allow-origin khớp, credentials=true | Code CORS đang hoạt động; cấu hình vẫn cho phép local. |
| Cookie CSRF từ Render | Path=/; HttpOnly; SameSite=Lax; không Domain hoặc Secure | Host-only, Path phù hợp proxy. Cần bật cấu hình production để Secure được đặt. |
| Vercel `/student/dashboard` | 200 HTML SPA | Không gặp HTTP 404 khi refresh đường dẫn này. App hiện dùng hash routing. |

Không đọc được giá trị environment đã lưu trong dashboard từ repository; kết luận cấu hình trên dựa trên HTTP response thực tế.

## File đã sửa

- `vercel.json`: rewrite `/api/:path*` → `https://math11.onrender.com/api/:path*` trước SPA fallback; Cache-Control=no-store và tắt rewrite caching cho API. Static files vẫn theo cơ chế filesystem của Vercel.
- `src/services/api.ts`: một base URL chung, mặc định `/api`; hỗ trợ tên biến cũ và WAR context, bổ sung hậu tố thiếu và loại `/api` lặp. Giữ cookie/CSRF/payload; request API không dùng HTTP cache. Kiểm tra JSON media type, từ chối HTML/malformed JSON; không parse body cho 204/205/HEAD.
- `.env.example`: dùng `/api` trên cả local và Vercel.
- `tests/services.test.ts`: regression tests cho base URL, rewrite/cache, CSRF bootstrap, response HTML/non-JSON và response rỗng.
- `README.md`: báo cáo này và hướng dẫn deploy/kiểm tra.

Không sửa component, CSS, nội dung hiển thị, navigation, business contract, backend source hoặc database schema.

## Rà soát API

Chỉ `src/services/api.ts` gọi fetch; không có Axios/client HTTP thứ hai trong src. Các service gọi chung qua apiRequest/postApi/apiOperation/submitContent:

| Nhóm | Endpoint dưới `/api` |
| --- | --- |
| Auth/profile | auth/csrf, auth/login, auth/me, auth/logout, auth/change-password, profile |
| Student/Teacher/Admin | users, students/*, admin/config/*, admin/audit-logs |
| Course/lesson | courses/*, chapters, topics, lessons/* |
| Question/exercise | questions/*, exercises |
| Exam/bài làm | exams/*, quiz-attempts/*, essays/*, learning-history |
| Upload/ảnh | Multipart qua submitContent; backend trả ảnh bằng /api/files/* hoặc /api/exam-files/*, cũng cần proxy |
| AI | ai/chat, ai/conversations/*, ai/questions/generate, ai/exams/generate, ai/essays/*, ai/tutor/* |
| Dữ liệu chung | dashboard, analytics, search, notifications/* |

Không tạo endpoint assignments hoặc mock production. Chức năng dùng exam/attempt tiếp tục gọi endpoint hiện có; không thay request/response contract.

## Environment và thao tác dashboard

| Nơi | Biến | Giá trị |
| --- | --- | --- |
| Vercel | VITE_API_URL | /api |
| Vercel | VITE_USE_MOCK_API | false |
| Vercel | VITE_API_BASE_URL | Xóa giá trị cũ hoặc đặt /api; chỉ dùng nếu VITE_API_URL chưa đặt |
| Render | APP_ENV | production |
| Render | APP_ORIGIN | https://math11-khaki.vercel.app, không có path/dấu slash cuối |
| Render | DB_URL, DB_USER, DB_PASSWORD | Giữ credentials SQL Server hợp lệ, chỉ lưu backend |
| Render | Secret Gemini/encryption hiện có | Chỉ lưu backend, không thêm vào VITE_* |

1. Vercel: Root Directory=FE, Framework=Vite, Build Command=npm run build, Output Directory=dist. Cập nhật biến đúng môi trường Production; cấu hình Preview riêng nếu sử dụng.
2. Render: sửa APP_ORIGIN và APP_ENV như bảng. Nhiều origin tin cậy được phân cách bằng dấu phẩy; không dùng wildcard hoặc tự chấp nhận mọi subdomain Vercel.
3. Deploy commit có rewrite mới, Redeploy Vercel sau khi đổi biến vì Vite nhúng biến lúc build. Render cần redeploy/restart sau khi đổi environment. Kiểm tra domain production trỏ đúng deployment.
4. Không coi code local đã sửa là dashboard đã được cập nhật hoặc production đã được kiểm thử thành công.

Backend hiện có session/role/Origin/CSRF/CORS checks. Khi APP_ENV=production, cookie session dùng HttpOnly; Secure; SameSite=None. Qua proxy cùng origin cấu hình này vẫn dùng được. Không đặt Domain=onrender.com cho cookie proxied; Path của ROOT.war phải là `/`. Browser cần lưu cookie host-only trên math11-khaki.vercel.app và gửi lại cho /api/*. Việc chuyển tiếp Cookie, Set-Cookie, X-CSRF-Token, Origin và Idempotency-Key qua Vercel phải kiểm tra thực tế sau deploy. Không đưa session hoặc CSRF vào localStorage.

## Kết quả kiểm thử thực tế

- npm install --offline --ignore-scripts --no-audit --no-fund: thành công, dependency có sẵn; không đổi dependency version hoặc nội dung lockfile.
- npm run typecheck: qua.
- npm run test:services: 16/16 qua.
- npm run test:e2e:api, VITE_API_URL=/api và mock mode=false: 22/22 qua. Bao gồm login/CSRF, restore khi reload, logout, Student/Teacher/Admin, role bypass, session expiry, multipart upload, quiz, course/lesson, search, notifications.
- npm run build, VITE_API_URL=/api và mock mode=false: thành công. Còn warning có sẵn về __dirname của Vite config và JS chunk trên 500 kB.
- git diff --check: qua. Không có script lint; script format là formatter có ghi file, không dùng để sửa cả dự án.

Browser tests dùng transport fixtures, không xác nhận SQL Server/Gemini hoặc đăng nhập production thật. Production probes là HTTP thật, không theo redirect hoặc dùng conditional request. Chưa xác minh successful login/logout với tài khoản production, cookie xuyên Vercel sau deploy hoặc việc environment đã được lưu/redeploy trong dashboard. Không đưa credentials production vào test/frontend.

## Chrome DevTools → Network: kiểm tra sau deploy

1. Bật Preserve log và Disable cache khi chẩn đoán. GET /api/auth/me chưa đăng nhập phải là 401 JSON. Đọc Content-Type và Response; riêng Server=Vercel hoặc 304 không chứng minh sai tuyến.
2. Dùng tài khoản thật đã được cấp để đăng nhập. GET /api/auth/csrf phải là 200 JSON có csrfToken, không redirect. Kiểm tra Set-Cookie và Application → Cookies: host frontend, Path=/, HttpOnly, Secure. Không đưa giá trị cookie/token vào log công khai.
3. POST /api/auth/login phải đi đến cùng origin Vercel, có cookie và X-CSRF-Token. Thành công trả user và token mới, đổi session ID. Request tiếp theo dùng token mới. Nếu 403 Origin không được phép, kiểm tra Render APP_ORIGIN.
4. Reload dashboard theo role: auth/me phải là 200 JSON đúng user và session còn hiệu lực. Kiểm tra course/lesson/question/exercise/AI/dashboard đều dùng /api/..., không thiếu hoặc lặp /api.
5. Upload ảnh giữ multipart boundary do browser tạo, cookie, CSRF và Idempotency-Key. GET /api/files/* hoặc /api/exam-files/* trả image media type, không trả SPA.
6. Logout trả 204 không body; auth/me sau logout và sau reload phải là 401 JSON.
7. API có Cache-Control=no-store, không nhận HTML hoặc cache HIT của index.html. Static JS/CSS/font trả đúng loại. Các hash routes /#/dashboard, /#/courses refresh bình thường. /login, /student/dashboard, /teacher/courses phục vụ shell SPA, không yêu cầu đổi router hiện tại.

Tham khảo: [Vercel external rewrites và tắt rewrite cache](https://vercel.com/docs/routing/rewrites), [Vercel Cache-Control](https://vercel.com/docs/caching/cache-control-headers).
