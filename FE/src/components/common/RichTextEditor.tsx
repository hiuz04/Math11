import { useEffect, useState } from "react";
import { useEditor, EditorContent } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import Mathematics from "@tiptap/extension-mathematics";
import katex from "katex";
import Button from "./Button";
import Icon from "./Icon";
export default function RichTextEditor({ value, onChange, readOnly = false }: { value: string; onChange?: (html: string) => void; readOnly?: boolean }) {
  const [formulaOpen, setFormulaOpen] = useState(false), [latex, setLatex] = useState(""), [error, setError] = useState("");
  const [mathPosition, setMathPosition] = useState<number>();
  const editor = useEditor({
    extensions: [StarterKit.configure({ heading: { levels: [1, 2] }, link: { openOnClick: false } }), Mathematics.configure({ katexOptions: { trust: false, throwOnError: false }, inlineOptions: { onClick: (node, pos) => { if (!readOnly) { setLatex(String(node.attrs.latex)); setMathPosition(pos); setFormulaOpen(true); } } } })],
    content: value, editable: !readOnly,
    onUpdate: ({ editor }) => onChange?.(editor.getHTML()),
    editorProps: { attributes: { "aria-label": "Nội dung văn bản", role: "textbox", "aria-multiline": "true" } },
  });
  useEffect(() => { if (editor && editor.getHTML() !== value) editor.commands.setContent(value, { emitUpdate: false }); }, [editor, value]);
  if (!editor) return null;
  function insertFormula() {
    try {
      katex.renderToString(latex, { throwOnError: true, trust: false });
      if (mathPosition !== undefined) editor!.chain().focus().updateInlineMath({ latex, pos: mathPosition }).run();
      else editor!.chain().focus().insertInlineMath({ latex }).run();
      setFormulaOpen(false); setMathPosition(undefined); setLatex(""); setError("");
    } catch { setError("Công thức LaTeX chưa hợp lệ."); }
  }
  return <div className={`rich-editor ${readOnly ? "rich-editor-readonly" : ""}`}>
    {!readOnly && <div className="rich-toolbar" role="toolbar" aria-label="Định dạng văn bản">
      <button type="button" title="Bold" aria-label="Bold" aria-pressed={editor.isActive("bold")} onClick={() => editor.chain().focus().toggleBold().run()}><b>B</b></button>
      <button type="button" title="Italic" aria-label="Italic" aria-pressed={editor.isActive("italic")} onClick={() => editor.chain().focus().toggleItalic().run()}><i>I</i></button>
      <button type="button" aria-label="Heading 1" aria-pressed={editor.isActive("heading", { level: 1 })} onClick={() => editor.chain().focus().toggleHeading({ level: 1 }).run()}>H1</button>
      <button type="button" aria-label="Heading 2" aria-pressed={editor.isActive("heading", { level: 2 })} onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}>H2</button>
      <button type="button" aria-label="Bullet list" onClick={() => editor.chain().focus().toggleBulletList().run()}>• ≡</button>
      <button type="button" aria-label="Numbered list" onClick={() => editor.chain().focus().toggleOrderedList().run()}>1. ≡</button>
      <button type="button" aria-label="Formula" onClick={() => { setMathPosition(undefined); setLatex(""); setError(""); setFormulaOpen(true); }}>∑</button>
      <button type="button" aria-label="Undo" disabled={!editor.can().undo()} onClick={() => editor.chain().focus().undo().run()}>↶</button>
      <button type="button" aria-label="Redo" disabled={!editor.can().redo()} onClick={() => editor.chain().focus().redo().run()}>↷</button>
    </div>}
    <EditorContent editor={editor}/>
    {formulaOpen && <div className="modal-backdrop"><div className="form-modal" role="dialog" aria-modal="true" aria-label="Công thức toán"><div className="section-head"><h2>Công thức toán</h2><button type="button" className="icon-btn" aria-label="Đóng" onClick={() => setFormulaOpen(false)}><Icon name="close"/></button></div><label>LaTeX<input autoFocus value={latex} onChange={e => setLatex(e.target.value)} placeholder="u_n = u_1 + (n-1)d"/></label>{error && <p role="alert" className="feature-error">{error}</p>}<div><Button variant="secondary" onClick={() => setFormulaOpen(false)}>Hủy</Button><Button onClick={insertFormula} disabled={!latex.trim()}>Chèn công thức</Button></div></div></div>}
  </div>;
}
