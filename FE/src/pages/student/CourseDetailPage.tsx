import { useResource } from "../../services/useResource";
import { lmsService, selection } from "../../services/lmsService";
import { useMockApi } from "../../services/serviceMode";
import { lessonService } from "../../services/lessonService";
import Icon from "../../components/common/Icon";
import Button from "../../components/common/Button";
import Badge from "../../components/common/Badge";
import Progress from "../../components/common/Progress";
import Breadcrumb from "../../components/common/Breadcrumb";

export default function ChapterDetail({ setActive }: { setActive: (p: string) => void }) {
  const { data: records, error } = useResource(lmsService.lessons, []);
  const { data: chapters } = useResource(lmsService.chapters, []);
  const chapter = chapters.find(c => c.id === selection.chapterId) || chapters[0];
  const lessons = useMockApi ? lessonService.getLessons() : records.map(l => [l.title, `${Math.ceil(l.durationSeconds / 60)} phút`, l.completed] as const);
  const progress = useMockApi ? 40 : Math.round(chapter?.progress || 0);
  const open = (i = 0) => { selection.lessonId = records[i]?.id || 0; setActive("lesson"); };
  return <div className="page"><Breadcrumb items={[["Trang chủ","dashboard"],["Toán 11","courses"],["Chương 1","chapter"]]} setActive={setActive}/>{error && <p className="feature-error" role="alert">{error}</p>}<div className="chapter-hero"><div><Badge tone="purple">CHƯƠNG {useMockApi ? 1 : chapter?.position || "—"}</Badge><h1>{useMockApi ? "Hàm số lượng giác và phương trình lượng giác" : chapter?.title || "Đang tải…"}</h1><p>{useMockApi ? "Khám phá các hàm số lượng giác, tính chất và phương pháp giải phương trình cơ bản." : "Nội dung bài học trong chương đã chọn."}</p><div><span><Icon name="play" size={16}/> {useMockApi ? 5 : lessons.length} bài học</span><span><Icon name="file" size={16}/> {useMockApi ? 24 : chapter?.exerciseCount || 0} bài tập</span><span><Icon name="clock" size={16}/> {useMockApi ? "2 giờ 10 phút" : `${Math.round(records.reduce((n,l)=>n+l.durationSeconds,0)/60)} phút`}</span></div></div><div className="chapter-ring"><strong>{progress}%</strong><span>Hoàn thành</span></div></div>
    <div className="chapter-detail-grid"><section className="card lesson-list-card"><div className="section-head"><div><h2>Danh sách bài học</h2><p>Học tuần tự để đạt kết quả tốt nhất</p></div></div>{lessons.map((l,i)=><button className={`lesson-row ${(useMockApi ? i === 2 : records[i]?.id === selection.lessonId) ? "current" : ""}`} key={String(l[0])} onClick={() => open(i)}><span className="lesson-play"><Icon name={l[2] ? "check" : "play"} size={18}/></span><span><strong>{l[0]}</strong><small><Icon name="clock" size={13}/> {l[1]}</small></span><span>{l[2] ? <Badge tone="green">Đã hoàn thành</Badge> : (useMockApi ? i === 2 : records[i]?.id === selection.lessonId) ? <span className="btn btn-primary"><span>Bắt đầu học</span></span> : <Icon name="arrow" size={17}/>}</span></button>)}</section><aside className="card chapter-side"><h2>Tiến độ chương</h2><Progress value={progress} tone="purple"/><div><span>Bài học</span><strong>{useMockApi ? "2/5" : `${records.filter(l=>l.completed).length}/${records.length}`}</strong></div><div><span>Bài tập</span><strong>{useMockApi ? "8/24" : chapter?.exerciseCount || 0}</strong></div><Button className="full" onClick={() => open(Math.max(0, records.findIndex(l => !l.completed)))}>Tiếp tục học</Button><div className="ai-recommend"><Icon name="spark"/><p><b>Lộ trình học</b>{useMockApi ? "Hoàn thành Bài 3 trước khi làm bài luyện tập." : "Hoàn thành bài học trước khi làm bài luyện tập."}</p></div></aside></div>
  </div>;
}
