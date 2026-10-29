import { useResource } from "../../services/useResource";
import { lmsService, selection } from "../../services/lmsService";
import { apiRequest } from "../../services/api";
import type { HistoryEntry } from "../../types/lms";
import Icon from "../../components/common/Icon";
import Button from "../../components/common/Button";
import Badge from "../../components/common/Badge";
import Progress from "../../components/common/Progress";
import Breadcrumb from "../../components/common/Breadcrumb";
import { useState } from "react";
import { essayService } from "../../services/essayService";
import { errorMessage } from "../../services/serviceMode";
import EssaySubmissionDetail from "../../components/essay/EssaySubmissionDetail";
import RichTextEditor from "../../components/common/RichTextEditor";
import type { EssaySubmission } from "../../types/lms";
import type { QuizAttempt } from "../../services/quizApiService";
export default function ApiStudentDetailPage({setActive}:{setActive:(p:string)=>void}) {
  const {data,error}=useResource(()=>lmsService.studentAnalytics(selection.studentId),undefined,[selection.studentId]);
  const {data:history,error:historyError}=useResource(async signal => {
    if (!selection.studentId) throw new Error("Hãy chọn học sinh để xem chi tiết.");
    return apiRequest<HistoryEntry[]>(`learning-history?studentId=${selection.studentId}`, { signal });
  },[],[selection.studentId]);
  const [detail,setDetail]=useState<EssaySubmission | QuizAttempt>(), [detailError,setDetailError]=useState(""), [detailPending,setDetailPending]=useState(false);
  async function open(entry: HistoryEntry) {
    if (detailPending) return;
    setDetailError(""); setDetailPending(true);
    try {
      const next = entry.submissionId ? await essayService.get(entry.submissionId) : entry.attemptId ? await apiRequest<QuizAttempt>(`quiz-attempts/${entry.attemptId}`) : undefined;
      if (!next) throw new Error("Hoạt động này chưa có chi tiết bài làm.");
      setDetail(next);
    } catch (e) { setDetailError(errorMessage(e)); } finally { setDetailPending(false); }
  }
  const total=data?.chapters.reduce((n,c)=>n+c.totalLessons,0)||0;
  const stats=[{icon:"chart",value:`${total?Math.round((data?.completedLessons||0)/total*100):0}%`,label:"Tiến độ"},{icon:"award",value:Number(data?.averageScore||0).toFixed(2),label:"Điểm trung bình"},{icon:"clock",value:`${data?.elapsedHours||0} giờ`,label:"Thời gian học"},{icon:"check",value:`${data?.completedLessons||0}/${total}`,label:"Bài học hoàn thành"}] as const;
  return <div className="page"><Breadcrumb items={[["Dashboard","teacher"],["Học sinh","students"],[data?.name || "Học sinh","student-detail"]]} setActive={setActive}/>{(error || historyError || detailError) && <p className="feature-error" role="alert">{error || historyError || detailError}</p>}<div className="student-detail-head"><span className="profile-avatar">{data?.name.split(" ").slice(-2).map(s=>s[0]).join("")}</span><div><h1>{data?.name || "Đang tải…"}</h1><p>Toán 11</p></div><Badge tone="green">Hồ sơ học tập</Badge></div><div className="stat-grid">{stats.map(s=><div className="stat-card" key={s.label}><span className="stat-icon blue"><Icon name={s.icon}/></span><div className="stat-main"><span>{s.label}</span><strong>{s.value}</strong></div></div>)}</div><div className="analytics-grid"><section className="card topic-card"><h2>Tiến độ theo chương</h2>{data?.chapters.map(c=><div className="topic-row" key={c.title}><div><span>{c.title}</span><strong>{Math.round(c.progress)}%</strong></div><Progress value={c.progress}/></div>)}</section><section className="card ai-insight"><Icon name="spark"/><h2>Nhận xét học tập</h2><p>{data?.completedExercises || 0} bài tập đã thực hiện. Xem các lần làm để nhận xét kết quả và cách giải.</p><Button variant="secondary" onClick={()=>document.getElementById("student-activities")?.scrollIntoView({behavior:"smooth"})}>Xem chi tiết</Button></section></div><section className="card recent-card" id="student-activities"><h2>Hoạt động gần đây</h2><div className="activity-list">{history.map(h=><div className="activity-row" key={h.id} role="button" tabIndex={0} aria-label={`Xem bài làm: ${h.title}`} aria-disabled={detailPending} onClick={()=>void open(h)} onKeyDown={event=>{if(event.key==="Enter" || event.key===" "){event.preventDefault();void open(h);}}}><span className="activity-icon"><Icon name={h.type==="AI_TUTOR"?"spark":"award"}/></span><div><strong>{h.title} · {h.status}{h.score!==undefined?` · ${h.score}/10`:""}</strong><small>{new Date(h.date).toLocaleString("vi-VN")}</small></div></div>)}</div></section>{detail && <div className="modal-backdrop"><section className="form-modal question-form" role="dialog" aria-modal="true" aria-label="Chi tiết bài làm học sinh"><div className="section-head"><h2>Chi tiết bài làm</h2><Button variant="ghost" onClick={()=>setDetail(undefined)}>Đóng</Button></div>{"questionId" in detail ? <EssaySubmissionDetail submission={detail}/> : <div className="main-column"><h3>{detail.exam.title}</h3><Badge tone="purple">{detail.status}</Badge><p>{detail.result ? `Điểm: ${detail.result.score}/10` : "Chưa có kết quả cuối cùng."}</p>{detail.questions.map(q=>{const review=detail.result?.review.find(r=>r.id===q.id);const content=detail.essayAnswers?.[q.id]?.content;return <section className="card lesson-editor" key={q.id}><h3>{q.title}</h3><RichTextEditor value={q.content} readOnly/><p className="feature-copy">Bài làm: {q.type==="MCQ" ? detail.answers[q.id] || "Chưa trả lời" : content?.text || ""}</p>{content?.image && <figure className="upload-preview essay-image"><img src={content.image} alt="Ảnh bài làm đã nộp"/></figure>}{review && <p className="feature-copy">Đáp án / hướng dẫn chấm: {review.answer} {review.feedback}</p>}</section>;})}</div>}</section></div>}</div>;
}
