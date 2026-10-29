import { test, expect, type Page } from "@playwright/test";
const user = { id: 17, name: "Học sinh từ database", email: "real@example.vn", role: "STUDENT" };
const statistics = [{ icon: "check", value: "0", label: "Bài học hoàn thành", note: "Dữ liệu đã lưu", tone: "green" }];
const analytics = { name: user.name, completedLessons: 0, completedExercises: 0, averageScore: 0, elapsedHours: 0, chapters: [], activities: [], scores: [] };
// Transport contract fixtures only; production services still make HTTP requests.
async function fixture(page: Page, loggedIn = true, failDashboard = false) {
  let authenticated = loggedIn;
  await page.route("**/api/**", async route => {
    const request = route.request(), path = new URL(request.url()).pathname;
    const json = (value: unknown, status = 200) => route.fulfill({ status, contentType: "application/json", body: JSON.stringify(value) });
    if (path === "/api/auth/csrf") return json({ csrfToken: "pre-login-csrf" });
    if (path === "/api/auth/me") return authenticated ? json({ user, csrfToken: "session-csrf" }) : json({ error: { message: "Vui lòng đăng nhập." } }, 401);
    if (path === "/api/auth/login") { expect(request.headers()["x-csrf-token"]).toBe("pre-login-csrf"); expect(request.postDataJSON()).toEqual({ email: "real@example.vn", password: "RealPassword123!" }); authenticated = true; return json({ user, csrfToken: "session-csrf" }); }
    if (path === "/api/auth/logout") { expect(request.headers()["x-csrf-token"]).toBe("session-csrf"); authenticated = false; return route.fulfill({ status: 204 }); }
    if (path === "/api/dashboard") return failDashboard ? json({ error: { message: "Database unavailable for test" } }, 503) : json(statistics);
    if (path === "/api/courses") return json([{id:8,title:"Khóa SQL",teacherId:2,description:"",published:true,createdAt:new Date().toISOString()}]);
    if (path === "/api/analytics") return json(analytics);
    if (path === "/api/notifications" || path === "/api/ai/conversations") return json([]);
    if (path === "/api/ai/chat") return json({ error: { message: "Gemini đang tắt." } }, 503);
    return json([]);
  });
}
test("API login sends credentials and CSRF, restores session, and logs out on the server", async ({ page }) => {
  await fixture(page, false); await page.goto("/");
  await page.getByPlaceholder("Nhập địa chỉ email").fill("real@example.vn");
  await page.getByPlaceholder("Nhập mật khẩu").fill("RealPassword123!");
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page.locator(".profile-name strong")).toHaveText(user.name);
  await page.reload(); await expect(page.locator(".profile-name strong")).toHaveText(user.name);
  expect(await page.evaluate(() => JSON.stringify(localStorage))).not.toContain("RealPassword123!");
  await page.locator(".sidebar").getByRole("button", { name: "Đăng xuất" }).click();
  await page.getByRole("button", { name: "Đăng xuất", exact: true }).last().click();
  await expect(page.getByRole("heading", { name: "Chào mừng trở lại" })).toBeVisible();
});
test("API dashboard failure is visible and never displays demo scores or statistics", async ({ page }) => {
  await fixture(page, true, true); await page.goto("/");
  await expect(page.getByRole("alert")).toHaveText("Database unavailable for test");
  await expect(page.locator(".stat-card")).toHaveCount(0);
  await expect(page.locator("tbody tr")).toHaveCount(0);
  await expect(page.getByText("8 giờ 25 phút", { exact: true })).toHaveCount(0);
});
test("API AI chat reports disabled Gemini and never renders the sample solved conversation", async ({ page }) => {
  await fixture(page); await page.goto("/"); await page.locator(".sidebar").getByRole("button", { name: /^AI Tutor(?: AI)?$/ }).click();
  await expect(page.getByText("Giải phương trình sin 2x", { exact: true })).toHaveCount(0);
  await page.getByPlaceholder("Nhập câu hỏi Toán của bạn...").fill("Tôi cần gợi ý cấp số cộng");
  await page.locator(".send-btn").click(); await expect(page.getByRole("alert")).toHaveText("Gemini đang tắt.");
  await expect(page.locator(".question-box strong")).toHaveCount(0);
});
test("API quiz saves real answers, hides grading before submit and renders server result", async ({ page }) => {
  await fixture(page);
  const question = { id: 2, title: "Câu hỏi từ SQL", content: "Tính 1 + 1", type: "MCQ", options: ["1", "2"], difficulty: "EASY", cognitiveLevel: "KNOWLEDGE", chapter: "Toán 11", topic: "Ôn tập" };
  const attempt = { id: 51, examId: 8, exam: { title: "Đề từ SQL", durationSeconds: 1800 }, status: "IN_PROGRESS", startedAt: new Date().toISOString(), elapsedSeconds: 0, questions: [question], answers: {} as Record<string,string>, result: undefined as unknown };
  await page.route("**/api/exams**", async route => { const path = new URL(route.request().url()).pathname; await route.fulfill({ contentType: "application/json", body: JSON.stringify(path.endsWith("/attempts") ? attempt : [{ id: 8, questions: [question] }]) }); });
  await page.route("**/api/quiz-attempts/**", async route => { const request = route.request(); if (request.method() === "POST") { expect(request.headers()["x-csrf-token"]).toBe("session-csrf"); expect(request.headers()["idempotency-key"]).toBeTruthy(); expect(request.postDataJSON()).not.toHaveProperty("score"); attempt.answers = request.postDataJSON().answers; if (request.url().endsWith("/submit")) { attempt.status = "SUBMITTED"; attempt.result = { score: 10, correctCount: 1, totalCount: 1, review: [{ ...question, answer: "2", studentAnswer: "2", correct: true }] }; } } await route.fulfill({ contentType: "application/json", body: JSON.stringify(attempt) }); });
  await page.goto("/"); await page.locator(".sidebar").getByRole("button", { name: "Kiểm tra", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Câu hỏi từ SQL" })).toBeVisible();
  await page.locator(".options button").filter({ hasText: "B2" }).click();
  await expect(page.locator(".answer-feedback")).toHaveCount(0);
  await page.getByRole("button", { name: "Nộp bài", exact: true }).first().click();
  await page.getByRole("button", { name: "Nộp bài", exact: true }).last().click();
  await expect(page.locator(".result-score strong")).toHaveText("10");
  await expect(page.locator(".review-grid button")).toHaveCount(1);
});

test("Teacher uses SQL curriculum and persists a new essay question through the API", async ({ page }) => {
  await fixture(page);
  await page.route("**/api/auth/me", r=>r.fulfill({json:{user:{...user,name:"Giáo viên SQL",role:"TEACHER"},csrfToken:"session-csrf"}}));
  await page.route("**/api/chapters", r=>r.fulfill({json:[{id:88,title:"Chương từ SQL Server",position:1,lessonCount:0,exerciseCount:0,progress:0}]}));
  await page.route("**/api/topics", r=>r.fulfill({json:[{id:89,chapterId:88,title:"Chủ đề từ SQL Server"}]}));
  let questions: unknown[]=[]; let saved=false;
  await page.route("**/api/questions**", async r=>{
    if(r.request().method()==="POST") { const body=r.request().postDataJSON();expect(body.questions[0].id).toBeLessThan(0);expect(body.questions[0]).toMatchObject({chapter:"Chương từ SQL Server",topic:"Chủ đề từ SQL Server",type:"ESSAY",answer:"2",courseId:8});expect(r.request().headers()["x-csrf-token"]).toBe("session-csrf");questions=body.questions.map((q:object)=>({...q,id:900}));saved=true; }
    await r.fulfill({json:questions});
  });
  await page.goto("/");await page.locator(".sidebar nav").getByRole("button",{name:"Ngân hàng câu hỏi",exact:true}).click();
  await page.getByRole("button",{name:"Thêm mới",exact:true}).click();
  const form=page.getByRole("dialog",{name:"Tạo câu hỏi"});
  await form.getByLabel("Tên câu hỏi",{exact:true}).fill("Câu hỏi SQL mới");
  await expect(form.getByLabel(/^Chương/)).toHaveValue("8:0");
  await form.getByLabel(/^Loại câu hỏi/).selectOption("ESSAY");
  await form.getByRole("textbox",{name:"Nội dung văn bản"}).fill("Tính 1 + 1");
  await form.getByLabel("Đáp án / hướng dẫn chấm",{exact:true}).fill("2");
  await form.getByRole("button",{name:"Lưu câu hỏi",exact:true}).click();
  await expect(page.locator("tbody tr").filter({hasText:"Câu hỏi SQL mới"})).toHaveCount(1);expect(saved).toBeTruthy();
});

test("Admin creates, filters, locks and deletes users through the API", async ({ page }) => {
  await fixture(page);
  const administrator={...user,name:"Quản trị SQL",role:"ADMIN",active:true};
  await page.route("**/api/auth/me",r=>r.fulfill({json:{user:administrator,csrfToken:"session-csrf"}}));
  let users: Record<string,unknown>[]=[administrator];let created=false,locked=false,deleted=false;
  await page.route("**/api/users**",async r=>{
    const request=r.request(),path=new URL(request.url()).pathname;
    if(request.method()==="POST"){
      const body=request.postDataJSON();expect(request.headers()["x-csrf-token"]).toBe("session-csrf");
      if(path==="/api/users"){expect(body).toMatchObject({name:"Giáo viên vừa tạo",email:"teacher-new@example.vn",role:"TEACHER",password:"NewPassword123!"});const next={...body,id:91,active:true};delete next.password;users.push(next);created=true;return r.fulfill({status:201,json:next});}
      expect(path).toBe("/api/users/91");expect(body.active).toBe(false);users=users.map(u=>u.id===91?{...u,...body}:u);locked=true;return r.fulfill({json:users.find(u=>u.id===91)});
    }
    if(request.method()==="DELETE"){expect(path).toBe("/api/users/91");users=users.filter(u=>u.id!==91);deleted=true;return r.fulfill({status:204});}
    await r.fulfill({json:users});
  });
  await page.goto("/");await page.locator(".sidebar nav").getByRole("button",{name:"Người dùng",exact:true}).click();
  await page.getByRole("button",{name:"Thêm tài khoản",exact:true}).click();const form=page.locator(".form-modal");
  await form.getByLabel("Họ và tên",{exact:true}).fill("Giáo viên vừa tạo");await form.getByLabel("Email",{exact:true}).fill("teacher-new@example.vn");await form.getByLabel("Mật khẩu",{exact:true}).fill("NewPassword123!");await form.getByLabel(/^Vai trò/).selectOption("TEACHER");
  await form.getByRole("button",{name:"Tạo tài khoản",exact:true}).click();
  await page.getByPlaceholder("Tìm theo tên hoặc email...").fill("teacher-new@example.vn");
  const row=page.locator("tbody tr").filter({hasText:"teacher-new@example.vn"});await expect(row).toHaveCount(1);expect(created).toBeTruthy();
  await row.getByRole("button",{name:"Khóa",exact:true}).click();await page.getByRole("button",{name:"Xác nhận",exact:true}).click();await expect(row.getByText("Đã khóa",{exact:true})).toBeVisible();expect(locked).toBeTruthy();
  await row.getByRole("button",{name:"Xóa",exact:true}).click();await page.getByRole("button",{name:"Xác nhận",exact:true}).click();await expect(row).toHaveCount(0);expect(deleted).toBeTruthy();
});

test("student URL role bypass is blocked and forbidden responses preserve the session", async ({ page }) => {
  await fixture(page); let usersRead = false;
  await page.route("**/api/users**", r => { usersRead = true; return r.fulfill({ json: [] }); });
  for (const path of ["users", "teacher", "admin", "create-lesson", "student-detail", "system"]) {
    await page.goto(`/#/${path}`);
    await expect(page.getByRole("alert")).toHaveText("Bạn không có quyền truy cập trang này.");
    await expect(page.locator(".profile-name strong")).toHaveText(user.name);
  }
  expect(usersRead).toBe(false);
  await page.route("**/api/dashboard*", r => r.fulfill({ status: 403, json: { error: { message: "Không có quyền đọc thống kê." } } }));
  await page.goto("/#/dashboard");
  await expect(page.getByRole("alert")).toHaveText("Không có quyền đọc thống kê.");
  await expect(page.locator(".profile-name strong")).toHaveText(user.name);
});

test("session expiry during a resource request returns to login", async ({ page }) => {
  await fixture(page);
  await page.route("**/api/dashboard*", r => r.fulfill({ status: 401, json: { error: { message: "Session expired" } } }));
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Chào mừng trở lại" })).toBeVisible();
});

const sqlQuestion = { id: 2, title: "Câu khôi phục", content: "1+1", type: "MCQ", options: ["1", "2"], difficulty: "EASY", cognitiveLevel: "KNOWLEDGE", chapter: "Toán", topic: "Ôn tập" };
test("quiz refresh restores the same saved attempt and server deadline without POST start", async ({ page }) => {
  await fixture(page); let creations = 0;
  const start = Date.now() - 60_000;
  await page.route("**/api/exams/**", r => { creations++; return r.fulfill({ status: 500 }); });
  await page.route("**/api/quiz-attempts/51", r => r.fulfill({ json: { id: 51, examId: 8, status: "IN_PROGRESS", startedAt: new Date(start).toISOString(), deadlineAt: new Date(start + 1_800_000).toISOString(), serverTime: new Date().toISOString(), elapsedSeconds: 60, exam: { title: "Đề đang làm", durationSeconds: 1800 }, questions: [sqlQuestion], answers: { 2: "2" } } }));
  await page.goto("/#/quiz?examId=8&attemptId=51");
  await expect(page.locator(".options .selected")).toContainText("2");
  await expect(page.locator(".quiz-timer strong")).toHaveText(/^28:|^29:/);
  await page.reload();
  await expect(page.locator(".options .selected")).toContainText("2");
  await expect(page.locator(".quiz-timer strong")).toHaveText(/^28:|^29:/);
  expect(creations).toBe(0);
  expect(await page.evaluate(() => JSON.stringify(localStorage))).not.toContain("Đề đang làm");
});

test("expired quiz submits once, locks answers and reuses the key after a network failure", async ({ page }) => {
  await fixture(page); let submissions = 0; const keys: string[] = []; let submitted = false;
  const now = Date.now();
  const attempt = () => ({ id: 51, examId: 8, status: submitted ? "SUBMITTED" : "IN_PROGRESS", startedAt: new Date(now - 1800_000).toISOString(), deadlineAt: new Date(now - 1000).toISOString(), serverTime: new Date().toISOString(), elapsedSeconds: 1800, exam: { title: "Đề hết giờ", durationSeconds: 1800 }, questions: [sqlQuestion], answers: { 2: "2" } });
  await page.route("**/api/quiz-attempts/51**", async r => {
    if (r.request().method() === "POST") { submissions++; keys.push(r.request().headers()["idempotency-key"]); if (submissions === 1) return r.abort("connectionfailed"); submitted = true; }
    return r.fulfill({ json: attempt() });
  });
  await page.goto("/#/quiz?examId=8&attemptId=51");
  await expect(page.getByRole("alert")).toContainText("Đã hết giờ");
  await expect(page.locator(".options button").first()).toBeDisabled();
  expect(submissions).toBe(1);
  await page.evaluate(() => window.dispatchEvent(new Event("online")));
  await expect(page.locator(".result-page")).toBeVisible();
  expect(submissions).toBe(2); expect(keys[0]).toBe(keys[1]);
  await expect(page.getByText("CHỜ CÔNG BỐ KẾT QUẢ")).toBeVisible();
});

test("exercise filters use metadata and duplicate titles keep their own IDs", async ({ page }) => {
  await fixture(page);
  await page.route("**/api/exercises", r => r.fulfill({ json: [
    { id: 8, title: "Trùng tên", chapter: "Chương A", kind: "quiz", difficulty: "EASY", questionCount: 12, durationSeconds: 2700, status: "COMPLETED", bestScore: 0 },
    { id: 9, title: "Trùng tên", chapter: "Chương B", kind: "quiz", difficulty: "HARD", questionCount: 3, durationSeconds: 600, status: "IN_PROGRESS" },
  ] }));
  await page.goto("/#/exercises");
  await expect(page.locator(".exercise-card")).toHaveCount(2);
  await expect(page.locator(".exercise-card").first()).toContainText("45 phút");
  await page.getByRole("combobox", { name: "Chương", exact: true }).selectOption("Chương B");
  await page.getByRole("combobox", { name: "Độ khó", exact: true }).selectOption("HARD");
  await page.getByRole("combobox", { name: "Loại bài tập", exact: true }).selectOption("quiz");
  await page.getByRole("combobox", { name: "Trạng thái", exact: true }).selectOption("IN_PROGRESS");
  await expect(page.locator(".exercise-card")).toHaveCount(1);
  await page.locator(".exercise-card").getByRole("button", { name: "Tiếp tục" }).click();
  await expect(page).toHaveURL(/examId=9/);
});

test("teacher regenerates exactly one question and keeps edited neighbours", async ({ page }) => {
  await fixture(page);
  await page.route("**/api/auth/me", r => r.fulfill({ json: { user: { ...user, role: "TEACHER" }, csrfToken: "session-csrf" } }));
  await page.route("**/api/chapters", r => r.fulfill({ json: [{ id: 1, title: "Chương SQL" }] }));
  await page.route("**/api/topics", r => r.fulfill({ json: [{ id: 2, chapterId: 1, title: "Chủ đề SQL" }] }));
  let calls = 0;
  await page.route("**/api/ai/questions/generate", r => {
    const input = r.request().postDataJSON(); calls++;
    expect(input).toMatchObject({ chapter: "Chương SQL", topic: "Chủ đề SQL", cognitiveLevel: "KNOWLEDGE", questionType: "MCQ", difficulty: "MEDIUM", count: calls === 1 ? 5 : 1 });
    return r.fulfill({ json: Array.from({ length: input.count }, (_, i) => ({ ...sqlQuestion, id: -i - 1, chapter: input.chapter, topic: input.topic, difficulty: input.difficulty, content: calls === 1 ? `Câu gốc ${i + 1}` : "Câu thay thế" })) });
  });
  await page.goto("/#/generator");
  await page.locator(".generator-form").getByRole("button", { name: "Tạo câu hỏi bằng AI", exact: true }).click();
  await expect(page.getByText("Câu gốc 5", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Tạo lại", exact: true }).nth(2).click();
  await expect(page.getByText("Câu thay thế", { exact: true })).toBeVisible();
  await expect(page.getByText("Câu gốc 1", { exact: true })).toBeVisible();
  await expect(page.getByText("Câu gốc 5", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Tạo lại", exact: true })).toHaveCount(5);
  expect(calls).toBe(2);
});

test("essay image uses multipart and survives switching input modes before submission", async ({ page }) => {
  await fixture(page);
  const question = { ...sqlQuestion, id: 101, type: "ESSAY", title: "Tự luận SQL" };
  const submission = { id: 61, studentId: user.id, questionId: 101, question, originalSubmission: { image: "/api/files/11", imageName: "answer.png", imageHash: "server-hash" }, originalFeedback: "Đã nhận", status: "CORRECT", aiSteps: [], studentStepAnswers: [], retrySubmissions: [], totalSteps: 0, currentStep: 0, startedAt: new Date().toISOString(), updatedAt: new Date().toISOString(), elapsedSeconds: 3, finalResult: { correct: true, feedback: "Đã đánh giá", score: 10 } };
  await page.route("**/api/questions/101", r => r.fulfill({ json: question }));
  let uploads = 0;
  await page.route("**/api/essays", r => {
    uploads++;
    expect(r.request().headers()["content-type"]).toMatch(/^multipart\/form-data; boundary=/);
    expect(r.request().headers()["x-csrf-token"]).toBe("session-csrf");
    const body = r.request().postDataBuffer()!.toString("latin1");
    expect(body).toContain('name="payload"'); expect(body).toContain('name="image"');
    expect(body).not.toContain("blob:"); expect(body).not.toContain("data:image");
    return r.fulfill({ status: 201, json: submission });
  });
  await page.route("**/api/ai/essays/evaluate", r => r.fulfill({ json: submission }));
  await page.route("**/api/essays/61", r => r.fulfill({ json: submission }));
  await page.goto("/#/essay-practice?essayQuestionId=101");
  const bytes = Buffer.from(await page.evaluate(() => { const c = document.createElement("canvas"); c.width = c.height = 2; return c.toDataURL("image/png").split(",")[1]; }), "base64");
  await page.getByRole("button", { name: "Upload ảnh", exact: true }).click();
  await page.getByLabel("Upload ảnh bài làm", { exact: true }).setInputFiles({ name: "answer.png", mimeType: "image/png", buffer: bytes });
  await expect(page.getByAltText("Ảnh bài làm đã chọn")).toBeVisible();
  await page.getByRole("button", { name: "Nhập bài làm", exact: true }).click();
  await page.getByRole("button", { name: "Upload ảnh", exact: true }).click();
  expect(await page.getByAltText("Ảnh bài làm đã chọn").evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth > 0)).toBe(true);
  await page.getByRole("button", { name: "Nộp bài", exact: true }).click();
  await expect(page).toHaveURL(/essay-result.*submissionId=61/);
  expect(uploads).toBe(1);
});

test("teacher saves and reloads lesson content using the backend ID", async ({ page }) => {
  await fixture(page);
  await page.route("**/api/auth/me", r => r.fulfill({ json: { user: { ...user, role: "TEACHER" }, csrfToken: "session-csrf" } }));
  await page.route("**/api/chapters", r => r.fulfill({ json: [{ id: 88, title: "Chương SQL" }] }));
  let content = "";
  await page.route("**/api/lessons", r => { const payload = r.request().postDataJSON(); expect(payload).toMatchObject({ chapterId: 88, courseId: 8, title: "Bài SQL", published: false }); content = payload.content; return r.fulfill({ json: { id: 77 } }); });
  await page.route("**/api/lessons/77", r => r.fulfill({ json: { id: 77, courseId: 8, chapterId: 88, title: "Bài SQL", description: "", content, published: false, completed: 0 } }));
  await page.goto("/#/create-lesson");
  await page.getByLabel("Tên bài giảng", { exact: true }).fill("Bài SQL");
  await page.getByRole("textbox", { name: "Nội dung văn bản" }).fill("Nội dung SQL x²");
  await page.getByRole("button", { name: "Lưu nháp", exact: true }).click();
  await expect(page).toHaveURL(/editLessonId=77/);
  await page.reload();
  await expect(page.getByLabel("Tên bài giảng", { exact: true })).toHaveValue("Bài SQL");
  await expect(page.getByRole("textbox", { name: "Nội dung văn bản" })).toContainText("Nội dung SQL x²");
});

test("essay questions inside an exam restore text and submit for server grading", async ({ page }) => {
  await fixture(page);
  let saved = false, submitted = false;
  const start = new Date().toISOString();
  const attempt = { id: 90, examId: 9, status: "IN_PROGRESS", startedAt: start, elapsedSeconds: 0, exam: { title: "Đề tự luận", durationSeconds: 1800 }, questions: [{ ...sqlQuestion, type: "ESSAY" }], answers: {}, essayAnswers: { 2: { content: { text: "Bài đã lưu" } } } };
  await page.route("**/api/quiz-attempts/90**", r => {
    if (r.request().method() === "POST") {
      const payload = r.request().postDataJSON(); expect(payload).not.toHaveProperty("score"); expect(payload.essayAnswers[2].content.text).toBe("Bài đã sửa");
      saved = true; if (r.request().url().endsWith("/submit")) { submitted = true; attempt.status = "PENDING_GRADING"; }
    }
    return r.fulfill({ json: attempt });
  });
  await page.goto("/#/quiz?examId=9&attemptId=90");
  await expect(page.getByLabel("Bài làm", { exact: true })).toHaveValue("Bài đã lưu");
  await page.getByLabel("Bài làm", { exact: true }).fill("Bài đã sửa");
  await page.getByLabel("Bài làm", { exact: true }).blur();
  await expect.poll(() => saved).toBe(true);
  await page.locator(".quiz-top").getByRole("button", { name: "Nộp bài", exact: true }).click();
  await page.getByRole("button", { name: "Nộp bài", exact: true }).last().click();
  await expect(page.locator(".result-page")).toBeVisible();
  expect(submitted).toBe(true);
  await expect(page.getByText("CHỜ CÔNG BỐ KẾT QUẢ")).toBeVisible();
});

test("teacher with multiple courses must explicitly bind new questions to a course", async ({ page }) => {
  await fixture(page);
  await page.route("**/api/auth/me", r => r.fulfill({ json: { user: { ...user, role: "TEACHER" }, csrfToken: "session-csrf" } }));
  await page.route("**/api/courses", r => r.fulfill({ json: [{id:8,title:"Khóa A"},{id:9,title:"Khóa B"}] }));
  await page.route("**/api/chapters", r => r.fulfill({ json: [{ id: 1, title: "Chương SQL" }] }));
  await page.route("**/api/topics", r => r.fulfill({ json: [{ id: 2, chapterId: 1, title: "Chủ đề SQL" }] }));
  let persisted = false;
  await page.route("**/api/ai/questions/generate", r => {
    const input = r.request().postDataJSON(); expect(input.courseId).toBe(9);
    return r.fulfill({ json: Array.from({length:input.count},(_,i)=>({...sqlQuestion,id:-i-1,content:`Câu ${i+1}`,answer:"2",chapter:input.chapter,topic:input.topic})) });
  });
  await page.route("**/api/questions**", r => { if(r.request().method()==="POST") { expect(r.request().postDataJSON().questions.every((q:{courseId:number})=>q.courseId===9)).toBe(true); persisted=true; } return r.fulfill({json:[]}); });
  await page.goto("/#/generator");
  const generate = page.locator(".generator-form").getByRole("button", {name:"Tạo câu hỏi bằng AI",exact:true});
  await expect(generate).toBeDisabled();
  await page.locator(".generator-form").getByRole("combobox").first().selectOption("9:0");
  await generate.click();
  await page.getByRole("button",{name:"Thêm 5 câu vào ngân hàng",exact:true}).click();
  await expect.poll(()=>persisted).toBe(true);
});

test("exam essay image uploads as multipart, restores on refresh and submits only the server reference", async ({ page }) => {
  await fixture(page); let uploaded=false, submitted=false;
  const attempt = { id:90,examId:9,status:"IN_PROGRESS",startedAt:new Date().toISOString(),elapsedSeconds:0,exam:{title:"Đề ảnh",durationSeconds:1800},questions:[{...sqlQuestion,type:"ESSAY"}],answers:{},essayAnswers:{2:{content:{text:"Bài đã lưu",image:"",imageHash:""}}} };
  await page.goto("/");
  const bytes = Buffer.from(await page.evaluate(()=>{const c=document.createElement("canvas");c.width=c.height=2;return c.toDataURL("image/png").split(",")[1];}),"base64");
  await page.route("**/api/exam-files/44", r=>r.fulfill({contentType:"image/png",body:bytes}));
  await page.route("**/api/quiz-attempts/90**", r=>{
    if(r.request().method()==="POST") {
      if(r.request().headers()["content-type"].startsWith("multipart/")) {
        const raw=r.request().postDataBuffer()!.toString("utf8");expect(raw).toContain('"questionId":2');expect(raw).not.toContain("blob:");expect(raw).not.toContain("data:image");
        uploaded=true;attempt.essayAnswers[2].content.image="/api/exam-files/44";attempt.essayAnswers[2].content.imageHash="server-hash";
      } else if(r.request().url().endsWith("/submit")) {
        const input=r.request().postDataJSON();expect(input.essayAnswers[2].content.image).toBe("/api/exam-files/44");expect(input.essayAnswers[2].content.text).toBe("Bài đã lưu");expect(input.essayAnswers[2].content).not.toHaveProperty("imageFile");submitted=true;attempt.status="PENDING_GRADING";
      }
    }
    return r.fulfill({json:attempt});
  });
  await page.goto("/#/quiz?examId=9&attemptId=90");
  await page.getByLabel("Upload ảnh bài làm",{exact:true}).setInputFiles({name:"exam.png",mimeType:"image/png",buffer:bytes});
  await expect.poll(()=>uploaded).toBe(true);
  await expect(page.getByAltText("Ảnh bài làm đã chọn")).toHaveAttribute("src","/api/exam-files/44");
  await page.reload();
  await expect(page.getByAltText("Ảnh bài làm đã chọn")).toHaveAttribute("src","/api/exam-files/44");
  await page.locator(".quiz-top").getByRole("button",{name:"Nộp bài",exact:true}).click();
  await page.getByRole("button",{name:"Nộp bài",exact:true}).last().click();
  await expect(page.locator(".result-page")).toBeVisible();expect(submitted).toBe(true);
});

test("teacher opens an authorized student's quiz detail through the existing history", async ({ page }) => {
  await fixture(page);
  await page.route("**/api/auth/me",r=>r.fulfill({json:{user:{...user,role:"TEACHER"},csrfToken:"session-csrf"}}));
  await page.route("**/api/students/17/statistics",r=>r.fulfill({json:analytics}));
  await page.route("**/api/learning-history*",r=>r.fulfill({json:[{id:"h1",title:"Bài của học sinh",type:"MCQ",category:"Kiểm tra",attempt:1,score:10,status:"Hoàn thành",date:new Date().toISOString(),elapsedSeconds:60,attemptId:51}]}));
  await page.route("**/api/quiz-attempts/51",r=>r.fulfill({json:{id:51,examId:8,status:"SUBMITTED",exam:{title:"Đề được phân công",durationSeconds:1800},questions:[sqlQuestion],answers:{2:"2"},result:{score:10,correctCount:1,totalCount:1,review:[{...sqlQuestion,answer:"2",studentAnswer:"2",correct:true}]}}}));
  await page.goto("/#/student-detail?studentId=17");
  await page.getByRole("button",{name:"Xem bài làm: Bài của học sinh",exact:true}).click();
  const dialog=page.getByRole("dialog",{name:"Chi tiết bài làm học sinh",exact:true});
  await expect(dialog).toContainText("Đề được phân công");await expect(dialog).toContainText("Điểm: 10/10");await expect(dialog).toContainText("Bài làm: 2");
});

test("topbar search paginates on the server, restores URL state and opens the exact lesson", async ({ page }) => {
  await fixture(page);
  const lesson={id:77,title:"Chủ đề bài giảng",chapter:"Chương SQL",courseId:8,chapterId:3,description:"Nội dung từ API",durationSeconds:900,completed:1,published:true,updatedAt:new Date().toISOString(),content:"<p>Bài giảng đã lưu</p>"};
  const matches=Array.from({length:25},(_,i)=>({id:i===20?77:100+i,title:i===20?lesson.title:`Chủ đề ${i+1}`,chapter:lesson.chapter,kind:i===20?"lesson":"quiz",courseId:8,chapterId:3}));
  const offsets:number[]=[];
  await page.route("**/api/search?*",r=>{const query=new URL(r.request().url()).searchParams;expect(query.get("q")).toBe("chu de");expect(query.get("limit")).toBe("21");const offset=Number(query.get("offset"));offsets.push(offset);return r.fulfill({json:matches.slice(offset,offset+21)});});
  await page.route("**/api/lessons**",r=>{const path=new URL(r.request().url()).pathname;return r.fulfill({json:path.endsWith("/progress")?{saved:true}:path.endsWith("/77")?lesson:[lesson]});});
  await page.goto("/");
  const input=page.locator(".topbar").getByPlaceholder("Tìm bài học, bài tập...");
  await page.keyboard.press("Control+k");await expect(input).toBeFocused();
  await input.fill("chu de");await input.press("Enter");
  await expect(page.locator("tbody tr")).toHaveCount(20);
  await page.getByRole("button",{name:"Trang sau",exact:true}).click();
  await expect(page.locator("tbody tr")).toHaveCount(5);
  await expect(page).toHaveURL(/page=1/);
  await page.reload();await expect(page.locator("tbody tr")).toHaveCount(5);
  await page.locator("tbody tr").filter({hasText:lesson.title}).getByRole("button",{name:"Xem",exact:true}).click();
  await expect(page).toHaveURL(/lessonId=77/);
  await expect(page.getByRole("heading",{name:lesson.title,exact:true})).toBeVisible();
  await expect(page.getByRole("button",{name:"Đã hoàn thành",exact:true})).toBeDisabled();
  expect(offsets).toEqual([0,20,20]);
});

test("search failure remains visible and never shows mock results", async ({ page }) => {
  await fixture(page);
  await page.route("**/api/search?*",r=>r.fulfill({status:500,json:{error:{message:"Không tải được kết quả tìm kiếm."}}}));
  await page.goto("/#/search?q=sin");
  await expect(page.getByRole("alert")).toHaveText("Không tải được kết quả tìm kiếm.");
  await expect(page.locator("tbody tr")).toHaveCount(0);
  await expect(page.locator(".profile-name strong")).toHaveText(user.name);
});

test("teacher enrolls a permitted student through the existing students action", async ({ page }) => {
  await fixture(page);let enrolled=false;
  await page.route("**/api/auth/me",r=>r.fulfill({json:{user:{...user,role:"TEACHER"},csrfToken:"session-csrf"}}));
  await page.route("**/api/users",r=>r.fulfill({json:[{...user,active:true,createdAt:new Date().toISOString()}]}));
  await page.route("**/api/courses/8/enrollments",r=>{expect(r.request().postDataJSON()).toEqual({studentId:17});expect(r.request().headers()["x-csrf-token"]).toBe("session-csrf");enrolled=true;return r.fulfill({json:{saved:true}});});
  await page.goto("/#/students");await expect(page.locator("tbody tr")).toHaveCount(1);
  await page.getByRole("button",{name:"Thêm mới",exact:true}).click();
  await page.getByRole("combobox",{name:"Khóa học",exact:true}).selectOption("8");
  await page.getByRole("combobox",{name:"Học sinh",exact:true}).selectOption("17");
  await page.getByRole("button",{name:"Lưu ghi danh",exact:true}).click();
  await expect.poll(()=>enrolled).toBe(true);await expect(page.getByText("Đã lưu thay đổi.",{exact:true})).toBeVisible();
});

test("notification reads update sidebar and topbar without duplicate initial GET requests", async ({ page }) => {
  await fixture(page);let reads=0,marked=false;
  await page.route("**/api/notifications",r=>{reads++;return r.fulfill({json:[{id:3,content:"Thông báo từ server",isRead:marked,createdAt:new Date().toISOString()}]});});
  await page.route("**/api/notifications/3/read",r=>{marked=true;return r.fulfill({json:{saved:true}});});
  await page.goto("/");await expect(page.locator(".sidebar-bottom").getByRole("button",{name:"Thông báo 1",exact:true})).toBeVisible();expect(reads).toBe(1);
  await page.locator(".sidebar-bottom").getByRole("button",{name:"Thông báo 1",exact:true}).click();
  await expect(page.locator(".notification-page .unread")).toHaveCount(1);
  await page.getByText("Thông báo từ server",{exact:true}).click();
  await expect(page.locator(".sidebar-bottom").getByRole("button",{name:"Thông báo 0",exact:true})).toBeVisible();
  await page.locator(".topbar .notification").click();
  await expect(page.locator(".notification-popover")).toContainText("0 mới");
  await expect(page.locator(".notification-popover .unread-dot")).toHaveCount(0);
  expect(reads).toBe(2);
});
