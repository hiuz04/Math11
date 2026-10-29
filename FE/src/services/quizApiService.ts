import { apiRequest, apiOperation } from "./api";
import { selection } from "./lmsService";
import { selectionGeneration } from "./routeState";
import { submitContent } from "./imageService";
import type { StudentQuestionDTO, QuizReviewDTO, SubmissionContent } from "../types/lms";
export interface QuizAttempt {
  id: number; examId: number; status: "IN_PROGRESS" | "SUBMITTED" | "EXPIRED" | "PENDING_GRADING" | "GRADED";
  startedAt: string; deadlineAt?: string; serverTime?: string; expired?: boolean; elapsedSeconds: number;
  exam: { title: string; durationSeconds: number }; questions: StudentQuestionDTO[];
  answers: Record<string, string>; result?: QuizReviewDTO;
  essayAnswers?: Record<string, { content: SubmissionContent }>;
}
type AttemptSummary = Pick<QuizAttempt, "id" | "examId" | "status">;
type EssayAnswers = NonNullable<QuizAttempt["essayAnswers"]>;
function serializeEssays(essays: EssayAnswers) {
  return Object.fromEntries(Object.entries(essays).map(([id, { content }]) => {
    const { imageFile, image, ...metadata } = content;
    return [id, { content: { ...metadata, ...(!imageFile ? { image: image || null } : {}) } }];
  }));
}
const starts = new Map<string, Promise<QuizAttempt>>();
function validateAttempt(attempt: QuizAttempt): QuizAttempt {
  if (!attempt || !Number.isSafeInteger(attempt.id) || !Number.isSafeInteger(attempt.examId) || !Array.isArray(attempt.questions) || !attempt.answers || typeof attempt.answers !== "object" || !attempt.exam || !Number.isFinite(attempt.exam.durationSeconds) || !Number.isFinite(Date.parse(attempt.startedAt))) throw new Error("Máy chủ trả dữ liệu bài thi không hợp lệ.");
  if (attempt.questions.some(q => ["answer", "explanation", "rubric", "correct", "score"].some(field => field in q)) || attempt.status === "IN_PROGRESS" && attempt.result) throw new Error("Dữ liệu bài thi vi phạm hợp đồng bảo vệ đáp án. Vui lòng liên hệ giáo viên.");
  return attempt;
}
export const quizApiService = {
  async start(): Promise<QuizAttempt> {
    const examId = selection.examId, attemptId = selection.attemptId;
    const generation = selectionGeneration();
    const key = `${generation}:${examId}:${attemptId}`;
    const existing = starts.get(key); if (existing) return existing;
    const pending = (async () => {
      if (attemptId) return apiRequest<QuizAttempt>(`quiz-attempts/${attemptId}`);
      const attempts = await apiRequest<AttemptSummary[]>("quiz-attempts");
      const open = attempts.find(a => a.status === "IN_PROGRESS" && (!examId || a.examId === examId));
      if (open) return apiRequest<QuizAttempt>(`quiz-attempts/${open.id}`);
      let selected = examId;
      if (!selected) {
        const exams = await apiRequest<{ id: number; questions: StudentQuestionDTO[] }[]>("exams");
        const exam = exams.find(e => e.questions.length && e.questions.every(q => q.type === "MCQ"));
        if (!exam) throw new Error("Chưa có đề trắc nghiệm được xuất bản.");
        selected = exam.id;
      }
      return apiOperation<QuizAttempt>(`exams/${selected}/attempts`, "start", { method: "POST", body: "{}" });
    })().then(validateAttempt).then(attempt => {
      if (generation !== selectionGeneration() || selection.examId !== examId || selection.attemptId !== attemptId) throw new DOMException("Yêu cầu bài thi đã được thay thế.", "AbortError");
      selection.examId = attempt.examId; selection.attemptId = attempt.id; return attempt;
    }).finally(() => starts.delete(key));
    starts.set(key, pending);
    return pending;
  },
  async get(): Promise<QuizAttempt> {
    if (!selection.attemptId) throw new Error("Hãy chọn một bài làm trong lịch sử học tập.");
    return apiRequest<QuizAttempt>(`quiz-attempts/${selection.attemptId}`).then(validateAttempt);
  },
  save(id: number, answers: Record<string, string>, submit = false, essayAnswers?: Record<string, { content: SubmissionContent }>) {
    const body = JSON.stringify({ answers, ...(essayAnswers ? { essayAnswers: serializeEssays(essayAnswers) } : {}) });
    return apiOperation<QuizAttempt>(`quiz-attempts/${id}/${submit ? "submit" : "answers"}`, body, { method: "POST", body }).then(validateAttempt);
  },
  saveImage(id: number, questionId: number, answers: Record<string, string>, essays: EssayAnswers) {
    const content = essays[questionId]?.content;
    if (!content?.imageFile) throw new Error("Hãy chọn ảnh bài làm cần lưu.");
    return submitContent<QuizAttempt>(`quiz-attempts/${id}/answers`, { questionId, answers, essayAnswers: serializeEssays(essays) }, content).then(validateAttempt);
  },
};
export function quizClock(attempt: QuizAttempt, receivedAt: number) {
  const start = Date.parse(attempt.startedAt);
  const deadline = Date.parse(attempt.deadlineAt || "") || start + attempt.exam.durationSeconds * 1000;
  const server = Date.parse(attempt.serverTime || "") || start + attempt.elapsedSeconds * 1000;
  return { deadline, offset: server - receivedAt };
}
export const quizRemaining = (clock: { deadline: number; offset: number }, now: number) => Math.max(0, Math.ceil((clock.deadline - now - clock.offset) / 1000));
