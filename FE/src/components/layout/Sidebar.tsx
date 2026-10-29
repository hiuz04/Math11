import type { NotificationRecord } from "../../services/notificationService";
import { useMockApi } from "../../services/serviceMode";
import Icon from "../common/Icon";
import Logo from "../common/Logo";
import { studentNav } from "../../constants/navigation";
import { teacherNav } from "../../constants/navigation";
import { adminNav } from "../../constants/navigation";

export default function Sidebar({ active, setActive, role, mobileOpen, close, onLogout, notifications }: { active: string; setActive: (p: string) => void; role: string; mobileOpen: boolean; close: () => void; onLogout: () => void; notifications:NotificationRecord[] }) {
  const nav = role === "Giáo viên" ? teacherNav : role === "Quản trị viên" ? adminNav : studentNav;
  return <aside className={`sidebar ${mobileOpen ? "open" : ""}`}>
    <div className="sidebar-head"><button className="logo-button" onClick={() => setActive(role === "Giáo viên" ? "teacher" : role === "Quản trị viên" ? "admin" : "dashboard")}><Logo/></button><button className="icon-btn mobile-close" onClick={close}><Icon name="close"/></button></div>
    <div className="nav-label">KHÔNG GIAN HỌC TẬP</div>
    <nav className="nav-list">
      {nav.map((item) => <button key={item.id} className={`nav-item ${active === item.id ? "active" : ""}`} onClick={() => { setActive(item.id); close(); }}><Icon name={item.icon}/><span>{item.label}</span>{item.id === "ai" && <em>AI</em>}</button>)}
    </nav>
    <div className="sidebar-bottom">
      <button className="nav-item" onClick={() => {setActive("notifications");close();}}><Icon name="bell"/><span>Thông báo</span><b>{useMockApi ? 3 : notifications.filter(n=>!n.isRead).length}</b></button>
      <button className="nav-item" onClick={() => {setActive("profile");close();}}><Icon name="user"/><span>Hồ sơ cá nhân</span></button>
      <div className="help-card"><div className="help-icon"><Icon name="spark"/></div><strong>Cần trợ giúp?</strong><span>AI Tutor luôn sẵn sàng giải đáp</span><button onClick={() => setActive("ai")}>Bắt đầu trò chuyện</button></div>
      <button className="nav-item logout" onClick={onLogout}><Icon name="logout"/><span>Đăng xuất</span></button>
    </div>
  </aside>;
}
