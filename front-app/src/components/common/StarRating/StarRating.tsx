import { Star } from 'lucide-react';

import { cn } from '@/utils/classNames';

const STARS = [1, 2, 3, 4, 5] as const;
const SIZES = { sm: 'size-3.5', md: 'size-4', lg: 'size-5' };

interface StarRatingProps {
  /** 0 to 5; halves and other fractions fill the star partly */
  value: number;
  size?: keyof typeof SIZES;
  className?: string;
}

/** Five stars filled up to `value`, with a text alternative for screen readers ("4.5 out of 5"). */
export function StarRating({ value, size = 'md', className }: StarRatingProps) {
  const clamped = Math.min(5, Math.max(0, value));
  return (
    <span
      role="img"
      aria-label={`${formatStars(clamped)} out of 5`}
      className={cn('inline-flex items-center gap-0.5', className)}
    >
      {STARS.map((star) => {
        const filled = Math.min(1, Math.max(0, clamped - (star - 1)));
        return (
          <span key={star} aria-hidden="true" className={cn('relative inline-flex', SIZES[size])}>
            <Star className={cn('absolute inset-0 text-ink-soft/40', SIZES[size])} />
            <span
              className="absolute inset-0 overflow-hidden"
              style={{ width: `${filled * 100}%` }}
            >
              <Star className={cn('fill-warning-500 text-warning-500', SIZES[size])} />
            </span>
          </span>
        );
      })}
    </span>
  );
}

/** 4 -> '4', 4.5 -> '4.5', 3.666 -> '3.7' */
function formatStars(value: number): string {
  return String(Math.round(value * 10) / 10);
}
