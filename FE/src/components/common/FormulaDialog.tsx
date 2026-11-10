import { useId, useLayoutEffect, useRef, useState } from "react";
import katex from "katex";
import Icon from "./Icon";

const symbols = [
  { label: "Phân số", symbol: "a⁄b", latex: "\\frac{a}{b}", slot: "a" },
  { label: "Căn bậc hai", symbol: "√x", latex: "\\sqrt{x}", slot: "x" },
  { label: "Lũy thừa", symbol: "xⁿ", latex: "x^{n}", slot: "n" },
  { label: "Chỉ số dưới", symbol: "uₙ", latex: "u_{n}", slot: "n" },
  { label: "Giới hạn", symbol: "lim", latex: "\\lim_{x \\to a}", slot: "x \\to a" },
  { label: "Tổng", symbol: "∑", latex: "\\sum_{i=1}^{n}", slot: "i=1" },
  { label: "Sin", symbol: "sin", latex: "\\sin{x}", slot: "x" },
  { label: "Cos", symbol: "cos", latex: "\\cos{x}", slot: "x" },
  { label: "Tang", symbol: "tan", latex: "\\tan{x}", slot: "x" },
  { label: "Pi", symbol: "π", latex: "\\pi " },
  { label: "Alpha", symbol: "α", latex: "\\alpha " },
  { label: "Vô cực", symbol: "∞", latex: "\\infty " },
  { label: "Nhỏ hơn hoặc bằng", symbol: "≤", latex: "\\le " },
  { label: "Lớn hơn hoặc bằng", symbol: "≥", latex: "\\ge " },
  { label: "Khác", symbol: "≠", latex: "\\ne " },
  { label: "Thuộc", symbol: "∈", latex: "\\in " },
];

export default function FormulaDialog({ initialLatex, editing, onApply, onClose }: {
  initialLatex: string;
  editing: boolean;
  onApply: (latex: string) => boolean;
  onClose: (applied: boolean) => void;
}) {
  const [latex, setLatex] = useState(initialLatex);
  const [error, setError] = useState("");
  const dialog = useRef<HTMLDialogElement>(null);
  const input = useRef<HTMLInputElement>(null);
  const titleId = useId();
  const hintId = useId();
  let preview = "";
  if (latex.trim()) {
    try {
      preview = katex.renderToString(latex, { displayMode: true, throwOnError: true, trust: false });
    } catch {
      // A partial formula is normal while the user is typing.
    }
  }

  useLayoutEffect(() => {
    dialog.current?.showModal();
    input.current?.focus();
  }, []);

  function close(applied = false) {
    dialog.current?.close();
    onClose(applied);
  }

  function insertSymbol(symbol: (typeof symbols)[number]) {
    const field = input.current!;
    const from = field.selectionStart ?? field.value.length;
    const to = field.selectionEnd ?? from;
    field.setRangeText(symbol.latex, from, to, "end");
    field.focus();
    if (symbol.slot) {
      const start = from + symbol.latex.indexOf("{") + 1;
      field.setSelectionRange(start, start + symbol.slot.length);
    }
    setLatex(field.value);
    setError("");
  }

  return (
    <dialog ref={dialog} className="formula-dialog" aria-labelledby={titleId} onCancel={event => {
      event.preventDefault();
      close();
    }}>
      <form className="form-modal" onSubmit={event => {
        event.preventDefault();
        if (!preview) setError("Công thức LaTeX chưa hợp lệ.");
        else if (onApply(latex.trim())) close(true);
        else setError("Không thể chèn công thức tại vị trí này.");
      }}>
        <div className="section-head">
          <h2 id={titleId}>{editing ? "Sửa công thức toán" : "Công thức toán"}</h2>
          <button type="button" className="icon-btn" aria-label="Đóng" onClick={() => close()}><Icon name="close"/></button>
        </div>
        <p id={hintId} className="formula-hint">Chọn ký hiệu hoặc mẫu bên dưới, rồi chỉnh công thức trong ô LaTeX.</p>
        <div className="formula-symbols" role="group" aria-label="Ký hiệu và mẫu toán học">
          {symbols.map(symbol => (
            <button key={symbol.label} type="button" aria-label={symbol.label} title={symbol.label} onClick={() => insertSymbol(symbol)}>
              {symbol.symbol}
            </button>
          ))}
        </div>
        <label>LaTeX
          <input ref={input} value={latex} aria-describedby={hintId} spellCheck={false} autoComplete="off"
            placeholder="u_n = u_1 + (n-1)d" onChange={event => { setLatex(event.target.value); setError(""); }}/>
        </label>
        <section className="formula-preview" aria-label="Xem trước công thức">
          {preview
            ? <div dangerouslySetInnerHTML={{ __html: preview }}/>
            : <p>{latex.trim() ? "Hoàn thiện công thức để xem trước." : "Công thức sẽ hiển thị ở đây."}</p>}
        </section>
        {error && <p role="alert" className="feature-error">{error}</p>}
        <div>
          <button type="button" className="btn btn-secondary" onClick={() => close()}><span>Hủy</span></button>
          <button type="submit" className="btn btn-primary" disabled={!latex.trim()}><span>{editing ? "Lưu công thức" : "Chèn công thức"}</span></button>
        </div>
      </form>
    </dialog>
  );
}
