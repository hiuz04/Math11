import "fake-indexeddb/auto";
import { test } from "node:test";
import assert from "node:assert/strict";
import { essayService } from "../src/services/essayService";
import { aiTutorService } from "../src/services/aiTutorService";
import { historyService } from "../src/services/historyService";
import { questionService } from "../src/services/questionService";
import { adminConfigService } from "../src/services/adminConfigService";
import { defaultPrompts } from "../src/constants/lms";
import { apiRequest, apiOperation, ApiError, setCsrfToken, clearApiOperations } from "../src/services/api";
import { quizApiService, quizClock, quizRemaining, type QuizAttempt } from "../src/services/quizApiService";
import { selection, resetSelection, canAccess } from "../src/services/routeState";
import { submitContent } from "../src/services/imageService";
import { validateQuestions } from "../src/services/questionValidation";
import { lmsService } from "../src/services/lmsService";
const settings = new Map<string, string>();
Object.defineProperty(globalThis, "localStorage", { value: { getItem: (key: string) => settings.get(key) ?? null, setItem: (key: string, value: string) => settings.set(key, value) }, configurable: true });

test("essay Tutor gates every step, keeps all attempts, requires a new image and persists history", async () => {
  const initial = await essayService.submit(101, { text: "u10 = 30", image: "original-image", imageHash: "old-hash" }, new Date().toISOString());
  let s = await aiTutorService.evaluateEssay(initial.id);
  assert.equal(s.status, "NEEDS_TUTOR");
  s = await aiTutorService.startTutor(s.id);
  assert.equal(s.aiSteps.length, 1);
  assert.doesNotMatch(s.aiSteps[0].instruction, /29/);
  await assert.rejects(aiTutorService.nextStep(s.id));
  s = await aiTutorService.checkStep(s.id, "2+(10-1)*4");
  assert.equal(s.aiSteps[0].correct, false);
  assert.doesNotMatch(s.aiSteps[0].hint!, /29/);
  assert.equal(s.aiSteps.length, 1);
  s = await aiTutorService.checkStep(s.id, "2 + (10 - 1) × 3");
  assert.equal(s.aiSteps[0].correct, true);
  assert.equal(s.aiSteps.length, 1, "must wait for Continue");
  await assert.rejects(aiTutorService.evaluateRetry(s.id, { image: "new", imageHash: "new" }));
  s = await aiTutorService.nextStep(s.id);
  s = await aiTutorService.checkStep(s.id, "27");
  s = await aiTutorService.nextStep(s.id);
  s = await aiTutorService.checkStep(s.id, "29");
  s = await aiTutorService.nextStep(s.id);
  assert.equal(s.status, "RETRY_REQUIRED");
  await assert.rejects(aiTutorService.evaluateRetry(s.id, { text: "29" }));
  await assert.rejects(aiTutorService.evaluateRetry(s.id, { image: "renamed", imageHash: "old-hash" }));
  s = await aiTutorService.evaluateRetry(s.id, { image: "new-image", imageName: "retry.png", imageHash: "new-hash" });
  assert.equal(s.status, "NEEDS_REVISION", "mock must not invent OCR grading");
  assert.equal(s.retrySubmissions.length, 1);
  assert.equal(s.aiSteps[0].attempts.length, 2);
  assert.equal((await essayService.get(s.id)).retrySubmission?.image, "new-image");
  await assert.rejects(aiTutorService.evaluateRetry(s.id, { image: "same", imageHash: "new-hash" }));
  const rows = (await historyService.list()).filter(r => r.submissionId === s.id);
  assert.equal(rows.length, 3);
  assert.ok(rows.some(r => r.type === "AI_TUTOR"));
  assert.ok(rows.some(r => r.attempt === 2));
});

test("correct original answer skips Tutor and second essay uses its own plan", async () => {
  let s = await essayService.submit(101, { text: "u10 = 29" }, new Date().toISOString());
  s = await aiTutorService.evaluateEssay(s.id);
  assert.equal(s.status, "CORRECT");
  assert.equal(s.finalResult?.score, 10);
  await assert.rejects(aiTutorService.startTutor(s.id));
  s = await essayService.submit(102, { text: "40" }, new Date().toISOString());
  s = await aiTutorService.evaluateEssay(s.id);
  s = await aiTutorService.startTutor(s.id);
  s = await aiTutorService.checkStep(s.id, "3*2^(5-1)");
  assert.equal(s.aiSteps[0].correct, true);
});

