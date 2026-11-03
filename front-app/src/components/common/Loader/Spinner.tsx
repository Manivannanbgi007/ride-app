import { cn } from '@/utils/classNames';

const SIZES = {
  sm: 'size-4 border-2',
  md: 'size-6 border-2',
  lg: 'size-10 border-[3px]',
};

interface SpinnerProps {
  size?: keyof typeof SIZES;
  /** Read out by screen readers */
  label?: string;
  className?: string;
}

/** A turning circle; takes the colour of the text around it. */
export function Spinner({ size = 'md', label = 'Loading', className }: SpinnerProps) {
  return (
    <span
      role="status"
      aria-label={label}
      className={cn(
        'inline-block animate-spin rounded-full border-current border-t-transparent',
        SIZES[size],
        className,
      )}
    />
  );
}
