import Icon from "./Icon";

export default function EmptyState({ title, description }: { title: string; description: string }) {
  return <div className="empty-state compact"><div><Icon name="search" size={28}/></div><h2>{title}</h2><p>{description}</p></div>;
}
