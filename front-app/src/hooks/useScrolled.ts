import { useEffect, useState } from 'react';

/** Whether the page is scrolled down at all (a fixed header then shows a shadow to separate it from the page). */
export function useScrolled(threshold = 4): boolean {
  const [scrolled, setScrolled] = useState(() => window.scrollY > threshold);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > threshold);
    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();
    return () => window.removeEventListener('scroll', onScroll);
  }, [threshold]);

  return scrolled;
}
