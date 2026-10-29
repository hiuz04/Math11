import { useEffect, useRef, useState } from "react";
import { quizApiService, quizClock, quizRemaining, type QuizAttempt } from "../../services/quizApiService";
import { errorMessage } from "../../services/serviceMode";
import Icon from "../../components/common/Icon";
import Button from "../../components/common/Button";
import Badge from "../../components/common/Badge";
import Progress from "../../components/common/Progress";
import Modal from "../../components/common/Modal";
import RichTextEditor from "../../components/common/RichTextEditor";
import type { SubmissionContent } from "../../types/lms";
import { ApiError } from "../../services/api";
import EssayUpload from "../../components/essay/EssayUpload";
export default function ApiQuizPage({ setActive, notify }: { setActive: (p: string) => void; notify: (m: string, t?: "success" | "error" | "warning") => void }) {
  const [attempt, setAttempt] = useState<QuizAttempt>(), [question, setQuestion] = useState(1), [answers, setAnswers] = useState<Record<string,string>>({});
  const [error, setError] = useState(""), [saving, setSaving] = useState(false), [submitOpen, setSubmitOpen] = useState(false), [now, setNow] = useState(Date.now());
  const [clock, setClock] = useState({ deadline: 0, offset: 0 });
  const [essayAnswers, setEssayAnswers] = useState<Record<string, { content: SubmissionContent }>>({});
  const [imageReading, setImageReading] = useState(false);
  const busy = useRef(false), autoSubmitted = useRef(false), mounted = useRef(false);
  const previews = useRef(new Set<string>());
  useEffect(() => {
    const current = new Set(Object.values(essayAnswers).map(a => a.content.image).filter((url): url is string => Boolean(url?.startsWith("blob:"))));
    for (const url of previews.current) if (!current.has(url)) URL.revokeObjectURL(url);
    previews.current = current;
  }, [essayAnswers]);
  useEffect(() => () => { for (const url of previews.current) URL.revokeObjectURL(url); }, []);
  useEffect(() => {
    mounted.current = true;
    quizApiService.start().then(a => { if (mounted.current) { setAttempt(a); setAnswers(a.answers); setEssayAnswers(a.essayAnswers || {}); setClock(quizClock(a, Date.now())); if (a.status !== "IN_PROGRESS") setActive("quiz-result"); } }).catch(e => { if (mounted.current) setError(errorMessage(e)); });
    const timer = setInterval(() => setNow(Date.now()), 1000);
    const online = () => { autoSubmitted.current = false; setNow(Date.now()); };
    window.addEventListener("online", online);
    return () => { mounted.current = false; clearInterval(timer); window.removeEventListener("online", online); };
  }, []);
  const current = attempt?.questions[question - 1], count = attempt?.questions.length || 0, answered = Object.keys(answers).length + Object.values(essayAnswers).filter(a => a.content.text?.trim() || a.content.image).length;
  const remaining = attempt && !attempt.expired ? quizRemaining(clock, now) : 0;
  const timer = `${Math.floor(remaining/60)}:${String(remaining%60).padStart(2,"0")}`;
  async function reconcile(error: unknown) {
    if (error instanceof ApiError && error.status === 409) {
      try { const fresh = await quizApiService.get(); if (mounted.current) { setAttempt(fresh); setClock(quizClock(fresh, Date.now())); if (fresh.status !== "IN_PROGRESS") setActive("quiz-result"); } }
      catch (refreshError) { if (mounted.current) setError(errorMessage(refreshError)); }
    }
  }
  async function select(value: string) {
    if (!attempt || !current || busy.current || !quizRemaining(clock, Date.now()) || attempt.status !== "IN_PROGRESS") return;
    const next = { ...answers, [current.id]: value }; setAnswers(next); busy.current = true; setSaving(true); setError("");
    try { const saved = await quizApiService.save(attempt.id, next, false, essayAnswers); if (mounted.current) { setAttempt(saved); setClock(quizClock(saved, Date.now())); } }
    catch (e) { if (mounted.current) setError(`${errorMessage(e)} Câu trả lời đang được giữ trên trang; hãy thử lưu lại hoặc nộp bài trước khi rời trang.`); await reconcile(e); }
    finally { busy.current = false; if (mounted.current) setSaving(false); }
  }
  async function saveEssay(next = essayAnswers, imageQuestionId?: number) {
    if (!attempt || busy.current || !quizRemaining(clock, Date.now()) || attempt.status !== "IN_PROGRESS") return;
    busy.current = true; setSaving(true); setError("");
    try {
      const saved = imageQuestionId ? await quizApiService.saveImage(attempt.id, imageQuestionId, answers, next) : await quizApiService.save(attempt.id, answers, false, next);
      if (mounted.current) { setAttempt(saved); setClock(quizClock(saved, Date.now())); if (imageQuestionId && saved.essayAnswers?.[imageQuestionId]) setEssayAnswers(old => ({ ...old, [imageQuestionId]: saved.essayAnswers![imageQuestionId] })); }
    }
    catch (e) { if (mounted.current) setError(`${errorMessage(e)} Bài làm vẫn được giữ trên trang; hãy thử lưu lại hoặc nộp bài trước khi rời trang.`); await reconcile(e); }
    finally { busy.current = false; if (mounted.current) setSaving(false); }
  }
  function selectEssayImage(value: SubmissionContent) {
    if (!current || !remaining || busy.current) { if (value.image?.startsWith("blob:")) URL.revokeObjectURL(value.image); return; }
    const next = { ...essayAnswers, [current.id]: { content: { text: essayAnswers[current.id]?.content.text, ...value } } };
    setEssayAnswers(next); void saveEssay(next, value.imageFile ? current.id : undefined);
  }
  async function submit() {
    if (!attempt || busy.current || imageReading) return;
    busy.current = true; autoSubmitted.current = true; setSaving(true); setSubmitOpen(false); setError("");
    let uploading = false;
    try {
      let finalEssays = essayAnswers;
      for (const [id, value] of Object.entries(essayAnswers)) {
        if (!value.content.imageFile || !quizRemaining(clock, Date.now()) || attempt.expired) continue;
        uploading = true;
        const saved = await quizApiService.saveImage(attempt.id, Number(id), answers, finalEssays);
        if (saved.essayAnswers?.[id]) finalEssays = { ...finalEssays, [id]: saved.essayAnswers[id] };
        if (mounted.current) setEssayAnswers(finalEssays);
      }
      uploading = false;
      await quizApiService.save(attempt.id, answers, true, finalEssays);
      if (mounted.current) { notify("Đã nộp bài thành công."); setActive("quiz-result"); }
    }
    catch (e) { if (mounted.current) setError(`${errorMessage(e)}${!quizRemaining(clock, Date.now()) ? " Đã hết giờ; bài làm đang chờ đồng bộ. Kết nối lại hoặc nhấn Nộp bài để thử lại." : ""}`); if (uploading && e instanceof ApiError && e.status === 409) { await reconcile(e); autoSubmitted.current = false; } }
    finally { busy.current = false; if (mounted.current) setSaving(false); }
  }
  useEffect(() => { if (attempt?.status === "IN_PROGRESS" && remaining === 0 && !saving && !imageReading && !autoSubmitted.current) { autoSubmitted.current = true; void submit(); } }, [attempt?.id, attempt?.status, remaining, saving, imageReading, now]);
  return <div className="quiz-page"><div className="quiz-top"><div><button onClick={() => setActive("exercises")}><Icon name="close"/></button><div><strong>{attempt?.exam.title || "Đang tải đề…"}</strong><span>{count} câu · {Math.round((attempt?.exam.durationSeconds || 0)/60)} phút</span></div></div><div className="quiz-timer"><Icon name="clock"/><span>Thời gian còn lại</span><strong>{timer}</strong></div><Button disabled={!attempt || saving || imageReading} onClick={() => setSubmitOpen(true)}>Nộp bài</Button></div>{error && <p className="feature-error" role="alert">{error}</p>}<div className="quiz-body"><main className="question-area"><div className="question-meta"><Badge tone="purple">CÂU {question} / {count}</Badge><span>{count ? (10/count).toFixed(2) : 0} điểm</span></div><Progress value={count ? question/count*100 : 0} tone="purple"/>{current && <div className="question-card"><h1>{current.title}</h1><RichTextEditor key={current.id} value={current.content} readOnly/>{current.type === "ESSAY" && <><label>Bài làm<textarea aria-label="Bài làm" rows={8} disabled={saving || !remaining} value={essayAnswers[current.id]?.content.text || ""} onChange={e => setEssayAnswers(old => ({ ...old, [current.id]: { content: { ...old[current.id]?.content, text: e.target.value } } }))} onBlur={() => void saveEssay()}/></label><EssayUpload value={essayAnswers[current.id]?.content || {}} disabled={saving || !remaining} onChange={selectEssayImage} onLoadingChange={setImageReading}/></>}{current.type === "MCQ" && <div className="options">{current.options?.map((o,i) => <button disabled={saving || !remaining} key={i} className={answers[current.id] === o ? "selected" : ""} onClick={() => void select(o)}><span>{String.fromCharCode(65+i)}</span><strong>{o}</strong></button>)}</div>}</div>}<div className="question-actions"><Button variant="secondary" disabled={question===1 || imageReading} onClick={() => setQuestion(question-1)}>‹ Câu trước</Button><Button disabled={!count || imageReading} onClick={() => question===count ? setSubmitOpen(true) : setQuestion(question+1)}>{question===count ? "Nộp bài" : "Câu tiếp theo ›"}</Button></div></main><aside className="question-nav"><h2>Danh sách câu hỏi</h2><p>Đã trả lời <b>{answered}/{count}</b> câu</p><div>{attempt?.questions.map((q,i) => <button key={q.id} disabled={imageReading} className={`${question===i+1?"active":""} ${answers[q.id] || essayAnswers[q.id]?.content.text?.trim() ? "answered" : ""}`} onClick={() => setQuestion(i+1)}>{i+1}</button>)}</div><div className="nav-legend"><span><i className="answered"/>Đã trả lời</span><span><i/>Chưa trả lời</span></div></aside></div><button className="floating-ai" onClick={() => notify("Hoàn thành bài kiểm tra trước khi trao đổi với AI.","warning")}><Icon name="spark"/> Hỏi AI</button>{submitOpen && <Modal title="Nộp bài?" description={`Bạn đã trả lời ${answered}/${count} câu. Những câu chưa trả lời sẽ không được tính điểm.`} confirmText="Nộp bài" onClose={() => setSubmitOpen(false)} onConfirm={() => void submit()}/>}</div>;
}