test("generation honors counts, question type and cognitive level; exam matrix persists", async () => {
  const questions = await questionService.generate({ chapter: "Chương 2 – Dãy số", topic: "Cấp số cộng", questionType: "ESSAY", cognitiveLevel: "APPLICATION", count: 7, difficulty: "HARD" });
  assert.equal(questions.length, 7);
  assert.ok(questions.every(q => q.cognitiveLevel === "APPLICATION" && q.type === "ESSAY" && q.difficulty === "HARD"));
  assert.equal(new Set(questions.map(q => q.content)).size, 7);
  await questionService.save(questions);
  assert.ok((await questionService.list()).some(q => q.id === questions[0].id));
  const exam = await questionService.generateExam("Chương 2 – Dãy số", "Cấp số cộng", { KNOWLEDGE: { MCQ: 5, ESSAY: 1 }, UNDERSTANDING: { MCQ: 4, ESSAY: 1 }, APPLICATION: { MCQ: 3, ESSAY: 2 } });
  assert.equal(exam.questions.length, 16);
  assert.equal(exam.questions.filter(q => q.type === "ESSAY").length, 4);
  assert.equal(new Set(exam.questions.map(q => q.id)).size, 16);
  await questionService.saveExam(exam);
  assert.equal((await questionService.listExams())[0].questions.length, 16);
  await assert.rejects(questionService.generate({ chapter: "x", topic: "x", questionType: "MCQ", cognitiveLevel: "KNOWLEDGE", count: 0, difficulty: "EASY" }));
});

test("admin validation, disabled Gemini, prompt persistence and no secret storage", async () => {
  const config = await adminConfigService.getGemini();
  await assert.rejects(adminConfigService.saveGemini({ ...config, temperature: 3 }));
  await adminConfigService.saveGemini({ ...config, enabled: false }, "test-secret-never-persist");
  assert.equal((await adminConfigService.getGemini()).enabled, false);
  assert.doesNotMatch([...settings.values()].join(""), /test-secret-never-persist/);
  assert.equal((await adminConfigService.testConnection({ ...config, enabled: false })).ok, false);
  await assert.rejects(aiTutorService.startTutor(123), /đang tắt/);
  await adminConfigService.saveGemini(config);
  assert.equal((await adminConfigService.testConnection(config)).ok, false, "mock must not invent a successful connection");
  await adminConfigService.savePrompts({ ...defaultPrompts, essayEvaluation: "Custom evaluation" });
  assert.equal((await adminConfigService.getPrompts()).essayEvaluation, "Custom evaluation");
  await assert.rejects(adminConfigService.savePrompts({ ...defaultPrompts, system: "" }));
});

test("multipart keeps browser boundary and cookie/CSRF; JSON keeps its media type", async () => {
  const original = globalThis.fetch; setCsrfToken("token");
  const requests: RequestInit[] = [];
  globalThis.fetch = async (_url, options) => { requests.push(options!); return Response.json({ ok: true }); };
  try {
    const imageFile = new File(["bytes"], "answer.png", { type: "image/png" });
    await submitContent("essays", { questionId: 7 }, { text: "bài làm", image: "blob:preview", imageFile, imageHash: "sha256" });
    const multipart = requests[0], headers = new Headers(multipart.headers);
    assert.equal(headers.has("Content-Type"), false);
    assert.equal(headers.get("X-CSRF-Token"), "token");
    assert.equal(multipart.credentials, "include");
    assert.ok(multipart.body instanceof FormData);
    assert.match(String(multipart.body.get("payload")), /bài làm/);
    assert.doesNotMatch(String(multipart.body.get("payload")), /blob:|data:image/);
    await apiRequest("test", { method: "POST", body: "{}" });
    assert.equal(new Headers(requests[1].headers).get("Content-Type"), "application/json");
    await apiRequest("test", { method: "DELETE" });
    assert.equal(new Headers(requests[2].headers).has("Content-Type"), false);
  } finally { globalThis.fetch = original; clearApiOperations(); }
});

