import { cn } from '@/utils/classNames';

/**
 * How buttons look, shared by <Button> and links that look like buttons (<Link className={buttonStyles(...)}>).
 *
 * Motion: a raised button lifts a little with a stronger glow on hover and presses in on click; the main buttons
 * also catch a light sweep of shine. Nothing moves for people who asked their system for less motion, and a
 * disabled button never reacts.
 */
const BASE = [
  'group/button relative isolate inline-flex items-center justify-center gap-2 overflow-hidden rounded-xl',
  'font-semibold whitespace-nowrap select-none',
  'transition-[translate,scale,box-shadow,background-color,border-color,color] duration-200 ease-out',
  'focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none',
  'disabled:cursor-not-allowed disabled:opacity-50 aria-disabled:cursor-not-allowed aria-disabled:opacity-50',
].join(' ');

/** Lift on hover, press on click. */
const LIFT = [
  'not-disabled:motion-safe:hover:-translate-y-0.5',
  'not-disabled:motion-safe:active:translate-y-0 not-disabled:motion-safe:active:scale-[0.97]',
].join(' ');

/** A light sweep across the button on hover (behind the label). */
const SHINE = [
  "before:pointer-events-none before:absolute before:inset-y-0 before:-left-1/2 before:-z-10 before:w-1/3 before:-skew-x-12 before:bg-white/25 before:content-['']",
  'before:opacity-0 before:transition-[left,opacity] before:duration-700 before:ease-out',
  'not-disabled:hover:before:left-[130%] not-disabled:hover:before:opacity-100 motion-reduce:before:hidden',
].join(' ');

const VARIANTS = {
  primary: cn(
    'bg-brand-500 text-on-brand shadow-brand',
    'not-disabled:hover:bg-brand-400 not-disabled:hover:shadow-brand-lg',
    LIFT,
    SHINE,
  ),
  secondary: cn(
    'bg-brand-50 text-brand-text-strong not-disabled:hover:bg-brand-100 not-disabled:hover:shadow-sm',
    LIFT,
  ),
  outline: cn(
    'border border-brand-500/70 bg-surface text-brand-text-strong',
    'not-disabled:hover:border-brand-500 not-disabled:hover:bg-brand-50 not-disabled:hover:shadow-brand',
    LIFT,
  ),
  danger: cn(
    'bg-linear-to-br from-danger-500 to-danger-600 text-white shadow-danger',
    'not-disabled:hover:shadow-[0_12px_26px_-8px_rgb(217_59_59/0.6)]',
    LIFT,
    SHINE,
  ),
  ghost: 'text-ink not-disabled:hover:bg-scrim/5 not-disabled:active:bg-scrim/10',
};

const SIZES = {
  sm: 'h-9 px-3.5 text-sm',
  md: 'h-11 px-5 text-sm',
  lg: 'h-12 px-6 text-base',
};

export type ButtonVariant = keyof typeof VARIANTS;
export type ButtonSize = keyof typeof SIZES;

export function buttonStyles(
  variant: ButtonVariant = 'primary',
  size: ButtonSize = 'md',
  className?: string,
): string {
  return cn(BASE, VARIANTS[variant], SIZES[size], className);
}
