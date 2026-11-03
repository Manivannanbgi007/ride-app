import type { LucideIcon } from 'lucide-react';

import { AnimatedNumber } from '@/components/common/AnimatedNumber';
import { cn } from '@/utils/classNames';
import { stagger } from '@/utils/motion';

const TONES = {
  brand: 'bg-brand-50 text-brand-500',
  danger: 'bg-danger-50 text-danger-500',
  info: 'bg-info-50 text-info-500',
  warning: 'bg-warning-50 text-warning-text',
};

/** Colourful tiles (the dashboard's top row): a gradient with the icon large and faint behind the figure. */
const ACCENTS = {
  berry: 'from-berry-700 to-berry-900',
  sunset: 'from-sunset-500 to-sunset-600',
  brand: 'from-brand-600 to-brand-800',
  ruby: 'from-danger-500 to-ruby-600',
};

interface StatCardProps {
  icon: LucideIcon;
  /** Already formatted: '1,234', '₹18,591.10', '—' */
  value: string;
  label: string;
  /** A small line under the label: '(last 7 days)' */
  hint?: string;
  tone?: keyof typeof TONES;
  /** A coloured gradient tile with white text instead of the white card */
  accent?: keyof typeof ACCENTS;
  /** Its place in a row of cards: it rises in a little after the one before */
  index?: number;
}

/**
 * A key figure with its icon (the cards at the top of the dashboard). The card measures its own width: in a
 * narrow card (five in a row) the icon sits above the figure, so the figure and its label keep the whole width.
 * It rises into place and its figure counts up (motion-reduce: at once); a coloured tile's sheen sweeps now and then.
 */
export function StatCard({
  icon: Icon,
  value,
  label,
  hint,
  tone = 'brand',
  accent,
  index = 0,
}: StatCardProps) {
  if (accent) {
    return (
      <div
        style={stagger(index)}
        className={cn(
          'group/stat @container relative isolate overflow-hidden rounded-2xl bg-linear-to-br p-5 text-white shadow-card stagger motion-safe:animate-rise-in',
          'transition duration-300 hover:shadow-lg motion-safe:hover:-translate-y-1',
          ACCENTS[accent],
        )}
      >
        {/* Soft light, a sweeping sheen and the large icon: the tile's picture */}
        <span
          aria-hidden="true"
          className="absolute -top-10 -right-8 -z-10 size-36 rounded-full bg-white/10 transition-transform duration-700 group-hover/stat:scale-125"
        />
        <span
          aria-hidden="true"
          className="absolute inset-y-0 left-0 -z-10 w-1/4 bg-white/15 blur-sm motion-safe:animate-sheen"
        />
        <Icon
          aria-hidden="true"
          className="absolute -right-3 -bottom-4 -z-10 size-24 -rotate-12 text-white/20 transition-transform duration-500 group-hover/stat:-rotate-3 @xs:size-28"
        />
        <div className="relative min-w-0 pr-16">
          <p className="text-sm font-medium text-white/90">{label}</p>
          <p className="mt-1 truncate text-3xl font-bold tracking-tight tabular-nums" title={value}>
            <AnimatedNumber value={value} />
          </p>
          {hint && <p className="mt-1 text-xs text-white/75">{hint}</p>}
        </div>
      </div>
    );
  }
  return (
    <div
      style={stagger(index)}
      className="group/stat @container rounded-2xl bg-surface p-5 shadow-card transition duration-300 stagger hover:shadow-lg motion-safe:animate-rise-in motion-safe:hover:-translate-y-0.5"
    >
      <div className="flex items-center gap-4 @max-[15rem]:flex-col @max-[15rem]:items-start @max-[15rem]:gap-3">
        <span
          className={cn(
            'flex size-12 shrink-0 items-center justify-center rounded-2xl transition-transform duration-300 group-hover/stat:scale-110 group-hover/stat:-rotate-6 @xs:size-14',
            TONES[tone],
          )}
        >
          <Icon aria-hidden="true" className="size-6 @xs:size-7" />
        </span>
        <div className="min-w-0">
          <p
            className="truncate text-2xl font-bold tracking-tight tabular-nums @xs:text-3xl"
            title={value}
          >
            <AnimatedNumber value={value} />
          </p>
          <p className="text-sm">{label}</p>
          {hint && <p className="text-xs text-ink-soft">{hint}</p>}
        </div>
      </div>
    </div>
  );
}
