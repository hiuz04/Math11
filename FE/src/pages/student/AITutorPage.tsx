import { useEffect, useState, useRef } from "react";
import { aiService } from "../../services/aiService";
import { readSubmissionImage } from "../../services/imageService";
import { errorMessage, useMockApi } from "../../services/serviceMode";
import type { SubmissionContent } from "../../types/lms";
import Icon from "../../components/common/Icon";
import Button from "../../components/common/Button";

export default function AiTutor() {
  const [conversations,setConversations]=useState<{id:number;createdAt:string}[]>([]), [thread,setThread]=useState<import("../../services/aiService").ChatMessage[]>([]);
  const open=async(id:number)=>{try{setThread(await aiService.openConversation(id));setSent(false);}catch(e){setError(errorMessage(e));}};
  useEffect(()=>{if(!useMockApi)aiService.conversations().then(async rows=>{setConversations(rows);if(rows.length)await open(aiService.currentConversation() || rows[0].id);}).catch(e=>setError(errorMessage(e)));},[]);
  const requestActive = useRef(false);
  const [message, setMessage] = useState("");
  const [sent, setSent] = useState(false);
  const [uploaded, setUploaded] = useState<SubmissionContent>({});
  const picker = useRef<HTMLInputElement>(null);
  useEffect(() => () => { if (uploaded.image?.startsWith("blob:")) URL.revokeObjectURL(uploaded.image); }, [uploaded.image]);
  const [reply, setReply] = useState(""), [lastMessage, setLastMessage] = useState(""), [error, setError] = useState(""), [busy, setBusy] = useState(false);
  const send = async (text = message, image = uploaded) => { if(requestActive.current)return;requestActive.current=true;setBusy(true); setError(""); try { const result = await aiService.chat(text, image); setReply(result); setLastMessage(text); setSent(true); if(!useMockApi){setThread(await aiService.openConversation(aiService.currentConversation()));setConversations(await aiService.conversations());setMessage("");setUploaded({});} } catch (e) { setError(errorMessage(e)); } finally { requestActive.current=false;setBusy(false); } };
  const upload = async (file?: File) => { if (!file) return; setBusy(true); setError(""); try { setUploaded(await readSubmissionImage(file)); } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); } };
  const [tutorStep, setTutorStep] = useState(0);
  return <div className="ai-page">
    <div className="chat-history">
      <Button icon="plus" className="full" onClick={() => { setSent(false); setMessage(""); setUploaded({}); setTutorStep(0); aiService.newConversation(); setThread([]); }}>Cuộc trò chuyện mới</Button>
      <div className="nav-label">GẦN ĐÂY</div>
{useMockApi ? <>      <button className="history-item active"><Icon name="file" size={17}/><span>Giải phương trình sin 2x</span></button>
      <button className="history-item"><Icon name="file" size={17}/><span>Giới hạn của dãy số</span></button>
      <button className="history-item"><Icon name="file" size={17}/><span>Cấp số nhân là gì?</span></button>
</> : conversations.map(c=><button key={c.id} className={`history-item ${c.id===aiService.currentConversation()?"active":""}`} onClick={()=>void open(c.id)}><Icon name="file" size={17}/><span>Trao đổi · {new Date(c.createdAt).toLocaleString("vi-VN")}</span></button>)}      <div className="history-note"><Icon name="shield"/><span>AI giúp bạn hiểu cách làm, không chỉ đưa đáp án.</span></div>
    </div>
    <div className="chat-main">
      <div className="chat-head"><div><div className="ai-mini"><Icon name="spark"/></div><div><h2>AI Tutor</h2><p><i/> {useMockApi ? "Trực tuyến · Trợ lý Toán 11" : "Trợ lý Toán 11"}</p></div></div><button className="icon-btn"><Icon name="more"/></button></div>
      <div className="messages">
        <div className="day-separator"><span>Hôm nay</span></div>
        <div className="message ai-message"><div className="message-avatar"><Icon name="spark" size={18}/></div><div><small>{useMockApi ? "AI Tutor · 14:32" : "AI Tutor"}</small><div className="bubble"><p>Chào bạn! Mình là trợ lý học Toán 11 của bạn.</p><p>Bạn đang cần hỗ trợ bài toán nào?</p></div></div></div>
{useMockApi&&<>        <div className="message user-message"><div><small>Bạn · 14:33</small><div className="bubble">Giải phương trình <b>sin 2x = 1/2</b></div></div><span className="avatar">NA</span></div>
        <div className="message ai-message"><div className="message-avatar"><Icon name="spark" size={18}/></div><div><small>AI Tutor · 14:33</small><div className="bubble"><p>Được rồi! Mình sẽ cùng bạn giải từng bước nhé, thay vì đưa đáp án ngay.</p><div className="formula-box"><span>sin 2x = ½</span></div><p>Trước tiên, em hãy nhớ lại trên đường tròn lượng giác:</p><div className="question-box"><strong>sin α = ½ khi α bằng những giá trị nào?</strong><div><button onClick={()=>setTutorStep(2)}>π/6</button><button onClick={()=>void send("π/3", undefined)}>π/3</button><button onClick={()=>void send("π/2", undefined)}>π/2</button><button onClick={()=>setTutorStep(1)}>Không nhớ</button></div></div><p className="hint"><Icon name="spark" size={16}/> Gợi ý: sin dương ở góc phần tư I và II.</p></div></div></div>
        {tutorStep === 1 && <div className="message ai-message"><div className="message-avatar"><Icon name="spark" size={18}/></div><div><small>AI Tutor · Bây giờ</small><div className="bubble"><p>Không sao. Ta có:</p><div className="formula-box">sin(π/6) = 1/2</div><p>Từ đây, em thử xác định nghiệm còn lại trên đường tròn lượng giác nhé.</p></div></div></div>}
</>}        {uploaded.image && <><div className="message user-message"><div><small>Bạn · Bây giờ</small><div className="bubble">Đã tải lên: {uploaded.imageName}</div></div><span className="avatar">NA</span></div><div className="message ai-message"><div className="message-avatar"><Icon name="spark" size={18}/></div><div><div className="bubble"><div className="upload-preview"><img src={uploaded.image} alt="Ảnh bài toán đã chọn" style={{maxWidth:"100%", maxHeight:180, objectFit:"contain"}}/></div><p>Mình đã nhận được bài toán. Bạn muốn mình:</p><div className="question-box"><div>{["Giải thích đề bài","Cho gợi ý","Hướng dẫn từng bước","Kiểm tra lời giải"].map(x=><button key={x} disabled={busy} onClick={()=>{setMessage(x);void send(x)}}>{x}</button>)}</div></div></div></div></div></>}
        {useMockApi&&sent && <><div className="message user-message"><div><small>Bạn · Bây giờ</small><div className="bubble">{lastMessage || "Ảnh bài toán"}</div></div><span className="avatar">NA</span></div><div className="message ai-message"><div className="message-avatar"><Icon name="spark" size={18}/></div><div><small>AI Tutor · Bây giờ</small><div className="bubble"><p>{reply}</p></div></div></div></>}
        {!useMockApi&&thread.map(m=><div className={`message ${m.role==="user"?"user-message":"ai-message"}`} key={m.id}>{m.role==="model"&&<div className="message-avatar"><Icon name="spark" size={18}/></div>}<div><small>{m.role==="user"?"Bạn":"AI Tutor"} · {new Date(m.createdAt).toLocaleString("vi-VN")}</small><div className="bubble"><p>{m.content}</p></div></div>{m.role==="user"&&<span className="avatar">HS</span>}</div>)}
      </div>
      <div className="chat-composer">
        <div className="quick-prompts">{["Cho tôi gợi ý","Giải thích công thức","Tạo bài tương tự"].map(x=><button key={x} disabled={busy} onClick={()=>{setMessage(x);void send(x)}}>{x}</button>)}</div>
        <input ref={picker} hidden type="file" accept=".jpg,.jpeg,.png,.webp" onChange={e => { void upload(e.target.files?.[0]); e.target.value = ""; }}/>{error && <p className="feature-error" role="alert">{error}</p>}<div className="composer-box"><button className="icon-btn" aria-label="Chọn ảnh" disabled={busy} onClick={()=>picker.current?.click()}><Icon name="image"/></button><textarea placeholder="Nhập câu hỏi Toán của bạn..." value={message} onChange={e => setMessage(e.target.value)}/><button className="send-btn" disabled={busy || (!message.trim() && !uploaded.image)} onClick={() => void send()}><Icon name="send" size={19}/></button></div>
        <small>AI có thể mắc lỗi. Hãy kiểm tra lại các kết quả quan trọng.</small>
      </div>
    </div>
  </div>;
}