test("HTTP errors have safe Vietnamese messages and stable explicit retry keys", async () => {
  const original = globalThis.fetch; setCsrfToken("token");
  try {
    for (const status of [400, 401, 403, 404, 409, 413, 422, 429, 500]) {
      globalThis.fetch = async () => new Response("<html>private stack trace</html>", { status });
      await assert.rejects(apiRequest("test"), (e: ApiError) => e.status === status && !e.message.includes("private"));
    }
    globalThis.fetch = async () => new Response("<html>proxy returned a page with HTTP 200</html>");
    await assert.rejects(apiRequest("test"), (e: ApiError) => e.status === 502 && !e.message.includes("<html>"));
    setCsrfToken("token");
    const keys: string[] = []; let calls = 0;
    globalThis.fetch = async (_url, options) => { keys.push(new Headers(options?.headers).get("Idempotency-Key")!); if (++calls === 1) throw new TypeError("offline"); return Response.json({ saved: true }); };
    await assert.rejects(apiOperation("essay-test", "same operation", { method: "POST", body: "{}" }), (e: ApiError) => e.kind === "network");
    assert.equal(calls, 1, "mutations must never be automatically retried");
    await apiOperation("essay-test", "same operation", { method: "POST", body: "{}" });
    assert.equal(keys[0], keys[1]);
    const abort = new AbortController(); abort.abort();
    await assert.rejects(apiRequest("test", { signal: abort.signal }), { name: "AbortError" });
    assert.equal(calls, 2);
  } finally { globalThis.fetch = original; clearApiOperations(); }
});

test("session changes discard stale CSRF and response bodies; body cancellation stays AbortError", async () => {
  const original = globalThis.fetch;
  clearApiOperations(); setCsrfToken("");
  try {
    let resolveCsrf!: (response: Response) => void;
    const sentTokens: string[] = [];
    let csrfCalls = 0;
    globalThis.fetch = async (url, options) => {
      if (String(url).endsWith("auth/csrf")) {
        if (++csrfCalls === 1) return new Promise<Response>(resolve => { resolveCsrf = resolve; });
        return Response.json({ csrfToken: "new-session-token" });
      }
      sentTokens.push(new Headers(options?.headers).get("X-CSRF-Token")!);
      return Response.json({ saved: true });
    };
    const oldRequest = apiRequest("test", { method: "POST", body: "{}" });
    const rejectedOld = assert.rejects(oldRequest, { name: "AbortError" });
    clearApiOperations(); setCsrfToken("");
    await apiRequest("test", { method: "POST", body: "{}" });
    resolveCsrf(Response.json({ csrfToken: "stale-token" }));
    await rejectedOld;
    await apiRequest("test", { method: "POST", body: "{}" });
    assert.deepEqual(sentTokens, ["new-session-token", "new-session-token"]);
    assert.equal(csrfCalls, 2);

    let resolveBody!: (value: unknown) => void;
    let startedBody!: () => void;
    const bodyStarted = new Promise<void>(resolve => { startedBody = resolve; });
    const delayedResponse = Response.json({});
    delayedResponse.json = () => { startedBody(); return new Promise(resolve => { resolveBody = resolve; }); };
    globalThis.fetch = async () => delayedResponse;
    const oldBody = apiRequest("test");
    const rejectedBody = assert.rejects(oldBody, { name: "AbortError" });
    await bodyStarted;
    clearApiOperations();
    resolveBody({ private: "previous session" });
    await rejectedBody;

    const cancelled = Response.json({});
    cancelled.json = async () => { throw new DOMException("cancelled", "AbortError"); };
    globalThis.fetch = async () => cancelled;
    await assert.rejects(apiRequest("test"), { name: "AbortError" });
  } finally { globalThis.fetch = original; clearApiOperations(); setCsrfToken(""); }
});

test("quiz resumes by ID without creating attempts and simultaneous starts share one creation", async () => {
  const original = globalThis.fetch; resetSelection(); setCsrfToken("token");
  const attempt = { id: 12, examId: 7, status: "IN_PROGRESS", startedAt: new Date().toISOString(), elapsedSeconds: 0, exam: { title: "Quiz", durationSeconds: 1800 }, questions: [], answers: {} }; const urls: string[] = [];
  globalThis.fetch = async (url, options) => { urls.push(`${options?.method || "GET"} ${url}`); return Response.json(String(url).endsWith("quiz-attempts") ? [attempt] : attempt); };
  try {
    selection.examId = 7;
    assert.equal((await quizApiService.start()).id, 12);
    assert.equal(urls.some(url => url.startsWith("POST")), false);
    urls.length = 0;
    await quizApiService.start();
    assert.deepEqual(urls, ["GET /api/quiz-attempts/12"]);
    resetSelection(); selection.examId = 7; urls.length = 0;
    globalThis.fetch = async (url, options) => { urls.push(`${options?.method || "GET"} ${url}`); return Response.json(String(url).endsWith("quiz-attempts") ? [] : attempt); };
    await Promise.all([quizApiService.start(), quizApiService.start()]);
    assert.equal(urls.filter(url => url.startsWith("POST")).length, 1);
  } finally { resetSelection(); globalThis.fetch = original; clearApiOperations(); }
});

