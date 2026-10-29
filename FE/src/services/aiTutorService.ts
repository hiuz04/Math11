import type { AITutorStep, EssaySubmission, Evaluation, SubmissionContent } from "../types/lms";
import { essayService } from "./essayService";
import { assertMockAiEnabled, postApi, useMockApi } from "./serviceMode";
import { submitContent } from "./imageService";
// Private mock plans. UI only receives the current unlocked step, never future answers.
const plans = {
  ARITHMETIC: [
    { instruction: "Hãy thay u₁, n và d của đề bài vào công thức, chưa cần tính đáp số.", formula: "u_n = u_1 + (n-1)d", check: (s: string) => /2\+\(?10-1\)?[*×·]?3/.test(s), hint: "Em hãy kiểm tra giá trị n và n − 1; giữ nguyên phép tính." },
    { instruction: "Tính phần tăng thêm: (10 − 1) × 3. Nhập kết quả của phép tính này.", formula: "(n-1)d", check: (s: string) => /^(?:.*=)?27$/.test(s), hint: "Hãy thực hiện phép trừ trong ngoặc trước rồi mới nhân với công sai." },
    { instruction: "Cộng số hạng đầu với phần tăng thêm. Em hãy tự nhập giá trị u₁₀.", formula: "u_{10} = u_1 + (10-1)d", check: (s: string) => /^(?:.*=)?29$/.test(s), hint: "Đừng quên cộng số hạng đầu vào phần tăng thêm em vừa tính." },
  ],
  GEOMETRIC: [
    { instruction: "Thay số ghế hàng đầu, công bội và số thứ tự hàng cần tìm vào công thức. Chưa tính kết quả cuối.", formula: "u_n = u_1 q^{n-1}", check: (s: string) => /3[*×·]?2\^\(?5-1\)?/.test(s), hint: "Số ghế gấp đôi nghĩa là công bội bằng 2. Số mũ bằng số thứ tự hàng trừ 1." },
    { instruction: "Tính hệ số tăng: 2 mũ (5 − 1). Nhập kết quả của lũy thừa.", formula: "q^{n-1}", check: (s: string) => /^(?:.*=)?16$/.test(s), hint: "Tính số mũ trước rồi nhân số 2 với chính nó đúng số lần." },
    { instruction: "Nhân số ghế hàng đầu với hệ số tăng vừa tính. Em hãy tự nhập số ghế ở hàng thứ 5.", formula: "u_5 = u_1 q^4", check: (s: string) => /^(?:.*=)?48$/.test(s), hint: "Nhân số ghế hàng đầu với hệ số tăng, không cộng hai giá trị." },
  ],
};
function plan(s: EssaySubmission) { return plans[s.question.scenario || "ARITHMETIC"]; }
function stepFor(s: EssaySubmission, index: number): AITutorStep {
  const next = plan(s)[index];
  return { id: index + 1, stepNumber: index + 1, instruction: next.instruction, formula: next.formula, correct: false, attempts: [] };
}
function update(s: EssaySubmission) {
  s.updatedAt = new Date().toISOString();
  s.elapsedSeconds = Math.max(0, Math.round((Date.now() - Date.parse(s.startedAt)) / 1000));
  return essayService.save(s);
}
function evaluateText(s: EssaySubmission): Evaluation {
  if (s.originalSubmission.image && !s.originalSubmission.text?.trim()) return { correct: false, feedback: "Đã nhận ảnh thật. Chế độ mock chưa đọc/chấm được ảnh; em có thể luyện các bước của bài này. Cần backend để đánh giá ảnh." };
  const text = (s.originalSubmission.text || "").trim();
  const expected = s.question.scenario === "GEOMETRIC" ? "48" : "29";
  const correct = new RegExp(`(?:^|[=\\s])${expected}(?:\\s*(?:ghế|\\.)?\\s*)$`).test(text);
  return correct ? { correct: true, feedback: "Chính xác theo kiểm tra đáp số mẫu. Chế độ mock chưa đánh giá đầy đủ lập luận.", score: 10 } : { correct: false, feedback: "Kết quả chưa khớp với bài mẫu. Hãy kiểm tra lại cách thay dữ kiện; Tutor sẽ giúp em từng bước." };
}
export const aiTutorService = {
  async evaluateEssay(id: number): Promise<EssaySubmission> {
    assertMockAiEnabled();
    if (!useMockApi) return postApi("ai/essays/evaluate", { submissionId: id });
    const s = await essayService.get(id);
    if (s.status !== "SUBMITTED") return s;
    const result = evaluateText(s); s.originalFeedback = result.feedback;
    s.status = result.correct ? "CORRECT" : "NEEDS_TUTOR";
    if (result.correct) s.finalResult = result;
    return update(s);
  },
  async startTutor(id: number): Promise<EssaySubmission> {
    assertMockAiEnabled();
    if (!useMockApi) return postApi("ai/tutor/start", { submissionId: id });
    const s = await essayService.get(id);
    if (s.status !== "NEEDS_TUTOR") throw new Error("Bài làm chưa cần Tutor hoặc đã hoàn thành.");
    if (!s.aiSteps.length) s.aiSteps.push(stepFor(s, 0));
    s.totalSteps = plan(s).length;
    return update(s);
  },
  async checkStep(id: number, answer: string, stepNumber?: number): Promise<EssaySubmission> {
    assertMockAiEnabled();
    if (!answer.trim()) throw new Error("Hãy nhập câu trả lời của em.");
    if (!useMockApi) { const current = stepNumber ?? (await essayService.get(id)).currentStep + 1; return postApi("ai/tutor/check-step", { submissionId: id, answer, stepNumber: current }); }
    const s = await essayService.get(id); const step = s.aiSteps[s.currentStep];
    if (s.status !== "NEEDS_TUTOR" || !step || step.correct) throw new Error("Bước này chưa mở hoặc đã trả lời đúng.");
    const definition = plan(s)[s.currentStep];
    const normalized = answer.toLowerCase().replace(/\s/g, "").replace(/−/g, "-").replace(/\*\*/g, "^");
    step.correct = definition.check(normalized); step.studentAnswer = answer;
    step.hint = step.correct ? undefined : definition.hint;
    step.attempts.push({ answer, correct: step.correct, hint: step.hint, date: new Date().toISOString() });
    s.studentStepAnswers ??= [];
    s.studentStepAnswers.push({ ...step.attempts[step.attempts.length - 1], stepId: step.id });
    return update(s);
  },
  async nextStep(id: number, stepNumber?: number): Promise<EssaySubmission> {
    assertMockAiEnabled();
    if (!useMockApi) { const current = stepNumber ?? (await essayService.get(id)).currentStep + 1; return postApi("ai/tutor/next-step", { submissionId: id, stepNumber: current }); }
    const s = await essayService.get(id);
    if (s.status !== "NEEDS_TUTOR" || !s.aiSteps[s.currentStep]?.correct) throw new Error("Em cần trả lời đúng bước hiện tại trước.");
    if (s.currentStep + 1 === plan(s).length) s.status = "RETRY_REQUIRED";
    else { s.currentStep++; s.aiSteps.push(stepFor(s, s.currentStep)); }
    return update(s);
  },
  async evaluateRetry(id: number, content: SubmissionContent): Promise<EssaySubmission> {
    assertMockAiEnabled();
    if (!content.image || !content.imageHash) throw new Error("Hãy chụp hoặc upload ảnh bài làm mới.");
    if (!useMockApi) return submitContent("ai/essays/evaluate-retry", { submissionId: id }, content);
    const s = await essayService.get(id);
    if (!["RETRY_REQUIRED", "NEEDS_REVISION"].includes(s.status)) throw new Error("Hãy hoàn thành các bước Tutor trước khi nộp lại.");
    if (s.originalSubmission.imageHash === content.imageHash || s.retrySubmissions.some(r => r.content.imageHash === content.imageHash)) throw new Error("Ảnh đã được sử dụng. Hãy chụp ảnh bài làm mới.");
    const result: Evaluation = { correct: false, feedback: "Đã lưu ảnh nộp lại. Chế độ mock chưa có OCR nên chưa thể xác nhận đúng/sai hoặc cho điểm. Cần Java Servlet để chấm ảnh." };
    s.retrySubmission = content; s.finalResult = result;
    s.retrySubmissions.push({ content, result, date: new Date().toISOString() }); s.status = "NEEDS_REVISION";
    return update(s);
  },
};
