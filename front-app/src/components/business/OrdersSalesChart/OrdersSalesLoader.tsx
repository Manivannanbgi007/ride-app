import { Skeleton } from '@/components/common/Loader';
import { cn } from '@/utils/classNames';

/**
 * An orders-and-sales overview while it loads: the same layout (key figures, the chart, the side column), so nothing
 * jumps when the figures arrive.
 */
export function OrdersSalesLoader({ label, figures }: { label: string; figures: 3 | 4 }) {
  return (
    <div role="status" aria-label={label} className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_16rem]">
      <div className="flex flex-col gap-4">
        <div
          className={cn(
            'grid grid-cols-2 gap-3',
            figures === 4 ? 'lg:grid-cols-4' : 'sm:grid-cols-3',
          )}
        >
          {Array.from({ length: figures }, (_, index) => (
            <Skeleton
              key={index}
              className={cn(
                'h-24 rounded-2xl',
                figures === 3 && index === 0 && 'col-span-2 sm:col-span-1',
              )}
            />
          ))}
        </div>
        <Skeleton className="h-64 w-full rounded-2xl" />
      </div>
      <div className="flex flex-col gap-3">
        {Array.from({ length: 5 }, (_, index) => (
          <Skeleton key={index} className="h-8 w-full rounded-lg" />
        ))}
      </div>
    </div>
  );
}
