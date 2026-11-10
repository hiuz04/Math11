import { useEffect, useState } from "react";
import { useEditor, EditorContent } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import Mathematics from "@tiptap/extension-mathematics";
import FormulaDialog from "./FormulaDialog";
export default function RichTextEditor({ value, onChange, onTextChange, readOnly = false, disabled = false, label = "Nội dung văn bản" }: {
  value: string;
  onChange?: (html: string) => void;
  onTextChange?: (text: string) => void;
  readOnly?: boolean;
  disabled?: boolean;
  label?: string;
}) {
  const [formula, setFormula] = useState<{ latex: string; position?: number }>();
  const editor = useEditor({
    extensions: [
      StarterKit.configure({ heading: { levels: [1, 2] }, link: { openOnClick: false } }),
      Mathematics.configure({
        katexOptions: { trust: false, throwOnError: false },
        inlineOptions: {
          onClick: readOnly ? undefined : (node, position) => setFormula({ latex: String(node.attrs.latex), position }),
        },
      }),
    ],
    content: value, editable: !readOnly && !disabled,
    onUpdate: ({ editor }) => {
      onChange?.(editor.getHTML());
      onTextChange?.(editor.getText({
        textSerializers: {
          inlineMath: ({ node }) => `\\(${node.attrs.latex}\\)`,
          blockMath: ({ node }) => `\\[${node.attrs.latex}\\]`,
          hardBreak: () => "\n",
        },
      }).trim());
    },
    editorProps: { attributes: { "aria-label": label, role: "textbox", "aria-multiline": "true" } },
  });
  useEffect(() => { if (editor && editor.getHTML() !== value) editor.commands.setContent(value, { emitUpdate: false }); }, [editor, value]);
  useEffect(() => { editor?.setEditable(!readOnly && !disabled, false); }, [editor, readOnly, disabled]);
  if (!editor) return null;
  function applyFormula(latex: string) {
    if (!editor?.isEditable) return false;
    if (formula?.position !== undefined) return editor.commands.updateInlineMath({ latex, pos: formula.position });
    return editor.commands.insertContent({ type: "inlineMath", attrs: { latex } });
  }
  return <div className={`rich-editor ${readOnly ? "rich-editor-readonly" : ""}`} inert={disabled}>
    {!readOnly && <div className="rich-toolbar" role="toolbar" aria-label="Định dạng văn bản">
      <button type="button" title="Bold" aria-label="Bold" aria-pressed={editor.isActive("bold")} onClick={() => editor.chain().focus().toggleBold().run()}><b>B</b></button>
      <button type="button" title="Italic" aria-label="Italic" aria-pressed={editor.isActive("italic")} onClick={() => editor.chain().focus().toggleItalic().run()}><i>I</i></button>
      <button type="button" aria-label="Heading 1" aria-pressed={editor.isActive("heading", { level: 1 })} onClick={() => editor.chain().focus().toggleHeading({ level: 1 }).run()}>H1</button>
      <button type="button" aria-label="Heading 2" aria-pressed={editor.isActive("heading", { level: 2 })} onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}>H2</button>
      <button type="button" aria-label="Bullet list" onClick={() => editor.chain().focus().toggleBulletList().run()}>• ≡</button>
      <button type="button" aria-label="Numbered list" onClick={() => editor.chain().focus().toggleOrderedList().run()}>1. ≡</button>
      <button type="button" aria-haspopup="dialog" onClick={() => setFormula({ latex: "" })}>∑ Công thức</button>
      <button type="button" aria-label="Undo" disabled={!editor.can().undo()} onClick={() => editor.chain().focus().undo().run()}>↶</button>
      <button type="button" aria-label="Redo" disabled={!editor.can().redo()} onClick={() => editor.chain().focus().redo().run()}>↷</button>
    </div>}
    <EditorContent editor={editor}/>
    {formula && !readOnly && !disabled && <FormulaDialog initialLatex={formula.latex} editing={formula.position !== undefined}
      onApply={applyFormula} onClose={applied => {
        setFormula(undefined);
        if (applied) editor.view.focus();
      }}/>}
  </div>;
}
