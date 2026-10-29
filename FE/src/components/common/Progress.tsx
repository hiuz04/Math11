

export default function Progress({ value, tone = "blue" }: { value: number; tone?: "blue" | "green" | "purple" }) {
  return <div className="progress"><div className={`progress-fill ${tone}`} style={{ width: `${value}%` }}/></div>;
}
