import { UtensilsCrossed } from 'lucide-react';
import { useEffect } from 'react';

import { pageLoaderShown } from '@/utils/pageLoadTracker';

interface PageLoaderProps {
  /** Read out by screen readers and shown under the animation */
  message?: string;
}

/**
 * Stands in for a whole page while its code downloads (the first page of a visit, or the first page of the other
 * area): a green ring turning around the plate-and-cutlery mark, with a soft pulse and three waving dots.
 *
 * - It fades in only after 0.2 s, so on a fast connection it never flashes.
 * - The thin bar at the top stays away while it is shown (one loading sign at a time).
 * - People who asked their system for less motion see it standing still.
 *
 * Parts of a page that load their own data use skeletons instead (AsyncContent), never this loader.
 */
export function PageLoader({ message = 'Loading the page…' }: PageLoaderProps) {
  useEffect(() => pageLoaderShown(), []);

  return (
    <div
      role="status"
      aria-label={message}
      className="flex min-h-[50vh] animate-loader-in flex-col items-center justify-center gap-5"
    >
      <span aria-hidden="true" className="relative flex size-20 items-center justify-center">
        <span className="absolute inset-0 rounded-full bg-brand-500/20 motion-safe:animate-loader-pulse" />
        <span className="absolute inset-1 rounded-full border-4 border-brand-100" />
        <span className="absolute inset-1 rounded-full border-4 border-transparent border-t-brand-500 border-r-brand-400 motion-safe:animate-spin" />
        <span className="relative flex size-11 items-center justify-center rounded-full bg-linear-to-br from-brand-600 to-brand-800 text-white shadow-brand">
          <UtensilsCrossed className="size-5 motion-safe:animate-loader-bob" />
        </span>
      </span>
      <span className="flex flex-col items-center gap-2.5">
        <span className="text-sm font-semibold text-ink">{message}</span>
        <span aria-hidden="true" className="flex gap-1.5">
          {[0, 1, 2].map((dot) => (
            <span
              key={dot}
              className="size-1.5 rounded-full bg-brand-500 motion-safe:animate-loader-dot"
              style={{ animationDelay: `${dot * 0.16}s` }}
            />
          ))}
        </span>
      </span>
    </div>
  );
}
