import type { LucideIcon } from 'lucide-react';
import { Link } from 'react-router';

import { AnimatedNumber } from '@/components/common/AnimatedNumber';
import { cn } from '@/utils/classNames';
import { stagger } from '@/utils/motion';
import { PALETTE, type PaletteColour } from '@/utils/palette';

export interface Kpi {
  label: string;
  /** Already formatted: '1,234', '₹18,591.10', '—' */
  value: string;
  /** A short line under the label: '12 Oct - 18 Oct', 'Food ready to delivered' */
  hint?: string;
  icon: LucideIcon;
  colour: PaletteColour;
  /** The tile opens this page (the list behind the figure) */
  to?: string;
}

const COLUMNS: Record<number, string> = {
  3: 'lg:grid-cols-3',
  4: 'lg:grid-cols-4',
  5: 'lg:grid-cols-5',
  6: 'lg:grid-cols-3 xl:grid-cols-6',
};

/**
 * Key figures in one compact row of tiles: each on its colour's light tint (or, `filled`, on its gradient with
 * white text), with its icon, the figure, what it is and a short note. Two per row on a phone. The tiles rise in one
 * after the other and their figures count up (motion-reduce: at once).
 */
export function KpiStrip({
  items,
  label,
  filled = false,
}: {
  items: readonly Kpi[];
  /** Read out by screen readers: what the figures are about */
  label: string;
  filled?: boolean;
}) {
  return (
    <ul
      aria-label={label}
      className={cn(
        'grid grid-cols-2 gap-3',
        items.length >= 5 && 'sm:grid-cols-3',
        COLUMNS[items.length] ?? 'lg:grid-cols-4',
      )}
    >
      {items.map((item, index) => (
        <li
          key={item.label}
          style={stagger(index)}
          className="flex min-w-0 stagger motion-safe:animate-rise-in"
        >
          <KpiTile kpi={item} filled={filled} />
        </li>
      ))}
    </ul>
  );
}

function KpiTile({ kpi, filled }: { kpi: Kpi; filled: boolean }) {
  const { icon: Icon, colour } = kpi;
  const look = PALETTE[colour];
  const content = (
    <>
      {filled ? (
        <>
          <Icon
            aria-hidden="true"
            className="absolute -right-3 -bottom-4 -z-10 size-20 -rotate-12 text-white/15 transition-transform duration-500 group-hover/kpi:-rotate-3"
          />
          {/* A sheen sweeps across the coloured tile now and then */}
          <span
            aria-hidden="true"
            className="absolute inset-y-0 left-0 -z-10 w-1/4 bg-white/20 blur-sm motion-safe:animate-sheen"
          />
        </>
      ) : (
        <span
          aria-hidden="true"
          className={cn(
            'absolute -top-6 -right-6 -z-10 size-16 rounded-full opacity-15 transition-transform duration-500 group-hover/kpi:scale-150',
            look.fill,
          )}
        />
      )}
      <span
        aria-hidden="true"
        className={cn(
          'flex size-10 shrink-0 items-center justify-center rounded-xl transition-transform duration-300 group-hover/kpi:scale-110 group-hover/kpi:-rotate-6',
          filled ? 'bg-white/20 ring-1 ring-white/30' : cn('text-white shadow-sm', look.fill),
        )}
      >
        <Icon className="size-5" />
      </span>
      <span className="min-w-0">
        <span
          className={cn(
            'block truncate text-lg leading-tight font-extrabold tracking-tight tabular-nums sm:text-xl',
            filled ? 'text-white' : 'text-ink',
          )}
          title={kpi.value}
        >
          <AnimatedNumber value={kpi.value} />
        </span>
        <span
          className={cn(
            'block truncate text-xs font-semibold',
            filled ? 'text-white/95' : look.text,
          )}
        >
          {kpi.label}
        </span>
        {kpi.hint && (
          <span
            className={cn('block truncate text-[11px]', filled ? 'text-white/80' : 'text-ink-soft')}
          >
            {kpi.hint}
          </span>
        )}
      </span>
    </>
  );
  const tile = cn(
    'group/kpi relative isolate flex w-full min-w-0 items-center gap-3 overflow-hidden rounded-2xl p-3 transition duration-300 hover:shadow-md motion-safe:hover:-translate-y-0.5 sm:p-3.5',
    filled ? cn('shadow-lg', look.fill, look.glow) : cn('ring-1 ring-line/50', look.soft),
  );
  return kpi.to ? (
    <Link
      to={kpi.to}
      className={cn(
        tile,
        'focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none',
      )}
    >
      {content}
    </Link>
  ) : (
    <div className={tile}>{content}</div>
  );
}
