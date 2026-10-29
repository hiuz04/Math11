import Icon from "./Icon";
import Button from "./Button";

export default function Modal({ title, description, confirmText, danger = false, onClose, onConfirm }: { title: string; description: string; confirmText: string; danger?: boolean; onClose: () => void; onConfirm: () => void }) {
  return <div className="modal-backdrop"><div className="modal-card"><div className={`modal-icon ${danger ? "danger" : ""}`}><Icon name={danger ? "shield" : "check"}/></div><h2>{title}</h2><p>{description}</p><div><Button variant="secondary" onClick={onClose}>Hủy</Button><Button onClick={onConfirm} className={danger ? "danger-button" : ""}>{confirmText}</Button></div></div></div>;
}
