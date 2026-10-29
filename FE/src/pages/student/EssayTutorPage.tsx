import RichTextEditor from "../../components/common/RichTextEditor";
import { useEffect, useState } from "react";
import type { EssaySubmission, SubmissionContent } from "../../types/lms";
import PageHeading from "../../components/common/PageHeading";
import Button from "../../components/common/Button";
import Progress from "../../components/common/Progress";
import EssayUpload from "../../components/essay/EssayUpload";
import TutorStep from "../../components/ai/TutorStep";
import { essayService } from "../../services/essayService";
import { aiTutorService } from "../../services/aiTutorService";
import { errorMessage } from "../../services/serviceMode";

export default function EssayTutorPage({ submissionId, setActive }: { submissionId: number; setActive: (p: string) => void }) {
  const [submission, setSubmission] = useState<EssaySubmission>(), [error, setError] = useState("");
  const [busy, setBusy] = useState(false), [retryOpen, setRetryOpen] = useState(false), [image, setImage] = useState<SubmissionContent>({});
  const [imageReading, setImageReading] = useState(false);
  useEffect(() => () => { if (image.image?.startsWith("blob:")) URL.revokeObjectURL(image.image); }, [image.image]);
  useEffect(() => { let live = true; (async () => {
    try { let s = await essayService.get(submissionId); if (s.status === "SUBMITTED") s = await aiTutorService.evaluateEssay(submissionId); if (s.status === "NEEDS_TUTOR") s = await aiTutorService.startTutor(submissionId); if (live) setSubmission(s); }
    catch (e) { if (live) setError(errorMessage(e)); }
  })(); return () => { live = false; }; }, [submissionId]);
  async function run(action: () => Promise<EssaySubmission>, result = false) {
    setBusy(true); setError("");
    try { const s = await action(); setSubmission(s); if (result) setActive("essay-result"); }
    catch (e) { setError(errorMessage(e)); } finally { setBusy(false); }
  }
  const step = submission?.aiSteps[submission.currentStep];
  return <div className="page"><PageHeading eyebrow="AI TUTOR" title="Hướng dẫn từng bước" subtitle={submission?.question.title || "Đang tải bài làm…"} action={<Button variant="secondary" onClick={() => setActive("history")}>Lịch sử học tập</Button>}/>{error && <p className="feature-error" role="alert">{error}</p>}{submission && <div className="editor-grid"><div className="main-column"><section className="card lesson-editor"><h2>Đề bài</h2>{submission.question.content.trim().startsWith("<") ? <RichTextEditor key={submission.question.content} value={submission.question.content} readOnly/> : <p className="feature-copy">{submission.question.content}</p>}<p className="feature-note">{submission.originalFeedback}</p><Progress value={submission.aiSteps.filter(s => s.correct).length / Math.max(submission.totalSteps || 1, 1) * 100}/></section>{submission.status === "NEEDS_TUTOR" && step && <TutorStep key={step.id} step={step} busy={busy} onCheck={answer => void run(() => aiTutorService.checkStep(submissionId, answer, step.stepNumber))} onContinue={() => void run(() => aiTutorService.nextStep(submissionId, step.stepNumber))}/>} {["RETRY_REQUIRED", "NEEDS_REVISION"].includes(submission.status) && <section className="card lesson-editor"><h2>{retryOpen ? "Nộp lại bài làm" : "Tự làm lại bài"}</h2><p className="feature-copy">Bây giờ hãy tự làm lại toàn bộ bài toán trên giấy mà không xem lại lời giải.</p>{!retryOpen ? <Button onClick={() => { setImage({}); setRetryOpen(true); }}>Làm lại bài</Button> : <><EssayUpload value={image} onChange={setImage} onLoadingChange={setImageReading} excludedHashes={[submission.originalSubmission.imageHash || "", ...submission.retrySubmissions.map(r => r.content.imageHash || "")]} disabled={busy}/><Button onClick={() => void run(() => aiTutorService.evaluateRetry(submissionId, image), true)} disabled={busy || imageReading || !image.image}>{busy ? "Đang kiểm tra…" : "Nộp lại bài"}</Button></>}</section>}{["COMPLETED", "CORRECT"].includes(submission.status) && <Button onClick={() => setActive("essay-result")}>Xem kết quả</Button>}</div><aside className="card publish-panel"><h2>Cách học cùng Tutor</h2><p className="feature-copy">Nhập câu trả lời rồi nhấn Kiểm tra. Khi chính xác, nhấn Tiếp tục để mở bước tiếp theo.</p><p className="feature-note">Tutor dừng ở mỗi bước. Sau bước cuối, em cần nộp ảnh bài làm mới.</p></aside></div>}</div>;
}
