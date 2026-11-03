import { StarRating } from '@/components/common/StarRating';
import { Skeleton } from '@/components/common/Loader';
import { useReviewSummary } from '@/hooks/api/reviewHooks';
import type { ReviewScope } from '@/types/review';
import { formatNumber } from '@/utils/formatUtils';

const LEVELS = [5, 4, 3, 2, 1] as const;

/** The average rating, the number of reviews and how many gave 5 to 1 stars. Nothing while there are no reviews. */
export function ReviewSummaryBar({ scope }: { scope: ReviewScope }) {
  const summary = useReviewSummary(scope);
  const data = summary.data;

  if (summary.isPending) {
    return <Skeleton className="mb-4 h-24 w-full rounded-2xl" />;
  }
  // A failed summary is only a missing extra: the list below explains its own failures
  if (!data || data.count === 0 || data.averageRating === null) {
    return null;
  }

  return (
    <div className="mb-4 grid items-center gap-5 rounded-2xl bg-page p-4 sm:grid-cols-[auto_1fr]">
      <div className="flex flex-col items-start gap-1 sm:items-center">
        <p className="text-3xl font-bold tracking-tight">{data.averageRating.toFixed(1)}</p>
        <StarRating value={data.averageRating} />
        <p className="text-xs text-ink-soft">
          {formatNumber(data.count)} {data.count === 1 ? 'review' : 'reviews'}
        </p>
      </div>
      <ul aria-label="Reviews by rating" className="flex flex-col gap-1">
        {LEVELS.map((level) => {
          const count = data.distribution[String(level)] ?? 0;
          const share = (count / data.count) * 100;
          return (
            <li
              key={level}
              className="grid grid-cols-[1.5rem_1fr_2.5rem] items-center gap-3 text-xs"
            >
              <span className="font-medium">{level}★</span>
              <span
                role="meter"
                aria-label={`${level} stars`}
                aria-valuemin={0}
                aria-valuemax={data.count}
                aria-valuenow={count}
                className="h-2 overflow-hidden rounded-full bg-line"
              >
                <span
                  className="block h-full rounded-full bg-warning-500"
                  style={{ width: `${share}%` }}
                />
              </span>
              <span className="text-right text-ink-soft">{formatNumber(count)}</span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
