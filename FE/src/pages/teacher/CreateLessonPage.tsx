import { useEffect, useState } from "react";
import { useResource } from "../../services/useResource";
import { lmsService, selection } from "../../services/lmsService";
import { useMockApi } from "../../services/serviceMode";
import CourseChapterSelect from "../../components/teacher/CourseChapterSelect";
import Button from "../../components/common/Button";
import Badge from "../../components/common/Badge";
import Breadcrumb from "../../components/common/Breadcrumb";
import PageHeading from "../../components/common/PageHeading";
import RichTextEditor from "../../components/common/RichTextEditor";
import { lessonService } from "../../services/lessonService";
import { errorMessage } from "../../services/serviceMode";

export default function CreateLesson({ setActive, notify }: { setActive: (p:string)=>void; notify:(m:string)=>void }) {
  const { data: chapters } = useResource(lmsService.chapters, []);
  const [metadata, setMetadata] = useState({ title: useMockApi ? "Hàm số lượng giác và ứng dụng" : "", courseId:selection.courseId,contentType:"TEXT",chapterId: useMockApi ? 1 : 0, description: useMockApi ? "Giới thiệu định nghĩa, tính chất và đồ thị của các hàm số lượng giác." : "", objectives: useMockApi ? "Nhận biết hàm số lượng giác\nVận dụng đồ thị để giải bài toán" : "", videoUrl: "" });
  useEffect(() => { if (!useMockApi && selection.editLessonId) lmsService.lesson(selection.editLessonId).then(l => { setMetadata({ title: l.title, courseId:l.courseId,contentType:l.contentType || "TEXT",chapterId: l.chapterId, description: l.description, objectives: l.objectives || "", videoUrl: l.videoUrl || "" }); setContent(l.content || ""); }).catch(e => setError(errorMessage(e))); }, []);
  useEffect(()=>{if(!useMockApi&&!selection.editLessonId&&chapters.length&&!chapters.some(c=>c.id===metadata.chapterId))setMetadata(m=>({...m,chapterId:chapters[0].id}));},[chapters]);
  const [saving,setSaving]=useState(false);
  const [content, setContent] = useState(lessonService.getDraft);
  const [preview, setPreview] = useState(false);
  const [error, setError] = useState("");
  const save = async (published = false) => {
    if (!useMockApi && !metadata.courseId) { setError("Hãy chọn chương trong khóa học cần lưu."); return; }
    setSaving(true); setError("");
    try { await lessonService.saveContent(content, published, metadata); notify(published ? "Bài giảng đã được xuất bản thành công." : "Đã lưu bản nháp."); if (published) setActive("lessons"); }
    catch (e) { setError(errorMessage(e)); } finally { setSaving(false); }
  };
  const publish = () => void save(true);
  return <div className="page"><Breadcrumb items={[["Dashboard","teacher"],["Bài giảng","lessons"],["Thêm bài giảng","create-lesson"]]} setActive={setActive}/><PageHeading eyebrow="QUẢN LÝ NỘI DUNG" title="Thêm bài giảng mới" subtitle="Xây dựng nội dung học tập rõ ràng và hấp dẫn."/><div className="editor-grid"><section className="card lesson-editor"><label>Tên bài giảng<input value={metadata.title} onChange={e => setMetadata({...metadata, title: e.target.value})}/></label><div className="form-row"><label>Chương<CourseChapterSelect courseId={metadata.courseId} chapter={metadata.chapterId} chapters={useMockApi ? [{value:1,title:"Chương 1 – Hàm số lượng giác"}] : chapters.map(c=>({value:c.id,title:c.title}))} disabled={saving} onChange={(courseId,chapterId)=>setMetadata(m=>({...m,courseId,chapterId}))}/></label><label>Loại nội dung<select value={metadata.contentType} onChange={e=>setMetadata({...metadata,contentType:e.target.value})}><option value="VIDEO">Video</option><option value="TEXT">Text</option><option value="YOUTUBE">YouTube</option></select></label></div><label>Mô tả<textarea value={metadata.description} onChange={e => setMetadata({...metadata, description: e.target.value})}/></label><label>Mục tiêu bài học<textarea value={metadata.objectives} onChange={e => setMetadata({...metadata, objectives: e.target.value})}/></label><label>YouTube URL<input placeholder="https://youtube.com/watch?v=..." value={metadata.videoUrl} onChange={e => setMetadata({...metadata, videoUrl: e.target.value})}/></label><label>Nội dung bài học<RichTextEditor value={content} onChange={setContent}/></label></section><aside className="card publish-panel"><h2>Xuất bản bài giảng</h2><div><span>Trạng thái</span><Badge tone="orange">Bản nháp</Badge></div><div><span>Hiển thị</span><strong>{useMockApi ? "Tất cả học sinh" : "Học sinh trong khóa học"}</strong></div><Button variant="secondary" className="full" onClick={() => setPreview(!preview)}>Xem trước</Button><Button variant="ghost" className="full" onClick={() => void save()} disabled={saving}>Lưu nháp</Button><Button className="full" onClick={publish} disabled={saving}>{saving?"Đang xuất bản...":"Xuất bản"}</Button></aside></div>{error && <p className="feature-error" role="alert">{error}</p>}{preview && <section className="card lesson-editor feature-section"><h2>Xem trước bài giảng</h2><RichTextEditor key={content} value={content} readOnly/></section>}</div>;
}
