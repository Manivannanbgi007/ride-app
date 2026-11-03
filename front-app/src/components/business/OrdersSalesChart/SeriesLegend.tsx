import { cn } from '@/utils/classNames';
import { formatNumber } from '@/utils/formatUtils';

import { ORDER_BARS, SALES_LINE, type SalesSeries, type SalesSeriesKey } from './ordersSalesSeries';

interface SeriesLegendProps {
  /** The period's count of each bar's orders, shown next to its name */
  counts: Record<Exclude<SalesSeriesKey, 'revenue'>, number>;
  hidden: ReadonlySet<SalesSeriesKey>;
  onToggle: (key: SalesSeriesKey) => void;
}

/** The chart's legend: one button per figure (sales, then the bars' parts) that switches it on or off. */
export function SeriesLegend({ counts, hidden, onToggle }: SeriesLegendProps) {
  return (
    <div role="group" aria-label="Shown in the chart" className="flex flex-wrap items-center gap-2">
      <SeriesToggle
        series={SALES_LINE}
        active={!hidden.has(SALES_LINE.key)}
        onClick={() => onToggle(SALES_LINE.key)}
      />
      {ORDER_BARS.map((series) => (
        <SeriesToggle
          key={series.key}
          series={series}
          count={counts[series.key as keyof SeriesLegendProps['counts']]}
          active={!hidden.has(series.key)}
          onClick={() => onToggle(series.key)}
        />
      ))}
    </div>
  );
}

/** A legend entry: a gradient dot, the name and the count; crossed out while its figure is hidden. */
function SeriesToggle({
  series,
  count,
  active,
  onClick,
}: {
  series: SalesSeries;
  count?: number;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      className={cn(
        'inline-flex cursor-pointer items-center gap-2 rounded-full px-3 py-1 text-xs font-medium ring-1 transition duration-200',
        'focus-visible:ring-2 focus-visible:ring-brand-500/40 focus-visible:outline-none',
        active
          ? 'bg-surface text-ink ring-line hover:bg-page'
          : 'text-ink-soft line-through ring-transparent hover:bg-page',
      )}
    >
      <span
        aria-hidden="true"
        className="size-3 shrink-0 rounded-full transition-opacity"
        style={{
          backgroundImage: `linear-gradient(160deg, ${series.color}, ${series.to})`,
          opacity: active ? 1 : 0.35,
        }}
      />
      {series.label}
      {/* The space keeps "Delivered 4" two words for screen readers (the gap only spaces them on screen) */}
      {count !== undefined && ' '}
      {count !== undefined && <span className="font-bold tabular-nums">{formatNumber(count)}</span>}
    </button>
  );
}
