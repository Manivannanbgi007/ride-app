import type { LucideIcon } from 'lucide-react';
import { useLayoutEffect, useRef, type CSSProperties } from 'react';

import { cn } from '@/utils/classNames';

export type TooltipPlacement = 'right' | 'bottom' | 'top';

interface TooltipBubbleProps {
  label: string;
  icon?: LucideIcon;
  placement: TooltipPlacement;
  /** The point it points at (fixed, in the window): the trigger's right middle, bottom centre or top centre */
  x: number;
  y: number;
  /** Going away: it slips back toward its trigger before it is removed */
  leaving: boolean;
}

/** The space kept between a tooltip and the window's edge (px). */
const EDGE = 8;

/** Where each placement springs from (the side of its trigger). */
const FROM: Record<TooltipPlacement, { x: string; y: string }> = {
  right: { x: '-10px', y: '0px' },
  bottom: { x: '0px', y: '-8px' },
  top: { x: '0px', y: '8px' },
};

/** Where the bubble sits against its point, and the side it grows from. */
const POSITION: Record<TooltipPlacement, string> = {
  right: '-translate-y-1/2',
  bottom: '-translate-x-1/2',
  top: '-translate-x-1/2 -translate-y-full',
};
const ORIGIN: Record<TooltipPlacement, string> = {
  right: 'origin-left',
  bottom: 'origin-top',
  top: 'origin-bottom',
};
const ARROW: Record<TooltipPlacement, string> = {
  right: 'top-1/2 -left-1 -translate-y-1/2 bg-brand-600',
  bottom: '-top-1 left-1/2 ml-(--tooltip-arrow-shift) -translate-x-1/2 bg-brand-600',
  top: '-bottom-1 left-1/2 ml-(--tooltip-arrow-shift) -translate-x-1/2 bg-brand-800',
};

/**
 * The tooltip itself: a small green gradient bubble with an arrow toward its trigger, the trigger's icon and its
 * title. It is decoration for mouse and keyboard users (the trigger already has the same name for screen
 * readers), drawn over the page so no panel edge can cut it.
 */
export function TooltipBubble({ label, icon: Icon, placement, x, y, leaving }: TooltipBubbleProps) {
  const outer = useRef<HTMLSpanElement>(null);

  // Past the window's edge: slide it back in, and move the arrow the other way so it still points at the trigger
  useLayoutEffect(() => {
    const element = outer.current;
    if (!element) {
      return;
    }
    element.style.left = `${x}px`;
    element.style.setProperty('--tooltip-arrow-shift', '0px');
    const box = element.getBoundingClientRect();
    if (box.width === 0) {
      return;
    }
    const right = window.innerWidth - EDGE;
    const shift = box.right > right ? right - box.right : box.left < EDGE ? EDGE - box.left : 0;
    if (shift !== 0) {
      const arrowRoom = box.width / 2 - 16;
      element.style.left = `${x + shift}px`;
      element.style.setProperty(
        '--tooltip-arrow-shift',
        `${Math.max(-arrowRoom, Math.min(arrowRoom, -shift))}px`,
      );
    }
  }, [x, label, placement]);

  const style = {
    left: x,
    top: y,
    '--tooltip-from-x': FROM[placement].x,
    '--tooltip-from-y': FROM[placement].y,
  } as CSSProperties;

  return (
    <span
      ref={outer}
      aria-hidden="true"
      style={style}
      className={cn('pointer-events-none fixed z-50', POSITION[placement])}
    >
      <span
        className={cn(
          'relative flex',
          ORIGIN[placement],
          leaving ? 'animate-tooltip-out' : 'animate-tooltip-in',
        )}
      >
        {/* The arrow, in the colour of the bubble's side it leaves */}
        <span className={cn('absolute size-2.5 rotate-45 rounded-[2px]', ARROW[placement])} />
        <span
          className={cn(
            'relative flex items-center gap-2 overflow-hidden rounded-xl px-3.5 py-2 text-sm font-semibold whitespace-nowrap text-white',
            'bg-linear-to-br from-brand-600 to-brand-800 ring-1 ring-white/15',
            'shadow-[0_12px_28px_-10px_rgb(29_88_45/0.7)]',
            // One sweep of shine as it appears
            "before:absolute before:inset-y-0 before:-left-1/2 before:w-1/3 before:-skew-x-12 before:bg-white/25 before:content-['']",
            'before:animate-tooltip-shine motion-reduce:before:hidden',
          )}
        >
          {Icon && <Icon className="relative size-4 shrink-0 opacity-90" />}
          <span className="relative">{label}</span>
        </span>
      </span>
    </span>
  );
}
