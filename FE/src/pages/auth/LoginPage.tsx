import { authService } from "../../services/authService";
import { demoAccounts } from "../../constants/mockData";
import { useState } from "react";
import Icon from "../../components/common/Icon";
import Button from "../../components/common/Button";
import Badge from "../../components/common/Badge";
import Logo from "../../components/common/Logo";
import { useMockApi, errorMessage } from "../../services/serviceMode";

export default function Login({ onLogin, sessionPending=false }: { onLogin: (role: string) => void; sessionPending?: boolean }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const accounts = demoAccounts;
  const submit = async () => {
    if (loading || sessionPending) return; setError(""); setLoading(true);
    try { const nextRole = await authService.login({ email, password });
      if (!nextRole) { setError("Email hoặc mật khẩu không chính xác."); return; }
      onLogin(nextRole);
    } catch (e) { setError(errorMessage(e)); } finally { setLoading(false); }
  };
  return <div className="login-page">
    <div className="login-panel">
      <Logo/>
      <div className="login-copy"><Badge tone="purple">AI-POWERED LEARNING</Badge><h1>Chào mừng trở lại</h1><p>Đăng nhập để tiếp tục hành trình chinh phục Toán 11.</p></div>
      <div className="login-form">
        <label>Email<div className="login-input"><Icon name="user" size={18}/><input value={email} onChange={e => setEmail(e.target.value)} placeholder="Nhập địa chỉ email"/></div></label>
        <label>Mật khẩu<div className="login-input"><Icon name="shield" size={18}/><input type="password" value={password} onChange={e => setPassword(e.target.value)} placeholder="Nhập mật khẩu" onKeyDown={e => e.key === "Enter" && submit()}/></div></label>
        {error && <div className="login-error">{error}</div>}
        <div className="login-options"><label><input type="checkbox"/> Ghi nhớ đăng nhập</label><button>Quên mật khẩu?</button></div>
        <Button className="full login-submit" onClick={submit} disabled={loading || sessionPending}>{loading || sessionPending ? <><span className="spinner"/> Đang đăng nhập...</> : "Đăng nhập"}</Button>
      </div>
      <div className="demo-accounts"><div className="demo-title"><span/>{useMockApi ? "Tài khoản demo" : "Tài khoản phát triển (cần seed)"}<span/></div>{accounts.map(a => <button key={a[0]} onClick={() => { setEmail(a[1]); setPassword(useMockApi ? "123456" : ""); setError(""); }}><span className="avatar">{a[2]}</span><span><strong>{a[0]}</strong><small>{a[1]}{useMockApi ? " · 123456" : " · Nhập mật khẩu đã cấu hình"}</small></span><Icon name="arrow" size={16}/></button>)}</div>
    </div>
    <div className="login-visual"><div className="visual-grid"/><div className="login-orbit orbit-a">sin²x + cos²x = 1</div><div className="login-orbit orbit-b">uₙ = u₁ + (n−1)d</div><div className="login-hero-card"><div className="ai-icon"><Icon name="spark" size={24}/></div><Badge tone="purple">MATHIO AI TUTOR</Badge><h2>Hiểu bản chất.<br/>Vững tương lai.</h2><p>Học Toán 11 theo cách riêng của bạn với trợ lý AI hướng dẫn từng bước.</p><div className="login-proof"><span className="avatar">NA</span><div><strong>+8.500 học sinh</strong><small>đang học thông minh hơn mỗi ngày</small></div></div></div></div>
  </div>;
}
