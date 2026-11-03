/**
 * The small, safe subset of Markdown the assistant's text may use: paragraphs, line breaks, **bold**, `code`,
 * "- " lists and "1. " lists. Nothing else is interpreted (no HTML, no images, no links): the result is plain data
 * that React renders as text, so no markup in the answer can ever reach the page.
 */
export type InlineNode = { type: 'text' | 'bold' | 'code'; text: string };

export type MarkdownBlock =
  { type: 'paragraph'; lines: InlineNode[][] } | { type: 'ul' | 'ol'; items: InlineNode[][] };

const INLINE = /(\*\*[^*\n]+?\*\*|`[^`\n]+`)/g;

export function parseInline(text: string): InlineNode[] {
  const nodes: InlineNode[] = [];
  for (const part of text.split(INLINE)) {
    if (!part) {
      continue;
    }
    if (part.length > 4 && part.startsWith('**') && part.endsWith('**')) {
      nodes.push({ type: 'bold', text: part.slice(2, -2) });
    } else if (part.length > 2 && part.startsWith('`') && part.endsWith('`')) {
      nodes.push({ type: 'code', text: part.slice(1, -1) });
    } else {
      nodes.push({ type: 'text', text: part });
    }
  }
  return nodes;
}

const BULLET = /^\s{0,3}[-*]\s+(.*)$/;
const NUMBERED = /^\s{0,3}\d{1,3}[.)]\s+(.*)$/;

export function parseMarkdown(source: string): MarkdownBlock[] {
  const blocks: MarkdownBlock[] = [];
  const lines = source.replace(/\r\n?/g, '\n').split('\n');
  let paragraph: string[] = [];

  const endParagraph = () => {
    if (paragraph.length > 0) {
      blocks.push({ type: 'paragraph', lines: paragraph.map(parseInline) });
      paragraph = [];
    }
  };

  for (const line of lines) {
    const bullet = BULLET.exec(line);
    const numbered = bullet ? null : NUMBERED.exec(line);
    const match = bullet ?? numbered;
    if (match) {
      endParagraph();
      const type = bullet ? 'ul' : 'ol';
      const last = blocks[blocks.length - 1];
      if (last?.type === type) {
        last.items.push(parseInline(match[1]));
      } else {
        blocks.push({ type, items: [parseInline(match[1])] });
      }
    } else if (line.trim() === '') {
      endParagraph();
    } else {
      paragraph.push(line.trim());
    }
  }
  endParagraph();
  return blocks;
}
