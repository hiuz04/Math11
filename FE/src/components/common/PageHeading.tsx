import { type ReactNode } from "react";

export default function PageHeading({ eyebrow, title, subtitle, action }: { eyebrow: string; title: string; subtitle: string; action?: ReactNode }) {
  return <div className="page-heading"><div><div className="eyebrow"><span/> {eyebrow}</div><h1>{title}</h1><p>{subtitle}</p></div>{action && <div>{action}</div>}</div>;
}
