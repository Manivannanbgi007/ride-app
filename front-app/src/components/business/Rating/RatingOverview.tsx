import { StarRating } from '@/components/common/StarRating';
import type { RatingDetails } from '@/types/rating';
import { formatAverage, formatReviewCount } from '@/utils/ratingUtils';

const STAR_LEVELS = ['5', '4', '3', '2', '1'] as const;

/** The big average with its stars and the count, and how many gave five, four ... one star (a bar each). */
export function RatingOverview({ details }: { details: RatingDetails }) {
  return (
    <div className="grid items-center gap-6 sm:grid-cols-[auto_1fr]">
      <div className="flex flex-col items-start gap-1">
        <p className="text-5xl leading-none font-extrabold">
          {details.average === null ? '–' : formatAverage(details.average)}
          <span className="ml-1 text-base font-semibold text-ink-soft">/ 5</span>
        </p>
        <StarRating value={details.average ?? 0} size="lg" />
        <p className="text-sm text-ink-soft">{formatReviewCount(details.count)}</p>
      </div>
      <ul aria-label="Reviews by stars" className="flex flex-col gap-1.5">
        {STAR_LEVELS.map((level) => {
          const votes = details.distribution[level];
          const share = details.count > 0 ? (votes / details.count) * 100 : 0;
          return (
            <li key={level} className="flex items-center gap-3 text-sm">
              <span className="w-12 shrink-0 text-ink-soft">{level} stars</span>
              <span
                role="img"
                aria-label={`${votes} of ${details.count} gave ${level} stars`}
                className="h-2.5 min-w-0 flex-1 overflow-hidden rounded-full bg-line"
              >
                <span
                  className="block h-full rounded-full bg-warning-500"
                  style={{ width: `${share}%` }}
                />
              </span>
              <span className="w-8 shrink-0 text-right text-ink-soft tabular-nums">{votes}</span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
