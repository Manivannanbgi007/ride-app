/**
 * A row of cards: on a phone it scrolls sideways (each card snaps into place, the next one peeks in), from a small
 * tablet on it is a grid of two columns, then of `wide` columns. The page's 16px side gutter is kept while scrolling.
 */
export function cardRow(wide: 3 | 4): string {
  return `-mx-4 flex snap-x snap-mandatory scroll-px-4 gap-4 overflow-x-auto px-4 pb-2 sm:mx-0 sm:grid sm:grid-cols-2 sm:overflow-visible sm:px-0 sm:pb-0 ${wide === 3 ? 'lg:grid-cols-3' : 'lg:grid-cols-4'}`;
}

/** One card of a cardRow */
export const CARD_ROW_ITEM = 'flex w-[82%] max-w-80 shrink-0 snap-start sm:w-auto sm:max-w-none';
