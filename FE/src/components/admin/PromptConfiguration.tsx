import { useEffect, useState } from "react";
import Button from "../common/Button";
import Modal from "../common/Modal";
import { adminConfigService } from "../../services/adminConfigService";
import { defaultPrompts } from "../../constants/lms";
import { errorMessage } from "../../services/serviceMode";
import type { Prompts } from "../../types/lms";
const labels: Record<keyof Prompts, string> = { system: "System Prompt", essayEvaluation: "Essay Evaluation Prompt", stepTutor: "Step-by-Step Tutor Prompt", retryEvaluation: "Retry Evaluation Prompt" };
export default function PromptConfiguration({ notify }: { notify: (message: string) => void }) {
  const [prompts, setPrompts] = useState<Prompts>(), [busy, setBusy] = useState(false), [error, setError] = useState(""), [restore, setRestore] = useState(false);
  useEffect(() => { adminConfigService.getPrompts().then(setPrompts).catch(e => setError(errorMessage(e))); }, []);
  async function save() { if (!prompts) return; setBusy(true); setError(""); try { await adminConfigService.savePrompts(prompts); notify("Đã lưu Prompt."); } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); } }
  return <section className="card lesson-editor"><h2>Cấu hình AI Tutor</h2>{prompts && <>{(Object.keys(labels) as (keyof Prompts)[]).map(key => <label key={key}>{labels[key]}<textarea aria-label={labels[key]} rows={key === "system" ? 14 : 5} value={prompts[key]} onChange={e => setPrompts({ ...prompts, [key]: e.target.value })}/></label>)}<div className="feature-actions"><Button variant="secondary" onClick={() => setRestore(true)} disabled={busy}>Khôi phục mặc định</Button><Button onClick={() => void save()} disabled={busy}>{busy ? "Đang lưu…" : "Lưu Prompt"}</Button></div></>}{error && <p className="feature-error" role="alert">{error}</p>}{restore && <Modal title="Khôi phục Prompt?" description="Các chỉnh sửa chưa lưu sẽ được thay bằng Prompt mặc định. Nhấn Lưu Prompt để lưu thay đổi." confirmText="Khôi phục" onClose={() => setRestore(false)} onConfirm={() => { setPrompts({ ...defaultPrompts }); setRestore(false); }}/>}</section>;
}
