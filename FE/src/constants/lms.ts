import type { CognitiveLevel, Question, Prompts } from "../types/lms";
export const cognitiveLabels: Record<CognitiveLevel, string> = { KNOWLEDGE: "Nhận biết", UNDERSTANDING: "Thông hiểu", APPLICATION: "Vận dụng" };
export const difficultyLabels = { EASY: "Dễ", MEDIUM: "Trung bình", HARD: "Khó" };
export const curriculum: Record<string, string[]> = {
  "Chương 1 – Hàm số lượng giác": ["Phương trình lượng giác", "Hàm số lượng giác"],
  "Chương 2 – Dãy số": ["Cấp số cộng", "Cấp số nhân"],
  "Chương 3 – Giới hạn": ["Giới hạn của dãy số"],
  "Chương 4 – Đường thẳng và mặt phẳng": ["Đường thẳng và mặt phẳng trong không gian"],
  "Chương 5 – Quan hệ song song": ["Quan hệ song song trong không gian"],
};
export const essayQuestions: Question[] = [
  { id: 101, title: "Cấp số cộng và ứng dụng", content: "Cho cấp số cộng có u₁ = 2 và d = 3. Tính u₁₀ và trình bày cách làm.", chapter: "Chương 2 – Dãy số", topic: "Cấp số cộng", type: "ESSAY", difficulty: "MEDIUM", cognitiveLevel: "UNDERSTANDING", scenario: "ARITHMETIC" },
  { id: 102, title: "Bài toán thực tế về cấp số nhân", content: "Một hàng ghế đầu có 3 ghế. Mỗi hàng sau có số ghế gấp đôi hàng trước. Tính số ghế ở hàng thứ 5 và trình bày cách làm.", chapter: "Chương 2 – Dãy số", topic: "Cấp số nhân", type: "ESSAY", difficulty: "HARD", cognitiveLevel: "APPLICATION", scenario: "GEOMETRIC" },
];
export const defaultPrompts: Prompts = {
  system: `Bạn là AI Tutor dành cho học sinh lớp 11.
Mục tiêu của bạn là giúp học sinh tự giải bài toán, không cung cấp ngay đáp án hoàn chỉnh.
Khi học sinh làm sai:
1. Phân tích lỗi.
2. Chia lời giải thành từng bước nhỏ.
3. Chỉ hiển thị một bước tại một thời điểm.
4. Mỗi bước chỉ cung cấp công thức hoặc gợi ý cần thiết.
5. Yêu cầu học sinh tự nhập kết quả.
6. Không đưa kết quả của bước nếu học sinh chưa thử trả lời.
7. Nếu học sinh trả lời sai, đưa thêm gợi ý nhưng không đưa đáp án.
8. Chỉ chuyển sang bước tiếp theo khi bước hiện tại đã đúng.
9. Không hiển thị toàn bộ lời giải cùng lúc.
10. Sau khi hoàn thành tất cả các bước, yêu cầu học sinh tự làm lại toàn bộ bài trên giấy.
11. Yêu cầu học sinh chụp hoặc upload ảnh bài làm mới.
12. Đánh giá lại bài làm mới.`,
  essayEvaluation: "Kiểm tra bài làm theo đề bài. Trả JSON correct, feedback, score. Nếu sai chỉ chỉ ra lỗi và chuyển sang Tutor, không đưa đáp số hay lời giải đầy đủ.",
  stepTutor: "Chỉ trả một bước hiện tại gồm công thức và câu hỏi. Kiểm tra câu trả lời học sinh, sai thì gợi ý không có đáp án; đúng thì chờ học sinh nhấn Tiếp tục. Không tiết lộ bước sau.",
  retryEvaluation: "Đánh giá ảnh bài làm mới độc lập sau Tutor. So sánh nội dung với yêu cầu đề bài; không chấp nhận ảnh cũ. Trả correct, feedback, score; không đoán nếu không đọc được ảnh.",
};
