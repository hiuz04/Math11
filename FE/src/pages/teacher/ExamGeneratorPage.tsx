import { useEffect, useState } from "react";
import type { Exam, ExamMatrix as Matrix } from "../../types/lms";
import CourseChapterSelect from "../../components/teacher/CourseChapterSelect";
import { selection } from "../../services/lmsService";
import PageHeading from "../../components/common/PageHeading";
import Button from "../../components/common/Button";
import ExamMatrix from "../../components/teacher/ExamMatrix";
import GeneratedQuestionEditor from "../../components/teacher/GeneratedQuestionEditor";
import type { Question } from "../../types/lms";
import QuestionPreview from "../../components/teacher/QuestionPreview";
import { useCurriculum } from "../../services/useCurriculum";
import { questionService } from "../../services/questionService";
import { errorMessage, useMockApi } from "../../services/serviceMode";
export default function ExamGeneratorPage({ setActive, notify }: { setActive: (page: string) => void; notify: (message: string) => void }) {
  const {data:curriculum,error:catalogError}=useCurriculum();
  const [courseId,setCourseId]=useState(selection.courseId);
  const [chapter, setChapter] = useState(Object.keys(curriculum)[0] || ""), [topic, setTopic] = useState(curriculum[chapter]?.[0] || "");
  const [matrix, setMatrix] = useState<Matrix>({ KNOWLEDGE: { MCQ: 5, ESSAY: 1 }, UNDERSTANDING: { MCQ: 4, ESSAY: 1 }, APPLICATION: { MCQ: 3, ESSAY: 2 } });
  const [exam, setExam] = useState<Exam>(), [saved, setSaved] = useState<Exam[]>([]), [busy, setBusy] = useState(false), [error, setError] = useState("");
  const [editing,setEditing]=useState<Question>();
  useEffect(() => { questionService.listExams().then(setSaved).catch(e => setError(errorMessage(e))); }, []);
  function updateQuestions(questions: Question[]) { const next:Matrix={KNOWLEDGE:{MCQ:0,ESSAY:0},UNDERSTANDING:{MCQ:0,ESSAY:0},APPLICATION:{MCQ:0,ESSAY:0}};questions.forEach(q=>next[q.cognitiveLevel][q.type]++);setMatrix(next);setExam(e=>e?{...e,questions,matrix:next}:e); }
  useEffect(()=>{const first=Object.keys(curriculum)[0];if(!first)return;if(!curriculum[chapter]){setChapter(first);setTopic(curriculum[first][0]);}else if(!curriculum[chapter].includes(topic))setTopic(curriculum[chapter][0]);},[curriculum,chapter,topic]);
  async function generate() { setBusy(true); setError(""); try { setExam(await questionService.generateExam(chapter, topic, matrix, courseId || undefined)); } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); } }
  async function save() { if (!exam) return; setBusy(true); setError(""); try { setExam(await questionService.saveExam(exam)); setSaved(await questionService.listExams()); notify("Đã lưu đề và câu hỏi vào ngân hàng."); } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); } }
  return <div className="page"><PageHeading eyebrow="CÔNG CỤ DÀNH CHO GIÁO VIÊN" title="Tạo đề theo ma trận" subtitle="Chọn số câu trắc nghiệm và tự luận theo mức độ nhận thức." action={<Button variant="secondary" onClick={() => setActive("questions")}>Ngân hàng câu hỏi</Button>}/><section className="card lesson-editor"><div className="form-row"><label>Chương<CourseChapterSelect courseId={courseId} chapter={chapter} chapters={Object.keys(curriculum).map(c=>({value:c,title:c}))} disabled={busy} onChange={(id,next)=>{setCourseId(id);setChapter(next);setTopic(curriculum[next]?.[0] || "");}}/></label><label>Chủ đề<select value={topic} disabled={busy} onChange={e => setTopic(e.target.value)}>{curriculum[chapter]?.map(t => <option key={t}>{t}</option>)}</select></label></div><ExamMatrix value={matrix} onChange={setMatrix} disabled={busy}/>{useMockApi && <p className="feature-note">Mock tạo câu hỏi mẫu theo chương/chủ đề và lưu mức độ nhận thức. Giáo viên cần kiểm tra nội dung trước khi sử dụng.</p>}<div className="feature-actions"><Button icon="spark" onClick={() => void generate()} disabled={busy || !chapter || !topic || !useMockApi && !courseId}>{busy ? "Đang xử lý…" : "Tạo đề"}</Button>{exam && <Button variant="secondary" onClick={() => void save()} disabled={busy}>Lưu đề</Button>}</div>{(error || catalogError) && <p className="feature-error" role="alert">{error || catalogError}</p>}</section>{saved.length > 0 && <section className="card lesson-editor feature-section"><h2>Đề đã lưu</h2><div className="feature-actions">{saved.map(e => <Button key={e.id} variant="secondary" onClick={() => {setExam(e);setMatrix(e.matrix);}}>{e.title} · {e.questions.length} câu</Button>)}</div></section>}{exam && <section className="feature-section"><div className="section-head"><h2>{exam.title} · {exam.questions.length} câu</h2></div>{exam.questions.map((q, i) => <QuestionPreview key={q.id} question={q} number={i + 1} actions={<><button onClick={()=>setEditing(q)}>Sửa</button><button className="danger-link" onClick={()=>updateQuestions(exam.questions.filter(item=>item.id!==q.id))}>Xóa</button></>}/>)}</section>}{editing&&<GeneratedQuestionEditor key={editing.id} question={editing} onClose={()=>setEditing(undefined)} onSave={q=>{if(exam)updateQuestions(exam.questions.map(old=>old.id===q.id?q:old));setEditing(undefined);}}/>}</div>;
}
