import { useEffect, useRef, useState } from 'react';

import type { GeoPoint } from '@/types/liveTracking';
import { lerpPoint } from '@/utils/geoUtils';

/**
 * A map point that glides to each new position over `durationMs` instead of jumping (the partner's marker between
 * two GPS updates). With `animate` false (reduced motion) it jumps straight there.
 */
export function useAnimatedPoint(
  target: GeoPoint | undefined,
  durationMs: number,
  animate: boolean,
): GeoPoint | undefined {
  const [shown, setShown] = useState(target);
  const shownRef = useRef(target);

  useEffect(() => {
    if (!target) {
      return;
    }
    const from = shownRef.current;
    const jump = () => {
      shownRef.current = target;
      setShown(target);
    };
    if (!animate || !from || typeof window.requestAnimationFrame !== 'function') {
      jump();
      return;
    }
    let frame = 0;
    const start = performance.now();
    const step = (time: number) => {
      const progress = Math.min(1, (time - start) / durationMs);
      const point = lerpPoint(from, target, progress);
      shownRef.current = point;
      setShown(point);
      if (progress < 1) {
        frame = window.requestAnimationFrame(step);
      }
    };
    frame = window.requestAnimationFrame(step);
    return () => window.cancelAnimationFrame(frame);
    // A new target object with the same position (a refresh that found no movement) does not restart the glide
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [target?.lat, target?.lng, durationMs, animate]);

  return shown;
}
