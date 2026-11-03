import { useEffect, useMemo, useRef, useState } from 'react';

import { formatFigure, parseFigure } from '@/utils/figureUtils';
import { prefersReducedMotion } from '@/utils/motion';

const DURATION_MS = 900;

/**
 * A formatted figure ('1,234', '₹18,591.10', '87%') that counts up to its value when it appears, and from the old
 * value to the new one when it changes. With less motion asked for (or in the tests) it shows the value at once; the
 * last frame is always the text exactly as given.
 */
export function AnimatedNumber({ value }: { value: string }) {
  const figure = useMemo(() => parseFigure(value), [value]);
  // The text of the running animation; null = no animation runs: the value itself shows
  const [frame, setFrame] = useState<string | null>(() =>
    figure && figure.target !== 0 && !prefersReducedMotion() ? formatFigure(figure, 0) : null,
  );
  const shown = useRef(0);

  useEffect(() => {
    if (!figure || prefersReducedMotion()) {
      return;
    }
    const from = shown.current;
    const to = figure.target;
    let start: number | null = null;
    let request = requestAnimationFrame(function tick(now) {
      start ??= now;
      const progress = Math.min(1, (now - start) / DURATION_MS);
      // Ease out: fast at first, settling softly on the value
      const current = from + (to - from) * (1 - (1 - progress) ** 3);
      shown.current = current;
      if (progress < 1) {
        setFrame(formatFigure(figure, current));
        request = requestAnimationFrame(tick);
      } else {
        shown.current = to;
        setFrame(null);
      }
    });
    return () => cancelAnimationFrame(request);
  }, [figure]);

  return <>{frame ?? value}</>;
}
