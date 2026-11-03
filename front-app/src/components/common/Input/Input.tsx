import { useId, type ComponentProps, type ReactNode } from 'react';

import { cn } from '@/utils/classNames';

export interface InputProps extends ComponentProps<'input'> {
  label?: string;
  /** Shown under the field in red; also marks the field invalid for screen readers */
  error?: string;
  /** A short help text under the field (when there is no error) */
  hint?: string;
  leftIcon?: ReactNode;
  /** A decoration at the right end (not clickable) */
  rightIcon?: ReactNode;
  /** Something to click at the right end, e.g. the eye that shows a password */
  rightAction?: ReactNode;
}

export function Input({
  label,
  error,
  hint,
  leftIcon,
  rightIcon,
  rightAction,
  id,
  className,
  ...props
}: InputProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const messageId = `${inputId}-message`;
  const message = error ?? hint;

  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <label htmlFor={inputId} className="text-sm font-medium">
          {label}
        </label>
      )}
      <div className="relative">
        {leftIcon && (
          <span className="pointer-events-none absolute inset-y-0 left-4 flex items-center text-ink-soft">
            {leftIcon}
          </span>
        )}
        <input
          id={inputId}
          aria-invalid={error ? true : undefined}
          aria-describedby={message ? messageId : undefined}
          className={cn(
            'h-11 w-full rounded-xl border bg-surface px-4 text-sm shadow-xs transition duration-200 placeholder:text-ink-soft',
            'hover:border-brand-300 focus:border-brand-500 focus:ring-4 focus:ring-brand-500/15 focus:outline-none',
            leftIcon !== undefined && 'pl-11',
            (rightIcon !== undefined || rightAction !== undefined) && 'pr-11',
            error ? 'border-danger-500' : 'border-line',
            className,
          )}
          {...props}
        />
        {rightAction && (
          <span className="absolute inset-y-0 right-1.5 flex items-center">{rightAction}</span>
        )}
        {rightIcon && (
          <span className="pointer-events-none absolute inset-y-0 right-4 flex items-center text-ink-soft">
            {rightIcon}
          </span>
        )}
      </div>
      {message && (
        <p id={messageId} className={cn('text-xs', error ? 'text-danger-500' : 'text-ink-soft')}>
          {message}
        </p>
      )}
    </div>
  );
}
