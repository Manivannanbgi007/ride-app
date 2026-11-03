import { useState } from 'react';

import type { SalesSeriesKey } from './ordersSalesSeries';

/** The chart's figures switched off in its legend, and a switch for one of them. */
export function useHiddenSeries() {
  const [hidden, setHidden] = useState<ReadonlySet<SalesSeriesKey>>(new Set());
  const toggle = (key: SalesSeriesKey) =>
    setHidden((current) => {
      const next = new Set(current);
      if (!next.delete(key)) {
        next.add(key);
      }
      return next;
    });
  return { hidden, toggle };
}
