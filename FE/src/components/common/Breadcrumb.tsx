

export default function Breadcrumb({ items, setActive }: { items: [string,string][]; setActive: (p: string) => void }) {
  return <div className="breadcrumb">{items.map((item,i)=><span key={`${item[1]}:${i}`}><button onClick={() => setActive(item[1])}>{item[0]}</button>{i < items.length - 1 && <b>/</b>}</span>)}</div>;
}
