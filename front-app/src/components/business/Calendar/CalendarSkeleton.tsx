import { Skeleton } from '@/components/common/Loader';

/** The grid's placeholder while the first range loads: six rows of seven days, the shape of the month view. */
export function CalendarSkeleton() {
  return (
    <div role="status" aria-label="Loading the calendar" className="grid grid-cols-7 gap-1.5">
      {Array.from({ length: 42 }, (_, index) => (
        <Skeleton key={index} className="h-16 sm:h-24 lg:h-28" />
      ))}
    </div>
  );
}
