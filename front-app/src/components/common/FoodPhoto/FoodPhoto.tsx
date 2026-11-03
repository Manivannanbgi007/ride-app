import { useState, type ReactNode } from 'react';

import { cn } from '@/utils/classNames';

interface FoodPhotoProps {
  /** Pictures to try, best first (the dish's own, then its category's); empty and missing ones are skipped */
  sources: readonly (string | null | undefined)[];
  /** Shown when there is no picture, or none of them can be loaded */
  fallback: ReactNode;
  className?: string;
}

/**
 * A food picture that never shows a broken-image icon: if one address cannot be loaded the next is tried, and
 * after the last the fallback is shown. Decorative (the name is always written next to it), so it has no alt text.
 */
export function FoodPhoto({ sources, fallback, className }: FoodPhotoProps) {
  const [failed, setFailed] = useState<readonly string[]>([]);
  const source = sources.find((candidate): candidate is string =>
    Boolean(candidate && !failed.includes(candidate)),
  );

  if (!source) {
    return <>{fallback}</>;
  }
  return (
    <img
      src={source}
      alt=""
      loading="lazy"
      decoding="async"
      onError={() => setFailed((previous) => [...previous, source])}
      className={cn('shrink-0 object-cover', className)}
    />
  );
}
