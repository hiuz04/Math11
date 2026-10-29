import Icon from "../../components/common/Icon";
import { useMockApi } from "../../services/serviceMode";
import ApiStudentDetailPage from "./ApiStudentDetailPage";
import Button from "../../components/common/Button";
import Badge from "../../components/common/Badge";
import Progress from "../../components/common/Progress";
import Breadcrumb from "../../components/common/Breadcrumb";
import type { IconName } from "../../types/icon";

export default function StudentDetail({ setActive }: { setActive:(p:string)=>void }) {
  if(!useMockApi) return <ApiStudentDetailPage setActive={setActive}/>;
  return <div className="page"><Breadcrumb items={[["Dashboard","teacher"],["Học sinh","students"],["Nguyễn Minh Anh","student-detail"]]} setActive={setActive}/><div className="student-detail-head"><span className="profile-avatar">NA</span><div><h1>Nguyễn Minh Anh</h1><p>Lớp 11A1 · student@demo.vn</p></div><Badge tone="green">Đang hoạt động</Badge></div><div className="stat-grid">{[["chart","72%","Tiến độ"],["award","8.5","Điểm trung bình"],["clock","16 giờ","Thời gian học"],["check","12/24","Bài học hoàn thành"]].map(x=><div className="stat-card" key={x[2]}><span className="stat-icon blue"><Icon name={x[0] as IconName}/></span><div className="stat-main"><span>{x[2]}</span><strong>{x[1]}</strong></div></div>)}</div><div className="analytics-grid"><section className="card topic-card"><h2>Tiến độ theo chương</h2>{[["Chương 1",100],["Chương 2",65],["Chương 3",20]].map(x=><div className="topic-row" key={x[0]}><div><span>{x[0]}</span><strong>{x[1]}%</strong></div><Progress value={Number(x[1])}/></div>)}</section><section className="card ai-insight"><Icon name="spark"/><h2>Nhận xét từ AI</h2><p>Học sinh đang gặp khó khăn với phương trình lượng giác, đặc biệt dạng có sin(2x).</p><Button variant="secondary">Xem chi tiết</Button></section></div><section className="card recent-card"><h2>Hoạt động gần đây</h2><div className="activity-list">{["Hoàn thành bài Hàm số lượng giác","Đạt 8.5 điểm bài luyện tập","Hỏi AI về phương trình sin 2x"].map((x,i)=><div className="activity-row" key={x}><span className="activity-icon"><Icon name={i===0?"check":i===1?"award":"spark"}/></span><div><strong>{x}</strong><small>{i===0?"Hôm nay, 14:32":i===1?"Hôm qua, 20:15":"2 ngày trước"}</small></div></div>)}</div></section></div>;
}
