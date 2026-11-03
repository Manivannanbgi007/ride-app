import { AlertTriangle, RefreshCw } from 'lucide-react';

import { toApiError } from '@/api/apiErrorHandler';
import { Button } from '@/components/common/Button';

interface ErrorMessageProps {
  /** What failed, from ERROR_MESSAGES: "Unable to load the dashboard" */
  title: string;
  /** The error as thrown; only its user message is shown (the technical part is in the console) */
  error: unknown;
  /** Shows a "Try again" button */
  onRetry?: () => void;
  isRetrying?: boolean;
}

/** A failed request, explained in plain words, with a way to try again. */
export function ErrorMessage({ title, error, onRetry, isRetrying = false }: ErrorMessageProps) {
  const { userMessage, requestId } = toApiError(error);
  return (
    <div role="alert" className="flex flex-col items-center gap-3 px-6 py-8 text-center">
      <span className="flex size-14 items-center justify-center rounded-full bg-danger-50 text-danger-500">
        <AlertTriangle aria-hidden="true" className="size-7" />
      </span>
      <h3 className="text-base font-semibold">{title}</h3>
      <p className="max-w-md text-sm text-ink-soft">{userMessage}</p>
      {requestId && <p className="text-xs text-ink-soft">Reference: {requestId}</p>}
      {onRetry && (
        <Button
          variant="outline"
          size="sm"
          onClick={onRetry}
          isLoading={isRetrying}
          leftIcon={<RefreshCw aria-hidden="true" className="size-4" />}
        >
          Try again
        </Button>
      )}
    </div>
  );
}
