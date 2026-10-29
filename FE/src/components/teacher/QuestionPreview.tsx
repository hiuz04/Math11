import type { Question } from "../../types/lms";
import type { ReactNode } from "react";
import RichTextEditor from "../common/RichTextEditor";
import Badge from "../common/Badge";
import { cognitiveLabels, difficultyLabels } from "../../constants/lms";
export default function QuestionPreview({ question, number, actions }: { question: Question; number: number; actions?: ReactNode }) {
  return <div className="card generated-question"><div><Badge tone="orange">{difficultyLabels[question.difficulty]}</Badge><Badge tone="purple">{cognitiveLabels[question.cognitiveLevel]}</Badge><span>Câu {number} · {question.type === "MCQ" ? "Trắc nghiệm" : "Tự luận"}</span>{actions && <div className="row-actions">{actions}</div>}</div>{question.content.startsWith("<") ? <RichTextEditor key={question.content} value={question.content} readOnly/> : <h3>{question.content}</h3>}{question.options && <div className="answers">{question.options.map((option, i) => <span className={option === question.answer ? "correct" : ""} key={i}>{String.fromCharCode(65 + i)}. {option}</span>)}</div>}<div className="question-explain"><strong>Đáp án: {question.answer || "Theo hướng dẫn chấm của giáo viên"}</strong></div></div>;
}
