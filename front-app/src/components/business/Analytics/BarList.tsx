import { EmptyState } from '@/components/common/EmptyState';
import { CHART_COLORS } from '@/utils/chartColors';
import { formatNumber } from '@/utils/formatUtils';

export interface BarListItem {
  key: string;
  label: string;
  value: number;
  /** What is written next to the bar (default: the value) */
  display?: string;
  /** A chart colour (CHART_COLORS); default: the brand green */
  color?: string;
}

interface BarListProps {
  items: readonly BarListItem[];
  /** "orders", "customers": read out with each bar */
  unit: string;
  emptyTitle?: string;
}

/**
 * Counts by category as horizontal bars (orders by status, customers by order frequency). Plain HTML, so the
 * labels and numbers can be read and selected like text; the longest bar fills the width.
 */
export function BarList({ items, unit, emptyTitle = 'Nothing in this period' }: BarListProps) {
  const largest = Math.max(0, ...items.map((item) => item.value));
  if (items.length === 0 || largest === 0) {
    return <EmptyState title={emptyTitle} />;
  }
  return (
    <ul className="flex flex-col gap-3">
      {items.map((item) => (
        <li
          key={item.key}
          className="grid grid-cols-[minmax(7rem,10rem)_1fr_auto] items-center gap-3"
        >
          <span className="truncate text-sm">{item.label}</span>
          <span className="h-3 overflow-hidden rounded-full bg-page" aria-hidden="true">
            <span
              className="block h-full rounded-full"
              style={{
                width: `${(item.value / largest) * 100}%`,
                backgroundColor: item.color ?? CHART_COLORS.brand,
              }}
            />
          </span>
          <span
            className="text-sm font-semibold"
            aria-label={`${formatNumber(item.value)} ${unit}`}
          >
            {item.display ?? formatNumber(item.value)}
          </span>
        </li>
      ))}
    </ul>
  );
}
