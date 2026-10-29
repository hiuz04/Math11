import { useEffect, useState } from "react";
import { dashboardService } from "../../services/dashboardService";
import { useResource } from "../../services/useResource";
import { useMockApi, errorMessage } from "../../services/serviceMode";
import { lmsService, selection } from "../../services/lmsService";
import { authService } from "../../services/authService";
import Icon from "../../components/common/Icon";
import Button from "../../components/common/Button";
import Badge from "../../components/common/Badge";
import Progress from "../../components/common/Progress";
import PageHeading from "../../components/common/PageHeading";
import type { IconName } from "../../types/icon";

export default function ManagementDashboard({ role, setActive }: { role: string; setActive: (p: string) => void }) {
  const teacher = role === "Giáo viên";
  const [statistics, setStatistics] = useState(() => useMockApi ? dashboardService.getMockStatistics(role) : []);
  const [error,setError]=useState("");
  const {data:analytics}=useResource(lmsService.managementAnalytics,undefined);
  const trend=analytics?.trend || [];
  const max=Math.max(1,...trend.map(t=>t.value));
  const points=trend.map((t,i)=>[(i*700/Math.max(1,trend.length-1)),220-t.value/max*190]);
  const line=points.map((p,i)=>(i?"L":"M")+p[0]+" "+p[1]).join(" ");
  const distribution=analytics?.distribution || [];
  const sum=distribution.reduce((n,d)=>n+d.count,0);
  useEffect(() => { let live = true; dashboardService.getStatistics(role).then(s => { if (live) setStatistics(s); }).catch(e => { if(live) setError(errorMessage(e)); }); return () => { live = false; }; }, [role]);
  const data = statistics.map(s => [s.icon, s.value, s.label, s.note, s.tone]);
  return <div className="page"><PageHeading eyebrow={teacher ? "TRUNG TÂM GIẢNG DẠY" : "QUẢN TRỊ HỆ THỐNG"} title={teacher ? (useMockApi ? "Chào buổi sáng, cô Lan!" : `Xin chào, ${authService.current()?.name || "giáo viên"}!`) : "Tổng quan hệ thống"} subtitle={teacher ? "Đây là tình hình học tập của học sinh hôm nay." : "Theo dõi hoạt động và tăng trưởng của nền tảng Mathio."} action={<Button icon="plus" onClick={()=>{selection.editLessonId=0;setActive(teacher?"create-lesson":"users");}}>{teacher ? "Tạo bài giảng" : "Thêm người dùng"}</Button>}/>
    {error&&<p className="feature-error" role="alert">{error}</p>}<div className="stat-grid">{data.map(s => <div className="stat-card" key={s[2]}><div className={`stat-icon ${s[4]}`}><Icon name={s[0] as IconName}/></div><div className="stat-main"><span>{s[2]}</span><strong>{s[1]}</strong></div><Badge tone={s[4] === "purple" ? "purple" : s[4] === "orange" ? "orange" : "green"}>{s[3]}</Badge></div>)}</div>
    <div className="analytics-grid"><section className="card large-chart"><div className="section-head"><div><h2>{teacher ? "Hiệu suất học tập của học sinh" : "Tăng trưởng người dùng"}</h2><p>{teacher ? "Điểm trung bình theo tuần" : "Số người dùng mới trong 6 tháng"}</p></div><select><option>6 tháng qua</option></select></div><svg className="line-chart" viewBox="0 0 700 240" preserveAspectRatio="none"><path className="grid-line" d="M0 40H700M0 100H700M0 160H700M0 220H700"/><path className="area" d={useMockApi ? "M0 200 C100 190,100 155,180 165 S280 130,350 140 S450 100,520 110 S620 60,700 45 L700 240 L0 240Z" : points.length ? `${line} L700 240 L0 240Z` : ""}/><path className="line" d={useMockApi ? "M0 200 C100 190,100 155,180 165 S280 130,350 140 S450 100,520 110 S620 60,700 45" : line}/></svg><div className="chart-labels">{(useMockApi ? ["Tháng 1","Tháng 2","Tháng 3","Tháng 4","Tháng 5","Tháng 6"] : trend.map(t=>t.month)).map(month=><span key={month}>{month}</span>)}</div></section>
      <section className="card topic-card"><div className="section-head"><div><h2>{teacher ? "Phân bố kết quả" : "Trạng thái hệ thống"}</h2><p>Cập nhật theo thời gian thực</p></div></div>{(useMockApi ? [["Xuất sắc",32,"green"],["Khá",44,"blue"],["Cần cải thiện",24,"purple"]] : distribution.map((d,i)=>[d.label,sum?Math.round(d.count/sum*100):0,["green","blue","purple"][i%3]])).map(t => <div className="topic-row" key={t[0]}><div><span>{t[0]}</span><strong>{t[1]}%</strong></div><Progress value={Number(t[1])} tone={t[2] as "green"|"blue"|"purple"}/></div>)}<div className="system-ok"><Icon name="shield"/><div><strong>{useMockApi ? "Hệ thống hoạt động tốt" : error ? "Không tải được dữ liệu" : "Thống kê đã ghi nhận"}</strong><span>{useMockApi ? "Không có sự cố nào" : analytics?.aiUsage!==undefined ? `${analytics.aiUsage} yêu cầu AI` : "Dữ liệu trong phạm vi giảng dạy"}</span></div></div></section></div>
    <section className="card recent-card"><div className="section-head"><div><h2>Hoạt động gần đây</h2><p>Những cập nhật mới nhất trong hệ thống</p></div><button className="text-btn" onClick={() => setActive(teacher ? "students" : "users")}>Xem tất cả <Icon name="arrow" size={16}/></button></div><div className="activity-list">{(useMockApi ? [
      ["users","Nguyễn Minh Anh","đã hoàn thành bài kiểm tra Chương 2","2 phút trước"],
      ["book",teacher ? "Cô Trần Thu Hà" : "Thầy Nguyễn Văn Hùng","đã xuất bản bài giảng mới","18 phút trước"],
      ["award","Lê Hoàng Nam","đạt điểm 10 bài Hàm số lượng giác","1 giờ trước"],
      ["file","Lớp 11A2","đã được giao bài tập mới","2 giờ trước"],
    ] : analytics?.activities.map(h=>["file",h.name || "",h.title+" · "+h.status,new Date(h.date).toLocaleString("vi-VN")]) || []).map(a=><div className="activity-row" key={a[3]}><div className="activity-icon"><Icon name={a[0] as IconName}/></div><div><strong>{a[1]}</strong> <span>{a[2]}</span><small>{a[3]}</small></div><button className="icon-btn"><Icon name="more"/></button></div>)}</div></section>
  </div>;
}
