import { type ReactNode } from "react";

export default function Badge({ children, tone = "blue" }: { children: ReactNode; tone?: "blue" | "green" | "orange" | "red" | "purple" }) {
  return <span className={`badge badge-${tone}`}>{children}</span>;
}
