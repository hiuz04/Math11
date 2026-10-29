import { useEffect, useRef, useState } from "react";
import Button from "../common/Button";
import { readSubmissionImage } from "../../services/imageService";
import { errorMessage } from "../../services/serviceMode";
import type { SubmissionContent } from "../../types/lms";

export default function EssayUpload({ value, onChange, excludedHashes = [], disabled = false, onLoadingChange }: { value: SubmissionContent; onChange: (value: SubmissionContent) => void; excludedHashes?: string[]; disabled?: boolean; onLoadingChange?: (loading: boolean) => void }) {
  const picker = useRef<HTMLInputElement>(null), camera = useRef<HTMLInputElement>(null);
  const [error, setError] = useState(""); const [loading, setLoading] = useState(false);
  const active = useRef(true);
  useEffect(() => { active.current = true; return () => { active.current = false; }; }, []);
  async function select(file?: File) {
    if (!file) return;
    setError(""); setLoading(true); onLoadingChange?.(true);
    try {
      const next = await readSubmissionImage(file);
      if (!active.current) { if (next.image?.startsWith("blob:")) URL.revokeObjectURL(next.image); return; }
      if (next.imageHash && excludedHashes.includes(next.imageHash)) { if (next.image?.startsWith("blob:")) URL.revokeObjectURL(next.image); throw new Error("Ảnh đã nộp trước đó. Hãy chụp hoặc chọn ảnh bài làm mới."); }
      onChange(next);
    } catch (e) { if (active.current) setError(errorMessage(e)); }
    finally { if (active.current) { setLoading(false); onLoadingChange?.(false); } }
  }
  return <div className="essay-upload">
    <input aria-label="Upload ảnh bài làm" ref={picker} hidden type="file" accept=".jpg,.jpeg,.png,.webp" disabled={disabled || loading} onChange={e => { void select(e.target.files?.[0]); e.target.value = ""; }}/>
    <input aria-label="Chụp ảnh bài làm" ref={camera} hidden type="file" accept="image/jpeg,image/png,image/webp" capture="environment" disabled={disabled || loading} onChange={e => { void select(e.target.files?.[0]); e.target.value = ""; }}/>
    {value.image && <figure className="upload-preview essay-image"><img src={value.image} alt="Ảnh bài làm đã chọn"/><figcaption>{value.imageName}</figcaption></figure>}
    <div className="feature-actions"><Button variant="secondary" icon="image" onClick={() => picker.current?.click()} disabled={disabled || loading}>{loading ? "Đang đọc ảnh…" : value.image ? "Thay ảnh" : "Upload ảnh"}</Button><Button variant="secondary" icon="image" onClick={() => camera.current?.click()} disabled={disabled || loading}>Chụp ảnh bài làm</Button>{value.image && <Button variant="ghost" onClick={() => onChange({})} disabled={disabled || loading}>Xóa ảnh</Button>}</div>
    <p className="feature-note">JPG, JPEG, PNG, WEBP · Tối đa 8 MB</p>
    {error && <p className="feature-error" role="alert">{error}</p>}
  </div>;
}
