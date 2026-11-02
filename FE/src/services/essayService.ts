import { allPages } from "./pagination";
import { essayQuestions } from "../constants/lms";
import type { EssaySubmission, SubmissionContent } from "../types/lms";
import { readRecords, writeRecord } from "./storage";
import { apiOperation, apiRequest } from "./api";
import { postApi, useMockApi } from "./serviceMode";
import { createMockId } from "./id";
import { submitContent } from "./imageService";
type StoredEssay = { id: string; kind: "essay"; value: EssaySubmission };
export const essayService = {
  grade(id: number, score: number, feedback: string): Promise<EssaySubmission> {
    if (!Number.isFinite(score) || score < 0 || score > 10) throw new Error("Điểm phải từ 0 đến 10.");
    const body = JSON.stringify({ score, feedback });
    return apiOperation(`essays/${id}/grade`, body, { method: "POST", body });
  },
  getQuestion: (id: number) => essayQuestions.find(q => q.id === id),
  async getQuestionById(id: number) {
    if (!useMockApi) return apiRequest<import("../types/lms").Question>(`questions/${id}`);
    const question = this.getQuestion(id);
    if (!question) throw new Error("Bài tự luận không tồn tại.");
    return question;
  },
  async list(): Promise<EssaySubmission[]> {
    if (!useMockApi) return allPages<EssaySubmission>("essays");
    return (await readRecords<StoredEssay>()).filter(r => r.kind === "essay").map(r => r.value);
  },
  async get(id: number): Promise<EssaySubmission> {
    if (!useMockApi) return apiRequest(`essays/${id}`);
    const item = (await this.list()).find(s => s.id === id);
    if (!item) throw new Error("Không tìm thấy bài làm.");
    return item;
  },
  async save(submission: EssaySubmission): Promise<EssaySubmission> {
    if (!useMockApi) return postApi(`essays/${submission.id}`, submission);
    await writeRecord<StoredEssay>({ id: "essay:" + submission.id, kind: "essay", value: submission });
    return submission;
  },
  async submit(questionId: number, content: SubmissionContent, startedAt: string): Promise<EssaySubmission> {
    if (!content.text?.trim() && !content.image) throw new Error("Hãy nhập bài làm hoặc chọn ảnh.");
    if (!useMockApi) return submitContent("essays", { questionId, startedAt }, content);
    const question = this.getQuestion(questionId);
    if (!question) throw new Error("Bài tự luận không tồn tại.");
    return this.save({ id: createMockId(), studentId: 1, questionId, question, originalSubmission: content, originalFeedback: "", status: "SUBMITTED", aiSteps: [], studentStepAnswers: [], totalSteps: 0, currentStep: 0, retrySubmissions: [], startedAt, updatedAt: new Date().toISOString(), elapsedSeconds: Math.max(0, Math.round((Date.now() - Date.parse(startedAt)) / 1000)) });
  },
};
