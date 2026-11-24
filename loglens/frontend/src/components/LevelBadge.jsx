export default function LevelBadge({ level }) {
  return <span className={`badge badge-${String(level).toLowerCase()}`}>{level}</span>;
}
