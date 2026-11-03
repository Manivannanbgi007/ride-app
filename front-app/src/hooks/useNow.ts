import { useEffect, useState } from 'react';

/** The current time (milliseconds), looked at again every `everyMs`: for "placed 12 min ago" that keeps counting. */
export function useNow(everyMs = 30_000): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), everyMs);
    return () => window.clearInterval(timer);
  }, [everyMs]);
  return now;
}