test("server deadline survives refresh and client clock skew; route roles are allowlisted", () => {
  const attempt = { startedAt: "2026-10-26T00:00:00Z", deadlineAt: "2026-10-26T00:30:00Z", serverTime: "2026-10-26T00:29:50Z", elapsedSeconds: 1790, exam: { durationSeconds: 1800 } } as QuizAttempt;
  const receivedAt = Date.parse("2030-01-01T00:00:00Z");
  const clock = quizClock(attempt, receivedAt);
  assert.equal(quizRemaining(clock, receivedAt), 10);
  assert.equal(quizRemaining(clock, receivedAt + 11_000), 0);
  assert.equal(quizRemaining(quizClock(attempt, receivedAt + 100_000), receivedAt + 100_000), 10);
  for (const route of ["users", "admin", "teacher", "create-lesson", "student-detail", "generator", "system", "unknown"]) assert.equal(canAccess(route, "Học sinh"), false);
  assert.equal(canAccess("users", "Giáo viên"), false);
  assert.equal(canAccess("generator", "Giáo viên"), true);
  assert.equal(canAccess("system", "Quản trị viên"), true);
});

test("question validation rejects empty essays, invalid MCQs and duplicate generation", () => {
  const q = { id: 1, title: "Tính", content: "1+1", chapter: "Chương", topic: "Chủ đề", type: "MCQ", difficulty: "EASY", cognitiveLevel: "KNOWLEDGE", options: ["1", "2"], answer: "2" } as const;
  const valid = { ...q, options: [...q.options] };
  assert.doesNotThrow(() => validateQuestions([valid]));
  assert.throws(() => validateQuestions([{ ...valid, options: ["1", "1"] }]));
  assert.throws(() => validateQuestions([{ ...valid, type: "ESSAY", content: "<p></p>" }]));
  assert.throws(() => validateQuestions([valid, { ...valid, id: 2 }]));
});

test("late mutation responses from a cleared session cannot replace a new session operation", async () => {
  const original = globalThis.fetch; setCsrfToken("token"); clearApiOperations();
  const replies: ((response: Response) => void)[] = [];
  globalThis.fetch = async () => new Promise<Response>(resolve => replies.push(resolve));
  try {
    const first = apiOperation("session-test", "same", { method: "POST", body: "{}" });
    const rejected = assert.rejects(first, { name: "AbortError" });
    await new Promise(resolve => setImmediate(resolve));
    clearApiOperations();
    const second = apiOperation("session-test", "same", { method: "POST", body: "{}" });
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(replies.length, 2);
    replies[0](Response.json({ session: "old" })); await rejected;
    const duplicate = apiOperation("session-test", "same", { method: "POST", body: "{}" });
    assert.equal(replies.length, 2);
    replies[1](Response.json({ session: "new" }));
    assert.deepEqual(await second, { session: "new" });
    assert.deepEqual(await duplicate, { session: "new" });
  } finally { globalThis.fetch = original; clearApiOperations(); }
});

test("lesson adapter normalizes the Servlet integer progress DTO and rejects missing student IDs", async () => {
  const original = globalThis.fetch;
  try {
    globalThis.fetch = async () => Response.json({id:7,completed:0});
    assert.equal((await lmsService.lesson(7)).completed, false);
    globalThis.fetch = async () => Response.json({id:7,completed:1});
    assert.equal((await lmsService.lesson(7)).completed, true);
    globalThis.fetch = async () => Response.json({id:7,completed:"unknown"});
    await assert.rejects(lmsService.lesson(7), /tiến độ/);
    await assert.rejects(lmsService.studentAnalytics(0), /chọn học sinh/);
  } finally { globalThis.fetch = original; }
});
