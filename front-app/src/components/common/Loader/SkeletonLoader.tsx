import { cn } from '@/utils/classNames';

/** One grey placeholder block; give it the size of what it stands for. */
export function Skeleton({ className }: { className?: string }) {
  return (
    <div
      aria-hidden="true"
      className={cn(
        'animate-shimmer rounded-lg bg-linear-to-r from-line via-surface to-line bg-size-[200%_100%]',
        className,
      )}
    />
  );
}

interface SkeletonLoaderProps {
  /** Number of text lines */
  lines?: number;
  className?: string;
}

/** Placeholder lines where text or a chart will appear: the page keeps its shape while loading. */
export function SkeletonLoader({ lines = 3, className }: SkeletonLoaderProps) {
  return (
    <div role="status" aria-label="Loading" className={cn('flex flex-col gap-3', className)}>
      {Array.from({ length: lines }, (_, index) => (
        <Skeleton key={index} className={cn('h-4', index === lines - 1 ? 'w-2/3' : 'w-full')} />
      ))}
    </div>
  );
}
