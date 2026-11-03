import { CalendarDays, type LucideIcon } from 'lucide-react';

import { cn } from '@/utils/classNames';
import { formatWeekdayDayMonth } from '@/utils/dateUtils';

export interface ChartTooltipRow {
  label: string;
  value: string;
  /** A coloured figure (a line or bar of the chart); rows without a colour are totals, shown below a hairline */
  color?: string;
}

/** One line of the hover card: a glowing dot of the figure's colour (when it has one), its name and its value. */
function TooltipLine({ row }: { row: ChartTooltipRow }) {
  return (
    <li className="flex items-center justify-between gap-5">
      <span className="flex items-center gap-2 text-white/75">
        {row.color && (
          <span
            aria-hidden="true"
            className="size-2.5 shrink-0 rounded-full ring-2 ring-white/15"
            style={{ backgroundColor: row.color, boxShadow: `0 0 10px ${row.color}` }}
          />
        )}
        {row.label}
      </span>
      <span className="font-bold text-white tabular-nums">{row.value}</span>
    </li>
  );
}

/**
 * The charts' hover card: dark glass (a deep slate to indigo gradient, blurred behind) with a violet-to-rose-to-orange
 * line along its top, the day as a pill, the coloured figures, and below a hairline the totals. It is the same in
 * both themes: white on the deep gradient reads well on a light and on a dark page.
 */
export function ChartTooltip({
  day,
  title,
  icon: Icon = CalendarDays,
  rows,
}: {
  /** The day the figures are of ('YYYY-MM-DD'), written 'Thu, 16 Oct' */
  day?: string;
  /** Another heading instead of a day ('1 pm – 2 pm') */
  title?: string;
  icon?: LucideIcon;
  rows: readonly ChartTooltipRow[];
}) {
  const parts = rows.filter((row) => row.color);
  const totals = rows.filter((row) => !row.color);
  return (
    <div className="relative min-w-52 overflow-hidden rounded-2xl bg-linear-to-br from-slate-900/95 via-slate-900/95 to-indigo-950/95 px-4 pt-3.5 pb-3 text-sm text-white shadow-2xl ring-1 shadow-slate-950/40 ring-white/15 backdrop-blur-md">
      <span
        aria-hidden="true"
        className="absolute inset-x-0 top-0 h-1 bg-linear-to-r from-st-accepted-from via-st-placed-to to-sunset-500"
      />
      <p className="mb-2.5 inline-flex items-center gap-1.5 rounded-full bg-white/10 px-2.5 py-0.5 text-xs font-semibold text-white/90">
        <Icon aria-hidden="true" className="size-3.5" />
        {title ?? (day ? formatWeekdayDayMonth(day) : '')}
      </p>
      {parts.length > 0 && (
        <ul className="flex flex-col gap-1.5">
          {parts.map((row) => (
            <TooltipLine key={row.label} row={row} />
          ))}
        </ul>
      )}
      {totals.length > 0 && (
        <ul
          className={cn(
            'flex flex-col gap-1',
            parts.length > 0 && 'mt-2.5 border-t border-white/10 pt-2',
          )}
        >
          {totals.map((row) => (
            <TooltipLine key={row.label} row={row} />
          ))}
        </ul>
      )}
    </div>
  );
}
