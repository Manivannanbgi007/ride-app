import { Skeleton } from '@/components/common/Loader/SkeletonLoader';

interface TableLoaderProps {
  rows?: number;
  columns?: number;
}

/** Placeholder rows while a table's data loads. */
export function TableLoader({ rows = 5, columns = 4 }: TableLoaderProps) {
  return (
    <div role="status" aria-label="Loading" className="flex flex-col gap-4 py-2">
      {Array.from({ length: rows }, (_, row) => (
        <div
          key={row}
          className="grid gap-4"
          style={{ gridTemplateColumns: `repeat(${columns}, 1fr)` }}
        >
          {Array.from({ length: columns }, (_, column) => (
            <Skeleton key={column} className="h-4" />
          ))}
        </div>
      ))}
    </div>
  );
}
