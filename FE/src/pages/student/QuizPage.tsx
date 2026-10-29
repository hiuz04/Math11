import { useState } from "react";
import { quizService } from "../../services/quizService";
import { errorMessage, useMockApi } from "../../services/serviceMode";
import ApiQuizPage from "./ApiQuizPage";
import Icon from "../../components/common/Icon";
import Button from "../../components/common/Button";
import Badge from "../../components/common/Badge";
import Progress from "../../components/common/Progress";
import Modal from "../../components/common/Modal";
import AiDrawer from "../../components/ai/AiDrawer";

type QuizProps = { category?: "Luyện tập" | "Kiểm tra"; setActive: (p: string) => void; notify: (m: string, t?: "success"|"error"|"warning") => void };
export default function Quiz(props: QuizProps) { return useMockApi ? <MockQuiz {...props}/> : <ApiQuizPage {...props}/>; }
function MockQuiz({ setActive, notify, category = "Kiểm tra" }: QuizProps) {
  const [question, setQuestion] = useState(1);
  const [startedAt] = useState(() => new Date().toISOString());
  const [saving, setSaving] = useState(false);
  const submit = async () => { if (saving) return; setSaving(true); try { await quizService.recordSubmission(answer, startedAt, category); setSubmitOpen(false); notify("Đã nộp bài thành công."); setActive("quiz-result"); } catch (e) { notify(errorMessage(e), "error"); } finally { setSaving(false); } };
  const [answer, setAnswer] = useState("");
  const [hint, setHint] = useState(false);
  const [aiOpen, setAiOpen] = useState(false);
  const [submitOpen, setSubmitOpen] = useState(false);
  const select = (x: string) => setAnswer(x);
  const correct = answer === "C";
  const wrong = answer === "D";
  return <div className="quiz-page"><div className="quiz-top"><div><button onClick={() => setActive("exercises")}><Icon name="close"/></button><div><strong>Luyện tập – Hàm số lượng giác</strong><span>15 câu · 30 phút</span></div></div><div className="quiz-timer"><Icon name="clock"/><span>Thời gian còn lại</span><strong>24:18</strong></div><Button onClick={() => setSubmitOpen(true)}>Nộp bài</Button></div><div className="quiz-body"><main className="question-area"><div className="question-meta"><Badge tone="purple">CÂU {question} / 15</Badge><span>1 điểm</span></div><Progress value={(question/15)*100} tone="purple"/><div className="question-card"><h1>Cho hàm số:</h1><div className="quiz-formula">y = sin(x)</div><h2>Giá trị lớn nhất của hàm số là?</h2><div className="options">{[["A","−1"],["B","0"],["C","1"],["D","2"]].map(o=><button key={o[0]} className={`${answer === o[0] ? "selected" : ""} ${answer === o[0] && wrong ? "wrong" : ""} ${answer === o[0] && correct ? "correct" : ""}`} onClick={() => select(o[0])}><span>{o[0]}</span><strong>{o[1]}</strong>{answer === o[0] && (wrong || correct) && <Icon name={correct ? "check" : "close"}/>}</button>)}</div>{wrong && <div className="answer-feedback wrong-feedback"><div><Icon name="close"/><span><strong>Chưa chính xác</strong><p>Hãy nhớ rằng giá trị của sin(x) luôn nằm trong đoạn [−1, 1].</p></span></div>{hint && <p className="revealed-hint"><Icon name="spark" size={16}/> Gợi ý: Hãy xác định miền giá trị của hàm số sin(x).</p>}<div><Button variant="secondary" onClick={() => { setAnswer(""); setHint(false); }}>Thử lại</Button><Button variant="secondary" onClick={() => setHint(true)}>Xem gợi ý</Button><Button icon="spark" onClick={() => setAiOpen(true)}>Hỏi AI</Button></div></div>}{correct && <div className="answer-feedback correct-feedback"><Icon name="check"/><span><strong>Chính xác!</strong><p>Vì −1 ≤ sin(x) ≤ 1 nên giá trị lớn nhất là 1.</p></span></div>}</div><div className="question-actions"><Button variant="secondary" disabled={question===1} onClick={() => setQuestion(Math.max(1,question-1))}>‹ Câu trước</Button><Button onClick={() => question===15 ? setSubmitOpen(true) : setQuestion(Math.min(15,question+1))}>{question===15 ? "Nộp bài" : "Câu tiếp theo ›"}</Button></div></main><aside className="question-nav"><h2>Danh sách câu hỏi</h2><p>Đã trả lời <b>{answer ? 14 : 13}/15</b> câu</p><div>{Array.from({length:15},(_,i)=><button key={i} className={`${question===i+1?"active":""} ${i<12?"answered":""}`} onClick={() => setQuestion(i+1)}>{i+1}</button>)}</div><div className="nav-legend"><span><i className="answered"/>Đã trả lời</span><span><i/>Chưa trả lời</span></div></aside></div><button className="floating-ai" onClick={() => setAiOpen(true)}><Icon name="spark"/> Hỏi AI</button>{aiOpen && <AiDrawer quizMode onClose={() => setAiOpen(false)}/>} {submitOpen && <Modal title="Nộp bài?" description={`Bạn đã trả lời ${answer ? "14" : "13"}/15 câu. Những câu chưa trả lời sẽ không được tính điểm.`} confirmText="Nộp bài" onClose={() => setSubmitOpen(false)} onConfirm={() => void submit()}/>}</div>;
}
