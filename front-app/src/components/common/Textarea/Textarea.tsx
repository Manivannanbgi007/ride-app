import { useId, type ComponentProps } from 'react';

import { cn } from '@/utils/classNames';

export interface TextareaProps extends ComponentProps<'textarea'> {
  label?: string;
  /** Shown under the field in red; also marks the field invalid for screen readers */
  error?: string;
  hint?: string;
}

/** A text field of several lines (a reason, a note). */
export function Textarea({ label, error, hint, id, className, rows = 4, ...props }: TextareaProps) {
  const generatedId = useId();
  const textareaId = id ?? generatedId;
  const messageId = `${textareaId}-message`;
  const message = error ?? hint;

  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <label htmlFor={textareaId} className="text-sm font-medium">
          {label}
        </label>
      )}
      <textarea
        id={textareaId}
        rows={rows}
        aria-invalid={error ? true : undefined}
        aria-describedby={message ? messageId : undefined}
        className={cn(
          'w-full rounded-xl border bg-surface px-4 py-3 text-sm placeholder:text-ink-soft',
          'focus:border-brand-500 focus:ring-2 focus:ring-brand-100 focus:outline-none',
          error ? 'border-danger-500' : 'border-line',
          className,
        )}
        {...props}
      />
      {message && (
        <p id={messageId} className={cn('text-xs', error ? 'text-danger-500' : 'text-ink-soft')}>
          {message}
        </p>
      )}
    </div>
  );
}
