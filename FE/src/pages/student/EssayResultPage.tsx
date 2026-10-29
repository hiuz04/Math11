import { useEffect, useState } from "react";
import PageHeading from "../../components/common/PageHeading";
import Button from "../../components/common/Button";
import EssaySubmissionDetail from "../../components/essay/EssaySubmissionDetail";
import { essayService } from "../../services/essayService";
import { errorMessage } from "../../services/serviceMode";
import type { EssaySubmission } from "../../types/lms";
export default function EssayResultPage({ submissionId, setActive }: { submissionId: number; setActive: (page: string) => void }) {
  const [submission, setSubmission] = useState<EssaySubmission>(), [error, setError] = useState("");
  useEffect(() => { let live = true; essayService.get(submissionId).then(s => { if (live) setSubmission(s); }).catch(e => { if (live) setError(errorMessage(e)); }); return () => { live = false; }; }, [submissionId]);
  return <div className="page"><PageHeading eyebrow="KẾT QUẢ TỰ LUẬN" title="Chi tiết bài làm" subtitle={submission?.question.title || "Đang tải…"} action={<Button variant="secondary" onClick={() => setActive("history")}>Lịch sử học tập</Button>}/>{error && <p role="alert">{error}</p>}{submission && <>{["NEEDS_TUTOR", "RETRY_REQUIRED", "NEEDS_REVISION", "SUBMITTED"].includes(submission.status) && <div className="feature-actions feature-section"><Button onClick={() => setActive("essay-tutor")}>{submission.status === "NEEDS_REVISION" ? "Nộp ảnh mới" : "Tiếp tục học"}</Button></div>}<EssaySubmissionDetail submission={submission}/></>}</div>;
}
