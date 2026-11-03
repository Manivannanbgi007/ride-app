import { Bug, WifiOff } from 'lucide-react';

import { Button } from '@/components/common/Button';
import { ROUTES } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { isPageDownloadError } from '@/utils/pageLoadTracker';

interface ErrorFallbackProps {
  error: Error;
  /** Shows the page again (useful when the problem was temporary) */
  onReset: () => void;
}

/**
 * Shown instead of a page that crashed while being drawn; the rest of the application keeps working. A page whose
 * code could not be downloaded (connection lost) gets its own message: only reloading brings it back.
 */
export function ErrorFallback({ error, onReset }: ErrorFallbackProps) {
  if (isPageDownloadError(error)) {
    return (
      <div
        role="alert"
        className="mx-auto flex max-w-lg flex-col items-center gap-4 py-16 text-center"
      >
        <span className="flex size-16 items-center justify-center rounded-full bg-warning-50 text-warning-text">
          <WifiOff aria-hidden="true" className="size-8" />
        </span>
        <h2 className="text-xl font-semibold">{ERROR_MESSAGES.PAGE_DOWNLOAD_FAILED}</h2>
        <p className="text-sm text-ink-soft">
          Please check your internet connection, then reload the page.
        </p>
        <Button onClick={() => window.location.reload()}>Reload page</Button>
      </div>
    );
  }

  return (
    <div
      role="alert"
      className="mx-auto flex max-w-lg flex-col items-center gap-4 py-16 text-center"
    >
      <span className="flex size-16 items-center justify-center rounded-full bg-danger-50 text-danger-500">
        <Bug aria-hidden="true" className="size-8" />
      </span>
      <h2 className="text-xl font-semibold">{ERROR_MESSAGES.PAGE_CRASHED}</h2>
      <p className="text-sm text-ink-soft">
        Please try again. If it keeps happening, go back to the dashboard and tell us what you did
        just before.
      </p>
      {import.meta.env.DEV && (
        <pre className="max-w-full overflow-auto rounded-xl bg-page p-3 text-left text-xs">
          {error.message}
        </pre>
      )}
      <div className="flex gap-3">
        <Button variant="outline" onClick={onReset}>
          Try again
        </Button>
        {/* A full page load: also clears whatever state made the page crash */}
        <Button onClick={() => window.location.assign(ROUTES.dashboard)}>Go to dashboard</Button>
      </div>
    </div>
  );
}
