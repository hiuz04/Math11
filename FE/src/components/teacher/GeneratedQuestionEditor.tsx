import { useState } from "react";
import Button from "../common/Button";
import RichTextEditor from "../common/RichTextEditor";
import CognitiveLevelSelector from "./CognitiveLevelSelector";
import { validateQuestion } from "../../services/questionValidation";
import type { Question } from "../../types/lms";
export default function GeneratedQuestionEditor({ question, onSave, onClose }: { question: Question; onSave: (question: Question) => void; onClose: () => void }) {
  const [draft, setDraft] = useState(question);
  let valid = true; try { validateQuestion(draft); } catch { valid = false; }
  return <div className="modal-backdrop"><section className="form-modal question-form" role="dialog" aria-modal="true" aria-label="Chỉnh sửa câu hỏi"><div className="section-head"><h2>Chỉnh sửa câu hỏi</h2></div><div className="lesson-editor question-fields"><label>Tên câu hỏi<input value={draft.title} onChange={e => setDraft({ ...draft, title: e.target.value })}/></label><CognitiveLevelSelector value={draft.cognitiveLevel} onChange={level => setDraft({ ...draft, cognitiveLevel: level })}/><div><span className="feature-label">Nội dung câu hỏi</span><RichTextEditor value={draft.content} onChange={content => setDraft({ ...draft, content })}/></div>{draft.options?.map((option, i) => <label key={i}>Lựa chọn {String.fromCharCode(65 + i)}<input value={option} onChange={e => setDraft({ ...draft, options: draft.options?.map((o, n) => n === i ? e.target.value : o), answer: draft.answer === option ? e.target.value : draft.answer })}/></label>)}<label>Đáp án / hướng dẫn chấm<input value={draft.answer || ""} onChange={e => setDraft({ ...draft, answer: e.target.value })}/></label></div><div><Button variant="secondary" onClick={onClose}>Hủy</Button><Button disabled={!valid} onClick={() => onSave(draft)}>Lưu thay đổi</Button></div></section></div>;
}
