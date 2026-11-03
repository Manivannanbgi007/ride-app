/** A page number button, or a gap ("...") between page numbers. */
export type PageButton = number | 'gap';

/**
 * Which page buttons to show (pages are zero-based; the screen shows them from 1): always the first and the last,
 * and two on each side of the current one. pageButtons(9, 20) -> [0, 'gap', 7, 8, 9, 10, 11, 'gap', 19]
 */
export function pageButtons(current: number, totalPages: number): PageButton[] {
  const pages = new Set<number>([0, totalPages - 1]);
  for (let page = current - 2; page <= current + 2; page++) {
    if (page >= 0 && page < totalPages) {
      pages.add(page);
    }
  }
  const sorted = [...pages].filter((page) => page >= 0).sort((a, b) => a - b);

  const buttons: PageButton[] = [];
  sorted.forEach((page, index) => {
    if (index > 0 && page - sorted[index - 1] > 1) {
      buttons.push('gap');
    }
    buttons.push(page);
  });
  return buttons;
}

/** "21-40 of 303" for page 1 (zero-based) of 20 rows. */
export function shownRange(page: number, size: number, totalElements: number): string {
  if (totalElements === 0) {
    return '0 of 0';
  }
  const first = page * size + 1;
  const last = Math.min((page + 1) * size, totalElements);
  return `${first}-${last} of ${totalElements.toLocaleString('en-IN')}`;
}
