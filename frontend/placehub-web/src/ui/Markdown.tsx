import type { ReactNode } from 'react';

/** Inline **bold** and *italic*; everything else is plain text (React escapes it, no HTML is ever injected). */
function inline(text: string, keyBase: string): ReactNode[] {
  const out: ReactNode[] = [];
  const re = /(\*\*[^*]+\*\*|\*[^*]+\*)/g;
  let last = 0;
  let m: RegExpExecArray | null;
  let i = 0;
  while ((m = re.exec(text)) !== null) {
    if (m.index > last) out.push(text.slice(last, m.index));
    const token = m[0];
    out.push(token.startsWith('**')
      ? <strong key={`${keyBase}-${i++}`}>{token.slice(2, -2)}</strong>
      : <em key={`${keyBase}-${i++}`}>{token.slice(1, -1)}</em>);
    last = m.index + token.length;
  }
  if (last < text.length) out.push(text.slice(last));
  return out;
}

/**
 * Minimal, safe markdown for consent texts: #/##/### headings, "- " lists, paragraphs, bold and italic.
 */
export function Markdown({ source }: { source: string }) {
  const blocks = source.replace(/\r\n/g, '\n').split(/\n{2,}/);
  return (
    <div className="prose">
      {blocks.map((block, bi) => {
        const lines = block.split('\n').filter((l) => l.trim() !== '');
        if (lines.length === 0) return null;
        const heading = /^(#{1,3})\s+(.*)$/.exec(lines[0]);
        if (heading && lines.length === 1) {
          const level = heading[1].length;
          const content = inline(heading[2], `h${bi}`);
          return level === 1 ? <h2 key={bi}>{content}</h2> : level === 2 ? <h3 key={bi}>{content}</h3> : <h4 key={bi}>{content}</h4>;
        }
        if (lines.every((l) => /^\s*[-*]\s+/.test(l))) {
          return <ul key={bi}>{lines.map((l, li) => <li key={li}>{inline(l.replace(/^\s*[-*]\s+/, ''), `${bi}-${li}`)}</li>)}</ul>;
        }
        return <p key={bi}>{lines.map((l, li) => <span key={li}>{li > 0 && <br />}{inline(l, `${bi}-${li}`)}</span>)}</p>;
      })}
    </div>
  );
}
