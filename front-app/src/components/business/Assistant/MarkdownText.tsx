import { Fragment } from 'react';

import { parseMarkdown, type InlineNode } from '@/components/business/Assistant/markdown';

function Inline({ nodes }: { nodes: InlineNode[] }) {
  return (
    <>
      {nodes.map((node, index) =>
        node.type === 'bold' ? (
          <strong key={index} className="font-semibold">
            {node.text}
          </strong>
        ) : node.type === 'code' ? (
          <code key={index} className="rounded bg-line px-1 py-0.5 font-mono text-[0.85em]">
            {node.text}
          </code>
        ) : (
          <Fragment key={index}>{node.text}</Fragment>
        ),
      )}
    </>
  );
}

/** The assistant's text, drawn from the safe Markdown subset (see markdown.ts). Never uses innerHTML. */
export function MarkdownText({ text }: { text: string }) {
  const blocks = parseMarkdown(text);
  return (
    <div className="flex flex-col gap-2 [overflow-wrap:anywhere]">
      {blocks.map((block, index) => {
        if (block.type === 'paragraph') {
          return (
            <p key={index}>
              {block.lines.map((line, lineIndex) => (
                <Fragment key={lineIndex}>
                  {lineIndex > 0 && <br />}
                  <Inline nodes={line} />
                </Fragment>
              ))}
            </p>
          );
        }
        const List = block.type === 'ul' ? 'ul' : 'ol';
        return (
          <List
            key={index}
            className={`flex flex-col gap-1 pl-5 ${block.type === 'ul' ? 'list-disc' : 'list-decimal'}`}
          >
            {block.items.map((item, itemIndex) => (
              <li key={itemIndex}>
                <Inline nodes={item} />
              </li>
            ))}
          </List>
        );
      })}
    </div>
  );
}
