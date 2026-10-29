import { useEffect, useRef, useState } from "react";
import { authService } from "../../services/authService";
import { useMockApi } from "../../services/serviceMode";
import type { NotificationRecord } from "../../services/notificationService";
import Icon from "../common/Icon";
import Badge from "../common/Badge";
import { getSearchQuery, setSearchQuery } from "../../services/routeState";

export default function Header({ role, openMenu, setActive, onLogout, notifications, notificationError }: { role: string; openMenu: () => void; setActive: (p: string) => void; onLogout: () => void; notifications:NotificationRecord[]; notificationError:string }) {
  const [showProfile, setShowProfile] = useState(false);
  const [showNotifications, setShowNotifications] = useState(false);
  const [query,setQuery]=useState(getSearchQuery);
  const search = useRef<HTMLInputElement>(null);
  useEffect(()=>{const focus=(event:KeyboardEvent)=>{if((event.metaKey||event.ctrlKey)&&event.key.toLowerCase()==="k"){event.preventDefault();search.current?.focus();}};window.addEventListener("keydown",focus);return()=>window.removeEventListener("keydown",focus);},[]);
  const name = !useMockApi ? authService.current()?.name || "—" : role === "Giáo viên" ? "Nguyễn Thị Lan" : role === "Quản trị viên" ? "Trần Văn Minh" : "Nguyễn Minh Anh";
  return <header className="topbar">
    <button className="icon-btn menu-btn" onClick={openMenu}><Icon name="menu"/></button>
    <div className="search-box"><Icon name="search" size={18}/><input maxLength={200} ref={search} placeholder="Tìm bài học, bài tập..." value={query} onChange={e=>setQuery(e.target.value)} onKeyDown={e=>{if(e.key==="Enter"){setSearchQuery(query);setActive("search");}}}/><kbd>⌘ K</kbd></div>
    <div className="header-actions">
      <div className="top-popover-wrap"><button className="icon-btn notification" onClick={() => { setShowNotifications(!showNotifications); setShowProfile(false); }}><Icon name="bell"/>{(useMockApi || notifications.some(n=>!n.isRead)) && <i/>}</button>
      {showNotifications && <div className="top-popover notification-popover">{notificationError && <p className="feature-error" role="alert">{notificationError}</p>}<div className="popover-title"><strong>Thông báo</strong><Badge tone="blue">{useMockApi ? "3 mới" : `${notifications.filter(n=>!n.isRead).length} mới`}</Badge></div>{(useMockApi ? ["Bạn đã được giao bài tập mới.","Bài kiểm tra Chương 1 đã được chấm.","Giáo viên đã thêm bài giảng mới."] : notifications.slice(0,3).map(n=>n.content)).map((n,i)=><button key={n} onClick={() => setActive("notifications")}><i className={(useMockApi ? i < 2 : !notifications[i].isRead) ? "unread-dot" : ""}/><span><strong>{n}</strong><small>{useMockApi ? (i === 0 ? "5 phút trước" : i === 1 ? "1 giờ trước" : "Hôm qua") : new Date(notifications[i].createdAt).toLocaleString("vi-VN")}</small></span></button>)}<button className="popover-footer" onClick={() => { setActive("notifications"); setShowNotifications(false); }}>Xem tất cả thông báo</button></div>}</div>
      <div className="role-wrap">
        <button className="profile-chip" onClick={() => { setShowProfile(!showProfile); setShowNotifications(false); }}>
          <span className="avatar">{!useMockApi ? name.split(" ").slice(-2).map(part=>part[0]).join("") : role === "Giáo viên" ? "NL" : role === "Quản trị viên" ? "TM" : "NA"}</span><span className="profile-name"><strong>{name}</strong><small>{role}</small></span><Icon name="chevron" size={16}/>
        </button>
        {showProfile && <div className="role-menu">
          <span>TÀI KHOẢN</span>
          <button onClick={() => { setActive("profile"); setShowProfile(false); }}><span>Hồ sơ cá nhân</span><Icon name="user" size={16}/></button>
          <button onClick={() => { setActive("profile"); setShowProfile(false); }}><span>Cài đặt</span><Icon name="settings" size={16}/></button>
          <button className="menu-danger" onClick={onLogout}><span>Đăng xuất</span><Icon name="logout" size={16}/></button>
        </div>}
      </div>
    </div>
  </header>;
}
