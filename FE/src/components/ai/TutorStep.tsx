import { useState } from "react";
import Button from "../common/Button";
import Badge from "../common/Badge";
import MathFormula from "../common/MathFormula";
import type { AITutorStep } from "../../types/lms";

export default function TutorStep({ step, busy, onCheck, onContinue }: { step: AITutorStep; busy: boolean; onCheck: (answer: string) => void; onContinue: () => void }) {
  const [answer, setAnswer] = useState(step.studentAnswer || "");
  return <section className="card lesson-editor"><div className="section-head"><h2>Bước {step.stepNumber}</h2><Badge tone={step.correct ? "green" : "purple"}>{step.correct ? "Chính xác" : `Lần thử: ${step.attempts.length}`}</Badge></div><p className="feature-copy">{step.instruction}</p>{step.formula && <MathFormula formula={step.formula}/>}<label>Câu trả lời của bạn<input value={answer} disabled={busy || step.correct === true} onChange={e => setAnswer(e.target.value)} onKeyDown={e => { if (e.key === "Enter" && !busy && !step.correct && answer.trim()) onCheck(answer); }} placeholder="Nhập kết quả hoặc phép tính…"/></label>{step.hint && <div className="note-block" role="status"><strong>Gợi ý</strong><p>{step.hint}</p></div>}<div className="feature-actions">{step.correct ? <Button onClick={onContinue} disabled={busy}>Tiếp tục</Button> : <Button onClick={() => onCheck(answer)} disabled={busy || !answer.trim()}>{busy ? "Đang kiểm tra…" : "Kiểm tra"}</Button>}</div></section>;
}
