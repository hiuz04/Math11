import katex from "katex";
export default function MathFormula({ formula }: { formula: string }) {
  return <div className="math-block feature-formula" dangerouslySetInnerHTML={{ __html: katex.renderToString(formula, { throwOnError: false, trust: false, displayMode: true }) }}/>;
}
