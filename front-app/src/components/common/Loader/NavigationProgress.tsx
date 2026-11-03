import { usePageLoading } from '@/hooks/usePageLoading';
import { cn } from '@/utils/classNames';

/**
 * A thin green bar along the top of the window while the next page's code downloads (the first time a page is
 * opened). The page on screen stays usable meanwhile.
 *
 * - It appears only when the download takes more than 0.15 s (no flash on a fast connection).
 * - It creeps toward the end while waiting, runs to the end when the page is there, then fades out.
 * - It stays away while the big page loader is already shown.
 *
 * Rendered once, at the top of the application (App.tsx).
 */
export function NavigationProgress() {
  const { downloads, loadersShown } = usePageLoading();
  const busy = downloads > 0 && loadersShown === 0;

  return (
    <>
      <div
        aria-hidden="true"
        className={cn(
          'pointer-events-none fixed inset-x-0 top-0 z-60 h-[3px] transition-opacity',
          busy ? 'opacity-100 delay-150 duration-200' : 'opacity-0 delay-300 duration-300',
        )}
      >
        <div
          className={cn(
            'relative h-full origin-left overflow-hidden rounded-r-full bg-linear-to-r from-brand-400 via-brand-500 to-brand-600',
            'shadow-[0_0_10px_rgb(57_197_118/0.7)]',
            // Done: from where it crept to the end (its full width), while it fades
            busy ? 'animate-progress-creep' : 'transition-transform duration-300 ease-out',
          )}
        >
          {/* A light glinting along it */}
          <span className="absolute inset-y-0 left-0 w-1/4 bg-linear-to-r from-transparent via-white/60 to-transparent motion-safe:animate-progress-glint" />
        </div>
      </div>
      {/* For screen readers: the bar itself is only a picture */}
      <span role="status" className="sr-only">
        {busy ? 'Loading the page' : ''}
      </span>
    </>
  );
}
