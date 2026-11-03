import { useEffect, useState } from 'react';

/**
 * The height of the area's sticky top bar (the element marked data-app-header), in pixels, kept up to date: the bar
 * wraps to two rows on a phone. 0 when there is none. Something sticky under the bar sits at this offset.
 */
export function useAppHeaderHeight(): number {
  const [height, setHeight] = useState(0);

  useEffect(() => {
    const header = document.querySelector<HTMLElement>('[data-app-header]');
    if (!header) {
      return undefined;
    }
    const measure = () => setHeight(Math.round(header.getBoundingClientRect().height));
    measure();
    if (typeof ResizeObserver === 'undefined') {
      return undefined;
    }
    const observer = new ResizeObserver(measure);
    observer.observe(header);
    return () => observer.disconnect();
  }, []);

  return height;
}
