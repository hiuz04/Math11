import { type ReactNode } from "react";
import Icon from "./Icon";
import type { IconName } from "../../types/icon";

export default function Button({ children, variant = "primary", icon, onClick, className = "", disabled = false }: { children: ReactNode; variant?: "primary" | "secondary" | "ghost"; icon?: IconName; onClick?: () => void; className?: string; disabled?: boolean }) {
  return <button className={`btn btn-${variant} ${className}`} onClick={onClick} disabled={disabled}>{icon && <Icon name={icon} size={18}/>}<span>{children}</span></button>;
}
