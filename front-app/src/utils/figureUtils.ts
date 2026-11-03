import { DISPLAY_LOCALE } from '@/utils/constants';

/** A formatted figure taken apart: '₹18,591.10' is '₹' + 18591.1 (2 decimals, grouped) + ''. */
export interface Figure {
  before: string;
  target: number;
  after: string;
  decimals: number;
  grouped: boolean;
}

const NUMBER = /-?\d[\d,]*(?:\.(\d+))?/;

/**
 * The one number in a formatted figure, or null when there is none ('—') or more than one ('2h 15m', '12 Oct - 18
 * Oct'): such texts are shown as they are (components/common/AnimatedNumber).
 */
export function parseFigure(text: string): Figure | null {
  const match = NUMBER.exec(text);
  if (!match) {
    return null;
  }
  const before = text.slice(0, match.index);
  const after = text.slice(match.index + match[0].length);
  const target = Number(match[0].replace(/,/g, ''));
  if (!Number.isFinite(target) || /\d/.test(before) || /\d/.test(after)) {
    return null;
  }
  return {
    before,
    target,
    after,
    decimals: match[1]?.length ?? 0,
    grouped: match[0].includes(','),
  };
}

/** The figure with another number in it, written the way the original was (decimals, grouping). */
export function formatFigure(figure: Figure, value: number): string {
  const number = value.toLocaleString(DISPLAY_LOCALE, {
    minimumFractionDigits: figure.decimals,
    maximumFractionDigits: figure.decimals,
    useGrouping: figure.grouped,
  });
  return `${figure.before}${number}${figure.after}`;
}
