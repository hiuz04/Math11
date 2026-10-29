import type { HistoryEntry } from "../types/lms";
import { readRecords, writeRecord } from "./storage";
import { essayService } from "./essayService";
import { apiRequest } from "./api";
import { postApi, useMockApi } from "./serviceMode";
export const essayStatusLabels = { SUBMITTED: "Đã nộp", CORRECT: "Hoàn thành", NEEDS_TUTOR: "Đang hướng dẫn", RETRY_REQUIRED: "Cần nộp lại", COMPLETED: "Hoàn thành", NEEDS_REVISION: "Cần chỉnh sửa" };
export const historyService = {
  async record(entry: Omit<HistoryEntry, "id">): Promise<void> {
    if (!useMockApi) return postApi("learning-history", entry);
    await writeRecord({ id: "history:" + crypto.randomUUID(), kind: "history", value: entry });
  },
  async list(): Promise<HistoryEntry[]> {
    if (!useMockApi) return apiRequest("learning-history");
    const records = await readRecords<{ id: string; kind: string; value: HistoryEntry }>();
    const rows = records.filter(r => r.kind === "history").map(r => ({ ...r.value, id: r.id }));
    const essays = await essayService.list();
    for (const s of essays) {
      const base = { title: s.question.title, submissionId: s.id, elapsedSeconds: s.elapsedSeconds };
      rows.push({ ...base, id: `essay:${s.id}`, type: "ESSAY", category: "Luyện tập", attempt: 1, score: !s.retrySubmissions.length ? s.finalResult?.score : undefined, status: essayStatusLabels[s.status], date: s.startedAt });
      if (s.aiSteps.length) rows.push({ ...base, id: `tutor:${s.id}`, type: "AI_TUTOR", category: "AI Tutor", attempt: 1, status: s.status === "NEEDS_TUTOR" ? "Đang hướng dẫn" : "Đã hướng dẫn", date: s.updatedAt });
      s.retrySubmissions.forEach((r, index) => rows.push({ ...base, id: `retry:${s.id}:${index}`, type: "ESSAY", category: "Luyện tập", attempt: index + 2, score: r.result.score, status: r.result.correct ? "Hoàn thành" : "Cần chỉnh sửa", date: r.date }));
    }
    return rows.sort((a, b) => Date.parse(b.date) - Date.parse(a.date));
  },
};
