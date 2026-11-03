import type { ComponentProps, ReactNode } from 'react';

import {
  buttonStyles,
  type ButtonSize,
  type ButtonVariant,
} from '@/components/common/Button/buttonStyles';
import { Spinner } from '@/components/common/Loader';

export interface ButtonProps extends ComponentProps<'button'> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  /** Shows a spinner and blocks further clicks while the action runs (saving, sending) */
  isLoading?: boolean;
  leftIcon?: ReactNode;
}

/** A button (looks: buttonStyles). Its icon grows a little on hover; while loading a spinner takes its place. */
export function Button({
  variant = 'primary',
  size = 'md',
  isLoading = false,
  leftIcon,
  disabled,
  className,
  children,
  type = 'button',
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled || isLoading}
      aria-busy={isLoading || undefined}
      className={buttonStyles(variant, size, className)}
      {...props}
    >
      {isLoading ? (
        <Spinner size="sm" label="Please wait" />
      ) : (
        leftIcon && (
          <span className="inline-flex transition-transform duration-200 group-hover/button:scale-110">
            {leftIcon}
          </span>
        )
      )}
      {children}
    </button>
  );
}
