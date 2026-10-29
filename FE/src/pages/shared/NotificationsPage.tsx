import { useState } from "react";
import { useMockApi, errorMessage } from "../../services/serviceMode";
import { notificationService, type NotificationRecord } from "../../services/notificationService";
import Icon from "../../components/common/Icon";
import PageHeading from "../../components/common/PageHeading";

export default function NotificationsPage({records:data,error:loadError,loading,onRead}:{records:NotificationRecord[];error:string;loading:boolean;onRead:(id:number)=>void}) {
  const [mutationError,setError]=useState("");const error=mutationError || loadError;
  const read=async(id:number)=>{if(data.find(row=>row.id===id)?.isRead)return;setError("");try{await notificationService.read(id);onRead(id);}catch(e){setError(errorMessage(e));}};
  return <div className="page"><PageHeading eyebrow="CẬP NHẬT MỚI NHẤT" title="Thông báo" subtitle="Theo dõi bài tập, kết quả và hoạt động học tập."/>{error&&<p className="feature-error" role="alert">{error}</p>}<section className="card notification-page">{!useMockApi && loading && <p role="status">Đang tải thông báo…</p>}{!useMockApi && !loading && !error && !data.length && <p className="feature-note">Chưa có thông báo.</p>}{(useMockApi ? ["Bạn đã được giao bài tập mới.","Bài kiểm tra Chương 1 đã được chấm.","Bạn đã hoàn thành Chương 1.","Giáo viên đã thêm bài giảng mới."] : data.map(n=>n.content)).map((n,i)=><div className={(useMockApi ? i<2 : !data[i]?.isRead)?"unread":""} key={useMockApi?n:data[i].id} onClick={()=>!useMockApi&&void read(data[i].id)}><span className={`activity-icon ${i===0?"purple":""}`}><Icon name={i===0?"file":i===1?"award":"bell"}/></span><p><strong>{n}</strong><small>{useMockApi ? ["5 phút trước","1 giờ trước","Hôm qua","2 ngày trước"][i] : new Date(data[i].createdAt).toLocaleString("vi-VN")}</small></p>{(useMockApi?i<2:!data[i]?.isRead)&&<i/>}</div>)}</section></div>;
}
