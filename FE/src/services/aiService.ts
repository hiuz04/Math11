import { historyService } from "./historyService";
import { assertMockAiEnabled, postApi, useMockApi } from "./serviceMode";
import { apiRequest } from "./api";
import { submitContent } from "./imageService";
import type { SubmissionContent } from "../types/lms";
let conversationId = 0;
let conversationGeneration = 0;
export interface ChatMessage { id: number; role: "user" | "model"; content: string; createdAt: string }
// Backend owns prompt application and all real Gemini calls.
export const aiService = {
  currentConversation: () => conversationId,
  newConversation: () => { conversationGeneration++; conversationId = 0; },
  conversations: () => apiRequest<{ id: number; createdAt: string }[]>("ai/conversations"),
  async openConversation(id: number) { const generation = ++conversationGeneration; const messages = await apiRequest<ChatMessage[]>(`ai/conversations/${id}`); if (generation !== conversationGeneration) throw new DOMException("Cuộc trò chuyện đã thay đổi.", "AbortError"); conversationId = id; return messages; },
  async chat(message: string, attachment?: string | SubmissionContent): Promise<string> {
    const generation = conversationGeneration;
    const image = typeof attachment === "string" ? attachment : attachment?.image;
    assertMockAiEnabled();
    if (!message.trim() && !image) throw new Error("Hãy nhập câu hỏi hoặc chọn ảnh.");
    if (!useMockApi) { const result = typeof attachment === "object" && attachment.imageFile
      ? await submitContent<{ reply: string; conversationId: number }>("ai/chat", { message, conversationId }, attachment)
      : await postApi<{ reply: string; conversationId: number }>("ai/chat", { message, image, conversationId }); if (generation !== conversationGeneration) throw new DOMException("Cuộc trò chuyện đã thay đổi.", "AbortError"); conversationId = result.conversationId; return result.reply; }
    const reply = image ? "Đã nhận ảnh. Mock chưa đọc được ảnh; em hãy nhập đề bài để luyện tập. Bài tự luận có Tutor từng bước nằm trong mục Bài tập." : /π\/3|π\/2/.test(message) ? "Hãy kiểm tra lại giá trị sin của góc em chọn. Gợi ý: sin dương ở góc phần tư I và II." : "Trước tiên, em hãy xác định dữ kiện và công thức cần sử dụng. Mock hỗ trợ luồng Tutor từng bước cho cấp số cộng/cấp số nhân trong mục Bài tập.";
    await historyService.record({ title: message.slice(0, 80) || "Trao đổi bằng ảnh", type: "AI_TUTOR", category: "AI Tutor", attempt: 1, status: "Đã trao đổi", date: new Date().toISOString(), elapsedSeconds: 0, detail: { prompt: message || "Ảnh bài toán", answer: message, feedback: reply, image } });
    return reply;
  },
};
