import { cognitiveLabels } from "../../constants/lms";
import type { CognitiveLevel } from "../../types/lms";
export default function CognitiveLevelSelector({ value, onChange }: { value: CognitiveLevel; onChange: (value: CognitiveLevel) => void }) {
  return <label>Mức độ nhận thức<select value={value} onChange={e => onChange(e.target.value as CognitiveLevel)}>{Object.entries(cognitiveLabels).map(([key, text]) => <option key={key} value={key}>{text}</option>)}</select></label>;
}
