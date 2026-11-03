import { describe, expect, it } from 'vitest';

import { formatFigure, parseFigure } from '@/utils/figureUtils';

describe('parseFigure', () => {
  it('takes the number out of a formatted figure, with what surrounds it', () => {
    expect(parseFigure('₹18,591.10')).toEqual({
      before: '₹',
      target: 18591.1,
      after: '',
      decimals: 2,
      grouped: true,
    });
    expect(parseFigure('87%')).toMatchObject({ before: '', target: 87, after: '%', decimals: 0 });
    expect(parseFigure('-12')).toMatchObject({ target: -12 });
  });

  it.each(['—', 'No orders', '2h 15m', '12 Oct - 18 Oct', ''])(
    'leaves a text without exactly one number alone: %s',
    (text) => {
      expect(parseFigure(text)).toBeNull();
    },
  );
});

describe('formatFigure', () => {
  it('writes another number the way the figure was written (Indian grouping, decimals)', () => {
    const figure = parseFigure('₹1,23,456.00');
    expect(figure).not.toBeNull();
    expect(formatFigure(figure!, 98765.4)).toBe('₹98,765.40');
    expect(formatFigure(figure!, 0)).toBe('₹0.00');
  });

  it('keeps an ungrouped figure ungrouped', () => {
    expect(formatFigure(parseFigure('2026 orders')!, 1500)).toBe('1500 orders');
  });
});
