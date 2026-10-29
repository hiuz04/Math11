import { aiService } from "../../services/aiService";
import { useState, useRef, useEffect } from "react";
import { readSubmissionImage } from "../../services/imageService";
import { errorMessage } from "../../services/serviceMode";
import type { SubmissionContent } from "../../types/lms";
import Icon from "../common/Icon";

export default function AiDrawer({ onClose, quizMode = false }: { onClose: () => void; quizMode?: boolean }) {
  const requestActive = useRef(false);
  const [step, setStep] = useState(quizMode ? 1 : 0);
  const [text, setText] = useState("");
  const [uploaded, setUploaded] = useState<SubmissionContent>({});
  useEffect(() => () => { if (uploaded.image?.startsWith("blob:")) URL.revokeObjectURL(uploaded.image); }, [uploaded.image]);
  const picker = useRef<HTMLInputElement>(null);
  const [reply, setReply] = useState(""), [error, setError] = useState(""), [busy, setBusy] = useState(false);
  const send = async (message = text) => { if(requestActive.current)return;requestActive.current=true;setBusy(true); setError(""); try { setReply(await aiService.chat(message, uploaded)); setText(message); setStep(3); } catch (e) { setError(errorMessage(e)); } finally { requestActive.current=false;setBusy(false); } };
  const upload = async (file?: File) => { if (!file) return; setBusy(true); try { setUploaded(await readSubmissionImage(file)); setError(""); } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); } };
  return <div className="ai-drawer-backdrop" onClick={onClose}><aside className="ai-drawer" onClick={e => e.stopPropagation()}><div className="drawer-head"><div><div className="ai-mini"><Icon name="spark"/></div><div><h2>AI Tutor</h2><p><i/> Trợ lý Toán 11</p></div></div><button className="icon-btn" onClick={onClose}><Icon name="close"/></button></div><div className="drawer-messages"><div className="message ai-message"><div className="message-avatar"><Icon name="spark" size={17}/></div><div><div className="bubble">{quizMode ? "Em hãy thử nhớ lại miền giá trị của hàm số sin(x). sin(x) có thể lớn hơn 1 không?" : "Chào bạn! Bạn đang gặp khó khăn ở phần nào?"}</div></div></div>
    {quizMode && step === 1 && <div className="drawer-choices"><button onClick={() => void send("Em nghĩ sin x có thể lớn hơn 1")}>Có</button><button onClick={() => setStep(2)}>Không</button></div>}
    {quizMode && step >= 2 && <div className="message ai-message"><div className="message-avatar"><Icon name="spark" size={17}/></div><div><div className="bubble"><b>Đúng rồi!</b> Vì −1 ≤ sin(x) ≤ 1 nên giá trị lớn nhất của sin(x) là bao nhiêu?</div></div></div>}
    {!quizMode && <div className="quick-grid">{["Giải thích phần này","Cho mình một ví dụ","Mình chưa hiểu công thức","Tạo bài tương tự","Cho mình gợi ý"].map(q=><button key={q} disabled={busy} onClick={() => void send(q)}>{q}</button>)}</div>}
    {uploaded.image && <div className="upload-preview"><img src={uploaded.image} alt="Ảnh bài toán" style={{maxWidth:"100%", maxHeight:180, objectFit:"contain"}}/><p><b>Mình đã nhận được bài toán.</b> Bạn muốn mình hỗ trợ theo cách nào?</p><div className="quick-grid">{["Giải thích đề bài","Cho gợi ý","Hướng dẫn từng bước","Kiểm tra lời giải"].map(x=><button key={x} disabled={busy} onClick={() => void send(x)}>{x}</button>)}</div></div>}
    {step === 3 && <><div className="message user-message"><div><div className="bubble">{text}</div></div><span className="avatar">NA</span></div><div className="message ai-message"><div className="message-avatar"><Icon name="spark" size={17}/></div><div><div className="bubble">{reply}</div></div></div></>}
    </div><div className="drawer-compose"><input ref={picker} hidden type="file" accept=".jpg,.jpeg,.png,.webp" onChange={e => { void upload(e.target.files?.[0]); e.target.value = ""; }}/>{error && <p className="feature-error" role="alert">{error}</p>}<div className="composer-box"><button className="icon-btn" disabled={busy} onClick={() => picker.current?.click()}><Icon name="image"/></button><textarea placeholder="Nhập câu hỏi của bạn..." value={text} onChange={e => setText(e.target.value)}/><button className="send-btn" disabled={busy || (!text.trim() && !uploaded.image)} onClick={() => void send()}><Icon name="send" size={18}/></button></div></div></aside></div>;
}
