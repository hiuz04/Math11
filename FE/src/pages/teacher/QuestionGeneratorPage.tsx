import { useEffect, useState } from "react";
import CourseChapterSelect from "../../components/teacher/CourseChapterSelect";
import { selection } from "../../services/lmsService";
import Icon from "../../components/common/Icon";
import Button from "../../components/common/Button";
import PageHeading from "../../components/common/PageHeading";
import CognitiveLevelSelector from "../../components/teacher/CognitiveLevelSelector";
import QuestionPreview from "../../components/teacher/QuestionPreview";
import EmptyState from "../../components/common/EmptyState";
import GeneratedQuestionEditor from "../../components/teacher/GeneratedQuestionEditor";
import { useCurriculum } from "../../services/useCurriculum";
import { questionService } from "../../services/questionService";
import { errorMessage, useMockApi } from "../../services/serviceMode";
import type { CognitiveLevel, Question, QuestionType } from "../../types/lms";
export default function Generator({ notify, setActive }: { notify: (m: string) => void; setActive: (p: string) => void }) {
  const {data:curriculum,error:catalogError}=useCurriculum();
  const [courseId, setCourseId] = useState(selection.courseId);
  const [questions, setQuestions] = useState<Question[]>([]), [loading, setLoading] = useState(false), [error, setError] = useState("");
  const [editing, setEditing] = useState<Question>();
  const [count, setCount] = useState(5), [chapter, setChapter] = useState(Object.keys(curriculum)[0] || "");
  const [topic, setTopic] = useState(curriculum[chapter]?.[0] || ""), [level, setLevel] = useState<CognitiveLevel>("KNOWLEDGE");
  const [type, setType] = useState<QuestionType>("MCQ"), [difficulty, setDifficulty] = useState<Question["difficulty"]>("MEDIUM"), [instructions, setInstructions] = useState("Tạo câu hỏi phù hợp học sinh lớp 11.");
  useEffect(()=>{const first=Object.keys(curriculum)[0];if(!first)return;if(!curriculum[chapter]){setChapter(first);setTopic(curriculum[first][0]);}else if(!curriculum[chapter].includes(topic))setTopic(curriculum[chapter][0]);},[curriculum,chapter,topic]);
  async function generate() {
    setLoading(true); setError("");
    try { setQuestions(await questionService.generate({ chapter, topic, questionType: type, cognitiveLevel: level, count, difficulty, instructions, courseId: courseId || undefined })); }
    catch (e) { setError(errorMessage(e)); } finally { setLoading(false); }
  }
  async function regenerate(question: Question) {
    if (loading) return;
    setLoading(true); setError("");
    try {
      const [next] = await questionService.generate({ chapter: question.chapter, topic: question.topic, questionType: question.type, cognitiveLevel: question.cognitiveLevel, count: 1, difficulty: question.difficulty, instructions, courseId: question.courseId });
      if (!next) throw new Error("AI không trả về câu hỏi thay thế.");
      setQuestions(current => current.map(q => q.id === question.id ? { ...next, id: question.id } : q));
    } catch (e) { setError(errorMessage(e)); } finally { setLoading(false); }
  }
  async function save() { setLoading(true); try { await questionService.save(questions); notify(`Đã thêm ${questions.length} câu hỏi vào ngân hàng.`); setActive("questions"); } catch (e) { setError(errorMessage(e)); } finally { setLoading(false); } }
  return <div className="page"><PageHeading eyebrow="CÔNG CỤ DÀNH CHO GIÁO VIÊN" title="Tạo câu hỏi bằng AI" subtitle="Thiết lập yêu cầu và để AI hỗ trợ xây dựng bộ câu hỏi."/>
    <div className="generator-grid"><section className="card generator-form"><div className="section-head"><div><h2>Thiết lập nội dung</h2><p>Chọn tiêu chí cho bộ câu hỏi</p></div><div className="ai-icon"><Icon name="spark"/></div></div><label>Chương<CourseChapterSelect courseId={courseId} chapter={chapter} chapters={Object.keys(curriculum).map(c=>({value:c,title:c}))} disabled={loading} onChange={(id,next)=>{setCourseId(id);setChapter(next);setTopic(curriculum[next]?.[0] || "");}}/></label><label>Chủ đề<select value={topic} onChange={e => setTopic(e.target.value)}>{curriculum[chapter]?.map(t => <option key={t}>{t}</option>)}</select></label><div className="form-row"><label>Độ khó<select value={difficulty} onChange={e => setDifficulty(e.target.value as Question["difficulty"])}><option value="MEDIUM">Trung bình</option><option value="EASY">Dễ</option><option value="HARD">Khó</option></select></label><label>Loại câu hỏi<select value={type} onChange={e => setType(e.target.value as QuestionType)}><option value="MCQ">Trắc nghiệm</option><option value="ESSAY">Tự luận</option></select></label></div><CognitiveLevelSelector value={level} onChange={setLevel}/><label>Số lượng câu hỏi<div className="number-control"><button aria-label="Giảm số câu" disabled={loading} onClick={() => setCount(Math.max(1, count - 1))}>−</button><strong>{count}</strong><button aria-label="Tăng số câu" disabled={loading} onClick={() => setCount(Math.min(100, count + 1))}>+</button></div></label><label>Yêu cầu bổ sung<textarea value={instructions} onChange={e => setInstructions(e.target.value)}/></label><Button icon="spark" className="full" onClick={() => void generate()} disabled={loading || !chapter || !topic || !useMockApi && !courseId}>{loading ? "AI đang tạo câu hỏi…" : "Tạo câu hỏi bằng AI"}</Button>{useMockApi && <p className="feature-note">Đang dùng câu hỏi mẫu. Yêu cầu bổ sung được gửi khi kết nối backend AI.</p>}{(error || catalogError) && <p className="feature-error" role="alert">{error || catalogError}</p>}</section>
      <section className="generated-area">{loading ? <div className="empty-state generating"><span className="spinner large"/><h2>Đang xử lý câu hỏi…</h2></div> : !questions.length ? <EmptyState title="Câu hỏi sẽ xuất hiện tại đây" description="Điền các tiêu chí bên trái và nhấn Tạo câu hỏi để bắt đầu."/> : <><div className="section-head"><div><h2>{questions.length} câu hỏi đã tạo</h2><p>Kiểm tra trước khi lưu</p></div><Button variant="secondary" onClick={() => void save()}>Thêm {questions.length} câu vào ngân hàng</Button></div>{questions.map((q, i) => <QuestionPreview key={q.id} question={q} number={i + 1} actions={<><button onClick={() => setEditing(structuredClone(q))}>Chỉnh sửa</button><button onClick={() => void regenerate(q)}>Tạo lại</button><button className="danger-link" onClick={() => setQuestions(questions.filter(item => item.id !== q.id))}>Xóa</button></>}/>)}</>}</section></div>
    {editing && <GeneratedQuestionEditor key={editing.id} question={editing} onClose={() => setEditing(undefined)} onSave={next => { setQuestions(questions.map(q => q.id === next.id ? next : q)); setEditing(undefined); }}/>}
  </div>;
}
