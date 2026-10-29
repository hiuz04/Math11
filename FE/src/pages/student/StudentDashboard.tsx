import { useResource } from "../../services/useResource";
import { lmsService, selection } from "../../services/lmsService";
import { dashboardService } from "../../services/dashboardService";
import { useMockApi } from "../../services/serviceMode";
import Icon from "../../components/common/Icon";
import Button from "../../components/common/Button";
import Badge from "../../components/common/Badge";
import Progress from "../../components/common/Progress";
import { stats as mockStats } from "../../constants/mockData";

export default function Dashboard({ setActive }: { setActive: (p: string) => void }) {
  const {data:statistics,error}=useResource(()=>dashboardService.getStatistics("Học sinh"),[]);
  const {data:analytics}=useResource(()=>lmsService.studentAnalytics(),undefined);
  const stats=useMockApi?mockStats:statistics.map(s=>({...s,unit:""}));
  const done=analytics?.completedLessons || 0;
  const total=analytics?.chapters.reduce((n,c)=>n+c.totalLessons,0) || 0;
  const progress=total?Math.round(done/total*100):0;
  const days=new Set(analytics?.activities.map(a=>a.date.slice(0,10)) || []).size;
  return <div className="page dashboard-page">
    {error&&<p className="feature-error" role="alert">{error}</p>}<div className="welcome-row">
      <div><div className="eyebrow"><span/> {useMockApi ? "THỨ HAI, 24 THÁNG 6" : new Date().toLocaleDateString("vi-VN", {weekday:"long",day:"numeric",month:"long",timeZone:"Asia/Ho_Chi_Minh"}).toUpperCase()}</div><h1>Xin chào, {useMockApi ? "Minh Anh" : analytics?.name || "bạn"}!</h1><p>Tiếp tục hành trình chinh phục Toán 11 của bạn.</p></div>
      <div className="streak"><div className="streak-icon">{useMockApi ? 7 : days}</div><div><strong>{useMockApi ? "Chuỗi 7 ngày" : `${days} ngày hoạt động gần đây`}</strong><span>Tiếp tục phát huy nhé!</span></div></div>
    </div>
    <div className="stat-grid">
      {stats.map(s => <div className="stat-card" key={s.label}><div className={`stat-icon ${s.tone}`}><Icon name={s.icon}/></div><div className="stat-main"><span>{s.label}</span><strong>{s.value} <small>{s.unit}</small></strong></div><Badge tone={s.tone === "purple" ? "purple" : s.tone === "orange" ? "orange" : "green"}>{s.note}</Badge></div>)}
    </div>
    <div className="dashboard-grid">
      <div className="main-column">
        <section className="card continue-card">
          <div className="section-head"><div><h2>Tiếp tục học</h2><p>Hoàn thành bài học còn dang dở</p></div><button className="text-btn" onClick={() => setActive("courses")}>Xem khóa học <Icon name="arrow" size={16}/></button></div>
          <div className="course-feature">
            <div className="course-visual">
              <div className="orb orb-one"/><div className="orb orb-two"/>
              <div className="formula">u<sub>n</sub> = u<sub>1</sub> + (n−1)d</div>
              <span>{useMockApi ? "CHƯƠNG 2" : "TOÁN 11"}</span>
            </div>
            <div className="course-info">
              <Badge tone="purple">ĐANG HỌC</Badge>
              <h3>{useMockApi ? "Dãy số, cấp số cộng và cấp số nhân" : analytics?.continueLesson?.title || "Chưa có bài học tiếp theo"}</h3>
              <p>{useMockApi ? "Bài 4 · Cấp số nhân và ứng dụng" : "Tiếp tục bài giảng đã xuất bản"}</p>
              <div className="course-meta"><span><Icon name="play" size={16}/> {useMockApi ? "8/12" : `${done}/${total}`} bài học</span><strong>{useMockApi ? 65 : progress}%</strong></div>
              <Progress value={useMockApi ? 65 : progress} tone="purple"/>
              <Button disabled={!useMockApi&&!analytics?.continueLesson} onClick={() => {selection.lessonId=analytics?.continueLesson?.id || 0;setActive("lesson");}}>Tiếp tục học <Icon name="arrow" size={17}/></Button>
            </div>
          </div>
        </section>
        <section className="card chart-card">
          <div className="section-head"><div><h2>Tiến độ học tập</h2><p>{useMockApi ? "Thời gian học trong 7 ngày qua" : "Hoạt động học tập trong 7 ngày qua"}</p></div><select defaultValue="week"><option value="week">Tuần này</option><option value="month">Tháng này</option></select></div>
          <div className="chart-summary"><div><strong>{useMockApi ? "8 giờ 25 phút" : `${analytics?.elapsedHours || 0} giờ`}</strong><span><Icon name="trend" size={15}/> {useMockApi ? "12% so với tuần trước" : "Tổng thời gian đã ghi nhận"}</span></div><div className="chart-legend"><i/> {useMockApi ? "Thời gian học" : "Số hoạt động"}</div></div>
          <div className="bar-chart">
            {(useMockApi ? [42, 58, 34, 72, 48, 85, 62] : Array.from({length:7},(_,i)=>{const d=new Date();d.setDate(d.getDate()-6+i);return analytics?.activities.filter(a=>a.date.slice(0,10)===d.toISOString().slice(0,10)).length || 0;}).map(n=>Math.min(100,n*10))).map((h, i) => <div className="bar-col" key={i}><div className="bar-track"><div className={`bar ${i === 5 ? "highlight" : ""}`} style={{height: `${h}%`}}>{useMockApi&&i === 5 && <span>1h 42p</span>}</div></div><small>{["T2","T3","T4","T5","T6","T7","CN"][i]}</small></div>)}
          </div>
        </section>
        <section className="card recent-card">
          <div className="section-head"><div><h2>Bài tập gần đây</h2><p>Kết quả luyện tập mới nhất</p></div><button className="text-btn" onClick={() => setActive("exercises")}>Xem tất cả <Icon name="arrow" size={16}/></button></div>
          <div className="table-scroll"><table><thead><tr><th>BÀI TẬP</th><th>CHƯƠNG</th><th>ĐIỂM</th><th>TRẠNG THÁI</th><th>NGÀY LÀM</th></tr></thead><tbody>
{useMockApi ? <>
            <tr><td><strong>Hàm số lượng giác</strong><span>15 câu trắc nghiệm</span></td><td>Chương 1</td><td><b className="score good">9.0</b></td><td><Badge tone="green">Hoàn thành</Badge></td><td>23/06/2025</td></tr>
            <tr><td><strong>Cấp số cộng</strong><span>10 câu tự luận</span></td><td>Chương 2</td><td><b className="score mid">7.5</b></td><td><Badge tone="green">Hoàn thành</Badge></td><td>21/06/2025</td></tr>
            <tr><td><strong>Phương trình lượng giác</strong><span>12 câu trắc nghiệm</span></td><td>Chương 1</td><td><b className="score good">8.5</b></td><td><Badge tone="blue">Đã chấm</Badge></td><td>19/06/2025</td></tr>
</> : analytics?.activities.filter(a=>a.type!=="AI_TUTOR").slice(0,5).map((a,i)=><tr key={i}><td><strong>{a.title}</strong><span>{a.type==="ESSAY"?"Tự luận":"Trắc nghiệm"}</span></td><td>Toán 11</td><td><b className="score">{a.score??"—"}</b></td><td><Badge tone="blue">{a.status}</Badge></td><td>{new Date(a.date).toLocaleDateString("vi-VN")}</td></tr>)}
          </tbody></table></div>
        </section>
      </div>
      <div className="side-column">
        <section className="ai-card">
          <div className="ai-glow"/><div className="ai-icon"><Icon name="spark" size={25}/></div>
          <Badge tone="purple">TRỢ LÝ AI</Badge><h2>Bạn đang gặp khó khăn với bài toán nào?</h2><p>Nhận gợi ý từng bước để tự tìm ra đáp án.</p>
          <button className="ai-prompt" onClick={() => setActive("ai")}><span>Nhập bài toán của bạn...</span><i><Icon name="send" size={17}/></i></button>
          <div className="ai-suggestions"><span onClick={() => setActive("ai")}>Giải phương trình</span><span onClick={() => setActive("ai")}>Giải thích công thức</span></div>
        </section>
        <section className="card schedule-card">
          <div className="section-head"><div><h2>Lịch học hôm nay</h2><p>{useMockApi ? "2 hoạt động sắp tới" : "Chưa có lịch học được giao"}</p></div><button className="icon-btn"><Icon name="more"/></button></div>
{useMockApi&&<>          <div className="timeline-item"><div className="time"><strong>18:00</strong><span>45 phút</span></div><div className="timeline-line"><i/></div><div><Badge tone="blue">BÀI HỌC</Badge><strong>Giới hạn của dãy số</strong><span>Chương 3 · Bài 1</span></div></div>
          <div className="timeline-item"><div className="time"><strong>20:00</strong><span>30 phút</span></div><div className="timeline-line orange"><i/></div><div><Badge tone="orange">KIỂM TRA</Badge><strong>Ôn tập Chương 2</strong><span>15 câu · 30 phút</span></div></div>
</>}          <Button variant="secondary" icon="calendar" className="full" onClick={()=>setActive("courses")}>Xem lịch học</Button>
        </section>
        <section className="card goal-card">
          <div className="goal-ring"><svg viewBox="0 0 42 42"><circle cx="21" cy="21" r="16"/><circle className="fill" cx="21" cy="21" r="16"/></svg><strong>{useMockApi ? "4/5" : `${done}/${total}`}</strong></div>
          <div><span>{useMockApi ? "Mục tiêu tuần" : "Tiến độ khóa học"}</span><strong>{useMockApi ? "Hoàn thành 5 bài học" : `${done} bài học đã hoàn thành`}</strong><p>{useMockApi ? "Chỉ còn 1 bài nữa!" : `${Math.max(0,total-done)} bài học còn lại`}</p></div>
        </section>
      </div>
    </div>
  </div>;
}
