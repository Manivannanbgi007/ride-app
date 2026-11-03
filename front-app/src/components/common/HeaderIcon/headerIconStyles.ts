/**
 * The header's icons and user chip, in the same style as the main buttons: at rest a white tile with a green icon
 * (clear on the tinted header); on hover the brand gradient fades in, the icon turns white, the tile lifts with a
 * green glow and a light shine sweeps across; on click it presses in. No movement for people who asked their system
 * for less motion.
 *
 * Markup: <Link className={HEADER_ITEM}> <span className={HEADER_TILE}> icon </span> badge </Link>
 * (the badge sits outside the tile, which clips its gradient and shine to the rounded shape).
 */

/** The link or button: lifts on hover, presses on click, shows a clear focus ring. */
export const HEADER_ITEM = [
  'group/header relative inline-flex rounded-xl transition-transform duration-200 ease-out',
  'motion-safe:hover:-translate-y-0.5 motion-safe:active:translate-y-0 motion-safe:active:scale-95',
  'focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none',
].join(' ');

/** The visible tile: white with a green icon; the bright brand colour (deep-green icon) and a shine on hover. */
const TILE = [
  'relative isolate flex items-center overflow-hidden bg-surface text-brand-text-strong shadow-card ring-1 ring-brand-200',
  'transition-[box-shadow,color] duration-200 ease-out',
  // The gradient, faded in (a background change itself cannot be animated smoothly)
  "after:absolute after:inset-0 after:-z-20 after:bg-brand-500 after:opacity-0 after:transition-opacity after:duration-300 after:content-['']",
  // The shine
  "before:pointer-events-none before:absolute before:inset-y-0 before:-left-1/2 before:-z-10 before:w-1/3 before:-skew-x-12 before:bg-white/30 before:opacity-0 before:content-['']",
  'before:transition-[left,opacity] before:duration-700 before:ease-out motion-reduce:before:hidden',
  'group-hover/header:text-on-brand group-hover/header:shadow-brand-lg group-hover/header:ring-transparent',
  'group-hover/header:after:opacity-100 group-hover/header:before:left-[130%] group-hover/header:before:opacity-100',
].join(' ');

/** A square icon tile. */
export const HEADER_TILE = `${TILE} size-11 justify-center rounded-xl`;

/** A wider tile for the user: avatar and name. */
export const HEADER_CHIP = `${TILE} h-11 gap-3 rounded-xl py-1 pr-4 pl-1`;

/** The avatar inside the chip: light green, and see-through white on hover. */
export const HEADER_AVATAR = [
  'flex size-9 shrink-0 items-center justify-center rounded-lg bg-brand-100 text-sm font-bold text-brand-text-strong',
  'transition-colors duration-200 group-hover/header:bg-white/20 group-hover/header:text-white',
].join(' ');

/** How the icon itself moves on hover. */
export const ICON_MOTIONS = {
  ring: 'group-hover/header:motion-safe:animate-ring',
  pop: 'group-hover/header:motion-safe:animate-pop',
  nudge: 'group-hover/header:motion-safe:animate-nudge',
} as const;
export type IconMotion = keyof typeof ICON_MOTIONS;
