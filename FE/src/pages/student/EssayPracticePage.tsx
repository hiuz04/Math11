import RichTextEditor from "../../components/common/RichTextEditor";
import { useEffect, useState } from "react";
import Button from "../../components/common/Button";
import Badge from "../../components/common/Badge";
import PageHeading from "../../components/common/PageHeading";
import Breadcrumb from "../../components/common/Breadcrumb";
import EssayUpload from "../../components/essay/EssayUpload";
import { difficultyLabels, cognitiveLabels } from "../../constants/lms";
import { essayService } from "../../services/essayService";
import { aiTutorService } from "../../services/aiTutorService";
import { errorMessage, useMockApi } from "../../services/serviceMode";
import type { SubmissionContent } from "../../types/lms";

export default function EssayPracticePage({ questionId, setActive, onSubmission }: { questionId: number; setActive: (page: string) => void; onSubmission: (id: number, tutor: boolean) => void }) {
  const [question, setQuestion] = useState(() => useMockApi ? essayService.getQuestion(questionId) : undefined);
  const [mode, setMode] = useState("text"), [text, setText] = useState("");
  const [image, setImage] = useState<SubmissionContent>({}), [busy, setBusy] = useState(false), [error, setError] = useState("");
  const [submittedId,setSubmittedId]=useState(0);
  const [startedAt] = useState(() => new Date().toISOString());
  const [imageReading, setImageReading] = useState(false);
  useEffect(() => () => { if (image.image?.startsWith("blob:")) URL.revokeObjectURL(image.image); }, [image.image]);
  useEffect(() => { let live = true; essayService.getQuestionById(questionId).then(q => { if (live) setQuestion(q); }).catch(e => { if (live) setError(errorMessage(e)); }); return () => { live = false; }; }, [questionId]);
  async function submit() {
    if (busy || imageReading) return;
    setBusy(true); setError("");
    try {
      const s = submittedId ? await essayService.get(submittedId) : await essayService.submit(questionId, mode === "text" ? { text: text.trim() } : image, startedAt);
      setSubmittedId(s.id);
      const evaluated = await aiTutorService.evaluateEssay(s.id);
      onSubmission(s.id, evaluated.status === "NEEDS_TUTOR");
    } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); }
  }
  if (!question) return <div className="page"><p role={error ? "alert" : "status"}>{error || "Đang tải bài tự luận…"}</p><Button onClick={() => setActive("exercises")}>Về bài tập</Button></div>;
  return <div className="page"><Breadcrumb items={[["Trang chủ", "dashboard"], ["Bài tập", "exercises"], ["Tự luận", "essay-practice"]]} setActive={setActive}/><PageHeading eyebrow="LUYỆN TẬP TỰ LUẬN" title={question.title} subtitle={`${question.chapter} · ${question.topic}`}/><div className="editor-grid"><div className="main-column"><section className="card lesson-editor"><div className="section-head"><h2>Đề bài</h2><div className="feature-actions"><Badge tone="orange">{difficultyLabels[question.difficulty]}</Badge><Badge tone="purple">{cognitiveLabels[question.cognitiveLevel]}</Badge></div></div>{question.content.trim().startsWith("<") ? <RichTextEditor key={question.content} value={question.content} readOnly/> : <p className="feature-copy">{question.content}</p>}</section><section className="card lesson-editor"><h2>Bài làm của bạn</h2><div className="tabs"><button className={mode === "text" ? "active" : ""} disabled={busy || imageReading || Boolean(submittedId)} onClick={() => setMode("text")}>Nhập bài làm</button><button className={mode === "image" ? "active" : ""} disabled={busy || imageReading} onClick={() => setMode("image")}>Upload ảnh</button></div>{mode === "text" ? <label>Bài làm<textarea rows={8} value={text} disabled={busy || imageReading || Boolean(submittedId)} onChange={e => setText(e.target.value)} placeholder="Trình bày các bước và kết quả của em…"/></label> : <EssayUpload value={image} onChange={setImage} onLoadingChange={setImageReading} disabled={busy || imageReading || Boolean(submittedId)}/>}<Button onClick={() => void submit()} disabled={busy || imageReading || (mode === "text" ? !text.trim() : !image.image)}>{busy ? "Đang kiểm tra…" : "Nộp bài"}</Button>{error && <p className="feature-error" role="alert">{error}</p>}</section></div><aside className="card publish-panel"><h2>Hướng dẫn làm bài</h2><p className="feature-copy">Trình bày rõ công thức, cách thay dữ kiện và kết quả. Nếu cần hỗ trợ, Tutor sẽ hướng dẫn em từng bước.</p>{useMockApi && <p className="feature-note">Đang dùng AI mock. Ảnh được lưu thật; chức năng đọc/chấm ảnh cần backend.</p>}<Button variant="secondary" className="full" onClick={() => setActive("history")}>Lịch sử học tập</Button></aside></div></div>;
}
