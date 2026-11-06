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

test("lesson practice uses linked IDs and keeps its scope through reload and browser history", async ({ page }) => {
  await fixture(page);
  const lesson = { id: 77, title: "Bài giảng SQL", chapter: "Chương SQL", courseId: 8, chapterId: 88, completed: 1, published: true, elapsedSeconds: 60, content: "<p>Nội dung bài giảng</p>" };
  const otherLesson = { ...lesson, id: 78 };
  await page.route("**/api/lessons?**", r => r.fulfill({ json: [lesson, otherLesson] }));
  await page.route("**/api/lessons/77", r => r.fulfill({ json: lesson }));
  await page.route("**/api/lessons/78", r => r.fulfill({ json: otherLesson }));
  await page.route("**/api/lessons/77/progress", r => r.fulfill({ json: { saved: true } }));
  const quiz = { id: 321, title: "Đề luyện", chapter: lesson.chapter, kind: "quiz", difficulty: "EASY", status: "IN_PROGRESS", questionCount: 1 };
  const essay = { ...quiz, id: 654, kind: "essay", title: "Bài tự luận liên kết", status: "NOT_STARTED" };
  await page.route("**/api/exercises**", r => {
    const id = new URL(r.request().url()).searchParams.get("lessonId");
    return r.fulfill({ json: id === "77" ? [quiz, essay] : id === "78" ? [] : [{ ...quiz, id: 999 }] });
  });
  await page.route("**/api/questions/654", r => r.fulfill({ json: { ...sqlQuestion, id: essay.id, title: essay.title, type: "ESSAY" } }));
  const attempt = { id: 87, examId: quiz.id, status: "IN_PROGRESS", exam: { title: quiz.title, durationSeconds: 1800 }, startedAt: new Date().toISOString(), questions: [sqlQuestion], answers: {} };
  await page.route("**/api/quiz-attempts", r => r.fulfill({ json: [
    { id: 86, examId: 999, status: "IN_PROGRESS" },
    { id: attempt.id, examId: quiz.id, status: attempt.status },
  ] }));
  await page.route("**/api/quiz-attempts/87", r => r.fulfill({ json: attempt }));

  await page.goto("/#/lesson?lessonId=77");
  await expect(page.getByRole("button", { name: "Đã hoàn thành" })).toBeDisabled();
  await page.getByRole("button", { name: "Luyện tập bài này" }).click();
  await expect(page).toHaveURL(/exercises.*practiceLessonId=77/);
  await page.reload();
  await expect(page.locator(".exercise-card")).toHaveCount(2);
  await page.getByRole("button", { name: "Tiếp tục" }).click();
  await expect(page).toHaveURL(/quiz.*examId=321&attemptId=87/);
  await page.reload();
  await expect(page.locator(".quiz-top")).toContainText(quiz.title);
  await page.goBack();
  await expect(page).toHaveURL(/exercises.*practiceLessonId=77$/);
  await page.goForward();
  await expect(page.locator(".quiz-top")).toContainText(quiz.title);
  await page.goBack();
  await page.getByRole("button", { name: "Làm bài ngay" }).click();
  await expect(page).toHaveURL(/essay-practice.*essayQuestionId=654/);
  await expect(page.getByRole("heading", { name: essay.title })).toBeVisible();
  await page.goBack();
  await page.goBack();
  await expect(page).toHaveURL(/lesson\?lessonId=77$/);
  await page.getByRole("button", { name: "Bài tiếp theo" }).click();
  await expect(page).toHaveURL(/lesson\?lessonId=78$/);
  await page.getByRole("button", { name: "Luyện tập bài này" }).click();
  await expect(page.getByText("Bài giảng chưa có bài luyện tập")).toBeVisible();
  await page.locator(".sidebar").getByRole("button", { name: "Bài tập", exact: true }).click();
  await expect(page).not.toHaveURL(/practiceLessonId/);
  await expect(page.getByRole("heading", { name: quiz.title })).toBeVisible();
});

