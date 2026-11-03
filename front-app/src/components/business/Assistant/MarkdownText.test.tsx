import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { parseMarkdown } from '@/components/business/Assistant/markdown';
import { MarkdownText } from '@/components/business/Assistant/MarkdownText';

describe('parseMarkdown', () => {
  it('splits paragraphs on blank lines and keeps single line breaks', () => {
    const blocks = parseMarkdown('one\ntwo\n\nthree');
    expect(blocks).toHaveLength(2);
    expect(blocks[0]).toMatchObject({ type: 'paragraph' });
    expect(blocks[0].type === 'paragraph' && blocks[0].lines).toHaveLength(2);
  });

  it('reads bullet and numbered lists', () => {
    const blocks = parseMarkdown('Intro\n- a\n- b\n1. first\n2. second');
    expect(blocks.map((block) => block.type)).toEqual(['paragraph', 'ul', 'ol']);
    expect(blocks[1].type === 'ul' && blocks[1].items).toHaveLength(2);
    expect(blocks[2].type === 'ol' && blocks[2].items).toHaveLength(2);
  });

  it('reads bold and inline code only', () => {
    const [block] = parseMarkdown('a **b** `c` *d* _e_');
    expect(block.type === 'paragraph' && block.lines[0]).toEqual([
      { type: 'text', text: 'a ' },
      { type: 'bold', text: 'b' },
      { type: 'text', text: ' ' },
      { type: 'code', text: 'c' },
      { type: 'text', text: ' *d* _e_' },
    ]);
  });

  it('copes with empty text and Windows line ends', () => {
    expect(parseMarkdown('')).toEqual([]);
    expect(parseMarkdown('a\r\n\r\nb')).toHaveLength(2);
  });
});

describe('MarkdownText', () => {
  it('draws paragraphs, line breaks, lists, bold and code as elements', () => {
    const { container } = render(
      <MarkdownText
        text={'Hello **world**\nsecond line\n\n- one\n- two\n\n1. first\n2. second `x`'}
      />,
    );
    expect(container.querySelectorAll('p')).toHaveLength(1);
    expect(container.querySelectorAll('br')).toHaveLength(1);
    expect(container.querySelector('strong')).toHaveTextContent('world');
    expect(container.querySelectorAll('ul > li')).toHaveLength(2);
    expect(container.querySelectorAll('ol > li')).toHaveLength(2);
    expect(container.querySelector('code')).toHaveTextContent('x');
  });

  it('never creates HTML, images, scripts or links from the text', () => {
    const hostile =
      '<script>alert(1)</script><img src=x onerror=alert(1)><a href="https://evil.example">x</a> ![img](https://evil.example/a.png) [link](https://evil.example) https://evil.example <b>bold</b>';
    const { container } = render(<MarkdownText text={hostile} />);
    expect(container.querySelector('script, img, a, b')).toBeNull();
    expect(container.textContent).toContain('<script>alert(1)</script>');
    expect(container.textContent).toContain('[link](https://evil.example)');
    expect(container.textContent).toContain('<b>bold</b>');
  });
});
