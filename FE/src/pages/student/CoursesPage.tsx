import { useEffect, useState } from "react";
import { useResource } from "../../services/useResource";
import { lmsService, selection } from "../../services/lmsService";
import { useMockApi } from "../../services/serviceMode";
import { courseService } from "../../services/courseService";
import Icon from "../../components/common/Icon";
import Button from "../../components/common/Button";
import Badge from "../../components/common/Badge";
import Progress from "../../components/common/Progress";
import Breadcrumb from "../../components/common/Breadcrumb";
import PageHeading from "../../components/common/PageHeading";

export default function Courses({ setActive }: { setActive: (p: string) => void }) {
  const [currentCourse,setCurrentCourse]=useState(selection.courseId);
  const {data:courses}=useResource(lmsService.courses,[]);
  useEffect(()=>{if(!useMockApi&&courses.length&&!currentCourse){selection.courseId=courses[0].id;setCurrentCourse(courses[0].id);}},[courses]);
  const chosen=courses.find(c=>c.id===currentCourse);
  const { data: chapters, error } = useResource(() => courseService.list(), courseService.getCourses(),[currentCourse]);
  const { data: catalog } = useResource(lmsService.chapters, [],[currentCourse]);
  const progress = chapters.length ? Math.round(chapters.reduce((n,c) => n+c[4],0)/chapters.length) : 0;
  return <div className="page"><Breadcrumb items={[["Trang chủ","dashboard"],["Toán 11","courses"]] } setActive={setActive}/><PageHeading eyebrow="KHÓA HỌC CỦA TÔI" title={useMockApi ? "Toán lớp 11" : chosen?.title || "Khóa học của tôi"} subtitle="Nắm vững kiến thức, tự tin chinh phục mọi kỳ thi." action={<Button icon="play" onClick={() => setActive("lesson")}>Tiếp tục học</Button>}/>
    {error && <p className="feature-error" role="alert">{error}</p>}<div className="tabs"><button className="active">Tổng quan</button><button onClick={() => setActive("chapter")}>Bài giảng</button><button onClick={() => setActive("exercises")}>Bài tập</button><button onClick={() => setActive("progress")}>Tiến độ</button></div>
    <div className="course-banner"><div><Badge tone="purple">CHƯƠNG TRÌNH GDPT 2018</Badge><h2>{useMockApi ? "Toán 11 · Học kỳ I" : chosen?.title || "Đang tải…"}</h2><p>{useMockApi ? "5 chương · 57 bài học · 104 bài tập" : `${chapters.length} chương · ${catalog.reduce((n,c)=>n+c.lessonCount,0)} bài học · ${catalog.reduce((n,c)=>n+c.exerciseCount,0)} bài tập`}</p><div className="banner-progress"><span>Tiến độ tổng thể</span><strong>{useMockApi ? 42 : progress}%</strong><Progress value={useMockApi ? 42 : progress}/></div></div><div className="math-art"><span>f(x)</span><b>∫</b><i>π</i></div></div>
    <div className="section-head chapters-head"><div><h2>Nội dung khóa học</h2><p>Tiếp tục học theo lộ trình được đề xuất</p></div><select value={currentCourse} onChange={e=>{selection.courseId=Number(e.target.value);selection.chapterId=0;selection.lessonId=0;setCurrentCourse(Number(e.target.value));}}>{useMockApi ? <option value={0}>Sắp xếp theo chương</option> : courses.map(c=><option key={c.id} value={c.id}>{c.title}</option>)}</select></div>
    <div className="chapter-list">{chapters.map((c, i) => <div className={`chapter-card ${(useMockApi ? i === 1 : c[4] > 0 && c[4] < 100) ? "featured" : ""}`} key={c[0]} onClick={() => { selection.chapterId = catalog[i]?.id || 0; setActive("chapter"); }}><div className="chapter-number">{c[0]}</div><div className="chapter-body"><div className="chapter-title">{(useMockApi ? i === 1 : c[4] > 0 && c[4] < 100) && <Badge tone="purple">ĐANG HỌC</Badge>}<h3>{c[1]}</h3><p><span><Icon name="play" size={15}/> {c[2]}</span><span><Icon name="file" size={15}/> {c[3]}</span></p></div><div className="chapter-progress"><div><span>{c[4] === 100 ? "Hoàn thành" : c[4] ? "Tiến độ" : "Chưa bắt đầu"}</span><strong>{c[4]}%</strong></div><Progress value={Number(c[4])} tone={c[4] === 100 ? "green" : "purple"}/></div><Button variant={c[4] ? "secondary" : "ghost"} onClick={() => { selection.chapterId = catalog[i]?.id || 0; setActive("chapter"); }}>{c[4] === 100 ? "Xem lại" : c[4] ? "Tiếp tục" : "Bắt đầu"} <Icon name="arrow" size={16}/></Button></div></div>)}</div>
  </div>;
}
