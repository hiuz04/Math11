import type { ToastMessage } from "../../types/feedback";
import { useEffect } from "react";
import Icon from "./Icon";

export default function Toast({ toast, clear }: { toast: ToastMessage | null; clear: () => void }) {
  useEffect(() => { if (toast) { const timer = window.setTimeout(clear, 3200); return () => window.clearTimeout(timer); } }, [toast, clear]);
  if (!toast) return null;
  return <div className={`toast toast-${toast.type}`}><span>{toast.type === "success" ? <Icon name="check"/> : toast.type === "warning" ? "!" : "×"}</span><div><strong>{toast.type === "success" ? "Thành công" : toast.type === "warning" ? "Lưu ý" : "Có lỗi xảy ra"}</strong><p>{toast.message}</p></div><button onClick={clear}><Icon name="close" size={15}/></button></div>;
}
