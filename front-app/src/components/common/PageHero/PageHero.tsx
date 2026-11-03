import type { LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';

import { AnimatedNumber } from '@/components/common/AnimatedNumber';
import { cn } from '@/utils/classNames';
import { stagger } from '@/utils/motion';
import { PALETTE, type PaletteColour } from '@/utils/palette';

/** A live figure in the hero's row of chips. */
export interface HeroStat {
  label: string;
  /** Already formatted ('1,234', '₹18,591.10', '—'); a number counts up when it appears */
  value: string;
  icon?: LucideIcon;
}

interface PageHeroProps {
  /** The page's heading (its only h1) */
  title: string;
  subtitle?: ReactNode;
  /** The section's icon, in a floating tile of the hero's colour */
  icon: LucideIcon;
  /** The hero's colour (utils/palette): the icon tile, its glow, the soft shapes behind and the chips' icons */
  colour?: PaletteColour;
  /** Small words above the title: the part of the admin area ("Sales", "Catalogue") */
  eyebrow?: string;
  /** At the right: the page's main buttons, a period filter, a search */
  actions?: ReactNode;
  /** A few figures at a glance, in chips under the title */
  stats?: readonly HeroStat[];
}

/**
 * The top of an admin page: a panel washed with the section's colour, its icon floating in a gradient tile (a sheen
 * sweeps across it), the title with the part of the admin area above it, the page's actions at the right and a row
 * of figures at a glance that count up as they pop in. Two soft shapes drift slowly behind (motion-reduce: still).
 * Nothing is clipped: a calendar or menu opened from the actions may reach past the panel.
 */
export function PageHero({
  title,
  subtitle,
  icon: Icon,
  colour = 'green',
  eyebrow,
  actions,
  stats,
}: PageHeroProps) {
  const look = PALETTE[colour];

  return (
    <section className="relative z-10 rounded-3xl bg-surface p-5 shadow-card ring-1 ring-line/60 motion-safe:animate-rise-in sm:p-6 lg:p-7">
      {/* The backdrop: a tint of the colour, two slow blobs and a fading dot grid (clipped to the panel) */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 -z-10 overflow-hidden rounded-3xl"
      >
        <span className={cn('absolute inset-0 opacity-60', look.soft)} />
        <span className="absolute inset-0 bg-linear-to-r from-surface via-surface/85 to-transparent" />
        <span
          className={cn(
            'absolute -top-28 -right-10 size-80 rounded-full opacity-30 blur-3xl motion-safe:animate-blob',
            look.fill,
          )}
        />
        <span
          className={cn(
            'absolute -bottom-36 left-[45%] size-72 rounded-full opacity-20 blur-3xl motion-safe:animate-blob motion-safe:[animation-delay:-9s]',
            look.fill,
          )}
        />
        <span className="absolute inset-0 bg-[radial-gradient(rgb(29_134_76/0.12)_1px,transparent_1.5px)] mask-[linear-gradient(to_left,#000,transparent_70%)] bg-size-[18px_18px]" />
      </div>

      <div className="flex flex-wrap items-center justify-between gap-5">
        <div className="flex min-w-0 items-center gap-4">
          <span
            aria-hidden="true"
            className={cn(
              'relative flex size-14 shrink-0 items-center justify-center overflow-hidden rounded-2xl text-white shadow-lg motion-safe:animate-float sm:size-16',
              look.fill,
              look.glow,
            )}
          >
            <Icon className="size-7 sm:size-8" />
            <span className="absolute inset-y-0 left-0 w-1/3 bg-white/30 blur-[2px] motion-safe:animate-sheen" />
          </span>
          <div className="min-w-0">
            {eyebrow && (
              <p className={cn('text-xs font-bold tracking-wider uppercase', look.text)}>
                {eyebrow}
              </p>
            )}
            <h1 className="text-[1.75rem] leading-tight font-extrabold tracking-tight sm:text-3xl">
              {title}
            </h1>
            {subtitle && <p className="mt-1 text-sm text-ink-soft">{subtitle}</p>}
          </div>
        </div>
        {actions && <div className="flex flex-wrap items-center gap-2.5">{actions}</div>}
      </div>

      {stats && stats.length > 0 && (
        <ul aria-label={`${title} at a glance`} className="mt-5 flex flex-wrap gap-2.5">
          {stats.map(({ label, value, icon: StatIcon }, index) => (
            <li
              key={label}
              style={stagger(index + 2)}
              className="flex items-center gap-2.5 rounded-2xl bg-surface/85 py-2 pr-4 pl-2 shadow-sm ring-1 ring-line backdrop-blur-sm transition stagger motion-safe:animate-pop-in motion-safe:hover:-translate-y-0.5"
            >
              {StatIcon && (
                <span
                  aria-hidden="true"
                  className={cn(
                    'flex size-8 shrink-0 items-center justify-center rounded-xl',
                    look.soft,
                    look.text,
                  )}
                >
                  <StatIcon className="size-4" />
                </span>
              )}
              <span className="flex flex-col leading-tight">
                <span className="text-base font-extrabold tracking-tight tabular-nums">
                  <AnimatedNumber value={value} />
                </span>
                <span className="text-[11px] font-medium text-ink-soft">{label}</span>
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
