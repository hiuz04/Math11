import type { Question, GenerationRequest, Exam, ExamMatrix, CognitiveLevel, QuestionType } from "../types/lms";
import { curriculum, essayQuestions } from "../constants/lms";
import { readSetting, writeSetting } from "./storage";
import { apiRequest } from "./api";
import { assertMockAiEnabled, postApi, useMockApi } from "./serviceMode";
import { createMockId } from "./id";
import { validateQuestions } from "./questionValidation";
import { apiOperation } from "./api";
const seed: Question[] = [
  { id: 1, title: "Hàm số lượng giác", content: "Giá trị lớn nhất của y = sin x là bao nhiêu?", chapter: "Chương 1 – Hàm số lượng giác", topic: "Hàm số lượng giác", type: "MCQ", difficulty: "EASY", cognitiveLevel: "KNOWLEDGE", options: ["−1", "0", "1", "2"], answer: "1" },
  ...essayQuestions,
];
function validateRequest(r: GenerationRequest) {
  if (useMockApi && !curriculum[r.chapter]?.includes(r.topic)) throw new Error("Hãy chọn chương và chủ đề hợp lệ.");
  if (!Number.isInteger(r.count) || r.count < 1 || r.count > 100) throw new Error("Số câu phải từ 1 đến 100.");
}
function sample(r: GenerationRequest, i: number): Question {
  const a = i + 2, d = i % 4 + 2, n = i + 5;
  let content: string, answer: string;
  let customOptions: string[] | undefined;
  if (r.topic === "Cấp số cộng") { content = `Cho cấp số cộng có u₁ = ${a}, d = ${d}. Tính u${n}.`; answer = String(a + (n - 1) * d); }
  else if (r.topic === "Cấp số nhân") { content = `Cho cấp số nhân có u₁ = ${a}, q = 2. Tính u${n}.`; answer = String(a * 2 ** (n - 1)); }
  else if (r.topic === "Giới hạn của dãy số") { content = `Tính giới hạn của dãy số uₙ = (${a}n + ${d}) / n khi n tiến tới vô cùng.`; answer = String(a); }
  else if (r.topic === "Phương trình lượng giác") { content = `Tìm nghiệm của sin x = 0 trên đoạn [${i}π; ${i + 1}π].`; answer = `${i}π và ${i + 1}π`; }
  else if (r.topic === "Đường thẳng và mặt phẳng trong không gian") { content = `Cho ${a} điểm phân biệt, không có ba điểm thẳng hàng. Có bao nhiêu đường thẳng đi qua hai trong các điểm này?`; answer = String(a * (a - 1) / 2); }
  else if (r.topic === "Quan hệ song song trong không gian") { content = `Trong cùng một mặt phẳng, hai đường thẳng a${i + 1} và b${i + 1} không trùng nhau và cùng song song với c${i + 1}. Quan hệ giữa a${i + 1} và b${i + 1} là gì?`; answer = "Song song"; customOptions = ["Cắt nhau", "Song song", "Chéo nhau", "Vuông góc"]; }
  else { content = `Tìm giá trị lớn nhất của y = ${a} sin x + ${d}.`; answer = String(a + d); }
  if (r.cognitiveLevel === "UNDERSTANDING") content += " Giải thích công thức hoặc tính chất đã sử dụng.";
  if (r.cognitiveLevel === "APPLICATION") content += " Xây dựng một tình huống thực tế phù hợp và giải thích ý nghĩa kết quả.";
  const options = r.questionType === "MCQ" ? customOptions || (/^\d+$/.test(answer) ? [String(Number(answer) - 1), answer, String(Number(answer) + 1), String(Number(answer) + 2)] : [answer, "Không có nghiệm", `${i}π`, `${i + 1}π`]) : undefined;
  return { id: createMockId(), title: `${r.topic} · Câu ${i + 1}`, content, answer, options, chapter: r.chapter, topic: r.topic, type: r.questionType, cognitiveLevel: r.cognitiveLevel, difficulty: r.difficulty };
}
export const questionService = {
  async get(id: number): Promise<Question> { if (!useMockApi) return apiRequest(`questions/${id}`); const question = (await this.list()).find(q => q.id === id); if (!question) throw new Error("Không tìm thấy câu hỏi."); return question; },
  async list(): Promise<Question[]> { return useMockApi ? readSetting("questions", seed) : apiRequest("questions"); },
  async save(questions: Question[]): Promise<void> {
    validateQuestions(questions);
    if (!useMockApi) { const body = JSON.stringify({ questions }); await apiOperation("questions/bulk", body, { method: "POST", body }); return; }
    const bank = await this.list(); const map = new Map(bank.map(q => [q.id, q]));
    questions.forEach(q => map.set(q.id, q)); writeSetting("questions", [...map.values()]);
  },
  async remove(id: number): Promise<void> {
    if (!useMockApi) return apiRequest(`questions/${id}`, { method: "DELETE" });
    writeSetting("questions", (await this.list()).filter(q => q.id !== id));
  },
  async generate(request: GenerationRequest): Promise<Question[]> {
    assertMockAiEnabled();
    validateRequest(request);
    if (!useMockApi) { const questions = await postApi<Question[]>("ai/questions/generate", request); return questions.map(q => ({ ...q, ...(request.courseId ? { courseId: request.courseId } : {}) })); }
    return Array.from({ length: request.count }, (_, i) => sample(request, i));
  },
  async generateExam(chapter: string, topic: string, matrix: ExamMatrix, courseId?: number): Promise<Exam> {
    const total = Object.values(matrix).reduce((n, row) => n + row.MCQ + row.ESSAY, 0);
    if (!total || total > 100) throw new Error("Tổng số câu phải từ 1 đến 100.");
    if (!useMockApi) { const exam = await postApi<Exam>("ai/exams/generate", { chapter, topic, matrix, ...(courseId ? { courseId } : {}) }); return { ...exam, ...(courseId ? { courseId, questions: exam.questions.map(q => ({ ...q, courseId })) } : {}) }; }
    const questions: Question[] = [];
    for (const level of Object.keys(matrix) as CognitiveLevel[]) for (const type of ["MCQ", "ESSAY"] as QuestionType[]) {
      const count = matrix[level][type];
      if (count) { const batch = await this.generate({ chapter, topic, cognitiveLevel: level, questionType: type, difficulty: "MEDIUM", count }); batch.forEach(q => questions.push({ ...q, id: createMockId() })); }
    }
    return { id: createMockId(), title: `Đề kiểm tra · ${topic}`, chapter, topic, matrix: structuredClone(matrix), questions, createdAt: new Date().toISOString() };
  },
  async saveExam(exam: Exam): Promise<Exam> {
    validateQuestions(exam.questions);
    if (!useMockApi) { const body = JSON.stringify(exam); return apiOperation("exams", body, { method: "POST", body }); }
    writeSetting("exams", [...readSetting<Exam[]>("exams", []).filter(e => e.id !== exam.id), exam]);
    await this.save(exam.questions);
    return exam;
  },
  async listExams(): Promise<Exam[]> { return useMockApi ? readSetting("exams", []) : apiRequest("exams"); },
};
