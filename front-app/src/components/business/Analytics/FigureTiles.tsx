import { cn } from '@/utils/classNames';
import { PALETTE, type PaletteColour } from '@/utils/palette';

/** Each tone's colour: the label, the stripe and the light tint of its tile. */
const TONES: Record<NonNullable<Figure['tone']>, PaletteColour> = {
  ink: 'violet',
  brand: 'green',
  danger: 'rose',
  info: 'sky',
  warning: 'amber',
};

export interface Figure {
  label: string;
  /** Already formatted; '—' when unknown */
  value: string;
  /** A small line under the value: 'right now', 'of 120 customers' */
  hint?: string;
  tone?: 'ink' | 'brand' | 'danger' | 'info' | 'warning';
}

const COLUMNS: Record<number, string> = {
  3: 'lg:grid-cols-3',
  4: 'lg:grid-cols-4',
  5: 'lg:grid-cols-5',
  6: 'lg:grid-cols-3 xl:grid-cols-6',
};

/** A report's key figures, as one compact row of tinted tiles (each with its colour's stripe and label). */
export function FigureTiles({ figures }: { figures: readonly Figure[] }) {
  return (
    <dl
      className={cn(
        'grid grid-cols-2 gap-3 sm:grid-cols-3',
        COLUMNS[figures.length] ?? 'lg:grid-cols-4',
      )}
    >
      {figures.map((figure) => {
        const colour = PALETTE[TONES[figure.tone ?? 'ink']];
        return (
          <div
            key={figure.label}
            className={cn(
              'relative min-w-0 overflow-hidden rounded-2xl py-3 pr-3 pl-4 ring-1 ring-line/50',
              colour.soft,
            )}
          >
            <span aria-hidden="true" className={cn('absolute inset-y-0 left-0 w-1', colour.fill)} />
            <dt className={cn('truncate text-xs font-semibold', colour.text)}>{figure.label}</dt>
            <dd className="mt-0.5 truncate text-xl leading-tight font-extrabold text-ink tabular-nums">
              {figure.value}
            </dd>
            {figure.hint && <dd className="truncate text-[11px] text-ink-soft">{figure.hint}</dd>}
          </div>
        );
      })}
    </dl>
  );
}
