import { ArrowRight, type LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router';

import { cn } from '@/utils/classNames';

import { PALETTE, type PaletteColour } from './palette';

/** An icon on a small tile in one of the dashboard's gradients (white icon). */
export function ColourIcon({
  icon: Icon,
  colour,
  size = 'md',
}: {
  icon: LucideIcon;
  colour: PaletteColour;
  size?: keyof typeof ICON_SIZES;
}) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'flex shrink-0 items-center justify-center text-white shadow-md',
        ICON_SIZES[size].tile,
        PALETTE[colour].fill,
        PALETTE[colour].glow,
      )}
    >
      <Icon className={ICON_SIZES[size].icon} />
    </span>
  );
}

const ICON_SIZES = {
  xs: { tile: 'size-6 rounded-md', icon: 'size-3.5' },
  sm: { tile: 'size-8 rounded-lg', icon: 'size-4' },
  md: { tile: 'size-10 rounded-xl', icon: 'size-5' },
} as const;

/** "View all orders →": a link to the whole list, as a pill in the section's colour. */
export function SectionLink({
  to,
  colour,
  children,
}: {
  to: string;
  colour: PaletteColour;
  children: ReactNode;
}) {
  return (
    <Link
      to={to}
      className={cn(
        'group inline-flex items-center gap-1 rounded-full px-3.5 py-1.5 text-sm font-semibold transition hover:shadow-md focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none',
        PALETTE[colour].soft,
        PALETTE[colour].text,
      )}
    >
      {children}
      <ArrowRight
        aria-hidden="true"
        className="size-4 transition-transform group-hover:translate-x-0.5"
      />
    </Link>
  );
}

/**
 * A dashboard section's heading: its coloured icon, the title (with an id for aria-labelledby), what it shows, and
 * something at the right (a link, a switch).
 */
export function SectionHeading({
  id,
  icon,
  colour,
  title,
  subtitle,
  children,
}: {
  id: string;
  icon: LucideIcon;
  colour: PaletteColour;
  title: ReactNode;
  subtitle?: ReactNode;
  children?: ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div className="flex min-w-0 items-center gap-3">
        <ColourIcon icon={icon} colour={colour} />
        <div className="min-w-0">
          <h2 id={id} className="text-lg leading-tight font-bold text-ink">
            {title}
          </h2>
          {subtitle && <p className="text-sm text-ink-soft">{subtitle}</p>}
        </div>
      </div>
      {children}
    </div>
  );
}
