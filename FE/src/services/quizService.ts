import { historyService } from "./historyService";
import { useMockApi } from "./serviceMode";
export const quizService = {
  async recordSubmission(answer: string, startedAt: string, category: "Luyện tập" | "Kiểm tra" = "Kiểm tra") {
    if (!useMockApi) throw new Error("Sử dụng quizApiService để nộp và chấm bài trên máy chủ.");
    // Preserve the existing demo quiz. Its 13 prefilled answers are illustrative.
    const attempts = (await historyService.list()).filter(r => r.type === "MCQ" && r.title === "Hàm số lượng giác").length;
    await historyService.record({ title: "Hàm số lượng giác", type: "MCQ", category, attempt: attempts + 1, score: 8.5, status: "Hoàn thành", date: new Date().toISOString(), elapsedSeconds: Math.max(0, Math.round((Date.now() - Date.parse(startedAt)) / 1000)), detail: { prompt: "Cho y = sin(x). Giá trị lớn nhất của hàm số là?", answer: answer || "Chưa trả lời câu hiện tại", feedback: "Quiz demo gốc: điểm minh họa 8.5/10; đáp án đúng câu hiện tại là C. Chấm quiz đầy đủ cần backend." } });
  },
};
