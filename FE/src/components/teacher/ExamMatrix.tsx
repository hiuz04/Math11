import { cognitiveLabels } from "../../constants/lms";
import type { CognitiveLevel, ExamMatrix as Matrix, QuestionType } from "../../types/lms";
export default function ExamMatrix({ value, onChange, disabled }: { value: Matrix; onChange: (next: Matrix) => void; disabled: boolean }) {
  function set(level: CognitiveLevel, type: QuestionType, count: number) {
    onChange({ ...value, [level]: { ...value[level], [type]: Math.max(0, Math.min(100, Math.floor(count || 0))) } });
  }
  const total = Object.values(value).reduce((sum, row) => sum + row.MCQ + row.ESSAY, 0);
  return <><div className="table-scroll"><table className="exam-matrix"><thead><tr><th>MỨC ĐỘ NHẬN THỨC</th><th>TRẮC NGHIỆM</th><th>TỰ LUẬN</th></tr></thead><tbody>{(Object.keys(cognitiveLabels) as CognitiveLevel[]).map(level => <tr key={level}><td>{cognitiveLabels[level]}</td>{(["MCQ", "ESSAY"] as QuestionType[]).map(type => <td key={type}><div className="number-control"><button aria-label={`Giảm ${cognitiveLabels[level]} ${type}`} disabled={disabled || value[level][type] === 0} onClick={() => set(level, type, value[level][type] - 1)}>−</button><input aria-label={`${cognitiveLabels[level]} ${type}`} type="number" min={0} max={100} value={value[level][type]} disabled={disabled} onChange={e => set(level, type, Number(e.target.value))}/><button aria-label={`Tăng ${cognitiveLabels[level]} ${type}`} disabled={disabled} onClick={() => set(level, type, value[level][type] + 1)}>+</button></div></td>)}</tr>)}</tbody></table></div><p className="feature-copy">Tổng số câu: <strong>{total}</strong></p></>;
}
