/**
 * The colours of the dashboards' tiles, icons and accents (customer and admin), made of the order-status palette of
 * index.css (--color-st-*), so they match the owner's pages and both themes. As whole class names, so Tailwind finds
 * them:
 *   fill   a gradient that carries white text (at least 4.5:1, the same in light and dark mode)
 *   line   the same gradient from left to right (a panel's top edge)
 *   glow   a coloured shadow under a filled tile
 *   soft   a light tint (dark in dark mode); text the colour's own text on the tint or on a card
 */
export const PALETTE = {
  green: {
    fill: 'bg-linear-to-br from-st-delivered-from to-st-out-to',
    line: 'bg-linear-to-r from-st-delivered-from to-st-out-to',
    glow: 'shadow-st-delivered-from/30',
    soft: 'bg-st-delivered-soft',
    text: 'text-st-delivered-text',
  },
  violet: {
    fill: 'bg-linear-to-br from-st-accepted-from to-st-accepted-to',
    line: 'bg-linear-to-r from-st-accepted-from to-st-accepted-to',
    glow: 'shadow-st-accepted-to/30',
    soft: 'bg-st-accepted-soft',
    text: 'text-st-accepted-text',
  },
  sky: {
    fill: 'bg-linear-to-br from-st-ready-from to-st-ready-to',
    line: 'bg-linear-to-r from-st-ready-from to-st-ready-to',
    glow: 'shadow-st-ready-to/30',
    soft: 'bg-st-ready-soft',
    text: 'text-st-ready-text',
  },
  teal: {
    fill: 'bg-linear-to-br from-st-out-from to-st-out-to',
    line: 'bg-linear-to-r from-st-out-from to-st-out-to',
    glow: 'shadow-st-out-to/30',
    soft: 'bg-st-out-soft',
    text: 'text-st-out-text',
  },
  sunset: {
    fill: 'bg-linear-to-br from-st-placed-from to-st-placed-to',
    line: 'bg-linear-to-r from-st-placed-from to-st-placed-to',
    glow: 'shadow-st-placed-to/30',
    soft: 'bg-st-placed-soft',
    text: 'text-st-placed-text',
  },
  amber: {
    fill: 'bg-linear-to-br from-st-preparing-from to-st-preparing-to',
    line: 'bg-linear-to-r from-st-preparing-from to-st-preparing-to',
    glow: 'shadow-st-preparing-from/30',
    soft: 'bg-st-preparing-soft',
    text: 'text-st-preparing-text',
  },
  rose: {
    fill: 'bg-linear-to-br from-st-rejected-to to-st-placed-to',
    line: 'bg-linear-to-r from-st-rejected-to to-st-placed-to',
    glow: 'shadow-st-placed-to/30',
    soft: 'bg-st-rejected-soft',
    text: 'text-st-rejected-text',
  },
} as const;

export type PaletteColour = keyof typeof PALETTE;