test("essay editor submits text and LaTeX and retries evaluation without resubmitting", async ({ page }) => {
  await fixture(page);
  const question = { ...sqlQuestion, id: 101, type: "ESSAY", title: "Tự luận SQL" };
  const text = "Kết quả:\n\n\\(\\frac{1}{2}\\)";
  const submission = { id: 61, studentId: user.id, questionId: 101, question, originalSubmission: { text }, originalFeedback: "Đã đánh giá", status: "CORRECT", aiSteps: [], studentStepAnswers: [], retrySubmissions: [], totalSteps: 0, currentStep: 0, startedAt: new Date().toISOString(), updatedAt: new Date().toISOString(), elapsedSeconds: 3 };
  await page.route("**/api/questions/101", r => r.fulfill({ json: question }));
  let submits = 0, evaluations = 0;
  await page.route("**/api/essays", r => {
    submits++;
    const payload = r.request().postDataJSON();
    expect(payload.questionId).toBe(101);
    expect(payload.content).toEqual({ text });
    expect(r.request().headers()["idempotency-key"]).toBeTruthy();
    return r.fulfill({ status: 201, json: { ...submission, status: "SUBMITTED" } });
  });
  await page.route("**/api/ai/essays/evaluate", r => {
    expect(r.request().postDataJSON()).toEqual({ submissionId: 61 });
    return ++evaluations === 1
      ? r.fulfill({ status: 503, json: { error: { message: "Đánh giá tạm gián đoạn." } } })
      : r.fulfill({ json: submission });
  });
  await page.route("**/api/essays/61", r => r.fulfill({ json: submission }));
  await page.goto("/#/essay-practice?essayQuestionId=101");
  const editor = page.getByRole("textbox", { name: "Bài làm", exact: true });
  const submit = page.getByRole("button", { name: "Nộp bài", exact: true });
  await expect(submit).toBeDisabled();
  await editor.fill("   ");
  await expect(submit).toBeDisabled();
  await page.getByRole("button", { name: "Formula" }).click();
  const formula = page.getByRole("textbox", { name: "LaTeX" });
  await formula.fill("\\frac{");
  await page.getByRole("button", { name: "Chèn công thức" }).click();
  await expect(page.getByText("Công thức LaTeX chưa hợp lệ.")).toBeVisible();
  await formula.fill("\\frac{1}{2}");
  await page.getByRole("button", { name: "Chèn công thức" }).click();
  await expect(editor.locator(".katex")).toBeVisible();
  await expect(submit).toBeEnabled();
  await editor.fill("");
  await expect(submit).toBeDisabled();
  await editor.evaluate(element => {
    const clipboardData = new DataTransfer();
    clipboardData.setData("text/html", '<p><strong>Kết quả:</strong><img src="x" onerror="window.unsafeEssayPaste=true"><script>window.unsafeEssayPaste=true</script></p>');
    element.dispatchEvent(new ClipboardEvent("paste", { clipboardData, bubbles: true, cancelable: true }));
  });
  await expect(editor.locator("strong")).toHaveText("Kết quả:");
  await expect(editor.locator("img, script, [onerror]")).toHaveCount(0);
  await editor.press("Control+End");
  await editor.press("Enter");
  await page.getByRole("button", { name: "Formula" }).click();
  await formula.fill("\\frac{1}{2}");
  await page.getByRole("button", { name: "Chèn công thức" }).click();
  await page.getByRole("button", { name: "Upload ảnh", exact: true }).click();
  await page.getByRole("button", { name: "Nhập bài làm", exact: true }).click();
  await expect(editor.locator(".katex")).toBeVisible();
  await submit.click();
  await expect(page.getByRole("alert")).toHaveText("Đánh giá tạm gián đoạn.");
  await expect(page.locator(".tiptap[aria-label='Bài làm']")).toHaveAttribute("contenteditable", "false");
  await submit.click();
  await expect(page).toHaveURL(/essay-result.*submissionId=61/);
  await expect(page.getByText("Đã đánh giá")).toBeVisible();
  expect(submits).toBe(1);
  expect(evaluations).toBe(2);
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
  await page.route("**/api/courses?*", r => r.fulfill({ json: [{id:8,title:"Khóa A"},{id:9,title:"Khóa B"}] }));
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
  await page.route("**/api/users?*",r=>r.fulfill({json:[{...user,active:true,createdAt:new Date().toISOString()}]}));
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

test("teacher grades pending essay answers and reloads the server-confirmed result", async ({ page }) => {
  await fixture(page);
  await page.route("**/api/auth/me", r=>r.fulfill({json:{user:{...user,role:"TEACHER"},csrfToken:"session-csrf"}}));
  await page.route("**/api/students/17/statistics",r=>r.fulfill({json:analytics}));
  await page.route("**/api/learning-history*",r=>r.fulfill({json:[{id:"pending",title:"Bài chờ chấm",attemptId:51,status:"PENDING_GRADING",date:new Date().toISOString()}]}));
  const essay={...sqlQuestion,id:3,title:"Tự luận cần chấm",type:"ESSAY",options:undefined,answer:"Rubric của giáo viên"};
  let status="PENDING_GRADING", writes=0;
  let graded: unknown;
  await page.route("**/api/quiz-attempts/51**",async r=>{
    if(r.request().method()==="POST"){
      expect(r.request().postDataJSON()).toEqual({grades:[{questionId:3,score:8,feedback:"Đúng phương pháp"}]});
      expect(r.request().headers()["idempotency-key"]).toBeTruthy();writes++;status="GRADED";graded={score:8,feedback:"Đúng phương pháp",gradedBy:17};
    }
    return r.fulfill({json:{id:51,examId:8,status,exam:{title:"Đề hỗn hợp",durationSeconds:1800},questions:[sqlQuestion,essay],answers:{2:"2"},mcqScores:{2:10},essayAnswers:{3:{content:{text:"Lời giải của học sinh"},...(graded as object || {})}},...(status==="GRADED"?{result:{score:9,review:[]}}:{})}});
  });
  page.on("dialog",dialog=>dialog.accept());
  await page.goto("/#/student-detail?studentId=17");
  await page.getByRole("button",{name:"Xem bài làm: Bài chờ chấm",exact:true}).click();
  const dialog=page.getByRole("dialog");
  await expect(dialog).toContainText("Lời giải của học sinh");
  await dialog.getByLabel("Điểm: Tự luận cần chấm",{exact:true}).fill("11");
  await dialog.getByLabel("Nhận xét",{exact:true}).fill("Đúng phương pháp");
  await dialog.getByRole("button",{name:"Lưu kết quả chấm",exact:true}).click();
  await expect(dialog.getByRole("alert")).toContainText("0 đến 10");expect(writes).toBe(0);
  await dialog.getByLabel("Điểm: Tự luận cần chấm",{exact:true}).fill("8");
  await dialog.getByRole("button",{name:"Lưu kết quả chấm",exact:true}).click();
  await expect(dialog).toContainText("GRADED");expect(writes).toBe(1);
  await page.reload();await page.getByRole("button",{name:"Xem bài làm: Bài chờ chấm",exact:true}).click();
  await expect(page.getByLabel("Điểm: Tự luận cần chấm",{exact:true})).toHaveValue("8");
  await expect(page.getByLabel("Điểm: Tự luận cần chấm",{exact:true})).toBeDisabled();
});

test("exam draft duration, explicit publication and result publication use distinct API fields", async ({ page }) => {
  await fixture(page);
  await page.route("**/api/auth/me",r=>r.fulfill({json:{user:{...user,role:"TEACHER"},csrfToken:"session-csrf"}}));
  await page.route("**/api/chapters",r=>r.fulfill({json:[{id:1,title:"Toán",position:1}]}));
  await page.route("**/api/topics",r=>r.fulfill({json:[{id:2,chapterId:1,title:"Ôn tập"}]}));
  let exam={id:9,title:"Đề nháp",chapter:"Toán",topic:"Ôn tập",courseId:8,published:false,resultsPublished:false,durationSeconds:1800,matrix:{KNOWLEDGE:{MCQ:1,ESSAY:0},UNDERSTANDING:{MCQ:0,ESSAY:0},APPLICATION:{MCQ:0,ESSAY:0}},questions:[{...sqlQuestion,answer:"2"}],createdAt:new Date().toISOString()};
  const saves:boolean[]=[];let publication=0;
  await page.route("**/api/exams**",async r=>{
    const path=new URL(r.request().url()).pathname;
    if(path.endsWith("publish-results")){publication++;exam.resultsPublished=true;return r.fulfill({json:{published:true}});}
    if(r.request().method()==="POST"){const body=r.request().postDataJSON();expect(body).not.toHaveProperty("resultsPublished");expect(body.durationSeconds).toBe(2700);saves.push(body.published);exam={...exam,...body};return r.fulfill({json:exam});}
    return r.fulfill({json:[exam]});
  });
  page.on("dialog",d=>d.accept());
  await page.goto("/#/exam-generator");
  await page.getByRole("button",{name:/Đề nháp · 1 câu/}).click();
  await page.getByLabel("Thời gian làm bài (phút)",{exact:true}).fill("45");
  await page.getByRole("button",{name:"Lưu nháp",exact:true}).click();await expect.poll(()=>saves.length).toBe(1);expect(saves[0]).toBe(false);
  await expect(page.getByRole("button",{name:"Xuất bản",exact:true})).toBeEnabled();
  await page.getByRole("button",{name:"Xuất bản",exact:true}).click();await expect.poll(()=>saves.length).toBe(2);expect(saves[1]).toBe(true);
  await expect(page.getByRole("button",{name:"Công bố kết quả",exact:true})).toBeEnabled();
  await page.getByRole("button",{name:"Công bố kết quả",exact:true}).click();await expect(page.getByText("Đã công bố kết quả",{exact:true})).toBeVisible();expect(publication).toBe(1);
});

test("admin pagination filters the whole server dataset and survives refresh", async ({ page }) => {
  await fixture(page);
  await page.route("**/api/auth/me",r=>r.fulfill({json:{user:{...user,role:"ADMIN"},csrfToken:"session-csrf"}}));
  const users=Array.from({length:125},(_,i)=>({id:i+1,name:`User ${i+1}`,email:`user${i+1}@example.vn`,role:"STUDENT",active:true}));
  const offsets:number[]=[];
  await page.route("**/api/users?*",r=>{const q=new URL(r.request().url()).searchParams;const offset=Number(q.get("offset"));offsets.push(offset);const rows=users.filter(u=>(u.name+u.email).includes(q.get("q") || ""));return r.fulfill({json:rows.slice(offset,offset+Number(q.get("limit")))});});
  await page.goto("/#/users");await expect(page.locator("tbody tr")).toHaveCount(20);
  await page.locator(".pagination button").last().click();await expect(page.locator("tbody tr").first()).toContainText("User 21");expect(offsets).toContain(20);
  await page.getByPlaceholder("Tìm theo tên hoặc email...").fill("user125@");await expect(page.locator("tbody tr")).toHaveCount(1);await expect(page.locator("tbody tr")).toContainText("User 125");
  await page.reload();await expect(page.getByPlaceholder("Tìm theo tên hoặc email...")).toHaveValue("user125@");await expect(page.locator("tbody tr")).toHaveCount(1);
});

test("teacher grades a standalone essay separately from quiz grading", async ({page})=>{
  await fixture(page);
  await page.route("**/api/auth/me",r=>r.fulfill({json:{user:{...user,role:"TEACHER"},csrfToken:"session-csrf"}}));
  await page.route("**/api/students/17/statistics",r=>r.fulfill({json:analytics}));
  await page.route("**/api/learning-history*",r=>r.fulfill({json:[{id:"essay",title:"Tự luận độc lập",submissionId:71,status:"SUBMITTED",date:new Date().toISOString()}]}));
  let result:unknown,writes=0;
  await page.route("**/api/essays/71**",r=>{
    if(r.request().method()==="POST"){expect(r.request().postDataJSON()).toEqual({score:7,feedback:"Cần giải thích thêm"});writes++;result={score:7,feedback:"Cần giải thích thêm",correct:true,gradedBy:17};}
    return r.fulfill({json:{id:71,studentId:17,questionId:3,question:{...sqlQuestion,type:"ESSAY",title:"Bài độc lập"},originalSubmission:{text:"Lời giải gốc"},originalFeedback:"Feedback AI đã lưu",status:result?"COMPLETED":"SUBMITTED",aiSteps:[],studentStepAnswers:[],retrySubmissions:[],totalSteps:0,currentStep:0,startedAt:new Date().toISOString(),updatedAt:new Date().toISOString(),elapsedSeconds:120,finalResult:result}});
  });
  page.on("dialog",d=>d.accept());await page.goto("/#/student-detail?studentId=17");await page.getByRole("button",{name:"Xem bài làm: Tự luận độc lập",exact:true}).click();
  const dialog=page.getByRole("dialog");await expect(dialog).toContainText("Lời giải gốc");await expect(dialog).toContainText("Feedback AI đã lưu");await dialog.getByLabel("Điểm: Bài độc lập",{exact:true}).fill("7");await dialog.getByLabel("Nhận xét",{exact:true}).fill("Cần giải thích thêm");await dialog.getByRole("button",{name:"Lưu kết quả chấm",exact:true}).click();await expect(dialog).toContainText("Điểm: 7/10");expect(writes).toBe(1);await expect(dialog.getByRole("button",{name:"Lưu kết quả chấm",exact:true})).toHaveCount(0);
});

test("offline essay draft survives reload and revision conflict preserves text until explicit reload",async({page})=>{
  await fixture(page);let conflict=false,writes=0;
  const attempt=()=>({id:51,examId:8,revision:conflict?"v2":"v1",status:"IN_PROGRESS",startedAt:new Date().toISOString(),serverTime:new Date().toISOString(),elapsedSeconds:0,exam:{title:"Đề nháp offline",durationSeconds:1800},questions:[{...sqlQuestion,type:"ESSAY"}],answers:{},essayAnswers:{2:{content:{text:conflict?"Lời giải từ tab khác":""}}}});
  await page.route("**/api/quiz-attempts/51**",r=>{
    if(r.request().method()==="POST"){writes++;if(!conflict)return r.abort("connectionfailed");return r.fulfill({status:409,json:{error:{message:"Bài làm đã thay đổi ở tab khác."}}});}
    return r.fulfill({json:attempt()});
  });
  await page.goto("/#/quiz?examId=8&attemptId=51");const text=page.getByRole("textbox",{name:"Bài làm",exact:true});await text.fill("Lời giải chưa lưu");await expect(page.getByRole("alert")).toContainText("Bài làm vẫn được giữ");
  expect(await page.evaluate(()=>sessionStorage.getItem("math11:quiz:17:51"))).toContain("Lời giải chưa lưu");
  await page.reload();await expect(text).toHaveValue("Lời giải chưa lưu");
  conflict=true;await page.getByRole("button",{name:"Lưu bài",exact:true}).click();await expect(page.getByRole("button",{name:"Tải bài mới nhất",exact:true})).toBeVisible();await expect(text).toHaveValue("Lời giải chưa lưu");await expect(text).toBeDisabled();const before=writes;await page.waitForTimeout(1000);expect(writes).toBe(before);
  page.on("dialog",d=>d.accept());await page.getByRole("button",{name:"Tải bài mới nhất",exact:true}).click();await expect(text).toHaveValue("Lời giải từ tab khác");
});

test("admin catalog form creates chapters and topics and displays protected-delete errors",async({page})=>{
  await fixture(page);await page.route("**/api/auth/me",r=>r.fulfill({json:{user:{...user,role:"ADMIN"},csrfToken:"session-csrf"}}));
  const chapters=[{id:1,title:"Chương hiện có",position:1}],topics:{id:number;title:string;chapterId:number;position:number}[]=[];let created=0;
  await page.route("**/api/chapters**",r=>{if(r.request().method()==="POST"){const body=r.request().postDataJSON();expect(body).toEqual({title:"Chương mới",position:2});chapters.push({id:2,...body});created++;return r.fulfill({json:{id:2,saved:true}});}return r.fulfill({json:chapters});});
  await page.route("**/api/topics**",r=>{if(r.request().method()==="DELETE")return r.fulfill({status:409,json:{error:{message:"Danh mục đang được sử dụng; không thể xóa."}}});if(r.request().method()==="POST"){const body=r.request().postDataJSON();expect(body).toEqual({title:"Chủ đề mới",chapterId:2,position:0});topics.push({id:3,...body});created++;return r.fulfill({json:{id:3,saved:true}});}return r.fulfill({json:topics});});
  page.on("dialog",d=>d.accept());await page.goto("/#/lessons");
  const panel=page.locator("section").filter({has:page.getByRole("heading",{name:"Quản lý chương và chủ đề",exact:true})});
  await panel.getByLabel("Tên",{exact:true}).fill("Chương mới");await panel.getByLabel("Thứ tự",{exact:true}).fill("2");await panel.getByRole("button",{name:"Lưu danh mục",exact:true}).click();await expect.poll(()=>created).toBe(1);
  await expect(panel.getByLabel("Danh mục",{exact:true})).toBeEnabled();await panel.getByLabel("Danh mục",{exact:true}).selectOption("topics");await panel.getByLabel("Tên",{exact:true}).fill("Chủ đề mới");await panel.getByLabel("Chương",{exact:true}).selectOption("2");await panel.getByLabel("Thứ tự",{exact:true}).fill("0");await panel.getByRole("button",{name:"Lưu danh mục",exact:true}).click();await expect.poll(()=>created).toBe(2);
  await expect(panel.getByRole("button",{name:"Lưu danh mục",exact:true})).toBeDisabled();await panel.getByLabel("Mục cần sửa",{exact:true}).selectOption("3");await panel.getByRole("button",{name:"Xóa danh mục",exact:true}).click();await expect(panel.getByRole("alert")).toContainText("đang được sử dụng");
});
