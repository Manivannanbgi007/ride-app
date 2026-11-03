import { Skeleton } from '@/components/common/Loader';

/** The dashboard's shape while its first data loads. */
export function DashboardSkeleton() {
  return (
    <div role="status" aria-label="Loading the dashboard" className="flex flex-col gap-6">
      <div className="grid gap-6 sm:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: 4 }, (_, index) => (
          <Skeleton key={index} className="h-28 rounded-2xl bg-surface" />
        ))}
      </div>
      <div className="grid gap-6 xl:grid-cols-2">
        <Skeleton className="h-72 rounded-2xl bg-surface" />
        <Skeleton className="h-72 rounded-2xl bg-surface" />
      </div>
    </div>
  );
}
