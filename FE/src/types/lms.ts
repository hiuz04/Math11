export type CognitiveLevel = "KNOWLEDGE" | "UNDERSTANDING" | "APPLICATION";
export type QuestionType = "MCQ" | "ESSAY";
export interface StudentQuestionDTO {
  id: number;
  title: string;
  content: string;
  chapter: string;
  topic: string;
  type: QuestionType;
  difficulty: "EASY" | "MEDIUM" | "HARD";
  cognitiveLevel: CognitiveLevel;
  options?: string[];
  scenario?: "ARITHMETIC" | "GEOMETRIC";
}
export interface TeacherQuestionDTO extends StudentQuestionDTO { answer?: string; courseId?: number; published?: boolean }
export type Question = TeacherQuestionDTO;
export interface QuizReviewDTO { score: number; correctCount: number; totalCount: number; review: (TeacherQuestionDTO & { studentAnswer: string | SubmissionContent; correct?: boolean; score?: number; feedback?: string })[] }
export interface SubmissionContent { text?: string; image?: string; imageName?: string; imageHash?: string; imageFile?: File }
export interface StepAnswer { answer: string; correct: boolean | null; hint?: string; date: string; verification?: "EQUIVALENT" | "NOT_EQUIVALENT" | "UNKNOWN"; diagnostic?: string }
export interface AITutorStep {
  id: number;
  stepNumber: number;
  instruction: string;
  formula?: string;
  studentAnswer?: string;
  correct: boolean | null;
  hint?: string;
  attempts: StepAnswer[];
}
export interface Evaluation { correct: boolean; feedback: string; score?: number }
export interface EssaySubmission {
  id: number;
  studentId: number;
  questionId: number;
  question: Question;
  originalSubmission: SubmissionContent;
  originalFeedback: string;
  status: "SUBMITTED" | "CORRECT" | "NEEDS_TUTOR" | "RETRY_REQUIRED" | "COMPLETED" | "NEEDS_REVISION";
  aiSteps: AITutorStep[];
  studentStepAnswers: (StepAnswer & { stepId: number })[];
  totalSteps: number;
  currentStep: number;
  retrySubmissions: { content: SubmissionContent; result: Evaluation; date: string }[];
  retrySubmission?: SubmissionContent;
  finalResult?: Evaluation;
  startedAt: string;
  updatedAt: string;
  elapsedSeconds: number;
}
export interface HistoryEntry {
  id: string;
  title: string;
  type: QuestionType | "AI_TUTOR";
  category: "Luyện tập" | "Kiểm tra" | "AI Tutor";
  attempt: number;
  score?: number;
  status: string;
  date: string;
  elapsedSeconds: number;
  submissionId?: number;
  attemptId?: number;
  detail?: { prompt: string; answer: string; feedback: string; image?: string };
}
export interface GenerationRequest {
  chapter: string; topic: string; questionType: QuestionType;
  cognitiveLevel: CognitiveLevel; count: number;
  difficulty: Question["difficulty"]; instructions?: string; courseId?: number;
}
export type ExamMatrix = Record<CognitiveLevel, Record<QuestionType, number>>;
export interface Exam { id: number; title: string; chapter: string; topic: string; matrix: ExamMatrix; questions: Question[]; createdAt: string; courseId?: number; published?: boolean; resultsPublished?: boolean; durationSeconds?: number }
export interface Prompts { system: string; essayEvaluation: string; stepTutor: string; retryEvaluation: string }
export interface GeminiConfig { model: string; temperature: number; maxOutputTokens: number; enabled: boolean; hasApiKey: boolean }
