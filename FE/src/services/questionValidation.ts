import type { Question } from "../types/lms";
const hasContent = (value?: string) => Boolean(value?.replace(/<[^>]*>/g, "").replace(/&nbsp;/g, " ").trim()) || Boolean(value?.includes("data-latex"));
export function validateQuestion(question: Question) {
  if (!hasContent(question.title) || !hasContent(question.content) || !question.chapter || !question.topic) throw new Error("Hãy nhập tên, nội dung, chương và chủ đề của câu hỏi.");
  if (!hasContent(question.answer)) throw new Error("Hãy nhập đáp án hoặc hướng dẫn chấm.");
  if (question.type === "MCQ") {
    const options = question.options || [];
    if (options.length < 2 || options.length > 8 || options.some(o => !hasContent(o)) || new Set(options.map(o => o.trim())).size !== options.length || !options.includes(question.answer || "")) throw new Error("Câu trắc nghiệm cần 2–8 lựa chọn khác nhau và đáp án khớp một lựa chọn.");
  }
}
export function validateQuestions(questions: Question[]) {
  if (!questions.length || questions.length > 100) throw new Error("Danh sách cần từ 1 đến 100 câu hỏi.");
  const seen = new Set<string>();
  for (const q of questions) { validateQuestion(q); const key = `${q.type}:${q.content.trim()}`; if (seen.has(key)) throw new Error("Có câu hỏi trùng nội dung trong danh sách."); seen.add(key); }
}
